package com.comst19.dambom.feature.library.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.feature.library.R
import com.comst19.dambom.feature.library.file.copyStreams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportDocumentStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        private val journal get() = context.noBackupFilesDir.resolve("pending-exports")
        private val resolver get() = context.contentResolver
        private val active = mutableSetOf<String>()

        internal suspend fun save(
            destination: Uri,
            transform: (suspend (File) -> Unit)? = null,
            source: (File) -> File,
        ): Long {
            val handledByStore = AtomicBoolean(false)
            try {
                return withContext(io) {
                    val entry = Entry(UUID.randomUUID().toString(), destination, ownsGrant(destination))
                    var complete = false
                    try {
                        handledByStore.set(true)
                        synchronized(active) {
                            entry.record()
                            active.add(entry.id)
                        }
                        if (entry.releaseGrant) {
                            // Tree grants already cover child documents. Some providers only grant temporary access.
                            runCatching { resolver.takePersistableUriPermission(destination, WRITE_GRANT) }
                        }
                        val temporary = entry.temporary
                        transform?.invoke(temporary)
                        val file = source(temporary)
                        val size = file.length()
                        check(size > 0) { "Export source is empty or missing" }
                        copyStreams(
                            openInput = file::inputStream,
                            openOutput = { checkNotNull(resolver.openOutputStream(destination, "wt")) },
                        )
                        currentCoroutineContext().ensureActive()
                        entry.commit()
                        complete = true
                        size
                    } finally {
                        withContext(NonCancellable) {
                            try {
                                val removed = complete || removeDocument(destination)
                                entry.finish()
                                if (!removed) showCleanupWarning()
                            } finally {
                                synchronized(active) { active.remove(entry.id) }
                            }
                        }
                    }
                }
            } finally {
                if (!handledByStore.get()) {
                    discard(destination)
                }
            }
        }

        internal suspend fun discard(destination: Uri) {
            withContext(NonCancellable + io) {
                if (!removeDocument(destination)) showCleanupWarning()
            }
        }

        suspend fun recoverInterruptedExports() {
            val entries =
                withContext(io) {
                    synchronized(active) {
                        val files = journal.listFiles().orEmpty()
                        files.filter { it.extension == "new" }.forEach { it.delete() }
                        files.filter {
                            it.extension in RECOVERABLE_EXTENSIONS && it.nameWithoutExtension !in active
                        }
                    }
                }
            if (entries.isEmpty()) return
            withContext(NonCancellable + io) {
                var needsWarning = false
                for (marker in entries) {
                    if (!recover(marker)) needsWarning = true
                }
                if (needsWarning) showCleanupWarning()
            }
        }

        private fun recover(marker: File): Boolean {
            val id = marker.nameWithoutExtension
            val claimed = synchronized(active) { marker.isFile && active.add(id) }
            if (!claimed) return true
            return try {
                val lines = runCatching { marker.readLines() }.getOrNull()
                val destination = lines?.getOrNull(1)?.let(Uri::parse)
                if (destination?.scheme != "content") {
                    marker.delete()
                    false
                } else {
                    val entry = Entry(id, destination, lines.first() == "1")
                    val removed = marker.extension == "complete" || removeDocument(destination)
                    entry.finish()
                    removed
                }
            } finally {
                synchronized(active) { active.remove(id) }
            }
        }

        private fun ownsGrant(uri: Uri): Boolean =
            !DocumentsContract.isTreeUri(uri) &&
                resolver.persistedUriPermissions.none { it.uri == uri && it.isWritePermission }

        private fun removeDocument(uri: Uri): Boolean {
            val removed =
                try {
                    DocumentsContract.deleteDocument(resolver, uri)
                } catch (_: FileNotFoundException) {
                    runCatching {
                        resolver
                            .query(uri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID), null, null, null)
                            ?.use { !it.moveToFirst() } == true
                    }.getOrDefault(false)
                } catch (_: Exception) {
                    false
                }
            if (!removed) {
                // "rwt" requests truncation when the provider permits writing but refuses deletion.
                runCatching { checkNotNull(resolver.openFileDescriptor(uri, "rwt")).close() }
            }
            return removed
        }

        private suspend fun showCleanupWarning() {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.library_export_cleanup_incomplete, Toast.LENGTH_LONG).show()
            }
        }

        private inner class Entry(
            val id: String,
            val uri: Uri,
            val releaseGrant: Boolean,
        ) {
            private val pending get() = journal.resolve("$id.pending")
            private val completed get() = journal.resolve("$id.complete")
            val temporary get() = context.cacheDir.resolve("export-$id.mp4")

            fun record() {
                check(journal.isDirectory || journal.mkdirs())
                val staging = journal.resolve("$id.new")
                try {
                    staging.outputStream().use {
                        it.write(("${if (releaseGrant) 1 else 0}\n$uri").toByteArray())
                        it.fd.sync()
                    }
                    check(staging.renameTo(pending))
                } finally {
                    staging.delete()
                }
            }

            fun commit() {
                check(pending.renameTo(completed)) { "Could not commit export" }
            }

            fun finish() {
                if (pending.exists()) pending.renameTo(completed)
                if (releaseGrant) {
                    runCatching { resolver.releasePersistableUriPermission(uri, WRITE_GRANT) }
                }
                if (temporary.delete() || !temporary.exists()) {
                    pending.delete()
                    completed.delete()
                }
            }
        }

        private companion object {
            const val WRITE_GRANT = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val RECOVERABLE_EXTENSIONS = setOf("pending", "complete")
        }
    }

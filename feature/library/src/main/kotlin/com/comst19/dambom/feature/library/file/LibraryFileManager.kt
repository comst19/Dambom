package com.comst19.dambom.feature.library.file

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.comst19.dambom.core.coroutine.IoDispatcher
import com.comst19.dambom.core.domain.model.DownloadTask
import com.comst19.dambom.feature.library.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal class LibraryFileManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun export(
            task: DownloadTask,
            destination: Uri,
        ) = withContext(ioDispatcher) {
            task.requireLocalFile().copyTo(destination)
        }

        suspend fun exportToConfiguredLocation(
            task: DownloadTask,
            treeUri: Uri?,
        ) = withContext(ioDispatcher) {
            val source = task.requireLocalFile()
            if (treeUri == null) {
                source.copyToDefaultDownloads(task)
            } else {
                check(hasPersistedTreePermission(treeUri)) { "Configured download folder permission is unavailable" }
                source.copyToTree(task, treeUri)
            }
        }

        fun hasPersistedTreePermission(treeUri: Uri): Boolean =
            hasPersistedTreePermission(
                treeUri = treeUri,
                persistedPermissions =
                    context.contentResolver.persistedUriPermissions.map {
                        PersistedTreePermission(
                            uri = it.uri,
                            canRead = it.isReadPermission,
                            canWrite = it.isWritePermission,
                        )
                    },
            )

        fun createShareIntent(task: DownloadTask): Intent? =
            task.localFileOrNull()?.shareUriOrNull()?.let { uri ->
                val sendIntent =
                    Intent(Intent.ACTION_SEND)
                        .setType(task.mimeType ?: "video/*")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        .apply {
                            clipData = ClipData.newUri(context.contentResolver, task.title, uri)
                        }
                Intent
                    .createChooser(sendIntent, context.getString(R.string.library_share_chooser))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        private fun File.shareUriOrNull(): Uri? =
            try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    this,
                )
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun File.copyTo(destination: Uri) {
            val output = checkNotNull(context.contentResolver.openOutputStream(destination))
            inputStream().use { input -> output.use(input::copyTo) }
        }

        private fun File.copyToTree(
            task: DownloadTask,
            treeUri: Uri,
        ) {
            val parent =
                DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            val destination =
                checkNotNull(
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parent,
                        task.mimeType ?: DEFAULT_VIDEO_MIME_TYPE,
                        task.suggestedFileName(),
                    ),
                )
            try {
                copyTo(destination)
            } catch (throwable: Throwable) {
                DocumentsContract.deleteDocument(context.contentResolver, destination)
                throw throwable
            }
        }

        private fun File.copyToDefaultDownloads(task: DownloadTask) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                copyToMediaStore(task)
            } else {
                copyToLegacyDownloads(task)
            }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun File.copyToMediaStore(task: DownloadTask) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, task.suggestedFileName())
                    put(MediaStore.Video.Media.MIME_TYPE, task.mimeType ?: DEFAULT_VIDEO_MIME_TYPE)
                    put(MediaStore.Video.Media.RELATIVE_PATH, DEFAULT_DOWNLOAD_RELATIVE_PATH)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            val destination =
                checkNotNull(
                    context.contentResolver.insert(defaultDownloadCollectionUri(), values),
                )
            try {
                copyTo(destination)
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(destination, values, null, null)
            } catch (throwable: Throwable) {
                context.contentResolver.delete(destination, null, null)
                throw throwable
            }
        }

        @Suppress("DEPRECATION")
        private fun File.copyToLegacyDownloads(task: DownloadTask) {
            val directory =
                Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .resolve(DEFAULT_DOWNLOAD_DIRECTORY)
            check(directory.isDirectory || directory.mkdirs())
            val destination = directory.availableFile(task.suggestedFileName())
            copyTo(destination)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destination.path),
                arrayOf(task.mimeType ?: DEFAULT_VIDEO_MIME_TYPE),
                null,
            )
        }
    }

internal fun DownloadTask.suggestedFileName(): String {
    val extension =
        localFilePath
            ?.let(::File)
            ?.extension
            .orEmpty()
            .ifBlank { "mp4" }
    val baseName =
        title
            .trim()
            .replace(INVALID_FILE_NAME_CHARACTERS, "_")
            .take(MAX_FILE_NAME_LENGTH)
            .ifBlank { "video" }
    return if (baseName.endsWith(".$extension", ignoreCase = true)) baseName else "$baseName.$extension"
}

private fun DownloadTask.requireLocalFile(): File = localFileOrNull() ?: error("Saved video file is missing")

private fun DownloadTask.localFileOrNull(): File? = localFilePath?.let(::File)?.takeIf(File::isFile)

private fun File.availableFile(fileName: String): File {
    val requested = resolve(fileName)
    if (!requested.exists()) return requested
    val extension = requested.extension
    val baseName = requested.nameWithoutExtension
    var index = 1
    while (true) {
        val candidateName = if (extension.isBlank()) "$baseName ($index)" else "$baseName ($index).$extension"
        val candidate = resolve(candidateName)
        if (!candidate.exists()) return candidate
        index++
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun defaultDownloadCollectionUri(): Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

internal data class PersistedTreePermission(
    val uri: Uri,
    val canRead: Boolean,
    val canWrite: Boolean,
)

internal fun hasPersistedTreePermission(
    treeUri: Uri,
    persistedPermissions: Iterable<PersistedTreePermission>,
): Boolean = persistedPermissions.any { it.uri == treeUri && it.canRead && it.canWrite }

private val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private const val MAX_FILE_NAME_LENGTH = 80
private const val DEFAULT_DOWNLOAD_DIRECTORY = "Dambom"
private const val DEFAULT_DOWNLOAD_RELATIVE_PATH = "Download/Dambom"
private const val DEFAULT_VIDEO_MIME_TYPE = "video/mp4"

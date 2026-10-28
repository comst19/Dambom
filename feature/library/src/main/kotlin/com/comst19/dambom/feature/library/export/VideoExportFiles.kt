package com.comst19.dambom.feature.library.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.comst19.dambom.core.common.util.suspendRunCatching
import com.comst19.dambom.core.coroutine.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

internal class VideoExportFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun save(
            destination: String,
            prefix: String,
            transform: suspend (File) -> Unit,
        ): Long {
            val temporary = File(context.cacheDir, "$prefix-${UUID.randomUUID()}.mp4")
            var saved = false
            try {
                transform(temporary)
                val bytes =
                    withContext(ioDispatcher) {
                        check(temporary.isFile && temporary.length() > 0L)
                        temporary.inputStream().use { input ->
                            val stream =
                                checkNotNull(context.contentResolver.openOutputStream(Uri.parse(destination), "wt"))
                            stream.use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var count = input.read(buffer)
                                while (count >= 0) {
                                    currentCoroutineContext().ensureActive()
                                    output.write(buffer, 0, count)
                                    count = input.read(buffer)
                                }
                            }
                        }
                        temporary.length()
                    }
                saved = true
                return bytes
            } finally {
                withContext(NonCancellable + ioDispatcher) {
                    temporary.delete()
                    if (!saved) discard(destination)
                }
            }
        }

        suspend fun discard(destination: String) {
            withContext(ioDispatcher) {
                suspendRunCatching { DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(destination)) }
            }
        }
    }

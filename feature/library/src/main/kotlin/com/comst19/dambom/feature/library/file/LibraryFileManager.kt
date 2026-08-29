package com.comst19.dambom.feature.library.file

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
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
            val source = task.requireLocalFile()
            val output = checkNotNull(context.contentResolver.openOutputStream(destination))
            source.inputStream().use { input ->
                output.use(input::copyTo)
            }
            Unit
        }

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

private val INVALID_FILE_NAME_CHARACTERS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private const val MAX_FILE_NAME_LENGTH = 80

@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import android.os.StatFs
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.comst19.dambom.core.common.io.videoThumbnailFile
import com.comst19.dambom.core.common.io.videoThumbnailTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailUnavailableFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal open class DownloadFileStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val partialDirectory = context.filesDir.resolve("download-parts").apply(File::mkdirs)
        private val videoDirectory = context.filesDir.resolve("videos").apply(File::mkdirs)
        private val streamRoot = context.filesDir.resolve("download-streams")
        val streamDatabaseProvider by lazy { StandaloneDatabaseProvider(context) }

        fun streamDirectory(id: String): File = streamRoot.resolve(id)

        fun clearStream(id: String): Boolean {
            val directory = streamDirectory(id)
            return when {
                SimpleCache.isCacheFolderLocked(directory) -> {
                    false
                }

                !directory.exists() -> {
                    true
                }

                else -> {
                    SimpleCache.delete(directory, streamDatabaseProvider)
                    !directory.exists()
                }
            }
        }

        fun partialFile(id: String): File = partialDirectory.resolve("$id.part")

        fun hasSpaceFor(byteCount: Int): Boolean = hasSpaceFor(byteCount.toLong())

        fun hasSpaceFor(byteCount: Long): Boolean {
            val availableBytes = StatFs(partialDirectory.path).availableBytes
            return availableBytes - MIN_FREE_BYTES >= byteCount
        }

        fun partialValidatorFile(id: String): File = partialDirectory.resolve("$id.part.validator")

        fun clearPartial(id: String) {
            partialFile(id).delete()
            partialValidatorFile(id).delete()
        }

        fun completedFile(
            id: String,
            url: String,
            mimeType: String?,
        ): File = videoDirectory.resolve(id + fileExtension(url, mimeType))

        fun completedFilePath(localFileName: String?): String? =
            localFileName
                ?.let(videoDirectory::resolve)
                ?.takeIf(File::isFile)
                ?.absolutePath

        open fun delete(
            id: String,
            localFileName: String?,
        ): Boolean {
            val files =
                buildList {
                    add(partialFile(id))
                    add(partialValidatorFile(id))
                    localFileName?.let { fileName ->
                        val videoFile = videoDirectory.resolve(fileName)
                        add(videoFile)
                        add(videoFile.videoThumbnailFile())
                        add(videoFile.videoThumbnailTemporaryFile())
                        add(videoFile.videoThumbnailUnavailableFile())
                    }
                }
            var allDeleted = true
            if (!clearStream(id)) allDeleted = false
            files.forEach { file ->
                if (!deleteIfPresent(file)) allDeleted = false
            }
            return allDeleted
        }
    }

private fun fileExtension(
    url: String,
    mimeType: String?,
): String {
    if (streamMimeType(url, mimeType) != null) return ".mp4"
    val urlExtension =
        url
            .substringBefore('?')
            .substringAfterLast('/', missingDelimiterValue = "")
            .substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
    return urlExtension?.let { ".$it" } ?: when (mimeType?.substringBefore(';')) {
        "video/webm" -> ".webm"
        "video/quicktime" -> ".mov"
        "video/x-m4v" -> ".m4v"
        else -> ".mp4"
    }
}

private const val MIN_FREE_BYTES = 16L * 1024 * 1024

private fun deleteIfPresent(file: File): Boolean {
    if (!file.exists()) return true
    file.delete()
    return !file.exists()
}

package com.comst19.dambom.core.data.download

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DownloadFileStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val partialDirectory = context.filesDir.resolve("download-parts").apply(File::mkdirs)
        private val videoDirectory = context.filesDir.resolve("videos").apply(File::mkdirs)

        fun partialFile(id: String): File = partialDirectory.resolve("$id.part")

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

        fun delete(
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
                        add(File(videoFile.absolutePath + VIDEO_THUMBNAIL_SUFFIX))
                        add(File(videoFile.absolutePath + VIDEO_THUMBNAIL_SUFFIX + TEMPORARY_FILE_SUFFIX))
                        add(File(videoFile.absolutePath + VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX))
                    }
                }
            var allDeleted = true
            files.forEach { file ->
                if (!deleteIfPresent(file)) allDeleted = false
            }
            return allDeleted
        }

        private fun deleteIfPresent(file: File): Boolean {
            if (!file.exists()) return true
            file.delete()
            return !file.exists()
        }
    }

private fun fileExtension(
    url: String,
    mimeType: String?,
): String {
    val urlExtension =
        url
            .substringBefore('?')
            .substringAfterLast('/', missingDelimiterValue = "")
            .substringAfterLast('.', missingDelimiterValue = "")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
    if (urlExtension != null) return ".$urlExtension"
    return when (mimeType?.substringBefore(';')) {
        "video/webm" -> ".webm"
        "video/quicktime" -> ".mov"
        "video/x-m4v" -> ".m4v"
        else -> ".mp4"
    }
}

private const val VIDEO_THUMBNAIL_SUFFIX = ".thumbnail.jpg"
private const val VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX = ".thumbnail.unavailable"
private const val TEMPORARY_FILE_SUFFIX = ".tmp"

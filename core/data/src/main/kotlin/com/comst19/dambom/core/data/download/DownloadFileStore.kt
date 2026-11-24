@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.comst19.dambom.core.data.download

import android.content.Context
import android.content.Intent
import android.os.StatFs
import androidx.core.content.FileProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.core.common.io.videoSubtitleTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailFile
import com.comst19.dambom.core.common.io.videoThumbnailTemporaryFile
import com.comst19.dambom.core.common.io.videoThumbnailUnavailableFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("TooManyFunctions")
internal open class DownloadFileStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val partialDirectory by lazy { context.filesDir.resolve("download-parts").apply(File::mkdirs) }
        private val videoDirectory by lazy { context.filesDir.resolve("videos").apply(File::mkdirs) }
        private val privateVideoDirectory by lazy { context.filesDir.resolve("private_videos") }
        private val streamRoot by lazy { context.filesDir.resolve("download-streams") }
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

        open fun revokeSharing(localFileName: String) {
            val file = safeResolve(videoDirectory, localFileName) ?: return
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        fun completedFilePath(
            localFileName: String?,
            isPrivate: Boolean = false,
        ): String? =
            localFileName?.let { fileName ->
                val privatePath =
                    if (isPrivate) {
                        safeResolve(privateVideoDirectory, fileName)
                            ?.takeIf(File::isFile)
                            ?.absolutePath
                    } else {
                        null
                    }
                privatePath
                    ?: safeResolve(videoDirectory, fileName)
                        ?.takeIf(File::isFile)
                        ?.absolutePath
            }

        open fun movePrivacyFiles(
            localFileName: String,
            toPrivate: Boolean,
            preserveExistingTarget: Boolean = false,
        ): PrivacyFileMoveResult {
            val sourceDir = if (toPrivate) videoDirectory else privateVideoDirectory
            val targetDir = if (toPrivate) privateVideoDirectory else videoDirectory
            val sourceVideo = safeResolve(sourceDir, localFileName)
            val targetVideo = safeResolve(targetDir, localFileName)
            return when {
                sourceVideo == null || targetVideo == null -> {
                    PrivacyFileMoveResult.Failed
                }

                else -> {
                    moveVideoAndSidecars(
                        sourceVideo = sourceVideo,
                        targetVideo = targetVideo,
                        targetDir = targetDir,
                        preserveExistingTarget = preserveExistingTarget,
                    )
                }
            }
        }

        open fun rollbackPrivacyFiles(movedFiles: List<MovedPrivacyFile>) {
            rollbackMovedPrivacyFiles(movedFiles)
        }

        open fun delete(
            id: String,
            localFileName: String?,
        ): Boolean {
            val files =
                buildList {
                    add(partialFile(id))
                    add(partialValidatorFile(id))
                    localFileName?.let { fileName ->
                        safeResolve(videoDirectory, fileName)?.let { addAll(videoAndSidecarFiles(it)) }
                        safeResolve(privateVideoDirectory, fileName)?.let { addAll(videoAndSidecarFiles(it)) }
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

internal data class MovedPrivacyFile(
    val source: File,
    val target: File,
)

internal sealed interface PrivacyFileMoveResult {
    data class Moved(
        val files: List<MovedPrivacyFile>,
        val removedDuplicates: Boolean = false,
    ) : PrivacyFileMoveResult {
        val changed: Boolean
            get() = files.isNotEmpty() || removedDuplicates
    }

    data object MissingVideo : PrivacyFileMoveResult

    data object Failed : PrivacyFileMoveResult
}

private fun safeResolve(
    directory: File,
    fileName: String,
): File? {
    if (fileName.isBlank()) return null
    val root = directory.canonicalFile
    val candidate = File(root, fileName).canonicalFile
    return candidate.takeIf { it.parentFile == root }
}

private fun videoAndSidecarFiles(videoFile: File): List<File> =
    listOf(
        videoFile,
        videoFile.videoSubtitleFile(),
        videoFile.videoSubtitleTemporaryFile(),
        videoFile.videoThumbnailFile(),
        videoFile.videoThumbnailTemporaryFile(),
        videoFile.videoThumbnailUnavailableFile(),
    )

private fun moveVideoAndSidecars(
    sourceVideo: File,
    targetVideo: File,
    targetDir: File,
    preserveExistingTarget: Boolean,
): PrivacyFileMoveResult {
    val existingPairs =
        videoAndSidecarFiles(sourceVideo)
            .zip(videoAndSidecarFiles(targetVideo))
            .filter { (source, _) -> source.exists() }
    val needsTargetDir = existingPairs.any { (_, target) -> !preserveExistingTarget || !target.isFile }
    return when {
        !sourceVideo.isFile && !targetVideo.isFile -> {
            PrivacyFileMoveResult.MissingVideo
        }

        existingPairs.isEmpty() -> {
            PrivacyFileMoveResult.Moved(emptyList())
        }

        needsTargetDir && !targetDir.exists() && !targetDir.mkdirs() -> {
            PrivacyFileMoveResult.Failed
        }

        else -> {
            moveExistingPairs(existingPairs, preserveExistingTarget)
        }
    }
}

private fun moveExistingPairs(
    existingPairs: List<Pair<File, File>>,
    preserveExistingTarget: Boolean,
): PrivacyFileMoveResult {
    val movedFiles = mutableListOf<MovedPrivacyFile>()
    var removedDuplicates = false
    val succeeded =
        existingPairs.all { (source, target) ->
            when {
                preserveExistingTarget && target.isFile -> {
                    source.delete().also { deleted -> if (deleted) removedDuplicates = true }
                }

                moveFile(source, target) -> {
                    movedFiles += MovedPrivacyFile(source = source, target = target)
                    true
                }

                else -> {
                    false
                }
            }
        }
    return if (succeeded) {
        PrivacyFileMoveResult.Moved(
            files = movedFiles,
            removedDuplicates = removedDuplicates,
        )
    } else {
        rollbackMovedPrivacyFiles(movedFiles)
        PrivacyFileMoveResult.Failed
    }
}

private fun rollbackMovedPrivacyFiles(movedFiles: List<MovedPrivacyFile>) {
    movedFiles.asReversed().forEach { moved ->
        moveFile(moved.target, moved.source)
    }
}

internal fun moveFile(
    source: File,
    target: File,
): Boolean {
    val parent = target.parentFile
    return when {
        source.renameTo(target) -> {
            true
        }

        parent == null -> {
            false
        }

        else -> {
            val tempFile = File(parent, ".${target.name}.${System.nanoTime()}.move.tmp")
            runCatching {
                source.copyTo(tempFile, overwrite = true)
                if (!tempFile.renameTo(target)) {
                    tempFile.delete()
                    false
                } else if (source.delete()) {
                    true
                } else {
                    target.delete()
                    false
                }
            }.getOrElse {
                tempFile.delete()
                false
            }
        }
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

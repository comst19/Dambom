package com.comst19.dambom.core.common.io

import java.io.File

fun File.videoThumbnailFile(): File = File(absolutePath + VIDEO_THUMBNAIL_SUFFIX)

fun File.videoThumbnailUnavailableFile(): File = File(absolutePath + VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX)

fun File.videoThumbnailTemporaryFile(): File = File(videoThumbnailFile().absolutePath + TEMPORARY_FILE_SUFFIX)

private const val VIDEO_THUMBNAIL_SUFFIX = ".thumbnail.jpg"
private const val VIDEO_THUMBNAIL_UNAVAILABLE_SUFFIX = ".thumbnail.unavailable"
private const val TEMPORARY_FILE_SUFFIX = ".tmp"

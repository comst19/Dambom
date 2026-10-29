package com.comst19.dambom.core.data.media

internal fun ByteArray.hasMp4FileTypeBox(): Boolean =
    size >= MP4_SIGNATURE_BYTES &&
        copyOfRange(MP4_FILE_TYPE_OFFSET, MP4_FILE_TYPE_END).contentEquals(MP4_FILE_TYPE_BOX)

internal const val MP4_SIGNATURE_BYTES = 12L
private const val MP4_FILE_TYPE_OFFSET = 4
private const val MP4_FILE_TYPE_END = 8
private val MP4_FILE_TYPE_BOX = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())

package com.comst19.dambom.core.common.ui.format

import java.text.NumberFormat
import java.util.Locale

fun Long.formatFileSize(locale: Locale = Locale.getDefault()): String {
    val unit = if (this >= BYTES_PER_MEGABYTE) FileSizeUnit.Megabyte else FileSizeUnit.Kilobyte
    val value = this.toDouble() / unit.bytes
    val numberFormat =
        NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = unit.fractionDigits
            maximumFractionDigits = unit.fractionDigits
        }
    return "${numberFormat.format(value)} ${unit.label}"
}

private enum class FileSizeUnit(
    val bytes: Long,
    val fractionDigits: Int,
    val label: String,
) {
    Kilobyte(BYTES_PER_KILOBYTE, 0, "KB"),
    Megabyte(BYTES_PER_MEGABYTE, 1, "MB"),
}

private const val BYTES_PER_KILOBYTE = 1_024L
private const val BYTES_PER_MEGABYTE = 1_024L * BYTES_PER_KILOBYTE

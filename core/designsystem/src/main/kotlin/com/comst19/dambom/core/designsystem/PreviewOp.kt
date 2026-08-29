package com.comst19.dambom.core.designsystem

fun previewNoOp() = Unit

fun <T> previewNoOp(
    @Suppress("UNUSED_PARAMETER") value: T,
) = Unit

fun <T1, T2> previewNoOp(
    @Suppress("UNUSED_PARAMETER") value1: T1,
    @Suppress("UNUSED_PARAMETER") value2: T2,
) = Unit

package com.comst19.dambom.core.common.io

import java.io.File

fun File.videoSubtitleFile(): File = File(absolutePath + ".translated.srt")

fun File.videoSubtitleTemporaryFile(): File = File(videoSubtitleFile().absolutePath + ".tmp")

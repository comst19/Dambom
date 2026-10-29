package com.comst19.dambom.feature.library.subtitle.playback

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.comst19.dambom.core.common.io.videoSubtitleFile
import com.comst19.dambom.feature.library.subtitle.model.SubtitleCue
import com.comst19.dambom.feature.library.subtitle.model.parseGeneratedSrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

@Composable
internal fun rememberSubtitleCues(uri: Uri?): State<List<SubtitleCue>> {
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(emptyList<SubtitleCue>(), uri, revision, lifecycle) {
        value = emptyList()
        val path = uri?.takeIf { it.scheme == "file" }?.path ?: return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            withContext(Dispatchers.IO) {
                val file = File(path).videoSubtitleFile()
                var previousStamp: Pair<Long, Long>? = null
                while (true) {
                    val stamp = file.lastModified() to file.length()
                    if (stamp != previousStamp) {
                        value = readSubtitleCues(file)
                        previousStamp = stamp
                    }
                    delay(SUBTITLE_FILE_CHECK_MILLIS)
                }
            }
        }
    }
}

private fun readSubtitleCues(file: File): List<SubtitleCue> =
    try {
        file.takeIf(File::isFile)?.let { parseGeneratedSrt(it.readText()) }.orEmpty()
    } catch (_: IOException) {
        emptyList()
    }

private const val SUBTITLE_FILE_CHECK_MILLIS = 1_000L

package com.comst19.dambom.feature.library.subtitle.model

import com.google.mlkit.genai.common.GenAiException

internal enum class SubtitleFailure { SPEECH_PERMISSION, NO_SPEECH, SPEECH, AUDIO, UNKNOWN }

internal class NoSubtitleSpeechException : Exception()

internal fun subtitleFailure(failure: Exception): SubtitleFailure =
    when {
        failure is NoSubtitleSpeechException -> {
            SubtitleFailure.NO_SPEECH
        }

        failure is GenAiException && failure.message.orEmpty().contains("ERROR_TYPE_INSUFFICIENT_PERMISSION") -> {
            SubtitleFailure.SPEECH_PERMISSION
        }

        failure is GenAiException && failure.message.orEmpty().contains("ERROR_TYPE_NO_SPEECH_DETECTED") -> {
            SubtitleFailure.NO_SPEECH
        }

        failure is GenAiException -> {
            SubtitleFailure.SPEECH
        }

        failure.message == "Video has no audio track" -> {
            SubtitleFailure.AUDIO
        }

        else -> {
            SubtitleFailure.UNKNOWN
        }
    }

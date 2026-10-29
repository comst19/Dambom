package com.comst19.dambom.feature.library.subtitle

import com.comst19.dambom.feature.library.subtitle.model.NoSubtitleSpeechException
import com.comst19.dambom.feature.library.subtitle.model.SubtitleFailure
import com.comst19.dambom.feature.library.subtitle.model.subtitleFailure
import com.google.mlkit.genai.common.GenAiException
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleFailureTest {
    @Test fun `silent speech responses are distinct from permission and service failures`() {
        val silent = GenAiException("ERROR_TYPE_NO_SPEECH_DETECTED", null, GenAiException.ErrorCode.UNKNOWN)
        val permission = GenAiException("ERROR_TYPE_INSUFFICIENT_PERMISSION", null, GenAiException.ErrorCode.UNKNOWN)
        val service = GenAiException("Service disconnected", null, GenAiException.ErrorCode.UNKNOWN)
        assertEquals(SubtitleFailure.NO_SPEECH, subtitleFailure(silent))
        assertEquals(SubtitleFailure.SPEECH_PERMISSION, subtitleFailure(permission))
        assertEquals(SubtitleFailure.SPEECH, subtitleFailure(service))
        assertEquals(SubtitleFailure.NO_SPEECH, subtitleFailure(NoSubtitleSpeechException()))
    }
}

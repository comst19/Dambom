package com.comst19.dambom.translationruntime

import android.content.Context
import com.comst19.dambom.feature.library.subtitle.runtime.LocalSubtitleSpeechClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.core.BaseOptions

internal class YamnetSpeechClassifier(
    context: Context,
) : LocalSubtitleSpeechClassifier {
    private val format =
        AudioData.AudioDataFormat
            .builder()
            .setNumOfChannels(1)
            .setSampleRate(SAMPLE_RATE)
            .build()
    private val classifier =
        AudioClassifier.createFromOptions(
            context,
            AudioClassifier.AudioClassifierOptions
                .builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath("yamnet.tflite").build())
                .setCategoryAllowlist(listOf(SPEECH))
                .build(),
        )

    override fun speechScore(samples: ShortArray): Float {
        val input = AudioData.create(format, samples.size)
        input.load(samples)
        return classifier
            .classify(input)
            .classificationResults()
            .single()
            .classifications()
            .single()
            .categories()
            .single()
            .score()
    }

    override fun close() = classifier.close()
}

private const val SAMPLE_RATE = 16_000f
private const val SPEECH = "Speech"

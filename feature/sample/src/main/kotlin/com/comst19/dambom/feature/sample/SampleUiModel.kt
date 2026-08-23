package com.comst19.dambom.feature.sample

import androidx.compose.runtime.Immutable
import com.comst19.dambom.core.domain.model.Sample
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Immutable
data class SampleUiModel(
    val id: Long,
    val title: String,
    val description: String,
)

internal fun List<Sample>.toUiModels(): ImmutableList<SampleUiModel> =
    map { sample ->
        SampleUiModel(sample.id, sample.title, sample.description)
    }.toImmutableList()

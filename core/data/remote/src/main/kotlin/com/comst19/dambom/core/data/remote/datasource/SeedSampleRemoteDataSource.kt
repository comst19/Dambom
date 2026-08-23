package com.comst19.dambom.core.data.remote.datasource

import com.comst19.dambom.core.data.remote.model.NetworkSample

internal class SeedSampleRemoteDataSource : SampleRemoteDataSource {
    override suspend fun fetchSamples(): List<NetworkSample> =
        listOf(
            NetworkSample(FIRST_SAMPLE_ID, "Offline first", "Room is the source of truth."),
            NetworkSample(SECOND_SAMPLE_ID, "Replaceable boundaries", "Network and storage use contracts."),
            NetworkSample(THIRD_SAMPLE_ID, "Stateless Compose", "Routes own state, screens render values."),
        )
}

private const val FIRST_SAMPLE_ID = 1L
private const val SECOND_SAMPLE_ID = 2L
private const val THIRD_SAMPLE_ID = 3L

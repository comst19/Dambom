package com.comst19.dambom.core.data.remote.datasource

import com.comst19.dambom.core.data.remote.model.NetworkSample

interface SampleRemoteDataSource {
    suspend fun fetchSamples(): List<NetworkSample>
}

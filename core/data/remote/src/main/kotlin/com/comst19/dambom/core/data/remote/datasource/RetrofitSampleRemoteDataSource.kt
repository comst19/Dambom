package com.comst19.dambom.core.data.remote.datasource

import com.comst19.dambom.core.data.remote.api.SampleApi
import com.comst19.dambom.core.data.remote.mapper.toNetworkSample
import com.comst19.dambom.core.data.remote.model.NetworkSample
import com.comst19.dambom.core.data.remote.model.SampleResponse
import com.comst19.dambom.core.network.model.unwrapData
import javax.inject.Inject

class RetrofitSampleRemoteDataSource
    @Inject
    constructor(
        private val api: SampleApi,
    ) : SampleRemoteDataSource {
        override suspend fun fetchSamples(): List<NetworkSample> =
            remoteDataCall {
                api.getSamples().unwrapData().map(SampleResponse::toNetworkSample)
            }
    }

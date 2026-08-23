package com.comst19.dambom.core.data.remote.api

import com.comst19.dambom.core.data.remote.model.SampleResponse
import com.comst19.dambom.core.network.model.ApiResponse
import retrofit2.http.GET

interface SampleApi {
    @GET("samples")
    suspend fun getSamples(): ApiResponse<List<SampleResponse>>
}

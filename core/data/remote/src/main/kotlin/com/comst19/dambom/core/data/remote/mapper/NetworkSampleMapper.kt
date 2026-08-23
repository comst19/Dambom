package com.comst19.dambom.core.data.remote.mapper

import com.comst19.dambom.core.data.remote.model.NetworkSample
import com.comst19.dambom.core.data.remote.model.SampleResponse

internal fun SampleResponse.toNetworkSample(): NetworkSample =
    NetworkSample(
        id = id,
        title = title,
        description = description.orEmpty(),
    )

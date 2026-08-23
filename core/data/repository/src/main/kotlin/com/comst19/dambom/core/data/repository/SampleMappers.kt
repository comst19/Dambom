package com.comst19.dambom.core.data.repository

import com.comst19.dambom.core.data.remote.model.NetworkSample
import com.comst19.dambom.core.database.SampleEntity
import com.comst19.dambom.core.domain.model.Sample

internal fun NetworkSample.toEntity(nowEpochMillis: Long): SampleEntity =
    SampleEntity(
        id = id,
        title = title.trim(),
        description = description,
        syncedAtEpochMillis = nowEpochMillis,
    )

internal fun SampleEntity.toDomain(): Sample =
    Sample(
        id = id,
        title = title,
        description = description,
    )

package com.comst19.dambom.core.domain.model

data class VideoTag(
    val id: String,
    val name: String,
)

data class VideoTagAssignment(
    val videoId: String,
    val tagId: String,
)

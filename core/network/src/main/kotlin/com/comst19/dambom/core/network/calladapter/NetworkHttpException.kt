package com.comst19.dambom.core.network.calladapter

class NetworkHttpException(
    val statusCode: Int,
    val errorCode: String?,
    message: String?,
) : Exception(message ?: "HTTP $statusCode")

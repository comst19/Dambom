package com.comst19.dambom.feature.web.webview

import android.webkit.WebViewClient
import com.comst19.dambom.feature.web.R

internal enum class WebNavigationFailure(
    val titleRes: Int,
    val descriptionRes: Int,
    val retryable: Boolean,
    val canOpenExternal: Boolean = true,
) {
    CONNECTION(
        R.string.web_connection_error_title,
        R.string.web_connection_error_description,
        retryable = true,
    ),
    TIMEOUT(
        R.string.web_timeout_error_title,
        R.string.web_timeout_error_description,
        retryable = true,
    ),
    UNSUPPORTED_URL(
        R.string.web_unsupported_url_error_title,
        R.string.web_unsupported_url_error_description,
        retryable = false,
    ),
    HTTP_CLIENT(
        R.string.web_access_error_title,
        R.string.web_access_error_description,
        retryable = false,
    ),
    HTTP_SERVER(
        R.string.web_server_error_title,
        R.string.web_server_error_description,
        retryable = true,
    ),
    SECURITY(
        R.string.web_security_error_title,
        R.string.web_security_error_description,
        retryable = false,
        canOpenExternal = false,
    ),
    UNKNOWN(
        R.string.web_unknown_error_title,
        R.string.web_unknown_error_description,
        retryable = false,
    ),
}

internal fun classifyWebNavigationFailure(
    isForMainFrame: Boolean,
    errorCode: Int?,
): WebNavigationFailure? {
    if (!isForMainFrame || errorCode == null) return null
    return when (errorCode) {
        WebViewClient.ERROR_HOST_LOOKUP,
        WebViewClient.ERROR_CONNECT,
        WebViewClient.ERROR_IO,
        -> WebNavigationFailure.CONNECTION

        WebViewClient.ERROR_TIMEOUT -> WebNavigationFailure.TIMEOUT

        WebViewClient.ERROR_UNSUPPORTED_SCHEME,
        WebViewClient.ERROR_BAD_URL,
        WebViewClient.ERROR_REDIRECT_LOOP,
        WebViewClient.ERROR_FILE,
        WebViewClient.ERROR_FILE_NOT_FOUND,
        -> WebNavigationFailure.UNSUPPORTED_URL

        WebViewClient.ERROR_AUTHENTICATION,
        WebViewClient.ERROR_PROXY_AUTHENTICATION,
        WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME,
        WebViewClient.ERROR_TOO_MANY_REQUESTS,
        -> WebNavigationFailure.HTTP_CLIENT

        WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        WebViewClient.ERROR_UNSAFE_RESOURCE,
        -> WebNavigationFailure.SECURITY

        else -> WebNavigationFailure.UNKNOWN
    }
}

internal fun classifyHttpNavigationFailure(
    isForMainFrame: Boolean,
    statusCode: Int?,
): WebNavigationFailure? {
    if (!isForMainFrame || statusCode == null) return null
    return when {
        statusCode == HTTP_REQUEST_TIMEOUT -> WebNavigationFailure.TIMEOUT
        statusCode in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END -> WebNavigationFailure.HTTP_SERVER
        statusCode in HTTP_CLIENT_ERROR_START..HTTP_CLIENT_ERROR_END -> WebNavigationFailure.HTTP_CLIENT
        else -> null
    }
}

private const val HTTP_CLIENT_ERROR_START = 400
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_CLIENT_ERROR_END = 499
private const val HTTP_SERVER_ERROR_START = 500
private const val HTTP_SERVER_ERROR_END = 599

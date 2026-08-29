package com.comst19.dambom.feature.library

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun originalLinkIntents(sourcePageUrl: String): List<Intent> {
    val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(sourcePageUrl))
    return if (videoSourcePresentation(sourcePageUrl).kind == VideoSourceKind.X) {
        listOf(Intent(genericIntent).setPackage(X_ANDROID_PACKAGE), genericIntent)
    } else {
        listOf(genericIntent)
    }
}

internal fun openOriginalLink(
    context: Context,
    sourcePageUrl: String,
): Boolean =
    originalLinkIntents(sourcePageUrl).any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

private const val X_ANDROID_PACKAGE = "com.twitter.android"

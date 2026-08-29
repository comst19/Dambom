package com.comst19.dambom.feature.library.external

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.comst19.dambom.feature.library.VideoSourceKind
import com.comst19.dambom.feature.library.videoSourcePresentation

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
        context.startActivitySafely(intent)
    }

internal fun copyOriginalLink(
    context: Context,
    sourcePageUrl: String,
) {
    context
        .getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("URL", sourcePageUrl))
}

internal fun shareOriginalLink(
    context: Context,
    sourcePageUrl: String,
    chooserTitle: String,
): Boolean = context.startActivitySafely(originalLinkShareIntent(sourcePageUrl, chooserTitle))

internal fun originalLinkShareIntent(
    sourcePageUrl: String,
    chooserTitle: String,
): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, sourcePageUrl),
        chooserTitle,
    )

private fun Context.startActivitySafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }

private const val X_ANDROID_PACKAGE = "com.twitter.android"

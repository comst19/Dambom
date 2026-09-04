package com.comst19.dambom.feature.web

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

internal fun Context.openExternal(
    url: String,
    failureMessage: String,
) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

internal fun Context.copyLink(
    url: String,
    copiedMessage: String,
) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("URL", url))
    Toast.makeText(this, copiedMessage, Toast.LENGTH_SHORT).show()
}

internal fun Context.shareLink(
    url: String,
    failureMessage: String,
) {
    val intent =
        Intent
            .createChooser(
                Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, url),
                getString(R.string.web_share_chooser),
            )
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

package com.comst19.dambom.feature.settings.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.comst19.dambom.core.designsystem.DambomDarkColorScheme
import com.comst19.dambom.core.designsystem.DambomLightColorScheme
import com.comst19.dambom.core.designsystem.DambomTypography
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity

private const val SUPPORT_EMAIL = "madeatnaru@gmail.com"

internal fun Context.sendSupportEmail(
    subject: String,
    body: String,
    failureMessage: String,
) {
    val intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL"))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
    startExternalActivity(intent, failureMessage)
}

internal fun Context.openExternalPage(
    url: String,
    failureMessage: String,
) {
    startExternalActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), failureMessage)
}

private fun Context.startExternalActivity(
    intent: Intent,
    failureMessage: String,
) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, failureMessage, Toast.LENGTH_SHORT).show()
    }
}

internal fun Context.openSourceLicenses(title: String) {
    OssLicensesMenuActivity.setActivityTitle(title)
    OssLicensesMenuActivity.setTheme(DambomLightColorScheme, DambomDarkColorScheme, DambomTypography)
    startActivity(Intent(this, OssLicensesMenuActivity::class.java))
}

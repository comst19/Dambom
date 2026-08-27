package com.comst19.dambom.feature.settings.component

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.comst19.dambom.core.designsystem.DambomDarkColorScheme
import com.comst19.dambom.core.designsystem.DambomLightColorScheme
import com.comst19.dambom.core.designsystem.DambomTypography
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity

internal fun Context.sendFeedback(
    chooserTitle: String,
    body: String,
    failureMessage: String,
) {
    val intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                .putExtra(Intent.EXTRA_TEXT, body),
            chooserTitle,
        )
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

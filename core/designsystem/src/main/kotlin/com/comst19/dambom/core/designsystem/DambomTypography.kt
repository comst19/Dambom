package com.comst19.dambom.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DambomTypography =
    Typography().run {
        copy(
            displaySmall = displaySmall.copy(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            headlineSmall = headlineSmall.copy(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
            bodyLarge = bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
            labelLarge = labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodySmall = bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
        )
    }

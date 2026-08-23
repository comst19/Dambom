package com.comst19.dambom.core.designsystem

import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Phone",
    device = Devices.PHONE,
    showBackground = true,
)
@Preview(
    name = "Foldable",
    device = Devices.FOLDABLE,
    showBackground = true,
)
@Preview(
    name = "Tablet",
    device = Devices.TABLET,
    showBackground = true,
)
@Preview(
    name = "Desktop",
    device = Devices.DESKTOP,
    showBackground = true,
)
annotation class FormFactorPreviews

@Preview(
    name = "Phone portrait",
    device = "spec:width=411dp,height=891dp,dpi=420",
    showBackground = true,
)
@Preview(
    name = "Phone landscape",
    device = "spec:width=891dp,height=411dp,dpi=420",
    showBackground = true,
)
annotation class PhoneOrientationPreviews

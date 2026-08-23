plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    alias(libs.plugins.roborazzi)
}

android { namespace = "com.comst19.dambom.core.screenshot.testing" }

dependencies {
    api(libs.androidx.compose.ui.test.junit4)
    api(libs.androidx.compose.ui.test.manifest)
    api(libs.roborazzi)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.robolectric)
}

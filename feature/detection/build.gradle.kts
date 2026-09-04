plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.detection" }

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
}

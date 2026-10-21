plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.detection" }

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.okhttp.core)
    implementation(libs.androidx.media3.datasource.okhttp)
    runtimeOnly(libs.androidx.media3.exoplayer.hls)
    runtimeOnly(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)
}

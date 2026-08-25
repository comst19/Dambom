plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.web" }

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.webkit)
}

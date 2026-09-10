plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.home" }

dependencies {
    implementation(projects.core.common)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
}

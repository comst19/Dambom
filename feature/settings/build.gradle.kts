plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.settings" }

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.oss.licenses)
}

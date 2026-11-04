plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.downloads" }

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.coroutine)
    implementation(libs.androidx.compose.material.icons.extended)
}

plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.downloads" }

dependencies {
    implementation(projects.feature.downloads.api)
    implementation(projects.feature.library.api)
    implementation(projects.core.common)
    implementation(projects.core.coroutine)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.feature.home.api)
    testImplementation(projects.feature.web.api)
}

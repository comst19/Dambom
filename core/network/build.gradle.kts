plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android {
    namespace = "com.comst19.dambom.core.network"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    testImplementation(libs.mockwebserver)
}

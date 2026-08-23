plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
    id("dambom.kotlin.serialization")
}

android {
    namespace = "com.comst19.dambom.core.network"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    testImplementation(libs.mockwebserver)
}

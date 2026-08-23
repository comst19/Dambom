plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
    id("dambom.kotlin.serialization")
}

android { namespace = "com.comst19.dambom.core.data.remote" }

dependencies {
    implementation(projects.core.network)
    implementation(libs.retrofit.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.retrofit.kotlin.serialization)
}

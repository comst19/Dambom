plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.data.repository" }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.mockwebserver)
}

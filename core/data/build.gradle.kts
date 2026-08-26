plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
    id("dambom.kotlin.serialization")
}

android { namespace = "com.comst19.dambom.core.data" }

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.coroutine)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.room.testing)
}

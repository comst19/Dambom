plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.data" }

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.coroutine)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.database)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.room.testing)
}

plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.library" }

dependencies {
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.androidx.work.testing)
    implementation(libs.mlkit.speech.recognition)
    implementation(libs.play.feature.delivery)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.mockwebserver)
    implementation(projects.core.common)
    implementation(projects.core.coroutine)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui.compose)
}

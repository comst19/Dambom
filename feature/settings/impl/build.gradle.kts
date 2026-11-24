plugins {
    id("dambom.android.feature")
    id("dambom.kotlin.serialization")
}

android { namespace = "com.comst19.dambom.feature.settings" }

dependencies {
    implementation(projects.feature.settings.api)
    implementation(projects.core.common)
    implementation(projects.core.coroutine)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.play.services.oss.licenses)
}

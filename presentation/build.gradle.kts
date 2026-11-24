plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.presentation" }

dependencies {
    implementation(projects.feature.detection.api)
    implementation(projects.feature.detection.impl)
    implementation(projects.feature.downloads.impl)
    implementation(projects.feature.home.api)
    implementation(projects.feature.home.impl)
    implementation(projects.feature.library.api)
    implementation(projects.feature.library.impl)
    implementation(projects.feature.settings.impl)
    implementation(projects.feature.web.impl)

    implementation(projects.core.commonUi)
    implementation(projects.core.common)
    implementation(projects.core.coroutine)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.navigation)

    implementation(libs.androidx.activity.compose)
    api(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.core.testing)
    testImplementation(projects.feature.downloads.api)
    testImplementation(projects.feature.settings.api)
    testImplementation(projects.feature.web.api)
}

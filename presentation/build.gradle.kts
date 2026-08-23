plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.presentation" }

dependencies {
    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.sample)
    implementation(projects.feature.settings)

    implementation(projects.core.commonUi)
    implementation(projects.core.common)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.navigation)
    implementation(projects.core.navigationContract)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(projects.core.testing)
}

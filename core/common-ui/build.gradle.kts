plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.common.ui" }

dependencies {
    implementation(projects.core.common)
    implementation(libs.kotlinx.coroutines.android)
}

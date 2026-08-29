plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.navigation" }

dependencies {
    api(projects.core.navigationContract)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.coroutines.core)
}

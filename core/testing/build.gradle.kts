plugins {
    id("dambom.android.library")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.testing" }

dependencies {
    api(projects.core.analytics)
    api(projects.core.navigation)
    api(libs.kotlinx.coroutines.test)
    api(libs.junit)
}

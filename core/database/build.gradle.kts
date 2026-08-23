plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.room")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.database" }

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

plugins {
    id("dambom.android.library")
    id("dambom.android.hilt")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.datastore" }

dependencies {
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
}

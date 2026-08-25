plugins {
    id("dambom.android.library")
    id("dambom.android.compose")
    id("dambom.android.test")
}

android { namespace = "com.comst19.dambom.core.designsystem" }

dependencies { implementation(libs.androidx.compose.material3.adaptive) }

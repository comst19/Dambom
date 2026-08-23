plugins {
    id("dambom.android.library")
    id("dambom.android.test")
    id("dambom.kotlin.serialization")
}

android { namespace = "com.comst19.dambom.core.navigation.contract" }

dependencies {
    api(projects.core.navigation)
}

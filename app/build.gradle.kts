plugins {
    id("dambom.android.application")
    id("dambom.android.hilt")
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.comst19.dambom"
    defaultConfig {
        applicationId = "com.comst19.dambom"
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { buildConfig = true }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"DEBUG\"")
        }
        getByName("qa") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"QA\"")
        }
        getByName("release") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"RELEASE\"")
        }
    }
}

dependencies {
    implementation(projects.presentation)
    implementation(projects.core.navigation)
    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.analytics)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.profileinstaller)
    testImplementation(libs.junit)
}

baselineProfile {
    variants {
        create("release") {
            from(project(":macrobenchmark"))
        }
    }
}

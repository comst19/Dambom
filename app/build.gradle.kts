plugins {
    id("dambom.android.application")
    id("dambom.android.hilt")
}

apply(plugin = "androidx.baselineprofile")

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
            buildConfigField("String", "API_BASE_URL", "\"https://dev.example.invalid/\"")
        }
        getByName("qa") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"QA\"")
            buildConfigField("String", "API_BASE_URL", "\"https://qa.example.invalid/\"")
        }
        getByName("release") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"RELEASE\"")
            buildConfigField("String", "API_BASE_URL", "\"https://api.example.invalid/\"")
        }
    }
}

dependencies {
    add("baselineProfile", projects.benchmarks)
    implementation(projects.presentation)
    implementation(projects.core.navigation)
    implementation(projects.core.common)
    implementation(projects.core.data.repository)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.network)
    implementation(projects.core.coroutine)
    implementation(projects.core.analytics)
    implementation(libs.androidx.profileinstaller)
}

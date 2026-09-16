plugins {
    id("dambom.android.application")
    id("dambom.android.hilt")
    id("com.google.android.gms.oss-licenses-plugin")
    alias(libs.plugins.baselineprofile)
}

val releaseStoreFile = providers.environmentVariable("DAMBOM_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("DAMBOM_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("DAMBOM_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("DAMBOM_RELEASE_KEY_PASSWORD").orNull
val crashlyticsEnabled =
    providers.gradleProperty("dambom.crashlytics.enabled").map(String::toBooleanStrict).getOrElse(false)

if (crashlyticsEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")

    tasks.withType<com.google.gms.googleservices.GoogleServicesTask>().configureEach {
        onlyIf {
            name == "processGooglePlayReleaseGoogleServices" || name == "processOnestoreReleaseGoogleServices"
        }
    }
}

android {
    namespace = "com.comst19.dambom"
    defaultConfig {
        applicationId = "com.comst19.dambom"
        versionCode = 2
        versionName = "1.0.1"
        manifestPlaceholders["crashlyticsCollectionEnabled"] = false
    }

    buildFeatures { buildConfig = true }

    flavorDimensions += "store"
    productFlavors {
        create("googlePlay") {
            dimension = "store"
        }
        create("onestore") {
            dimension = "store"
            applicationIdSuffix = ".onestore"
        }
    }

    sourceSets.getByName("release") {
        baselineProfiles.srcDir("src/release/generated/baselineProfiles")
    }

    signingConfigs {
        if (
            releaseStoreFile != null &&
            releaseStorePassword != null &&
            releaseKeyAlias != null &&
            releaseKeyPassword != null
        ) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"DEBUG\"")
        }
        getByName("qa") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"QA\"")
        }
        getByName("release") {
            buildConfigField("String", "APP_ENVIRONMENT", "\"RELEASE\"")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = crashlyticsEnabled
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        if (crashlyticsEnabled) {
            configureEach {
                extensions.configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                    mappingFileUploadEnabled = name == "release"
                }
            }
        }
    }
}

dependencies {
    "releaseImplementation"(platform(libs.firebase.bom))
    "releaseImplementation"(libs.firebase.crashlytics)
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

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.comst19.dambom.macrobenchmark"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    testOptions.managedDevices.localDevices {
        create("pixel6Api35") {
            device = "Pixel 6"
            apiLevel = 35
            systemImageSource = "aosp"
        }
    }
}

val useConnectedDevices =
    providers
        .gradleProperty("dambom.baselineProfile.useConnectedDevices")
        .map(String::toBooleanStrict)
        .getOrElse(false)

baselineProfile {
    managedDevices.clear()
    if (useConnectedDevices) {
        useConnectedDevices = true
    } else {
        managedDevices += "pixel6Api35"
        useConnectedDevices = false
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.test.ext)
    implementation(libs.androidx.test.rules)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
}

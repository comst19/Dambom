import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { alias(libs.plugins.android.dynamic.feature) }

android {
    namespace = "com.comst19.dambom.translationruntime"
    compileSdk = 37
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    externalNativeBuild { ndkBuild { path = file("src/main/cpp/Android.mk") } }
    flavorDimensions += "store"
    productFlavors {
        create("googlePlay") { dimension = "store" }
        create("onestore") { dimension = "store" }
    }
    buildTypes {
        maybeCreate("qa").initWith(getByName("release"))
        getByName("release") { proguardFiles("proguard-rules.pro") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":app"))
    implementation(project(":feature:library"))
    implementation(libs.litertlm)
    implementation(libs.kotlinx.coroutines.android)
}

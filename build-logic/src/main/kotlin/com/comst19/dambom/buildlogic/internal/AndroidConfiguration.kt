package com.comst19.dambom.buildlogic.internal

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

internal fun ApplicationExtension.configureAndroid() {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging.resources.excludes += COMMON_RESOURCE_EXCLUDES
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
}

internal fun LibraryExtension.configureAndroid() {
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging.resources.excludes += COMMON_RESOURCE_EXCLUDES
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
    val releaseBuildType = buildTypes.getByName("release")
    buildTypes.maybeCreate("qa").apply {
        initWith(releaseBuildType)
    }
}

private val COMMON_RESOURCE_EXCLUDES =
    setOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/LICENSE.md",
        "META-INF/LICENSE-notice.md",
    )

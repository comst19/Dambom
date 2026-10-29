package com.comst19.dambom.buildlogic.internal

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

internal fun ApplicationExtension.configureAndroid() = configureAndroidDefaults()

internal fun LibraryExtension.configureAndroid() {
    configureAndroidDefaults()
    val releaseBuildType = buildTypes.getByName("release")
    buildTypes.maybeCreate("qa").apply {
        initWith(releaseBuildType)
    }
}

private fun CommonExtension.configureAndroidDefaults() {
    compileSdk = 37
    defaultConfig.apply {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions.apply {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions.apply {
        unitTests.isIncludeAndroidResources = true
    }
    packaging.resources.excludes += COMMON_RESOURCE_EXCLUDES
    lint.apply {
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }
}

private val COMMON_RESOURCE_EXCLUDES =
    setOf(
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/LICENSE.md",
        "META-INF/LICENSE-notice.md",
    )

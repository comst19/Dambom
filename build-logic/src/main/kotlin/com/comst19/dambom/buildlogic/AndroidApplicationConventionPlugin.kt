package com.comst19.dambom.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.comst19.dambom.buildlogic.internal.configureAndroid
import com.comst19.dambom.buildlogic.internal.configureJacoco
import com.comst19.dambom.buildlogic.internal.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("jacoco")
        extensions.configure<ApplicationExtension> {
            configureAndroid()
            defaultConfig.targetSdk = 36
            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                }
                val releaseBuildType = getByName("release") {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
                maybeCreate("qa").apply {
                    initWith(releaseBuildType)
                    applicationIdSuffix = ".qa"
                    signingConfig = signingConfigs.getByName("debug")
                }
            }
        }
        configureKotlin()
        configureJacoco()
    }
}

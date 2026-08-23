package com.comst19.dambom.buildlogic

import com.android.build.api.dsl.LibraryExtension
import com.comst19.dambom.buildlogic.internal.configureAndroid
import com.comst19.dambom.buildlogic.internal.configureJacoco
import com.comst19.dambom.buildlogic.internal.configureKotlin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        pluginManager.apply("jacoco")
        extensions.configure<LibraryExtension> {
            configureAndroid()
        }
        configureKotlin()
        configureJacoco()
    }
}

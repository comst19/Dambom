package com.comst19.dambom.buildlogic

import com.comst19.dambom.buildlogic.internal.configureJacoco
import com.comst19.dambom.buildlogic.internal.configureKotlin
import com.comst19.dambom.buildlogic.internal.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        pluginManager.apply("com.android.lint")
        pluginManager.apply("jacoco")
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
        configureKotlin()
        configureJacoco()
        val libs = extensions.libs
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
        }
    }
}

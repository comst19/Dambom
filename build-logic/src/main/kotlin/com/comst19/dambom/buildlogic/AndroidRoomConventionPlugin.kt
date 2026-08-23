package com.comst19.dambom.buildlogic

import com.comst19.dambom.buildlogic.internal.libs
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        val libs = extensions.libs
        dependencies {
            add("implementation", libs.findLibrary("room-runtime").get())
            add("implementation", libs.findLibrary("room-ktx").get())
            add("ksp", libs.findLibrary("room-compiler").get())
            add("androidTestImplementation", libs.findLibrary("room-testing").get())
        }
        extensions.configure<KspExtension> {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }
}

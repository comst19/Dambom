package com.comst19.dambom.buildlogic

import com.comst19.dambom.buildlogic.internal.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.dagger.hilt.android")
        pluginManager.apply("com.google.devtools.ksp")
        val libs = extensions.libs
        dependencies {
            add("implementation", libs.findLibrary("hilt-android").get())
            add("implementation", libs.findLibrary("error-prone-annotations").get())
            add("ksp", libs.findLibrary("hilt-compiler").get())
        }
    }
}

package com.comst19.dambom.buildlogic

import com.comst19.dambom.buildlogic.internal.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val libs = extensions.libs
        dependencies {
            add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
            add("testImplementation", libs.findLibrary("turbine").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("androidx-test-core").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-ext").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-runner").get())
            add("androidTestImplementation", libs.findLibrary("androidx-test-espresso-core").get())
        }
    }
}

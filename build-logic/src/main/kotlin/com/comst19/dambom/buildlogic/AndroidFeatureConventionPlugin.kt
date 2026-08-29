package com.comst19.dambom.buildlogic

import com.comst19.dambom.buildlogic.internal.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dambom.android.library")
        pluginManager.apply("dambom.android.compose")
        pluginManager.apply("dambom.android.hilt")
        pluginManager.apply("dambom.android.test")
        val libs = extensions.libs
        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:common-ui"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:navigation"))
            add("implementation", project(":core:navigation-contract"))
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-ktx").get())
            add("implementation", libs.findLibrary("androidx-navigation3-runtime").get())
            add("implementation", libs.findLibrary("hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("kotlinx-collections-immutable").get())
            add("testImplementation", project(":core:testing"))
        }
    }
}

package com.comst19.dambom.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("dambom.android.library")
        pluginManager.apply("dambom.kotlin.serialization")
        dependencies {
            add("api", project(":core:navigation"))
        }
    }
}

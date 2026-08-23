package com.comst19.dambom.buildlogic.internal

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

internal fun Project.configureJacoco() {
    extensions.configure<JacocoPluginExtension> {
        toolVersion = "0.8.15"
    }
}

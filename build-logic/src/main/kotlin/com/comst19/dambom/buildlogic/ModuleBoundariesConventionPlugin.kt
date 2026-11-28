package com.comst19.dambom.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault

class ModuleBoundariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target == target.rootProject) {
            "dambom.module.boundaries can only be applied to the root project."
        }
        val checkTask =
            target.tasks.register<CheckModuleBoundariesTask>("checkModuleBoundaries") {
                group = "verification"
                description = "Verifies declared project module dependency boundaries across production configurations."
            }
        target.gradle.projectsEvaluated {
            val encodedEdges =
                target.subprojects
                    .flatMap { subproject ->
                        subproject.configurations
                            .filter { isProductionDependencyConfiguration(it.name) }
                            .flatMap { configuration ->
                                configuration.dependencies
                                    .withType(ProjectDependency::class.java)
                                    .map { dependency ->
                                        ModuleDependencyEdge(
                                            source = subproject.path,
                                            configuration = configuration.name,
                                            target = dependency.path,
                                        ).encode()
                                    }
                            }
                    }.toSortedSet()
            checkTask.configure {
                dependencyEdges.set(encodedEdges)
            }
        }
    }
}

@DisableCachingByDefault(because = "Fast in-memory validation of declared module boundaries")
abstract class CheckModuleBoundariesTask : DefaultTask() {
    @get:Input
    abstract val dependencyEdges: SetProperty<String>

    @TaskAction
    fun check() {
        val edges = dependencyEdges.get().map(ModuleDependencyEdge::decode)
        val violations = findModuleBoundaryViolations(edges)
        if (violations.isNotEmpty()) {
            val details = violations.joinToString(separator = "\n") { " - ${it.format()}" }
            throw GradleException("Module boundary violations detected:\n$details")
        }
    }
}

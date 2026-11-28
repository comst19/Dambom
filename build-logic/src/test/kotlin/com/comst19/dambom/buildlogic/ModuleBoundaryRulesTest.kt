package com.comst19.dambom.buildlogic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleBoundaryRulesTest {
    @Test
    fun `production dependency configurations include base and product variants`() {
        val included =
            listOf(
                "api",
                "implementation",
                "compileOnly",
                "compileOnlyApi",
                "runtimeOnly",
                "debugImplementation",
                "releaseImplementation",
                "googlePlayDebugImplementation",
                "onestoreReleaseApi",
                "googlePlayReleaseRuntimeOnly",
                "debugCompileOnly",
                "releaseCompileOnlyApi",
            )
        included.forEach { config ->
            assertTrue("Expected $config to be included", isProductionDependencyConfiguration(config))
        }
    }

    @Test
    fun `test and tooling configurations are excluded`() {
        val excluded =
            listOf(
                "testImplementation",
                "testApi",
                "testRuntimeOnly",
                "testDebugImplementation",
                "debugTestImplementation",
                "androidTestImplementation",
                "androidTestGooglePlayDebugImplementation",
                "debugAndroidTestImplementation",
                "screenshotTestImplementation",
                "testFixturesImplementation",
                "debugTestFixturesApi",
                "debugCompileClasspath",
                "releaseRuntimeClasspath",
                "ksp",
                "kspDebug",
                "lintChecks",
                "detektPlugins",
            )
        excluded.forEach { config ->
            assertFalse("Expected $config to be excluded", isProductionDependencyConfiguration(config))
        }
    }

    @Test
    fun `allowed architectural dependencies produce no violations`() {
        val edges =
            listOf(
                ModuleDependencyEdge(":feature:detection:impl", "implementation", ":feature:detection:api"),
                ModuleDependencyEdge(":feature:detection:impl", "implementation", ":feature:home:api"),
                ModuleDependencyEdge(":feature:detection:impl", "implementation", ":core:domain"),
                ModuleDependencyEdge(":feature:detection:impl", "implementation", ":core:network"),
                ModuleDependencyEdge(":feature:library:api", "api", ":core:navigation"),
                ModuleDependencyEdge(":presentation", "implementation", ":feature:library:impl"),
                ModuleDependencyEdge(":presentation", "implementation", ":feature:library:api"),
                ModuleDependencyEdge(":presentation", "implementation", ":core:domain"),
                ModuleDependencyEdge(":app", "implementation", ":presentation"),
                ModuleDependencyEdge(":app", "implementation", ":core:data"),
                ModuleDependencyEdge(":core:data", "implementation", ":core:database"),
                ModuleDependencyEdge(":core:common-ui", "testImplementation", ":core:datastore"),
            )
        assertTrue(findModuleBoundaryViolations(edges).isEmpty())
    }

    @Test
    fun `feature impl cannot depend on data layer or other feature impl across variants`() {
        val edges =
            listOf(
                ModuleDependencyEdge(":feature:library:impl", "implementation", ":core:data"),
                ModuleDependencyEdge(":feature:library:impl", "debugImplementation", ":core:database"),
                ModuleDependencyEdge(":feature:library:impl", "googlePlayReleaseRuntimeOnly", ":core:datastore"),
                ModuleDependencyEdge(":feature:home:impl", "implementation", ":feature:web:impl"),
                ModuleDependencyEdge(":feature:web:impl", "releaseImplementation", ":presentation"),
                ModuleDependencyEdge(":feature:web:impl", "implementation", ":app"),
            )
        val violations = findModuleBoundaryViolations(edges)
        assertEquals(edges.size, violations.size)
        assertEquals(edges.map { it.target }.toSet(), violations.map { it.edge.target }.toSet())
    }

    @Test
    fun `feature api cannot depend on impl or infrastructure modules`() {
        val edges =
            listOf(
                ModuleDependencyEdge(":feature:library:api", "api", ":feature:library:impl"),
                ModuleDependencyEdge(":feature:library:api", "implementation", ":core:data"),
                ModuleDependencyEdge(":feature:library:api", "implementation", ":core:network"),
                ModuleDependencyEdge(":feature:library:api", "debugImplementation", ":presentation"),
            )
        val violations = findModuleBoundaryViolations(edges)
        assertEquals(edges.size, violations.size)
    }

    @Test
    fun `presentation cannot depend on data layer or network or app`() {
        val edges =
            listOf(
                ModuleDependencyEdge(":presentation", "implementation", ":core:data"),
                ModuleDependencyEdge(":presentation", "debugImplementation", ":core:database"),
                ModuleDependencyEdge(":presentation", "releaseImplementation", ":core:datastore"),
                ModuleDependencyEdge(":presentation", "implementation", ":core:network"),
                ModuleDependencyEdge(":presentation", "implementation", ":app"),
            )
        val violations = findModuleBoundaryViolations(edges)
        assertEquals(edges.size, violations.size)
    }

    @Test
    fun `core domain cannot declare direct project dependencies`() {
        val edges =
            listOf(
                ModuleDependencyEdge(":core:domain", "implementation", ":core:data"),
                ModuleDependencyEdge(":core:domain", "debugImplementation", ":core:common"),
                ModuleDependencyEdge(":core:domain", "testImplementation", ":core:testing"),
            )
        val violations = findModuleBoundaryViolations(edges)
        assertEquals(2, violations.size)
        assertEquals(listOf(":core:common", ":core:data"), violations.map { it.edge.target })
    }

    @Test
    fun `edge encoding round-trips cleanly`() {
        val edge = ModuleDependencyEdge(":feature:library:impl", "debugImplementation", ":core:data")
        assertEquals(edge, ModuleDependencyEdge.decode(edge.encode()))
    }
}

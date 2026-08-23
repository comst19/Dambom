pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Dambom"

include(":app")
include(":presentation")
include(":benchmarks")
include(":core:common")
include(":core:common-ui")
include(":core:coroutine")
include(":core:designsystem")
include(":core:navigation")
include(":core:navigation-contract")
include(":core:domain")
include(":core:data:repository")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:analytics")
include(":core:testing")
include(":core:screenshot-testing")
include(":ui-test-manifest")
include(":feature:home")
include(":feature:detection")
include(":feature:downloads")
include(":feature:library")
include(":feature:settings")
include(":feature:web")

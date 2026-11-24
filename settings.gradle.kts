pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.google.android.gms.oss-licenses-plugin") {
                useModule("com.google.android.gms:oss-licenses-plugin:0.13.0")
            }
        }
    }
}

plugins {
    // 필요한 JDK가 로컬에 없을 때 자동 다운로드할 수 있도록 resolver 등록 (Java 버전은 기존 toolchain 설정 유지)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
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
include(":macrobenchmark")
include(":core:common")
include(":core:common-ui")
include(":core:coroutine")
include(":core:designsystem")
include(":core:navigation")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:analytics")
include(":core:testing")
include(":core:screenshot-testing")
include(":ui-test-manifest")
include(":feature:home:api")
include(":feature:home:impl")
include(":feature:detection:api")
include(":feature:detection:impl")
include(":feature:downloads:api")
include(":feature:downloads:impl")
include(":feature:library:api")
include(":feature:library:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
include(":feature:web:api")
include(":feature:web:impl")
include(":translationruntime")

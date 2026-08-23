plugins {
    `kotlin-dsl`
}

group = "com.comst19.dambom.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.hilt.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dambom.android.application"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "dambom.android.library"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "dambom.android.compose"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "dambom.android.feature"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidHilt") {
            id = "dambom.android.hilt"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "dambom.android.room"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidRoomConventionPlugin"
        }
        register("androidTest") {
            id = "dambom.android.test"
            implementationClass = "com.comst19.dambom.buildlogic.AndroidTestConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "dambom.kotlin.library"
            implementationClass = "com.comst19.dambom.buildlogic.KotlinLibraryConventionPlugin"
        }
        register("kotlinSerialization") {
            id = "dambom.kotlin.serialization"
            implementationClass = "com.comst19.dambom.buildlogic.KotlinSerializationConventionPlugin"
        }
    }
}

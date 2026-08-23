plugins {
    id("dambom.kotlin.library")
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(libs.hilt.core)
    implementation(libs.kotlinx.coroutines.core)
    ksp(libs.hilt.compiler)
}

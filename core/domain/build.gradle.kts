plugins {
    id("dambom.kotlin.library")
}

dependencies {
    implementation(libs.hilt.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(projects.core.testFixture)
}

plugins { id("dambom.kotlin.library") }

dependencies {
    api(projects.core.domain)
    implementation(libs.kotlinx.coroutines.core)
}

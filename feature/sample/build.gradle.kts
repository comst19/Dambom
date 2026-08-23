plugins { id("dambom.android.feature") }

android { namespace = "com.comst19.dambom.feature.sample" }

dependencies {
    testImplementation(projects.core.testFixture)
    implementation(libs.kotlinx.collections.immutable)
}

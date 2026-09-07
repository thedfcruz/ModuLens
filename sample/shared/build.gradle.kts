plugins {
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
}

kotlin {
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

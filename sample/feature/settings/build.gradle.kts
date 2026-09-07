plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dfcruz.modulens.sample.feature.settings"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }
}

dependencies {
    implementation(project(":sample:core:domain"))
    implementation(project(":sample:core:ui"))
}

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dfcruz.modulens.sample.core.ui"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 29
    }
}

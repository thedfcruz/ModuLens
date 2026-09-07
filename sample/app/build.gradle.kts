plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dfcruz.modulens.sample"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dfcruz.modulens.sample"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(project(":sample:feature:dashboard"))
    implementation(project(":sample:feature:settings"))
    implementation(project(":sample:core:data"))

    // Deliberately reachable through the feature modules, for ModuLens sample reporting.
    implementation(project(":sample:core:domain"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}

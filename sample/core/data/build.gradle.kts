plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

dependencies {
    implementation(project(":sample:core:domain"))
    implementation(project(":sample:core:network"))
    implementation(project(":sample:shared"))
}

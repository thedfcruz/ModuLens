plugins {
    `kotlin-dsl`
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

gradlePlugin {
    plugins {
        create("modulens") {
            id = "com.dfcruz.modulens"
            displayName = "ModuLens"
            description = "Module analysis"
            implementationClass = "com.dfcruz.modulens.ModuleAnalysePlugin"
        }
    }
}

plugins {
    `kotlin-dsl`
    alias(libs.plugins.maven.publish)
}

dependencies {
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

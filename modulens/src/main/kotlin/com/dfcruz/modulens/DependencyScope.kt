package com.dfcruz.modulens

enum class DependencyScope(
    val displayName: String,
    val optionName: String,
) {
    PRODUCTION("Production", "production"),
    DEBUG("Debug", "debug"),
    RELEASE("Release", "release"),
    UNIT_TEST("Unit test", "unitTest"),
    ANDROID_TEST("Android test", "androidTest"),
    ;

    companion object {
        fun fromConfiguration(configurationName: String): DependencyScope? = when {
            configurationName.startsWith("androidTest") ||
                configurationName.contains("InstrumentedTest") -> ANDROID_TEST

            configurationName.startsWith("test") ||
                configurationName.matches(Regex(".+Test(Api|Implementation|CompileOnly|RuntimeOnly)$")) -> UNIT_TEST

            configurationName.startsWith("debug") -> DEBUG
            configurationName.startsWith("release") -> RELEASE
            configurationName in setOf("api", "implementation", "compileOnly", "runtimeOnly") ||
                configurationName.matches(Regex(".+Main(Api|Implementation|CompileOnly|RuntimeOnly)$")) -> PRODUCTION

            else -> null
        }

        fun fromOption(option: String): DependencyScope =
            entries.firstOrNull { it.optionName == option }
                ?: error("Unknown dependency scope '$option'.")
    }
}

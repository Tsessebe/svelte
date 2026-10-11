pluginManagement {
    repositories {
        maven("https://repo1.maven.org/maven2")
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm" -> useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.intellij.platform" -> useModule("org.jetbrains.intellij.platform:intellij-platform-gradle-plugin:${requested.version}")
            }
        }
    }
}
rootProject.name = "svelte-plugin"

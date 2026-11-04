pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mesma fonte da dependência oficial EmbroideryIO usada no app Android.
        maven { url = uri("https://jitpack.io") }
    }
}
rootProject.name = "BrotherMatrizesDesktop"

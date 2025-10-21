pluginManagement {
    repositories {
        google()                  // 🔥 Required for Firebase
        mavenCentral()            // 🔥 Required for Kotlin and AndroidX
        gradlePluginPortal()      // 🔥 For Gradle plugins
        maven { url = uri("https://jitpack.io") } // 🔥 For MPAndroidChart
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") } // 👈 add this line
    }
}



rootProject.name = "CrabTrack"
include(":app")

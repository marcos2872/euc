rootProject.name = "euc"

// Plugins (AGP, Kotlin) vêm do portal + repositório Google.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Resolução de dependências centralizada: módulos não declaram repositórios.
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
    }
}

// Núcleo puro (Kotlin/JVM, sem Android) — testável sem SDK
include(":core:protocol")

// Transporte Bluetooth LE (Android) — implementa a porta WheelLink
include(":core:ble")

// App Android (Jetpack Compose)
include(":app")

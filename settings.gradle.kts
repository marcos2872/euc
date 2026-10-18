rootProject.name = "euc"

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

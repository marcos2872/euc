// Build raiz — declara todos os plugins usados pelos módulos (sem aplicar),
// para que as versões sejam resolvidas uma única vez.
buildscript {
    dependencies {
        // AGP 9 tem Kotlin embutido e depende do KGP 2.2.10; subimos para 2.4.21
        // (mesma versão do catálogo) para alinhar com as libs de 2026 — Vico usa
        // stdlib 2.4.10 e Haze 2.4.20. O `buildscript` roda antes do catálogo,
        // então a versão é repetida aqui de propósito.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}

// Build raiz — declara todos os plugins usados pelos módulos (sem aplicar),
// para que as versões sejam resolvidas uma única vez.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
}

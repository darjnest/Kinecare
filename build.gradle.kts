// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Todas las versiones de plugin se resuelven UNA sola vez aqui (apply false).
// Los modulos hijos los aplican sin version (id("...")) para evitar el
// conflicto "plugin already on the classpath with an unknown version" que
// tira AGP 9 (built-in Kotlin) cuando dos proyectos piden la misma version
// de forma explicita en momentos distintos de la configuracion.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
}

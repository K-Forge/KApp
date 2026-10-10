// Root build file. Plugins are declared here and applied in :app, which is the whole project for
// now - KApp is a single-module app until a second module earns its keep.
//
// There is no Kotlin Android plugin: AGP 9 compiles Kotlin itself. The Compose compiler and the
// serialization plugins are still their own, because they ship with Kotlin rather than with AGP.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

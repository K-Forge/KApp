// Root build file. Plugins are declared here and applied in :app, which is the whole project for
// now - KApp is a single-module app until a second module earns its keep.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

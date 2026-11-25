// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // AGP 9 built-in Kotlin ships an older KGP. COUI 2.1.0 needs Kotlin 2.4 and the Compose compiler
    // plugin must match the KGP version, so put KGP 2.4.10 on the build classpath (not applied).
    alias(libs.plugins.kotlin.android) apply false
}

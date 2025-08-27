// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// build.gradle (Nivel de Proyecto)

buildscript {
    repositories {
        google() // <-- ASEGÚRATE DE QUE ESTÉ AQUÍ
        mavenCentral()
    }
    // ... otras configuraciones ...
}

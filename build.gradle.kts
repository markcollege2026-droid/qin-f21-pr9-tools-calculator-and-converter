// Root build file of the Qin F21 toolbox.
//
// The project is split into small modules so that the pure logic (conversion, calculators)
// stays testable on a plain JVM, while the Android specific parts (hardware access, data
// storage, design system) live in their own modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

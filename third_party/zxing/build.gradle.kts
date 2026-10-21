// Pure-Java Android library wrapping the vendored ZXing core.
//
// The sources stay in the auditable location `third_party/zxing-core/` (a sibling
// directory) and are compiled HERE, by javac, into this module's classes. The app
// then depends on this module as a normal project dependency, so the Kotlin
// compiler sees `com.google.zxing.*` through the ordinary classpath - no
// source-dir sharing between compilers is involved.
//
// (Sharing the directory with the app's Kotlin compilation turned out to be
// unreliable: KGP 1.8.22 neither resolves .java files from an extra AGP `java`
// srcDir nor tolerates them in its own `kotlin` srcDirs for Android modules.)
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.google.zxing"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    sourceSets {
        getByName("main") {
            java.srcDir("../zxing-core")
        }
    }
}

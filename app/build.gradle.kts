plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.qin.f21.toolbox"
    compileSdk = 30

    defaultConfig {
        applicationId = "com.qin.f21.toolbox"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = false
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            // The ZXing core is vendored in the repository so that the app can be built
            // and audited without downloading anything. javac compiles the .java files
            // from these roots.
            java.srcDir("../third_party/zxing-core")
        }
    }

    packagingOptions {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
    }
}

// The vendored ZXing is plain Java. javac compiles it (see android.sourceSets.main.java
// above); the Kotlin compiler, however, only resolves symbols from its own `kotlin`
// source roots, so the same directory must be registered here for CameraActivity.kt's
// `com.google.zxing.*` imports to resolve. kotlinc parses the .java files for symbol
// resolution but does not emit class files for them (javac does).
kotlin {
    sourceSets {
        getByName("main") {
            kotlin.srcDir("../third_party/zxing-core")
        }
    }
}

// CI diagnostic: prints exactly which source directories (and whether they exist)
// are registered on each compiler, plus how many of the resolved compileDebugKotlin
// sources come from the vendored ZXing tree. Output is prefixed so the CI problem
// matcher can surface it as check-run annotations. All access is reflective so the
// snippet does not depend on the exact AGP/KGP/Gradle API shape of the accessors.
tasks.register("printSources") {
    doLast {
        try {
            val diag = { msg: String -> println("GRADLE_ERROR DIAG $msg") }
            fun existsOf(d: Any?): Boolean {
                if (d == null) return false
                val obj = d
                return try {
                    obj.javaClass.getMethod("exists").invoke(obj) as? Boolean ?: false
                } catch (e: Exception) {
                    try {
                        (obj.javaClass.getMethod("getAsFile").invoke(obj) as? java.io.File)?.exists() ?: false
                    } catch (e2: Exception) { false }
                }
            }
            fun srcDirsOf(name: String, set: Any?) {
                if (set == null) { diag("$name: (not present)"); return }
                val s = set
                val v = runCatching { s.javaClass.getMethod("getSrcDirs").invoke(s) }.getOrNull()
                if (v is Iterable<*>) {
                    var count = 0
                    v.forEach { d -> count++; diag("$name: $d (exists=${existsOf(d)})") }
                    if (count == 0) diag("$name: (no registered dirs)")
                } else {
                    diag("$name: (getSrcDirs unavailable; type=${s.javaClass.name})")
                }
            }
            val androidMain = project.extensions
                .findByType(com.android.build.gradle.AppExtension::class.java)
                ?.sourceSets?.getByName("main")
            srcDirsOf("android main.java", androidMain?.java)
            srcDirsOf("android main.kotlin", androidMain?.kotlin)
            val kotlinMain = project.extensions
                .findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)
                ?.sourceSets?.getByName("main")
            srcDirsOf("kotlin main.kotlin", kotlinMain?.kotlin)
            val compileKt = project.tasks.getByName("compileDebugKotlin")
            val getter = compileKt.javaClass.methods
                .firstOrNull { it.name == "getSources" && it.parameterCount == 0 }
            val files: Set<java.io.File> = when (val v = getter?.invoke(compileKt)) {
                is org.gradle.api.file.FileCollection -> v.files
                is Iterable<*> -> v.mapNotNull { it as? java.io.File }.toSet()
                else -> { diag("compileDebugKotlin (${compileKt.javaClass.name}): no readable sources property"); emptySet() }
            }
            val zx = files.filter { it.path.contains("zxing") }
            diag("compileDebugKotlin sources: ${files.size} files total, ${zx.size} from vendored zxing")
            zx.sortedBy { it.path }.take(5).forEach { diag("  zxing: ${it.path}") }
            diag("DIAG done")
        } catch (t: Throwable) {
            println("GRADLE_ERROR DIAG ERROR: ${t.javaClass.name}: ${t.message}")
            t.stackTrace.take(5).forEach { println("GRADLE_ERROR DIAG at $it") }
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:conversion"))
    implementation(project(":core:calc"))
    implementation(project(":core:hardware"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
}

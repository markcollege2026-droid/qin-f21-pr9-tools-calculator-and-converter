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

    packagingOptions {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
    }

    lint {
        // ExpiredTargetSdkVersion (fatal) enforces the Google Play policy that apps
        // target API 31+. This app is not distributed through Play - it is sideloaded
        // onto the Qin F21 Pro, an Android 11 (API 30) appliance - so targetSdk 30 is
        // the correct value by design and the policy check does not apply.
        disable += "ExpiredTargetSdkVersion"
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
                val dirs = if (v is Iterable<*>) v.toList() else listOf("<no getSrcDirs on ${s.javaClass.name}>")
                // One line per set: the registered roots with existence, to keep the
                // annotation budget free for actual build errors.
                diag("$name: " + dirs.joinToString("; ") { d -> "$d (exists=${existsOf(d)})" })
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
            fun filesOf(task: Any, prop: String): Set<java.io.File> {
                val g = task.javaClass.methods.firstOrNull { it.name == "get$prop" && it.parameterCount == 0 }
                return when (val v = g?.invoke(task)) {
                    is org.gradle.api.file.FileCollection -> v.files
                    is Iterable<*> -> v.mapNotNull { it as? java.io.File }.toSet()
                    else -> emptySet()
                }
            }
            val srcFiles = filesOf(compileKt, "Sources")
            val cpFiles = filesOf(compileKt, "Classpath")
            val zxSrc = srcFiles.filter { it.path.contains("zxing") }
            val zxCp = cpFiles.filter { it.path.contains("zxing") }
            diag("compileDebugKotlin sources: ${srcFiles.size} files, ${zxSrc.size} zxing")
            diag("compileDebugKotlin classpath: ${cpFiles.size} entries, ${zxCp.size} zxing -> " + zxCp.map { it.name }.distinct().joinToString(", "))
            diag("DIAG done")
        } catch (t: Throwable) {
            println("GRADLE_ERROR DIAG ERROR: ${t.javaClass.name}: ${t.message}")
            t.stackTrace.take(5).forEach { println("GRADLE_ERROR DIAG at $it") }
        }
    }
}

dependencies {
    // Vendored ZXing core, compiled from source by the :third_party:zxing Java
    // library (see its build file). Comes in as a normal classpath dependency.
    implementation(project(":third_party:zxing"))
    implementation(project(":core:common"))
    implementation(project(":core:conversion"))
    implementation(project(":core:calc"))
    implementation(project(":core:hardware"))
    implementation(project(":core:data"))
    implementation(project(":core:design"))
}

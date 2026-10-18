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
            java.srcDir("../../third_party/zxing-core")
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
            kotlin.srcDir("../../third_party/zxing-core")
        }
    }
}

// CI diagnostic: prints exactly which source directories (and whether they exist)
// are registered on each compiler, plus how many of the resolved compileDebugKotlin
// sources come from the vendored ZXing tree. Output is prefixed so the CI problem
// matcher can surface it as check-run annotations.
tasks.register("printSources") {
    doLast {
        try {
            val diag = { msg: String -> println("GRADLE_ERROR DIAG $msg") }
            project.extensions.findByType(com.android.build.gradle.AppExtension::class.java)
                ?.sourceSets?.getByName("main")?.let { ss ->
                    ss.java.srcDirs.forEach { d -> diag("android main.java: ${d.absolutePath} (exists=${d.exists()})") }
                    ss.kotlin.srcDirs.forEach { d -> diag("android main.kotlin: ${d.absolutePath} (exists=${d.exists()})") }
                }
            project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java)
                ?.sourceSets?.getByName("main")?.let { ss ->
                    ss.kotlin.srcDirs.forEach { d -> diag("kotlin main.kotlin: ${d.absolutePath} (exists=${d.exists()})") }
                }
            val compileKt = project.tasks.getByName("compileDebugKotlin")
            val files: Set<java.io.File> = when (compileKt) {
                is org.gradle.api.tasks.SourceTask -> compileKt.sources.files
                else -> {
                    val getter = compileKt.javaClass.methods.firstOrNull { it.name == "getSources" && it.parameterCount == 0 }
                    when (val v = getter?.invoke(compileKt)) {
                        is org.gradle.api.file.FileCollection -> v.files
                        else -> { diag("compileDebugKotlin is ${compileKt.javaClass.name} (no usable sources property)"); emptySet() }
                    }
                }
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

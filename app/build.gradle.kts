plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.qin.f21.toolbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.qin.f21.toolbox"
        minSdk = 30          // Android 11, the oldest supported release
        targetSdk = 36       // Android 16, required by current Android and Google Play
        versionCode = 2
        versionName = "1.1.0"
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

    // `packagingOptions` was removed in AGP 8; this is the replacement block.
    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
    }

    lint {
        // Lint used to require this app (targetSdk 30) to be exempted from the Play
        // policy check. It targets API 36 now, so no exemption is needed any more.
        warningsAsErrors = false
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

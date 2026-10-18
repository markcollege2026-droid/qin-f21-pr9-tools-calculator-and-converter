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

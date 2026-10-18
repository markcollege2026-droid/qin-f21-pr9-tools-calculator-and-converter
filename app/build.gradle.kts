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
            // and audited without downloading anything.
            java.srcDir("../../third_party/zxing-core")
        }
    }

    packagingOptions {
        resources.excludes += setOf("META-INF/*.kotlin_module", "META-INF/DEPENDENCIES")
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

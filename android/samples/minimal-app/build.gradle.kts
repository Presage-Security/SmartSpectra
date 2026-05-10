apply(from = rootProject.file("samples/build_flavor_config.gradle"))

plugins {
    id("com.android.application")
}

val androidNdkVersion = rootProject.extra["androidNdkVersion"] as String

extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
    namespace = "com.presagetech.smartspectra_minimal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.presagetech.smartspectra_minimal"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt")
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildToolsVersion = "36.1.0"
    ndkVersion = androidNdkVersion
    compileSdkMinor = 1
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("com.google.android.material:material:1.13.0")
}

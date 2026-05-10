apply(from = rootProject.file("samples/build_flavor_config.gradle"))

plugins {
    id("com.android.application")
}

val androidNdkVersion = rootProject.extra["androidNdkVersion"] as String

extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
    namespace = "com.presagetech.smartspectra_example"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.presagetech.smartspectra_example"
        minSdk = 28
        targetSdk = 34
        versionCode = 4
        versionName = "4.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules-debug.pro"
            )
            isJniDebuggable = true
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.camera:camera-core:1.6.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
}

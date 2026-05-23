apply(from = rootProject.file("samples/build_flavor_config.gradle"))

plugins {
    id("com.android.application")
}

val androidNdkVersion = rootProject.extra["androidNdkVersion"] as String
val minimalAppApiKey = providers.gradleProperty("minimalAppApiKey")
    .orElse(providers.environmentVariable("SMARTSPECTRA_API_KEY"))
    .orElse("")

fun String.asBuildConfigString(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

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

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "SMARTSPECTRA_API_KEY",
                "\"${minimalAppApiKey.get().asBuildConfigString()}\"",
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField("String", "SMARTSPECTRA_API_KEY", "\"\"")
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
    implementation("androidx.camera:camera-view:1.6.0")
    implementation("com.google.android.material:material:1.13.0")

    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

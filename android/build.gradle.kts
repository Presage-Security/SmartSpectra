plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "com.presagetech"

extra["androidNdkVersion"] = System.getenv("ANDROID_NDK_FULL_VERSION") ?: "29.0.14206865"

// AGP 9's built-in Kotlin compiles .kt files but does not register the
// prepareKotlinBuildScriptModel task that Android Studio invokes during Gradle
// sync to warm the Kotlin DSL build-script model for newly-added modules.
// Verified missing on AGP 9.1.1 and 9.2.0. Register a no-op stub so sync
// succeeds; once AGP registers the task itself, this block can be removed.
subprojects {
    tasks.register("prepareKotlinBuildScriptModel")
}

nexusPublishing {
    // We are using "Publishing By Using the Portal OSSRH Staging API"
    // more details: https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
            // Credentials should be set via environment variables or gradle.properties
            // ORG_GRADLE_PROJECT_sonatypeUsername and ORG_GRADLE_PROJECT_sonatypePassword
        }
    }
}

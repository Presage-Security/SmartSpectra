plugins {
    id("com.android.application") version "9.2.1" apply false
    id("com.android.library") version "9.2.1" apply false
    id("org.jetbrains.dokka") version "2.2.0" apply false
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = "com.presagetech"

extra["androidNdkVersion"] = System.getenv("ANDROID_NDK_FULL_VERSION") ?: "29.0.14206865"

// Dependency locking: a committed gradle.lockfile per module pins the fully
// resolved dependency tree, so builds are reproducible and the resolved
// versions are auditable by dependency scanners. Where no lockfile is present
// (e.g. a fresh checkout of the published samples), resolution proceeds
// normally. Regenerate after a dependency change with:
//   ./gradlew :sdk:dependencies :samples:demo-app:dependencies \
//             :samples:minimal-app:dependencies dependencies --write-locks
allprojects {
    dependencyLocking {
        lockAllConfigurations()
        // LENIENT: locked versions are enforced for every module that resolves,
        // but a configuration whose resolved set differs from the lock state
        // does not fail the build. Required because the samples' published-
        // artifact flavor resolves com.presagetech:smartspectra from the
        // snapshot feed / mavenLocal, whose transitive set is environment-
        // dependent (dev machine vs CI) — the default lock mode demands an
        // exact match in both directions and fails CI on that difference.
        lockMode.set(LockMode.LENIENT)
    }
}

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

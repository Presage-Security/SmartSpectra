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

    // Pin netty for AGP's Unified Test Platform (the androidTest runner). Two UTP
    // configurations drag in vulnerable netty versions, from two separate upstream chains:
    //   unified-test-platform-core -> com.google.testing.platform:core -> grpc 1.57.2 -> 4.1.93
    //   ...host-emulator-control -> com.android.tools.emulator:proto -> grpc 1.69.1 -> 4.1.110
    // Both carry GHSA-93wv-jw9v-4972 (HTTP/2 decompression direct-memory leak, fixed in
    // 4.1.136.Final) on top of ~200 older advisories. The dependency_policy job scans the
    // committed lockfiles with no `changes:` filter, so when that advisory was published it
    // blocked every MR in the repo at once.
    //
    // Why a force and not an upstream bump: there is no newer UTP to move to. UTP 32.3.1
    // (AGP 9.3.1, the newest AGP) resolves the same core:0.0.9-alpha04 — the newest version
    // Google has ever published — and the same two grpc versions as 32.2.1. grpc's own pin
    // lags too: 1.69.1 declares netty 4.1.110, which this advisory also covers. Google has to
    // move first, and they have not.
    //
    // Why a force and not a waiver in ci/security/dependency-policy.json: a waiver needs an
    // owner and an expiry, and check_waiver_expiry.py deliberately re-blocks MRs when the date
    // lapses. With no upstream fix in sight that is a recurring chore; this is one line.
    //
    // netty here is test-runner tooling only — nothing in the shipped SDK uses it. The force is
    // scoped to the unified-test-platform* configurations so it can never reach the SDK's own
    // runtime classpath if someone adds netty (or gRPC) to the SDK later.
    //
    // KNOWN FUTURE FAILURE: when Google finally ships UTP on a netty newer than 4.1.136.Final,
    // useVersion() will silently DOWNGRADE it to 4.1.136.Final. That fails loudly rather than
    // quietly — any advisory against 4.1.136 blocks every MR again, exactly as GHSA-93wv-jw9v-4972
    // did — but the fix at that point is to delete this block, not to raise the number. Removal
    // check: delete the block, regenerate the lockfiles (the --write-locks command above), and run
    // `bash ci/scripts/run_dependency_policy.sh`. If it passes, upstream is patched and this is dead
    // code. Also prune the netty entries it re-baselines in ci/security/dependency-policy.json.
    configurations.matching { it.name.startsWith("unified-test-platform") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") useVersion("4.1.136.Final")
        }
    }

    // Dokka 2.2.0 resolves jsoup 1.16.1 for HTML documentation generation. Pin the
    // fixed 1.23.1 release until Dokka updates its transitive dependency.
    configurations.matching { it.name.startsWith("dokkaHtmlGeneratorRuntimeResolver") }.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jsoup" && requested.name == "jsoup") useVersion("1.23.1")
        }
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

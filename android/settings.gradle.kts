pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven( url ="https://jitpack.io")
        maven( url = "https://central.sonatype.com/repository/maven-snapshots/")
        mavenLocal()
    }
}

rootProject.name = "SmartSpectra"
include(":samples")
include(":samples:demo-app")
include(":samples:minimal-app")

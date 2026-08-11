// The IP. Domain model + engines written once in commonMain, compiled to:
//   - JVM  -> consumed by :server as the verifier
//   - JS   -> consumed by :web for on-device (offline) computation
// A shared golden-vector suite (M0-07) binds both targets forever.
//
// commonMain must stay framework-free — no Spring, no JPA, no serialization
// framework. Enforced by the ArchUnit purity test in M0-06.

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())

    jvm()

    js(IR) {
        browser()
        binaries.library()
        // M7's core-bridge consumes these declarations.
        generateTypeScriptDefinitions()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

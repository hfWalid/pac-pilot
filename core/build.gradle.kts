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

        // ArchUnit analyses JVM bytecode, so the purity rules live in the JVM test source set.
        // They still guard commonMain: commonMain compiles into the JVM target's output, which is
        // what the rules import. See ArchitecturePurityTest for why that is sufficient.
        jvmTest.dependencies {
            implementation(project.dependencies.platform(libs.junit.bom))
            implementation(libs.archunit.junit5)
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
}

rootProject.name = "pac-pilot"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Modules are added by their own tasks:
//   core   -> M0-02 (Kotlin Multiplatform: JVM + JS)
//   server -> M0-03 (Spring Boot 3 / Java 21)
//   web    -> M0-04 (React + TypeScript PWA)

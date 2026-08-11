rootProject.name = "pac-pilot"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")

// Remaining modules are added by their own tasks:
//   server -> M0-03 (Spring Boot 3 / Java 21)
//   web    -> M0-04 (React + TypeScript PWA)

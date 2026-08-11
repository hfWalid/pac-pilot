rootProject.name = "pac-pilot"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":server")

// Remaining modules are added by their own tasks:
//   web -> M0-04 (React + TypeScript PWA)

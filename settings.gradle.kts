rootProject.name = "pac-pilot"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":server")
include(":web")

// rulepacks -> M6 (versioned, checksummed barème artifacts)

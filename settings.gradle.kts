rootProject.name = "pac-pilot"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":core")
include(":server")
include(":web")

// Versioned, checksummed barème artefacts (CLAUDE.md §4.4, §7). Landed at M6-02, which is what the
// note this replaces was waiting for.
include(":rulepacks")

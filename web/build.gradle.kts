// The PWA — the only client (CLAUDE.md §2, §5). No native build, no app store.
//
// This module owns no Kotlin source. It wraps the npm build so CI has one entry point
// (`./gradlew build`) and so the dependency on the core's JS target is declared rather than
// assumed: `npm install` resolves `pac-pilot-core` from core/build/dist/js/productionLibrary,
// which only exists once :core:jsProductionLibraryDistribution has run.

plugins {
    base
}

val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

// Referenced by path as a string so Gradle resolves it lazily — :core is not yet evaluated
// while :web is being configured. Note the exact task name: `:core:jsProductionLibraryDistribution`
// appears to work on the command line only because Gradle abbreviation-matches it.
val coreJsLibraryTask = ":core:jsBrowserProductionLibraryDistribution"
val coreJsLibraryDir = project(":core").layout.buildDirectory.dir("dist/js/productionLibrary")

val npmInstall by tasks.registering(Exec::class) {
    description = "Installs web dependencies, including the core's JS library."
    group = "build"

    dependsOn(coreJsLibraryTask)

    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.dir(coreJsLibraryDir)
    outputs.dir(layout.projectDirectory.dir("node_modules"))

    workingDir = projectDir
    commandLine(npmCommand, "install")
}

val npmBuild by tasks.registering(Exec::class) {
    description = "Type-checks and bundles the PWA for production."
    group = "build"

    dependsOn(npmInstall)

    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.file(layout.projectDirectory.file("index.html"))
    inputs.dir(layout.projectDirectory.dir("public"))
    // The core library must be an input here too, not only on npmInstall: node_modules symlinks
    // to it, so a core-only change leaves this task up-to-date and silently ships a stale bundle.
    inputs.dir(coreJsLibraryDir)
    outputs.dir(layout.projectDirectory.dir("dist"))

    workingDir = projectDir
    commandLine(npmCommand, "run", "build")
}

tasks.assemble {
    dependsOn(npmBuild)
}

tasks.clean {
    delete(layout.projectDirectory.dir("dist"))
}

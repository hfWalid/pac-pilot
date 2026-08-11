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
val nodeCommand = if (System.getProperty("os.name").startsWith("Windows")) "node.exe" else "node"

// Vite runs on the ambient Node, while Kotlin/JS provisions its own (pinned to the same .nvmrc
// value in the root build). This task is what keeps the two from silently diverging: without it,
// the golden vectors and the shipped bundle could execute on different runtimes.
val verifyNodeToolchain by tasks.registering {
    description = "Fails if the ambient Node does not match the version pinned in .nvmrc."
    group = "verification"

    val pinned = rootProject.extra["pinnedNodeVersion"] as String
    val nvmrc = rootProject.layout.projectDirectory.file(".nvmrc")
    val execOps = providers.exec {
        commandLine(nodeCommand, "--version")
        isIgnoreExitValue = true
    }

    inputs.file(nvmrc)

    doLast {
        val actual = execOps.standardOutput.asText.get().trim().removePrefix("v")
        if (actual != pinned) {
            throw GradleException(
                """
                Node version mismatch: found $actual, .nvmrc pins $pinned.

                The build uses one Node version everywhere — Kotlin/JS provisions $pinned to run the
                golden vectors, so the PWA must be bundled with the same runtime.

                Fix: run `nvm use` in the repository root (or install Node $pinned).
                """.trimIndent(),
            )
        }
    }
}

// Referenced by path as a string so Gradle resolves it lazily — :core is not yet evaluated
// while :web is being configured. Note the exact task name: `:core:jsProductionLibraryDistribution`
// appears to work on the command line only because Gradle abbreviation-matches it.
val coreJsLibraryTask = ":core:jsBrowserProductionLibraryDistribution"
val coreJsLibraryDir = project(":core").layout.buildDirectory.dir("dist/js/productionLibrary")

val npmInstall by tasks.registering(Exec::class) {
    description = "Installs web dependencies from the lockfile, including the core's JS library."
    group = "build"

    dependsOn(verifyNodeToolchain, coreJsLibraryTask)

    inputs.file(layout.projectDirectory.file("package.json"))
    // The lockfile is a tracked source file and must invalidate this task: a lockfile-only change
    // (a dependency bump, `npm audit fix`) would otherwise leave node_modules stale but up-to-date.
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    inputs.dir(coreJsLibraryDir)
    outputs.dir(layout.projectDirectory.dir("node_modules"))

    workingDir = projectDir
    // `npm ci`, not `npm install`: it installs exactly what the lockfile pins and never rewrites it,
    // so a build can neither drift from the lockfile nor dirty the working tree. It also wipes
    // node_modules first, which makes the outputs declaration above exact rather than approximate.
    commandLine(npmCommand, "ci")
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

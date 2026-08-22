package fr.pacpilot.core

/**
 * JS-only façade. Kotlin types are not all directly consumable from TypeScript, so the JS target
 * exposes a thin `@JsExport` surface over `commonMain`.
 *
 * Keep this file glue-only. Anything with behaviour belongs in `commonMain`, or the
 * "one source of truth, two targets" property (ARCHITECTURE #3) quietly erodes.
 */
@JsExport
@OptIn(ExperimentalJsExport::class)
object CoreFacade {

    fun identify(): String = CoreInfo.identify()
}

package fr.pacpilot.server.catalog.api;

import fr.pacpilot.core.shared.DepartementClimate;
import java.util.Optional;

/**
 * The Catalog context's climate surface — where a département's outdoor design temperature is
 * resolved.
 *
 * <p>This is the boundary the core describes: an adapter looks the département up, and
 * {@code InputsSnapshot} records the <b>resolved</b> temperature so the study stays reproducible
 * even after this table is corrected. The engine never reaches out to look anything up.
 *
 * <p>Returns empty when no row exists, which is the normal case today — {@code V6} seeds no
 * climate rows at all, because no verified source exists for them. A caller must refuse to compute
 * rather than substitute a default: a plausible base temperature silently changes every heat load.
 */
public interface ClimateReference {

    Optional<DepartementClimate> forDepartement(String departementCode);
}

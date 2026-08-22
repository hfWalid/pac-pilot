package fr.pacpilot.server.catalog.api;

import fr.pacpilot.core.shared.PowerKw;
import java.util.List;
import java.util.Optional;

/** The Catalog context's product surface. */
public interface ProductCatalogue {

    Optional<CatalogProduct> findById(String id);

    /**
     * Machines whose power at −7 °C falls inside the recommended band of a study.
     *
     * <p>The band, not the bare heat load: under-sizing leaves a cold client in February and
     * over-sizing short-cycles the machine to an early death, and the two margins are asymmetric by
     * design (M2-04). Selecting on the load alone would ignore that entirely.
     */
    List<CatalogProduct> withinBand(PowerKw minimum, PowerKw maximum);
}

package fr.pacpilot.server.catalog.api;

import fr.pacpilot.core.dimensioning.model.EmitterType;
import fr.pacpilot.core.shared.PowerKw;
import java.util.Set;

/**
 * One machine in the catalogue, as another context sees it.
 *
 * <p>Read-mostly reference data. A devis never holds one of these — it holds a
 * {@code ProductSnapshot} copied at quote time, because a discontinued model or a revised price
 * must not rewrite a document already in a client's file.
 *
 * <p><b>No price here, deliberately.</b> A price belongs to a devis at a moment, not to a machine:
 * it is negotiated, it varies by installer and by job, and {@code ProductSnapshot.priceAtQuoteTime}
 * is where the one that was actually quoted is recorded.
 *
 * <p>{@code isProvisional} is not decoration. Every row seeded today is synthetic, and a caller
 * putting one on a devis is doing something the catalogue cannot back.
 */
public record CatalogProduct(
        String id,
        String brand,
        String model,
        PowerKw powerAtMinusSevenC,
        Set<EmitterType> compatibleEmitters,
        String source) {

    public boolean isProvisional() {
        return source.startsWith("SOURCE_TBD");
    }
}

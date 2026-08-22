package fr.pacpilot.server.dossier.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * The Dossier context's HTTP shapes.
 *
 * <p>Owned by the web adapter and never shared with the domain. The records here look almost
 * identical to {@code Client} and {@code Site} today, and that duplication is the point: the moment
 * the API needs a field the domain does not have — or has to keep one the domain drops — they
 * diverge, and nothing about that has to reach inward.
 */
final class DossierDtos {

    private DossierDtos() {}

    /**
     * @param id client-supplied. Aggregates are born offline in a cellar ({@code CLAUDE.md} §4.3),
     *     so the device owns identity and the server never mints one.
     */
    record CreateClientRequest(
            UUID id,
            UUID installerId,
            String firstName,
            String lastName,
            String email,
            String phone) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ClientResponse(
            UUID id, UUID installerId, String firstName, String lastName, String email, String phone) {}

    record CreateSiteRequest(
            UUID id,
            UUID clientId,
            String addressLine,
            String postcode,
            String commune,
            String departementCode,
            BigDecimal latitude,
            BigDecimal longitude,
            int surfaceCentiSquareMetres,
            int ceilingHeightCentimetres,
            String constructionPeriod,
            String insulationLevel,
            String ventilationType,
            String emitterType,
            int electricalSupplyKva) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SiteResponse(
            UUID id,
            UUID clientId,
            String addressLine,
            String postcode,
            String commune,
            String departementCode,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean geocoded) {}
}

package fr.pacpilot.server.dossier.adapter.out.persistence;

import fr.pacpilot.server.dossier.domain.Client;
import fr.pacpilot.server.dossier.domain.DwellingObservations;
import fr.pacpilot.server.dossier.domain.Site;
import fr.pacpilot.server.dossier.domain.SiteAddress;
import java.util.Optional;

/**
 * The only class that knows both shapes.
 *
 * <p>Explicit and hand-written rather than reflective. A mapping framework would make the two
 * models look interchangeable, which is the belief this whole arrangement exists to prevent — and
 * when the entity grows a column the domain has no opinion about, an explicit mapper is a
 * compile error rather than a silently copied field.
 *
 * <p>{@code null} in the database becomes {@link Optional#empty()} here and nowhere further in.
 * ADR-0014 hard-deletes contact details on erasure, so an absent email is an expected state that
 * the domain should have to acknowledge rather than trip over.
 */
final class DossierMapper {

    private DossierMapper() {}

    static ClientEntity toEntity(Client client) {
        return new ClientEntity(
                client.id(),
                client.installerId(),
                client.firstName(),
                client.lastName(),
                client.email().orElse(null),
                client.phone().orElse(null),
                client.createdAt(),
                client.updatedAt(),
                client.anonymisedAt().orElse(null));
    }

    static Client toDomain(ClientEntity entity) {
        return new Client(
                entity.getId(),
                entity.getInstallerId(),
                entity.getFirstName(),
                entity.getLastName(),
                Optional.ofNullable(entity.getEmail()),
                Optional.ofNullable(entity.getPhone()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                Optional.ofNullable(entity.getAnonymisedAt()));
    }

    static SiteEntity toEntity(Site site) {
        SiteAddress address = site.address();
        DwellingObservations observations = site.observations();
        return new SiteEntity(
                site.id(),
                site.clientId(),
                address.addressLine(),
                address.postcode(),
                address.commune(),
                address.departementCode(),
                address.latitude().orElse(null),
                address.longitude().orElse(null),
                observations.surfaceCentiSquareMetres(),
                observations.ceilingHeightCentimetres(),
                observations.constructionPeriod(),
                observations.insulationLevel(),
                observations.ventilationType(),
                observations.emitterType(),
                observations.electricalSupplyKva(),
                site.createdAt(),
                site.updatedAt(),
                site.anonymisedAt().orElse(null));
    }

    static Site toDomain(SiteEntity entity) {
        return new Site(
                entity.getId(),
                entity.getClientId(),
                new SiteAddress(
                        entity.getAddressLine(),
                        entity.getPostcode(),
                        entity.getCommune(),
                        entity.getDepartementCode(),
                        Optional.ofNullable(entity.getLatitude()),
                        Optional.ofNullable(entity.getLongitude())),
                new DwellingObservations(
                        entity.getSurfaceCentiSquareMetres(),
                        entity.getCeilingHeightCentimetres(),
                        entity.getConstructionPeriod(),
                        entity.getInsulationLevel(),
                        entity.getVentilationType(),
                        entity.getEmitterType(),
                        entity.getElectricalSupplyKva()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                Optional.ofNullable(entity.getAnonymisedAt()));
    }
}

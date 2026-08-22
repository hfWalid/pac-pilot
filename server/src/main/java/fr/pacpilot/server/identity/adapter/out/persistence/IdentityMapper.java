package fr.pacpilot.server.identity.adapter.out.persistence;

import fr.pacpilot.server.identity.domain.Installer;
import fr.pacpilot.server.identity.domain.Siret;
import java.util.Optional;

/** The only class that knows both the row and the domain record. Explicit, for the usual reason. */
final class IdentityMapper {

    private IdentityMapper() {}

    static InstallerEntity toEntity(Installer installer) {
        return new InstallerEntity(
                installer.id(),
                installer.displayName(),
                installer.siret().value(),
                installer.qualificationRef().orElse(null),
                installer.createdAt(),
                installer.updatedAt());
    }

    static Installer toDomain(InstallerEntity entity) {
        return new Installer(
                entity.getId(),
                entity.getDisplayName(),
                new Siret(entity.getSiret()),
                Optional.ofNullable(entity.getQualificationRef()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

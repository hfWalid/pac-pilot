package fr.pacpilot.server.identity.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code identity_installer}. Package-private. */
interface InstallerJpaRepository extends JpaRepository<InstallerEntity, UUID> {}

package fr.pacpilot.server.dossier.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code dossier_client}. Package-private: nothing outside may hold one. */
interface ClientJpaRepository extends JpaRepository<ClientEntity, UUID> {}

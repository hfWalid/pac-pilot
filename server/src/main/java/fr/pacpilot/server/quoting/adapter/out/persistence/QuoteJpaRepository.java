package fr.pacpilot.server.quoting.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data's view of {@code quoting_quote}. Package-private. */
interface QuoteJpaRepository extends JpaRepository<QuoteEntity, UUID> {}

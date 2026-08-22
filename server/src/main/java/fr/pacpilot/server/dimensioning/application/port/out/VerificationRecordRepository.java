package fr.pacpilot.server.dimensioning.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Driven port — where verification verdicts are kept.
 *
 * <p>Append-only by intent: there is no update and no delete. A verdict is a record of what was
 * found at a moment, and rewriting one would be indistinguishable from never having found it.
 */
public interface VerificationRecordRepository {

    VerificationRecord save(VerificationRecord record);

    /** Most recent first. */
    List<VerificationRecord> findByStudyId(UUID studyId);
}

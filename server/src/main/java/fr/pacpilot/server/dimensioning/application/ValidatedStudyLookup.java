package fr.pacpilot.server.dimensioning.application;

import fr.pacpilot.core.dimensioning.model.Dimensioning;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.server.dimensioning.api.ValidatedStudies;
import fr.pacpilot.server.dimensioning.application.port.out.DimensioningRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Satisfies {@link ValidatedStudies} from this context's own repository. */
@Service
class ValidatedStudyLookup implements ValidatedStudies {

    private final DimensioningRepository studies;

    ValidatedStudyLookup(DimensioningRepository studies) {
        this.studies = studies;
    }

    /**
     * Narrows the sealed aggregate to its signed case.
     *
     * <p>An unsigned study becomes an empty result rather than an error: it is a perfectly valid
     * study that simply cannot carry a devis yet, and the caller's handling of "no signed study
     * here" is the same either way.
     */
    @Override
    public Optional<ValidatedDimensioning> findValidated(UUID id) {
        return studies.findById(id)
                .filter(ValidatedDimensioning.class::isInstance)
                .map(ValidatedDimensioning.class::cast);
    }
}

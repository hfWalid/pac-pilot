package fr.pacpilot.server.aids.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.pacpilot.core.aids.model.AidsInputs;
import fr.pacpilot.core.aids.model.AidsOutcome;
import fr.pacpilot.core.aids.model.HeatPumpType;
import fr.pacpilot.core.aids.model.IncomeDecile;
import fr.pacpilot.core.aids.model.ReplacedSystem;
import fr.pacpilot.core.aids.port.ResolveAids;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The aids preview, over HTTP.
 *
 * <p>Calls {@code ResolveAids} and nothing else. Today every call refuses, because no barème is
 * published (ADR-0017) — and the response says exactly that rather than reporting zero aids. Zero is
 * a claim about the household; a refusal is a statement about the system, and a homeowner told
 * "0 €" would reasonably conclude they qualify for nothing.
 */
@RestController
@RequestMapping("/api/aids")
class AidsController {

    private final ResolveAids aids;

    AidsController(ResolveAids aids) {
        this.aids = aids;
    }

    /**
     * @param incomeDecile sensitive personal data ({@code CLAUDE.md} §4.6). Accepted as an input and
     *     never echoed in any response or error — see {@link AidsResolutionResponse}.
     */
    record ResolveAidsRequest(
            int incomeDecile,
            String heatPumpType,
            String climateZone,
            String replacedSystem,
            long workCostCents,
            String effectiveDate) {}

    /**
     * <p><b>Nothing here carries the income decile back.</b> M3-08 keeps it out of every rendered
     * surface, and a response body is the most rendered surface there is — it reaches a browser, a
     * log, and whatever the PWA caches.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AidsResolutionResponse(
            String outcome,
            String packVersion,
            List<AidLineResponse> lines,
            String totalAids,
            String vat,
            String estimatedTotalIncludingVat,
            String estimatedResteACharge,
            Boolean overGranted,
            String refusalDate,
            String refusalStatement) {

        static final String RESOLVED = "RESOLVED";
        static final String NO_PACK_PUBLISHED = "NO_PACK_PUBLISHED";
    }

    record AidLineResponse(String rule, String label, String amount, String source) {}

    @PostMapping("/resolutions")
    ResponseEntity<AidsResolutionResponse> resolve(@RequestBody ResolveAidsRequest request) {
        LocalDate date = LocalDate.parse(request.effectiveDate());
        EffectiveDate effectiveDate =
                new EffectiveDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        AidsOutcome outcome =
                aids.resolve(
                        new AidsInputs(
                                new IncomeDecile(request.incomeDecile()),
                                HeatPumpType.valueOf(request.heatPumpType()),
                                ClimateZone.valueOf(request.climateZone()),
                                ReplacedSystem.valueOf(request.replacedSystem()),
                                new MoneyEur(request.workCostCents())),
                        effectiveDate);

        if (outcome instanceof AidsOutcome.Resolved resolved) {
            var resolution = resolved.getResolution();
            return ResponseEntity.ok(
                    new AidsResolutionResponse(
                            AidsResolutionResponse.RESOLVED,
                            resolution.getAids().getPackVersion().getValue(),
                            resolution.getAids().getLines().stream()
                                    .map(
                                            line ->
                                                    new AidLineResponse(
                                                            line.getRule().getValue(),
                                                            line.getLabel(),
                                                            line.getAmount().render(),
                                                            line.getSource()))
                                    .toList(),
                            resolution.getAids().getTotal().render(),
                            resolution.getVat().render(),
                            resolution.getEstimatedTotalIncludingVat().render(),
                            resolution.getEstimatedResteACharge().getAmount().render(),
                            resolution.getEstimatedResteACharge().isOverGranted(),
                            null,
                            null));
        }

        // A refusal, and a successful response: the request was well-formed and the server worked.
        var refusal = (AidsOutcome.NoPackPublished) outcome;
        return ResponseEntity.ok(
                new AidsResolutionResponse(
                        AidsResolutionResponse.NO_PACK_PUBLISHED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        refusal.getEffectiveDate().render(),
                        "Aucun bareme publie ne couvre cette date; aucune aide ne peut etre calculee."));
    }
}

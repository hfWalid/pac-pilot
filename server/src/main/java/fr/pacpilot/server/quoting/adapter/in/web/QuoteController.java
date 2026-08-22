package fr.pacpilot.server.quoting.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.pacpilot.core.aids.model.AidsInputs;
import fr.pacpilot.core.aids.model.HeatPumpType;
import fr.pacpilot.core.aids.model.IncomeDecile;
import fr.pacpilot.core.aids.model.ReplacedSystem;
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.ProductSnapshot;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.core.shared.ClimateZone;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.ProductId;
import fr.pacpilot.server.quoting.application.IssueDevis;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The devis, over HTTP. Calls {@link IssueDevis} and maps; decides nothing. */
@RestController
@RequestMapping("/api/quotes")
class QuoteController {

    private final IssueDevis devis;

    QuoteController(IssueDevis devis) {
        this.devis = devis;
    }

    record IssueQuoteRequest(
            UUID dimensioningId,
            String effectiveDate,
            int incomeDecile,
            String heatPumpType,
            String climateZone,
            String replacedSystem,
            long workCostCents,
            String productId,
            String productModel,
            int productPowerAtMinusSevenWatts,
            long productPriceCents,
            List<LineItemRequest> lines) {}

    record LineItemRequest(String label, long unitPriceCents, int quantity, int vatBasisPoints) {}

    /** No income decile in any field — it is an input, never something to echo back (M3-08). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QuoteResponse(
            String outcome,
            UUID id,
            String status,
            String subtotalExcludingVat,
            String vat,
            String totalIncludingVat,
            String resteACharge,
            String packVersion,
            Boolean overGranted,
            String refusalDate,
            String refusalStatement) {

        static final String ISSUED = "ISSUED";
        static final String NO_BAREME_PUBLISHED = "NO_BAREME_PUBLISHED";
    }

    @PostMapping
    ResponseEntity<QuoteResponse> issue(@RequestBody IssueQuoteRequest request) {
        LocalDate date = LocalDate.parse(request.effectiveDate());
        EffectiveDate effectiveDate =
                new EffectiveDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());

        IssueDevis.Outcome outcome =
                devis.issue(
                        request.dimensioningId(),
                        new AidsInputs(
                                new IncomeDecile(request.incomeDecile()),
                                HeatPumpType.valueOf(request.heatPumpType()),
                                ClimateZone.valueOf(request.climateZone()),
                                ReplacedSystem.valueOf(request.replacedSystem()),
                                new MoneyEur(request.workCostCents())),
                        new ProductSnapshot(
                                new ProductId(request.productId()),
                                request.productModel(),
                                new PowerKw(request.productPowerAtMinusSevenWatts()),
                                new MoneyEur(request.productPriceCents())),
                        request.lines().stream()
                                .map(
                                        line ->
                                                new LineItem(
                                                        line.label(),
                                                        new MoneyEur(line.unitPriceCents()),
                                                        line.quantity(),
                                                        new Percentage(line.vatBasisPoints())))
                                .toList(),
                        effectiveDate);

        if (outcome instanceof IssueDevis.Outcome.Issued issued) {
            Quote quote = issued.devis();
            return ResponseEntity.ok(
                    new QuoteResponse(
                            QuoteResponse.ISSUED,
                            UUID.fromString(quote.getId().getValue()),
                            quote.getStatus().name(),
                            quote.getSubtotalExcludingVat().render(),
                            quote.getVat().render(),
                            quote.getTotalIncludingVat().render(),
                            quote.getResteACharge().getAmount().render(),
                            quote.getResolvedAids().getPackVersion().getValue(),
                            quote.getResteACharge().isOverGranted(),
                            null,
                            null));
        }

        // A refusal, and a successful response. The request was well-formed and the server worked;
        // there is simply no barème to price against yet (ADR-0017).
        var refusal = (IssueDevis.Outcome.NoBaremePublished) outcome;
        return ResponseEntity.ok(
                new QuoteResponse(
                        QuoteResponse.NO_BAREME_PUBLISHED,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        refusal.effectiveDate().render(),
                        "Aucun bareme publie ne couvre cette date; un devis ne peut pas etre etabli."));
    }
}

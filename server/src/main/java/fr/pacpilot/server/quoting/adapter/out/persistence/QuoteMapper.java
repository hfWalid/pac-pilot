package fr.pacpilot.server.quoting.adapter.out.persistence;

import fr.pacpilot.core.aids.model.AidLine;
import fr.pacpilot.core.aids.model.AidRuleId;
import fr.pacpilot.core.aids.model.AidRulePackVersion;
import fr.pacpilot.core.aids.model.ResolvedAids;
import fr.pacpilot.core.dimensioning.model.ValidatedDimensioning;
import fr.pacpilot.core.quoting.model.LineItem;
import fr.pacpilot.core.quoting.model.ProductSnapshot;
import fr.pacpilot.core.quoting.model.Quote;
import fr.pacpilot.core.quoting.model.QuoteStatus;
import fr.pacpilot.core.shared.EffectiveDate;
import fr.pacpilot.core.shared.MoneyEur;
import fr.pacpilot.core.shared.Percentage;
import fr.pacpilot.core.shared.PowerKw;
import fr.pacpilot.core.shared.ProductId;
import fr.pacpilot.core.shared.QuoteId;
import fr.pacpilot.server.quoting.application.port.out.CorruptQuoteException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * The only class that knows both the devis rows and the aggregate.
 *
 * <p><b>Reconstruction replays the state machine rather than writing the status back in.</b>
 * {@code Quote} is not a {@code data class} precisely so the status cannot be replaced without
 * passing through {@code transitionTo}, and a mapper that set the field directly would reintroduce
 * the path M1-08 removed — a rejected devis could be loaded as accepted, and no later reader could
 * tell that apart from a genuine acceptance.
 *
 * <p>So a stored status is reached by walking the transitions {@code ARCHITECTURE} #7 allows. A row
 * carrying a status the machine cannot reach fails loudly on load instead of producing an aggregate
 * that could never have existed.
 */
final class QuoteMapper {

    private QuoteMapper() {}

    static QuoteEntity toEntity(
            Quote quote, UUID dimensioningId, Instant createdAt, Integer incomeDecile) {
        ProductSnapshot product = quote.getProduct();
        EffectiveDate effectiveDate = quote.getEffectiveDate();

        QuoteEntity entity =
                new QuoteEntity(
                        UUID.fromString(quote.getId().getValue()),
                        dimensioningId,
                        product.getId().getValue(),
                        product.getModel(),
                        product.getPowerAtMinusSevenC().getWatts(),
                        product.getPriceAtQuoteTime().getCents(),
                        quote.getResolvedAids().getPackVersion().getValue(),
                        LocalDate.of(effectiveDate.getYear(), effectiveDate.getMonth(), effectiveDate.getDay()),
                        quote.getStatus().name(),
                        incomeDecile,
                        createdAt);

        List<LineItem> lines = quote.getLines();
        entity.replaceLines(
                IntStream.range(0, lines.size())
                        .mapToObj(
                                position ->
                                        new LineItemEntity(
                                                position,
                                                lines.get(position).getLabel(),
                                                lines.get(position).getUnitPrice().getCents(),
                                                lines.get(position).getQuantity(),
                                                lines.get(position).getVatRate().getBasisPoints()))
                        .toList());

        List<AidLine> aids = quote.getResolvedAids().getLines();
        entity.replaceAidLines(
                IntStream.range(0, aids.size())
                        .mapToObj(
                                position ->
                                        new AidLineEntity(
                                                position,
                                                aids.get(position).getRule().getValue(),
                                                aids.get(position).getLabel(),
                                                aids.get(position).getAmount().getCents(),
                                                aids.get(position).getSource()))
                        .toList());
        return entity;
    }

    static Quote toDomain(QuoteEntity entity, ValidatedDimensioning study) {
        Quote draft =
                Quote.Companion.draft(
                        new QuoteId(entity.getId().toString()),
                        study,
                        new ProductSnapshot(
                                new ProductId(entity.getProductId()),
                                entity.getProductModel(),
                                new PowerKw(entity.getProductPowerAtMinusSevenWatts()),
                                new MoneyEur(entity.getProductPriceCents())),
                        entity.getLines().stream()
                                .map(
                                        row ->
                                                new LineItem(
                                                        row.getLabel(),
                                                        new MoneyEur(row.getUnitPriceCents()),
                                                        row.getQuantity(),
                                                        new Percentage(row.getVatBasisPoints())))
                                .toList(),
                        new ResolvedAids(
                                new AidRulePackVersion(entity.getAidPackVersion()),
                                entity.getAidLines().stream()
                                        .map(
                                                row ->
                                                        new AidLine(
                                                                new AidRuleId(row.getRuleId()),
                                                                row.getLabel(),
                                                                new MoneyEur(row.getAmountCents()),
                                                                row.getSource()))
                                        .toList()),
                        new EffectiveDate(
                                entity.getEffectiveDate().getYear(),
                                entity.getEffectiveDate().getMonthValue(),
                                entity.getEffectiveDate().getDayOfMonth()));

        return replayTo(draft, QuoteStatus.valueOf(entity.getStatus()));
    }

    /**
     * Walks a fresh draft forward to the stored status through the transitions the domain allows.
     *
     * <p>The path is a chain with one branch at the end, so at each step there is exactly one move
     * that leads to the target and {@code transitionTo} rejects anything else. If a stored status is
     * unreachable — a value the machine has no route to — this throws rather than returning a devis
     * in a state it could never have been in.
     */
    private static Quote replayTo(Quote draft, QuoteStatus target) {
        Quote quote = draft;
        while (quote.getStatus() != target) {
            QuoteStatus current = quote.getStatus();
            QuoteStatus next =
                    current.getAllowedNext().stream()
                            .filter(candidate -> leadsTo(candidate, target))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new CorruptQuoteException(
                                                    "stored devis "
                                                            + draft.getId().getValue()
                                                            + " has status "
                                                            + target
                                                            + ", which is not reachable from "
                                                            + current
                                                            + "; the row is corrupt"));
            quote = quote.transitionTo(next);
        }
        return quote;
    }

    /** Whether {@code target} is {@code candidate} or lies downstream of it. */
    private static boolean leadsTo(QuoteStatus candidate, QuoteStatus target) {
        if (candidate == target) {
            return true;
        }
        return candidate.getAllowedNext().stream().anyMatch(next -> leadsTo(next, target));
    }
}

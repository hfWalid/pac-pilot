package fr.pacpilot.rulepacks;

import fr.pacpilot.core.aids.model.AidRulePack;
import java.util.List;

/**
 * Where published packs live. Append-only, by contract and — in production — by bucket policy.
 *
 * <p><b>There is no delete and no overwrite, and that is the point.</b> Overwriting a published pack
 * silently changes every devis that referenced it, with nothing in the system indicating anything
 * changed ({@code CLAUDE.md} §4.4). A method that could do it would eventually be called.
 *
 * <p>The contract alone is not the guarantee. The real failure mode is an operator with credentials
 * and an {@code aws s3 cp}, not a bug in this code — so the production implementation must also
 * enforce immutability at the storage layer (object-lock or a write-once policy). See PAC-71 and
 * {@code docs/RULEPACK-RUNBOOK.md}.
 */
public interface PackStore {

    /** Every pack ever published, in any order. */
    List<AidRulePack> published();

    /**
     * @throws PackValidationException if a pack with this version already exists. Refusing here is
     *     the last line before the storage layer's own refusal.
     */
    void publish(AidRulePack pack, String serialised);
}

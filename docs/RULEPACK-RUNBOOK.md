# Rule-pack runbook — publishing a barème update

**Audience:** you, in six months, having done this once.
**Cadence:** two to four times a year, whenever MaPrimeRénov', a CEE fiche or the TVA conditions move.
**Target:** one person, one afternoon.

That target is the point. PRODUCT-VIEWS #11 puts *"barème churn outpaces pack pipeline"* as the
second-highest risk on the map — and the way that risk actually materialises is not a broken
pipeline. It is a pipeline that works but takes three days of rediscovery, gets skipped once, and
leaves the product pricing devis against a superseded barème with nothing indicating it.

> **Timings below are estimates.** The first real run has not happened — it is PAC-75, the ⚑ gate.
> Replace them with what it actually took, and mark this line done.

---

## 0. Prerequisites

- The signing key, from the deployment's secret store, as `PACPILOT_RULEPACK_SIGNING_KEY`
  (base64 PKCS#8 Ed25519). **Never from a file in this repository.**
- The official source pages open: [anah.gouv.fr](https://www.anah.gouv.fr) for MaPrimeRénov',
  ATEE/DGEC for the CEE fiches, and the current TVA conditions.
- The previously published pack, so you know the date it runs to.

---

## 1. Notice the barème changed  ·  *~15 min*

Where to look, in order:

| Scheme | Source | What moves |
|---|---|---|
| MaPrimeRénov' | anah.gouv.fr | income tiers, plafonds, eligibility |
| CEE | ATEE / DGEC, fiches BAR-TH-171 and BAR-TH-104 | forfait amounts, bonifications |
| TVA 5,5 % | the conditions, not usually the rate | *which work qualifies* |

**Record the consultation date for each.** It goes in the citation, and a year from now it is the
difference between a re-checkable source and a dead link.

## 2. Encode the change  ·  *~30 min*

Copy the previous source in `rulepacks/sources/`, rename it to the new version, and edit.

```bash
cp rulepacks/sources/2026-H1.pack rulepacks/sources/2026-H2.pack
```

The format is in [`rulepacks/sources/README.md`](../rulepacks/sources/README.md). Three mechanisms and
nothing else.

**`effective-to` is inclusive.** A barème *"applicable jusqu'au 30 juin"* ends `2026-06-30` and its
successor starts `2026-07-01`. **This is the most likely error you will make**, and the pipeline
catches it against the published series — but only if the predecessor's `effective-to` was right too.

**Close the predecessor.** If it was published open-ended, it has to be given an `effective-to`
before a successor can exist. See §7.

## 3. ⚑ Verify, line by line  ·  *~2 h — the real work*

**This is the gate.** Not reading the source once: checking it.

Print the encoded source beside the published document and check each rule:

- [ ] Every `decile.N` amount matches the published tier table, digit for digit.
- [ ] Every `cap` matches the published plafond, and rules with no plafond have no `cap` line.
- [ ] Every CEE `amount` matches the fiche, and the fiche **reference and version** are in the citation.
- [ ] The TVA `rate` and — harder — **the conditions under which it applies**. The rate is easy; when
      it applies is a rule, and M3-04 deliberately made that the pack's business rather than the
      engine's.
- [ ] `effective-from` and `effective-to` match the dates the barème itself states.
- [ ] Every `source` names a page or a fiche **and** the date you consulted it.

**The question before you continue:** if a homeowner receives less than a devis priced on this pack
promised, does the encoded source and its citation show why? If the answer is "we would have to work
it out", the citations are not specific enough yet.

## 4. Build, validate, checksum, sign  ·  *~5 min*

```bash
PACPILOT_RULEPACK_SIGNING_KEY="$(pass show pacpilot/rulepack-signing-key)" \
  ./gradlew :rulepacks:run --args="rulepacks/sources/2026-H2.pack rulepacks/published"
```

Refusals are named, with the file and the line. See §7 for the common ones.

## 5. Publish, and confirm the predecessor is untouched  ·  *~10 min*

The command above writes to `rulepacks/published/`. Upload that artefact to the FR-region bucket.

**Then confirm the predecessor is byte-identical to what it was**, checksum included. Immutability is
enforced by the bucket policy, but confirming is cheap and the failure is silent.

> ### ⚠ Outstanding operational step
>
> **The object-storage bucket does not exist yet, and its immutability policy is not configured.**
> PAC-71 asks for immutability enforced *at the storage layer* — object-lock or a write-once policy —
> because the failure mode is an operator with credentials and an `aws s3 cp`, not a bug in this code.
>
> `FilePackStore` enforces the same *contract* (`CREATE_NEW`, so the filesystem refuses an overwrite
> atomically) and the pipeline and its tests run against it. What is missing is a Scaleway or OVH
> account, a bucket in an FR region, and the policy — none of which can be provisioned from this
> repository. **Do this before the first real publication, not after.**
>
> Keep the pack bucket **separate** from the one holding photos and PDFs: those carry RGPD retention
> rules, and a policy written for one must not be able to reach the other.

## 6. Confirm resolution  ·  *~10 min*

Point the server at the store and check both sides of the handover:

```bash
PACPILOT_RULEPACKS_DIRECTORY=/path/to/published ./gradlew :server:bootRun
```

- A date inside the **new** range resolves the new pack.
- A date inside the **old** range still resolves the old one. *This is the reproduction guarantee —
  the whole reason the pipeline exists.*
- The last day of the old range and the first day of the new resolve to exactly one pack each.

## 7. When validation refuses

| Message contains | What happened | Fix |
|---|---|---|
| `overlap` | the new range starts on or before the predecessor's `effective-to` | correct `effective-from`, or the predecessor's `effective-to` in a corrective successor |
| `gap` | the new range starts more than one day after the predecessor ended | `effective-from` should be the day *after* `effective-to` |
| `still open-ended` | the predecessor has no `effective-to` | publish a corrective successor closing it, then publish this one |
| `already published` | the version is taken | pick a new version — packs are never overwritten |
| `SOURCE_TBD` | a citation was left as a placeholder | finish step 3 |
| `is missing 'source'` | a rule has no citation at all | add one, with the consultation date |
| `not a number with at most two decimals` | an amount is malformed | euros with two decimals, no thousands separators |
| `will not publish an unsigned pack` | the signing key is not set | export `PACPILOT_RULEPACK_SIGNING_KEY` |

---

## Rollback — read this before you need it

**A published pack is never deleted and never edited.** The instinct when you notice a bad pack will
be to remove it. Do not: every devis priced against it referenced that version, and deleting it makes
those devis irreproducible, which is worse than the wrong figure.

**The fix is always forward.**

1. Publish a **corrective successor** whose `effective-from` is today, closing the bad pack with
   `effective-to` = yesterday.
2. The bad pack stays. Devis priced against it still reproduce — showing what was quoted, which is
   what an auditor needs to see.
3. Identify the affected devis: they carry the bad pack's version, so
   `select id from quoting_quote where aid_pack_version = '<bad version>'`.
4. Those devis need a human decision, not a migration. The figures were quoted; whether to reissue is
   a commercial call.

**Correcting a bad pack "in place" would silently rewrite the past.** That is the one thing this
architecture exists to make impossible.

---

## Test this document

The only test that counts: hand it to someone who has never published a pack, give them a barème
change, and watch. **Every point where they stop to ask a question is a gap in this document.**

Then time it. If a routine update cannot be done in an afternoon by one person, the risk
PRODUCT-VIEWS #11 names has not been mitigated — and that finding belongs on the ticket rather than
being absorbed as effort.

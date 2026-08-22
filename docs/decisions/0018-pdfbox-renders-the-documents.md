# 18. Apache PDFBox renders the documents, not a headless browser

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Relates to:** PAC-62, PAC-66, [ADR-0007](0007-single-pinned-node-lts.md), [ADR-0004](0004-modular-monolith.md)

---

## Context

The devis and the pre-visit report leave the system and end up in a homeowner's file, an insurer's
file, or an auditor's hands. PAC-66 makes the hard requirement explicit: **the same devis
regenerated must produce byte-identical output**, otherwise there is no way to demonstrate that the
PDF in an insurer's file is the one the system issued, and the audit chain that M1–M4 built into the
data stops at the boundary of the artefact.

Two candidates, and they fail in different ways.

**HTML → PDF via a headless browser.** Templates are quick to author and easy to make look right.
The costs are operational and, decisively, temporal: a browser binary in the deployment, a container
several hundred megabytes heavier, and a rendering engine that **reflows text on patch releases**.
A devis regenerated after a routine upgrade would differ in bytes while looking identical — the
worst possible failure, because nothing would flag it.

**A pure-JVM PDF library.** Heavier to author, no extra runtime dependency, and deterministic by
nature: the library writes the objects the code tells it to write, in the order it is told.

## Decision

**Apache PDFBox**, pinned in the version catalog.

Two things decided it, and only one of them is determinism.

**1. Determinism is controllable rather than hoped for.** Every source of drift PAC-66 names —
`CreationDate`, `ModDate`, `Producer`, `Creator`, the document `/ID`, font subsetting, object
ordering — is a value this code sets explicitly. There is no rendering engine deciding where a line
breaks. Where a browser would need output normalised after the fact, here the output is simply
written deterministically in the first place.

**2. The licence, which is not a footnote for a commercial product.** PDFBox is **Apache 2.0**.
The most capable alternative, iText 7, is **AGPL**: using it in a hosted SaaS without a commercial
licence would oblige us to publish the source of the whole service. OpenPDF is LGPL/MPL, workable
but with obligations. For a product intended to be sold, Apache 2.0 is the only one of the three
that asks nothing.

**Standard-14 Helvetica, with no embedded font.** Font subsetting is the subtlest determinism risk —
glyph discovery order can vary between runs — and the standard 14 fonts are not embedded at all, so
the risk does not exist rather than being mitigated. WinAnsiEncoding covers everything French
typography needs here: `é`, `à`, `ç`, `€`. The cost is a plain-looking document, and PAC-63 already
settles that trade: *"A plain document that reproduces exactly beats a handsome one that does not."*

## Consequences

- **The deployment stays one artefact.** No browser binary, no `--no-sandbox` flags, no separate
  process to supervise — which matters at M11 on Scaleway or OVH, and matters more for a one-person
  operation.
- **Layout is code, not CSS.** Tables, wrapping and pagination are written by hand. This is the real
  cost of the decision and it is paid at M5-03 and M5-04. It is accepted because the alternative
  trades authoring convenience for an audit property the product cannot do without.
- **A renderer upgrade needs the determinism checklist re-run**, not merely a visual glance. The
  knobs are listed in `docs/DOCUMENT-DETERMINISM.md` so an upgrade has a checklist rather than a
  mystery.
- **Typography is constrained by the standard 14 fonts.** If the QualiPAC report ever needs a
  specific typeface, embedding one is a deliberate change with a determinism test attached — not a
  quiet template edit.
- [ADR-0007](0007-single-pinned-node-lts.md)'s reasoning about pinning a runtime applies here too,
  and this decision means it does not have to: there is no second runtime to pin.

## Alternatives considered

**Headless Chromium via Playwright or Puppeteer.** Rejected on determinism first and weight second.
A rendering engine that reflows on a patch release cannot promise byte-identical regeneration in two
years, and PAC-66 is not a nice-to-have — it is what makes the PDF evidence. The ADR-0007 precedent
would have handled the pinning, but pinning a browser version forever is its own liability: it means
never taking a security patch for the binary that parses our own templates.

**iText 7.** Technically the strongest option and the easiest to lay out well. Rejected on licence:
AGPL in a hosted service obliges publication of the whole service's source, and the commercial
licence is priced for companies that have revenue. Revisit only if the product has both.

**OpenPDF.** LGPL/MPL, a maintained fork of iText 4, and a reasonable middle path. Rejected because
Apache 2.0 asks nothing at all and PDFBox is the more actively maintained of the two.

**Generate the PDF client-side in the PWA.** Rejected outright: `CLAUDE.md` §5 puts PDF generation
server-side, and a document produced on the device could not be verified against the server's own
recomputation — which is the entire point of §4.2.

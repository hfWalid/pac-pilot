# Document determinism — the checklist

**Why:** a devis regenerated in two years must be provably the same document. Without that, there is
no way to show that the PDF in an insurer's file is the one the system issued, and the audit chain
M1–M4 built into the data stops at the boundary of the artefact (PAC-66).

**Where it is enforced:** `DeterministicPdf` sets every value below, and
`DocumentRenderingTest.theRenderedDocumentsStillHashToTheirRecordedValues` asserts the resulting
bytes still hash to their recorded values.

---

## The knobs, and what each one would do if left alone

| Source of drift | Default behaviour | What we do |
|---|---|---|
| `CreationDate` | the moment of rendering | set to midnight UTC on the document's own effective date |
| `ModDate` | the moment of rendering | same value as `CreationDate` |
| `Producer` | `Apache PDFBox <version>` | fixed string `pac-pilot` |
| `Creator` | unset or library-supplied | fixed string `pac-pilot` |
| document `/ID` | MD5 derived from the current time | set from the document's own identity (`devis-<id>` / `rapport-<id>`) |
| font subsetting | varies with glyph discovery order | no embedded fonts at all — standard-14 only (ADR-0018) |

**The timestamps come from the document, never from a clock.** That is not only a determinism
requirement: `:core` forbids clocks in computation, and a document stamped with the moment it was
printed could not be regenerated identically by anyone, ever.

## The character-set constraint, which is easy to trip over

Standard-14 fonts use **WinAnsiEncoding**. It covers everything French typography needs here — `é`,
`à`, `ç`, `€`, `°`, `–`, `—`, and U+00A0 — and **does not** cover the typographic minus `−` (U+2212),
the narrow no-break space `‰` (U+202F), or most other characters above U+00FF.

PDFBox refuses an unencodable character at render time rather than dropping it. That is the right
failure, in a poor place to discover it: in front of a client. This was hit once already, on
`Puissance à −7 °C`, which is why the devis now uses an ASCII hyphen.

**If a document needs a character outside WinAnsi**, that is a font-embedding decision with a
determinism test attached (ADR-0018) — not a quiet template edit.

## When the hash test fails

**A failure is not automatically a defect.** Work through this in order:

1. **Did someone change a template?** Then the hash moved legitimately. Re-record it, and say so in
   the commit — the hash is documentation of what shipped.
2. **Did PDFBox change version?** Re-run this checklist first. A library upgrade that changes bytes
   is fine; one that changes them *because it now embeds a timestamp we thought we had pinned* is
   not.
3. **Neither?** Then something non-deterministic entered. Diff the two PDFs as text
   (`pdftotext`, or `PDFTextStripper`) and, if the text matches, compare the raw objects — the
   difference will be in metadata or object ordering.

## What the in-run tests can and cannot prove

`theSameDevisRendersToIdenticalBytes` renders twice inside one JVM. **It cannot see clock drift**:
two renders in the same second produce the same timestamp either way. This was proven while building
the foundation — a deliberately broken `CreationDate` did *not* fail that test, and only a per-call
variation did.

The pinned hash is what carries the across-time claim. It was recorded in one JVM and is asserted in
every later one, which is a different process, a different clock reading, and potentially a different
machine.

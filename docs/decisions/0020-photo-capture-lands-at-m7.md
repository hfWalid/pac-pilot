# 20. Photo capture lands at M7; object storage stays at M9

**Status:** accepted
**Date:** 2026-08-22
**Deciders:** operator
**Settles:** open question #1 in [README](README.md), raised on [PAC-9](https://atlasiam.atlassian.net/browse/PAC-9)
**Relates to:** [ADR-0019](0019-m7-no-longer-waits-for-m6.md), [ADR-0003](0003-pwa-is-the-only-client.md)

---

## Context

PRODUCT-VIEWS #5 puts photo capture at **step ③ of the 15-minute pre-visit**, between the survey and
the dimensioning. `DELIVERY-PLAN` §4 schedules it at **M9**, two epics after the PWA.

Both cannot be true. M7's Definition of Done is *"the full pre-visit flow completes in airplane
mode, start to finish"* — and a flow missing its third step is not the full flow. As written, M7
could be signed off on something the installer could not actually use on a visit.

This is the same class of problem as [ADR-0012](0012-intervention-persistence-lands-at-m4.md):
a plan that would make an epic's Definition of Done untrue on the day it is signed.

## Decision

**Client-side capture lands at M7. The object-storage adapter and the report's photo rendering stay
at M9.**

The split is along the network boundary, which is the only place it can honestly go:

| At M7 — works in a cellar | At M9 — needs a network |
|---|---|
| capture from the device camera | upload to object storage |
| timestamp and geotag at capture | the storage adapter and its bucket |
| store the blob in IndexedDB | photo rendering in the pre-visit report PDF |
| show thumbnails in the flow | retention and RGPD handling of image data |

## Why this split and not another

**Evidential metadata must be captured at capture time or it is worthless.** The timestamp and
geotag are what give a pre-visit photo its value in front of an insurer (`CLAUDE.md` §3). Attaching
them later — at upload, on the server, from EXIF — means attaching them from a less trustworthy
source, and a geotag applied after the fact is not evidence of where anyone stood.

**Everything on the M7 side works with no network by construction.** Camera, clock, geolocation and
IndexedDB are all local. Nothing in the M7 column needs a signal, which is precisely why it belongs
in the epic whose DoD is airplane mode.

**Nothing on the M9 side is possible without a network anyway**, so deferring it costs nothing and
keeps M9 coherent: it becomes the epic that gets photos *off* the device and onto a document.

## Consequences

- M7 grows by the capture step. It was already the largest remaining epic; this is accepted here
  rather than discovered at M9.
- **M7's DoD becomes true as written.** The full pre-visit flow, including step ③, completes offline.
- M9 becomes an upload-and-render epic with no UI capture work, which is a cleaner boundary than the
  one it had.
- Photos are **personal data captured before there is anywhere to put them**. They sit in IndexedDB on
  the device until M9 exists, which means an erasure request before M9 cannot reach them from the
  server. Recorded as a known limitation rather than solved here: it is bounded by the device, and
  M9 is where it stops being one.
- iOS eviction (PRODUCT-VIEWS #11, [ADR-0003](0003-pwa-is-the-only-client.md)) now also risks losing
  captured photos, not just cached packs. That raises the cost of a long sync gap and is an argument
  for prompting sync on app open, which M8 owns.

## Alternatives considered

**Leave capture at M9 as planned.** Rejected: it makes M7's Definition of Done false on the day it is
signed, and the plan then treats the PWA as complete from that point onward.

**Move all of M9 into M7.** Rejected as the opposite error. Object storage needs an FR-region bucket
and a provider account, which is the same operational dependency PAC-71 is already waiting on — and
pulling it into M7 would put the PWA behind an account rather than behind code.

**Capture photos but skip the geotag until M9.** Rejected. A photo without its capture-time metadata
is a picture, not evidence, and back-filling it later is worse than not having it: it would look like
evidence while not being any.

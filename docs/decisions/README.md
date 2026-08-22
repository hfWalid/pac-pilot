# Architecture decision records

One file per locked decision. Short, dated, immutable once accepted — a decision that changes gets
a **new** ADR that supersedes the old one; the old file stays, because the reasoning that was true
at the time is the record.

The valuable part of an ADR is not the choice, it is the **reasoning and the alternatives**. That is
what stops a settled question being reopened in a ticket six months from now.

## Index

| ADR | Decision | Status |
|-----|----------|--------|
| [0001](0001-gradle-over-maven.md) | Gradle over Maven | accepted |
| [0002](0002-kotlin-multiplatform-core.md) | One calculation core, compiled to JVM and JS | accepted |
| [0003](0003-pwa-is-the-only-client.md) | The PWA is the only client; no native build | accepted |
| [0004](0004-modular-monolith.md) | Modular monolith, no broker, no microservices | accepted |
| [0005](0005-no-payments-in-v1.md) | No payment handling of any kind | accepted |
| [0006](0006-no-marketplace.md) | No marketplace or homeowner-side discovery in V1–V2 | accepted |
| [0007](0007-single-pinned-node-lts.md) | One pinned Node LTS version across the whole build | accepted |
| [0008](0008-catalog-lists-only-consumed-dependencies.md) | The version catalog lists only consumed dependencies | accepted |
| [0009](0009-golden-vectors-are-append-only.md) | Golden vectors are embedded, append-only fixtures | accepted |
| [0010](0010-java-server-kotlin-core.md) | Java for the server; Kotlin confined to the core | accepted |
| [0012](0012-intervention-persistence-lands-at-m4.md) | `Intervention` persistence lands at M4, not M7.5 | accepted |
| [0013](0013-minimal-installer-identity-at-m4.md) | A minimal `Installer` identity at M4; auth stays at M10 | accepted |
| [0014](0014-personal-data-in-the-m4-schema.md) | What the M4 schema stores about people, and for how long | accepted |
| [0015](0015-server-boots-in-indicative-mode.md) | The server boots without a validated method, in explicit indicative mode | accepted |
| [0016](0016-cross-context-foreign-keys.md) | Cross-context foreign keys are allowed, and extraction pays for it | accepted |

## 0011 is reserved

`docs/M2-GATE-WORKSHEET.md` becomes `0011-simplified-dimensioning-method.md` when
[PAC-42](https://atlasiam.atlassian.net/browse/PAC-42) closes. The number is held for it so the
worksheet keeps the identity it was drafted under.

## Open questions — deliberately NOT recorded here

Three of the six questions raised in review remain **undecided**. They are not ADRs, because there is
no decision to record yet, and writing them up would falsely imply one. They are tracked on the Jira
epics they affect (and listed on the `Documentation` ticket):

1. Photo capture is step ③ of the 15-minute pre-visit (PRODUCT-VIEWS #5) but is scheduled at M9,
   two epics after the PWA — so M7's "full pre-visit flow in airplane mode" cannot be met as written.
2. M6 (real barème packs, human-gated) currently blocks M7, but the PWA only needs the M3 sample
   pack; this serialises the critical path unnecessarily.
6. E-signature is listed as H1.5 in PRODUCT-VIEWS #12 but as V2 in the DELIVERY-PLAN deferral table.

**Settled at M4-01, 2026-08-22** — numbering kept so the originals stay traceable:

- 3 → [ADR-0012](0012-intervention-persistence-lands-at-m4.md): `Intervention` lands at M4.
- 4 → [ADR-0013](0013-minimal-installer-identity-at-m4.md): a minimal `Installer` lands at M4.
- 5 → [ADR-0014](0014-personal-data-in-the-m4-schema.md): retention and erasure decided before the
  first migration.

When one of the remaining three is settled, it gets an ADR.

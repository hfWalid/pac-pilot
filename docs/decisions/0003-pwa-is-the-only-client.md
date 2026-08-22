# ADR-0003 — The PWA is the only client; no native build

- **Status:** accepted
- **Date:** 2026-08-11

## Context

The user is a solo or small-team chauffagiste, not digital-native, working on a tablet or phone
inside a client's home. Adoption has to happen in the first real pre-visit, and anything resembling
setup friction loses them.

There is also a naming hazard worth recording: the core compiles to two targets
([ADR-0002](0002-kotlin-multiplatform-core.md)), and "Kotlin Multiplatform" invites the assumption
that a native mobile app is part of the plan. It is not.

## Decision

One client: a React + TypeScript Progressive Web App. The installer opens a URL once and adds it to
the home screen. No native iOS or Android project, no app store presence, and no native targets in
the KMP build.

## Consequences

- No store review, no release binaries, no separate update channel — a fix is a deploy.
- One codebase and one UI to build, for a solo founder.
- Cost: **iOS is the weak spot.** Installation lives in the Share menu rather than a prompt,
  background sync is unreliable, and iOS can evict stored data after weeks of disuse — which
  matters for a user who may go a week between pre-visits. Mitigations belong to M7: prompt sync on
  open, warn on unsynced data, request persistent storage, and test on a real iPhone early. The
  field interviews should establish whether the installer base is mostly Android, which would
  largely retire this risk (PRODUCT-VIEWS #11).
- Cost: no access to native-only capabilities. Nothing in V1 needs them; camera and geolocation are
  available to a PWA.

## Alternatives considered

**Native app, or React Native / KMP mobile UI.** Rejected for V1: store friction and a second
codebase, for capabilities V1 does not need.

**Responsive web app with no offline story.** Rejected: offline is the hardest requirement and the
main architectural driver, not a nice-to-have.

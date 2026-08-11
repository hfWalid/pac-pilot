# ADR-0006 — No marketplace or homeowner-side discovery in V1–V2

- **Status:** accepted
- **Date:** 2026-08-11

## Context

The obvious adjacent move for a tool used by many artisans is to add the demand side: let homeowners
find installers, route leads, add ratings. It is the standard platform play and it is frequently
proposed.

## Decision

Rejected for V1 **and** V2 — not postponed pending capacity. The product does not model artisan
discovery, ratings, or lead routing, and does not scaffold for them.

## Consequences

- The positioning stays coherent. Independence from lead-gen intermediaries is the wedge against the
  CEE délégataires (Hellio, Effy); becoming one inverts the pitch.
- Development stays on the wedge: the 15-minute on-site devis.
- Cost: we forgo the network-effect story that makes investors comfortable.
- The defensible version exists, but only later: at H3, with roughly a thousand instrumented artisans
  and geocoded install-base coverage, demand-side becomes the monetisation of an asset no lead-gen
  player has — qualified, verified supply. **Earned on supply density, never launched from zero.**

## Alternatives considered

**Launch a marketplace alongside the tool.** Rejected: it would put a self-funded solo founder
against funded lead-gen incumbents on their strongest ground, while diluting the one thing that
differentiates the product.

**Build the data model now so a marketplace is easy later.** Rejected as speculative. Nothing in the
V1 domain needs artisan discovery; modelling it now would be an abstraction with no current
responsibility. The H3 version, if it happens, is built on the install-base data that accumulates
anyway.

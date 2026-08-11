# ADR-0004 — Modular monolith, no broker, no microservices

- **Status:** accepted
- **Date:** 2026-08-11

## Context

The domain is rules-heavy and calculation-heavy with a very small write volume: quotes, not streams.
The stated priorities are correctness, offline reliability and legal reproducibility — not
throughput. The team is one person.

## Decision

One deployable Spring Boot application with hard internal walls between bounded contexts
(`Identity · Dossier · Interventions · Catalog · Dimensioning · Aids · Quoting · Sync`). Contexts are
packages with enforced boundaries, not services. PostgreSQL is the only datastore. No message
broker, no cache tier, no second datastore, no Kubernetes.

## Consequences

- Operational footprint stays at the scale one person can run; scale-out is adding app replicas.
- Transactions are local and boring — no distributed consistency problem to solve.
- Any context can be extracted into a service later without a rewrite, because the walls exist from
  the start. That is why the empty context packages were committed at M0-03 rather than created on
  demand: boundaries are cheap to establish and expensive to retrofit around code that has already
  grown across them.
- Cost: the walls are only as real as their enforcement. ArchUnit guards core purity today; context
  boundary and adapter-direction rules are due at M4, and without them the packages are decoration.

## Alternatives considered

**Microservices.** Rejected: it would add network partitions, distributed transactions and
deployment complexity to a system whose load is trivial, in exchange for nothing.

**Kafka or another broker for reliable outbound events.** Rejected for V1. The transactional outbox
is noted in the brief as a *candidate* pattern, not a prescription, and sync already has an on-device
outbox for a different purpose. Ask before adding one.

**Event sourcing.** Rejected: reproducibility is already guaranteed by persisting resolved inputs
plus the rule-pack version, which is far simpler than rebuilding state from an event log — and RGPD
deletion obligations fight append-only stores.

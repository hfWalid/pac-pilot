# ADR-0005 — No payment handling of any kind

- **Status:** accepted
- **Date:** 2026-08-11

## Context

A heat-pump installation is a five-figure job that is roughly 60 % publicly subsidised. Money moves
between the homeowner, the installer, Anah (MaPrimeRénov'), the CEE obligés, and the state via
reduced VAT. It is tempting to sit in that flow: acompte collection, payment links, escrow, or
acting as a MaPrimeRénov' mandataire.

## Decision

The product touches none of the money. It **computes, itemises and versions** aid amounts so the
reste-à-charge shown on-site matches what the homeowner will actually receive, and stops there. No
acompte collection, no payment links, no escrow, no mandataire role. Stripe Billing for our own
subscription is stubbed at the boundary only, until there is a first paying customer.

## Consequences

- No regulated financial activity, no accreditation, no custody of client funds.
- The neutrality is also positioning: the wedge against the CEE délégataires is independence, and an
  intermediary that holds money is not independent.
- The engine computes amounts and eligibility, never the disbursement path — which is correct
  anyway, since routing varies by dossier (direct, avance, mandataire, délégataire).
- Cost: we forgo a revenue line and some lock-in.
- Cost: clean MaPrimeRénov' dossiers still have to reach Anah somehow. That is an H2 concern, handed
  to a partner who already carries the accreditation and the liability.

## Alternatives considered

**Acting as MaPrimeRénov' mandataire.** Rejected for V1: requires Anah accreditation and carries
legal liability. Revisit at V2 through a partner.

**Acompte collection or escrow.** Rejected outright at every horizon in the current plan: regulated,
and a poor fit for a heavily subsidised five-figure job.

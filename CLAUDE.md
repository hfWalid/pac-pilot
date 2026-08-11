# CLAUDE.md — Project brief (working codename: `pac-pilot`)

> Persistent context for Claude Code. Read fully before acting.
> Companion docs: `docs/ARCHITECTURE.md` (13 technical diagrams), `docs/PRODUCT-VIEWS.md` (12 product
> diagrams), `docs/DELIVERY-PLAN.md` (repo structure + milestones).
> When a decision is ambiguous, ask — never invent thermal formulas, U-values, or aids barèmes.

---

## 1. Mission

Mobile-first, offline-capable, local-first SaaS for French **heat-pump installers** (artisans QualiPAC, 1–10 employees).

**V1 wedge (only this first):** during the mandatory on-site pre-visit, the installer produces in ~15 minutes a
**simplified heat-loss dimensioning**, an **assisted PAC selection**, and a **devis with public aids pre-computed
and reste-à-charge shown to the client** — fully offline, synced and server-verified later.

Rules-heavy, calculation-heavy, tiny write volume. Priorities: **correctness, offline reliability, legal
reproducibility.** Not throughput.

---

## 2. Target user (drives every UX decision)

Solo/small chauffagiste or frigoriste. Non-digital-native. Works on a tablet/phone **inside the home, often with
no network** (cellar/attic). Wants few taps, instant result, defensible output. Will abandon anything ERP-shaped.
→ **The app must work fully offline and sync later.** First-class from commit #1.

**Delivery form: ONE app — a React PWA.** No native iOS/Android build, no app store. The installer opens a URL
once and adds it to his home screen. "Kotlin Multiplatform" below refers to the **calculation core compiled to
JVM + JS**, not to a cross-platform mobile UI. Do not scaffold native mobile targets.

---

## 3. Scope

### In — V1
- Client + Site record (address, construction year, surface, insulation, emitters, elec power).
- **Interventions timeline** — the artisan's own visits attached to a Site: pre-visit, install, maintenance, SAV.
  CRUD, statuses, filters (date, type, status, client, commune), past history. **Auto-generated maintenance
  recurrence** after a completed install. This is the app's home screen. See §14.
- **Dimensioning engine** (simplified, EN 12831–aligned — §6).
- Timestamped, geotagged pre-visit photos (evidential value).
- **PAC selection** from a multi-brand product catalog (power@-7°C, SCOP by zone, dB, emitter compat).
- **Devis** with aids pre-computed: MaPrimeRénov' + CEE + TVA 5,5 % + reste-à-charge.
- PDF export: devis + pre-visit technical report (QualiPAC-shaped).
- Local-first storage + deferred sync + **server-side verification** of every computed result.

### Deferred — decided, scoped, NOT in V1
- **Artisan-owned booking link (V1.5)** — homeowner requests a slot via the artisan's own public link; artisan
  explicitly confirms. Demand belongs to the artisan, never to us. Model `Intervention` so this is an added
  status path, not a rewrite (§14). Do not build the public surface in V1.
- **Electronic signature of the devis (V2)** — the real conversion moment; higher value and lighter regulation
  than handling money.
- **Route/tournée optimization (post-V1)** — reframed as "prepare my day" (ordered stops, drive time, handoff to
  Waze/Google Maps). Geocoding via `adresse.data.gouv.fr` (BAN, free, FR). Never Google Maps APIs for addresses.

### Out — V1 (don't build, don't scaffold for)
- Acting as MaPrimeRénov' mandataire / filing with Anah (needs accreditation + liability → V2 via partner).
- **Payments of any kind.** No acompte collection, no payment links, no escrow. Stripe Billing for our own
  subscription is stubbed at the boundary only, until the first paying customer.
- **Public marketplace / lead-gen / homeowner-side discovery.** Explicitly rejected: it inverts the independence
  positioning that is our wedge, and puts us against funded lead-gen incumbents. Revisit only with supply density
  (H3 in PRODUCT-VIEWS #12). Do not model artisan discovery, ratings, or lead routing.
- Géothermie. Full RE2020 thermal study. General accounting/invoicing suite.
- Microservices, Kafka/broker, blockchain/ledger, multi-region DB, event-sourcing, Keycloak, multi-tenant orgs.
  **All explicitly excluded** (see §12).

### Strategic (build core to allow, don't implement yet)
- The engines ship later as an **embeddable SDK** inside a grossiste portal or fabricant configurator.
  → keep engines pure and behind a driving port so embedding is a new adapter, not a rewrite.

---

## 4. Hard constraints (non-negotiable)

1. **Portable core, two targets.** Dimensioning + aids engines written **once in Kotlin Multiplatform**,
   compiled to **JVM** (server) and **JS** (client). One source of truth; client and server never disagree.
2. **Client computes, server verifies.** Client runs the JS core for offline UX. On sync, the server
   **recomputes** from the stored inputs + rule-pack and asserts equality. Divergence → persist + flag.
   A shared **golden-vector** suite (immutable input→output fixtures) binds both targets forever.
3. **Local-first.** Device holds the user's replica; engine runs offline; sync is secondary. Single-owner
   aggregates → **client UUIDs + outbox/change-log + idempotent ingestion + last-write-wins**. No CRDT/consensus.
4. **Rules are versioned, immutable, checksummed packs — not live DB queries.** Each MaPrimeRénov'/CEE/TVA/
   thermal-coefficient version is a signed artifact on the CDN, cached on-device, referenced by version on every
   devis. New barème = new pack. Never mutate an old pack. Never redeploy for a barème change. Past devis
   reproduce against their own pack.
5. **Liability framing in the model.** Tool = **aide à la décision**. Every result is a proposal the pro
   validates and signs. Persist `validatedBy`/`validatedAt` distinct from `computed`. This is the legal shield.
6. **EU hosting, RGPD by design.** We store names, addresses, **fiscal income deciles** (MaPrimeRénov'). Treat
   income as sensitive. Hosting FR (Scaleway/OVHcloud). DPAs for hosting + email.
7. **Modular monolith.** One deployable, hard internal bounded-context walls (ARCHITECTURE #4). No broker, no K8s.

---

## 5. Tech stack

- **Core:** Kotlin Multiplatform. `commonMain` = model + engines + rule-pack evaluator, framework-free.
  Targets: JVM (server verifier) + JS (client). Golden vectors in `commonTest`.
- **Server:** Kotlin/Spring Boot 3, Java 21 runtime. Depends on the core JVM target. Modular monolith.
- **Build:** **Gradle** (KMP requires it — this resolves the earlier Maven/Gradle question).
- **Persistence:** PostgreSQL, Flyway (forward-only). JPA entities kept separate from the domain model.
- **Object storage:** S3-compatible (Scaleway/OVH) — photos, PDFs, published rule packs.
- **Frontend:** React + TypeScript PWA, mobile-first, offline-first (service worker + IndexedDB-backed local
  store). Consumes the **JS target** of the core for on-device computation. **This is the only client.**
- **PDF:** server-side, deterministic (HTML→PDF headless or a JVM PDF lib).
- **Auth:** Spring Security, email/password + magic link. **No Keycloak.** Single-user accounts; multi-seat = V2
  (don't model it, don't make it impossible).
- **Edge:** CDN for PWA assets + rule packs. **Payments:** none in V1 (§3).

---

## 6. The core engines (the IP)

Both live in `commonMain`, pure, framework-free, exhaustively tested via golden vectors.

### 6a. Dimensioning (heat loss)
- Reference: **EN 12831**, simplified for field use (only observable inputs; no plans).
- In: surface, ceiling height, construction period → default U-values, climate zone (H1/H2/H3 + base temp by
  département), insulation level, ventilation, emitter type (→ water law).
- Out: heat load (kW) at base temp, recommended PAC power band, water-law guidance, assumptions/confidence note.
- **CRITICAL — do not guess.** The exact simplifications that are both field-fast AND accepted by QualiPAC audits +
  decennial insurers are domain knowledge **not yet finalized**. Reference data: EN 12831 (paid, AFNOR) + 3CL-DPE
  (public). Until the human validates the method, implement the engine with the **formula set injected/configurable**
  and mark every coefficient `SOURCE_TBD` in code and in golden vectors. Never ship an invented coefficient as authoritative.

### 6b. Aids
- In: income decile, PAC type, zone, system replaced, work cost.
- Rules from **versioned packs** (§4.4): MaPrimeRénov' (anah.gouv.fr), CEE fiches BAR-TH-171/104 (ATEE/DGEC), TVA 5,5 %.
- Out: itemized aids + total + reste-à-charge, tied to the pack version valid at devis date. Fully reproducible.

---

## 7. Rule-pack spec

- A pack = `{ version, effective_from, effective_to, payload, checksum, signature }`, immutable once published.
- Built from public sources, checksummed, published to object storage, distributed via CDN, cached on-device.
- Client verifies checksum on pull. Server resolves the pack whose date range contains the devis date.
- Every devis persists the resolved **pack version**, not just the numbers → past devis reproduce exactly.
- Barème update = publish new version. Old versions are never mutated or deleted.

---

## 8. Local-first & sync

- Every aggregate gets a **client-generated UUID** at creation (offline).
- Changes queued in an on-device **outbox** (append-only change-log).
- On reconnect: POST changes; server ingestion is **idempotent** (safe to replay).
- Server **recomputes** via the JVM core and compares to the client result (golden-vector-backed). Match → persist
  verified; diverge → persist + anomaly flag surfaced to the user.
- Conflicts: single-owner aggregates → **last-write-wins**. No CRDT.

---

## 9. Domain model (initial — refine, don't ossify)

`Installer` · `Client` · `Site` · `Intervention` (typed visit on a Site — see §14) · `Dimensioning` (inputs
snapshot, result, `validatedBy/At`, assumptions) · `PreVisitReport` (photo refs, timestamp, geotag) ·
`Product` (catalog, read-mostly) · `Quote`/Devis (lines, selected product, resolved aids + pack version,
reste-à-charge, status) · `AidRulePack` (versioned, immutable).
Everything with legal weight must be **reproducible after the fact**: persist resolved inputs + pack versions, not
just final numbers. See ER diagram in `docs/ARCHITECTURE.md` #6.

---

## 10. Architecture & conventions

- **Modular monolith** with bounded contexts: `Dossier · Interventions · Dimensioning · Aids · Catalog · Quoting ·
  Sync · Identity` (ARCHITECTURE #4). Hard walls; any context extractable later without rewrite.
- **Hexagonal** (ARCHITECTURE #5): domain core pure; REST, SDK, persistence, PDF, storage, rule-packs are adapters
  behind ports. Enforce core purity with an **ArchUnit** test (no Spring/JPA in `commonMain`/domain).
- **TDD, golden vectors first.** Engines tested in pure Kotlin `commonTest`, no Spring context. Every thermal/aids
  coefficient has a characterization test citing its source in a comment. Mockito only at adapter boundaries.
- **Determinism.** Same inputs + same pack version = byte-identical output. No hidden clocks in engines — pass the
  effective date in.
- **Migrations:** Flyway, reviewed. Reference data (climate zones, default U-values) seeded via versioned migrations
  or a seed pipeline — never by hand.

---

## 11. First session — build order (stop at each flag)

1. **Scaffold:** Gradle multi-module — `core` (KMP: JVM+JS), `server` (Spring, depends on core JVM), `web` (PWA,
   consumes core JS). CI (build + test both targets). ArchUnit purity rule. Golden-vector harness shared across targets.
2. **Domain model** in `commonMain` — pure Kotlin, value objects for units (kW, °C, %). No persistence yet.
3. [CONFIRM] **Dimensioning engine** in `commonMain`, formula set injectable, all coefficients `SOURCE_TBD`, golden
   vectors (structured placeholders). **Confirm the simplified method with the human before hardcoding any coefficient.**
4. **Aids engine + rule-pack evaluator** in `commonMain` against a sample pack. Tests prove reproducibility
   (a past date resolves the old pack version).
5. **Server:** Spring adapters, JVM core wired as **verifier**, Postgres + Flyway, minimal REST
   (create client/site → dimension → validate → build quote → resolve aids).
6. **PDF adapter:** devis + pre-visit report, deterministic.
7. **Rule-pack pipeline:** build → checksum → publish → CDN → client pull/cache/verify.
8. **PWA:** offline-first — JS core, IndexedDB store, outbox, sync + verification round-trip.
9. **Interventions:** timeline home screen, CRUD, filters, maintenance recurrence (§14). After the PWA store and
   outbox exist; before sync hardening.

Do not build auth, payments, multi-seat, the booking link, or the mandataire flow until asked.

---

## 12. Things NOT to assume / NOT to add

- No invented thermal coefficients, U-values, or barèmes. Unsourced number → `TBD`, surfaced, never authoritative.
- No microservices, message broker, cache tier, or second datastore "for scale." Ask first.
- No blockchain/ledger (no trustless multi-party problem; RGPD needs deletion — ledgers fight it).
- No multi-region/distributed DB, no full event-sourcing (reproducibility comes from stored inputs + pack versions).
- No framework coupling in the domain core. No Keycloak, RBAC/ABAC, or multi-tenant org model in V1.
- **No native mobile project.** The PWA is the only client (§2).
- **No payment integration, no marketplace surface, no routing/optimization engine** in V1 (§3).

---

## 13. Glossary (French domain terms — keep as identifiers in code where they are domain concepts)

**QualiPAC** RGE qualification for PAC installers (our user) · **Déperditions** heat losses · **MaPrimeRénov'** income-tiered state subsidy ·
**CEE** energy-savings certificates (fiches BAR-TH-171/104) · **Devis** quote · **Reste à charge** client out-of-pocket after aids ·
**Loi d'eau** heating curve · **SCOP** seasonal coefficient of performance · **Visite technique préalable** mandatory pre-quote on-site visit ·
**Tournée** a day's round of visits.

---

## 14. Interventions context (V1) — scope and rules

The artisan's operational timeline. It is **not a general agenda** and must never present itself as one.

**Framing rule (product-critical).** Label it *"mes visites"*, never *"mon agenda"*. It holds only what the app
owns — visits attached to a `Site`. It cannot go stale because nothing external writes to it. Never claim to show
the user's full week. Read-only import of an external calendar is a V2 idea, not V1.

**Aggregate**

```
Intervention
  id                client-generated UUID (created offline)
  site_id           → Site (Dossier context)
  type              PRE_VISIT | INSTALL | MAINTENANCE | SAV
  status            see state set below
  scheduled_at      instant; duration_minutes
  address_snapshot  denormalized from Site at creation (survives Site edits)
  lat / lon         geocoded via BAN when network allows; nullable, never blocking
  outcome_notes     free text, filled on completion
  links             dimensioning_id? · quote_id?   (produced during this visit)
  cancellation_reason?  captured on every cancel/no-show
```

**Statuses (V1 set).** `PLANNED → DONE | CANCELLED | RESCHEDULED | NO_SHOW`.
V1.5 adds the booking-link entry path in front: `REQUESTED → CONFIRMED | DECLINED`. Model the status as an open
enum so V1.5 is an addition, not a migration of meaning. **Nothing is ever auto-confirmed** — an appointment the
artisan did not accept makes the timeline untrustworthy.

**Rules**
- Fully offline: create, edit, complete, filter — all local. Same outbox/idempotent-sync path as every other
  aggregate. Geocoding is opportunistic and never blocks creating or completing an intervention.
- Filters are local IndexedDB queries: date range, type, status, client, commune. No server round-trip.
- Home screen = *aujourd'hui / cette semaine / à faire*. This is what makes the artisan open the app by reflex.
- **Maintenance recurrence:** completing an `INSTALL` auto-creates a `MAINTENANCE` intervention one year out
  (editable, dismissible). This is the retention mechanism and the artisan's recurring-revenue hook.
- A completed `PRE_VISIT` links to the `Dimensioning` and `Quote` produced during it — the audit chain
  (PRODUCT-VIEWS #8) gains an entry point.
- Geocoded history accumulates an install-base map over time (H3 asset). Do not build map features on it in V1.

**Explicitly not in this context:** route optimization, drive-time calculation, availability publishing,
homeowner-facing surfaces, reminders/SMS, payment.

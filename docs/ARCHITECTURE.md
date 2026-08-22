# ARCHITECTURE.md — `pac-pilot`

Thirteen diagrams, each a different lens. Read top-to-bottom to assimilate the whole system:
business context → users → product lifecycle → service decomposition → technical core → data & trust → deployment.

| # | Diagram | Lens |
|---|---|---|
| 1 | System context | Who talks to what |
| 2 | Runtime topology | Where code runs |
| 3 | Portable core (two targets) | The keystone decision |
| 4 | Bounded contexts | Service decomposition |
| 5 | Hexagonal core | Ports & adapters |
| 6 | Domain model | Data shape |
| 7 | Quote lifecycle | Product states |
| 8 | Installer journey | User perspective |
| 9 | Local-first sync | Offline → reconnect |
| 10 | Rule-pack lifecycle | Decentralized data + reproducibility |
| 11 | Ecosystem map | Users, partners, competitors |
| 12 | Deployment | FR / RGPD infra |
| 13 | Intervention lifecycle | The operational timeline (V1) + booking path (V1.5) |

> **One client only.** The delivery form is a single React PWA. "Two targets" throughout this document means
> the calculation core compiled to **JVM + JS** — not a cross-platform mobile app. There is no native build.

---

## 1. System context — who talks to what

The platform sits between the installer (who does all the work, on-site, mostly offline) and the homeowner (who only reads the devis). Everything else is data feeds and, strategically, partner surfaces that embed the engine.

```mermaid
flowchart TB
    subgraph actors["People"]
        A["Installer / Artisan QualiPAC<br/>primary user · on-site · mobile"]
        C["Homeowner<br/>reads the devis"]
    end

    P["pac-pilot<br/>dimensioning · aids · quoting"]

    subgraph ext["External systems & data"]
        RP["Rule packs<br/>MaPrimeRénov' · CEE · TVA · thermal coeffs"]
        CDN["CDN / edge"]
        GR["Grossiste portal<br/>embeds engine (SDK, V2)"]
        FAB["Fabricant configurator<br/>embeds engine (SDK, V2)"]
        MAIL["Transactional email"]
        PAY["Stripe Billing (later)"]
    end

    A -->|"capture site · dimension · build devis"| P
    P -->|"PDF devis"| C
    RP --> CDN --> P
    P -.->|"engine embedded"| GR
    P -.->|"engine embedded"| FAB
    P --> MAIL
    P -.-> PAY
```

---

## 2. Runtime topology — where code runs

Sophistication at the edge, boredom in the cloud. The engines run **on the device** (offline UX) and **on the server** (verifier). The server is stateless and thin; Postgres is the only stateful piece.

```mermaid
flowchart TB
    subgraph device["Device — offline-first"]
        PWA["React PWA<br/>service worker + local store"]
        JSCORE["Core — JS target<br/>dimensioning + aids"]
        OUTBOX["Outbox / change-log"]
        PWA --> JSCORE
        PWA --> OUTBOX
    end

    subgraph edge["Edge"]
        CDN["CDN<br/>PWA assets + rule packs"]
    end

    subgraph cloud["Cloud — FR / RGPD"]
        APP["Stateless app tier<br/>modular monolith (Spring)"]
        JVMCORE["Core — JVM target<br/>= VERIFIER"]
        DB[("PostgreSQL")]
        OBJ[("Object store<br/>photos · PDFs · rule packs")]
        APP --> JVMCORE
        APP --> DB
        APP --> OBJ
    end

    PWA <-->|"assets"| CDN
    OUTBOX -->|"sync on reconnect · idempotent · UUIDs"| APP
    CDN -->|"rule packs"| PWA
    OBJ -->|"publish packs"| CDN
```

---

## 3. Portable core — one source, two targets

The single most important decision. The engines are written **once** in Kotlin Multiplatform and compiled to JVM (server) and JS (client). Client and server can never disagree on a formula. A shared suite of **golden vectors** (immutable input→output fixtures) is the correctness contract binding both targets forever.

```mermaid
flowchart LR
    subgraph src["Kotlin Multiplatform — single source of truth"]
        DIM["Dimensioning engine<br/>EN 12831 simplified"]
        AID["Aids engine<br/>rule-pack evaluator"]
        VEC["Golden vectors<br/>input → output fixtures"]
    end

    DIM --> JVM["JVM target"]
    AID --> JVM
    DIM --> JS["JS target"]
    AID --> JS

    JVM --> SRV["Server = verifier"]
    JS --> CLI["Client = UX / offline"]

    VEC -.->|"same contract"| JVM
    VEC -.->|"same contract"| JS

    CLI -->|"result"| CHK{"server recompute<br/>== client result?"}
    SRV --> CHK
    CHK -->|"match"| OK["persist verified"]
    CHK -->|"diverge"| FLAG["flag anomaly"]
```

---

## 4. Bounded contexts — modular monolith

One deployable, hard internal walls. Any context can later be extracted into a service (e.g. a calculation API for a partner) without a rewrite. Highlighted contexts (Dimensioning, Aids) are the IP; the rest are plumbing.

```mermaid
flowchart TB
    ID["Identity<br/>accounts · auth"]
    DOS["Dossier<br/>Client · Site"]
    INT["Interventions<br/>timeline · statuses · recurrence"]
    CAT["Catalog<br/>PAC products · SCOP · power@-7°C"]
    DIMC["Dimensioning<br/>heat-loss engine · validation"]
    AIDC["Aids<br/>rule-pack evaluation"]
    QUO["Quoting<br/>devis · line items · reste à charge"]
    SYNC["Sync<br/>outbox ingestion · idempotency"]

    DOS --> INT
    DOS --> DIMC
    INT -.->|"a PRE_VISIT produces"| DIMC
    INT -.->|"a PRE_VISIT produces"| QUO
    DIMC --> AIDC
    ID --> QUO
    CAT --> QUO
    DIMC --> QUO
    AIDC --> QUO
    SYNC --> DIMC
    SYNC --> QUO
    SYNC --> INT
    SYNC --> ID

    classDef core fill:#dbeafe,stroke:#1e40af,color:#1e3a8a;
    classDef new fill:#f3e8ff,stroke:#7e22ce,color:#581c87;
    class DIMC,AIDC core;
    class INT new;
```

`Interventions` (purple) is the V1 addition: the artisan's operational timeline and the app's home screen.
It **consumes** Dossier and **references** the artifacts a visit produces — it owns no calculation. Note the
dependency direction: Dimensioning and Quoting know nothing about Interventions, so the context could be
removed or extracted without touching the IP.

---

## 5. Hexagonal core — ports & adapters

The domain (model + both engines) is pure Kotlin with zero framework imports. Everything else — REST, the embeddable SDK, persistence, PDF, storage, rule-pack loading — is an adapter behind a port. This is what makes the core portable *and* embeddable.

```mermaid
flowchart LR
    subgraph core["Domain core — pure, no framework"]
        UC["Use cases<br/>RunDimensioning · ResolveAids · BuildQuote"]
        MODEL["Model + engines"]
        UC --> MODEL
    end

    subgraph inports["Driving ports (in)"]
        WEB["REST adapter"]
        SDK["Embeddable SDK adapter"]
    end

    subgraph outports["Driven ports (out)"]
        PERSIST["Persistence adapter (JPA)"]
        PDF["PDF adapter"]
        STORE["Object-store adapter"]
        PACKS["Rule-pack repository adapter"]
    end

    WEB --> UC
    SDK --> UC
    UC --> PERSIST
    UC --> PDF
    UC --> STORE
    UC --> PACKS
```

---

## 6. Domain model — data shape

Note what carries legal weight: `DIMENSIONING.validated_by/at` (the liability shield) and `QUOTE.rulepack_version` (reproducibility). Nothing stores only a final number; it stores the inputs + the rule version used.

`INTERVENTION` carries the operational layer. Its links to `DIMENSIONING` and `QUOTE` are optional in both
directions — a visit may produce nothing, and a dimensioning may exist without a scheduled visit. `address_snapshot`
is denormalized deliberately: it must survive later edits to the `Site`.

```mermaid
erDiagram
    INSTALLER ||--o{ CLIENT : manages
    CLIENT ||--o{ SITE : owns
    SITE ||--o{ INTERVENTION : scheduled_at
    SITE ||--o{ DIMENSIONING : assessed_by
    INTERVENTION |o--o| DIMENSIONING : produced
    INTERVENTION |o--o| QUOTE : produced
    DIMENSIONING ||--|| PREVISITREPORT : documented_by
    DIMENSIONING ||--o{ QUOTE : basis_for
    PRODUCT ||--o{ QUOTE : selected_in
    AIDRULEPACK ||--o{ QUOTE : resolved_against

    INSTALLER {
        uuid id
        string company
        string qualipac_ref
    }
    SITE {
        uuid id
        int construction_year
        float surface_m2
        string climate_zone
        string emitter_type
    }
    INTERVENTION {
        uuid id
        string type
        string status
        datetime scheduled_at
        int duration_minutes
        string address_snapshot
        float lat
        float lon
        string outcome_notes
        string cancellation_reason
    }
    DIMENSIONING {
        uuid id
        json inputs_snapshot
        float heat_load_kw
        string validated_by
        datetime validated_at
    }
    QUOTE {
        uuid id
        string status
        uuid product_id
        string rulepack_version
        float reste_a_charge
    }
    AIDRULEPACK {
        string version
        date effective_from
        date effective_to
        string checksum
    }
```

---

## 7. Quote lifecycle — product states

The two annotated states are the ones that matter legally and operationally: dimensioning happens offline; validation is an explicit human act that gets persisted.

```mermaid
stateDiagram-v2
    [*] --> Draft
    Draft --> Dimensioned : run engine (offline OK)
    Dimensioned --> Validated : pro signs off
    Validated --> Quoted : select product + lines
    Quoted --> AidsResolved : evaluate rule pack
    AidsResolved --> Sent : PDF to client
    Sent --> Accepted
    Sent --> Rejected
    Accepted --> [*]
    Rejected --> [*]

    note right of Dimensioned
        computed on device, synced later
    end note
    note right of Validated
        persist who/when = liability shield
    end note
```

---

## 8. Installer journey — user perspective

Scores = how smooth the step feels. The high-value moment is on-site, offline, when the installer shows the homeowner the reste-à-charge live. Sync/verify happen quietly afterward.

```mermaid
journey
    title Installer journey — one heat-pump quote
    section On-site pre-visit (often offline)
      Capture site data: 4: Installer
      Photograph and geotag: 4: Installer
      Run dimensioning: 5: Installer
      Validate result: 5: Installer
    section Build the offer (on-site)
      Select PAC model: 4: Installer
      Resolve aids: 5: Installer
      Show reste a charge: 5: Installer, Client
    section After (reconnect)
      Sync to cloud: 3: Installer
      Server verifies: 5: Installer
      Send PDF devis: 4: Installer, Client
```

---

## 9. Local-first sync — offline to reconnect

The only genuinely distributed problem in the system, and a solved one: client UUIDs + outbox + idempotent ingestion + server recompute. Single-owner aggregates mean last-write-wins is enough — no CRDTs, no consensus.

```mermaid
sequenceDiagram
    participant U as Installer
    participant PWA as PWA (device)
    participant OB as Outbox
    participant API as App tier
    participant V as Verifier (JVM core)
    participant DB as Postgres

    U->>PWA: capture + dimension (offline)
    PWA->>PWA: compute via JS core
    PWA->>OB: append change (client UUID)
    Note over PWA,OB: works with no network
    U->>PWA: reconnect
    PWA->>API: POST changes (idempotent)
    API->>V: recompute from inputs + rulepack
    V-->>API: server result
    API->>API: compare client vs server
    alt match
        API->>DB: persist verified
        API-->>PWA: ack + state
    else diverge
        API->>DB: persist + anomaly flag
        API-->>PWA: ack + warning
    end
```

---

## 10. Rule-pack lifecycle — decentralized data + reproducibility

Barèmes are not queried live; each version is an immutable, checksummed artifact pushed to the CDN and cached on-device. A devis references its pack version, so any past devis reproduces exactly against the pack it was built with. Updating a barème = publishing a new pack — never mutating an old one, never a redeploy.

```mermaid
flowchart TB
    SRC["Source barèmes<br/>anah.gouv.fr · ATEE · DGEC · EN 12831"]
    BUILD["Build pack<br/>version + effective dates"]
    SIGN["Checksum + sign<br/>immutable artifact"]
    PUB["Publish to object store"]
    CDN["CDN"]
    DEV["Device pulls + caches"]
    DEVIS["Devis references pack version"]
    REPRO["Past devis reproduces<br/>against its own pack"]

    SRC --> BUILD --> SIGN --> PUB --> CDN --> DEV --> DEVIS --> REPRO
    SIGN -.->|"old versions never mutate"| REPRO
```

---

## 11. Ecosystem map — users, partners, competitors

Position at a glance: buyers are also users (short sale), partners solve distribution and trust, competitors are boxed by a specific counter-angle each.

```mermaid
flowchart TB
    YOU["pac-pilot"]

    subgraph users["Users — buyer = user"]
        U1["Plombier-chauffagiste<br/>converting to PAC"]
        U2["Frigoriste<br/>air/air → air/eau"]
        U3["Small PAC PME<br/>5-20 installs/mo"]
    end

    subgraph partners["Partners — distribution + trust"]
        P1["Grossistes<br/>Rexel · CEDEO · Téréva"]
        P2["Fabricants challengers"]
        P3["Mandataires MaPrimeRénov'"]
        P4["Assureurs décennale"]
        P5["Qualit'EnR / formation"]
    end

    subgraph competitors["Competition"]
        C1["Fabricant free tools<br/>mono-brand, biased"]
        C2["BAO / legacy desktop"]
        C3["Tolteck / Obat<br/>generalist, no thermal"]
        C4["Hellio / Effy pro<br/>lock into CEE"]
    end

    U1 --> YOU
    U2 --> YOU
    U3 --> YOU
    P1 -->|"channel"| YOU
    P2 -->|"data + embed"| YOU
    YOU -->|"clean dossiers"| P3
    P4 -->|"endorse dimensioning"| YOU
    P5 -->|"reach"| YOU
    YOU -.->|"neutrality"| C1
    YOU -.->|"modern UX"| C2
    YOU -.->|"thermal depth"| C3
    YOU -.->|"independence"| C4
```

---

## 12. Deployment — FR / RGPD infra

Stateless app instances behind a load balancer, managed Postgres with PITR, S3-compatible object store, CDN in front. All in-region. Footprint stays at the ~€50–150/mo level; scale-out is adding replicas, not re-architecting.

```mermaid
flowchart TB
    subgraph frr["FR region — Scaleway / OVHcloud"]
        LB["Load balancer / TLS"]
        APP1["App instance"]
        APP2["App instance (scale-out later)"]
        PG[("Managed PostgreSQL<br/>backups + PITR")]
        S3[("Object storage")]
        LB --> APP1
        LB --> APP2
        APP1 --> PG
        APP2 --> PG
        APP1 --> S3
        APP2 --> S3
    end
    CF["Cloudflare — CDN + DNS"]
    BREVO["Brevo email (FR)"]
    MON["Sentry / monitoring"]

    CF --> LB
    APP1 --> BREVO
    APP1 --> MON
```

---

## 13. Intervention lifecycle — the operational timeline

The artisan's own visits, attached to a `Site`. **Not a general agenda**: it holds only what the app owns, so it
cannot go stale. Solid states are V1; the dashed `REQUESTED → CONFIRMED` path is the V1.5 booking link, modelled
now as an *entry path in front of* `PLANNED` so it is an addition rather than a migration.

The two rules that make the timeline trustworthy: **nothing is ever auto-confirmed** (the artisan explicitly
accepts every request), and **every cancellation captures a reason**. The recurrence edge is the retention
mechanism — a completed install schedules its own maintenance.

```mermaid
stateDiagram-v2
    [*] --> PLANNED : artisan creates (offline)
    [*] --> REQUESTED : booking link (V1.5)

    REQUESTED --> CONFIRMED : artisan accepts — never automatic
    REQUESTED --> DECLINED : out of zone / scope
    CONFIRMED --> PLANNED : enters the timeline

    PLANNED --> DONE : visit completed
    PLANNED --> RESCHEDULED : new slot, same thread
    PLANNED --> CANCELLED : reason captured
    PLANNED --> NO_SHOW : reason captured
    RESCHEDULED --> PLANNED

    DONE --> [*]
    CANCELLED --> [*]
    DECLINED --> [*]
    NO_SHOW --> [*]

    note right of DONE
        PRE_VISIT → links Dimensioning + Quote
        INSTALL → auto-creates MAINTENANCE +1 year
    end note
    note left of REQUESTED
        V1.5 only — demand belongs to the
        artisan's own link, never to a marketplace
    end note
```

### Types and what each one triggers

```mermaid
flowchart LR
    PV["PRE_VISIT<br/>visite technique préalable"]
    IN["INSTALL"]
    MA["MAINTENANCE<br/>auto-generated, editable"]
    SA["SAV"]

    PV -->|"produces"| ART["Dimensioning + validated<br/>Quote + pre-visit report"]
    ART -->|"quote accepted"| IN
    IN -->|"on completion, +1 year"| MA
    MA -->|"+1 year, recurring"| MA
    IN -.->|"issue reported"| SA

    classDef hook fill:#dcfce7,stroke:#15803d,color:#14532d;
    class MA hook;
```

The green node is where retention comes from: an artisan whose whole maintenance schedule lives in the app does
not churn, and the recurrence surfaces revenue he is currently leaving on the table.

### Where it runs

Entirely local-first — no new infrastructure. Creation, edition, completion and all filters (date, type, status,
client, commune) are IndexedDB queries on the device; changes flow through the same outbox and idempotent
ingestion as every other aggregate (#9). Geocoding via `adresse.data.gouv.fr` (BAN — free, FR-hosted, no key) is
**opportunistic**: it runs when there is network and never blocks creating or completing an intervention.

```mermaid
flowchart LR
    UI["Timeline home screen<br/>aujourd'hui · cette semaine · à faire"]
    IDB[("IndexedDB<br/>local queries + filters")]
    OB["Outbox"]
    API["App tier — Interventions context"]
    BAN["adresse.data.gouv.fr<br/>geocoding · opportunistic"]

    UI <--> IDB
    IDB --> OB
    OB -->|"idempotent sync"| API
    UI -.->|"when online, non-blocking"| BAN

    classDef opt stroke-dasharray: 4 4;
    class BAN opt;
```

**Explicitly out of this context:** route/tournée optimization, drive-time calculation, availability publishing,
homeowner-facing discovery, reminders/SMS, and payment of any kind. Route preparation is post-V1; marketplace and
escrow are rejected outright (`CLAUDE.md` §3).

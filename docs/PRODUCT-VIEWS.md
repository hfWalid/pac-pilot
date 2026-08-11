# PRODUCT-VIEWS.md — `pac-pilot`

Twelve product-side diagrams complementing the twelve technical ones in `ARCHITECTURE.md`.
Where ARCHITECTURE answers *how the system works*, this file answers *why it exists, who it serves,
how it is used, what it changes, and what can kill it*.

| # | Diagram | Lens |
|---|---|---|
| 1 | Problem space today | The pain being replaced |
| 2 | Jobs-to-be-done → features | Why each V1 feature exists |
| 3 | Money & aids flow | Economic impact on the value chain |
| 4 | Use cases by actor | Who does what with the product |
| 5 | The 15-minute pre-visit | The core usage moment, tap by tap |
| 6 | Before / after | Time-to-devis compression |
| 7 | Adoption funnel & habit loop | How an artisan becomes a paying user |
| 8 | Trust & audit chain | Why the output is defensible |
| 9 | Degraded-mode UX | What the user sees when things go wrong |
| 10 | KPI tree | What we measure, rooted in one north star |
| 11 | Risk map | What can kill the product |
| 12 | Horizons V1 → V2+ | Where this goes |

---

## 1. Problem space today — the pain being replaced

The tool exists because one quote today spans several days, three disconnected tools, and an
uncomfortable liability gap. Every red node is a pain the V1 wedge removes.

```mermaid
flowchart TB
    VISIT["On-site pre-visit<br/>notes on paper · photos in phone gallery"]
    HOME["Back at the office, evening"]
    EXCEL["Excel / rule of thumb<br/>dimensioning ≈ m² × coefficient"]
    FAB["Fabricant selection tool<br/>mono-brand, biased"]
    AIDS["Aids lookup<br/>anah.gouv.fr + CEE simulators, often stale"]
    DEVIS["Devis assembled by hand<br/>Word / invoicing tool"]
    WAIT["Client waits 3–10 days<br/>compares competing offers"]
    RISK["No traceable method<br/>audit / decennial exposure"]

    VISIT --> HOME --> EXCEL --> FAB --> AIDS --> DEVIS --> WAIT
    EXCEL -.-> RISK
    DEVIS -.-> RISK

    classDef pain fill:#fee2e2,stroke:#b91c1c,color:#7f1d1d;
    class EXCEL,FAB,AIDS,WAIT,RISK pain;
```

---

## 2. Jobs-to-be-done → features — why each V1 feature exists

Every V1 feature traces to a job the installer already has. If a proposed feature has no arrow
from a job, it does not belong in V1.

```mermaid
flowchart LR
    subgraph jobs["Installer's jobs-to-be-done"]
        J1["Size the machine right<br/>(too small = cold client,<br/>too big = short-cycling)"]
        J2["Pick a defensible model<br/>across brands"]
        J3["Tell the client what it<br/>really costs after aids"]
        J4["Win the deal before<br/>a competitor quotes"]
        J5["Survive a QualiPAC audit<br/>and keep decennial cover"]
        J6["Keep track of who I'm<br/>seeing and what I owe them<br/>(and get the maintenance<br/>revenue I forget)"]
    end

    subgraph feats["V1 features"]
        F1["Dimensioning engine<br/>EN 12831 simplified"]
        F2["Multi-brand catalog<br/>power@-7°C · SCOP · dB"]
        F3["Aids engine<br/>MaPrimeRénov' + CEE + TVA 5,5%"]
        F4["On-site devis PDF<br/>reste-à-charge live"]
        F5["Geotagged photos +<br/>validated pre-visit report"]
        F6["Interventions timeline<br/>+ maintenance recurrence"]
    end

    J1 --> F1
    J2 --> F2
    J3 --> F3
    J4 --> F4
    J5 --> F5
    J6 --> F6
    F1 --> F5
    F6 -.->|"a PRE_VISIT opens"| F1
```

**Validate J6 before building F6.** The job is asserted, not confirmed. Interview question: *"Vos clients,
comment ils prennent rendez-vous avec vous aujourd'hui? Et ça vous pose problème?"* — followed by *"Comment vous
gérez votre planning? Montrez-moi."* Watch for the trap answer: many artisans **like** the phone call, because
it is how they qualify a job and decline politely. If most already run a synced Google Calendar and are happy,
F6 shrinks to the dossier timeline only and the booking link (H1.5) is a nice-to-have, not a priority.

---

## 3. Money & aids flow — economic impact on the value chain

The product touches none of the money. It computes, itemizes, and versions the aid amounts so the
reste-à-charge shown on-site matches what the flows below eventually produce. That neutrality is
deliberate: handling funds would mean mandataire accreditation and liability (V2, via partner).

```mermaid
flowchart TB
    HO["Homeowner"]
    INST["Installer (RGE QualiPAC)"]
    ANAH["Anah — MaPrimeRénov'"]
    OBL["Obligés / délégataires — CEE"]
    STATE["State — TVA 5,5%"]
    PP["pac-pilot<br/>computes · itemizes · versions"]

    HO -->|"pays reste-à-charge"| INST
    ANAH -->|"MaPrimeRénov' subsidy"| HO
    OBL -->|"CEE premium"| HO
    STATE -.->|"reduced VAT on invoice"| INST

    PP -.->|"pre-computes amounts<br/>per barème version"| HO
    PP -.->|"devis + audit trail"| INST

    classDef tool fill:#dbeafe,stroke:#1e40af,color:#1e3a8a;
    class PP tool;
```

> Exact payment routing (avance, mandataire, délégataire) varies by dossier — the engine computes
> amounts and eligibility, never the disbursement path. Flows above are the canonical direct case.

---

## 4. Use cases by actor — who does what

Three actors only. The homeowner never logs in — they are shown a screen and receive a PDF.
The system itself is an actor (verification, pack refresh) because those acts carry product meaning.

```mermaid
flowchart LR
    INST(["Installer"])
    HO(["Homeowner"])
    SYS(["System (background)"])

    subgraph uc["Use cases — V1"]
        U1["Create client + site record"]
        U2["Capture geotagged photos"]
        U3["Run dimensioning"]
        U4["Validate dimensioning (sign-off)"]
        U5["Select PAC from catalog"]
        U6["Resolve aids + reste-à-charge"]
        U7["Generate + send devis PDF"]
        U8["Review anomaly flags"]
        V1["Verify synced computations"]
        V2["Refresh rule packs from CDN"]
    end

    INST --> U1 & U2 & U3 & U4 & U5 & U6 & U7 & U8
    HO -.->|"views reste-à-charge on tablet"| U6
    HO -.->|"receives"| U7
    SYS --> V1 & V2
```

---

## 5. The 15-minute pre-visit — the core usage moment

The entire product bet compressed into one screen flow. Budget per step is a design constraint:
if a step exceeds its budget, the UX has failed regardless of engine quality. Every step works offline.

```mermaid
flowchart TB
    S1["① Client & site<br/>address · year · surface · ceiling<br/>~3 min"]
    S2["② Building survey<br/>insulation level · ventilation ·<br/>emitters · elec power<br/>~4 min"]
    S3["③ Photos<br/>timestamped + geotagged<br/>~2 min"]
    S4["④ Dimensioning<br/>instant compute · assumptions shown<br/>~1 min"]
    S5["⑤ Pro validates<br/>explicit sign-off act<br/>~1 min"]
    S6["⑥ Machine selection<br/>filtered: power band · emitters · dB<br/>~2 min"]
    S7["⑦ Devis + aids<br/>reste-à-charge on screen,<br/>tablet turned to client<br/>~2 min"]

    S1 --> S2 --> S3 --> S4 --> S5 --> S6 --> S7

    classDef money fill:#dcfce7,stroke:#15803d,color:#14532d;
    class S7 money;
```

---

## 6. Before / after — time-to-devis compression

The measurable promise: the same deliverable in one visit instead of a multi-day round-trip.
The strategic effect is at the end — the client commits before competitors have even quoted.

```mermaid
timeline
    title One heat-pump quote — today vs with pac-pilot
    section Today (3–10 days)
        Day 0 : Pre-visit, paper notes
        Day 1–2 : Evening Excel dimensioning : Fabricant tool per brand
        Day 3–5 : Aids lookup, often stale : Devis assembled by hand
        Day 5–10 : Client compares offers : Deal at risk
    section With pac-pilot (15 min)
        Minute 0–10 : Survey + photos + dimensioning + validation
        Minute 10–15 : Machine + aids + reste-à-charge shown live
        Same visit : Devis PDF sent : Client can commit on the spot
```

---

## 7. Adoption funnel & habit loop — how an artisan becomes a paying user

The buyer is the user, so the funnel is short — but the audience is non-digital-native, so the
first-value moment must arrive inside the first real pre-visit. Retention is a loop, not a stage:
every completed devis reinforces the habit and feeds referral in a word-of-mouth trade.

```mermaid
flowchart TB
    subgraph funnel["Acquisition funnel"]
        A1["Hears about it<br/>grossiste counter · formation ·<br/>peer word-of-mouth"]
        A2["Tries it<br/>free trial, no setup,<br/>works offline immediately"]
        A3["First real pre-visit<br/>⚡ first-value moment:<br/>devis produced on-site"]
        A4["Pays<br/>subscription ≪ one won deal"]
    end

    subgraph loop["Habit loop (retention)"]
        H1["Next visit lands in the timeline"]
        H2["Opens pac-pilot by reflex<br/>aujourd'hui · cette semaine"]
        H3["Devis won faster"]
        H4["Tells peers"]
        H5["Install completed →<br/>maintenance auto-scheduled +1 year"]
    end

    A1 --> A2 --> A3 --> A4
    A4 --> H1 --> H2 --> H3 --> H4
    H3 --> H5 --> H1
    H3 --> H1
    H4 -.-> A1

    classDef hook fill:#dcfce7,stroke:#15803d,color:#14532d;
    class H2,H5 hook;
```

Two green nodes carry retention. **H2** is what the Interventions timeline buys: before it, the app was only
opened while standing in a cellar; now it is the home screen for the working week. **H5** is the stronger of the
two — an artisan whose entire maintenance schedule lives here does not churn, and the recurrence hands him a
recurring-revenue stream he is currently leaving on the table.

---

## 8. Trust & audit chain — why the output is defensible

The product's deepest moat is not the calculation — it is that every number can be replayed and
attributed years later. This chain is what a QualiPAC auditor or decennial insurer walks backwards.

```mermaid
flowchart LR
    IN["Inputs snapshot<br/>persisted verbatim"]
    PACK["Rule-pack version<br/>immutable · checksummed"]
    ENG["Engine result<br/>deterministic replay"]
    VAL["Validation act<br/>who · when — the pro signs"]
    PHOTO["Geotagged, timestamped photos"]
    PDF["Devis + pre-visit report PDF"]
    AUD["QualiPAC audit /<br/>decennial insurer /<br/>client dispute"]

    IN --> ENG
    PACK --> ENG
    ENG --> VAL --> PDF
    PHOTO --> PDF
    PDF --> AUD
    AUD -.->|"replay: same inputs + same pack<br/>= same result, byte-identical"| ENG

    classDef shield fill:#fef9c3,stroke:#a16207,color:#713f12;
    class VAL shield;
```

---

## 9. Degraded-mode UX — what the user sees when things go wrong

Offline is the happy path, so "degraded" means something else here. Three honesty rules: never block
the visit, never silently guess, never hide a divergence. Each degraded state has an explicit banner.

```mermaid
stateDiagram-v2
    [*] --> Normal

    Normal --> Offline : network lost
    Offline --> Normal : reconnect + sync

    Offline --> StalePacks : cached rule pack<br/>past effective_to
    StalePacks --> Normal : pack refreshed
    note right of StalePacks
        banner — "aids computed on barème vX,
        may be outdated" · devis marked provisional
    end note

    Normal --> Diverged : server recompute ≠<br/>client result
    Diverged --> Resolved : user reviews flagged devis
    note right of Diverged
        anomaly flag on the devis ·
        never silently corrected
    end note

    Normal --> Unsupported : inputs outside validated<br/>envelope (SOURCE_TBD zone)
    note right of Unsupported
        engine refuses authority —
        "manual study required" ·
        no invented number, ever
    end note

    Resolved --> Normal
    Unsupported --> Normal
```

---

## 10. KPI tree — what we measure

One north star, decomposed into three drivers. Anything not feeding this tree is vanity.
The counter-metrics guard the two ways the north star could be gamed: rushed junk devis and
silent computational drift.

```mermaid
mindmap
    root(("North star:<br/>validated devis<br/>per installer / month"))
        Activation
            Trial → first real devis rate
            Time to first devis
            Pre-visit completed fully offline %
        Efficiency
            Median pre-visit duration vs 15 min budget
            Taps per devis
            Devis sent same-visit %
        Trust
            Devis win rate (accepted / sent)
            Validation rate (validated / computed)
            Counter: divergence flags per 1000 devis
            Counter: devis reopened after send %
        Retention loop
            Weekly active days (timeline opened)
            Interventions logged per installer / month
            Maintenance recurrences accepted vs dismissed
            Counter: % interventions created but never completed
        Business
            Trial → paid conversion
            Monthly churn
            Referral share of signups
```

---

## 11. Risk map — what can kill the product

Positioned by likelihood and severity. The top-right quadrant holds the two existential risks —
both are domain risks, not technical ones, which is why the build order gates on human validation
of the simplified method before any coefficient becomes authoritative.

Two risks added with the Interventions scope. **Agenda competition:** a calendar that holds only *some* of his
appointments is worse than none — distrust in the timeline bleeds into the rest of the app. Mitigation is
framing, not features: it is *"mes visites"*, never *"mon agenda"*, and it only ever contains what the app owns.
**iOS PWA:** install lives in the Share menu, background sync is unreliable, and iOS can evict stored data after
weeks of disuse — which matters for a user who may go a week between pre-visits. Mitigations: prompt sync on
open, warn on unsynced data, request persistent storage. Test on a real iPhone early, and ask iPhone-vs-Android
in the field interviews — if the base is mostly Android, this risk largely evaporates.

```mermaid
quadrantChart
    title Product risks — likelihood x severity
    x-axis Low severity --> High severity
    y-axis Low likelihood --> High likelihood
    quadrant-1 Mitigate now
    quadrant-2 Monitor closely
    quadrant-3 Accept
    quadrant-4 Contingency plan
    "Simplified method rejected by auditors/insurers": [0.9, 0.75]
    "Bareme churn outpaces pack pipeline": [0.6, 0.8]
    "Adoption: too ERP-like for artisans": [0.75, 0.6]
    "Fabricant free tools improve": [0.5, 0.55]
    "Catalog data licensing/freshness": [0.45, 0.65]
    "RGPD incident on income data": [0.85, 0.2]
    "MaPrimeRenov budget cuts shrink market": [0.7, 0.35]
    "KMP JS target friction": [0.25, 0.4]
    "Sync edge-case bugs": [0.35, 0.3]
    "Timeline competes with his existing agenda": [0.5, 0.5]
    "iOS PWA storage eviction / install friction": [0.4, 0.45]
```

---

## 12. Horizons V1 → V2+ — where this goes

The wedge earns the right to expand. Each horizon reuses the same core engines — that is why they
stay pure and behind ports. Nothing in H2/H3 is built, scaffolded, or modeled in V1.

```mermaid
timeline
    title Product horizons
    section H1 — the wedge (V1)
        On-site tool : Dimensioning + selection + devis + aids : Offline-first PWA : Single-user accounts
        Operational layer : Interventions timeline : Maintenance auto-recurrence
    section H1.5 — the artisan's own demand
        Booking link : Homeowner requests a slot via the artisan's link : Artisan confirms, always explicitly
        E-signature : Devis signed on the spot — the real conversion moment
    section H2 — distribution & depth
        Embeddable SDK : Engines inside grossiste portals and fabricant configurators
        Mandataire partner : Clean MaPrimeRenov dossiers handed off, no accreditation carried
        Multi-seat orgs : The 5–20 installs/month PME
        Prepare my day : Ordered stops + drive time, handoff to Waze — not route optimization
    section H3 — the record of truth
        Install base data : Every validated dimensioning + outcome, geocoded
        Insurer products : Endorsed method, decennial pricing input
        Fleet & maintenance : Post-install lifecycle
        Demand side — only here : Earned on supply density, never launched from zero
```

**On the marketplace question.** Homeowner-side discovery is rejected for V1 and V2, not merely postponed. It
inverts the independence positioning that is the entire wedge against the CEE délégataires (#11), and it would
put a self-funded solo founder against funded lead-gen incumbents on their strongest ground. The defensible
version arrives only at H3: with ~1,000 instrumented artisans and geocoded coverage, demand-side becomes the
monetization of an asset no lead-gen player has — qualified, verified supply. Earned, not launched.

Payments are rejected outright at every horizon in this plan: holding funds on behalf of an artisan is a
regulated activity, and it sits badly on a five-figure job that is ~60 % publicly subsidised.

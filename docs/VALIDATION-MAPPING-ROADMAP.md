# Roadmap: Validation & Bidirectional Mapping

One page tying together the GitHub issues (#549, #551), the four issue drafts, the `Endo` monoid surfaced by the Phase 1 probe, and the two feature designs (A1 mapper, A2 patch) into a single dependency map and build order. Origin: the telescope comparative analysis (recommendation **A1**), then a foundations-first re-prioritisation validated by two independent consumers.

## Dependency map

```
PHASE 0 — Foundations
  #549 NonEmptyList ───────────┐  error carrier
  Endo<S> monoid ────────────┐ │  write carrier (new — surfaced by the A2 probe)
  #551 Ior ──────────┐       │ │  tolerant carrier (parallel core type)
                     │       │ │
  assembly builder ◄─────────┼─┘  depends: #549            ── "located accumulation" core
  labelling / E7   ◄─────────┼────  depends: #549          ──/
                     │       │
  refraction optic ◄─┴───────┴────  depends: #549 + assembly + labelling

PHASE 1 — first user-visible feature
  A2  Patch / multi-edit  ◄───────  depends: assembly + labelling + Endo     (validates the foundation)

PHASE 2 — marquee feature
  A1  bidirectional mapper ◄───────  depends: assembly + labelling + refraction
        └─ follow-ons: @DeriveMapping codegen · multi-source merge · third-party (@ImportOptics-style) mapping
        └─ NOTE: A2's Patch IS the mapper's `patch` — one mechanism, shared

ALONGSIDE (cross-cutting):  A4 telescope-grade diagnostics (baked into the new processors) · A5 setup on-ramp
DEFERRED:                   A3 auto-detecting bean writers (only when the mapper targets the JPA/bean market)
```

## Work items

| Item | Kind | Depends on | Status | Doc / issue |
|---|---|---|---|---|
| **#549 `NonEmptyList`** | core type | — | **issue open** | GitHub #549 |
| **#551 `Ior` / `These`** | core type | — | **issue open** | GitHub #551 |
| **`Endo<S>` monoid** | `Monoids` addition | — | surfaced (no issue) | `PHASE1-MULTI-EDIT-ANALYSIS.md` |
| **Assembly builder** (`accumulate()`) | validation ergonomics | #549 | **draft** | `DRAFT-ISSUE-open-arity-validated-assembly.md` |
| **Field-path labelling / `FieldError`** (E7) | optics + errors | #549 | **draft** | `DRAFT-ISSUE-optic-path-labelling.md` · `PHASE0-FIELD-PATH-LABELLING-ANALYSIS.md` |
| **Refraction optic** (validated prism) | optic | #549, assembly, labelling | **draft** | `DRAFT-ISSUE-validated-refraction-optic.md` |
| **A2 — `Patch` / multi-edit** | feature | assembly, labelling, `Endo` | **draft** | `DRAFT-ISSUE-multi-edit-patch.md` · `PHASE1-MULTI-EDIT-ANALYSIS.md` |
| **A1 — `@DeriveMapping`** | feature | assembly, labelling, refraction | designed + **draft** | `DRAFT-ISSUE-derive-mapping.md` · `BIDIRECTIONAL-MAPPING-DESIGN.md` · `PHASE2-MAPPER-ANALYSIS.md` |
| Mapper follow-ons (merge · third-party) | feature | A1 | **draft** | `DRAFT-ISSUE-mapper-follow-ons.md` |
| A4 — processor diagnostics | quality | the new processors | **draft** | `DRAFT-ISSUE-mapper-adjacent.md` |
| A5 — setup on-ramp | adoption | — | **draft** | `DRAFT-ISSUE-mapper-adjacent.md` |
| A3 — bean writers | feature | A1 | **fast-follow** (revised — `CopyStrategy` exists) | `PHASE2-MAPPER-ANALYSIS.md` |

## Build order (and why)

0. **Carriers — `#549` + `Endo`** (parallel, independent). Both are small, standard, broadly useful, and unblock everything above. `Endo` was surfaced late by the A2 probe; land it with #549.
1. **Located-accumulation core — assembly builder + labelling (E7)** (parallel; both need #549). Together they are the “report every bad field, each located” engine. This is the highest-leverage layer — it powers *all* of validation, both features, and `hkj-spring`.
2. **Refraction optic** — the parse-don’t-validate boundary primitive; needs 0 + 1.
3. **A2 `Patch`** — first user-visible win; needs the core + `Endo`. Ships fast and *validates the foundation* (second independent consumer).
4. **A1 mapper** — the marquee; needs the core + refraction. **Subsumes A2’s `patch`** (one mechanism). Then its follow-ons (codegen, merge, third-party).
   - **Alongside:** A4 (bake telescope-grade diagnostics into the new processors from day one) · A5 (frictionless codegen on-ramp for the new audience).
   - **Deferred:** A3 (bean writers) — only when the mapper reaches the JPA/bean market.

## Cross-cutting notes (caught by the analyses)

- **`Endo<S>` monoid** — the one new primitive both features share for “fold N writes”; powers A2’s pure patch (and `overIfPresent(absent) = identity`). Fold the dependency into the A2 issue (or a micro-issue) and note it on #549/assembly.
- **E7 value-level labelling** — A2 is the first consumer that labels from a *value-level* `FocusPath` (not codegen), so the labelling issue should confirm a value-level path surfaces its segment, or accepts `over(path, "label", fn)`.
- **#551 tolerant variants** — `Ior` gives the “apply/parse with warnings” twins of the assembly builder, the refraction, and A2’s patch; same NEL channel, same mechanism.
- **A1 ≡ A2 for patching** — the mapper’s sparse overlay is exactly an A2 multi-edit; keep them one implementation.

## Foundation validation

Two **independent** features converge on the same Phase 0 substrate:

- **A1 mapper** — `parse` = assembly builder + refraction + labelling (+ #549).
- **A2 patch** — validated patch = assembly builder + `Endo` + labelling (+ #549).

That convergence is the evidence the foundation is correctly scoped. The probes surfaced **one** missing primitive (`Endo`) — small, standard, additive — before any code was written. None of it touches the optic composition core or the van Laarhoven engine; all of it is additive plus two naming cleanups.

## Document index

| Doc | Role |
|---|---|
| `TELESCOPE-COMPARATIVE-ANALYSIS.md` | origin — telescope vs HKJ; recommendations A1–A5 + the reflection/codegen axis |
| `BIDIRECTIONAL-MAPPING-DESIGN.md` | A1 design (§§1–10): mapping-as-optic, profunctor crossover, API readiness review |
| `PHASE0-VALIDATED-ASSEMBLY-ANALYSIS.md` | assembly builder + `NonEmptyList` deep analysis |
| `PHASE0-FIELD-PATH-LABELLING-ANALYSIS.md` | E7 labelling deep analysis |
| `PHASE1-MULTI-EDIT-ANALYSIS.md` | A2 patch deep analysis + foundation validation |
| `DRAFT-ISSUE-*.md` (×4) | postable issue drafts: assembly · refraction · labelling · patch |
| `VALIDATION-MAPPING-ROADMAP.md` | this page |

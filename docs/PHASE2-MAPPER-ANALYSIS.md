# Phase 2 Analysis: The Mapper (A1) + Adjacents — worth-doing verdicts

**Status:** design analysis (no implementation)
**Assesses:** `@DeriveMapping` (record↔record), merge, third-party mapping, A4 (processor diagnostics), A5 (on-ramp), A3 (bean writers). Determines worth and grounds the draft issues. Builds on `BIDIRECTIONAL-MAPPING-DESIGN.md` and the Phase 0/1 analyses.
**Headline:** *The mapper processor is mostly an **assembly of existing `hkj-processor` components**, not a new build — which raises its feasibility and makes both follow-ons (merge, third-party) and beans (A3) cheaper than first assumed.*

## The reuse finding (changes the estimates)

`hkj-processor` already ships the parts a structural mapper needs (`hkj-processor/src/main/java/org/higherkindedj/optics/processing/`):

- **`external/TypeKindAnalyser` + `TypeAnalysis`** — classify a type as record / sealed / enum / bean. This *is* the mapper’s “classify the relationship” step (§ mapping-as-optic).
- **`external/ContainerType`** — List / Optional / Map shape detection. The mapper’s container-lifting, already written.
- **`external/CopyStrategy` + `CopyStrategyCodeGenerator` + `ExternalLensGenerator` + `WitherInfo`** — bean write strategies (builder / wither / copy-set) for `@ImportOptics`/`OpticsSpec`. **This is the bean-writer machinery A3 needs — it already exists.**
- **`ImportOpticsProcessor`** — generates optics for *un-owned* classes listed by `.class`. The pattern third-party mapping reuses verbatim.
- **`IsoProcessor` / `LensProcessor` / `PrismProcessor`** — emit the optic values (JavaPoet `FieldSpec`/`MethodSpec`); the mapper emits an `Iso`/`Lens` the same way.
- **`ProcessorUtils`, `OpticExpressionResolver`, `CoupledLensGenerator`** — shared helpers; `CoupledLensGenerator` already emits `Lens.paired` (relevant to A2).

So `@DeriveMapping` is **new glue** (a field-correspondence classifier + a `parse`-body generator) over **existing analysers and emitters** — materially more feasible than a green-field processor.

Diagnostics baseline (for A4): processors report via `error(msg, element) → Messager.printMessage(Diagnostic.Kind.ERROR, …)` with **terse one-liners** (e.g. `FocusProcessor.java:147` “The @GenerateFocus annotation can only be applied to records.”) — correct but no *why/fix*. The gap A4 targets is real and the baseline is confirmed.

---

## Verdicts

### 1. `@DeriveMapping` (record↔record) — **WORTH (marquee).** Draft: `DRAFT-ISSUE-derive-mapping.md`

The whole point (A1). Decomposition, all grounded in reuse:

1. **Classify** the source/target with `TypeKindAnalyser`; **match** components by name + type.
2. **Same name+type →** identity; **same name, mappable type** (an in-scope `Mapping`/`Codec`/`Refraction`) → recurse; **container** (`ContainerType`) → lift; **mismatch / unaccounted target field →** *compile error* (exhaustiveness).
3. **Emit** an `Iso` (lossless) or `Lens` (lossy projection — `patch` falls out) via the existing emitters; expose a `Mapping` facade (`render`/`parse`/`asIso`).
4. **Generate `parse`** as the assembly builder over per-field `Refraction`s, located via E7 — *all* field errors, each path-tagged.
5. **Generate a law test** (round-trip / GetPut-PutGet) into the existing `LensLawsTestFactory`/`PrismLawsTestFactory` harness.

Depends on the **refraction** (leaf optic) + **assembly builder** + **labelling** (+ #549). The refraction issue is the *leaf*; this is the *structural derivation* on top. **Worth doing — it is the feature; the reuse makes it tractable.**

### 2. Multi-source **merge** (N sources → one target) — **WORTH (modest, cheap).** Draft: `DRAFT-ISSUE-mapper-follow-ons.md`

Forward-only assembly from several sources into one target (telescope’s `Telescope.merge`; I read its `Merge.java` — auto-backfill by name+type, no general inverse). In HKJ it’s the assembly builder fed from N sources, with no `parse`/inverse. Real but less common than 1:1 mapping; small once the assembly builder exists. **Worth doing as a cheap follow-on; lower priority.**

### 3. **Third-party** (un-owned) mapping — **WORTH (cheap).** Draft: `DRAFT-ISSUE-mapper-follow-ons.md`

`@DeriveMapping` listing foreign classes by `.class` (the `ImportOpticsProcessor` pattern, `@Target({PACKAGE,TYPE})`, `Class<?>[]`). Closes the matrix row where telescope only offers reflection (HKJ stays compile-time, reflection-free). Cheap because `ImportOpticsProcessor` already does the un-owned-type plumbing. **Worth doing; pairs naturally with the mapper.**

### 4. **A4 — telescope-grade processor diagnostics** — **WORTH (cross-cutting, low effort).** Draft: `DRAFT-ISSUE-mapper-adjacent.md`

The new processors (mapper, and the generated refractions) will emit many new diagnostics (exhaustiveness failure, type-mismatch, non-invertible-declared-`Iso`, missing codec). Baseline messages are terse one-liners. Bake a *what / why / fix* bar into the new processors from day one (cheaper than retrofitting), and audit the existing ones. **Worth doing — pure ergonomics ROI, no architectural risk.**

### 5. **A5 — codegen on-ramp** (plugin auto-wiring + quickstart) — **WORTH (low effort, adoption).** Draft: `DRAFT-ISSUE-mapper-adjacent.md`

The mapper is the marquee entry point that draws new users (§ reflection/codegen axis); high-friction setup loses them. The `hkj-gradle-plugin`/`hkj-maven-plugin` already exist — have them auto-wire the processor, plus a sub-minute quickstart. **Worth doing alongside A1.**

### 6. **A3 — bean writers** — **REVISED: less deferred than thought; do *with* the mapper, opt-in.**

Earlier I deferred A3 as “the bean-writer half, build later.” The reuse finding changes this: **`CopyStrategy` / `ExternalLensGenerator` already implement bean write strategies** for `@ImportOptics`. So a mapper targeting a bean source/target mostly *reuses existing machinery* — the marginal cost is wiring, not new write logic. **Revised verdict:** keep it opt-in and secondary to records (immutability-first), but it is a **low-marginal-cost extension to land with the mapper**, not a far-future item. Thin issue body below; promote when the mapper lands.

> **A3 thin issue (deferred-but-ready):** *Allow `@DeriveMapping` source/target to be a JavaBean*, reusing `external/CopyStrategy` for the write side (auto-detect builder → wither → copy-set, overridable). Records stay the default; beans never leak mutation into the pure surface. Depends on the mapper. Promote from “deferred” to “fast-follow” given the existing `CopyStrategy` reuse.

---

## Relationship to the existing drafts

- **B2 / refraction** (`DRAFT-ISSUE-validated-refraction-optic.md`) is the **leaf** optic; **`@DeriveMapping`** is the **structural derivation** that assembles leaf `Refraction`s/`Codec`s into a whole-record `Mapping`. Two issues, clean layering.
- The mapper’s `patch` **is** A2’s `Patch` (one mechanism); the mapper’s `parse` **is** the assembly builder + refractions + labelling. The mapper adds only: the *classifier*, the *emit*, and the *generated law test*.

## Net

All six are worth doing. Sequencing within Phase 2: **`@DeriveMapping` first** (needs refraction + assembly + labelling), then **third-party** and **merge** (cheap follow-ons), with **A4** baked into the processor and **A5** alongside. **A3** is promoted from deferred to *fast-follow* (low marginal cost via existing `CopyStrategy`), still opt-in. Nothing here is a green-field build; the heaviest piece (`@DeriveMapping`) is glue over existing analysers.

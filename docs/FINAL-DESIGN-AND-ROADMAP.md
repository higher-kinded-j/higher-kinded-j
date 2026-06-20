# FINAL — Validation & Bidirectional Mapping: Corrected Design & Roadmap

**This is the canonical, single-surface document.** It consolidates the program and applies every correction from the six-reviewer panel (`EXPERT-REVIEW.md`). Where it disagrees with any earlier doc, **this wins**. The supporting docs (`TELESCOPE-COMPARATIVE-ANALYSIS`, `BIDIRECTIONAL-MAPPING-DESIGN`, the three `PHASE*` analyses, the eight `DRAFT-ISSUE-*`, the old `VALIDATION-MAPPING-ROADMAP`) are retained as provenance/detail; read them through the corrections in §9 + Appendix A.

---

## 1. Thesis (verified)

A record↔DTO mapping is **the weakest lawful optic the relationship admits** — derive it at compile time, reflection-free, law-checked, effect-aware. The panel verified this is sound, idiomatic, and better than telescope (no fabricated total inverse) and MapStruct (bidirectional + effect-aware). The corrected taxonomy:

| Domain ↔ wire relationship | Render (domain→wire) | Parse (wire→domain) | Optic |
|---|---|---|---|
| Lossless bijection | total | total | **`Iso`** |
| Wire is a **lossy projection**, write-back lawful | total | needs a base | **`Lens`** |
| Wire **computable, no lawful write-back** (derived/read-only) | total | — | **`Getter` / `Fold`** *(added per review — the taxonomy was non-exhaustive)* |
| Wire→domain is a **validated parse** | total (`build`) | fallible, accumulating | **`Refraction`** (a `Prism` whose match is `Validated`) |
| Sealed / ADT case | inject | match one case | **`Prism`** |

**Corrected (F1): `patch` is *not* `Lens.set`.** A *sparse* patch (only present fields overwrite) is a **monoidal fold of per-field `Endo<S>` writes** (`overIfPresent(absent) = identity`) — this is the `Edits` builder (§6). Only a **total** projection write equals `Lens.set`. The earlier "patch = Lens.set" slogan was a category error.

## 2. Locked naming & vocabulary (single source of truth)

The panel found three blocker-level collisions. Resolved here; ratify or override on review.

| Concept | **Locked name** | Was | Why |
|---|---|---|---|
| Multi-edit builder | **`Edits`** (`Edits.fold` → `Endo<S>`; `Edits.accumulate` → `Validated<NEL<FieldError>,S>`) | `Patch` | `Patch` collides with `Path` (Effect-Path facade), *in the same expressions* |
| Per-edit factories | `Edit.over / overIfPresent / mapIfPresent` | (same) | unchanged |
| Mapper annotation | **`@GenerateMapping`** | `@DeriveMapping`/`@GenerateMapper` | aligns with the documented `@Generate*` family |
| Mapper spec | **`MappingSpec<A,B>` interface** (OpticsSpec-style; per-field overrides as annotated abstract methods) | nested `@Rename[]/@Via[]/@Drop[]` arrays | nested-annotation arrays have **no HKJ precedent**; `OpticsSpec`/`@ImportOptics` is the idiom |
| Rename row | **`@MapField(to="…")`** | `@Via(field=…)` | `@Via*` is the existing copy-strategy family; `.via` is optic composition |
| Leaf converter | **`Codec<A,B>`** (a hand-written `Iso` or `Refraction`) — documented as *"an `Iso`/`Refraction` you write by hand,"* never a new abstraction | (same) | avoid a lattice-hiding facade (Part D) |
| Reverse direction (whole new family) | **`render`** | mixed `render`/`build`/`reverseGet` | one verb; `build` kept only as the optic-law alias on `Refraction` |
| Error carrier | **`NonEmptyList<FieldError>` everywhere** (a leaf wraps a single error in a singleton) | `Validated<FieldError,…>` at leaves | F8 — single-error leaves can't accumulate |
| `FieldError` | **`record FieldError(Path path, String message)`** | three informal shapes | locked once; others reference it |
| `Endo` surface | **`Monoids.endo()`** | `Endo.MONOID` | matches `Monoids.list()/set()/…` noun factories |
| Validated prism | **`Refraction`** *(ratify — see §5)* | (same) | no standard optic name; a deliberate choice |

## 3. What is unconditionally worth doing (substrate)

Independently valuable for *all* validation and `hkj-spring`, regardless of whether the mapper ships:

- **`NonEmptyList`** (#549) — total `head`/`reduce`; the default accumulating carrier.
- **`Endo<S>` monoid** via `Monoids.endo()` — genuinely absent today; powers the pure multi-edit.
- **`accumulate()` builder** — fills the real gap (`map5` ceiling confirmed). **Corrected (F2):** `Tuple3..Tuple12` *already ship* (`Tuple.java`, via `@GenerateForComprehensions`), so the curried-`ap` choice must be argued on its **true merits** (HKT-free surface; one shape over `Validated`/`ValidationPath`/`Ior`; drops the per-call `Semigroup`), *not* on a non-existent tuple gap — and a `tupled()` form is also already viable. **Prior art (F12):** `OpticOps.modifyAllAccumulating` already accumulates over *one* traversal; the builder's novelty is *heterogeneous, multi-arity* assembly. Pin **left-to-right** accumulation order (NEL concat is non-commutative).
- **`Edits` multi-edit / sparse PATCH** (§6).

## 4. Field-path labelling (`FieldError`)

Built on existing machinery: `IndexedOptic` already threads a "where" and composes it (`iandThen → Pair<I,J>`); `IndexedFold.ifoldMap` collects located results; the codegen already holds field names. **Locked:** `FieldError(Path, message)` on the `NonEmptyList` channel. **MVP** uses a **flat-string `Path`**; the full sealed `PathSegment` (`Field`/`Index`/`Key`/`Case`) model + `Monoid<Path>` is a **later generalisation**, kept a strict *specialisation* of `IndexedOptic` (one locating mechanism, not two).

## 5. `Refraction` — promoted to a first-class, deliberated optic

Per the steward + optics reviewers, this is a **new public optic family** in a "don't hide the lattice" library — it gets its own decision, not an assembly-builder follow-on:

- **Name** to ratify: `Refraction` vs `ValidatedPrism`/`ParsePrism`.
- **Both laws (F10):** `parse(build(a)) == Valid(a)` **and** `parse(s) == Valid(a) ⇒ build(a) == s` (`build` is a section of `parse` — forbids lossy "parse-normalise" that breaks round-trip).
- **Composition matrix:** specify & test all `andThen` cells with `Iso`/`Lens`/`Prism`/`Refraction`; `andThen` (nesting) short-circuits, sibling assembly accumulates.
- **`render`/`build`** naming per §2; bridges to `ValidationPath`.
- For the MVP (§8) a *throwaway hand-written* refraction suffices; the real optic is its own issue.

## 6. `Edits` — multi-edit / sparse PATCH (corrected semantics)

- **Pure:** `Edits.fold(over(P,fn), overIfPresent(P,v), …) : Endo<S>` — `foldMap` into `Monoids.endo()`; absent ⇒ identity. (Write is `set` for value-edits, `modify` for function-edits — F-minor.)
- **Validated:** `Edits.accumulate(…) : Validated<NEL<FieldError>, S>` = the `accumulate()` builder over `Validated<NEL, Endo<S>>`.
- **Corrected (F7) — two phases, not "parallel-on-original":** ① each edit *validates its value independently* (parallel ⇒ accumulation is sound); ② the resulting writes are applied by a **sequential `foldLeft`** onto the source (order-independent only for **disjoint** paths; coupled paths use the existing `Lens.paired`).
- **`Edits` *is* the mapper's `patch`** — one mechanism; generalises `Optics.modifyAllAccumulating` to heterogeneous multi-path edits.

## 7. `@GenerateMapping` — the mapper (honestly re-priced)

**Corrected (F4): not "glue."** The *classify* step genuinely reuses `TypeKindAnalyser` (RECORD/SEALED/ENUM/WITHER/UNSUPPORTED) and `ContainerType` (one-level List/Optional/Map). Everything else is **new code**:

- the cross-record **emit** body (component-wise construction, container *recursion* — `ContainerType` only detects one level, sealed→prism dispatch);
- the **`parse`** assembly (the `accumulate()` builder over per-field `Refraction`s);
- **exhaustiveness (F9):** enforce in the **processor** via `Messager.ERROR` (a set-difference over `target.getRecordComponents()`), **not** `hkj-checker` (a Tree-phase plugin with no view of mapper semantics);
- **recursion** resolution + **termination** (cyclic record graphs need a visited-set/depth cap; resolve via same-round source annotations, per the `FocusProcessor` precedent);
- **law tests (F3):** there is **no `IsoLaws`**, and the `Lens`/`Prism` factories are private `hkj-core/src/test` JUnit classes a processor can't target — a **new published optic-law harness** (in `hkj-test`) must be built; treat as its own sub-feature.

**Surface:** lead with `interface UserMapping extends MappingSpec<User,UserDto> {}` (empty = identity match; per-field overrides as annotated abstract methods). **Records-first.** The true critical path is **the processor + exhaustiveness/diagnostics**, where annotation-processor projects routinely overrun — price it as net-new, not assembly.

**Corrected (F5): A3 beans is NOT fast-follow.** `CopyStrategy` has **no auto-detection** (it emits only from explicit `@ViaBuilder`/`@Wither`), and `TypeKindAnalyser` has **no general bean category** (a plain bean is `UNSUPPORTED`). Bean targets need a *new* builder/bean detector. **Corrected (F6):** `CoupledLensGenerator` does **not** emit `Lens.paired` (it emits an arity ladder via `Lens.of`).

## 8. Roadmap — vertical-slice first (corrected sequencing)

The panel was unanimous that **foundations-first then big-mapper is the wrong shape** (100% design / 0% code; marquee gated behind 5+ greenfield primitives). Reconciled path:

**Step 0 — Thin vertical slice (the MVP that proves the whole thesis):**
one strict `@GenerateMapping` on a **3-field record, one validated leaf**, end-to-end onto **one `hkj-spring` 422** — with *minimal* substrate: flat-string `FieldError`; `accumulate()` only at the needed arity; a throwaway hand-written `Refraction`; **no** `PathSegment` model, **no** `Endo`/`Edits`, **no** composition matrix, **no** generated law tests. This surfaces the real processor/checker risk immediately and ships visible value.

**Then — generalise each piece only as the slice proves it needed**, in dependency order:
`NonEmptyList` (#549) → `accumulate()` builder (true arity) → `Endo`/`Edits` → `FieldError`/`PathSegment` model → `Refraction` (first-class) → full `@GenerateMapping`.

**Land independently (don't wait on the mapper):** `#549`, the `accumulate()` builder, and `Edits` — they improve validation/`hkj-spring` on their own merits.

**Build when claimed:** the **published optic-law harness** (needed for the "law-checked" guarantee; none exists).

**Records-first governance — defer to demonstrated demand:** A3 bean writers, multi-source `merge`, the Spring `Mapping` registry, all `Ior`/#551 tolerant twins, generated per-mapping law tests, the full `Function2..12` ladder, the rich `Path` model. Tier-4 profunctor stays unbuilt.

```
Step 0  vertical slice: 3-field strict @GenerateMapping -> 422   (proves thesis, de-risks processor)
        │
Subst.  #549 NonEmptyList · accumulate() builder · Endo/Edits     (land independently; unconditionally useful)
        │
Optic   FieldError/PathSegment model · Refraction (first-class: name+laws+matrix) · optic-law harness
        │
Bet     full @GenerateMapping (records)  ── separate go/no-go ──►  [deferred-to-demand: beans, merge, registry, Ior twins]
along   A4 processor diagnostics · A5 codegen on-ramp
```

## 9. Decisions — for your review

1. **Greenlight scope:** the **substrate** (`#549` + `accumulate()` + `Edits`) is unconditionally worth doing and safe to post/build now. The **mapper** is the strategic bet, now honestly priced as net-new processor + exhaustiveness work — *separate go/no-go*. **Your call: substrate-only, or substrate + mapper-slice?**
2. **Sequencing:** adopt the vertical-slice-first path (§8)? *(Recommended.)*
3. **Records-first governance:** confirm deferral of beans/merge/registry/`Ior` twins until users ask. *(Recommended.)*
4. **Names:** ratify the §2 table — especially `Edits` (was `Patch`), `@GenerateMapping`, and `Refraction`.
5. **Posting:** the eight issue drafts carry pre-review names (`Patch`, `@Via`, `@DeriveMapping`) and a few corrected claims — I will apply the §2 names + Appendix-A fixes to the drafts on your word before any are posted.

---

## Appendix A — corrections incorporated (traceability)

From `EXPERT-REVIEW.md`. **Fixed in this doc; supporting docs flagged.**

| F | Correction | Status |
|---|---|---|
| F1 | `patch` = `Endo` fold, not `Lens.set` (only *total* write = `Lens.set`) | §1 — fixed; also fixed in `BIDIRECTIONAL-MAPPING-DESIGN.md` §1 |
| F2 | `Tuple3..Tuple12` already ship; re-argue curried-`ap` on merits | §3 — fixed; also fixed in `PHASE0-VALIDATED-ASSEMBLY-ANALYSIS.md` |
| F3 | No optic-law harness to reuse — build a new published one | §7, §8 |
| F4 | Mapper = net-new emit/parse, not "glue" (classify only is reuse) | §7 |
| F5 | A3 beans not fast-follow (no bean auto-detect; bean = UNSUPPORTED) | §7 |
| F6 | `CoupledLensGenerator` ≠ `Lens.paired` | §7 |
| F7 | Validated patch: parallel validation + sequential write fold | §6 |
| F8 | `NEL<FieldError>` channel everywhere (leaves wrap singletons) | §2 |
| F9 | Exhaustiveness in the processor (`Messager.ERROR`), not `hkj-checker` | §7 |
| F10 | Refraction needs the second prism law | §5 |
| F11 | Profunctor "face/half-way" softened to "named end-adapters; full encoding = Tier 4" | §5 ref / design §5 |
| F12 | Acknowledge `OpticOps.modifyAllAccumulating` prior art | §3, §6 |
| F13 | Getter rung added; `Iso` container-lift necessary; verbs/citations/counts; left-to-right order | §1–§4 |

**Naming blockers:** `Patch`→`Edits`, `@Via`→`@MapField`, annotation-arrays→`MappingSpec` interface, reverse-verb→`render`, `@DeriveMapping`→`@GenerateMapping`, `Endo.MONOID`→`Monoids.endo()` — §2.

## Appendix B — document map

- **Canonical:** this file.
- **Origin / still-valid:** `TELESCOPE-COMPARATIVE-ANALYSIS.md` (the comparison; its findings stand), `EXPERT-REVIEW.md` (the panel).
- **Superseded for the corrected state (provenance/detail):** `BIDIRECTIONAL-MAPPING-DESIGN.md`, `PHASE0-VALIDATED-ASSEMBLY-ANALYSIS.md`, `PHASE0-FIELD-PATH-LABELLING-ANALYSIS.md`, `PHASE1-MULTI-EDIT-ANALYSIS.md`, `PHASE2-MAPPER-ANALYSIS.md`, `VALIDATION-MAPPING-ROADMAP.md`, the eight `DRAFT-ISSUE-*.md` (apply §2 names before posting).

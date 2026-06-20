# Expert Review Panel — Synthesis

> ✅ **Resolved:** the corrections and naming decisions from this review are incorporated in the canonical **`FINAL-DESIGN-AND-ROADMAP.md`** (see its Appendix A for F1–F13 traceability). This review is retained as the rationale.

Six independent reviewers (four specialist, two holistic) audited the validation & mapping design set on this branch, each **verifying every load-bearing claim against the actual HKJ source (file:line)**. This synthesis attributes findings, maps where the panel agrees and diverges, lists the **objective factual corrections** the docs need regardless of strategy, and ends with **one prioritised actionable table** plus the open decisions that are yours to make.

> Candid note: most of the factual corrections below are errors in documents *I* authored. The panel did its job — read this as the correction of optimism, not a verdict that the idea is wrong. The core thesis survived scrutiny.

## The reviewers

| | Lens | Verdict (one line) |
|---|---|---|
| **R1** | Optics & category theory | Foundation largely sound and accurately grounded; two headline claims wrong (`patch`=`Lens.set`; law-test reuse). Go-with-corrections. |
| **R2** | Validation & HKT semantics | Core semantics verified sound; one **blocker factual error** (the "only `Tuple2`" premise is false). |
| **R3** | API ergonomics & Java idioms | Concepts strong; **naming surface not ready to post** (3 collisions + non-idiomatic annotation arrays). |
| **R4** | Codegen / processor feasibility | Mapper feasible, but "glue over existing components" overstates reuse; emit/parse/law-tests/beans are new code. |
| **R5** | Software architect (holistic) | Fundamentally sound but **sequenced as a research project** (100% design / 0% code); build a thin vertical slice instead. |
| **R6** | Project steward / idiom fit (holistic) | On-mission and HKJ-idiomatic; biggest risk is **blast radius** — hold the records-first line, gate framework follow-ons. |

## Consensus verdict

The central reframing — **a mapping is the weakest lawful optic the relationship admits** (`Iso`/`Lens`/`Refraction`), so immutability, composition, and `patch` come from the lattice — is **correct, idiomatic, and genuinely better than telescope/MapStruct**, and the panel verified the structural substrate (the `andThen` narrowing matrix, the van-Laarhoven/profunctor seam, the indexed-optic labelling base, the accumulate/short-circuit duality) is real and accurate. The substrate primitives (`NonEmptyList`, `Endo`, the `accumulate()` builder, `Patch`) are sound and mostly additive. **But**: (1) several load-bearing claims are factually wrong and must be corrected before any go/no-go; (2) the mapper's feasibility rests on a "reuse" story that is real for the *classify* step but **overstated** for emit/parse/law-tests/beans — the processor + `hkj-checker` exhaustiveness is the true critical path; (3) the program is **100% design / 0% code** and should pivot to a thin vertical slice; (4) **naming and records-first scope** need decisions before issues are posted.

## Where the panel agrees (high signal — ≥2 reviewers)

1. **The law-test "reuse" is an overclaim.** No `IsoLaws` exists; `LensLawsTestFactory`/`PrismLawsTestFactory` are *private JUnit classes in `hkj-core/src/test`* (not the published `hkj-test` harness, not jqwik), which a processor's `Filer` cannot target. It is a **new sub-feature**, not reuse. — **R1, R4**
2. **The mapper's "glue over existing components" overstates reuse.** Classify (`TypeKindAnalyser`, `ContainerType`, component introspection) is genuine reuse; the cross-record **emit** body, the `parse`/refraction assembly, generated law tests, and bean writers are **new code**. Re-price accordingly. — **R4, R5, R6**
3. **A3/beans is *not* fast-follow.** `CopyStrategy` has **no auto-detection** (emits only from explicit `@ViaBuilder`/`@Wither`), and `TypeKindAnalyser` has **no general bean category** — a plain bean is `UNSUPPORTED`. The "promote to fast-follow via existing CopyStrategy" claim is wrong. — **R4, R5**
4. **"HKJ has only `Tuple2`" is false.** `Tuple3..Tuple12` ship in `hkj-core` (`Tuple.java:26-37`, generated via `@GenerateForComprehensions`). The curried-`ap` builder must be re-argued on its true merits (HKT-free surface, one shape over Validated/Ior/ValidationPath), not on a non-existent tuple gap. — **R2, R5**
5. **Naming + `FieldError` shape must be fixed before posting.** `Patch` collides with `Path`; `@Via` is overloaded three ways; nested-annotation arrays have no HKJ precedent (use an `OpticsSpec`-style interface); `FieldError`'s shape and the `NEL<FieldError>` channel must be locked once and referenced. — **R3, R6** (+ R1 on FieldError)
6. **Hold the records-first line; gate the framework-shaped follow-ons.** `@DeriveMapping` + beans + merge + Spring registry + `Ior` twins, *in aggregate*, is a MapStruct-style framework. Greenlight the optic-derivation core; defer beans/merge/registry/tolerant-twins to demonstrated demand. — **R6, R5**
7. **"No HKT tax" should read "no `Kind` in *user* code."** True at the call site (FocusPath already proves the pattern), but the implementation rides the usual `WitnessArity`/`Kind` machinery, and the `apply(Function2..12)` overload ladder is its own cost. — **R2, R3**

## Where the panel diverges (genuine forks for you)

- **Sequencing.** **R5 (architect)** wants a *thin vertical slice* — one end-to-end strict `@DeriveMapping` on a 3-field record — built first, to prove the thesis and surface the real processor/checker risk immediately, inverting foundations-first. **R6 (steward)** wants the *independently-valuable substrate* (NEL/Ior/Endo/assembly/Patch) landed first, with `@DeriveMapping` as a **separate go/no-go** afterward. They agree foundations-first-then-big-mapper is the wrong shape and that the mapper is the risk; they differ on vertical-vs-horizontal. **Reconciliation (my read):** do the vertical slice *first* (it already includes minimal substrate), let it prove the processor/checker spine, then generalise each substrate piece *as the slice shows it's needed*, treating full `@DeriveMapping` scope as R6's separate go/no-go. This satisfies both.
- **Refraction's weight.** **R6** says it's a *new public optic family* deserving its own deliberated decision (name/laws/composition matrix), not an "assembly-builder follow-on." **R1** says its law set is incomplete (needs the second prism law). **R5** says the MVP should *hand-write a minimal refraction with no composition matrix*. **Reconciliation:** the MVP uses a throwaway hand-written refraction; the *real* `Refraction` optic becomes its own first-class issue (full laws + 5×5 composition matrix + name decision) — promoted out of the assembly issue.

## Factual corrections required (objective — fix regardless of strategy)

| # | Claim in the docs | Reality (evidence) | Affected docs | Flagged by |
|---|---|---|---|---|
| F1 | "`patch` is not a feature — it is `Lens.set`" | Category error. Sparse patch = a fold of per-field `Endo` writes (`overIfPresent(absent)=identity`); `Lens.set(A,S)` overwrites the *whole* projection. Only a **total** DTO write = `Lens.set`. A2 itself says this. | DESIGN §1 | R1 (blocker), R2 |
| F2 | "HKJ has only `Tuple2`, no `Tuple3+`" | False — `Tuple3..Tuple12` ship (`Tuple.java:26-37`, `@GenerateForComprehensions`). | PHASE0-ASSEMBLY §1/§3/§8; assembly draft | R2 (blocker), R5 |
| F3 | Generated law tests "plug into the existing `LensLawsTestFactory`/`PrismLawsTestFactory` harness" | No `IsoLaws` exists; the factories are private `hkj-core/src/test` JUnit, not published, not jqwik; `Filer` can't target them. New harness needed. | DESIGN §4.6/§6/Tier2; derive-mapping; PHASE0-labelling §9 | R1, R4 |
| F4 | `@DeriveMapping` is "glue over existing components" | Classify reuses `TypeKindAnalyser`/`ContainerType`; **emit/parse/law-tests/beans are new code**. | PHASE2 §reuse; derive-mapping | R4, R5, R6 |
| F5 | A3 beans "fast-follow via existing `CopyStrategy`" | No auto-detection; `TypeKindAnalyser` has no bean category; plain bean = `UNSUPPORTED`. | PHASE2 §6 | R4, R5 |
| F6 | "`CoupledLensGenerator` already emits `Lens.paired`" | It emits an arity-3–9 ladder via `Lens.of`; never calls `Lens.paired`. | PHASE2 reuse list | R5 |
| F7 | Validated patch "applies parallel-on-original" | Validation phase is parallel (why accumulation is sound); **write phase is a sequential `foldLeft`** (order-independent only for disjoint paths). Split the two. | PHASE1 §3/§6 | R2 |
| F8 | `Codec.parse : Validated<FieldError, …>` (single error) | Contradicts the `NEL<FieldError>` channel everywhere else; a single-error leaf can't accumulate. | DESIGN §6b | R3, R1 |
| F9 | Exhaustiveness "extend `hkj-checker`" | `hkj-checker` is a Tree-phase plugin with no view of mapper semantics; enforce in the **processor** (`Messager.ERROR`). | derive-mapping | R4 |
| F10 | Refraction laws | Missing the second prism law: `parse(s)==Valid(a) ⇒ build(a)==s` (forbids lossy "parse-normalise"). | refraction draft; labelling §9 | R1 |
| F11 | "van Laarhoven *with a profunctor face* / already half-way" | Oversell — the three same-named methods act on *structure ends*, not the categorical encoding; §5b build/project recovery is Tier-4, not current. `FunctionProfunctor` is never wired to optics. | DESIGN §5a/§5b/§9.4 | R1 |
| F12 | A2 "what HKJ already has" omits prior art | `OpticOps.modifyAllAccumulating` already does accumulating modify over one traversal; A2's novelty is the *heterogeneous multi-path* generalisation. | PHASE1 §2/§4 | R2 |
| F13 | Misc | Taxonomy missing the read-only `Getter`/`Fold` rung; `Iso` container-lift is *necessary* (not optional) for lossless leaf reuse; drifted ±2 line citations (`Setter`/`OpticOps`); "×4 drafts" miscount (7 files / 8 issues); pin left-to-right NEL accumulation order (non-commutative). | various | R1, R2, R3, R6 |

## Naming / consistency decisions before posting (P1)

- **Rename `Patch`** — collides with `Path`, the Effect-Path facade, *in the same expressions*. (R3 blocker) → `Edits` / `MultiEdit` / `Focus.patch`.
- **Rename the `@Via` codec annotation** — `.via` is optic composition; `@ViaBuilder/@ViaConstructor` is the copy-strategy family. (R3 blocker) → `@MapField`/`@Convert`.
- **Lead with an `OpticsSpec`-style `MappingSpec extends Mapping<A,B>` interface**, not nested `@Rename[]/@Via[]/@Drop[]` arrays (no HKJ precedent; MapStruct's idiom). (R3 major, R6)
- **Lock `FieldError`'s shape + the `NEL<FieldError>` channel** in the labelling issue; have the other docs reference it. (R3, R6, R1)
- **Unify the reverse-direction verb** (`render`) across `Codec`/`Mapping`/`Refraction`; keep `build` only as the optic-law alias. (R3)
- **Reconcile the annotation verb** with the `@Generate*`/`@ImportOptics` family (`@DeriveMapping` vs `@GenerateMapper`). (R6, R3)
- **Fix `Lens.of` arg-order (§10 R1)** before building `Refraction`/`Mapping`/`Patch` on it. (R3 major)

## The recommended path (synthesis)

1. **Pivot from design to a thin vertical slice (R5's MVP).** One strict `@DeriveMapping` on a 3-field record, one validated leaf, end-to-end onto one `hkj-spring` 422 — with *minimal* substrate (flat-string `FieldError`; `accumulate()` only at the arity the record needs; a throwaway hand-written refraction; no `PathSegment` model, no `Endo`/A2, no composition matrix, no generated law tests). This proves the entire thesis and surfaces the real processor/checker risk in a fraction of the effort.
2. **Generalise each substrate piece only as the slice proves it needed**, in the build order the dependency map already gives — but justified by the slice, not by an unbuilt foundation.
3. **Treat full `@DeriveMapping` scope as a separate go/no-go (R6)** after the slice; hold **records-first**, and defer **A3 beans, merge, the Spring registry, and all `Ior` twins** to demonstrated demand.
4. **Promote `Refraction` to its own first-class issue** (name + complete laws + composition matrix), out of the assembly follow-on.
5. **Apply the F1–F13 factual corrections** to the docs now (independent of strategy), and **make the P1 naming decisions** before any issue is posted.
6. **Stop expanding the doc set;** convert the roadmap into a tracked issue with the MVP slice as issue #1.

## Consolidated actionable items, prioritised

| # | Action | Type | Why / payoff | Reviewers | Effort |
|---|---|---|---|---|---|
| 1 | Apply factual corrections **F1–F12** to the docs | Fix-doc | The docs currently mislead a go/no-go (patch, Tuple, reuse, beans, law tests, profunctor) | R1,R2,R4,R5,R6 | S |
| 2 | Rename `Patch`; rename `@Via`; unify reverse-verb; lock `FieldError` shape | Decision+Fix | Removes blocker-level collisions before issues are posted | R3,R6,R1 | S |
| 3 | Lead the mapper spec with an `OpticsSpec`-style interface (not annotation arrays) | Decision | Aligns with `@ImportOptics`/`OpticsSpec`; idiomatic | R3,R6 | S |
| 4 | Re-price the mapper as **net-new emit + `hkj-checker` exhaustiveness** (true critical path) | Decision | Honest go/no-go; processor risk is where projects overrun | R4,R5 | S |
| 5 | **Build the thin vertical slice** (3-field strict mapper → 422) | Build | Proves the thesis + de-risks processor/checker; ships visible value | R5 | M |
| 6 | Fix `Lens.of` arg-order (§10 R1) before dependents | Build | Newcomer-facing footgun the new builders inherit | R1,R2,R3 | S |
| 7 | Hold records-first; defer A3 beans / merge / registry / `Ior` twins to demand | Decision | Keeps HKJ an FP/optics library, not a mapping framework | R6,R5 | — |
| 8 | Promote `Refraction` to its own issue (name + laws + composition matrix) | Decision | It's a new *public optic family*, not a follow-on | R6,R1 | S |
| 9 | Cut v1 gold-plating: generated law tests, full `Function2..12` ladder, rich `PathSegment`, `Endo`/A2 as independent follow-on | Decision | Less surface before a user exists | R5 | — |
| 10 | Build/publish an optic-law harness (`Iso`/`Lens`/`Prism`/`Refraction`) in `hkj-test` | Build | The "law-checked" guarantee needs it; none exists today | R1,R4 | M |
| 11 | Land the standalone substrate (`#549` NEL, `accumulate()` builder, A2 `Patch`) on their own merits | Build | Valuable for all validation/`hkj-spring` regardless of the mapper | R6 | M |
| 12 | Convert the roadmap to a tracked issue; stop adding design docs | Process | 14 docs / 0 code is the wrong ratio for a solo project | R5 | S |

## Open decisions for you

1. **Sequencing:** thin vertical slice first (R5) vs standalone substrate first (R6)? *(Panel reconciliation: slice first, generalise substrate as proven, full mapper as a separate go/no-go.)*
2. **Does `@DeriveMapping` get a green light at all, or only the substrate?** The substrate (NEL/assembly/Patch) is unconditionally worth doing; the mapper is the strategic bet whose feasibility is now honestly priced as net-new processor work.
3. **Records-first as governance:** are you willing to *defer* beans/merge/registry/`Ior` twins until users ask? (Both holistic reviewers say yes.)
4. **`Refraction` name + scope:** its own deliberated issue (recommended), or keep it bundled?
5. **Apply F1–F13 now?** I can correct the docs immediately on your word — they're objective fixes independent of the strategic choices above.

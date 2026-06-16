# Draft GitHub Issue — Open-Arity Validated Assembly

> **Working draft for review.** Everything under **“Issue body — copy from here”** is the postable content and follows the `#549`/`#551` template. The **Reviewer notes** above it are *not* part of the issue — delete/ignore before posting. Derived from `docs/PHASE0-VALIDATED-ASSEMBLY-ANALYSIS.md`.

**Suggested title:** `[FEAT] Add open-arity accumulating assembly (accumulate() builder) for Validated / ValidationPath / Ior`
**Suggested label:** `enhancement` (matching #549 / #551)

### Reviewer notes (decisions you may want to change)

- **Scope split.** I kept this issue to the **assembly builder** and flagged the **validated prism / “refraction”** optic as a *follow-on* (it crosses into the optics subsystem). If you’d rather bundle them — as originally floated — say so and I’ll inline the refraction section.
- **Naming.** `accumulate()` is a placeholder. Alternatives: `Validated.zipAll()`, `Validated.forEach()`, `Validated.builder()`, `Apply.of(...)`, a static `mapN(...)`. Pick the one that reads best beside the existing `ap` / `zipWithAccum`.
- **`.field(label, v)` vs `.and(v)`.** I propose both — labelled for the common case, unlabelled for leaves that already carry their own path. You may prefer only one.
- **Carriers.** I scoped it across `Validated` / `ValidationPath` / `Ior`. If `Ior` (#551) is far off, drop that bullet and add it when #551 lands.
- **`zipWithNAccum` family.** I suggest soft-deprecating `zipWith4Accum`+ once the builder exists; remove that line if you’d rather keep them first-class.
- **Dependency framing.** Written as **depends on #549**. If you’d rather it be self-contained (carry its own `Semigroup` until NEL lands), adjust “The gap” / “Ergonomic constraints”.

---

## Issue body — copy from here

## The gap

HKJ accumulates validation errors today, but assembling a **record from N validated fields** — the everyday case (a request DTO → a domain aggregate; a raw config → a settings object) — has no ergonomic, open-arity form. What exists:

- **Pairwise** — `Validated.ap(Validated<E, Fn>, Semigroup<E>)` and `ValidationPath.zipWithAccum(...)`. Both thread a `Semigroup<E>` on **every** call.
- **Fixed low arity** — `Applicative.map2 … map5`, capped at **five** fields and expressed in full `Kind` / HKT ceremony.
- **A proliferating family** — `ValidationPath.zipWith3Accum`, `zipWith4Accum`, … each implemented by **nesting `Tuple2`** (HKJ has only `Tuple2` — no `Tuple3+`) plus a `FunctionN`.

Three problems, all felt exactly when you reach for it:

- **It doesn’t scale past a handful of fields.** `map5` caps at five; the `zipWithNAccum` family is one method per arity, each a `Tuple2`-nesting boilerplate.
- **It’s ceremony-heavy.** Every step passes `Semigroups.list()` by hand, and the accumulation lives on `ValidationPath`, not on `Validated` itself.
- **Errors can’t say *where*.** None of these carries a per-field label, so an accumulated failure is a flat list with no `"address.zip"` path — consumers thread location manually.

This is the **assembly half** of HKJ’s headline validation story, and it is the piece that a bidirectional mapper’s `parse` and an accumulating partial-update both stand on. It also pairs directly with #549 and #551 (below).

## What “good” looks like (user perspective)

One flat, labelled, all-errors-at-once expression — no `Semigroup` argument, no arity wall, no `Kind`:

```java
// Strict: every bad field reported at once, each with its path.
Validated<NonEmptyList<FieldError>, User> user =
    Validated.accumulate()
        .field("name",  Name.parse(dto.name()))      // Validated<NEL<FieldError>, Name>
        .field("email", Email.parse(dto.email()))
        .field("age",   Age.parse(dto.age()))
        .apply(User::new);                            // (Name, Email, Age) -> User

// Invalid(NEL[ "email: not an address", "age: not a number" ])  — name was fine.
```

```java
// Tolerant (with #551): problems become warnings, the value still flows.
Ior<NonEmptyList<Warning>, Config> cfg =
    Ior.accumulate()
        .field("port",    parsePortLenient(raw.port()))
        .field("timeout", parseTimeoutLenient(raw.timeout()))
        .apply(Config::new);                          // Both(warnings, config) or Right(config)
```

- **No `Semigroup` argument** — the carrier defaults to `NonEmptyList` (#549); accumulation is concatenation, fixed once.
- **No HKT** — `accumulate().field(...).apply(ctor)` is a plain fluent chain; `Kind` never appears.
- **Open arity** — `.apply(...)` matches the constructor’s arity up to the existing `Function12`; records beyond that nest.
- **Located errors** — `.field(label, v)` tags the slot, so accumulated `FieldError`s carry their paths.
- **One shape, three carriers** — the same builder serves `Validated` (strict), `ValidationPath` (railway), and `Ior` (tolerant), parameterised by the accumulating applicative.

## Things to explore

- **Representation — curried-`ap` vs tuple-growing.** A curried-`ap` builder (holds a `Validated<NEL, FunctionK>`; each `.field` applies `ap`) reaches `Function12` with **no new tuple types**. A tuple-growing builder would first need `Tuple3..TupleN` (HKJ has only `Tuple2`). Recommend curried-`ap`; treat `TupleN` as a separable decision, only if a `tupled()` surface is wanted elsewhere.
- **Arity ceiling.** `apply(Function2) … apply(Function12)` overloads cover the realistic range; document the nest-a-sub-record escape for >12 fields.
- **Label threading.** Attach the path at the **leaf** (`FieldError.at(step)`) so accumulation just concatenates and paths compose through nesting; `.field(label, v)` is call-site sugar over `.and(v)`.
- **Carriers.** Put `accumulate()` on `Validated`, `ValidationPath`, **and** `Ior` (#551), sharing one builder over the accumulating applicative — do not introduce a second accumulation mechanism.
- **Consolidating `zipWithNAccum`.** The builder subsumes that family; keep them for back-compat, make the builder the documented front door, and consider soft-deprecating `zipWith4Accum`+.
- **`FieldError` type.** A small composable error: a `path` (`at(step)`), a message, NEL-friendly. Coordinate with #549’s error channel and `hkj-spring`’s error→HTTP (one 422 listing every field).
- **Validated prism / “refraction” (likely a follow-on issue).** The natural **optic** consumer is a `Prism` whose match returns `Validated`/`Ior` rather than `Optional` (`parse : S -> Validated<NEL<FieldError>, A>`, total `build`). `accumulate()…apply(ctor)` is exactly its `parse` body. Probably its own issue once the builder lands; noted here for the dependency.
- **HKT plumbing.** The builder is plain fluent on the surface, but the underlying accumulating applicative (the NEL-default `Validated`/`Ior` instance) follows the existing `instances` / `Witnesses` conventions so generic code can still reach it.
- **Laws & tests.** Reuse the applicative law factories; add assembly tests (accumulation associativity, *all* errors collected, label composition). An `assertThat…` helper in `hkj-test`.
- **Docs.** Update the validation / `where_to_start` pages that currently show `map2` / `Semigroups.list()` to lead with `accumulate()`.

## Ergonomic constraints

- **No HKT tax** — usable as a plain fluent chain; `Kind` never surfaces for casual users.
- **No `Semigroup` argument** in the common case — NEL default (#549).
- **No new tuple types required** — curried-`ap` over the existing `FunctionN` (to `Function12`).
- **Located by default** — labels are cheap and compose; a mapper can tag them automatically from field names.
- **One accumulation mechanism** — shared across `Validated` / `ValidationPath` / `Ior`; consistent with #549 / #551, not a divergent path.
- **Backward compatible** — purely additive; `ap`, `zipWithAccum`, the `zipWithNAccum` family, and `map2..map5` all keep working unchanged.

## Dependencies & relationship to other issues

- **Depends on #549 (`NonEmptyList`)** — supplies the non-empty carrier and lets the builder drop the `Semigroup` argument in the common case.
- **Pairs with #551 (`Ior` / `These`)** — the tolerant `accumulate()` is the `Both`-accumulating twin; same mechanism, same NEL warning channel.
- **Enables** a bidirectional record↔DTO mapper’s validated `parse` (the generated body is `accumulate()…apply(canonicalCtor)` with auto-tagged labels) and accumulating partial-update flows.

## Scope

- **In:** the `accumulate()` builder over `Validated`, `ValidationPath`, and `Ior`; `.field(label, v)` / `.and(v)`; `apply(Function2..Function12)`; a small `FieldError` with a composable path; NEL-default carrier.
- **Out (defer):** the validated prism / refraction optic (its own follow-on); `Tuple3..TupleN`; annotation-processor (`@DeriveMapping`) codegen assembly (part of the mapper feature, not this issue).

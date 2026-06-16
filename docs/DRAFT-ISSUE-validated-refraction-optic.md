# Draft GitHub Issue — Validated Refraction Optic (`Prism` with a reason)

> **Working draft for review.** Everything under **“Issue body — copy from here”** is the postable content and follows the `#549`/`#551` template. The **Reviewer notes** above it are *not* part of the issue. This is the optics-subsystem **follow-on** to the open-arity assembly builder draft (`docs/DRAFT-ISSUE-open-arity-validated-assembly.md`); it should be posted *after*, and marked dependent on, that issue and #549.

**Suggested title:** `[FEAT] Add Refraction optic (a Prism whose match is Validated, for parse-don't-validate boundaries)`
**Suggested label:** `enhancement`

### Reviewer notes (decisions you may want to change)

- **Name.** There is **no established optic name** for “a prism whose match carries a reason”, so this is the #1 decision (like Ior-vs-These in #551). I lead with **`Refraction`** (fits the optics-light metaphor — Iso = mirror, Prism = splitter, Refraction = partial bend — and matches the mapping design doc). Self-documenting alternatives: `ValidatedPrism`, `ParsePrism`, `Parser`. Pick one primary, document the others as aliases.
- **Concrete vs generic error channel.** I scoped the public surface to **`Validated<NonEmptyList<FieldError>, A>`** (no HKT tax). The internals are an `Optic` whose `modifyF` runs over the accumulating applicative, so an `F`-generic form is possible later — flag if you want that exposed.
- **Tolerant (`Ior`) twin.** Included as a sibling pending #551. Drop the bullet if #551 is far off and add it when `Ior` lands.
- **Focus-DSL integration.** I put a `RefractionPath` / `.focus()` bridge under *explore, possibly defer* — say if you want it in-scope for v1 or split to a third issue.
- **Composition matrix depth.** I specify the important cells and flag the rest as “specify & test every combination” (mirroring #551’s semantics-matrix bullet) rather than enumerating all 25 in the issue.

---

## Issue body — copy from here

## The gap

HKJ’s `Prism<S, A>` models “`S` *might* be an `A`” with `getOptional(S) : Optional<A>` (match) and `build(A) : S` (construct). The match answers **yes/no** — it cannot say **why not**, and it cannot report **more than one** problem.

That is exactly the wrong shape for a **validated boundary** — the “parse, don’t validate” pattern where an unvalidated wire/DTO value is turned into an always-valid domain value:

- The forward direction (**parse**, wire → domain) is **fallible and wants reasons**: `"not an email"`, and ideally *all* the reasons at once, each located.
- The backward direction (**build/render**, domain → wire) is **total**: a valid domain value always renders.

A `Prism` collapses the fallible direction to `Optional` — it throws the reasons away. So today, parsing-with-reasons through the optic lattice is impossible: you either compose a `Prism` and lose the *why*, or you drop out of optics entirely into hand-written `Validated` code and lose composition with `Lens`/`Iso`/`Traversal` and the Effect-Path railway.

The missing piece is a **`Prism` whose match is `Validated` instead of `Optional`** — a *smart-constructor optic*. `build` stays total; `parse` returns `Validated<NonEmptyList<FieldError>, A>` and **accumulates**. It is the optic that the assembly builder (companion issue) and `NonEmptyList` (#549) were built to feed, and it is the lawful, composable home for a bidirectional mapper’s `parse`.

## What “good” looks like (user perspective)

A leaf smart-constructor as an optic — fallible parse, total build — that composes like any other optic and lands on the railway:

```java
// build is total; parse fails with accumulated, located reasons.
Refraction<String, EmailAddress> email = Refraction.of(
    wire   -> EmailAddress.parse(wire),   // String -> Validated<NEL<FieldError>, EmailAddress>
    addr   -> addr.value());              // EmailAddress -> String   (total)

Validated<NonEmptyList<FieldError>, EmailAddress> parsed = email.parse("  NOPE ");
String rendered = email.build(addr);      // always succeeds
```

```java
// Composes with the lattice; sibling fields ACCUMULATE (via the assembly builder),
// nested fields SHORT-CIRCUIT (you can't parse the inner if the outer failed).
Refraction<RegisterDto, User> user = /* assembled from per-field refractions */;
Validated<NonEmptyList<FieldError>, User> result = user.parse(dto);   // every bad field, each located

// Drops straight onto the railway:
EitherPath<ApiError, User> railway =
    Path.validated(user.parse(dto)).mapError(ApiError::validation).toEitherPath();
```

- **`build` is always total** — encodes the parse-don’t-validate asymmetry the `Prism`/`Optional` shape can’t.
- **`parse` reports *why*, and *all* of them** — `Validated<NEL<FieldError>, A>`, each error path-located (`"address.zip: …"`).
- **It’s an optic, not an island** — `andThen` composes it with `Iso`/`Lens`/`Prism`/`Traversal`; its `modifyF` runs over the accumulating applicative; it bridges to `ValidationPath` / `Path.validated`.
- **No HKT** — `Refraction.of(parse, build)`, `.parse`, `.build`, `.andThen` are plain; `Kind` never surfaces.

## Things to explore

- **Name.** No standard optic name exists. `Refraction` (light metaphor) vs `ValidatedPrism` / `ParsePrism` / `Parser`. Pick one primary; document the alias.
- **Shape.** `parse(S) : Validated<NonEmptyList<FieldError>, A>` + `build(A) : S`. Public surface concrete over `Validated`/NEL (no HKT tax); internals an `Optic` whose `modifyF` is over the accumulating applicative (profunctorially `Star (Validated NEL)`), leaving an `F`-generic form open later.
- **Conversions (the bridge to the existing lattice).**
  - `Iso<S,A> → Refraction<S,A>` — parse never fails (`Valid` always).
  - `Prism<S,A> → Refraction<S,A>` — supply a reason for the empty case: `prism.refract(reason)` maps `Optional.empty → Invalid(reason)`.
  - `Refraction<S,A> → Affine<S,A>` / `Prism` — *forget the reasons* (`Validated → Optional`).
- **Composition matrix (the subtle part — specify & test every combination).** Key cells: `Refraction ∘ Refraction = Refraction` (sequential ⇒ **short-circuit**, like `via`); `Refraction ∘ Iso = Refraction`; `Iso ∘ Refraction = Refraction`; `Lens ∘ Refraction = Refraction` and `Refraction ∘ Lens = Refraction`; `Refraction ∘ Prism` needs a reason for the prism’s empty case. Be explicit that **`andThen` (deeper into structure) short-circuits**, while **assembling sibling fields (the builder) accumulates** — the same split `ValidationPath` already draws between `via` and `zipWithAccum`.
- **Effect-Path bridge.** `parse` returns `Validated`, so a `toValidationPath()` / `.focus()`-style bridge into `ValidationPath` / `Path.validated` should be first-class — this is not an optics-only type.
- **Laws.** Generalise the prism partial round-trip: `parse(build(a)) == Valid(a)` (build-then-parse round-trips to a valid); and on a successful parse the build reconstructs consistently. Specify, then reuse the `PrismLawsTestFactory`-style harness; add an `assertThatRefraction` (or reuse `assertThatValidated` on `parse`) in `hkj-test`.
- **Field-path labels.** `parse` tags each `FieldError` with its path; auto from field names when generated, leaf-level by hand — shared with the assembly companion issue.
- **Tolerant twin (with #551).** A `Refraction` whose `parse` returns `Ior<NonEmptyList<Warning>, A>` — “parse succeeds, possibly with warnings”. Same `build`, same NEL channel; lenient boundaries.
- **Focus-DSL integration (explore, possibly defer).** A `RefractionPath` so the Focus DSL can navigate *through* a validated boundary, mirroring `FocusPath`/`AffinePath`/`TraversalPath`. May be a separate issue.
- **HKT plumbing.** As an `Optic`, it slots into the existing `optics` package and composition machinery; follow `Prism`/`Affine` conventions.

## Ergonomic constraints

- **No HKT tax** — `Refraction.of(parse, build)` / `.parse` / `.build` / `.andThen` are plain fluent calls; `Kind` never surfaces.
- **`build` total by construction** — the type encodes the parse-don’t-validate asymmetry; there is no fallible build.
- **Located, accumulated errors by default** — `Validated<NEL<FieldError>, _>` with composable paths; a mapper tags them automatically from field names.
- **A first-class member of the lattice** — composes with `Iso`/`Lens`/`Prism`/`Traversal` via `andThen` and bridges to the Effect-Path railway; not a parallel island.
- **Clear short-circuit vs accumulate contract** — `andThen` (nested) short-circuits; sibling assembly accumulates. Must be documented so users arriving with `Prism`/`flatMap` intuitions aren’t surprised.
- **Backward compatible** — purely additive; `Prism`, `Affine`, `Iso` are untouched, with explicit conversions between them and `Refraction`.

## Dependencies & relationship to other issues

- **Depends on #549 (`NonEmptyList`)** — the error carrier for `parse`.
- **Depends on the open-arity assembly builder** (companion issue) — `accumulate()…apply(ctor)` is exactly the body of a multi-field `parse`; this optic is its principal consumer.
- **Pairs with #551 (`Ior`)** — the tolerant `parse` variant.
- **Enables** a bidirectional record↔DTO mapper: the mapper’s validated `parse` *is* a generated `Refraction` (mapper codegen is a separate feature, out of scope here).

## Scope

- **In:** the `Refraction<S, A>` optic (`parse : S → Validated<NEL<FieldError>, A>`, total `build : A → S`); `of`, `parse`, `build`, `andThen` composition with `Refraction`/`Iso`/`Lens`/`Prism`/`Traversal`; conversions to/from `Iso`/`Prism`/`Affine`; the `ValidationPath` bridge; laws + tests; the `Ior` tolerant twin (pending #551).
- **Out (defer):** `@DeriveMapping` / annotation-processor generation (part of the mapper feature); a full `RefractionPath` Focus-DSL integration (possible separate issue); an `F`-generic error channel (revisit if a non-`Validated` accumulating effect is needed).

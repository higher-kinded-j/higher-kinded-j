# Draft GitHub Issue — `Patch` Builder + `Endo` Monoid (sparse, accumulating multi-edit)

> **Working draft for review.** Everything under **“Issue body — copy from here”** is the postable content and follows the `#549`/`#551` template. The **Reviewer notes** above it are *not* part of the issue. Fourth in the dependent set; derived from `docs/PHASE1-MULTI-EDIT-ANALYSIS.md`. Depends on the assembly-builder and labelling drafts (and transitively #549).

**Suggested title:** `[FEAT] Add Patch builder + Endo monoid (sparse, accumulating multi-edit over optics)`
**Suggested label:** `enhancement`

### Reviewer notes (decisions you may want to change)

- **Name.** `all` is already taken (`OpticOps.all` is a Fold query). I lead with **`Patch`** (`Patch.fold` / `Patch.accumulate`); alternatives: `Focus.patch(...)`, `Edits.of(...)`. Pick one.
- **`Endo` split.** I bundle the `Endo<S>` monoid here. It’s a tiny, standalone `Monoids` addition (textbook, useful beyond this) — split it into its own micro-issue if you’d rather land it independently.
- **Result selection.** Pure edits → `Endo<S>`; fallible edits → `Validated`/`ValidationPath` (accumulate) or `EitherPath` (fail-fast), chosen by the edits, not separate methods. Flag if you’d prefer explicit method names.
- **Semantics.** I specify **parallel-on-original** (each edit independent → accumulation works). Confirm that over a sequential/threaded variant.
- **Coupled paths.** Out of scope — point users at the existing `Lens.paired`. Say if you want auto-detection of overlap.

---

## Issue body — copy from here

## The gap

HKJ can edit one path at a time (`FocusPath.set/modify`, `Setter.modify`) and update two *coupled* fields atomically (`Lens.paired`). What it has no ergonomic form for is the everyday case: **apply N *independent* edits at different paths in one reusable operation** — and, for the partial-update/REST-`PATCH` shape, **skip the edits whose incoming value is absent**.

Today that means hand-threading: `var s1 = pathA.set(...); var s2 = pathB.modify(..., s1); …`, with a manual `if (v != null)` around each, and no way to (a) reuse the patch across sources, (b) report *all* bad fields at once, or (c) attach a path to each failure. There is also no `Endo` / function-composition monoid to fold the writes through.

This is the in-place sibling of a DTO mapper’s construction: where the mapper *builds* a domain value from a DTO, a patch *updates* an existing aggregate from a sparse DTO. It rides the same accumulation/labelling machinery (the companion assembly-builder and labelling issues) and is, in fact, the general form of a mapper’s `patch`.

## What “good” looks like (user perspective)

A reusable, composable patch — and a sparse, all-errors-at-once validated PATCH:

```java
import static org.higherkindedj.optics.Edit.*;

// Pure: reusable across sources; composes (Endo<S> is a Monoid).
Endo<Order> normalise = Patch.fold(over(EMAIL, String::toLowerCase), over(SKU, String::trim));
Order a = normalise.apply(orderA);
Order b = normalise.andThen(applyDiscount).apply(orderB);
```

```java
// Sparse PATCH controller: nullable DTO fields land 1:1; every bad field reported at once, located.
Validated<NonEmptyList<FieldError>, Order> updated =
    Patch.accumulate(
        overIfPresent(ORDER_NUMBER, req.orderNumber()),         // null -> no-op (identity slot)
        overIfPresent(EMAIL,        req.email(), Email::parse),  // String -> Validated<_, Email>
        mapIfPresent (QUANTITY,     req.qtyDelta(), (d,q) -> q + d))
      .apply(order);
// Invalid(NEL[ "email: not an address" ]) — or Valid(order') with only the present fields changed.
```

- **`overIfPresent(absent)` is a no-op** — it contributes the monoid identity; the sparse-PATCH win falls out of the algebra.
- **Pure → `Endo<S>`, fallible → `Validated`/`ValidationPath`/`EitherPath`** — selected by the edits, not separate APIs.
- **Located, accumulated errors** — each failed edit is tagged with its optic path (companion labelling issue), so a validated PATCH yields one 422 listing every bad field.
- **Reusable & composable** — a pure patch is a named `Endo<S>` you apply to many sources and compose with other patches.
- **No HKT** — `Patch.fold` / `Patch.accumulate` / `over` / `overIfPresent` / `mapIfPresent` are plain.

## Things to explore

- **`Edit<S>` abstraction.** An existential-leaf edit (`over` / `overIfPresent` ×2 / `mapIfPresent`), mirroring the shape proven by telescope’s `Edit<S>`, built on `FocusPath`/`Setter`.
- **`Endo<S>` monoid.** `empty = s -> s`, `combine = andThen`; add to `Monoids`. The pure patch is `foldMap(edits, Endo.MONOID)`; the identity powers `overIfPresent(absent)`. (Could be its own micro-issue.)
- **Validated patch = the assembly builder over `Endo<S>`.** Accumulate per-edit `Validated<NEL<FieldError>, Endo<S>>`, then fold the writes onto the **original** source iff all are `Valid`. No new accumulation mechanism.
- **Semantics — parallel-on-original.** Each edit reads/validates independently against the original; this is *why* accumulation works and is the correct PATCH semantics (distinct from a threaded `.modify(...).modify(...)`).
- **Disjoint vs coupled paths.** Disjoint top-level fields compose cleanly (the common case); genuinely coupled fields use the existing **`Lens.paired`** as one atomic edit. Consider a debug-time overlap check.
- **Tolerant variant (with #551).** `Ior`-returning edits → “apply, but collect warnings”.
- **Generalises the mapper’s `patch`.** A mapper’s sparse overlay is `Patch.fold(overIfPresent(fieldₖ, dtoₖ)…)` — same mechanism; keep them one.
- **`hkj-spring`.** A validated PATCH is a `ValidationPath` station → one 422 keyed by path; reuse the existing return-value handling.
- **Laws/tests.** `Endo` is a lawful monoid (`MonoidLawsTestFactory`); patch over disjoint paths is order-independent; `overIfPresent(null)` is identity.

## Ergonomic constraints

- **No HKT tax** — plain fluent builder; `Kind` never surfaces.
- **Sparse by default** — `overIfPresent(absent)` is the monoid identity; nullable DTO fields land 1:1 with no `if` ceremony.
- **Reusable & composable** — a pure patch is an `Endo<S>` you reuse across sources and compose with other patches.
- **Located, accumulated errors** — all bad fields at once, each path-tagged (companion labelling issue).
- **One mechanism, not a zoo** — `Endo` monoid (pure) + the assembly builder (validated); pure/accumulate/fail-fast selected by the edits.
- **Backward compatible** — purely additive; `FocusPath`, `Setter`, `Lens.paired` untouched.

## Dependencies & relationship to other issues

- **Depends on the assembly-builder issue** — the validated patch *is* that builder with success type `Endo<S>`.
- **Depends on the labelling issue (E7)** — to locate each failed edit by its optic path. (A2 is the first consumer that labels from a *value-level* `FocusPath`; the labelling issue should confirm a value-level path surfaces its segment, or accepts `over(path, "label", fn)`.)
- **Transitively depends on #549 (`NonEmptyList`)** — the error channel.
- **Pairs with #551 (`Ior`)** — tolerant “apply with warnings” PATCH.
- **Generalises the bidirectional mapper’s `patch`** — A1 and this share one mechanism.

## Scope

- **In:** the `Edit<S>` abstraction (`over` / `overIfPresent` / `mapIfPresent`); the `Endo<S>` monoid (in `Monoids`); `Patch.fold` (pure → `Endo<S>`) and `Patch.accumulate` (validated → `Validated`/`ValidationPath`); parallel-on-original semantics; `hkj-spring` rendering via the existing `ValidationPath` handler.
- **Out (defer):** auto-resolution of overlapping/coupled paths (use `Lens.paired`); async edits; `@DeriveMapping` codegen (the mapper feature, which reuses this as its `patch`).

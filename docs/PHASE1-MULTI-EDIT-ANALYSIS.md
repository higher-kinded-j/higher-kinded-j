# Phase 1 Analysis: Sparse-PATCH / Multi-Edit (A2) — the second-consumer validation

> ⚠️ **Superseded for the corrected state by `FINAL-DESIGN-AND-ROADMAP.md`.** Corrections: the builder is `Edits` (not `Patch` — collides with `Path`); the validated patch is **two phases** (parallel validation + sequential `foldLeft` write), not "parallel-on-original"; `OpticOps.modifyAllAccumulating` is acknowledged prior art. Retained as provenance/detail.

**Status:** design analysis (no implementation)
**Sits in:** Phase 1 — the first feature *above* the Phase 0 foundations. Its analytical job is to **validate the foundation** by checking whether a *second, independent* feature rides the same Phase 0 primitives (it does) and to surface any *new* requirement before any of it is built (exactly one: an `Endo` monoid). Companion to the Phase 0 analyses and the bidirectional-mapping design.
**One line:** *A multi-edit is a fold of independent writes into an `Endo<S>` monoid; the validated multi-edit is that fold accumulated through the Phase 0 assembly builder — so A2 reuses the entire foundation and is, in fact, the general form of the mapper’s `patch`.*

Proposed API is marked **`SKETCH`**.

---

## 1. What A2 is

Telescope’s `Telescope.all(over(P1, fn), overIfPresent(P2, dtoVal), mapIfPresent(P3, delta, biFn))` folds several heterogeneous edits at different paths into one reusable `Function<S,S>`, with first-class nullable-DTO PATCH support: an absent value contributes an identity slot (`overIfPresent(absent) = no-op`). HKJ today has only `Lens.paired` (two *coupled* fields updated atomically) — there is no ergonomic “apply N *independent* edits, skipping absent values” combinator, and no accumulating/validated variant.

## 2. What HKJ already has (grounded)

- **`Setter<S,A>`** — the write-only optic: `modify(Function<A,A>, S) : S`, `set(A, S) : S`, `andThen(Setter) : Setter` (`Setter.java:54,66,77,97`). The natural “write” half of an edit.
- **`FocusPath` / `AffinePath`** — `set(A,S)`, `modify(Function<A,A>,S)`, and `modifyF(Function<A,Kind<F,A>>, S, app)` (`FocusPath.java:94,105,121`). A *single-path* effectful edit already works; A2 is the *N-path* generalisation.
- **`Lens.paired(first, second, reconstructor) : Lens<S, Pair<A,B>>`** (`Lens.java:431,480`) — atomic update of two *coupled* fields. Handles the overlapping-path case A2 deliberately leaves out (§6).
- **`Monoids` / `Semigroups` catalogues** exist (`hkt/Monoids.java`, `Semigroups.java`) — the home an `Endo` monoid would slot into.
- **Gaps confirmed:** there is **no `Endo` / function-composition monoid** in `Monoids`, and **no multi-edit combinator** — `OpticOps.all` is a *Fold query* (“do all focused elements match a predicate”, `OpticOps.java:731,751`), so even the name `all` is taken.

## 3. The decomposition (why A2 is mostly Phase 0)

An edit `eᵢ` binds an optic `Pᵢ` (a `Setter`/`FocusPath`) to a per-leaf transform. A multi-edit folds them:

```java
// SKETCH — the edit abstraction (existential leaf, like telescope's Edit<S>)
sealed interface Edit<S> {
  static <S,A> Edit<S> over(FocusPath<S,A> at, Function<A,A> fn);             // always edits
  static <S,A> Edit<S> overIfPresent(FocusPath<S,A> at, @Nullable A value);   // null -> identity slot
  static <S,A,V> Edit<S> overIfPresent(FocusPath<S,A> at, @Nullable V v, Function<V,A> f);
  static <S,A,V> Edit<S> mapIfPresent(FocusPath<S,A> at, @Nullable V v, BiFunction<V,A,A> f);
}
```

**Pure multi-edit = fold the edits into the `Endo<S>` monoid.** Each present edit becomes a write `Endo<S> = s -> Pᵢ.set(newValue, s)`; an absent `overIfPresent` becomes the monoid **identity** (`s -> s`). The whole patch is `foldMap(edits, Endo.MONOID)`:

```java
// SKETCH
Endo<S> patch = Edits.fold(over(NAME, String::trim), overIfPresent(EMAIL, dto.email()));
S updated = patch.apply(order);     // reusable across sources; composes (it's a Monoid)
```

**Validated multi-edit = the Phase 0 assembly builder, accumulating `Validated<NEL<FieldError>, Endo<S>>`.** When an edit’s transform can fail (`Aᵢ -> Validated<E,Aᵢ>`), each edit yields a *validated deferred write*; the assembly builder accumulates them (all failures at once, each located by its optic path via E7), and only if every edit is `Valid` are the writes folded onto the **original** source:

```java
// SKETCH — all bad fields reported at once, located; writes applied only if all valid.
Validated<NonEmptyList<FieldError>, Order> r =
    Edits.accumulate(
        over(SHIPPING_CITY, City::parse),          // String -> Validated<_, City>
        overIfPresent(EMAIL, dto.email(), Email::parse))
      .apply(order);
```

This is literally `accumulate()…` over `Endo<S>` values, then `endos.foldLeft(original, (s,w)->w.apply(s))`. **A2’s validated half is the assembly builder with success type `Endo<S>`.**

## 4. Second-consumer verdict (the point of the probe)

A2 consumes the **entire** Phase 0 stack:

| Phase 0 primitive | How A2 uses it |
|---|---|
| Assembly builder | accumulates the per-edit `Validated<…, Endo<S>>` |
| `NonEmptyList` (#549) | the error channel |
| Field-path labels (E7) | locates each failed edit by its optic path |
| (Refraction) | an edit whose transform *parses* is a refraction at that path |

…and surfaces **exactly one** new requirement:

- **`Endo<S>` monoid** — `empty = s -> s`, `combine = andThen`. Small, textbook, and missing from `Monoids`. The pure multi-edit can be done with raw `Function<S,S>` composition, but a named lawful `Endo` is the clean home (gives `foldMap` a target, makes patches first-class/composable, and supplies the identity that powers `overIfPresent`). Independently useful beyond A2.

**This is the ideal outcome:** a second, independent feature converges on the same foundation, validating its scope, and asks for one cheap, standard addition — caught *before* the Phase 0 issues are implemented (so #549/assembly can note the `Endo` dependency now).

## 5. Unifications this analysis uncovered

- **A2 is the general form of the mapper’s `patch`.** The mapper’s `patch(base, partialDto)` (sparse `Lens.set` overlay) is exactly `Edits.fold(overIfPresent(fieldₖ, dtoₖ) …).apply(base)`. A1’s `patch` and A2’s multi-edit are *one mechanism* — A2 subsumes it.
- **The type of the edits selects the result type** — pure edits → `Endo<S>`; fallible edits → `Validated<NEL<FieldError>, S>` (accumulate) or `EitherPath<E,S>` (fail-fast). Same “fallibility infects the result” story as the mapper’s `render`/`parse`.
- **It lands on the railway.** A validated PATCH *is* a `ValidationPath` station, dropping into `hkj-spring` (one 422 listing every bad field by path) — the same controller ergonomics as the mapper.

## 6. Semantics to pin down

- **Batch edits apply to the *original* source (parallel), not sequentially.** This is *why* accumulation works (each edit is validated independently) and is the right PATCH semantics (a set of independent field updates). It differs from a chained `.modify(...).modify(...)` (which threads).
- **Disjoint paths compose cleanly; overlapping/coupled paths do not.** Two edits rebuilding from the original at nested/overlapping paths risk a lost update. The common PATCH case is disjoint top-level fields. For genuinely coupled fields, use the existing **`Lens.paired`** (atomic) as a single edit. Document this boundary; consider a debug-time overlap check.
- **Naming.** `all` is taken by the Fold query. Suggest `Patch` / `Focus.patch(...)` (reads for the use case) or `Edits.fold` / `Edits.accumulate`; `over`/`overIfPresent`/`mapIfPresent` mirror telescope’s `Edit`.

## 7. One refinement back to Phase 0 (E7)

A2’s edits are **value-level optics**, so the field-path segment must come from the **optic’s own path** (a `FocusPath` self-locating from its generated `Field` segment) or from an **explicit label** (`over(path, "label", fn)`) — not only from a record component name at codegen. E7’s model already supports both, but its issue should explicitly state that a *value-level* `FocusPath` surfaces its segment (or accepts an override), since A2 is the first consumer that labels from a path value rather than from generated code. Minor; fold into the E7 issue.

## 8. Ergonomic surface (SKETCH)

```java
import static org.higherkindedj.optics.Edit.*;

// Reusable, composable patch (Endo<S> is a Monoid):
Endo<Order> normalise = Patch.fold(over(EMAIL, String::toLowerCase), over(SKU, String::trim));
Order a = normalise.apply(orderA);
Order b = normalise.andThen(applyDiscount).apply(orderB);   // patches compose

// Sparse PATCH controller — nullable DTO fields land 1:1, all errors at once, located:
Validated<NonEmptyList<FieldError>, Order> updated =
    Patch.accumulate(
        overIfPresent(ORDER_NUMBER, req.orderNumber()),
        overIfPresent(EMAIL,        req.email(), Email::parse),
        mapIfPresent (QUANTITY,     req.qtyDelta(), (d,q) -> q + d))
      .apply(order);
```

- **Pure → `Endo<S>`** (reusable, composes via the monoid). **Fallible → `Validated`/`ValidationPath`/`EitherPath`** (accumulate or fail-fast) — selected by the edits, not a separate method.
- **`overIfPresent(absent) = identity`** — the sparse-PATCH win falls out of the monoid identity.
- No new combinator zoo: one `Patch` builder over the `Endo` monoid (pure) and the assembly builder (validated).

## 9. Recommended issue shape

A standalone *“multi-edit / sparse-PATCH (`Patch` builder + `Endo` monoid)”* issue, **depending on the assembly builder + E7** (and transitively #549), with the `Endo<S>` monoid either inlined or split as a tiny `Monoids` addition. It explicitly notes it **generalises the mapper’s `patch`**, so A1 and A2 share one mechanism. Drafted on request (`docs/DRAFT-ISSUE-multi-edit-patch.md`).

## 10. Verdict

Phase 1 validates Phase 0. A2 is not a new pillar — it is the assembly builder + field-path labels + `NonEmptyList`, with a single small new monoid (`Endo<S>`) to carry the writes, and it turns out to *be* the general form of the mapper’s `patch`. Build order is unchanged (Phase 0 → A2 → A1), now with two confirmed consumers of the foundation and one cheap addition (`Endo`) folded into the Phase 0 plan. The foundation holds.

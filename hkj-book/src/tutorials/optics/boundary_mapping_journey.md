# Optics: Boundary Mapping Journey

~~~admonish info title="What We'll Learn"
- Multi-edit and sparse updates: several edits, one operation, all errors at once
- `ValidatedPrism`: parse-don't-validate as an optic, with both round-trip laws
- `@GenerateMapping`: the whole domain ↔ DTO boundary derived from a spec interface
- Located errors end to end: leaves, nesting, renames, and the sparse PATCH sibling
- The edge cases: a `null`, a list index, a left-out field, a record's own rule, and a PATCH bean's default
~~~

**Duration**: ~50 minutes | **Tutorials**: 4 (T24-T27) | **Exercises**: 19

~~~admonish tip title="Where This Fits in the Bigger Picture"
This journey is the hands-on lane for the [Mapping at the Boundary](../../mapping/ch_intro.md) chapter. Tutorial 24 builds the update-side machinery by hand (`Edits.combine` / `Edits.accumulate`), Tutorial 25 builds the leaf every fallible correspondence rests on (`ValidatedPrism`), and Tutorial 26 lets the processor derive the whole boundary and proves it lawful. Tutorial 27 takes it to the edge cases a real request brings. The [capstone](../../mapping/capstone.md) then shows the same machinery at full scale.
~~~

**Prerequisites**: [Optics: Lens & Prism Journey](lens_prism_journey.md); the accumulating-assembly exercises in the [Error Handling Journey](../coretypes/error_handling_journey.md) help with Tutorials 25-26.

## Journey Overview

A service boundary has two directions and two failure styles: outbound rendering that cannot fail, and inbound parsing that should report *every* problem, located. This journey builds that boundary from its parts, then generates it:

```
Edits.accumulate          ValidatedPrism            @GenerateMapping           edge cases
(hand-written fold)  ──▶  (the fallible leaf)  ──▶  (the derived boundary) ──▶ (nulls, lists, defaults)
     T24                       T25                       T26                      T27
```

---

## Tutorial 24: Multi-Edit and Sparse Updates (~12 minutes)
**File**: `Tutorial24_MultiEdit.java` | **Exercises**: 5

Apply N independent edits at different paths in one reusable operation, including the sparse, all-errors-at-once REST PATCH shape.

**What you'll learn**:
- Folding pure edits into one reusable `Update<S>` with `Edits.combine`
- Sparse updates: the `…IfPresent` factories treat `null` as "leave it alone"
- The validated PATCH: `Edits.accumulate` reports all located failures at once
- Why a fallible edit cannot slip into `combine` (compile-time purity)

**Key insight**: validation is source-independent and runs first; the writes run as one fold only if everything validated.

---

## Tutorial 25: ValidatedPrism (~10 minutes)
**File**: `Tutorial25_ValidatedPrism.java` | **Exercises**: 3

The smart-constructor optic: a `Prism` whose match says *why not*, and all the reasons at once.

**What you'll learn**:
- `ValidatedPrism.of(parse, build)`: a fallible, accumulating `parse` and a total `build`
- Lifting a plain prism with a reason via `fromPrism`
- Nesting short-circuits; sibling fields accumulate through `Validated.fields()`
- Verifying both round-trip laws with `ValidatedPrismLaws`

**Key insight**: the section law forbids a normalising `build`; the prism's parse is exactly the leaf shape the mapper and the `Edits` builder consume.

---

## Tutorial 26: Record Mapping (~12 minutes)
**File**: `Tutorial26_RecordMapping.java` | **Exercises**: 5

The boundary, generated: `@GenerateMapping` derives a total `build` and an accumulating, located `parse` from a spec interface (the specs live in `org.higherkindedj.example.tutorials.mapping`, main sources, where the processor runs).

**What you'll learn**:
- Calling the generated Impl, bound once in the calling class: `build` is total, `parse` returns `Validated<NonEmptyList<FieldError>, Domain>`
- Reading located errors: stock codec messages, a nested spec's `guest.email` path, declaration order
- Law-checking a mapping with one `MappingLaws` call
- The sparse PATCH sibling: `UpdateSpec`, null-as-absent, same leaf vocabulary

**Key insight**: everything Tutorials 24 and 25 built by hand is what the processor derives, and the laws prove the derivation honest.

---

## Tutorial 27: Boundary Edge Cases (~15 minutes)
**File**: `Tutorial27_BoundaryEdgeCases.java` | **Exercises**: 6

| Exercise | The request | Where it lands |
|---|---|---|
| 1 | A `null` id, and a guest with no email | `id: must not be null`, beside `guest.email: must not be null` |
| 2 | A bad email on the second guest in a list | `guests.1.email`, located by its index |
| 3 | A room request that leaves its note out | `Optional.empty()`, because `@OptionalBridge` says `null` means absent |
| 4 | A stay that ends before it starts | the record's own path, carrying its constructor's message |
| 5 | An empty PATCH | no change, as the sparse identity law checks |
| Diagnostic | An empty PATCH on a bean with `marketingOptIn = false` | the opt-in overwritten, which the law catches only if the sample differs from the default |

A real request is rarely just a bad value. It leaves a field out, sends a list with one bad element, breaks a rule that spans two fields, or arrives as a PATCH bean that fills in a value nobody sent. Each exercise asks where that request lands. Its specs sit beside Tutorial 26's.

**Key insight**: every edge case ends up as a value in the `Validated`, never as an exception, except the one no compiler can see. For that one you need a law, and a sample chosen to catch it.

---

~~~admonish tip title="See Also"
- [Mapping at the Boundary](../../mapping/ch_intro.md) - The reference chapter this journey practises
- [Capstone: One 422, Every Bad Field](../../mapping/capstone.md) - The same machinery at full scale
- [Absent Fields and Record Invariants](../../mapping/absence.md) - Tutorial 27's `@OptionalBridge` and invariant rules in full
- [Sparse PATCH](../../mapping/beans_patch.md) - Why a PATCH bean must leave its fields uninitialised
- [Multi-Edit and Sparse Updates](../../optics/multi_edit.md) - Tutorial 24's reference page
- [Validated Prisms](../../optics/validated_prism.md) - Tutorial 25's reference page
~~~

---

**Previous:** [Optics: Batching & Coupled Updates](batching_journey.md)
**Next:** [Expression: ForState](../expression/forstate_journey.md)

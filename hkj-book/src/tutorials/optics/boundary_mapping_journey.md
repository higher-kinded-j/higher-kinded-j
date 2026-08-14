# Optics: Boundary Mapping Journey

~~~admonish info title="What We'll Learn"
- Multi-edit and sparse updates: several edits, one operation, all errors at once
- `ValidatedPrism`: parse-don't-validate as an optic, with both round-trip laws
- `@GenerateMapping`: the whole domain ↔ DTO boundary derived from a spec interface
- Located errors end to end: leaves, nesting, renames, and the sparse PATCH sibling
~~~

**Duration**: ~35 minutes | **Tutorials**: 3 (T24-T26) | **Exercises**: 13

~~~admonish tip title="Where This Fits in the Bigger Picture"
This journey is the hands-on lane for the [Mapping at the Boundary](../../mapping/ch_intro.md) chapter. Tutorial 24 builds the update-side machinery by hand (`Edits.combine` / `Edits.accumulate`), Tutorial 25 builds the leaf every fallible correspondence rests on (`ValidatedPrism`), and Tutorial 26 lets the processor derive the whole boundary and proves it lawful. The [capstone](../../mapping/capstone.md) then shows the same machinery at full scale.
~~~

**Prerequisites**: [Optics: Lens & Prism Journey](lens_prism_journey.md); the accumulating-assembly exercises in the [Error Handling Journey](../coretypes/error_handling_journey.md) help with Tutorials 25-26.

## Journey Overview

A service boundary has two directions and two failure styles: outbound rendering that cannot fail, and inbound parsing that should report *every* problem, located. This journey builds that boundary from its parts, then generates it:

```
Edits.accumulate          ValidatedPrism            @GenerateMapping
(hand-written fold)  ──▶  (the fallible leaf)  ──▶  (the derived boundary)
     T24                       T25                       T26
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

## Tutorial 25: ValidatedPrism (~10 minutes)
**File**: `Tutorial25_ValidatedPrism.java` | **Exercises**: 3

The smart-constructor optic: a `Prism` whose match says *why not*, and all the reasons at once.

**What you'll learn**:
- `ValidatedPrism.of(parse, build)`: a fallible, accumulating `parse` and a total `build`
- Lifting a plain prism with a reason via `fromPrism`
- Nesting short-circuits; sibling fields accumulate through `Validated.fields()`
- Verifying both round-trip laws with `ValidatedPrismLaws`

**Key insight**: the section law forbids a normalising `build`; the prism's parse is exactly the leaf shape the mapper and the `Edits` builder consume.

## Tutorial 26: Record Mapping (~12 minutes)
**File**: `Tutorial26_RecordMapping.java` | **Exercises**: 5

The boundary, generated: `@GenerateMapping` derives a total `build` and an accumulating, located `parse` from a spec interface (the specs live in `org.higherkindedj.example.tutorials.mapping`, main sources, where the processor runs).

**What you'll learn**:
- Calling the generated Impl: `build` is total, `parse` returns `Validated<NonEmptyList<FieldError>, Domain>`
- Reading located errors: stock codec messages, a nested spec's `guest.email` path, declaration order
- Law-checking a mapping with one `MappingLaws` call
- The sparse PATCH sibling: `UpdateSpec`, null-as-absent, same leaf vocabulary

**Key insight**: everything Tutorials 24 and 25 built by hand is what the processor derives, and the laws prove the derivation honest.

---

~~~admonish tip title="See Also"
- [Mapping at the Boundary](../../mapping/ch_intro.md) - The reference chapter this journey practises
- [Capstone: One 422, Every Bad Field](../../mapping/capstone.md) - The same machinery at full scale
- [Multi-Edit and Sparse Updates](../../optics/multi_edit.md) - Tutorial 24's reference page
- [Validated Prisms](../../optics/validated_prism.md) - Tutorial 25's reference page
~~~

---

**Previous:** [Focus DSL](focus_dsl_journey.md)
**Next:** [Expression: ForState](../expression/forstate_journey.md)

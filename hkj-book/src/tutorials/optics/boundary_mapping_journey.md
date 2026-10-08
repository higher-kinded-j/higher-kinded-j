# Optics: Boundary Mapping Journey

~~~admonish info title="What We'll Learn"
- Multi-edit and sparse updates: several edits, one operation, all errors at once
- `ValidatedPrism`: parse-don't-validate as an optic, with both round-trip laws
- `@GenerateMapping`: the whole domain ↔ DTO boundary derived from a spec interface
- Located errors end to end: leaves, nesting, renames, and the sparse PATCH sibling
- The edge cases: a `null`, a list index, a left-out field, a record's invariant, and a PATCH bean's default
- An order desk, the Optics chapter's capstone: named paths, every bad price, a sparse amendment and a change of state
~~~

**Tutorials**: 5 (T24-T27 and the Capstone) | **Exercises**: 24
<!-- exercises: optics/Tutorial24_MultiEdit optics/Tutorial25_ValidatedPrism optics/Tutorial26_RecordMapping optics/Tutorial27_BoundaryEdgeCases optics/TutorialCapstone_OrderDesk -->

~~~admonish tip title="Where This Fits in the Bigger Picture"
This journey is the hands-on lane for the [Mapping at the Boundary](../../mapping/ch_intro.md) chapter. Tutorial 24 builds the update-side machinery by hand (`Edits.combine` / `Edits.accumulate`), Tutorial 25 builds the leaf every fallible correspondence rests on (`ValidatedPrism`), and Tutorial 26 lets the processor derive the whole boundary and proves it lawful. Tutorial 27 takes it to the edge cases a real request brings. The Mapping chapter's capstone, [One 422, Every Bad Field](../../mapping/capstone.md), then shows the same machinery at full scale. The optics track's Capstone closes the journey with the Optics chapter's [Capstone: An Order Desk](../../optics/capstone.md), built from named paths and Tutorial 24's edits.
~~~

**Prerequisites**: [Optics: Lens & Prism Journey](lens_prism_journey.md); the accumulating-assembly exercises in the [Error Handling Journey](../coretypes/error_handling_journey.md) help with Tutorials 25-27. The Capstone also assumes Tutorial 09 of the [Fluent & Free DSL Journey](fluent_free_journey.md) and Tutorial 12 of the [Focus DSL Journey](focus_dsl_journey.md).

## Journey Overview

A service boundary has two directions and two failure styles: outbound rendering that cannot fail, and inbound parsing that should report *every* problem, located. This journey builds that boundary from its parts, then generates it:

```
T24  Edits.accumulate    the hand-written fold
 │
 ▼
T25  ValidatedPrism      the fallible leaf
 │
 ▼
T26  @GenerateMapping    the derived boundary
 │
 ▼
T27  edge cases          nulls, list indexes, invariants, PATCH defaults
 │
 ▼
Capstone  order desk     the Optics chapter's capstone, as exercises
```

---

## Tutorial 24: Multi-Edit and Sparse Updates
**File**: `Tutorial24_MultiEdit.java` | **Exercises**: 5

Apply N independent edits at different paths in one reusable operation, including the sparse, all-errors-at-once REST PATCH shape.

**What you'll learn**:
- Folding pure edits into one reusable `Update<S>` with `Edits.combine`
- Sparse updates: the `…IfPresent` factories treat `null` as "leave it alone"
- The validated PATCH: `Edits.accumulate` reports all located failures at once
- Why a fallible edit cannot slip into `combine` (compile-time purity)

**Key insight**: validation is source-independent and runs first; the writes run as one fold only if everything validated.

---

## Tutorial 25: ValidatedPrism
**File**: `Tutorial25_ValidatedPrism.java` | **Exercises**: 3

The smart-constructor optic: a `Prism` whose match says *why not*, and all the reasons at once.

**What you'll learn**:
- `ValidatedPrism.of(parse, build)`: a fallible, accumulating `parse` and a total `build`
- Lifting a plain prism with a reason via `fromPrism`
- Nesting short-circuits; sibling fields accumulate through `Validated.fields()`
- Verifying both round-trip laws with `ValidatedPrismLaws`

**Key insight**: the section law forbids a `parse` that normalises; the prism's parse is exactly the leaf shape the mapper and the `Edits` builder consume.

---

## Tutorial 26: Record Mapping
**File**: `Tutorial26_RecordMapping.java` | **Exercises**: 5

The boundary, generated: `@GenerateMapping` derives a total `build` and an accumulating, located `parse` from a spec interface (the specs live in `org.higherkindedj.example.tutorials.mapping`, main sources, where the processor runs).

**What you'll learn**:
- Calling the generated Impl, bound once in the calling class: `build` is total, `parse` returns `Validated<NonEmptyList<FieldError>, Domain>`
- Reading located errors: stock codec messages, a nested spec's `guest.email` path, declaration order
- Law-checking a mapping with one `MappingLaws` call
- The sparse PATCH sibling: `UpdateSpec`, null-as-absent, same leaf vocabulary

**Key insight**: everything Tutorials 24 and 25 built by hand is what the processor derives, and the laws prove the derivation honest.

---

## Tutorial 27: Boundary Edge Cases
**File**: `Tutorial27_BoundaryEdgeCases.java` | **Exercises**: 6

A real request is rarely just a bad value. It leaves a field out, sends a list with one bad element, breaks a rule that spans two fields, or arrives as a PATCH bean that fills in a value nobody sent. Each exercise asks where that request lands. Its specs sit beside Tutorial 26's.

**What you'll learn**:
- A `null` field on the wire is a located error, beside every other error
- A list element is located by its index
- `@OptionalBridge` declares, per component, that a `null` means absent
- A record's constructor refusal becomes an error at the record's path
- A PATCH bean's default reads as sent, and how a law catches it

| Exercise | The request | The question |
|---|---|---|
| 1 | A booking with no id, whose guest has no email | Where is each `null` reported, and with what message? |
| 2 | A party whose first guest has no name, and whose second has a bad email | How does the path say which guest? |
| 3 | A room request that leaves its note out | What does `@OptionalBridge` make of the `null`? |
| 4 | A stay whose departure is not after its arrival | Where does the constructor's refusal land? |
| 5 | A PATCH that sends nothing | How do we check that it changes nothing? |
| Diagnostic | The same PATCH, on a bean whose schema said `default: false` | Why does the team's law pass, and which sample makes it fail? |

**Key insight**: `parse` and `updateFrom` return every edge case here as a value, never a thrown exception. The odd one out, a PATCH bean's default, comes back valid and wrong; only a law, run with a sample that differs from the default, catches it.

---

## Capstone: The Order Desk {#capstone-the-order-desk}
**File**: `TutorialCapstone_OrderDesk.java` | **Exercises**: 5

The Optics chapter's capstone as exercises, on its running example: an order, and the consignment that ships it. Each exercise asks for a piece of the Capstone page's order desk, on a request the page did not show.

**What you'll learn**:
- Reusing a named path in every operation that needs it
- Checking every price with `modifyAllValidated`, and changing only a valid result with `map`
- Narrowing a traversal path with `filter`, so a write leaves the other lines alone
- Amending an order from a sparse request with `Edits.accumulate`, every bad field located
- Moving a sealed state to another variant only from the one you expect

**Key insight**: The paths do the navigating, so each operation is a few lines, and each failure is a value that names where it happened.

---

~~~admonish tip title="See Also"
- [Mapping at the Boundary](../../mapping/ch_intro.md): The reference chapter this journey practises
- [Capstone: One 422, Every Bad Field](../../mapping/capstone.md): The same machinery at full scale
- [Null has an address, not a stack trace](../../mapping/basics.md#null-doctrine): Tutorial 27's null rule
- [Nesting a spec, and a list of them](../../mapping/structure.md#nesting-containers-and-recursion): How a list element is located
- [Absent Fields and Record Invariants](../../mapping/absence.md): Tutorial 27's `@OptionalBridge` and invariant rules in full
- [A PATCH getter must answer `null` until set](../../mapping/beans_patch.md#patch-getters-answer-null): Why a PATCH bean must leave its fields uninitialised
- [Many Edits at Once](../../optics/multi_edit.md): Tutorial 24's reference page
- [Validated Prisms](../../optics/validated_prism.md): Tutorial 25's reference page
- [Capstone: An Order Desk](../../optics/capstone.md): the Capstone's reference page
~~~

---

**Previous:** [Optics: Batching & Coupled Updates](batching_journey.md)
**Next:** [Expression: ForState](../expression/forstate_journey.md)

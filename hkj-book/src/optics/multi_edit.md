# Multi-Edit and Sparse Updates

~~~admonish example title="See Example Code"
**The code on this page is [MultiEditBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java)** - the page includes it directly, so it is compiled and run by the build.
~~~


_Apply N independent edits at different paths in one reusable operation, including the sparse, all-errors-at-once REST `PATCH` shape._

~~~admonish info title="What You'll Learn"
- Combining several edits at different paths into one reusable operation with `Edits.combine`
- Writing sparse updates where a `null` field means "leave it alone", with no `if` per field
- Building a REST `PATCH` with `Edits.accumulate` that validates every field and reports all the bad ones at once, not just the first
- The two-phase model: validate everything first, then apply the writes only if all of them passed
- How the pure and validating edits are kept apart at compile time, and when overlapping paths need one atomic edit instead
~~~

## The problem

An optic edits one path at a time (`FocusPath.set`, `Setter.modify`). But the everyday case is applying **several** edits at once, the classic example being a REST `PATCH` that tidies the email, trims the SKU, and bumps the quantity. By hand that means threading the value through every step, guarding each optional field with an `if`, and (if you validate at all) throwing on the first bad field:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java:before}}
```

Three pains recur: one `if` per optional field, the value re-threaded by hand at every step, and validation that stops at the first error instead of collecting them all.

## The solution: two entry points

The `org.higherkindedj.optics.edit` package folds all of that into two operations. Which one you reach for depends only on whether any edit can *fail*:

| Entry point | Reach for it when | Returns |
|---|---|---|
| `Edits.combine(...)` | every edit is always safe (no validation) | one reusable `Update<S>` |
| `Edits.accumulate(...)` | some edits validate their input (a REST `PATCH`) | a patch you apply to get `Validated<NonEmptyList<FieldError>, S>` |

Those are the whole API. The rest of this page is how each one behaves, and how the compiler keeps a validating edit from slipping into `combine` by accident.

---

## Pure multi-edit: `Edits.combine`

Each `Edit` factory pairs an optic (a `FocusPath` or a `Setter`) with a value or function. `combine` folds them (via the [`Update` monoid](../functional/semigroup_and_monoid.md)) into one named, reusable transformation:

``` java
import static org.higherkindedj.optics.edit.Edit.*;

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java:combine}}
```

Only pure `Edit`s fit `combine`'s signature; a fallible edit is rejected **at compile time**, so validation failures can never be silently dropped.

---

## Sparse updates: absent means "leave it alone"

The `…IfPresent` factories treat `null` as *absent*: the edit contributes the identity update, so a sparse request DTO lands one-to-one with no `if` ceremony:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java:sparse}}
```

Each request field maps to exactly one slot; an absent field simply contributes nothing to the fold:

```
    PatchRequest { email: "a@b.example",   sku: null,        qtyDelta: 3 }
                          │                     │                  │
                          ▼                     ▼                  ▼
                    write email             identity           qty += 3
                          └─────────────────────┼──────────────────┘
                                                ▼
                  order' — email and quantity changed, sku untouched
```

~~~admonish warning title="Absent and null are deliberately the same"
A sparse edit cannot *clear* a field: `setIfPresent(path, null)` means "no change requested", not "set to null". This suits non-null domain models; if a field must be clearable, model the cleared state explicitly (e.g. `Maybe`) and `set` it. The functions given to `modifyIfPresent`/`parseIfPresent` are never invoked with `null`.
~~~

---

## Validated PATCH: `Edits.accumulate`

`parseIfPresent` parses the incoming value first, and a generated path locates any failure **automatically** from its own label (`.at(label)` prepends further context, exactly as `FieldError.at` composes paths). The parser you hand it has exactly the shape of a [`ValidatedPrism`](validated_prism.md)'s `parse`: define the boundary once as a prism (gaining the total render-back and the round-trip laws) and pass `Email::parse` here. `accumulate` validates **every** edit independently and reports **all** bad fields at once:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/MultiEditBook.java:accumulate}}
```

Errors accumulate in edit order on the `NonEmptyList` channel, exactly like the [accumulating assembly](../monads/validated_assembly.md), but as a homogeneous fold, so there is **no arity ceiling**.

~~~admonish tip title="Generated paths label themselves"
A path from a `@GenerateFocus` companion carries its record-component name as a **segment** (`OrderFocus.email()` → `"email"`); composing paths concatenates segments (`customer.via(address).via(zip)` → `"customer.address.zip"`, surfaced by `segments()`/`pathString()`), and `parseIfPresent` locates failures with them **automatically**: no `.at(...)` needed for generated paths. An explicit `.at(label)` still prepends outward, for hand-written optics or extra context.
~~~

For the railway, `applyPath(order)` is the `ValidationPath` twin of `apply(order)`, and `toValidated()` exposes the folded `Update` itself for reuse.

~~~admonish tip title="Generate this when the shape is regular"
When the request DTO's fields line up one-to-one with a domain record — the common REST PATCH case — you need not hand-write the fold at all: `@GenerateMapping` on an [`UpdateSpec<Domain, Wire>`](record_mapping.md#sparse-patch-write-back-updatespec) generates exactly this `Edits.accumulate` over the present fields, as `updateFrom(wire) : Edits.Accumulated<Domain>` (the same `apply`/`applyPath`/`toValidated` surface). Reach for the hand-written `Edits` here when the edits are irregular — a `qtyDelta` that *modifies*, coupled fields, a computed target; reach for `UpdateSpec` when each present field maps to one slot.
~~~

---

## How the split stays compile-time safe

`combine` accepts only pure edits; `accumulate` accepts both. That is not a rule you have to remember: it is carried by the type of each edit. `set`, `modify`, and the `…IfPresent` forms produce an `Edit` (cannot fail); `parseIfPresent` produces a `FallibleEdit` (may fail). Passing a `FallibleEdit` to `combine` does not compile, so a validation failure can never be silently dropped.

```
    FallibleEdit<S>            may fail: carries Validated<NEL<FieldError>, Update<S>>
        ▲       ▲
        │       └── FallibleEdit.Parsed     the fallible leaf   (from parseIfPresent)
        │
    Edit<S>                    cannot fail: carries the Update<S> directly
        ▲
        └────────── Edit.Infallible         the infallible leaf (from set / modify / …IfPresent)

    Edits.combine(Edit<S>...)          ← only pure edits fit: a FallibleEdit is a compile error
    Edits.accumulate(FallibleEdit<S>...) ← both fit: a pure edit is one that always validates
```

---

## Semantics: validate everything, then write once

An accumulated patch works in two phases:

1. **Validate**: each edit's incoming value is checked independently. Validation never sees a source, which is what makes accumulation sound *and* lets one patch apply to many sources.
2. **Apply**: only if every edit validated, the writes run as a single left-to-right fold.

```
  Phase 1 — validate every edit independently (no source involved)

    setIfPresent(SKU, null)     parseIfPresent(EMAIL, raw)      modifyIfPresent(QTY, 3)
             │                            │                             │
      absent → identity              parser runs                 present → write
             │                            │                             │
        Valid(no-op)          Valid(write) or Invalid(errors)      Valid(write)
             └────────────────────────────┼─────────────────────────────┘
                                          │
                        all Valid?  ──────┴──────  any Invalid?
                             │                          │
  Phase 2 — one              ▼                          ▼
  left-to-right fold   Valid(order')          Invalid(NEL[ email: … ])
  of the writes        only the present       every bad field, located,
                       fields changed         in edit order — no writes run
```

Application order is observable only when paths overlap: disjoint paths commute; an edit at an overlapping path sees the previous edit's result (a `modify` reads the *current* value at application time). Genuinely coupled fields belong in one atomic edit (see [Coupled Fields](coupled_fields.md) and `Lens.paired`).

---

~~~admonish info title="Key Takeaways"
* **`Edits.combine`** folds pure edits into one reusable `Update<S>`; compile-time purity
* **`…IfPresent` + null-as-absent** gives sparse updates with no `if` ceremony: absent contributes the monoid identity
* **`Edits.accumulate`** validates every edit independently and reports all located failures at once, in edit order, with no arity ceiling
* **Two phases**: validation is source-independent; writes run left-to-right only when everything validated
* **Overlapping paths see earlier writes**; coupled fields should be one atomic edit (`Lens.paired`)
~~~

~~~admonish info title="Hands-On Learning"
Practice the whole model in [Tutorial 24: Multi-Edit and Sparse Updates](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial24_MultiEdit.java) (5 exercises, ~12 minutes): pure folds, sparse patches, and the all-errors-at-once validated PATCH.
~~~

~~~admonish tip title="See Also"
- [Semigroup and Monoid](../functional/semigroup_and_monoid.md) - The `Update` monoid that powers `combine`
- [Accumulating Assembly](../monads/validated_assembly.md) - The same all-errors-at-once model for *constructing* values
- [Coupled Fields](coupled_fields.md) - Atomic updates of interdependent fields
- [Record Mapping — sparse PATCH (`UpdateSpec`)](record_mapping.md#sparse-patch-write-back-updatespec) - Generate this `Edits.accumulate` fold when the DTO maps one-to-one
~~~

---

**Previous:** [Core Type Integration](core_type_integration.md)
**Next:** [Optics Extensions](optics_extensions.md)

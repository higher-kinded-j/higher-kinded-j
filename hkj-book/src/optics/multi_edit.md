# Multi-Edit and Sparse Updates

_Apply N independent edits at different paths in one reusable operation — including the sparse, all-errors-at-once REST `PATCH` shape._

Optics edit one path at a time: `FocusPath.set`, `Setter.modify`. The everyday case of applying **several independent edits** — tidy the email, trim the SKU, bump the quantity — previously meant hand-threading the result through a chain of reassignments, wrapping each optional field in an `if`, and reporting only the first bad value.

The `org.higherkindedj.optics.edit` package folds all of that into two entry points:

| Entry point | Accepts | Returns |
|---|---|---|
| `Edits.combine(...)` | pure edits only (compile-time) | one `Update<S>` |
| `Edits.accumulate(...)` | pure and fallible edits mixed | an accumulated patch: apply it to get `Validated<NonEmptyList<FieldError>, S>` |

The sealed hierarchy is what makes that split compile-time safe:

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

## Pure multi-edit: `Edits.combine`

Each `Edit` factory pairs an optic (a `FocusPath` or a `Setter`) with a value or function. `combine` folds them — via the [`Update` monoid](../functional/semigroup_and_monoid.md) — into one named, reusable transformation:

``` java
import static org.higherkindedj.optics.edit.Edit.*;

Update<Order> normalise = Edits.combine(
    modify(EMAIL, String::toLowerCase),
    modify(SKU,   String::trim));

Order a = normalise.apply(orderA);
Order b = normalise.andThen(applyDiscount).apply(orderB);   // Update composes further
```

Only pure `Edit`s fit `combine`'s signature — a fallible edit is rejected **at compile time**, so validation failures can never be silently dropped.

## Sparse updates: absent means "leave it alone"

The `…IfPresent` factories treat `null` as *absent*: the edit contributes the identity update, so a sparse request DTO lands one-to-one with no `if` ceremony:

``` java
setIfPresent(ORDER_NUMBER, req.orderNumber())              // null -> no-op
modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta)
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

## Validated PATCH: `Edits.accumulate`

`parseIfPresent` parses the incoming value first — and `.at(label)` locates any failure, exactly as `FieldError.at` composes paths. `accumulate` validates **every** edit independently and reports **all** bad fields at once:

``` java
Validated<NonEmptyList<FieldError>, Order> updated =
    Edits.accumulate(
            setIfPresent(ORDER_NUMBER, req.orderNumber()),
            parseIfPresent(EMAIL, req.email(), Email::parse).at("email"),
            modifyIfPresent(QUANTITY, req.qtyDelta(), (delta, qty) -> qty + delta))
        .apply(order);
// Invalid(NEL[ "email: not an address" ]) — or Valid(order') with only the present fields changed
```

Errors accumulate in edit order on the `NonEmptyList` channel, exactly like the [accumulating assembly](../monads/validated_assembly.md) — but as a homogeneous fold, so there is **no arity ceiling**. For the railway, `applyPath(order)` is the `ValidationPath` twin of `apply(order)`, and `toValidated()` exposes the folded `Update` itself for reuse.

## Semantics: validate everything, then write once

An accumulated patch works in two phases:

1. **Validate** — each edit's incoming value is checked independently. Validation never sees a source, which is what makes accumulation sound *and* lets one patch apply to many sources.
2. **Apply** — only if every edit validated, the writes run as a single left-to-right fold.

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

Application order is observable only when paths overlap: disjoint paths commute; an edit at an overlapping path sees the previous edit's result (a `modify` reads the *current* value at application time). Genuinely coupled fields belong in one atomic edit — see [Coupled Fields](coupled_fields.md) and `Lens.paired`.

---

~~~admonish info title="Key Takeaways"
* **`Edits.combine`** folds pure edits into one reusable `Update<S>`; compile-time purity
* **`…IfPresent` + null-as-absent** gives sparse updates with no `if` ceremony — absent contributes the monoid identity
* **`Edits.accumulate`** validates every edit independently and reports all located failures at once, in edit order, with no arity ceiling
* **Two phases**: validation is source-independent; writes run left-to-right only when everything validated
* **Overlapping paths see earlier writes**; coupled fields should be one atomic edit (`Lens.paired`)
~~~

~~~admonish info title="Hands-On Learning"
Practice the whole model in [Tutorial 24: Multi-Edit and Sparse Updates](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial24_MultiEdit.java) (5 exercises, ~12 minutes) — pure folds, sparse patches, and the all-errors-at-once validated PATCH.
~~~

~~~admonish tip title="See Also"
- [Semigroup and Monoid](../functional/semigroup_and_monoid.md) - The `Update` monoid that powers `combine`
- [Accumulating Assembly](../monads/validated_assembly.md) - The same all-errors-at-once model for *constructing* values
- [Coupled Fields](coupled_fields.md) - Atomic updates of interdependent fields
~~~

---

**Previous:** [Core Type Integration](core_type_integration.md)
**Next:** [Optics Extensions](optics_extensions.md)

# Record Mapping Basics

_One interface, both directions: a total `build` from domain to DTO, and an accumulating, located `parse` back._

Every service boundary maps between a rich domain record and a flat wire DTO. Hand-written mappers drift; reflection-based mappers fail at runtime and know nothing about validation. `@GenerateMapping` derives the mapping **at compile time, reflection-free**, from an interface you own, and because the fallible direction returns `Validated<NonEmptyList<FieldError>, Domain>`, a bad DTO reports **every** bad field at once, each located by name.

~~~admonish info title="What You'll Learn"
- Declaring a mapping as a `MappingSpec<Domain, Wire>` interface and using the generated Impl
- Why `build` is total while `parse` is fallible and accumulating
- The null doctrine: how every `null` on the wire becomes a located `FieldError`, never an exception
- Converting type-differing fields with `ValidatedPrism` leaves
- Renaming components with `@MapField`, and computing wire-only fields with derived getters
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

The whole declaration is an empty interface naming the pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
```

The two directions have different shapes, and that asymmetry runs through the whole chapter:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

The generated class is `<Spec>Impl` beside the spec, used through its `INSTANCE` constant. A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

---

## One null doctrine, both wire shapes {#null-doctrine}

A JSON binder leaves a missing property `null`, on a record component just as on an unset bean property. So every reference-typed `parse` read is null-guarded: a `null` component is a located `FieldError` (`must not be null`) that accumulates with every other bad field, never an exception, and it locates through nesting (`customer.name: must not be null`). A `null` never reaches a leaf's prism.

The doctrine reaches inside containers too, identity-copied ones included:

- A `null` element or map value locates by its index or key (`emails.1: must not be null`), whether the container lifts through a leaf ([`parseAll`/`parseValues`](../optics/validated_prism.md#the-bulk-forms-parseall-and-parsevalues)) or copies by identity. The index is a plain positional segment, matching the map-key grammar.
- An identity container still copies by reference; the scan only locates nulls, it never rebuilds.
- A `null` container *component* is guarded like any reference read (`emails: must not be null`).

What stays the caller's error (`NullPointerException`), by contract: a `null` *wire* itself, a `null` map *key* (a structurally broken map, not a wrong value), and calling the bulk forms directly with a `null` list or map.

Absence-as-a-meaning remains exclusively the [sparse `UpdateSpec` tier](beans_patch.md#sparse-patch-write-back-updatespec)'s: a record cannot express absence, it can only be wrong.

~~~admonish tip title="At the Spring boundary: one 422, every bad field by path"
In a Spring controller the parse result needs no wrapping: return it as-is and `hkj-spring` renders an `Invalid` as one **422 Unprocessable Content** response listing every located `FieldError` by path. See [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).
~~~

---

## Validated leaves

A **leaf** is the conversion at a single field: the point where the mapping stops delegating and one wire value becomes one domain value. Where the two sides differ in type, the leaf is a [`ValidatedPrism`](../optics/validated_prism.md), supplied as a **zero-parameter `default` method named after the domain component**:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_usage}}
```

The stock conversion families (identifiers, dates, enums, money) need no hand-written leaves at all; [Standard Codecs](codecs.md) covers them.

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when the two component types are identical, so a `ValidatedPrism<String, String>` can validate a field the types alone would copy verbatim. Validate, not normalise: a parse that trims or case-folds accepts a spelling its `build` cannot reproduce, which breaks the [section law](../optics/validated_prism.md#laws) (an accepted wire value must rebuild to exactly itself).
~~~

---

## Renames: `@MapField`

A rename is an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:rename_spec}}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises.

Located error paths use **domain** component names, renames included: a wire sending `fullName` gets its errors at `name`. Every path in the system (nesting, containers, the sparse tier's labels) is domain-named, so paths stay mutually consistent and stable under wire refactors; a client mapping errors back onto its own payload keys must apply the rename in reverse.

---

## Derived wire fields

A wire component with **no domain counterpart** can be computed from the whole domain value. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_usage}}
```

The two directions are asymmetric: `build` computes the derived component, `parse` throws it away.

```
  build : fills the derived component from the whole domain value
  ────────────────────────────────────────────────────────────────
  Profile(first, last) ──▶ ProfileDto(first, last, displayName)
                                                   ▲
             displayName() : Getter<Profile,String>│  first + " " + last
                                                   └── computed, not copied

  parse : ignores the derived component (it is derivable)
  ────────────────────────────────────────────────────────────────
  ProfileDto(first, last, displayName) ──▶ Valid(Profile(first, last))
                          └── displayName dropped, never read
```

`build` fills the component by applying the getter to the whole domain value. `parse` **ignores** it: the data is derivable, so parse stays accumulating over the remaining components. (A mapping whose only extra is a derived field is *total-parse*: no **well-formed** wire value can fail it. The [null doctrine](#null-doctrine)'s guards still apply, so a hostile null component is a located invalid, exactly as everywhere else.)

The optic is a `Getter` because a derived field is single-valued, exactly one focus computed from the whole domain value. A `Fold`, with its zero-to-many focuses, has no single-component meaning here.

~~~admonish note title="How the two `default` families are told apart"
Leaves are named after *domain* components and return `ValidatedPrism`; derived fields are named after *wire-only* components and return `Getter`. The processor matches the two differently:

- A zero-parameter `default` returning `Getter` is *always* claimed as a derived field, and validated as one. So give getter-shaped utility helpers a parameter or a different return type, or they will be mistaken for derived fields.
- A `default` returning `ValidatedPrism` is matched by name against the domain's components, and a *locally declared* leaf **must** match: an unmatched local leaf is a compile error with a nearest-name hint (`leaf 'emial' names no component of Customer. Did you mean 'email()'?`), because a silently inert leaf would silently stop validating that field. Prism-returning helpers belong in `private` or `static` methods, which are never leaf-shaped.
- *Inherited* [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) leaves that match nothing stay inert by design: a shared vocabulary may carry leaves for components only some extending specs have.
- On a **sealed** mapping, locally declared leaves and derived fields are rejected outright (a dispatch has no components); inherited vocabulary stays inert there too.

Four shapes are rejected, each with a what/why/fix diagnostic: a `Getter` named after a *domain* component (ambiguous with a leaf); a `Getter` naming nothing on the wire; a `Getter` with the wrong type arguments; and a `@MapField` rename targeting a component a derived field already fills.
~~~

**Derived fields and the emission tiers.** A spec with any derived field never emits `asIso()`: the wire round trip recomputes the derived component, so it is an identity only for wire values that were already consistent. Combining a derived field with a projection (a wire otherwise smaller than the domain) is rejected too, because the projection's `asLens()` write-back could never honour a component that `build` recomputes. [The Emission Tiers](tiers.md) is the full story.

---

~~~admonish info title="Key Takeaways"
* **A mapping is an interface you own**: `@GenerateMapping` on a `MappingSpec<Domain, Wire>` generates `<Spec>Impl` with `build` and `parse`
* **Two directions, two shapes**: `build` is total; `parse` reports every bad field at once, each located by a domain-named path
* **Null is located, never thrown**: one doctrine across both wire shapes and inside containers; only a null wire itself stays the caller's error
* **Leaves convert, renames rename, getters derive**: `ValidatedPrism` leaves for type-differing fields, `@MapField` for names, `Getter` defaults for wire-only fields
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md) - The leaf optic every fallible correspondence is built from
- [Standard Codecs and Shared Vocabulary](codecs.md) - The stock leaf vocabulary and how to share it
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg) - The parse result as one HTTP response
~~~

---

**Previous:** [Mapping at the Boundary](ch_intro.md)
**Next:** [Standard Codecs and Shared Vocabulary](codecs.md)

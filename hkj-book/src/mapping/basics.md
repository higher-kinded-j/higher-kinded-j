# Record Mapping Basics

_Declare one interface; get a `build` that cannot fail and a `parse` that reports every bad field at once._

Most mappings are boring, and the mapper treats them that way: same-named, same-typed components match automatically, and one empty interface is the whole declaration. This page walks the happy path first (declare, build, parse, read the errors), then adds the three declarations you will actually reach for: a conversion, a rename, and a computed field. The precise rules live in [the fine print](#the-fine-print) at the end, where they belong.

~~~admonish info title="What You'll Learn"
- Declaring a mapping as a `MappingSpec<Domain, Wire>` interface and calling the generated Impl
- Reading a `parse` failure: every bad field at once, each located by name
- Converting a type-differing field with a `ValidatedPrism` leaf
- Renaming components with `@MapField`, and computing wire-only fields with derived getters
- Why a wire `null` becomes a located error, never an exception
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

## Your first mapping

The whole declaration is an empty interface naming the pair:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
```

That is all of it: no mapper class, no configuration. The processor derives both directions from the two records at compile time, reflection-free, and re-derives them on every compile, so the mapping cannot drift away from the records it maps.

The two directions have different shapes, and that asymmetry runs through the whole chapter:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

The generated class is `<Spec>Impl` beside the spec; a concrete spec like this one is used through its `INSTANCE` constant ([generic specs](generics.md#one-rule-three-access-shapes) use `instance()` or `of(...)` instead). A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

---

## Validated leaves

Real boundaries convert: the wire sends a `String`, the domain wants an email that has already been checked. A **leaf** is the conversion at a single field: the point where the mapping stops copying and one wire value becomes one domain value. The leaf itself is a [`ValidatedPrism`](../optics/validated_prism.md), two functions: a parse that may reject, and a render that cannot:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:email_leaf}}
```

Attach it to the spec as a zero-parameter `default` method named after the domain component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_usage}}
```

Note what the failure looks like: a value, not an exception, and the error knows *which field* it belongs to. With several bad fields, `parse` reports all of them at once; the client fixes everything in one round trip.

You will rarely write leaves like this one by hand. The standard conversion families (identifiers, dates, enums, money) ship ready-made; [Standard Codecs](codecs.md) covers them, and it is the natural next page.

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when the two component types are identical, so a `ValidatedPrism<String, String>` can validate a field the types alone would copy verbatim. Validate, not normalise: a parse that trims or case-folds accepts a spelling its `build` cannot reproduce, which breaks the [section law](../optics/validated_prism.md#laws) (an accepted wire value must rebuild to exactly itself).
~~~

---

## Renames: `@MapField`

When the wire calls it `fullName` and the domain calls it `name`, declare an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:rename_spec}}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises.

Error paths use **domain** component names, renames included: a wire sending `fullName` gets its errors at `name`. Every path in the system is domain-named, so paths stay consistent and stable under wire refactors; a client mapping errors back onto its own payload keys applies the rename in reverse.

---

## Derived wire fields

A wire component with **no domain counterpart** can be computed from the whole domain value: a `displayName` the domain does not store because it is derivable. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:derived_usage}}
```

The two directions are asymmetric: `build` computes the derived component, `parse` throws it away (the data is derivable, so nothing is lost).

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

The optic is a `Getter` because a derived field is single-valued: exactly one focus computed from the whole domain value. How the processor distinguishes leaf methods from derived-field methods, and how a derived field interacts with the [emission tiers](tiers.md), is [fine print](#the-fine-print).

---

## Null has an address, not a stack trace {#null-doctrine}

A JSON binder leaves a missing property `null`, so a boundary meets nulls constantly. The rule is one sentence: **every value `parse` reads from the wire is null-guarded, and a `null` read becomes a located `FieldError` (`must not be null`), accumulating with every other bad field, never an exception.** It locates through nesting (`customer.name: must not be null`) and inside containers (`emails.1: must not be null`), and a `null` never reaches a leaf's conversion logic.

~~~admonish tip title="Why this matters"
Compare the alternatives you have debugged before: an NPE with a stack trace pointing into generated code, or Jackson's `MismatchedInputException` naming a Java class. A located error names *the client's own field*, sits beside every other defect in the same response, and costs the client one round trip instead of one per null. Here, a null always gets an address and never a stack trace.
~~~

~~~admonish tip title="At the Spring boundary"
Returned as-is from a controller, this result becomes the single 422 response the introduction showed: [the 422 leg](../spring/spring_boot_integration.md#the-422-leg).
~~~

The exact contract (what happens inside containers, and which nulls remain the caller's bug) is in [the fine print](#the-fine-print) below.

---

## The fine print {#the-fine-print}

Nothing above requires this section; come back when a corner case finds you.

### The null contract, precisely

The null guard covers every reference-typed `parse` read, on record and bean wires alike, and reaches inside containers, identity-copied ones included:

- A `null` element or map value locates by its index or key (`emails.1: must not be null`), whether the container lifts through a leaf ([`parseAll`/`parseValues`](../optics/validated_prism.md#the-bulk-forms-parseall-and-parsevalues)) or copies by identity. The index is a plain positional segment, matching the map-key grammar.
- An identity container still copies by reference; the scan only locates nulls, it never rebuilds.
- A `null` container *component* is guarded like any reference read (`emails: must not be null`).

What stays the caller's error (`NullPointerException`), by contract: a `null` *wire* itself, a `null` map *key* (a structurally broken map, not a wrong value), and calling the bulk forms directly with a `null` list or map.

Absence-as-a-meaning belongs exclusively to the [sparse `UpdateSpec` tier](beans_patch.md#sparse-patch-write-back-updatespec): a record cannot express absence, it can only be wrong.

### How the two `default` families are told apart

Leaves are named after *domain* components and return `ValidatedPrism`; derived fields are named after *wire-only* components and return `Getter`. The processor matches the two differently:

- A zero-parameter `default` returning `Getter` is *always* claimed as a derived field, and validated as one. So give getter-shaped utility helpers a parameter or a different return type, or they will be mistaken for derived fields.
- A `default` returning `ValidatedPrism` is matched by name against the domain's components, and a *locally declared* leaf **must** match: an unmatched local leaf is a compile error with a nearest-name hint (`leaf 'emial' names no component of Customer. Did you mean 'email()'?`), because a silently inert leaf would silently stop validating that field. Prism-returning helpers belong in `private` or `static` methods, which are never leaf-shaped.
- *Inherited* [mix-in](codecs.md#shared-vocabulary-mix-in-interfaces) leaves that match nothing stay inert by design: a shared vocabulary may carry leaves for components only some extending specs have.
- On a **sealed** mapping, locally declared leaves and derived fields are rejected outright (a dispatch has no components); inherited vocabulary stays inert there too.

Four shapes are rejected, each with a what/why/fix diagnostic: a `Getter` named after a *domain* component (ambiguous with a leaf); a `Getter` naming nothing on the wire; a `Getter` with the wrong type arguments; and a `@MapField` rename targeting a component a derived field already fills.

### Derived fields and the emission tiers

A spec with any derived field never emits `asIso()`: the wire round trip recomputes the derived component, so it is an identity only for wire values that were already consistent. A mapping whose *only* extra is a derived field is *total-parse*: no **well-formed** wire value can fail it (the null guards above still apply, and a fallible leaf elsewhere in the spec still makes the whole parse fallible). Combining a derived field with a projection (a wire otherwise smaller than the domain) is rejected, because the projection's `asLens()` write-back could never honour a component that `build` recomputes. [The Emission Tiers](tiers.md) is the full story.

---

~~~admonish info title="Key Takeaways"
* **A mapping is an interface you own**: `@GenerateMapping` on a `MappingSpec<Domain, Wire>` generates `<Spec>Impl` with `build` and `parse`
* **Two directions, two shapes**: `build` is total; `parse` reports every bad field at once, each located by a domain-named path
* **Leaves convert, renames rename, getters derive**: `ValidatedPrism` leaves for type-differing fields, `@MapField` for names, `Getter` defaults for wire-only fields
* **Null is located, never thrown**: one rule across both wire shapes and inside containers; only a null wire itself stays the caller's error
~~~

~~~admonish tip title="See Also"
- [Validated Prisms](../optics/validated_prism.md) - The leaf optic every fallible correspondence is built from
- [Standard Codecs and Shared Vocabulary](codecs.md) - The stock leaf vocabulary and how to share it
- [The 422 leg](../spring/spring_boot_integration.md#the-422-leg) - The parse result as one HTTP response
~~~

---

**Previous:** [Mapping at the Boundary](ch_intro.md)
**Next:** [Standard Codecs and Shared Vocabulary](codecs.md)

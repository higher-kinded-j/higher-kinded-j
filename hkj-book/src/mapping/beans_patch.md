# Beans and Sparse PATCH

_The same mapper for getter/setter wires, and the opt-in tier where `null` means "leave unchanged" instead of "broken"._

Not every wire type is a record. Generated clients, JAXB payloads and legacy DTOs are beans, and one very common bean, the REST PATCH request, changes what `null` *means*: not broken data but *not provided*. This page covers both: mapping bean-shaped wires with the full feature set, and the explicit `UpdateSpec` opt-in that gives a PATCH bean its sparse semantics.

~~~admonish info title="What You'll Learn"
- Mapping bean-shaped wire types (setters, builders, JAXB lists) with the same features as records
- Why a bean mapping withholds `asIso()`, and how `Optional` bridges through `null`
- What PATCH and *sparse* PATCH actually mean, and why `null` is ambiguous in a PATCH body
- Opting into sparse semantics with `UpdateSpec`: present fields fold in, absent fields leave the domain alone
- The rules that keep the null-as-absent contract honest, and how containers patch through the element vocabulary
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.
~~~

## Bean-shaped wire targets

The wire side need not be a record. A **bean** (a mutable class with a no-args constructor and getters/setters, or an immutable one with a builder) maps the same way, with the same features (renames, leaves, derived fields, container lifting, nesting). Only *how* the wire is read and written changes: `build` fills through setters or a builder, and `parse` reads through getters.

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bean_spec}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:bean_usage}}
```

The design decisions worth knowing:

- **Null is located, never thrown, like every wire.** The null guard is universal ([one rule](basics.md#null-doctrine), both shapes), so a `null` property read is a located `FieldError` exactly as on a record wire. What is bean-*specific* is why nulls are expected at all: an unset property is a representable, ordinary state of a mutable bean, not just a hostile binding.
- **Honest tiers.** Because an unset property is ordinary, a bean's guarded reference reads count as fallible and the mapping withholds `asIso()` automatically; an all-primitive bean (whose reads can never be null) still earns it. A record wire's guards exist for hostile bindings only, so a lossless record mapping keeps `asIso()`, with the parse-iso coherence law scoped to wires whose reference components are non-null. Nesting is unaffected: a bean mapping exposes `asValidatedPrism()` like any other, so record specs nest it and containers lift it.
- **Construction strategy** is detected from the bean's shape, tried in order: a public no-args constructor with `setX` setters (and, for a getter-only `List`, the JAXB convention `getItems().addAll(...)`); then a static `builder()`/`newBuilder()` whose setters fill it and whose `build()` yields the wire. A bean that fits neither gets a what/why/fix diagnostic.
- **Optionality bridges through `null`.** On a full mapping, a domain `Optional<T>` maps to a nullable bean property `T` (bean conventions leave `Optional` off property types): empty bridges to absent (`build` skips the write, leaving the property unset; `parse` reads `Optional.ofNullable(...)`), and a present value still validates through its leaf. The [sparse tier](#sparse-patch-write-back-updatespec) is the deliberate exception: there `null` already means "leave unchanged", so a PATCH bean encodes "set to empty" with an `Optional`-typed property instead.
- **The domain stays a record.** `parse` assembles the domain through its canonical constructor, so only the *wire* may be bean-shaped; a bean domain gets a diagnostic.

Bean *projections* with reference properties and one-directional (parse-only or build-only) beans are not supported yet ([#702](https://github.com/higher-kinded-j/higher-kinded-j/issues/702), [#703](https://github.com/higher-kinded-j/higher-kinded-j/issues/703)); an all-primitive bean projection maps as a lawful `asLens()` today.

---

## What PATCH actually means {#what-patch-means}

Before the sparse tier, the contract it implements, because the whole design follows from it.

`PUT` replaces a resource; `PATCH` edits one. A **sparse** PATCH body carries only the fields the client wants to change: `{"email": "new@example.com"}` means *change the email, touch nothing else*. That contract makes `null` ambiguous. When the bound request object reports `getName() == null`, did the client omit `name` (leave it unchanged), or send `"name": null` deliberately? A typical JSON binder produces the same object either way.

So the same wire value carries opposite meanings in the two tiers:

```mermaid
flowchart TD
    N["the bound request has<br/>name = null"] --> DQ{"which contract<br/>does the DTO serve?"}
    DQ -->|"full parse or patch:<br/>dense, every field expected"| DE["located FieldError:<br/>name: must not be null"]
    DQ -->|"UpdateSpec updateFrom:<br/>sparse, null means absent"| SP["skipped: the domain's<br/>current name survives"]

    classDef wire fill:#8caaee,stroke:#1e66f5,color:#232634
    classDef error fill:#e78284,stroke:#d20f39,color:#232634
    classDef tier fill:#a6d189,stroke:#40a02b,color:#232634
    classDef decision fill:#e5c890,stroke:#df8e1d,color:#232634
    class N wire
    class DE error
    class SP tier
    class DQ decision
```

Which reading applies is a fact about the endpoint's contract, not about the data, and not something a mapper can infer from the types. That is why sparse semantics are an **explicit opt-in**.

---

## Sparse PATCH write-back: `UpdateSpec`

To opt in, the spec extends `UpdateSpec<Domain, Wire>` instead of `MappingSpec`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:update_spec}}
```

The Impl exposes a *single* method, `updateFrom(Wire) : Edits.Accumulated<Domain>`. There is no `build`, `parse`, or `as*` tier (a sparse mapping is not a projection of information, and an all-absent wire is *valid*, not a total parse). `updateFrom` folds the present properties into an [`Update<Domain>`](../optics/multi_edit.md), leaving the absent ones alone:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:update_usage}}
```

- **Present and valid** → the field is set, or parsed through its leaf, and folded in.
- **Present and invalid** → a located `FieldError`, accumulating as usual: sparseness never weakens validation of what *was* sent. `Edits.Accumulated` also offers `applyPath(current)` to drop straight onto the [validation railway](../effect/path_validation.md), so a controller answers with every error at once instead of persisting a partial write.
- **Absent (null)** → skipped; the domain's current value survives.

The return type is exactly what a hand-written [`Edits.accumulate(...)`](../optics/multi_edit.md) PATCH builder produces, so the two compose and the same consumption story (`apply`, `applyPath`, `toValidated`) carries over.

The rules that keep the contract honest:

- **A primitive wire property is rejected.** A primitive is always present (its default), so it can never carry the null-as-absent signal; use the wrapper type (`Integer`, `Boolean`). This is *forced*, not a style choice: an all-absent body must fold to the identity update, which a primitive would break.
- **A domain `Optional<T>` component bridged from a non-Optional property is rejected.** Under null-as-absent, `null` already means "leave unchanged", so "set to empty" has no encoding through a plain property (and a null-clears rule would be JSON Merge Patch's opposite contract). An `Optional`-typed wire property, though, *can* express it, patching by identity or through an element leaf: a present empty Optional sets empty; an absent (`null`) one leaves unchanged. Two binder caveats come with that power: Jackson binds an explicit JSON `null` on an `Optional`-typed property to `Optional.empty()`, so on this one property shape a sent `null` means *clear*, not *leave unchanged*; and the bean field must default to `null`, not the idiomatic `Optional.empty()`, or every request that omits the field clears the domain value.
- **A record wire is rejected.** A record component is always present, so absence is inexpressible; sparse PATCH is a bean-only shape.
- **A sealed hierarchy is rejected**, on either side: dispatch has no sparse meaning (an absent property cannot choose a subtype to patch).
- **A present container parses through the element vocabulary.** A `List`, `Optional` or `Map`-valued property (a pair declared as exactly those container types) routes through the element leaf named after the component: the same leaf the dense tiers lift, so one [mix-in vocabulary](codecs.md#shared-vocabulary-mix-in-interfaces) serves a full spec and its PATCH sibling. Replacement stays wholesale; each failing element is located by index or key (`phones.1`). A whole-container leaf (`ValidatedPrism<List<S>, List<A>>`) is the more specific declaration and wins over the element interpretation. A nested *spec* still does not lift through a sparse container; give the component an element leaf delegating to the nested Impl's `asValidatedPrism()` if its elements need a whole mapping.
- **Coverage is one-sided.** Every wire property maps to a domain component, but a domain component with *no* wire property is simply never changed: a PATCH DTO deliberately covers a subset.
- **A same-typed nested record, `Optional`, `List` or `Map` replaces wholesale** through identity, the fallback when no more specific leaf applies. The details:
  - A same-typed `List` or `Map` carries the dense tiers' null scan: a null element or value is a located, accumulating invalid (`tags.1: must not be null`), never written into the domain; a valid container still passes by reference, unrebuilt.
  - The scan needs a properly parameterised container; a raw or wildcard-argument one is written as sent.
  - A same-typed `Optional` needs no scan (it cannot hold a null element), so its identity write is unconditional: a present empty sets empty, absent leaves unchanged.
  - A nested record whose wire differs is patched wholesale through its own full mapping spec. Deep merge is out of scope.

One vocabulary, both tiers. The element leaf a full spec lifts elementwise is exactly the leaf its PATCH sibling lifts:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:update_container}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:update_container_usage}}
```

The sparse tier is law-checked like every other, through the same `MappingLaws` harness:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/RecordMappingBookLawsTest.java:update_laws}}
```

Identity (an all-absent wire is the identity update), idempotence (applying the same patch twice equals applying it once, which holds because the generated edits *set* and *parse*, never *modify*), and validation (a present invalid field fails). The same laws hold over container elements:

``` java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/RecordMappingBookLawsTest.java:update_container_laws}}
```

~~~admonish tip title="Why this matters"
The three sparse laws are operational guarantees, not formalities. Identity means a client sending an empty PATCH cannot corrupt anything. Idempotence means a retried request (a timeout, a nervous user, an at-least-once queue) lands exactly as if sent once. Validation means sparseness is never an excuse: what the client did send is checked as strictly as a full submission. Hand-written PATCH handlers get these properties by luck; the one `MappingLaws` call above checks them in your build.
~~~

~~~admonish tip title="At the Spring boundary: the PATCH endpoint, end to end"
The hkj-spring example app serves `PATCH /api/users/{id}` through exactly this tier; [Sparse PATCH at the Spring boundary](../spring/spring_boot_integration.md#sparse-patch) walks the controller, the not-found-plus-validation channel, and the slice test. An all-`FieldError` payload takes [the 422 leg](../spring/spring_boot_integration.md#the-422-leg) (`hkj.web.validation-field-error-status`, default 422).
~~~

---

~~~admonish info title="Key Takeaways"
* **Beans map with the full feature set**: only the read/write mechanics differ, and the tiers stay honest (`asIso` is withheld where unset properties make reads fallible)
* **Sparse semantics are an explicit opt-in**: `UpdateSpec` gives a PATCH bean null-as-absent; nothing is inferred from the shape alone
* **Sparseness never weakens validation**: present fields still parse through their leaves, and every bad one is a located, accumulated `FieldError`
* **One vocabulary serves both tiers**: the element leaf a full spec lifts is the leaf its PATCH sibling lifts
~~~

~~~admonish tip title="See Also"
- [Multi-Edit and Sparse Updates](../optics/multi_edit.md) - The hand-written `Edits.accumulate` this tier generates
- [Sparse PATCH at the Spring boundary](../spring/spring_boot_integration.md#sparse-patch) - The controller story
- [The Emission Tiers](tiers.md) - Where `updateFrom` sits among the surfaces
~~~

---

**Previous:** [The Emission Tiers](tiers.md)
**Next:** [Generic Specs](generics.md)

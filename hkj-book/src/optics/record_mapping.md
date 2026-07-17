# Record Mapping

_One interface, both directions: a total `build` from domain to DTO, and an accumulating, located `parse` back._

Every service boundary maps between a rich domain record and a flat wire DTO. Hand-written mappers drift; reflection-based mappers fail at runtime and know nothing about validation. `@GenerateMapping` derives the mapping **at compile time, reflection-free**, from an interface you own, and because the fallible direction returns `Validated<NonEmptyList<FieldError>, Domain>`, a bad DTO reports **every** bad field at once, each located by name.

~~~admonish info title="What You'll Learn"
- How `@GenerateMapping` derives a compile-time, reflection-free mapper from an interface you own: a total `build` and an accumulating, field-located `parse`
- Handling type-differing fields with `ValidatedPrism` leaves, renaming components with `@MapField`, and how nesting, containers, and recursion compose into dotted error paths
- Dispatching a mapping over two sealed hierarchies, exhaustively in both directions
- Reading the emission tiers so the generated surface (`asIso`, `asLens`, `asValidatedPrism`) only ever offers what the field correspondences can lawfully support
- Assembling one target from several sources with `@GenerateMerge`
- Typing an error's diagnostic context, and retiring the untyped `Map<String, Object>`, with `@GenerateErrorEnvelope`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [RecordMappingBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java)** - the page includes it directly, so it is compiled and run by the build.

[GenerateMappingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GenerateMappingExample.java)
~~~

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:basics_usage}}
```

The two directions have different shapes, and that asymmetry runs through the whole page:

```
   build : Domain ──▶ DTO      total, always succeeds
   parse : DTO ──▶ Domain      fallible, reports every bad field at once
                               Validated<NonEmptyList<FieldError>, Domain>
```

The generated class is `<Spec>Impl` beside the spec, used through its `INSTANCE` constant. A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

---

## Validated leaves

Where the two sides differ in type, the boundary conversion is a [`ValidatedPrism`](validated_prism.md) supplied as a **zero-parameter `default` method named after the domain component**:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:leaf_usage}}
```

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when the two component types are identical, so a `ValidatedPrism<String, String>` can validate or normalise a field the types alone would copy verbatim.
~~~

---

## Renames: `@MapField`

A rename is an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:rename_spec}}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises.

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
                          displayName ──┘ dropped, never read
```

`build` fills the component by applying the getter to the whole domain value. `parse` **ignores** it: the data is derivable, so parse stays total and accumulating over the remaining components. (A mapping whose only extra is a derived field is *total-parse*: the accumulating parse still runs, it simply cannot fail.)

The optic is a `Getter` because a derived field is single-valued, exactly one focus computed from the whole domain value. A `Fold`, with its zero-to-many focuses, has no single-component meaning here.

**How the two `default` families are told apart.** Leaves are named after *domain* components and return `ValidatedPrism`; derived fields are named after *wire-only* components and return `Getter`. The processor matches the two differently:

- A zero-parameter `default` returning `Getter` is *always* claimed as a derived field, and validated as one. So give getter-shaped utility helpers a parameter or a different return type, or they will be mistaken for derived fields.
- A `default` returning `ValidatedPrism` is matched by name; an unmatched one stays an inert helper.

Four shapes are rejected, each with a what/why/fix diagnostic:

- a `Getter` named after a *domain* component (ambiguous with a leaf);
- a `Getter` naming nothing on the wire;
- a `Getter` with the wrong type arguments;
- a `@MapField` rename targeting a component a derived field already fills.

**Derived fields and the emission tiers.** A spec with any derived field never emits `asIso()`: the wire round trip recomputes the derived component, so it is an identity only for wire values that were already consistent. Combining a derived field with a projection (a wire otherwise smaller than the domain) is rejected too, because the projection's `asLens()` write-back could never honour a component that `build` recomputes.

---

## Nesting, containers, and recursion

A component whose two sides are themselves mapped by **another spec in the same compilation** nests automatically, and failures compose into dotted paths:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:nesting_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:nesting_usage}}
```

Containers lift the same way:

- `List` and `Optional` components lift through the element's leaf or spec.
- `Map` components lift their **values**; keys pass through untouched, and each entry's failures are located by its key, so a bad value under key `en` reports as `attributes.en.email`.

Because nesting is *delegation* (each spec's `Impl` exposes `asValidatedPrism()`, so a whole mapping plugs in wherever a leaf does), recursion terminates by construction: a self-referential `Tree(String value, List<Tree> children)` maps with an empty spec and round-trips any finite tree.

~~~admonish note title="Map keys are located by `toString()`"
The rendered path uses each key's `toString()`, so a key containing a dot looks the same as deeper nesting, and two distinct keys whose renderings collide share a location. The structured `FieldError` path list stays exact regardless, holding the whole key as one segment, and every error is still reported.
~~~

---

## Sealed hierarchies

A `MappingSpec` over two **sealed interfaces** dispatches over the permitted subtype pairs, one spec per pair, exhaustively in both directions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:sealed_spec}}

// generated PaymentMappingImpl.build:
//   return switch (domain) {
//     case Card v -> CardMappingImpl.INSTANCE.build(v);
//     case Bank v -> BankMappingImpl.INSTANCE.build(v);
//   };
```

A domain subtype without a spec, or a wire subtype nothing produces, is a compile error: the dispatch cannot be partial.

---

## The emission tiers: truthful types

The field correspondences select what the Impl can lawfully offer; nothing is fabricated:

| Spec shape | Generated surface |
|---|---|
| All components identity-matched (lossless) | `build`, total `parse`, **`asIso()`** |
| Any fallible leaf, nested spec or derived field | `build`, accumulating `parse`, no `asIso` |
| Wire record with *fewer* components (lossy projection) | `build` + **`asLens()`** whose `set` writes the projected components back, **no `parse`** (the dropped components cannot be reconstructed) |
| Every parse-capable mapping | **`asValidatedPrism()`**: the mapping as a leaf, so it nests and lifts |

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:projection_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:projection_usage}}
// department written back, age kept: a lawful lens, not a fake inverse
```

### Law-checked, in the repo and in your tests

"Lawfully offer" is verified, not promised: every emission tier above (lossless iso, projection lens, fallible leaf, nested spec, `List`/`Optional`/`Map` lifting, sealed dispatch, derived fields) is compiled and law-checked in the Higher-Kinded-J build itself, against the published [`hkj-test` law harness](../tooling/test_assertions.md#optic-laws). Your own specs get the same guarantee with one call from a test, where `hkj-test` lives:

``` java
import org.higherkindedj.optics.laws.MappingLaws;

{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/mapping/RecordMappingBookLawsTest.java:laws}}
```

The overloads follow the tiers:

- **Lossless mapping:** pass `asIso()` plus `asValidatedPrism()` to check the iso laws, both round trips, and the coherence between the two surfaces.
- **Projection:** pass `asLens()` with a domain value and two wire values.
- **Fallible tier:** pass `asValidatedPrism()` with a parsing and a non-parsing wire value.
- **Derived-field (total-parse) mapping:** `build` recomputes what `parse` ignores, so only the non-derived components round-trip. The domain-sample overload `assertMappingLaws(prism, domainValue)` asserts exactly that and nothing stronger.

A spec with a derived field *and* a fallible leaf is better served by the fallible overload, given a parseable wire value whose derived components match what `build` would produce (this keeps the no-parse check). Reserve the domain-sample overload for total-parse mappings, where no wire value can fail.

~~~admonish tip title="Mapping types you don't own"
The annotation sits on *your* spec interface, never on the mapped types, so third-party records and sealed hierarchies from compiled libraries map without being annotatable: `interface VendorOrderMapping extends MappingSpec<com.vendor.OrderRecord, OrderDto> {}` works today. Bean-shaped foreign types (getter/setter DTOs) are the one un-owned shape that the mapping processor does not currently support.
~~~

---

## Merging several sources: `@GenerateMerge`

The forward-only sibling: assemble one target from **several** sources, declared entirely by the spec method's signature, no class literals, no inverse (truthful types):

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:merge_spec}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:merge_usage}}
```

Each target component fills from the one source with a same-named component: identity when the types match, through a `ValidatedPrism` leaf when they differ, or through a sibling `@GenerateMapping` spec (the `customer` below parses through `CustomerMappingImpl`, and failures locate as dotted paths):

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/RecordMappingBook.java:nested_merge_spec}}
```

Ambiguity (two sources carrying the component) and unfilled components are compile errors, and the return type must tell the truth: fallible fills demand the `Validated` return; an identity-only merge must declare the plain target.

---

## Generating error envelopes: `@GenerateErrorEnvelope`

The third generator in the family targets the other end of the boundary: the typed domain error a fallible mapping produces. A sealed error hierarchy re-declares the same envelope (`code`, `message`, `timestamp`, `context`) on every variant, and `context` is usually an untyped `Map<String, Object>`. `@GenerateErrorEnvelope` supplies the envelope and types the context, so each variant declares only its domain-specific components plus one `ErrorEnvelope<C>` component:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:error_envelope}}
```

~~~admonish note title="Two senses of 'context'"
The *typed context* here is diagnostic metadata attached to an error value: a records-as-schema type such as `OrderErrorContext`. It is unrelated to the [`ErrorContext`](../effect/effect_contexts_error.md) effect type, which is a composable IO-plus-`Either` computation. This page's context is data carried on an error; that one is a way of running effects.
~~~

For `OrderError` the processor generates a companion named `OrderErrors` with three pieces:

- **A factory per variant.** `code` is the UPPER_SNAKE variant name and `message` its humanised form; the timestamp is read from a [`TimeSource`](../monads/io_monad.md), so an overload takes one explicitly and the convenience uses `TimeSource.system()`.
- **A fluent `context()` builder** over the context record's components.
- **An `editContext(error, edit)` wither** that rebuilds the concrete variant through an exhaustive switch.

Add a one-line `default` so the wither reads as an instance method, and construction plus enrichment matches the shape you would hand-write:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/mapping/OrderErrorBook.java:edit_context}}
```

The context type is discovered **structurally** from the `ErrorEnvelope` component's type argument, never a class literal, and every variant must agree on it. Three rules apply, each a what/why/fix diagnostic:

- the hierarchy, its variants, and the context record must be non-generic;
- permitted variants must be records; a nested sealed sub-hierarchy is rejected with a flatten-it fix, not recursed into;
- the context record's components must be nullable reference types. The all-absent context holds `null`, so primitives are rejected at compile time; and because a null-rejecting compact constructor cannot be detected by the processor, keep the context a plain nullable data carrier.

~~~admonish note title="Fine-grained or coarse variants?"
The design choice is about the *hierarchy*, not the annotation.

- **Fine-grained** (one variant per failure mode, each with its own typed fields, as in `MarketError`'s `FeedDisconnected` / `RiskLimitBreached` / `StaleData`): the generated `MarketErrors` factories carry everything, and no hand-written construction remains.
- **Coarse** (a variant grouping several codes, as in `OrderError`'s `CustomerError` covering `CUSTOMER_NOT_FOUND` and `CUSTOMER_SUSPENDED`): suits a boundary whose downstream `switch` presents failures by category. One generated factory per variant derives only one code, so these variants keep a hand-written factory per code, each calling the canonical constructor with `ErrorEnvelope.of(...)` and the generated builder.

Either way the repeated envelope and the untyped `Map<String, Object>` are gone. Reach for fine-grained variants when each failure mode is genuinely distinct, and group them when a boundary treats a whole category uniformly.
~~~

Two verbs keep the two operations distinct: `ErrorEnvelope.withContext(D)` is the record wither that **replaces** the context (and may change its type), while the generated `editContext(error, edit)` **transforms** the existing context through the builder, seeded from the current value. Reach for `withContext` to set a context, `editContext` to enrich one.

---

## Diagnostics and limits

Every rejection follows the processor's what/why/fix standard: the message states what is wrong, why the mapper needs it, and the code to write. Current limits, each with its own diagnostic:

- `parse` is assembled with [`Validated.fields()`](../monads/validated_assembly.md), which locates up to **16 components**; group larger records into nested records, which nest through their own specs.
- Nested and sealed resolution sees specs **in the same compilation**, and a spec extends `MappingSpec` and nothing else; inherited renames/leaves arrive with the full mapper.
- `Map` components lift values only: keys are identity, so differing key types, raw `Map`s and wildcard type arguments are compile errors.
- Projections are identity-only (a leaf would make the write-back fallible) and cannot carry derived fields (the write-back could never honour a recomputed component); generic types arrive with the full mapper.

~~~admonish tip title="See Also"
- [Validated Prisms](validated_prism.md) - The leaf optic every fallible correspondence is built from
- [Accumulating Assembly](../monads/validated_assembly.md) - The `fields()` builder behind the generated `parse`
- [Multi-Edit and Sparse Updates](multi_edit.md) - The update-side counterpart at the same boundary
- `GenerateMappingExample` in `hkj-examples` - every feature on this page, runnable; its `GenerateMappingExampleLawsTest` law-checks the example's mappings through `MappingLaws`
~~~

---

**Previous:** [Focus DSL with External Libraries](focus_external_bridging.md)
**Next:** [Kind Field Support](kind_field_support.md)

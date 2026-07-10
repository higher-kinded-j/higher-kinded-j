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

``` java
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;

@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {}

// Same-named, same-typed components match automatically:
PersonDto dto   = PersonMappingImpl.INSTANCE.build(person);      // total
Validated<NonEmptyList<FieldError>, Person> back =
    PersonMappingImpl.INSTANCE.parse(dto);                       // accumulating, located
```

The generated class is `<Spec>Impl` beside the spec, used through its `INSTANCE` constant. A spec nested in an outer class joins the enclosing simple names: `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

---

## Validated leaves

Where the two sides differ in type, the boundary conversion is a [`ValidatedPrism`](validated_prism.md) supplied as a **zero-parameter `default` method named after the domain component**:

``` java
@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() {   // wire first, domain second
    return EmailCodecs.EMAIL;
  }
}

CustomerMappingImpl.INSTANCE.parse(new CustomerDto("Bob", "not-an-email"));
// Invalid(NonEmptyList[email: not an email address])
```

~~~admonish tip title="A leaf beats an identity match"
An explicit leaf wins even when the two component types are identical, so a `ValidatedPrism<String, String>` can validate or normalise a field the types alone would copy verbatim.
~~~

---

## Renames: `@MapField`

A rename is an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "fullName")
  String name();          // Person.name <-> PersonDto.fullName
}
```

Each wire component takes exactly one domain source; colliding renames are compile errors, not surprises.

---

## Derived wire fields

A wire component with **no domain counterpart** can be computed from the whole domain value. Declare a zero-parameter `default` method named after the *wire* component, returning `Getter<Domain, WireComponentType>`:

``` java
import org.higherkindedj.optics.Getter;

public record Profile(String first, String last) {}
public record ProfileDto(String first, String last, String displayName) {}

@GenerateMapping
public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {
    return Getter.of(p -> p.first() + " " + p.last());
  }
}

ProfileMappingImpl.INSTANCE.build(new Profile("Ada", "Lovelace"));
// ProfileDto[first=Ada, last=Lovelace, displayName=Ada Lovelace]
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

`build` fills the component by applying the getter to the whole domain value; `parse` **ignores** it, because the data is derivable, and stays total and accumulating over the remaining components (a derived-only mapping is *total-parse*: the accumulating parse still runs, it simply cannot fail). The optic is a `Getter` because a derived field is single-valued, exactly one focus computed from the whole domain value; a `Fold` has zero-to-many focuses and no single-component meaning. Naming keeps the two `default` families apart: leaves are named after *domain* components and return `ValidatedPrism`, derived fields after wire-only components and return `Getter`. The two families are matched differently, though: a zero-parameter `default` returning `Getter` is *always* claimed as a derived field (and validated as one), while `ValidatedPrism`-returning defaults are matched by name and otherwise stay inert helpers, so give getter-shaped utility helpers parameters or a different return type. A `Getter` method named after a domain component is ambiguous and rejected, as is one naming nothing on the wire, one with the wrong type arguments, and a `@MapField` rename targeting a component a derived field also fills.

A spec with any derived field never emits `asIso()`: the wire round trip recomputes the derived component, so it is only an identity for wire values that were consistent to begin with. Combining a derived field with a projection (a wire otherwise smaller than the domain) is rejected too, because the projection's `asLens()` write-back could never honour a component `build` recomputes.

## Nesting, containers, and recursion

A component pair mapped by **another spec in the same compilation** nests automatically, and `List`/`Optional` components lift through the element's leaf or spec. `Map` components lift their **values** the same way; keys pass through untouched and each entry's failures are located by its key, so a bad value under key `en` reports as `attributes.en.email`. Keys are located via `toString()`, so in the *rendered* path a key containing a dot is indistinguishable from deeper nesting (the structured `FieldError` path list stays exact, holding the whole key as one segment), and distinct keys whose renderings collide share a location while every error is still reported. Failures compose into dotted paths:

``` java
public record Invoice(String id, Customer customer) {}
public record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto("Bob", "nope")));
// Invalid(NonEmptyList[customer.email: not an email address])
```

Because nesting is *delegation* (each spec's Impl exposes `asValidatedPrism()`, so a whole mapping plugs in wherever a leaf does), recursion terminates by construction: a self-referential `Tree(String value, List<Tree> children)` maps with an empty spec and round-trips any finite tree.

---

## Sealed hierarchies

A `MappingSpec` over two **sealed interfaces** dispatches over the permitted subtype pairs, one spec per pair, exhaustively in both directions:

``` java
@GenerateMapping public interface CardMapping extends MappingSpec<Card, CardDto> {}
@GenerateMapping public interface BankMapping extends MappingSpec<Bank, BankDto> {}
@GenerateMapping public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

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
public record Employee(String name, String department, int age) {}
public record EmployeeCardDto(String name, String department) {}

@GenerateMapping
public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}

Lens<Employee, EmployeeCardDto> badge = EmployeeCardMappingImpl.INSTANCE.asLens();
Employee moved = badge.set(new EmployeeCardDto("Ada", "Platform"), employee);
// department written back, age kept — a lawful lens, not a fake inverse
```

### Law-checked, in the repo and in your tests

"Lawfully offer" is verified, not promised: every emission tier above (lossless iso, projection lens, fallible leaf, nested spec, `List`/`Optional`/`Map` lifting, sealed dispatch, derived fields) is compiled and law-checked in the Higher-Kinded-J build itself, against the published [`hkj-test` law harness](../tooling/test_assertions.md#optic-laws). Your own specs get the same guarantee with one call from a test, where `hkj-test` lives:

``` java
import org.higherkindedj.optics.laws.MappingLaws;

MappingLaws.assertMappingLaws(
    CustomerMappingImpl.INSTANCE.asValidatedPrism(),
    new CustomerDto("Ada", "ada@example.org"),    // parses
    new CustomerDto("Bob", "not-an-email"));      // must not parse
```

The overloads follow the tiers: pass `asIso()` plus `asValidatedPrism()` for a lossless mapping (iso laws, both round trips, and the coherence between the two surfaces), `asLens()` with a domain value and two wire values for a projection, and `asValidatedPrism()` with a parsing and a non-parsing wire value for the fallible tier. For a derived-field mapping, `build` recomputes what `parse` ignores, so only the non-derived components round-trip; the domain-sample overload `assertMappingLaws(prism, domainValue)` asserts exactly that and nothing stronger. A spec with a derived field *and* a fallible leaf is better served by the fallible overload with a parseable wire value whose derived components match what `build` would produce, which keeps the no-parse check; reserve the domain-sample overload for total-parse mappings, where no wire value can fail.

~~~admonish tip title="Mapping types you don't own"
The annotation sits on *your* spec interface, never on the mapped types, so third-party records and sealed hierarchies from compiled libraries map without being annotatable: `interface VendorOrderMapping extends MappingSpec<com.vendor.OrderRecord, OrderDto> {}` works today. Bean-shaped foreign types (getter/setter DTOs) are the one un-owned shape that needs future work.
~~~

---

## Merging several sources: `@GenerateMerge`

The forward-only sibling: assemble one target from **several** sources, declared entirely by the spec method's signature, no class literals, no inverse (truthful types):

``` java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard dashboard = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

Each target component fills from the one source with a same-named component: identity when the types match, through a `ValidatedPrism` leaf when they differ, or through a sibling `@GenerateMapping` spec (the `customer` below parses through `CustomerMappingImpl`, and failures locate as dotted paths):

``` java
@GenerateMerge
public interface ProfileCardAssembly {
  Validated<NonEmptyList<FieldError>, ProfileCard> assemble(User user, Wrapper wrapper);
}
// Invalid(NonEmptyList[customer.email: not an email address])
```

Ambiguity (two sources carrying the component) and unfilled components are compile errors, and the return type must tell the truth: fallible fills demand the `Validated` return; an identity-only merge must declare the plain target.

---

## Generating error envelopes: `@GenerateErrorEnvelope`

The third generator in the family targets the other end of the boundary: the typed domain error a fallible mapping produces. A sealed error hierarchy re-declares the same envelope (`code`, `message`, `timestamp`, `context`) on every variant, and `context` is usually an untyped `Map<String, Object>`. `@GenerateErrorEnvelope` supplies the envelope and types the context, so each variant declares only its domain-specific components plus one `ErrorEnvelope<C>` component:

``` java
// The context is records-as-schema: nullable components, an all-absent default.
record OrderErrorContext(@Nullable OrderId orderId, @Nullable TraceId traceId) {}

@GenerateErrorEnvelope
public sealed interface OrderError {
  ErrorEnvelope<OrderErrorContext> envelope();          // declared once

  record OutOfStock(List<ProductId> products, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}
  record PaymentDeclined(CardRef card, ErrorEnvelope<OrderErrorContext> envelope)
      implements OrderError {}
}
```

~~~admonish note title="Two senses of 'context'"
The *typed context* here is diagnostic metadata attached to an error value: a records-as-schema type such as `OrderErrorContext`. It is unrelated to the [`ErrorContext`](../effect/effect_contexts_error.md) effect type, which is a composable IO-plus-`Either` computation. This page's context is data carried on an error; that one is a way of running effects.
~~~

For `OrderError` the processor generates `OrderErrors`: a factory per variant (`code` is the UPPER_SNAKE variant name, `message` its humanised form, the timestamp read from a `TimeSource`, so an overload takes one explicitly and the convenience uses `TimeSource.system()`), a fluent `context()` builder over the context record's components, and an `editContext(error, edit)` wither that rebuilds the concrete variant through an exhaustive switch. Add a one-line default so the wither reads as an instance method, and construction plus enrichment matches the shape you would hand-write:

``` java
default OrderError editContext(UnaryOperator<OrderErrors.ContextBuilder> edit) {
  return OrderErrors.editContext(this, edit);
}

OrderError error = OrderErrors.outOfStock(products)                 // typed factory
    .editContext(ctx -> ctx.orderId(orderId).traceId(traceId));     // typed context, not map.put
```

The context type is discovered **structurally** from the `ErrorEnvelope` component's type argument, never a class literal, and every variant must agree on it. The rules, each a what/why/fix diagnostic: the hierarchy and its variants and the context record must be non-generic; permitted variants must be records (a nested sealed sub-hierarchy is rejected with a flatten-it fix, not recursed into); and the context record's components must be nullable reference types (primitives are rejected at compile time, because the all-absent context holds `null`; a null-rejecting compact constructor cannot be detected by the processor, so keep the context a plain nullable data carrier).

The two example hierarchies show the spread of the win on purpose. `MarketError` is fine-grained: each variant is a single failure mode with its own typed fields (`FeedDisconnected`, `RiskLimitBreached`, `StaleData`), so its generated `MarketErrors` factories carry everything and no hand-written construction remains. `OrderError` is coarser: variants such as `CustomerError` and `InventoryError` group several codes (`CUSTOMER_NOT_FOUND` and `CUSTOMER_SUSPENDED`; `OUT_OF_STOCK`, `RESERVATION_FAILED`, `PARTIAL_STOCK`) because a downstream `switch` presents failures by category, and one generated factory per variant derives only one code. Those variants keep a hand-written factory per code, each calling the canonical constructor with `ErrorEnvelope.of(...)` and the generated builder.

Either way the repeated envelope and the untyped `Map<String, Object>` are gone; the fully-generated factories are the extra a fine-grained hierarchy earns. So the design guidance is about the *hierarchy*, not the annotation: reach for fine-grained variants when each failure mode is genuinely distinct, and group them when a boundary treats a whole category uniformly, accepting a hand-written factory per code as the price of that grouping.

Two verbs keep the two operations distinct: `ErrorEnvelope.withContext(D)` is the record wither that **replaces** the context (and may change its type), while the generated `editContext(error, edit)` **transforms** the existing context through the builder, seeded from the current value. Reach for `withContext` to set a context, `editContext` to enrich one.

## Diagnostics and limits

Every rejection follows the processor's what/why/fix standard: the message states what is wrong, why the mapper needs it, and the code to write. Current limits, each with its own diagnostic:

- `parse` is assembled with [`Validated.fields()`](../monads/validated_assembly.md), which locates up to **12 components**; group larger records into nested records, which nest through their own specs.
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

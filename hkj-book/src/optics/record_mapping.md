# Record Mapping

_One interface, both directions: a total `build` from domain to DTO, and an accumulating, located `parse` back._

Every service boundary maps between a rich domain record and a flat wire DTO. Hand-written mappers drift; reflection-based mappers fail at runtime and know nothing about validation. `@GenerateMapping` derives the mapping **at compile time, reflection-free**, from an interface you own — and because the fallible direction returns `Validated<NonEmptyList<FieldError>, Domain>`, a bad DTO reports **every** bad field at once, each located by name.

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

The generated class is `<Spec>Impl` beside the spec, used through its `INSTANCE` constant. A spec nested in an outer class joins the enclosing simple names — `Shop.CustomerMapping` generates `ShopCustomerMappingImpl`.

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

## Renames — `@MapField`

A rename is an abstract method named after the *domain* component, with `to` naming the *wire* component:

``` java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "fullName")
  String name();          // Person.name <-> PersonDto.fullName
}
```

Each wire component takes exactly one domain source — colliding renames are compile errors, not surprises.

## Nesting, containers, and recursion

A component pair mapped by **another spec in the same compilation** nests automatically, and `List`/`Optional` components lift through the element's leaf or spec. Failures compose into dotted paths:

``` java
public record Invoice(String id, Customer customer) {}
public record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto("Bob", "nope")));
// Invalid(NonEmptyList[customer.email: not an email address])
```

Because nesting is *delegation* (each spec's Impl exposes `asValidatedPrism()`, so a whole mapping plugs in wherever a leaf does), recursion terminates by construction — a self-referential `Tree(String value, List<Tree> children)` maps with an empty spec and round-trips any finite tree.

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

A domain subtype without a spec, or a wire subtype nothing produces, is a compile error — the dispatch cannot be partial.

## The emission tiers — truthful types

The field correspondences select what the Impl can lawfully offer; nothing is fabricated:

| Spec shape | Generated surface |
|---|---|
| All components identity-matched (lossless) | `build`, total `parse`, **`asIso()`** |
| Any fallible leaf or nested spec | `build`, accumulating `parse` — no `asIso` |
| Wire record with *fewer* components (lossy projection) | `build` + **`asLens()`** whose `set` writes the projected components back — **no `parse`** (the dropped components cannot be reconstructed) |
| Every parse-capable mapping | **`asValidatedPrism()`** — the mapping as a leaf, so it nests and lifts |

``` java
public record Employee(String name, String department, int age) {}
public record EmployeeCardDto(String name, String department) {}

@GenerateMapping
public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}

Lens<Employee, EmployeeCardDto> badge = EmployeeCardMappingImpl.INSTANCE.asLens();
Employee moved = badge.set(new EmployeeCardDto("Ada", "Platform"), employee);
// department written back, age kept — a lawful lens, not a fake inverse
```

~~~admonish tip title="Mapping types you don't own"
The annotation sits on *your* spec interface, never on the mapped types — so third-party records and sealed hierarchies from compiled libraries map without being annotatable: `interface VendorOrderMapping extends MappingSpec<com.vendor.OrderRecord, OrderDto> {}` works today. Bean-shaped foreign types (getter/setter DTOs) are the one un-owned shape that needs future work.
~~~

## Merging several sources — `@GenerateMerge`

The forward-only sibling: assemble one target from **several** sources, declared entirely by the spec method's signature — no class literals, no inverse (truthful types):

``` java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard dashboard = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

Each target component fills from the one source with a same-named component — identity when the types match, through a `ValidatedPrism` leaf when they differ, or through a sibling `@GenerateMapping` spec (the `customer` below parses through `CustomerMappingImpl`, and failures locate as dotted paths):

``` java
@GenerateMerge
public interface ProfileCardAssembly {
  Validated<NonEmptyList<FieldError>, ProfileCard> assemble(User user, Wrapper wrapper);
}
// Invalid(NonEmptyList[customer.email: not an email address])
```

Ambiguity (two sources carrying the component) and unfilled components are compile errors, and the return type must tell the truth: fallible fills demand the `Validated` return; an identity-only merge must declare the plain target.

## Diagnostics and limits

Every rejection follows the processor's what/why/fix standard — the message states what is wrong, why the mapper needs it, and the code to write. Current limits, each with its own diagnostic:

- `parse` is assembled with [`Validated.fields()`](../monads/validated_assembly.md), which locates up to **12 components** — group larger records into nested records, which nest through their own specs.
- Nested and sealed resolution sees specs **in the same compilation**, and a spec extends `MappingSpec` and nothing else — inherited renames/leaves arrive with the full mapper.
- Projections are identity-only (a leaf would make the write-back fallible), and generic types, `Map` value lifting and derived wire fields arrive with the full mapper.

~~~admonish tip title="See Also"
- [Validated Prisms](validated_prism.md) - The leaf optic every fallible correspondence is built from
- [Accumulating Assembly](../monads/validated_assembly.md) - The `fields()` builder behind the generated `parse`
- [Multi-Edit and Sparse Updates](multi_edit.md) - The update-side counterpart at the same boundary
- `GenerateMappingExample` in `hkj-examples` - every feature on this page, runnable
~~~

---

**Previous:** [Focus DSL with External Libraries](focus_external_bridging.md)
**Next:** [Kind Field Support](kind_field_support.md)

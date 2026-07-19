---
name: hkj-mapping
description: "Compile-time data assembly and mapping with Higher-Kinded-J. Use whenever the task involves: mapping a domain record to/from a wire DTO, request, response, entity or payload (@GenerateMapping, MappingSpec, @MapField); mapping a sealed domain hierarchy to a sealed DTO hierarchy; hand-written toDto()/fromDto()/toDomain()/toEntity() mapper or converter classes; replacing MapStruct/ModelMapper/Dozer or a reflective bean mapper; building one target record by merging several source records (@GenerateMerge); constructing a record from independently-validated parts, collecting every error (@GenerateAssembly, fields(), accumulate()); or giving a sealed error hierarchy a structured envelope with a typed context record (@GenerateErrorEnvelope, ErrorEnvelope, editContext). Also covers the arity rules (the hand-written fields()/accumulate() ladder stops at 16; @GenerateAssembly has no ceiling), parse/build law-checking with MappingLaws, and -parameters."
---

# Higher-Kinded-J Compile-Time Mapping and Assembly

You are helping a developer replace hand-written mapper, builder and converter boilerplate with
generated, reflection-free, **law-checked** code.

The shared idea across all four generators: **`build` is total, `parse` is fallible.** Going from a
validated domain type out to the wire cannot fail. Coming from the wire in can, so it returns
`Validated<NonEmptyList<FieldError>, Domain>` and reports **every** bad field, not just the first.

## When to load supporting files

- If the user wants a **complete worked DTO boundary**, load `reference/mapping-example.md`
- For **optics themselves** (`@GenerateLenses`, Focus DSL, `Edits`, `ValidatedPrism`), suggest `/hkj-optics`
- For **`ValidationPath` and the railway model**, suggest `/hkj-guide`
- For **testing the generated code**, suggest `/hkj-test` (`MappingLaws`)

---

## Which Generator?

| You have | You want | Use |
|----------|----------|-----|
| One domain record + one wire DTO | Convert both ways | **`@GenerateMapping`** |
| N source records | One target record | **`@GenerateMerge`** |
| One record + independently-validated parts | Construct it, collecting every error | **`@GenerateAssembly`** |
| A sealed error hierarchy | Codes, timestamps, and a typed context | **`@GenerateErrorEnvelope`** |

All live in `org.higherkindedj.optics.annotations`.

---

## `@GenerateMapping`: record <-> DTO

Declare the mapping as a **spec interface**; the processor writes the implementation.

<!-- verify -->
```java
@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
//                                                   ^domain    ^wire
```

Generated: `CustomerMappingImpl`, reached through `INSTANCE`.

<!-- verify -->
```java
CustomerDto dto = CustomerMappingImpl.INSTANCE.build(customer);              // total
Validated<NonEmptyList<FieldError>, Customer> back =
    CustomerMappingImpl.INSTANCE.parse(dto);                                 // accumulating
```

Naming rule: the generated class joins the spec's enclosing simple names, then appends `Impl`. A
top-level `CustomerMapping` gives `CustomerMappingImpl`; one nested inside `Shop` gives
`ShopCustomerMappingImpl`.

### Components are matched by name

Same name, same type on both sides: mapped automatically, no declaration needed. The spec only ever
says what is *not* obvious.

| Need | Declare on the spec |
|------|---------------------|
| Rename a field on the wire | an **abstract** method named after the domain component, annotated `@MapField(to = "fullName")` |
| A component that must be **parsed** (`String` -> `EmailAddress`) | a zero-arg `default` method named after the domain component, returning `ValidatedPrism<Wire, Domain>` |
| A wire-only field **derived** from the domain | a zero-arg `default` method returning `Getter<Domain, WireType>` |

<!-- verify -->
```java
public record Customer(String name, EmailAddress email) {}
public record CustomerDto(String name, String email) {}

@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {

  // Customer.email is an EmailAddress; the DTO carries a String.
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
```

A leaf `ValidatedPrism` beats an identity match, so declaring one is how you say "this component
needs validating on the way in".

Renaming, and deriving a wire-only field:

<!-- verify -->
```java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "fullName")
  String name();                       // abstract: it is a declaration, not an implementation
}

@GenerateMapping
public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {         // filled on build, ignored on parse
    return Getter.of(p -> p.first() + " " + p.last());
  }
}
```

### Nesting and collections come free

A leaf prism declared for a component applies **elementwise** through `List`, `Optional` and `Map`:

<!-- verify -->
```java
public record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}
public record TeamDto(String name, List<String> members, Optional<String> lead) {}

@GenerateMapping
public interface TeamMapping extends MappingSpec<Team, TeamDto> {
  default ValidatedPrism<String, EmailAddress> members() { return emailPrism(); }
  default ValidatedPrism<String, EmailAddress> lead()    { return emailPrism(); }

  // `private static`, not `default`: a zero-arg `default` returning a ValidatedPrism is matched
  // BY NAME against a domain component, so a shared helper must stay out of that namespace.
  private static ValidatedPrism<String, EmailAddress> emailPrism() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
```

And a nested record resolves through its **sibling spec**, so this spec is empty and still maps
`Customer` <-> `CustomerDto` inside the invoice:

<!-- verify -->
```java
public record Invoice(String id, Customer customer) {}
public record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}   // nothing to say
```

Recursive records (a `Tree` of `Tree`) work too.

### Sealed hierarchies dispatch exhaustively

A `MappingSpec` may be declared over two **sealed interfaces**, not just two records. Give each
permitted subtype pair its own spec; the parent dispatches over them:

```java
@GenerateMapping public interface CardMapping    extends MappingSpec<Card, CardDto> {}
@GenerateMapping public interface BankMapping    extends MappingSpec<Bank, BankDto> {}
@GenerateMapping public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

// PaymentMappingImpl.build dispatches:
//   case Card v -> CardMappingImpl.INSTANCE.build(v);
//   case Bank v -> BankMappingImpl.INSTANCE.build(v);
```

A domain subtype with no spec, or a wire subtype nothing produces, is a **compile error**: the
dispatch cannot be partial.

### What else gets generated

Depending on what the mapping can honour:

| Mapping is | You also get |
|------------|--------------|
| Lossless both ways | `asIso()` |
| A lossy projection (domain -> wire only) | `asLens()`, and **no** `parse` |
| Parse-capable | `asValidatedPrism()` |
| Carrying **any** derived field | **no `asIso()`**: the round trip recomputes the derived component, so it is not an identity |

Combining a derived field **with** a projection (a wire otherwise smaller than the domain) is
rejected outright: the projection's `asLens()` write-back could never honour a component that
`build` recomputes. There is no build-only fallback; drop the derived methods, or widen the wire.

### Things worth knowing

- **`Map` components lift values only.** Keys pass through by identity; a failure is located by its
  key.
- **The mapped record need not be yours.** The annotation sits on *your spec interface*, never on
  the record, so third-party and library records map fine.
- **The wire need not be a record.** A bean-shaped DTO maps too - detected in three shapes: a no-args
  constructor with `setX` setters; an immutable bean with a static `builder()`/`newBuilder()`
  (Lombok, Immutables, AutoValue, protobuf); or the JAXB convention, where a getter-only `List` is
  filled with `getItems().addAll(...)`. `build` fills through setters or the builder, `parse` reads
  through getters. A `null` bean property becomes a located `FieldError` (never thrown), and a domain
  `Optional<T>` bridges to a nullable bean property `T`. See `reference/mapping-example.md`.
- **`parse` is capped at 16 components**: it is assembled via `Validated.fields()`.
- **It is law-checked.** Assert `build`/`parse` round-trip with `MappingLaws.assertMappingLaws(...)`
  from `hkj-test`.

---

## `@GenerateMerge`: N sources -> one target

The spec is a plain interface, with no marker supertype. **The single abstract method signature *is*
the declaration.**

<!-- verify -->
```java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard d = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

Each component of `Dashboard` is filled from the source that has a component of that name. Merging
is **forward-only**: there is no inverse, because in general you cannot split a merged record back
into its sources. The types say so rather than pretending otherwise.

Two things are **compile errors**, by design:

- a target component that no source can fill
- a target component that *two* sources could fill (ambiguous)

For a fallible merge, return `Validated<NonEmptyList<FieldError>, Target>`:

```java
@GenerateMerge
public interface DashboardAssembly {
  Validated<NonEmptyList<FieldError>, Dashboard> assemble(User user, Account account);

  default ValidatedPrism<String, EmailAddress> email() { ... }   // leaf, as with @GenerateMapping
}
```

A component can also resolve through a sibling `@GenerateMapping` spec; nested failures then locate
themselves as dotted paths, e.g. `"customer.email"`.

---

## `@GenerateAssembly`: construct one record, collecting every error

Put it on the record. You get a staged builder with one method per component, in declaration order.

<!-- verify -->
```java
@GenerateAssembly
record SignupUser(String name, String email, int age) {}
```

<!-- verify -->
```java
Validated<NonEmptyList<FieldError>, SignupUser> result =
    SignupUserAssembly.fields()
        .name(Validated.validNel("Ada"))
        .email(Validated.validNel("ada@example.com"))
        .age(Validated.validNel(36))
        .assemble();          // every invalid field reported, not just the first
```

Generated class is `<Record>Assembly` (nested records join their enclosing names).

### Nested records compose

If a component's type is *itself* `@GenerateAssembly`-annotated, its companion's result drops
straight in, and the outer component name **prefixes the inner error paths** (`address.zip`):

```java
@GenerateAssembly record Address(String zip, String city) {}
@GenerateAssembly record Customer(String name, Address address) {}

Validated<NonEmptyList<FieldError>, Customer> customer =
    CustomerAssembly.fields()
        .name(parseName(dto.name()))
        .address(AddressAssembly.fields()          // a sub-companion's result, used directly
            .zip(parseZip(dto.zip()))
            .city(parseCity(dto.city()))
            .assemble())
        .assemble();                                // a bad zip reports as "address.zip"
```

**Generic records are not supported.** Annotating one is a compile error; use the hand-written
`fields()` ladder for those, and for any record you cannot annotate.

**When to prefer the hand-written ladder instead.** `Validated.fields()` / `Validated.accumulate()`
(and their `Path` and `EitherOrBoth` twins, described in `/hkj-guide`) do the same job without annotating
the record. Use `@GenerateAssembly` when you want the component *names* checked by the compiler at
each stage; use the ladder for ad-hoc assembly or for a record you do not own.

**The ladder is capped at 16 components; `@GenerateAssembly` is not.** The generator emits a curried
`Validated.ap` chain at exactly the record's arity, so a 17-component record is fine. The ladder
stops at `ValidatedFields16`, which offers only `apply(...)` and no further `.field(...)`. So for a
wide record, the annotation is the answer, not the workaround.

---

## `@GenerateErrorEnvelope`: structured errors with a typed context

This is a **core + codegen** feature, not a web one. Put it on a sealed error hierarchy to give
every variant a code, a message, a timestamp, and a **typed context**.

```java
@GeneratePrisms
@GenerateErrorEnvelope
public sealed interface OrderError permits OrderError.OutOfStock, OrderError.PaymentDeclined {
  ErrorEnvelope<OrderErrorContext> envelope();

  default String code() { return envelope().code(); }
}

// The context is an ordinary record. Every component must be nullable.
record OrderErrorContext(OrderId orderId, TraceId traceId) {}
```

`ErrorEnvelope<C>` (`org.higherkindedj.hkt.error`) is a record
`(String code, String message, Instant timestamp, C context)`.

The processor generates an `OrderErrors` companion: per-variant factories, a fluent `context()`
builder, and `editContext`:

<!-- verify -->
```java
OrderError error = OrderErrors.editContext(
    OrderErrors.outOfStock(products),
    ctx -> ctx.orderId(orderId).traceId(traceId));    // attach context as it propagates
```

The typed context record **replaces the `Map<String, Object>` context bag**: records as schema, so a
typo is a compile error and the fields are discoverable.

Timestamps are read from an injected `TimeSource` (see `/hkj-arch`), which is what makes error
records deterministic in tests.

> **No Spring integration.** `hkj-spring` does not inspect `ErrorEnvelope`. Error -> HTTP status is
> still resolved by **class name** (`ErrorStatusCodeMapper` / `DefaultErrorStatusCodeStrategy`).
> Adding `@GenerateErrorEnvelope` does not change your status codes.

---

## Setup

Nothing beyond the standard processor wiring. If you use the Gradle or Maven plugin, **`-parameters`
is added for you**; do not set it by hand. `@GenerateMapping`'s copy strategies rely on it to read
constructor parameter names.

Processor diagnostics name the offending element, the reason, and the fix, so read the compile error
before rearranging the spec.

---

## Common Mistakes

| Mistake | Fix |
|---------|-----|
| Annotating the *record* with `@GenerateMapping` | It goes on the **spec interface**. That is what lets you map records you do not own |
| Expecting `parse` from a lossy projection | A projection drops data, so it cannot be inverted. You get `asLens()`, not `parse` |
| Expecting `@GenerateMerge` to give you a reverse split | Merging is forward-only by design |
| `Validated.fields()` will not take a 17th field | The **ladder** stops at 16. `@GenerateAssembly` has no ceiling, so annotate the record instead (`FOR_COMPREHENSION` is a separate ceiling, still 12) |
| Two nested specs generating the same `Impl` | Nested specs join their enclosing simple names; rename one |
| Assuming sealed hierarchies are unsupported | They are supported. Give each permitted subtype pair a spec; the parent dispatches |
| `@GenerateAssembly` on a **generic** record | Not supported. Use the hand-written `fields()` ladder |
| Expecting `asIso()` alongside a derived field | A derived field is recomputed on the round trip, so the mapping is not an identity |
| Adding `-parameters` manually | The plugin already does it |
| Assuming `@GenerateErrorEnvelope` changes HTTP status codes | It does not. Status is mapped by error **class name** |

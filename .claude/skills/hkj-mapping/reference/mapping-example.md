# Worked Example: A Complete DTO Boundary

The job: keep the domain model honest (`EmailAddress`, not `String`) while the wire stays flat and
JSON-friendly, without hand-writing a mapper and without a reflective one.

## The Problem With the Hand-Written Mapper

<!-- verify -->
```java
// Before: correct today, quietly wrong after the next field is added.
public final class CustomerMapper {

  public static CustomerDto toDto(Customer c) {
    return new CustomerDto(c.name(), c.email().value());
  }

  public static Customer fromDto(CustomerDto dto) {
    if (!dto.email().contains("@")) {
      throw new IllegalArgumentException("bad email");   // first failure wins; caller gets nothing useful
    }
    return new Customer(dto.name(), new EmailAddress(dto.email()));
  }
}
```

Three defects, none of which the compiler can see:

1. **Add a field to `Customer`** and this still compiles. It silently stops copying it.
2. `fromDto` throws on the first bad field, so a form with three errors reports one.
3. Nothing checks that `toDto` and `fromDto` are actually inverses.

## The Spec

State only what is *not* obvious. Name-and-type matches need no declaration.

<!-- verify -->
```java
public record EmailAddress(String value) {}
public record Customer(String name, EmailAddress email) {}
public record CustomerDto(String name, String email) {}

@GenerateMapping
public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {

  // The only asymmetry: EmailAddress on the domain side, String on the wire.
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw -> raw.contains("@")
            ? Validated.validNel(new EmailAddress(raw))
            : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}
```

Add a field to `Customer` now and the build **fails** until the mapping can account for it. That is
the whole point.

## Using It

```java
CustomerDto dto = CustomerMappingImpl.INSTANCE.build(customer);   // total: cannot fail

Validated<NonEmptyList<FieldError>, Customer> parsed =
    CustomerMappingImpl.INSTANCE.parse(dto);                      // every bad field, not just the first

return parsed.fold(
    errors   -> badRequest(errors),      // NonEmptyList<FieldError>, each one located
    customer -> ok(service.save(customer)));
```

## Nesting Is Free

An invoice carries a customer. The sibling spec is found automatically, so `InvoiceMapping` has
nothing to declare:

<!-- verify -->
```java
public record Invoice(String id, Customer customer) {}
public record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}
```

A failure inside the nested customer locates itself as a dotted path: `customer.email`.

## Collections Lift Elementwise

Declare the leaf prism once per component; `List`, `Optional` and `Map` lift it for you. (For a
`Map`, values lift and keys pass through by identity; a failure is located by its key.)

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

## Wire-Only Fields

A field the wire wants and the domain does not store. `Getter` is `build`-only: `parse` ignores it,
which is exactly right: it is derived, so there is nothing to read back.

<!-- verify -->
```java
public record Profile(String first, String last) {}
public record ProfileDto(String first, String last, String displayName) {}

@GenerateMapping
public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {
    return Getter.of(p -> p.first() + " " + p.last());
  }
}
```

## Renaming

The wire calls it `fullName`; the domain calls it `name`. Declare it **abstract**: you are stating
a fact, not supplying an implementation.

<!-- verify -->
```java
@GenerateMapping
public interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "fullName")
  String name();
}
```

## Prove the Round-Trip

Defect 3 from the top: nobody checked that the two directions agree. `MappingLaws` does:

<!-- verify -->
```java
@Test
void customerMappingObeysTheLaws() {
  MappingLaws.assertMappingLaws(
      CustomerMappingImpl.INSTANCE.asValidatedPrism(),   // an optic, not the spec impl
      new CustomerDto("Ada", "ada@example.com"),         // a wire sample that parses
      new CustomerDto("Ada", "not-an-email"));           // one that does not
}
```

Two points that are easy to get wrong:

- **`assertMappingLaws` takes an optic**, never the spec impl. Reach for the tier the mapping
  actually emits: `asValidatedPrism()` when it can parse, `asIso()` when it is lossless, `asLens()`
  for a projection.
- This mapping has a **fallible leaf** (the email prism), so pass the two-wire-sample overload. The
  single-sample overload would pass without ever exercising the failure path, which is the one
  thing you wanted checked.

`hkj-test` is test-scope, so the laws live in a test, not beside the spec.

## Merging Several Sources

A dashboard drawn from three aggregates. The method signature **is** the declaration:

<!-- verify -->
```java
@GenerateMerge
public interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

Dashboard d = DashboardAssemblyImpl.INSTANCE.assemble(user, account, settings);
```

If two sources both offer a component of the same name, that is ambiguous and the build fails. If no
source can fill a component, the build fails. Neither is discoverable in a hand-written merge until
production.

Merging is forward-only: there is no generated inverse, because a merged record generally cannot be
split back into its sources. The API refuses to pretend otherwise.

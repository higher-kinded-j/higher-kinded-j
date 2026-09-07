# Focus DSL with External Libraries

## _Bridging Fluent Navigation into Immutables, Lombok, and Beyond_

> *"The best interface is no interface at all. The second best is the one that feels invisible."*
>
> – Don Norman

---

The best solution would be Focus DSL working seamlessly with every external type, no extra code. That is not possible; external libraries do not know about our optics. The second best is what this page builds: a bridge that feels invisible in use, so a developer navigates from `CompanyFocus.headquarters()` into `AddressOptics.city()` without thinking about the boundary.

Here is the whole pattern, compiled by the build. A Focus path over your own records, joined by `.via()` to an optic generated for a type you do not own:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/CompanyBridge.java:traversal_bridge}}
```

`CompanyFocus.departments()` and `DepartmentFocus.staff()` are generated from your records; `ContactInfoOptics.email()` is generated from a spec interface for an Immutables value. `.via()` does not care which is which.

~~~admonish info title="What You'll Learn"
- How to extend Focus navigation into external library types
- Building spec interfaces for Immutables-generated classes
- Where `toLens()` is needed and where `.via()` already suffices
- Organising a bridge layer so the boundary stays discoverable
~~~

~~~admonish example title="See Example Code"
[CompanyBridge.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/CompanyBridge.java) | [AddressOpticsSpec.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/external/AddressOpticsSpec.java)

The page includes these directly, so the build compiles every bridge shown here.
~~~

---

## The Bridge Pattern

Focus covers the types you own. Spec interfaces cover the types you do not. The bridge layer is a handful of composed constants where the two meet, and the only real decision is which copy strategy the external library's generated code calls for. [Database Records with JOOQ](copy_strategies.md#choosing-a-strategy) sets those four out in full; this page is about what happens once the external optics exist.

---

## A Complete Example: Immutables Value Objects

### The External Types

`Address` and `ContactInfo` come from an Immutables-generated module: accessor methods with no `get` prefix, and wither methods for modification.

```java
@Value.Immutable
public interface Address {
  String street();
  String city();
  String postcode();
  String country();

  // Immutables generates withStreet(...), withCity(...), and a builder
}
```

### The Spec Interface

`@Wither` names each getter and wither pair, and the processor generates `AddressOptics`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/external/AddressOpticsSpec.java:spec}}
```

### Your Local Domain

Ordinary records, annotated as usual, with fields whose types come from the external library:

<!-- verify -->
```java
@GenerateFocus
@GenerateLenses
public record Company(String name, Address headquarters, List<Department> departments) {}

@GenerateFocus
@GenerateLenses
public record Department(String name, Employee manager, List<Employee> staff, Address location) {}

@GenerateFocus
@GenerateLenses
public record Employee(String id, String name, ContactInfo contact, BigDecimal salary) {}
```

### The Bridge

`.via()` covers the composition itself. `toLens()` earns its place when you want the *result* to be a reusable `Lens` constant rather than a path, which is what lets code that has never heard of the Focus DSL consume it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/CompanyBridge.java:lens_bridge}}
```

Using them looks like nothing in particular, which is the point. These values come from running [FocusBridgingExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/bridge/FocusBridgingExample.java) against a sample `Acme Corp` whose headquarters is in New York and whose two departments sit in Boston and Chicago:

<!-- verify -->
```java
String city = CompanyBridge.HEADQUARTERS_CITY.get(acme);
// "New York": read straight through the Immutables Address

Company moved = CompanyBridge.HEADQUARTERS_CITY.set("Seattle", acme);
// headquarters.city is "Seattle"; every other Address field, and acme itself, is untouched

List<String> emails = CompanyBridge.allCompanyEmails().getAll(acme);
// [alice@acme.com, bob@acme.com, carol@acme.com]: three records deep, across the boundary

List<String> phones = CompanyBridge.allCompanyPhones().getAll(acme);
// [617-555-0101, 617-555-0102, 312-555-0201]
```

~~~admonish tip title="Why this matters"
Nothing in that chain knows it crosses a library boundary. `DepartmentFocus.staff()` is generated from your record, `ContactInfoOptics.email()` from an Immutables interface, and `.via()` treats them identically because both are optics. The boundary that usually shows up as a helper method per field, or a stream pipeline with a manual rebuild, disappears into one composition.
~~~

~~~admonish note title="`.via()` needs no conversion"
`.via()` has overloads for `Lens`, `Prism`, `Affine`, `Traversal`, `Iso` and for other Focus paths, so a generated external optic composes onto a Focus path as it stands. Reach for `toLens()`, `toAffine()` or `toTraversal()` only when the value you want to keep is a raw optic.
~~~

---

## Using the Bridges

The service layer sees one vocabulary:

<!-- verify -->
```java
public final class CompanyService {

  /** Relocate the headquarters. */
  public Company relocate(Company company, String newCity) {
    return CompanyBridge.HEADQUARTERS_CITY.set(newCity, company);
  }

  /** Every employee email, company-wide. */
  public List<String> allEmails(Company company) {
    return CompanyBridge.allCompanyEmails().getAll(company);
  }

  /** Standardise phone numbers across the whole company. */
  public Company standardisePhones(Company company, UnaryOperator<String> formatter) {
    return CompanyBridge.allCompanyPhones().modifyAll(formatter, company);
  }

  /** Raise salaries in one city only. */
  public Company raiseIn(Company company, String city, BigDecimal multiplier) {
    return CompanyFocus.departments()
        .filter(d -> CompanyBridge.departmentCity().get(d).equals(city))
        .via(DepartmentFocus.staff())
        .via(EmployeeFocus.salary())
        .modifyAll(salary -> salary.multiply(multiplier), company);
  }
}
```

The last method is worth a second look: `filter` narrows the traversal by a predicate that itself reads through a bridge, and the rest of the chain carries on unchanged.

---

## Other Libraries, Same Shape

Each library differs only in which copy strategy its generated code exposes.

**Lombok `@Builder(toBuilder = true)`:**

```java
@ImportOptics
interface LombokPersonOpticsSpec extends OpticsSpec<LombokPerson> {

  @ViaBuilder(getter = "getName", setter = "name")
  Lens<LombokPerson, String> name();
}
```

**AutoValue:**

```java
@ImportOptics
interface AutoPersonOpticsSpec extends OpticsSpec<AutoPerson> {

  @ViaBuilder(getter = "name", toBuilder = "toBuilder", setter = "setName")
  Lens<AutoPerson, String> name();
}
```

**Protocol Buffers:**

```java
@ImportOptics
interface PersonProtoOpticsSpec extends OpticsSpec<PersonProto> {

  @ViaBuilder(getter = "getName", setter = "setName")
  Lens<PersonProto, String> name();
}
```

See [Database Records with JOOQ](copy_strategies.md) for the strategies in full.

---

## Organising the Bridge Layer

```text
com.myapp.optics/
├── external/
│   ├── AddressOpticsSpec.java       # spec, generates AddressOptics
│   └── ContactInfoOpticsSpec.java   # spec, generates ContactInfoOptics
├── bridges/
│   ├── CompanyBridge.java           # composed constants for the Company domain
│   └── OrderBridge.java
└── package-info.java                # @ImportOptics for the simple types
```

Three habits keep the layer honest:

**Name the bridges for the domain, not the path.** `employeeEmail()` says what it reaches; `contact().andThen(email())` says how, which is the part that should be free to change.

**Document the boundary in one place.** A short class comment naming the external library and its copy mechanism saves the next reader a trip through the generated sources.

**Test the round trip.** The external type is the part you do not control, so a lens-law check at the boundary is worth more than it is anywhere else:

<!-- verify -->
```java
@Test
void headquartersCityBridgeIsLawful() {
  LensLaws.assertLensLaws(CompanyBridge.HEADQUARTERS_CITY, acme, "Boston", "Seattle");
}
```

---

~~~admonish info title="Key Takeaways"
* **The bridge is one composition, not a framework.** A Focus path plus a generated external optic, joined by `.via()` or `toLens().andThen(...)`.
* **`.via()` already accepts raw optics.** Convert with `toLens()` when you want a reusable `Lens` constant rather than a path.
* **A `List` field's generated path is already element-level**, so it composes with the external optic directly.
* **The library only changes the copy strategy.** Immutables takes `@Wither`, Lombok, AutoValue and Protobuf take `@ViaBuilder`, and everything downstream is identical.
* **Law-check at the boundary.** The external type is the half you do not own; `LensLaws` is cheap insurance against a surprising `withX`.
~~~

~~~admonish tip title="See Also"
- [Optics for External Types](importing_optics.md): `@ImportOptics` and auto-detection
- [Taming JSON with Jackson](optics_spec_interfaces.md): spec interfaces for predicate-based discrimination
- [Database Records with JOOQ](copy_strategies.md): the copy strategies in full
- [Test Assertions](../tooling/test_assertions.md#optic-laws): `LensLaws` and the rest of the law family
~~~

~~~admonish tip title="Further Reading"
- **Immutables**: [immutables.github.io](https://immutables.github.io/): generated builders and withers, the `@Wither` case
- **AutoValue**: [github.com/google/auto](https://github.com/google/auto/tree/main/value): google's value types, a `@ViaBuilder` case
- **Protocol Buffers**: [protobuf.dev](https://protobuf.dev/): generated message builders, another `@ViaBuilder` case
~~~

---

**Previous:** [Database Records with JOOQ](copy_strategies.md)
**Next:** [Kind Field Support](kind_field_support.md)

# Focus DSL with External Libraries

## _Bridging Fluent Navigation into Immutables, Lombok, and Beyond_

> "The best interface is no interface at all. The second best is the one that feels invisible."
> -- Don Norman

Norman's design principle applies perfectly to our bridging challenge. The *best* solution would be if Focus DSL worked seamlessly with every external type, no extra code needed. That's not possible; external libraries don't know about our optics. But the *second best* is what we build here: bridges that feel invisible in use. Once we've set up the spec interfaces and bridge utilities, developers navigate from `CompanyFocus.headquarters()` into `AddressOptics.city()` without thinking about library boundaries.

The goal is fluent, IDE-guided navigation that crosses from your code into Immutables, Lombok, or any external library as if the boundary didn't exist. The setup takes a few minutes per external type; the benefit lasts the lifetime of your codebase.

~~~admonish info title="What You'll Learn"
- How to extend Focus DSL's fluent navigation into external library types
- Building spec interfaces for Immutables-generated classes
- Creating seamless navigation chains that cross library boundaries
- Patterns for maintaining IDE discoverability across your entire domain
~~~

---

## The Bridge Pattern

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Your Application                             │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Local Domain Records                      │   │
│  │          (Company, Department, Employee)                     │   │
│  │               @GenerateFocus @GenerateLenses                 │   │
│  └───────────────────────────┬──────────────────────────────────┘   │
│                              │                                      │
│                        references                                   │
│                              │                                      │
│  ┌───────────────────────────┼──────────────────────────────────┐   │
│  │                           ▼                                  │   │
│  │              ┌─────────────────────────┐                     │   │
│  │              │    Spec Interfaces      │                     │   │
│  │              │  AddressOpticsSpec      │                     │   │
│  │              │  ContactInfoOpticsSpec  │                     │   │
│  │              │     @ImportOptics       │                     │   │
│  │              └─────────────────────────┘                     │   │
│  │                           │                                  │   │
│  │                     generates                                │   │
│  │                           ▼                                  │   │
│  │              ┌─────────────────────────┐                     │   │
│  │              │   AddressOptics.java    │◀─── Generated!      │   │
│  │              │   ContactInfoOptics.java│                     │   │
│  │              └─────────────────────────┘                     │   │
│  │                                                              │   │
│  │                    BRIDGE LAYER                              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                         composes                                    │
│                              ▼                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                  External Libraries                          │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │   │
│  │  │  Immutables  │  │    Lombok    │  │   AutoValue  │        │   │
│  │  │   Address    │  │    types     │  │    types     │        │   │
│  │  │  ContactInfo │  │              │  │              │        │   │
│  │  └──────────────┘  └──────────────┘  └──────────────┘        │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

The Focus DSL provides nice, IDE-friendly navigation for types we own:

```java
@GenerateFocus
@GenerateLenses
record Company(String name, Address headquarters, List<Department> departments) {}

// Fluent navigation with full IDE autocomplete
String city = CompanyFocus.headquarters().city().get(company);
```

But our domain models rarely exist in isolation. They reference types from external libraries: Immutables value objects, Lombok-generated classes, JDK types, third-party DTOs. When we hit these boundaries, the Focus chain ends.

**The bridge pattern solves this**: we generate spec interface optics for external types, then compose them with Focus paths to maintain fluent navigation throughout.

---

## A Complete Example: Company Domain with Immutables

Let's build a realistic domain where local records reference Immutables-generated value objects.

### The External Types (Immutables)

The `Address` and `ContactInfo` types are generated by the Immutables library:

```java
// Defined in an external library or generated module
@Value.Immutable
public interface Address {
    String street();
    String city();
    String postcode();
    String country();

    // Immutables generates:
    // - ImmutableAddress.builder()
    // - ImmutableAddress.copyOf(address).withCity("New York")
    // - address.withCity("New York") (wither methods)
}

@Value.Immutable
public interface ContactInfo {
    String email();
    String phone();
    Optional<String> fax();

    // Similar builder and wither methods generated
}
```

### Our Local Domain (Records with Focus)

Our application's domain model references these external types:

```java
@GenerateFocus
@GenerateLenses
record Employee(
    String id,
    String name,
    ContactInfo contact,      // Immutables type
    BigDecimal salary
) {}

@GenerateFocus
@GenerateLenses
record Department(
    String name,
    Employee manager,
    List<Employee> staff,
    Address location          // Immutables type
) {}

@GenerateFocus
@GenerateLenses
record Company(
    String name,
    Address headquarters,     // Immutables type
    List<Department> departments
) {}
```

### The Spec Interfaces for Immutables

Now we create spec interfaces to generate optics for the Immutables types:

```java
@ImportOptics
public interface AddressOptics extends OpticsSpec<Address> {

    // Immutables generates withX() methods, so we use @Wither
    @Wither(value = "withStreet", getter = "street")
    Lens<Address, String> street();

    @Wither(value = "withCity", getter = "city")
    Lens<Address, String> city();

    @Wither(value = "withPostcode", getter = "postcode")
    Lens<Address, String> postcode();

    @Wither(value = "withCountry", getter = "country")
    Lens<Address, String> country();
}

@ImportOptics
public interface ContactInfoOptics extends OpticsSpec<ContactInfo> {

    @Wither(value = "withEmail", getter = "email")
    Lens<ContactInfo, String> email();

    @Wither(value = "withPhone", getter = "phone")
    Lens<ContactInfo, String> phone();

    @Wither(value = "withFax", getter = "fax")
    Lens<ContactInfo, Optional<String>> fax();
}
```

### Bridging Focus to External Optics

Now we create bridge optics that connect Focus paths to the external type optics:

```java
/**
 * Bridge utilities for connecting Focus DSL to Immutables types.
 * These compose Focus paths with spec interface optics.
 */
public final class CompanyBridge {

    // ===== Company → Address (headquarters) bridging =====

    /** Focus path to headquarters, bridged to city */
    public static final Lens<Company, String> HEADQUARTERS_CITY =
        CompanyFocus.headquarters().toLens()
            .andThen(AddressOptics.city());

    /** Focus path to headquarters, bridged to postcode */
    public static final Lens<Company, String> HEADQUARTERS_POSTCODE =
        CompanyFocus.headquarters().toLens()
            .andThen(AddressOptics.postcode());

    // ===== Department → Address (location) bridging =====

    /** Focus path to department location city */
    public static Lens<Department, String> departmentCity() {
        return DepartmentFocus.location().toLens()
            .andThen(AddressOptics.city());
    }

    // ===== Employee → ContactInfo bridging =====

    /** Focus path to employee email */
    public static Lens<Employee, String> employeeEmail() {
        return EmployeeFocus.contact().toLens()
            .andThen(ContactInfoOptics.email());
    }

    /** Focus path to employee phone */
    public static Lens<Employee, String> employeePhone() {
        return EmployeeFocus.contact().toLens()
            .andThen(ContactInfoOptics.phone());
    }

    // ===== Deep traversals across boundaries =====
    // Note: For List<T> fields, generated Focus returns TraversalPath<S, T>
    // directly (auto-traversed), so no .each() is needed.
    // TraversalPath provides getAll() and modifyAll() directly.

    /** All employee emails in a department */
    public static TraversalPath<Department, String> allStaffEmails() {
        // staff() returns TraversalPath<Department, Employee>
        return DepartmentFocus.staff()
            .via(EmployeeFocus.contact())
            .via(ContactInfoOptics.email());
    }

    /** All department cities in a company */
    public static TraversalPath<Company, String> allDepartmentCities() {
        // departments() returns TraversalPath<Company, Department>
        return CompanyFocus.departments()
            .via(DepartmentFocus.location())
            .via(AddressOptics.city());
    }

    /** All employee emails across an entire company */
    public static TraversalPath<Company, String> allCompanyEmails() {
        // Compose TraversalPaths - no .each() needed
        return CompanyFocus.departments()
            .via(DepartmentFocus.staff())
            .via(EmployeeFocus.contact())
            .via(ContactInfoOptics.email());
    }
}
```

---

## Using the Bridges

Now our business logic enjoys fluent, IDE-discoverable navigation that crosses library boundaries:

```java
public class CompanyService {

    /**
     * Relocate company headquarters to a new city.
     */
    public Company relocateHeadquarters(Company company, String newCity) {
        return CompanyBridge.HEADQUARTERS_CITY.set(newCity, company);
    }

    /**
     * Update postcodes for all departments in a region.
     * Note: departments() already returns TraversalPath (auto-traversed)
     */
    public Company updateRegionalPostcodes(Company company, String region, String newPostcode) {
        return CompanyFocus.departments()
            .filter(dept -> DepartmentFocus.location().toLens().get(dept)
                .country().equals(region))
            .via(DepartmentFocus.location())
            .via(AddressOptics.postcode())
            .modifyAll(__ -> newPostcode, company);
    }

    /**
     * Get all unique cities where the company operates.
     */
    public Set<String> getAllOperatingCities(Company company) {
        Set<String> cities = new HashSet<>();
        cities.add(CompanyBridge.HEADQUARTERS_CITY.get(company));
        cities.addAll(CompanyBridge.allDepartmentCities().getAll(company));
        return cities;
    }

    /**
     * Send announcement to all employees via email.
     */
    public List<String> getAllEmployeeEmails(Company company) {
        return CompanyBridge.allCompanyEmails().getAll(company);
    }

    /**
     * Standardise phone format across all employees.
     * Note: Both departments() and staff() return TraversalPaths (auto-traversed)
     */
    public Company standardisePhoneNumbers(Company company, UnaryOperator<String> formatter) {
        // Navigate: Company → departments[] → staff[] → contact → phone
        return CompanyFocus.departments()
            .via(DepartmentFocus.staff())
            .via(EmployeeFocus.contact())
            .via(ContactInfoOptics.phone())
            .modifyAll(formatter, company);
    }

    /**
     * Give raises to all staff in a specific city.
     */
    public Company giveRaisesToCity(Company company, String city, BigDecimal raisePercent) {
        BigDecimal multiplier = BigDecimal.ONE.add(raisePercent.divide(BigDecimal.valueOf(100)));

        return CompanyFocus.departments()
            .filter(dept -> CompanyBridge.departmentCity().get(dept).equals(city))
            .via(DepartmentFocus.staff())
            .via(EmployeeFocus.salary())
            .modifyAll(salary -> salary.multiply(multiplier), company);
    }
}
```

Every method maintains full type safety and IDE autocomplete throughout the entire navigation chain, even when crossing into Immutables-generated types.

---

## The Bridge Builder Pattern

For complex domains, we can create a more systematic bridge builder:

```java
/**
 * Type-safe builder for creating bridge optics.
 */
public final class BridgeBuilder<S, A> {

    private final Lens<S, A> baseLens;

    private BridgeBuilder(Lens<S, A> baseLens) {
        this.baseLens = baseLens;
    }

    public static <S, A> BridgeBuilder<S, A> from(Focus<S, A> focus) {
        return new BridgeBuilder<>(focus.toLens());
    }

    public <B> BridgeBuilder<S, B> andThen(Lens<A, B> next) {
        return new BridgeBuilder<>(baseLens.andThen(next));
    }

    public Lens<S, A> build() {
        return baseLens;
    }
}

// Usage
Lens<Company, String> hqCity = BridgeBuilder
    .from(CompanyFocus.headquarters())
    .andThen(AddressOptics.city())
    .build();
```

---

## Working with Different External Libraries

### Lombok @Builder

Lombok generates builders, so we use `@ViaBuilder`:

```java
// Lombok-generated class
@Builder(toBuilder = true)
@Value
public class LombokPerson {
    String name;
    int age;
    String email;
}

// Spec interface
@ImportOptics
public interface LombokPersonOptics extends OpticsSpec<LombokPerson> {

    @ViaBuilder(getter = "getName", setter = "name")
    Lens<LombokPerson, String> name();

    @ViaBuilder(getter = "getAge", setter = "age")
    Lens<LombokPerson, Integer> age();

    @ViaBuilder(getter = "getEmail", setter = "email")
    Lens<LombokPerson, String> email();
}
```

### AutoValue

AutoValue generates similar patterns:

```java
@AutoValue
public abstract class AutoPerson {
    public abstract String name();
    public abstract int age();

    public abstract Builder toBuilder();

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setName(String name);
        public abstract Builder setAge(int age);
        public abstract AutoPerson build();
    }
}

@ImportOptics
public interface AutoPersonOptics extends OpticsSpec<AutoPerson> {

    @ViaBuilder(getter = "name", toBuilder = "toBuilder", setter = "setName")
    Lens<AutoPerson, String> name();

    @ViaBuilder(getter = "age", toBuilder = "toBuilder", setter = "setAge")
    Lens<AutoPerson, Integer> age();
}
```

### Protocol Buffers

Protobuf messages have builders:

```java
// Generated by protoc
public final class PersonProto {
    public String getName() { ... }
    public Builder toBuilder() { ... }

    public static final class Builder {
        public Builder setName(String value) { ... }
        public PersonProto build() { ... }
    }
}

@ImportOptics
public interface PersonProtoOptics extends OpticsSpec<PersonProto> {

    @ViaBuilder(getter = "getName", setter = "setName")
    Lens<PersonProto, String> name();
}
```

---

## Best Practices

### 1. Organise Bridges by Domain

```
com.myapp.optics/
├── external/
│   ├── AddressOptics.java      # Spec interface for Immutables Address
│   ├── ContactInfoOptics.java  # Spec interface for Immutables ContactInfo
│   └── LombokPersonOptics.java # Spec interface for Lombok types
├── bridges/
│   ├── CompanyBridge.java      # Bridges for Company domain
│   └── OrderBridge.java        # Bridges for Order domain
└── package-info.java           # @ImportOptics for simple types
```

### 2. Document the Boundary

```java
/**
 * Bridges Focus DSL navigation into Immutables {@link Address} type.
 *
 * <p>Use these optics when navigating from local records into Address fields.
 * The underlying Address type is generated by Immutables and uses wither methods.
 *
 * @see AddressOptics for the raw optics
 */
public final class AddressBridges {
    // ...
}
```

### 3. Test Across Boundaries

```java
@Test
void bridgeShouldComposeCorrectly() {
    Address address = ImmutableAddress.builder()
        .street("123 Main St")
        .city("Boston")
        .postcode("02101")
        .country("USA")
        .build();

    Company company = new Company("Acme", address, List.of());

    // Test read through bridge
    assertThat(CompanyBridge.HEADQUARTERS_CITY.get(company))
        .isEqualTo("Boston");

    // Test write through bridge
    Company relocated = CompanyBridge.HEADQUARTERS_CITY.set("New York", company);
    assertThat(relocated.headquarters().city()).isEqualTo("New York");

    // Original unchanged (immutability)
    assertThat(company.headquarters().city()).isEqualTo("Boston");
}
```

### 4. Create Convenience Methods for Common Patterns

```java
public final class FocusBridges {

    /**
     * Bridge a Focus path to an external optic in one call.
     */
    public static <S, A, B> Lens<S, B> bridge(
            Focus<S, A> focus,
            Lens<A, B> externalOptic) {
        return focus.toLens().andThen(externalOptic);
    }

    /**
     * Bridge a FocusPath pointing to a List to an external optic.
     * Note: Use this for manually constructed FocusPath<S, List<A>>.
     * Generated Focus classes for List fields already return TraversalPath,
     * so you can use .via() directly without .each().
     */
    public static <S, A, B> Traversal<S, B> bridgeTraversal(
            FocusPath<S, ? extends List<A>> focus,
            Lens<A, B> externalOptic) {
        return focus.each().via(externalOptic).toTraversal();
    }
}

// Usage
Lens<Company, String> hqCity = FocusBridges.bridge(
    CompanyFocus.headquarters(),
    AddressOptics.city()
);
```

---

~~~admonish tip title="IDE Discoverability"
The bridge pattern preserves IDE autocomplete throughout:
1. Type `CompanyFocus.` → IDE shows `headquarters()`, `departments()`, etc.
2. Chain `.headquarters().` → IDE shows Focus methods
3. Call `.toLens().andThen(` → IDE shows optic composition options
4. Type `AddressOptics.` → IDE shows `city()`, `street()`, etc.

At each step, the IDE guides us to valid choices.
~~~

~~~admonish info title="Key Takeaways"
* Focus DSL provides fluent navigation for local types
* Spec interfaces generate optics for external library types
* The `.toLens()` method bridges Focus paths to raw optics
* Compose Focus + external optics for seamless cross-boundary navigation
* Organise bridges by domain and document the boundaries
~~~

---

~~~admonish tip title="Making Your External Library Integration Even Better"
Consider these opportunities to enhance your bridge layer integration:

- **Create domain-specific bridge utilities**: Layer meaningful names over raw compositions (`employeeEmail()` instead of `contact().andThen(email())`)
- **Build validation pipelines**: Combine bridge optics with `Validated` or `Either` for error-accumulating transformations when modifying external types
- **Add computed properties**: Extend bridges with derived values like `fullAddress()` that compose multiple fields
- **Create bidirectional conversions**: When external types have alternative representations, build prisms that safely convert between them
- **Wrap collection operations**: Build helper methods that expose common patterns like "all employees in city X" as first-class operations
- **Document boundary contracts**: Make clear what invariants the external library expects and how your bridges preserve them
- **Test lens laws at boundaries**: Verify that get/set round-trips work correctly, especially important when the external library has mutable components
~~~

---

## Further Reading

**Value Object Libraries:**
- [Immutables](https://immutables.github.io/) - Annotation processor for immutable value objects with generated builders and withers
- [Lombok](https://projectlombok.org/) - Code generation for Java: `@Builder`, `@Value`, `@Data`, and more
- [AutoValue](https://github.com/google/auto/tree/main/value) - Google's annotation processor for immutable value types
- [FreeBuilder](https://github.com/inferred/FreeBuilder) - Automatic generation of Builder patterns

**Serialisation Libraries with Builder Patterns:**
- [Protocol Buffers](https://protobuf.dev/) - Cross-language serialisation with generated message classes and builders
- [FlatBuffers](https://flatbuffers.dev/) - Memory-efficient serialisation with builder-based construction
- [Avro](https://avro.apache.org/) - Apache data serialisation with generated Java classes

**Related Higher-Kinded-J Documentation:**
- [Optics for External Types](importing_optics.md) - `@ImportOptics` basics and auto-detection
- [Taming JSON with Jackson](optics_spec_interfaces.md) - Spec interfaces for predicate-based type discrimination
- [Database Records with JOOQ](copy_strategies.md) - Working with builder patterns and mutable records

---

**Previous:** [Database Records with JOOQ](copy_strategies.md)
**Next:** [Kind Field Support](kind_field_support.md)

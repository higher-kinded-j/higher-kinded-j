# Getters: A Practical Guide

_Read one value, stored or computed, through an optic that composes and can never write._

~~~admonish info title="What You'll Learn"
- Generate getters with `@GenerateGetters`, or derive a computed value such as a full name with `Getter.of`
- Compose getters with `andThen`, and reach into a collection by composing with a `Fold` through `asFold()`
- Query a getter's one value with the `Fold` operations it inherits, such as `exists` and `find`
- Navigate nullable fields with `getMaybe` and `flatMap`, getting a `Maybe` instead of a `NullPointerException`
- Decide between a getter, a lens, a fold and direct field access
~~~

~~~admonish example title="See Example Code"
[GetterUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GetterUsageExample.java)
~~~

In previous guides, we explored **`Fold`** for querying zero or more elements from a structure. But what if you need to extract exactly one value? What if you want a composable accessor for a single, guaranteed-to-exist value? This is where **`Getter`** excels.

A **`Getter`** is the simplest read-only optic: it extracts precisely one value from a source. It plays the part of a derived accessor, such as a `fullName()` computed from two fields. Unlike an accessor, it is a value: it composes with other getters, answers `exists` and `find` itself, and after `asFold()` composes with any other optic's `asFold()`. [Choosing an optic](optics_intro.md#choosing-an-optic) sets it beside the other optic types.

---

## The Scenario: Employee Reporting System

Consider a corporate reporting system where you need to extract various pieces of information from employee records:

**The Data Model:**

<!-- verify -->
```java
@GenerateGetters
public record Person(String firstName, String lastName, int age, Address address) {}

@GenerateGetters
public record Address(String street, String city, String zipCode, String country) {}

@GenerateGetters
public record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}
```

**Common Extraction Needs:**
* "Get the CEO's full name"
* "Extract the CEO's city"
* "Check whether the CEO passes an age threshold"
* "Generate an employee's email address"
* "Compute the length of a person's full name"

A `Getter` makes these extractions type-safe, composable, and expressive.

---

## Getter vs Lens vs Fold: Understanding the Differences

| Aspect | Getter | Lens | Fold |
|--------|--------|------|------|
| **Focus** | Exactly one element | Exactly one element | Zero or more elements |
| **Can modify?** | No | Yes | No |
| **Core operation** | `get(source)` | `get(source)`, `set(value, source)` | `foldMap(monoid, fn, source)` |
| **Use case** | Computed/derived values | Field access with updates | Queries over collections |
| **Intent** | "Extract this single value" | "Get or set this field" | "Query all these values" |

**Key Insight**: Every `Lens` can be viewed as a `Getter` (its read-only half), but not every `Getter` can be a `Lens`. A `Getter` extends `Fold`, meaning it inherits all query operations (`exists`, `all`, `find`, `preview`) whilst guaranteeing exactly one focused element.

---

## A Step-by-Step Walkthrough

### Step 1: Creating Getters

#### Using `@GenerateGetters` Annotation

Annotating a record with **`@GenerateGetters`** creates a companion class (e.g., `PersonGetters`) containing a `Getter` for each field:

<!-- verify -->
```java
import org.higherkindedj.optics.annotations.GenerateGetters;

@GenerateGetters
public record Person(String firstName, String lastName, int age, Address address) {}
```

This generates:
* `PersonGetters.firstName()` → `Getter<Person, String>`
* `PersonGetters.lastName()` → `Getter<Person, String>`
* `PersonGetters.age()` → `Getter<Person, Integer>`
* `PersonGetters.address()` → `Getter<Person, Address>`

Plus convenience methods:
* `PersonGetters.getFirstName(person)` → `String`
* `PersonGetters.getLastName(person)` → `String`
* etc.

As with every generator in this chapter, a `targetPackage` attribute relocates the generated class; see [Customising the Generated Package](traversals.md#customising-the-generated-package).

#### Using Factory Methods

Create Getters programmatically for computed or derived values:

<!-- verify -->
```java
// Simple field extraction
Getter<Person, String> firstName = Getter.of(Person::firstName);

// Computed value
Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());

// Derived value
Getter<Person, String> initials = Getter.of(p ->
    p.firstName().charAt(0) + "." + p.lastName().charAt(0) + ".");

// Alternative factory (alias for of)
Getter<String, Integer> stringLength = Getter.to(String::length);
```

### Step 2: Core Getter Operations

#### **`get(source)`**: Extract the Focused Value

The fundamental operation: returns exactly one value:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:get}}
```

### Step 3: Composing Getters

Chain Getters together to extract deeply nested values:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:compose}}
```

#### Deep Composition Chain

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:deep_chain}}
```

### Step 4: Getter as a Fold

Since `Getter` extends `Fold`, you inherit all query operations, but they operate on exactly one element:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:as_fold}}
```

### Step 5: Combining Getters with Folds

Compose Getters with Folds for powerful queries. Two small tools make it work: `Fold.of` builds a fold from any function that lists the targets (here, the list itself), and `asFold()` moves a `Getter` into `Fold` position so the fold-composing `andThen` overload applies (a Getter already *is* a single-target Fold, so the conversion costs nothing).

The company here employs John Doe, Alice Johnson and Bob Williams, aged 30, 28 and 35:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:with_folds}}
```

### Step 6: Maybe-Based Getter Extension

~~~admonish note title="Maybe-Based Extension"
Higher-Kinded-J provides the `getMaybe` extension method that integrates `Getter` with the `Maybe` type, enabling null-safe navigation through potentially nullable fields. It is available via static import from `GetterExtensions`. `Maybe` itself, and the Maybe-versus-Optional trade-offs, were covered in [Folds: Maybe-Based Fold Extensions](folds.md#maybe-based-fold-extensions); this section shows only the Getter side.
~~~

#### The Challenge: Null-Safe Navigation

When working with nested data structures, intermediate values may be `null`, leading to `NullPointerException` if not handled carefully. Traditional approaches require verbose null checks at each level:

<!-- verify -->
```java
// Verbose traditional approach with null checks
Person ceo = company.ceo();
if (ceo != null) {
    Address ceoAddress = ceo.address();
    if (ceoAddress != null) {
        String city = ceoAddress.city();
        if (city != null) {
            System.out.println("City: " + city);
        }
    }
}
```

The `getMaybe` extension method provides a more functional approach by wrapping extracted values in `Maybe`, which explicitly models presence or absence without the risk of NPE. It plays the part of `Optional.ofNullable(getter.get(source))`, with `Maybe` in place of `Optional`.

#### How getMaybe Works

The `getMaybe` static method is imported from `GetterExtensions`:

<!-- verify -->
```java
import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;
```

**Signature:**
```java
public static <S extends @Nullable Object, A extends @Nullable Object>
    Maybe<@NonNull A> getMaybe(Getter<S, A> getter, S source)
```

It extracts a value using the provided `Getter` and wraps it in `Maybe`:
* If the extracted value is **non-null**, returns `Just(value)`
* If the extracted value is **null**, returns `Nothing`

#### Basic Usage Example

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:get_maybe}}
```

#### Safe Navigation with Composed Getters

The real power of `getMaybe` emerges when navigating nested structures with potentially null intermediate values. By using `flatMap`, you can safely chain extractions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:safe_navigation}}
```

**Key Pattern**: Use `flatMap` to chain `getMaybe` calls, creating a null-safe pipeline.

#### Comparison: Direct Access vs getMaybe

Understanding when to use each approach:

| Approach | Null Safety | Composability | Verbosity | Use Case |
|----------|-------------|---------------|-----------|----------|
| **Direct field access** | NPE risk | No | Minimal | Known non-null values |
| **Manual null checks** | Safe | No | Very verbose | Simple cases |
| **Optional chaining** | Safe | Limited | Moderate | Java interop |
| **getMaybe** | Safe | Excellent | Concise | Functional pipelines |

**Example Comparison:**

<!-- verify -->
```java
// Direct access (risky)
String city1 = person.address().city(); // NPE if address is null!

// Manual null checks (verbose)
String city2 = null;
if (person.address() != null && person.address().city() != null) {
    city2 = person.address().city();
}

// Optional chaining (better)
Optional<String> city3 = Optional.ofNullable(person.address())
    .map(Address::city);

// getMaybe (best for functional code)
Maybe<String> city4 = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr));
```

#### Integration with Maybe Operations

Once you've extracted a value into `Maybe`, you can leverage the full power of monadic operations:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:maybe_operations}}
```

#### When to Use getMaybe

**Use `getMaybe` when:**
* Navigating through **potentially null** intermediate values
* Building **functional pipelines** with Maybe-based operations
* You want **explicit presence/absence** semantics
* Composing with other Maybe-returning functions
* Working within HKT-based abstractions

<!-- verify -->
```java
// Perfect for null-safe navigation
Maybe<String> safeCity = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr));
```

**Use standard `get()` when:**
* You **know** the values are non-null
* You're working in **performance-critical** code
* You want **immediate NPE** on unexpected nulls (fail-fast)

<!-- verify -->
```java
// Fine when values are guaranteed non-null
String knownCity = cityGetter.get(knownAddress);
```

**Use `Getter.preview()` when:**
* You prefer Java's `Optional` for **interoperability**
* Working at API boundaries with standard Java code

<!-- verify -->
```java
// Good for Java interop
Optional<String> optionalCity = cityGetter.preview(address);
```

#### Real-World Scenario: Employee Profile Lookup

Here's a practical example showing how `getMaybe` simplifies complex null-safe extractions:

<!-- verify -->
```java
import org.higherkindedj.optics.Getter;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

public record Employee(String id, PersonalInfo personalInfo) {}
public record PersonalInfo(ContactInfo contactInfo, EmergencyContact emergencyContact) {}
public record ContactInfo(String email, String phone, Address address) {}
public record EmergencyContact(String name, String phone) {}

public class EmployeeService {
    private static final Getter<Employee, PersonalInfo> PERSONAL_INFO =
        Getter.of(Employee::personalInfo);
    private static final Getter<PersonalInfo, ContactInfo> CONTACT_INFO =
        Getter.of(PersonalInfo::contactInfo);
    private static final Getter<ContactInfo, Address> ADDRESS =
        Getter.of(ContactInfo::address);
    private static final Getter<Address, String> CITY =
        Getter.of(Address::city);

    // Extract employee city with full null safety
    public Maybe<String> getEmployeeCity(Employee employee) {
        return getMaybe(PERSONAL_INFO, employee)
            .flatMap(info -> getMaybe(CONTACT_INFO, info))
            .flatMap(contact -> getMaybe(ADDRESS, contact))
            .flatMap(addr -> getMaybe(CITY, addr));
    }

    // Generate location-based welcome message
    public String generateWelcomeMessage(Employee employee) {
        return getEmployeeCity(employee)
            .map(city -> "Welcome to our " + city + " office!")
            .orElse("Welcome to our company!");
    }

    // Check if employee is in specific city
    public boolean isEmployeeInCity(Employee employee, String targetCity) {
        return getEmployeeCity(employee)
            .map(city -> city.equalsIgnoreCase(targetCity))
            .orElse(false);
    }

    // Collect all cities from employee list (skipping unknowns)
    public List<String> getAllCities(List<Employee> employees) {
        return employees.stream()
            .map(this::getEmployeeCity)
            .filter(Maybe::isJust)
            .map(Maybe::get)
            .distinct()
            .toList();
    }

    // Get city or fallback to emergency contact location
    public String getAnyCityInfo(Employee employee) {
        Getter<PersonalInfo, EmergencyContact> emergencyGetter =
            Getter.of(PersonalInfo::emergencyContact);

        // Try primary address first
        Maybe<String> primaryCity = getMaybe(PERSONAL_INFO, employee)
            .flatMap(info -> getMaybe(CONTACT_INFO, info))
            .flatMap(contact -> getMaybe(ADDRESS, contact))
            .flatMap(addr -> getMaybe(CITY, addr));

        // If not found, could try emergency contact (simplified example)
        return primaryCity.orElse("Location unknown");
    }
}
```

#### Practical Pattern: Building Maybe-Safe Composed Getters

Create reusable null-safe extraction functions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/SafeGetters.java:safe_getters}}
```

~~~admonish example title="See Example Code"
See [GetterExtensionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/extensions/GetterExtensionsExample.java) for a runnable demonstration of `getMaybe` with practical scenarios.
~~~

---

## Built-in Helper Getters

Higher-Kinded-J provides several utility Getters:

### **`identity()`**: Returns the Source Itself

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:identity}}
```

Useful as a base case in composition or for type adaptation.

### **`constant(value)`**: Always Returns the Same Value

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GettersBook.java:constant}}
```

Useful for providing default values in pipelines.

### **`first()`** and **`second()`**: Pair Element Extractors

<!-- verify -->
```java
Map.Entry<Person, Address> pair = new AbstractMap.SimpleEntry<>(ceo, hqAddress);

Getter<Map.Entry<Person, Address>, Person> firstGetter = Getter.first();
Getter<Map.Entry<Person, Address>, Address> secondGetter = Getter.second();

Person person = firstGetter.get(pair);
// the entry's key, the CEO

Address address = secondGetter.get(pair);
// the entry's value, the headquarters address
```

---

## When to Use Getter vs Other Approaches

### Use Getter When

* You need **computed or derived values** without storing them
* You want **composable extraction** pipelines
* You're building **reporting or analytics** features
* You need **type-safe accessors** that compose with other optics
* You want **clear read-only intent** in your code

<!-- verify -->
```java
// Good: Computed value without storage overhead
Getter<Person, String> email = Getter.of(p ->
    p.firstName().toLowerCase() + "." + p.lastName().toLowerCase() + "@company.com");

// Good: Composable pipeline
Getter<Company, String> ceoCityUppercase = ceoGetter
    .andThen(addressGetter)
    .andThen(cityGetter)
    .andThen(Getter.of(String::toUpperCase));
```

### Use Lens When

* You need **both reading and writing**
* You're working with **mutable state** (functionally)

<!-- verify -->
```java
// Use Lens when you need to modify
Lens<Person, String> firstName = Lens.of(
    Person::firstName,
    (p, name) -> new Person(name, p.lastName(), p.age(), p.address()));

Person updated = firstName.set("Janet", person);
```

### Use Fold When

* You're querying **zero or more elements**
* You need to **aggregate or search** collections

<!-- verify -->
```java
// Use Fold for collections
Fold<Order, Product> itemsFold = Fold.of(Order::items);
List<Product> all = itemsFold.getAll(order);
```

### Use Direct Field Access When

* You need **maximum performance** with no abstraction overhead
* You're not composing with other optics

<!-- verify -->
```java
// Direct access when composition isn't needed
String name = person.firstName();
```

---

## Real-World Use Cases

### Data Transformation Pipelines

<!-- verify -->
```java
Getter<Person, String> email = Getter.of(p ->
    p.firstName().toLowerCase() + "." + p.lastName().toLowerCase() + "@techcorp.com");

Getter<Person, String> badgeId = Getter.of(p ->
    p.lastName().substring(0, Math.min(3, p.lastName().length())).toUpperCase() +
    String.format("%04d", p.age() * 100));

// Generate employee reports
for (Person emp : company.employees()) {
    System.out.println("Employee: " + fullName.get(emp));
    System.out.println("  Email: " + email.get(emp));
    System.out.println("  Badge: " + badgeId.get(emp));
}
```

### Analytics and Reporting

<!-- verify -->
```java
Fold<Company, Person> allEmployees = Fold.of(Company::employees);
Getter<Person, Integer> age = Getter.of(Person::age);

// Calculate total age
int totalAge = allEmployees.andThen(age.asFold())
    .foldMap(sumMonoid(), Function.identity(), company);

// Calculate average age
double averageAge = (double) totalAge / company.employees().size();

// Check conditions
boolean allFromUK = allEmployees.andThen(addressGetter.asFold())
    .andThen(countryGetter.asFold())
    .all(c -> c.equals("UK"), company);
```

### API Response Mapping

<!-- verify -->
```java
// Extract specific fields from nested API responses
Getter<ApiResponse, User> userGetter = Getter.of(ApiResponse::user);
Getter<User, Profile> profileGetter = Getter.of(User::profile);
Getter<Profile, String> displayName = Getter.of(Profile::displayName);

Getter<ApiResponse, String> userName = userGetter
    .andThen(profileGetter)
    .andThen(displayName);

String name = userName.get(response);
```

---

## Common Pitfalls

### Don't Use Getter When You Need to Modify

<!-- verify -->
```java
// Wrong: Getter can't modify
Getter<Person, String> nameGetter = Getter.of(Person::firstName);
// nameGetter.set("Jane", person); // Compilation error - no set method!
```

### Use Lens When Modification Is Required

<!-- verify -->
```java
// Correct: Use Lens for read-write access
Lens<Person, String> nameLens = Lens.of(Person::firstName, (p, n) ->
    new Person(n, p.lastName(), p.age(), p.address()));

Person updated = nameLens.set("Jane", person);
```

### Don't Overlook Null Safety

<!-- verify -->
```java
// Risky: Getter doesn't handle null values specially
Getter<NullableRecord, String> getter = Getter.of(NullableRecord::value);
String result = getter.get(new NullableRecord(null)); // hands the null straight back
```

### Handle Nulls Explicitly

<!-- verify -->
```java
// Safe: Handle nulls in the getter function
Getter<NullableRecord, String> safeGetter = Getter.of(r ->
    r.value() != null ? r.value() : "default");
```

---

## Complete, Runnable Example

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GetterExample.java:imports}}

{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/getters/GetterExample.java:getter_example}}
```

**Expected Output:**

```
{{#include ../../../hkj-examples/src/test/resources/golden/optics-getters-example-output.txt.golden}}
```

---

~~~admonish info title="Key Takeaways"
* **A getter is a pure function in optic form**: exactly one value out, never a write, composable with everything else
* **Computed values need no storage**: derive full names, flags, and metrics through `Getter.of` without adding fields
* **A Getter is already a Fold of one element**: `exists`, `all`, and `find` work directly; `asFold()` just restates the type where a `Fold` is wanted
* **`getMaybe` makes nulls explicit**: navigation through nullable fields returns `Maybe` instead of risking an NPE
* **Reach for `Lens` only when you also write**: using a Getter documents read-only intent in the type
~~~

~~~admonish tip title="See Also"
- [Folds](folds.md): the zero-or-more counterpart with monoid aggregation
- [Setters](setters.md): the write-only mirror of this page
- [Lenses](lenses.md): when the same field needs reading and writing
- [Production Readiness](production_readiness.md#read-cost): what a read and `getMaybe` cost, and when to cache a composed optic
~~~

---

**Previous:** [Folds](folds.md)
**Next:** [Setters](setters.md)

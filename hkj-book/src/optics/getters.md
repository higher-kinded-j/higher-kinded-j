# Getters: A Practical Guide

## _Composable Read-Only Access_

~~~admonish info title="What You'll Learn"
- How to extract values from structures using composable, read-only optics
- Using `@GenerateGetters` to create type-safe value extractors automatically
- Understanding the relationship between Getter and Fold
- Creating computed and derived values without storing them
- Composing Getters with other optics for deep data extraction
- Factory methods: `of`, `to`, `constant`, `identity`, `first`, `second`
- Null-safe navigation with `getMaybe` for functional optional handling
- When to use Getter vs Lens vs direct field access
- Building data transformation pipelines with clear read-only intent
~~~

~~~admonish title="Example Code"
[GetterUsageExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/GetterUsageExample.java)
~~~

In previous guides, we explored **`Fold`** for querying zero or more elements from a structure. But what if you need to extract exactly one value? What if you want a composable accessor for a single, guaranteed-to-exist value? This is where **`Getter`** excels.

A **`Getter`** is the simplest read-only optic: it extracts precisely one value from a source. Think of it as a **function wrapped in optic form**, enabling composition with other optics whilst maintaining read-only semantics.

---

## The Scenario: Employee Reporting System

Consider a corporate reporting system where you need to extract various pieces of information from employee records:

**The Data Model:**

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
* "Extract the company's headquarters city"
* "Calculate the CEO's age group"
* "Generate an employee's email address"
* "Compute the length of a person's full name"

A `Getter` makes these extractions type-safe, composable, and expressive.

---

## Think of Getters Like...

* **A functional accessor**: Extracting a specific value from a container
* **A read-only lens**: Focusing on one element without modification capability
* **A computed property**: Deriving values on-the-fly without storage
* **A data pipeline stage**: Composable extraction steps
* **A pure function in optic form**: Wrapping functions for composition

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

#### Customising the Generated Package

By default, generated classes are placed in the same package as the annotated record. You can specify a different package using the `targetPackage` attribute:

```java
// Generated class will be placed in org.example.generated.optics
@GenerateGetters(targetPackage = "org.example.generated.optics")
public record Person(String firstName, String lastName, int age, Address address) {}
```

This is useful when you need to avoid name collisions or organise generated code separately.

#### Using Factory Methods

Create Getters programmatically for computed or derived values:

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

```java
Person person = new Person("Jane", "Smith", 45, address);

Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());
String name = fullName.get(person);
// Result: "Jane Smith"

Getter<Person, Integer> age = Getter.of(Person::age);
int years = age.get(person);
// Result: 45
```

### Step 3: Composing Getters

Chain Getters together to extract deeply nested values:

```java
Getter<Person, Address> addressGetter = Getter.of(Person::address);
Getter<Address, String> cityGetter = Getter.of(Address::city);

// Compose: Person → Address → String
Getter<Person, String> personCity = addressGetter.andThen(cityGetter);

Person person = new Person("Jane", "Smith", 45,
    new Address("123 Main St", "London", "EC1A", "UK"));

String city = personCity.get(person);
// Result: "London"
```

#### Deep Composition Chain

```java
Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);
Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());
Getter<String, Integer> lengthGetter = Getter.of(String::length);

// Compose: Company → Person → String → Integer
Getter<Company, Integer> ceoNameLength = ceoGetter
    .andThen(fullNameGetter)
    .andThen(lengthGetter);

Company company = new Company("TechCorp", ceo, employees, headquarters);
int length = ceoNameLength.get(company);
// Result: 10 (length of "Jane Smith")
```

### Step 4: Getter as a Fold

Since `Getter` extends `Fold`, you inherit all query operations, but they operate on exactly one element:

```java
Getter<Person, Integer> ageGetter = Getter.of(Person::age);
Person person = new Person("Jane", "Smith", 45, address);

// preview() returns Optional with the single value
Optional<Integer> age = ageGetter.preview(person);
// Result: Optional[45]

// getAll() returns a single-element list
List<Integer> ages = ageGetter.getAll(person);
// Result: [45]

// exists() checks if the single value matches
boolean isExperienced = ageGetter.exists(a -> a > 40, person);
// Result: true

// all() checks the single value (always same as exists for Getter)
boolean isSenior = ageGetter.all(a -> a >= 65, person);
// Result: false

// find() returns the value if it matches
Optional<Integer> foundAge = ageGetter.find(a -> a > 30, person);
// Result: Optional[45]

// length() always returns 1 for Getter
int count = ageGetter.length(person);
// Result: 1

// isEmpty() always returns false for Getter
boolean empty = ageGetter.isEmpty(person);
// Result: false
```

### Step 5: Combining Getters with Folds

Compose Getters with Folds for powerful queries:

```java
Getter<Company, List<Person>> employeesGetter = Getter.of(Company::employees);
Fold<List<Person>, Person> listFold = Fold.of(list -> list);
Getter<Person, String> fullNameGetter = Getter.of(p -> p.firstName() + " " + p.lastName());

// Company → List<Person> → Person (multiple) → String
Fold<Company, String> allEmployeeNames = employeesGetter
    .asFold()  // Convert Getter to Fold
    .andThen(listFold)
    .andThen(fullNameGetter.asFold());

List<String> names = allEmployeeNames.getAll(company);
// Result: ["John Doe", "Alice Johnson", "Bob Williams"]

boolean hasExperienced = listFold.andThen(Getter.of(Person::age).asFold())
    .exists(age -> age > 40, employees);
// Result: depends on employee ages
```

### Step 6: Maybe-Based Getter Extension

~~~admonish title="Extension Method"
Higher-kinded-j provides the `getMaybe` extension method that integrates `Getter` with the `Maybe` type, enabling null-safe navigation through potentially nullable fields. This extension is available via static import from `GetterExtensions`.
~~~

#### The Challenge: Null-Safe Navigation

When working with nested data structures, intermediate values may be `null`, leading to `NullPointerException` if not handled carefully. Traditional approaches require verbose null checks at each level:

```java
// Verbose traditional approach with null checks
Person person = company.getCeo();
if (person != null) {
    Address address = person.getAddress();
    if (address != null) {
        String city = address.getCity();
        if (city != null) {
            System.out.println("City: " + city);
        }
    }
}
```

The `getMaybe` extension method provides a more functional approach by wrapping extracted values in `Maybe`, which explicitly models presence or absence without the risk of NPE.

#### Think of getMaybe Like...

* **A safe elevator** - Transports you to the desired floor, or tells you it's unavailable
* **A null-safe wrapper** - Extracts values whilst protecting against null
* **Optional's functional cousin** - Same safety guarantees, better functional composition
* **A maybe-monad extractor** - Lifts extraction into the Maybe context

#### How getMaybe Works

The `getMaybe` static method is imported from `GetterExtensions`:

```java
import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;
```

**Signature:**
```java
public static <S, A> Maybe<A> getMaybe(Getter<S, A> getter, S source)
```

It extracts a value using the provided `Getter` and wraps it in `Maybe`:
* If the extracted value is **non-null**, returns `Just(value)`
* If the extracted value is **null**, returns `Nothing`

#### Basic Usage Example

```java
import org.higherkindedj.optics.Getter;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

public record Person(String firstName, String lastName, Address address) {}
public record Address(String street, String city) {}

Getter<Person, String> firstNameGetter = Getter.of(Person::firstName);
Getter<Person, Address> addressGetter = Getter.of(Person::address);

Person person = new Person("Jane", "Smith", address);

// Extract non-null value
Maybe<String> name = getMaybe(firstNameGetter, person);
// Result: Just("Jane")

// Extract nullable value
Person personWithNullAddress = new Person("Bob", "Jones", null);
Maybe<Address> address = getMaybe(addressGetter, personWithNullAddress);
// Result: Nothing
```

#### Safe Navigation with Composed Getters

The real power of `getMaybe` emerges when navigating nested structures with potentially null intermediate values. By using `flatMap`, you can safely chain extractions:

```java
Getter<Person, Address> addressGetter = Getter.of(Person::address);
Getter<Address, String> cityGetter = Getter.of(Address::city);

// Safe navigation: Person → Maybe<Address> → Maybe<String>
Person personWithAddress = new Person("Jane", "Smith",
    new Address("123 Main St", "London"));

Maybe<String> city = getMaybe(addressGetter, personWithAddress)
    .flatMap(addr -> getMaybe(cityGetter, addr));
// Result: Just("London")

// Safe with null intermediate
Person personWithNullAddress = new Person("Bob", "Jones", null);

Maybe<String> noCity = getMaybe(addressGetter, personWithNullAddress)
    .flatMap(addr -> getMaybe(cityGetter, addr));
// Result: Nothing (safely handles null address)
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

```java
Getter<Person, Address> addressGetter = Getter.of(Person::address);
Getter<Address, String> cityGetter = Getter.of(Address::city);

Person person = new Person("Jane", "Smith",
    new Address("123 Main St", "London"));

// Extract and transform
Maybe<String> uppercaseCity = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr))
    .map(String::toUpperCase);
// Result: Just("LONDON")

// Extract with default
String cityOrDefault = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr))
    .getOrElse("Unknown");
// Result: "London"

// Extract and filter
Maybe<String> longCityName = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr))
    .filter(name -> name.length() > 5);
// Result: Just("London") (length is 6)

// Chain multiple operations
String report = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr))
    .map(city -> "Person lives in " + city)
    .getOrElse("Address unknown");
// Result: "Person lives in London"
```

#### When to Use getMaybe

**Use `getMaybe` when:**
* Navigating through **potentially null** intermediate values
* Building **functional pipelines** with Maybe-based operations
* You want **explicit presence/absence** semantics
* Composing with other Maybe-returning functions
* Working within HKT-based abstractions

```java
// Perfect for null-safe navigation
Maybe<String> safeCity = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr));
```

**Use standard `get()` when:**
* You **know** the values are non-null
* You're working in **performance-critical** code
* You want **immediate NPE** on unexpected nulls (fail-fast)

```java
// Fine when values are guaranteed non-null
String knownCity = cityGetter.get(knownAddress);
```

**Use `Getter.preview()` when:**
* You prefer Java's `Optional` for **interoperability**
* Working at API boundaries with standard Java code

```java
// Good for Java interop
Optional<String> optionalCity = cityGetter.preview(address);
```

#### Real-World Scenario: Employee Profile Lookup

Here's a practical example showing how `getMaybe` simplifies complex null-safe extractions:

```java
import org.higherkindedj.optics.Getter;
import org.higherkindedj.hkt.maybe.Maybe;
import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

public record Employee(String id, PersonalInfo personalInfo) {}
public record PersonalInfo(ContactInfo contactInfo, EmergencyContact emergencyContact) {}
public record ContactInfo(String email, String phone, Address address) {}
public record EmergencyContact(String name, String phone) {}
public record Address(String street, String city, String postcode) {}

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
            .getOrElse("Welcome to our company!");
    }

    // Check if employee is in specific city
    public boolean isEmployeeInCity(Employee employee, String targetCity) {
        return getEmployeeCity(employee)
            .filter(city -> city.equalsIgnoreCase(targetCity))
            .isJust();
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
        return primaryCity.getOrElse("Location unknown");
    }
}
```

#### Performance Considerations

`getMaybe` adds minimal overhead:

* **One null check**: Checks if the extracted value is null
* **One Maybe wrapping**: Creates `Just` or `Nothing` instance
* **Same extraction cost**: Uses `Getter.get()` internally

**Optimisation Tip**: For performance-critical hot paths where values are guaranteed non-null, use `Getter.get()` directly. For most business logic, the safety and composability of `getMaybe` far outweigh the negligible cost.

```java
// Hot path with guaranteed non-null (use direct get)
String fastAccess = nameGetter.get(person);

// Business logic with potential nulls (use getMaybe)
Maybe<String> safeAccess = getMaybe(addressGetter, person)
    .flatMap(addr -> getMaybe(cityGetter, addr));
```

#### Practical Pattern: Building Maybe-Safe Composed Getters

Create reusable null-safe extraction functions:

```java
public class SafeGetters {
    // Create a null-safe composed getter using Maybe
    public static <A, B, C> Function<A, Maybe<C>> safePath(
        Getter<A, B> first,
        Getter<B, C> second
    ) {
        return source -> getMaybe(first, source)
            .flatMap(intermediate -> getMaybe(second, intermediate));
    }

    // Usage example
    private static final Function<Person, Maybe<String>> SAFE_CITY_LOOKUP =
        safePath(
            Getter.of(Person::address),
            Getter.of(Address::city)
        );

    public static void main(String[] args) {
        Person person = new Person("Jane", "Smith", null);
        Maybe<String> city = SAFE_CITY_LOOKUP.apply(person);
        // Result: Nothing (safely handled null address)
    }
}
```

~~~admonish title="Complete Example"
See [GetterExtensionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/extensions/GetterExtensionsExample.java) for a runnable demonstration of `getMaybe` with practical scenarios.
~~~

---

## Built-in Helper Getters

Higher-Kinded-J provides several utility Getters:

### **`identity()`**: Returns the Source Itself

```java
Getter<String, String> id = Getter.identity();
String result = id.get("Hello");
// Result: "Hello"
```

Useful as a base case in composition or for type adaptation.

### **`constant(value)`**: Always Returns the Same Value

```java
Getter<String, Integer> always42 = Getter.constant(42);
int result = always42.get("anything");
// Result: 42
```

Useful for providing default values in pipelines.

### **`first()`** and **`second()`**: Pair Element Extractors

```java
Map.Entry<Person, Address> pair = new AbstractMap.SimpleEntry<>(ceo, hqAddress);

Getter<Map.Entry<Person, Address>, Person> firstGetter = Getter.first();
Getter<Map.Entry<Person, Address>, Address> secondGetter = Getter.second();

Person person = firstGetter.get(pair);
// Result: the CEO Person

Address address = secondGetter.get(pair);
// Result: the headquarters Address
```

---

## When to Use Getter vs Other Approaches

### Use Getter When:

* You need **computed or derived values** without storing them
* You want **composable extraction** pipelines
* You're building **reporting or analytics** features
* You need **type-safe accessors** that compose with other optics
* You want **clear read-only intent** in your code

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

### Use Lens When:

* You need **both reading and writing**
* You're working with **mutable state** (functionally)

```java
// Use Lens when you need to modify
Lens<Person, String> firstName = Lens.of(
    Person::firstName,
    (p, name) -> new Person(name, p.lastName(), p.age(), p.address()));

Person updated = firstName.set("Janet", person);
```

### Use Fold When:

* You're querying **zero or more elements**
* You need to **aggregate or search** collections

```java
// Use Fold for collections
Fold<Order, Product> itemsFold = Fold.of(Order::items);
List<Product> all = itemsFold.getAll(order);
```

### Use Direct Field Access When:

* You need **maximum performance** with no abstraction overhead
* You're not composing with other optics

```java
// Direct access when composition isn't needed
String name = person.firstName();
```

---

## Real-World Use Cases

### Data Transformation Pipelines

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

```java
// Wrong: Getter can't modify
Getter<Person, String> nameGetter = Getter.of(Person::firstName);
// nameGetter.set("Jane", person); // Compilation error - no set method!
```

### Use Lens When Modification Is Required

```java
// Correct: Use Lens for read-write access
Lens<Person, String> nameLens = Lens.of(Person::firstName, (p, n) ->
    new Person(n, p.lastName(), p.age(), p.address()));

Person updated = nameLens.set("Jane", person);
```

### Don't Overlook Null Safety

```java
// Risky: Getter doesn't handle null values specially
Getter<NullableRecord, String> getter = Getter.of(NullableRecord::value);
String result = getter.get(new NullableRecord(null)); // Returns null
```

### Handle Nulls Explicitly

```java
// Safe: Handle nulls in the getter function
Getter<NullableRecord, String> safeGetter = Getter.of(r ->
    r.value() != null ? r.value() : "default");
```

---

## Performance Considerations

Getters are **extremely lightweight**:

* **Zero overhead**: Just a function wrapper
* **No reflection**: Direct method references
* **Inline-friendly**: JIT can optimise away the abstraction
* **Lazy evaluation**: Values computed only when `get()` is called

**Best Practice**: Use Getters freely; they add minimal runtime cost whilst providing excellent composability and type safety.

```java
// Efficient: Computed on demand
Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());

// No storage overhead, computed each time get() is called
String name1 = fullName.get(person1);
String name2 = fullName.get(person2);
```

---

## Complete, Runnable Example

```java
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.hkt.Monoid;
import java.util.*;
import java.util.function.Function;

public class GetterExample {

    public record Person(String firstName, String lastName, int age, Address address) {}
    public record Address(String street, String city, String zipCode, String country) {}
    public record Company(String name, Person ceo, List<Person> employees, Address headquarters) {}

    public static void main(String[] args) {
        // Create sample data
        Address ceoAddress = new Address("123 Executive Blvd", "London", "EC1A", "UK");
        Person ceo = new Person("Jane", "Smith", 45, ceoAddress);

        List<Person> employees = List.of(
            new Person("John", "Doe", 30, new Address("456 Oak St", "Manchester", "M1", "UK")),
            new Person("Alice", "Johnson", 28, new Address("789 Elm Ave", "Birmingham", "B1", "UK")),
            new Person("Bob", "Williams", 35, new Address("321 Pine Rd", "Leeds", "LS1", "UK"))
        );

        Address hqAddress = new Address("1000 Corporate Way", "London", "EC2A", "UK");
        Company company = new Company("TechCorp", ceo, employees, hqAddress);

        // === Basic Getters ===
        Getter<Person, String> fullName = Getter.of(p -> p.firstName() + " " + p.lastName());
        Getter<Person, Integer> age = Getter.of(Person::age);

        System.out.println("CEO: " + fullName.get(ceo));
        System.out.println("CEO Age: " + age.get(ceo));

        // === Computed Values ===
        Getter<Person, String> initials = Getter.of(p ->
            p.firstName().charAt(0) + "." + p.lastName().charAt(0) + ".");
        Getter<Person, String> email = Getter.of(p ->
            p.firstName().toLowerCase() + "." + p.lastName().toLowerCase() + "@techcorp.com");

        System.out.println("CEO Initials: " + initials.get(ceo));
        System.out.println("CEO Email: " + email.get(ceo));

        // === Composition ===
        Getter<Person, Address> addressGetter = Getter.of(Person::address);
        Getter<Address, String> cityGetter = Getter.of(Address::city);
        Getter<Company, Person> ceoGetter = Getter.of(Company::ceo);

        Getter<Person, String> personCity = addressGetter.andThen(cityGetter);
        Getter<Company, String> companyCeoCity = ceoGetter.andThen(personCity);

        System.out.println("CEO City: " + personCity.get(ceo));
        System.out.println("Company CEO City: " + companyCeoCity.get(company));

        // === Getter as Fold ===
        Optional<Integer> ceoAge = age.preview(ceo);
        boolean isExperienced = age.exists(a -> a > 40, ceo);
        int ageCount = age.length(ceo); // Always 1 for Getter

        System.out.println("CEO Age (Optional): " + ceoAge);
        System.out.println("CEO is Experienced: " + isExperienced);
        System.out.println("Age Count: " + ageCount);

        // === Employee Analysis ===
        Fold<List<Person>, Person> listFold = Fold.of(list -> list);

        List<String> employeeNames = listFold.andThen(fullName.asFold()).getAll(employees);
        System.out.println("Employee Names: " + employeeNames);

        List<String> employeeEmails = listFold.andThen(email.asFold()).getAll(employees);
        System.out.println("Employee Emails: " + employeeEmails);

        // Calculate average age
        int totalAge = listFold.andThen(age.asFold())
            .foldMap(sumMonoid(), Function.identity(), employees);
        double avgAge = (double) totalAge / employees.size();
        System.out.println("Average Employee Age: " + String.format("%.1f", avgAge));

        // Check if all from UK
        Getter<Address, String> countryGetter = Getter.of(Address::country);
        boolean allUK = listFold.andThen(addressGetter.asFold())
            .andThen(countryGetter.asFold())
            .all(c -> c.equals("UK"), employees);
        System.out.println("All Employees from UK: " + allUK);
    }

    private static Monoid<Integer> sumMonoid() {
        return new Monoid<>() {
            @Override public Integer empty() { return 0; }
            @Override public Integer combine(Integer a, Integer b) { return a + b; }
        };
    }
}
```

**Expected Output:**

```
CEO: Jane Smith
CEO Age: 45
CEO Initials: J.S.
CEO Email: jane.smith@techcorp.com
CEO City: London
Company CEO City: London
CEO Age (Optional): Optional[45]
CEO is Experienced: true
Age Count: 1
Employee Names: [John Doe, Alice Johnson, Bob Williams]
Employee Emails: [john.doe@techcorp.com, alice.johnson@techcorp.com, bob.williams@techcorp.com]
Average Employee Age: 31.0
All Employees from UK: true
```

---

## Why Getters Are Important

`Getter` completes the read-only optics family by providing:

* **Single-element focus**: Guarantees exactly one value (unlike Fold's zero-or-more)
* **Composability**: Chains beautifully with other optics
* **Computed values**: Derive data without storage overhead
* **Clear intent**: Explicitly read-only, preventing accidental modifications
* **Type safety**: Compile-time guarantees on extraction paths
* **Fold inheritance**: Leverages query operations (exists, all, find) for single values

By adding `Getter` to your optics toolkit alongside `Lens`, `Prism`, `Iso`, `Traversal`, and `Fold`, you have precise control over read-only access patterns. Use `Getter` when you need composable value extraction, `Fold` when you query collections, and `Lens` when you need both reading and writing.

The key insight: **Getters make pure functions first-class composable citizens**, allowing you to build sophisticated data extraction pipelines with clarity and type safety.

---

**Previous:** [Indexed Optics: Position-Aware Operations](indexed_optics.md)
**Next:** [Setters: Composable Write-Only Modifications](setters.md)

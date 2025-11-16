# Profunctor Optics: Advanced Data Transformation

## *Adapting Optics to Different Data Types*


~~~admonish info title="What You'll Learn"
- How to adapt existing optics to work with different data types
- Using `contramap` to change source types and `map` to change target types
- Combining both adaptations with `dimap` for complete format conversion
- Creating reusable adapter patterns for API integration
- Working with type-safe wrapper classes and legacy system integration
- When to use profunctor adaptations vs creating new optics from scratch
~~~

~~~admonish title="Example Code"
[OpticProfunctorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/profunctor/OpticProfunctorExample.java)
~~~

In the previous optics guides, we explored how to work with data structures directly using `Lens`, `Prism`, `Iso`, and `Traversal`. But what happens when you need to use an optic designed for one data type with a completely different data structure? What if you want to adapt an existing optic to work with new input or output formats?

This is where the **profunctor** nature of optics becomes invaluable. Every optic in higher-kinded-j is fundamentally a profunctor, which means it can be adapted to work with different source and target types using powerful transformation operations.

---

## The Challenge: Type Mismatch in Real Systems

In real-world applications, you frequently encounter situations where:

* **Legacy Integration**: You have optics designed for old data structures but need to work with new ones
* **API Adaptation**: External APIs use different field names or data formats than your internal models
* **Type Safety**: You want to work with strongly-typed wrapper classes but reuse optics designed for raw values
* **Data Migration**: You're transitioning between data formats and need optics that work with both

Consider this scenario: you have a well-tested `Lens` that operates on a `Person` record, but you need to use it with an `Employee` record that contains a `Person` as a nested field. Rather than rewriting the lens, you can **adapt** it.

## Think of Profunctor Adaptations Like...

* **Universal adapters**: Like electrical plug adapters that make devices work in different countries
* **Translation layers**: Converting between different "languages" of data representation
* **Lens filters**: Modifying what the optic sees (input) and what it produces (output)
* **Pipeline adapters**: Connecting optics that weren't originally designed to work together

## The Three Profunctor Operations

Every optic provides three powerful adaptation methods that mirror the core profunctor operations:

### 1. **`contramap`**: Adapting the Source Type

The `contramap` operation allows you to adapt an optic to work with a different source type by providing a function that converts from the new source to the original source.

**Use Case**: You have a `Lens<Person, String>` for getting a person's first name, but you want to use it with `Employee` objects.

```java
// Original lens: Person -> String (first name)
Lens<Person, String> firstNameLens = PersonLenses.firstName();

// Adapt it to work with Employee by providing the conversion
Lens<Employee, String> employeeFirstNameLens = 
    firstNameLens.contramap(employee -> employee.personalInfo());

// Now you can use the adapted lens directly on Employee objects
Employee employee = new Employee(123, new Person("Alice", "Johnson", ...), "Engineering");
String firstName = employeeFirstNameLens.get(employee); // "Alice"
```

### 2. **`map`**: Adapting the Target Type

The `map` operation adapts an optic to work with a different target type by providing a function that converts from the original target to the new target.

**Use Case**: You have a `Lens<Person, LocalDate>` for birth dates, but you want to work with formatted strings instead.

```java
// Original lens: Person -> LocalDate
Lens<Person, LocalDate> birthDateLens = PersonLenses.birthDate();

// Adapt it to work with formatted strings
Lens<Person, String> birthDateStringLens = 
    birthDateLens.map(date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE));

// The adapted lens now returns strings
Person person = new Person("Bob", "Smith", LocalDate.of(1985, 12, 25), ...);
String dateString = birthDateStringLens.get(person); // "1985-12-25"
```

### 3. **`dimap`**: Adapting Both Source and Target Types

The `dimap` operation is the most powerful—it adapts both the source and target types simultaneously. This is perfect for converting between completely different data representations.

**Use Case**: You have optics designed for internal `Person` objects but need to work with external `PersonDto` objects that use different field structures.

```java
// Original traversal: Person -> String (hobbies)
Traversal<Person, String> hobbiesTraversal = PersonTraversals.hobbies();

// Adapt it to work with PersonDto (different source) and call them "interests" (different context)
Traversal<PersonDto, String> interestsTraversal = 
    hobbiesTraversal.dimap(
        // Convert PersonDto to Person
        dto -> new Person(
            dto.fullName().split(" ")[0],
            dto.fullName().split(" ")[1], 
            LocalDate.parse(dto.birthDateString()),
            dto.interests()
        ),
        // Convert Person back to PersonDto  
        person -> new PersonDto(
            person.firstName() + " " + person.lastName(),
            person.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
            person.hobbies()
        )
    );
```

---

## Decision Guide: When to Use Each Operation

### Use `contramap` When:

* **Different source type, same target** - Existing optic works perfectly, just need different input
* **Extracting nested data** - Your new type contains the old type as a field
* **Wrapper type handling** - Working with strongly-typed wrappers around base types

java

```java
// Perfect for extracting nested data
Lens<Order, String> customerNameLens = 
    OrderLenses.customer().contramap(invoice -> invoice.order());
```

### Use `map` When:

* **Same source, different target format** - You want to transform the output
* **Data presentation** - Converting raw data to display formats
* **Type strengthening** - Wrapping raw values in type-safe containers

java

```java
// Perfect for presentation formatting
Lens<Product, String> formattedPriceLens = 
    ProductLenses.price().map(price -> "£" + price.setScale(2));
```

### Use `dimap` When:

* **Complete format conversion** - Both input and output need transformation
* **API integration** - External systems use completely different data structures
* **Legacy system support** - Bridging between old and new data formats
* **Data migration** - Supporting multiple data representations simultaneously

java

```java
// Perfect for API integration
Traversal<ApiUserDto, String> apiRolesTraversal = 
    UserTraversals.roles().dimap(
        dto -> convertApiDtoToUser(dto),
        userLogin -> convertUserToApiDto(userLogin)
    );
```

---

## Common Pitfalls

### ❌ Don't Do This:


```java
// Creating adapters inline repeatedly
var lens1 = PersonLenses.firstName().contramap(emp -> emp.person());
var lens2 = PersonLenses.firstName().contramap(emp -> emp.person());
var lens3 = PersonLenses.firstName().contramap(emp -> emp.person());

// Over-adapting simple cases
Lens<Person, String> nameUpper = PersonLenses.firstName()
    .map(String::toUpperCase)
    .map(s -> s.trim())
    .map(s -> s.replace(" ", "_")); // Just write one function!

// Forgetting null safety in conversions
Lens<EmployeeDto, String> unsafeLens = PersonLenses.firstName()
    .contramap(dto -> dto.person()); // What if dto.person() is null?

// Complex conversions without error handling
Traversal<String, LocalDate> fragileParser = 
    Iso.of(LocalDate::toString, LocalDate::parse).asTraversal()
    .contramap(complexString -> extractDatePart(complexString)); // Might throw!
```

### ✅ Do This Instead:

```java
// Create adapters once, reuse everywhere
public static final Lens<Employee, String> EMPLOYEE_FIRST_NAME = 
    PersonLenses.firstName().contramap(Employee::personalInfo);

// Combine transformations efficiently
Function<String, String> normalise = name -> 
    name.toUpperCase().trim().replace(" ", "_");
Lens<Person, String> normalisedNameLens = PersonLenses.firstName().map(normalise);

// Handle null safety explicitly
Lens<EmployeeDto, Optional<String>> safeNameLens = PersonLenses.firstName()
    .contramap((EmployeeDto dto) -> Optional.ofNullable(dto.person()))
    .map(Optional::of);

// Use safe conversions with proper error handling
Function<String, Either<String, LocalDate>> safeParse = str -> {
    try {
        return Either.right(LocalDate.parse(extractDatePart(str)));
    } catch (Exception e) {
        return Either.left("Invalid date: " + str);
    }
};
```
---

## Performance Notes

Profunctor adaptations are designed for efficiency:

* **Automatic fusion**: Multiple `contramap` or `map` operations are automatically combined
* **Lazy evaluation**: Conversions only happen when the optic is actually used
* **No boxing overhead**: Simple transformations are inlined by the JVM
* **Reusable adapters**: Create once, use many times without additional overhead

**Best Practice**: Create adapted optics as constants and reuse them:


```java
public class OpticAdapters {
    // Create once, use everywhere
    public static final Lens<Employee, String> FIRST_NAME = 
        PersonLenses.firstName().contramap(Employee::personalInfo);
  
    public static final Lens<Employee, String> FORMATTED_BIRTH_DATE = 
        PersonLenses.birthDate()
            .contramap(Employee::personalInfo)
            .map(date -> date.format(DateTimeFormatter.DD_MM_YYYY));
      
    public static final Traversal<CompanyDto, String> EMPLOYEE_EMAILS = 
        CompanyTraversals.employees()
            .contramap((CompanyDto dto) -> convertDtoToCompany(dto))
            .andThen(EmployeeTraversals.contacts())
            .andThen(ContactLenses.email().asTraversal());
}
```

---

## Real-World Example: API Integration

Let's explore a comprehensive example where you need to integrate with an external API that uses different field names and data structures than your internal models.

**The Scenario**: Your internal system uses `Employee` records, but the external API expects `EmployeeDto` objects with different field names:

```java
// Internal model
@GenerateLenses
@GenerateTraversals
public record Employee(int id, Person personalInfo, String department) {}

@GenerateLenses
@GenerateTraversals  
public record Person(String firstName, String lastName, LocalDate birthDate, List<String> skills) {}

// External API model  
@GenerateLenses
public record EmployeeDto(int employeeId, PersonDto person, String dept) {}

@GenerateLenses
public record PersonDto(String fullName, String birthDateString, List<String> expertise) {}
```

**The Solution**: Create an adapter that converts between these formats while reusing your existing optics:

```java
public class ApiIntegration {
  
    // Conversion utilities
    private static Employee dtoToEmployee(EmployeeDto dto) {
        PersonDto personDto = dto.person();
        String[] nameParts = personDto.fullName().split(" ", 2);
        Person person = new Person(
            nameParts[0],
            nameParts.length > 1 ? nameParts[1] : "",
            LocalDate.parse(personDto.birthDateString()),
            personDto.expertise()
        );
        return new Employee(dto.employeeId(), person, dto.dept());
    }
  
    private static EmployeeDto employeeToDto(Employee employee) {
        Person person = employee.personalInfo();
        PersonDto personDto = new PersonDto(
            person.firstName() + " " + person.lastName(),
            person.birthDate().toString(),
            person.skills()
        );
        return new EmployeeDto(employee.id(), personDto, employee.department());
    }
  
    // Adapted optics for API integration
    public static final Lens<EmployeeDto, String> API_EMPLOYEE_DEPARTMENT = 
        EmployeeLenses.department().dimap(
            ApiIntegration::dtoToEmployee,
            ApiIntegration::employeeToDto
        );
  
    public static final Lens<EmployeeDto, String> API_EMPLOYEE_FIRST_NAME = 
        EmployeeLenses.personalInfo()
            .andThen(PersonLenses.firstName())
            .dimap(
                ApiIntegration::dtoToEmployee,
                ApiIntegration::employeeToDto
            );
  
    public static final Traversal<EmployeeDto, String> API_EMPLOYEE_SKILLS = 
        EmployeeTraversals.personalInfo()
            .andThen(PersonTraversals.skills())
            .dimap(
                ApiIntegration::dtoToEmployee,
                ApiIntegration::employeeToDto
            );
  
    // Use the adapters seamlessly with external data
    public void processApiData(EmployeeDto externalEmployee) {
        // Update department using existing business logic
        EmployeeDto promoted = API_EMPLOYEE_DEPARTMENT.modify(
            dept -> "Senior " + dept, 
            externalEmployee
        );
    
        // Normalise skills using existing traversal logic
        EmployeeDto normalisedSkills = Traversals.modify(
            API_EMPLOYEE_SKILLS,
            skill -> skill.toLowerCase().trim(),
            externalEmployee
        );
    
        sendToApi(promoted);
        sendToApi(normalisedSkills);
    }
}
```

---

## Working with Type-Safe Wrappers

Another powerful use case is adapting optics to work with strongly-typed wrapper classes while maintaining type safety.

**The Challenge**: You want to use string manipulation functions on wrapper types:


```java
// Strongly-typed wrappers
public record UserId(String value) {}
public record UserName(String value) {}
public record Email(String value) {}

@GenerateLenses
public record User(UserId id, UserName name, Email email, LocalDate createdAt) {}
```

**The Solution**: Create adapted lenses that unwrap and rewrap values:


```java
public class WrapperAdapters {
  
    // Generic wrapper lens creator
    public static <W> Lens<W, String> stringWrapperLens(
        Function<W, String> unwrap,
        Function<String, W> wrap
    ) {
        return Lens.of(unwrap, (wrapper, newValue) -> wrap.apply(newValue));
    }
  
    // Specific wrapper lenses
    public static final Lens<UserId, String> USER_ID_STRING = 
        stringWrapperLens(UserId::value, UserId::new);
      
    public static final Lens<UserName, String> USER_NAME_STRING = 
        stringWrapperLens(UserName::value, UserName::new);
      
    public static final Lens<Email, String> EMAIL_STRING = 
        stringWrapperLens(Email::value, Email::new);
  
    // Composed lenses for User operations
    public static final Lens<User, String> USER_NAME_VALUE = 
        UserLenses.name().andThen(USER_NAME_STRING);
      
    public static final Lens<User, String> USER_EMAIL_VALUE = 
        UserLenses.email().andThen(EMAIL_STRING);
  
    // Usage examples
    public User normaliseUser(User userLogin) {
        return USER_NAME_VALUE.modify(name -> 
            Arrays.stream(name.toLowerCase().split(" "))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(joining(" ")),
            userLogin
        );
    }
  
    public User updateEmailDomain(User userLogin, String newDomain) {
        return USER_EMAIL_VALUE.modify(email -> {
            String localPart = email.substring(0, email.indexOf('@'));
            return localPart + "@" + newDomain;
        }, userLogin);
    }
}
```

---

## Migration Patterns

Profunctor adaptations are particularly valuable during system migrations:

### Legacy System Integration


```java
// You have optics for PersonV1, but data is now PersonV2
public record PersonV1(String name, int age) {}

@GenerateLenses
public record PersonV2(String firstName, String lastName, LocalDate birthDate) {}

public class MigrationAdapters {
  
    // Convert between versions
    private static PersonV1 v2ToV1(PersonV2 v2) {
        return new PersonV1(
            v2.firstName() + " " + v2.lastName(),
            Period.between(v2.birthDate(), LocalDate.now()).getYears()
        );
    }
  
    private static PersonV2 v1ToV2(PersonV1 v1) {
        String[] nameParts = v1.name().split(" ", 2);
        return new PersonV2(
            nameParts[0],
            nameParts.length > 1 ? nameParts[1] : "",
            LocalDate.now().minusYears(v1.age())
        );
    }
  
    // Existing V1 optics work with V2 data
    public static final Lens<PersonV2, String> V2_NAME_FROM_V1_LENS = 
        // Assume we have a V1 name lens
        Lens.of(PersonV1::name, (p1, name) -> new PersonV1(name, p1.age()))
            .dimap(MigrationAdapters::v2ToV1, MigrationAdapters::v1ToV2);
}
```

### Database Schema Evolution


```java
// Old database entity
public record CustomerEntityV1(Long id, String name, String email) {}

// New database entity  
@GenerateLenses
public record CustomerEntityV2(Long id, String firstName, String lastName, String emailAddress, boolean active) {}

public class SchemaAdapters {
  
    // Adapter for name field
    public static final Lens<CustomerEntityV2, String> FULL_NAME_ADAPTER = 
        Lens.of(CustomerEntityV1::name, (v1, name) -> new CustomerEntityV1(v1.id(), name, v1.email()))
            .dimap(
                // V2 -> V1 conversion
                v2 -> new CustomerEntityV1(v2.id(), v2.firstName() + " " + v2.lastName(), v2.emailAddress()),
                // V1 -> V2 conversion  
                v1 -> {
                    String[] parts = v1.name().split(" ", 2);
                    return new CustomerEntityV2(
                        v1.id(),
                        parts[0],
                        parts.length > 1 ? parts[1] : "",
                        v1.email(),
                        true // Default active status
                    );
                }
            );
}
```

---

## Complete, Runnable Example

This comprehensive example demonstrates all three profunctor operations in a realistic scenario:


```java
package org.higherkindedj.example.optics.profunctor;

import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public class OpticProfunctorExample {

    // Internal data model
    @GenerateLenses
    @GenerateTraversals
    public record Person(String firstName, String lastName, LocalDate birthDate, List<String> hobbies) {}
  
    @GenerateLenses
    public record Employee(int id, Person personalInfo, String department) {}
  
    // External API model
    @GenerateLenses
    public record PersonDto(String fullName, String birthDateString, List<String> interests) {}
  
    @GenerateLenses  
    public record EmployeeDto(int employeeId, PersonDto person, String dept) {}
  
    // Type-safe wrapper
    public record UserId(long value) {}
  
    @GenerateLenses
    public record UserProfile(UserId id, String displayName, boolean active) {}

    public static void main(String[] args) {
        System.out.println("=== PROFUNCTOR OPTICS EXAMPLE ===");
      
        // Test data
        var person = new Person("Alice", "Johnson", 
            LocalDate.of(1985, 6, 15), 
            List.of("reading", "cycling", "photography"));
        var employee = new Employee(123, person, "Engineering");
      
        // --- SCENARIO 1: contramap - Adapt source type ---
        System.out.println("--- Scenario 1: contramap (Source Adaptation) ---");
      
        // Original lens works on Person, adapt it for Employee
        Lens<Person, String> firstNameLens = PersonLenses.firstName();
        Lens<Employee, String> employeeFirstNameLens = 
            firstNameLens.contramap(Employee::personalInfo);
      
        String name = employeeFirstNameLens.get(employee);
        Employee renamedEmployee = employeeFirstNameLens.set("Alicia", employee);
      
        System.out.println("Original employee: " + employee);
        System.out.println("Extracted name: " + name);
        System.out.println("Renamed employee: " + renamedEmployee);
        System.out.println();
      
        // --- SCENARIO 2: map - Adapt target type ---
        System.out.println("--- Scenario 2: map (Target Adaptation) ---");
      
        // Original lens returns LocalDate, adapt it to return formatted string
        Lens<Person, LocalDate> birthDateLens = PersonLenses.birthDate();
        Lens<Person, String> birthDateStringLens = 
            birthDateLens.map(date -> date.format(DateTimeFormatter.ISO_LOCAL_DATE));
      
        String formattedDate = birthDateStringLens.get(person);
        // Note: set operation would need to parse the string back to LocalDate
        System.out.println("Person: " + person);
        System.out.println("Formatted birth date: " + formattedDate);
        System.out.println();
      
        // --- SCENARIO 3: dimap - Adapt both source and target ---
        System.out.println("--- Scenario 3: dimap (Both Source and Target Adaptation) ---");
      
        // Convert between internal Person and external PersonDto
        Traversal<Person, String> hobbiesTraversal = PersonTraversals.hobbies();
        Traversal<PersonDto, String> interestsTraversal = hobbiesTraversal.dimap(
            // PersonDto -> Person
            dto -> {
                String[] nameParts = dto.fullName().split(" ", 2);
                return new Person(
                    nameParts[0],
                    nameParts.length > 1 ? nameParts[1] : "",
                    LocalDate.parse(dto.birthDateString()),
                    dto.interests()
                );
            },
            // Person -> PersonDto  
            p -> new PersonDto(
                p.firstName() + " " + p.lastName(),
                p.birthDate().toString(),
                p.hobbies()
            )
        );
      
        var personDto = new PersonDto("Bob Smith", "1990-03-20", 
            List.of("gaming", "cooking", "travel"));
      
        List<String> extractedInterests = Traversals.getAll(interestsTraversal, personDto);
        PersonDto updatedDto = Traversals.modify(interestsTraversal, 
            interest -> interest.toUpperCase(), personDto);
      
        System.out.println("Original DTO: " + personDto);
        System.out.println("Extracted interests: " + extractedInterests);
        System.out.println("Updated DTO: " + updatedDto);
        System.out.println();
      
        // --- SCENARIO 4: Working with wrapper types ---
        System.out.println("--- Scenario 4: Wrapper Type Integration ---");
      
        // Create a lens that works directly with the wrapped value
        Lens<UserId, Long> userIdValueLens = Lens.of(UserId::value, (id, newValue) -> new UserId(newValue));
        Lens<UserProfile, Long> profileIdValueLens = 
            UserProfileLenses.id().andThen(userIdValueLens);
      
        var userProfile = new UserProfile(new UserId(456L), "Alice J.", true);
      
        Long idValue = profileIdValueLens.get(userProfile);
        UserProfile updatedProfile = profileIdValueLens.modify(id -> id + 1000, userProfile);
      
        System.out.println("Original profile: " + userProfile);
        System.out.println("Extracted ID value: " + idValue);
        System.out.println("Updated profile: " + updatedProfile);
        System.out.println();
      
        // --- SCENARIO 5: Chaining adaptations ---
        System.out.println("--- Scenario 5: Chaining Adaptations ---");
      
        // Chain multiple adaptations: Employee -> Person -> String (formatted)
        Lens<Employee, String> formattedEmployeeName = 
            PersonLenses.firstName()
                .contramap(Employee::personalInfo)  // Employee -> Person
                .map(name -> "Mr/Ms. " + name.toUpperCase()); // String -> Formatted String
      
        String formalName = formattedEmployeeName.get(employee);
        Employee formalEmployee = formattedEmployeeName.set("Mr/Ms. ROBERT", employee);
      
        System.out.println("Original employee: " + employee);
        System.out.println("Formal name: " + formalName);
        System.out.println("Employee with formal name: " + formalEmployee);
        System.out.println();
      
        // --- SCENARIO 6: Safe adaptations with Optional ---
        System.out.println("--- Scenario 6: Safe Adaptations ---");
      
        // Handle potentially null fields safely
        Lens<Optional<Person>, Optional<String>> safeNameLens = 
            PersonLenses.firstName()
                .map(Optional::of)
                .contramap(optPerson -> optPerson.orElse(new Person("", "", LocalDate.now(), List.of())));
      
        Optional<Person> maybePerson = Optional.of(person);
        Optional<Person> emptyPerson = Optional.empty();
      
        Optional<String> safeName1 = safeNameLens.get(maybePerson);
        Optional<String> safeName2 = safeNameLens.get(emptyPerson);
      
        System.out.println("Safe name from present person: " + safeName1);
        System.out.println("Safe name from empty person: " + safeName2);
    }
}
```

**Expected Output:**

```
=== PROFUNCTOR OPTICS EXAMPLE ===
--- Scenario 1: contramap (Source Adaptation) ---
Original employee: Employee[id=123, personalInfo=Person[firstName=Alice, lastName=Johnson, birthDate=1985-06-15, hobbies=[reading, cycling, photography]], department=Engineering]
Extracted name: Alice
Renamed employee: Employee[id=123, personalInfo=Person[firstName=Alicia, lastName=Johnson, birthDate=1985-06-15, hobbies=[reading, cycling, photography]], department=Engineering]

--- Scenario 2: map (Target Adaptation) ---
Person: Person[firstName=Alice, lastName=Johnson, birthDate=1985-06-15, hobbies=[reading, cycling, photography]]
Formatted birth date: 1985-06-15

--- Scenario 3: dimap (Both Source and Target Adaptation) ---
Original DTO: PersonDto[fullName=Bob Smith, birthDateString=1990-03-20, interests=[gaming, cooking, travel]]
Extracted interests: [gaming, cooking, travel]
Updated DTO: PersonDto[fullName=Bob Smith, birthDateString=1990-03-20, interests=[GAMING, COOKING, TRAVEL]]

--- Scenario 4: Wrapper Type Integration ---
Original profile: UserProfile[id=UserId[value=456], displayName=Alice J., active=true]
Extracted ID value: 456
Updated profile: UserProfile[id=UserId[value=1456], displayName=Alice J., active=true]

--- Scenario 5: Chaining Adaptations ---
Original employee: Employee[id=123, personalInfo=Person[firstName=Alice, lastName=Johnson, birthDate=1985-06-15, hobbies=[reading, cycling, photography]], department=Engineering]
Formal name: Mr/Ms. ALICE
Employee with formal name: Employee[id=123, personalInfo=Person[firstName=ROBERT, lastName=Johnson, birthDate=1985-06-15, hobbies=[reading, cycling, photography]], department=Engineering]

--- Scenario 6: Safe Adaptations ---
Safe name from present person: Optional[Alice]
Safe name from empty person: Optional[]
```

---

## Integration with Existing Optics

Profunctor adaptations work seamlessly with all the optic types and features you've already learned:

### With Effectful Updates

```java
// Original effectful lens
Lens<Person, String> emailLens = PersonLenses.email();

// Adapt it for Employee and use with validation
Lens<Employee, String> employeeEmailLens = emailLens.contramap(Employee::personalInfo);

// Use with effectful validation as normal
Kind<ValidatedKind.Witness<String>, Employee> result = 
    employeeEmailLens.modifyF(this::validateEmail, employee, validatedApplicative);
```

### With Deep Composition


```java
// Compose adapted optics just like regular optics
Traversal<EmployeeDto, String> deepPath = 
    apiAdapter.asTraversal()
        .andThen(PersonTraversals.hobbies())
        .andThen(stringProcessor);
```

This profunctor capability makes higher-kinded-j optics incredibly flexible and reusable, allowing you to adapt existing, well-tested optics to work with new data formats and requirements without rewriting your core business logic.

---

**Previous:**[Folds: Querying Immutable Data](folds.md)
**Next:**[Capstone Example: Deep Validation](composing_optics.md)


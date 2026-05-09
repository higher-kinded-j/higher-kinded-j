# Profunctor Optics: Recipes

## _Wrapper adapters, migration recipes, and a complete worked example_

~~~admonish info title="What You'll Learn"
- How to build reusable wrapper-type lenses for strongly-typed value classes (`UserId`, `Email`, etc.).
- Migration patterns: bridging V1 and V2 record schemas with `dimap`, and database-schema-evolution helpers.
- A single end-to-end runnable example exercising `contramap`, `map`, and `dimap` together.
- How profunctor adaptations interact with effectful updates and deep optic composition.
~~~

This page is a recipe shelf for the production-shaped problems that profunctor adaptations solve. The conceptual material lives in [Profunctor Optics](profunctor_optics.md); use this page when you need a copy-paste recipe for a wrapper, a migration adapter, or want to see all three operations interact in a single program.

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

**Previous:** [Profunctor Optics](profunctor_optics.md)
**Next:** [Polymorphic Optics](polymorphic_optics.md)

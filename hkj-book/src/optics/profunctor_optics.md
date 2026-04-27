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

The `dimap` operation is the most powerful: it adapts both the source and target types simultaneously. This is perfect for converting between completely different data representations.

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

### Don't Do This:


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

### Do This Instead:

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

~~~admonish tip title="See Also"
- [Profunctor Optics: Recipes](profunctor_optics_recipes.md), wrapper-type recipes, V1/V2 migration adapters, and a complete runnable example.
~~~

---

**Previous:** [Advanced Prism Patterns: Recipes](advanced_prism_patterns_recipes.md)
**Next:** [Profunctor Optics: Recipes](profunctor_optics_recipes.md)

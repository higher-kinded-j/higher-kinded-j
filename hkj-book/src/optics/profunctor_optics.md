# Profunctor Optics: Advanced Data Transformation

## *Adapting Optics to Different Data Types*


```admonish
[OpticProfunctorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/profunctor/OpticProfunctorExample.java)
```

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

---

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

## Real-World Example: API Integration

Let's explore a comprehensive example where you need to integrate with an external API that uses different field names and data structures than your internal models.

**The Scenario**: Your internal system uses `Employee` records, but the external API expects `EmployeeDto` objects with different field names:

```java
// Internal model
@GenerateLenses
public record Employee(int id, Person personalInfo, String department) {}

// External API model  
@GenerateLenses
public record EmployeeDto(int employeeId, PersonDto person, String dept) {}
```

**The Solution**: Create an adapter that converts between these formats while reusing your existing optics:

```java
// Your existing, well-tested lens
Lens<Employee, String> departmentLens = EmployeeLenses.department();

// Create an adapter for the external format using dimap
Lens<EmployeeDto, String> apiAdapter = departmentLens.dimap(
    // Convert from API format to internal format
    dto -> new Employee(
        dto.employeeId(),
        convertDtoToPerson(dto.person()),
        dto.dept()
    ),
    // Convert from internal format back to API format
    employee -> new EmployeeDto(
        employee.id(),
        convertPersonToDto(employee.personalInfo()),
        employee.department()
    )
);

// Use the adapter seamlessly with external data
EmployeeDto externalEmployee = fetchFromApi();
EmployeeDto promoted = apiAdapter.modify(dept -> "Senior " + dept, externalEmployee);
sendToApi(promoted);
```

---

## Working with Type-Safe Wrappers

Another powerful use case is adapting optics to work with strongly-typed wrapper classes while maintaining type safety.

**The Challenge**: You want to use string manipulation functions on a `UserName` wrapper type:

```java
public record UserName(String value) {}
public record User(UserId id, UserName name, FormattedDate createdAt) {}
```

**The Solution**: Create an adapted lens that unwraps and rewraps the value:

```java
// Original lens: User -> UserName
Lens<User, UserName> userNameLens = UserLenses.name();

// Adapt it to work directly with strings
Lens<User, String> stringNameLens = userNameLens.dimap(
    Function.identity(), // Keep User as-is for source
    userName -> userName.value(), // Extract string from UserName
    name -> new UserName(name)    // Wrap string back into UserName
);

// Now you can use string operations directly
User user = new User(new UserId(789L), new UserName("john doe"), ...);
User updated = stringNameLens.modify(name -> name.toUpperCase(), user);
// Result: User with UserName("JOHN DOE")
```

---

## Performance and Composition Considerations

### Efficient Composition

Profunctor adaptations compose efficiently. Multiple `contramap` or `map` operations are automatically fused:

```java
// These operations are fused into a single transformation
Lens<A, D> efficientLens = originalLens
    .contramap(a -> b)
    .contramap(b -> c)  // Composed with previous contramap
    .map(x -> y)
    .map(y -> z);       // Composed with previous map
```

### When to Use Each Operation

* **`contramap`**: When you need to use an existing optic with a different source type but the same target type
* **`map`**: When you want to transform the result of an optic without changing how you access the source
* **`dimap`**: When you're bridging between completely different data representations or working with external APIs

### Best Practices

1. **Create Adapters Once**: Define your profunctor adaptations as reusable components rather than inline transformations
2. **Document Conversions**: Clearly document what each adaptation does, especially for `dimap` operations
3. **Test Thoroughly**: Profunctor adaptations change the behaviour of optics, so test both directions of conversion
4. **Consider Performance**: For high-frequency operations, ensure your conversion functions are efficient

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

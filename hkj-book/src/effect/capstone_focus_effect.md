# Capstone: Effects Meet Optics

> *"The greatest value of a picture is when it forces us to notice what we never expected to see."*
>
> — John Tukey, *Exploratory Data Analysis*

~~~admonish info title="What You'll Learn"
- How Effect paths and Focus paths combine in a single, realistic pipeline
- A complete before/after comparison: imperative Java vs HKJ
- When to use `focus()` inside an effect pipeline vs `toEitherPath()` to enter one
- The pattern for navigating optional nested fields with `AffinePath` under effects
~~~

---

## The Scenario

You maintain an internal company directory. The domain model is a set of nested immutable records:

```java
@GenerateLenses @GenerateFocus
record Company(String name, List<Department> departments) {}

@GenerateLenses @GenerateFocus
record Department(String name, Optional<Employee> manager, List<Employee> staff) {}

@GenerateLenses @GenerateFocus
record Employee(String name, ContactInfo contact) {}

@GenerateLenses @GenerateFocus
record ContactInfo(String phone, Optional<String> email) {}
```

The generated Focus classes give you:

- `CompanyFocus.departments()` — `TraversalPath<Company, Department>`
- `DepartmentFocus.manager()` — `AffinePath<Department, Employee>` (manager is optional)
- `DepartmentFocus.name()` — `FocusPath<Department, String>`
- `EmployeeFocus.contact()` — `FocusPath<Employee, ContactInfo>`
- `ContactInfoFocus.email()` — `AffinePath<ContactInfo, String>` (email is optional)
- `ContactInfoFocus.phone()` — `FocusPath<ContactInfo, String>`

**The task:** Given a department name, look up that department's manager and update their email address. The operation must:

1. Find the department by name (it may not exist)
2. Get the manager (the department may have no manager)
3. Validate the new email format
4. Return the updated company, or a typed error explaining what went wrong

---

## The Imperative Approach

Here is the standard Java solution. It works, but notice how the business logic (validate and update an email) drowns in defensive checks:

```java
sealed interface DirectoryError {
    record DepartmentNotFound(String name) implements DirectoryError {}
    record NoManager(String department) implements DirectoryError {}
    record InvalidEmail(String email, String reason) implements DirectoryError {}
}

Either<DirectoryError, Company> updateManagerEmail(
        Company company, String deptName, String newEmail) {

    // 1. Find the department
    Department dept = null;
    for (Department d : company.departments()) {
        if (d.name().equals(deptName)) {
            dept = d;
            break;
        }
    }
    if (dept == null) {
        return Either.left(new DirectoryError.DepartmentNotFound(deptName));
    }

    // 2. Get the manager
    if (dept.manager().isEmpty()) {
        return Either.left(new DirectoryError.NoManager(deptName));
    }
    Employee manager = dept.manager().get();

    // 3. Validate the new email
    if (!newEmail.contains("@") || newEmail.length() < 5) {
        return Either.left(
            new DirectoryError.InvalidEmail(newEmail, "Must contain @ and be at least 5 characters"));
    }

    // 4. Rebuild the entire structure (the painful part)
    ContactInfo updatedContact = new ContactInfo(manager.contact().phone(), Optional.of(newEmail));
    Employee updatedManager = new Employee(manager.name(), updatedContact);
    Department updatedDept = new Department(dept.name(), Optional.of(updatedManager), dept.staff());

    List<Department> updatedDepts = company.departments().stream()
        .map(d -> d.name().equals(deptName) ? updatedDept : d)
        .toList();

    return Either.right(new Company(company.name(), updatedDepts));
}
```

**30 lines.** Five levels of concern tangled together: searching, null-checking, validating, reconstructing, and error-wrapping. The actual business logic ("validate the email, then set it") is two lines buried in the middle.

---

## The HKJ Approach

The same operation, using Effect paths for error handling and Focus paths for structural navigation:

```java
EitherPath<DirectoryError, Company> updateManagerEmail(
        Company company, String deptName, String newEmail) {

    // Optics path for the update: company → matching dept → manager → contact
    TraversalPath<Company, ContactInfo> contactInDept =
        CompanyFocus.departments()
            .filter(d -> d.name().equals(deptName))
            .via(DepartmentFocus.manager())
            .via(EmployeeFocus.contact());

    return findDepartment(company, deptName)                              // EitherPath: find dept
        .focus(DepartmentFocus.manager(),                                 // AffinePath: get manager
               new DirectoryError.NoManager(deptName))
        .focus(EmployeeFocus.contact())                                   // FocusPath: navigate to contact
        .via(contact -> validateEmail(newEmail)                           // EitherPath: validate
            .map(valid -> new ContactInfo(contact.phone(), Optional.of(valid))))
        .map(updatedContact ->                                            // Reconstruct via optics
            contactInDept.setAll(updatedContact, company));
}
```

**Six lines of pipeline.** Each line has one job. The error handling is implicit in the railway; if any step fails, subsequent steps are skipped and the error propagates to the caller.

The helper methods are equally clean:

```java
EitherPath<DirectoryError, Department> findDepartment(Company company, String name) {
    return company.departments().stream()
        .filter(d -> d.name().equals(name))
        .findFirst()
        .map(d -> Path.<DirectoryError, Department>right(d))
        .orElse(Path.left(new DirectoryError.DepartmentNotFound(name)));
}

EitherPath<DirectoryError, String> validateEmail(String email) {
    if (email.contains("@") && email.length() >= 5) {
        return Path.right(email);
    }
    return Path.left(
        new DirectoryError.InvalidEmail(email, "Must contain @ and be at least 5 characters"));
}
```

~~~admonish note title="Alternative: Traversal-Based Lookup"
The `findDepartment` helper uses a plain Java stream to search by name. You could also express
this with a filtered traversal:

```java
EitherPath<DirectoryError, Department> findDepartment(Company company, String name) {
    return CompanyFocus.departments()
        .filter(d -> d.name().equals(name))
        .toMaybePath(company)
        .toEitherPath(new DirectoryError.DepartmentNotFound(name));
}
```

Both approaches produce the same result. The choice comes down to what you are navigating:

| Approach | Favour when |
|----------|-------------|
| **Stream lookup** | The search is value-based (matching on a name, ID, or other runtime data). The stream idiom makes the "find first" intent obvious and is familiar to all Java developers. |
| **Filtered traversal** | The access pattern is structural and reusable. If `departments().filter(...)` is a path you compose with other optics (e.g., further navigating into the matched department's staff list), a traversal keeps the entire chain in the optics domain. |

As a rule of thumb: use optics for *where* the data lives (structure), and streams or effects for *which* data you want (queries).
~~~

---

## What Happened

The railway diagram for this pipeline:

<pre style="line-height:1.5;font-size:0.95em">
    <span style="color:#4CAF50"><b>Success</b> ═══●══════════════●════════════════●══════════●══════════●═══▶  Company</span>
    <span style="color:#4CAF50">       findDept    focus(manager)  focus(contact)  via       map</span>
    <span style="color:#4CAF50">                                                (validate) (setAll)</span>
                         ╲
                          ╲  absent: NoManager
                           ╲
    <span style="color:#F44336"><b>Failure</b> ──●───────────────●────────────────────────────●──────────────▶  DirectoryError</span>
    <span style="color:#F44336">    DeptNotFound    NoManager                   InvalidEmail</span>
</pre>

Let's trace what the pipeline does, step by step:

| Step | Operator | What Happens |
|------|----------|--------------|
| `findDepartment(...)` | Entry point | Returns `Right(dept)` or `Left(DepartmentNotFound)` |
| `.focus(DepartmentFocus.manager(), ...)` | AffinePath focus | Extracts the manager from the department. If `Optional.empty()`, switches to failure track with `NoManager` |
| `.focus(EmployeeFocus.contact())` | FocusPath focus | Extracts the `ContactInfo` from the manager. Always succeeds (the field is required) |
| `.via(contact -> validateEmail(...).map(...))` | Effect chain | Validates the new email. If invalid, switches to failure. If valid, builds an updated `ContactInfo` |
| `.map(updatedContact -> contactInDept.setAll(...))` | Green track transform | Reconstructs the `Company` using the composed optics path |

Three different optic types (`AffinePath`, `FocusPath`, and plain `map`) combine seamlessly within the same effect pipeline. The types guide you: `focus()` with an `AffinePath` requires an error argument; `focus()` with a `FocusPath` does not.

---

## The Two Directions

This capstone uses both bridging directions described in [Focus-Effect Integration](focus_integration.md):

**Effects → Optics** (the `focus()` calls): You are already in an `EitherPath` pipeline and need to navigate into the contained value's structure. The `focus()` method applies an optic to the success track value.

```java
// Already in an effect; drill into the contained Department
eitherPath.focus(DepartmentFocus.manager(), errorIfAbsent)
```

**Optics → Effects** (the entry point): You start with concrete data and need to enter the effect domain. The `findDepartment` helper uses `Path.right(...)` and `Path.left(...)` to lift values into `EitherPath`.

```java
// Start with a raw value; lift into the effect domain
Path.<DirectoryError, Department>right(department)
```

---

## Key Takeaways

~~~admonish info title="Key Takeaways"
* **Effects and optics solve different problems.** Effects handle "what might go wrong" (absence, failure, validation). Optics handle "where is the data" (nested fields, optional values). Together, they cover both dimensions in a single pipeline.
* **`focus()` with `AffinePath` requires an error.** When the optic might not find a value (optional fields, sum type variants), you must supply the error that goes on the failure track. This makes the absent case explicit rather than hidden.
* **`focus()` with `FocusPath` always succeeds.** When the field is guaranteed to exist (a required record component), no error is needed. The type system enforces this distinction.
* **The before/after difference is structural, not cosmetic.** The imperative version mixes searching, checking, validating, and rebuilding in one block. The HKJ version gives each concern its own pipeline step, connected by the railway's automatic error propagation.
~~~

~~~admonish tip title="See Also"
- [Focus-Effect Integration](focus_integration.md) - Complete bridging guide between optics and effects
- [Effect Path Overview](effect_path_overview.md) - The railway model and operator diagrams
- [Focus DSL](../optics/focus_dsl.md) - Full guide to Focus paths and navigation
- [Stack Archetypes](../transformers/archetypes.md) - Named patterns for common effect stacks
~~~

---

**Previous:** [Focus-Effect Integration](focus_integration.md)
**Next:** [Patterns and Recipes](patterns.md)

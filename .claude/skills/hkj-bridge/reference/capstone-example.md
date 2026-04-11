# Capstone: Effects Meet Optics Reference

Complete before/after example showing effects + optics integration.

Source: `hkj-book/src/effect/capstone_focus_effect.md`

---

## The Problem

Update a manager's email in a nested company directory. The operation must:
1. Find department by name (may not exist)
2. Get the manager (department may have no manager)
3. Validate new email format
4. Return updated company or typed error

Domain model -- nested immutable records:

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

Error type:

```java
sealed interface DirectoryError {
  record DepartmentNotFound(String name) implements DirectoryError {}
  record NoManager(String department) implements DirectoryError {}
  record InvalidEmail(String email, String reason) implements DirectoryError {}
}
```

---

## Generated Optics

`@GenerateLenses` and `@GenerateFocus` produce typed paths:

| Path | Type | Always Present? |
|------|------|----------------|
| `CompanyFocus.departments()` | `TraversalPath<Company, Department>` | Yes (list) |
| `DepartmentFocus.manager()` | `AffinePath<Department, Employee>` | No (Optional) |
| `DepartmentFocus.name()` | `FocusPath<Department, String>` | Yes |
| `EmployeeFocus.contact()` | `FocusPath<Employee, ContactInfo>` | Yes |
| `ContactInfoFocus.email()` | `AffinePath<ContactInfo, String>` | No (Optional) |
| `ContactInfoFocus.phone()` | `FocusPath<ContactInfo, String>` | Yes |

Key distinction:
- `FocusPath` = guaranteed field, always succeeds
- `AffinePath` = optional field, may be absent (needs error argument in effect pipeline)
- `TraversalPath` = zero-or-more targets (list traversal)

---

## BEFORE: Imperative Approach (30 lines)

```java
Either<DirectoryError, Company> updateManagerEmail(
    Company company, String deptName, String newEmail) {

  // 1. Find department -- manual loop
  Department dept = null;
  for (Department d : company.departments()) {
    if (d.name().equals(deptName)) { dept = d; break; }
  }
  if (dept == null) {
    return Either.left(new DirectoryError.DepartmentNotFound(deptName));
  }

  // 2. Get manager -- manual Optional check
  if (dept.manager().isEmpty()) {
    return Either.left(new DirectoryError.NoManager(deptName));
  }
  Employee manager = dept.manager().get();

  // 3. Validate email -- manual check
  if (!newEmail.contains("@") || newEmail.length() < 5) {
    return Either.left(
        new DirectoryError.InvalidEmail(newEmail, "Must contain @ and be >= 5 chars"));
  }

  // 4. Rebuild entire structure -- the painful part
  ContactInfo updatedContact = new ContactInfo(manager.contact().phone(), Optional.of(newEmail));
  Employee updatedManager = new Employee(manager.name(), updatedContact);
  Department updatedDept = new Department(dept.name(), Optional.of(updatedManager), dept.staff());
  List<Department> updatedDepts = company.departments().stream()
      .map(d -> d.name().equals(deptName) ? updatedDept : d)
      .toList();

  return Either.right(new Company(company.name(), updatedDepts));
}
```

**Issues**: Five concerns tangled -- searching, null-checking, validating, reconstructing, error-wrapping. Actual business logic is 2 lines buried in the middle.

---

## AFTER: Effects + Optics Pipeline (6 lines)

```java
EitherPath<DirectoryError, Company> updateManagerEmail(
    Company company, String deptName, String newEmail) {

  // Compose optics path for the structural update
  TraversalPath<Company, ContactInfo> contactInDept =
      CompanyFocus.departments()
          .filter(d -> d.name().equals(deptName))
          .via(DepartmentFocus.manager())
          .via(EmployeeFocus.contact());

  // Railway pipeline: each step has one job
  return findDepartment(company, deptName)                     // EitherPath: find dept
      .focus(DepartmentFocus.manager(),                        // AffinePath: get manager
             new DirectoryError.NoManager(deptName))
      .focus(EmployeeFocus.contact())                          // FocusPath: get contact
      .via(contact -> validateEmail(newEmail)                  // EitherPath: validate
          .map(valid -> new ContactInfo(contact.phone(), Optional.of(valid))))
      .map(updatedContact ->                                   // Reconstruct via optics
          contactInDept.setAll(updatedContact, company));
}
```

Helper methods:

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
      new DirectoryError.InvalidEmail(email, "Must contain @ and be >= 5 chars"));
}
```

---

## Railway Diagram

```
Success ===*===========*===============*===========*===========*===>  Company
       findDept  focus(manager)  focus(contact)    via        map
                                               (validate)  (setAll)
                      \
                       \  absent: NoManager
                        \
Failure ---*------------*--------------------------*--------------->  DirectoryError
      DeptNotFound  NoManager                  InvalidEmail
```

Each step either continues on the success track or diverts to failure.

---

## Pipeline Step-by-Step

| Step | Operator | What Happens | Failure Case |
|------|----------|-------------|-------------|
| `findDepartment(...)` | Entry | Returns `Right(dept)` or `Left(DepartmentNotFound)` | Department not in list |
| `.focus(DepartmentFocus.manager(), error)` | AffinePath focus | Extracts Optional manager. Absent = switches to failure | `NoManager` |
| `.focus(EmployeeFocus.contact())` | FocusPath focus | Extracts ContactInfo. Always succeeds (required field) | N/A |
| `.via(contact -> validateEmail(...).map(...))` | Effect chain | Validates email, builds updated ContactInfo | `InvalidEmail` |
| `.map(updated -> contactInDept.setAll(...))` | Transform | Reconstructs Company via composed optics path | N/A |

---

## The Two Bridging Directions

### Effects -> Optics: `focus()` calls

Already in an `EitherPath` pipeline; need to drill into the contained value's structure.

```java
// AffinePath focus -- requires error argument (field may be absent)
eitherPath.focus(DepartmentFocus.manager(), new DirectoryError.NoManager(deptName))

// FocusPath focus -- no error needed (field always present)
eitherPath.focus(EmployeeFocus.contact())
```

**Rule**: `AffinePath` = error required. `FocusPath` = no error needed. Types enforce this.

### Optics -> Effects: entry point

Start with concrete data; lift into the effect domain.

```java
// Lift into EitherPath
Path.<DirectoryError, Department>right(department)   // success
Path.left(new DirectoryError.DepartmentNotFound(name))  // failure
```

Alternative using filtered traversal:

```java
EitherPath<DirectoryError, Department> findDepartment(Company company, String name) {
  return CompanyFocus.departments()
      .filter(d -> d.name().equals(name))
      .toMaybePath(company)
      .toEitherPath(new DirectoryError.DepartmentNotFound(name));
}
```

---

## When to Use Each Approach

| Approach | Favour When |
|----------|------------|
| Stream lookup | Search is value-based (name, ID, runtime data). `findFirst` intent is obvious. |
| Filtered traversal | Access is structural and reusable. Path composes with other optics. |

Rule of thumb: optics for **where** the data lives (structure), streams/effects for **which** data you want (queries).

---

## Optics Path Composition

The composed path used for reconstruction:

```java
TraversalPath<Company, ContactInfo> contactInDept =
    CompanyFocus.departments()                      // TraversalPath<Company, Department>
        .filter(d -> d.name().equals(deptName))     // filtered traversal
        .via(DepartmentFocus.manager())             // -> AffinePath -> TraversalPath
        .via(EmployeeFocus.contact());              // -> FocusPath -> TraversalPath

// Apply update to ALL matching targets
contactInDept.setAll(updatedContact, company);
```

Optic composition rules:

| Composed With | FocusPath | AffinePath | TraversalPath |
|--------------|-----------|------------|---------------|
| **FocusPath** | FocusPath | AffinePath | TraversalPath |
| **AffinePath** | AffinePath | AffinePath | TraversalPath |
| **TraversalPath** | TraversalPath | TraversalPath | TraversalPath |

Result type is always the "weakest" (most general) of the two.

---

## Key Takeaways

| Aspect | Imperative | Effects + Optics |
|--------|-----------|-----------------|
| Lines | ~30 | ~6 pipeline + 2 helpers |
| Error handling | Manual if/return | Railway (implicit propagation) |
| Structure rebuild | Manual constructor chain | `setAll` via composed optics |
| Concern separation | All tangled | One concern per pipeline step |
| Absent optional | `isEmpty()` + early return | `focus(affinePath, error)` |
| Type safety | Runtime null risk | Compile-time: AffinePath requires error, FocusPath does not |

The difference is structural, not cosmetic. Each concern (searching, checking, validating, rebuilding) occupies its own pipeline step, connected by automatic error propagation.

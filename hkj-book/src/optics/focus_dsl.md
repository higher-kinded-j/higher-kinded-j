# Focus DSL: Path-Based Optic Syntax

## _Type-Safe Navigation Through Nested Data_

~~~admonish info title="What You'll Learn"
- How to navigate deeply nested data structures with type-safe paths
- Using `@GenerateFocus` to generate path builders automatically
- The difference between `FocusPath`, `AffinePath`, and `TraversalPath`, and which one a field gives you
- Fluent cross-type navigation with generated navigators, and where `.via()` takes over
- Where to go next for collections, effects, container types, and the reference material
~~~

~~~admonish example title="See Example Code"
[NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java) | [ContainerNavigationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java) | [TraverseIntegrationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/TraverseIntegrationExample.java) | [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ValidationPipelineExample.java)
~~~

The Focus DSL provides a fluent, path-based syntax for working with optics. Instead of composing lenses, prisms, and traversals by hand, you navigate your data structures with method chains that mirror the shape of the data.

---

## Five-Minute Focus DSL

If you only have a few minutes, this is the entire feature.

**Step 1.** Annotate two records:

<!-- verify -->
```java
import org.higherkindedj.optics.annotations.GenerateFocus;

@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}
```

**Step 2.** Use the generated `UserFocus` companion class:

<!-- verify -->
```java
// Get
String city = UserFocus.address().city().get(alice);              // "London"

// Set
User moved = UserFocus.address().city().set("Paris", alice);

// Modify
User shouty = UserFocus.address().city().modify(String::toUpperCase, alice);
```

**Step 3.** That is it. The path you typed (`UserFocus.address().city()`) is a typed value: store it, pass it around, reuse it. The processor generated `UserFocus` and `AddressFocus` at compile time; nothing reflective happens at runtime.

Collections, optionals and sealed types extend the same pattern through `.each()`, `.some()`, `.at(i)` and `instanceOf()`. A `Kind<F, A>` field needs none of them: the processor recognises the witness and generates the traversal, which [Kind Field Support](kind_field_support.md) covers. The rest of this page walks through the path types; you almost never have to compose lenses by hand to get useful work done.

~~~admonish tip title="Why this matters"
`generateNavigators = true` is what makes `.city()` chain directly off `.address()`. Without it, `UserFocus.address()` is still a perfectly good `FocusPath<User, Address>`; you just spell the next hop `.via(AddressFocus.city())`. Navigators are sugar over composition, not a separate mechanism, so nothing is lost by leaving them off and nothing is locked in by turning them on.
~~~

---

## The Problem: Verbose Manual Composition

Deep updates without optics mean rebuilding every record on the way down. With raw optics they mean composing at each use site:

```java
// Manual composition: verbose and repetitive
Traversal<Company, String> employeeNames =
    CompanyTraversals.departments()
        .andThen(DepartmentTraversals.employees())
        .andThen(EmployeeLenses.name().asTraversal());

List<String> names = Traversals.getAll(employeeNames, company);
```

With the Focus DSL the same navigation reads as a path, and the path type tracks how many elements are in focus:

<!-- verify -->
```java
List<String> names =
    CompanyFocus.departments()          // TraversalPath<Company, Department>
        .via(DepartmentFocus.employees()) // TraversalPath<Company, Employee>
        .via(EmployeeFocus.name())        // TraversalPath<Company, String>
        .getAll(company);
```

---

## Think of Focus Paths Like...

- **File system paths**: `/company/departments/employees/name`
- **JSON pointers**: `$.departments[*].employees[*].name`
- **XPath expressions**: `//department/employee/name`
- **IDE navigation**: click through nested fields with autocomplete

The key difference: Focus paths are fully type-safe, with compile-time checking at every step.

---

## What the Processor Gives You

### Annotate the records

Add `@GenerateFocus` to generate path builders. The Focus class builds its own lenses, so `@GenerateFocus` alone compiles. Add `@GenerateLenses` as well in practice: several idioms in this chapter (indexing a list, composing `ListPrisms`, acting on a collection as a whole) start from `FocusPath.of(TheseLenses.field())`, which needs the generated lens class.

```java
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
@GenerateFocus
public record Company(String name, List<Department> departments) {}

@GenerateLenses
@GenerateFocus
public record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus
public record Employee(String name, int age, Optional<String> email) {}
```

### One method per component, and the field type picks the path

The generated companion has one method per record component, and the field's *type* decides the path type you get back:

<!-- verify -->
```java
// A plain field: exactly one focus
FocusPath<Company, String> namePath = CompanyFocus.name();
String companyName = namePath.get(company);

// A List field: the processor has already stepped into the elements
TraversalPath<Company, Department> deptPath = CompanyFocus.departments();
List<Department> allDepts = deptPath.getAll(company);

// An Optional field: zero or one focus
AffinePath<Employee, String> emailPath = EmployeeFocus.email();
Optional<String> email = emailPath.getOptional(employee);
```

~~~admonish warning title="A collection field is already element-level"
`CompanyFocus.departments()` focuses each `Department`, not the `List<Department>`. That is what you want for bulk reads and updates, but it means list-level operations (`ListPrisms.head()`, indexing into the list) do not compose onto it. When you need the list itself, start from the lens instead: `FocusPath.of(CompanyLenses.departments())`. See [Navigation and Composition](focus_navigation.md#access-by-index) for the indexing forms.
~~~

### Chain with `.via()`

Paths compose with `.via()`. Composing a path with a wider one widens the result, so one focus joined to many focuses is many focuses, and anything joined to a traversal is a traversal:

<!-- verify -->
```java
TraversalPath<Company, String> allEmployeeNames =
    CompanyFocus.departments()
        .via(DepartmentFocus.employees())
        .via(EmployeeFocus.name());

// Read every one of them
List<String> names = allEmployeeNames.getAll(company);

// Or update every one of them
Company updated = allEmployeeNames.modifyAll(String::toUpperCase, company);
```

---

## The Three Path Types

Focus DSL provides three path types, mirroring the optic hierarchy:

```
         FocusPath<S, A>
        (exactly one focus)
               |
        AffinePath<S, A>
      (zero or one focus)
               |
      TraversalPath<S, A>
      (zero or more focus)
```

### FocusPath: Exactly One Element

`FocusPath<S, A>` wraps a `Lens<S, A>` and guarantees exactly one focused element:

<!-- verify -->
```java
FocusPath<Employee, String> namePath = EmployeeFocus.name();

String name = namePath.get(employee);                            // always a value
Employee updated = namePath.set("Bob", employee);                // always succeeds
Employee modified = namePath.modify(String::toUpperCase, employee);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(Function<A,A>, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract the underlying optic |

### AffinePath: Zero or One Element

`AffinePath<S, A>` wraps an `Affine<S, A>` for optional access:

<!-- verify -->
```java
AffinePath<Employee, String> emailPath = EmployeeFocus.email();

Optional<String> email = emailPath.getOptional(employee);        // may be empty
Employee updated = emailPath.set("new@email.com", employee);     // writes anyway: creates the email if absent
Employee modified = emailPath.modify(String::toLowerCase, employee);
boolean hasEmail = emailPath.matches(employee);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Write the value, creating the focus if the last step is absent |
| `modify(Function<A,A>, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check whether a value is in focus |
| `getOrElse(A, S)` | `A` | Extract, or the given default |
| `toAffine()` | `Affine<S, A>` | Extract the underlying optic |

~~~admonish warning title="Set on an absent focus writes anyway"
`set` through an `AffinePath` is not conditional. `EmployeeFocus.email().set(x, employee)` on an employee with no email returns an employee *with* that email, because the last step's setter rebuilds the present case unconditionally: `Affine.set` "always updates the structure", as its own javadoc puts it. `modify` is the operation that no-ops on an absent focus.

The rule is positional. A miss at the *last* step writes through and creates the focus; a miss at an *earlier* step of a multi-step path skips the whole set, because `Affine.andThen(Affine)` does guard. When absence must be preserved, reach for `modify`, or test with `matches` first.
~~~

### TraversalPath: Zero or More Elements

`TraversalPath<S, A>` wraps a `Traversal<S, A>` for collection access:

<!-- verify -->
```java
TraversalPath<Department, Employee> employeesPath = DepartmentFocus.employees();

List<Employee> all = employeesPath.getAll(department);
Department updated = employeesPath.setAll(employee, department);
Department modified = employeesPath.modifyAll(Fixture::promote, department);
int headcount = employeesPath.count(department);
```

`Fixture`, here and throughout this chapter, is the compiled example's own setup: sample records, hand-written optics and validators. Nothing in it is library API.

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `setAll(A, S)` | `S` | Replace all focused values |
| `modifyAll(Function<A,A>, S)` | `S` | Transform all focused values |
| `filter(Predicate<A>)` | `TraversalPath<S, A>` | Narrow to the matching elements (on a raw `Traversal` the same narrowing is spelled `filtered`) |
| `preview(S)` | `Optional<A>` | The first focused value, if any |
| `count(S)`, `isEmpty(S)` | `int`, `boolean` | Query the number in focus |
| `exists(Predicate<A>, S)`, `all(Predicate<A>, S)` | `boolean` | Does any, or every, focused value match |
| `find(Predicate<A>, S)` | `Optional<A>` | The first focused value that matches |
| `fold(Monoid<A>, S)` | `A` | Combine every focused value through a monoid |
| `headOption()` | `AffinePath<S, A>` | Narrow to the first focused element (reads the first, writes to all) |
| `toTraversal()` | `Traversal<S, A>` | Extract the underlying optic |

---

~~~admonish info title="Key Takeaways"
* **The field type picks the path type.** A plain field gives `FocusPath`, an `Optional` field gives `AffinePath`, a `List` or `Set` field gives `TraversalPath` already stepped into the elements.
* **`.via()` is the universal join.** Navigators are generated sugar for a subset of fields; everything else composes with `.via()`, and the two mix freely. [Navigation and Composition](focus_navigation.md) sets out which fields qualify.
* **Widening is one-directional.** Composing a path with a wider one widens the result: one focus plus zero-or-one is zero-or-one, and anything joined to a traversal is a traversal.
* **A path is a value, not a call.** Build it once, store it in a static field, pass it around. `toLens()`, `toAffine()` and `toTraversal()` hand the underlying optic to any API that wants a raw optic.
~~~

~~~admonish info title="Hands-On Learning"
- [Tutorial12_FocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) (10 exercises, ~10 minutes)
- [Tutorial13_AdvancedFocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java) (8 exercises, ~10 minutes)
- [Tutorial19_NavigatorGeneration.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java) (8 exercises, ~10 minutes)
~~~

~~~admonish tip title="See Also"
- [Navigation and Composition](focus_navigation.md): collection navigation, `.via()` composition, and generated navigators
- [Type Class and Effect Integration](focus_effects.md): `modifyF()`, `foldMap()`, `traverseOver()`, sum types, and Effect path bridging
- [Custom Containers and Code Generation](focus_containers.md): generated class structure, SPI container types, and registration
- [Focus DSL Reference](focus_reference.md): decision guide, common patterns, performance, pitfalls, and FAQ
~~~

---

**Previous:** [Java-Friendly APIs](ch4_intro.md)
**Next:** [Navigation and Composition](focus_navigation.md)

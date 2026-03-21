# Focus DSL: Path-Based Optic Syntax

## _Type-Safe Navigation Through Nested Data_

~~~admonish info title="What You'll Learn"
- How to navigate deeply nested data structures with type-safe paths
- Using `@GenerateFocus` to generate path builders automatically
- **Fluent cross-type navigation** with generated navigators (no `.via()` needed)
- The difference between `FocusPath`, `AffinePath`, and `TraversalPath`
- Collection navigation with `.each()`, `.each(Each)`, `.at()`, `.some()`, `.some(Affine)`, `.nullable()`, and `.traverseOver()`
- **Custom container types**: automatic `AffinePath` and `TraversalPath` generation for `Either`, `Try`, `Validated`, `Map`, arrays, and your own types
- **Seamless nullable field handling** with `@Nullable` annotation detection
- Type class integration: effectful operations, monoid aggregation, and Traverse support
- Working with sum types using `instanceOf()` and conditional modification with `modifyWhen()`
- Composing Focus paths with existing optics
- Debugging paths with `traced()`
- When to use Focus DSL vs manual lens composition
~~~

~~~admonish title="Hands On Practice"
- [Tutorial12_FocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial12_FocusDSL.java) 
- [Tutorial13_AdvancedFocusDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial13_AdvancedFocusDSL.java) 
- [Tutorial19_NavigatorGeneration.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial19_NavigatorGeneration.java)
~~~

~~~admonish title="Example Code"
[NavigatorExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/NavigatorExample.java) | [ContainerNavigationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ContainerNavigationExample.java) | [TraverseIntegrationExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/TraverseIntegrationExample.java) | [ValidationPipelineExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/focus/ValidationPipelineExample.java)
~~~

The Focus DSL provides a fluent, path-based syntax for working with optics. Instead of manually composing lenses, prisms, and traversals, you can navigate through your data structures using intuitive method chains that mirror the shape of your data.

---

## The Problem: Verbose Manual Composition

When working with deeply nested data structures, manual optic composition becomes verbose:

```java
// Manual composition - verbose and repetitive
Lens<Company, String> employeeNameLens =
    CompanyLenses.departments()
        .andThen(DepartmentLenses.employees())
        .andThen(EmployeeLenses.name());

// Must compose at each use site
String name = employeeNameLens.get(company);
```

With the Focus DSL, the same operation becomes:

```java
// Focus DSL - fluent and intuitive
String name = CompanyFocus.departments().employees().name().get(company);
```

---

## Think of Focus Paths Like...

- **File system paths**: `/company/departments/employees/name`
- **JSON pointers**: `$.departments[*].employees[*].name`
- **XPath expressions**: `//department/employee/name`
- **IDE navigation**: Click through nested fields with autocomplete

The key difference: Focus paths are fully type-safe, with compile-time checking at every step.

---

## A Step-by-Step Walkthrough

### Step 1: Annotate Your Records

Add `@GenerateFocus` alongside `@GenerateLenses` to generate path builders:

```java
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateFocus;

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

### Step 2: Use Generated Focus Classes

The annotation processor generates companion Focus classes with path builders:

```java
// Generated: CompanyFocus.java
// Navigate to company name
FocusPath<Company, String> namePath = CompanyFocus.name();
String companyName = namePath.get(company);

// Navigate through collections
TraversalPath<Company, Department> deptPath = CompanyFocus.departments();
List<Department> allDepts = deptPath.getAll(company);

// Navigate to specific index
AffinePath<Company, Department> firstDeptPath = CompanyFocus.department(0);
Optional<Department> firstDept = firstDeptPath.getOptional(company);
```

### Step 3: Chain Navigation Methods

Focus paths support fluent chaining for deep navigation:

```java
// Deep path through collections
TraversalPath<Company, String> allEmployeeNames =
    CompanyFocus.departments()     // TraversalPath<Company, Department>
        .employees()               // TraversalPath<Company, Employee>
        .name();                   // TraversalPath<Company, String>

// Get all employee names across all departments
List<String> names = allEmployeeNames.getAll(company);

// Modify all employee names
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

```java
// Always succeeds - the field always exists
FocusPath<Employee, String> namePath = EmployeeFocus.name();

String name = namePath.get(employee);           // Always returns a value
Employee updated = namePath.set("Bob", employee);  // Always succeeds
Employee modified = namePath.modify(String::toUpperCase, employee);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `get(S)` | `A` | Extract the focused value |
| `set(A, S)` | `S` | Replace the focused value |
| `modify(Function<A,A>, S)` | `S` | Transform the focused value |
| `toLens()` | `Lens<S, A>` | Extract underlying optic |

### AffinePath: Zero or One Element

`AffinePath<S, A>` wraps an `Affine<S, A>` for optional access:

```java
// May or may not have a value
AffinePath<Employee, String> emailPath = EmployeeFocus.email();

Optional<String> email = emailPath.getOptional(employee);  // May be empty
Employee updated = emailPath.set("new@email.com", employee);  // Always succeeds
Employee modified = emailPath.modify(String::toLowerCase, employee);  // No-op if absent
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getOptional(S)` | `Optional<A>` | Extract if present |
| `set(A, S)` | `S` | Replace (creates if structure allows) |
| `modify(Function<A,A>, S)` | `S` | Transform if present |
| `matches(S)` | `boolean` | Check if value exists |
| `toAffine()` | `Affine<S, A>` | Extract underlying optic |

### TraversalPath: Zero or More Elements

`TraversalPath<S, A>` wraps a `Traversal<S, A>` for collection access:

```java
// Focuses on multiple elements
TraversalPath<Department, Employee> employeesPath = DepartmentFocus.employees();

List<Employee> all = employeesPath.getAll(department);    // All employees
Department updated = employeesPath.setAll(defaultEmployee, department);
Department modified = employeesPath.modifyAll(e -> promote(e), department);
```

**Key Operations:**

| Method | Return Type | Description |
|--------|-------------|-------------|
| `getAll(S)` | `List<A>` | Extract all focused values |
| `setAll(A, S)` | `S` | Replace all focused values |
| `modifyAll(Function<A,A>, S)` | `S` | Transform all focused values |
| `filter(Predicate<A>)` | `TraversalPath<S, A>` | Filter focused elements |
| `toTraversal()` | `Traversal<S, A>` | Extract underlying optic |

---

~~~admonish tip title="See Also"
1. [Navigation and Composition](focus_navigation.md) - Collection navigation, `.via()` composition, and generated navigators
2. [Type Class and Effect Integration](focus_effects.md) - `modifyF()` `foldMap()`, `traverseOver()`, sum types, and Effect path bridging
3. [Custom Containers and Code Generation](focus_containers.md) - Generated class structure, SPI container types, and registration
4. [Focus DSL Reference](focus_reference.md) - Decision guide, common patterns, performance, pitfalls, and FAQ
~~~

---

**Previous:** [Introduction](ch4_intro.md)
**Next:** [Navigation and Composition](focus_navigation.md)

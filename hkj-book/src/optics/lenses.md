# Nested Updates with Lenses: A Practical Guide

## _Working with Product Types_

![Visual representation of a lens focusing on a single field within nested immutable data structures](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- How to safely access and update fields in immutable data structures
- Using `@GenerateLenses` to automatically create type-safe field accessors
- Composing lenses to navigate deeply nested records
- The difference between `get`, `set`, and `modify` operations
- Building reusable, composable data access patterns
- When to use lenses vs direct field access
~~~

~~~admonish title="Example Code"
[LensesExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensUsageExample.java)
~~~

In the introduction to optics, we saw how updating nested immutable data can be verbose and why optics provide a clean, functional solution. We identified the `Lens` as the primary tool for working with "has-a" relationships, like a field within a record.

This guide provides a complete, step-by-step walkthrough of how to solve the nested update problem using a composable Lens and its generated helper methods.

---

## The Scenario: Updating an Employee's Address

Let's use a common business scenario involving a deeply nested data structure. Our goal is to update the `street` of an `Employee`'s `Company``Address`.

**The Data Model:**

```java
public record Address(String street, String city) {}
public record Company(String name, Address address) {}
public record Employee(String name, Company company) {}
```

Without optics, changing the street requires manually rebuilding the entire `Employee` object graph. With optics, we can define a direct path to the `street` and perform the update in a single, declarative line.

---

## A Step-by-Step Walkthrough

### Step 1: Generating the Lenses

Manually writing `Lens` implementations is tedious boilerplate. The `hkj-optics` library automates this with an annotation processor. To begin, we simply annotate our records with **`@GenerateLenses`**.

This process creates a companion class for each record (e.g., `EmployeeLenses`, `CompanyLenses`) that contains two key features:

1. **Lens Factories**: Static methods that create a `Lens` for each field (e.g., `EmployeeLenses.company()`).
2. **`with*` Helpers**: Static convenience methods for easy, shallow updates (e.g., `EmployeeLenses.withCompany(...)`).

```java
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
public record Address(String street, String city) {}

@GenerateLenses
public record Company(String name, Address address) {}

@GenerateLenses
public record Employee(String name, Company company) {}
```

#### Customising the Generated Package

By default, generated classes are placed in the same package as the annotated record. You can specify a different package using the `targetPackage` attribute to avoid name collisions or to organise generated code separately:

```java
// Generated class will be placed in org.example.generated.optics
@GenerateLenses(targetPackage = "org.example.generated.optics")
public record Address(String street, String city) {}
```

This is particularly useful when:
- Multiple records in different packages share the same name
- You want to keep generated code separate from source code
- You need to control the visibility of generated classes

### Step 2: Composing a Deep Lens

With the lenses generated, we can now compose them using the **`andThen`** method. We'll chain the individual lenses together to create a single, new `Lens` that represents the complete path from the top-level object (`Employee`) to the deeply nested field (`street`).

The result is a new, powerful, and reusable `Lens<Employee, String>`.

```java
// Get the generated lenses
Lens<Employee, Company> employeeToCompany = EmployeeLenses.company();
Lens<Company, Address> companyToAddress = CompanyLenses.address();
Lens<Address, String> addressToStreet = AddressLenses.street();

// Compose them to create a single, deep lens
Lens<Employee, String> employeeToStreet =
    employeeToCompany
        .andThen(companyToAddress)
        .andThen(addressToStreet);
```

### Step 3: Performing Updates with the Composed Lens

With our optics generated, we have two primary ways to perform updates.

#### A) Simple, Shallow Updates with `with*` Helpers

For simple updates to a top-level field, the generated `with*` methods are the most convenient and readable option.

```java
// Create an employee instance
var employee = new Employee("Alice", ...);

// Use the generated helper to create an updated copy
var updatedEmployee = EmployeeLenses.withName(employee, "Bob");
```

This is a cleaner, more discoverable alternative to using the lens directly (`EmployeeLenses.name().set("Bob", employee)`).

#### B) Deep Updates with a Composed Lens

For deep updates into nested structures, the composed lens is the perfect tool. The `Lens` interface provides two primary methods for this:

* `set(newValue, object)`: Replaces the focused value with a new one.
* `modify(function, object)`: Applies a function to the focused value to compute the new value.

Both methods handle the "copy-and-update" cascade for you, returning a completely new top-level object.

```java
// Use the composed lens from Step 2
Employee updatedEmployee = employeeToStreet.set("456 Main St", initialEmployee);
```

---

## When to Use `with*` Helpers vs Manual Lenses

Understanding when to use each approach will help you write cleaner, more maintainable code:

### Use `with*` Helpers When:

* **Simple, top-level field updates** - Direct field replacement on the immediate object
* **One-off updates** - You don't need to reuse the update logic
* **API clarity** - You want the most discoverable, IDE-friendly approach


```java
// Perfect for simple updates
var promotedEmployee = EmployeeLenses.withName(employee, "Senior " + employee.name());
```

### Use Composed Lenses When:

* **Deep updates** - Navigating multiple levels of nesting
* **Reusable paths** - The same update pattern will be used multiple times
* **Complex transformations** - Using `modify()` with functions
* **Conditional updates** - Part of larger optic compositions


```java
// Ideal for reusable deep updates
Lens<Employee, String> streetLens = employeeToCompany
    .andThen(companyToAddress)
    .andThen(addressToStreet);

// Can be reused across your application
Employee moved = streetLens.set("New Office Street", employee);
Employee uppercased = streetLens.modify(String::toUpperCase, employee);
```

~~~admonish tip title="Cross-Optic Composition"
Lenses can also compose with other optic types. When you compose a `Lens` with a `Prism`, you get a `Traversal`:

```java
// Lens >>> Prism = Traversal
record User(Optional<Settings> settings) {}
Lens<User, Optional<Settings>> settingsLens = UserLenses.settings();
Prism<Optional<Settings>, Settings> somePrism = Prisms.some();

Traversal<User, Settings> userSettings = settingsLens.andThen(somePrism);
```

See [Composition Rules](composition_rules.md) for the complete reference on how different optics compose.
~~~

### Use Manual Lens Creation When:

* **Computed properties** - The lens represents derived data
* **Complex transformations** - Custom getter/setter logic
* **Legacy integration** - Working with existing APIs


```java
// For computed or derived properties
Lens<Employee, String> fullAddressLens = Lens.of(
    emp -> emp.company().address().street() + ", " + emp.company().address().city(),
    (emp, fullAddr) -> {
        String[] parts = fullAddr.split(", ");
        return employeeToCompany.andThen(companyToAddress).set(
            new Address(parts[0], parts[1]), emp);
    }
);
```

---

## Common Pitfalls

### Don't Do This:


```java
// Inefficient: Calling get() multiple times
var currentStreet = employeeToStreet.get(employee);
var newEmployee = employeeToStreet.set(currentStreet.toUpperCase(), employee);

// Verbose: Rebuilding lenses repeatedly
var street1 = EmployeeLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street()).get(emp1);
var street2 = EmployeeLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street()).get(emp2);

// Mixing approaches unnecessarily
var tempCompany = EmployeeLenses.company().get(employee);
var updatedCompany = CompanyLenses.withName(tempCompany, "New Company");
var finalEmployee = EmployeeLenses.withCompany(employee, updatedCompany);
```

### Do This Instead:


```java
// Efficient: Use modify() for transformations
var newEmployee = employeeToStreet.modify(String::toUpperCase, employee);

// Reusable: Create the lens once, use many times
var streetLens = EmployeeLenses.company().andThen(CompanyLenses.address()).andThen(AddressLenses.street());
var street1 = streetLens.get(emp1);
var street2 = streetLens.get(emp2);

// Consistent: Use one approach for the entire update
var finalEmployee = EmployeeLenses.company()
    .andThen(CompanyLenses.name())
    .set("New Company", employee);
```

---

## Performance Notes

Lenses are optimised for immutable updates:

* **Memory efficient**: Only creates new objects along the path that changes
* **Reusable**: Composed lenses can be stored and reused across your application
* **Type-safe**: All operations are checked at compile time
* **Lazy**: Operations are only performed when needed

**Best Practice**: For frequently used paths, create the composed lens once and store it as a static field:

```java
public class EmployeeOptics {
    public static final Lens<Employee, String> STREET = 
        EmployeeLenses.company()
            .andThen(CompanyLenses.address())
            .andThen(AddressLenses.street());
        
    public static final Lens<Employee, String> COMPANY_NAME = 
        EmployeeLenses.company()
            .andThen(CompanyLenses.name());
}
```

---

## Complete, Runnable Example

The following standalone example puts all these steps together. You can run it to see the output and the immutability in action.

```java
package org.higherkindedj.example.lens;

// Imports for the generated classes would be automatically resolved by your IDE
import org.higherkindedj.example.lens.LensUsageExampleLenses.AddressLenses;
import org.higherkindedj.example.lens.LensUsageExampleLenses.CompanyLenses;
import org.higherkindedj.example.lens.LensUsageExampleLenses.EmployeeLenses;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

public class LensUsageExample {

    // 1. Define a nested, immutable data model.
    @GenerateLenses
    public record Address(String street, String city) {}

    @GenerateLenses
    public record Company(String name, Address address) {}

    @GenerateLenses
    public record Employee(String name, Company company) {}


    public static void main(String[] args) {
        // 2. Create an initial, nested immutable object.
        var initialAddress = new Address("123 Fake St", "Anytown");
        var initialCompany = new Company("Initech Inc.", initialAddress);
        var initialEmployee = new Employee("Alice", initialCompany);

        System.out.println("Original Employee: " + initialEmployee);
        System.out.println("------------------------------------------");


        // --- SCENARIO 1: Simple update with a `with*` helper ---
        System.out.println("--- Scenario 1: Using `with*` Helper ---");
        var employeeWithNewName = EmployeeLenses.withName(initialEmployee, "Bob");
        System.out.println("After `withName`:    " + employeeWithNewName);
        System.out.println("------------------------------------------");

        // --- SCENARIO 2: Deep update with a composed Lens ---
        System.out.println("--- Scenario 2: Using Composed Lens ---");
        Lens<Employee, String> employeeToStreet =
            EmployeeLenses.company()
                .andThen(CompanyLenses.address())
                .andThen(AddressLenses.street());

        // Use `set` to replace a value
        Employee updatedEmployeeSet = employeeToStreet.set("456 Main St", initialEmployee);
        System.out.println("After deep `set`:       " + updatedEmployeeSet);

        // Use `modify` to apply a function
        Employee updatedEmployeeModify = employeeToStreet.modify(String::toUpperCase, initialEmployee);
        System.out.println("After deep `modify`:    " + updatedEmployeeModify);
        System.out.println("Original is unchanged:  " + initialEmployee);
      
        // --- SCENARIO 3: Demonstrating reusability ---
        System.out.println("--- Scenario 3: Reusing Composed Lens ---");
        var employee2 = new Employee("Charlie", new Company("Tech Corp", new Address("789 Oak Ave", "Tech City")));
      
        // Same lens works on different employee instances
        var bothUpdated = List.of(initialEmployee, employee2)
            .stream()
            .map(emp -> employeeToStreet.modify(street -> "Remote: " + street, emp))
            .toList();
          
        System.out.println("Batch updated: " + bothUpdated);
    }
}
```

**Expected Output:**

```
Original Employee: Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
------------------------------------------
--- Scenario 1: Using `with*` Helper ---
After `withName`:    Employee[name=Bob, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
------------------------------------------
--- Scenario 2: Using Composed Lens ---
After deep `set`:       Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=456 Main St, city=Anytown]]]
After deep `modify`:    Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 FAKE ST, city=Anytown]]]
Original is unchanged:  Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
------------------------------------------
--- Scenario 3: Reusing Composed Lens ---
Batch updated: [Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=Remote: 123 Fake St, city=Anytown]]], Employee[name=Charlie, company=Company[name=Tech Corp, address=Address[street=Remote: 789 Oak Ave, city=Tech City]]]]
```

As you can see, the generated optics provide a clean, declarative, and type-safe API for working with immutable data, whether your updates are simple and shallow or complex and deep.

---

## Beyond the Basics: Effectful Updates with `modifyF`

While `set` and `modify` are for simple, pure updates, the `Lens` interface also supports effectful operations through `modifyF`. This method allows you to perform updates within a context like an `Optional`, `Validated`, or `CompletableFuture`.

This means you can use the same `employeeToStreet` lens to perform a street name update that involves failable validation or an asynchronous API call, making your business logic incredibly reusable and robust.

```java
// Example: Street validation that might fail
Function<String, Kind<ValidatedKind.Witness<String>, String>> validateStreet = 
    street -> street.length() > 0 && street.length() < 100 
        ? VALIDATED.widen(Validated.valid(street))
        : VALIDATED.widen(Validated.invalid("Street name must be between 1 and 100 characters"));

// Use the same lens with effectful validation
Kind<ValidatedKind.Witness<String>, Employee> result = 
    employeeToStreet.modifyF(validateStreet, employee, validatedApplicative);
```

---

**Previous:** [An Introduction to Optics](optics_intro.md)
**Next:** [Prisms: Working with Sum Types](prisms.md)

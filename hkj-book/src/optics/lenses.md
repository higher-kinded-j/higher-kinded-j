# Practical Guide: Solving Nested Updates with Lenses (Product types)

In the introduction to optics, we saw how updating nested immutable data can be verbose and why optics provide a clean, functional solution. We identified the `Lens` as the primary tool for working with "has-a" relationships, like a field within a record.

This guide provides a complete, step-by-step walkthrough of how to solve the nested update problem using a composable `Lens`. We will take a concrete data model and show how to generate, compose, and use lenses to perform deep, immutable updates effortlessly.

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

This process creates a companion class for each record (e.g., `EmployeeLenses`, `CompanyLenses`) that contains the static lens fields we'll need for composition.

```java
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
public record Address(String street, String city) {}

@GenerateLenses
public record Company(String name, Address address) {}

@GenerateLenses
public record Employee(String name, Company company) {}
```

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

Now that we have our `employeeToStreet` lens, we can perform updates effortlessly. The `Lens` interface provides two primary methods for this:

* `set(newValue, object)`: Replaces the focused value with a new one.
* `modify(function, object)`: Applies a function to the focused value to compute the new value.

Both methods handle the "copy-and-update" cascade for you, returning a completely new top-level `Employee` with the change applied deep inside, while leaving the original object untouched.

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


        // 3. Compose lenses to create a "deep" focus.
        Lens<Employee, String> employeeToStreet =
            EmployeeLenses.company()
                .andThen(CompanyLenses.address())
                .andThen(AddressLenses.street());


        // 4. Use the composed lens to perform immutable updates.

        // --- Using `set` to replace a value ---
        Employee updatedEmployeeSet = employeeToStreet.set("456 Main St", initialEmployee);

        System.out.println("After `set`:       " + updatedEmployeeSet);
        System.out.println("Original is unchanged: " + initialEmployee);
        System.out.println("------------------------------------------");


        // --- Using `modify` to apply a function to the value ---
        Employee updatedEmployeeModify = employeeToStreet.modify(String::toUpperCase, initialEmployee);

        System.out.println("After `modify`:    " + updatedEmployeeModify);
        System.out.println("Original is unchanged: " + initialEmployee);
    }
}
```

**Expected Output:**

```
Original Employee: Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
------------------------------------------
After `set`:       Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=456 Main St, city=Anytown]]]
Original is unchanged: Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
------------------------------------------
After `modify`:    Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 FAKE ST, city=Anytown]]]
Original is unchanged: Employee[name=Alice, company=Company[name=Initech Inc., address=Address[street=123 Fake St, city=Anytown]]]
```

As you can see, composing lenses provides a clean, declarative, and type-safe API for working with nested immutable data, completely removing the need for manual, error-prone boilerplate.

---

## Beyond the Basics: Effectful Updates with `modifyF`

While `set` and `modify` are for simple, pure updates, the `Lens` interface also supports effectful operations through `modifyF`. This method allows you to perform updates within a context like an `Optional`, `Validated`, or `CompletableFuture`.

This means you can use the same `employeeToStreet` lens to perform a street name update that involves failable validation or an asynchronous API call, making your business logic incredibly reusable and robust.

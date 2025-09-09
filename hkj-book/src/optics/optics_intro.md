# An Introduction to Optics

![optics.jpg](../images/optics.jpg)

As Java developers, we love the safety and predictability of immutable objects, especially with the introduction of records. However, this safety comes at a cost: updating nested immutable data can be a verbose and error-prone nightmare.

Consider a simple nested record structure:

```java
record Street(String name, int number) {}
record Address(Street street, String city) {}
record User(String name, Address address) {}e
```

How do you update the user's street name? In standard Java, you're forced into a "copy-and-update" cascade:

```java
// What most Java developers actually write
public User updateStreetName(User user, String newStreetName) {
    var address = user.address();
    var street = address.street();
    var newStreet = new Street(newStreetName, street.number());
    var newAddress = new Address(newStreet, address.city());
    return new User(user.name(), newAddress);
}
```

This is tedious, hard to read, and gets exponentially worse with deeper nesting. What if there was a way to "zoom in" on the data you want to change, update it, and get a new copy of the top-level object back, all in one clean operation?

This is the problem that **Optics** solve.

## What Are Optics?

At their core, optics are simply **composable, functional getters and setters** for immutable data structures.

Think of an optic as a "zoom lens" for your data. It's a first-class object that represents a path from a whole structure (like `User`) to a specific part (like the street `name`). Because it's an object, you can pass it around, compose it with other optics, and use it to perform functional updates.

## Think of Optics Like...

* **Lens**: A magnifying glass that focuses on one specific part 🔎
* **Prism**: A tool that splits light, but only works with certain types of light 🔬
* **Iso**: A universal translator between equivalent languages 🔄
* **Traversal**: A spotlight that can illuminate many targets at once 🗺️

Every optic provides two basic capabilities:

1. **`get`**: Focus on a structure `S` and retrieve a part `A`.
2. **`set`**: Focus on a structure `S`, provide a new part `A`, and receive a new `S` with the part updated. This is always an immutable operation —> a new copy of `S` is returned.

The real power comes from their **composability**. You can chain optics together to peer deeply into nested structures and perform targeted updates with ease.

## The Optics Family in Higher-Kinded-J

The `higher-kinded-j` library provides the foundation for a rich optics library, primarily focused on three main types. Each is designed to solve a specific kind of data access problem.

### 1. Lens: For "Has-A" Relationships 🔎

A **Lens** is the most common optic. It focuses on a single, required piece of data within a larger "product type" (a `record` or class with fields). It's for data that is guaranteed to exist.

* **Problem it solves**: Getting and setting a field within an object, especially a deeply nested one.
* **Generated Code**: Annotating a record with `@GenerateLenses` produces a companion class (e.g., `UserLenses`) that contains:

  1. A **lens** for each field (e.g., `UserLenses.address()`).
  2. Convenient **`with*` helper methods** for easy updates (e.g., `UserLenses.withAddress(...)`).
* **Example (Deep Update with Lenses)**:

  * To solve our initial problem of updating the user's street name, we compose lenses:

```java
    // Compose lenses to create a direct path to the nested data
    var userToStreetName = UserLenses.address()
        .andThen(AddressLenses.street())
        .andThen(StreetLenses.name());
  
    // Perform the deep update in a single, readable line
    User updatedUser = userToStreetName.set("New Street", user);
```

* **Example (Shallow Update with `with*` Helpers)**:

  * For simple, top-level updates, the `with*` methods are more direct and discoverable.

```java
// Before: Using the lens directly
User userWithNewName = UserLenses.name().set("Bob", user);

// After: Using the generated helper method
User userWithNewName = UserLenses.withName(user, "Bob");
```

### 2. Iso: For "Is-Equivalent-To" Relationships 🔄

An **Iso** (Isomorphism) is a special, reversible optic. It represents a lossless, two-way conversion between two types that hold the exact same information. Think of it as a type-safe, composable adapter.

* **Problem it solves**: Swapping between different representations of the same data, such as a wrapper class and its raw value, or between two structurally different but informationally equivalent records.
* **Example**: Suppose you have a `Point` record and a `Tuple2<Integer, Integer>`, which are structurally different but hold the same data.

  ```java
  public record Point(int x, int y) {}
  ```

  You can define an `Iso` to convert between them:

  ```java
  @GenerateIsos
  public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
    return Iso.of(
        point -> Tuple.of(point.x(), point.y()), // get
        tuple -> new Point(tuple._1(), tuple._2())  // reverseGet
    );
  }
  ```

  This `Iso` can now be composed with other optics to, for example, create a `Lens` that goes from a `Point` directly to its first element inside a `Tuple` representation.

### 3. Prism: For "Is-A" Relationships 🔬

A **Prism** is like a Lens, but for "sum types" (`sealed interface` or `enum`). It focuses on a single, *possible case* of a type. A Prism's `get` operation can fail (it returns an `Optional`), because the data might not be the case you're looking for. Think of it as a type-safe, functional `instanceof` and cast.

* **Problem it solves**: Safely operating on one variant of a sealed interface.
* **Example**: Instead of using an `if-instanceof` chain to handle a specific `DomainError`:

```java
// Using a generated Prism for a sealed interface
DomainErrorPrisms.shippingError()
   .getOptional(error) // Safely gets a ShippingError if it matches
   .filter(ShippingError::isRecoverable)
   .ifPresent(this::handleRecovery); // Perform action only if it's the right type
```

### 4. Traversal: For "Has-Many" Relationships 🗺️

A **Traversal** is an optic that can focus on multiple targets at once—typically all the items within a collection inside a larger structure.

* **Problem it solves**: Applying an operation to every element in a `List`, `Set`, or other collection that is a field within an object.
* **Example**: To validate a list of promo codes in an order with `Validated`:

  ```java
  @GenerateTraversals
  public record OrderData(..., List<String> promoCodes) {}
  var codesTraversal = OrderDataTraversals.promoCodes();
  // returns Validated<Error, Code>
  var validationFunction = (String code) -> validate(code); 

  // Use the traversal to apply the function to every code.
  // The Applicative for Validated handles the error accumulation automatically.
  Validated<Error, OrderData> result = codesTraversal.modifyF(
      validationFunction, orderData, validatedApplicative
  );
  ```

## Advanced Capabilities: Profunctor Adaptations

One of the most powerful features of `higher-kinded-j` optics is their **profunctor** nature. Every optic can be adapted to work with different source and target types using three key operations:

* **`contramap`**: Adapt an optic to work with a different source type
* **`map`**: Transform the result type of an optic
* **`dimap`**: Adapt both source and target types simultaneously

This makes optics incredibly flexible for real-world scenarios like API integration, legacy system support, and working with different data representations. For a detailed exploration of these capabilities, see the [Profunctor Optics Guide](profunctor_optics.md).

## How `higher-kinded-j` Provides Optics

This brings us to the unique advantages `higher-kinded-j` offers for optics in Java.

1. **An Annotation-Driven Workflow**: Manually writing optics is boilerplate. The `higher-kinded-j` approach automates this. By simply adding an annotation (`@GenerateLenses`, `@GeneratePrisms`, etc.) to your data classes, you get fully-functional, type-safe optics for free. This is a massive productivity boost and eliminates a major barrier to using optics in Java.
2. **Higher-Kinded Types for Effectful Updates**: This is the most powerful feature. Because `higher-kinded-j` provides an HKT abstraction (`Kind<F, A>`) and type classes like `Functor` and `Applicative`, the optics can perform *effectful* modifications. The `modifyF` method is generic over an `Applicative` effect `F`. This means you can perform an update within the context of any data type that has an `Applicative` instance:
   * Want to perform an update that might fail? Use `Optional` or `Either` as your `F`.
   * Want to perform an asynchronous update? Use `CompletableFuture` as your `F`.
   * Want to accumulate validation errors? Use `Validated` as your `F`.
3. **Profunctor Adaptability**: Every optic is fundamentally a profunctor, meaning it can be adapted to work with different data types and structures. This provides incredible flexibility for integrating with external systems, handling legacy data formats, and working with strongly-typed wrappers.

## Common Patterns

### When to Use `with*` Helpers vs Manual Lenses

* **Use `with*` helpers** for simple, top-level field updates
* **Use composed lenses** for deep updates or when you need to reuse the path
* **Use manual lens creation** for computed properties or complex transformations

### Decision Guide

* **Need to focus on a required field?** → **Lens**
* **Need to work with optional variants?** → **Prism**
* **Need to convert between equivalent types?** → **Iso**
* **Need to operate on collections?** → **Traversal**
* **Need to adapt existing optics?** → **Profunctor operations**

## Common Pitfalls

**❌ Don't do this:**

java

```java
// Calling get() multiple times is inefficient
var street = employeeToStreet.get(employee);
var newEmployee = employeeToStreet.set(street.toUpperCase(), employee);
```

**✅ Do this instead:**

java

```java
// Use modify() for transformations
var newEmployee = employeeToStreet.modify(String::toUpperCase, employee);
```

This level of abstraction allows you to write highly reusable and testable business logic that is completely decoupled from the details of state management, asynchrony, or error handling—a core benefit of functional programming brought to Java by the foundation `higher-kinded-j` provides.

---

**Next:**[Lenses: Working with Product Types](lenses.md)



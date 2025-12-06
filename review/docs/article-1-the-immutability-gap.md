# The Immutability Gap: Why Java Records Need Optics

*Part 1 of the Functional Optics for Modern Java series*

Modern Java has embraced immutability. Records give us concise, immutable data carriers. Pattern matching lets us elegantly destructure nested structures. Sealed interfaces enable exhaustive type hierarchies. Yet despite these advances, one fundamental operation remains surprisingly painful: updating a value deep within an immutable structure.

This article introduces *optics*, a family of composable abstractions that complete the immutability story. If pattern matching is how we *read* nested data, optics are how we *write* it.

---

## The Promise of Modern Java

Java's evolution over the past few years has been remarkable. With records, we can define immutable data types in a single line:

```java
public record Address(String street, String city, String postcode) {}
```

No more boilerplate. No more mutable fields to worry about. The compiler generates `equals()`, `hashCode()`, and `toString()` for us. Records are final, their fields are final, and they encourage a data-oriented programming style that functional programmers have long advocated.

Pattern matching, introduced progressively from Java 16 onwards, lets us destructure these records elegantly:

```java
if (employee instanceof Employee(var id, var name, Address(var street, _, _))) {
    System.out.println(name + " lives on " + street);
}
```

We can reach into nested structures, extract what we need, and bind values to variables in a single expression. Combined with sealed interfaces, we get exhaustive switch expressions that the compiler can verify:

```java
sealed interface Shape permits Circle, Rectangle, Triangle {}

String describe(Shape shape) {
    return switch (shape) {
        case Circle(var r) -> "A circle with radius " + r;
        case Rectangle(var w, var h) -> "A " + w + " by " + h + " rectangle";
        case Triangle(var a, var b, var c) -> "A triangle";
    };
}
```

This is genuinely excellent. Modern Java has become a credible language for data-oriented programming, with immutability at its core.

But there's a problem.

---

## The Nested Update Problem

Reading nested immutable data is elegant. Writing it is not.

Consider a simple domain model for a company:

```java
public record Address(String street, String city, String postcode) {}
public record Employee(String id, String name, Address address) {}
public record Department(String name, Employee manager, List<Employee> staff) {}
public record Company(String name, Address headquarters, List<Department> departments) {}
```

Four straightforward records. Nothing complex. Now suppose we need to update the street address of the Engineering department's manager. In a mutable world, this would be trivial:

```java
company.getDepartment("Engineering").getManager().getAddress().setStreet("100 New Street");
```

One line. Done. But our records are immutable: there are no setters. Instead, we must reconstruct every record in the path from root to leaf:

```java
public static Company updateManagerStreet(Company company, String deptName, String newStreet) {
    List<Department> updatedDepts = new ArrayList<>();

    for (Department dept : company.departments()) {
        if (dept.name().equals(deptName)) {
            Employee manager = dept.manager();
            Address oldAddress = manager.address();

            // Rebuild address with new street
            Address newAddress = new Address(
                newStreet,
                oldAddress.city(),
                oldAddress.postcode()
            );

            // Rebuild employee with new address
            Employee newManager = new Employee(
                manager.id(),
                manager.name(),
                newAddress
            );

            // Rebuild department with new manager
            Department newDept = new Department(
                dept.name(),
                newManager,
                dept.staff()
            );

            updatedDepts.add(newDept);
        } else {
            updatedDepts.add(dept);
        }
    }

    return new Company(
        company.name(),
        company.headquarters(),
        List.copyOf(updatedDepts)
    );
}
```

Twenty-five lines of code to change a single string. Every record in the path must be manually reconstructed, copying all unchanged fields. This is the *copy constructor cascade*, an anti-pattern that plagues immutable codebases.

You might think: "Just add `withX()` methods to each record." Indeed, you could:

```java
public record Address(String street, String city, String postcode) {
    public Address withStreet(String street) {
        return new Address(street, this.city, this.postcode);
    }
}
```

This helps somewhat, but it doesn't compose. You still need to thread the updated value back through every layer:

```java
var newAddress = manager.address().withStreet("100 New Street");
var newManager = manager.withAddress(newAddress);
var newDept = dept.withManager(newManager);
// ... and so on
```

The ceremony remains. The boilerplate persists. And the potential for error (accidentally copying the wrong field, forgetting to update an intermediate layer) grows with each level of nesting.

---

## Pattern Matching: Half the Solution

Here's the insight that motivated this article: pattern matching solves *reading* nested data, but provides no help for *writing*.

Consider the asymmetry. To read an employee's street, we can write:

```java
if (employee instanceof Employee(_, _, Address(var street, _, _))) {
    return street;
}
```

Pattern matching lets us drill down through layers, ignoring fields we don't care about, and extract exactly what we need. It's declarative, composable, and elegant.

But to write a new street? We're back to the imperative copy-constructor cascade. There's no "pattern setting" in Java. We cannot write:

```java
employee with { address.street = "100 New Street" }  // Nested updates: not supported
```

### A Note on JEP 468: Derived Record Creation

Java is making progress here. [JEP 468](https://openjdk.org/jeps/468) introduces derived record creation, a `with` expression for records. Currently a Candidate JEP (preview in JDK 23), it allows:

```java
Address updated = oldAddress with { street = "100 New Street"; };
```

This is genuinely useful. Instead of manually copying every field, you specify only what changes. The compiler handles the rest.

However, JEP 468 solves *single-level* updates, not *nested* ones. You cannot write:

```java
employee with { address.street = "100 New Street" }  // Not supported by JEP 468
```

To update a nested field, you must chain `with` expressions at each level:

```java
Employee updated = employee with {
    address = address with { street = "100 New Street"; };
};
```

Better than the full copy-constructor cascade, certainly. But you still manually thread updates through each layer. The ceremony shrinks but doesn't disappear. As nesting deepens (a company containing departments containing employees containing addresses), even chained `with` expressions become unwieldy.

JEP 468 is a welcome addition, but it addresses syntax, not composability. Optics provide something fundamentally different: reusable, composable access paths that can be defined once and applied anywhere.

### The Wider Landscape

Other languages have recognised this gap. Haskell has lenses. Scala has Monocle. F# has property access expressions. C# has `with` expressions for records (similar to JEP 468). What distinguishes optics is *composition*: the ability to combine small, focused accessors into larger ones that handle arbitrary depth automatically.

This asymmetry isn't just inconvenient; it actively discourages immutability. Developers facing the copy-constructor cascade often reach for mutability instead. "Just make the fields non-final," they say. "It's simpler." And in the short term, it is. But mutability brings its own problems: thread safety issues, defensive copying, spooky action at a distance when an object you thought you owned gets modified by code you didn't control.

The promise of modern Java (clean, immutable, data-oriented code) remains half-fulfilled. Pattern matching gave us elegant reading. Now we need elegant writing.

[Higher-Kinded-J](https://github.com/higher-kinded-j/higher-kinded-j) is a new library that brings the full power of optics to Java. Throughout this series, we'll use it to demonstrate how these patterns work in practice, with annotation-driven generation that eliminates boilerplate while preserving type safety.

**Pattern matching is half the puzzle; optics complete it.**

---

## Optics: A New Mental Model

An optic is a first-class representation of an access path into a data structure. Think of it as a reified getter-and-setter pair that can be composed, stored, and passed around.

The key insight is that access paths compose. If you have a way to focus on an employee's address, and a way to focus on an address's street, you can combine them to focus on an employee's street. This composition is the heart of optics.

Consider an analogy: XPath for objects. In XPath, you might write `/company/departments/manager/address/street` to navigate to a specific element. Optics provide similar navigation, but:

- They're type-safe: the compiler ensures your path is valid
- They support both reading *and* writing
- They compose with standard function composition

The simplest optic is a *lens*. A lens focuses on exactly one value within a larger structure. Given a lens from `Employee` to `Address`, you can:

1. **Get** the address from any employee
2. **Set** a new address, returning a new employee with everything else unchanged
3. **Modify** the address using a function, returning a new employee

Here's what a lens looks like conceptually:

```java
public record Lens<S, A>(
    Function<S, A> get,
    BiFunction<A, S, S> set
) {
    public S modify(Function<A, A> f, S whole) {
        return set.apply(f.apply(get.apply(whole)), whole);
    }
}
```

Two functions: one to extract, one to replace. The `modify` method combines them: extract the value, transform it, put it back.

The magic happens when you compose lenses:

```java
public <B> Lens<S, B> andThen(Lens<A, B> other) {
    return Lens.of(
        s -> other.get(this.get(s)),
        (b, s) -> this.set(other.set(b, this.get(s)), s)
    );
}
```

Given a lens from `Employee` to `Address` and a lens from `Address` to `String` (the street), `andThen` produces a lens from `Employee` to `String`. The composed lens automatically handles the intermediate reconstruction, eliminating the manual copy-constructor cascade.

Optics have a rich history. They emerged from the Haskell community in the early 2010s, with Edward Kmett's `lens` library becoming the definitive implementation. The ideas spread to Scala (Monocle), PureScript, and other functional languages. The theoretical foundations connect to category theory, though you needn't understand the theory to use optics effectively.

For Java developers, the practical takeaway is this: optics let you treat deeply nested immutable updates as simple, composable operations. The same twenty-five-line method becomes a single expression.

---

## The Optics Family

Lenses are just one member of a family of optics. Each type handles a different kind of focus:

### Lens: Focus on Exactly One (Has-A)

A lens focuses on exactly one value that is guaranteed to exist. It's the optic for "has-a" relationships:

- An `Employee` *has an* `Address`
- An `Address` *has a* `street`
- A `Department` *has a* `manager`

Lenses always succeed: you can always get the focused value, and you can always set a new one.

### Prism: Focus on One Variant (Is-A)

A prism focuses on one variant of a sum type. It's the optic for "is-a" relationships:

```java
sealed interface Shape permits Circle, Rectangle {}
record Circle(double radius) implements Shape {}
record Rectangle(double width, double height) implements Shape {}
```

A prism for `Circle` can:
- **Get** the `Circle` from a `Shape`, if it is one (returning `Optional`)
- **Build** a `Shape` from a `Circle` (always succeeds)

Prisms handle the uncertainty of sum types. Not every `Shape` is a `Circle`, so `get` might fail. But every `Circle` is a `Shape`, so `build` always works.

### Traversal: Focus on Many (Has-Many)

A traversal focuses on zero or more values simultaneously. It's the optic for collections:

- A `Department` *has many* `Employee`s in its staff list
- A `Company` *has many* `Department`s

Traversals let you modify all focused values at once:

```java
// Update the street for ALL staff members
Traversal<Department, String> allStaffStreets = ...;
Department updated = allStaffStreets.modify(s -> s.toUpperCase(), dept);
```

Every staff member's street is now uppercase. The traversal handled the iteration internally.

### The Composition Table

Optics compose, but the result type depends on what you're composing:

| First | Second | Result |
|-------|--------|--------|
| Lens | Lens | Lens |
| Lens | Prism | Affine |
| Lens | Affine | Affine |
| Lens | Traversal | Traversal |
| Prism | Lens | Affine |
| Prism | Prism | Prism |
| Prism | Affine | Affine |
| Prism | Traversal | Traversal |
| Affine | Lens | Affine |
| Affine | Prism | Affine |
| Affine | Affine | Affine |
| Affine | Traversal | Traversal |
| Traversal | Lens | Traversal |
| Traversal | Prism | Traversal |
| Traversal | Affine | Traversal |
| Traversal | Traversal | Traversal |

The intuition: composing optics that might fail to focus yields an optic that reflects that uncertainty.

### Affine: Focus on Zero or One

Between Prism and Lens sits another useful optic: the *Affine* (sometimes called "Optional"). An Affine focuses on at most one value that might not exist:

- A lens composed with a prism yields an Affine
- Accessing an optional field uses an Affine
- Looking up a key in a map uses an Affine

Higher-Kinded-J provides full Affine support, completing the optics hierarchy.

### When to Use Each

- **Lens**: Navigate to a field that always exists (exactly one)
- **Prism**: Navigate to a variant that might or might not apply (zero or one, with construction)
- **Affine**: Navigate to a value that might not exist (zero or one)
- **Traversal**: Navigate to multiple elements in a collection (zero to many)

In practice, you'll compose all four. Navigating to "the radius of every circle in a list of shapes" requires a traversal (for the list), a prism (for circles), and a lens (for the radius). Accessing an optional configuration value uses an Affine.

---

## Quick Win: Optics in 60 Seconds

Before diving deeper into theory, let's see the payoff. Here's the twenty-five-line method from earlier:

```java
// Manual approach: ~25 lines
public static Company updateManagerStreet(Company company, String deptName, String newStreet) {
    List<Department> updatedDepts = new ArrayList<>();
    for (Department dept : company.departments()) {
        if (dept.name().equals(deptName)) {
            Employee manager = dept.manager();
            Address oldAddress = manager.address();
            Address newAddress = new Address(newStreet, oldAddress.city(), oldAddress.postcode());
            Employee newManager = new Employee(manager.id(), manager.name(), newAddress);
            Department newDept = new Department(dept.name(), newManager, dept.staff());
            updatedDepts.add(newDept);
        } else {
            updatedDepts.add(dept);
        }
    }
    return new Company(company.name(), company.headquarters(), List.copyOf(updatedDepts));
}
```

And here's the same operation with optics:

```java
// Optics approach: 1 line
private static final Lens<Employee, String> employeeStreet =
    Employee.Lenses.address().andThen(Address.Lenses.street());

private static final Lens<Department, String> managerStreet =
    Department.Lenses.manager().andThen(employeeStreet);

public static Department updateManagerStreet(Department dept, String newStreet) {
    return managerStreet.set(newStreet, dept);
}
```

Define the path once. Use it anywhere. The lens composition handles all the intermediate reconstruction automatically.

Want to update *all* employee streets in a department? With manual code, you'd need nested loops. With optics:

```java
public static Department updateAllStreets(Department dept, String newStreet) {
    Department withManager = managerStreet.set(newStreet, dept);
    return allStaffStreets().modify(_ -> newStreet, withManager);
}
```

Two lines. No loops. No manual reconstruction. The traversal handles the collection, the lenses handle the path.

**If this intrigues you, read on for the how and why.**

---

## First Taste: A Simple Lens

Let's build a working lens from scratch. We'll start with the core abstraction:

```java
public record Lens<S, A>(
    Function<S, A> get,
    BiFunction<A, S, S> set
) {
    public static <S, A> Lens<S, A> of(Function<S, A> getter, BiFunction<A, S, S> setter) {
        return new Lens<>(getter, setter);
    }

    public A get(S whole) {
        return get.apply(whole);
    }

    public S set(A newValue, S whole) {
        return set.apply(newValue, whole);
    }

    public S modify(Function<A, A> f, S whole) {
        return set(f.apply(get(whole)), whole);
    }

    public <B> Lens<S, B> andThen(Lens<A, B> other) {
        return Lens.of(
            s -> other.get(this.get(s)),
            (b, s) -> this.set(other.set(b, this.get(s)), s)
        );
    }
}
```

Now we can define lenses for our records:

```java
public record Address(String street, String city, String postcode) {

    public static final class Lenses {
        public static Lens<Address, String> street() {
            return Lens.of(
                Address::street,
                (newStreet, addr) -> new Address(newStreet, addr.city(), addr.postcode())
            );
        }
    }
}
```

The pattern is mechanical: the getter is the record accessor, the setter creates a new record with one field changed. In production code with higher-kinded-j, the `@GenerateLenses` annotation generates these automatically.

Composition is where the magic happens:

```java
Lens<Employee, String> employeeStreet =
    Employee.Lenses.address().andThen(Address.Lenses.street());

// Get the street
String street = employeeStreet.get(employee);

// Set a new street (returns a new Employee)
Employee updated = employeeStreet.set("100 New Street", employee);

// Modify the street (returns a new Employee)
Employee uppercased = employeeStreet.modify(String::toUpperCase, employee);
```

One composed lens replaces what would otherwise be multiple levels of manual reconstruction. The "aha" moment: deep updates become shallow expressions.

---

## What's Coming

This article introduced the problem (the immutability gap) and sketched the solution (optics). We've seen:

- Why nested immutable updates are painful in Java
- How pattern matching solves reading but not writing
- The basic optics family: lens, prism, traversal
- A quick win showing the dramatic code reduction

### Introducing higher-kinded-j

Throughout this series, we'll use [Higher-Kinded-J](https://github.com/higher-kinded-j/higher-kinded-j), a library that brings functional programming abstractions to modern Java. It provides:

- **Production-ready optics**: Lens, Prism, Traversal, and more, with proper composition and laws
- **Annotation-driven generation**: The `@GenerateLenses` annotation eliminates boilerplate by generating lens accessors for your records automatically
- **Higher-kinded types**: The foundation that makes optics (and other abstractions like functors and monads) possible in Java's type system
- **Zero runtime overhead**: All the abstraction happens at compile time

The library fills a gap in the Java ecosystem. While Scala has Monocle and Haskell has the `lens` library, Java has lacked a mature, idiomatic optics implementation. higher-kinded-j brings these patterns to Java without sacrificing type safety or requiring exotic language features.

You don't need to understand higher-kinded types to use the library effectively. The optics API is intuitive: compose lenses with `andThen`, get values with `get`, set values with `set`. The underlying type machinery stays out of your way.

### The Road Ahead

In Article 2, we'll dive deeper into optics fundamentals:
- Lens laws and why they matter for correctness
- Prisms for sum types and sealed interfaces
- Affines for optional values
- Traversals for collections and bulk operations
- Setting up higher-kinded-j for annotation-driven lens generation

From Article 3 onwards, we'll build an expression language interpreter, the canonical optics showcase, demonstrating how these abstractions shine for AST manipulation, tree transformations, and effectful operations.

By the end of this series, you'll never want to update nested data manually again.

---

## Further Reading

### Data-Oriented Programming in Java

- **Chris Kiehl, [*Data-Oriented Programming in Java*](https://www.manning.com/books/data-oriented-programming-in-java)** (Manning): A practical guide to DOP in modern Java, covering records, sealed types, and functional patterns.

- **Brian Goetz, ["Data-Oriented Programming in Java"](https://www.infoq.com/articles/data-oriented-programming-java/)** (InfoQ, 2022): Goetz's foundational article explaining the philosophy behind Java's DOP features.

- **[JEP 395: Records](https://openjdk.org/jeps/395)**, **[JEP 409: Sealed Classes](https://openjdk.org/jeps/409)**, **[JEP 441: Pattern Matching for switch](https://openjdk.org/jeps/441)**: The JDK Enhancement Proposals that brought DOP to Java, essential for understanding the design rationale.

- **[JEP 468: Derived Record Creation (Preview)](https://openjdk.org/jeps/468)**: The forthcoming `with` expression for records, addressing single-level updates (though not nested ones).

### The Broader DOP Philosophy

- **Eric Normand, *Grokking Simplicity*** (Manning, 2021): An accessible introduction to functional thinking and data-oriented design from the Clojure perspective.

- **Rich Hickey, ["The Value of Values"](https://www.infoq.com/presentations/Value-Values/)** (Strange Loop, 2012): The influential talk that shaped modern thinking about immutable data, from Clojure's creator.

### Higher-Kinded-J

- **[Higher-Kinded-J GitHub Repository](https://github.com/higher-kinded-j/higher-kinded-j)**: Source code, documentation, and examples.

- **[Optics Module Documentation](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-core/src/main/java/org/higherkindedj/optics)**: API reference for lenses, prisms, and traversals.

---

*Next: [Article 2: Optics Fundamentals](article-2-optics-fundamentals.md)*

# Optics Fundamentals: Lenses, Prisms, and Traversals in Practice

*Part 2 of the Functional Optics for Modern Java series*

In Article 1, we identified the immutability gap: modern Java excels at reading nested data through pattern matching, but provides no elegant solution for writing. We introduced optics as the missing piece: composable abstractions that treat access paths as first-class values.

Now it's time to get practical. This article dives deep into the three core optic types: lenses for product types, prisms for sum types, and traversals for collections. By the end, you'll understand not just how to use each, but when and why.

---

## Setting Up Higher-Kinded-J

Before we explore optics in depth, let's configure our project to use Higher-Kinded-J's annotation-driven generation.  
You will want to grab latest Java 25 and if you are using Gradle 9.2.1+.

### Gradle Configuration

```kotlin
plugins {
    java
}

repositories {
    mavenCentral()
}
// This is the current version of HKJ
val hkjVersion = "0.3.0" 

dependencies {
    // Core library with optics
    implementation("io.github.higher-kinded-j:hkj-core:$hkjVersion")

    // Annotation processors for lens/prism generation
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:$hkjVersion")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
```

### Maven Configuration

```xml

<properties>
    <hkj.version>0.3.0</hkj.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.higher-kinded-j</groupId>
        <artifactId>hkj-core</artifactId>
        <version>${hkj.version}</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <path>
                        <groupId>io.github.higher-kinded-j</groupId>
                        <artifactId>hkj-processor-plugins</artifactId>
                        <version>${hkj.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

With the dependencies in place, we're ready to explore each optic type in depth.

### Running the Examples



---

## Article Code

**[All code examples in this article have runnable demos in the companion code:](https://github.com/higher-kinded-j/expression-language-example)**

- **[LensDemo](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/demo/LensDemo.java)**: Basic lens operations and composition
- **[PrismDemo](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/demo/PrismDemo.java)**: Prism operations and type-safe downcasting
- **[TraversalDemo](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/demo/TraversalDemo.java)**: List traversals and filtering
- **[CompositionDemo](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/demo/CompositionDemo.java)**: Deep path composition for nested updates
- **[ExpressionPreviewDemo](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/demo/ExpressionPreviewDemo.java)**: Preview of the expression language from Article 3

The domain classes use Higher-Kinded-J's annotation-driven generation, defined in [`org.higherkindedj.article2.domain`](https://github.com/higher-kinded-j/expression-language-example/src/main/java/org/higherkindedj/article2/domain/).

---

## Lenses: The Foundation

A lens focuses on exactly one value within a larger structure. It represents a "has-a" relationship: an `Employee` *has an* `Address`; an `Address` *has a* `street`. Lenses always succeed; the focused value is guaranteed to exist.

### Generating Lenses

The `@GenerateLenses` annotation instructs Higher-Kinded-J to generate lens accessors for each record component:

```java
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
public record Address(String street, String city, String postcode) {}

@GenerateLenses
public record Employee(String id, String name, Address address) {}

@GenerateLenses
public record Department(String name, Employee manager, List<Employee> staff) {}
```

The annotation processor generates a companion class with lens factories:

```java
// Generated: AddressLenses.java
public final class AddressLenses {
    public static Lens<Address, String> street() { ... }
    public static Lens<Address, String> city() { ... }
    public static Lens<Address, String> postcode() { ... }
}

// Generated: EmployeeLenses.java
public final class EmployeeLenses {
    public static Lens<Employee, String> id() { ... }
    public static Lens<Employee, String> name() { ... }
    public static Lens<Employee, Address> address() { ... }
}
```

### Using Lenses

Each lens provides three core operations:

```java
Lens<Address, String> streetLens = AddressLenses.street();

// Get: extract the focused value
String street = streetLens.get(address);

// Set: return a new structure with the focused value replaced
Address updated = streetLens.set("100 New Street", address);

// Modify: apply a function to the focused value
Address uppercased = streetLens.modify(String::toUpperCase, address);
```

The `modify` operation is particularly powerful: it combines get and set in a single traversal, ensuring the transformation is applied consistently.

### Lens Composition

The real power emerges when you compose lenses. The `andThen` method chains lenses to reach deeper into nested structures:

```
┌──────────────┐      ┌─────────────┐      ┌────────────┐
│   Employee   │─────▶│   Address   │─────▶│   String   │
│              │      │             │      │  (street)  │
└──────────────┘      └─────────────┘      └────────────┘
       │                    │                    │
       │    addressLens     │    streetLens      │
       │                    │                    │
       └────────────────────┴────────────────────┘
                employeeStreet (composed)
```

```java
// Compose: Employee → Address → String
Lens<Employee, String> employeeStreet =
    EmployeeLenses.address().andThen(AddressLenses.street());

// Now we can get/set/modify the street directly on an Employee
String street = employeeStreet.get(employee);
Employee updated = employeeStreet.set("200 Oak Avenue", employee);
Employee transformed = employeeStreet.modify(s -> s + " (verified)", employee);
```

Each composed lens handles all the intermediate reconstruction automatically. That twenty-five-line copy-constructor cascade from Article 1? It's now implicit in the lens composition.

### Lens Laws

Well-behaved lenses must satisfy three laws that ensure predictable behaviour:

1. **Get-Set**: If you get a value and then set it back, the structure is unchanged.
   ```java
   lens.set(lens.get(s), s) == s
   ```

2. **Set-Get**: If you set a value, getting it returns what you set.
   ```java
   lens.get(lens.set(a, s)) == a
   ```

3. **Set-Set**: Setting twice is the same as setting once with the final value.
   ```java
   lens.set(a2, lens.set(a1, s)) == lens.set(a2, s)
   ```

These laws aren't just theoretical; they form the guarantee that lenses behave like mathematical getters and setters. The annotation processor generates lenses that will satisfy these laws automatically.

### Pattern: Lens as Structural Path

Think of a lens as a "structural pointer" for your objects. Just as `/employee/address/street` navigates a JSON document, `employeeAddress.andThen(addressStreet)` navigates a Java object graph. The difference: it's type-safe, and it works bidirectionally.

---

## Prisms: Sum Type Access

Where lenses focus on fields that always exist (product types), prisms focus on variants that might exist (sum types). A prism represents an "is-a" relationship: a `Shape` *might be a* `Circle`; an `Expr` *might be a* `Binary`.

### The Optional Nature of Prisms

Consider a sealed interface:

```java
public sealed interface Shape permits Circle, Rectangle, Triangle {}

@GenerateLenses
public record Circle(double radius) implements Shape {}

@GenerateLenses
public record Rectangle(double width, double height) implements Shape {}

@GenerateLenses
public record Triangle(double a, double b, double c) implements Shape {}
```

Given a `Shape`, we don't know which variant it is. A prism for `Circle` must handle the possibility that the shape isn't a circle at all.

### Generating Prisms

The `@GeneratePrisms` annotation on a sealed interface generates prisms for each permitted subtype:

```java
import org.higherkindedj.optics.annotations.GeneratePrisms;

@GeneratePrisms
public sealed interface Shape permits Circle, Rectangle, Triangle {}
```

This generates:

```java
// Generated: ShapePrisms.java
public final class ShapePrisms {
    public static Prism<Shape, Circle> circle() { ... }
    public static Prism<Shape, Rectangle> rectangle() { ... }
    public static Prism<Shape, Triangle> triangle() { ... }
}
```

### Using Prisms

Prisms provide different operations than lenses, reflecting their optional nature:

```
                          match (might fail)
    ┌───────────┐     ─────────────────────▶     ┌───────────┐
    │   Shape   │        Optional<Circle>        │  Circle   │
    │           │     ◀─────────────────────     │           │
    └───────────┘        build (always works)    └───────────┘
         │                                             │
         ├─── Circle ──┐                               │
         ├─── Rectangle│  (only one variant matches)   │
         └─── Triangle─┘                               │
```

```java
Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

// getOptional: extract the variant if it matches
Optional<Circle> maybeCircle = circlePrism.getOptional(shape);

// build: construct the sum type from the variant (always succeeds)
Shape shape = circlePrism.build(new Circle(5.0));

// matches: check if the prism matches
boolean isCircle = circlePrism.matches(shape);

// modify: transform if it matches, leave unchanged otherwise
Shape doubled = circlePrism.modify(c -> new Circle(c.radius() * 2), shape);
```

The `modify` on a prism is particularly elegant: it applies the transformation only if the prism matches, otherwise returning the original value unchanged. No explicit pattern matching is required.

### Composing Prisms with Lenses

Prisms compose with lenses to reach into variant-specific fields:

```java
// Prism: Shape → Circle, then Lens: Circle → radius
Prism<Shape, Circle> circlePrism = ShapePrisms.circle();
Lens<Circle, Double> radiusLens = CircleLenses.radius();

// Compose into an Affine (zero or one focus)
Affine<Shape, Double> shapeRadius =
    circlePrism.andThen(radiusLens);

// Get the radius if it's a circle
Optional<Double> radius = shapeRadius.getOptional(shape);

// Double the radius if it's a circle
Shape modified = shapeRadius.modify(r -> r * 2, shape);
```

Notice the type: composing a `Prism` with a `Lens` yields an `Affine`. This reflects the reality: we might find zero elements (if it's not a circle) or one element (if it is). The affine handles both cases elegantly.

### Pattern: Type-Safe Downcasting

Prisms provide type-safe downcasting without the need for explicit `instanceof` checks:

```java
// Traditional approach
if (shape instanceof Circle circle) {
    return new Circle(circle.radius() * 2);
}
return shape;

// Prism approach
return circlePrism.modify(c -> new Circle(c.radius() * 2), shape);
```

The prism version is more composable. You can store it, pass it around, and combine it with other optics, something you can't do with an `instanceof` expression.

---

## Traversals: Bulk Operations

Traversals generalise lenses to focus on zero or more values simultaneously. They're the optic for "has-many" relationships: a `Department` *has many* `Employee`s; an `Order` *has many* `LineItem`s.

### Basic List Traversal

Higher-Kinded-J provides a built-in traversal for lists:

```java
Traversal<List<String>, String> listTraversal = Traversals.forList();

List<String> names = List.of("alice", "bob", "charlie");

// Modify all elements
List<String> uppercased = Traversals.modify(listTraversal, String::toUpperCase, names);
// ["ALICE", "BOB", "CHARLIE"]

// Get all elements (as a list)
List<String> all = Traversals.getAll(listTraversal, names);
// ["alice", "bob", "charlie"]
```

### Composing Traversals for Nested Collections

We can go even further composing traversals with lenses to reach into nested structures:

```java
// Lens: Department → List<Employee>
Lens<Department, List<Employee>> staffLens = DepartmentLenses.staff();

// Traversal: List<Employee> → Employee
Traversal<List<Employee>, Employee> eachEmployee = Traversals.forList();

// Lens: Employee → Address
Lens<Employee, Address> addressLens = EmployeeLenses.address();

// Lens: Address → String
Lens<Address, String> streetLens = AddressLenses.street();

// Compose them all: Department → each employee's street
// Note: we convert lenses to traversals with asTraversal() before composing
Traversal<Department, String> allStaffStreets =
    staffLens.asTraversal()
        .andThen(eachEmployee)
        .andThen(addressLens.asTraversal())
        .andThen(streetLens.asTraversal());

// Update every staff member's street
Department updated = Traversals.modify(allStaffStreets, s -> s + " (relocated)", dept);

// Collect all streets
List<String> streets = Traversals.getAll(allStaffStreets, dept);
```

One composed traversal replaces what would otherwise be nested loops with manual reconstruction at each level.

### Filtered Traversals

Sometimes you want to focus on only a subset of elements. The `filtered` method creates a traversal that only matches elements satisfying a predicate:

```java
// Only employees in Newcastle
Traversal<List<Employee>, Employee> newcastleStaff =
    Traversals.<Employee>forList()
        .filtered(e -> e.address().city().equals("Newcastle"));

// Give Newcastle staff a pay rise
List<Employee> updated = Traversals.modify(
    newcastleStaff,
    e -> new Employee(e.id(), e.name(), e.address(), e.salary().multiply(new BigDecimal("1.1"))),
    employees
);
```

Filters compose naturally with other optics, enabling precise targeting deep within structures.

### Aggregation with Folds

Traversals support folding, which aggregates all focused values into a single result. If you've used Java's `Stream.reduce()`, you already understand the core idea.

**What is a Fold?** A fold combines multiple values into one. In Java, we use this pattern all the time:

```java
// This is a fold using Stream API
int sum = numbers.stream().reduce(0, Integer::sum);
```

Optics bring this same power to nested structures. The `Traversals` utility class provides folding operations:

```java
// Collect all focused values into a list
List<Double> allSalaries = Traversals.getAll(allStaffSalaries, department);

// Then use standard Java to aggregate
double totalSalary = allSalaries.stream()
    .reduce(0.0, Double::sum);

// Or count elements
long count = allSalaries.size();

// Or collect unique values
Set<String> uniqueCities = Traversals.getAll(allStaffCities, department)
    .stream()
    .collect(Collectors.toSet());
```

**Understanding Monoids (the Java way):** The term "monoid" might sound unfamiliar, but you use them daily:

- **Addition**: combine with `+`, start with `0`
- **Multiplication**: combine with `*`, start with `1`
- **String concatenation**: combine with `+`, start with `""`
- **List concatenation**: combine with `addAll`, start with an empty list
- **Set union**: combine with `union`, start with an empty set

A monoid is simply: (1) a way to combine two values, and (2) an "empty" starting value. That's exactly what `Stream.reduce(identity, combiner)` expects! When the documentation mentions monoids, think "something I can reduce over."

---

## Composition Patterns

In Article 1 we introduced the composition table showing how optics combine. The key insight bears repeating: composing with something "weaker" (that might not find anything, or might find many things) yields a `Traversal`.

In Higher-Kinded-J, we use `asTraversal()` to convert lenses and prisms before composing them with `andThen()`. This uniform API means you don't need to remember special composition methods for each combination.

**Why Traversal?** You might wonder why `Prism + Lens` doesn't yield some special "zero-or-one" type. In practice, a `Traversal` that focuses on at most one element works identically, and the simpler type hierarchy means fewer concepts to learn. The `Traversals.getAll()` method returns a list that will have 0, 1, or many elements depending on the composed optics.

### Building Deep Paths

In practice, you'll build paths incrementally:

```java
// Company → departments (lens to list)
// → each department (traversal over list)
// → manager (lens to employee)
// → address (lens to address)
// → city (lens to string)

Traversal<Company, String> allManagerCities =
    CompanyLenses.departments().asTraversal()
        .andThen(Traversals.forList())
        .andThen(DepartmentLenses.manager().asTraversal())
        .andThen(EmployeeLenses.address().asTraversal())
        .andThen(AddressLenses.city().asTraversal());

// Relocate all managers to Manchester
Company relocated = Traversals.modify(allManagerCities, _ -> "Manchester", company);
```

### Real-World Example: Updating Nested Orders

Consider an e-commerce domain:

```java
@GenerateLenses
public record Customer(String id, String name, List<Order> orders) {}

@GenerateLenses
public record Order(String orderId, List<LineItem> items, OrderStatus status) {}

@GenerateLenses
public record LineItem(String productId, int quantity, BigDecimal price) {}
```

To apply a 10% discount to all items across all orders for a customer:

```java
// Manual approach: ~20 lines of nested loops and reconstruction

// Optics approach: define the path once
Traversal<Customer, BigDecimal> allItemPrices =
    CustomerLenses.orders().asTraversal()
        .andThen(Traversals.forList())
        .andThen(OrderLenses.items().asTraversal())
        .andThen(Traversals.forList())
        .andThen(LineItemLenses.price().asTraversal());

// Apply discount
Customer discounted = Traversals.modify(
    allItemPrices,
    price -> price.multiply(new BigDecimal("0.90")),
    customer
);
```

The path is declarative and reusable. Need to calculate the total value? Use the same path with `Traversals.getAll()` and standard Java streams.

---

## Effect-Polymorphic Operations: A Preview

So far, our optics have performed pure transformations. But real applications need effects: validation that might fail, state that accumulates, logging for debugging.

Higher-Kinded-J's optics support *effect-polymorphic* operations through `modifyF`:

```java
// Pure modification
Employee updated = streetLens.modify(String::toUpperCase, employee);

// Effectful modification with Optional (might fail)
Optional<Employee> validated = streetLens.modifyF(
    OptionalKind.INSTANCE,
    street -> street.isBlank() ? Optional.empty() : Optional.of(street.trim()),
    employee
);

// Effectful modification with Either (might fail with error)
Either<ValidationError, Employee> checked = streetLens.modifyF(
    EitherKind.INSTANCE,
    street -> validateStreet(street),
    employee
);
```

The same optic (the same composed path) works with any effect. This is the power of higher-kinded types: abstracting over the computational context.

We'll explore `modifyF` fully in Article 5, where we'll use it for type-checking with error accumulation and interpretation with state. For now, know that the optics you're learning aren't limited to pure transformations.

### A Preview: The [Focus DSL](https://higher-kinded-j.github.io/v0.3.0/optics/ch4_intro.html)

The optics we're learning in this article form the foundation. But Higher-Kinded-J also provides something even more ergonomic: the **Focus DSL**. With `@GenerateFocus` annotations, you can write fluent navigation chains like:

```java
// Instead of manually composing lenses:
Lens<Employee, String> streetLens =
    EmployeeLenses.address().andThen(AddressLenses.street());
String street = streetLens.get(employee);

// With Focus DSL:
String street = EmployeeFocus.address().street().get(employee);
```

The Focus DSL wraps optics in path types (`FocusPath`, `AffinePath`, `TraversalPath`) that enable fluent cross-type navigation. When navigators are enabled, you chain directly through nested types without explicit composition.

We'll introduce the Focus DSL properly in Article 3 and use it extensively from Article 4 onwards. For now, understanding the underlying optics gives you the conceptual foundation that makes the DSL's elegance possible.

```java 
MaybePath<String> maybeEmail = emailPath.toMaybePath(user)
    .map(String::toLowerCase)
    .filter(e -> e.contains("@"));

// Or use ValidationPath for comprehensive error checking
ValidationPath<List<Error>, String> validated = emailPath.toValidationPath(user)
    .via(email -> validateEmail(email));
```

The [Effect Path API](https://higher-kinded-j.github.io/v0.3.0/effect/ch_intro.html) becomes the primary focus in Article 5, where we use it for type checking with error accumulation and interpretation with state. The combination of Focus paths (for navigation) with Effect paths (for computation) gives you a complete toolkit for data-oriented programming.

---

## Introducing the Expression Language

Starting in the next Article 3, we'll build an expression language interpreter, the canonical showcase for optics. Here's a preview of the domain:

```java
@GeneratePrisms
public sealed interface Expr {
    @GenerateLenses record Literal(Object value) implements Expr {}
    @GenerateLenses record Variable(String name) implements Expr {}
    @GenerateLenses record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}
    @GenerateLenses record Conditional(Expr cond, Expr then_, Expr else_) implements Expr {}
}

public enum BinaryOp { ADD, SUB, MUL, DIV, EQ, LT, GT, AND, OR }
```

This domain showcases every optic type:

- **Lenses** for accessing expression fields (`Binary.left`, `Conditional.cond`)
- **Prisms** for matching expression variants (is this a `Literal`? a `Binary`?)
- **Traversals** for visiting all sub-expressions recursively

We'll implement:
- Variable renaming across an entire expression tree
- Constant folding (evaluating `1 + 2` to `3` at compile time)
- Dead code elimination (removing unreachable branches)
- A complete interpreter using stateful evaluation

The expression language is small enough to understand completely, yet it is rich enough to demonstrate every optics pattern you would need for real-world tree manipulation.

---

## Summary

This article covered the three fundamental optic types:

- **Lenses** focus on exactly one value (product types, "has-a")
- **Prisms** focus on one variant that might not match (sum types, "is-a")
- **Traversals** focus on zero or more values (collections, "has-many")

Key takeaways:

1. **Composition is the superpower**: Small, focused optics combine into powerful paths
2. **The type tells you what to expect**: Lens always succeeds; Prism might not; Traversal might find many
3. **Annotation-driven generation eliminates boilerplate**: `@GenerateLenses` and `@GeneratePrisms` do the mechanical work
4. **Effects come later**: The same optics work with pure transformations and effectful ones
5. **The Focus DSL awaits**: An even more ergonomic API builds on these foundations

### The Higher-Kinded-J Advantage

What makes Higher-Kinded-J elegant is how it brings these functional programming patterns to Java without sacrificing type safety or requiring language extensions. The annotation processor generates clean, idiomatic code that integrates seamlessly with Java's records and sealed interfaces. 
You get the full power of composable optics with the expressiveness of higher-kinded types, while the API remains approachable to Java developers unfamiliar with Haskell or Scala.

The library's design philosophy prioritises practicality: minimal boilerplate, compile-time verification of optic laws, and clear composition semantics. 
Rather than fighting Java's type system, Higher-Kinded-J works with it, using witness types and type-class patterns that feel natural once understood.

In Article 3, we'll apply these fundamentals to build the expression language AST, introducing the `@GenerateFocus` annotation alongside lenses and prisms. You'll see how the Focus DSL transforms optic composition from explicit method chains into fluent navigation, making real-world tree manipulation remarkably clean.

---

## Further Reading

### Optics Theory and History

- **Edward Kmett, [lens library](https://hackage.haskell.org/package/lens)**: The Haskell library that established modern optics. Dense but comprehensive; the README alone is an education.

- **Julien Truffaut, [Monocle](https://www.optics.dev/Monocle/)**: Scala's premier optics library, with excellent documentation that bridges theory and practice.

- **Simon Peyton Jones, ["Lenses: Compositional Data Access and Manipulation"](https://www.youtube.com/watch?v=k-QwBL9Dia0)** (Skills Matter, 2013): An accessible introduction to lens theory from one of functional programming's luminaries.

### Critical Perspectives

- **Alexis King, ["Optics are monoids"](https://www.tweag.io/blog/2022-05-05-optics-are-monoids/)** (Tweag, 2022): A deeper look at the mathematical structure underlying optics, useful for understanding why composition "just works".

- The optics learning curve is real. Some practitioners argue that pattern matching (as Java 25 provides) handles 80% of cases more readably. Optics shine for the remaining 20%: deep updates, reusable paths, and effect-polymorphic traversals.

### Higher-Kinded-J

- **[Higher-Kinded-J GitHub Repository](https://github.com/higher-kinded-j/higher-kinded-j)**: Source code, documentation, and examples.

- **[Annotation Processor Guide](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-processor)**: How `@GenerateLenses` and `@GeneratePrisms` work under the hood.

- **[Type-Class Instances](https://higher-kinded-j.github.io/v0.3.0/functional/ch_intro.html)**: The `Functor`, `Applicative`, and `Monad` abstractions that power effect-polymorphic operations.

- **[Focus DSL Guide](https://higher-kinded-j.github.io/v0.3.0/optics/ch4_intro.html)**: Fluent navigation with FocusPath, AffinePath, and TraversalPath.

- **[Effect Path API Guide](https://higher-kinded-j.github.io/v0.3.0/effect/ch_intro.html)**: Railway-style error handling with MaybePath, EitherPath, and ValidationPath.

---

*Next: [Article 3: Optics in Practice: An Expression Language AST](article-3-ast-basic-optics.md)*

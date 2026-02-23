# The Const Type: Constant Functors with Phantom Types

~~~admonish info title="What You'll Learn"
- Understanding phantom types and how Const ignores its second type parameter
- Using Const for efficient fold implementations and data extraction
- Leveraging Const with bifunctor operations to transform constant values
- Applying Const in lens and traversal patterns for compositional getters
- Real-world use cases in validation, accumulation, and data mining
- How Const relates to Scala's Const and van Laarhoven lenses
~~~

~~~admonish example title="See Example Code:"
[ConstExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java)
~~~

The `Const` type is a constant functor that holds a value of type `C` whilst treating `A` as a **phantom type parameter**: a type that exists only in the type signature but has no runtime representation. This seemingly simple property unlocks powerful patterns for accumulating values, implementing efficient folds, and building compositional getters in the style of van Laarhoven lenses.

~~~admonish note
New to phantom types? See the [Glossary](../glossary.md#phantom-type) for a detailed explanation with Java-focused examples, or continue reading for practical demonstrations.
~~~

---

## What is Const?

A `Const<C, A>` is a container that holds a single value of type `C`. The type parameter `A` is phantom: it influences the type signature for composition and type safety but doesn't correspond to any stored data. This asymmetry is the key to Const's utility.

```java
// Create a Const holding a String, with Integer as the phantom type
Const<String, Integer> stringConst = new Const<>("Hello");

// The constant value is always accessible
String value = stringConst.value(); // "Hello"

// Create a Const holding a count, with Person as the phantom type
Const<Integer, Person> countConst = new Const<>(42);
int count = countConst.value(); // 42
```

### Key Characteristics

* **Constant value**: Holds a value of type `C` that can be retrieved via `value()`
* **Phantom type**: The type parameter `A` exists only for type-level composition
* **Bifunctor instance**: Implements `Bifunctor<ConstKind2.Witness>` where:
  * `first(f, const)` transforms the constant value
  * `second(g, const)` changes only the phantom type, leaving the constant value unchanged
  * `bimap(f, g, const)` combines both transformations (but only `f` affects the constant)

---

## Core Components

**The Const Type**

```java
public record Const<C, A>(C value) {
  public <D> Const<D, A> mapFirst(Function<? super C, ? extends D> firstMapper);
  public <B> Const<C, B> mapSecond(Function<? super A, ? extends B> secondMapper);
  public <D, B> Const<D, B> bimap(
      Function<? super C, ? extends D> firstMapper,
      Function<? super A, ? extends B> secondMapper);
}
```

**The HKT Bridge for Const**

* **`ConstKind2<C, A>`**: The HKT marker interface extending `Kind2<ConstKind2.Witness, C, A>`
* **`ConstKind2.Witness`**: The phantom type witness for Const in the Kind2 system
* **`ConstKindHelper`**: Utility providing `widen2` and `narrow2` for Kind2 conversions

**Type Classes for Const**

* **`ConstBifunctor`**: The singleton bifunctor instance implementing `Bifunctor<ConstKind2.Witness>`

---

## The Phantom Type Property

The defining characteristic of `Const` is that mapping over the second type parameter has **no effect** on the constant value. This property is enforced both conceptually and at runtime.

~~~admonish example title="Example: Phantom Type Transformations"

[ConstExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java)

```java
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;

Bifunctor<ConstKind2.Witness> bifunctor = ConstBifunctor.INSTANCE;

// Start with a Const holding an integer count
Const<Integer, String> original = new Const<>(42);
System.out.println("Original value: " + original.value());
// Output: 42

// Use second() to change the phantom type from String to Double
Kind2<ConstKind2.Witness, Integer, Double> transformed =
    bifunctor.second(
        s -> s.length() * 2.0, // Function defines phantom type transformation
        CONST.widen2(original));

Const<Integer, Double> result = CONST.narrow2(transformed);
System.out.println("After second(): " + result.value());
// Output: 42 (UNCHANGED!)

// The phantom type changed (String -> Double), but the constant value stayed 42
```

**Note:** Whilst the mapper function in `second()` is never applied to actual data (since `A` is phantom), it is still validated and applied to `null` for exception propagation. This maintains consistency with bifunctor exception semantics.
~~~

---

## Const as a Bifunctor

`Const` naturally implements the `Bifunctor` type class, providing three fundamental operations:

### 1. `first()` - Transform the Constant Value

The `first` operation transforms the **constant value** from type `C` to type `D`, leaving the phantom type unchanged.

```java
Const<String, Integer> stringConst = new Const<>("hello");

// Transform the constant value from String to Integer
Kind2<ConstKind2.Witness, Integer, Integer> lengthConst =
    bifunctor.first(String::length, CONST.widen2(stringConst));

Const<Integer, Integer> result = CONST.narrow2(lengthConst);
System.out.println(result.value()); // Output: 5
```

### 2. `second()` - Transform Only the Phantom Type

The `second` operation changes the **phantom type** from `A` to `B` without affecting the constant value.

```java
Const<String, Integer> stringConst = new Const<>("constant");

// Change the phantom type from Integer to Boolean
Kind2<ConstKind2.Witness, String, Boolean> boolConst =
    bifunctor.second(i -> i > 10, CONST.widen2(stringConst));

Const<String, Boolean> result = CONST.narrow2(boolConst);
System.out.println(result.value()); // Output: "constant" (unchanged)
```

### 3. `bimap()` - Transform Both Simultaneously

The `bimap` operation combines both transformations, but remember: only the first function affects the constant value.

```java
Const<String, Integer> original = new Const<>("hello");

Kind2<ConstKind2.Witness, Integer, String> transformed =
    bifunctor.bimap(
        String::length,          // Transforms constant: "hello" -> 5
        i -> "Number: " + i,     // Phantom type transformation only
        CONST.widen2(original));

Const<Integer, String> result = CONST.narrow2(transformed);
System.out.println(result.value()); // Output: 5
```

---

## Use Case 1: Efficient Fold Implementations

One of the most practical applications of `Const` is implementing folds that accumulate a single value whilst traversing a data structure. The phantom type represents the "shape" being traversed, whilst the constant value accumulates the result.

~~~admonish example title="Example: Folding with Const"

[ConstExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java)

```java
// Count elements in a list using Const
List<String> items = List.of("apple", "banana", "cherry", "date");

Const<Integer, String> count = items.stream()
    .reduce(
        new Const<>(0),                                           // Initial count
        (acc, item) -> new Const<Integer, String>(acc.value() + 1), // Increment
        (c1, c2) -> new Const<>(c1.value() + c2.value()));        // Combine

System.out.println("Count: " + count.value());
// Output: 4

// Accumulate total length of all strings
Const<Integer, String> totalLength = items.stream()
    .reduce(
        new Const<>(0),
        (acc, item) -> new Const<Integer, String>(acc.value() + item.length()),
        (c1, c2) -> new Const<>(c1.value() + c2.value()));

System.out.println("Total length: " + totalLength.value());
// Output: 23
```

In this pattern, the phantom type (`String`) represents the type of elements we're folding over, whilst the constant value (`Integer`) accumulates the result. This mirrors the implementation of folds in libraries like [Cats](https://typelevel.org/cats/datatypes/const.html) and [Scalaz](https://github.com/scalaz/scalaz) in Scala.
~~~

---

## Use Case 2: Getters and Van Laarhoven Lenses

`Const` is fundamental to the lens pattern pioneered by Edward Kmett and popularised in Scala libraries like [Monocle](https://www.optics.dev/Monocle/). A lens is an abstraction for focusing on a part of a data structure, and `Const` enables the "getter" half of this abstraction.

### The Getter Pattern

A getter extracts a field from a structure without transforming it. Using `Const`, we represent this as a function that produces a `Const` where the phantom type tracks the source structure.

~~~admonish example title="Example: Compositional Getters"

[ConstExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java)

```java
record Person(String name, int age, String city) {}
record Company(String name, Person ceo) {}

Person alice = new Person("Alice", 30, "London");
Company acmeCorp = new Company("ACME Corp", alice);

// Define a getter using Const
Function<Person, Const<String, Person>> nameGetter =
    person -> new Const<>(person.name());

// Extract the name
Const<String, Person> nameConst = nameGetter.apply(alice);
System.out.println("CEO name: " + nameConst.value());
// Output: Alice

// Define a getter for the CEO from a Company
Function<Company, Const<Person, Company>> ceoGetter =
    company -> new Const<>(company.ceo());

// Compose getters: get CEO name from Company using mapFirst
Function<Company, Const<String, Company>> ceoNameGetter = company ->
    ceoGetter.apply(company)
        .mapFirst(person -> nameGetter.apply(person).value());

Const<String, Company> result = ceoNameGetter.apply(acmeCorp);
System.out.println("Company CEO name: " + result.value());
// Output: Alice
```

This pattern is the foundation of van Laarhoven lenses, where `Const` is used with `Functor` or `Applicative` to implement compositional getters. For a deeper dive, see [Van Laarhoven Lenses](https://www.twanvl.nl/blog/haskell/cps-functional-references) and [Scala Monocle](https://www.optics.dev/Monocle/docs/focus).
~~~

---

## Use Case 3: Data Extraction from Validation Results

When traversing validation results, you often want to extract accumulated errors or valid data without transforming the individual results. `Const` provides a clean way to express this pattern.

~~~admonish example title="Example: Validation Data Mining"

[ConstExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/constant/ConstExample.java)

```java
record ValidationResult(boolean isValid, List<String> errors, Object data) {}

List<ValidationResult> results = List.of(
    new ValidationResult(true, List.of(), "Valid data 1"),
    new ValidationResult(false, List.of("Error A", "Error B"), null),
    new ValidationResult(true, List.of(), "Valid data 2"),
    new ValidationResult(false, List.of("Error C"), null)
);

// Extract all errors using Const
List<String> allErrors = new ArrayList<>();

for (ValidationResult result : results) {
    // Use Const to extract errors, phantom type represents ValidationResult
    Const<List<String>, ValidationResult> errorConst = new Const<>(result.errors());
    allErrors.addAll(errorConst.value());
}

System.out.println("All errors: " + allErrors);
// Output: [Error A, Error B, Error C]

// Count valid results
Const<Integer, ValidationResult> validCount = results.stream()
    .reduce(
        new Const<>(0),
        (acc, result) -> new Const<Integer, ValidationResult>(
            result.isValid() ? acc.value() + 1 : acc.value()),
        (c1, c2) -> new Const<>(c1.value() + c2.value()));

System.out.println("Valid results: " + validCount.value());
// Output: 2
```

The phantom type maintains the "context" of what we're extracting from (ValidationResult), whilst the constant value accumulates the data we care about (errors or counts).
~~~

---

## Const vs Other Types

Understanding how `Const` relates to similar types clarifies its unique role:

| Type | First Parameter | Second Parameter | Primary Use |
|------|----------------|------------------|-------------|
| `Const<C, A>` | Constant value (stored) | Phantom (not stored) | Folds, getters, extraction |
| `Tuple2<A, B>` | First element (stored) | Second element (stored) | Pairing related values |
| `Identity<A>` | Value (stored) | N/A (single parameter) | Pure computation wrapper |
| `Either<L, R>` | Error (sum type) | Success (sum type) | Error handling |

**Use Const when:**
- You need to accumulate a single value during traversal
- You're implementing getters or read-only lenses
- You want to extract data without transformation
- The phantom type provides useful type-level information for composition

**Use Tuple2 when:**
- You need to store and work with both values
- Both parameters represent actual data

**Use Identity when:**
- You need a minimal monad wrapper with no additional effects

---

## Exception Propagation Note

Although `mapSecond` doesn't transform the constant value, the mapper function is still applied to `null` to ensure exception propagation. This maintains consistency with bifunctor semantics.

```java
Const<String, Integer> const_ = new Const<>("value");

// This will throw NullPointerException from the mapper
Const<String, Double> result = const_.mapSecond(i -> {
    if (i == null) throw new NullPointerException("Expected non-null");
    return i * 2.0;
});
```

This behaviour ensures that invalid mappers are detected, even though the mapper's result isn't used. For null-safe mappers, simply avoid dereferencing the parameter:

```java
// Null-safe phantom type transformation
Const<String, Double> safe = const_.mapSecond(i -> 3.14);
```

---

## Summary

* **Const<C, A>** holds a constant value of type `C` with a phantom type parameter `A`
* **Phantom types** exist only in type signatures, enabling type-safe composition without runtime overhead
* **Bifunctor operations**:
  * `first` transforms the constant value
  * `second` changes only the phantom type
  * `bimap` combines both (but only affects the constant via the first function)
* **Use cases**:
  * Efficient fold implementations that accumulate a single value
  * Compositional getters in lens and traversal patterns
  * Data extraction from complex structures without transformation
* **Scala heritage**: Mirrors `Const` in Cats, Scalaz, and Monocle
* **External resources**:
  * [Cats Const Documentation](https://typelevel.org/cats/datatypes/const.html)
  * [Monocle Getter Optics](https://www.optics.dev/Monocle/docs/optics/getter)
  * [Van Laarhoven Lenses](https://www.twanvl.nl/blog/haskell/cps-functional-references)

Understanding `Const` empowers you to write efficient, compositional code for data extraction and accumulation, leveraging patterns battle-tested in the Scala functional programming ecosystem.

---

**Previous:** [Writer](writer_monad.md)
**Next:** [More Functional Thinking](../reading.md)

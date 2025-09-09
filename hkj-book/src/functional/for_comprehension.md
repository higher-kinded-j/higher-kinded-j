# For-Comprehensions

~~~admonish info title="What You'll Learn"
- How to transform nested `flatMap` chains into readable, sequential code
- The four types of operations: generators (`.from()`), bindings (`.let()`), guards (`.when()`), and projections (`.yield()`)
- Building complex workflows with StateT and other monad transformers
- Converting "pyramid of doom" code into clean, imperative-style scripts
- Real-world examples from simple Maybe operations to complex state management
~~~

~~~ admonish example title="See Example Code:"
[ForComprehensionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForComprehensionExample.java)
~~~

Endless nested callbacks and unreadable chains of flatMap calls can be tiresome. The `higher-kinded-j` library brings the elegance and power of Scala-style for-comprehensions to Java, allowing you to write complex asynchronous and sequential logic in a way that is clean, declarative, and easy to follow.

Let's see how to transform "callback hell" into a readable, sequential script.

## The "Pyramid of Doom" Problem

In functional programming, monads are a powerful tool for sequencing operations, especially those with a context like `Optional`, `List`, or `CompletableFuture`. However, chaining these operations with `flatMap` can quickly become hard to read.

Consider combining three `Maybe` values:

```java
// The "nested" way
Kind<MaybeKind.Witness, Integer> result = maybeMonad.flatMap(a ->
    maybeMonad.flatMap(b ->
        maybeMonad.map(c -> a + b + c, maybeC),
    maybeB),
maybeA);
```

This code works, but the logic is buried inside nested lambdas. The intent—to simply get values from `maybeA`, `maybeB`, and `maybeC` and add them—is obscured. This is often called the "pyramid of doom."

## _For_ A Fluent, Sequential Builder

The `For` comprehension builder provides a much more intuitive way to write the same logic. It lets you express the sequence of operations as if they were simple, imperative steps.

Here’s the same example rewritten with the `For` builder:

```java
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import org.higherkindedj.hkt.expression.For;
// ... other imports

var maybeMonad = MaybeMonad.INSTANCE;
var maybeA = MAYBE.just(5);
var maybeB = MAYBE.just(10);
var maybeC = MAYBE.just(20);

// The clean, sequential way
var result = For.from(maybeMonad, maybeA)    // Get a from maybeA
    .from(a -> maybeB)                       // Then, get b from maybeB
    .from(t -> maybeC)                       // Then, get c from maybeC
    .yield((a, b, c) -> a + b + c);          // Finally, combine them

System.out.println(MAYBE.narrow(result)); // Prints: Just(35)
```

This version is flat, readable, and directly expresses the intended sequence of operations. The `For` builder automatically handles the `flatMap` and `map` calls behind the scenes.


## Core Operations of the `For` Builder

A for-comprehension is built by chaining four types of operations:

### 1. Generators: `.from()`

A generator is the workhorse of the comprehension. It takes a value from a previous step, uses it to produce a new monadic value (like another `Maybe` or `List`), and extracts the result for the next step. This is a direct equivalent of **`flatMap`**.

Each `.from()` adds a new variable to the scope of the comprehension.

```java
// Generates all combinations of user IDs and roles
var userRoles = For.from(listMonad, LIST.widen(List.of("user-1", "user-2"))) // a: "user-1", "user-2"
    .from(a -> LIST.widen(List.of("viewer", "editor")))       // b: "viewer", "editor"
    .yield((a, b) -> a + " is a " + b);

// Result: ["user-1 is a viewer", "user-1 is a editor", "user-2 is a viewer", "user-2 is a editor"]
```


### 2. Value Bindings: `.let()`

A `.let()` binding allows you to compute a pure, simple value from the results you've gathered so far and add it to the scope. It does *not* involve a monad. This is equivalent to a **`map`** operation that carries the new value forward.

```java
var idMonad = IdentityMonad.instance();

var result = For.from(idMonad, Id.of(10))        // a = 10
    .let(a -> a * 2)                          // b = 20 (a pure calculation)
    .yield((a, b) -> "Value: " + a + ", Doubled: " + b);

// Result: "Value: 10, Doubled: 20"
System.out.println(ID.unwrap(result));
```


### 3. Guards: `.when()`

For monads that can represent failure or emptiness (like `List`, `Maybe`, or `Optional`), you can use `.when()` to **filter** results. If the condition is false, the current computational path is stopped by returning the monad's "zero" value (e.g., an empty list or `Maybe.nothing()`).

> This feature requires a `MonadZero` instance. See the `MonadZero` documentation for more details.
>

```java
var evens = For.from(listMonad, LIST.widen(List.of(1, 2, 3, 4, 5, 6)))
    .when(i -> i % 2 == 0) // Guard: only keep even numbers
    .yield(i -> i);

// Result: [2, 4, 6]
```



### 4. Projection: `.yield()`

Every comprehension ends with `.yield()`. This is the final **`map`** operation where you take all the values you've gathered from the generators and bindings and produce your final result. You can access the bound values as individual lambda parameters or as a single `Tuple`.



## Turn the power up: `StateT` Example

- [ForComprehensionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForComprehensionExample.java)

The true power of for-comprehensions becomes apparent when working with complex structures like monad transformers. A `StateT` over `Optional` represents a **stateful computation that can fail**. Writing this with nested `flatMap` calls would be extremely complex. With the `For` builder, it becomes a simple, readable script.

```java
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
// ... other imports

private static void stateTExample() {
    final var optionalMonad = OptionalMonad.INSTANCE;
    final var stateTMonad = StateTMonad.<Integer, OptionalKind.Witness>instance(optionalMonad);

    // Helper: adds a value to the state (an integer)
    final Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Unit>> add =
        n -> StateT.create(s -> optionalMonad.of(StateTuple.of(s + n, Unit.INSTANCE)), optionalMonad);

    // Helper: gets the current state as the value
    final var get = StateT.<Integer, OptionalKind.Witness, Integer>create(s -> optionalMonad.of(StateTuple.of(s, s)), optionalMonad);

    // This workflow looks like a simple script, but it's a fully-typed, purely functional composition!
    final var statefulComputation =
        For.from(stateTMonad, add.apply(10))      // Add 10 to state
            .from(a -> add.apply(5))              // Then, add 5 more
            .from(b -> get)                       // Then, get the current state (15)
            .let(t -> "The state is " + t._3())   // Compute a string from it
            .yield((a, b, c, d) -> d + ", original value was " + c); // Produce the final string

    // Run the computation with an initial state of 0
    final var resultOptional = STATE_T.runStateT(statefulComputation, 0);
    final Optional<StateTuple<Integer, String>> result = OPTIONAL.narrow(resultOptional);

    result.ifPresent(res -> {
        System.out.println("Final value: " + res.value());
        System.out.println("Final state: " + res.state());
    });
    // Expected Output:
    // Final value: The state is 15, original value was 15
    // Final state: 15
}
```

In this example, Using the `For` comprehension really helps hide the complexity of threading the state (`Integer`) and handling potential failures (`Optional`), making the logic clear and maintainable.


For a more extensive example of using the full power of the For comprehension head over to the [Order Workflow](../hkts/order-walkthrough.md)

## Similarities to Scala

If you're familiar with Scala, you'll recognise the pattern. In Scala, a for-comprehension looks like this:

```scala
for {
 a <- maybeA
 b <- maybeB
 if (a + b > 10)
 c = a + b
} yield c * 2
```

This is built in syntactic sugar that the compiler translates into a series of `flatMap`, `map`, and `withFilter` calls. 
The `For` builder in `higher-kinded-j` provides the same expressive power through a method-chaining API.  



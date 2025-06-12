# For-Comprehensions

`higher-kinded-j` provides a powerful `For` comprehension builder that brings some of the elegance and readability of Scala's for-comprehensions to Java. This tutorial will guide you through its features and show you how to write cleaner, more declarative code when working with monads and monad transformers.

## Why Use a For-Comprehension?

Working with monads often involves chaining operations using `flatMap` and `map`. While powerful, this can lead to deeply nested code, sometimes called a "pyramid of doom" or "callback hell."

Consider this example using `Maybe`:

```java
MaybeMonad maybeMonad = new MaybeMonad();
Kind<MaybeKind.Witness, Integer> maybeA = MAYBE.just(5);
Kind<MaybeKind.Witness, Integer> maybeB = MAYBE.just(10);
Kind<MaybeKind.Witness, Integer> maybeC = MAYBE.just(20);

Kind<MaybeKind.Witness, Integer> result = maybeMonad.flatMap(a ->
    maybeMonad.flatMap(b ->
        maybeMonad.map(c -> a + b + c, maybeC),
    maybeB),
maybeA);
```

This code is functionally correct but can be difficult to read and maintain as more steps are added. The logic is spread across nested lambdas, obscuring the simple sequential nature of the computation.

The `For` comprehension builder solves this by providing a fluent, sequential API that achieves the same result in a more readable way.

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

is syntactic sugar that the compiler translates into a series of `flatMap`, `map`, and `withFilter` calls. The `For` builder in `higher-kinded-j` provides the same expressive power through a method-chaining API.

## Getting Started: The `For` Builder

You start a comprehension by calling the static `For.from()` method, providing a monad instance and the initial monadic source.

Let's see the previous example rewritten using the `For` builder. We'll use static imports and `var` for maximum clarity and conciseness.

```java
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
// ... other imports

var maybeMonad = new MaybeMonad();

var maybeA = MAYBE.just(5);
var maybeB = MAYBE.just(10);
var maybeC = MAYBE.just(20);

var result = For.from(maybeMonad, maybeA)
    .from(a -> maybeB)
    .from(t -> maybeC) // 't' is a Tuple of the previous results (a, b)
    .yield((a, b, c) -> a + b + c); // Yield provides all bound values

System.out.println(MAYBE.narrow(result)); // Prints: Just(35)
```

This code is much easier to read. It clearly shows a sequence of three values being extracted from their monadic context and then combined in the final `yield` step.

## Core Operations

The `For` builder has four main operations you can chain together.

### 1. Generators: `.from()`

A "generator" extracts a value from a monadic context. It's equivalent to a `flatMap` operation. Each call to `.from()` adds a new step to the sequence. The lambda you provide to `.from()` receives the previously bound values as a `Tuple`.

```java
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import org.higherkindedj.hkt.list.ListMonad;
// ... other imports

var listMonad = ListMonad.INSTANCE;

var users = For.from(listMonad, LIST.widen(List.of(1, 2)))      // a: 1, 2
    .from(a -> LIST.widen(List.of("a", "b"))) // b: "a", "b"
    .yield((a, b) -> "User " + a + b);

// Result: ["User 1a", "User 1b", "User 2a", "User 2b"]
System.out.println("Generated users: " + LIST.narrow(users));

```

### 2. Value Bindings: `.let()`

Sometimes you want to compute a value based on previous results without entering a new monadic context. This is a pure computation, equivalent to a `map` operation. The `.let()` method allows you to do this, binding the result to a new variable for subsequent steps.

```java
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdentityMonad;
// ... other imports

var idMonad = IdentityMonad.instance();

var result = For.from(idMonad, Id.of(10)) // a = 10
    .let(a -> a * 2)               // b = 20 (pure function)
    .yield((a, b) -> "Value: " + a + ", Doubled: " + b);

// Result: "Value: 10, Doubled: 20"
System.out.println(ID.unwrap(result));
```

### 3. Guards: `.when()`

For monads that can represent failure or "emptiness" (implementing `MonadZero`), you can filter results using `.when()`. If the predicate returns `false`, the comprehension short-circuits at that point for that particular path, yielding the monad's "zero" element (e.g., `Maybe.nothing()`, `List.of()`).
- [see MonadZero documentation here](../monad_zero.md)

`List` and `Maybe`/`Optional` are `MonadZero` implementations and support filtering.

```java
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import org.higherkindedj.hkt.list.ListMonad;
// ... other imports

var listMonad = ListMonad.INSTANCE;

var evens = For.from(listMonad, LIST.widen(List.of(1, 2, 3, 4, 5, 6)))
    .when(i -> i % 2 == 0) // Keep only even numbers
    .yield(i -> i);

// Result: [2, 4, 6]
System.out.println("Filtered evens: " + LIST.narrow(evens));
```

### 4. Yielding the Result: `.yield()`

The final step in every for-comprehension is `.yield()`. It takes the values bound in all previous steps and produces the final result. The values are provided either as individual parameters to a lambda or as a single `Tuple`.

```java
// Yielding with multiple parameters
var result1 = For.from(monad, sourceA)
    .from(a -> sourceB)
    .yield((a, b) -> a + ":" + b); // BiFunction

// Yielding with a tuple
var result2 = For.from(monad, sourceA)
    .from(a -> sourceB)
    .yield(t -> t._1() + ":" + t._2()); // Function<Tuple2<A,B>, R>
```

## Complete Example with `StateT`

The true power of the `For` comprehension shines when working with more complex structures like monad transformers. Here is an example using `StateT` over `Optional`, which represents a stateful computation that can also fail.

```java
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
// ... other imports

private static void stateTExample() {
    final var optionalMonad = OptionalMonad.INSTANCE;
    final var stateTMonad = StateTMonad.<Integer, OptionalKind.Witness>instance(optionalMonad);

    // Helper to create a StateT that modifies the state and returns Unit
    final Function<Integer, Kind<StateTKind.Witness<Integer, OptionalKind.Witness>, Unit>> add =
        n -> StateT.create(s -> optionalMonad.of(StateTuple.of(s + n, Unit.INSTANCE)), optionalMonad);

    // Helper to create a StateT that gets the current state as its value
    final var get = StateT.<Integer, OptionalKind.Witness, Integer>create(s -> optionalMonad.of(StateTuple.of(s, s)), optionalMonad);

    final var statefulComputation =
        For.from(stateTMonad, add.apply(10))      // State becomes 10, a = Unit
            .from(a -> add.apply(5))              // State becomes 15, b = Unit
            .from(b -> get)                       // State is 15, c = 15
            .let(t -> "The state is " + t._3())   // d = "The state is 15"
            .yield((a, b, c, d) -> d + ", original value was " + c);

    // Run the computation with an initial state of 0
    final var resultOptional = STATE_T.runStateT(statefulComputation, 0);
    final Optional<StateTuple<Integer, String>> result = OPTIONAL.narrow(resultOptional);

    result.ifPresent(res -> {
        System.out.println("Final value: " + res.value());
        System.out.println("Final state: " + res.state());
    });
}
```

In this example, the `For` comprehension elegantly hides the complexity of threading the state (`Integer`) and handling the potential absence of a value (`Optional`), making the logic look like a simple, sequential script.

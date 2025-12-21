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

This code works, but the logic is buried inside nested lambdas. The intent (to simply get values from `maybeA`, `maybeB`, and `maybeC` and add them) is obscured. This is often called the "pyramid of doom."

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
// Generates all combinations of userLogin IDs and roles
var userRoles = For.from(listMonad, LIST.widen(List.of("userLogin-1", "userLogin-2"))) // a: "userLogin-1", "userLogin-2"
    .from(a -> LIST.widen(List.of("viewer", "editor")))       // b: "viewer", "editor"
    .yield((a, b) -> a + " is a " + b);

// Result: ["userLogin-1 is a viewer", "userLogin-1 is a editor", "userLogin-2 is a viewer", "userLogin-2 is a editor"]
```


### 2. Value Bindings: `.let()`

A `.let()` binding allows you to compute a pure, simple value from the results you've gathered so far and add it to the scope. It does *not* involve a monad. This is equivalent to a **`map`** operation that carries the new value forward.

```java
var idMonad = IdMonad.instance();

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

---

## Working with Optics

~~~admonish info title="For-Optics Integration"
The For comprehension integrates natively with the [Optics](../optics/optics_intro.md) library, providing first-class support for lens-based extraction, prism-based pattern matching, and traversal-based iteration within monadic comprehensions.
~~~

The `For` builder provides two optic-aware operations that extend the comprehension capabilities:

### Extracting Values with `focus()`

The `focus()` method extracts a value using a function and adds it to the accumulated tuple. This is particularly useful when working with nested data structures.

```java
record User(String name, Address address) {}
record Address(String city, String street) {}

var users = List.of(
    new User("Alice", new Address("London", "Baker St")),
    new User("Bob", new Address("Paris", "Champs-Élysées"))
);

Kind<ListKind.Witness, String> result =
    For.from(listMonad, LIST.widen(users))
        .focus(user -> user.address().city())
        .yield((user, city) -> user.name() + " lives in " + city);

// Result: ["Alice lives in London", "Bob lives in Paris"]
```

The extracted value is accumulated alongside the original value, making both available in subsequent steps and in the final `yield()`.

### Pattern Matching with `match()`

The `match()` method provides prism-like pattern matching within comprehensions. When used with a `MonadZero` (such as `List` or `Maybe`), it short-circuits the computation if the match fails.

```java
sealed interface Result permits Success, Failure {}
record Success(String value) implements Result {}
record Failure(String error) implements Result {}

Prism<Result, Success> successPrism = Prism.of(
    r -> r instanceof Success s ? Optional.of(s) : Optional.empty(),
    s -> s
);

List<Result> results = List.of(
    new Success("data1"),
    new Failure("error"),
    new Success("data2")
);

Kind<ListKind.Witness, String> successes =
    For.from(listMonad, LIST.widen(results))
        .match(successPrism)
        .yield((result, success) -> success.value().toUpperCase());

// Result: ["DATA1", "DATA2"] - failures are filtered out
```

With `Maybe`, failed matches short-circuit to `Nothing`:

```java
Kind<MaybeKind.Witness, String> result =
    For.from(maybeMonad, MAYBE.just((Result) new Failure("error")))
        .match(successPrism)
        .yield((r, s) -> s.value());

// Result: Nothing - the match failed
```

~~~admonish tip title="Combining focus() and match()"
Both operations can be chained to build complex extraction and filtering pipelines:

```java
For.from(listMonad, LIST.widen(items))
    .focus(item -> item.category())
    .match(premiumCategoryPrism)
    .when(t -> t._3().discount() > 0.1)
    .yield((item, category, premium) -> item.name());
```
~~~

---

## Bulk Operations with ForTraversal

For operations over multiple elements within a structure, use `ForTraversal`. This provides a fluent API for applying transformations to all elements focused by a [Traversal](../optics/traversals.md).

```java
record Player(String name, int score) {}

List<Player> players = List.of(
    new Player("Alice", 100),
    new Player("Bob", 200)
);

Traversal<List<Player>, Player> playersTraversal = Traversals.forList();
Lens<Player, Integer> scoreLens = Lens.of(
    Player::score,
    (p, s) -> new Player(p.name(), s)
);

// Add bonus points to all players
Kind<IdKind.Witness, List<Player>> result =
    ForTraversal.over(playersTraversal, players, IdMonad.instance())
        .modify(scoreLens, score -> score + 50)
        .run();

List<Player> updated = IdKindHelper.ID.unwrap(result);
// Result: [Player("Alice", 150), Player("Bob", 250)]
```

### Filtering Within Traversals

The `filter()` method preserves non-matching elements unchanged whilst applying transformations only to matching elements:

```java
Kind<IdKind.Witness, List<Player>> result =
    ForTraversal.over(playersTraversal, players, IdMonad.instance())
        .filter(p -> p.score() >= 150)
        .modify(scoreLens, score -> score * 2)
        .run();

// Alice (100) unchanged, Bob (200) doubled to 400
```

### Collecting Results

Use `toList()` to collect all focused elements:

```java
Kind<IdKind.Witness, List<Player>> allPlayers =
    ForTraversal.over(playersTraversal, players, IdMonad.instance())
        .toList();
```

---

## Stateful Updates with ForState

`ForState` provides a fluent API for threading state updates through a workflow using lenses. This is particularly useful for building up complex state transformations step by step.

```java
record WorkflowContext(
    String orderId,
    boolean validated,
    boolean inventoryChecked,
    String confirmationId
) {}

Lens<WorkflowContext, Boolean> validatedLens = Lens.of(
    WorkflowContext::validated,
    (ctx, v) -> new WorkflowContext(ctx.orderId(), v, ctx.inventoryChecked(), ctx.confirmationId())
);

Lens<WorkflowContext, Boolean> inventoryLens = Lens.of(
    WorkflowContext::inventoryChecked,
    (ctx, c) -> new WorkflowContext(ctx.orderId(), ctx.validated(), c, ctx.confirmationId())
);

Lens<WorkflowContext, String> confirmationLens = Lens.of(
    WorkflowContext::confirmationId,
    (ctx, id) -> new WorkflowContext(ctx.orderId(), ctx.validated(), ctx.inventoryChecked(), id)
);

var initialContext = new WorkflowContext("ORD-123", false, false, null);

Kind<IdKind.Witness, WorkflowContext> result =
    ForState.withState(idMonad, Id.of(initialContext))
        .update(validatedLens, true)
        .update(inventoryLens, true)
        .update(confirmationLens, "CONF-456")
        .yield();

WorkflowContext finalCtx = IdKindHelper.ID.unwrap(result);
// finalCtx: validated=true, inventoryChecked=true, confirmationId="CONF-456"
```

### Modifying State Based on Current Values

Use `modify()` to update state based on the current value:

```java
ForState.withState(idMonad, Id.of(context))
    .modify(scoreLens, score -> score + bonus)
    .yield();
```

---

## Position-Aware Traversals with ForIndexed

`ForIndexed` extends traversal comprehensions to include index/position awareness, enabling transformations that depend on element position within the structure.

```java
IndexedTraversal<Integer, List<Player>, Player> indexedPlayers =
    IndexedTraversals.forList();

List<Player> players = List.of(
    new Player("Alice", 100),
    new Player("Bob", 200),
    new Player("Charlie", 150)
);

// Add position-based bonus (first place gets more)
Kind<IdKind.Witness, List<Player>> result =
    ForIndexed.overIndexed(indexedPlayers, players, IdMonad.instance())
        .modify(scoreLens, (index, score) -> score + (100 - index * 10))
        .run();

// Alice: 100 + 100 = 200
// Bob: 200 + 90 = 290
// Charlie: 150 + 80 = 230
```

### Filtering by Position

Use `filterIndex()` to focus only on specific positions:

```java
// Only modify top 3 players
ForIndexed.overIndexed(indexedPlayers, players, idApplicative)
    .filterIndex(i -> i < 3)
    .modify(scoreLens, (i, s) -> s * 2)
    .run();
```

### Combined Index and Value Filtering

Use `filter()` with a `BiPredicate` to filter based on both position and value:

```java
ForIndexed.overIndexed(indexedPlayers, players, idApplicative)
    .filter((index, player) -> index < 5 && player.score() > 100)
    .modify(scoreLens, (i, s) -> s + 50)
    .run();
```

### Collecting with Indices

Use `toIndexedList()` to collect elements along with their indices:

```java
Kind<IdKind.Witness, List<Pair<Integer, Player>>> indexed =
    ForIndexed.overIndexed(indexedPlayers, players, idApplicative)
        .toIndexedList();

// Result: [Pair(0, Player("Alice", 100)), Pair(1, Player("Bob", 200)), ...]
```

~~~admonish tip title="See Also"
For more details on indexed optics, see [Indexed Optics](../optics/indexed_optics.md).
~~~

~~~admonish tip title="See Also"
- [ForPath Comprehension](../effect/forpath_comprehension.md) - For-comprehensions that work directly with Effect Path types
~~~

---

~~~admonish tip title="Further Reading"
- **Scala For-Comprehensions**: [Tour of Scala](https://docs.scala-lang.org/tour/for-comprehensions.html) - The inspiration for this API
~~~

---

**Previous:** [Natural Transformation](natural_transformation.md)


# Fluent API for Optics: Java-Friendly Optic Operations

![fluent_code.jpg](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- Two styles of optic operations: static methods and fluent builders
- When to use each style for maximum clarity and productivity
- How to perform common optic operations with Java-friendly syntax
- Effectful modifications using type classes
- Practical patterns for real-world Java applications
~~~

~~~admonish title="Example Code"
[FluentOpticOpsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FluentOpticOpsExample.java)
~~~

## Introduction: Making Optics Feel Natural in Java

While optics provide immense power for working with immutable data structures, their traditional functional programming syntax can feel foreign to Java developers. Method names like `view`, `over`, and `preview` don't match Java conventions, and the order of parameters can be unintuitive.

The `OpticOps` fluent API bridges this gap, providing two complementary styles that make optics feel natural in Java:

1. **Static methods** - Concise, direct operations for simple cases
2. **Fluent builders** - Method chaining with IDE-discoverable operations

Both styles operate on the same underlying optics, so you can mix and match based on what feels most natural for each situation.

---

## The Two Styles: A Quick Comparison

Let's see both styles in action with a simple example:

```java
@GenerateLenses
public record Person(String name, int age, String status) {}

Person person = new Person("Alice", 25, "ACTIVE");
Lens<Person, Integer> ageLens = PersonLenses.age();
```

### Static Method Style (Concise)

```java
// Get a value
int age = OpticOps.get(person, ageLens);

// Set a value
Person updated = OpticOps.set(person, ageLens, 30);

// Modify a value
Person modified = OpticOps.modify(person, ageLens, a -> a + 1);
```

### Fluent Builder Style (Explicit)

```java
// Get a value
int age = OpticOps.getting(person).through(ageLens);

// Set a value
Person updated = OpticOps.setting(person).through(ageLens, 30);

// Modify a value
Person modified = OpticOps.modifying(person).through(ageLens, a -> a + 1);
```

Both produce identical results. The choice is about **readability** and **discoverability** for your specific use case.

---

## Part 1: Static Methods - Simple and Direct

Static methods provide the most concise syntax. They follow a consistent pattern: operation name, source object, optic, and optional parameters.

### Getting Values

#### Basic Get Operations

```java
// Get a required value (Lens or Getter)
String name = OpticOps.get(person, PersonLenses.name());

// Get an optional value (Prism or Traversal)
Optional<Address> address = OpticOps.preview(person, PersonPrisms.homeAddress());

// Get all values (Traversal or Fold)
List<String> playerNames = OpticOps.getAll(team, TeamTraversals.playerNames());
```

~~~admonish example title="Practical Example: Extracting Data"
```java
@GenerateLenses
@GenerateTraversals
public record Team(String name, List<Player> players) {}

@GenerateLenses
public record Player(String name, int score) {}

Team team = new Team("Wildcats",
    List.of(
        new Player("Alice", 100),
        new Player("Bob", 85)
    ));

// Get all player names
List<String> names = OpticOps.getAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.name().asTraversal())
);
// Result: ["Alice", "Bob"]
```
~~~

### Setting Values

```java
// Set a single value (Lens)
Person updated = OpticOps.set(person, PersonLenses.age(), 30);

// Set all values (Traversal)
Team teamWithBonuses = OpticOps.setAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    100  // Everyone gets 100 points!
);
```

### Modifying Values

The `modify` operations are particularly powerful because they transform existing values rather than replacing them:

```java
// Modify a single value
Person olderPerson = OpticOps.modify(
    person,
    PersonLenses.age(),
    age -> age + 1
);

// Modify all values
Team teamWithDoubledScores = OpticOps.modifyAll(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score * 2
);
```

### Querying Data

These operations work with `Fold` and `Traversal` to query data without modification:

```java
// Check if any element matches
boolean hasHighScorer = OpticOps.exists(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score > 90
);

// Check if all elements match
boolean allPassed = OpticOps.all(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> score >= 50
);

// Count elements
int playerCount = OpticOps.count(team, TeamTraversals.players());

// Check if empty
boolean noPlayers = OpticOps.isEmpty(team, TeamTraversals.players());

// Find first matching element
Optional<Player> topScorer = OpticOps.find(
    team,
    TeamTraversals.players(),
    player -> player.score() > 90
);
```

### Effectful Modifications

These are the most powerful operations, allowing modifications that can fail, accumulate errors, or execute asynchronously:

```java
// Modify with an effect (e.g., validation)
// Note: Error should be your application's error type (e.g., String, List<String>, or a custom error class)
Functor<Validated.Witness<Error>> validatedFunctor =
    ValidatedApplicative.instance(ErrorSemigroup.instance());

Validated<Error, Person> result = OpticOps.modifyF(
    person,
    PersonLenses.age(),
    age -> validateAge(age + 1),  // Returns Validated<Error, Integer>
    validatedFunctor
);

// Modify all with effects (e.g., async operations)
Applicative<CompletableFutureKind.Witness> cfApplicative =
    CompletableFutureMonad.instance();

CompletableFuture<Team> asyncResult = OpticOps.modifyAllF(
    team,
    TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
    score -> fetchBonusAsync(score),  // Returns CompletableFuture<Integer>
    cfApplicative
).thenApply(CompletableFutureKind::narrow);
```

---

## Part 2: Fluent Builders - Explicit and Discoverable

Fluent builders provide excellent IDE support through method chaining. They make the intent of your code crystal clear.

### The GetBuilder Pattern

```java
// Start with getting(source), then specify the optic
int age = OpticOps.getting(person).through(PersonLenses.age());

Optional<Address> addr = OpticOps.getting(person)
    .maybeThrough(PersonPrisms.homeAddress());

List<String> names = OpticOps.getting(team)
    .allThrough(TeamTraversals.playerNames());
```

### The SetBuilder Pattern

```java
// Start with setting(source), then specify optic and value
Person updated = OpticOps.setting(person)
    .through(PersonLenses.age(), 30);

Team updatedTeam = OpticOps.setting(team)
    .allThrough(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        100
    );
```

### The ModifyBuilder Pattern

```java
// Start with modifying(source), then specify optic and function
Person modified = OpticOps.modifying(person)
    .through(PersonLenses.age(), age -> age + 1);

Team modifiedTeam = OpticOps.modifying(team)
    .allThrough(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score * 2
    );

// Effectful modifications
Validated<Error, Person> result = OpticOps.modifying(person)
    .throughF(
        PersonLenses.age(),
        age -> validateAge(age + 1),
        validatedFunctor
    );
```

### The QueryBuilder Pattern

```java
// Start with querying(source), then specify checks
boolean hasHighScorer = OpticOps.querying(team)
    .anyMatch(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score > 90
    );

boolean allPassed = OpticOps.querying(team)
    .allMatch(
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score >= 50
    );

Optional<Player> found = OpticOps.querying(team)
    .findFirst(TeamTraversals.players(), player -> player.score() > 90);

int count = OpticOps.querying(team)
    .count(TeamTraversals.players());

boolean empty = OpticOps.querying(team)
    .isEmpty(TeamTraversals.players());
```

---

## Part 3: Real-World Examples

### Example 1: E-Commerce Order Processing

```java
@GenerateLenses
@GenerateTraversals
public record Order(String orderId,
                    OrderStatus status,
                    List<OrderItem> items,
                    ShippingAddress address) {}

@GenerateLenses
public record OrderItem(String productId, int quantity, BigDecimal price) {}

@GenerateLenses
public record ShippingAddress(String street, String city, String postCode) {}

// Scenario: Apply bulk discount and update shipping
Order processOrder(Order order, BigDecimal discountPercent) {
    // Apply discount using fluent API
    Order discountedOrder = OpticOps.modifying(order)
        .allThrough(
            OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal()),
            price -> price.multiply(BigDecimal.ONE.subtract(discountPercent))
        );

    // Update status using static method
    return OpticOps.set(
        discountedOrder,
        OrderLenses.status(),
        OrderStatus.PROCESSING
    );
}
```

### Example 2: Validation with Error Accumulation

```java
// Using Validated to accumulate all validation errors
Validated<List<String>, Order> validateOrder(Order order) {
    Applicative<Validated.Witness<List<String>>> applicative =
        ValidatedApplicative.instance(ListSemigroup.instance());

    // Validate all item quantities
    return OpticOps.modifyAllF(
        order,
        OrderTraversals.items().andThen(OrderItemLenses.quantity().asTraversal()),
        qty -> {
            if (qty > 0 && qty <= 1000) {
                return Validated.valid(qty);
            } else {
                return Validated.invalid(List.of(
                    "Quantity must be between 1 and 1000, got: " + qty
                ));
            }
        },
        applicative
    ).narrow();
}
```

### Example 3: Async Database Updates

```java
// Using CompletableFuture for async operations
CompletableFuture<Team> updatePlayerScoresAsync(
    Team team,
    Function<Player, CompletableFuture<Integer>> fetchNewScore
) {
    Applicative<CompletableFutureKind.Witness> cfApplicative =
        CompletableFutureMonad.instance();

    return OpticOps.modifyAllF(
        team,
        TeamTraversals.players(),
        player -> fetchNewScore.apply(player)
            .thenApply(newScore ->
                OpticOps.set(player, PlayerLenses.score(), newScore)
            )
            .thenApply(CompletableFutureKind::of),
        cfApplicative
    ).thenApply(kind -> CompletableFutureKind.narrow(kind).join());
}
```

---

## When to Use Each Style

### Use Static Methods When:

✅ **Performing simple, one-off operations**
```java
// Clear and concise
String name = OpticOps.get(person, PersonLenses.name());
```

✅ **Chaining is not needed**
```java
// Direct transformation
Person older = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

✅ **Performance is critical** (slightly less object allocation)

### Use Fluent Builders When:

✅ **Building complex workflows**
```java
import static java.util.stream.Collectors.toList;

// Clear intent at each step
return OpticOps.getting(order)
    .allThrough(OrderTraversals.items())
    .stream()
    .filter(item -> item.quantity() > 10)
    .map(OrderItem::productId)
    .collect(toList());
```

✅ **IDE autocomplete is important** (great for discovery)

✅ **Code reviews matter** (explicit intent)

✅ **Teaching or documentation** (self-explanatory)

---

## Common Patterns and Idioms

### Pattern 1: Pipeline Transformations

```java
// Sequential transformations for multi-step pipeline
// Note: Result and Data should be your application's domain types with appropriate lenses
Result processData(Data input) {
    Data afterStage1 = OpticOps.modifying(input)
        .through(DataLenses.stage1(), this::transformStage1);

    Data afterStage2 = OpticOps.modifying(afterStage1)
        .through(DataLenses.stage2(), this::transformStage2);

    return OpticOps.modifying(afterStage2)
        .through(DataLenses.stage3(), this::transformStage3);
}
```

### Pattern 2: Conditional Updates

```java
// Static style for simple conditionals
Person updateIfAdult(Person person) {
    int age = OpticOps.get(person, PersonLenses.age());
    return age >= 18
        ? OpticOps.set(person, PersonLenses.status(), "ADULT")
        : person;
}
```

### Pattern 3: Bulk Operations with Filtering

```java
// Combine both styles for clarity
Team updateTopPerformers(Team team, int threshold) {
    // Use fluent for query
    List<Player> topPerformers = OpticOps.querying(team)
        .allThrough(TeamTraversals.players())
        .stream()
        .filter(p -> p.score() >= threshold)
        .toList();

    // Use static for transformation
    return OpticOps.modifyAll(
        team,
        TeamTraversals.players(),
        player -> topPerformers.contains(player)
            ? OpticOps.set(player, PlayerLenses.status(), "STAR")
            : player
    );
}
```

---

## Performance Considerations

### Object Allocation

- **Static methods**: Minimal allocation (just the result)
- **Fluent builders**: Create intermediate builder objects
- **Impact**: Negligible for most applications; avoid in tight loops

### Optic Composition

Both styles benefit from composing optics once and reusing them:

```java
// ✅ Good: Compose once, use many times
Lens<Order, BigDecimal> orderToTotalPrice =
    OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal())
        .andThen(someAggregationLens);

orders.stream()
    .map(order -> OpticOps.getAll(order, orderToTotalPrice))
    .collect(toList());

// ❌ Avoid: Recomposing in loop
orders.stream()
    .map(order -> OpticOps.getAll(
        order,
        OrderTraversals.items()
            .andThen(OrderItemLenses.price().asTraversal())  // Recomposed each time!
    ))
    .collect(toList());
```

---

## Integration with Existing Java Code

### Working with Streams

```java
// Optics integrate naturally with Stream API
List<String> highScorerNames = OpticOps.getting(team)
    .allThrough(TeamTraversals.players())
    .stream()
    .filter(p -> p.score() > 90)
    .map(p -> OpticOps.get(p, PlayerLenses.name()))
    .collect(toList());
```

### Working with Optional

```java
// Optics and Optional work together
Optional<Person> maybePerson = findPerson(id);

Optional<Integer> age = maybePerson
    .map(p -> OpticOps.get(p, PersonLenses.age()));

Person updated = maybePerson
    .map(p -> OpticOps.modify(p, PersonLenses.age(), a -> a + 1))
    .orElse(new Person("Default", 0, "UNKNOWN"));
```

---

## Common Pitfalls

### ❌ Don't: Call `get` then `set`

```java
// Inefficient - two traversals
int age = OpticOps.get(person, PersonLenses.age());
Person updated = OpticOps.set(person, PersonLenses.age(), age + 1);
```

### ✅ Do: Use `modify`

```java
// Efficient - single traversal
Person updated = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

### ❌ Don't: Recompose optics unnecessarily

```java
// Bad - composing in a loop
for (Order order : orders) {
    var itemPrices = OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal());  // Composed each iteration!
    process(OpticOps.getAll(order, itemPrices));
}
```

### ✅ Do: Compose once, reuse

```java
// Good - compose outside loop
var itemPrices = OrderTraversals.items()
    .andThen(OrderItemLenses.price().asTraversal());

for (Order order : orders) {
    process(OpticOps.getAll(order, itemPrices));
}
```

---

## Further Reading

- **Fluent Interfaces**: [Martin Fowler's article](https://martinfowler.com/bliki/FluentInterface.html) on designing fluent APIs
- **Builder Pattern**: [Effective Java, 3rd Edition](https://www.oracle.com/java/technologies/effective-java.html) by Joshua Bloch
- **Method Chaining**: [Patterns of Enterprise Application Architecture](https://martinfowler.com/eaaCatalog/)
- **Lens Tutorial**: [Haskell lens tutorial](https://hackage.haskell.org/package/lens-tutorial) for deeper theoretical understanding

---

**Next Steps:**

- [Free Monad DSL for Optics](free_monad_dsl.md) - Build composable programs
- [Optic Interpreters](interpreters.md) - Multiple execution strategies
- [Advanced Patterns](composing_optics.md) - Complex real-world scenarios

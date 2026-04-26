# Fluent API Field Guide

## _Decision guide, idiom catalogue, performance notes, and pitfalls_

~~~admonish info title="What You'll Learn"
- When to reach for the static-method style versus the fluent-builder style.
- Common patterns and idioms (composition shortcuts, conditional updates, error handling).
- Performance considerations and integration tactics for existing Java codebases.
- Common pitfalls and how to avoid them.
~~~

This page is the lookup shelf for the Fluent API. The narrative explanation, side-by-side comparison, and worked examples live in [Fluent API](fluent_api.md); use this page when you already know the style choices and need a quick answer.

---

## When to Use Each Style

### Use Static Methods When:

**Performing simple, one-off operations**
```java
// Clear and concise
String name = OpticOps.get(person, PersonLenses.name());
```

**Chaining is not needed**
```java
// Direct transformation
Person older = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

**Performance is critical** (slightly less object allocation)

### Use Fluent Builders When:

**Building complex workflows**
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

**IDE autocomplete is important** (great for discovery)

**Code reviews matter** (explicit intent)

**Teaching or documentation** (self-explanatory)

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
// Good: Compose once, use many times
Lens<Order, BigDecimal> orderToTotalPrice =
    OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal())
        .andThen(someAggregationLens);

orders.stream()
    .map(order -> OpticOps.getAll(order, orderToTotalPrice))
    .collect(toList());

// Avoid: Recomposing in loop
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

### Don't: Call `get` then `set`

```java
// Inefficient - two traversals
int age = OpticOps.get(person, PersonLenses.age());
Person updated = OpticOps.set(person, PersonLenses.age(), age + 1);
```

### Do: Use `modify`

```java
// Efficient - single traversal
Person updated = OpticOps.modify(person, PersonLenses.age(), a -> a + 1);
```

### Don't: Recompose optics unnecessarily

```java
// Bad - composing in a loop
for (Order order : orders) {
    var itemPrices = OrderTraversals.items()
        .andThen(OrderItemLenses.price().asTraversal());  // Composed each iteration!
    process(OpticOps.getAll(order, itemPrices));
}
```

### Do: Compose once, reuse

```java
// Good - compose outside loop
var itemPrices = OrderTraversals.items()
    .andThen(OrderItemLenses.price().asTraversal());

for (Order order : orders) {
    process(OpticOps.getAll(order, itemPrices));
}
```

---

~~~admonish tip title="Further Reading"
- **Martin Fowler**: [Fluent Interface](https://martinfowler.com/bliki/FluentInterface.html) - The original pattern description
- **Haskell Lens**: [Lens Tutorial](https://hackage.haskell.org/package/lens-tutorial) - Deeper theoretical understanding
~~~

~~~admonish info title="Hands-On Learning"
Practice the fluent API in [Tutorial 09: Fluent Optics API](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial09_FluentOpticsAPI.java) (7 exercises, ~10 minutes).
~~~

---

**Next Steps:**

- [Free Monad DSL for Optics](free_monad_dsl.md) - Build composable programs
- [Optic Interpreters](interpreters.md) - Multiple execution strategies
- [Advanced Patterns](composing_optics.md) - Complex real-world scenarios


---

**Previous:** [Fluent API](fluent_api.md)
**Next:** [Integration and Recipes](ch5_intro.md)

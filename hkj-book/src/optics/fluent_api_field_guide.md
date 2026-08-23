# Fluent API Field Guide

## _Style choice, idiom catalogue, performance notes, and pitfalls_

~~~admonish info title="What You'll Learn"
- When to reach for the static-method style and when for the fluent builders
- The idioms that recur: pipelines, conditional updates, filtered bulk operations
- How optics and the Stream API fit together
- The performance rule that actually matters, and the pitfalls worth knowing
~~~

This page is the lookup shelf for the Fluent API. The narrative, the side-by-side comparison and the worked validation examples live in [Fluent API](fluent_api.md); come here when you already know the style choices and want a quick answer.

---

## Which Style?

**Static methods** for short, one-off operations, where naming the operation twice would be noise:

<!-- verify -->
```java
String name = OpticOps.get(alice, PersonLenses.name());
Person older = OpticOps.modify(alice, PersonLenses.age(), a -> a + 1);
```

**Fluent builders** when the optic expression is long, when the call site is teaching material, or when you want the IDE to enumerate what is possible:

<!-- verify -->
```java
List<String> bigOrders =
    OpticOps.getting(order).allThrough(OrderTraversals.items()).stream()
        .filter(item -> item.quantity() > 10)
        .map(OrderItem::productId)
        .collect(toList());
```

Note which builder that is: `getting(...)` reads values out, so it is the one that returns a `List` you can stream. `querying(...)` answers questions (`anyMatch`, `count`, `findFirst`) and never hands you the whole collection, though `findFirst` returns at most one element.

---

## Idioms

### Pipeline Transformations

Each stage takes the previous result, so a multi-step transformation is a sequence of named locals rather than one unreadable expression:

<!-- verify -->
```java
Order discounted =
    OpticOps.modifying(order).allThrough(Fixture.itemPrices, price -> price.multiply(new BigDecimal("0.9")));

Order rounded =
    OpticOps.modifying(discounted)
        .allThrough(Fixture.itemPrices, price -> price.setScale(2, java.math.RoundingMode.HALF_UP));
```

### Conditional Updates

Read once, decide, then write. `modify` is not the tool when the *decision* depends on the value and the update targets a different field:

<!-- verify -->
```java
Person classified =
    OpticOps.get(alice, PersonLenses.age()) >= 18
        ? OpticOps.set(alice, PersonLenses.status(), "ADULT")
        : alice;
```

### Bulk Operations, Narrowed by a Predicate

`filtered` narrows the traversal itself, so the update only reaches the elements that qualify and no membership test leaks into the modification function:

<!-- verify -->
```java
Traversal<Team, Player> topPerformers =
    TeamTraversals.players().filtered(player -> player.score() >= 90);

Team starred =
    Traversals.modify(
        topPerformers.andThen(PlayerLenses.status().asTraversal()), status -> "STAR", team);

List<Player> stars = OpticOps.getAll(team, topPerformers);
```

### Aggregation

A `Fold` collapses every focused element through a `Monoid`, which is usually clearer than collecting to a list and reducing. A `Traversal` is not itself a `Fold`, so it converts with `asFold()`:

<!-- verify -->
```java
int totalQuantity =
    OrderTraversals.items()
        .andThen(OrderItemLenses.quantity().asTraversal())
        .asFold()
        .foldMap(Monoids.integerAddition(), q -> q, order);
// 15
```

---

## Working with Existing Java Code

### Streams

Optics get the values out; the Stream API does the rest:

<!-- verify -->
```java
List<String> highScorerNames =
    OpticOps.getting(team).allThrough(TeamTraversals.players()).stream()
        .filter(p -> p.score() > 90)
        .map(p -> OpticOps.get(p, PlayerLenses.name()))
        .collect(toList());
```

### Optional

<!-- verify -->
```java
Optional<Person> maybePerson = Fixture.findPerson("alice");

Optional<Integer> age = maybePerson.map(p -> OpticOps.get(p, PersonLenses.age()));

Person updated =
    maybePerson
        .map(p -> OpticOps.modify(p, PersonLenses.age(), a -> a + 1))
        .orElse(new Person("Unknown", 0, "UNKNOWN"));
```

---

## Performance

**Object allocation.** Static methods allocate only the result. Builders allocate one short-lived builder as well. The difference is real and almost never the reason your code is slow; avoid builders in a tight inner loop and stop thinking about it.

**Optic composition is the one that counts.** Composing an optic walks the chain and builds new objects, so composing inside a loop repeats that work per iteration:

<!-- verify -->
```java
// Compose once, above the loop
Traversal<Order, BigDecimal> prices =
    OrderTraversals.items().andThen(OrderItemLenses.price().asTraversal());

for (Order o : Fixture.orders) {
  List<BigDecimal> values = OpticOps.getAll(o, prices);
}
```

---

## Pitfalls

**Do not `get` then `set` when you mean `modify`.** Two traversals where one would do, and a race between them if anything else can touch the structure:

<!-- verify -->
```java
// Instead of get-then-set
Person older = OpticOps.modify(alice, PersonLenses.age(), a -> a + 1);
```

**Do not recompose optics in a loop.** As above: hoist the composition.

**Do not reach for `querying` when you want the elements.** `querying` answers `anyMatch`, `allMatch`, `findFirst`, `count` and `isEmpty`; only `findFirst` returns a value, and only one. To get them all, use `getting(...).allThrough(...)`.

**Do not expect an instance `modify` on a bare `Traversal`.** Reads and writes go through the `Traversals` utility, or through `OpticOps`, or through a `TraversalPath` from the [Focus DSL](focus_dsl.md), which does carry `getAll` and `modifyAll` directly.

---

~~~admonish info title="Key Takeaways"
* **Static for short, builders for long.** They compile to the same operations, so the choice is about the reader.
* **`getting` returns values, `querying` returns answers.** Reaching for `querying` when you wanted the elements is the mistake this page exists to prevent.
* **Compose optics once.** Hoisting the composition out of a loop is the performance rule that matters; builder allocation is not.
* **`filtered` belongs on the optic, not in the function.** The predicate then travels with the path and cannot be forgotten at a call site.
* **Aggregate with a `Monoid`.** `asFold().foldMap(...)` beats collecting a list and reducing it.
~~~

~~~admonish info title="Hands-On Learning"
Practice the fluent API in [Tutorial 09: Fluent Optics API](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial09_FluentOpticsAPI.java) (7 exercises, ~10 minutes).
~~~

~~~admonish tip title="See Also"
- [Fluent API](fluent_api.md): the narrative version, with the four validation strategies
- [Composing Optics](composing_optics.md): building the optics this page consumes
- [Free Monad DSL](free_monad_dsl.md): when the plan itself is the artefact
~~~

~~~admonish tip title="Further Reading"
- **Martin Fowler**: [Fluent Interface](https://martinfowler.com/bliki/FluentInterface.html): the original description of the pattern
~~~

---

**Previous:** [Fluent API](fluent_api.md)
**Next:** [Integration and Recipes](ch5_intro.md)

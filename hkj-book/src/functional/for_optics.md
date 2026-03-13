# Optics Integration

~~~admonish info title="For-Optics Integration"
The For comprehension integrates natively with the [Optics](../optics/optics_intro.md) library, providing first-class support for lens-based extraction, prism-based pattern matching, and traversal-based iteration within monadic comprehensions.
~~~

The `For` builder provides two optic-aware operations that extend the comprehension capabilities:

## Extracting Values with `focus()`

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

## Pattern Matching with `match()`

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

---

**Previous:** [Traverse Within Comprehensions](for_traverse.md) | **Next:** [MTL & ForState Bridge](for_mtl.md)

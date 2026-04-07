# Optics Integration

> "To see a World in a Grain of Sand,
> And a Heaven in a Wild Flower,
> Hold Infinity in the palm of your hand,
> And Eternity in an hour."
>
> -- William Blake, _Auguries of Innocence_

~~~admonish info title="What You'll Learn"
- How `focus()` and `match()` let you extract and filter data within comprehensions
- Using `through(Iso)` for type-safe value conversion
- ForState's traversal-aware bulk operations: `traverseOver` and `modifyThrough`
- Iso-integrated state updates with `modifyVia` and `updateVia`
- Bulk operations with `ForTraversal` and position-aware traversals with `ForIndexed`
~~~

~~~admonish example title="See Example Code"
- [EnhancedOpticsIntegrationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/EnhancedOpticsIntegrationExample.java)
- [ForStateExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForStateExample.java) -- ForState basics
~~~

Comprehensions give you sequential, composable workflows. Optics give you precise, composable data access. When the two meet, something powerful happens: you can reach into nested structures, filter on variant types, convert between representations, and transform entire collections -- all within a single fluent pipeline.

This chapter builds a payroll processing scenario step by step. Each section introduces an optics operation that solves a specific part of the problem, so by the end you will have seen how all the pieces fit together.

---

## Seeing Into Your Data

The first challenge in any data pipeline is getting at the values you need. In a traditional approach you would destructure records, call getters, and scatter intermediate variables across your method. Within a comprehension, `focus()` and `match()` let you do this declaratively -- each extracted value is accumulated alongside the original, available to every subsequent step.

### Extracting Nested Values with `focus()`

Suppose you have a list of employees, each with a nested department. You want to produce a summary that includes both the employee's name and their department:

```java
record Employee(String name, int salaryInCents, Department department) {}
record Department(String name, int budgetInCents) {}

var employees = List.of(
    new Employee("Alice", 80000, new Department("Engineering", 500000)),
    new Employee("Bob", 90000, new Department("Engineering", 500000))
);

Kind<ListKind.Witness, String> result =
    For.from(listMonad, LIST.widen(employees))
        .focus(emp -> emp.department().name())
        .yield((emp, deptName) -> emp.name() + " works in " + deptName);

// Result: ["Alice works in Engineering", "Bob works in Engineering"]
```

The `focus()` call does not introduce a new monadic binding -- it simply extracts a value from what is already in scope and adds it to the tuple. Both the original employee and the extracted department name are available in `yield()`.

### Filtering with Pattern Matching via `match()`

Not all data fits neatly into a single type. When you need to work with sum types -- sealed interfaces, variants, or optional shapes -- `match()` provides prism-like pattern matching directly within the comprehension. Elements that do not match are filtered out (with `List`) or short-circuit the computation (with `Maybe`):

```java
sealed interface PayrollResult permits Paid, Skipped {}
record Paid(String employeeName, int amount) implements PayrollResult {}
record Skipped(String reason) implements PayrollResult {}

Prism<PayrollResult, Paid> paidPrism = Prism.of(
    r -> r instanceof Paid p ? Optional.of(p) : Optional.empty(),
    p -> p
);

List<PayrollResult> results = List.of(
    new Paid("Alice", 80000),
    new Skipped("Bob on leave"),
    new Paid("Charlie", 75000)
);

Kind<ListKind.Witness, String> receipts =
    For.from(listMonad, LIST.widen(results))
        .match(paidPrism)
        .yield((result, paid) -> paid.employeeName() + ": " + paid.amount());

// Result: ["Alice: 80000", "Charlie: 75000"] -- skipped entries are filtered out
```

With `Maybe`, a failed match short-circuits to `Nothing`:

```java
Kind<MaybeKind.Witness, String> result =
    For.from(maybeMonad, MAYBE.just((PayrollResult) new Skipped("on leave")))
        .match(paidPrism)
        .yield((r, paid) -> paid.employeeName());

// Result: Nothing -- the match failed
```

~~~admonish tip title="Chaining focus() and match()"
Both operations compose naturally. Extract a nested value, match on its shape, then guard on the result:

```java
For.from(listMonad, LIST.widen(items))
    .focus(item -> item.category())
    .match(premiumCategoryPrism)
    .when(t -> t._3().discount() > 0.1)
    .yield((item, category, premium) -> item.name());
```
~~~

---

## Working in the Right Units

> "Nothing is true, everything is permitted."
>
> -- William Burroughs, _Cities of the Red Night_

A salary stored as `int` cents and a budget reasoned about in dollars are the same information in different clothes. Converting back and forth is tedious and error-prone -- you end up with `/ 100.0` and `* 100` calls scattered throughout your code. An [Iso](../optics/iso.md) formalises this equivalence, and comprehensions provide three ways to use it.

### Converting Within a For Comprehension: `through(Iso)`

The `through()` method converts the currently bound value via an Iso and keeps both representations in scope. This is available on `MonadicSteps1` and `FilterableSteps1`:

```java
Iso<Integer, Double> centsToDollars =
    Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

Kind<IdKind.Witness, String> result =
    For.from(idMonad, Id.of(50000))
        .through(centsToDollars)
        .yield((cents, dollars) ->
            "Budget: " + cents + " cents = $" + dollars);

// Result: "Budget: 50000 cents = $500.0"
```

Both the original value and the Iso-converted value are available in subsequent steps and in the final `yield()`. This eliminates manual conversion calls scattered through your comprehension.

When used with a `MonadZero`, `through()` returns a `FilterableSteps2`, preserving the ability to apply `when()` guards on the converted values:

```java
Kind<ListKind.Witness, String> result =
    For.from(listMonad, LIST.widen(temperatures))
        .through(celsiusToFahrenheitIso)
        .when(t -> t._2().value() > 50.0)  // filter on Fahrenheit value
        .yield((celsius, fahrenheit) -> celsius.value() + "C");
```

~~~admonish tip title="When to Use through(Iso)"
Use `through()` when you need both the original and converted values in scope. If you only need the converted value, a simple `let(t -> iso.get(t))` suffices. At higher arities (Steps2 and above), `let` is recommended since `through()` is only available on Steps1.
~~~

### Modifying State Through an Iso: `modifyVia`

Within a `ForState` workflow, `modifyVia(lens, iso, modifier)` lets you modify a field in a different representation. It extracts the field via the lens, converts through the Iso, applies your modifier in the converted type, and converts back:

```java
Iso<Integer, Double> centsToDollars =
    Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

// Give everyone a 10% raise -- reasoning in dollars, storing in cents
Kind<IdKind.Witness, Department> result =
    ForState.withState(idMonad, Id.of(department))
        .modifyVia(budgetLens, centsToDollars, dollars -> dollars * 1.1)
        .yield();
```

The flow is: `lens.get` &#8594; `iso.get` &#8594; `modifier` &#8594; `iso.reverseGet` &#8594; `lens.set`. You never touch the internal representation directly.

### Setting State Through an Iso: `updateVia`

When you want to set a field to a specific value in the converted representation, use `updateVia(lens, iso, value)`:

```java
// Set budget to exactly $750.00 (stored internally as 75000 cents)
ForState.withState(idMonad, Id.of(department))
    .updateVia(budgetLens, centsToDollars, 750.0)
    .yield();
```

~~~admonish tip title="modifyVia vs updateVia"
- **`modifyVia`** transforms the existing value: read, convert, modify, convert back, write
- **`updateVia`** replaces with a new value: convert the new value back, write

Both honour the Iso's round-trip property, keeping internal and external representations consistent.
~~~

---

## Transforming Collections in Place

With extraction, filtering, and conversion covered, the remaining challenge is bulk operations -- applying a transformation to every element in a collection, potentially with effects. ForState provides two operations for this, and the standalone `ForTraversal` and `ForIndexed` builders offer additional flexibility.

### Effectful Traversal with `traverseOver`

`traverseOver(traversal, function)` applies an effectful function to each element focused by the traversal. The monad governs how effects compose -- with `Maybe`, a single failure short-circuits the entire operation:

```java
Traversal<List<Employee>, Employee> empTraversal = Traversals.forList();

// Validate all employees have positive salaries
Kind<MaybeKind.Witness, List<Employee>> result =
    ForState.withState(maybeMonad, MAYBE.just(employees))
        .traverseOver(empTraversal,
            emp -> emp.salaryInCents() > 0 ? MAYBE.just(emp) : MAYBE.nothing())
        .yield();
// Just(employees) if all valid, Nothing if any fails
```

The key difference from `traverse(lens, traversal, function)` is that `traverseOver` operates directly on the state type itself. Use `traverseOver` when the state *is* the collection; use `traverse` when the collection is a field within a larger state record.

### Pure Traversal with `modifyThrough`

When your transformation is pure -- no validation, no effects, no possibility of failure -- use `modifyThrough(traversal, modifier)`. It uses the Identity monad internally, so there is no monadic overhead:

```java
// Uppercase all employee names (pure operation, no effect needed)
Kind<IdKind.Witness, List<Employee>> result =
    ForState.withState(idMonad, Id.of(employees))
        .modifyThrough(empTraversal,
            emp -> new Employee(emp.name().toUpperCase(), emp.salaryInCents()))
        .yield();
```

The three-argument form `modifyThrough(traversal, lens, modifier)` composes a traversal with a lens to modify a nested field within each element:

```java
// Increase every employee's salary by 500
ForState.withState(idMonad, Id.of(employees))
    .modifyThrough(empTraversal, salaryLens, s -> s + 500)
    .yield();
```

This is equivalent to manually composing `traversal` with `lens.asTraversal()`, but more concise and intention-revealing.

### Choosing Between traverseOver and modifyThrough

```
+-------------------------------------------------------------------+
|                    ForState Traversal Operations                   |
+-------------------------------------------------------------------+
|                                                                   |
|  traverseOver(traversal, f)    modifyThrough(traversal, modifier) |
|  +------------------------+   +-------------------------------+   |
|  | Effectful: A -> M<A>   |   | Pure: A -> A                  |   |
|  | Uses monad directly    |   | Uses Identity monad internally|   |
|  | Can fail/short-circuit |   | Always succeeds               |   |
|  +------------------------+   +-------------------------------+   |
|                                                                   |
|  modifyThrough(traversal, lens, modifier)                         |
|  +-------------------------------+                                |
|  | Composed: focus on nested     |                                |
|  | field within each element     |                                |
|  +-------------------------------+                                |
|                                                                   |
+-------------------------------------------------------------------+
```

The rule of thumb: if your function returns `Kind<M, A>`, use `traverseOver`. If it returns plain `A`, use `modifyThrough`.

---

## Bulk Operations with ForTraversal

For operations over multiple elements within a structure, `ForTraversal` provides a standalone fluent API built around a [Traversal](../optics/traversals.md). This is useful when you want to apply a sequence of modifications and filters without threading state through a `ForState` workflow:

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

Sometimes the transformation depends on *where* an element sits, not just *what* it is. `ForIndexed` extends traversal comprehensions with index awareness:

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

Use `filter()` with a `BiPredicate` to filter on both position and value:

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

## Putting It Together

The real power of optics integration emerges when you combine these operations in a single pipeline. Here is a payroll workflow that validates employees, normalises their names, gives everyone a raise, and increases the department budget -- all in one composable chain:

```java
MaybeMonad maybeMonad = MaybeMonad.INSTANCE;
Iso<Integer, Double> centsToDollars =
    Iso.of(cents -> cents / 100.0, dollars -> (int) (dollars * 100));

Kind<MaybeKind.Witness, Department> result =
    ForState.withState(maybeMonad, MAYBE.just(department))
        // Validate: every employee must have a positive salary
        .traverse(staffLens, Traversals.forList(),
            emp -> emp.salaryInCents() > 0 ? MAYBE.just(emp) : MAYBE.nothing())
        // Normalise: uppercase all names (pure, no effects)
        .modifyThrough(Traversals.forList(), empNameLens, String::toUpperCase)
        // Raise: increase budget by 10%, reasoning in dollars
        .modifyVia(budgetLens, centsToDollars, dollars -> dollars * 1.1)
        .yield();
```

Each operation addresses a different concern -- validation, transformation, unit conversion -- yet they compose into a single, readable pipeline. The monad handles failure propagation; the optics handle data access. Neither concern leaks into the other.

---

**Previous:** [Traverse Within Comprehensions](for_traverse.md) | **Next:** [MTL & ForState Bridge](for_mtl.md)

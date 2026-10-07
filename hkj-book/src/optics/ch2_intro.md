<!-- description: Traversals and folds for zero or more values, such as every element of a list inside a record, and read-only getters and write-only setters. -->

# Collections

> *"The world is full of abandoned meanings."*
>
> – Don DeLillo, *White Noise*

---

Read this group when you want to know what the Focus DSL's `.each()` does underneath, or need a traversal the generated paths do not give you. Until then, you can skip it.

Single values are straightforward enough. The challenge arrives when you need to handle *many* of them: a discount on every price in an order means a stream, a map, a collector, and the suspicion that there must be a better way. There is. Here is the destination, before any theory: one reusable path from a league to every player's score, and a bulk update through it. Every line compiles against the real library on every build:

<!-- verify -->
```java
var everyScore = LeagueTraversals.teams()
    .andThen(TeamTraversals.players())
    .andThen(PlayerLenses.score());

League bonus = Traversals.modify(everyScore, score -> score + 5, league);
// Traversals.getAll(everyScore, league) -> [100, 90, 110, 120]
// Traversals.getAll(everyScore, bonus)  -> [105, 95, 115, 125]
// every team and player is rebuilt for you; league itself is untouched
```

~~~admonish tip title="Why this matters"
A stream pipeline that rebuilds nested records is code you write again for every operation. A composed traversal is a value: define the path once and reuse it for pure updates, for queries, and (through [`modifyF`](../glossary/optics.md#modifyf)) for validating or asynchronous passes over every element. And when a path should never write, `asFold()` or a Getter says so in the type, so read-only intent is checked by the compiler rather than promised in a comment.
~~~

A **Traversal** focuses zero or more values, and reads and writes them all. A **Fold** is its read-only cousin, for queries, searches and aggregates, so code that must not modify data says so in its type. A **Getter** reads exactly one value and never writes, and a **Setter** writes without reading. [Optic Capabilities](optic_capabilities.md) lists which operations each type declares, and where a `Traversal`'s reads and writes live instead.

---

## Pages in this group

1. [Traversals](traversals.md): Bulk operations on collection elements
2. [Folds](folds.md): Read-only queries and monoid-based aggregation
3. [Getters](getters.md): Read-only focus on a single value
4. [Setters](setters.md): Write-only modification without reading
5. [Common Data Structures](common_data_structure_traversals.md): Ready-made traversals for `List`, `Map`, `Set` and more
6. [Limiting Traversals](limiting_traversals.md): The first N elements, a slice, or chosen indices
7. [List Decomposition](list_decomposition.md): Cons and snoc patterns for lists

~~~admonish info title="Hands-On Learning"
Practise this group in the [Traversals & Practice Journey](../tutorials/optics/traversals_journey.md) (28 exercises).
~~~

---

**Previous:** [Profunctor Optics: Recipes](profunctor_optics_recipes.md)
**Next:** [Traversals](traversals.md)

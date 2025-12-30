# List Decomposition: Cons and Snoc Patterns

> _"Begin at the beginning," the King said gravely, "and go on till you come to the end: then stop."_
>
> – Lewis Carroll, *Alice's Adventures in Wonderland*

~~~admonish info title="What You'll Learn"
- How to decompose lists using the classic functional programming patterns cons and snoc
- When to use head/tail decomposition versus init/last decomposition
- Convenience affines for accessing first and last elements directly
- Stack-safe operations for processing arbitrarily large lists
- Integration with the Focus DSL for fluent list manipulation
~~~

~~~admonish title="Hands On Practice"
[Tutorial15_ListPrisms.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial15_ListPrisms.java)
~~~

~~~admonish example title="See Example Code"
[ListDecompositionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ListDecompositionExample.java)
~~~

Lists are fundamental to programming. Yet Java's standard library offers only indexed access and streams for working with them. Functional programming languages like Haskell and Scala provide more expressive patterns: decomposing a list into its first element and the rest (*cons*), or its beginning and final element (*snoc*).

Higher-Kinded-J brings these patterns to Java through the `ListPrisms` utility class, enabling pattern matching on list structure using optics.

---

## The Two Decomposition Patterns

Lists can be viewed from either end. This simple observation leads to two complementary decomposition strategies:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          CONS (head/tail)                               │
│                                                                         │
│   List:     [ A  |  B  |  C  |  D  |  E ]                              │
│               ↓     └──────────┬────────┘                               │
│             head            tail                                        │
│                                                                         │
│   Pair.of(A, [B, C, D, E])                                             │
│                                                                         │
│   "Begin at the beginning..."                                           │
└─────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────┐
│                          SNOC (init/last)                               │
│                                                                         │
│   List:     [ A  |  B  |  C  |  D  |  E ]                              │
│              └──────────┬────────┘    ↓                                 │
│                       init          last                                │
│                                                                         │
│   Pair.of([A, B, C, D], E)                                             │
│                                                                         │
│   "...and go on till you come to the end"                               │
└─────────────────────────────────────────────────────────────────────────┘
```

Both patterns return `Optional.empty()` for empty lists, making them safe to use without null checks.

---

## The ListPrisms Utility Class

The `ListPrisms` class provides prisms and affines for list decomposition. It offers both traditional functional programming names and Java-friendly aliases:

| FP Name | Java Alias | Type | Description |
|---------|------------|------|-------------|
| `cons()` | `headTail()` | `Prism<List<A>, Pair<A, List<A>>>` | Decomposes as (first, rest) |
| `snoc()` | `initLast()` | `Prism<List<A>, Pair<List<A>, A>>` | Decomposes as (all-but-last, last) |
| `head()` | – | `Affine<List<A>, A>` | Focus on first element |
| `last()` | – | `Affine<List<A>, A>` | Focus on last element |
| `tail()` | – | `Affine<List<A>, List<A>>` | Focus on all but first |
| `init()` | – | `Affine<List<A>, List<A>>` | Focus on all but last |
| `empty()` | – | `Prism<List<A>, Unit>` | Matches empty lists |

![list_prisms.svg](../images/puml/list_prisms.svg)

---

## Pattern Matching with Cons

The `cons()` prism views a non-empty list as a pair: its first element and the remaining elements.

```java
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.ListPrisms;
import org.higherkindedj.optics.indexed.Pair;

// Create the prism
Prism<List<String>, Pair<String, List<String>>> cons = ListPrisms.cons();

// Decompose a list
List<String> names = List.of("Alice", "Bob", "Charlie");
Optional<Pair<String, List<String>>> result = cons.getOptional(names);
// result = Optional.of(Pair.of("Alice", ["Bob", "Charlie"]))

// Empty lists return Optional.empty()
Optional<Pair<String, List<String>>> empty = cons.getOptional(List.of());
// empty = Optional.empty()
```

### Building Lists with Cons

The prism works in both directions. Use `build` to construct a list from a head and tail:

```java
// Prepend an element
List<String> built = cons.build(Pair.of("New", List.of("List", "Here")));
// built = ["New", "List", "Here"]

// This is equivalent to:
// new ArrayList<>(List.of("New")) + tail
```

### Modifying the Head

Since cons is a prism, you can use `modify` to transform the first element:

```java
List<String> modified = cons.modify(
    pair -> Pair.of(pair.first().toUpperCase(), pair.second()),
    names
);
// modified = ["ALICE", "Bob", "Charlie"]
```

---

## Pattern Matching with Snoc

The `snoc()` prism (cons spelled backwards) views a non-empty list from the other end: all elements except the last, paired with the last element.

```java
Prism<List<Integer>, Pair<List<Integer>, Integer>> snoc = ListPrisms.snoc();

List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Optional<Pair<List<Integer>, Integer>> result = snoc.getOptional(numbers);
// result = Optional.of(Pair.of([1, 2, 3, 4], 5))
```

### Building Lists with Snoc

Use `build` to append an element to a list:

```java
List<Integer> appended = snoc.build(Pair.of(List.of(1, 2, 3), 4));
// appended = [1, 2, 3, 4]
```

### Modifying the Last Element

```java
List<Integer> modified = snoc.modify(
    pair -> Pair.of(pair.first(), pair.second() * 10),
    numbers
);
// modified = [1, 2, 3, 4, 50]
```

---

## Convenience Affines: Head, Last, Tail, Init

When you need direct access to just one part of the decomposition, the convenience affines and prisms provide a cleaner API:

### Accessing the First Element

```java
Affine<List<String>, String> head = ListPrisms.head();

Optional<String> first = head.getOptional(List.of("Alice", "Bob"));
// first = Optional.of("Alice")

// Modify just the first element
List<String> modified = head.modify(String::toUpperCase, List.of("alice", "bob"));
// modified = ["ALICE", "bob"]

// Setting on an empty list creates a single-element list
List<String> created = head.set("New", List.of());
// created = ["New"]
```

### Accessing the Last Element

```java
Affine<List<Integer>, Integer> last = ListPrisms.last();

Optional<Integer> lastElem = last.getOptional(List.of(1, 2, 3, 4, 5));
// lastElem = Optional.of(5)

List<Integer> modified = last.modify(x -> x * 10, List.of(1, 2, 3));
// modified = [1, 2, 30]
```

### Accessing the Tail and Init

```java
Affine<List<String>, List<String>> tail = ListPrisms.tail();
Optional<List<String>> t = tail.getOptional(List.of("a", "b", "c"));
// t = Optional.of(["b", "c"])

Affine<List<Integer>, List<Integer>> init = ListPrisms.init();
Optional<List<Integer>> i = init.getOptional(List.of(1, 2, 3, 4, 5));
// i = Optional.of([1, 2, 3, 4])
```

---

## Matching Empty Lists

The `empty()` prism complements cons and snoc by matching only empty lists:

```java
Prism<List<String>, Unit> empty = ListPrisms.empty();

boolean isEmpty = empty.matches(List.of());       // true
boolean isNotEmpty = empty.matches(List.of("a")); // false
```

This enables complete pattern matching on list structure:

```java
public <A> String describeList(List<A> list) {
    if (ListPrisms.<A>empty().matches(list)) {
        return "Empty list";
    }

    Pair<A, List<A>> headTail = ListPrisms.<A>cons()
        .getOptional(list)
        .orElseThrow();

    if (headTail.second().isEmpty()) {
        return "Single element: " + headTail.first();
    }

    return "List starting with " + headTail.first() +
           " followed by " + headTail.second().size() + " more";
}
```

---

## Stack-Safe Operations

For processing very large lists, `ListPrisms` provides trampoline-based operations that avoid stack overflow:

| Method | Description |
|--------|-------------|
| `foldRight(list, initial, f)` | Right-associative fold |
| `mapTrampoline(list, f)` | Transform each element |
| `filterTrampoline(list, predicate)` | Keep matching elements |
| `reverseTrampoline(list)` | Reverse the list |
| `flatMapTrampoline(list, f)` | Map and flatten |
| `zipWithTrampoline(list1, list2, f)` | Combine two lists element-wise |
| `takeTrampoline(list, n)` | Take first n elements |
| `dropTrampoline(list, n)` | Drop first n elements |

### Example: Processing a Million Elements

```java
// These operations are safe for arbitrarily large lists
List<Integer> largeList = IntStream.range(0, 1_000_000).boxed().toList();

// Sum using stack-safe fold
Integer sum = ListPrisms.foldRight(largeList, 0, Integer::sum);

// Transform without stack overflow
List<String> strings = ListPrisms.mapTrampoline(largeList, Object::toString);

// Filter safely
List<Integer> evens = ListPrisms.filterTrampoline(largeList, n -> n % 2 == 0);
```

---

## Focus DSL Integration

The list decomposition prisms compose with the Focus DSL using `.via()`:

```java
// Given a record with a list field
@GenerateLenses
@GenerateFocus
record Container(String name, List<Item> items) {}

// Navigate to the first item using ListPrisms
AffinePath<Container, Item> firstItem = ContainerFocus.items()
    .via(ListPrisms.head());     // Compose with head affine

Optional<Item> first = firstItem.getOptional(container);

// Navigate to the last item
AffinePath<Container, Item> lastItem = ContainerFocus.items()
    .via(ListPrisms.last());

// Decompose with cons pattern
TraversalPath<Container, Pair<Item, List<Item>>> consPath = ContainerFocus.items()
    .via(ListPrisms.cons());

// You can also compose directly on the traversal
TraversalPath<Container, Item> allItems = ContainerFocus.items();
AffinePath<Container, Item> firstViaHeadOption = allItems.headOption();
```

### Available ListPrisms for Composition

| ListPrisms Method | Type | Use with `.via()` |
|-------------------|------|-------------------|
| `ListPrisms.head()` | `Affine<List<A>, A>` | Focus on first element |
| `ListPrisms.last()` | `Affine<List<A>, A>` | Focus on last element |
| `ListPrisms.tail()` | `Affine<List<A>, List<A>>` | Focus on all but first |
| `ListPrisms.init()` | `Affine<List<A>, List<A>>` | Focus on all but last |
| `ListPrisms.cons()` | `Prism<List<A>, Pair<A, List<A>>>` | Decompose as (head, tail) |
| `ListPrisms.snoc()` | `Prism<List<A>, Pair<List<A>, A>>` | Decompose as (init, last) |
| `ListPrisms.empty()` | `Prism<List<A>, Unit>` | Match empty lists only |

---

## When to Use Each Pattern

### Use Cons (head/tail) When:

- Processing lists from front to back
- Implementing recursive algorithms that peel off the first element
- Building lists by prepending elements
- Pattern matching on "first and rest" structure

### Use Snoc (init/last) When:

- Processing lists from back to front
- Algorithms that need the final element
- Building lists by appending elements
- Pattern matching on "everything before and last" structure

### Use Head/Last Affines When:

- You only need direct access to the first or last element
- Modifying endpoints without caring about the rest
- Cleaner code when you don't need the full decomposition

---

## Composition with Other Optics

List prisms compose naturally with other optics for deep list manipulation:

```java
// Focus on the name of the first player in a team
Affine<Team, String> firstPlayerName =
    TeamLenses.players().asAffine()
        .andThen(ListPrisms.head())
        .andThen(PlayerLenses.name().asAffine());

// Modify the score of the last player
Team updated = TeamLenses.players().asAffine()
    .andThen(ListPrisms.last())
    .andThen(PlayerLenses.score().asAffine())
    .modify(score -> score + 10, team);
```

---

~~~admonish info title="Key Takeaways"
* **Cons and snoc** are complementary patterns for decomposing lists from either end
* **ListPrisms** provides both FP-style names (`cons`, `snoc`) and Java-friendly aliases (`headTail`, `initLast`)
* **Convenience affines** (`head`, `last`) offer direct access when you only need one element
* **Empty list handling** is safe by design: prisms return `Optional.empty()` for empty lists
* **Stack-safe operations** using trampolines enable processing of arbitrarily large lists
* **Focus DSL integration** provides fluent navigation into list structure
~~~

~~~admonish info title="Hands-On Learning"
Practice list decomposition patterns in [Tutorial 15: List Prisms](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial15_ListPrisms.java) (19 exercises, ~12 minutes).
~~~

---

~~~admonish tip title="See Also"
- [Prisms](prisms.md) - The underlying optic type for pattern matching
- [Affines](affine.md) - Optics for "zero or one" targets
- [Focus DSL](focus_dsl.md) - Fluent API for building optic paths
- [Traversals](traversals.md) - Working with all list elements
- [Trampoline](../monads/trampoline_monad.md) - Stack-safe recursion
~~~

---

**Previous:** [Limiting Traversals](limiting_traversals.md)
**Next:** [Filtered Optics](../optics/filtered_optics.md)

# Each: Canonical Element-Wise Traversal

## _Simplicity is the ultimate sophistication_

> *"Simplicity is the ultimate sophistication."*
>
> – Leonardo da Vinci

~~~admonish info title="What You'll Learn"
- How the `Each` type class provides canonical traversals for container types
- Using `EachInstances` for Java collections (List, Set, Map, Optional, arrays, Stream, String)
- Using `EachExtensions` for HKT types (Maybe, Either, Try, Validated)
- Indexed traversal support via `eachWithIndex()` for position-aware operations
- Integration with Focus DSL using `.each(Each)` method
- Creating custom `Each` instances for your own types
- When to use `Each` vs direct `Traversal` creation
~~~

~~~admonish title="Example Code"
[EachInstancesExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/EachInstancesExample.java)
~~~

Every container type has a natural way to traverse its elements. A `List` iterates from first to last. A `Map` visits each value. An `Optional` yields zero or one element. The **Each** type class captures this canonical traversal pattern, providing a uniform interface across diverse container types.

Rather than writing traversal logic repeatedly for each container, `Each` gives you a single method: `each()`. Call it, receive a `Traversal`, and you're ready to read or modify all elements.

---

## The Problem: Repeated Traversal Patterns

When working with different container types, you often need similar traversal operations:

```java
// List: traverse all elements
Traversal<List<String>, String> listTraversal = Traversals.forList();

// Map: traverse all values
Traversal<Map<String, Integer>, Integer> mapTraversal = Traversals.forMapValues();

// Optional: traverse the value if present
Traversal<Optional<String>, String> optTraversal = Traversals.forOptional();

// Each time you need a traversal, you must know which factory method to use
```

With `Each`, the pattern becomes uniform:

```java
// Get the canonical traversal for any container
Traversal<List<String>, String> listTrav = EachInstances.<String>listEach().each();
Traversal<Map<String, Integer>, Integer> mapTrav = EachInstances.<String, Integer>mapValuesEach().each();
Traversal<Optional<String>, String> optTrav = EachInstances.<String>optionalEach().each();
```

More importantly, `Each` integrates with the Focus DSL, enabling fluent navigation through any container type.

---

## Think of Each Like...

* **A universal remote**: One interface controls many devices
* **An iterator factory**: Each container knows how to produce its own iterator
* **A catalogue index**: Every library has a standard way to browse its contents
* **The "for-each" loop**: Java's enhanced for loop works on any `Iterable`; `Each` works on any container

The key insight: `Each` abstracts the *how* of traversal, letting you focus on the *what*.

---

## The Each Type Class

![each_typeclass.svg](../images/puml/each_typeclass.svg)

```java
@FunctionalInterface
public interface Each<S, A> {

    // The canonical traversal for all elements
    Traversal<S, A> each();

    // Optional: indexed traversal if the container supports it
    default <I> Optional<IndexedTraversal<I, S, A>> eachWithIndex() {
        return Optional.empty();
    }

    // Check if indexed access is supported
    default boolean supportsIndexed() {
        return eachWithIndex().isPresent();
    }
}
```

The interface is simple by design. Implement `each()` to provide a traversal; optionally implement `eachWithIndex()` if your container has meaningful indices.

---

## Available Each Instances

### Standard Java Types (EachInstances)

```
┌──────────────────────────────────────────────────────────────┐
│  Type          │ each()  │ eachWithIndex() │ Index Type     │
├──────────────────────────────────────────────────────────────┤
│  List<A>       │   ✓     │       ✓         │  Integer       │
│  Set<A>        │   ✓     │       ✗         │     -          │
│  Map<K, V>     │   ✓     │       ✓         │     K          │
│  Optional<A>   │   ✓     │       ✗         │     -          │
│  A[]           │   ✓     │       ✓         │  Integer       │
│  Stream<A>     │   ✓     │       ✗         │     -          │
│  String        │   ✓     │       ✓         │  Integer       │
└──────────────────────────────────────────────────────────────┘
```

```java
import org.higherkindedj.optics.each.EachInstances;

// List traversal with index support
Each<List<String>, String> listEach = EachInstances.listEach();

// Set traversal (no meaningful index)
Each<Set<Integer>, Integer> setEach = EachInstances.setEach();

// Map values traversal with key as index
Each<Map<String, Double>, Double> mapEach = EachInstances.mapValuesEach();

// Optional traversal (0 or 1 element)
Each<Optional<String>, String> optEach = EachInstances.optionalEach();

// Array traversal with index support
Each<Integer[], Integer> arrayEach = EachInstances.arrayEach();

// Stream traversal (consumed during traversal)
Each<Stream<String>, String> streamEach = EachInstances.streamEach();

// String character traversal with index support
Each<String, Character> stringEach = EachInstances.stringCharsEach();
```

### HKT Types (EachExtensions)

For Higher-Kinded-J core types, use `EachExtensions`:

```
┌──────────────────────────────────────────────────────────────┐
│  Type            │ each()  │ eachWithIndex() │ Index Type   │
├──────────────────────────────────────────────────────────────┤
│  Maybe<A>        │   ✓     │       ✗         │     -        │
│  Either<L, R>    │   ✓     │       ✗         │     -        │
│  Try<A>          │   ✓     │       ✗         │     -        │
│  Validated<E, A> │   ✓     │       ✗         │     -        │
└──────────────────────────────────────────────────────────────┘
```

```java
import org.higherkindedj.optics.extensions.EachExtensions;

// Maybe traversal (0 or 1 element)
Each<Maybe<String>, String> maybeEach = EachExtensions.maybeEach();

// Either right value traversal
Each<Either<Error, Value>, Value> eitherEach = EachExtensions.eitherRightEach();

// Try success value traversal
Each<Try<String>, String> tryEach = EachExtensions.trySuccessEach();

// Validated valid value traversal
Each<Validated<List<Error>, Value>, Value> validatedEach = EachExtensions.validatedEach();
```

---

## Basic Usage

### Traversing All Elements

```java
Each<List<String>, String> listEach = EachInstances.listEach();
Traversal<List<String>, String> traversal = listEach.each();

List<String> names = List.of("alice", "bob", "charlie");

// Get all elements
List<String> all = Traversals.getAll(traversal, names);
// Result: ["alice", "bob", "charlie"]

// Modify all elements
List<String> upper = Traversals.modify(traversal, String::toUpperCase, names);
// Result: ["ALICE", "BOB", "CHARLIE"]

// Set all elements to the same value
List<String> same = Traversals.set(traversal, "anonymous", names);
// Result: ["anonymous", "anonymous", "anonymous"]
```

### Using Indexed Traversal

When position matters, use `eachWithIndex()`:

```java
Each<List<String>, String> listEach = EachInstances.listEach();

listEach.<Integer>eachWithIndex().ifPresent(indexed -> {
    List<String> items = List.of("apple", "banana", "cherry");

    // Number each element
    List<String> numbered = IndexedTraversals.imodify(
        indexed,
        (index, value) -> (index + 1) + ". " + value,
        items
    );
    // Result: ["1. apple", "2. banana", "3. cherry"]
});
```

### Map Key as Index

For maps, the index is the key:

```java
Each<Map<String, Integer>, Integer> mapEach = EachInstances.mapValuesEach();

mapEach.<String>eachWithIndex().ifPresent(indexed -> {
    Map<String, Integer> scores = Map.of("alice", 100, "bob", 85, "charlie", 92);

    // Add key prefix to each value's string representation
    Map<String, String> labelled = IndexedTraversals.imodify(
        indexed,
        (key, value) -> key + ": " + value,
        scores
    );
    // Result: {"alice": "alice: 100", "bob": "bob: 85", ...}
});
```

---

## Integration with Focus DSL

The Focus DSL provides an `.each(Each)` method on `FocusPath`, `AffinePath`, and `TraversalPath`. This enables fluent navigation through custom container types:

```java
record User(String name, List<Order> orders) {}
record Order(String id, Map<String, Integer> items) {}

// Create lenses
Lens<User, List<Order>> ordersLens = Lens.of(User::orders, (u, o) -> new User(u.name(), o));
Lens<Order, Map<String, Integer>> itemsLens = Lens.of(Order::items, (o, i) -> new Order(o.id(), i));

// Navigate using Each instances
FocusPath<User, List<Order>> userOrders = FocusPath.of(ordersLens);

// Use listEach to traverse orders
TraversalPath<User, Order> allOrders = userOrders.each(EachInstances.listEach());

// Continue navigation
TraversalPath<User, Map<String, Integer>> allItems = allOrders.via(itemsLens);

// Use mapValuesEach to traverse item quantities
TraversalPath<User, Integer> allQuantities = allItems.each(EachInstances.mapValuesEach());

// Now modify all quantities across all orders
User updated = allQuantities.modifyAll(qty -> qty * 2, user);
```

This is particularly useful when the container type isn't a standard `List` that `.each()` recognises automatically.

---

## Creating Custom Each Instances

For custom container types, implement the `Each` interface:

```java
// A simple tree structure
public sealed interface Tree<A> {
    record Leaf<A>(A value) implements Tree<A> {}
    record Branch<A>(Tree<A> left, Tree<A> right) implements Tree<A> {}
}

// Each instance for Tree
public static <A> Each<Tree<A>, A> treeEach() {
    return new Each<>() {
        @Override
        public Traversal<Tree<A>, A> each() {
            return new Traversal<>() {
                @Override
                public <F extends WitnessArity<TypeArity.Unary>> Kind<F, Tree<A>> modifyF(
                        Function<A, Kind<F, A>> f,
                        Tree<A> source,
                        Applicative<F> app) {
                    return switch (source) {
                        case Tree.Leaf<A> leaf ->
                            app.map(Tree.Leaf::new, f.apply(leaf.value()));
                        case Tree.Branch<A> branch ->
                            app.map2(
                                modifyF(f, branch.left(), app),
                                modifyF(f, branch.right(), app),
                                Tree.Branch::new
                            );
                    };
                }
            };
        }
    };
}
```

### From Existing Traversal

If you already have a `Traversal`, wrap it:

```java
Traversal<MyContainer<A>, A> existingTraversal = ...;
Each<MyContainer<A>, A> each = Each.fromTraversal(existingTraversal);
```

### From IndexedTraversal

If you have an `IndexedTraversal`, you get both methods:

```java
IndexedTraversal<Integer, MyList<A>, A> indexed = ...;
Each<MyList<A>, A> each = Each.fromIndexedTraversal(indexed);

// Both work
Traversal<MyList<A>, A> trav = each.each();
Optional<IndexedTraversal<Integer, MyList<A>, A>> iTrav = each.eachWithIndex();
```

---

## Each vs Traverse

You might wonder how `Each` relates to the `Traverse` type class from HKT. Here's the distinction:

| Aspect | Each<S, A> | Traverse<F> |
|--------|------------|-------------|
| Works on | Concrete types (`List<A>`, `Map<K, V>`) | Higher-kinded types (`Kind<F, A>`) |
| Returns | `Traversal<S, A>` | Sequence/traverse methods |
| Use case | Optics composition | Applicative traversal |
| Java-friendly | Very (no Kind wrappers) | Requires HKT encoding |

**Use Each** when working with optics and standard Java collections. **Use Traverse** when working with HKT-encoded types and applicative effects.

---

## Common Patterns

### Bulk Validation

```java
Each<List<Order>, Order> orderEach = EachInstances.listEach();
Traversal<List<Order>, Order> allOrders = orderEach.each();

// Validate all orders, accumulating errors
Validated<List<String>, List<Order>> result = allOrders.modifyF(
    order -> validateOrder(order),
    orders,
    ValidatedApplicative.instance(Semigroups.list())
);
```

### Conditional Modification with Index

```java
Each<List<Product>, Product> productEach = EachInstances.listEach();

productEach.<Integer>eachWithIndex().ifPresent(indexed -> {
    // Apply discount to even-indexed products
    List<Product> discounted = IndexedTraversals.imodify(
        indexed,
        (index, product) -> index % 2 == 0
            ? product.withPrice(product.price() * 0.9)
            : product,
        products
    );
});
```

### Nested Container Navigation

```java
// User -> List<Project> -> Map<String, Task>
Each<List<Project>, Project> projectEach = EachInstances.listEach();
Each<Map<String, Task>, Task> taskEach = EachInstances.mapValuesEach();

TraversalPath<User, Task> allTasks =
    FocusPath.of(userProjectsLens)
        .each(projectEach)
        .via(projectTasksLens)
        .each(taskEach);

// Update all tasks across all projects
User updated = allTasks.modifyAll(Task::markReviewed, user);
```

---

## When to Use Each

**Use Each when:**
- You need a canonical traversal for a container type
- Integrating with Focus DSL's `.each(Each)` method
- Building reusable optics for custom containers
- You want indexed access when available

**Use direct Traversal when:**
- You need a non-canonical traversal (e.g., every second element)
- The traversal is one-off and doesn't need reuse
- Performance is critical and you want to avoid the extra indirection

---

~~~admonish info title="Key Takeaways"
* **Each<S, A>** provides a canonical `Traversal<S, A>` for any container type
* **EachInstances** covers Java collections: List, Set, Map, Optional, arrays, Stream, String
* **EachExtensions** covers HKT types: Maybe, Either, Try, Validated
* **eachWithIndex()** returns an `IndexedTraversal` when the container supports meaningful indices
* **Focus DSL integration** via `.each(Each)` enables fluent navigation through custom containers
* **Each.fromTraversal()** and **Each.fromIndexedTraversal()** wrap existing optics
~~~

~~~admonish tip title="See Also"
- [Traversals](traversals.md) - Understanding the Traversal optic
- [Indexed Optics](indexed_optics.md) - Position-aware operations with IndexedTraversal
- [Focus DSL](focus_dsl.md) - Fluent path-based navigation
- [Foldable and Traverse](../functional/foldable_and_traverse.md) - The HKT Traverse type class
~~~

---

**Previous:** [Indexed Optics](indexed_optics.md)
**Next:** [String Traversals](string_traversals.md)

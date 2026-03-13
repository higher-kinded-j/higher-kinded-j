# Traverse Within Comprehensions

The `traverse()`, `sequence()`, and `flatTraverse()` methods allow bulk operations over traversable structures directly within for-comprehension chains. Rather than breaking out of the comprehension to process a collection, you can apply an effectful function to every element and collect the results in a single step.

## `traverse()`

Apply an effectful function to each element of a traversable structure. The `traverse()` method extracts a `Kind<T, C>` from the current tuple, applies `f: C -> Kind<M, B>` to each element, and collects the results as `Kind<T, B>`, all within the enclosing monad.

```java
var maybeMonad = MaybeMonad.INSTANCE;
var listTraverse = ListTraverse.INSTANCE;

Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> result =
    For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(1, 2, 3))))
        .traverse(listTraverse,
            t -> t._1(),                              // extract the list
            n -> n > 0 ? MAYBE.just(n * 10) : MAYBE.nothing())  // effectful function
        .yield((original, transformed) -> transformed);

// Result: Just([10, 20, 30])
// If any element fails the check, the entire result is Nothing.
```

## `sequence()`

Flip a `Structure<Effect<A>>` into an `Effect<Structure<A>>`. This is equivalent to `traverse` with the identity function and is useful when you already have a collection of monadic values that need to be "turned inside-out".

```java
List<Kind<MaybeKind.Witness, Integer>> items = List.of(
    MAYBE.just(1), MAYBE.just(2), MAYBE.just(3));

Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> result =
    For.from(maybeMonad, MAYBE.just(LIST.widen(items)))
        .sequence(listTraverse, t -> t._1())
        .yield((original, collected) -> collected);

// Result: Just([1, 2, 3])
```

## `flatTraverse()`

Like `traverse`, but flattens nested structures using an inner monad. This is useful when the effectful function itself returns a nested structure (e.g., `A -> Kind<M, Kind<T, B>>` where `T` is also monadic), and you want the inner layer flattened.

```java
Kind<MaybeKind.Witness, Kind<ListKind.Witness, Integer>> result =
    For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(1, 2, 3))))
        .flatTraverse(listTraverse, ListMonad.INSTANCE,
            t -> t._1(),
            n -> MAYBE.just(LIST.widen(List.of(n, n * 10))))
        .yield((original, flattened) -> flattened);

// Result: Just([1, 10, 2, 20, 3, 30])
```

## Operations Summary

| Operation | Purpose | Signature (simplified) |
|-----------|---------|----------------------|
| `traverse()` | Apply effectful function to each element, collect results | `(Traverse<T>, extractor, C -> Kind<M, B>) -> Kind<T, B>` |
| `sequence()` | Flip `Structure<Effect<A>>` to `Effect<Structure<A>>` | `(Traverse<T>, extractor) -> Kind<T, A>` |
| `flatTraverse()` | Traverse then flatten nested inner structures | `(Traverse<T>, Monad<T>, extractor, C -> Kind<M, Kind<T, B>>) -> Kind<T, B>` |

~~~admonish example title="See Example Code"
[ForTraverseComprehensionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForTraverseComprehensionExample.java)
~~~

~~~admonish tip title="See Also"
- For a detailed introduction to `Traverse`, `Foldable`, and `sequenceA`, see [Foldable & Traverse](foldable_and_traverse.md).
- For traverse within ForPath comprehensions, see [ForPath Traverse](../effect/forpath_traverse.md).
~~~

---

**Previous:** [Parallel Composition](for_par.md) | **Next:** [Optics Integration](for_optics.md)

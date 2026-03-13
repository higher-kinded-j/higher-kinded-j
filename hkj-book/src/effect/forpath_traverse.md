# ForPath Traverse

A common pattern in effectful programming is having a collection of items where each item needs to be processed through an effect -- validating every field in a form, fetching details for every ID in a list, or parsing every line in a file. Without `traverse`, you'd break out of the comprehension, process the collection separately, and thread the result back in. The `traverse()`, `sequence()`, and `flatTraverse()` methods let you do this *inline*, keeping the entire pipeline in one fluent chain.

## Traverse: Apply an Effect to Every Element

`traverse()` takes a collection from the current tuple, applies a Path-producing function to each element, and collects the results. If any element's effect fails, the entire computation short-circuits according to the Path type's semantics.

### MaybePath with Traverse

Here every positive number is scaled by 10. If any element were non-positive, the whole result would be `Nothing`:

```java
var listTraverse = ListTraverse.INSTANCE;

MaybePath<Kind<ListKind.Witness, Integer>> result =
    ForPath.from(Path.just(LIST.widen(List.of(1, 2, 3))))
        .traverse(listTraverse,
            t -> t._1(),
            n -> n > 0 ? Path.just(n * 10) : Path.<Integer>nothing())
        .yield((original, transformed) -> transformed);

// Result: Just([10, 20, 30])
// If any element fails the check, the entire result is Nothing.
```

### EitherPath with Traverse

The same all-or-nothing pattern works with `EitherPath`, where failure carries a descriptive error message:

```java
EitherPath<String, Kind<ListKind.Witness, String>> result =
    ForPath.from(Path.<String, List<String>>right(
            LIST.widen(List.of("alice", "bob"))))
        .traverse(listTraverse,
            t -> t._1(),
            name -> name.length() > 2
                ? Path.<String, String>right(name.toUpperCase())
                : Path.<String, String>left("Name too short: " + name))
        .yield((original, uppercased) -> uppercased);

// Result: Right(["ALICE", "BOB"])
```

## Sequence: Flip the Nesting

Sometimes you already have a collection of effectful values (e.g., a `List<MaybePath<Integer>>`) and need to "turn it inside-out" into a single effect containing the collection (`MaybePath<List<Integer>>`). `sequence()` does exactly this -- it's equivalent to `traverse` with the identity function.

## FlatTraverse: Traverse and Flatten

When the mapping function itself returns a nested collection (e.g., each element produces a list), `flatTraverse()` traverses *and* flattens in one step, avoiding an intermediate nested structure.

```java
// sequence: flip Structure<Effect<A>> to Effect<Structure<A>>
MaybePath<Kind<ListKind.Witness, Integer>> sequenced =
    ForPath.from(Path.just(LIST.widen(
            List.of(MAYBE.just(1), MAYBE.just(2), MAYBE.just(3)))))
        .sequence(listTraverse, t -> t._1())
        .yield((original, collected) -> collected);

// flatTraverse: traverse then flatten
MaybePath<Kind<ListKind.Witness, Integer>> flat =
    ForPath.from(Path.just(LIST.widen(List.of(1, 2, 3))))
        .flatTraverse(listTraverse, ListMonad.INSTANCE,
            t -> t._1(),
            n -> Path.just(LIST.widen(List.of(n, n * 10))))
        .yield((original, flattened) -> flattened);

// Result: Just([1, 10, 2, 20, 3, 30])
```

~~~admonish example title="See Example Code"
[ForTraverseComprehensionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForTraverseComprehensionExample.java)
~~~

~~~admonish tip title="See Also"
- For the underlying type-class-based traverse within `For` comprehensions, see [Traverse Within Comprehensions](../functional/for_traverse.md).
- For a detailed introduction to `Traverse`, `Foldable`, and `sequenceA`, see [Foldable & Traverse](../functional/foldable_and_traverse.md).
~~~

---

**Previous:** [ForPath Parallel Composition](forpath_par.md) | **Next:** [Type Conversions](conversions.md)

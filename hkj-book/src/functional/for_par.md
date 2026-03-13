# Parallel Composition with `par()`

Sometimes two or more computations within a comprehension are **independent** of each other. Using `.from()` forces them into a sequential `flatMap` chain, even though neither needs the other's result. The `par()` combinator lets you express this independence explicitly.

## Static Entry Points

`For.par()` combines two to five independent computations using applicative semantics (`map2`/`map3`/`map4`/`map5`) rather than monadic `flatMap`:

```java
// Two independent values combined in parallel
Kind<IdKind.Witness, String> result =
    For.par(idMonad, Id.of("hello"), Id.of("world"))
        .yield((a, b) -> a + " " + b);
// "hello world"

// Three independent values
Kind<IdKind.Witness, Integer> sum =
    For.par(idMonad, Id.of(10), Id.of(20), Id.of(30))
        .yield((a, b, c) -> a + b + c);
// 60
```

For `MonadZero` types like `Maybe`, short-circuiting works as expected:

```java
Kind<MaybeKind.Witness, String> result =
    For.par(maybeMonad, MAYBE.just("Alice"), MAYBE.<Integer>nothing())
        .yield((name, age) -> name + " is " + age);
// Nothing — the second computation failed
```

## Instance `par()` for Dependent Branches

When a prior value is needed before branching, use the instance `par()` method on `Steps1`. This performs a sequential `flatMap` to obtain the first value, then fans out the branches with `map2`/`map3`:

```java
Kind<IdKind.Witness, String> result =
    For.from(idMonad, Id.of("Alice"))
        .par(
            name -> Id.of(name.length()),          // branch 1
            name -> Id.of(name.toUpperCase()))      // branch 2
        .yield((name, len, upper) -> upper + " has " + len + " letters");
// "ALICE has 5 letters"
```

## Chaining After `par()`

The result of `par()` is a regular step, so you can chain `.from()`, `.let()`, `.when()`, or another `.par()` after it:

```java
Kind<IdKind.Witness, String> result =
    For.par(idMonad, Id.of("Alice"), Id.of(5))
        .from(t -> Id.of(t._1() + " has " + t._2() + " letters"))
        .yield((name, len, sentence) -> sentence.toUpperCase());
// "ALICE HAS 5 LETTERS"
```

## When Is `par()` Actually Parallel?

The `par()` combinator expresses *independence*, not necessarily *concurrency*. True parallel execution depends on the underlying type:

| Type | Behaviour |
|------|-----------|
| VTask | True concurrency via `Par.map2`/`Par.map3` on virtual threads |
| IO | Sequential (future: parallel IO executor) |
| Maybe, Either, Optional | Sequential; short-circuits on failure |
| List | Cartesian product (applicative, not monadic) |
| Id | Immediate; no effect to parallelise |

Even when execution is sequential, `par()` documents the *intent* that the computations are independent, making the dependency structure of your workflow explicit.

~~~admonish tip title="See Also"
For `par()` with Effect Path types (no manual Kind extraction), see [ForPath Parallel Composition](../effect/forpath_par.md).
~~~

---

**Previous:** [For Comprehension](for_comprehension.md) | **Next:** [Traverse Within Comprehensions](for_traverse.md)

# Foldable & Traverse: Reducing and Reshaping Structures

> *"Simplicity is the ultimate sophistication."*
>
> – Leonardo da Vinci

~~~admonish info title="What We'll Learn"
- How `foldMap` reduces any data structure to a single summary value
- Why swapping the `Monoid` is the trick that makes one fold do many jobs
- How `traverse` turns a structure of effects inside out
- Why `traverse` plus `Validated` is the cleanest way to validate an entire collection
- The difference between `traverse` and `sequenceA`, and when each one fits
~~~

## Foldable: One Structure, Many Summaries

The `Foldable` type class names something every Java developer has done a hundred times: walk a structure, build up an answer.

Sum a list. Concatenate strings. Check whether every element passes a predicate. The walk is always the same; the answer differs only in *what we combine* and *what we start from*. `Foldable` extracts that walk so we can write the combine-and-start rule once and reuse the walk forever.

That combine-and-start rule has a name: a `Monoid`. Pair the `Foldable` walk with a `Monoid`, and we have `foldMap`.

~~~admonish note title="Interface Signature"
```java
public interface Foldable<F extends WitnessArity<TypeArity.Unary>> {
  <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<F, A> fa);
}
```
~~~

---

### Same Data, Three Summaries

```java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);

Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

Integer sum = listFoldable.foldMap(
    Monoids.integerAddition(),
    Function.identity(),
    numbersKind);
// 15

Boolean allPositive = listFoldable.foldMap(
    Monoids.booleanAnd(),
    n -> n > 0,
    numbersKind);
// true

String joined = listFoldable.foldMap(
    Monoids.string(),
    String::valueOf,
    numbersKind);
// "12345"
```

Same data, same `foldMap`, three different summaries. The difference is one argument: which `Monoid` we pass. That is the abstraction earning its keep.

---

## Traverse: Effectful Iteration

`Traverse` extends `Foldable` and `Functor` with one additional operation: walk a structure, run an effect at each step, and collect the results back into a single effect.

The picture worth keeping is a structure flipping inside out:

```
   traverse turns a structure of effects inside out:

   Before: List< Validated<E, A> >
           ┌──────────┬──────────┬──────────┐
           │ Valid(1) │ Valid(2) │ Valid(3) │
           └──────────┴──────────┴──────────┘

   After:  Validated< E, List<A> >
           Valid( [1, 2, 3] )

   With errors mixed in:
           ┌──────────┬─────────────┬──────────┐
           │ Valid(1) │ Invalid(e1) │ Valid(3) │
           └──────────┴─────────────┴──────────┘

   becomes:
           Invalid( e1 )           // or accumulated, depending on the Applicative
```

If we have ever validated every entry in a form, fetched a record for every id in a list, or parsed every line in a file and wanted to know whether the whole batch was good, we have wanted `traverse`. The hand-rolled version always involves a loop, a list to collect errors, and a flag to track whether anything went wrong. `traverse` is that pattern in one method call.

~~~admonish note title="Interface Signature"
```java
public interface Traverse<T extends WitnessArity<TypeArity.Unary>>
    extends Functor<T>, Foldable<T> {

  <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, Kind<T, B>> traverse(
      Applicative<F> applicative,
      Function<A, Kind<F, B>> f,
      Kind<T, A> ta);

  // sequenceA also available
}
```
~~~

---

### Validating a List of Promo Codes

**The problem.** A request arrives with four promo codes. We want every one of them validated, with every error reported, in one pass.

**The solution.**

```java
public Kind<ValidatedKind.Witness<String>, String> validateCode(String code) {
    if (code.startsWith("VALID")) {
        return VALIDATED.widen(Validated.valid(code));
    }
    return VALIDATED.widen(Validated.invalid("'" + code + "' is not a valid code"));
}

List<String> codes = List.of("VALID-A", "EXPIRED", "VALID-B", "INVALID");
Kind<ListKind.Witness, String> codesKind = LIST.widen(codes);

Applicative<ValidatedKind.Witness<String>> validatedApplicative =
    Instances.validated(Semigroups.string("; "));

Kind<ValidatedKind.Witness<String>, Kind<ListKind.Witness, String>> result =
    ListTraverse.INSTANCE.traverse(
        validatedApplicative,
        this::validateCode,
        codesKind);

// Invalid("'EXPIRED' is not a valid code; 'INVALID' is not a valid code")
```

One call. Both errors. No loop, no error list, no flag.

### `sequenceA`

`sequenceA` is `traverse` with the identity function: we already have a structure of effects, and we just want to flip it. `List<Optional<A>>` becomes `Optional<List<A>>`. `List<Either<E, A>>` becomes `Either<E, List<A>>`. Same machinery, narrower input.

We reach for `traverse` when we are turning each element into an effect. We reach for `sequenceA` when the elements *already are* effects.

---

## Traverse Inside For-Comprehensions

`traverse`, `sequence`, and `flatTraverse` are not just standalone operations; they fit inside a `For` chain so we can blend collection iteration with the rest of a monadic workflow without breaking out.

```java
For.from(maybeMonad, MAYBE.just(LIST.widen(List.of(1, 2, 3))))
    .traverse(ListTraverse.INSTANCE,
        t -> t._1(),
        n -> n > 0 ? MAYBE.just(n * 10) : MAYBE.nothing())
    .yield((original, transformed) -> transformed);
```

`ForPath` exposes the same capability for Effect Path types, which is usually how application code reaches it.

The collection-style Effect Paths expose this fold directly as a terminal operation: `ListPath` and `StreamPath` both provide `fold(identity, op)` for a same-type reduction and `foldMap(monoid, fn)` for the `Monoid`-driven summary, keeping the reduction inside the path chain instead of unwrapping the collection first.

```java
String joined = ListPath.of(1, 2, 3).foldMap(Monoids.string(), i -> i + ",");
// "1,2,3,"
```

~~~admonish tip title="See Also"
- [Traverse Within Comprehensions](for_traverse.md) - the full API with `traverse`, `sequence`, and `flatTraverse`
- [ForPath Traverse](../effect/forpath_traverse.md) - the same operations through the Effect Path API
- [Effect Path Cheatsheet](../cheatsheet.md) - the full fold family on `ListPath`, `StreamPath`, and `VStreamPath`
~~~

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"`Foldable` is just `Stream.reduce` with extra steps."** It is the same idea, formalised so the same fold can target `List`, `Maybe`, `Either`, custom containers, anything with a `Foldable` instance. `Stream.reduce` works on `Stream`. `foldMap` works on whatever we hand it.
- **"`traverse` runs the steps sequentially."** It runs them in the order the structure dictates, but the *combination* uses the `Applicative`, which can be parallel for instances that support it. `Validated` accumulates; `IO` sequences; a parallel-aware applicative could fan out. The walking order is fixed, the meaning of "and then combine" is up to the instance.
- **"`sequenceA` and `traverse` do different things."** `sequenceA` is `traverse(applicative, x -> x, ta)`. They differ only in whether we have a function to apply on the way through. The library keeps both because both come up in code, not because they have separate stories.
- **"`Monoid` and `Foldable` are independent."** They are independent type classes, but they pair so naturally that we will almost always see them together. `foldMap` exists precisely because that pairing is the right interface for "reduce a structure to a summary".
~~~

---

~~~admonish info title="Key Takeaways"
* `Foldable` reduces a structure to a summary; the rule for combining lives in the `Monoid`
* Swap the `Monoid` and the same fold yields sums, products, joined strings, or boolean checks
* `Traverse` flips a structure of effects into an effect of a structure
* `traverse` paired with `Validated` is the cleanest way to validate an entire collection in one pass
* `sequenceA` is `traverse` with the identity; reach for it when the elements are already effects
~~~

~~~admonish tip title="See Also"
- [Semigroup and Monoid](semigroup_and_monoid.md) - the combining abstractions that power folding
- [Applicative](applicative.md) - the effect-combining type class that powers traversal
- [Validated](../monads/validated_monad.md) - the type designed for error accumulation with Traverse
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Java Stream reduce()](https://www.baeldung.com/java-stream-reduce) - Java's built-in fold operation, conceptually similar to Foldable
- **Mojang**: [DataFixerUpper](https://github.com/Mojang/DataFixerUpper) - Minecraft's Java library that uses traversals for data migration between game versions
~~~

---

**Previous:** [Semigroup and Monoid](semigroup_and_monoid.md)
**Next:** [MonadZero](monad_zero.md)

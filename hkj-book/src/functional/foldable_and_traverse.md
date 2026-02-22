# Foldable & Traverse: Reducing a Structure to a Summary

> *"Simplicity is the ultimate sophistication."*
>
> -- Leonardo da Vinci

~~~admonish info title="What You'll Learn"
- How to reduce any data structure to a summary value using `foldMap`
- The power of swapping Monoids to get different aggregations from the same data
- Turning effects "inside-out" with `traverse` operations
- Validating entire collections and collecting all errors at once
- The relationship between `sequence` and `traverse` for effectful operations
~~~

## Foldable: One Structure, Many Summaries

The **`Foldable`** type class represents one of the most common and powerful patterns in functional programming: **reducing a data structure to a single summary value**. If you've ever calculated the sum of a list of numbers or concatenated a list of strings, you've performed a fold.

`Foldable` abstracts this pattern, allowing you to write generic code that can aggregate any data structure that knows how to be folded. The key insight is that by swapping the `Monoid`, you get completely different summaries from the same data.

~~~admonish note title="Interface Signature"
``` java
public interface Foldable<F extends WitnessArity<TypeArity.Unary>> {
  <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<F, A> fa
  );
}
```
~~~

---

### Practical Example: Aggregating a List with Different Monoids

``` java
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);

Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

// --- Sum the numbers ---
Integer sum = listFoldable.foldMap(
    Monoids.integerAddition(),    // empty = 0, combine = +
    Function.identity(),
    numbersKind
);
// Result: 15

// --- Check if all numbers are positive ---
Boolean allPositive = listFoldable.foldMap(
    Monoids.booleanAnd(),         // empty = true, combine = &&
    num -> num > 0,
    numbersKind
);
// Result: true

// --- Convert to strings and concatenate ---
String asString = listFoldable.foldMap(
    Monoids.string(),             // empty = "", combine = +
    String::valueOf,
    numbersKind
);
// Result: "12345"
```

Same data, same `foldMap` call, three completely different results, just by swapping the Monoid.

---

# Traverse: Effectful Iteration

The **`Traverse`** type class is a powerful extension of `Foldable` and `Functor`. It allows you to iterate over a data structure, but with a twist: at each step, you can perform an **effectful** action and then collect all the results back into a single effect.

The true power of `traverse` is that it can turn a structure of effects "inside-out":

```
  traverse turns a structure of effects inside-out:

  Before: List< Validated<E, A> >
          ┌──────────┬──────────┬──────────┐
          │ Valid(1)  │ Valid(2) │ Valid(3)  │
          └──────────┴──────────┴──────────┘

  After:  Validated< E, List<A> >
          Valid( [1, 2, 3] )

  With errors:
          ┌──────────┬────────────┬──────────┐
          │ Valid(1)  │ Invalid(e) │ Valid(3)  │
          └──────────┴────────────┴──────────┘

          Invalid( e )   <-- errors accumulated
```

This is one of the most useful type classes for real-world applications, as it elegantly handles scenarios like validating all items in a list, fetching data for each ID in a collection, and much more.

~~~admonish note title="Interface Signature"
```java
public interface Traverse<T extends WitnessArity<TypeArity.Unary>> extends Functor<T>, Foldable<T> {
  <F extends WitnessArity<TypeArity.Unary>, A, B> Kind<F, Kind<T, B>> traverse(
      Applicative<F> applicative,
      Function<A, Kind<F, B>> f,
      Kind<T, A> ta
  );
  //... sequenceA method also available
}
```
~~~

---

### Practical Example: Validating a List of Promo Codes

**The problem:** You have a list of promo codes and you want to validate each one. Without `traverse`, you'd need a manual loop to collect errors and handle the logic yourself.

**The solution:** `traverse` does it in a single, elegant expression.

```java
public Kind<Validated.Witness<String>, String> validateCode(String code) {
    if (code.startsWith("VALID")) {
        return VALIDATED.widen(Validated.valid(code));
    }
    return VALIDATED.widen(Validated.invalid("'" + code + "' is not a valid code"));
}

List<String> codes = List.of("VALID-A", "EXPIRED", "VALID-B", "INVALID");
Kind<ListKind.Witness, String> codesKind = LIST.widen(codes);

Applicative<Validated.Witness<String>> validatedApplicative =
    ValidatedMonad.instance(Semigroups.string("; "));

Kind<Validated.Witness<String>, Kind<ListKind.Witness, String>> result =
    ListTraverse.INSTANCE.traverse(
        validatedApplicative,
        this::validateCode,
        codesKind
    );

// Result: Invalid("'EXPIRED' is not a valid code; 'INVALID' is not a valid code")
System.out.println(VALIDATED.narrow(result));
```

### `sequenceA`

`Traverse` also provides `sequenceA`, which is a specialised version of `traverse`. It's used when you already have a data structure containing effects (e.g., a `List<Optional<A>>`) and you want to flip it into a single effect containing the data structure (`Optional<List<A>>`).

---

~~~admonish info title="Key Takeaways"
* **Foldable reduces structures to summaries** using `foldMap` and a `Monoid`
* **Swap the Monoid, change the result**: sum, product, concatenation, boolean checks, all from the same fold
* **Traverse turns effects inside-out**: `List<Validated<E, A>>` becomes `Validated<E, List<A>>`
* **Error accumulation for free**: combine `traverse` with `Validated` to validate entire collections in one pass
* **`sequenceA`** is `traverse` with the identity function, useful when effects are already in place
~~~

~~~admonish tip title="See Also"
- [Semigroup and Monoid](semigroup_and_monoid.md) - The combining abstractions that power folding
- [Applicative](applicative.md) - The effect-combining type class that powers traversal
- [Validated](../monads/validated_monad.md) - The type designed for error accumulation with Traverse
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**: [Java Stream reduce()](https://www.baeldung.com/java-stream-reduce) - Java's built-in fold operation, conceptually similar to Foldable
- **Mojang**: [DataFixerUpper](https://github.com/Mojang/DataFixerUpper) - Minecraft's Java library that uses traversals for data migration between game versions
~~~

---

**Previous:** [Semigroup and Monoid](semigroup_and_monoid.md)
**Next:** [MonadZero](monad_zero.md)

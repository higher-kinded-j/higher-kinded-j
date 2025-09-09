# Foldable & Traverse: 
## Reducing a Structure to a Summary

~~~admonish info title="What You'll Learn"
- How to reduce any data structure to a summary value using `foldMap`
- The power of swapping Monoids to get different aggregations from the same data
- Turning effects "inside-out" with `traverse` operations
- Validating entire collections and collecting all errors at once
- The relationship between `sequence` and `traverse` for effectful operations
~~~

The **`Foldable`** typeclass represents one of the most common and powerful patterns in functional programming: **reducing a data structure to a single summary value**. If you've ever calculated the sum of a list of numbers or concatenated a list of strings, you've performed a fold.

`Foldable` abstracts this pattern, allowing you to write generic code that can aggregate any data structure that knows how to be folded.

---

## What is it?

A typeclass is `Foldable` if it can be "folded up" from left to right into a summary value. The key to this process is the **`Monoid`**, which provides two essential things:

1. An **`empty`** value to start with (e.g., `0` for addition).
2. A **`combine`** operation to accumulate the results (e.g., `+`).

The core method of the `Foldable` typeclass is **`foldMap`**.

### The `foldMap` Method

`foldMap` is a powerful operation that does two things in one step:

1. It **maps** each element in the data structure to a value of a monoidal type.
2. It **folds** (combines) all of those monoidal values into a final result.

The interface for `Foldable` in `hkj-api` is as follows:


``` java
public interface Foldable<F> {
  <A, M> M foldMap(
      Monoid<M> monoid,
      Function<? super A, ? extends M> f,
      Kind<F, A> fa
  );
}
```

---

### Why is it useful?

`Foldable` allows you to perform powerful aggregations on any data structure without needing to know its internal representation. By simply swapping out the `Monoid`, you can get completely different summaries from the same data.

Let's see this in action with `List`, which has a `Foldable` instance provided by `ListTraverse`.

**Example: Aggregating a List with Different Monoids**


``` java
// Our data
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);

// Our Foldable instance for List
Foldable<ListKind.Witness> listFoldable = ListTraverse.INSTANCE;

// --- Scenario 1: Sum the numbers ---
// We use the integer addition monoid (empty = 0, combine = +)
Integer sum = listFoldable.foldMap(
    Monoids.integerAddition(),
    Function.identity(), // Map each number to itself
    numbersKind
);
// Result: 15

// --- Scenario 2: Check if all numbers are positive ---
// We map each number to a boolean and use the "AND" monoid (empty = true, combine = &&)
Boolean allPositive = listFoldable.foldMap(
    Monoids.booleanAnd(),
    num -> num > 0,
    numbersKind
);
// Result: true

// --- Scenario 3: Convert to strings and concatenate ---
// We map each number to a string and use the string monoid (empty = "", combine = +)
String asString = listFoldable.foldMap(
    Monoids.string(),
    String::valueOf,
    numbersKind
);
// Result: "12345"
```

As you can see, `foldMap` provides a single, abstract way to perform a wide variety of aggregations, making your code more declarative and reusable.

---

# Traverse: Effectful Folding

The **`Traverse`** typeclass is a powerful extension of `Foldable` and `Functor`. It allows you to iterate over a data structure, but with a twist: at each step, you can perform an **effectful** action and then collect all the results back into a single effect.

This is one of the most useful typeclasses for real-world applications, as it elegantly handles scenarios like validating all items in a list, fetching data for each ID in a collection, and much more.

---

## What is it?

A typeclass is `Traverse` if it can be "traversed" from left to right. The key to this process is the **`Applicative`**, which defines how to sequence the effects at each step.

The core method of the `Traverse` typeclass is **`traverse`**.

### The `traverse` Method

The `traverse` method takes a data structure and a function that maps each element to an effectful computation (wrapped in an `Applicative` like `Validated`, `Optional`, or `Either`). It then runs these effects in sequence and collects the results.

The true power of `traverse` is that it can turn a structure of effects "inside-out". For example, it can transform a `List<Validated<E, A>>` into a single `Validated<E, List<A>>`.

The interface for `Traverse` in `hkj-api` extends `Functor` and `Foldable`:

**Java**

```
public interface Traverse<T> extends Functor<T>, Foldable<T> {
  <F, A, B> Kind<F, Kind<T, B>> traverse(
      Applicative<F> applicative,
      Function<A, Kind<F, B>> f,
      Kind<T, A> ta
  );
  //... sequenceA method also available
}
```

---

### Why is it useful?

`Traverse` abstracts away the boilerplate of iterating over a collection, performing a failable action on each element, and then correctly aggregating the results.

**Example: Validating a List of Promo Codes**

Imagine you have a list of promo codes, and you want to validate each one. Your validation function returns a `Validated<String, PromoCode>`. Without `traverse`, you'd have to write a manual loop, collect all the errors, and handle the logic yourself.

With `traverse`, this becomes a single, elegant expression.

**Java**

```
// Our validation function
public Kind<Validated.Witness<String>, String> validateCode(String code) {
    if (code.startsWith("VALID")) {
        return VALIDATED.widen(Validated.valid(code));
    }
    return VALIDATED.widen(Validated.invalid("'" + code + "' is not a valid code"));
}

// Our data
List<String> codes = List.of("VALID-A", "EXPIRED", "VALID-B", "INVALID");
Kind<ListKind.Witness, String> codesKind = LIST.widen(codes);

// The Applicative for Validated, using a Semigroup to join errors
Applicative<Validated.Witness<String>> validatedApplicative =
    ValidatedMonad.instance(Semigroups.string("; "));

// --- Traverse the list ---
Kind<Validated.Witness<String>, Kind<ListKind.Witness, String>> result =
    ListTraverse.INSTANCE.traverse(
        validatedApplicative,
        this::validateCode,
        codesKind
    );

// The result is a single Validated instance with accumulated errors.
// Result: Invalid("'EXPIRED' is not a valid code; 'INVALID' is not a valid code")
System.out.println(VALIDATED.narrow(result));
```

### `sequenceA`

`Traverse` also provides `sequenceA`, which is a specialised version of `traverse`. It's used when you already have a data structure containing effects (e.g., a `List<Optional<A>>`) and you want to flip it into a single effect containing the data structure (`Optional<List<A>>`).

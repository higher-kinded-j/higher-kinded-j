# Semigroup and Monoid: 
## Foundational Type Classes 🧮

In functional programming, we often use **type classes** to define common behaviors that can be applied to a wide range of data types. These act as interfaces that allow us to write more abstract and reusable code. In `higher-kinded-j`, we provide a number of these type classes to enable powerful functional patterns.

This document will cover two foundational type classes: `Semigroup` and `Monoid`. Understanding these will give you a solid foundation for many of the more advanced concepts in the library.

---

## **`Semigroup<A>`**

A **`Semigroup`** is one of the simplest and most fundamental type classes. It provides a blueprint for types that have a single, associative way of being combined.

### What is it?

A `Semigroup` is a type class for any data type that has a `combine` operation. This operation takes two values of the same type and merges them into a single value of that type. The only rule is that this operation must be **associative**.

This means that for any values `a`, `b`, and `c`:

`(a.combine(b)).combine(c)` must be equal to `a.combine(b.combine(c))`

The interface for `Semigroup` in `hkj-api` is as follows:


``` java 
public interface Semigroup<A> {
    A combine(A a1, A a2);
}
```

### Common Instances: The `Semigroups` Utility

To make working with `Semigroup` easier, `higher-kinded-j` provides a `Semigroups` utility interface with static factory methods for common instances.

**Java**

```
// Get a Semigroup for concatenating Strings
Semigroup<String> stringConcat = Semigroups.string();

// Get a Semigroup for concatenating Strings with a delimiter
Semigroup<String> stringConcatDelimited = Semigroups.string(", ");

// Get a Semigroup for concatenating Lists
Semigroup<List<Integer>> listConcat = Semigroups.list();
```

### Where is it used in `higher-kinded-j`?

The primary and most powerful use case for `Semigroup` in this library is to enable **error accumulation** with the **`Validated`** data type.

When you use the `Applicative` instance for `Validated`, you must provide a `Semigroup` for the error type. This tells the applicative how to combine errors when multiple invalid computations occur.

**Example: Accumulating Validation Errors**


``` java
// Create an applicative for Validated that accumulates String errors by joining them.
Applicative<Validated.Witness<String>> applicative =
    ValidatedMonad.instance(Semigroups.string("; "));

// Two invalid results
Validated<String, Integer> invalid1 = Validated.invalid("Field A is empty");
Validated<String, Integer> invalid2 = Validated.invalid("Field B is not a number");

// Combine them using the applicative's map2 method
Kind<Validated.Witness<String>, Integer> result =
    applicative.map2(
        VALIDATED.widen(invalid1),
        VALIDATED.widen(invalid2),
        (val1, val2) -> val1 + val2
    );

// The errors are combined using our Semigroup
// Result: Invalid("Field A is empty; Field B is not a number")
System.out.println(VALIDATED.narrow(result));
```

---

## **`Monoid<A>`**

A **`Monoid`** is a `Semigroup` with a special "identity" or "empty" element. This makes it even more powerful, as it provides a way to have a "starting" or "default" value.

### What is it?

A `Monoid` is a type class for any data type that has an associative `combine` operation (from `Semigroup`) and an `empty` value. This `empty` value is a special element that, when combined with any other value, returns that other value.

This is known as the **identity law**. For any value `a`:

`a.combine(empty())` must be equal to `a``empty().combine(a)` must be equal to `a`

The interface for `Monoid` in `hkj-api` extends `Semigroup`:


``` java 
public interface Monoid<A> extends Semigroup<A> {
    A empty();
}
```

### Common Instances: The `Monoids` Utility

Similar to `Semigroups`, the library provides a `Monoids` utility interface for creating common instances.

**Java**

```
// Get a Monoid for integer addition (empty = 0)
Monoid<Integer> intAddition = Monoids.integerAddition();

// Get a Monoid for String concatenation (empty = "")
Monoid<String> stringMonoid = Monoids.string();

// Get a Monoid for boolean AND (empty = true)
Monoid<Boolean> booleanAnd = Monoids.booleanAnd();
```

### Where it is used in `higher-kinded-j`

A `Monoid` is essential for **folding** (or reducing) a data structure. The `empty` element provides a safe starting value, which means you can correctly fold a collection that might be empty.

This is formalised in the **`Foldable`** typeclass, which has a `foldMap` method. This method maps every element in a structure to a monoidal type and then combines all the results.

**Example: Using `foldMap` with different Monoids**


``` java 
List<Integer> numbers = List.of(1, 2, 3, 4, 5);
Kind<ListKind.Witness, Integer> numbersKind = LIST.widen(numbers);

// 1. Sum the list using the integer addition monoid
Integer sum = ListTraverse.INSTANCE.foldMap(
    Monoids.integerAddition(),
    Function.identity(),
    numbersKind
); // Result: 15

// 2. Concatenate the numbers as strings
String concatenated = ListTraverse.INSTANCE.foldMap(
    Monoids.string(),
    String::valueOf,
    numbersKind
); // Result: "12345"
```

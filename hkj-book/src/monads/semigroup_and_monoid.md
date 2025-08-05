# _Semigroup and  Monoid_:
## Foundational Type Classes
In functional programming, we often use "type classes" to define common behaviors that can be applied to a wide range of data types. These act as interfaces that allow us to write more abstract and reusable code. In `higher-kinded-j`, we provide a number of these type classes to enable powerful functional patterns.

This document will cover three foundational type classes: `Semigroup`,and  `Monoid`. Understanding these will give you a solid foundation for many of the more advanced concepts in the library.

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

### Why is it useful?

The `Semigroup` type class is useful whenever you have a data type that can be combined. By abstracting this `combine` operation into a type class, we can write generic functions that can combine any two values of a `Semigroup` type, without needing to know the specific type.

Some common examples of semigroups include:

* **String Concatenation**: `"hello" + " " + "world"`
* **List Concatenation**: `[1, 2] + [3, 4]`
* **Integer Addition**: `1 + 2 + 3`

### Where is it used in `higher-kinded-j`?

A key use case for `Semigroup` is in the **`Writer`** monad. The `Writer` monad allows you to accumulate a log value alongside a computation. To combine these log values at each step, we need a `Semigroup` for the log type. For example, if you are logging strings, you would use a `Semigroup<String>` to concatenate them.

<hr>

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

### Why is it useful?

The `Monoid` type class is useful when you need to combine a collection of values into a single value. The `empty` element gives you a starting point. For example, if you have a list of numbers and want to sum them, you can start with `0` (the `empty` value for addition) and combine each number in the list.

Some common examples of monoids include:

* **Integer Addition**, where `empty` is `0`.
* **String Concatenation**, where `empty` is `""`.
* **List Concatenation**, where `empty` is an empty list.

### Where is it used in `higher-kinded-j`?

Just like `Semigroup`, the **`Monoid`** type class is essential for the **`Writer`** monad. When you start a `Writer` computation, you need an initial log value. The `empty` value from the `Monoid` provides this starting point. For example, a `Writer<String, A>` would start with an empty string `""` as its initial log.


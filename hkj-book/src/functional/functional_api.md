# Core API Interfaces: The Building Blocks

The `hkj-api` module contains the heart of the `higher-kinded-j` library—a set of interfaces that define the core functional programming abstractions. These are the building blocks you will use to write powerful, generic, and type-safe code.

This document provides a high-level overview of the most important interfaces, which are often referred to as **type classes**.

---

## Core HKT Abstraction

At the very centre of the library is the `Kind` interface, which makes higher-kinded types possible in Java.

* **`Kind<F, A>`**: This is the foundational interface that emulates a higher-kinded type. It represents a type `F` that is generic over a type `A`. For example, `Kind<ListKind.Witness, String>` represents a `List<String>`. You will see this interface used everywhere as the common currency for all our functional abstractions.

---

## The Monad Hierarchy

The most commonly used type classes form a hierarchy of power and functionality, starting with `Functor` and building up to `Monad`.

### **`Functor<F>`**

A **`Functor`** is a type class for any data structure that can be "mapped over". It provides a single operation, `map`, which applies a function to the value(s) inside the structure without changing the structure itself.

* **Key Method**: `map(Function<A, B> f, Kind<F, A> fa)`
* **Intuition**: If you have a `List<A>` and a function `A -> B`, a `Functor` for `List` lets you produce a `List<B>`. The same logic applies to `Optional`, `Either`, `Try`, etc.

### **`Applicative<F>`**

An **`Applicative`** (or Applicative Functor) is a `Functor` with more power. It allows you to apply a function that is itself wrapped in the data structure. This is essential for combining multiple independent computations.

* **Key Methods**:
  * `of(A value)`: Lifts a normal value `A` into the applicative context `F<A>`.
  * `ap(Kind<F, Function<A, B>> ff, Kind<F, A> fa)`: Applies a wrapped function to a wrapped value.
* **Intuition**: If you have an `Optional<Function<A, B>>` and an `Optional<A>`, you can use the `Applicative` for `Optional` to get an `Optional<B>`. This is how `Validated` is able to accumulate errors from multiple independent validation steps.

### **`Monad<F>`**

A **`Monad`** is an `Applicative` that adds the power of sequencing dependent computations. It provides a way to chain operations together, where the result of one operation is fed into the next.

* **Key Method**: `flatMap(Function<A, Kind<F, B>> f, Kind<F, A> fa)`
* **Intuition**: `flatMap` is the powerhouse of monadic composition. It takes a value from a context (like an `Optional<A>`), applies a function that returns a *new* context (`A -> Optional<B>`), and flattens the result into a single context (`Optional<B>`). This is what enables the elegant, chainable workflows you see in the examples.

### **`MonadError<F, E>`**

A **`MonadError`** is a specialised `Monad` that has a defined error type `E`. It provides explicit methods for raising and handling errors within a monadic workflow.

* **Key Methods**:
  * `raiseError(E error)`: Lifts an error `E` into the monadic context `F<A>`.
  * `handleErrorWith(Kind<F, A> fa, Function<E, Kind<F, A>> f)`: Provides a way to recover from a failed computation.

---

## Data Aggregation Type Classes

These type classes define how data can be combined and reduced.

### **`Semigroup<A>`**

A **`Semigroup`** is a simple type class for any type `A` that has an associative `combine` operation. It's the foundation for any kind of data aggregation.

* **Key Method**: `combine(A a1, A a2)`
* **Use Case**: Its primary use in this library is to tell a `Validated``Applicative` how to accumulate errors.

### **`Monoid<A>`**

A **`Monoid`** is a `Semigroup` that also has an "empty" or "identity" element. This is a value that, when combined with any other value, does nothing.

* **Key Methods**:
  * `combine(A a1, A a2)` (from `Semigroup`)
  * `empty()`
* **Use Case**: Essential for folding data structures, where `empty()` provides the starting value for the reduction.

---

## Structure-Iterating Type Classes

These type classes define how to iterate over and manipulate the contents of a data structure in a generic way.

### **`Foldable<F>`**

A **`Foldable`** is a type class for any data structure `F` that can be reduced to a single summary value. It uses a `Monoid` to combine the elements.

* **Key Method**: `foldMap(Monoid<M> monoid, Function<A, M> f, Kind<F, A> fa)`
* **Intuition**: It abstracts the process of iterating over a collection and aggregating the results.

### **`Traverse<F>`**

A **`Traverse`** is a powerful type class that extends both `Functor` and `Foldable`. It allows you to iterate over a data structure `F<A>` and apply an effectful function `A -> G<B>` at each step, collecting the results into a single effect `G<F<B>>`.

* **Key Method**: `traverse(Applicative<G> applicative, Function<A, Kind<G, B>> f, Kind<F, A> fa)`
* **Use Case**: This is incredibly useful for tasks like validating every item in a `List`, where the validation returns a `Validated`. The result is a single `Validated` containing either a `List` of all successful results or an accumulation of all errors.

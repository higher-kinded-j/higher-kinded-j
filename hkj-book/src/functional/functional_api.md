# Core API Interfaces: The Building Blocks

The `hkj-api` module contains the heart of the `higher-kinded-j` library: a set of interfaces that define the core functional programming abstractions. These are the building blocks you will use to write powerful, generic, and type-safe code.

This document provides a high-level overview of the most important interfaces, which are often referred to as **type classes**.

~~~admonish info title="What You'll Learn"
- How the `Kind<F, A>` interface enables higher-kinded types in Java and serves as the foundation for all functional abstractions
- The monad hierarchy from `Functor` through `Applicative` to `Monad`, plus specialized variants like `MonadError`, `Alternative`, `Selective`, and `MonadZero`
- How to combine and aggregate data using `Semigroup` and `Monoid` type classes for operations like error accumulation
- How to iterate over and transform data structures generically using `Foldable` and `Traverse` for tasks like validation and collection processing
- How dual-parameter type classes like `Profunctor` and `Bifunctor` enable advanced data transformations and power optics
~~~

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

### **`Alternative<F>`**

An **`Alternative`** is an `Applicative` that adds the concept of choice and failure. It provides operations for combining alternatives and representing empty/failed computations. Alternative sits at the same level as `Applicative` in the type class hierarchy.

* **Key Methods**:
  * `empty()`: Returns the empty/failure element for the applicative.
  * `orElse(Kind<F, A> fa, Supplier<Kind<F, A>> fb)`: Combines two alternatives, preferring the first if it succeeds, otherwise evaluating and returning the second.
  * `guard(boolean condition)`: Returns success (`of(Unit.INSTANCE)`) if true, otherwise empty.
* **Use Case**: Essential for parser combinators, fallback chains, non-deterministic computation, and trying multiple alternatives with lazy evaluation.

### **`Selective<F>`**

A **`Selective`** functor sits between `Applicative` and `Monad` in terms of power. It extends `Applicative` with the ability to conditionally apply effects based on the result of a previous computation, whilst maintaining a static structure where all possible branches are visible upfront.

* **Key Methods**:
  * `select(Kind<F, Choice<A, B>> fab, Kind<F, Function<A, B>> ff)`: Core operation that conditionally applies a function based on a `Choice`.
  * `whenS(Kind<F, Boolean> fcond, Kind<F, Unit> fa)`: Conditionally executes an effect based on a boolean condition.
  * `ifS(Kind<F, Boolean> fcond, Kind<F, A> fthen, Kind<F, A> felse)`: Provides if-then-else semantics with both branches visible upfront.
* **Use Case**: Perfect for feature flags, conditional logging, configuration-based behaviour, and any scenario where you need conditional effects with static analysis capabilities.

### **`MonadZero<F>`**

A **`MonadZero`** is a `Monad` that also extends `Alternative`, combining monadic bind with choice operations. It adds the concept of a "zero" or "empty" element, allowing it to represent failure or absence.

* **Key Methods**:
  * `zero()`: Returns the zero/empty element for the monad (implements `empty()` from Alternative).
  * Inherits `orElse()` and `guard()` from `Alternative`.
* **Use Case**: Primarily enables filtering in for-comprehensions via the `when()` clause at all supported arities (up to 8 bindings). Also provides all Alternative operations for monadic contexts. Implemented by List, Maybe, Optional, and Stream.

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

---

## Dual-Parameter Type Classes

These type classes work with types that take two type parameters, such as functions, profunctors, and bifunctors.

### **`Profunctor<P>`**

A **`Profunctor`** is a type class for any type constructor `P<A, B>` that is contravariant in its first parameter and covariant in its second. This is the abstraction behind functions and many data transformation patterns.

~~~admonish note
New to variance terminology? See the [Glossary](../glossary.md) for detailed explanations of covariant, contravariant, and invariant with Java-focused examples.
~~~

* **Key Methods**:
  * `lmap(Function<C, A> f, Kind2<P, A, B> pab)`: Pre-process the input (contravariant mapping)
  * `rmap(Function<B, C> g, Kind2<P, A, B> pab)`: Post-process the output (covariant mapping)
  * `dimap(Function<C, A> f, Function<B, D> g, Kind2<P, A, B> pab)`: Transform both input and output simultaneously
* **Use Case**: Essential for building flexible data transformation pipelines, API adapters, and validation frameworks that can adapt to different input and output formats without changing core business logic.

### **Profunctors in Optics**

Importantly, every optic in higher-kinded-j is fundamentally a profunctor. This means that `Lens`, `Prism`, `Iso`, and `Traversal` all support profunctor operations through their `contramap`, `map`, and `dimap` methods. This provides incredible flexibility for adapting optics to work with different data types and structures, making them highly reusable across different contexts and API boundaries.

### **`Bifunctor<F>`**

A **`Bifunctor`** is a type class for any type constructor `F<A, B>` that is covariant in *both* its type parameters. Unlike `Profunctor`, which is contravariant in the first parameter, `Bifunctor` allows you to map over both sides independently or simultaneously.

~~~admonish note
New to variance terminology? See the [Glossary](../glossary.md) for detailed explanations of covariant, contravariant, and invariant with Java-focused examples.
~~~

* **Key Methods**:
  * `bimap(Function<A, C> f, Function<B, D> g, Kind2<F, A, B> fab)`: Transform both type parameters simultaneously
  * `first(Function<A, C> f, Kind2<F, A, B> fab)`: Map over only the first type parameter
  * `second(Function<B, D> g, Kind2<F, A, B> fab)`: Map over only the second type parameter
* **Use Case**: Essential for transforming both channels of sum types (like `Either<L, R>` or `Validated<E, A>`) or product types (like `Tuple2<A, B>` or `Writer<W, A>`), where both parameters hold data rather than representing input/output relationships. Perfect for API response transformation, validation pipelines, data migration, and error handling scenarios.

---

**Previous:** [Introduction](ch_intro.md)
**Next:** [Functor](functor.md)

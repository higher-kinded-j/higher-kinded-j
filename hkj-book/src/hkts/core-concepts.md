# Core Concepts of Higher-Kinded-J

![concepts.png](../images/concepts.png)

~~~admonish info title="What You'll Learn"
- How the Kind<F, A> interface simulates higher-kinded types in Java
- The role of witness types in representing type constructors
- Understanding defunctionalisation and how it enables HKT simulation
- The difference between internal library types and external Java types
- How type classes provide generic operations across different container types
~~~

Higher-Kinded-J employs several key components to emulate Higher-Kinded Types (HKTs) and associated functional type classes in Java. Understanding these is crucial for using and extending the library.

[Feel free to skip ahead to the examples and come back later for the theory](hkt_basic_examples.md)

## 1. The HKT Problem in Java

As we have already discussed, Java's type system lacks native Higher-Kinded Types. We can easily parametrise a type by another type (like `List<String>`), but we cannot easily parametrise a type or method by a *type constructor* itself (like `F<_>`). We can't write `void process<F<_>>(F<Integer> data)` to mean "process any container F of Integers".

~~~ admonish warning
You will often see Higher-Kinded Types represented with an underscore, like `F<_>` (e.g., `List<_>`, `Optional<_>`). This notation, borrowed from languages like Scala, represents a "type constructor"—a type that is waiting for a type parameter. It's important to note that this underscore is a conceptual placeholder and is not the same as Java's `?` wildcard, which is used for instantiated types. Our library provides a way to simulate this `F<_>` concept in Java.

~~~

## 2. The `Kind<F, A>` Bridge

At the very centre of the library are the `Kind` interfaces, which make higher-kinded types possible in Java.

* **`Kind<F, A>`**: This is the foundational interface that emulates a higher-kinded type. It represents a type `F` that is generic over a type `A`. For example, `Kind<ListKind.Witness, String>` represents a `List<String>`. You will see this interface used everywhere as the common currency for all our functional abstractions.

* **`Kind2<F, A, B>`**: This interface extends the concept to types that take two type parameters, such as `Function<A, B>` or `Either<L, R>`. For example, `Kind2<FunctionKind.Witness, String, Integer>` represents a `Function<String, Integer>`. This is essential for working with profunctors and other dual-parameter abstractions.

![defunctionalisation_internal.svg](../images/puml/defunctionalisation_internal.svg)

* **Purpose:** To simulate the application of a type constructor `F` (like `List`, `Optional`, `IO`) to a type argument `A` (like `String`, `Integer`), representing the concept of `F<A>`.
* **`F` (Witness Type):** This is the crucial part of the simulation. Since `F<_>` isn't a real Java type parameter, we use a *marker type* (often an empty interface specific to the constructor) as a "witness" or stand-in for `F`. Examples:
  * `ListKind<ListKind.Witness>` represents the `List` type constructor.
  * `OptionalKind<OptionalKind.Witness>` represents the `Optional` type constructor.
  * `EitherKind.Witness<L>` represents the `Either<L, _>` type constructor (where `L` is fixed).
  * `IOKind<IOKind.Witness>` represents the `IO` type constructor.
* **`A` (Type Argument):** The concrete type contained within or parametrised by the constructor (e.g., `Integer` in `List<Integer>`).
* **How it Works:** The library provides a seamless bridge between a standard java type, like a `java.util.List<Integer>`and its `Kind` representation `Kind<ListKind.Witness, Integer>`. Instead of requiring you to manually wrap objects, this conversion is handled by static helper methods, typically `widen` and `narrow`.
  * To treat a `List<Integer>` as a `Kind`, you use a helper function like `LIST.widen()`.
  * This `Kind` object can then be passed to generic functions (such as `map` or `flatMap` from a `Functor` or `Monad` instance) that expect `Kind<F, A>`.
* **Reference:** [`Kind.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/Kind.java)

~~~admonish tip
For quick definitions of HKT concepts like Kind, Witness Types, and Defunctionalisation, see the [Glossary](../glossary.md).
~~~

## 3. Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`)

These are interfaces that define standard functional operations that work *generically* over any simulated type constructor `F` (represented by its witness type) for which an instance of the type class exists. They operate on `Kind<F, A>` objects.

![core_typeclasses_high_level.svg](../images/puml/core_typeclasses_high_level.svg)

* **`Functor<F>`:**
  * Defines `map(Function<A, B> f, Kind<F, A> fa)`: Applies a function `f: A -> B` to the value(s) inside the context `F` without changing the context's structure, resulting in a `Kind<F, B>`. Think `List.map`, `Optional.map`.
  * Laws: Identity (`map(id) == id`), Composition (`map(g.compose(f)) == map(g).compose(map(f))`).
  * Reference: [`Functor.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/Functor.java)
* **`Applicative<F>`:**
  * Extends `Functor<F>`.
  * Adds `of(A value)`: Lifts a pure value `A` into the context `F`, creating a `Kind<F, A>`. (e.g., `1` becomes `Optional.of(1)` wrapped in `Kind`).
  * Adds `ap(Kind<F, Function<A, B>> ff, Kind<F, A> fa)`: Applies a function wrapped in context `F` to a value wrapped in context `F`, returning a `Kind<F, B>`. This enables combining multiple independent values within the context.
  * Provides default `mapN` methods (e.g., `map2`, `map3`) built upon `ap` and `map`.
  * Laws: Identity, Homomorphism, Interchange, Composition.
  * Reference: [`Applicative.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/Applicative.java)
* **`Monad<F>`:**
  * Extends `Applicative<F>`.
  * Adds `flatMap(Function<A, Kind<F, B>> f, Kind<F, A> ma)`: Sequences operations within the context `F`. Takes a value `A` from context `F`, applies a function `f` that returns a *new context* `Kind<F, B>`, and returns the result flattened into a single `Kind<F, B>`. Essential for chaining dependent computations (e.g., chaining `Optional` calls, sequencing `CompletableFuture`s, combining `IO` actions). Also known in functional languages as `bind` or `>>=`.
  * Laws: Left Identity, Right Identity, Associativity.
  * Reference: [`Monad.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/Monad.java)
* **`MonadError<F, E>`:**
  * Extends `Monad<F>`.
  * Adds error handling capabilities for contexts `F` that have a defined error type `E`.
  * Adds `raiseError(E error)`: Lifts an error `E` into the context `F`, creating a `Kind<F, A>` representing the error state (e.g., `Either.Left`, `Try.Failure` or failed `CompletableFuture`).
  * Adds `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)`: Allows recovering from an error state `E` by providing a function that takes the error and returns a *new context* `Kind<F, A>`.
  * Provides default recovery methods like `handleError`, `recover`, `recoverWith`.
  * Reference: [`MonadError.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-api/src/main/java/org/higherkindedj/hkt/MonadError.java)

## 4. Defunctionalisation (Per Type Constructor)

For each Java type constructor (like `List`, `Optional`, `IO`) you want to simulate as a Higher-Kinded Type, a specific pattern involving several components is used. The exact implementation differs slightly depending on whether the type is defined *within* the Higher-Kinded-J library (e.g., `Id`, `Maybe`, `IO`, monad transformers) or if it's an *external type* (e.g., `java.util.List`, `java.util.Optional`, `java.util.concurrent.CompletableFuture`).

**Common Components:**

* **The `XxxKind` Interface:** A specific marker interface, for example, `OptionalKind<A>`. This interface extends `Kind<F, A>`, where `F` is the witness type representing the type constructor.
  * **Example:** `public interface OptionalKind<A> extends Kind<OptionalKind.Witness, A> { /* ... Witness class ... */ }`
  * The `Witness` (e.g., `OptionalKind.Witness`) is a static nested final class (or a separate accessible class) within `OptionalKind`. This `Witness` type is what's used as the `F` parameter in generic type classes like `Monad<F>`.

* **The `KindHelper` Class (e.g., `OptionalKindHelper`):** A crucial utility `widen` and `narrow` methods:
  * `widen(...)`: Converts the standard Java type (e.g., `Optional<String>`) into its `Kind<F, A>` representation.
  * `narrow(Kind<F, A> kind)`: Converts the `Kind<F, A>` representation back to the underlying Java type (e.g., `Optional<String>`).
    * **Crucially, this method throws `KindUnwrapException` if the input `kind` is structurally invalid** (e.g., `null`, the wrong `Kind` type, or, where applicable, a `Holder` containing `null` where it shouldn't). This ensures robustness.
  * May contain other convenience factory methods.

* **Type Class Instance(s):** Concrete classes implementing `Functor<F>`, `Monad<F>`, etc., for the specific witness type `F` (e.g., `OptionalMonad implements Monad<OptionalKind.Witness>`). These instances use the `KindHelper`'s `widen` and `narrow` methods to operate on the underlying Java types.

**External Types:**

![defunctionalisation_external.svg](../images/puml/defunctionalisation_external.svg)

* **For Types Defined Within Higher-Kinded-J (e.g., `Id`, `Maybe`, `IO`, Monad Transformers like `EitherT`):**
    * These types are designed to directly participate in the HKT simulation.
    * The type itself (e.g., `Id<A>`, `MaybeT<F, A>`) will directly implement its corresponding `XxxKind` interface (e.g., `Id<A> implements IdKind<A>`, where `IdKind<A> extends Kind<IdKind.Witness, A>`).
    * In this case, a separate `Holder` record is **not needed** for the primary `wrap`/`unwrap` mechanism in the `KindHelper`.
    * `XxxKindHelper.wrap(Id<A> id)` would effectively be a type cast (after null checks) to `Kind<IdKind.Witness, A>` because `Id<A>` *is already* an `IdKind<A>`.
    * `XxxKindHelper.unwrap(Kind<IdKind.Witness, A> kind)` would check `instanceof Id` (or `instanceof MaybeT`, etc.) and perform a cast.

This distinction is important for understanding how `wrap` and `unwrap` function for different types. However, from the perspective of a user of a type class instance (like `OptionalMonad`), the interaction remains consistent: you provide a `Kind` object, and the type class instance handles the necessary operations.

## 5. The `Unit` Type

In functional programming, it's common to have computations or functions that perform an action (often a side effect) but do not produce a specific, meaningful result value. In Java, methods that don't return a value use the `void` keyword. However, `void` is not a first-class type and cannot be used as a generic type parameter `A` in `Kind<F, A>`.

Higher-Kinded-J provides the `org.higherkindedj.hkt.Unit` type to address this.

* **Purpose:** `Unit` is a type that has exactly one value, `Unit.INSTANCE`. It is used to represent the successful completion of an operation that doesn't yield any other specific information. Think of it as a functional equivalent of `void`, but usable as a generic type.
* **Usage in HKT:**
    * When a monadic action `Kind<F, A>` completes successfully but has no specific value to return (e.g., an `IO` action that prints to the console), `A` can be `Unit`. The action would then be `Kind<F, Unit>`, and its successful result would conceptually be `Unit.INSTANCE`. For example, `IO<Unit>` for a print operation.
    * In `MonadError<F, E>`, if the error state `E` simply represents an absence or a failure without specific details (like `Optional.empty()` or `Maybe.Nothing()`), `Unit` can be used as the type for `E`. The `raiseError` method would then be called with `Unit.INSTANCE`. For instance, `OptionalMonad` implements `MonadError<OptionalKind.Witness, Unit>`, and `MaybeMonad` implements `MonadError<MaybeKind.Witness, Unit>`.
* **Example:**
    ```java
    // An IO action that just performs a side effect (printing)
    Kind<IOKind.Witness, Unit> printAction = IOKindHelper.delay(() -> {
        System.out.println("Effect executed!");
        return Unit.INSTANCE; // Explicitly return Unit.INSTANCE
    });
    IOKindHelper.unsafeRunSync(printAction); // Executes the print

    // Optional treated as MonadError<..., Unit>
    OptionalMonad optionalMonad = OptionalMonad.INSTANCE;
    Kind<OptionalKind.Witness, String> emptyOptional = optionalMonad.raiseError(Unit.INSTANCE); // Creates Optional.empty()
    ```
* **Reference:** [`Unit.java`](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/unit/Unit.java)

## 6. Error Handling Philosophy

* **Domain Errors:** These are expected business-level errors or alternative outcomes. They are represented *within* the structure of the simulated type (e.g., `Either.Left`, `Maybe.Nothing`, `Try.Failure`, a failed `CompletableFuture`, potentially a specific result type within `IO`). These are handled using the type's specific methods or `MonadError` capabilities (`handleErrorWith`, `recover`, `fold`, `orElse`, etc.) *after* successfully unwrapping the `Kind`.
* **Simulation Errors (`KindUnwrapException`):** These indicate a problem with the HKT simulation *itself* – usually a programming error. Examples include passing `null` to `unwrap`, passing a `ListKind` to `OptionalKindHelper.unwrap`, or (if it were possible) having a `Holder` record contain a `null` reference to the underlying Java object it's supposed to hold. These are signalled by throwing the unchecked `KindUnwrapException` from `unwrap` methods to clearly distinguish infrastructure issues from domain errors. You typically shouldn't need to catch `KindUnwrapException` unless debugging the simulation usage itself.

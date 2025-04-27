# Core Concepts of the HKT Simulation

This simulation employs several key components to emulate Higher-Kinded Types (HKTs) and associated functional type classes in Java. Understanding these is crucial for using and extending the library.

## 1. The HKT Problem in Java

Java's type system lacks native Higher-Kinded Types. We can easily parameterise a type by another type (like `List<String>`), but we cannot easily parameterise a type or method by a *type constructor* itself (like `F<_>`). We can't write `void process<F<_>>(F<Integer> data)` to mean "process any container F of Integers".

## 2. The `Kind<F, A>` Bridge

* **Purpose:** To simulate the application of a type constructor `F` (like `List`, `Optional`, `IO`) to a type argument `A` (like `String`, `Integer`), representing the concept of `F<A>`.
* **`F` (Witness Type):** This is the crucial part of the simulation. Since `F<_>` isn't a real Java type parameter, we use a *marker type* (often an empty interface specific to the constructor) as a "witness" or stand-in for `F`. Examples:
  * `ListKind<?>` represents the `List` type constructor.
  * `OptionalKind<?>` represents the `Optional` type constructor.
  * `EitherKind<L, ?>` represents the `Either<L, _>` type constructor (where `L` is fixed).
  * `IOKind<?>` represents the `IO` type constructor.
* **`A` (Type Argument):** The concrete type contained within or parameterised by the constructor (e.g., `Integer` in `List<Integer>`).
* **How it Works:** An actual object, like a `java.util.List<Integer>`, is wrapped in a helper class (e.g., `ListHolder`) which implements `Kind<ListKind<?>, Integer>`. This `Kind` object can then be passed to generic functions that expect `Kind<F, A>`.
* **Reference:** [`Kind.java`](../src/main/java/org/simulation/hkt/Kind.java)

## 3. Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`)

These are interfaces that define standard functional operations that work *generically* over any simulated type constructor `F` (represented by its witness type) for which an instance of the type class exists. They operate on `Kind<F, A>` objects.

* **`Functor<F>`:**
  * Defines `map(Function<A, B> f, Kind<F, A> fa)`: Applies a function `f: A -> B` to the value(s) inside the context `F` without changing the context's structure, resulting in a `Kind<F, B>`. Think `List.map`, `Optional.map`.
  * Laws: Identity (`map(id) == id`), Composition (`map(g.compose(f)) == map(g).compose(map(f))`).
  * Reference: [`Functor.java`](../src/main/java/org/simulation/hkt/Functor.java)
* **`Applicative<F>`:**
  * Extends `Functor<F>`.
  * Adds `of(A value)`: Lifts a pure value `A` into the context `F`, creating a `Kind<F, A>`. (e.g., `1` becomes `Optional.of(1)` wrapped in `Kind`).
  * Adds `ap(Kind<F, Function<A, B>> ff, Kind<F, A> fa)`: Applies a function wrapped in context `F` to a value wrapped in context `F`, returning a `Kind<F, B>`. This enables combining multiple independent values within the context.
  * Provides default `mapN` methods (e.g., `map2`, `map3`) built upon `ap` and `map`.
  * Laws: Identity, Homomorphism, Interchange, Composition.
  * Reference: [`Applicative.java`](../src/main/java/org/simulation/hkt/Applicative.java)
* **`Monad<F>`:**
  * Extends `Applicative<F>`.
  * Adds `flatMap(Function<A, Kind<F, B>> f, Kind<F, A> ma)`: Sequences operations within the context `F`. Takes a value `A` from context `F`, applies a function `f` that returns a *new context* `Kind<F, B>`, and returns the result flattened into a single `Kind<F, B>`. Essential for chaining dependent computations (e.g., chaining `Optional` calls, sequencing `CompletableFuture`s, combining `IO` actions). Also known in functional languages as `bind` or `>>=`.
  * Laws: Left Identity, Right Identity, Associativity.
  * Reference: [`Monad.java`](../src/main/java/org/simulation/hkt/Monad.java)
* **`MonadError<F, E>`:**
  * Extends `Monad<F>`.
  * Adds error handling capabilities for contexts `F` that have a defined error type `E`.
  * Adds `raiseError(E error)`: Lifts an error `E` into the context `F`, creating a `Kind<F, A>` representing the error state (e.g., `Either.Left`, `Try.Failure`, failed `CompletableFuture`).
  * Adds `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)`: Allows recovering from an error state `E` by providing a function that takes the error and returns a *new context* `Kind<F, A>`.
  * Provides default recovery methods like `handleError`, `recover`, `recoverWith`.
  * Reference: [`MonadError.java`](../src/main/java/org/simulation/hkt/MonadError.java)

## 4. Simulation Plumbing (Per Type Constructor)

For each Java type constructor (like `List`, `Optional`, `IO`) you want to simulate:

* **`Kind` Interface:** A specific marker interface extending `Kind<F, A>` (e.g., `OptionalKind<A> extends Kind<OptionalKind<?>, A>`). The `OptionalKind<?>` part is the witness `F`.
* **`Holder` Record:** An internal (often package-private) record that implements the `*Kind` interface and holds the actual underlying Java object (e.g., `OptionalHolder` holds a `java.util.Optional`). This is the concrete implementation of the `Kind`.
* **`KindHelper` Class:** A crucial utility class with static `wrap` and `unwrap` methods:
  * `wrap(JavaType<A> value)`: Takes the standard Java type (e.g., `Optional<String>`) and returns the `Kind<F, A>` simulation type (by creating and returning a `*Holder`). Requires non-null input for the container itself (e.g., the `Optional` instance cannot be null, though it can be `Optional.empty()`).
  * `unwrap(Kind<F, A> kind)`: Takes the `Kind<F, A>` simulation type and returns the underlying Java type (e.g., `Optional<String>`). **Crucially, this method throws `KindUnwrapException` if the input `kind` is structurally invalid** (e.g., `null`, the wrong `Kind` type, or a `Holder` containing `null` where it shouldn't). This ensures robustness within the simulation layer.
  * May contain other convenience factories (e.g., `MaybeKindHelper.just(...)`, `IOKindHelper.delay(...)`).
* **Type Class Instance(s):** Concrete classes implementing `Functor<F>`, `Applicative<F>`, `Monad<F>`, and/or `MonadError<F, E>` for the specific witness type `F` (e.g., `OptionalMonad implements MonadError<OptionalKind<?>, Void>`). These instances contain the logic for `map`, `flatMap`, `of`, `ap`, `raiseError`, etc., using the `wrap` and `unwrap` helpers internally to manipulate the underlying Java types.

## 5. Error Handling Philosophy

* **Domain Errors:** These are expected business-level errors or alternative outcomes. They are represented *within* the structure of the simulated type (e.g., `Either.Left`, `Maybe.Nothing`, `Try.Failure`, a failed `CompletableFuture`, potentially a specific result type within `IO`). These are handled using the type's specific methods or `MonadError` capabilities (`handleErrorWith`, `recover`, `fold`, `orElse`, etc.) *after* successfully unwrapping the `Kind`.
* **Simulation Errors (`KindUnwrapException`):** These indicate a problem with the HKT simulation *itself* – usually a programming error. Examples include passing `null` to `unwrap`, passing a `ListKind` to `OptionalKindHelper.unwrap`, or (if it were possible) having a `Holder` record contain a `null` reference to the underlying Java object it's supposed to hold. These are signalled by throwing the unchecked `KindUnwrapException` from `unwrap` methods to clearly distinguish infrastructure issues from domain errors. You typically shouldn't need to catch `KindUnwrapException` unless debugging the simulation usage itself.

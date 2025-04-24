# Core Concepts of the HKT Simulation

This simulation employs several key components to emulate Higher-Kinded Types (HKTs) and associated functional type classes in Java.

## 1. `Kind<F, A>` - The HKT Bridge

* **Purpose:** Represents the application of a type constructor `F` (like `List`, `Optional`) to a type argument `A` (like `String`, `Integer`), simulating `F<A>`.
* **`F` (Witness Type):** A marker type, usually an empty interface specific to the constructor (e.g., `ListKind<?>`, `OptionalKind<?>`, `EitherKind<L, ?>`). It acts as a unique identifier or "witness" for the type constructor within Java's type system.
* **`A` (Type Argument):** The type contained within or parameterized by the constructor.
* **Reference:** [`Kind.java`](../src/main/java/org/simulation/hkt/Kind.java)

## 2. Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`)

These interfaces define standard functional operations that work over any `Kind<F, A>` for which an instance of the type class exists.

* **`Functor<F>`:**
    * Defines `map(Function<A, B> f, Kind<F, A> fa)`: Applies a function `f` to the value(s) inside the context `F` without changing the context's structure.
    * Laws: Identity (`map(id) == id`), Composition (`map(g.compose(f)) == map(g).compose(map(f))`).
    * Reference: [`Functor.java`](../src/main/java/org/simulation/hkt/Functor.java)
* **`Applicative<F>`:**
    * Extends `Functor<F>`.
    * Adds `of(A value)`: Lifts a pure value `A` into the context `F<A>`.
    * Adds `ap(Kind<F, Function<A, B>> ff, Kind<F, A> fa)`: Applies a function wrapped in context `F` to a value wrapped in context `F`.
    * Provides default `mapN` methods (e.g., `map2`, `map3`) for combining multiple values within the context.
    * Laws: Identity, Homomorphism, Interchange, Composition.
    * Reference: [`Applicative.java`](../src/main/java/org/simulation/hkt/Applicative.java)
* **`Monad<F>`:**
    * Extends `Applicative<F>`.
    * Adds `flatMap(Function<A, Kind<F, B>> f, Kind<F, A> ma)`: Sequences operations within the context `F`. Takes a value `A` from context `F`, applies a function `f` that returns a new context `F<B>`, and returns the result flattened into `F<B>`. Also known as `bind` or `>>=`.
    * Laws: Left Identity, Right Identity, Associativity.
    * Reference: [`Monad.java`](../../blob/main/src/main/java/org/simulation/hkt/Monad.java)
* **`MonadError<F, E>`:**
    * Extends `Monad<F>`.
    * Adds error handling capabilities for contexts `F` that have a defined error type `E`.
    * Adds `raiseError(E error)`: Lifts an error `E` into the context `F<A>`.
    * Adds `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)`: Allows recovering from an error state `E` by providing a function that returns a new context `F<A>`.
    * Provides default recovery methods like `handleError`, `recover`, `recoverWith`.
    * Reference: [`MonadError.java`](../src/main/java/org/simulation/hkt/MonadError.java)

## 3. Simulation Plumbing (Per Type Constructor)

For each Java type constructor (like `List`, `Optional`) you want to simulate:

* **`*Kind` Interface:** A specific marker extending `Kind<F, A>` (e.g., `ListKind<A> extends Kind<ListKind<?>, A>`). The `ListKind<?>` part is the witness `F`.
* **`*Holder` Record:** An internal (often package-private) record that implements the `*Kind` interface and holds the actual underlying Java object (e.g., `ListHolder` holds a `java.util.List`).
* **`*KindHelper` Class:** A final class with static `wrap` and `unwrap` methods:
    * `wrap(JavaType<A> value)`: Takes the standard Java type and returns the `Kind<F, A>` simulation type (by creating a `*Holder`). Requires non-null input for the container itself.
    * `unwrap(Kind<F, A> kind)`: Takes the `Kind<F, A>` simulation type and returns the underlying Java type. **Crucially, this method now throws `KindUnwrapException` if the input `kind` is structurally invalid** (e.g., `null`, wrong type, holder contains `null`). This ensures robustness within the simulation layer.
* **Type Class Instance(s):** Concrete classes implementing `Functor<F>`, `Applicative<F>`, `Monad<F>`, and/or `MonadError<F, E>` for the specific witness type `F` (e.g., `ListMonad implements Monad<ListKind<?>>`). These instances contain the logic for `map`, `flatMap`, `of`, `ap`, etc., using `wrap` and `unwrap` internally.

## 4. Error Handling

* **Domain Errors:** Handled by the specific monad's structure (e.g., `Either.Left`, `Maybe.Nothing`, `Optional.empty`, `Try.Failure`, failed `CompletableFuture`) and managed using `MonadError` methods like `handleErrorWith`.
* **Simulation Errors:** Errors related to incorrect use of the HKT simulation itself (e.g., passing `null` to `unwrap`, passing a `Kind` of the wrong type) are now signaled by throwing an unchecked `KindUnwrapException`. This clearly distinguishes infrastructure issues from domain errors.

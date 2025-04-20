# Java Higher-Kinded Type (HKT) Simulation

This simulation was created for the blog post [Higher Kinded Types with Java and Scala](https://magnussmith.github.io/blog/2025/04/01/higher-kinded-types-with-java-and-scala.html) to help illustrate different approaches.

This project demonstrates a technique to simulate Higher-Kinded Types (HKTs) in Java, a feature not natively supported by the language's type system. It uses a defunctionalisation approach, representing type constructors and type classes as interfaces and objects.

## Core Concepts

The simulation relies on several key components:

1.  **`Kind<F, A>` Interface:** This is the cornerstone of the simulation. It represents the application of a type constructor `F` (like `List`, `Optional`) to a type argument `A` (like `String`, `Integer`). Since Java cannot directly express `F<_>` as a type parameter, `Kind<F, A>` serves as a bridge.
    * `F`: The "witness type". This is usually a marker interface specific to the type constructor being simulated (e.g., `ListKind<?>`, `OptionalKind<?>`, `MaybeKind<?>`, `EitherKind<L, ?>`). It uniquely identifies the type constructor.
    * `A`: The type argument applied to the constructor.

2.  **Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`):** These interfaces define common functional operations applicable to generic type constructors (`F`).
    * `Functor<F>`: Defines the `map` operation, allowing a function `A -> B` to be applied to a value inside the context `F`, producing an `F<B>` from an `F<A>`.
    * `Applicative<F>`: Extends `Functor<F>` and adds `of` (lifts `A` into `F<A>`) and `ap` (applies a function `F<A -> B>` to a value `F<A>`). It also provides default `mapN` methods (e.g., `map2`, `map3`, `map4`) for combining multiple values within the context `F`.
    * `Monad<F>`: Extends `Applicative<F>` and adds `flatMap` (also known as `bind`, sequences operations `F<A> -> (A -> F<B>) -> F<B>`).
    * `MonadError<F, E>`: Extends `Monad<F>` and adds methods for error handling within the context `F`, parameterized by an error type `E`. Key methods are `raiseError(E error)` (lifts an error `E` into `F<A>`) and `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)` (recovers from an error state).

3.  **Concrete Implementations:** For each type constructor (like `List`, `Optional`) being simulated, several components are needed:
    * **Kind Interface:** A specific marker interface extending `Kind<F, A>` (e.g., `ListKind<A>`, `OptionalKind<A>`, `MaybeKind<A>`, `EitherKind<L, R>`).
    * **Holder Record/Class:** An internal class (often a `record`) that actually holds the underlying Java type (e.g., `ListHolder`, `OptionalHolder`, `MaybeHolder`, `EitherHolder`).
    * **Helper Class (`*KindHelper`):** Provides static `wrap` and `unwrap` methods to convert between the simulated `Kind<F, A>` type and the underlying Java type (e.g., `ListKindHelper.wrap(List<A>)` returns `ListKind<A>`, `ListKindHelper.unwrap(ListKind<A>)` returns `List<A>`). The `unwrap` methods have been made robust to return default values (e.g., empty collections, `Optional.empty`, `Maybe.nothing`, `Either.Left`) instead of throwing exceptions for null or invalid `Kind` inputs.
    * **Type Class Instances:** Concrete implementations of the type class interfaces for the specific Kind (e.g., `ListFunctor`/`ListMonad`, `OptionalFunctor`/`OptionalMonad`, `MaybeFunctor`/`MaybeMonad`, `EitherFunctor<L>`/`EitherMonad<L>`). The monad instances for `Optional`, `Maybe`, and `Either` also implement `MonadError`.

## Simulated Types

This simulation currently provides support for the following types:

* **`List`:** Represented by `ListKind`, `ListFunctor`, `ListMonad`.
* **`Optional`:** Represented by `OptionalKind`, `OptionalFunctor`, `OptionalMonad` (implements `MonadError<..., Void>`).
* **`Maybe`:** A custom Maybe type (similar to Optional) represented by `MaybeKind`, `MaybeFunctor`, `MaybeMonad` (implements `MonadError<..., Void>`). The `Maybe` type itself is implemented as a sealed interface with `Just` and `Nothing` implementations.
* **`Either<L, R>`:** A custom Either type (for values that can be one of two types, often used for error handling) represented by `EitherKind<L, R>`, `EitherFunctor<L>`, `EitherMonad<L>` (implements `MonadError<..., L>`). The `Either` type itself is implemented as a sealed interface with `Left` and `Right` record implementations. Note that the Functor/Monad instances are right-biased.

## Usage Example (`ListMonad`)

~~~~ java
import org.simulation.hkt.list.ListKind;
import org.simulation.hkt.list.ListKindHelper;
import org.simulation.hkt.list.ListMonad;
import org.simulation.hkt.Kind; // Often needed for types
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

// Instantiate the Monad implementation
ListMonad listMonad = new ListMonad(); // Also an Applicative & Functor

        // 1. Wrap a standard Java List into the Kind simulation
        List<Integer> numbers = Arrays.asList(1, 2, 3);
        Kind<ListKind<?>, Integer> numberKind = ListKindHelper.wrap(numbers); //

        // 2. Use 'map' (from Functor)
        Kind<ListKind<?>, String> stringsKind = listMonad.map(Object::toString, numberKind); //
        System.out.println("Mapped: " + ListKindHelper.unwrap(stringsKind)); // Output: [1, 2, 3]

        // 3. Use 'flatMap' (from Monad)
        Function<Integer, Kind<ListKind<?>, Integer>> duplicateAndMultiply =
                x -> ListKindHelper.wrap(Arrays.asList(x, x * 10)); //

        // Note: The Function returns Kind<ListKind<?>, Integer> as required by flatMap
        Kind<ListKind<?>, Integer> flatMappedList = listMonad.flatMap(duplicateAndMultiply, numberKind); //
        System.out.println("FlatMapped: " + ListKindHelper.unwrap(flatMappedList)); // Output: [1, 10, 2, 20, 3, 30]

        // 4. Use 'of' (from Applicative)
        Kind<ListKind<?>, Integer> singleItemList = listMonad.of(99); //
        System.out.println("Of: " + ListKindHelper.unwrap(singleItemList)); // Output: [99]

        // 5. Use 'ap' (from Applicative)
        Kind<ListKind<?>, Function<Integer, String>> funcKind = listMonad.of(i -> "N" + i);
        Kind<ListKind<?>, String> apResult = listMonad.ap(funcKind, numberKind); //
        System.out.println("Ap: " + ListKindHelper.unwrap(apResult)); // Output: [N1, N2, N3]
~~~~

(See `MonadSimulation.java` for more detailed examples with `Optional`, `Maybe`, and `Either`. 
`See OrderWorkflowRunner.java` for a more complex example using `EitherMonad` and `MonadError` concepts for error propagation and state management via a WorkflowContext object.)


## Motivation and Limitations
This simulation allows writing more abstract, generic code over different container types (like `List`, `Optional`, etc.) using functional patterns defined by type classes. This is useful for avoiding code duplication and building powerful abstractions.

However, this approach has some downsides compared to languages with native HKT support (like Scala):

- Boilerplate: Requires creating multiple interfaces and classes (Kind, Holder, Helper, Functor/Monad instances) for each simulated type.
- Verbosity: Code using the simulation is often more verbose due to the Kind wrapper, explicit witness types, and wrap/unwrap calls.
- Complexity: Understanding the simulation mechanism adds cognitive overhead.
Type Inference: Java's type inference can occasionally struggle with the complex generic types involved.

### Project Structure
The code is organized into packages:

- `org.simulation.hkt`: Core interfaces (`Kind`, `Functor`, `Applicative`, `Monad`, `MonadError`).
- `org.simulation.hkt.list`: Components for `List` simulation.
- `org.simulation.hkt.optional`: Components for `Optional` simulation.
- `org.simulation.hkt.maybe`: Components for `Maybe` simulation and the `Maybe` type itself.
- `org.simulation.hkt.either`: Components for `Either` simulation and the `Either` type itself.
- `org.simulation.hkt.function`: Helper functional interfaces (`Function3`, `Function4`).
- `org.simulation.hkt.example.order`: A practical example demonstrating an order processing workflow using `EitherMonad`.
- `org.simulation.hkt.MonadSimulation`: Contains executable examples demonstrating basic usage of different monads.
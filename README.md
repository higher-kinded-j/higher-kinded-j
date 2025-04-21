# Java Higher-Kinded Type (HKT) Simulation

This simulation was created for the blog post [Higher Kinded Types with Java and Scala](https://magnussmith.github.io/blog/2025/04/01/higher-kinded-types-with-java-and-scala.html) to help illustrate different approaches.

This project demonstrates a technique to simulate Higher-Kinded Types (HKTs) in Java, a feature not natively supported by the language's type system. It uses a defunctionalisation approach, representing type constructors and type classes as interfaces and objects.

## Core Concepts

The simulation relies on several key components:

1.  **`Kind<F, A>` Interface:** This is the cornerstone of the simulation. It represents the application of a type constructor `F` (like `List`, `Optional`, `CompletableFuture`) to a type argument `A` (like `String`, `Integer`). Since Java cannot directly express `F<_>` as a type parameter, `Kind<F, A>` serves as a bridge.
    * `F`: The "witness type". This is usually a marker interface specific to the type constructor being simulated (e.g., `ListKind<?>`, `OptionalKind<?>`, `MaybeKind<?>`, `EitherKind<L, ?>`, `CompletableFutureKind<?>`). It uniquely identifies the type constructor.
    * `A`: The type argument applied to the constructor.

2.  **Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`):** These interfaces define common functional operations applicable to generic type constructors (`F`).
    * `Functor<F>`: Defines the `map` operation, allowing a function `A -> B` to be applied to a value inside the context `F`, producing an `F<B>` from an `F<A>`.
    * `Applicative<F>`: Extends `Functor<F>` and adds `of` (lifts `A` into `F<A>`) and `ap` (applies a function `F<A -> B>` to a value `F<A>`). It also provides default `mapN` methods (e.g., `map2`, `map3`, `map4`) for combining multiple values within the context `F`.
    * `Monad<F>`: Extends `Applicative<F>` and adds `flatMap` (also known as `bind`, sequences operations `F<A> -> (A -> F<B>) -> F<B>`).
    * `MonadError<F, E>`: Extends `Monad<F>` and adds methods for error handling within the context `F`, parameterized by an error type `E`. Key methods are `raiseError(E error)` (lifts an error `E` into `F<A>`) and `handleErrorWith(Kind<F, A> ma, Function<E, Kind<F, A>> handler)` (recovers from an error state).

3.  **Concrete Implementations:** For each type constructor (like `List`, `Optional`, `CompletableFuture`) being simulated, several components are needed:
    * **Kind Interface:** A specific marker interface extending `Kind<F, A>` (e.g., `ListKind<A>`, `OptionalKind<A>`, `MaybeKind<A>`, `EitherKind<L, R>`, `CompletableFutureKind<A>`).
    * **Holder Record/Class:** An internal class (often a `record`) that actually holds the underlying Java type (e.g., `ListHolder`, `OptionalHolder`, `MaybeHolder`, `EitherHolder`, `CompletableFutureHolder`).
    * **Helper Class (`*KindHelper`):** Provides static `wrap` and `unwrap` methods to convert between the simulated `Kind<F, A>` type and the underlying Java type (e.g., `ListKindHelper.wrap(List<A>)` returns `ListKind<A>`, `ListKindHelper.unwrap(ListKind<A>)` returns `List<A>`). The `unwrap` methods have been made robust to return default/error values instead of throwing exceptions for null or invalid `Kind` inputs.
    * **Type Class Instances:** Concrete implementations of the type class interfaces for the specific Kind (e.g., `ListFunctor`/`ListMonad`, `OptionalFunctor`/`OptionalMonad`/`OptionalMonadError`, `MaybeFunctor`/`MaybeMonad`/`MaybeMonadError`, `EitherFunctor<L>`/`EitherMonad<L>`, `CompletableFutureFunctor`/`CompletableFutureApplicative`/`CompletableFutureMonad`/`CompletableFutureMonadError`). The monad instances for `Optional`, `Maybe`, `Either`, and `CompletableFuture` also implement `MonadError`.

## Simulated Types

This simulation currently provides support for the following types:

* **`java.util.List`:** Represented by `ListKind`, `ListFunctor`, `ListMonad`.
* **`java.util.Optional`:** Represented by `OptionalKind`, `OptionalFunctor`, `OptionalMonad`. Implements `MonadError<..., Void>` where `Void` signifies the `Optional.empty()` state.
* **`Maybe`:** A custom Maybe type (similar to Optional) represented by `MaybeKind`, `MaybeFunctor`, `MaybeMonad`. Implements `MonadError<..., Void>` where `Void` signifies the `Nothing` state. The `Maybe` type itself is implemented as a sealed interface with `Just` and `Nothing` implementations.
* **`Either<L, R>`:** A custom Either type (for values that can be one of two types, often used for error handling) represented by `EitherKind<L, R>`, `EitherFunctor<L>`, `EitherMonad<L>`. Implements `MonadError<..., L>` where `L` is the type held by `Left`. The `Either` type itself is implemented as a sealed interface with `Left` and `Right` record implementations. Note that the Functor/Monad instances are right-biased.
* **`java.util.concurrent.CompletableFuture<T>`:** Represented by `CompletableFutureKind`, `CompletableFutureFunctor`, `CompletableFutureApplicative`, `CompletableFutureMonad`, `CompletableFutureMonadError`. Implements `MonadError<..., Throwable>` where `Throwable` represents the exception causing the future to fail. This allows treating asynchronous computations as monadic values.

## Usage Examples

### Example 1: `ListMonad` (Synchronous)

```java
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
Kind<ListKind<?>, Integer> numberKind = ListKindHelper.wrap(numbers);

// 2. Use 'map' (from Functor)
Kind<ListKind<?>, String> stringsKind = listMonad.map(Object::toString, numberKind);
System.out.println("Mapped: " + ListKindHelper.unwrap(stringsKind)); // Output: [1, 2, 3]

// 3. Use 'flatMap' (from Monad)
Function<Integer, Kind<ListKind<?>, Integer>> duplicateAndMultiply =
    x -> ListKindHelper.wrap(Arrays.asList(x, x * 10));

// Note: The Function returns Kind<ListKind<?>, Integer> as required by flatMap
Kind<ListKind<?>, Integer> flatMappedList = listMonad.flatMap(duplicateAndMultiply, numberKind);
System.out.println("FlatMapped: " + ListKindHelper.unwrap(flatMappedList)); // Output: [1, 10, 2, 20, 3, 30]

// 4. Use 'of' (from Applicative)
Kind<ListKind<?>, Integer> singleItemList = listMonad.of(99);
System.out.println("Of: " + ListKindHelper.unwrap(singleItemList)); // Output: [99]

// 5. Use 'ap' (from Applicative)
Kind<ListKind<?>, Function<Integer, String>> funcKind = listMonad.of(i -> "N" + i);
Kind<ListKind<?>, String> apResult = listMonad.ap(funcKind, numberKind);
System.out.println("Ap: " + ListKindHelper.unwrap(apResult)); // Output: [N1, N2, N3]
```


### Example 2: `CompletableFutureMonadError` (Asynchronous)



```java
import org.simulation.hkt.future.CompletableFutureKind;
import org.simulation.hkt.future.CompletableFutureKindHelper;
import org.simulation.hkt.future.CompletableFutureMonadError;
import org.simulation.hkt.Kind;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

// Instantiate the MonadError implementation
CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();

// 1. Lift a value into an async context (completed future)
Kind<CompletableFutureKind<?>, String> futureKind = futureMonad.of("Hello");

// 2. Map asynchronously
Kind<CompletableFutureKind<?>, Integer> lengthKind = futureMonad.map(String::length, futureKind);
// Use helper to block for result (for demonstration only!)
System.out.println("Length (async): " + CompletableFutureKindHelper.join(lengthKind)); // Output: 5

// 3. FlatMap with another async operation
Function<Integer, Kind<CompletableFutureKind<?>, String>> describeLength =
    len -> CompletableFutureKindHelper.wrap(
        CompletableFuture.supplyAsync(() -> "Length is " + len) // Simulate async work
    );
Kind<CompletableFutureKind<?>, String> describedKind = futureMonad.flatMap(describeLength, lengthKind);
System.out.println("Description (async): " + CompletableFutureKindHelper.join(describedKind)); // Output: Length is 5

// 4. Handle errors using MonadError
Kind<CompletableFutureKind<?>, Integer> failedKind = futureMonad.raiseError(new RuntimeException("Boom"));
// Recover from any Throwable with -1
Kind<CompletableFutureKind<?>, Integer> recoveredKind = futureMonad.handleError(failedKind, error -> -1);
System.out.println("Recovered: " + CompletableFutureKindHelper.join(recoveredKind)); // Output: -1

```
(See MonadSimulation.java for more detailed examples with Optional, Maybe, and Either. 
See OrderWorkflowRunner.java for a more complex example using CompletableFutureMonadError to orchestrate a workflow with mixed synchronous/asynchronous steps, using Either for business errors and a WorkflowContext object for state management.)

## Motivation and Limitations
This simulation allows writing more abstract, generic code over different container types (like `List`, `Optional`, `CompletableFuture`, etc.) using functional patterns defined by type classes. This is useful for avoiding code duplication and building powerful abstractions, such as composing complex synchronous or asynchronous workflows with robust error handling.

However, this approach has significant downsides compared to languages with native HKT support (like Scala):
- Boilerplate: Requires creating multiple interfaces and classes (Kind, Holder, Helper, Functor/Applicative/Monad/MonadError instances) for each simulated type.
- Verbosity: Code using the simulation is often more verbose due to the Kind wrapper, explicit witness types, and wrap/unwrap calls.
- Complexity: Understanding the simulation mechanism adds cognitive overhead.
- Type Safety: Relies on casting within helper classes (unwrap), which can lead to runtime errors if used incorrectly, although the provided helpers attempt robustness by returning default/error values.
- Type Inference: Java's type inference can occasionally struggle with the complex generic types involved, sometimes requiring explicit type arguments.

### Project Structure
The code is organized into packages:
- `org.simulation.hkt`: Core interfaces (`Kind`, `Functor`, `Applicative`, `Monad`, `MonadError`).
- `org.simulation.hkt.list`: Components for `List` simulation.
- `org.simulation.hkt.optional`: Components for `Optional` simulation.
- `org.simulation.hkt.maybe`: Components for `Maybe` simulation and the `Maybe` type itself.
- `org.simulation.hkt.either`: Components for `Either` simulation and the `Either` type itself.
- `org.simulation.hkt.future`: Components for `CompletableFuture` simulation.
- `org.simulation.hkt.function`: Helper functional interfaces (`Function3`, `Function4`).
- `org.simulation.hkt.example.order`: A practical example demonstrating an order processing workflow using `EitherMonad`.
- `org.simulation.hkt.MonadSimulation`: Contains executable examples demonstrating basic usage of different monads.

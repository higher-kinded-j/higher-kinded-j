# Java Higher-Kinded Type (HKT) Simulation

This simulation was created for the blog post [Higher Kinded Types with Java and Scala](https://magnussmith.github.io/blog/2025/04/01/higher-kinded-types-with-java-and-scala.html) to help illustrate different approaches.

This project demonstrates a technique to simulate Higher-Kinded Types (HKTs) in Java, a feature not natively supported by the language's type system. It uses a defunctionalisation approach, representing type constructors and type classes as interfaces and objects.

## Core Concepts

The simulation relies on several key components:

1.  **`Kind<F, A>` Interface:** This is the cornerstone of the simulation. It represents the application of a type constructor `F` (like `List`, `Optional`) to a type argument `A` (like `String`, `Integer`). Since Java cannot directly express `F<_>` as a type parameter, `Kind<F, A>` serves as a bridge.
    * `F`: The "witness type". This is usually a marker interface specific to the type constructor being simulated (e.g., `ListKind<?>`, `OptionalKind<?>`, `MaybeKind<?>`, `EitherKind<L, ?>`). It uniquely identifies the type constructor.
    * `A`: The type argument applied to the constructor.

2.  **Type Classes (`Functor`, `Monad`):** These interfaces define common functional operations applicable to generic type constructors (`F`).
    * `Functor<F>`: Defines the `map` operation, allowing a function `A -> B` to be applied to a value inside the context `F`, producing an `F<B>` from an `F<A>`.
    * `Monad<F>`: Extends `Functor<F>` and adds `of` (also known as `pure` or `return`, lifts `A` into `F<A>`) and `flatMap` (also known as `bind`, sequences operations `F<A> -> (A -> F<B>) -> F<B>`).

3.  **Concrete Implementations:** For each type constructor (like `List`, `Optional`) being simulated, several components are needed:
    * **Kind Interface:** A specific marker interface extending `Kind<F, A>` (e.g., `ListKind<A> extends Kind<ListKind<?>, A>`).
    * **Holder Record/Class:** An internal class (often a `record`) that actually holds the underlying Java type (e.g., `ListHolder` holding `java.util.List`, `OptionalHolder` holding `java.util.Optional`, `MaybeHolder` holding `Maybe`, `EitherHolder` holding `Either`).
    * **Helper Class (`*KindHelper`):** Provides static `wrap` and `unwrap` methods to convert between the simulated `Kind<F, A>` type and the underlying Java type (e.g., `ListKindHelper.wrap(List<A>)` returns `ListKind<A>`, `ListKindHelper.unwrap(ListKind<A>)` returns `List<A>`).
    * **Type Class Instances:** Concrete implementations of the type class interfaces for the specific Kind (e.g., `ListFunctor`, `ListMonad`, `OptionalFunctor`, `OptionalMonad`, `MaybeFunctor`, `MaybeMonad`, `EitherFunctor<L>`, `EitherMonad<L>`).

## Simulated Types

This simulation currently provides support for the following types:

* **`java.util.List`:** Represented by `ListKind`, `ListFunctor`, `ListMonad`.
* **`java.util.Optional`:** Represented by `OptionalKind`, `OptionalFunctor`, `OptionalMonad`.
* **`Maybe`:** A custom Maybe type (similar to Optional) represented by `MaybeKind`, `MaybeFunctor`, `MaybeMonad`. The `Maybe` type itself is implemented as a sealed interface with `Just` and `Nothing` implementations.
* **`Either<L, R>`:** A custom Either type (for values that can be one of two types, often used for error handling) represented by `EitherKind<L, R>`, `EitherFunctor<L>`, `EitherMonad<L>`. The `Either` type itself is implemented as a sealed interface with `Left` and `Right` record implementations. Note that the Functor/Monad instances are right-biased.

## Usage Example (`ListMonad`)

```java
import org.simulation.hkt.list.ListKind;
import org.simulation.hkt.list.ListKindHelper;
import org.simulation.hkt.list.ListMonad;
import org.simulation.hkt.Kind; // Often needed for types
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

// Instantiate the Monad implementation
ListMonad listMonad = new ListMonad();

// 1. Wrap a standard Java List into the Kind simulation
List<Integer> numbers = Arrays.asList(1, 2, 3);
ListKind<Integer> numberKind = ListKindHelper.wrap(numbers); //

// 2. Use 'map' (from Functor, via Monad instance)
ListKind<String> stringsKind = listMonad.map(Object::toString, numberKind); //
System.out.println("Mapped: " + ListKindHelper.unwrap(stringsKind)); // Output: [1, 2, 3]

// 3. Use 'flatMap'
Function<Integer, Kind<ListKind<?>, Integer>> duplicateAndMultiply =
    x -> ListKindHelper.wrap(Arrays.asList(x, x * 10)); //

// Note: The Function returns Kind<ListKind<?>, Integer> as required by flatMap
ListKind<Integer> flatMappedList = listMonad.flatMap(duplicateAndMultiply, numberKind); //
System.out.println("FlatMapped: " + ListKindHelper.unwrap(flatMappedList)); // Output: [1, 10, 2, 20, 3, 30]

// 4. Use 'of'
ListKind<Integer> singleItemList = listMonad.of(99); //
System.out.println("Of: " + ListKindHelper.unwrap(singleItemList)); // Output: [99]
```
(See `MonadSimulation.java` for more detailed examples with `Optional`, `Maybe`, and `Either`.)

## Motivation and Limitations
This simulation allows writing more abstract, generic code over different container types (like `List`, `Optional`, etc.) using functional patterns defined by type classes. This is useful for avoiding code duplication and building powerful abstractions.

However, this approach has significant downsides compared to languages with native HKT support (like Scala):

- Boilerplate: Requires creating multiple interfaces and classes (Kind, Holder, Helper, Functor/Monad instances) for each simulated type.
- Verbosity: Code using the simulation is often more verbose due to the Kind wrapper, explicit witness types, and wrap/unwrap calls.
- Complexity: Understanding the simulation mechanism adds cognitive overhead.
Type Inference: Java's type inference can struggle with the complex generic types involved.

### Project Structure
The code is organized into packages:

`org.simulation.hkt`: Core interfaces (Kind, Functor, Monad).
`org.simulation.hkt.list`: Components for List simulation.
`org.simulation.hkt.optional`: Components for Optional simulation.
`org.simulation.hkt.maybe`: Components for Maybe simulation and the Maybe type itself.
`org.simulation.hkt.either`: Components for Either simulation and the Either type itself.
`MonadSimulation.java`: Contains executable examples demonstrating usage.
# Java Higher-Kinded Type (HKT) Simulation

[![codecov](https://codecov.io/gh/MagnusSmith/simulation-hkt/branch/main/graph/badge.svg?token=XO7GSI0ECI)](https://codecov.io/gh/MagnusSmith/simulation-hkt)

This library for Higher-Kinded Types in Java was initially created as a simulation for the blog post [Higher Kinded Types with Java and Scala](https://magnussmith.github.io/blog/2025/04/01/higher-kinded-types-with-java-and-scala.html) to help illustrate different approaches.

This project demonstrates a technique to simulate Higher-Kinded Types (HKTs) in Java, a feature not natively supported by the language's type system.
It uses a defunctionalisation approach, representing type constructors and type classes as interfaces and objects.

## See [Wiki](docs/home.md) for more details and examples







## Introduction: Abstracting Over Computation in Java

Java's powerful type system excels in many areas, but it lacks native support for Higher-Kinded Types (HKTs). This means we cannot easily write code that abstracts over type constructors like `List<A>`, `Optional<A>`, or `CompletableFuture<A>` in the same way we abstract over the type `A` itself. We can't easily define a generic function that works identically for *any* container or computational context (like List, Optional, Future).

This project tackles that challenge by demonstrating a **simulation of HKTs in Java** using a technique inspired by defunctionalization. It allows you to define and use common functional abstractions like `Functor`, `Applicative`, and `Monad` (including `MonadError`) in a way that works *generically* across different simulated type constructors.

**Why bother?** This simulation unlocks several benefits:

* **Write Abstract Code:** Define functions and logic that operate polymorphically over different computational contexts (e.g., handle optionality, asynchronous operations, error handling, or collections using the *same* core logic).
* **Leverage Functional Patterns:** Consistently apply powerful patterns like `map`, `flatMap`, `ap`, `sequence`, `traverse`, and monadic error handling (`raiseError`, `handleErrorWith`) across diverse data types.
* **Build Composable Systems:** Create complex workflows and abstractions by composing smaller, generic pieces, as demonstrated in the included Order Processing example.
* **Understand HKT Concepts:** Provides a practical, hands-on way to understand HKTs and type classes even within Java's limitations.

While this simulation introduces some boilerplate compared to languages with native HKT support, it offers a valuable way to explore these powerful functional programming concepts in Java.

## Core Concepts Explained

The simulation hinges on a few key ideas:

1.  **`Kind<F, A>` Interface:** The cornerstone, simulating the application of a type constructor `F` (like `List`, `Optional`) to a type `A`. Since Java can't express `F<_>` directly as a parameter, `Kind<F, A>` acts as this bridge.
    * `F`: A "witness type" uniquely identifying the constructor (e.g., `ListKind<?>`, `OptionalKind<?>`, `EitherKind<L, ?>`).
    * `A`: The type argument (e.g., `String`, `Integer`).

2.  **Type Classes (`Functor`, `Applicative`, `Monad`, `MonadError`):** Interfaces defining standard operations over the generic context `F`. Implementations exist for each simulated type.
    * `Functor<F>`: Provides `map(A -> B, F<A>) -> F<B>`.
    * `Applicative<F>`: Adds `of(A) -> F<A>` (lifting) and `ap(F<A -> B>, F<A>) -> F<B>` (applying functions within the context). Includes default `mapN` helpers.
    * `Monad<F>`: Adds `flatMap(A -> F<B>, F<A>) -> F<B>` (sequencing).
    * `MonadError<F, E>`: Adds `raiseError(E) -> F<A>` and `handleErrorWith(F<A>, E -> F<A>)` for contexts that have a specific error type `E`.

3.  **Simulation Plumbing:** For each simulated type (e.g., `List`), we need:
    * **`*Kind` Interface:** e.g., `ListKind<A> extends Kind<ListKind<?>, A>`.
    * **`*Holder` Record:** An internal record holding the actual Java type (e.g., `ListHolder` holds `List<A>`).
    * **`*KindHelper` Class:** Static `wrap` and `unwrap` methods to bridge `Kind<F, A>` and the underlying Java type (e.g., `ListKindHelper.wrap/unwrap`). The `unwrap` methods now throw `KindUnwrapException` for invalid `Kind` inputs, ensuring robustness.
    * **Type Class Instances:** Concrete implementations (e.g., `ListMonad` implements `Monad<ListKind<?>>`).

## Simulated Types

This simulation provides HKT wrappers and type class instances for:

* `java.util.List` ([`ListKind`](src/main/java/org/simulation/hkt/list/ListKind.java), [`ListMonad`](src/main/java/org/simulation/hkt/list/ListMonad.java))
* `java.util.Optional` ([`OptionalKind`](src/main/java/org/simulation/hkt/optional/OptionalKind.java), [`OptionalMonad`](src/main/java/org/simulation/hkt/optional/OptionalMonad.java) implementing `MonadError<..., Void>`)
* `Maybe` (custom type) ([`MaybeKind`](src/main/java/org/simulation/hkt/maybe/MaybeKind.java), [`MaybeMonad`](src/main/java/org/simulation/hkt/maybe/MaybeMonad.java) implementing `MonadError<..., Void>`)
* `Either<L, R>` (custom type) ([`EitherKind`](src/main/java/org/simulation/hkt/either/EitherKind.java), [`EitherMonad<L>`](src/main/java/org/simulation/hkt/either/EitherMonad.java) implementing `MonadError<..., L>`)
* `java.util.concurrent.CompletableFuture<T>` ([`CompletableFutureKind`](src/main/java/org/simulation/hkt/future/CompletableFutureKind.java), [`CompletableFutureMonadError`](src/main/java/org/simulation/hkt/future/CompletableFutureMonadError.java) implementing `MonadError<..., Throwable>`)
* `Try<T>` (custom type) ([`TryKind`](src/main/java/org/simulation/hkt/trymonad/TryKind.java), [`TryMonadError`](src/main/java/org/simulation/hkt/trymonad/TryMonadError.java) implementing `MonadError<..., Throwable>`)
* `EitherT<F, L, R>` (Monad Transformer) ([`EitherTKind`](src/main/java/org/simulation/hkt/trans/EitherTKind.java), [`EitherTMonad<F, L>`](src/main/java/org/simulation/hkt/trans/EitherTMonad.java))

*(See individual packages for details)*

## Practical Example: Order Processing Workflow

To see these concepts applied in a more realistic scenario, check out the **Order Processing Example** located in `org.simulation.hkt.example.order`.

This example demonstrates:

* Orchestrating an asynchronous workflow using `CompletableFutureMonadError`.
* Handling domain-specific errors using `Either` (wrapped within the future).
* Using the `EitherT` monad transformer to simplify working with the nested `CompletableFuture<Either<DomainError, T>>` structure.
* Integrating synchronous steps (returning `Either` or `Try`) and asynchronous steps seamlessly within the monadic flow.
* Using `MonadError` capabilities for error handling and recovery.

Explore the `OrderWorkflowRunner` class to see how `flatMap` and `handleErrorWith` are used to build the workflow.

## How to Use This Library

If you want to leverage this simulation in your own code:

1.  **Choose Your Context:** Identify the computational context (type constructor `F`) you need to work with abstractly (e.g., `Optional`, `CompletableFuture`, `List`).
2.  **Find the Type Class Instance:** Locate the corresponding implementation of `Functor`, `Applicative`, `Monad`, or `MonadError` for your chosen context (e.g., `OptionalMonad`, `CompletableFutureMonadError`, `ListMonad`).
3.  **Wrap Your Values:** Use the static `wrap` method from the relevant `*KindHelper` class (e.g., `OptionalKindHelper.wrap(myOptional)`) to convert your standard Java object into the simulated `Kind<F, A>` type.
4.  **Use Type Class Methods:** Call methods like `map`, `flatMap`, `ap`, `handleErrorWith`, etc., on the type class instance, passing your `Kind` values as arguments.
    ```java
    // Example: OptionalMonad
    OptionalMonad optMonad = new OptionalMonad();
    Optional<String> opt1 = Optional.of("hello");
    Optional<Integer> opt2 = Optional.of(5);

    Kind<OptionalKind<?>, String> kind1 = OptionalKindHelper.wrap(opt1);
    Kind<OptionalKind<?>, Integer> kind2 = OptionalKindHelper.wrap(opt2);

    // Use Applicative.map2 via the OptionalMonad instance
    Kind<OptionalKind<?>, String> resultKind = optMonad.map2(
        kind1,
        kind2,
        (str, num) -> str + " " + num // BiFunction combining results
    );
    ```
5.  **Unwrap the Result:** When you need the underlying Java value back (e.g., at the end of a computation), use the static `unwrap` method from the helper (e.g., `Optional<String> finalOpt = OptionalKindHelper.unwrap(resultKind);`). Be prepared to handle potential `KindUnwrapException` if the `Kind` object itself might be invalid due to programming errors.

## Applying to Your Applications

You can apply the patterns and techniques from this simulation in various ways:

* **Generic Utilities:** Write utility functions that work across different monadic types (e.g., a generic `sequence` function to turn a `List<Kind<F, A>>` into a `Kind<F, List<A>>`).
* **Composable Workflows:** Structure complex business logic, especially involving asynchronous steps and error handling (like the Order Example), in a more functional and composable manner.
* **Learning Tool:** Understand HKTs, type classes (Functor, Applicative, Monad), and functional error handling concepts through concrete Java examples.
* **Simulating Custom Types:** Follow the pattern (Kind interface, Holder, Helper, Type Class instances) to make your *own* custom data types or computational contexts work with the provided functional abstractions.

## Limitations

While demonstrating the concept, this simulation approach has inherent limitations in Java compared to languages with native HKTs:

* **Boilerplate:** Requires significant setup code for each simulated type.
* **Verbosity:** Usage often involves explicit wrapping/unwrapping and witness types.
* **Complexity:** Adds cognitive load to understand the simulation mechanism.
* **Type Safety Gaps:** Relies on some internal casting (`unwrap` methods), although the helpers are designed to be robust (throwing `KindUnwrapException` on structural failure).
* **Type Inference:** Java's inference may sometimes need help with the complex generics.

## Project Structure

*(This section remains largely the same as your existing structure description, ensure it's accurate)*

The code is organized into packages:

* `org.simulation.hkt`: Core interfaces (`Kind`, `Functor`, `Applicative`, `Monad`, `MonadError`).
* `org.simulation.hkt.list`: Components for `List` simulation.
* `org.simulation.hkt.optional`: Components for `Optional` simulation.
* `org.simulation.hkt.maybe`: Components for `Maybe` simulation and the `Maybe` type itself.
* `org.simulation.hkt.either`: Components for `Either` simulation and the `Either` type itself.
* `org.simulation.hkt.future`: Components for `CompletableFuture` simulation.
* `org.simulation.hkt.trymonad`: Components for `Try` simulation and the `Try` type itself.
* `org.simulation.hkt.trans`: Monad Transformer (`EitherT`) components.
* `org.simulation.hkt.function`: Helper functional interfaces (`Function3`, `Function4`).
* `org.simulation.hkt.exception`: Custom exceptions like `KindUnwrapException`.
* `org.simulation.example.order`: A practical example demonstrating an order processing workflow.
* `org.simulation.hkt.MonadSimulation`: Contains executable examples demonstrating basic usage.


## Contributing

Contributions to this project are welcome! Whether it's adding new features, improving existing code, or enhancing documentation, your help is GREATLY appreciated.

**Areas for Contribution:**

* **Simulate More Types:** Add HKT simulations and type class instances for other common Java types (e.g., `Stream`, `java.time` types) or functional concepts (e.g., an `IO` monad).
* **Implement More Type Classes:** Add implementations for other useful type classes like `Traverse`, `Monoid`, `Semigroup`, etc., where applicable.
* **Enhance Existing Implementations:** Improve performance, clarity, or robustness of the current simulations and type class instances.
* **Add Examples:** Create more diverse examples showcasing different use cases for the HKT simulation.
* **Improve Documentation:** Clarify concepts, add diagrams, or improve the Wiki/README.
* **Refactor with New Java Features:** Explore opportunities to use features like Structured Concurrency, updated Pattern Matching, etc., to improve the simulation or examples.
* **Testing:** Increase test coverage, particularly for type class laws and edge cases.

**How to Contribute:**

1.  **Fork the Repository:** Create your own fork of the project on GitHub.
2.  **Create a Branch:** Make your changes in a dedicated branch (e.g., `feature/add-stream-kind`, `fix/optional-monad-bug`).
3.  **Develop:** Implement your changes or fixes.
4.  **Add Tests:** Ensure your changes are well-tested. Verify that existing tests pass.
5.  **Commit:** Make clear, concise commit messages.
6.  **Push:** Push your branch to your fork.
7.  **Submit a Pull Request:** Open a Pull Request (PR) from your branch to the `main` branch of the original repository. Describe your changes clearly in the PR description.

If you're unsure where to start or want to discuss an idea, feel free to open a GitHub Issue first.
```
 _   _ _       _                      _   ___           _          _        ___ 
| | | (_)     | |                    | | / (_)         | |        | |      |_  |
| |_| |_  __ _| |__   ___ _ __ ______| |/ / _ _ __   __| | ___  __| |______  | |
|  _  | |/ _` | '_ \ / _ \ '__|______|    \| | '_ \ / _` |/ _ \/ _` |______| | |
| | | | | (_| | | | |  __/ |         | |\  \ | | | | (_| |  __/ (_| |    /\__/ /
\_| |_/_|\__, |_| |_|\___|_|         \_| \_/_|_| |_|\__,_|\___|\__,_|    \____/ 
          __/ |                                                                 
         |___/                                                                  
```
## _Bringing Higher-Kinded Types to Java functional patterns_
[![codecov](https://codecov.io/gh/higher-kinded-j/higher-kinded-j/graph/badge.svg?token=VR0K0ZEDHD)](https://codecov.io/gh/higher-kinded-j/higher-kinded-j)


This library for Higher-Kinded Types in Java was initially created as a simulation for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) to help illustrate different approaches.

This project demonstrates a technique to simulate Higher-Kinded Types (HKTs) in Java, a feature not natively supported by the language's type system.
It uses a defunctionalisation approach, representing type constructors and type classes as interfaces and objects.

## Where to start

**[All the details you need to get started with Higher-Kinded-J can be found here](https://higher-kinded-j.github.io/home.html)**


## Introduction: Abstracting Over Computation in Java

Java's powerful type system excels in many areas, but it lacks native support for Higher-Kinded Types (HKTs). This means we cannot easily write code that abstracts over type constructors like `List<A>`, `Optional<A>`, or `CompletableFuture<A>` in the same way we abstract over the type `A` itself. We can't easily define a generic function that works identically for *any* container or computational context (like List, Optional, Future, IO).

This project tackles that challenge by helping  **simulate of HKTs in Java** using a technique inspired by defunctionalisation. It allows you to define and use common functional abstractions like `Functor`, `Applicative`, and `Monad` (including `MonadError`) in a way that works *generically* across different simulated type constructors.


## Applying to Your Applications

You can apply the patterns and techniques from Higher-Kinded-J in many ways:

* **Generic Utilities:** Write utility functions that work across different monadic types (e.g., a generic `sequence` function to turn a `List<Kind<F, A>>` into a `Kind<F, List<A>>`).
* **Composable Workflows:** Structure complex business logic, especially involving asynchronous steps and error handling (like the Order Example), in a more functional and composable manner.
* **Managing Side Effects:** Use the `IO` monad to explicitly track and sequence side-effecting operations.
* **Deferred Computation:** Use the `Lazy` monad for expensive computations that should only run if needed.
* **Dependency Injection:** Use the `Reader` monad to manage dependencies cleanly.
* **State Management:** Use the `State` monad for computations that need to thread state through.
* **Logging/Accumulation:** Use the `Writer` monad to accumulate logs or other values alongside a computation.
* **Learning Tool:** Understand HKTs, type classes (Functor, Applicative, Monad), and functional error handling concepts through concrete Java examples.
* **Simulating Custom Types:** Follow the pattern (Kind interface, Holder, Helper, Type Class instances) to make your *own* custom data types or computational contexts work with the provided functional abstractions.



## Practical Example: Order Processing Workflow

To see these concepts applied in a more realistic scenario, check out the **Order Processing Example** located in `org.higherkindedj.hkt.example.order`.

This example demonstrates:

* Orchestrating an asynchronous workflow using `CompletableFutureMonadError`.
* Handling domain-specific errors using `Either` (wrapped within the future).
* Using the `EitherT` monad transformer to simplify working with the nested `CompletableFuture<Either<DomainError, T>>` structure.
* Integrating synchronous steps (returning `Either` or `Try`) and asynchronous steps seamlessly within the monadic flow.
* Using `MonadError` capabilities for error handling and recovery.
* Managing dependencies (like logging) via injection.

Explore the `OrderWorkflowRunner` class to see how `flatMap` and `handleErrorWith` are used to build the workflow. See the [Order Processing Example_Walkthrough](https://higher-kinded-j.github.io/order-walkthrough.html). for a detailed explanation.

## How to Use This Library

If you want to leverage this simulation in your own code:

1.  **Include the Code:** Copy the relevant packages (`org.higherkindedj.hkt` and the packages for the types you need, e.g., `org.higherkindedj.hkt.optional`) into your project's source code.
2.  **Understand the Pattern:** Familiarise yourself with the `Kind` interface, the specific `*Kind` interfaces (e.g., `OptionalKind`), the `*KindHelper` classes (e.g., `OptionalKindHelper`), and the type class instances (e.g., `OptionalMonad`).
3.  **Follow the Usage Guide:** Apply the steps outlined in the [Usage Guide](https://higher-kinded-j.github.io/usage-guide.md) to wrap your Java objects, obtain monad instances, use `map`/`flatMap`/etc., and unwrap the results.
4.  **Extend if Necessary:** If you need HKT simulation for types not included, follow the guide in [Extending Higher-Kinded-J](https://higher-kinded-j.github.io/extending-simulation.md).

**Note:** This simulation adds a layer of abstraction and associated boilerplate. Consider the trade-offs for your specific project needs compared to directly using the underlying Java types or other functional libraries for Java.


## Limitations

While useful the approach to simulating Higher-Kinded Types has inherent limitations in Java compared to languages with native HKTs:

* **Boilerplate:** Requires additional setup code for each simulated type.
* **Verbosity:** Usage often involves explicit wrapping/unwrapping and witness types.
* **Complexity:** Adds cognitive load to understand the simulation mechanism.
* **Type Safety Gaps:** Relies on some internal casting (`unwrap` methods), although the helpers are designed to be robust (throwing `KindUnwrapException` on structural failure).
* **Type Inference:** Java's inference can sometimes need help with the complex generics.

## Project Structure

The code is organized into packages:

* `org.higherkindedj.hkt`: Core interfaces (`Kind`, `Functor`, `Applicative`, `Monad`, `MonadError`, `Monoid`).
* `org.higherkindedj.hkt.list`: Components for `List` simulation.
* `org.higherkindedj.hkt.optional`: Components for `Optional` simulation.
* `org.higherkindedj.hkt.maybe`: Components for `Maybe` simulation and the `Maybe` type itself.
* `org.higherkindedj.hkt.either`: Components for `Either` simulation and the `Either` type itself.
* `org.higherkindedj.hkt.future`: Components for `CompletableFuture` simulation.
* `org.higherkindedj.hkt.trymonad`: Components for `Try` simulation and the `Try` type itself.
* `org.higherkindedj.hkt.io`: Components for `Id` simulation and the `Id` type itself.
* `org.higherkindedj.hkt.io`: Components for `IO` simulation and the `IO` type itself.
* `org.higherkindedj.hkt.lazy`: Components for `Lazy` simulation and the `Lazy` type itself.
* `org.higherkindedj.hkt.reader`: Components for `Reader` simulation and the `Reader` type itself.
* `org.higherkindedj.hkt.state`: Components for `State` simulation and the `State` type itself.
* `org.higherkindedj.hkt.writer`: Components for `Writer` simulation and the `Writer` type itself.
* `org.higherkindedj.hkt.trans.either_t`: Monad Transformer (`EitherT`) components.
* `org.higherkindedj.hkt.trans.maybe_t`: Monad Transformer (`MaybeT`) components.
* `org.higherkindedj.hkt.trans.optional_t`: Monad Transformer (`OptionalT`) components.
* `org.higherkindedj.hkt.trans.reader_t`: Monad Transformer (`ReaderT`) components.
* `org.higherkindedj.hkt.trans.state_t`: Monad Transformer (`StateT`) components.
* `org.higherkindedj.hkt.function`: Helper functional interfaces (`Function3`, `Function4`).
* `org.higherkindedj.hkt.exception`: Custom exceptions like `KindUnwrapException`.
* `org.higherkindedj.example.order`: A practical example demonstrating an order processing workflow.
* `org.higherkindedj.hkt.example`: Contains executable examples demonstrating basic usage.

## Requirements

* **Java Development Kit (JDK): Version 24** or later.
* Gradle (the project includes a Gradle wrapper).


## Contributing

Contributions to this project are very welcome! Whether it's adding new features, improving existing code, or enhancing documentation, your help is GREATLY appreciated. See [CONTRIBUTING.md](CONTRIBUTING.md) for details.

**Areas for Contribution:**

* **Simulate More Types:** Add HKT simulations and type class instances for other common Java types (e.g., `Stream`, `java.time` types) or functional concepts.
* **Implement More Type Classes:** Add implementations for other useful type classes like `Traverse`, `Semigroup`, etc., where applicable.
* **Enhance Existing Implementations:** Improve performance, clarity, or robustness of the current simulations and type class instances.
* **Add Examples:** Create more diverse examples showcasing different use cases of Higher-Kinded Type simulation.
* **Improve Documentation:** Clarify concepts, add diagrams, or improve the Wiki/README.
* **Refactor with New Java Features:** Explore opportunities to use features like Structured Concurrency, etc., to improve the simulation or examples.
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
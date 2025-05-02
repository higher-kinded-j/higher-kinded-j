# Higher-Kinded-J
**_Bringing Higher-Kinded Types to Java functional patterns_**

[==>Take me to the code](https://github.com/higher-kinded-j/higher-kinded-j)

This library aims to bring Higher-Kinded Functional patterns to Java by providing implementations of common Monads supporting Higher-Kinded Types.

**This Higher-Kinded-J simulation was originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) but since then has grown into something altogether more useful**

## Introduction: Abstracting Over Computation in Java

Java's powerful type system excels in many areas, but it lacks native support for Higher-Kinded Types (HKTs). This means we cannot easily write code that abstracts over type constructors like `List<A>`, `Optional<A>`, or `CompletableFuture<A>` in the same way we abstract over the type `A` itself. We can't easily define a generic function that works identically for *any* container or computational context (like List, Optional, Future, IO).

This project tackles that challenge by demonstrating a **simulation of HKTs in Java** using a technique inspired by defunctionalisation. It allows you to define and use common functional abstractions like `Functor`, `Applicative`, and `Monad` (including `MonadError`) in a way that works *generically* across different simulated type constructors.

**Why bother?** Higher-Kinded-J unlocks several benefits:

* **Write Abstract Code:** Define functions and logic that operate polymorphically over different computational contexts (e.g., handle optionality, asynchronous operations, error handling, side effects, or collections using the *same* core logic).
* **Leverage Functional Patterns:** Consistently apply powerful patterns like `map`, `flatMap`, `ap`, `sequence`, `traverse`, and monadic error handling (`raiseError`, `handleErrorWith`) across diverse data types.
* **Build Composable Systems:** Create complex workflows and abstractions by composing smaller, generic pieces, as demonstrated in the included [Order Processing Example](order-walkthrough.md).
* **Understand HKT Concepts:** Provides a practical, hands-on way to understand HKTs and type classes even within Java's limitations.

While Higher-Kinded-J introduces some boilerplate compared to languages with native HKT support, it offers a valuable way to explore these powerful functional programming concepts in Java.

## Getting Started

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


To understand and use Higher-Kinded-J effectively, explore these documents:

1.  **[Core Concepts](core-concepts.md):** Understand the fundamental building blocks – `Kind`, Witness Types, Type Classes (`Functor`, `Monad`, etc.), and the helper classes that bridge the simulation with standard Java types. **Start here!**
2.  **[Supported Types](supported-types.md):** See which Java types (like `List`, `Optional`, `CompletableFuture`) and custom types (`Maybe`, `Either`, `Try`, `IO`, `Lazy`) are currently simulated and have corresponding type class instances.
3.  **[Supported Types in Detail](supported_types_in_detail.md):** More in depth detailed explanations of how to use.
4.   **[Usage Guide](usage-guide.md):** Learn the practical steps involved in using Higher-Kinded-J: obtaining type class instances, wrapping/unwrapping values using helpers, and applying type class methods (`map`, `flatMap`, etc.).
5.   **[Order Example Walkthrough](order-walkthrough.md):** Dive into a detailed, practical example showcasing how `EitherT` (a monad transformer) combines `CompletableFuture` (for async) and `Either` (for domain errors) to build a robust workflow. This demonstrates a key use case.
6.   **[Extending Higher-Kinded-J](extending-simulation.md):** Learn the pattern for adding Higher-Kinded-J support and type class instances for your *own* custom Java types or other standard library types.






## How to Use Higher-Kinded-J (In Your Project)

You could adapt Higher-Kinded-J for use in your own projects:

1.  **Include the Code:** Copy the relevant packages (`org.higherkindedj.hkt` and the packages for the types you need, e.g., `org.higherkindedj.hkt.optional`) into your project's source code.
2.  **Understand the Pattern:** Familiarise yourself with the `Kind` interface, the specific `Kind` interfaces (e.g., `OptionalKind`), the `KindHelper` classes (e.g., `OptionalKindHelper`), and the type class instances (e.g., `OptionalMonad`).
3.  **Follow the Usage Guide:** Apply the steps outlined in the [Usage Guide](usage-guide.md) to wrap your Java objects, obtain monad instances, use `map`/`flatMap`/etc., and unwrap the results.
4.  **Extend if Necessary:** If you need HKT simulation for types not included, follow the guide in [Extending the Simulation](extending-simulation.md).

**Note:** This simulation adds a layer of abstraction and associated boilerplate. Consider the trade-offs for your specific project needs compared to directly using the underlying Java types or other functional libraries for Java.



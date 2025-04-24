# Welcome to the Java Higher-Kinded Type (HKT) Simulation Wiki!

This simulation demonstrates a technique to emulate Higher-Kinded Types (HKTs) in Java, enabling the use of functional programming abstractions like `Functor`, `Applicative`, and `Monad` across different "container" or "context" types (`List`, `Optional`, `CompletableFuture`, etc.).

**(Simulation created for the blog post [Higher Kinded Types with Java and Scala](https://magnussmith.github.io/blog/2025/04/01/higher-kinded-types-with-java-and-scala.html))**

## Why Simulate HKTs?

Java's type system doesn't natively support HKTs (`F<_>`). This simulation provides a bridge, allowing you to:

* **Write Abstract Code:** Create logic that works generically over different computational contexts.
* **Leverage Functional Patterns:** Consistently apply `map`, `flatMap`, error handling, etc.
* **Build Composable Systems:** Structure complex logic cleanly.
* **Learn HKT Concepts:** Understand these powerful ideas within Java.

## Getting Started

* **[Core Concepts](core-concepts.md)]**: Understand the simulation mechanism (`Kind`, Type Classes, Helpers).
* **[Supported Types](supported-types.md)**: See which Java types are currently simulated.
* **[Usage Guide](usage-guide.md)**: Learn how to use the simulation in your code.
* **[Order Example Walkthrough](order-walkthrough.md)**: Explore a practical example using `CompletableFuture`, `Either`, and `EitherT`.
* **[Extending the Simulation](extending-simulation.md)**: Learn how to add support for your own types.

## Key Example

A central piece of this repository is the **[Order Example Walkthrough](order-walkthrough.md)**, which showcases how to build an asynchronous workflow combining `CompletableFuture` for async operations and `Either` for domain error handling, simplified using the `EitherT` monad transformer. It's a great starting point to see the simulation in action.

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
## [_Bringing Higher-Kinded Types to Java functional patterns_](https://github.com/higher-kinded-j/higher-kinded-j)

<a href="https://github.com/higher-kinded-j/higher-kinded-j" style="color: #e70073"> Take me to the code ==> <img src="https://raw.githubusercontent.com/FortAwesome/Font-Awesome/refs/heads/6.x/svgs/brands/github.svg" width="20" height="20" style="vertical-align:middle" alt="github link to code"></a>


Higher-Kinded-J brings popular functional patterns to Java by providing implementations of common Monads and Transformers supporting Higher-Kinded Types.

**Higher-Kinded-J evolved from a simulation** that was originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) that explored Higher-Kinded types and their lack of support in Java. The blog post discussed a process called defuctionalisation that could be used to simulate Higher-Kinded types in Java. Since then Higher-Kinded-J has grown into something altogether more useful supporting more functional patterns.


## Introduction: Abstracting Over Computation in Java

Java's type system excels in many areas, but it lacks native support for Higher-Kinded Types (HKTs). This means we cannot easily write code that abstracts over type constructors like `List<A>`, `Optional<A>`, or `CompletableFuture<A>` in the same way we abstract over the type `A` itself. We can't easily define a generic function that works identically for *any* container or computational context (like List, Optional, Future, IO).

Higher-Kinded-J **simulates HKTs in Java** using a technique inspired by defunctionalisation. It allows you to define and use common functional abstractions like `Functor`, `Applicative`, and `Monad` (including `MonadError`) in a way that works *generically* across different simulated type constructors.

**Why bother?** Higher-Kinded-J unlocks several benefits:

* **Write Abstract Code:** Define functions and logic that operate polymorphically over different computational contexts (e.g., handle optionality, asynchronous operations, error handling, side effects, or collections using the *same* core logic).
* **Leverage Functional Patterns:** Consistently apply powerful patterns like `map`, `flatMap`, `ap`, `sequence`, `traverse`, and monadic error handling (`raiseError`, `handleErrorWith`) across diverse data types.
* **Build Composable Systems:** Create complex workflows and abstractions by composing smaller, generic pieces, as demonstrated in the included [Order Processing Example](order-walkthrough.md).
* **Understand HKT Concepts:** Provides a practical, hands-on way to understand HKTs and type classes even within Java's limitations.

While Higher-Kinded-J introduces some boilerplate compared to languages with native HKT support, it offers a valuable way to explore these powerful functional programming concepts in Java.

## Getting Started

> [!NOTE]  
> Before diving in, ensure you have the following:
> **Java Development Kit (JDK): Version 24** or later. The library makes use of features available in this version.

You can apply the patterns and techniques from Higher-Kinded-J in many ways:

* **Generic Utilities:** Write utility functions that work across different monadic types (e.g., a generic `sequence` function to turn a `List<Kind<F, A>>` into a `Kind<F, List<A>>`).
* **Composable Workflows:** Structure complex business logic, especially involving asynchronous steps and error handling (like the Order Example), in a more functional and composable manner.
* **Managing Side Effects:** Use the `IO` monad to explicitly track and sequence side-effecting operations.
* **Deferred Computation:** Use the `Lazy` monad for expensive computations that should only run if needed.
* **Dependency Injection:** Use the `Reader` monad to manage dependencies cleanly.
* **State Management:** Use the `State` monad for computations that need to thread state through.
* **Logging/Accumulation:** Use the `Writer` monad to accumulate logs or other values alongside a computation.
* **Learning Tool:** Understand HKTs, type classes (Functor, Applicative, Monad), and functional error handling concepts through concrete Java examples.
* **Simulating Custom Types:** Follow the pattern (Kind interface, _Holder if needed_, Helper, Type Class instances) to make your *own* custom data types or computational contexts work with the provided functional abstractions.


To understand and use Higher-Kinded-J effectively, explore these documents:

1.  **[Core Concepts](core-concepts.md):** Understand the fundamental building blocks – `Kind`, Witness Types, Type Classes (`Functor`, `Monad`, etc.), and the helper classes that bridge the simulation with standard Java types. **Start here!**
2.  **[Supported Types](supported-types.md):** See which Java types (like `List`, `Optional`, `CompletableFuture`) and custom types (`Maybe`, `Either`, `Try`, `IO`, `Lazy`) are currently simulated and have corresponding type class instances.
3.  **[Usage Guide](usage-guide.md):** Learn the practical steps involved in using Higher-Kinded-J: obtaining type class instances, wrapping/unwrapping values using helpers, and applying type class methods (`map`, `flatMap`, etc.).
4.  **[Order Example Walkthrough](order-walkthrough.md):** Dive into a detailed, practical example showcasing how `EitherT` (a monad transformer) combines `CompletableFuture` (for async) and `Either` (for domain errors) to build a robust workflow. This demonstrates a key use case.
5.  **[Extending Higher-Kinded-J](extending-simulation.md):** Learn the pattern for adding Higher-Kinded-J support and type class instances for your *own* custom Java types or other standard library types.



## How to Use Higher-Kinded-J (In Your Project)

You could adapt Higher-Kinded-J for use in your own projects:

1.  **Include the dependency:** The relevant packages (`org.higherkindedj.hkt` and the packages for the types you need, e.g., `org.higherkindedj.hkt.optional`) are available in the following dependency from maven central

~~~ admonish info

```
// latest gradle release 

dependencies {
  implementation("io.github.higher-kinded-j:higher-kinded-j:0.1.3")
}

// alternatively if you want to try a SNAPSHOT

repositories {
  mavenCentral()
  maven {
    url= uri("https://central.sonatype.com/repository/maven-snapshots/")
  }
}

dependencies {
  implementation("io.github.higher-kinded-j:higher-kinded-j:0.1.4-SNAPSHOT")
}

```
~~~

2.  **Understand the Pattern:** Familiarise yourself with the `Kind` interface, the specific `Kind` interfaces (e.g., `OptionalKind`), the `KindHelper` classes (e.g., `OptionalKindHelper`), and the type class instances (e.g., `OptionalMonad`).
3.  **Follow the Usage Guide:** Apply the steps outlined in the [Usage Guide](usage-guide.md) to wrap your Java objects, obtain monad instances, use `map`/`flatMap`/etc., and unwrap the results.
4.  **Extend if Necessary:** If you need HKT simulation for types not included, follow the guide in [Extending the Simulation](extending-simulation.md).

```admonish Note
This simulation adds a layer of abstraction and associated boilerplate. Consider the trade-offs for your specific project needs compared to directly using the underlying Java types or other functional libraries for Java.
```
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

## [_Bringing Higher-Kinded Types and Composable Optics to Java_](https://github.com/higher-kinded-j/higher-kinded-j)

[![Static Badge](https://img.shields.io/badge/code-blue?logo=github)
](https://github.com/higher-kinded-j/higher-kinded-j)
[![Codecov](https://img.shields.io/codecov/c/github/higher-kinded-j/higher-kinded-j?token=VR0K0ZEDHD)](https://codecov.io/gh/higher-kinded-j/higher-kinded-j) [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.higher-kinded-j/hkj-core)](https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core) [![GitHub Discussions](https://img.shields.io/github/discussions/higher-kinded-j/higher-kinded-j)](https://github.com/higher-kinded-j/higher-kinded-j/discussions) [![Mastodon Follow](https://img.shields.io/mastodon/follow/109367467120571209?domain=techhub.social&style=plastic&logoSize=auto)](https://techhub.social/@ultramagnetic)

Higher-Kinded-J brings two powerful functional programming toolsets to Java, enabling developers to write more abstract, composable, and robust code:

1. A **Higher-Kinded Types (HKT) Simulation** to abstract over computational contexts like `Optional`, `List`, or `CompletableFuture`.
2. A powerful **Optics Library** to abstract over immutable data structures, with boilerplate-free code generation.

These work together to solve common Java pain points in a functional, type-safe way.

## _Two Pillars of Functional Programming_

## 1: A [Higher-Kinded Types](hkts/hkt_introduction.md) Simulation ⚙️

Java's type system lacks native support for Higher-Kinded Types, making it difficult to write code that abstracts over "container" types. We can't easily define a generic function that works identically for `List<A>`, `Optional<A>`, and `CompletableFuture<A>`.

Higher-Kinded-J **simulates HKTs in Java** using a technique inspired by defunctionalisation. This unlocks the ability to use common functional abstractions like `Functor`, `Applicative`, and `Monad` generically across different data types.

**With HKTs, you can:**

* **Abstract Over Context:** Write logic that works polymorphically over different computational contexts (optionality, asynchrony, error handling, collections).
* **Leverage Typeclasses:** Consistently apply powerful patterns like `map`, `flatMap`, `sequence`, and `traverse` across diverse data types.
* **Build Adaptable Pipelines:** Use profunctors to create flexible data transformation pipelines that adapt to different input and output formats.
* **Manage Effects:** Use provided monads like `IO`, `Either`, `Validated`, and `State` to build robust, composable workflows.

## 2: A Powerful [Optics](optics/optics_intro.md) Library 🔎

Working with immutable data structures, like Java records, is great for safety but leads to verbose "copy-and-update" logic for nested data.

Higher-Kinded-J provides a full-featured **Optics library** that treats data access as a first-class value. An optic is a **composable, functional getter/setter** that lets you "zoom in" on a piece of data within a larger structure.

**With Optics, you can:**

* **Eliminate Boilerplate:** An annotation processor **generates** `Lens`, `Prism`, `Iso`, `Fold`, and `Traversal` optics for your records and sealed interfaces automatically.
* **Perform Deep Updates Effortlessly:** Compose optics to create a path deep into a nested structure and perform immutable updates in a single, readable line.
* **Decouple Data and Operations:** Model your data cleanly as immutable records, while defining complex, reusable operations separately as optics.
* **Perform Effectful Updates:** The Optics library is built on top of the HKT simulation, allowing you to perform failable, asynchronous, or stateful updates using the powerful `modifyF` method.
* **Adapt to Different Data Types:** Every optic is a profunctor, meaning it can be adapted to work with different source and target types using `contramap`, `map`, and `dimap` operations. This provides incredible flexibility for API integration, legacy system support, and data format transformations.
* **Query with Precision:** Use **filtered traversals** to declaratively focus on elements matching predicates, and **indexed optics** to perform position-aware transformations with full index tracking.
* **Java-Friendly Syntax:** Leverage the **fluent API** for discoverable, readable optic operations, or use the **Free Monad DSL** to build composable optic programs with multiple execution strategies (direct, logging, validation).

## Learn by Doing 🎯

The fastest way to master Higher-Kinded-J is through our **interactive tutorial series**. Each tutorial guides you through hands-on exercises with immediate test feedback.

- **[Core Types Tutorials](tutorials/coretypes_track.md)** (~60 minutes): Master Functors, Applicatives, and Monads through 7 progressive tutorials that build from `Kind<F, A>` basics to production workflows
- **[Optics Tutorials](tutorials/optics_track.md)** (~90 minutes): Learn Lenses, Prisms, and Traversals with 9 tutorials covering real-world scenarios from simple field updates to complex Free Monad DSLs

Perfect for developers who prefer learning by building rather than just reading. [Get started →](tutorials/tutorials_intro.md)


## [Spring Boot Integration](spring/spring_boot_integration.md) 🚀

Building enterprise applications with Spring Boot? The **hkj-spring-boot-starter** brings functional programming patterns seamlessly into your REST APIs, eliminating exception-based error handling whilst maintaining Spring's familiar conventions.

**With Spring Boot Integration, you can:**

* **Return Functional Types from Controllers:** Use `Either<Error, Data>`, `Validated<Errors, Data>`, and `EitherT` as return types with automatic HTTP response conversion.
* **Eliminate Exception Handling Boilerplate:** No more try-catch blocks or `@ExceptionHandler` methods—errors are explicit in your return types.
* **Compose Operations Naturally:** Chain operations with `map` and `flatMap` whilst preserving type safety and error information.
* **Accumulate Validation Errors:** Use `Validated` to collect **all** validation errors in a single request, improving user experience.
* **Handle Async Operations:** Use `EitherT` to compose asynchronous operations with typed errors seamlessly.
* **Monitor in Production:** Track Either success rates, Validated error distributions, and EitherT async performance with Spring Boot Actuator metrics.
* **Secure Functionally:** Integrate Spring Security with Either-based authentication and Validated-based authorisation logic.
* **Zero Configuration Required:** Auto-configuration handles everything—just add the dependency and start coding.

~~~admonish example title="Quick Example"
```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public Either<DomainError, User> getUser(@PathVariable String id) {
        return userService.findById(id);
        // Right(user) → HTTP 200 with JSON
        // Left(UserNotFoundError) → HTTP 404 with error details
    }

    @PostMapping
    public Validated<List<ValidationError>, User> createUser(@RequestBody UserRequest request) {
        return userService.validateAndCreate(request);
        // Valid(user) → HTTP 200
        // Invalid(errors) → HTTP 400 with ALL validation errors
    }
}
```
~~~

**[Get Started with Spring Boot Integration →](spring/spring_boot_integration.md)**


## Getting Started

> [!NOTE]
> Before diving in, ensure you have the following:
> **Java Development Kit (JDK): Version 24** or later. The library makes use of features available in this version.

The project is modular. To use it, add the relevant dependencies to your `build.gradle` or `pom.xml`. The use of an annotation processor helps to automatically generate the required boilerplate for Optics and other patterns.

**For HKTs:**

```gradle
    // build.gradle.kts
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
```

**For Optics:**

```gradle
    // build.gradle.kts
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
```

**For SNAPSHOTS:**

```gradle
repositories {
    mavenCentral()
    maven {
        url= uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

## Documentation

We recommend following the documentation in order to get a full understanding of the library's capabilities.

#### Optics Guides

This series provides a practical, step-by-step introduction to solving real-world problems with optics.

1. **[An Introduction to Optics](optics/optics_intro.md):** Learn what optics are and the problems they solve.
2. **[Practical Guide: Lenses](optics/lenses.md):** A deep dive into using `Lens` for nested immutable updates.
3. **[Practical Guide: Prisms](optics/prisms.md):** Learn how to use `Prism` to safely work with `sealed interface` (sum types).
4. **[Practical Guide: Isos](optics/iso.md):** Understand how `Iso` provides a bridge between equivalent data types.
5. **[Practical Guide: Traversals](optics/traversals.md):** Master the `Traversal` for performing bulk updates on collections.
6. **[Profunctor Optics](optics/profunctor_optics.md):** Discover how to adapt optics to work with different data types and structures.
7. **[Capstone Example: Deep Validation](optics/composing_optics.md):** A complete example that composes multiple optics to solve a complex problem.
8. **[Practical Guide: Filtered Optics](optics/filtered_optics.md):** Learn how to compose predicates with optics for declarative filtering.
9. **[Practical Guide: Indexed Optics](optics/indexed_optics.md):** Discover position-aware transformations with index tracking.
10. **[Practical Guide: Limiting Traversals](optics/limiting_traversals.md):** Master traversals that focus on portions of lists.
11. **[Fluent API for Optics](optics/fluent_api.md):** Explore Java-friendly syntax for optic operations.
12. **[Free Monad DSL](optics/free_monad_dsl.md):** Build composable optic programs as data structures.
13. **[Optic Interpreters](optics/interpreters.md):** Execute optic programs with different strategies (logging, validation).

#### HKT Core Concepts

For users who want to understand the underlying HKT simulation that powers the optics library or use monads directly.

1. **[An Introduction to HKTs](hkts/hkt_introduction.md):** Learn what HKTs are and the problems they solve.
2. **[Core Concepts](hkts/core-concepts.md):** Understand `Kind`, Witness Types, and Type Classes (`Functor`, `Monad`).
3. **[Supported Types](monads/supported-types.md):** See which types are simulated and have typeclass instances.
4. **[Usage Guide](hkts/usage-guide.md):** Learn the practical steps for using the HKT simulation directly.
5. **[Examples of how to use HKTs](hkts/hkt_basic_examples.md)**: Practical Examples of how to use the Monads.
6. **[Order Example Walkthrough](hkts/order-walkthrough.md):** A detailed example of building a robust workflow with monad transformers.
7. **[Extending Higher-Kinded-J](hkts/extending-simulation.md):** Learn how to add HKT support for your own custom types.

#### History

**Higher-Kinded-J evolved from a simulation** that was originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) that explored Higher-Kinded types and their lack of support in Java. The blog post discussed a process called defuctionalisation that could be used to simulate Higher-Kinded types in Java. Since then Higher-Kinded-J has grown into something altogether more useful supporting more functional patterns.

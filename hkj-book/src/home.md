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

## [_Bringing Higher-Kinded Types and Optics to Java functional patterns_](https://github.com/higher-kinded-j/higher-kinded-j)

[![Static Badge](https://img.shields.io/badge/code-blue?logo=github)
](https://github.com/higher-kinded-j/higher-kinded-j)
[![Codecov](https://img.shields.io/codecov/c/github/higher-kinded-j/higher-kinded-j?token=VR0K0ZEDHD)](https://codecov.io/gh/higher-kinded-j/higher-kinded-j) [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.higher-kinded-j/hkj-core)](https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core) [![GitHub Discussions](https://img.shields.io/github/discussions/higher-kinded-j/higher-kinded-j)](https://github.com/higher-kinded-j/higher-kinded-j/discussions) [![Mastodon Follow](https://img.shields.io/mastodon/follow/109367467120571209?domain=techhub.social&style=plastic&logoSize=auto)](https://techhub.social/@ultramagnetic)

Higher-Kinded-J brings two powerful functional programming toolsets to Java, enabling developers to write more abstract, composable, and robust code.

It provides:

1. A **Higher-Kinded Types (HKT) Simulation** to abstract over computational contexts like `Optional`, `List`, or `CompletableFuture`.
2. A powerful **Optics Library** to abstract over immutable data structures, with boilerplate-free code generation.

These two pillars work together to solve common problems in a functional, type-safe way.

## Core Features: Two Pillars of Functional Programming

### Pillar 1: A Higher-Kinded Types Simulation ⚙️

Java's type system lacks native support for Higher-Kinded Types, making it difficult to write code that abstracts over "container" types. We can't easily define a generic function that works identically for `List<A>`, `Optional<A>`, and `CompletableFuture<A>`.

Higher-Kinded-J **simulates HKTs in Java** using a technique inspired by defunctionalisation. This unlocks the ability to use common functional abstractions like `Functor`, `Applicative`, and `Monad` generically across different data types.

**With HKTs, you can:**

* **Abstract Over Context:** Write logic that works polymorphically over different computational contexts (optionality, asynchrony, error handling, collections).
* **Leverage Typeclasses:** Consistently apply powerful patterns like `map`, `flatMap`, `sequence`, and `traverse` across diverse data types.
* **Manage Effects:** Use provided monads like `IO`, `Either`, `Validated`, and `State` to build robust, composable workflows.

### Pillar 2: A Powerful Optics Library 🔎

Working with immutable data structures, like Java records, is great for safety but leads to verbose "copy-and-update" logic for nested data.

Higher-Kinded-J provides a full-featured **Optics library** that treats data access as a first-class value. An optic is a **composable, functional getter/setter** that lets you "zoom in" on a piece of data within a larger structure.

**With Optics, you can:**

* **Eliminate Boilerplate:** An annotation processor **generates**`Lens`, `Prism`, and `Traversal` optics for your records and sealed interfaces automatically.
* **Perform Deep Updates Effortlessly:** Compose optics to create a path deep into a nested structure and perform immutable updates in a single, readable line.
* **Decouple Data and Operations:** Model your data cleanly as immutable records, while defining complex, reusable operations separately as optics.
* **Perform Effectful Updates:** The Optics library is built on top of the HKT simulation, allowing you to perform failable, asynchronous, or stateful updates using the powerful `modifyF` method.



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
6. **[Capstone Example: Deep Validation](optics/composing_optics.md):** A complete example that composes multiple optics to solve a complex problem.

#### HKT Core Concepts

For users who want to understand the underlying HKT simulation that powers the optics library or use monads directly.

* **[Core Concepts](core-concepts.md):** Understand `Kind`, Witness Types, and Type Classes (`Functor`, `Monad`).
* **[Supported Types](supported-types.md):** See which types are simulated and have typeclass instances.
* **[Usage Guide](usage-guide.md):** Learn the practical steps for using the HKT simulation directly.
* **[Order Example Walkthrough](order-walkthrough.md):** A detailed example of building a robust workflow with monad transformers.
* **[Extending Higher-Kinded-J](extending-simulation.md):** Learn how to add HKT support for your own custom types.

#### History
**Higher-Kinded-J evolved from a simulation** that was originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html) that explored Higher-Kinded types and their lack of support in Java. The blog post discussed a process called defuctionalisation that could be used to simulate Higher-Kinded types in Java. Since then Higher-Kinded-J has grown into something altogether more useful supporting more functional patterns.
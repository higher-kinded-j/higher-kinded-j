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

## [_Unifying Composable Effects and Advanced Optics for Java_](https://github.com/higher-kinded-j/higher-kinded-j)

[![Static Badge](https://img.shields.io/badge/code-blue?logo=github)
](https://github.com/higher-kinded-j/higher-kinded-j)
[![Codecov](https://img.shields.io/codecov/c/github/higher-kinded-j/higher-kinded-j?token=VR0K0ZEDHD)](https://codecov.io/gh/higher-kinded-j/higher-kinded-j) [![Maven Central Version](https://img.shields.io/maven-central/v/io.github.higher-kinded-j/hkj-core)](https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core) [![GitHub Stars](https://img.shields.io/github/stars/higher-kinded-j/higher-kinded-j?style=flat)](https://github.com/higher-kinded-j/higher-kinded-j) [![GitHub Discussions](https://img.shields.io/github/discussions/higher-kinded-j/higher-kinded-j)](https://github.com/higher-kinded-j/higher-kinded-j/discussions) [![Mastodon Follow](https://img.shields.io/mastodon/follow/109367467120571209?domain=techhub.social&style=plastic&logoSize=auto)](https://techhub.social/@ultramagnetic) [![Bluesky](https://img.shields.io/badge/Bluesky-ultramagn3tic-blue?logo=bluesky&logoColor=white)](https://bsky.app/profile/ultramagn3tic.bsky.social)

---

Higher-Kinded-J brings two capabilities that Java has long needed: composable error handling through the **Effect Path API**, and type-safe immutable data navigation through the **Focus DSL**. Each is powerful alone. Together, they form a unified approach to building robust applications, where effects and structure compose seamlessly.

No more pyramids of nested checks. No more scattered validation logic. Just clean, flat pipelines that read like the business logic they represent.

---

## The Effect Path API

At the heart of Higher-Kinded-J lies the **Effect Path API**: a railway model for computation where success travels one track and failure travels another. Operations like `map`, `via`, and `recover` work identically across all effect types, whether you are handling optional values, typed errors, accumulated validations, or deferred side effects.

```java
// Traditional Java: pyramid of nested checks
if (user != null) {
    if (validator.validate(request).isValid()) {
        try {
            return paymentService.charge(user, amount);
        } catch (PaymentException e) { ... }
    }
}

// Effect Path API: flat, composable railway
return Path.maybe(findUser(userId))
    .toEitherPath(() -> new UserNotFound(userId))
    .via(user -> Path.either(validator.validate(request)))
    .via(valid -> Path.tryOf(() -> paymentService.charge(user, amount)))
    .map(OrderResult::success);
```

The nesting is gone. Each step follows the same pattern. Failures propagate automatically. The business logic reads top-to-bottom, not outside-in.

**[Explore the Effect Path API →](effect/ch_intro.md)**

---

## The Bridge: Effects Meet Optics

What makes Higher-Kinded-J unique is the seamless integration between **Effect Paths** and the **Focus DSL**. Where Effect Paths navigate *computational effects*, Focus Paths navigate *data structures*. Both use the same vocabulary. Both compose with `via`. And when you need to cross between them, the bridge API connects both worlds.

```
                    THE EFFECT-OPTICS BRIDGE

  EFFECTS DOMAIN                           OPTICS DOMAIN
  ══════════════                           ═════════════

  EitherPath<E, User>  ────┐         ┌──── FocusPath<User, Address>
  TryPath<Config>      ────┤         ├──── AffinePath<User, Email>
  IOPath<Data>         ────┤         ├──── TraversalPath<Team, Player>
  VTaskPath<A>         ────┤         └────
  ValidationPath<E, A> ────┘
                            │       │
                            ▼       ▼
                       ┌─────────────────┐
                       │  .focus(path)   │
                       │  .toEitherPath  │
                       │  .toMaybePath   │
                       └─────────────────┘
                              │
                              ▼
                    UNIFIED COMPOSITION
                    ════════════════════

  userService.findById(id)        // Effect: fetch
      .focus(UserFocus.address()) // Optics: navigate
      .via(validateAddress)       // Effect: validate
      .focus(AddressFocus.city()) // Optics: extract
      .map(String::toUpperCase)   // Transform
```

```java
// Fetch user (effect) → navigate to address (optics) →
// extract postcode (optics) → validate (effect)
EitherPath<Error, String> result =
    userService.findById(userId)           // EitherPath<Error, User>
        .focus(UserFocus.address())        // EitherPath<Error, Address>
        .focus(AddressFocus.postcode())    // EitherPath<Error, String>
        .via(code -> validatePostcode(code));
```

This is the unification that Java has been missing: effects and structure, composition and navigation, all speaking the same language.

**[Discover Focus-Effect Integration →](effect/focus_integration.md)**

---

## Two Foundations

The Effect Path API is built on two powerful functional programming pillars:

### [Higher-Kinded Types](hkts/hkt_introduction.md)

Java lacks native support for abstracting over type constructors like `Optional<A>`, `List<A>`, or `CompletableFuture<A>`. Higher-Kinded-J simulates HKTs using defunctionalisation, unlocking:

* **Polymorphic functions** that work across optionality, asynchrony, and error handling
* **Type classes** like `Functor`, `Applicative`, and `Monad` with consistent interfaces
* **Monad transformers** for composing effect stacks (`EitherT`, `StateT`, `ReaderT`)

### [Advanced Optics](optics/optics_intro.md)

Higher-Kinded-J provides the most comprehensive optics implementation available for Java. Working with immutable records means verbose "copy-and-update" logic; the Optics library treats data access as first-class values:

* **Complete optic hierarchy:** Lenses, Prisms, Isos, Affines, Traversals, Folds, and Setters
* **Automatic generation** via annotation processor for Java records and sealed interfaces
* **[External type import](optics/importing_optics.md)** via `@ImportOptics` for types you don't own
* **[Spec interfaces](optics/optics_spec_interfaces.md)** for complex external types with copy strategy annotations (`@ViaBuilder`, `@Wither`, `@ViaCopyAndSet`)
* **[Third-party integration](optics/focus_external_bridging.md)** with Jackson, JOOQ, Immutables, Lombok, AutoValue, and Protocol Buffers
* **Filtered traversals** for predicate-based focusing within collections
* **Indexed optics** for position-aware transformations
* **Profunctor architecture** enabling adaptation between different data shapes
* **Focus DSL** for type-safe, fluent path navigation with seamless bridging into external libraries
* **Effect integration** bridging optics with the Effect Path API

---

## Why Higher-Kinded-J?

Higher-Kinded-J offers the most advanced optics implementation in the Java ecosystem, combined with a unified effect system that no other library provides.

| Feature | Higher-Kinded-J | Functional Java | Fugue Optics | Derive4J |
|---------|:--------------:|:---------------:|:------------:|:--------:|
| **Lens** | ✓ | ✓ | ✓ | ✓^1^ |
| **Prism** | ✓ | ✓ | ✓ | ✓^1^ |
| **Iso** | ✓ | ✓ | ✓ | ✗ |
| **Affine/Optional** | ✓ | ✓ | ✓ | ✓^1^ |
| **Traversal** | ✓ | ✓ | ✓ | ✗ |
| **Filtered Traversals** | ✓ | ✗ | ✗ | ✗ |
| **Indexed Optics** | ✓ | ✗ | ✗ | ✗ |
| **Code Generation** | ✓ | ✗ | ✗ | ✓^1^ |
| **External Type Spec Interfaces** | ✓ | ✗ | ✗ | ✗ |
| **Java Records Support** | ✓ | ✗ | ✗ | ✗ |
| **Sealed Interface Support** | ✓ | ✗ | ✗ | ✗ |
| **Effect Integration** | ✓ | ✗ | ✗ | ✗ |
| **Focus DSL** | ✓ | ✗ | ✗ | ✗ |
| **Profunctor Architecture** | ✓ | ✓ | ✓ | ✗ |
| **Fluent API** | ✓ | ✗ | ✗ | ✗ |
| **Modern Java (21+)** | ✓ | ✗ | ✗ | ✗ |
| **Virtual Threads** | ✓ | ✗ | ✗ | ✗ |

^1^ *Derive4J generates getters/setters but requires Functional Java for actual optic classes*

---

## Path Types at a Glance

| Path Type | When to Reach for It |
|-----------|---------------------|
| `MaybePath<A>` | Absence is normal, not an error |
| `EitherPath<E, A>` | Errors carry typed, structured information |
| `TryPath<A>` | Wrapping code that throws exceptions |
| `ValidationPath<E, A>` | Collecting *all* errors, not just the first |
| `IOPath<A>` | Side effects you want to defer and sequence |
| `TrampolinePath<A>` | Stack-safe recursion |
| `CompletableFuturePath<A>` | Async operations |
| `ReaderPath<R, A>` | Dependency injection, configuration access |
| `WriterPath<W, A>` | Logging, audit trails, collecting metrics |
| `WithStatePath<S, A>` | Stateful computations (parsers, counters) |
| `ListPath<A>` | Batch processing with positional zipping |
| `StreamPath<A>` | Lazy sequences, large data processing |
| `NonDetPath<A>` | Non-deterministic search, combinations |
| `LazyPath<A>` | Deferred evaluation, memoisation |
| `IdPath<A>` | Pure computations (testing, generic code) |
| `OptionalPath<A>` | Bridge for Java's standard `Optional` |
| `FreePath<F, A>` / `FreeApPath<F, A>` | DSL building and interpretation |
| `VTaskPath<A>` | Virtual thread-based concurrency with Par combinators |

Each Path wraps its underlying effect and provides `map`, `via`, `run`, `recover`, and integration with the Focus DSL.

---

## Learn by Doing

The fastest way to master Higher-Kinded-J is through our **interactive tutorial series**. Nine journeys guide you through hands-on exercises with immediate test feedback.

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| **[Core: Foundations](tutorials/coretypes/foundations_journey.md)** | HKT simulation, Functor, Applicative, Monad | ~38 min | 24 |
| **[Core: Error Handling](tutorials/coretypes/error_handling_journey.md)** | MonadError, concrete types, real-world patterns | ~30 min | 20 |
| **[Core: Advanced](tutorials/coretypes/advanced_journey.md)** | Natural Transformations, Coyoneda, Free Applicative | ~26 min | 16 |
| **[Effect API](tutorials/effect/effect_journey.md)** | Effect paths, ForPath, Effect Contexts | ~65 min | 15 |
| **[Concurrency: VTask](tutorials/concurrency/vtask_journey.md)** | Virtual threads, VTaskPath, Par combinators | ~45 min | 16 |
| **[Optics: Lens & Prism](tutorials/optics/lens_prism_journey.md)** | Lens basics, Prism, Affine | ~40 min | 30 |
| **[Optics: Traversals](tutorials/optics/traversals_journey.md)** | Traversals, composition, practical applications | ~40 min | 27 |
| **[Optics: Fluent & Free](tutorials/optics/fluent_free_journey.md)** | Fluent API, Free Monad DSL | ~37 min | 22 |
| **[Optics: Focus DSL](tutorials/optics/focus_dsl_journey.md)** | Type-safe path navigation | ~22 min | 18 |

Perfect for developers who prefer learning by building. [Get started →](tutorials/tutorials_intro.md)

---

## [Spring Boot Integration](spring/spring_boot_integration.md)

Building enterprise applications with Spring Boot? The **hkj-spring-boot-starter** brings functional programming patterns seamlessly into your REST APIs, eliminating exception-based error handling whilst maintaining Spring's familiar conventions.

**With Spring Boot Integration, you can:**

* **Return Functional Types from Controllers:** Use `Either<Error, Data>`, `Validated<Errors, Data>`, and `CompletableFuturePath` as return types with automatic HTTP response conversion.
* **Eliminate Exception Handling Boilerplate:** No more try-catch blocks or `@ExceptionHandler` methods; errors are explicit in your return types.
* **Compose Operations Naturally:** Chain operations with `map` and `via` whilst preserving type safety and error information.
* **Accumulate Validation Errors:** Use `Validated` to collect **all** validation errors in a single request, improving user experience.
* **Handle Async Operations:** Use `CompletableFuturePath` to compose asynchronous operations seamlessly.
* **Monitor in Production:** Track EitherPath success rates, ValidationPath error distributions, and CompletableFuturePath async performance with Spring Boot Actuator metrics.
* **Secure Functionally:** Integrate Spring Security with Either-based authentication and Validated-based authorisation logic.
* **Zero Configuration Required:** Auto-configuration handles everything; just add the dependency and start coding.

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

---

## Getting Started

Ready to start? See the **[Quickstart](quickstart.md)** for Gradle and Maven setup (including required Java 25 preview flags) and your first Effect Paths in 5 minutes.

For a one-page operator reference, see the **[Cheat Sheet](cheatsheet.md)**.

---

## Documentation Guide

~~~admonish tip title="Recommended Starting Point"
If you want working code immediately, start with the **[Quickstart](quickstart.md)**. For a deeper understanding, continue with the **Effect Path API** section below.
~~~

### Effect Path API (Start Here)
1. **[Effect Path Overview](effect/effect_path_overview.md):** The railway model, creating paths, core operations
2. **[Capability Interfaces](effect/capabilities.md):** The powers that paths possess
3. **[Path Types](effect/path_types.md):** When to use each path type
4. **[Focus-Effect Integration](effect/focus_integration.md):** Bridging optics and effects

### Optics Guides
1. **[Introduction to Optics](optics/optics_intro.md):** What optics are and the problems they solve
2. **[Practical Guide: Lenses](optics/lenses.md):** Nested immutable updates
3. **[Practical Guide: Prisms](optics/prisms.md):** Working with sum types
4. **[Focus DSL](optics/focus_dsl.md):** Type-safe structural navigation
5. **[External Types](optics/importing_optics.md):** Importing optics for types you don't own
6. **[Spec Interfaces](optics/optics_spec_interfaces.md):** Jackson, JOOQ, and complex external types
7. **[Focus DSL Bridging](optics/focus_external_bridging.md):** Seamless navigation into Immutables, Lombok, AutoValue
8. **[Profunctor Optics](optics/profunctor_optics.md):** Adapting optics to different data shapes

### Foundations (Reference)
These sections document the underlying machinery. Most users can start with Effect Paths directly.

1. **[Higher-Kinded Types](hkts/hkt_introduction.md):** The simulation and why it matters
2. **[Type Classes](functional/ch_intro.md):** Functor, Monad, and other type classes
3. **[Core Types](monads/ch_intro.md):** Either, Maybe, Try, and other effect types
4. **[Order Example Walkthrough](hkts/order-walkthrough.md):** A complete workflow with monad transformers

### History

**Higher-Kinded-J evolved from a simulation** originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html). Since then it has grown into a comprehensive functional programming toolkit, with the Effect Path API providing the unifying layer that connects HKTs, type classes, and optics into a coherent whole.

<div class="hkj-logo" style="text-align: center; margin: 1rem 0;">
  <img class="hkj-logo-light" src="logos/hkj-logo-light.png" alt="Higher-Kinded-J" width="400">
  <img class="hkj-logo-dark" src="logos/hkj-logo-dark.png" alt="Higher-Kinded-J" width="400">
</div>

<h2 class="hkj-strapline"><a href="https://github.com/higher-kinded-j/higher-kinded-j">Unifying Composable Effects and Advanced Optics for Java</a></h2>

<div style="text-align: center;">
  <a href="https://github.com/higher-kinded-j/higher-kinded-j"><img src="https://img.shields.io/badge/code-blue?logo=github" alt="Static Badge"></a>
  <a href="https://codecov.io/gh/higher-kinded-j/higher-kinded-j"><img src="https://img.shields.io/codecov/c/github/higher-kinded-j/higher-kinded-j?token=VR0K0ZEDHD" alt="Codecov"></a>
  <a href="https://central.sonatype.com/artifact/io.github.higher-kinded-j/hkj-core"><img src="https://img.shields.io/maven-central/v/io.github.higher-kinded-j/hkj-core?label=maven-central" alt="Maven Central Version"></a>
  <a href="https://central.sonatype.com/repository/maven-snapshots/io/github/higher-kinded-j/hkj-core/"><img src="https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fgithub%2Fhigher-kinded-j%2Fhkj-core%2Fmaven-metadata.xml&label=snapshot&color=green" alt="Latest Snapshot"></a>
  <a href="https://github.com/higher-kinded-j/higher-kinded-j/discussions"><img src="https://img.shields.io/github/discussions/higher-kinded-j/higher-kinded-j" alt="GitHub Discussions"></a>
  <a href="https://techhub.social/@ultramagnetic"><img src="https://img.shields.io/mastodon/follow/109367467120571209?domain=techhub.social&style=plastic&logoSize=auto" alt="Mastodon Follow"></a>
</div>

---

Higher-Kinded-J brings two capabilities that Java has long needed: composable error handling through the **[Effect Path API](effect/ch_intro.md)**, and type-safe immutable data navigation through the **[Focus DSL](optics/focus_dsl.md)**. Each is powerful alone. Together, they form a unified approach to building robust applications, where effects and structure compose seamlessly. For services that need multiple execution modes, **[Effect Handlers](effect/effect_handlers_intro.md)** let you define domain operations as data and interpret them differently for production, testing, or audit.

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

  MaybePath<User>      ────┐         ┌──── FocusPath<User, Address>
  EitherPath<E, User>  ────┤         ├──── AffinePath<User, Email>
  TryPath<Config>      ────┤         └──── TraversalPath<Team, Player>
  IOPath<Data>         ────┤
  VTaskPath<A>         ────┤
  VStreamPath<A>       ────┤
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

**[Discover Optics Integration →](effect/focus_integration.md)**

---

## Two Foundations

The Effect Path API is built on two powerful functional programming pillars:

### [Higher-Kinded Types](hkts/hkt_introduction.md)

Java lacks native support for abstracting over type constructors like `Optional<A>`, `List<A>`, or `CompletableFuture<A>`. Higher-Kinded-J simulates HKTs using defunctionalisation, unlocking:

* **Polymorphic functions** that work across optionality, asynchrony, and error handling
* **Type classes** like `Functor`, `Applicative`, and `Monad` with consistent interfaces
* **Monad transformers** for composing effect stacks (`EitherT`, `StateT`, `ReaderT`)

### [Advanced Optics](optics/ch_intro.md)

Higher-Kinded-J provides the most comprehensive optics implementation available for Java. Working with immutable records means verbose "copy-and-update" logic; the Optics library treats data access as first-class values. The chapter opens with an annotation-led [Quickstart](optics/quickstart.md) and the [Annotations at a Glance](optics/annotations_at_a_glance.md) lookup table; you write a record, add `@GenerateLenses` and `@GenerateFocus`, and the processor writes a typed path builder for you.

* **Complete optic hierarchy:** Lenses, Prisms, Isos, Affines, Traversals, Folds, and Setters
* **Annotation-driven generation** for records, sealed interfaces, and enums; see [Annotations at a Glance](optics/annotations_at_a_glance.md) for the full surface
* **[External type import](optics/importing_optics.md)** via `@ImportOptics` for types you don't own
* **[Spec interfaces](optics/optics_spec_interfaces.md)** for complex external types with copy strategy annotations (`@ViaBuilder`, `@Wither`, `@ViaCopyAndSet`)
* **[Third-party integration](optics/focus_external_bridging.md)** with Jackson, JOOQ, Immutables, Lombok, AutoValue, and Protocol Buffers
* **Filtered traversals** for predicate-based focusing within collections
* **Indexed optics** for position-aware transformations
* **Profunctor architecture** enabling adaptation between different data shapes
* **Focus DSL** for type-safe, fluent path navigation with seamless bridging into external libraries
* **[SPI-aware container types](optics/focus_containers.md)** with automatic `AffinePath` and `TraversalPath` generation via cardinality-based widening, supporting 30 container types across JDK, Apache Commons, Eclipse Collections, Guava, Vavr, PCollections, and HKJ native types
* **Effect integration** bridging optics with the Effect Path API

### [Effect Handlers](effect/effect_handlers_intro.md)

For services with complex domain workflows, Higher-Kinded-J provides algebraic-effect-style programming via Free monads and interpreters. Define your domain operations as sealed interfaces with record variants, then write different interpreters for production, testing, dry-run, or audit modes:

* **[`@EffectAlgebra`](effect/effect_handlers.md)** generates boilerplate (Functor, smart constructors, interpreter skeleton)
* **[`@ComposeEffects`](effect/effect_handlers.md#composing-effects)** composes multiple operation types into a single program
* **Mock-free testing** via `Id` monad interpreters
* **[Program inspection](effect/effect_handlers.md#program-analysis)** with `ProgramAnalyser` before any side effects execute

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
| **Effect Handlers / Free Monads** | ✓ | ✗ | ✗ | ✗ |

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
| `VStreamPath<A>` | Lazy pull-based streaming on virtual threads |

Each Path wraps its underlying effect and provides `map`, `via`, `run`, `recover`, and integration with the Focus DSL.

---

## Learn by Doing

The fastest way to master Higher-Kinded-J is through our **interactive tutorial series**. Thirteen journeys guide you through hands-on exercises with immediate test feedback.

| Journey | Focus | Duration | Exercises |
|---------|-------|----------|-----------|
| **[Core: Foundations](tutorials/coretypes/foundations_journey.md)** | HKT simulation, Functor, Applicative, Monad | ~40 min | 24 |
| **[Core: Error Handling](tutorials/coretypes/error_handling_journey.md)** | MonadError, concrete types, real-world patterns | ~30 min | 20 |
| **[Core: Advanced](tutorials/coretypes/advanced_journey.md)** | Natural Transformations, Coyoneda, Free Applicative | ~40 min | 26 |
| **[Effect API](tutorials/effect/effect_journey.md)** | Effect paths, ForPath, Effect Contexts | ~65 min | 15 |
| **[Monad Transformers](tutorials/transformers/transformers_journey.md)** | When Path isn't enough, async + absence, stacking, MTL | ~90 min | 28 |
| **[Expression: ForState](tutorials/expression/forstate_journey.md)** | Named fields, guards, pattern matching, zoom | ~25 min | 11 |
| **[Concurrency: VTask](tutorials/concurrency/vtask_journey.md)** | Virtual threads, VTaskPath, Par combinators | ~45 min | 16 |
| **[Concurrency: Scope & Resource](tutorials/concurrency/scope_resource_journey.md)** | Structured concurrency, resource management | ~30 min | 12 |
| **[Resilience Patterns](tutorials/resilience/resilience_journey.md)** | Circuit breaker, saga, retry, bulkhead | ~45 min | 22 |
| **[Optics: Lens & Prism](tutorials/optics/lens_prism_journey.md)** | Lens basics, Prism, Affine | ~40 min | 30 |
| **[Optics: Traversals](tutorials/optics/traversals_journey.md)** | Traversals, composition, practical applications | ~40 min | 27 |
| **[Optics: Fluent & Free](tutorials/optics/fluent_free_journey.md)** | Fluent API, Free Monad DSL | ~35 min | 22 |
| **[Optics: Focus DSL](tutorials/optics/focus_dsl_journey.md)** | Type-safe path navigation, container widening | ~35 min | 29 |

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
1. **[Quickstart](effect/quickstart.md):** Three runnable examples showing MaybePath, EitherPath, and ForPath in about 150 lines
2. **[Core Paths](effect/effect_path_overview.md):** The railway model, the six core path types, composition, and basic ForPath comprehensions
3. **[Optics Integration](effect/focus_integration.md):** Bridging Effect Paths with the Focus DSL
4. **[Advanced Paths](effect/advanced_topics.md):** Free monads, effect handlers, contexts, ForPath parallelism, and resilience
5. **[Reference](effect/capabilities.md):** Capability typeclasses, type conversions, compiler errors, and production readiness

### Monad Transformers
For the cases where the Path API does not fit (a different outer monad, polymorphic library code, or integrating with raw `Kind` shapes).

1. **[Path or Transformer?](transformers/when_to_drop_to_transformers.md):** The triage page; read this first to know whether the rest of the chapter applies to you
2. **[Quickstart](transformers/quickstart.md):** Three runnable transformer examples in about 150 lines
3. **[Stack Archetypes](transformers/archetypes.md):** Seven named patterns covering the most common composition problems
4. **[MTL Capabilities](transformers/mtl_capabilities.md):** Stack-independent capability abstractions for polymorphic library code
5. **[Capstone](transformers/transformer_capstone.md):** End-to-end multi-capability workflow combining typed errors, configuration, audit, and async
6. **[Common Compiler Errors](transformers/common_errors.md):** Six common errors and the fix for each

### Optics
1. **[Quickstart](optics/quickstart.md):** Three runnable examples covering generated lenses, prisms and traversals, plus `@ImportOptics` for Jackson
2. **[Annotations at a Glance](optics/annotations_at_a_glance.md):** Every annotation, what it generates, and when to reach for each one
3. **[Fundamentals](optics/ch1_intro.md):** Lens, Prism, Affine, Iso, composition rules, and coupled fields
4. **[Java-Friendly APIs](optics/ch4_intro.md):** Focus DSL, optics for external types, Kind field support, and the Fluent API
5. **[Integration and Recipes](optics/ch5_intro.md):** Validation pipelines, core-type integration, and the cookbook
6. **[Advanced Optics](optics/ch6_intro.md):** Free Monad DSL and interpreters for programs-as-data
7. **[Reference](optics/ch7_intro.md):** Capabilities, conversions, compiler errors, production readiness, and consolidated decision trees

### Effect Handlers
1. **[Effect Handlers Introduction](effect/effect_handlers_intro.md):** Motivation, terminology, and when to use
2. **[Effect Handler Reference](effect/effect_handlers.md):** Defining, composing, and interpreting effects
3. **[Payment Processing Example](examples/payment_processing.md):** Complete worked example with four interpreters

### Foundations (Reference)
These sections document the underlying machinery. Most users can start with Effect Paths directly.

1. **[Higher-Kinded Types](hkts/hkt_introduction.md):** The simulation and why it matters
2. **[Type Classes](functional/ch_intro.md):** Functor, Monad, and other type classes
3. **[Core Types](monads/ch_intro.md):** Either, Maybe, Try, and other effect types
4. **[Order Example Walkthrough](hkts/order-walkthrough.md):** A complete workflow with monad transformers

### History

**Higher-Kinded-J evolved from a simulation** originally created for the blog post [Higher Kinded Types with Java and Scala](https://blog.scottlogic.com/2025/04/11/higher-kinded-types-with-java-and-scala.html). Since then it has grown into a comprehensive functional programming toolkit, with the Effect Path API providing the unifying layer that connects HKTs, type classes, and optics into a coherent whole.

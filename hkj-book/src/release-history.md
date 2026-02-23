# Release History

This page documents the evolution of Higher-Kinded-J from its initial release through to the current version. Each release builds on the foundations established by earlier versions, progressively adding type classes, monads, optics, and the Effect Path API.

~~~admonish info title="What You'll Find"
- Detailed release notes for recent versions (0.3.0+) with links to documentation
- Summary release notes for earlier versions (pre-0.3.0)
- Links to GitHub release pages for full changelogs
~~~

---

## Recent Releases

### [v0.3.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.5) -- 15 February 2026

**Extended For-Comprehensions, VTask API Refinement, and Documentation Restructure**

This release extends for-comprehension arity to 12, simplifies the VTask API, adds Maybe-to-Either conversions, upgrades to JUnit 6, and delivers a comprehensive documentation restructure with quickstart guides, cheat sheets, migration cookbooks, and railway diagrams.

- [For-Comprehension Arity 12](functional/for_comprehension.md) -- `For` and `ForPath` now support up to 12 monadic bindings (previously 5), with generated `Tuple9`–`Tuple12` and `Function9`–`Function12`
- [VTask API](monads/vtask_monad.md) -- `VTask.run()` no longer declares `throws Throwable`; checked exceptions are wrapped in `VTaskExecutionException`
- [Maybe.toEither](monads/maybe_monad.md) -- New `toEither(L)` and `toEither(Supplier<L>)` conversion methods for seamless `Maybe` → `Either` transitions
- [Quickstart](quickstart.md) -- New getting-started guide with Gradle and Maven setup including `--enable-preview` configuration
- [Cheat Sheet](cheatsheet.md) -- Quick-reference for Path types, operators, escape hatches, and type conversions
- [Stack Archetypes](transformers/archetypes.md) -- 7 named transformer stack archetypes with colour-coded railway diagrams
- [Migration Cookbook](migration_cookbook.md) -- 6 recipes for migrating from try/catch, Optional chains, null checks, CompletableFuture, validation, and nested records
- [Compiler Error Guide](compiler_errors.md) -- Solutions for the 5 most common Effect Path compiler errors
- [Effects-Optics Capstone](capstone_focus_effect.md) -- Combined effects and optics pipeline example
- Railway operator diagrams for all 8 Effect Path operators and for EitherT, MaybeT, OptionalT transformers
- JUnit 6.0.2 upgrade (from 5.14.1) across all test modules
- Golden file test infrastructure with automated sync verification and pitest mutation coverage improvements

---

### [v0.3.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.4) -- 31 January 2026

**External Type Optics and Examples Gallery**

This release introduces powerful optics generation for external types you cannot modify, plus a new Examples Gallery chapter documenting all runnable examples.

- [@ImportOptics](optics/importing_optics.md) -- Generate optics for JDK classes and third-party library types via auto-detection of withers and accessors
- [Spec Interfaces](optics/optics_spec_interfaces.md) -- Fine-grained control over external type optics with `OpticsSpec<S>` for complex types like Jackson's `JsonNode`
- [@ThroughField Auto-Detection](optics/copy_strategies.md#throughfield-auto-detection) -- Automatic traversal type detection for `List`, `Set`, `Optional`, arrays, and `Map` fields
- [Examples Gallery](examples/ch_intro.md) -- New chapter with categorised, runnable examples demonstrating core types, transformers, Effect Path API, and optics
- Comprehensive hkj-processor testing improvements with enhanced coverage

---

### [v0.3.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.3) -- 24 January 2026

**Structured Concurrency, Atomic Optics, and Enhanced Examples**

This release introduces structured concurrency primitives, atomic coupled-field updates, and a comprehensive Order Workflow example demonstrating these patterns.

- [Structured Concurrency](monads/vtask_scope.md) -- `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, `firstComplete()`, and `accumulating()` joiners
- [Resource Management](monads/vtask_resource.md) -- `Resource` for bracket-pattern cleanup with guaranteed release
- [Coupled Fields](optics/coupled_fields.md) -- `Lens.paired` for atomic multi-field updates bypassing invalid intermediate states
- [Order Workflow Overview](hkts/order-walkthrough.md) -- Reorganised documentation with focused sub-pages
- [Concurrency and Scale](hkts/order-concurrency.md) -- Context, Scope, Resource, VTaskPath patterns in practice
- `EnhancedOrderWorkflow` -- Full workflow demonstrating Context, Scope, Resource, VTaskPath
- `OrderContext` -- ScopedValue keys for trace ID, tenant isolation, and deadline enforcement
- [Scope & Resource Tutorials](tutorials/concurrency/scope_resource_journey.md) -- 18 exercises on concurrency patterns
- [Release History](release-history.md) -- New page documenting all releases

---

### [v0.3.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.2) -- 17 January 2026

**Virtual Thread Concurrency with VTask**

This release introduces `VTask<A>`, a lazy computation effect leveraging Java 25's virtual threads for lightweight concurrent programming.

- [VTask](monads/vtask_monad.md) -- Lazy computation effect for virtual thread execution
- [Structured Concurrency](monads/vtask_scope.md) -- `Scope` for parallel operations with `allSucceed()`, `anySucceed()`, and `accumulating()` patterns
- [Resource Management](monads/vtask_resource.md) -- `Resource` for bracket-pattern cleanup guarantees
- [VTaskPath](effect/path_vtask.md) -- Integration with the Effect Path API
- [Concurrency and Scale](hkts/order-concurrency.md) -- Practical patterns in the Order Workflow example
- `Par` parallel combinators for concurrent execution
- Comprehensive benchmarks comparing virtual vs. platform threads

---

### [v0.3.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.1) -- 15 January 2026

**Static Analysis Utilities**

This release adds utilities for statically analysing Free Applicative and Selective functors without execution.

- [Choosing Abstraction Levels](functional/abstraction_levels.md) -- Guide to selecting Functor, Applicative, Selective, or Monad
- [Free Applicative](monads/free_applicative.md) -- Static analysis with `FreeApAnalyzer`
- [Selective](functional/selective.md) -- Conditional effects with `SelectiveAnalyzer`
- Tutorial 11 on static analysis patterns

---

### [v0.3.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.3.0) -- 4 January 2026

**Effect Path Focus Integration**

Major release introducing the unified Effect Path API and Focus DSL integration. Requires Java 25 baseline.

- [Effect Path Overview](effect/effect_path_overview.md) -- The railway model for composable effects
- [Path Types](effect/path_types.md) -- 17+ composable Path types including `EitherPath`, `MaybePath`, `IOPath`
- [Focus DSL](optics/focus_dsl.md) -- Type-safe optics with annotation-driven generation
- [Focus-Effect Integration](effect/focus_integration.md) -- Bridge between optics and effects
- [ForPath Comprehension](effect/forpath_comprehension.md) -- For-comprehension syntax for Path types
- [Spring Boot Integration](spring/spring_boot_integration.md) -- Spring Boot 4.0.1+ support
- [Order Workflow](hkts/order-walkthrough.md) -- Complete example demonstrating Effect Path patterns

---

## Earlier Releases

### [v0.2.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.8) -- 26 December 2025

- Introduced `ForPath` for Path-native for-comprehension syntax
- Complete Spring Boot 4.0.1 migration of `hkj-spring` from `EitherT` to Effect Path API
- New return value handlers for Spring integration

### [v0.2.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.7) -- 20 December 2025

- Effect Contexts: `ErrorContext`, `OptionalContext`, `ConfigContext`, `MutableContext`
- Bridge API enabling seamless transitions between optics and effects

### [v0.2.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.6) -- 19 December 2025

- New Effect Path API with 17+ Path types
- Retry policies and parallel execution utilities
- Kind field support in Focus DSL

### [v0.2.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.5) -- 9 December 2025

- Annotation-driven Focus DSL for fluent optics composition
- Free Applicative and Coyoneda functors
- Natural Transformation support

### [v0.2.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.4) -- 3 December 2025

- Affine optic for focusing on zero or one element
- `ForTraversal`, `ForState`, and `ForIndexed` comprehension builders
- For-comprehension and optics integration

### [v0.2.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.3) -- 1 December 2025

- Cross-optic composition (Lens + Prism → Traversal)
- Experimental Spring Boot starter
- Custom target package support for annotation processor

### [v0.2.2](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.2) -- 29 November 2025

- Java 25 baseline
- Experimental `hkj-spring` module
- Validation helpers and ArchUnit architecture tests
- Thread-safety fix in Lazy memoisation

### [v0.2.1](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.1) -- 23 November 2025

- 7-part Core Types tutorial series
- 9-part Optics tutorial (~150 minutes total)
- Versioned documentation system
- Property-based testing infrastructure
- JMH benchmarking framework

### [v0.2.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.0) -- 21 November 2025

- Six new optic types: Fold, Getter, Setter, and indexed variants
- FreeMonad for DSL construction
- Trampoline for stack-safe recursion
- Const Functor
- Enhanced Monoid with new methods
- Alternative type class

### [v0.1.9](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.9) -- 14 November 2025

- Selective type class for conditional effects
- Enhanced optics with `modifyWhen()` and `modifyBranch()`
- Bifunctor for Either, Tuple2, Validated, and Writer
- Higher-kinded Stream support

### [v0.1.8](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.8) -- 9 September 2025

- Profunctor type class
- Profunctor operations in universal Optic interface

### [v0.1.7](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.7) -- 29 August 2025

- Generated `with*` helper methods for records via `@GenerateLenses`
- `Traversals.forMap()` for key-specific Map operations
- Semigroup interface with Monoid extending it
- Validated Applicative with error accumulation

### [v0.1.6](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.6) -- 14 July 2025

- Optics introduction: Lens, Iso, Prism, and Traversals
- Annotation-based optics generation
- Plugin architecture for extending Traversal types
- Modular release structure

### [v0.1.5](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.5) -- 12 June 2025

- For comprehension with generators, bindings, guards, and yield
- Tuple1-5 and Function5 support

### [v0.1.4](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.4) -- 5 June 2025

- Validated Monad
- Standardised widen/narrow pattern for KindHelpers (breaking change)

### [v0.1.3](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.3) -- 31 May 2025

- First Maven Central publication
- 12 monads, 5 transformers
- Comprehensive documentation

### [v0.1.0](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.0) -- 3 May 2025

- Initial release
- Core types: Either, Try, CompletableFuture, IO, Lazy, Reader, State, Writer
- EitherT transformer

---

~~~admonish tip title="See Also"
- [GitHub Releases](https://github.com/higher-kinded-j/higher-kinded-j/releases) -- Full changelogs and assets
- [Contributing](CONTRIBUTING.md) -- How to contribute to Higher-Kinded-J
~~~

---

**Previous:** [Glossary](glossary.md)
**Next:** [Benchmarks & Performance](benchmarks.md)

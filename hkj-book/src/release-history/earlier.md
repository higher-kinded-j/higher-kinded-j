# v0.1.0 to v0.2.8

## v0.2.8 (26 December 2025) {#v028-26-december-2025}

[v0.2.8 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.8)

- Introduced `ForPath` for Path-native for-comprehension syntax
- Complete Spring Boot 4.0.1 migration of `hkj-spring` from `EitherT` to Effect Path API
- New return value handlers for Spring integration

---

## v0.2.7 (20 December 2025) {#v027-20-december-2025}

[v0.2.7 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.7)

- Effect Contexts: `ErrorContext`, `OptionalContext`, `ConfigContext`, `MutableContext`
- Bridge API enabling seamless transitions between optics and effects

---

## v0.2.6 (19 December 2025) {#v026-19-december-2025}

[v0.2.6 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.6)

- New Effect Path API with 17+ Path types
- Retry policies and parallel execution utilities
- Kind field support in Focus DSL

---

## v0.2.5 (9 December 2025) {#v025-9-december-2025}

[v0.2.5 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.5)

- Annotation-driven Focus DSL for fluent optics composition
- Free Applicative and Coyoneda functors
- Natural Transformation support

---

## v0.2.4 (3 December 2025) {#v024-3-december-2025}

[v0.2.4 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.4)

- Affine optic for focusing on zero or one element
- `ForTraversal`, `ForState`, and `ForIndexed` comprehension builders
- For-comprehension and optics integration

---

## v0.2.3 (1 December 2025) {#v023-1-december-2025}

[v0.2.3 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.3)

- Cross-optic composition (Lens + Prism → Traversal)
- Experimental Spring Boot starter
- Custom target package support for annotation processor

---

## v0.2.2 (29 November 2025) {#v022-29-november-2025}

[v0.2.2 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.2)

- Java 25 baseline
- Experimental `hkj-spring` module
- Validation helpers and ArchUnit architecture tests
- Thread-safety fix in Lazy memoisation

---

## v0.2.1 (23 November 2025) {#v021-23-november-2025}

[v0.2.1 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.1)

- 7-part Core Types tutorial series
- 9-part Optics tutorial (~150 minutes total)
- Versioned documentation system
- Property-based testing infrastructure
- JMH benchmarking framework

---

## v0.2.0 (21 November 2025) {#v020-21-november-2025}

[v0.2.0 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.2.0)

- Six new optic types: Fold, Getter, Setter, and indexed variants
- FreeMonad for DSL construction
- Trampoline for stack-safe recursion
- Const Functor
- Enhanced Monoid with new methods
- Alternative type class

---

## v0.1.9 (14 November 2025) {#v019-14-november-2025}

[v0.1.9 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.9)

- Selective type class for conditional effects
- Enhanced optics with `modifyWhen()` and `modifyBranch()`
- Bifunctor for Either, Tuple2, Validated, and Writer
- Higher-kinded Stream support

---

## v0.1.8 (9 September 2025) {#v018-9-september-2025}

[v0.1.8 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.8)

- Profunctor type class
- Profunctor operations in universal Optic interface

---

## v0.1.7 (29 August 2025) {#v017-29-august-2025}

[v0.1.7 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.7)

- Generated `with*` helper methods for records via `@GenerateLenses`
- `Traversals.forMap()` for key-specific Map operations
- Semigroup interface with Monoid extending it
- Validated Applicative with error accumulation

---

## v0.1.6 (14 July 2025) {#v016-14-july-2025}

[v0.1.6 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.6)

- Optics introduction: Lens, Iso, Prism, and Traversals
- Annotation-based optics generation
- Plugin architecture for extending Traversal types
- Modular release structure

---

## v0.1.5 (12 June 2025) {#v015-12-june-2025}

[v0.1.5 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.5)

- For comprehension with generators, bindings, guards, and yield
- Tuple1-5 and Function5 support

---

## v0.1.4 (5 June 2025) {#v014-5-june-2025}

[v0.1.4 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.4)

- Validated Monad
- Standardised widen/narrow pattern for KindHelpers (breaking change)

---

## v0.1.3 (31 May 2025) {#v013-31-may-2025}

[v0.1.3 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.3)

- First Maven Central publication
- 12 monads, 5 transformers
- Comprehensive documentation

---

## v0.1.0 (3 May 2025) {#v010-3-may-2025}

[v0.1.0 on GitHub](https://github.com/higher-kinded-j/higher-kinded-j/releases/tag/v0.1.0)

- Initial release
- Core types: Either, Try, CompletableFuture, IO, Lazy, Reader, State, Writer
- EitherT transformer

---

**Previous:** [v0.3.0 to v0.3.7](v0_3.md)
**Next:** [Benchmarks & Performance](../benchmarks.md)

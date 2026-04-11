---
name: hkj-guide
description: "Help with Higher-Kinded-J (HKJ) Java library: choose Path types (MaybePath, EitherPath, TryPath, ValidationPath, IOPath, VTaskPath), set up Gradle/Maven, migrate imperative Java code to Effect Paths, fix compiler errors, understand the railway model"
---

# Higher-Kinded-J Guide

You are helping a developer use the **Higher-Kinded-J (HKJ)** Java library. HKJ provides composable error handling via the **Effect Path API** and type-safe immutable data navigation via **Optics / Focus DSL**. It requires **Java 25 with `--enable-preview`**.

Key entry point: `org.higherkindedj.hkt.effect.Path` — factory for all Path types.

## When to load supporting files

- If the user asks about **migrating imperative code** (try/catch, Optional, null checks, CompletableFuture), load `reference/migration-recipes.md`
- If the user asks about **service layer patterns, validation pipelines, or testing patterns**, load `reference/patterns.md`
- If the user asks about **retry, circuit breaker, saga, or bulkhead**, load `reference/resilience.md`
- For **optics, lenses, Focus DSL** in depth, suggest `/hkj-optics`
- For **effect handlers, @EffectAlgebra, Free monads**, suggest `/hkj-effects`
- For **combining effects with optics** (.focus() on paths), suggest `/hkj-bridge`
- For **Spring Boot integration**, suggest `/hkj-spring`
- For **architecture patterns** (functional core / imperative shell), suggest `/hkj-arch`

---

## Path Type Decision Tree

```
                         START HERE
                             |
                  Can the operation fail?
                       /          \
                     No            Yes
                     |              |
              Is the value      Do you need ALL
              optional?          errors at once?
               /     \            /          \
             Yes      No        Yes           No
              |        |         |             |
          MaybePath  IdPath  ValidationPath    |
                                          Is the error a
                                          typed domain error
                                          or an exception?
                                           /            \
                                        Typed        Exception
                                         |              |
                                     EitherPath     TryPath
```

Additional considerations:
- **Deferred side effects** (IO, network, DB): `IOPath`
- **Virtual thread concurrency**: `VTaskPath`
- **Lazy streaming on virtual threads**: `VStreamPath`
- **Stack-safe recursion**: `TrampolinePath`
- **Dependency injection / config**: `ReaderPath`
- **Logging / audit trail**: `WriterPath`
- **Stateful computation**: `WithStatePath`
- **Async (CompletableFuture)**: `CompletableFuturePath`
- **Interpretable DSL**: `FreePath`
- **java.util.Optional bridge**: `OptionalPath`
- **Non-deterministic / combinations**: `NonDetPath`

---

## Path Types Quick Reference

| Path Type | For | Create | Extract |
|-----------|-----|--------|---------|
| `MaybePath<A>` | Absence | `Path.maybe(v)`, `Path.just(v)`, `Path.nothing()` | `.run()` -> `Maybe<A>` |
| `EitherPath<E, A>` | Typed errors | `Path.right(v)`, `Path.left(e)`, `Path.either(e)` | `.run()` -> `Either<E, A>` |
| `TryPath<A>` | Exceptions | `Path.tryOf(() -> ...)`, `Path.success(v)`, `Path.failure(ex)` | `.run()` -> `Try<A>` |
| `ValidationPath<E, A>` | Accumulating errors | `Path.valid(v, sg)`, `Path.invalid(e, sg)` | `.run()` -> `Validated<E, A>` |
| `IOPath<A>` | Deferred side effects | `Path.io(() -> ...)`, `Path.ioPure(v)` | `.unsafeRun()` -> `A` |
| `VTaskPath<A>` | Virtual threads | `Path.vtask(() -> ...)`, `Path.vtaskPure(v)` | `.unsafeRun()` -> `A` |
| `ReaderPath<R, A>` | Dependency injection | `Path.reader(r)`, `Path.ask()`, `Path.asks(fn)` | `.run(env)` -> `A` |
| `WriterPath<W, A>` | Logging, audit | `Path.writer(w, m)`, `Path.tell(log, m)` | `.run()` -> `Writer<W, A>` |
| `WithStatePath<S, A>` | Stateful computation | `Path.state(s)`, `Path.getState()` | `.run(initial)` -> `(S, A)` |
| `TrampolinePath<A>` | Stack-safe recursion | `Path.trampolineDone(v)`, `Path.trampolineDefer(s)` | `.run()` -> `A` |
| `LazyPath<A>` | Deferred, memoised | `Path.lazyDefer(() -> ...)`, `Path.lazyNow(v)` | `.run()` -> `A` |
| `CompletableFuturePath<A>` | Async | `Path.future(cf)`, `Path.futureAsync(() -> ...)` | `.run()` -> `CompletableFuture<A>` |
| `ListPath<A>` | Batch (positional zip) | `Path.listPath(list)` | `.run()` -> `List<A>` |
| `StreamPath<A>` | Lazy sequences | `Path.stream(stream)` | `.run()` -> `Stream<A>` |
| `NonDetPath<A>` | Non-deterministic | `Path.list(list)` | `.run()` -> `List<A>` |
| `IdPath<A>` | Pure values | `Path.id(v)` | `.run()` -> `Id<A>` |
| `OptionalPath<A>` | Optional bridge | `Path.optional(opt)`, `Path.present(v)` | `.run()` -> `Optional<A>` |
| `FreePath<F, A>` | DSL building | `Path.freePure(v, f)`, `Path.freeLift(fa, f)` | `.foldMap(nat, m)` |
| `GenericPath<F, A>` | Custom monads | `Path.generic(kind, monad)` | `.run()` -> `Kind<F, A>` |

---

## Operators Quick Reference

### Transformation
| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.map(fn)` | Transform the success value | All paths |
| `.peek(fn)` | Observe without changing | All paths |
| `.focus(optic)` | Navigate into nested field via optics | MaybePath, EitherPath, TryPath, IOPath, ValidationPath |

### Chaining
| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.via(fn)` | Chain to another path (flatMap) | All chainable paths |
| `.flatMap(fn)` | Alias for `via` | All chainable paths |
| `.then(supplier)` | Sequence, ignoring previous value | All chainable paths |

### Combination
| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.zipWith(other, fn)` | Combine two paths (fail-fast) | All combinable paths |
| `.zipWith3(b, c, fn)` | Combine three paths (fail-fast) | Most paths |
| `.zipWithAccum(other, fn)` | Combine, accumulating errors | ValidationPath |
| `.zipWith3Accum(b, c, fn)` | Combine three, accumulating errors | ValidationPath |

### Error Handling
| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.recover(fn)` | Error -> success value | MaybePath, EitherPath, TryPath, ValidationPath |
| `.recoverWith(fn)` | Error -> new path | MaybePath, EitherPath, TryPath, ValidationPath |
| `.orElse(supplier)` | Try an alternative path | MaybePath, EitherPath, TryPath, ValidationPath |
| `.mapError(fn)` | Transform the error type | EitherPath, TryPath, ValidationPath |

### Extraction
| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.run()` | Extract the underlying value | All paths |
| `.unsafeRun()` | Execute deferred effect (may throw) | IOPath, VTaskPath |
| `.runSafe()` | Execute safely, returning `Try` | IOPath, VTaskPath |
| `.fold(onErr, onOk)` | Handle both tracks | EitherPath, TryPath, ValidationPath |
| `.getOrElse(default)` | Extract or use default | MaybePath, EitherPath, TryPath, ValidationPath |

**Rule of thumb**: Use `map` when your function returns a plain value (`A -> B`). Use `via` when your function returns a Path (`A -> Path<B>`).

---

## Type Conversions

| From | To | Method |
|------|----|--------|
| `MaybePath<A>` | `EitherPath<E, A>` | `.toEitherPath(error)` |
| `MaybePath<A>` | `TryPath<A>` | `.toTryPath(exceptionSupplier)` |
| `MaybePath<A>` | `ValidationPath<E, A>` | `.toValidationPath(error, semigroup)` |
| `EitherPath<E, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `EitherPath<E, A>` | `TryPath<A>` | `.toTryPath(errorToException)` |
| `EitherPath<E, A>` | `ValidationPath<E, A>` | `.toValidationPath(semigroup)` |
| `TryPath<A>` | `EitherPath<E, A>` | `.toEitherPath(exceptionToError)` |
| `TryPath<A>` | `MaybePath<A>` | `.toMaybePath()` |
| `ValidationPath<E, A>` | `EitherPath<E, A>` | `.toEitherPath()` |
| `FocusPath<S, A>` | `MaybePath<A>` | `.toMaybePath(source)` |
| `FocusPath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(source)` |
| `AffinePath<S, A>` | `MaybePath<A>` | `.toMaybePath(source)` |
| `AffinePath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(source, errorFn)` |

---

## ForPath Comprehensions

`ForPath` provides for-comprehension syntax directly with Path types (up to 12 chained bindings):

```java
EitherPath<Error, Result> result = ForPath.from(fetchUser(id))
    .from(user -> validateUser(user))
    .from((user, validated) -> checkInventory(items))
    .yield((user, validated, inventory) -> createOrder(user, validated, inventory));
```

| Path Type | Entry Point | Supports `when()` guard |
|-----------|-------------|------------------------|
| `MaybePath<A>` | `ForPath.from(maybePath)` | Yes |
| `EitherPath<E, A>` | `ForPath.from(eitherPath)` | No |
| `TryPath<A>` | `ForPath.from(tryPath)` | No |
| `IOPath<A>` | `ForPath.from(ioPath)` | No |
| `VTaskPath<A>` | `ForPath.from(vtaskPath)` | No |
| `NonDetPath<A>` | `ForPath.from(nonDetPath)` | Yes |

Operations: `.from()` (bind/flatMap), `.let()` (pure value), `.when()` (guard/filter), `.yield()` (final transform).

---

## Project Setup

### Gradle Plugin (Recommended)

```gradle
// build.gradle.kts
plugins {
    id("io.github.higher-kinded-j.hkj") version "LATEST_VERSION"
}
```

This single line configures dependencies, preview features, annotation processors, and compile-time checks.

### Manual Gradle Setup

```gradle
plugins { java }

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
}

dependencies {
    implementation("io.github.higher-kinded-j:hkj-core:LATEST_VERSION")
    annotationProcessor("io.github.higher-kinded-j:hkj-processor-plugins:LATEST_VERSION")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("--enable-preview")
}

tasks.withType<Test>().configureEach {
    jvmArgs("--enable-preview")
}

tasks.withType<JavaExec>().configureEach {
    jvmArgs("--enable-preview")
}
```

### Maven Setup

```xml
<dependency>
    <groupId>io.github.higher-kinded-j</groupId>
    <artifactId>hkj-core</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

Configure `maven-compiler-plugin` with `--enable-preview` and Java 25 source/target.

---

## Common Compiler Errors

### 1. "Cannot infer type arguments for Path.right(...)"

**Cause**: Error type `E` has no value to infer from when creating a success path.
**Fix**: Add explicit type parameters.
```java
// Before (fails):
return Path.right(user);
// After (works):
return Path.<AppError, User>right(user);
```

### 2. "MaybePath cannot be converted to EitherPath"

**Cause**: `via` requires the same Path kind throughout a chain.
**Fix**: Convert at the boundary.
```java
.via(id -> Path.maybe(findUser(id))
    .toEitherPath(new AppError.UserNotFound(id)))
```

### 3. "Method via is not applicable for the arguments"

**Cause**: Function returns a plain value instead of a Path.
**Fix**: Use `map` for plain transforms, `via` for Path-returning functions.
```java
.map(this::extractName)   // A -> B (plain value)
.via(this::validateName)  // A -> Path<B> (returns a Path)
```

### 4. "No suitable method found for map(...)"

**Cause**: Lambda has branches with different return types or missing return.
**Fix**: Ensure all branches return the same type, or extract to a named method.

### 5. "Type argument E is not within bounds" (error type mismatch)

**Cause**: Different steps in a chain use different error types.
**Fix**: Standardise on one error type using `.mapError()`.
```java
.via(id -> lookupUser(id)
    .mapError(dbError -> new AppError.DatabaseError(dbError)))
```

---

## Core Pattern: Railway-Oriented Pipeline

```java
// Flat, composable, readable
public EitherPath<OrderError, Order> processOrder(String userId, OrderRequest request) {
    return Path.maybe(userRepository.findById(userId))
        .toEitherPath(() -> new OrderError.UserNotFound(userId))
        .via(user -> Path.either(validator.validate(request))
            .mapError(OrderError.ValidationFailed::new))
        .via(validated -> Path.tryOf(() -> paymentService.charge(user, amount))
            .toEitherPath(OrderError.PaymentFailed::new))
        .map(payment -> createOrder(user, request, payment));
}
```

Success flows down the pipeline. Failures short-circuit automatically. No nesting required.

---

## Debugging Tips

- Use `.peek(v -> log.debug("value: {}", v))` to observe intermediate values without breaking the chain
- Use method references (`.via(this::lookupOrder)`) instead of lambdas for clearer stack traces
- Stack traces: look for YOUR package name at the top (the failing business logic) and skip library internals (`Either.flatMap`, `EitherPath.via`)

---
name: hkj-guide
description: "Help with Higher-Kinded-J (HKJ) Java library: choose Path types (MaybePath, EitherPath, TryPath, ValidationPath, IOPath, VTaskPath, VResultPath, EitherOrBothPath), set up Gradle/Maven, migrate imperative Java code to Effect Paths, fix compiler errors, understand the railway model. Also: NonEmptyList, EitherOrBoth (inclusive-or, success with warnings), open-arity accumulating assembly (fields()/accumulate()), path-native resilience (withRetry/withTimeout/withCircuitBreaker/withBulkhead), TimeSource"
---

# Higher-Kinded-J Guide

You are helping a developer use the **Higher-Kinded-J (HKJ)** Java library. HKJ provides composable error handling via the **Effect Path API** and type-safe immutable data navigation via **Optics / Focus DSL**. It requires **Java 25 with `--enable-preview`**.

Key entry point: `org.higherkindedj.hkt.effect.Path`, the factory for all Path types.

## When to load supporting files

- If the user asks about **migrating imperative code** (try/catch, Optional, null checks, CompletableFuture), load `reference/migration-recipes.md`
- If the user asks about **service layer patterns, validation pipelines, or testing patterns**, load `reference/patterns.md`
- If the user asks about **retry, circuit breaker, saga, bulkhead, or `VResultPath`**, load `reference/resilience.md`
- For **optics, lenses, Focus DSL** in depth, suggest `/hkj-optics`
- For **record <-> DTO mapping, merging, or generated assembly** (`@GenerateMapping`, `@GenerateMerge`, `@GenerateAssembly`, `@GenerateErrorEnvelope`), suggest `/hkj-mapping`
- For **effect handlers, @EffectAlgebra, Free monads**, suggest `/hkj-effects`
- For **combining effects with optics** (.focus() on paths), suggest `/hkj-bridge`
- For **Spring Boot integration**, suggest `/hkj-spring`
- For **architecture patterns** (functional core / imperative shell, `TimeSource`), suggest `/hkj-arch`
- For **testing** (assertions, optic laws, `SteppableClock`), suggest `/hkj-test`

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
                                         |
                              Is it also asynchronous?
                                         |
                                        Yes
                                         |
                                    VResultPath
```

**Can a success still carry warnings?** Neither `Either` (exclusive) nor `Validated` (no partial
value) can express "it worked, but here's what was degraded". That shape is `EitherOrBothPath` --
see *Inclusive-Or* below.

Additional considerations:
- **Deferred side effects** (IO, network, DB): `IOPath`
- **Virtual thread concurrency**: `VTaskPath`
- **Virtual threads + a typed domain error**: `VResultPath` (`VTask<Either<E, A>>`)
- **Success that may carry warnings**: `EitherOrBothPath`
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
| `VResultPath<E, A>` | Virtual threads + typed error | `Path.vresultRight(v)`, `Path.vresultLeft(e)`, `Path.vresultDefer(() -> either)` | `.toEitherPath()` -> `EitherPath<E, A>` |
| `EitherOrBothPath<L, A>` | Success that may carry warnings | `Path.rightNel(v)`, `Path.leftNel(e)`, `Path.bothNel(warn, v)` | `.run()` -> `EitherOrBoth<L, A>` |
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
| `VResultPath<E, A>` | `EitherPath<E, A>` | `.toEitherPath()` (runs it) |
| `VResultPath<E, A>` | `VTaskPath<A>` | `.toVTaskPath(errorToException)` |
| `EitherOrBoth<L, R>` | `Either<L, R>` | `.toEitherDroppingWarnings()`, `.toEitherFailingOnWarnings()` |
| `EitherOrBoth<L, R>` | `Validated<L, R>` | `.toValidated()` |
| `FocusPath<S, A>` | `MaybePath<A>` | `.toMaybePath(source)` |
| `FocusPath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(source)` |
| `AffinePath<S, A>` | `MaybePath<A>` | `.toMaybePath(source)` |
| `AffinePath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(source, errorFn)` |

---

## VResultPath: the async typed-error railway

`VResultPath<E, A>` wraps `VTask<Either<E, A>>`. It is an `EitherPath` that happens to be
asynchronous. Use it whenever a virtual-thread operation has a **typed domain failure**.

The distinction that matters:

| Type | Wraps | Failure channel |
|------|-------|-----------------|
| `VTaskPath<A>` | `VTask<A>` | Untyped: a thrown exception, and nothing else |
| `VResultPath<E, A>` | `VTask<Either<E, A>>` | **Typed** `E` in the *value* channel; defects still throw |

So a business failure (`Left`) is a **value**, not an exception. A defect (a thrown bug) stays on the
`VTask` channel. Keeping those two apart is what makes the resilience combinators safe (see below).

```java
VResultPath<OrderError, Receipt> receipt =
    validateAddress(request)
        .via(addr -> lookupCustomer(request))
        .map(Customer::preferredPayment)
        .recoverWith(err -> retryOnce(request))
        .mapError(OrderError::enrich);
```

Create: `Path.vresultRight(v)`, `Path.vresultLeft(e)`, `Path.vresultEither(either)`,
`Path.vresult(vtaskOfEither)`, `Path.vresultDefer(() -> either)` (a supplier of an `Either`, not of
a bare value).
Leave: `.toEitherPath()` (runs it), `.toVTaskPath(errorToException)`.

**Resilience is now on the path itself**: `.withRetry(...)`, `.withTimeout(...)`,
`.withCircuitBreaker(...)`, `.withBulkhead(...)`. On `VResultPath` it is railway-aware: a `Left`
is never retried and never trips the breaker, because a business rejection is not an outage. Load
`reference/resilience.md` for the full story.

---

## NonEmptyList: the total error channel

`org.higherkindedj.hkt.nonemptylist.NonEmptyList`: a list that cannot be empty, by construction.
It is the canonical carrier for an accumulating error channel: if a computation failed, there is at
least one reason, and the type should say so.

```java
NonEmptyList<String> reasons = NonEmptyList.of("too short", "no digit");
String first = reasons.head();   // total: no Optional, no throw. This is the whole point.
```

| Create | Notes |
|--------|-------|
| `NonEmptyList.of(head, rest...)` | Head is separate, so emptiness is unrepresentable |
| `NonEmptyList.single(v)` | One element |
| `NonEmptyList.fromList(list)` | Returns `Maybe<NonEmptyList<A>>`: the empty case is handled here, once |
| `NonEmptyList.semigroup()` | The canonical combine for accumulation |

Ops: `head()`, `last()`, `size()`, `map`, `flatMap`, `foldLeft`, `reverse`, `concat`, `append`,
`prepend`, `reduce(sg)`, `min/max(cmp)`, `toJavaList()`.

Use the `Nel` factories to get a `NonEmptyList` error channel without naming a semigroup:

```java
Validated<NonEmptyList<String>, Integer> v = Validated.invalidNel("bad");
ValidationPath<NonEmptyList<String>, Integer> p = Path.invalidNel("bad");
```

The race / first-success combinators take `NonEmptyList` overloads too (on `PathOps`), which removes
the empty-list `IllegalArgumentException` from the API entirely:
`PathOps.raceVTask(nel)`, `firstVTaskSuccess(nel)`, `firstSuccess(nel)`, `raceIO(nel)`,
`firstCompletedSuccess(nel)`.

---

## Open-Arity Assembly: `fields()` and `accumulate()`

Build a record from many independently-validated parts, collecting **every** failure. Available on
three carriers, each in a labelled and an unlabelled flavour. The ladder tops out at **16** fields.

| Carrier | Labelled (`FieldError`) | Unlabelled (your error type) |
|---------|-------------------------|------------------------------|
| `Validated` | `Validated.fields()` | `Validated.accumulate()` |
| `ValidationPath` | `Path.fields()` | `Path.accumulate()` |
| `EitherOrBoth` | `EitherOrBoth.fields()` | `EitherOrBoth.accumulate()` |

Note the `ValidationPath` twins live on **`Path`**, not on `ValidationPath`.

```java
// Labelled: errors carry the field name, so they locate themselves
Validated<NonEmptyList<FieldError>, User> user =
    Validated.fields()
        .field("name",  Name.parse(dto.name()))
        .field("email", Email.parse(dto.email()))
        .field("age",   Age.parse(dto.age()))
        .apply(User::new);          // every invalid field reported, not just the first

// Unlabelled: .and(...) instead of .field(label, ...)
Validated<NonEmptyList<String>, User> u =
    Validated.<String>accumulate()
        .and(parseName(dto.name()))
        .and(parseEmail(dto.email()))
        .apply(User::new);
```

**Past 16 fields, reach for `@GenerateAssembly`** (see `/hkj-mapping`): it emits a curried `ap` chain
at exactly the record's arity, so it has no ceiling. This ladder stops at 16.

---

## Inclusive-Or: `EitherOrBoth` and `EitherOrBothPath`

`org.higherkindedj.hkt.eitherorboth.EitherOrBoth<L, R>` is the inclusive or (`Ior` / `These`
elsewhere): **`Left`** (fatal), **`Right`** (clean success), or **`Both`** (success *carrying*
non-fatal warnings). Right-biased.

Reach for it when a success can still be degraded: a config that loaded but fell back to defaults,
an import that succeeded but skipped rows. `Either` cannot say this (exclusive), and `Validated`
cannot (it has no partial value).

```java
EitherOrBoth<String, Config> result = EitherOrBoth.both("port invalid, using 8080", config);

String rendered = result.fold(
    err          -> "failed: " + err,
    cfg          -> "ok",
    (warn, cfg)  -> "ok, with warning: " + warn);
```

Factories: `left(l)`, `right(r)`, `both(l, r)`, `fromEither(e)`, `fromValidated(v)`.
Accessors: `isLeft()` / `isRight()` / `isBoth()`, `getLeft()` -> `Maybe<L>`, `getRight()` -> `Maybe<R>`.

`EitherOrBothPath` is the Path wrapper. It is unusual in implementing **both** protocols:

| Protocol | Methods | Behaviour |
|----------|---------|-----------|
| `Chainable` | `via`, `zipWith` | Short-circuits on `Left` |
| `Accumulating` | `zipWithAccum`, `andAlso` | Parallel; collects every warning |

```java
Path.right(v, semigroup)   Path.left(e, semigroup)   Path.both(warn, v, semigroup)
Path.rightNel(v)           Path.leftNel(e)           Path.bothNel(warn, v)   // bake in NonEmptyList.semigroup()
```

**The one thing to get right:** `flatMap` / `ap` **short-circuit**. `EitherOrBoth` is a lawful monad,
*not* a Validated-style accumulator; only `Both` warnings accumulate as they pass through. The
collect-everything behaviour lives in `zipWithAccum` / `accumulate()` / `fields()`, which are
deliberately **not** monadic. Do not assume the monad accumulates.

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

It also adds **`-parameters`** to every compile task automatically. Do not add it by hand: the
plugin (and the Maven lifecycle participant) now wire it unconditionally, and `@GenerateMapping`
relies on it to read constructor parameter names.

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

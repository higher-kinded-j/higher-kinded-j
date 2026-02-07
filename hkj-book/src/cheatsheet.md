# Cheat Sheet

~~~admonish info title="What You'll Learn"
- Quick reference for all Path types, creation, and extraction
- Common operators at a glance
- How to get back to standard Java from any Path
- Type conversion routes between Path types
~~~

---

## Path Types

| Path Type | For | Create | Extract |
|-----------|-----|--------|---------|
| `MaybePath<A>` | Absence | `Path.maybe(v)`, `Path.just(v)`, `Path.nothing()` | `.run()` → `Maybe<A>` |
| `EitherPath<E, A>` | Typed errors | `Path.right(v)`, `Path.left(e)`, `Path.either(e)` | `.run()` → `Either<E, A>` |
| `TryPath<A>` | Exceptions | `Path.tryOf(() -> ...)`, `Path.success(v)`, `Path.failure(ex)` | `.run()` → `Try<A>` |
| `ValidationPath<E, A>` | Accumulating errors | `Path.valid(v, sg)`, `Path.invalid(e, sg)` | `.run()` → `Validated<E, A>` |
| `IOPath<A>` | Deferred side effects | `Path.io(() -> ...)`, `Path.ioPure(v)` | `.unsafeRun()` → `A` |
| `VTaskPath<A>` | Virtual threads | `Path.vtask(() -> ...)`, `Path.vtaskPure(v)` | `.unsafeRun()` → `A` |
| `ReaderPath<R, A>` | Dependency injection | `Path.reader(r)`, `Path.ask()`, `Path.asks(fn)` | `.run(env)` → `A` |
| `WriterPath<W, A>` | Logging, audit | `Path.writer(w, m)`, `Path.tell(log, m)` | `.run()` → `Writer<W, A>` |
| `WithStatePath<S, A>` | Stateful computation | `Path.state(s)`, `Path.getState()` | `.run(initial)` → `(S, A)` |
| `TrampolinePath<A>` | Stack-safe recursion | `Path.trampolineDone(v)`, `Path.trampolineDefer(s)` | `.run()` → `A` |
| `LazyPath<A>` | Deferred, memoised | `Path.lazyDefer(() -> ...)`, `Path.lazyNow(v)` | `.run()` → `A` |
| `CompletableFuturePath<A>` | Async | `Path.future(cf)`, `Path.futureAsync(() -> ...)` | `.run()` → `CompletableFuture<A>` |
| `ListPath<A>` | Batch processing with positional zipping | `Path.listPath(list)` | `.run()` → `List<A>` |
| `StreamPath<A>` | Lazy sequences | `Path.stream(stream)` | `.run()` → `Stream<A>` |
| `NonDetPath<A>` | Non-deterministic search, combinations | `Path.list(list)` | `.run()` → `List<A>` |
| `IdPath<A>` | Pure values | `Path.id(v)` | `.run()` → `Id<A>` |
| `OptionalPath<A>` | java.util.Optional bridge | `Path.optional(opt)`, `Path.present(v)` | `.run()` → `Optional<A>` |
| `FreePath<F, A>` | DSL building | `Path.freePure(v, f)`, `Path.freeLift(fa, f)` | `.foldMap(nat, m)` |
| `GenericPath<F, A>` | Custom monads | `Path.generic(kind, monad)` | `.run()` → `Kind<F, A>` |

---

## Operators

### Transformation

| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.map(fn)` | Transform the success value | All paths |
| `.peek(fn)` | Observe the value without changing it | All paths |
| `.focus(lens)` | Navigate into a nested field via optics | MaybePath, EitherPath, TryPath, IOPath, ValidationPath |

### Chaining

| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.via(fn)` | Chain to another path (monadic bind) | All chainable paths |
| `.flatMap(fn)` | Alias for `via` | All chainable paths |
| `.then(supplier)` | Sequence, ignoring previous value | All chainable paths |

### Combination

| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.zipWith(other, fn)` | Combine two paths (fail-fast) | All combinable paths |
| `.zipWith3(b, c, fn)` | Combine three paths (fail-fast) | Most paths |
| `.zipWithAccum(other, fn)` | Combine, accumulating errors | ValidationPath |
| `.zipWith3Accum(b, c, fn)` | Combine three, accumulating errors | ValidationPath |
| `.andAlso(other)` | Accumulate errors, keep left value | ValidationPath |

### Error Handling

| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.recover(fn)` | Error → success value | MaybePath, EitherPath, TryPath, ValidationPath |
| `.recoverWith(fn)` | Error → new path | MaybePath, EitherPath, TryPath, ValidationPath |
| `.orElse(supplier)` | Try an alternative path | MaybePath, EitherPath, TryPath, ValidationPath |
| `.mapError(fn)` | Transform the error type | EitherPath, TryPath, ValidationPath |
| `.peekLeft(fn)` | Observe the error without changing it | EitherPath |
| `.peekFailure(fn)` | Observe the exception without changing it | TryPath |

### Extraction

| Operator | What It Does | Available On |
|----------|-------------|--------------|
| `.run()` | Extract the underlying value | All paths |
| `.unsafeRun()` | Execute a deferred effect (may throw) | IOPath, VTaskPath |
| `.runSafe()` | Execute safely, returning `Try` | IOPath, VTaskPath |
| `.fold(onErr, onOk)` | Handle both tracks | EitherPath, TryPath, ValidationPath |
| `.getOrElse(default)` | Extract or use default | MaybePath, EitherPath, TryPath, ValidationPath |
| `.getOrElseGet(supplier)` | Extract or compute default | MaybePath, EitherPath, TryPath, ValidationPath |

---

## Escape Hatches

Getting back to standard Java from any Path:

| From | To | How |
|------|----|-----|
| `MaybePath<A>` | `Maybe<A>` | `.run()` |
| `MaybePath<A>` | `Optional<A>` | `.run().toOptional()` |
| `MaybePath<A>` | `A` (or default) | `.getOrElse(defaultValue)` |
| `EitherPath<E, A>` | `Either<E, A>` | `.run()` |
| `EitherPath<E, A>` | `A` (or default) | `.getOrElse(defaultValue)` |
| `EitherPath<E, A>` | `B` | `.run().fold(onErr, onOk)` |
| `TryPath<A>` | `Try<A>` | `.run()` |
| `TryPath<A>` | `A` (or default) | `.getOrElse(defaultValue)` |
| `ValidationPath<E, A>` | `Validated<E, A>` | `.run()` |
| `ValidationPath<E, A>` | `B` | `.fold(onInvalid, onValid)` |
| `IOPath<A>` | `A` | `.unsafeRun()` |
| `IOPath<A>` | `Try<A>` | `.runSafe()` |
| `VTaskPath<A>` | `A` | `.unsafeRun()` |
| `VTaskPath<A>` | `Try<A>` | `.runSafe()` |

---

## Type Conversions

| From | To | Method |
|------|----|--------|
| `MaybePath<A>` | `EitherPath<E, A>` | `.toEitherPath(error)` |
| `MaybePath<A>` | `TryPath<A>` | `.toTryPath(exceptionSupplier)` |
| `MaybePath<A>` | `ValidationPath<E, A>` | `.toValidationPath(error, semigroup)` |
| `MaybePath<A>` | `OptionalPath<A>` | `.toOptionalPath()` |
| `EitherPath<E, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `EitherPath<E, A>` | `TryPath<A>` | `.toTryPath(errorToException)` |
| `EitherPath<E, A>` | `ValidationPath<E, A>` | `.toValidationPath(semigroup)` |
| `EitherPath<E, A>` | `OptionalPath<A>` | `.toOptionalPath()` |
| `TryPath<A>` | `EitherPath<E, A>` | `.toEitherPath(exceptionToError)` |
| `TryPath<A>` | `MaybePath<A>` | `.toMaybePath()` |
| `ValidationPath<E, A>` | `EitherPath<E, A>` | `.toEitherPath()` |
| `ValidationPath<E, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `FocusPath<S, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `FocusPath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(errorFn)` |
| `AffinePath<S, A>` | `MaybePath<A>` | `.toMaybePath()` |
| `AffinePath<S, A>` | `EitherPath<E, A>` | `.toEitherPath(errorFn)` |

---

**Previous:** [Quickstart](quickstart.md)
**Next:** [Effect Path API](effect/ch_intro.md)

# Path Types

> *"It is not down on any map; true places never are."*
>
> — Herman Melville, *Moby-Dick*

Melville was speaking of Queequeg's island home, but the observation applies
to software: the territory you're navigating (nullable returns, network
failures, validation errors, deferred effects) isn't marked on any class
diagram. You need to choose your vessel before setting sail.

This chapter covers each Path type in detail. But before cataloguing the
fleet, a more pressing question: *which one do you need?*

~~~admonish info title="What You'll Learn"
- How to choose the right Path type for your situation
- Overview of all available Path types
- When each type is the right tool, and when it isn't
~~~

---

## Choosing Your Path

Before diving into specifics, orient yourself by the problem you're solving:

### _"The value might not exist"_

You're dealing with absence: a lookup that returns nothing, an optional
configuration, a field that might be null.

**Reach for [`MaybePath`](path_maybe.md)** if absence is normal and expected, not an error
condition. Nobody needs to know *why* the value is missing.

**Reach for [`OptionalPath`](path_optional.md)** if you're bridging to Java's `Optional` ecosystem
and want to stay close to the standard library.

### _"The operation might fail, and I need to know why"_

Something can go wrong, and the error carries information: a validation
message, a typed error code, a domain-specific failure.

**Reach for [`EitherPath`](path_either.md)** when you control the error type and want typed,
structured errors.

**Reach for [`TryPath`](path_try.md)** when you're wrapping code that throws exceptions and
want to stay in exception-land (with `Throwable` as the error type).

### _"I need ALL the errors, not just the first"_

Multiple independent validations, and stopping at the first failure would
be unkind to your users.

**Reach for [`ValidationPath`](path_validation.md)** with `zipWithAccum` to accumulate every error.

### _"The operation has side effects I want to defer"_

You're reading files, calling APIs, writing to databases, effects that
shouldn't happen until you're ready.

**Reach for [`IOPath`](path_io.md)** to describe the effect without executing it. Nothing
runs until you call `unsafeRun()`.

### _"I need concurrent operations at scale"_

You're building services that handle many concurrent requests, calling
multiple APIs in parallel, or processing streams of events.

**Reach for [`VTaskPath`](path_vtask.md)** for virtual thread-based concurrency. Write
simple blocking code that scales to millions of concurrent tasks without
the complexity of reactive streams.

### _"I need stack-safe recursion"_

Deep recursive algorithms that would blow the stack with direct recursion.

**Reach for [`TrampolinePath`](path_trampoline.md)** for guaranteed stack safety regardless of depth.

### _"I want to build an interpretable DSL"_

Separate description from execution, test with mock interpreters, or support
multiple interpretation strategies.

**Reach for [`FreePath`](path_free.md)** for sequential DSLs or
[`FreeApPath`](path_freeap.md) for parallel/static-analysis-friendly DSLs.

### _"The operation always succeeds"_

No failure case, no absence; you just want Path operations on a pure value.

**Reach for [`IdPath`](path_id.md)** when you need a trivial Path for generic code or testing.

### _"None of the above"_

You have a custom monad, or you're writing highly generic code.

**Reach for [`GenericPath`](path_generic.md)** as the escape hatch; it wraps any `Kind<F, A>`
with a `Monad` instance.

---

## Quick Reference

| Path Type | Wraps | Error Type | Evaluation | Key Use Case |
|-----------|-------|------------|------------|--------------|
| [`MaybePath<A>`](path_maybe.md) | `Maybe<A>` | None (absence) | Immediate | Optional values |
| [`EitherPath<E, A>`](path_either.md) | `Either<E, A>` | `E` (typed) | Immediate | Typed error handling |
| [`TryPath<A>`](path_try.md) | `Try<A>` | `Throwable` | Immediate | Exception wrapping |
| [`IOPath<A>`](path_io.md) | `IO<A>` | `Throwable` | **Deferred** | Side effects |
| [`VTaskPath<A>`](path_vtask.md) | `VTask<A>` | `Throwable` | **Deferred** | Virtual thread concurrency |
| [`ValidationPath<E, A>`](path_validation.md) | `Validated<E, A>` | `E` (accumulated) | Immediate | Form validation |
| [`IdPath<A>`](path_id.md) | `Id<A>` | None (always succeeds) | Immediate | Pure values |
| [`OptionalPath<A>`](path_optional.md) | `Optional<A>` | None (absence) | Immediate | Java stdlib bridge |
| [`GenericPath<F, A>`](path_generic.md) | `Kind<F, A>` | Depends on monad | Depends | Custom monads |
| [`TrampolinePath<A>`](path_trampoline.md) | `Trampoline<A>` | None | **Deferred** | Stack-safe recursion |
| [`FreePath<F, A>`](path_free.md) | `Free<F, A>` | None | Interpreted | DSL building |
| [`FreeApPath<F, A>`](path_freeap.md) | `FreeAp<F, A>` | None | Interpreted | Applicative DSLs |

---

## Path Types by Category

### Value Containers (Immediate Evaluation)

These types wrap values and evaluate operations immediately:

- **[MaybePath](path_maybe.md)** - For values that might be absent
- **[OptionalPath](path_optional.md)** - Bridge to Java's `Optional`
- **[IdPath](path_id.md)** - Always contains a value (identity)

### Error Handling (Immediate Evaluation)

These types handle failures with different strategies:

- **[EitherPath](path_either.md)** - Typed errors, short-circuit on first failure
- **[TryPath](path_try.md)** - Exception-based errors
- **[ValidationPath](path_validation.md)** - Accumulate all errors

### Deferred Computation

These types describe computations without executing them:

- **[IOPath](path_io.md)** - Side effects, runs when you call `unsafeRun()`
- **[VTaskPath](path_vtask.md)** - Virtual thread concurrency, runs when you call `run()`
- **[TrampolinePath](path_trampoline.md)** - Stack-safe recursion

### DSL Building

These types support building domain-specific languages:

- **[FreePath](path_free.md)** - Sequential, monadic DSLs
- **[FreeApPath](path_freeap.md)** - Parallel, applicative DSLs

### Universal

- **[GenericPath](path_generic.md)** - Works with any monad

---

## Summary: Choosing Your Vessel

| Scenario | Path Type | Why |
|----------|-----------|-----|
| Value might be absent | `MaybePath` | Simple presence/absence |
| Operation might fail with typed error | `EitherPath` | Structured error handling |
| Wrapping exception-throwing code | `TryPath` | Exception → functional bridge |
| Side effects to defer | `IOPath` | Lazy, referential transparency |
| Concurrent operations at scale | `VTaskPath` | Virtual threads, simple blocking code |
| Need ALL validation errors | `ValidationPath` | Error accumulation |
| Bridging Java's Optional | `OptionalPath` | Stdlib compatibility |
| Always succeeds, pure value | `IdPath` | Generic/testing contexts |
| Custom monad | `GenericPath` | Universal escape hatch |
| Deep recursion without stack overflow | `TrampolinePath` | Stack-safe trampolining |
| DSL with sequential operations | `FreePath` | Interpretable programs |
| DSL with independent operations | `FreeApPath` | Static analysis, parallel |

The choice isn't always obvious, and that's fine. You can convert between
types as your needs evolve (`MaybePath` to `EitherPath` when you need error
messages, `TryPath` to `EitherPath` when you want typed errors). The
[Type Conversions](conversions.md) chapter covers these conversions in detail.

~~~admonish tip title="See Also"
- [Composition Patterns](composition.md) - Combining and sequencing Path operations
- [Type Conversions](conversions.md) - Converting between Path types
- [Patterns and Recipes](patterns.md) - Common usage patterns
~~~

---

**Previous:** [Capability Interfaces](capabilities.md)
**Next:** [MaybePath](path_maybe.md)

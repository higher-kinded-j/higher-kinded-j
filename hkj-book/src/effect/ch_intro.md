# Effect Path API: Fluent Effect Composition

> *"A map is not the territory it represents, but, if correct, it has a similar
> structure to the territory, which accounts for its usefulness."*
>
> – Alfred Korzybski, *Science and Sanity*

---

Service layers return effects. A repository method yields `Maybe<User>`. A validation
function produces `Either<Error, Order>`. A file operation returns `Try<Contents>`.
Each effect type has its own API. Each requires its own handling pattern. String a few
together and the code becomes a nested mess of maps, flatMaps, and explicit unwrapping.

The Effect Path API provides a way through.

Rather than working with raw effect types, you wrap them in Path types: `MaybePath`,
`EitherPath`, `TryPath`, `IOPath`, `ValidationPath`, `IdPath`, `OptionalPath`, and
`GenericPath`. These thin wrappers expose a unified vocabulary; the same `via`, `map`,
and `recover` operations regardless of the underlying effect. Chain them together,
convert between them, extract results at the end. The underlying complexity remains
(it must), but the Path contains it.

The vocabulary deliberately mirrors the Focus DSL from the optics chapters. Where
FocusPath navigates through *data structures*, EffectPath navigates through *effect
types*. Both use `via` for composition. Both provide fluent, chainable operations.
If you've used optics, the patterns will feel familiar. If you haven't, the consistency
will help when you do.

---

~~~admonish info title="In This Chapter"
- **Path Types** – Fluent wrappers (`MaybePath`, `EitherPath`, `TryPath`, `IOPath`,
  `ValidationPath`, `IdPath`, `OptionalPath`, `GenericPath`) that provide unified
  composition over higher-kinded-j's effect types. Each wrapper delegates to its
  underlying type while exposing a consistent API.

- **Capability Interfaces** – The hierarchy that powers composition: `Composable`
  (mapping), `Combinable` (independent combination), `Chainable` (sequencing),
  `Recoverable` (error handling), `Effectful` (deferred execution), and
  `Accumulating` (error accumulation for validation).

- **The `via` Pattern** – Chain dependent computations using the same vocabulary as
  optics. Where FocusPath's `via` navigates data, EffectPath's `via` navigates
  effects.

- **Type Conversion** – Convert freely between path types: `MaybePath` to `EitherPath`
  (providing an error), `EitherPath` to `TryPath` (wrapping the error), and more.

- **Error Recovery** – Handle failures gracefully with `recover`, `orElse`, and
  `recoverWith`. Transform error types with `mapError`.

- **Error Accumulation** – Collect all validation errors with `ValidationPath` using
  `zipWithAccum` instead of short-circuiting on the first error.
~~~

~~~admonish example title="See Example Code"
- [BasicPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/BasicPathExample.java) - Creating and transforming paths
- [ChainedComputationsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ChainedComputationsExample.java) - Fluent chaining patterns
- [ErrorHandlingExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ErrorHandlingExample.java) - Recovery and error handling
- [ValidationPipelineExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ValidationPipelineExample.java) - Combining validations
- [ServiceLayerExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/ServiceLayerExample.java) - Real-world service patterns
- [AccumulatingValidationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/AccumulatingValidationExample.java) - Error accumulation with ValidationPath
- [PathOpsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/PathOpsExample.java) - Sequence and traverse utilities
- [CrossPathConversionsExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/CrossPathConversionsExample.java) - Converting between path types
~~~

## Chapter Contents

This section covers the Effect Path API across multiple chapters:

1. **[Effect Path Overview](effect_path_overview.md)** – Creating paths, basic transformations, and terminal operations
2. **[Capability Interfaces](capabilities.md)** – The interface hierarchy powering Path composition
3. **[Path Types](path_types.md)** – Detailed coverage of each Path type
4. **[Composition Patterns](composition.md)** – Chaining, combining, and debugging
5. **[Type Conversions](conversions.md)** – Moving between different Path types
6. **[Patterns and Recipes](patterns.md)** – Real-world patterns and best practices

---

## The Problem: Verbose Effect Composition

Consider a typical service layer operation that fetches a user, validates their permissions, and retrieves their orders:

```java
// Traditional approach: nested handling at each step
Maybe<User> maybeUser = userRepository.findById(userId);
if (maybeUser.isNothing()) {
    return Either.left(new UserNotFound(userId));
}
User user = maybeUser.get();

Either<AuthError, Permissions> permsResult = authService.getPermissions(user);
if (permsResult.isLeft()) {
    return Either.left(new AuthFailed(permsResult.getLeft()));
}
Permissions perms = permsResult.getRight();

if (!perms.canViewOrders()) {
    return Either.left(new AccessDenied("Cannot view orders"));
}

Try<List<Order>> ordersResult = orderService.getOrders(user);
if (ordersResult.isFailure()) {
    return Either.left(new OrderFetchFailed(ordersResult.getCause()));
}

return Either.right(ordersResult.get());
```

This code is correct but verbose. Each effect type requires its own handling pattern. The business logic is obscured by boilerplate.

## The Solution: Path Types

With the Effect Path API, the same logic becomes:

```java
EitherPath<ServiceError, List<Order>> result =
    Path.maybe(userRepository.findById(userId))
        .toEitherPath(() -> new UserNotFound(userId))
        .via(user -> Path.either(authService.getPermissions(user))
            .mapError(AuthFailed::new))
        .via(perms -> perms.canViewOrders()
            ? Path.right(perms)
            : Path.left(new AccessDenied("Cannot view orders")))
        .via(perms -> Path.tryOf(() -> orderService.getOrders(perms.user()))
            .toEitherPath(OrderFetchFailed::new));

return result.run();
```

The Path types handle the error propagation. Each step uses the same vocabulary (`via`, `map`, `toEitherPath`). The business logic is clear.

---

## Path Types at a Glance

| Path Type | Underlying Effect | Use Case |
|-----------|------------------|----------|
| `MaybePath<A>` | `Maybe<A>` | Optional values, absence without error |
| `EitherPath<E, A>` | `Either<E, A>` | Typed error handling |
| `TryPath<A>` | `Try<A>` | Exception handling |
| `IOPath<A>` | `IO<A>` | Deferred side effects |
| `ValidationPath<E, A>` | `Validated<E, A>` | Error-accumulating validation |
| `IdPath<A>` | `Id<A>` | Identity wrapper (trivial monad) |
| `OptionalPath<A>` | `Optional<A>` | Java stdlib Optional bridge |
| `GenericPath<F, A>` | `Kind<F, A>` | Custom monad escape hatch |

Each Path type wraps its underlying effect and provides:
- `map(f)` – Transform the success value
- `via(f)` – Chain to another Path (monadic bind)
- `run()` – Extract the underlying effect
- Type-specific operations (recovery, error transformation, etc.)

Continue to [Effect Path Overview](effect_path_overview.md) for detailed usage.

---

**Next:** [Effect Path Overview](effect_path_overview.md)

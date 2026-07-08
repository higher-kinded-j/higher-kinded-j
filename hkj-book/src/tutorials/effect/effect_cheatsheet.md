# Effect API Journey — Cheatsheet

A single-page reference for the Effect Path API. Two columns: how we wrote it before, how we write it now.

~~~admonish info title="Scope"
- All `Path` factory methods and the four core operations (`map`, `via`, recovery, `zipWith`)
- `ForPath` comprehension shape and tuple-binding semantics
- Effect Contexts: `ErrorContext`, `ConfigContext`, `MutableContext`
- Service-integration patterns (`@GeneratePathBridge`)
- Focus-Effect bridge basics
~~~

---

## Pick the right Path type

| The shape we have | The Path we want | Factory |
|--------------------|------------------|---------|
| A value that may be `null` | `MaybePath<A>` | `Path.maybe(nullable)` |
| A value we know is present | `MaybePath<A>` | `Path.just(value)` |
| Explicit absence | `MaybePath<A>` | `Path.nothing()` |
| A value or a typed error | `EitherPath<E, A>` | `Path.right(v)` / `Path.left(e)` / `Path.either(either)` |
| A `Supplier` that may throw | `TryPath<A>` | `Path.tryOf(() -> ...)` |
| A side-effecting computation, deferred | `IOPath<A>` | `Path.io(() -> ...)` |
| A virtual-thread async computation | `VTaskPath<A>` | `Path.vtask(() -> ...)` |
| A virtual-thread async computation with a typed error | `VResultPath<E, A>` | `Path.vresultDefer(() -> ...)` / `Path.vresult(vtaskOfEither)` |
| An existing `Optional<A>` | `OptionalPath<A>` | `Path.optional(opt)` |

---

## Core operations (work on every Path)

| Pattern | Imperative Java | Higher-Kinded-J |
|---------|------------------|-----------------|
| Transform the success value | `opt.map(f)` / `future.thenApply(f)` | `path.map(f)` |
| Chain a step that itself returns a Path | `opt.flatMap(f)` / `future.thenCompose(f)` | `path.via(f)` (`flatMap` is an alias) |
| Replace error with a value | `try { ... } catch (E e) { return default; }` | `path.recover(err -> default)` |
| Replace error with another path | `try { ... } catch (E e) { return retry(); }` | `path.recoverWith(err -> alt)` |
| Use an alternative when this fails | `Optional.or(() -> alt)` | `path.orElse(() -> alt)` |
| Translate the error type | manual map | `path.mapError(e -> e2)` |
| Combine two independent paths | `CompletableFuture.allOf` then unpack | `pathA.zipWith(pathB, combiner)` |

---

## `map` vs `via` — the canonical decision

| Function shape | Reach for | Result shape |
|----------------|-----------|--------------|
| `A -> B` (plain value) | `map` | `Path<B>` |
| `A -> Path<B>` (wrapped value) | `via` (or `flatMap`) | `Path<B>` (flattened) |
| `A -> Path<B>` but we used `map` | (compile error or worse) | `Path<Path<B>>` (silently nested) |

When in doubt, follow the type: if the lambda body returns a Path, the call is `via`.

---

## ForPath at a glance

```java
ForPath.from(initialPath)              // start with a Path
  .from(value -> dependentPath(value)) // bind another Path step
  .let(value -> pureFunction(value))   // bind a pure value (no new effect)
  .yield((step1, step2, step3) -> ...) // combine all bound values
```

- Each `.from(...)` lambda after the first receives a **tuple** of previously bound values; reach in with `t._1()`, `t._2()`, ...
- `.yield(...)` is the only place where every binding is in scope by name.
- Short-circuits on the first failing step (Nothing / Left / Failure), exactly like a plain chain of `via`s.

When to reach for ForPath: three or more dependent steps, or when intermediate values need to be referenced from later steps.

---

## Effect Contexts

| Context | Wraps | Use for |
|---------|-------|---------|
| `ErrorContext<?, E, A>` | `EitherT` (typed error + IO) | Async work that may fail with a domain error |
| `ConfigContext<?, R, A>` | `ReaderT` | Threading configuration through a workflow without DI |
| `MutableContext<?, S, A>` | `StateT` | Workflow-local state without `ThreadLocal` or mutable fields |

All three carry the same `map` / `via` / `recover` surface as the Path API; the suffix differs only in how we run the workflow at the boundary:

```java
errorCtx.runIO().unsafeRun();         // -> Either<E, A>
configCtx.runWithSync(config);        // -> A
mutableCtx.runWith(initial)           // -> StateTuple<S, A>
         .unsafeRun();
```

---

## Service integration

```java
@GeneratePathBridge
public interface UserService {
    @PathVia
    Optional<User> findById(Long id);

    @PathVia
    Either<Error, User> createUser(CreateUserRequest req);
}
```

The annotation processor generates `UserServicePaths` with:

| Original return | Generated wrapper |
|-----------------|--------------------|
| `Optional<T>` | `OptionalPath<T>` (or `MaybePath<T>` with `@PathVia(MAYBE)`) |
| `Either<E, T>` | `EitherPath<E, T>` |
| `T` (with `@PathVia(IO)`) | `IOPath<T>` |
| `CompletableFuture<T>` | `VTaskPath<T>` (or `CompletableFuturePath<T>`) |

The wrapper is exactly the one-liner shown in Tutorial 02 Exercise 6 — generated so we never have to write it.

---

## Focus-Effect bridge

```java
EitherPath<Error, User>
  .focus(addressPath)        // narrow to the Address inside the User
  .focus(cityPath)            // narrow further to the city String
  .map(String::toUpperCase);  // transform the focused field
```

Errors propagate through `focus(...)` unchanged. For more complex navigation use `ForPath` with `.focus(...)` (Tutorial 14 of the Optics journey covers the full bridge).

---

## The boundary rule

`unsafeRun()` (and equivalents like `runWithSync`) belong at the **edge** of the program. Build the entire workflow as a value, then run once.

```java
// Right
ErrorContext<?, E, A> workflow =
    step1().via(this::step2).via(this::step3).map(this::finalize);
return workflow.runIO().unsafeRun();

// Wrong
A intermediate = step1().runIO().unsafeRun();   // executes too early
return step2(intermediate).runIO().unsafeRun(); // runs the IO twice
```

---

## Where this lands in [One Line, Six Layers](../../hkts/one_line_six_layers.md)

The Effect API is the layer that lets us write the One Line, Six Layers expression at all:

```
   repo.find(id)              .toEitherPath()      .focus().attributes().at(key)
   └── Effect Path ───────────┤                    └── Optic ─ via Focus DSL ───┐
       MaybePath, EitherPath, │                                                 │
       TryPath, IOPath, ...   │                                                 │
                              │                                                 │
                              └── Tutorial 01: every Path conversion lives in   │
                                  this one method shape                         │
                                                                                │
   .modify(spec::validateAndCoerce)             .flatMap(repo::save);           │
   └── map (under the optic) ────┐              └── via / flatMap ──────────────┘
       Tutorial 01 (map)         │                  Tutorial 01 (via)
                                │
                                └── ForPath / Contexts (Tutorial 02) flatten
                                    multi-step versions of this shape
```

---

**See also:** [Effect Path Overview](../../effect/effect_path_overview.md) · [ForPath Comprehension](../../effect/forpath_comprehension.md) · [Effect Contexts](../../effect/effect_contexts.md) · [One Line, Six Layers](../../hkts/one_line_six_layers.md)

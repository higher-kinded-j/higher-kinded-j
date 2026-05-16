# Common Compiler Errors

Transformer code is generic-heavy by nature: a typical signature like `Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Result>` packs three type parameters into two layers. When something goes wrong, the compiler messages can be hard to read. This page documents the errors developers hit most often, what they actually mean, and the fix.

~~~admonish info title="What You'll Learn"
- How to satisfy the missing-Monad constraint when constructing a transformer monad
- How to unify error types across an `EitherT` chain
- Why `StateT.mapT` takes an extra parameter that the other `mapT` methods do not
- When to call `.value()` and when to leave a transformer alone
- How to spot type-inference failures in `EitherT.fromEither` and similar factories
~~~

---

## 1. "Cannot resolve constructor `EitherTMonad`"

**The error:**

```
error: constructor EitherTMonad in class EitherTMonad<F, L> cannot be applied to given types;
    var eitherTMonad = Instances.eitherT();
                       ^
  required: Monad<F>
  found:    no arguments
  reason:   actual and formal argument lists differ in length
```

**The trigger:**

```java
var eitherTMonad =
    Instances.eitherT();   // missing argument
```

Every transformer monad needs a `Monad<F>` instance for the *outer* effect. The constructor cannot infer it from thin air.

**The fix:** pass the outer monad to the constructor.

```java
var futureMonad  = Instances.monadError(completableFuture());
var eitherTMonad =
    Instances.eitherT(futureMonad);
```

The same rule applies to `OptionalTMonad`, `MaybeTMonad`, `ReaderTMonad`, `StateTMonad`, and `WriterTMonad`. `WriterTMonad` additionally requires a `Monoid<W>` for the output type.

---

## 2. "Incompatible types: error type mismatch in EitherT chain"

**The error:**

```
error: incompatible types: Kind<EitherTKind.Witness<F,String>,User> cannot be converted to
    Kind<EitherTKind.Witness<F,DomainError>,User>
```

**The trigger:**

```java
var eitherTMonad =
    Instances.eitherT(futureMonad);

// lookupUser returns EitherT<F, String, User>: error type is String, not DomainError
EitherT<CompletableFutureKind.Witness, String, User> lookupUser(String id) { ... }

For.from(eitherTMonad, validatedET)
    .from(id -> lookupUser(id));   // String vs DomainError mismatch
```

Every step in an `EitherT` comprehension shares the same error type `L`. Mixing a step that fails with `String` and one that fails with `DomainError` will not compile.

**The fix:** unify the error type, either by changing the function or by mapping at the boundary.

```java
// Option 1: change lookupUser to use DomainError
EitherT<CompletableFutureKind.Witness, DomainError, User> lookupUser(String id) { ... }

// Option 2: lift the foreign error type at the call site
EitherT<CompletableFutureKind.Witness, DomainError, User> liftLookup(String id) {
    var raw = lookupUser(id);                                            // EitherT<F, String, User>
    return EitherT.fromKind(
        futureMonad.map(
            either -> either.mapLeft(DomainError.UserLookup::new),       // String -> DomainError
            raw.value()));
}
```

Option 1 is preferred for new code. Option 2 is useful when integrating with code you cannot change.

---

## 3. "Method `mapT` cannot be applied" on `StateT`

**The error:**

```
error: method mapT in class StateT<S,F,A> cannot be applied to given types;
    stateT.mapT(f);
           ^
  required: Monad<G>, Function<Kind<F,StateTuple<S,A>>,Kind<G,StateTuple<S,A>>>
  found:    Function<Kind<F,StateTuple<S,A>>,Kind<G,StateTuple<S,A>>>
```

**The trigger:**

```java
StateT<Counter, IdKind.Witness, Integer> idState = ...;

var taskState = idState.mapT(idKind -> ioToTask.apply(idKind));   // missing first argument
```

Most transformers' `mapT` takes a single function. `StateT.mapT` is the exception: because the state-threading function `S -> Kind<G, (S, A)>` must close over the new monad, the call requires an explicit `Monad<G>` instance as the first argument.

**The fix:** pass the target monad alongside the function.

```java
var taskMonad = TaskMonad.INSTANCE;
var taskState = idState.mapT(taskMonad, idKind -> ioToTask.apply(idKind));
```

`EitherT.mapT`, `OptionalT.mapT`, `MaybeT.mapT`, `ReaderT.mapT`, and `WriterT.mapT` do not take this extra argument. Only `StateT` does.

---

## 4. "Cannot infer type-variable `L` for `EitherT.fromEither`"

**The error:**

```
error: method fromEither in class EitherT<F,L,R> cannot be applied to given types;
    EitherT.fromEither(futureMonad, Either.right(value));
           ^
  reason: cannot infer type-variable(s) F, L, R
```

**The trigger:**

```java
var eitherTMonad =
    Instances.eitherT(futureMonad);

return For.from(eitherTMonad,
        EitherT.fromEither(futureMonad, Either.right(validated)));   // L cannot be inferred
```

`Either.right(value)` has no `Left` value to infer the error type from, so the compiler cannot determine `L` for the resulting `EitherT`. The same problem appears with `Path.right` in the Effect Path API (see [Effect Compiler Errors](../effect/compiler_errors.md#1-cannot-infer-type-arguments-for-pathright)).

**The fix:** add an explicit type witness on the `Either`.

```java
return For.from(eitherTMonad,
        EitherT.fromEither(futureMonad, Either.<DomainError, Validated>right(validated)));
```

Inference works when the surrounding context constrains `L`, for example when assigning to a variable with an explicit type:

```java
EitherT<CompletableFutureKind.Witness, DomainError, Validated> step =
    EitherT.fromEither(futureMonad, Either.right(validated));     // L inferred from variable
```

When in doubt, add the witness. It costs nothing at runtime.

---

## 5. "Cannot find symbol `.value()`" on a `Kind`

**The error:**

```
error: cannot find symbol
    var future = workflow.value();
                         ^
  symbol:   method value()
  location: variable workflow of type Kind<EitherTKind.Witness<...>, Result>
```

**The trigger:**

```java
Kind<EitherTKind.Witness<CompletableFutureKind.Witness, DomainError>, Result> workflow =
    eitherTMonad.flatMap(...);

var future = workflow.value();   // Kind has no value() method
```

`.value()` is defined on the concrete `EitherT<F, L, R>` (and equivalently on `OptionalT`, `MaybeT`, `ReaderT`, `StateT`, `WriterT`). It is not part of the `Kind` interface. When you have a `Kind` value, you must narrow it back to the concrete transformer first.

**The fix:** call the matching narrow helper before extracting the underlying value.

```java
import static org.higherkindedj.hkt.either_t.EitherTKindHelper.EITHER_T;

var future = EITHER_T.narrow(workflow).value();
```

This boundary conversion is unavoidable when a method is typed in `Kind` but you need a concrete operation. If you control the call site, declaring the variable as the concrete `EitherT<...>` removes the need:

```java
EitherT<CompletableFutureKind.Witness, DomainError, Result> workflow =
    EITHER_T.narrow(eitherTMonad.flatMap(...));    // narrow once at the assignment

var future = workflow.value();
```

---

## 6. "Method `For.from` is not applicable" with the wrong monad

**The error:**

```
error: method from in class For cannot be applied to given types;
    For.from(eitherTMonad, OptionalT.fromKind(future));
        ^
  required: Monad<M>, Kind<M,A>
  found:    EitherTMonad<...>, OptionalT<...>
  reason:   inference variable M has incompatible bounds
```

**The trigger:**

```java
var eitherTMonad =
    Instances.eitherT(futureMonad);

For.from(eitherTMonad, OptionalT.fromKind(future))    // mismatched witness types
    .from(...)
    .yield(...);
```

`For.from(monad, source)` requires the source's witness type to match the monad's witness type. Passing an `EitherTMonad` and an `OptionalT` mixes two different transformer stacks.

**The fix:** make sure the monad you pass to `For.from` matches the witness of every step.

```java
// Either use EitherT throughout:
For.from(eitherTMonad, EitherT.fromKind(fetchEither(...)))
    .from(...)
    .yield(...);

// Or build the matching OptionalTMonad and use OptionalT throughout:
var optionalTMonad = Instances.optionalT(futureMonad);
For.from(optionalTMonad, OptionalT.fromKind(future))
    .from(...)
    .yield(...);
```

If you genuinely need to combine two effect layers (typed errors *and* absence in the same workflow), you are in stacking territory. See [Tutorial 03: Stacking Transformers](../tutorials/transformers/transformers_journey.md) and [Stack Archetypes](archetypes.md).

---

## Quick Diagnostic Table

| Symptom | Likely cause | Fix |
|---------|-------------|-----|
| "constructor cannot be applied", expects `Monad<F>` | Forgot to pass the outer monad | `Instances.eitherT(futureMonad)` |
| "incompatible types" with two `EitherTKind.Witness` shapes | Different error types `L` in the chain | Unify the error type or `mapLeft` at the boundary |
| `mapT` "cannot be applied" on `StateT` | Missing `Monad<G>` first argument | `stateT.mapT(targetMonad, f)` |
| "cannot infer type-variable" on `Either.right` / `EitherT.fromEither` | No `Left` value to infer `L` from | Add `Either.<E, A>right(...)` |
| "cannot find symbol `.value()`" on a `Kind` | Need to narrow first | `EITHER_T.narrow(kind).value()` |
| "For.from not applicable" with two stack types | Mixed transformer stacks | Use one consistent monad and lift accordingly |

---

~~~admonish tip title="See Also"
- [Path or Transformer?](when_to_drop_to_transformers.md), the decision page that may save you from this chapter entirely
- [Transformers at a Glance](transformers_at_a_glance.md), reference card for every transformer
- [Effect Path Common Compiler Errors](../effect/compiler_errors.md), the equivalent for the Path API
- [Migration Cookbook](migration_cookbook.md), side-by-side translations between styles
~~~

---

**Previous:** [Combining Capabilities](mtl_combining.md)
**Next:** [Capstone: A Multi-Capability Workflow](transformer_capstone.md)

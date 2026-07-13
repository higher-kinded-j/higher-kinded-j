# Obtaining Instances: The `Instances` Facade

Every type class in Higher-Kinded-J has a canonical instance for each data type. Historically, reaching that instance required knowing three independent things per type: the **instance class name**, its **package**, and **which access idiom** that class uses. The idiom was not uniform:

```java
// The old idioms — four spellings of one concept
Semigroup<String>            semi  = Semigroups.string();   // for Validated error accumulation
Monad<OptionalKind.Witness>  outer = OptionalMonad.INSTANCE; // outer monad for the transformer

Monad<MaybeKind.Witness>             m1 = MaybeMonad.INSTANCE;            // static field
Monad<EitherKind.Witness<String>>    m2 = EitherMonad.instance();         // generic static method
MonadError<ValidatedKind.Witness<String>, String> v =
    ValidatedMonad.instance(semi);                                        // factory + argument
MonadError<EitherTKind.Witness<OptionalKind.Witness, String>, String> et =
    new EitherTMonad<>(outer);                                            // constructor + argument
```

The `org.higherkindedj.hkt.instances.Instances` facade collapses all of these into **one predictable shape**, `Instances.x(...)`, discovered by capability through IDE autocomplete.

~~~admonish info title="What You'll Learn"
- How to obtain any built-in `Functor` / `Applicative` / `Monad` / `MonadError` through a single entry point
- How the typed `Witnesses` tokens preserve type inference for phantom-typed types
- Why argument-carrying instances put their dependency in the method signature
- That the facade is compile-time safe (no registry, no `Optional`, no runtime lookup)
~~~

---

## Zero-argument lookups

For the stateless-singleton and phantom-typed families, pass a typed token from `Witnesses`:

```java
import static org.higherkindedj.hkt.instances.Witnesses.*;
import org.higherkindedj.hkt.instances.Instances;

Monad<MaybeKind.Witness>       monad       = Instances.monad(maybe());
Applicative<MaybeKind.Witness> applicative = Instances.applicative(maybe());
Functor<MaybeKind.Witness>     functor     = Instances.functor(maybe());
```

Because the canonical instance implements the whole `Functor → Applicative → Monad` chain in **one object**, a single token yields all three levels by Java subtyping; there is no extra lookup data.

For canonical instances that also implement a richer capability, three further lookups are available (`monadError`, `monadZero` and `alternative`):

```java
MonadError<MaybeKind.Witness, Unit> me  = Instances.monadError(maybe());   // E inferred
MonadZero<ListKind.Witness>         mz  = Instances.monadZero(list());
Alternative<OptionalKind.Witness>   alt = Instances.alternative(optional());
```

Unlike `monad`/`applicative`/`functor`, these three are **not total**: they are only valid for instances that actually implement the requested capability (e.g. `monadError` for `Maybe`, `Optional`, `Try`, `Either`; `monadZero`/`alternative` for `Maybe`, `Optional`, `List`, `Stream`). Asking for a capability the canonical instance does not have is a programming error and fails fast with a `ClassCastException`, exactly as calling a method that does not exist would. The error type `E` of `monadError` is inferred from the assignment target, the same way the phantom type of `Either` is.

### Type inference for phantom-typed types

The tokens are **generic**, not `Class`-keyed. This matters: Java has no generic class literals, so a `Class`-keyed facade would erase the phantom type of `Either`, `Reader`, `State`, etc. The token form keeps inference working exactly as `EitherMonad.<L>instance()` does today:

```java
// <DomainError> flows from the assignment target
Monad<EitherKind.Witness<DomainError>> m = Instances.monad(either());
```

### Available tokens

| Family | Tokens |
|--------|--------|
| Stateless singletons | `maybe()`, `io()`, `list()`, `optional()`, `try_()`, `vtask()`, `vstream()`, `lazy()`, `stream()`, `completableFuture()`, `trampoline()` |
| Phantom-typed nullary | `id()`, `either()`, `reader()`, `context()`, `state()` |

> `try_()` is named with a trailing underscore because `try` is a Java reserved word.

---

## Argument-carrying instances

`Validated`, `Writer` and the monad transformers have a **structurally required dependency**: a `Semigroup`, a `Monoid`, or an outer `Monad`. There is no canonical default for these, so the facade cannot hide the dependency; instead it puts it **in the method signature**, where the compiler enforces it and the IDE documents it:

```java
MonadError<ValidatedKind.Witness<E>, E>  v  = Instances.validated(Semigroups.list());
Monad<WriterKind.Witness<String>>        w  = Instances.writer(Monoids.string());
MonadError<EitherTKind.Witness<F, L>, L> et = Instances.eitherT(Instances.monad(optional()));
MonadError<MaybeTKind.Witness<F>, Unit>  mt = Instances.maybeT(Instances.monad(optional()));
MonadError<OptionalTKind.Witness<F>, Unit> ot = Instances.optionalT(Instances.monad(maybe()));
Monad<ReaderTKind.Witness<F, R>>         rt = Instances.readerT(Instances.monad(optional()));
Monad<StateTKind.Witness<S, F>>          st = Instances.stateT(Instances.monad(optional()));
MonadWriter<WriterTKind.Witness<F, W>, W> wt =
    Instances.writerT(Instances.monad(optional()), Monoids.string());
```

This is the clearest pedagogical win: previously a user did not know `ValidatedMonad` needed a `Semigroup` until they found the class and read its only constructor. Now the requirement is visible at the call site.

---

## Design constraints

~~~admonish note title="Compile-time safety is preserved"
The facade is a **thin static re-export** of the existing accessors. It is intentionally:

- **not** Spring-wired (consistent with the documented `HkjAutoConfiguration` static-not-beans decision), and
- **not** backed by `PathRegistry`/`ServiceLoader`.

Every method resolves at compile time and never returns `Optional` or throws for a missing built-in instance. A `ServiceLoader`-backed lookup would trade this guarantee for a runtime lookup that can fail under native-image service stripping.
~~~

The old accessors (`MaybeMonad.INSTANCE`, `EitherMonad.instance()`, …) remain for binary compatibility. New code and examples should prefer the facade so there is **one idiom**, not two.

---

## Scope

| Type class | Coverage |
|------------|----------|
| `Functor` / `Applicative` / `Monad` | Covered (total, free via subtyping) |
| `MonadError` / `MonadZero` / `Alternative` | Covered (partial: only for instances that implement the capability) |
| Argument-carrying re-exports (`validated`, `writer`, `eitherT`, `maybeT`, `optionalT`, `readerT`, `stateT`, `writerT`) | Covered |
| `Traverse` / `Selective` / `Foldable` | Out of scope (separate instance classes, tracked as a follow-up) |
| `Free` / `FreeApplicative` | Out of scope (special constructions: built and interpreted, not retrieved as a ready instance) |
| `Const` | Out of scope (`Applicative`-only; a token here always carries a `Monad`) |
| `MonadReader` / `MonadState` / `MonadWriter` (MTL) | Out of scope (separate capability surface) |
| `Semigroup` / `Monoid` | Excluded by design (keyed by a concrete value type, not an HKT witness) |
| `Bifunctor` / `Profunctor` | Out of scope (binary arity, separate surface) |

---

## See Also

- [Functor](functor.md), [Applicative](applicative.md), [Monad](monad.md), [MonadError](monad_error.md)
- [Semigroup and Monoid](semigroup_and_monoid.md)
- [Monad Transformers](../transformers/transformers.md)

---

**Previous:** [Functional API](functional_api.md)
**Next:** [Functor](functor.md)

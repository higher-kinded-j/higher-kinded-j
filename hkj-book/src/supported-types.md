# Supported Types in Higher-Kinded-J

![monads_everywhere.webp](./images/monads_everywhere.webp)

Higher-Kinded-J currently provides Higher-Kinded Type wrappers (`Kind<F, A>`) and corresponding type class instances (`Functor`, `Applicative`, `Monad`, `MonadError`) for the following Java types and custom types.

![supported_types.svg](./images/puml/supported_types.svg)

---

### 1. `java.util.List<A>`

* **Kind Interface:** [`ListKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/list/ListKind.java)
* **Witness Type `F`:** `ListKind<?>`
* **Helper:** `ListKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances:**
  * `ListFunctor` (`Functor`)
  * [`ListMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/list/ListMonad.java) (`Monad`, `Applicative`)
* **Notes:** Standard list monad behavior. `map` applies a function to each element. `flatMap` applies a function returning a `ListKind` to each element and concatenates the resulting lists. `ap` applies functions in a list to values in another list (Cartesian product). `of(a)` creates `List(a)`.

---

### 2. `java.util.Optional<A>`

* **Kind Interface:** [`OptionalKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/optional/OptionalKind.java)
* **Witness Type `F`:** `OptionalKind<?>`
* **Helper:** `OptionalKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances:**
  * `OptionalFunctor` (`Functor`)
  * [`OptionalMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/optional/OptionalMonad.java) (`Monad`, `Applicative`, `MonadError<..., Void>`)
* **Notes:** Models optionality. Implements `MonadError` where the error type `E` is `Void` (represented by `null`), corresponding to the `Optional.empty()` state. `raiseError(null)` creates an empty `OptionalKind`. `of(null)` also results in an empty `OptionalKind`. `flatMap` chains operations only if the `Optional` is present.

---

### 3. `Maybe<A>`

* **Definition:** A custom `Optional`-like type ([`Maybe`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/Maybe.java)) implemented as a sealed interface with `Just` and `Nothing` implementations. `Just` enforces non-null values.
* **Kind Interface:** [`MaybeKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/MaybeKind.java)
* **Witness Type `F`:** `MaybeKind<?>`
* **Helper:** `MaybeKindHelper` (`wrap`, `unwrap`, `just`, `nothing`)
* **Type Class Instances:**
  * `MaybeFunctor` (`Functor`)
  * [`MaybeMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/MaybeMonad.java) (`Monad`, `Applicative`, `MonadError<..., Void>`)
* **Notes:** Similar to `Optional`, but `Just` cannot hold `null`. Implements `MonadError` where the error type `E` is `Void` (represented by `null`), corresponding to the `Nothing` state. `raiseError(null)` creates a `Nothing` `MaybeKind`. `of(null)` results in `Nothing`.

---

### 4. `Either<L, R>`

* **Definition:** A custom type ([`Either`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/Either.java)) representing a value of one of two types, `Left` (typically error) or `Right` (typically success). Implemented as a sealed interface with `Left` and `Right` record implementations.
* **Kind Interface:** [`EitherKind<L, R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/EitherKind.java)
* **Witness Type `F`:** `EitherKind<L, ?>` (Note: `L` is fixed for a given type class instance)
* **Helper:** `EitherKindHelper` (`wrap`, `unwrap`, `left`, `right`)
* **Type Class Instances:**
  * `EitherFunctor<L>` (`Functor`)
  * [`EitherMonad<L>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/EitherMonad.java) (`Monad`, `Applicative`, `MonadError<..., L>`)
* **Notes:** Represents computations that can fail with a *typed* error. Instances are right-biased (`map`/`flatMap` operate on `Right`). Implements `MonadError` where the error type `E` is the `Left` type `L`. `raiseError(l)` creates a `Left(l)` `EitherKind`. `of(r)` creates `Right(r)`. Useful for handling domain-specific errors explicitly.
* **Usage:** [How to use the Either Monad](./either_monad.md)

---

### 5. `Try<T>`

* **Definition:** A custom type ([`Try`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/Try.java)) representing a computation that might succeed (`Success<T>`) or fail with a `Throwable` (`Failure<T>`). Implemented as a sealed interface with `Success` and `Failure` record implementations.
* **Kind Interface:** [`TryKind<T>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/TryKind.java)
* **Witness Type `F`:** `TryKind<?>`
* **Helper:** `TryKindHelper` (`wrap`, `unwrap`, `success`, `failure`, `tryOf`)
* **Type Class Instances:**
  * `TryFunctor` (`Functor`)
  * `TryApplicative` (`Applicative`)
  * `TryMonad` (`Monad`)
  * [`TryMonadError`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/TryMonadError.java) (`MonadError<..., Throwable>`)
* **Notes:** Useful for wrapping computations that might throw arbitrary exceptions. Implements `MonadError` where the error type `E` is `Throwable`. `raiseError(t)` creates a `Failure(t)` `TryKind`. `of(v)` creates `Success(v)`. `flatMap` propagates failures or exceptions thrown during mapping.
* **Usage:** [How to use the Try Monad](./try_monad.md)

---

### 6. `java.util.concurrent.CompletableFuture<T>`

* **Kind Interface:** [`CompletableFutureKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/future/CompletableFutureKind.java)
* **Witness Type `F`:** `CompletableFutureKind<?>`
* **Helper:** `CompletableFutureKindHelper` (`wrap`, `unwrap`, `join`)
* **Type Class Instances:**
  * `CompletableFutureFunctor` (`Functor`)
  * `CompletableFutureApplicative` (`Applicative`)
  * `CompletableFutureMonad` (`Monad`)
  * [`CompletableFutureMonadError`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/future/CompletableFutureMonadError.java) (`MonadError<..., Throwable>`)
* **Notes:** Allows treating asynchronous computations monadically. `map` and `flatMap` correspond to `thenApply` and `thenCompose`. Implements `MonadError` where the error type `E` is `Throwable`, representing the exception that caused the future to fail. `raiseError(t)` creates a `failedFuture(t)`. `of(v)` creates a `completedFuture(v)`. `ap` uses `thenCombine`.
* **Usage:** [How to use the CompletableFuture Monad](./cf_monad.md)

---

### 7. `IO<A>`

* **Definition:** A custom type ([`IO`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IO.java)) representing a potentially side-effecting computation that produces a value `A`. Evaluation is deferred until `unsafeRunSync()` is called.
* **Kind Interface:** [`IOKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IOKind.java)
* **Witness Type `F`:** `IOKind<?>`
* **Helper:** `IOKindHelper` (`wrap`, `unwrap`, `delay`, `unsafeRunSync`)
* **Type Class Instances:**
  * `IOFunctor` (`Functor`)
  * `IOApplicative` (`Applicative`)
  * [`IOMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IOMonad.java) (`Monad`)
* **Notes:** Encapsulates side effects (console I/O, file access, network calls, random numbers, system time). `map` and `flatMap` sequence effects lazily. `of(a)` creates an `IO` that returns `a` without side effects. `IOKindHelper.delay(() -> ...)` is the primary way to capture a side-effecting computation. *Does not* implement `MonadError` by default, as exceptions during `unsafeRunSync` are typically unhandled unless explicitly caught within the `IO`'s definition.
* **Usage:** [How to use the IO Monad](./io_monad.md)
---

### 8. `Lazy<A>`

* **Definition:** A custom type ([`Lazy`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/Lazy.java)) representing a value whose computation is deferred until explicitly requested via `force()` and then memoised (cached).
* **Kind Interface:** [`LazyKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/LazyKind.java)
* **Witness Type `F`:** `LazyKind<?>`
* **Helper:** `LazyKindHelper` (`wrap`, `unwrap`, `defer`, `now`, `force`)
* **Type Class Instances:**
  * [`LazyMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/LazyMonad.java) (`Monad`, `Applicative`, `Functor`)
* **Notes:** Useful for expensive computations or values that should only be calculated if needed. `map` and `flatMap` preserve laziness. `of(a)` creates an already evaluated `Lazy` instance (`Lazy.now`). `LazyKindHelper.defer(() -> ...)` creates an unevaluated `Lazy`. Exceptions during computation are caught, memoised, and re-thrown by `force()`.
* **Usage:** [How to use the Lazy Monad](./lazy_monad.md)

---

### 9. `Reader<R, A>`

* **Definition:** A custom type ([`Reader`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/Reader.java)) representing a computation that depends on reading a value from a shared, read-only environment `R` to produce a value `A`. Essentially wraps `Function<R, A>`.
* **Kind Interface:** [`ReaderKind<R, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/ReaderKind.java)
* **Witness Type `F`:** `ReaderKind<R, ?>` (Note: `R` is fixed for a given type class instance)
* **Helper:** `ReaderKindHelper` (`wrap`, `unwrap`, `reader`, `ask`, `constant`, `runReader`)
* **Type Class Instances:**
  * `ReaderFunctor<R>` (`Functor`)
  * `ReaderApplicative<R>` (`Applicative`)
  * [`ReaderMonad<R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/ReaderMonad.java) (`Monad`)
* **Notes:** Facilitates dependency injection. `map` and `flatMap` compose functions that operate within the context of the environment `R`. `ask()` provides access to the environment itself. `of(a)` creates a `Reader` that ignores the environment and returns `a`.
* **Usage:** [How to use the Reader Monad](./reader_monad.md)
---

### 10. `State<S, A>`

* **Definition:** A custom type ([`State`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/State.java)) representing a stateful computation that takes an initial state `S`, produces a value `A`, and returns a new state `S`. Wraps `Function<S, StateTuple<S, A>>`.
* **Kind Interface:** [`StateKind<S,A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/StateKind.java)
* **Witness Type `F`:** `StateKind<S, ?>` (Note: `S` is fixed for a given type class instance)
* **Helper:** `StateKindHelper` (`wrap`, `unwrap`, `pure`, `get`, `set`, `modify`, `inspect`, `runState`, `evalState`, `execState`)
* **Type Class Instances:**
  * `StateFunctor<S>` (`Functor`)
  * `StateApplicative<S>` (`Applicative`)
  * [`StateMonad<S>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/StateMonad.java) (`Monad`)
* **Notes:** Models computations where state needs to be threaded through a sequence of operations. `flatMap` sequences computations, passing the resulting state from one step to the next. `get()` retrieves the current state, `set(s)` updates it, `modify(f)` updates it using a function. `of(a)` (`pure`) returns `a` without changing state.
* **Usage:** [How to use the State Monad](./state_monad.md)
---

### 11. `Writer<W, A>`

* **Definition:** A custom type ([`Writer`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/Writer.java)) representing a computation that produces a value `A` while accumulating a log or output `W`. Requires a `Monoid<W>` for combining logs.
* **Kind Interface:** [`WriterKind<W, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/WriterKind.java)
* **Witness Type `F`:** `WriterKind<W, ?>` (Note: `W` and its `Monoid` are fixed for a given type class instance)
* **Helper:** `WriterKindHelper` (`wrap`, `unwrap`, `value`, `tell`, `runWriter`, `run`, `exec`)
* **Type Class Instances:**
  * `WriterFunctor<W>` (`Functor`)
  * `WriterApplicative<W>` (`Applicative`)
  * [`WriterMonad<W>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/WriterMonad.java) (`Monad`)
* **Notes:** Useful for logging or accumulating results alongside the main computation. `flatMap` sequences computations and combines their logs using the provided `Monoid`. `tell(w)` logs a value `w` without producing a main result. `of(a)` (`value`) produces `a` with an empty log.
* **Usage:** [How to use the Writer Monad](./writer_monad.md)
---

### 12. `EitherT<F, L, R>` (Monad Transformer)

* **Definition:** A monad transformer ([`EitherT`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/either_t/EitherT.java)) that combines an outer monad `F` with an inner `Either<L, R>`. Implemented as a record wrapping `Kind<F, Either<L, R>>`.
* **Kind Interface:** [`EitherTKind<F, L, R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/either_t/EitherTKind.java)
* **Witness Type `G`:** `EitherTKind<F, L, ?>` (where `F` and `L` are fixed for a given type class instance)
* **Helper:** `EitherTKindHelper` (`wrap`, `unwrap`). Instances are primarily created via `EitherT` static factories (`fromKind`, `right`, `left`, `fromEither`, `liftF`).
* **Type Class Instances:**
  * [`EitherTMonad<F, L>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/either_t/EitherTMonad.java) (`MonadError<EitherTKind<F, L, ?>, L>`)
* **Notes:** Simplifies working with nested structures like `F<Either<L, R>>`. Requires a `Monad<F>` instance for the outer monad `F` passed to its constructor. Implements `MonadError` for the *inner* `Either`'s `Left` type `L`. See the [Order Processing Example Walkthrough](./order-walkthrough.md) for practical usage with `CompletableFuture` as `F`.
* **Usage:** [How to use the EitherT Monad Transformer](./eithert_transformer.md)

---
![transformers.svg](./images/puml/transformers.svg)
---

### 13. `MaybeT<F, A>` (Monad Transformer)

* **Definition:** A monad transformer ([`MaybeT`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/maybe_t/MaybeT.java)) that combines an outer monad `F` with an inner `Maybe<A>`. Implemented as a record wrapping `Kind<F, Maybe<A>>`.
* **Kind Interface:** [`MaybeTKind<F, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/maybe_t/MaybeTKind.java)
* **Witness Type `G`:** `MaybeTKind<F, ?>` (where `F` is fixed for a given type class instance)
* **Helper:** `MaybeTKindHelper` (`wrap`, `unwrap`). Instances are primarily created via `MaybeT` static factories (`fromKind`, `just`, `nothing`, `fromMaybe`, `liftF`).
* **Type Class Instances:**
  * [`MaybeTMonad<F>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/maybe_t/MaybeTMonad.java) (`MonadError<MaybeTKind<F, ?>, Void>`)
* **Notes:** Simplifies working with nested structures like `F<Maybe<A>>`. Requires a `Monad<F>` instance for the outer monad `F`. Implements `MonadError` where the error type is `Void`, corresponding to the `Nothing` state from the inner `Maybe`.
* **Usage:** [How to use the MaybeT Monad Transformer](./maybet_transformer.md)

---

### 14. `OptionalT<F, A>` (Monad Transformer)

* **Definition:** A monad transformer ([`OptionalT`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/optional_t/OptionalT.java)) that combines an outer monad `F` with an inner `java.util.Optional<A>`. Implemented as a record wrapping `Kind<F, Optional<A>>`.
* **Kind Interface:** [`OptionalTKind<F, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/optional_t/OptionalTKind.java)
* **Witness Type `G`:** `OptionalTKind<F, ?>` (where `F` is fixed for a given type class instance)
* **Helper:** `OptionalTKindHelper` (`wrap`, `unwrap`). Instances are primarily created via `OptionalT` static factories (`fromKind`, `some`, `none`, `fromOptional`, `liftF`).
* **Type Class Instances:**
  * [`OptionalTMonad<F>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/optional_t/OptionalTMonad.java) (`MonadError<OptionalTKind<F, ?>, Void>`)
* **Notes:** Simplifies working with nested structures like `F<Optional<A>>`. Requires a `Monad<F>` instance for the outer monad `F`. Implements `MonadError` where the error type is `Void`, corresponding to the `Optional.empty()` state from the inner `Optional`.
* **Usage:** [How to use the OptionalT Monad Transformer](./optionalt_transformer.md)

---

### 15. `ReaderT<F, R, A>` (Monad Transformer)

* **Definition:** A monad transformer ([`ReaderT`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/reader_t/ReaderT.java)) that combines an outer monad `F` with an inner `Reader<R, A>`-like behavior (dependency on environment `R`). Implemented as a record wrapping a function `R -> Kind<F, A>`.
* **Kind Interface:** [`ReaderTKind<F, R, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/reader_t/ReaderTKind.java)
* **Witness Type `G`:** `ReaderTKind<F, R, ?>` (where `F` and `R` are fixed for a given type class instance)
* **Helper:** `ReaderTKindHelper` (`wrap`, `unwrap`). Instances are primarily created via `ReaderT` static factories (`of`, `lift`, `reader`, `ask`).
* **Type Class Instances:**
  * [`ReaderTMonad<F, R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trans/reader_t/ReaderTMonad.java) (`Monad<ReaderTKind<F, R, ?>>`)
* **Notes:** Simplifies managing computations that depend on a read-only environment `R` while also involving other monadic effects from `F`. Requires a `Monad<F>` instance for the outer monad. The `run()` method of `ReaderT` takes `R` and returns `Kind<F, A>`.
* **Usage:** [How to use the ReaderT Monad Transformer](./readert_transformer.md)


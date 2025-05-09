# Supported Types in Higher-Kinded-J

![monads_everywhere.webp](./images/monads_everywhere.webp)

Higher-Kinded-J currently provides Higher-Kinded Type wrappers (`Kind<F, A>`) and corresponding type class instances (`Functor`, `Applicative`, `Monad`, `MonadError`) for the following Java types and custom types.

![supported_types.svg](./images/puml/supported_types.svg)

---

### 1. `Id<A>` (Identity)

* **Definition**: A simple type ([`Id`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/id/Id.java)) that wraps a value without adding any additional computational context or effect. It's the most basic monad. Implemented as a Java `record`.
* **Kind Interface**: `Id<A>` (it directly implements `Kind<Id.Witness, A>`)
* **Witness Type `F`**: `Id.Witness`
* **Helper**: [`IdKindHelper`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/id/IdKindHelper.java) (`wrap`, `narrow`, `unwrap`)
* **Type Class Instances**:
  * [`IdentityMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/id/IdentityMonad.java) (`Monad`, `Applicative`, `Functor`)
* **Notes**: `of(a)` creates `Id(a)`. `map` and `flatMap` operate on the wrapped value directly. It's primarily useful as a base case for monad transformers (e.g., `StateT<S, Id.Witness, A>` is equivalent to `State<S, A>`) and for generic monadic programming where no extra effects are desired.
* **Usage**: [How to use the Identity Monad](./id_monad.md)

---

### 2. `java.util.List<A>`

* **Kind Interface**: [`ListKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/list/ListKind.java)
* **Witness Type `F`**: `ListKind.Witness` (Note: The previous doc had `ListKind<?>`, corrected to `ListKind.Witness` for consistency with other witness patterns if `ListKind` itself is the HKT wrapper. If `ListKind` is just the `Kind` interface, then `ListMonad.Witness` or similar would be used. Assuming `ListKind.Witness` or a dedicated witness for `List`.)
* **Helper**: `ListKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances**:
  * `ListFunctor` (`Functor`)
  * [`ListMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/list/ListMonad.java) (`Monad`, `Applicative`)
* **Notes**: Standard list monad behavior. `map` applies a function to each element. `flatMap` applies a function returning a `ListKind` to each element and concatenates the resulting lists. `ap` applies functions in a list to values in another list (Cartesian product). `of(a)` creates a singleton list `List(a)`; `of(null)` creates an empty list.
* **Usage**: [How to use the List Monad](./list_monad.md)

---

### 3. `java.util.Optional<A>`

* **Kind Interface**: [`OptionalKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/optional/OptionalKind.java)
* **Witness Type `F`**: `OptionalKind.Witness`
* **Helper**: `OptionalKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances**:
  * `OptionalFunctor` (`Functor`)
  * [`OptionalMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/optional/OptionalMonad.java) (`Monad`, `Applicative`, `MonadError<..., Void>`)
* **Notes**: Models optionality for Java's `Optional`. Implements `MonadError` where the error type `E` is `Void` (represented by `null`), corresponding to the `Optional.empty()` state. `raiseError(null)` creates an empty `OptionalKind`. `of(value)` uses `Optional.ofNullable(value)`, so `of(null)` also results in an empty `OptionalKind`. `flatMap` chains operations only if the `Optional` is present.
* **Usage**: [How to use the Optional Monad](./optional_monad.md)

---

### 4. `Maybe<A>`

* **Definition**: A custom `Optional`-like type ([`Maybe`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/Maybe.java)) implemented as a sealed interface with `Just` and `Nothing` implementations. `Just<T>` enforces that its contained value is **non-null**.
* **Kind Interface**: [`MaybeKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/MaybeKind.java)
* **Witness Type `F`**: `MaybeKind.Witness`
* **Helper**: `MaybeKindHelper` (`wrap`, `unwrap`, `just`, `nothing`)
* **Type Class Instances**:
  * `MaybeFunctor` (`Functor`)
  * [`MaybeMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/maybe/MaybeMonad.java) (`Monad`, `Applicative`, `MonadError<..., Void>`)
* **Notes**: Similar to `Optional`, but `Just` cannot hold `null` (enforced by `Maybe.just()`). `Maybe.fromNullable(value)` or `MaybeMonad.of(value)` should be used for potentially null values, which will result in `Nothing` if the input is null. Implements `MonadError` where the error type `E` is `Void` (represented by `null`), corresponding to the `Nothing` state. `raiseError(null)` creates a `Nothing` `MaybeKind`.
* **Usage**: [How to use the Maybe Monad](./maybe_monad.md)

---

### 5. `Either<L, R>`

* **Definition**: A custom type ([`Either`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/Either.java)) representing a value of one of two types, `Left` (typically error) or `Right` (typically success). Implemented as a sealed interface with `Left` and `Right` record implementations.
* **Kind Interface**: [`EitherKind<L, R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/EitherKind.java)
* **Witness Type `F`**: `EitherKind.Witness<L>` (Note: `L` is fixed for a given type class instance)
* **Helper**: `EitherKindHelper` (`wrap`, `unwrap`, `left`, `right`)
* **Type Class Instances**:
  * `EitherFunctor<L>` (`Functor`)
  * [`EitherMonad<L>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/either/EitherMonad.java) (`Monad`, `Applicative`, `MonadError<..., L>`)
* **Notes**: Represents computations that can fail with a *typed* error. Instances are right-biased (`map`/`flatMap` operate on `Right`). Implements `MonadError` where the error type `E` is the `Left` type `L`. `raiseError(l)` creates a `Left(l)` `EitherKind`. `of(r)` creates `Right(r)`. Useful for handling domain-specific errors explicitly.
* **Usage**: [How to use the Either Monad](./either_monad.md)

---

### 6. `Try<T>`

* **Definition**: A custom type ([`Try`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/Try.java)) representing a computation that might succeed (`Success<T>`) or fail with a `Throwable` (`Failure<T>`). Implemented as a sealed interface with `Success` and `Failure` record implementations.
* **Kind Interface**: [`TryKind<T>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/TryKind.java)
* **Witness Type `F`**: `TryKind.Witness`
* **Helper**: `TryKindHelper` (`wrap`, `unwrap`, `success`, `failure`, `tryOf`)
* **Type Class Instances**:
  * `TryFunctor` (`Functor`)
  * `TryApplicative` (`Applicative`)
  * `TryMonad` (`Monad`)
  * [`TryMonadError`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/trymonad/TryMonadError.java) (`MonadError<..., Throwable>`)
* **Notes**: Useful for wrapping computations that might throw arbitrary exceptions. Implements `MonadError` where the error type `E` is `Throwable`. `raiseError(t)` creates a `Failure(t)` `TryKind`. `of(v)` creates `Success(v)`. `flatMap` propagates failures or exceptions thrown during mapping.
* **Usage**: [How to use the Try Monad](./try_monad.md)

---

### 7. `java.util.concurrent.CompletableFuture<T>`

* **Kind Interface**: [`CompletableFutureKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/future/CompletableFutureKind.java)
* **Witness Type `F`**: `CompletableFutureKind.Witness`
* **Helper**: `CompletableFutureKindHelper` (`wrap`, `unwrap`, `join`)
* **Type Class Instances**:
  * `CompletableFutureFunctor` (`Functor`)
  * `CompletableFutureApplicative` (`Applicative`)
  * `CompletableFutureMonad` (`Monad`)
  * [`CompletableFutureMonadError`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/future/CompletableFutureMonadError.java) (`MonadError<..., Throwable>`)
* **Notes**: Allows treating asynchronous computations monadically. `map` and `flatMap` correspond to `thenApply` and `thenCompose`. Implements `MonadError` where the error type `E` is `Throwable`, representing the exception that caused the future to fail. `raiseError(t)` creates a `failedFuture(t)`. `of(v)` creates a `completedFuture(v)`. `ap` uses `thenCombine`.
* **Usage**: [How to use the CompletableFuture Monad](./cf_monad.md)

---

### 8. `IO<A>`

* **Definition**: A custom type ([`IO`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IO.java)) representing a potentially side-effecting computation that produces a value `A`. Evaluation is deferred until `unsafeRunSync()` is called.
* **Kind Interface**: [`IOKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IOKind.java)
* **Witness Type `F`**: `IOKind.Witness`
* **Helper**: `IOKindHelper` (`wrap`, `unwrap`, `delay`, `unsafeRunSync`)
* **Type Class Instances**:
  * `IOFunctor` (`Functor`)
  * `IOApplicative` (`Applicative`)
  * [`IOMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/io/IOMonad.java) (`Monad`)
* **Notes**: Encapsulates side effects (console I/O, file access, network calls, random numbers, system time). `map` and `flatMap` sequence effects lazily. `of(a)` creates an `IO` that returns `a` without side effects. `IOKindHelper.delay(() -> ...)` is the primary way to capture a side-effecting computation. *Does not* implement `MonadError` by default, as exceptions during `unsafeRunSync` are typically unhandled unless explicitly caught within the `IO`'s definition.
* **Usage**: [How to use the IO Monad](./io_monad.md)

---

### 9. `Lazy<A>`

* **Definition**: A custom type ([`Lazy`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/Lazy.java)) representing a value whose computation is deferred until explicitly requested via `force()` and then memoised (cached).
* **Kind Interface**: [`LazyKind<A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/LazyKind.java)
* **Witness Type `F`**: `LazyKind.Witness`
* **Helper**: `LazyKindHelper` (`wrap`, `unwrap`, `defer`, `now`, `force`)
* **Type Class Instances**:
  * [`LazyMonad`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/lazy/LazyMonad.java) (`Monad`, `Applicative`, `Functor`)
* **Notes**: Useful for expensive computations or values that should only be calculated if needed. `map` and `flatMap` preserve laziness. `of(a)` creates an already evaluated `Lazy` instance (`Lazy.now`). `LazyKindHelper.defer(() -> ...)` creates an unevaluated `Lazy`. Exceptions during computation are caught, memoised, and re-thrown by `force()`.
* **Usage**: [How to use the Lazy Monad](./lazy_monad.md)

---

### 10. `Reader<R, A>`

* **Definition**: A custom type ([`Reader`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/Reader.java)) representing a computation that depends on reading a value from a shared, read-only environment `R` to produce a value `A`. Essentially wraps `Function<R, A>`.
* **Kind Interface**: [`ReaderKind<R, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/ReaderKind.java)
* **Witness Type `F`**: `ReaderKind.Witness<R>` (Note: `R` is fixed for a given type class instance)
* **Helper**: `ReaderKindHelper` (`wrap`, `unwrap`, `reader`, `ask`, `constant`, `runReader`)
* **Type Class Instances**:
  * `ReaderFunctor<R>` (`Functor`)
  * `ReaderApplicative<R>` (`Applicative`)
  * [`ReaderMonad<R>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/reader/ReaderMonad.java) (`Monad`)
* **Notes**: Facilitates dependency injection. `map` and `flatMap` compose functions that operate within the context of the environment `R`. `ask()` provides access to the environment itself. `of(a)` creates a `Reader` that ignores the environment and returns `a`.
* **Usage**: [How to use the Reader Monad](./reader_monad.md)

---

### 11. `State<S, A>`

* **Definition**: A custom type ([`State`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/State.java)) representing a stateful computation that takes an initial state `S`, produces a value `A`, and returns a new state `S`. Wraps `Function<S, StateTuple<S, A>>`.
* **Kind Interface**: [`StateKind<S,A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/StateKind.java)
* **Witness Type `F`**: `StateKind.Witness<S>` (Note: `S` is fixed for a given type class instance)
* **Helper**: `StateKindHelper` (`wrap`, `unwrap`, `pure`, `get`, `set`, `modify`, `inspect`, `runState`, `evalState`, `execState`)
* **Type Class Instances**:
  * `StateFunctor<S>` (`Functor`)
  * `StateApplicative<S>` (`Applicative`)
  * [`StateMonad<S>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/state/StateMonad.java) (`Monad`)
* **Notes**: Models computations where state needs to be threaded through a sequence of operations. `flatMap` sequences computations, passing the resulting state from one step to the next. `get()` retrieves the current state, `set(s)` updates it, `modify(f)` updates it using a function. `of(a)` (`pure`) returns `a` without changing state.
* **Usage**: [How to use the State Monad](./state_monad.md)

---

### 12. `Writer<W, A>`

* **Definition**: A custom type ([`Writer`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/Writer.java)) representing a computation that produces a value `A` while accumulating a log or output `W`. Requires a `Monoid<W>` for combining logs.
* **Kind Interface**: [`WriterKind<W, A>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/WriterKind.java)
* **Witness Type `F`**: `WriterKind.Witness<W>` (Note: `W` and its `Monoid` are fixed for a given type class instance)
* **Helper**: `WriterKindHelper` (`wrap`, `unwrap`, `value`, `tell`, `runWriter`, `run`, `exec`)
* **Type Class Instances**:
  * `WriterFunctor<W>` (`Functor`)
  * `WriterApplicative<W>` (`Applicative`)
  * [`WriterMonad<W>`](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/src/main/java/org/higherkindedj/hkt/writer/WriterMonad.java) (`Monad`)
* **Notes**: Useful for logging or accumulating results alongside the main computation. `flatMap` sequences computations and combines their logs using the provided `Monoid`. `tell(w)` logs a value `w` without producing a main result. `of(a)` (`value`) produces `a` with an empty log.
* **Usage**: [How to use the Writer Monad](./writer_monad.md)

---
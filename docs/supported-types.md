# Supported Types

This simulation currently provides Higher-Kinded Type wrappers and type class instances (`Functor`, `Applicative`, `Monad`, `MonadError`) for the following Java types and custom types:

---

### 1. `java.util.List<A>`

* **Kind Interface:**`ListKind<A>`
* **Witness Type `F`:**`ListKind<?>`
* **Helper:**`ListKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances:**
  * `ListFunctor` (`Functor`)
  * `ListMonad` (`Monad`)
* **Notes:** Standard list monad behavior (map applies to each element, flatMap concatenates results).

---

### 2. `java.util.Optional<A>`

* **Kind Interface:**`OptionalKind<A>`
* **Witness Type `F`:**`OptionalKind<?>`
* **Helper:**`OptionalKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances:**
  * `OptionalFunctor` (`Functor`)
  * `OptionalMonad` (`Monad`, `MonadError<..., Void>`)
* **Notes:** Implements `MonadError` where the error type `E` is `Void`, representing the `Optional.empty()` state. `raiseError(null)` creates an empty `OptionalKind`.

---

### 3. `Maybe<A>` (Custom Type)

* **Definition:** A custom `Optional`-like type implemented as a sealed interface (`Maybe.java` with `Just` and `Nothing` implementations). `Just` cannot hold `null`.
* **Kind Interface:**`MaybeKind<A>`
* **Witness Type `F`:**`MaybeKind<?>`
* **Helper:**`MaybeKindHelper` (`wrap`, `unwrap`, `just`, `nothing`)
* **Type Class Instances:**
  * `MaybeFunctor` (`Functor`)
  * `MaybeMonad` (`Monad`, `MonadError<..., Void>`)
* **Notes:** Implements `MonadError` where the error type `E` is `Void`, representing the `Nothing` state. `raiseError(null)` creates a `Nothing``MaybeKind`.

---

### 4. `Either<L, R>` (Custom Type)

* **Definition:** A custom type representing a value of one of two types, `Left` (typically error) or `Right` (typically success). Implemented as a sealed interface (`Either.java` with `Left` and `Right` record implementations).
* **Kind Interface:**`EitherKind<L, R>`
* **Witness Type `F`:**`EitherKind<L, ?>` (Note: `L` is fixed for a given type class instance)
* **Helper:**`EitherKindHelper` (`wrap`, `unwrap`)
* **Type Class Instances:**
  * `EitherFunctor<L>` (`Functor`)
  * `EitherMonad<L>` (`Monad`, `MonadError<..., L>`)
* **Notes:** Instances are right-biased (map/flatMap operate on `Right`). Implements `MonadError` where the error type `E` is the `Left` type `L`. `raiseError(l)` creates a `Left(l)``EitherKind`.

---

### 5. `Try<T>` (Custom Type)

* **Definition:** A custom type representing a computation that might succeed (`Success<T>`) or fail with a `Throwable` (`Failure<T>`). Implemented as a sealed interface (`Try.java` with `Success` and `Failure` record implementations).
* **Kind Interface:**`TryKind<T>`
* **Witness Type `F`:**`TryKind<?>`
* **Helper:**`TryKindHelper` (`wrap`, `unwrap`, `success`, `failure`, `tryOf`)
* **Type Class Instances:**
  * `TryFunctor` (`Functor`)
  * `TryApplicative` (`Applicative`)
  * `TryMonad` (`Monad`)
  * `TryMonadError` (`MonadError<..., Throwable>`)
* **Notes:** Implements `MonadError` where the error type `E` is `Throwable`. `raiseError(t)` creates a `Failure(t)``TryKind`.

---

### 6. `java.util.concurrent.CompletableFuture<T>`

* **Kind Interface:**`CompletableFutureKind<A>`
* **Witness Type `F`:**`CompletableFutureKind<?>`
* **Helper:**`CompletableFutureKindHelper` (`wrap`, `unwrap`, `join`)
* **Type Class Instances:**
  * `CompletableFutureFunctor` (`Functor`)
  * `CompletableFutureApplicative` (`Applicative`)
  * `CompletableFutureMonad` (`Monad`)
  * `CompletableFutureMonadError` (`MonadError<..., Throwable>`)
* **Notes:** Allows treating asynchronous computations monadically. Implements `MonadError` where the error type `E` is `Throwable`, representing the exception that caused the future to fail. `raiseError(t)` creates a `failedFuture(t)`.

---

### 7. `EitherT<F, L, R>` (Monad Transformer)

* **Definition:** A monad transformer that combines an outer monad `F` with an inner `Either<L, R>`. Implemented as a record wrapping `Kind<F, Either<L, R>>` (`EitherT.java`).
* **Kind Interface:**`EitherTKind<F, L, R>`
* **Witness Type `G`:**`EitherTKind<F, L, ?>` (where `F` and `L` are fixed for a given type class instance)
* **Helper:** No dedicated helper; use `EitherT.fromKind`, `EitherT.right`, `EitherT.left`, `EitherT.fromEither`, `EitherT.liftF` static factories and access the inner value via `eitherT.value()`.
* **Type Class Instances:**
  * `EitherTMonad<F, L>` (`MonadError<EitherTKind<F, L, ?>, L>`)
* **Notes:** Requires a `Monad<F>` instance for the outer monad `F` to be passed to its constructor. Implements `MonadError` for the *inner*`Either`'s `Left` type `L`. See the [[Order Example Walkthrough]] for practical usage.

# MaybeT - Combining Monadic Effects with Optionality

## `MaybeT` Monad Transformer.

![maybet_transformer.svg](images/puml/maybet_transformer.svg)

## `MaybeT<F, A>`: Combining Any Monad `F` with `Maybe<A>`

The `MaybeT` monad transformer allows you to combine the optionality of `Maybe<A>` (representing a value that might be `Just<A>` or `Nothing`) with another outer monad `F`. It transforms a computation that results in `Kind<F, Maybe<A>>` into a single monadic structure. This is useful for operations within an effectful context `F` (like `CompletableFutureKind` for async operations or `ListKind` for non-deterministic computations) that can also result in an absence of a value.

* **`F`**: The witness type of the **outer monad** (e.g., `CompletableFutureKind<?>`, `ListKind<?>`). This monad handles the primary effect (e.g., asynchronicity, non-determinism).
* **`A`**: The type of the value potentially held by the inner `Maybe`.

```
// From: org.higherkindedj.hkt.trans.maybe_t.MaybeT
public record MaybeT<F, A>(@NonNull Kind<F, Maybe<A>> value) { 
/* ... static factories ... */ }
```

`MaybeT<F, A>` wraps a value of type `Kind<F, Maybe<A>>`. It signifies a computation in the context of `F` that will eventually produce a `Maybe<A>`. The main benefit comes from its associated type class instance, `MaybeTMonad`, which provides monadic operations for this combined structure.

## `MaybeTKind<F, A>`: The Witness Type

Similar to other HKTs in Higher-Kinded-J, `MaybeT` uses `MaybeTKind<F, A>` as its witness type for use in generic functions.

* It extends `Kind<G, A>` where `G` (the witness for the combined monad) is `MaybeTKind<F, ?>`.
* `F` is fixed for a specific `MaybeT` context, while `A` is the variable type parameter.

```java
public interface MaybeTKind<F, A> extends Kind<MaybeTKind<F, ?>, A> {
  // Witness type G = MaybeTKind<F, ?>
  // Value type A = A (from Maybe<A>)
}
```

## `MaybeTKindHelper`

* This utility class provides static `wrap` and `unwrap` methods for safe conversion between the concrete `MaybeT<F, A>` and its `Kind` representation (`Kind<MaybeTKind<F, ?>, A>`).

```java
// To wrap:
// MaybeT<F, A> maybeT = ...;
Kind<MaybeTKind<F, ?>, A> kind = MaybeTKindHelper.wrap(maybeT);
// To unwrap:
MaybeT<F, A> unwrappedMaybeT = MaybeTKindHelper.unwrap(kind);
```

## `MaybeTMonad<F>`: Operating on `MaybeT`

The `MaybeTMonad<F>` class implements `MonadError<MaybeTKind<F, ?>, Void>`. The error type is `Void` because `MaybeT` represents failure (or absence) as `Nothing`, which doesn't carry an error value itself.

* It requires a `Monad<F>` instance for the outer monad `F`, provided during construction. This instance is used to manage the effects of `F`.
* It uses `MaybeTKindHelper.wrap` and `MaybeTKindHelper.unwrap` for conversions.
* Operations like `raiseError(null)` (since error type is Void) will create a `MaybeT` representing `F<Nothing>`. `handleErrorWith` allows "recovering" from a `Nothing` state by providing an alternative `MaybeT`.

```java
// Example: F = CompletableFutureKind<?>, Error type for MonadError is Void
// 1. Get the Monad instance for the outer monad F
Monad<CompletableFutureKind<?>> futureMonad = new CompletableFutureMonad(); // Or CompletableFutureMonadError if error handling for F is needed

// 2. Create the MaybeTMonad, providing the outer monad instance
MonadError<MaybeTKind<CompletableFutureKind<?>, ?>, Void> maybeTMonad =
    new MaybeTMonad<>(futureMonad);

// Now 'maybeTMonad' can be used to operate on Kind<MaybeTKind<CompletableFutureKind<?>, ?>, A> values.
```

### Key Operations with `MaybeTMonad`:

* **`maybeTMonad.of(value)`:** Lifts a nullable value `A` into the `MaybeT` context. Result: `F<Maybe.fromNullable(value)>`.
* **`maybeTMonad.map(f, maybeTKind)`:** Applies function `A -> B` to the `Just` value inside the nested structure. If it's `Nothing`, or `f` returns `null`, it propagates `F<Nothing>`.
* **`maybeTMonad.flatMap(f, maybeTKind)`:** Sequences operations. Takes `A -> Kind<MaybeTKind<F, ?>, B>`. If the input is `F<Just(a)>`, it applies `f(a)` to get the next `MaybeT<F, B>` and extracts its `Kind<F, Maybe<B>>`. If `F<Nothing>`, it propagates `F<Nothing>`.
* **`maybeTMonad.raiseError(null)`:** Creates `MaybeT` representing `F<Nothing>`.
* **`maybeTMonad.handleErrorWith(maybeTKind, handler)`:** Handles a `Nothing` state. The handler `Void -> Kind<MaybeTKind<F, ?>, A>` is invoked with `null`.

## Creating `MaybeT` Instances

`MaybeT` instances are typically created using its static factory methods, often requiring the outer `Monad<F>` instance:

```java


Monad<OptionalKind<?>> optMonad = new OptionalMonad(); // Outer Monad F=Optional
String presentValue = "Hello";

// 1. Lifting a non-null value: Optional<Just(value)>
MaybeT<OptionalKind<?>, String> mtJust = MaybeT.just(optMonad, presentValue);
// Resulting wrapped value: Optional.of(Maybe.just("Hello"))

// 2. Creating a 'Nothing' state: Optional<Nothing>
MaybeT<OptionalKind<?>, String> mtNothing = MaybeT.nothing(optMonad);
// Resulting wrapped value: Optional.of(Maybe.nothing())

// 3. Lifting a plain Maybe: Optional<Maybe(input)>
Maybe<Integer> plainMaybe = Maybe.just(123);
MaybeT<OptionalKind<?>, Integer> mtFromMaybe = MaybeT.fromMaybe(optMonad, plainMaybe);
// Resulting wrapped value: Optional.of(Maybe.just(123))

Maybe<Integer> plainNothing = Maybe.nothing();
MaybeT<OptionalKind<?>, Integer> mtFromMaybeNothing = MaybeT.fromMaybe(optMonad, plainNothing);
// Resulting wrapped value: Optional.of(Maybe.nothing())


// 4. Lifting an outer monad value F<A>: Optional<Maybe<A>> (using fromNullable)
Kind<OptionalKind<?>, String> outerOptional = OptionalKindHelper.wrap(Optional.of("World"));
MaybeT<OptionalKind<?>, String> mtLiftF = MaybeT.liftF(optMonad, outerOptional);
// Resulting wrapped value: Optional.of(Maybe.just("World"))

Kind<OptionalKind<?>, String> outerEmptyOptional = OptionalKindHelper.wrap(Optional.empty());
MaybeT<OptionalKind<?>, String> mtLiftFEmpty = MaybeT.liftF(optMonad, outerEmptyOptional);
// Resulting wrapped value: Optional.of(Maybe.nothing())


// 5. Wrapping an existing nested Kind: F<Maybe<A>>
Kind<OptionalKind<?>, Maybe<String>> nestedKind =
    OptionalKindHelper.wrap(Optional.of(Maybe.just("Present")));
MaybeT<OptionalKind<?>, String> mtFromKind = MaybeT.fromKind(nestedKind);
// Resulting wrapped value: Optional.of(Maybe.just("Present"))

// Accessing the wrapped value:
Kind<OptionalKind<?>, Maybe<String>> wrappedValue = mtJust.value();
Optional<Maybe<String>> unwrappedOptional = OptionalKindHelper.unwrap(wrappedValue);
// unwrappedOptional is Optional.of(Maybe.just("Hello"))
```

## Example: Asynchronous Optional Resource Fetching

Let's consider fetching a user and then their preferences, where each step is asynchronous and might not return a value.

```java
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.maybe.*;
import org.higherkindedj.hkt.future.*;
import org.higherkindedj.hkt.trans.maybe_t.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// --- Domain Model ---
record User(String id, String name) {}
record UserPreferences(String userId, String theme) {}

// --- Setup ---
Monad<CompletableFutureKind<?>> futureMonad = new CompletableFutureMonad();
MonadError<MaybeTKind<CompletableFutureKind<?>, ?>, Void> maybeTMonad =
    new MaybeTMonad<>(futureMonad);

// --- Service Stubs (returning Future<Maybe<T>>) ---

// Simulates fetching a user asynchronously
Kind<CompletableFutureKind<?>, Maybe<User>> fetchUserAsync(String userId) {
    System.out.println("Fetching user: " + userId);
    CompletableFuture<Maybe<User>> future = CompletableFuture.supplyAsync(() -> {
        try { TimeUnit.MILLISECONDS.sleep(50); } catch (InterruptedException e) { /* ignore */ }
        if ("user123".equals(userId)) {
            return Maybe.just(new User(userId, "Alice"));
        }
        return Maybe.nothing();
    });
    return CompletableFutureKindHelper.wrap(future);
}

// Simulates fetching user preferences asynchronously
Kind<CompletableFutureKind<?>, Maybe<UserPreferences>> fetchPreferencesAsync(String userId) {
    System.out.println("Fetching preferences for user: " + userId);
    CompletableFuture<Maybe<UserPreferences>> future = CompletableFuture.supplyAsync(() -> {
        try { TimeUnit.MILLISECONDS.sleep(30); } catch (InterruptedException e) { /* ignore */ }
        if ("user123".equals(userId)) {
            return Maybe.just(new UserPreferences(userId, "dark-mode"));
        }
        return Maybe.nothing(); // No preferences for other users or if user fetch failed
    });
    return CompletableFutureKindHelper.wrap(future);
}

// --- Workflow Definition using MaybeT ---

// Function to run the workflow for a given userId
Kind<CompletableFutureKind<?>, Maybe<UserPreferences>> getUserPreferencesWorkflow(String userIdToFetch) {

    // Step 1: Fetch User
    // Directly use MaybeT.fromKind as fetchUserAsync already returns F<Maybe<User>>
    Kind<MaybeTKind<CompletableFutureKind<?>, ?>, User> userMT =
        MaybeTKindHelper.wrap(MaybeT.fromKind(fetchUserAsync(userIdToFetch)));

    // Step 2: Fetch Preferences if User was found
    Kind<MaybeTKind<CompletableFutureKind<?>, ?>, UserPreferences> preferencesMT =
        maybeTMonad.flatMap(
            user -> { // This lambda is only called if userMT contains F<Just(user)>
                System.out.println("User found: " + user.name() + ". Now fetching preferences.");
                // fetchPreferencesAsync returns Kind<CompletableFutureKind<?>, Maybe<UserPreferences>>
                // which is F<Maybe<A>>, so we can wrap it directly.
                return MaybeTKindHelper.wrap(MaybeT.fromKind(fetchPreferencesAsync(user.id())));
            },
            userMT // Input to flatMap
        );

    // Try to recover if preferences are Nothing, but user was found (conceptual)
    Kind<MaybeTKind<CompletableFutureKind<?>,?>, UserPreferences> preferencesWithDefaultMT =
        maybeTMonad.handleErrorWith(preferencesMT, (Void v) -> { // Handler for Nothing
            System.out.println("Preferences not found, attempting to use default.");
            // We need userId here. For simplicity, let's assume we could get it or just return nothing.
            // This example shows returning nothing again if we can't provide a default.
            // A real scenario might try to fetch default preferences or construct one.
            return maybeTMonad.raiseError(null); // Still Nothing, or could be MaybeT.just(defaultPrefs)
        });


    // Unwrap the final MaybeT to get the underlying Future<Maybe<UserPreferences>>
    MaybeT<CompletableFutureKind<?>, UserPreferences> finalMaybeT =
        MaybeTKindHelper.unwrap(preferencesWithDefaultMT); // or preferencesMT if no recovery
    return finalMaybeT.value();
}

// --- Execution ---
public static void main(String[] args) {
    System.out.println("--- Fetching preferences for known user (user123) ---");
    Kind<CompletableFutureKind<?>, Maybe<UserPreferences>> resultKnownUserKind =
        getUserPreferencesWorkflow("user123");
    Maybe<UserPreferences> resultKnownUser = CompletableFutureKindHelper.join(resultKnownUserKind);
    System.out.println("Known User Result: " + resultKnownUser);
    // Expected: Just(UserPreferences[userId=user123, theme=dark-mode])

    System.out.println("\n--- Fetching preferences for unknown user (user999) ---");
    Kind<CompletableFutureKind<?>, Maybe<UserPreferences>> resultUnknownUserKind =
        getUserPreferencesWorkflow("user999");
    Maybe<UserPreferences> resultUnknownUser = CompletableFutureKindHelper.join(resultUnknownUserKind);
    System.out.println("Unknown User Result: " + resultUnknownUser);
    // Expected: Nothing
}
```

This example illustrates:

1. Setting up `MaybeTMonad` with `CompletableFutureMonad`.
2. Using `MaybeT.fromKind` to lift an existing `Kind<F, Maybe<A>>` into the `MaybeT` context.
3. Sequencing operations with `maybeTMonad.flatMap`. If `WorkspaceUserAsync` results in `F<Nothing>`, the lambda for fetching preferences is skipped.
4. The `handleErrorWith` shows a way to potentially recover from a `Nothing` state, though in this simple case it doesn't change the outcome if preferences are truly absent.
5. Finally, `.value()` is used to extract the underlying `Kind<CompletableFutureKind<?>, Maybe<UserPreferences>>`.

The `MaybeT` transformer simplifies working with nested optional values within other monadic contexts by providing a unified monadic interface, abstracting away the manual checks and propagation of `Nothing` states.

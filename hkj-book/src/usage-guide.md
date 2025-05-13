# Usage Guide: Working with Higher-Kinded-J

This guide explains the step-by-step process of using Higher-Kinded-J's simulated Higher-Kinded Types (HKTs) and associated type classes like `Functor`, `Applicative`, `Monad`, and `MonadError`.

## Core Workflow

The general process involves these steps:

1. **Identify the Context (`F_WITNESS`)**: Determine which type constructor (computational context) you want to work with abstractly. This context is represented by its *witness type*. Examples:

* `ListKind.Witness` for `java.util.List`
* `OptionalKind.Witness` for `java.util.Optional`
* `MaybeKind.Witness` for the custom `Maybe` type
* `EitherKind.Witness<L>` for the custom `Either<L, R>` type (where `L` is fixed)
* `TryKind.Witness` for the custom `Try` type
* `CompletableFutureKind.Witness` for `java.util.concurrent.CompletableFuture`
* `IOKind.Witness` for the custom `IO` type
* `LazyKind.Witness` for the custom `Lazy` type
* `ReaderKind.Witness<R_ENV>` for the custom `Reader<R_ENV, A>` type
* `StateKind.Witness<S>` for the custom `State<S, A>` type
* `WriterKind.Witness<W>` for the custom `Writer<W, A>` type
* For transformers, e.g., `EitherTKind.Witness<F_OUTER_WITNESS, L_ERROR>`

2. **Find the Type Class Instance**: Obtain an instance of the required type class (`Functor<F_WITNESS>`, `Applicative<F_WITNESS>`, `Monad<F_WITNESS>`, `MonadError<F_WITNESS, E>`) for your chosen context's witness type `F_WITNESS`. These are concrete classes provided in the corresponding package.

* Example (`Optional`): `OptionalMonad optionalMonad = new OptionalMonad();` (This implements `MonadError<OptionalKind.Witness, Void>`)
* Example (`List`): `ListMonad listMonad = new ListMonad();` (This implements `Monad<ListKind.Witness>`)
* Example (`CompletableFuture`): `CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();` (This implements `MonadError<CompletableFutureKind.Witness, Throwable>`)
* Example (`Either<String, ?>`): `EitherMonad<String> eitherMonad = new EitherMonad<>();` (This implements `MonadError<EitherKind.Witness<String>, String>`)
* Example (`IO`): `IOMonad ioMonad = new IOMonad();` (This implements `Monad<IOKind.Witness>`)
* Example (`Writer<String, ?>`): `WriterMonad<String> writerMonad = new WriterMonad<>(new StringMonoid());` (This implements `Monad<WriterKind.Witness<String>>`)

3. **Wrap Your Value (`JavaType<A>` -> `Kind<F_WITNESS, A>`)**: Convert your standard Java object (e.g., a `List<Integer>`, an `Optional<String>`, an `IO<String>`) into Higher-Kinded-J's `Kind` representation using the static `wrap` method from the corresponding `XxxKindHelper` class.

   ```java
   import org.higherkindedj.hkt.optional.OptionalKind; // For OptionalKind.Witness
   import org.higherkindedj.hkt.optional.OptionalKindHelper;
   import org.higherkindedj.hkt.Kind;
   import java.util.Optional;

   Optional<String> myOptional = Optional.of("test");

   // Wrap it into the Higher-Kinded-J Kind type
   // F_WITNESS here is OptionalKind.Witness
   Kind<OptionalKind.Witness, String> optionalKind = OptionalKindHelper.wrap(myOptional);
   ```

* Some helpers provide convenience factories like `MaybeKindHelper.just("value")`, `TryKindHelper.failure(ex)`, `IOKindHelper.delay(() -> ...)`, `LazyKindHelper.defer(() -> ...)`. Use these when appropriate.
* **Note on Wrapping**:
  * For JDK types (like `List`, `Optional`), `wrap` creates an internal `Holder` object.
  * For library-defined types (`Id`, `Maybe`, `IO`, Transformers like `EitherT`) that directly implement their `XxxKind` interface, `wrap` is often an efficient (checked) cast.

4. **Apply Type Class Methods**: Use the methods defined by the type class interface (`map`, `flatMap`, `of`, `ap`, `raiseError`, `handleErrorWith`, etc.) by calling them on the **type class instance** obtained in *Step 2*, passing your `Kind` value(s) as arguments. **Do not call `map`/`flatMap` directly on the `Kind` object itself if it's just the `Kind` interface.** (Some concrete `Kind` implementations like `Id` or `Maybe` might offer direct methods, but for generic programming, use the type class instance).

   ```java
   import org.higherkindedj.hkt.optional.OptionalMonad;
   import org.higherkindedj.hkt.optional.OptionalKindHelper;
   import org.higherkindedj.hkt.Kind;
   import org.higherkindedj.hkt.optional.OptionalKind; // For OptionalKind.Witness
   import java.util.Optional;
   import java.util.function.Function; // Standard Java Function

   // Assume optionalMonad is initialized as: OptionalMonad optionalMonad = new OptionalMonad();
   Optional<String> myOptional = Optional.of("test");
   Kind<OptionalKind.Witness, String> optionalKind = OptionalKindHelper.wrap(myOptional);

   // --- Using map ---
   Function<String, Integer> lengthFunc = String::length;
   // Apply map using the monad instance
   Kind<OptionalKind.Witness, Integer> lengthKind = optionalMonad.map(lengthFunc, optionalKind);
   // lengthKind now represents Kind<OptionalKind.Witness, Integer> containing Optional.of(4) if "test"

   // --- Using flatMap ---
   // Function A -> Kind<F_WITNESS, B>
   Function<Integer, Kind<OptionalKind.Witness, String>> checkLength =
       len -> OptionalKindHelper.wrap(len > 3 ? Optional.of("Long enough") : Optional.empty());
   // Apply flatMap using the monad instance
   Kind<OptionalKind.Witness, String> checkedKind = optionalMonad.flatMap(checkLength, lengthKind);
   // checkedKind now represents Kind<OptionalKind.Witness, String> containing Optional.of("Long enough")

   // --- Using MonadError (for Optional, error type is Void) ---
   Kind<OptionalKind.Witness, String> emptyKind = optionalMonad.raiseError(null); // Represents Optional.empty()
   // Handle the empty case (error state) using handleError
   Kind<OptionalKind.Witness, String> handledKind = optionalMonad.handleError(
       emptyKind,
       ignoredError -> "Default Value" // Provide a default value
   );
   // handledKind now represents Kind<OptionalKind.Witness, String> containing Optional.of("Default Value")
   ```
5. **Unwrap the Result (`Kind<F_WITNESS, A>` -> `JavaType<A>`)**: When you need the underlying Java value back (e.g., to return from a method boundary, perform side effects like printing or running `IO`), use the static `unwrap` method from the corresponding `XxxKindHelper` class.

   ```java
   // Continuing the Optional example:
   Optional<String> finalOptional = OptionalKindHelper.unwrap(checkedKind);
   System.out.println("Final Optional: " + finalOptional); // Output: Optional[Long enough]

   Optional<String> handledOptional = OptionalKindHelper.unwrap(handledKind);
   System.out.println("Handled Optional: " + handledOptional); // Output: Optional[Default Value]

   // Example for IO:
   // IOMonad ioMonad = new IOMonad();
   // Kind<IOKind.Witness, String> ioKind = IOKindHelper.delay(() -> "Hello from IO!");
   // String ioResult = IOKindHelper.unsafeRunSync(ioKind); // unsafeRunSync is specific to IOKindHelper
   // System.out.println(ioResult);
   ```

## Handling `KindUnwrapException`

The `unwrap` methods in all `KindHelper` classes are designed to be robust against *structural* errors within the HKT simulation layer.

* **When it's thrown**: If you pass `null` to `unwrap`, or pass a `Kind` object whose concrete type doesn't match what the specific helper expects (e.g., passing a `ListKind` to `OptionalKindHelper.unwrap`), `unwrap` will throw an unchecked `KindUnwrapException`. For types using a `Holder` (like `Optional`), if the `Holder` somehow contained `null` where it shouldn't (which `wrap` should prevent), that would also lead to an error.
* **What it means**: This exception signals a problem with how you are using the Higher-Kinded-J itself – usually a programming error in creating or passing `Kind` objects.
* **How to handle**: You generally **should not** need to catch `KindUnwrapException` in typical application logic. Its occurrence points to a bug that needs fixing in the code using Higher-Kinded-J.

```java
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.list.ListKindHelper; // For List
import org.higherkindedj.hkt.list.ListKind; // For ListKind.Witness
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalKind; // For OptionalKind.Witness
import org.higherkindedj.hkt.Kind;

import java.util.Optional;

// ...

Kind<OptionalKind.Witness, String> validOptionalKind = OptionalKindHelper.wrap(Optional.of("abc"));

try {
// ERROR: Attempting to unwrap an OptionalKind using ListKindHelper
// This will throw KindUnwrapException because ListKindHelper expects a Kind
// that is internally a ListHolder, not an OptionalHolder.
java.util.List<String> result = ListKindHelper.unwrap(validOptionalKind);
} catch(KindUnwrapException e) {
        System.err.println("Higher-Kinded-J Usage Error: " + e.getMessage());
        // In real code, fix the call to use OptionalKindHelper.unwrap instead of catching.
        }

        try {
// ERROR: Attempting to unwrap null
Optional<String> result = OptionalKindHelper.unwrap(null);
} catch(KindUnwrapException e) {
        System.err.println("Higher-Kinded-J Usage Error: " + e.getMessage());
        // Fix: Ensure the Kind being unwrapped is not null.
        }
```

**Important Distinction:**

* **`KindUnwrapException`**: Signals a problem with the Higher-Kinded-J structure itself (e.g., invalid `Kind` object passed to `unwrap`). Fix the code using Higher-Kinded-J.
* **Domain Errors / Absence**: Represented *within* a valid `Kind` structure (e.g., `Optional.empty()` wrapped in `Kind<OptionalKind.Witness, A>`, `Either.Left` wrapped in `Kind<EitherKind.Witness<L>, R>`). These should be handled using the monad's specific methods (`orElse`, `fold`, `handleErrorWith`, etc.) or by using the `MonadError` methods *before* unwrapping the final Java type.

## Example: Generic Function

Higher-Kinded-J allows writing functions generic over the simulated type constructor (represented by its witness `F_WITNESS`).

```java
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.list.*; // For ListKind, ListKindHelper, ListMonad
import org.higherkindedj.hkt.optional.*; // For OptionalKind, OptionalKindHelper, OptionalMonad

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class GenericExample {

   // Generic function: Doubles the number inside any Functor context F_WITNESS.
   // Requires the specific Functor<F_WITNESS> instance to be passed in.
   public static <F_WITNESS, A, B> Kind<F_WITNESS, B> mapWithFunctor(
           Functor<F_WITNESS> functorInstance, // Pass the type class instance for F_WITNESS
           Function<A, B> fn,
           Kind<F_WITNESS, A> kindABox) { // The value wrapped in Kind

      // Use the map method from the provided Functor instance
      return functorInstance.map(fn, kindABox);
   }

   public static void main(String[] args) {
      // Get instances of the type classes for the specific types (F_WITNESS) we want to use
      ListMonad listMonad = new ListMonad(); // Implements Functor<ListKind.Witness>
      OptionalMonad optionalMonad = new OptionalMonad(); // Implements Functor<OptionalKind.Witness>

      Function<Integer, Integer> doubleFn = x -> x * 2;

      // --- Use with List ---
      List<Integer> nums = List.of(1, 2, 3);
      // Wrap the List. F_WITNESS is ListKind.Witness
      Kind<ListKind.Witness, Integer> listKind = ListKindHelper.wrap(nums);
      // Call the generic function, passing the ListMonad instance and the wrapped List
      Kind<ListKind.Witness, Integer> doubledListKind = mapWithFunctor(listMonad, doubleFn, listKind);
      System.out.println("Doubled List: " + ListKindHelper.unwrap(doubledListKind)); // Output: [2, 4, 6]

      // --- Use with Optional (Present) ---
      Optional<Integer> optNum = Optional.of(10);
      // Wrap the Optional. F_WITNESS is OptionalKind.Witness
      Kind<OptionalKind.Witness, Integer> optKind = OptionalKindHelper.wrap(optNum);
      // Call the generic function, passing the OptionalMonad instance and the wrapped Optional
      Kind<OptionalKind.Witness, Integer> doubledOptKind = mapWithFunctor(optionalMonad, doubleFn, optKind);
      System.out.println("Doubled Optional: " + OptionalKindHelper.unwrap(doubledOptKind)); // Output: Optional[20]

      // --- Use with Optional (Empty) ---
      Optional<Integer> emptyOpt = Optional.empty();
      Kind<OptionalKind.Witness, Integer> emptyOptKind = OptionalKindHelper.wrap(emptyOpt);
      // Call the generic function, map does nothing on empty
      Kind<OptionalKind.Witness, Integer> doubledEmptyOptKind = mapWithFunctor(optionalMonad, doubleFn, emptyOptKind);
      System.out.println("Doubled Empty Optional: " + OptionalKindHelper.unwrap(doubledEmptyOptKind)); // Output: Optional.empty
   }
}
```

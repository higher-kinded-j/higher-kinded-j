# Usage Guide: Working with the HKT Simulation

This guide explains the step-by-step process of using the simulated Higher-Kinded Types (HKTs) and associated type classes (`Functor`, `Monad`, `MonadError`, etc.) provided by this library.

## Core Workflow

The general process involves these steps:

1. **Identify the Context (`F`):** Determine which type constructor (computational context) you want to work with abstractly. Examples: `List`, `Optional`, `Maybe`, `Either<L, ?>`, `Try`, `CompletableFuture`, `IO`, `Lazy`, `Reader<R, ?>`, `State<S, ?>`, `Writer<W, ?>`.
2. **Find the Type Class Instance:** Obtain an instance of the required type class (`Functor`, `Applicative`, `Monad`, `MonadError`) for your chosen context `F`. These are concrete classes provided in the corresponding package, often requiring necessary context like a `Monoid` for `Writer` or the fixed `Left` type `L` for `Either`.

   * Example (`Optional`): `OptionalMonad optionalMonad = new OptionalMonad();`
   * Example (`List`): `ListMonad listMonad = new ListMonad();`
   * Example (`CompletableFuture`): `CompletableFutureMonadError futureMonad = new CompletableFutureMonadError();`
   * Example (`Either<String, ?>`): `EitherMonad<String> eitherMonad = new EitherMonad<>();`
   * Example (`IO`): `IOMonad ioMonad = new IOMonad();`
   * Example (`Writer<String, ?>`): `WriterMonad<String> writerMonad = new WriterMonad<>(new StringMonoid());`
3. **Wrap Your Value (`JavaType<A>` -> `Kind<F, A>`):** Convert your standard Java object (e.g., a `List<Integer>`, an `Optional<String>`, an `IO<String>`) into the Higher-Kinded-J's `Kind` representation using the static `wrap` method from the corresponding `KindHelper` class. **Java**

   ```java
   import java.util.Optional;
   import org.higherkindedj.hkt.optional.OptionalKind;
   import org.higherkindedj.hkt.optional.OptionalKindHelper;
   import org.higherkindedj.hkt.Kind;

   // Your standard Java value
   Optional<String> myOptional = Optional.of("test");

   // Wrap it into the HKT simulation type
   Kind<OptionalKind<?>, String> optionalKind = OptionalKindHelper.wrap(myOptional);
   ```

   * Some helpers provide convenience factories like `MaybeKindHelper.just("value")`, `TryKindHelper.failure(ex)`, `IOKindHelper.delay(() -> ...)`, `LazyKindHelper.defer(() -> ...)`. Use these when appropriate.
4. **Apply Type Class Methods:** Use the methods defined by the type class interface (`map`, `flatMap`, `of`, `ap`, `raiseError`, `handleErrorWith`, etc.) by calling them on the ***type class instance*** obtained in Step 2, passing your `Kind` value(s) as arguments. **Do not call `map`/`flatMap` directly on the `Kind` object.****Java**

   ```java
   import org.higherkindedj.hkt.optional.OptionalMonad;
   import org.higherkindedj.hkt.optional.OptionalKindHelper;
   import org.higherkindedj.hkt.Kind;
   import org.higherkindedj.hkt.optional.OptionalKind; // Import the specific Kind
   import java.util.Optional;

   // Assume optionalMonad and optionalKind from previous steps

   // --- Using map ---
   Function<String, Integer> lengthFunc = String::length;
   // Apply map using the monad instance
   Kind<OptionalKind<?>, Integer> lengthKind = optionalMonad.map(lengthFunc, optionalKind);
   // lengthKind now represents Kind<OptionalKind<?>, Integer> containing Optional.of(5)

   // --- Using flatMap ---
   // Function A -> Kind<F, B>
   Function<Integer, Kind<OptionalKind<?>, String>> checkLength =
       len -> OptionalKindHelper.wrap(len > 3 ? Optional.of("Long enough") : Optional.empty());
   // Apply flatMap using the monad instance
   Kind<OptionalKind<?>, String> checkedKind = optionalMonad.flatMap(checkLength, lengthKind);
   // checkedKind now represents Kind<OptionalKind<?>, String> containing Optional.of("Long enough")

   // --- Using MonadError (for Optional, error type is Void) ---
   Kind<OptionalKind<?>, String> emptyKind = optionalMonad.raiseError(null); // Represents Optional.empty()
   // Handle the empty case (error state) using handleError
   Kind<OptionalKind<?>, String> handledKind = optionalMonad.handleError(
       emptyKind,
       ignoredError -> "Default Value" // Provide a default value
   );
   // handledKind now represents Kind<OptionalKind<?>, String> containing Optional.of("Default Value")
   ```
   
5. **Unwrap the Result (`Kind<F, A>` -> `JavaType<A>`):** When you need the underlying Java value back (e.g., to return from a method boundary, perform side effects like printing or running IO), use the static `unwrap` method from the corresponding `KindHelper` class. **Java**

   ```java
   // Continuing the Optional example:
   Optional<String> finalOptional = OptionalKindHelper.unwrap(checkedKind);
   System.out.println("Final Optional: " + finalOptional); // Output: Optional[Long enough]

   Optional<String> handledOptional = OptionalKindHelper.unwrap(handledKind);
   System.out.println("Handled Optional: " + handledOptional); // Output: Optional[Default Value]

   // Example for IO:
   // Kind<IOKind<?>, String> ioKind = IOKindHelper.delay(() -> "Hello from IO!");
   // String ioResult = IOKindHelper.unsafeRunSync(ioKind); // Use specific run method if available
   // System.out.println(ioResult);
   ```

## Handling `KindUnwrapException`

The `unwrap` methods in all `KindHelper` classes are designed to be robust against *structural* errors within the HKT simulation layer.

* **When it's thrown:** If you pass `null` to `unwrap`, or pass a `Kind` object of the wrong type (e.g., passing a `ListKind` to `OptionalKindHelper.unwrap`), or (if it were possible through incorrect usage) pass a `Holder` record that internally contains `null` where the helper expects a non-null underlying object, `unwrap` will throw an unchecked `KindUnwrapException`.
* **What it means:** This exception signals a problem with how you are using the Higher-Kinded-J itself – usually a programming error in creating or passing `Kind` objects.
* **How to handle:** You generally **should not** need to catch `KindUnwrapException` in typical application logic. Its occurrence points to a bug that needs fixing in the code using the Higher-Kinded-J.

```java
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.optional.OptionalKindHelper; // Use Optional helper
import org.higherkindedj.hkt.optional.OptionalKind; // Use Optional kind
import org.higherkindedj.hkt.Kind;

import java.util.Optional;

// ...

Kind<OptionalKind<?>, String> validOptionalKind = OptionalKindHelper.wrap(Optional.of("abc"));

try{
// ERROR: Attempting to unwrap an OptionalKind using ListKindHelper
java.util.List<String> result = ListKindHelper.unwrap(validOptionalKind);
}catch(
KindUnwrapException e){
        // This indicates incorrect usage of the helpers
        System.err.

println("HKT Simulation Usage Error: "+e.getMessage());
        // In real code, fix the call to use OptionalKindHelper.unwrap instead of catching.
        }

        try{
// ERROR: Attempting to unwrap null
Optional<String> result = OptionalKindHelper.unwrap(null);
}catch(
KindUnwrapException e){
        System.err.

println("HKT Simulation Usage Error: "+e.getMessage());
        // Fix: Ensure the Kind being unwrapped is not null.
        }
```

**Important Distinction:**

* **`KindUnwrapException`:** Signals a problem with the Higher-Kinded-J structure itself (invalid `Kind` object passed to `unwrap`). Fix the code using Higher-Kinded-J.
* **Domain Errors / Absence:** Represented *within* a valid `Kind` structure (e.g., `Optional.empty` wrapped in `OptionalKind`, `Either.Left` wrapped in `EitherKind`, `Try.Failure` wrapped in `TryKind`). These should be handled using the monad's specific methods (`orElse`, `fold`, `handleErrorWith`, etc.) *after* successfully unwrapping the `Kind` or by using the `MonadError` methods *before* unwrapping.

## Example: Generic Function

Higher-Kinded-J allows writing functions generic over the simulated type constructor `F`.

```java
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.list.*;
import org.higherkindedj.hkt.optional.*;

import java.util.List;
import java.util.Optional;

public class GenericExample {

   // Generic function: Doubles the number inside any Functor context F.
   // Requires the specific Functor<F> instance to be passed in.
   public static <F> Kind<F, Integer> doubleInContext(
           Functor<F> functorInstance, // Pass the type class instance for F
           Kind<F, Integer> numberKind // The value wrapped in the Kind<F, A> higherkindedj
   ) {
      // Use the map method from the provided Functor instance
      return functorInstance.map(x -> x * 2, numberKind);
   }

   public static void main(String[] args) {
      // Get instances of the type classes for the specific types (F) we want to use
      ListMonad listMonad = new ListMonad(); // Implements Functor<ListKind<?>>
      OptionalMonad optionalMonad = new OptionalMonad(); // Implements Functor<OptionalKind<?>>

      // --- Use with List ---
      List<Integer> nums = List.of(1, 2, 3);
      Kind<ListKind<?>, Integer> listKind = ListKindHelper.wrap(nums); // Wrap the List
      // Call the generic function, passing the ListMonad instance and the wrapped List
      Kind<ListKind<?>, Integer> doubledListKind = doubleInContext(listMonad, listKind);
      System.out.println("Doubled List: " + ListKindHelper.unwrap(doubledListKind)); // Output: [2, 4, 6]

      // --- Use with Optional (Present) ---
      Optional<Integer> optNum = Optional.of(10);
      Kind<OptionalKind<?>, Integer> optKind = OptionalKindHelper.wrap(optNum); // Wrap the Optional
      // Call the generic function, passing the OptionalMonad instance and the wrapped Optional
      Kind<OptionalKind<?>, Integer> doubledOptKind = doubleInContext(optionalMonad, optKind);
      System.out.println("Doubled Optional: " + OptionalKindHelper.unwrap(doubledOptKind)); // Output: Optional[20]

      // --- Use with Optional (Empty) ---
      Optional<Integer> emptyOpt = Optional.empty();
      Kind<OptionalKind<?>, Integer> emptyOptKind = OptionalKindHelper.wrap(emptyOpt);
      // Call the generic function, map does nothing on empty
      Kind<OptionalKind<?>, Integer> doubledEmptyOptKind = doubleInContext(optionalMonad, emptyOptKind);
      System.out.println("Doubled Empty Optional: " + OptionalKindHelper.unwrap(doubledEmptyOptKind)); // Output: Optional.empty
   }
}
```

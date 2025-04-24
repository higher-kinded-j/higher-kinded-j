# Usage Guide: Working with the HKT Simulation

This guide explains how to use the simulated Higher-Kinded Types and associated type classes (`Functor`, `Monad`, etc.) provided by this library.

## Steps for Usage

1. **Identify the Context (`F`):** Determine which type constructor (computational context) you want to work with abstractly. Examples: `List`, `Optional`, `Maybe`, `Either<L, ?>`, `Try`, `CompletableFuture`.
2. **Find the Type Class Instance:** Obtain an instance of the required type class (`Functor`, `Applicative`, `Monad`, `MonadError`) for your chosen context `F`. These are typically concrete classes provided in the corresponding package.

   * Example: For `Optional`, you'd use an instance of `OptionalMonad`. For `List`, use `ListMonad`. For `CompletableFuture`, use `CompletableFutureMonadError`. For `Either<String, ?>`, use `new EitherMonad<String>()`.
3. **Wrap Your Value (`JavaType<A>` -> `Kind<F, A>`):** Convert your standard Java object (e.g., a `List<Integer>`, an `Optional<String>`) into the simulation's `Kind` representation using the static `wrap` method from the corresponding `*KindHelper` class.

   ```java
   import java.util.Optional;
   import org.simulation.hkt.optional.OptionalKind;
   import org.simulation.hkt.optional.OptionalKindHelper;
   import org.simulation.hkt.Kind;

   Optional<String> myOptional = Optional.of("test");
   // Wrap the Optional into Kind<OptionalKind<?>, String>
   Kind<OptionalKind<?>, String> optionalKind = OptionalKindHelper.wrap(myOptional);
   ```

   * Some helpers provide convenience factories like `MaybeKindHelper.just("value")` or `TryKindHelper.failure(ex)`.
4. **Apply Type Class Methods:** Use the methods defined by the type class interface (`map`, `flatMap`, `of`, `ap`, `raiseError`, `handleErrorWith`, etc.) by calling them on the *type class instance* obtained in Step 2, passing your `Kind` value(s) as arguments.

   ```java
   import org.simulation.hkt.optional.OptionalMonad;
   import java.util.function.Function;

   OptionalMonad optMonad = new OptionalMonad(); // Get the instance

   // Example: Using map
   Function<String, Integer> lengthFunc = String::length;
   Kind<OptionalKind<?>, Integer> lengthKind = optMonad.map(lengthFunc, optionalKind);

   // Example: Using flatMap
   Function<Integer, Kind<OptionalKind<?>, String>> checkLength =
       len -> OptionalKindHelper.wrap(len > 3 ? Optional.of("Long enough") : Optional.empty());
   Kind<OptionalKind<?>, String> checkedKind = optMonad.flatMap(checkLength, lengthKind);

   // Example: Using MonadError
   Kind<OptionalKind<?>, String> errorKind = optMonad.raiseError(null); // Represents Optional.empty
   Kind<OptionalKind<?>, String> handledKind = optMonad.handleError(errorKind, ignoredError -> "Default Value");
   ```
5. **Unwrap the Result (`Kind<F, A>` -> `JavaType<A>`):** When you need the underlying Java value back (e.g., to return from a method boundary, perform side effects), use the static `unwrap` method from the corresponding `*KindHelper` class.

   ```java
   Optional<String> finalOptional = OptionalKindHelper.unwrap(checkedKind);
   System.out.println("Final Optional: " + finalOptional); // Output: Optional[Long enough]

   Optional<String> handledOptional = OptionalKindHelper.unwrap(handledKind);
   System.out.println("Handled Optional: " + handledOptional); // Output: Optional[Default Value]
   ```

## Handling `KindUnwrapException`

The `unwrap` methods in all `*KindHelper` classes are designed to be robust against invalid inputs *within the HKT simulation layer*. If you pass `null`, a `Kind` object of the wrong type (e.g., passing a `ListKind` to `OptionalKindHelper.unwrap`), or a `*Holder` record that internally contains `null` (which shouldn't happen if constructed via `wrap`), `unwrap` will throw an unchecked `KindUnwrapException`.

```java
import org.simulation.hkt.exception.KindUnwrapException;
import org.simulation.hkt.list.ListKindHelper;
import java.util.List; // Import List

try {
    // Example: Attempting to unwrap null
    List<String> result = ListKindHelper.unwrap(null);
} catch (KindUnwrapException e) {
    System.err.println("HKT Simulation Error: " + e.getMessage());
    // Handle the infrastructure error appropriately
}
Important Distinction:KindUnwrapException: Signals a problem with the HKT simulation structure itself (invalid Kind object). This usually indicates a programming error in how Kind objects are being created or passed around.Domain Errors/Absence: Represented within a valid Kind structure (e.g., Optional.empty wrapped in OptionalKind, Either.Left wrapped in EitherKind, Try.Failure wrapped in TryKind). These should be handled using the monad's specific methods (orElse, fold, handleErrorWith, etc.) after successfully unwrapping the Kind.Example: Generic Function (Illustrative)Imagine wanting a function that doubles the number inside any Functor context F:import org.simulation.hkt.Functor;
import org.simulation.hkt.Kind;
import org.simulation.hkt.list.*;
import org.simulation.hkt.optional.*;
import java.util.List;
import java.util.Optional;

public class GenericExample {

    // Generic function requiring a Functor instance for F
    public static <F> Kind<F, Integer> doubleInContext(
        Functor<F> functor, // Pass the type class instance
        Kind<F, Integer> numberKind)
    {
        return functor.map(x -> x * 2, numberKind);
    }

    public static void main(String[] args) {
        // Instances for List and Optional
        ListMonad listMonad = new ListMonad(); // Implements Functor<ListKind<?>>
        OptionalMonad optionalMonad = new OptionalMonad(); // Implements Functor<OptionalKind<?>>

        // --- Use with List ---
        List<Integer> nums = List.of(1, 2, 3);
        Kind<ListKind<?>, Integer> listKind = ListKindHelper.wrap(nums);
        Kind<ListKind<?>, Integer> doubledListKind = doubleInContext(listMonad, listKind);
        System.out.println("Doubled List: " + ListKindHelper.unwrap(doubledListKind)); // [2, 4, 6]

        // --- Use with Optional ---
        Optional<Integer> optNum = Optional.of(10);
        Kind<OptionalKind<?>, Integer> optKind = OptionalKindHelper.wrap(optNum);
        Kind<OptionalKind<?>, Integer> doubledOptKind = doubleInContext(optionalMonad, optKind);
        System.out.println("Doubled Optional: " + OptionalKindHelper.unwrap(doubledOptKind)); // Optional[20]

        Optional<Integer> emptyOpt = Optional.empty();
        Kind<OptionalKind<?>, Integer> emptyOptKind = OptionalKindHelper.wrap(emptyOpt);
        Kind<OptionalKind<?>, Integer> doubledEmptyOptKind = doubleInContext(optionalMonad, emptyOptKind);
        System.out.println("Doubled Empty Optional: " + OptionalKindHelper.unwrap(doubledEmptyOptKind)); // Optional.empty
    }
}

```

This demonstrates how the type class (`Functor`) and `Kind` allow writing code (`doubleInContext`) that is generic across different simulated HKTs.

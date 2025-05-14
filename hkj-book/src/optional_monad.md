# Optional - Monadic Operations for Java Optional

## Purpose

The `OptionalMonad` in the `Higher-Kinded-J` library provides a monadic interface for Java's standard `java.util.Optional<T>`. It allows developers to work with `Optional` values in a more functional and composable style, enabling operations like `map`, `flatMap`, and `ap` (apply) within the higher-kinded type (HKT) system. This is particularly useful for sequencing operations that may or may not produce a value, handling the presence or absence of values gracefully.

Key benefits include:

* **Functional Composition:** Easily chain operations on `Optional`s, where each operation might return an `Optional` itself. If any step results in an `Optional.empty()`, subsequent operations are typically short-circuited, propagating the empty state.
* **HKT Integration:** `OptionalKind<A>` (the higher-kinded wrapper for `Optional<A>`) and `OptionalMonad` allow `Optional` to be used with generic functions and type classes expecting `Kind<F, A>`, `Functor<F>`, `Applicative<F>`, `Monad<M>`, or even `MonadError<M, E>`.
* **Error Handling for Absence:** `OptionalMonad` implements `MonadError<OptionalKind.Witness, Void>`. In this context, `Optional.empty()` is treated as the "error" state, and `Void` is used as the phantom error type, signifying absence rather than a traditional exception.

It implements `MonadError<OptionalKind.Witness, Void>`, which means it also transitively implements `Monad<OptionalKind.Witness>`, `Applicative<OptionalKind.Witness>`, and `Functor<OptionalKind.Witness>`.

## Structure

![optional_monad.svg](./images/puml/optional_monad.svg)


## How to Use `OptionalMonad` and `OptionalKind`

### Creating Instances

`OptionalKind<A>` is the higher-kinded type representation for `java.util.Optional<A>`. You typically create `OptionalKind` instances using the `OptionalKindHelper` utility class or the `of` and `raiseError` methods from `OptionalMonad`.

1. **`OptionalKindHelper.wrap(Optional<A>)`:** Converts a standard `java.util.Optional<A>` into an `OptionalKind<A>`.

   ```java
   import org.higherkindedj.hkt.optional.OptionalKind;
   import org.higherkindedj.hkt.optional.OptionalKindHelper;
   import java.util.Optional;

   // Wrapping a present Optional
   Optional<String> presentOptional = Optional.of("Hello");
   OptionalKind<String> kindPresent = OptionalKindHelper.wrap(presentOptional);

   // Wrapping an empty Optional
   Optional<Integer> emptyOptional = Optional.empty();
   OptionalKind<Integer> kindEmpty = OptionalKindHelper.wrap(emptyOptional);

   // Wrapping an Optional that might be null (though Optional itself won't be null)
   String possiblyNullValue = null;
   Optional<String> nullableOptional = Optional.ofNullable(possiblyNullValue); // Results in Optional.empty()
   OptionalKind<String> kindFromNullable = OptionalKindHelper.wrap(nullableOptional);
   ```
2. **`optionalMonad.of(A value)`:** Lifts a single value (which can be `null`) into the `OptionalKind` context. It uses `Optional.ofNullable(value)` internally.

   ```java
   import org.higherkindedj.hkt.optional.OptionalMonad;
   import org.higherkindedj.hkt.optional.OptionalKind;

   OptionalMonad optionalMonad = new OptionalMonad();

   OptionalKind<String> kindFromValue = optionalMonad.of("World"); // Wraps Optional.of("World")
   OptionalKind<Integer> kindFromNullValue = optionalMonad.of(null); // Wraps Optional.empty()
   ```
3. **`optionalMonad.raiseError(Void error)`:** Creates an empty `OptionalKind`. Since `Void` is the error type, this method effectively represents the "error" state of an `Optional`, which is `Optional.empty()`. The `error` argument (which would be `null` for `Void`) is ignored.

   ```java
   import org.higherkindedj.hkt.optional.OptionalMonad;
   import org.higherkindedj.hkt.Kind; // For general Kind type
   import org.higherkindedj.hkt.optional.OptionalKind;


   OptionalMonad optionalMonad = new OptionalMonad();
   Kind<OptionalKind.Witness, String> emptyKindFromError = optionalMonad.raiseError(null); // Represents Optional.empty()
   ```

### Unwrapping `OptionalKind`

To get the underlying `java.util.Optional<A>` from an `OptionalKind<A>`, use `OptionalKindHelper.unwrap()`:

```java
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import java.util.Optional;

OptionalKind<String> kindPresent = OptionalKindHelper.wrap(Optional.of("Example"));
Optional<String> unwrappedOptional = OptionalKindHelper.unwrap(kindPresent); // Returns Optional.of("Example")
System.out.println("Unwrapped: " + unwrappedOptional);

OptionalKind<Integer> kindEmpty = OptionalKindHelper.wrap(Optional.empty());
Optional<Integer> unwrappedEmpty = OptionalKindHelper.unwrap(kindEmpty); // Returns Optional.empty()
System.out.println("Unwrapped Empty: " + unwrappedEmpty);
```

### Key Operations

The `OptionalMonad` provides standard monadic and error-handling operations:

* **`map(Function<A, B> f, Kind<OptionalKind.Witness, A> fa)`:** Applies a function `f` to the value inside `fa` if it's present. If `fa` is empty, it remains empty. The function `f` can return `null`, which `Optional.map` will turn into an `Optional.empty()`.

```java
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import java.util.Optional;
import java.util.function.Function;

OptionalMonad optionalMonad = new OptionalMonad();
OptionalKind<Integer> presentNumber = OptionalKindHelper.wrap(Optional.of(10));
OptionalKind<Integer> emptyNumber = OptionalKindHelper.wrap(Optional.empty());

Function<Integer, String> intToString = i -> "Number: " + i;
OptionalKind<String> presentString = optionalMonad.map(intToString, presentNumber);
// OptionalKindHelper.unwrap(presentString) would be Optional.of("Number: 10")

OptionalKind<String> emptyString = optionalMonad.map(intToString, emptyNumber);
// OptionalKindHelper.unwrap(emptyString) would be Optional.empty()

Function<Integer, String> intToNull = i -> null;
OptionalKind<String> mappedToNull = optionalMonad.map(intToNull, presentNumber);
// OptionalKindHelper.unwrap(mappedToNull) would be Optional.empty()

System.out.println("Map (Present): " + OptionalKindHelper.unwrap(presentString));
System.out.println("Map (Empty): " + OptionalKindHelper.unwrap(emptyString));
System.out.println("Map (To Null): " + OptionalKindHelper.unwrap(mappedToNull));
```

* **`flatMap(Function<A, Kind<OptionalKind.Witness, B>> f, Kind<OptionalKind.Witness, A> ma)`:** Applies a function `f` to the value inside `ma` if it's present. The function `f` itself returns an `OptionalKind<B>`. If `ma` is empty, or if `f` returns an empty `OptionalKind`, the result is an empty `OptionalKind`.

```java
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.Kind;
import java.util.Optional;
import java.util.function.Function;

OptionalMonad optionalMonad = new OptionalMonad();
OptionalKind<String> presentInput = OptionalKindHelper.wrap(Optional.of("5"));
OptionalKind<String> emptyInput = OptionalKindHelper.wrap(Optional.empty());

Function<String, Kind<OptionalKind.Witness, Integer>> parseToIntKind = s -> {
    try {
        return OptionalKindHelper.wrap(Optional.of(Integer.parseInt(s)));
    } catch (NumberFormatException e) {
        return OptionalKindHelper.wrap(Optional.empty());
    }
};

OptionalKind<Integer> parsedPresent = optionalMonad.flatMap(parseToIntKind, presentInput);
// OptionalKindHelper.unwrap(parsedPresent) would be Optional.of(5)

OptionalKind<Integer> parsedEmpty = optionalMonad.flatMap(parseToIntKind, emptyInput);
// OptionalKindHelper.unwrap(parsedEmpty) would be Optional.empty()

OptionalKind<String> nonNumericInput = OptionalKindHelper.wrap(Optional.of("abc"));
OptionalKind<Integer> parsedNonNumeric = optionalMonad.flatMap(parseToIntKind, nonNumericInput);
// OptionalKindHelper.unwrap(parsedNonNumeric) would be Optional.empty()

System.out.println("FlatMap (Present): " + OptionalKindHelper.unwrap(parsedPresent));
System.out.println("FlatMap (Empty Input): " + OptionalKindHelper.unwrap(parsedEmpty));
System.out.println("FlatMap (Non-numeric): " + OptionalKindHelper.unwrap(parsedNonNumeric));
```

* **`ap(Kind<OptionalKind.Witness, Function<A, B>> ff, Kind<OptionalKind.Witness, A> fa)`:** Applies an `OptionalKind` containing a function `ff` to an `OptionalKind` containing a value `fa`. If both are present, the function is applied. Otherwise, the result is empty.

```java
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.Kind;
import java.util.Optional;
import java.util.function.Function;

OptionalMonad optionalMonad = new OptionalMonad();

OptionalKind<Function<Integer, String>> presentFuncKind =
    OptionalKindHelper.wrap(Optional.of(i -> "Value: " + i));
OptionalKind<Function<Integer, String>> emptyFuncKind =
    OptionalKindHelper.wrap(Optional.empty());
handleErrorWith(Kind<OptionalKind.Witness, A> ma, Function<Void, Kind<OptionalKind.Witness, A>> handler): If ma is present, it's returned. If ma is empty (the "error" state), the handler function is invoked (with null as the Void argument) to provide a recovery OptionalKind.
OptionalKind<Integer> presentValueKind = OptionalKindHelper.wrap(Optional.of(100));
OptionalKind<Integer> emptyValueKind = OptionalKindHelper.wrap(Optional.empty());

// Both present
OptionalKind<String> result1 = optionalMonad.ap(presentFuncKind, presentValueKind);
// OptionalKindHelper.unwrap(result1) is Optional.of("Value: 100")

// Function empty
OptionalKind<String> result2 = optionalMonad.ap(emptyFuncKind, presentValueKind);
// OptionalKindHelper.unwrap(result2) is Optional.empty()

// Value empty
OptionalKind<String> result3 = optionalMonad.ap(presentFuncKind, emptyValueKind);
// OptionalKindHelper.unwrap(result3) is Optional.empty()

System.out.println("Ap (Both Present): " + OptionalKindHelper.unwrap(result1));
System.out.println("Ap (Function Empty): " + OptionalKindHelper.unwrap(result2));
System.out.println("Ap (Value Empty): " + OptionalKindHelper.unwrap(result3));
```

* **`handleErrorWith(Kind<OptionalKind.Witness, A> ma, Function<Void, Kind<OptionalKind.Witness, A>> handler)`:** If `ma` is present, it's returned. If `ma` is empty (the "error" state), the `handler` function is invoked (with `null` as the `Void` argument) to provide a recovery `OptionalKind`.

```java
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.Kind;
import java.util.Optional;
import java.util.function.Function;

OptionalMonad optionalMonad = new OptionalMonad();

OptionalKind<String> presentKind = OptionalKindHelper.wrap(Optional.of("Exists"));
OptionalKind<String> emptyKind = OptionalKindHelper.wrap(Optional.empty());

Function<Void, Kind<OptionalKind.Witness, String>> recoveryFunction =
    ignoredVoid -> OptionalKindHelper.wrap(Optional.of("Recovered Value"));

// Handling error on a present OptionalKind
Kind<OptionalKind.Witness, String> handledPresent =
    optionalMonad.handleErrorWith(presentKind, recoveryFunction);
// OptionalKindHelper.unwrap(handledPresent) is Optional.of("Exists")

// Handling error on an empty OptionalKind
Kind<OptionalKind.Witness, String> handledEmpty =
    optionalMonad.handleErrorWith(emptyKind, recoveryFunction);
// OptionalKindHelper.unwrap(handledEmpty) is Optional.of("Recovered Value")

System.out.println("HandleError (Present): " + OptionalKindHelper.unwrap(handledPresent));
System.out.println("HandleError (Empty): " + OptionalKindHelper.unwrap(handledEmpty));
```

### Using `OptionalMonad` with Higher-Kinded-J (Generic Code)

To use `OptionalMonad` in generic contexts that operate over `Kind<F, A>`:

1. **Get an instance of `OptionalMonad`:**

```java
OptionalMonad optionalMonad = new OptionalMonad();
```

2. **Wrap your `Optional` into `OptionalKind`:**

```java
Optional<Integer> myOptional = Optional.of(42);
Kind<OptionalKind.Witness, Integer> optionalKind = OptionalKindHelper.wrap(myOptional);

// Or using 'of' for values (handles null by creating Optional.empty())
Kind<OptionalKind.Witness, String> presentStringKind = optionalMonad.of("hello");
Kind<OptionalKind.Witness, String> emptyFromNullKind = optionalMonad.of(null);

// Or using 'raiseError' for an empty OptionalKind
Kind<OptionalKind.Witness, Double> emptyDoubleKind = optionalMonad.raiseError(null);
```

3. Use `OptionalMonad` methods:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import java.util.Optional;
import java.util.function.Function;

public class OptionalMonadExample {
    public static void main(String[] args) {
        OptionalMonad optionalMonad = new OptionalMonad();

        // 1. Create OptionalKind instances
        Kind<OptionalKind.Witness, Integer> presentIntKind = OptionalKindHelper.wrap(Optional.of(10));
        Kind<OptionalKind.Witness, Integer> emptyIntKind = optionalMonad.raiseError(null); // Creates empty

        // 2. Use map
        Function<Integer, String> intToMessage = n -> "Value is " + n;
        Kind<OptionalKind.Witness, String> mappedPresent = optionalMonad.map(intToMessage, presentIntKind);
        Kind<OptionalKind.Witness, String> mappedEmpty = optionalMonad.map(intToMessage, emptyIntKind);

        System.out.println("Mapped (Present): " + OptionalKindHelper.unwrap(mappedPresent)); // Optional[Value is 10]
        System.out.println("Mapped (Empty): " + OptionalKindHelper.unwrap(mappedEmpty));   // Optional.empty

        // 3. Use flatMap
        Function<Integer, Kind<OptionalKind.Witness, Double>> intToOptionalDouble = n ->
            (n > 0) ? optionalMonad.of(n / 2.0) : optionalMonad.raiseError(null);

        Kind<OptionalKind.Witness, Double> flatMappedPresent = optionalMonad.flatMap(intToOptionalDouble, presentIntKind);
        Kind<OptionalKind.Witness, Double> flatMappedEmpty = optionalMonad.flatMap(intToOptionalDouble, emptyIntKind);
        Kind<OptionalKind.Witness, Integer> zeroIntKind = optionalMonad.of(0);
        Kind<OptionalKind.Witness, Double> flatMappedZero = optionalMonad.flatMap(intToOptionalDouble, zeroIntKind);


        System.out.println("FlatMapped (Present): " + OptionalKindHelper.unwrap(flatMappedPresent)); // Optional[5.0]
        System.out.println("FlatMapped (Empty): " + OptionalKindHelper.unwrap(flatMappedEmpty));     // Optional.empty
        System.out.println("FlatMapped (Zero): " + OptionalKindHelper.unwrap(flatMappedZero));       // Optional.empty

        // 4. Use 'of' and 'raiseError' (already shown in creation)

        // 5. Use handleErrorWith
        Function<Void, Kind<OptionalKind.Witness, Integer>> recoverWithDefault =
            v -> optionalMonad.of(-1); // Default value if empty

        Kind<OptionalKind.Witness, Integer> recoveredFromEmpty =
            optionalMonad.handleErrorWith(emptyIntKind, recoverWithDefault);
        Kind<OptionalKind.Witness, Integer> notRecoveredFromPresent =
            optionalMonad.handleErrorWith(presentIntKind, recoverWithDefault);

        System.out.println("Recovered (from Empty): " + OptionalKindHelper.unwrap(recoveredFromEmpty)); // Optional[-1]
        System.out.println("Recovered (from Present): " + OptionalKindHelper.unwrap(notRecoveredFromPresent)); // Optional[10]

        // Unwrap to get back the standard Optional
        Optional<String> finalMappedOptional = OptionalKindHelper.unwrap(mappedPresent);
        System.out.println("Final unwrapped mapped optional: " + finalMappedOptional);
    }
}
```

This example demonstrates wrapping `Optional`s, applying monadic and error-handling operations via `OptionalMonad`, and unwrapping back to standard `Optional`s. The `MonadError` capabilities allow treating absence (`Optional.empty`) as a recoverable "error" state.

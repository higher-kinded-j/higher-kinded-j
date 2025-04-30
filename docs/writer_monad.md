# Writer - Accumulating Output Alongside Computations

## Purpose

The **Writer monad** is a functional pattern designed for computations that, in addition to producing a primary result value, also need to **accumulate** some secondary output or log along the way. Think of scenarios like:

* Detailed logging of steps within a complex calculation.
* Collecting metrics or events during a process.
* Building up a sequence of results or messages.

A `Writer<W, A>` represents a computation that produces a main result of type `A` and simultaneously accumulates an output of type `W`. The key requirement is that the accumulated type `W` must form a **Monoid**.

### The Role of `Monoid<W>`

A `Monoid<W>` is a type class that defines two things for type `W`:

1. `empty()`: Provides an identity element (like `""` for String concatenation, `0` for addition, or an empty list).
2. `combine(W w1, W w2)`: Provides an **associative** binary operation to combine two values of type `W` (like `+` for strings or numbers, or list concatenation).

The Writer monad uses the `Monoid<W>` to:

* Provide a starting point (the `empty` value) for the accumulation.
* Combine the accumulated outputs (`W`) from different steps using the `combine` operation when sequencing computations with `flatMap` or `ap`.

Common examples for `W` include `String` (using concatenation), `Integer` (using addition or multiplication), or `List` (using concatenation).

## Structure

![writer.svg](puml/writer.svg)

## The `Writer<W, A>` Type

The core type is the `Writer<W, A>` record:

```java
public record Writer<W, A>(@NonNull W log, @Nullable A value) {
    // Static factories
    public static <W, A> @NonNull Writer<W, A> create(@NonNull W log, @Nullable A value);
    public static <W, A> @NonNull Writer<W, A> value(@NonNull Monoid<W> monoidW, @Nullable A value);
    public static <W> @NonNull Writer<W, Void> tell(@NonNull W log);

    // Instance methods
    public <B> @NonNull Writer<W, B> map(@NonNull Function<? super A, ? extends B> f);
    public <B> @NonNull Writer<W, B> flatMap(
        @NonNull Monoid<W> monoidW,
        @NonNull Function<? super A, ? extends Writer<W, ? extends B>> f
    );
    public @Nullable A run(); // Get the value A
    public @NonNull W exec(); // Get the log W
}
```

* It simply holds a pair: the accumulated `log` (of type `W`) and the computed `value` (of type `A`).
* `create(log, value)`: Basic constructor.
* `value(monoid, value)`: Creates a Writer with the given value and an *empty* log according to the provided `Monoid`.
* `tell(log)`: Creates a Writer with the given log but no meaningful value (typically `Void`/`null`). Useful for just adding to the log.
* `map(...)`: Transforms the computed value `A` to `B` while leaving the log `W` untouched.
* `flatMap(...)`: Sequences computations. It runs the first Writer, uses its value `A` to create a second Writer, and combines the logs from both using the provided `Monoid`.
* `run()`: Extracts only the computed value `A`, discarding the log.
* `exec()`: Extracts only the accumulated log `W`, discarding the value.

## HKT Simulation Components

To integrate `Writer` with the generic HKT framework:

* **`WriterKind<W, A>`:** The marker interface extending `Kind<WriterKind<W, ?>, A>`. The witness type `F` is `WriterKind<W, ?>` (where `W` and its `Monoid` are fixed for a given monad instance), and the value type `A` is the result type of the writer.
* **`WriterKindHelper`:** The utility class with static methods:
  * `wrap(Writer<W, A>)`: Converts a `Writer` to `WriterKind<W, A>`.
  * `unwrap(Kind<WriterKind<W, ?>, A>)`: Converts `WriterKind` back to `Writer`. Throws `KindUnwrapException` if the input is invalid.
  * `value(Monoid<W>, A)`: Factory method for a `WriterKind` with an empty log.
  * `tell(Monoid<W>, W)`: Factory method for a `WriterKind` that only logs.
  * `runWriter(Kind<WriterKind<W, ?>, A>)`: Unwraps the `Writer` record.
  * `run(Kind<WriterKind<W, ?>, A>)`: Executes and returns only the value `A`.
  * `exec(Kind<WriterKind<W, ?>, A>)`: Executes and returns only the log `W`.

## Type Class Instances (`WriterFunctor`, `WriterApplicative`, `WriterMonad`)

These classes provide the standard functional operations for `WriterKind<W, ?>`, allowing you to treat `Writer` computations generically. **Crucially, `WriterApplicative` and `WriterMonad` require a `Monoid<W>` instance during construction.**

* **`WriterFunctor<W>`:** Implements `Functor<WriterKind<W, ?>>`. Provides `map` (operates only on the value `A`).
* **`WriterApplicative<W>`:** Extends `WriterFunctor<W>`, implements `Applicative<WriterKind<W, ?>>`. Requires a `Monoid<W>`. Provides `of` (lifting a value with an empty log) and `ap` (applying a wrapped function to a wrapped value, combining logs).
* **`WriterMonad<W>`:** Extends `WriterApplicative<W>`, implements `Monad<WriterKind<W, ?>>`. Requires a `Monoid<W>`. Provides `flatMap` for sequencing computations, automatically combining logs using the `Monoid`.

You typically instantiate `WriterMonad<W>` for the specific log type `W` and its corresponding `Monoid`.

## How to Use

### 1. Choose Your Log Type `W` and `Monoid<W>`

Decide what you want to accumulate (e.g., `String` for logs, `List<String>` for messages, `Integer` for counts) and get its `Monoid`.

```java
import org.simulation.hkt.typeclass.*; // For Monoid
import org.simulation.hkt.test.typeclass.StringMonoid; // Example Monoid from tests
// Assuming StringMonoid is accessible or you create your own Monoid impl

Monoid<String> stringMonoid = new StringMonoid(); // Use String concatenation
```


### 2. Get the `WriterMonad` Instance

Instantiate the monad for your chosen log type `W`, providing its `Monoid`.

```java
import org.simulation.hkt.writer.WriterMonad;

// Monad instance for computations logging Strings
WriterMonad<String> writerMonad = new WriterMonad<>(stringMonoid);

```


### 3. Create Writer Computations

Use `WriterKindHelper` factory methods, providing the `Monoid` where needed.

```java
import static org.simulation.hkt.writer.WriterKindHelper.*;
import org.simulation.hkt.Kind;
import org.simulation.hkt.writer.WriterKind;

// Writer with an initial value and empty log
Kind<WriterKind<String, ?>, Integer> initialValue = value(stringMonoid, 5); // ("", 5)

// Writer that just logs a message (value is Void/null)
Kind<WriterKind<String, ?>, Void> logStart = tell(stringMonoid, "Starting calculation; "); // ("Starting calculation; ", null)

// A function that performs a calculation and logs its step
Function<Integer, Kind<WriterKind<String, ?>, Integer>> addAndLog =
    x -> {
        int result = x + 10;
        String logMsg = "Added 10 to " + x + " -> " + result + "; ";
        // Create a Writer pairing the log message and the result
        return wrap(Writer.create(logMsg, result));
    };

Function<Integer, Kind<WriterKind<String, ?>, String>> multiplyAndLogToString =
    x -> {
        int result = x * 2;
        String logMsg = "Multiplied " + x + " by 2 -> " + result + "; ";
        return wrap(Writer.create(logMsg, "Final:" + result));
    };

```


### 4. Compose Computations using `map` and `flatMap`

Use the methods on the `writerMonad` instance. `flatMap` automatically combines logs using the `Monoid`.

```java
// Chain the operations: logStart >> initialValue >> addAndLog >> multiplyAndLogToString
// Note: flatMap ignores the Void value from logStart

Kind<WriterKind<String, ?>, String> finalComputation =
    writerMonad.flatMap(v -> logStart, writerMonad.of(0)) // Start with of(0), then logStart -> ("", null)
       .flatMap(ignored -> initialValue, logStart) // Run initialValue -> ("Starting calculation; ", 5)
       .flatMap(addAndLog, initialValue) // Run addAndLog(5) -> ("Starting calculation; Added 10 to 5 -> 15; ", 15)
       .flatMap(multiplyAndLogToString, addAndLog.apply(5)); // Run multiplyAndLog(15) -> ("Starting calculation; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30; ", "Final:30")


// Simpler chaining:
Kind<WriterKind<String, ?>, Integer> step1 = initialValue; // ("", 5)
Kind<WriterKind<String, ?>, Void> step2 = writerMonad.flatMap(i -> tell(stringMonoid, "Processing " + i + "; "), step1); // ("Processing 5; ", null)
Kind<WriterKind<String, ?>, Integer> step3 = writerMonad.flatMap(ignored -> addAndLog.apply(5), step2); // ("Processing 5; Added 10 to 5 -> 15; ", 15)
Kind<WriterKind<String, ?>, String> step4 = writerMonad.flatMap(multiplyAndLogToString, step3); // ("Processing 5; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30; ", "Final:30")


// Using map: Only transforms the value, log remains unchanged
Kind<WriterKind<String, ?>, Integer> initialVal = value(stringMonoid, 100); // ("", 100)
Kind<WriterKind<String, ?>, String> mappedVal = writerMonad.map(i -> "Value is " + i, initialVal); // ("", "Value is 100")
```


### 5. Run the Computation and Extract Results

Use `runWriter`, `run`, or `exec` from `WriterKindHelper`.

```java

import org.simulation.hkt.writer.Writer; // Import the record type

// Get the final Writer record (log and value)
Writer<String, String> finalResultWriter = runWriter(step4);
String finalLog = finalResultWriter.log();
String finalValue = finalResultWriter.value();

System.out.println("Final Log: " + finalLog);
// Output: Final Log: Processing 5; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30;
System.out.println("Final Value: " + finalValue);
// Output: Final Value: Final:30

// Or get only the value or log
String justValue = run(step4);
String justLog = exec(step4);

System.out.println("Just Value: " + justValue); // Output: Just Value: Final:30
System.out.println("Just Log: " + justLog);     // Output: Just Log: Processing 5; Added 10 to 5 -> 15; Multiplied 15 by 2 -> 30;

Writer<String, String> mappedResult = runWriter(mappedVal);
System.out.println("Mapped Log: " + mappedResult.log());   // Output: Mapped Log:
System.out.println("Mapped Value: " + mappedResult.value()); // Output: Mapped Value: Value is 100
```


## Summary

The Writer monad (`Writer<W, A>`, `WriterKind`, `WriterMonad`) in `simulation-hkt` provides a structured way to perform computations that produce a main value (`A`) while simultaneously accumulating some output (`W`, like logs or metrics). It relies on a `Monoid<W>` instance to combine the accumulated outputs when sequencing steps with `flatMap`. This pattern helps separate the core computation logic from the logging/accumulation aspect, leading to cleaner, more composable code. The HKT simulation allows these operations to be performed generically using standard type class interfaces.

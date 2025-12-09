# Higher-Kinded Types - Basic Usage Examples

> This document provides a brief summary of the example classes found in the `org.higherkindedj.example.basic` package in the [HKJ-Examples](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/basic).

These examples showcase how to use various monads and monad transformers to handle common programming tasks like managing optional values, asynchronous operations, and state in a functional way.

~~~admonish info title="What You'll Learn"
- Practical examples of core monads including Either, Maybe, Optional, IO, and State
- How to use monad transformers like EitherT, MaybeT, and StateT to combine effects
- Working with specialized monads like Reader for dependency injection and Writer for logging
- Using For comprehensions to compose complex monadic workflows
- Writing generic functions that work across different Functor and Monad instances
- Handling errors and exceptions functionally with Try, Either, and MonadError
~~~

---

## Monads

### [EitherExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either/EitherExample.java)

This example demonstrates the **Either monad**. `Either` is used to represent a value that can be one of two types, typically a success value (`Right`) or an error value (`Left`).

* **Key Concept**: A `Either` provides a way to handle computations that can fail with a specific error type.
* **Demonstrates**:
  * Creating `Either` instances for success (`Right`) and failure (`Left`) cases.
  * Using `flatMap` to chain operations that return an `Either`, short-circuiting on failure.
  * Using `fold` to handle both the `Left` and `Right` cases.

```java
// Chain operations that can fail
Either<String, Integer> result = input.flatMap(parse).flatMap(checkPositive);

// Fold to handle both outcomes
String message = result.fold(
    leftValue -> "Operation failed with: " + leftValue,
    rightValue -> "Operation succeeded with: " + rightValue
);
```

### [ForComprehensionExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/expression/ForComprehensionExample.java)

This example demonstrates how to use the `For` comprehension, a feature that provides a more readable, sequential syntax for composing monadic operations (equivalent to `flatMap` chains).

* **Key Concept**: A `For` comprehension offers syntactic sugar for `flatMap` and `map` calls, making complex monadic workflows easier to write and understand.
* **Demonstrates**:
  * Using `For.from()` to start and chain monadic operations.
  * Applying comprehensions to different monads like `List`, `Maybe`, and the `StateT` monad transformer.
  * Filtering intermediate results with `.when()`.
  * Introducing intermediate values with `.let()`.
  * Producing a final result with `.yield()`.

```java
// A for-comprehension with List
final Kind<ListKind.Witness, String> result =
    For.from(listMonad, list1)
        .from(_ -> list2)
        .when(t -> (t._1() + t._2()) % 2 != 0) // Filter
        .let(t -> "Sum: " + (t._1() + t._2())) // Introduce new value
        .yield((a, b, c) -> a + " + " + b + " = " + c); // Final result
```

### [CompletableFutureExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/future/CompletableFutureExample.java)

This example covers the **CompletableFuture monad**. It shows how to use `CompletableFuture` within the Higher-Kinded-J framework to manage asynchronous computations and handle potential errors.

* **Key Concept**: The `CompletableFuture` monad is used to compose asynchronous operations in a non-blocking way.
* **Demonstrates**:
  * Creating `Kind`-wrapped `CompletableFuture` instances for success and failure.
  * Using `map` (which corresponds to `thenApply`).
  * Using `flatMap` (which corresponds to `thenCompose`) to chain dependent asynchronous steps.
  * Using `handleErrorWith` to recover from exceptions that occur within the future.

```java
// Using handleErrorWith to recover from a failed future
Function<Throwable, Kind<CompletableFutureKind.Witness, String>> recoveryHandler =
    error -> {
      System.out.println("Handling error: " + error.getMessage());
      return futureMonad.of("Recovered from Error");
    };

Kind<CompletableFutureKind.Witness, String> recoveredFuture =
    futureMonad.handleErrorWith(failedFutureKind, recoveryHandler);
```

###  [IdExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/id/IdExample.java)

This example introduces the **Identity (Id) monad**. The `Id` monad is the simplest monad; it wraps a value without adding any computational context. It is primarily used to make generic code that works with any monad also work with simple, synchronous values.

* **Key Concept**: The `Id` monad represents a direct, synchronous computation. It wraps a value, and its `flatMap` operation simply applies the function to the value.
* **Demonstrates**:
  * Wrapping a plain value into an `Id`.
  * Using `map` and `flatMap` on an `Id` value.
  * Its use as the underlying monad in a monad transformer stack, effectively turning `StateT<S, IdKind.Witness, A>` into `State<S, A>`.

```java
// flatMap on Id simply applies the function to the wrapped value.
Id<String> idFromOf = Id.of(42);
Id<String> directFlatMap = idFromOf.flatMap(i -> Id.of("Direct FlatMap: " + i));
// directFlatMap.value() is "Direct FlatMap: 42"
```

### [IOExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/io/IOExample.java)

This example introduces the **IO monad**, which is used to encapsulate side effects like reading from the console, writing to a file, or making a network request.

* **Key Concept**: The `IO` monad describes a computation that can perform side effects. These effects are only executed when the `IO` action is explicitly run.
* **Demonstrates**:
  * Creating `IO` actions that describe side effects using `delay`.
  * Composing `IO` actions using `map` and `flatMap` to create more complex programs.
  * Executing `IO` actions to produce a result using `unsafeRunSync`.

```java
// Create an IO action to read a line from the console
Kind<IOKind.Witness, String> readLine = IO_OP.delay(() -> {
    System.out.print("Enter your name: ");
    try (Scanner scanner = new Scanner(System.in)) {
        return scanner.nextLine();
    }
});

// Execute the action to get the result
String name = IO_OP.unsafeRunSync(readLine);
```

### [LazyExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/lazy/LazyExample.java)

This example covers the **Lazy monad**. It's used to defer a computation until its result is explicitly requested. The result is then memoized (cached) so the computation is only executed once.

* **Key Concept**: A `Lazy` computation is not executed when it is created, but only when `force()` is called. The result (or exception) is then stored for subsequent calls.
* **Demonstrates**:
  * Creating a deferred computation with `LAZY.defer()`.
  * Forcing evaluation with `LAZY.force()`.
  * How results are memoized, preventing re-computation.
  * Using `map` and `flatMap` to build chains of lazy operations.

```java
// Defer a computation
java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
Kind<LazyKind.Witness, String> deferredLazy = LAZY.defer(() -> {
    counter.incrementAndGet();
    return "Computed Value";
});

// The computation only runs when force() is called
System.out.println(LAZY.force(deferredLazy)); // counter becomes 1
System.out.println(LAZY.force(deferredLazy)); // result is from cache, counter remains 1
```

### [ListMonadExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/list/ListMonadExample.java)

This example demonstrates the **List monad**. It shows how to perform monadic operations on a standard Java `List`, treating it as a context that can hold zero or more results.

* **Key Concept**: The `List` monad represents non-deterministic computation, where an operation can produce multiple results.
* **Demonstrates**:
  * Wrapping a `List` into a `Kind<ListKind.Witness, A>`.
  * Using `map` to transform every element in the list.
  * Using `flatMap` to apply a function that returns a list to each element, and then flattening the result.

```java
// A function that returns multiple results for even numbers
Function<Integer, Kind<ListKind.Witness, Integer>> duplicateIfEven =
    n -> {
      if (n % 2 == 0) {
        return LIST.widen(Arrays.asList(n, n * 10));
      } else {
        return LIST.widen(List.of()); // Empty list for odd numbers
      }
    };

// flatMap applies the function and flattens the resulting lists
Kind<ListKind.Witness, Integer> flatMappedKind = listMonad.flatMap(duplicateIfEven, numbersKind);
```

### [MaybeExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe/MaybeExample.java)

This example covers the **Maybe monad**. `Maybe` is a type that represents an optional value, similar to Java's `Optional`, but designed to be used as a monad within the Higher-Kinded-J ecosystem. It has two cases: `Just<A>` (a value is present) and `Nothing` (a value is absent).

* **Key Concept**: The `Maybe` monad provides a way to represent computations that may or may not return a value, explicitly handling the absence of a value.
* **Demonstrates**:
  * Creating `Just` and `Nothing` instances.
  * Using `map` to transform a `Just` value.
  * Using `flatMap` to chain operations that return a `Maybe`.
  * Handling the `Nothing` case using `handleErrorWith`.

```java
// flatMap to parse a string, which can result in Nothing
Function<String, Kind<MaybeKind.Witness, Integer>> parseString =
    s -> {
      try {
        return MAYBE.just(Integer.parseInt(s));
      } catch (NumberFormatException e) {
        return MAYBE.nothing();
      }
    };
```

### [OptionalExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional/OptionalExample.java)

This example introduces the **Optional monad**. It demonstrates how to wrap Java's `Optional` in a `Kind` to work with it in a monadic way, allowing for chaining of operations and explicit error handling.

* **Key Concept**: The `Optional` monad provides a way to represent computations that may or may not return a value.
* **Demonstrates**:
  * Wrapping `Optional` instances into a `Kind<OptionalKind.Witness, A>`.
  * Using `map` to transform the value inside a present `Optional`.
  * Using `flatMap` to chain operations that return `Optional`.
  * Using `handleErrorWith` to provide a default value when the `Optional` is empty.

```java
// Using flatMap to parse a string to an integer, which may fail
Function<String, Kind<OptionalKind.Witness, Integer>> parseToIntKind =
    s -> {
      try {
        return OPTIONAL.widen(Optional.of(Integer.parseInt(s)));
      } catch (NumberFormatException e) {
        return OPTIONAL.widen(Optional.empty());
      }
    };

Kind<OptionalKind.Witness, Integer> parsedPresent =
    optionalMonad.flatMap(parseToIntKind, presentInput);
```

### [ReaderExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader/ReaderExample.java)

This example introduces the **Reader monad**. The `Reader` monad is a pattern used for dependency injection. It represents a computation that depends on some configuration or environment of type `R`.

* **Key Concept**: A `Reader<R, A>` represents a function `R -> A`. It allows you to "read" from a configuration `R` to produce a value `A`, without explicitly passing the configuration object everywhere.
* **Demonstrates**:
  * Creating `Reader` computations that access parts of a configuration object.
  * Using `flatMap` to chain computations where one step depends on the result of a previous step and the shared configuration.
  * Running the final `Reader` computation by providing a concrete configuration object.

```java
// A Reader that depends on the AppConfig environment
Kind<ReaderKind.Witness<AppConfig>, String> connectionStringReader =
    readerMonad.flatMap(
        dbUrl -> READER.reader(config -> dbUrl + "?apiKey=" + config.apiKey()),
        getDbUrl // Another Reader that gets the DB URL
    );

// The computation is only run when a config is provided
String connectionString = READER.runReader(connectionStringReader, productionConfig);
```

###  [StateExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state/StateExample.java), [BankAccountWorkflow.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state/BankAccountWorkflow.java)

These examples demonstrate the **State monad**. The `State` monad is used to manage state in a purely functional way, abstracting away the boilerplate of passing state from one function to the next.

* **Key Concept**: A `State<S, A>` represents a function `S -> (S, A)`, which takes an initial state and returns a new state and a computed value. The monad chains these functions together.
* **Demonstrates**:
  * Creating stateful actions like `push`, `pop`, `deposit`, and `withdraw`.
  * Using `State.modify` to update the state and `State.inspect` to read from it.
  * Composing these actions into a larger workflow using a `For` comprehension.
  * Running the final computation with an initial state to get the final state and result.

```java
// A stateful action to withdraw money, returning a boolean success flag
public static Function<BigDecimal, Kind<StateKind.Witness<AccountState>, Boolean>> withdraw(String description) {
    return amount -> STATE.widen(
        State.of(currentState -> {
            if (currentState.balance().compareTo(amount) >= 0) {
                // ... update state and return success
                return new StateTuple<>(true, updatedState);
            } else {
                // ... update state with rejection and return failure
                return new StateTuple<>(false, updatedState);
            }
        })
    );
}
```

### [TryExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trymonad/TryExample.java)

This example introduces the **Try monad**. It's designed to encapsulate computations that can throw exceptions, making error handling more explicit and functional.

* **Key Concept**: A `Try` represents a computation that results in either a `Success` containing a value or a `Failure` containing an exception.
* **Demonstrates**:
  * Creating `Try` instances for successful and failed computations.
  * Using `map` and `flatMap` to chain operations, where exceptions are caught and wrapped in a `Failure`.
  * Using `recover` and `recoverWith` to handle failures and provide alternative values or computations.

```java
// A function that returns a Try, succeeding or failing based on the input
Function<Integer, Try<Double>> safeDivide =
    value ->
        (value == 0)
            ? Try.failure(new ArithmeticException("Div by zero"))
            : Try.success(10.0 / value);

// flatMap chains the operation, propagating failure
Try<Double> result = input.flatMap(safeDivide);
```

### [ValidatedMonadExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/validated/ValidatedMonadExample.java)

This example showcases the **Validated applicative functor**. While it has a `Monad` instance, it's often used as an `Applicative` to accumulate errors. This example, however, focuses on its monadic (fail-fast) behaviour.

* **Key Concept**: `Validated` is used for validation scenarios where you want either to get a valid result or to accumulate validation errors.
* **Demonstrates**:
  * Creating `Valid` and `Invalid` instances.
  * Using `flatMap` to chain validation steps, where the first `Invalid` result short-circuits the computation.
  * Using `handleErrorWith` to recover from a validation failure.

```java
// A validation function that returns a Kind-wrapped Validated
Function<String, Kind<ValidatedKind.Witness<List<String>>, Integer>> parseToIntKind =
    s -> {
      try {
        return validatedMonad.of(Integer.parseInt(s)); // Lifts to Valid
      } catch (NumberFormatException e) {
        return validatedMonad.raiseError(Collections.singletonList("'" + s + "' is not a number."));
      }
    };
```

### [WriterExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/writer/WriterExample.java)

This example introduces the **Writer monad**. The `Writer` monad is used for computations that need to produce a log or accumulate a secondary value alongside their primary result.

* **Key Concept**: A `Writer<W, A>` represents a computation that returns a primary result `A` and an accumulated value `W` (like a log), where `W` must have a `Monoid` instance to define how values are combined.
* **Demonstrates**:
  * Using `tell` to append to the log.
  * Using `flatMap` to sequence computations, where both the results and logs are combined automatically.
  * Running the final `Writer` to extract both the final value and the fully accumulated log.

```java
// An action that performs a calculation and logs what it did
Function<Integer, Kind<WriterKind.Witness<String>, Integer>> addAndLog =
    x -> {
      int result = x + 10;
      String logMsg = "Added 10 to " + x + " -> " + result + "; ";
      return WRITER.widen(new Writer<>(logMsg, result));
    };

// The monad combines the logs from each step automatically
Kind<WriterKind.Witness<String>, String> finalComputation = writerMonad.flatMap(
    intermediateValue -> multiplyAndLogToString.apply(intermediateValue),
    addAndLog.apply(5)
);
```

### [GenericExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/GenericExample.java)

This example showcases how to write **generic functions** that can operate on any `Functor` (or `Monad`) by accepting the type class instance as a parameter. This is a core concept of higher-kinded polymorphism.

* **Key Concept**: By abstracting over the computational context (`F`), you can write code that works for `List`, `Optional`, `IO`, or any other type that has a `Functor` instance.
* **Demonstrates**:
  * Writing a generic `mapWithFunctor` function that takes a `Functor<F>` instance and a `Kind<F, A>`.
  * Calling this generic function with different monad instances (`ListMonad`, `OptionalMonad`) and their corresponding `Kind`-wrapped types.

```java
// A generic function that works for any Functor F
public static <F, A, B> Kind<F, B> mapWithFunctor(
    Functor<F> functorInstance, // The type class instance
    Function<A, B> fn,
    Kind<F, A> kindBox) { // The value in its context
    return functorInstance.map(fn, kindBox);
}

// Calling it with a List
Kind<ListKind.Witness, Integer> doubledList = mapWithFunctor(listMonad, doubleFn, listKind);

// Calling it with an Optional
Kind<OptionalKind.Witness, Integer> doubledOpt = mapWithFunctor(optionalMonad, doubleFn, optKind);
```
### [ProfunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/profunctor/ProfunctorExample.java)

This example demonstrates the **Profunctor** type class using `FunctionProfunctor`, showing how to build flexible, adaptable data transformation pipelines.

* **Key Concept**: A `Profunctor` is contravariant in its first parameter and covariant in its second, making it perfect for adapting both the input and output of functions.
* **Demonstrates**:
    * Using `lmap` to adapt function inputs (contravariant mapping)
    * Using `rmap` to adapt function outputs (covariant mapping)
    * Using `dimap` to adapt both input and output simultaneously
    * Building real-world API adapters and validation pipelines
    * Creating reusable transformation chains

```java
// Original function: String length calculator
Function<String, Integer> stringLength = String::length;

// Adapt the input: now works with integers!
Kind2<FunctionKind.Witness, Integer, Integer> intToLength =
    profunctor.lmap(Object::toString, lengthFunction);

// Adapt the output: now returns formatted strings!
Kind2<FunctionKind.Witness, String, String> lengthToString =
    profunctor.rmap(len -> "Length: " + len, lengthFunction);

// Adapt both input and output in one operation
Kind2<FunctionKind.Witness, Integer, String> fullTransform =
    profunctor.dimap(Object::toString, len -> "Result: " + len, lengthFunction);
```


---

## Monad Transformers

These examples show how to use **monad transformers** (`EitherT`, `MaybeT`, `OptionalT`, `ReaderT`, `StateT`) to combine the capabilities of different monads.

### [EitherTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/either_t/EitherTExample.java)

* **Key Concept**: `EitherT` stacks the `Either` monad on top of another monad `F`, creating a new monad `EitherT<F, L, R>` that handles both the effects of `F` and the failure logic of `Either`.
* **Scenario**: Composing synchronous validation (`Either`) with an asynchronous operation (`CompletableFuture`) in a single, clean workflow.

### [MaybeTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/maybe_t/MaybeTExample.java)

* **Key Concept**: `MaybeT` stacks the `Maybe` monad on top of another monad `F`. This is useful for asynchronous operations that may not return a value.
* **Scenario**: Fetching a userLogin and their preferences from a database asynchronously, where each step might not find a result.

### [OptionalTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/optional_t/OptionalTExample.java)

* **Key Concept**: `OptionalT` stacks `Optional` on top of another monad `F`, creating `OptionalT<F, A>` to handle asynchronous operations that may return an empty result.
* **Scenario**: Fetching a userLogin and their preferences from a database asynchronously, where each step might not find a result.

### [ReaderTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTExample.java), [ReaderTUnitExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncExample.java),  [ReaderTAsyncUnitExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/reader_t/ReaderTAsyncUnitExample.java)

* **Key Concept**: `ReaderT` combines the `Reader` monad (for dependency injection) with an outer monad `F`. This allows for computations that both read from a shared environment and have effects of type `F`.
* **Scenario**: An asynchronous workflow that depends on a configuration object (`AppConfig`) to fetch and process data.

### [StateTExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTExample.java), [StateTStackExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/state_t/StateTStackExample.java)

* **Key Concept**: `StateT` combines the `State` monad with an outer monad `F`. This is for stateful computations that also involve effects from `F`.
* **Scenario**: A stateful stack that can fail (using `Optional` as the outer monad), where popping from an empty stack results in `Optional.empty()`.

---

For more advanced patterns combining State with other monads, see the [Order Processing Example](order-walkthrough.md) which demonstrates `StateT` with `EitherT`.

---

**Previous:** [Usage Guide](usage-guide.md)
**Next:** [Quick Reference](quick_reference.md)

# Reader Monad - Managed Dependencies and Configuration

## Purpose

The **Reader monad** is a functional programming pattern primarily used for managing dependencies and context propagation in a clean and composable way. Imagine you have multiple functions or components that all need access to some shared, read-only environment, such as:

* Configuration settings (database URLs, API keys, feature flags).
* Shared resources (thread pools, connection managers).
* User context (user ID, permissions).

Instead of explicitly passing this environment object as an argument to every single function (which can become cumbersome and clutter signatures), the Reader monad encapsulates computations that *depend* on such an environment.

A `Reader<R, A>` represents a computation that, when provided with an environment of type `R`, will produce a value of type `A`. It essentially wraps a function `R -> A`.

The benefits of using the Reader monad include:

1. **Implicit Dependency Injection:** The environment (`R`) is implicitly passed along the computation chain. Functions defined within the Reader context automatically get access to the environment when needed, without needing it explicitly in their signature.
2. **Composability:** Reader computations can be easily chained together using standard monadic operations like `map` and `flatMap`.
3. **Testability:** Dependencies are managed explicitly when the final Reader computation is run, making it easier to provide mock environments or configurations during testing.
4. **Code Clarity:** Reduces the need to pass configuration objects through multiple layers of functions.

In `Higher-Kinded-J`, the Reader monad pattern is implemented via the `Reader<R, A>` interface and its corresponding HKT simulation types (`ReaderKind`, `ReaderKindHelper`) and type class instances (`ReaderMonad`, `ReaderApplicative`, `ReaderFunctor`).

## Structure

![reader_monad.svg](./images/puml/reader_monad.svg)

## The `Reader<R, A>` Type

The core type is the `Reader<R, A>` functional interface:

```java
@FunctionalInterface
public interface Reader<R, A> {
  @Nullable A run(@NonNull R r); // The core function: Environment -> Value

  // Static factories
  static <R, A> @NonNull Reader<R, A> of(@NonNull Function<R, A> runFunction);
  static <R, A> @NonNull Reader<R, A> constant(@Nullable A value);
  static <R> @NonNull Reader<R, R> ask();

  // Instance methods (for composition)
  default <B> @NonNull Reader<R, B> map(@NonNull Function<? super A, ? extends B> f);
  default <B> @NonNull Reader<R, B> flatMap(@NonNull Function<? super A, ? extends Reader<R, ? extends B>> f);
}
```

* `run(R r)`: Executes the computation by providing the environment `r` and returning the result `A`.
* `of(Function<R, A>)`: Creates a `Reader` from a given function.
* `constant(A value)`: Creates a `Reader` that ignores the environment and always returns the provided value.
* `ask()`: Creates a `Reader` that simply returns the environment itself as the result.
* `map(Function<A, B>)`: Transforms the result `A` to `B`*after* the reader is run, without affecting the required environment `R`.
* `flatMap(Function<A, Reader<R, B>>)`: Sequences computations. It runs the first reader, uses its result `A` to create a *second* reader (`Reader<R, B>`), and then runs that second reader with the *original* environment `R`.

## Reader Components

To integrate `Reader` with Higher-Kinded-J:

* **`ReaderKind<R, A>`:** The marker interface extending `Kind<ReaderKind.Witness<R>, A>`. The witness type `F` is `ReaderKind.Witness<R>` (where `R` is fixed for a given monad instance), and the value type `A` is the result type of the reader.
* **`ReaderKindHelper`:** The utility class with static methods:
  * `wrap(Reader<R, A>)`: Converts a `Reader` to `ReaderKind<R, A>`.
  * `unwrap(Kind<ReaderKind.Witness<R>, A>)`: Converts `ReaderKind` back to `Reader`. Throws `KindUnwrapException` if the input is invalid.
  * `reader(Function<R, A>)`: Factory method to create a `ReaderKind` from a function.
  * `constant(A value)`: Factory method for a `ReaderKind` returning a constant value.
  * `ask()`: Factory method for a `ReaderKind` that returns the environment.
  * `runReader(Kind<ReaderKind.Witness<R>, A> kind, R environment)`: The primary way to execute a `ReaderKind` computation by providing the environment.

## Type Class Instances (`ReaderFunctor`, `ReaderApplicative`, `ReaderMonad`)

These classes provide the standard functional operations for `ReaderKind.Witness<R>`, allowing you to treat `Reader` computations generically within Higher-Kinded-J:

* **`ReaderFunctor<R>`:** Implements `Functor<ReaderKind.Witness<R>>`. Provides the `map` operation.
* **`ReaderApplicative<R>`:** Extends `ReaderFunctor<R>` and implements `Applicative<ReaderKind.Witness<R>>`. Provides `of` (lifting a value) and `ap` (applying a wrapped function to a wrapped value).
* **`ReaderMonad<R>`:** Extends `ReaderApplicative<R>` and implements `Monad<ReaderKind.Witness<R>>`. Provides `flatMap` for sequencing computations that depend on previous results while implicitly carrying the environment `R`.

You typically instantiate `ReaderMonad<R>` for the specific environment type `R` you are working with.

## How to Use
## Problem: ManagingConfiguration

- [ReaderExample.java](../../src/main/java/org/higherkindedj/example/basic/reader/ReaderExample.java)

### 1. Define Your Environment

```java
// Example Environment: Application Configuration
record AppConfig(String databaseUrl, int timeoutMillis, String apiKey) {}
```

### 2. Create Reader Computations

Use `ReaderKindHelper` factory methods:

```java
import static org.higherkindedj.hkt.reader.ReaderKindHelper.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.reader.ReaderKind;

// Reader that retrieves the database URL from the config
Kind<ReaderKind.Witness<AppConfig>, String> getDbUrl = reader(AppConfig::databaseUrl);

// Reader that retrieves the timeout
Kind<ReaderKind.Witness<AppConfig>, Integer> getTimeout = reader(AppConfig::timeoutMillis);

// Reader that returns a constant value, ignoring the environment
Kind<ReaderKind.Witness<AppConfig>, String> getDefaultUser = constant("guest");

// Reader that returns the entire configuration environment
Kind<ReaderKind.Witness<AppConfig>, AppConfig> getConfig = ask();
```

### 3. Get the `ReaderMonad` Instance

Instantiate the monad for your specific environment type `R`.

```java
import org.higherkindedj.hkt.reader.ReaderMonad;

// Monad instance for computations depending on AppConfig
ReaderMonad<AppConfig> readerMonad = new ReaderMonad<>();
```

### 4. Compose Computations using `map` and `flatMap`

Use the methods on the `readerMonad` instance.

```java
// Example 1: Map the timeout value
Kind<ReaderKind.Witness<AppConfig>, String> timeoutMessage = readerMonad.map(
    timeout -> "Timeout is: " + timeout + "ms",
    getTimeout // Input: Kind<ReaderKind.Witness<AppConfig>, Integer>
);

// Example 2: Use flatMap to get DB URL and then construct a connection string (depends on URL)
Function<String, Kind<ReaderKind.Witness<AppConfig>, String>> buildConnectionString =
    dbUrl -> reader( // <- We return a new Reader computation
        config -> dbUrl + "?apiKey=" + config.apiKey() // Access apiKey via the 'config' env
    );

Kind<ReaderKind.Witness<AppConfig>, String> connectionStringReader = readerMonad.flatMap(
    buildConnectionString, // Function: String -> Kind<ReaderKind.Witness<AppConfig>, String>
    getDbUrl               // Input: Kind<ReaderKind.Witness<AppConfig>, String>
);

// Example 3: Combine multiple values using mapN (from Applicative)
Kind<ReaderKind.Witness<AppConfig>, String> dbInfo = readerMonad.map2(
    getDbUrl,
    getTimeout,
    (url, timeout) -> "DB: " + url + " (Timeout: " + timeout + ")"
);
```

### 5. Run the Computation

Provide the actual environment using `ReaderKindHelper.runReader`:

```java
AppConfig productionConfig = new AppConfig("prod-db.example.com", 5000, "prod-key-123");
AppConfig stagingConfig = new AppConfig("stage-db.example.com", 10000, "stage-key-456");

// Run the composed computations with different environments
String prodTimeoutMsg = runReader(timeoutMessage, productionConfig);
String stageTimeoutMsg = runReader(timeoutMessage, stagingConfig);

String prodConnectionString = runReader(connectionStringReader, productionConfig);
String stageConnectionString = runReader(connectionStringReader, stagingConfig);

String prodDbInfo = runReader(dbInfo, productionConfig);
String stageDbInfo = runReader(dbInfo, stagingConfig);

// Get the raw config using ask()
AppConfig retrievedProdConfig = runReader(getConfig, productionConfig);


System.out.println("Prod Timeout: " + prodTimeoutMsg);           // Output: Timeout is: 5000ms
System.out.println("Stage Timeout: " + stageTimeoutMsg);         // Output: Timeout is: 10000ms
System.out.println("Prod Connection: " + prodConnectionString); // Output: prod-db.example.com?apiKey=prod-key-123
System.out.println("Stage Connection: " + stageConnectionString);// Output: stage-db.example.com?apiKey=stage-key-456
System.out.println("Prod DB Info: " + prodDbInfo);               // Output: DB: prod-db.example.com (Timeout: 5000)
System.out.println("Stage DB Info: " + stageDbInfo);             // Output: DB: stage-db.example.com (Timeout: 10000)
System.out.println("Retrieved Prod Config: " + retrievedProdConfig); // Output: AppConfig[databaseUrl=prod-db.example.com, timeoutMillis=5000, apiKey=prod-key-123]
```

Notice how the functions (`buildConnectionString`, the lambda in `map2`) don't need `AppConfig` as a parameter, but they can access it when needed within the `reader(...)` factory or implicitly via `flatMap` composition. The configuration is only provided once at the end when `runReader` is called.

## Key Points:

The Reader monad (`Reader<R, A>`, `ReaderKind`, `ReaderMonad`) in `Higher-Kinded-J` provides a functional approach to dependency injection and configuration management. It allows you to define computations that depend on a read-only environment `R` without explicitly passing `R` everywhere. By using the HKT simulation and the `ReaderMonad`, you can compose these dependent functions cleanly using `map` and `flatMap`, providing the actual environment only once when the final computation is executed via `runReader`. This leads to more modular, testable, and less cluttered code when dealing with shared context.

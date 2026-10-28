# Glossary: Effect Paths & Effect Handlers

~~~admonish info title="What This Page Covers"
- The Effect Path API and the Free-monad effect-handler machinery.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## BoundSet

**Definition:** A generated container holding `Bound` instances for each effect algebra in a composition. Each `Bound` provides smart constructors that automatically inject operations into the correct position in the composed `EitherF` chain. Obtained by constructing the generated `*Support.BoundSet`, whose components are each algebra's `Bound` at the composed witness.

**Example:**
<!-- verify -->
```java
var functor = AppEffectsSupport.functor(
    ConsoleOpFunctor.instance(), DbOpFunctor.instance());
var bounds = new AppEffectsSupport.BoundSet(
    ConsoleOpOps.boundTo(AppEffectsSupport.injectConsole(), functor),
    DbOpOps.boundTo(AppEffectsSupport.injectDb(), functor));

var console = bounds.console();  // ConsoleOp, bound to the composed witness
var db = bounds.db();            // DbOp, bound to the same one

// Java has no alias for that witness, so the program is a `var`
var program =
    console.readLine(Function.identity())
        .flatMap(name -> db.save(name, Function.identity()));
```

**Related:** [@ComposeEffects](#composeeffects), [Inject](#inject)

---

## CompletableFuturePath

**Definition:** A path over Java's `CompletableFuture<A>`: asynchronous work that completes with a value or fails with an exception. It adds recovery, timeouts and retry to the future. `join()` waits for the result, and `run()` returns the underlying future.

**Factories:** `Path.future(future)`, `Path.futureCompleted(value)`, `Path.futureFailed(exception)` and `Path.futureAsync(supplier)`.

**Operations:** `map`, `via`, `recover`, `withTimeout`, `completeOnTimeout`, `race` and `withRetry`.

**Related:** [CompletableFuture Monad](../monads/cf_monad.md), [Migration Cookbook (Recipe 4)](../effect/migration_cookbook.md#recipe-4-completablefuture-nesting-to-completablefuturepath), [Path](#path), [VTaskPath](#vtaskpath), [Retry](concurrency.md#retry)

---

## @ComposeEffects

**Definition:** An annotation processor that generates composition infrastructure for multiple effect algebras: `Inject` instances, a composed `Functor`, and a `BoundSet` for program construction. Annotate a record whose fields are declared `Class<XOp<?>>`, each naming an `@EffectAlgebra`.

**Example:**
<!-- verify -->
```java
@ComposeEffects
public record AppEffects(
    Class<ConsoleOp<?>> console,
    Class<DbOp<?>> db) {}
// Generates: AppEffectsSupport with typed injectConsole()/injectDb(), functor(...), BoundSet
```

**Related:** [EitherF](#eitherf), [Inject](#inject), [BoundSet](#boundset)

---

## Continuation-Passing Style (CPS)

**Definition:** A pattern where each operation record includes a `Function` parameter (conventionally named `k`) that transforms the operation's natural result type to the generic type parameter `A`. If a `Charge` operation naturally produces a `ChargeResult`, the continuation `Function<ChargeResult, A>` lets callers transform that result inline. This is the same idea as `CompletableFuture.thenApply`: chain a transformation onto a value that does not exist yet.

**Example:**
```java
// The continuation k transforms ChargeResult → A
record Charge<A>(Money amount, PaymentMethod method,
    Function<ChargeResult, A> k) implements PaymentGatewayOp<A> { }

// Using Function.identity() returns the natural result type directly
Free<G, ChargeResult> charge =
    gateway.charge(amount, method, Function.identity());

// Using a custom continuation transforms the result inline
Free<G, TransactionId> txId =
    gateway.charge(amount, method, result -> result.transactionId());
```

**Why CPS:** Enables proper Java type inference at call sites. Without the continuation, the compiler cannot infer the relationship between the operation's result and the program's type parameter.

**Related:** [Effect Algebra](#effect-algebra), [mapK](#mapk)

---

## Effect

**Definition:** A computational context that represents a value alongside some additional behaviour or outcome. Effects model computations that may fail, produce optional results, perform side effects, or require asynchronous execution. Rather than throwing exceptions or returning null, effects make these behaviours explicit in the type system.

**Common Effect Types in Higher-Kinded-J:**
- **Maybe** - Computation that may produce no result
- **Either** - Computation that may fail with typed error information
- **Try** - Computation that may throw an exception
- **IO** - Computation that performs side effects
- **Validated** - Computation that accumulates multiple errors

**Example:**
```java
// Without effects: hidden failure modes
User getUser(String id) {
    // Might return null? Throw exception? Which exceptions?
}

// With effects: explicit about what can happen
Either<UserError, User> getUser(String id) {
    // Returns Right(user) on success, Left(error) on failure
}

Maybe<User> findUser(String id) {
    // Returns Just(user) if found, Nothing if not found
}

IO<User> loadUser(String id) {
    // Describes a side effect that will load the user when run
}
```

**Why Effects Matter:**
- Make failure modes visible in type signatures
- Enable composition of operations that may fail
- Replace scattered try-catch blocks with unified error handling
- Allow reasoning about code behaviour from types alone

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming)

---

## Effect Algebra

**Definition:** A sealed interface annotated with `@EffectAlgebra` where each permitted record represents a domain operation. The Java equivalent of "algebraic effects" from functional programming. Each operation carries its parameters and a continuation function that transforms the operation's natural result type.

**Example:**
<!-- verify -->
```java
@EffectAlgebra
public sealed interface ConsoleOp<A>
    permits ConsoleOp.ReadLine, ConsoleOp.PrintLine {

  <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

  record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new ReadLine<>(k.andThen(f));
    }
  }

  record PrintLine<A>(String message, Function<Unit, A> k) implements ConsoleOp<A> {
    @Override
    public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
      return new PrintLine<>(message, k.andThen(f));
    }
  }
}
```

**When to Use:** When you need to define a closed set of domain operations that can be interpreted in multiple ways (production, testing, audit). The sealed modifier guarantees exhaustive handling in interpreters.

**Related:** [Continuation-Passing Style](#continuation-passing-style-cps), [Free Monad](#free-monad), [@EffectAlgebra](#effectalgebra)

---

## Effect Path

**Definition:** The primary API in Higher-Kinded-J for working with effects. Effect Paths wrap core effect types (Either, Maybe, Try, IO, Validated) and provide a fluent, railway-oriented interface for composition. Each Path type offers consistent operations (`map`, `via`, `recover`, `run`) regardless of the underlying effect.

**Available Path Types:**
| Path Type | Underlying Effect | Purpose |
|-----------|-------------------|---------|
| `EitherPath<E, A>` | `Either<E, A>` | Typed error handling |
| `MaybePath<A>` | `Maybe<A>` | Optional values |
| `TryPath<A>` | `Try<A>` | Exception handling |
| `IOPath<A>` | `IO<A>` | Deferred side effects |
| `ValidationPath<E, A>` | `Validated<E, A>` | Error accumulation |
| `TrampolinePath<A>` | `Trampoline<A>` | Stack-safe recursion |
| `CompletableFuturePath<A>` | `CompletableFuture<A>` | Async operations |

**Example:**
<!-- verify -->
```java
// Create paths using the Path factory
EitherPath<AppError, User> userPath = Path.either(findUser(id));
MaybePath<Config> configPath = Path.maybe(findConfig());
TryPath<Data> dataPath = Path.tryOf(() -> parseJson(input));
IOPath<String> ioPath = Path.io(() -> readFile(path));

// All paths share the same fluent API
EitherPath<AppError, String> result = userPath
    .map(User::name)                    // Transform success value
    .via(name -> validateName(name))    // Chain dependent operation
    .recover(err -> "Anonymous");       // Handle errors

// Execute and get result
String name = result.run().fold(err -> "Unknown", value -> value);
```

**Related:** [Path](#path), [via](#via), [recover](#recover), [Effect Path Documentation](../effect/ch_intro.md)

---

## Effect-Optics Bridge

**Definition:** The integration layer that connects Effect Paths with the Focus DSL, allowing seamless composition of effectful computations and immutable data navigation. The bridge enables focusing into data structures retrieved from effects and lifting optic operations into effectful contexts.

**How It Works:**
```
EFFECTS DOMAIN                    OPTICS DOMAIN
══════════════                    ═════════════
EitherPath<E, User>  ──┐    ┌──  FocusPath<User, Address>
TryPath<Config>      ──┤    ├──  Lens<Config, Settings>
IOPath<Data>         ──┘    └──  Traversal<Data, Item>
                        │  │
                        ▼  ▼
                   .focus(optic)
                        │
                        ▼
              UNIFIED COMPOSITION
```

**Example:**
<!-- verify -->
```java
// Fetch user (effect) then navigate to nested data (optics)
EitherPath<AppError, String> city = userService.findById(userId)  // Effect: fetch
    .focus(UserFocus.address())                                 // Optics: navigate
    .focus(AddressFocus.city())                                 // Optics: deeper
    .map(String::toUpperCase);                                  // Transform

// Modify nested data within an effectful context
EitherPath<AppError, User> updated = userService.findById(userId)
    .map(user -> UserFocus.address().via(AddressFocus.postcode())
        .modify(String::toUpperCase, user));

// Combine multiple effect sources with optic navigation. `focus` narrows through a single-target
// optic; a traversal reads its targets out, and the effect runs over them.
EitherPath<AppError, Report> report =
    Path.either(loadCompany(id))
        .map(company -> CompanyFocus.departments().getAll(company))  // Optics: every department
        .via(departments -> loadMetrics(departments))                // Effect over them
        .map(metrics -> generateReport(metrics));
```

**Benefits:**
- No boilerplate for null checks during navigation
- Optic failures (missing optional values) integrate with effect failures
- Single vocabulary (`map`, `via`, `focus`) for both domains
- Type-safe composition across effect and structure boundaries

**Related:** [Effect Path](#effect-path), [Focus DSL](optics.md#focus-dsl), [FocusPath](optics.md#focuspath)

---

## @EffectAlgebra

**Definition:** An annotation processor that generates five classes per annotated sealed interface:

| Generated Class | Purpose |
|---|---|
| `*Kind` | HKT marker + Witness |
| `*KindHelper` | widen/narrow conversions |
| `*Functor` | Functor instance (delegates to `mapK`) |
| `*Ops` | Smart constructors + `Bound` inner class |
| `*Interpreter` | Abstract interpreter skeleton |

**When to Use:** Annotate every sealed interface that defines a set of domain operations. The generated classes eliminate boilerplate and provide type-safe construction and interpretation.

**Related:** [Effect Algebra](#effect-algebra), [@ComposeEffects](#composeeffects)

---

## EitherF

**Definition:** A sum type lifted to the type constructor level. Used to compose multiple effect algebras into a single combined type via right-nesting. The `@ComposeEffects` annotation generates this composition automatically.

**Example:**
<!-- verify -->
```java
// Right-nested composition of four effect algebras:
// EitherF<PaymentGatewayOp,
//   EitherF<FraudCheckOp,
//     EitherF<LedgerOp,
//       NotificationOp>>>
@ComposeEffects
public record PaymentEffects(
    Class<PaymentGatewayOp<?>> gateway,
    Class<FraudCheckOp<?>> fraud,
    Class<LedgerOp<?>> ledger,
    Class<NotificationOp<?>> notification) {}
```

**When to Use:** When your program uses operations from multiple effect algebras. `@ComposeEffects` generates the `EitherF` nesting, `Inject` instances, and a `BoundSet` automatically.

**Related:** [Inject](#inject), [Effect Algebra](#effect-algebra)

---

## EitherOrBothPath

**Definition:** A path over `EitherOrBoth<L, A>`: a success that can also carry non-fatal warnings. A `Left` is fatal, a `Right` is clean, and a `Both` is a value with warnings.

**Factories:** `Path.right(value, semigroup)`, `Path.left(error, semigroup)` and `Path.both(warnings, value, semigroup)`. The shortcuts `Path.rightNel(value)`, `Path.leftNel(error)` and `Path.bothNel(warning, value)` use a `NonEmptyList` for the warnings.

**Operations:** `map`, `via`, `warnings()`, `isBoth()`, `toEitherPathDroppingWarnings()` and `toEitherPathFailingOnWarnings()`.

**Related:** [EitherOrBothPath documentation](../effect/path_either_or_both.md), [Path](#path), [EitherOrBoth](data-effects.md#eitherorboth), [Semigroup](type-classes.md#semigroup)

---

## EitherPath

**Definition:** A path over `Either<E, A>` for a computation that succeeds with a value or fails with a typed error `E`. The first failure stops the chain, and `run()` returns the `Either`.

**Factories:** `Path.right(value)`, `Path.left(error)`, and `Path.either(either)`.

**Related:** [EitherPath documentation](../effect/path_either.md), [Path](#path), [Either](data-effects.md#either), [Railway-Oriented Programming](#railway-oriented-programming), [recover](#recover)

---

## foldMap

**Definition:** The method that interprets a Free monad program by traversing its instruction tree, applying a natural transformation (interpreter) to each `Suspend` node, and combining results using the target monad's `flatMap`. Stack-safe via internal trampolining.

**Example:**
<!-- verify -->
```java
var interpreter = Interpreters.combine(consoleInterp, dbInterp);
IO<String> result = IOKindHelper.IO_OP.narrow(
    program.foldMap(interpreter, Instances.monad(io())));
```

**How It Works:**
- `Pure(a)` returns the value wrapped in the target monad
- `Suspend(instruction)` applies the interpreter to the instruction
- `FlatMapped(program, continuation)` interprets the sub-program, then flatMaps the continuation

**Related:** [Free Monad](#free-monad), [Interpreter](#interpreter-effect-handler)

---

## Free Monad

**Definition:** A data structure (`Free<F, A>`) that represents a program as a tree of instructions. There are five main node types: `Pure` (return a value), `Suspend` (an instruction to execute), `FlatMapped` (sequence two programs), `HandleError` (error recovery), and `Ap` (applicative sub-expression). Because the program is data, it can be inspected, transformed, and interpreted in different ways.

**Example:**
<!-- verify -->
```java
// Building a Free program from effect algebra operations
var program =
    console.readLine(Function.identity())
        .flatMap(name -> console.printLine("Hello, " + name, Function.identity())
        .flatMap(_ -> Free.pure("Done")));

// The program is a tree:
//   FlatMapped
//     Suspend[ReadLine]
//     λ(name) → FlatMapped
//                 Suspend[PrintLine("Hello, " + name)]
//                 λ(_) → Pure("Done")
```

**When to Use:** When you need to build programs that can be interpreted in multiple ways, inspected before execution, or composed from multiple effect algebras.

**Related:** [foldMap](#foldmap), [Effect Algebra](#effect-algebra), [EitherF](#eitherf)

---

## FreeApPath

**Definition:** A Path over `FreeAp<F, A>`: an applicative program whose structure can be analysed before it runs. It has no `via`, because no step may depend on another's result.

**Factories:** `Path.freeApPure(a, functor)`, `Path.freeApLift(fa, functor)` and `Path.freeAp(freeAp, functor)`.

**Operations:** `map`, `peek`, `zipWith` and `zipWith3`; `foldMap(interpreter, monad)` interprets it into a `GenericPath`, and `toFreeAp()` returns the `FreeAp`.

**Related:** [FreeApPath documentation](../effect/path_freeap.md), [Path](#path), [Free Applicative](type-classes.md#free-applicative), [FreePath](#freepath), [Interpreter (Effect Handler)](#interpreter-effect-handler)

---

## FreePath

**Definition:** A Path over `Free<F, A>`: a program written as data, to be run later by an interpreter. It is the way to build a DSL with the Path API.

**Factories:** `Path.freePure(a, functor)`, `Path.freeLift(fa, functor)` and `Path.free(free, functor)`.

**Operations:** `map`, `via`, `then`, `recover`, `attempt()`; `foldMap(interpreter, monad)` interprets it into a `GenericPath`, and `toFree()` returns the `Free`. `recover` and `attempt()` catch an error only when the target monad is a `MonadError`.

**Related:** [FreePath documentation](../effect/path_free.md), [Path](#path), [Free Monad](#free-monad), [Interpreter (Effect Handler)](#interpreter-effect-handler), [foldMap](#foldmap)

---

## GenericPath

**Definition:** A Path over any monad you supply, as `Kind<F, A>` with its `Monad<F>`. It is the escape hatch for a type that has no Path of its own.

**Factories:** `Path.generic(kind, monad)` and `Path.genericPure(a, monad)`. Both take a plain `Monad`, so `recover` on the result throws. `GenericPath.of(kind, monadError)` builds one that can recover.

**Operations:** `map`, `via`, `recover`, `supportsRecovery()`, `toMaybePath(narrower)`, `toEitherPath(narrower)`; `runKind()` returns the `Kind`.

**Related:** [GenericPath documentation](../effect/path_generic.md), [Path](#path), [Monad](type-classes.md#monad), [MonadError](type-classes.md#monaderror), [mapK](#mapk)

---

## IdPath

**Definition:** A path for a pure value wrapped in the identity type `Id<A>`. It always contains a non-null value and never fails.

**Example:**
```java
IdPath<String> name = Path.id("Alice");
String value = name.get();
```

**Factories:** `Path.id(value)` and `Path.idPath(id)`.

**Related:** [IdPath documentation](../effect/path_id.md), [Path](#path), [Monad](type-classes.md#monad)

---

## Inject

**Definition:** A type class witnessing that one effect algebra `F` can be embedded into a composed effect type `G` (a right-nested `EitherF` chain). Generated by `@ComposeEffects`. Provides the `inj` method that wraps an instruction in the appropriate `EitherF` position.

**When to Use:** Automatically generated and used by the `BoundSet` smart constructors. You rarely interact with `Inject` directly.

**Related:** [EitherF](#eitherf), [BoundSet](#boundset)

---

## Interpreter (Effect Handler)

**Definition:** A natural transformation that converts effect algebra instructions into a target monad (e.g., `IO` for production, `Id` for testing). Extends the abstract skeleton generated by `@EffectAlgebra`. Multiple interpreters are combined using `Interpreters.combine()`.

**Example:**
<!-- verify -->
```java
public class IOConsoleInterpreter extends ConsoleOpInterpreter<IOKind.Witness> {

  private final Scanner scanner = new Scanner(System.in);

  @Override
  protected <A> Kind<IOKind.Witness, A> handleReadLine(ConsoleOp.ReadLine<A> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> op.k().apply(scanner.nextLine())));
  }

  @Override
  protected <A> Kind<IOKind.Witness, A> handlePrintLine(ConsoleOp.PrintLine<A> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> { System.out.println(op.message()); return op.k().apply(Unit.INSTANCE); }));
  }
}
```

**When to Use:** Write one interpreter per effect algebra per execution mode. A production interpreter targets `IO`; a test interpreter targets `Id` for pure, synchronous execution.

**Related:** [foldMap](#foldmap), [Effect Algebra](#effect-algebra)

---

## IOPath

**Definition:** A path over `IO<A>`: a side effect that is described now and run later. Nothing happens until `unsafeRun()` is called. `run()` returns the underlying `IO` without running it.

**Factories:** `Path.io(supplier)`, `Path.ioRunnable(runnable)`, `Path.ioPure(value)` and `Path.ioPath(io)`.

**Operations:** `map`, `via`, `handleError`, `guarantee`, `withRetry` and `withTimeout`.

**Related:** [IOPath documentation](../effect/path_io.md), [Path](#path), [IO](data-effects.md#io), [Retry](concurrency.md#retry), [Bracket Pattern](concurrency.md#bracket-pattern)

---

## LazyPath

**Definition:** A path for a deferred, memoised computation. The value is evaluated on first access and cached for subsequent access.

**Example:**
```java
LazyPath<Integer> answer = Path.lazyDefer(() -> 6 * 7);
int value = answer.get();
```

**Factories:** `Path.lazyDefer(supplier)` and `Path.lazyNow(value)`.

**Related:** [Advanced Topics](../effect/advanced_topics.md), [Path](#path), [IO](data-effects.md#io)

---

## Leg

**Definition:** One of the routes a value can travel through a railway-modelled computation: a segment of the journey, as in a leg of a relay. Used especially at the Spring boundary, where each distinct route from a controller's return value to an HTTP response is a named leg: a `Left(DomainError)` travels the *Either leg* to its mapped error status, a generic `Invalid` travels the *validation leg* to 400, and an `Invalid` made entirely of located `FieldError`s takes [the 422 leg](../spring/spring_boot_integration.md#the-422-leg): one response naming every bad field. Which leg a response travels is decided by the value's shape, like a railway switch routing a train.

**Related:** [Railway-Oriented Programming](#railway-oriented-programming)

---

## mapK

**Definition:** A method on each effect algebra record that composes the continuation function with a new transformation. The generated Functor delegates to `mapK` rather than using unsafe casts. Analogous to `Stream.map` but applied to a single instruction rather than a collection.

**Example:**
```java
record ReadLine<A>(Function<String, A> k) implements ConsoleOp<A> {
  @Override
  public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
    return new ReadLine<>(k.andThen(f));  // Compose continuations
  }
}
```

**Why It Matters:** Enables the generated Functor to map over instructions without cast-through. Each record knows how to transform its own continuation, keeping type safety throughout the chain.

**Related:** [Effect Algebra](#effect-algebra), [Continuation-Passing Style](#continuation-passing-style-cps)

---

## MaybePath

**Definition:** A path over `Maybe<A>` for a value that may be absent. Absence is its only failure, so it has no error value, and `run()` returns the `Maybe`.

**Factories:** `Path.just(value)`, `Path.nothing()`, and `Path.maybe(value)` for either a nullable value or an existing `Maybe`.

**Related:** [MaybePath documentation](../effect/path_maybe.md), [Path](#path), [Maybe](data-effects.md#maybe), [OptionalPath](#optionalpath), [EitherPath](#eitherpath)

---

## OptionalPath

**Definition:** A path that wraps Java's `Optional<A>`, making standard-library optional values composable with the Effect Path API.

**Example:**
```java
OptionalPath<String> name = Path.present("Alice");
Optional<String> value = name.run();
```

**Factories:** `Path.optional(optional)` and `Path.present(value)`.

**Related:** [OptionalPath documentation](../effect/path_optional.md), [Path](#path), [Maybe](data-effects.md#maybe)

---

## Path

**Definition:** The unified factory class for creating Effect Paths. Provides static methods to wrap values and computations in the appropriate Path type, serving as the main entry point to the Effect Path API.

**Factory Methods:**
```java
// Maybe paths
Path.maybe(nullableValue)           // Wrap nullable, null becomes Nothing
Path.just(nonNullValue)             // Wrap known-present value
Path.nothing()                      // Empty MaybePath

// Either paths
Path.either(eitherValue)            // Wrap existing Either
Path.right(value)                   // Success EitherPath
Path.left(error)                    // Failure EitherPath

// Try paths
Path.tryOf(() -> riskyOperation())  // Wrap exception-throwing code
Path.success(value)                 // Successful TryPath
Path.failure(exception)             // Failed TryPath

// IO paths
Path.io(() -> sideEffect())         // Wrap side-effecting code
Path.ioPure(value)                  // Pure value in IO context

// Validation paths
Path.valid(value, semigroup)        // Valid result
Path.invalid(error, semigroup)      // Invalid with error
Path.validNel(value)                // Valid, accumulating into a NonEmptyList
Path.invalidNel(error)              // Invalid, accumulating into a NonEmptyList
```

**Example:**
<!-- verify -->
```java
// Building a complete workflow using Path factory
public EitherPath<OrderError, Receipt> processOrder(OrderRequest request) {
    return Path.maybe(customerRepository.find(request.customerId()))
        .<OrderError>toEitherPath(new OrderError.CustomerNotFound())
        .via(customer -> Path.either(validateOrder(request, customer)))
        .via(validated -> Path.tryOf(() -> paymentService.charge(validated))
            .toEitherPath(OrderError.PaymentFailed::new))
        .map(payment -> createReceipt(request, payment));
}
```

**Related:** [Effect Path](#effect-path), [via](#via), [recover](#recover), [Effect Path Documentation](../effect/ch_intro.md)

---

## ProgramAnalyser

**Definition:** A utility that traverses a Free monad program tree without executing it, counting instructions, error recovery points, and parallel scopes. All counts are lower bounds because `FlatMapped` continuations are opaque functions that cannot be inspected without a value.

**Example:**
<!-- verify -->
```java
ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

analysis.suspendCount();     // Number of instructions
analysis.recoveryPoints();   // Number of HandleError nodes
analysis.parallelScopes();   // Number of Ap nodes
analysis.hasOpaqueRegions(); // FlatMapped continuations present
```

**When to Use:** Before executing programs in production, to estimate cost, count external calls, or verify structural properties. Useful for audit logging and capacity planning.

**Related:** [Free Monad](#free-monad)

---

## Railway-Oriented Programming

**Definition:** A programming model where computations flow along two parallel tracks: a success track and a failure track. Operations automatically route values to the appropriate track, and failures propagate without manual checking at each step.

**The Railway Metaphor:**
```
SUCCESS TRACK  ═══════╦═══════╦═══════╦═══════► Result
                      ║       ║       ║
                   step 1  step 2  step 3
                      ║       ║       ║
FAILURE TRACK  ───────╨───────╨───────╨───────► Error
```

When a step succeeds, the value continues on the success track. When a step fails, execution switches to the failure track and subsequent steps are bypassed.

**Example:**
<!-- verify -->
```java
// Traditional approach: manual error checking at each step
BigDecimal balanceOrThrow(String id) {
    User user = findUserOrNull(id);
    if (user == null) throw new IllegalStateException("User not found");
    Account account = getAccountOrNull(user);
    if (account == null) throw new IllegalStateException("Account not found");
    if (!account.isActive()) throw new IllegalStateException("Account inactive");
    return account.getBalance();
}

// Railway-oriented: automatic track switching (lookupUser returns Either<AppError, User>)
EitherPath<AppError, BigDecimal> balance =
    Path.either(lookupUser(id))
        .via(user -> getAccount(user))
        .via(account -> validateActive(account))
        .map(account -> account.getBalance());
// Failures propagate automatically; no manual checks needed
```

**Key Operations:**
- `map` - Transform value on success track (stays on same track)
- `via` (flatMap) - Chain to next operation that may switch tracks
- `recover` - Switch from failure track back to success track

**Benefits:**
- Eliminates nested if-else and try-catch pyramids
- Business logic reads top-to-bottom
- Error handling is consistent and composable
- Type system ensures all failure cases are addressed

**Related:** [Effect Path](#effect-path), [via](#via), [recover](#recover)

---

## ReaderPath

**Definition:** A path over `Reader<R, A>`: a computation that needs an environment `R` to produce its value. It gives dependency injection without a framework. Nothing runs until `run(env)` supplies the environment.

**Factories:** `Path.reader(reader)`, `Path.readerPure(value)`, `Path.ask()` and `Path.asks(f)`.

**Operations:** `map`, `via`, `local(f)` and `magnify(...)`. `run(env)` returns the value.

**Related:** [ReaderPath documentation](../effect/advanced_effects.md#readerpath-the-environment-you-inherit), [Path](#path), [IdPath](#idpath), [Reader](data-effects.md#reader), [Reader Documentation](../monads/reader_monad.md)

---

## recover

**Definition:** The error recovery operation on Effect Paths, equivalent to `handleErrorWith` on MonadError. Allows switching from the failure track back to the success track by providing an alternative value or computation.

**Signature:** `Path<E, A>.recover(Function<E, A> handler) → Path<E, A>`

**Example:**
<!-- verify -->
```java
// Simple recovery with default value
EitherPath<AppError, Config> config = loadConfig()
    .recover(error -> Config.defaults());

// Recovery that inspects the error
EitherPath<ApiError, User> user = fetchUser(id)
    .recover(error -> switch (error) {
        case ApiError.NotFound _ -> User.guest();
        case ApiError.RateLimited _ -> User.cached(id);
        // Every variant is handled, so the switch needs no default
        case ApiError.Unavailable _ -> User.guest();
    });

// Recovery with a new Path (recoverWith)
EitherPath<AppError, Data> data = primarySource()
    .recoverWith(error -> fallbackSource());  // Try alternative on failure

// Partial recovery - only handle specific errors
EitherPath<ApiError, User> partial = fetchUser(id)
    .recoverWith(error ->
        error instanceof ApiError.RateLimited
            ? fetchUser(id)                 // one retry
            : Path.left(error));            // stay on the failure track
```

**When To Use:**
- Providing default values when operations fail
- Implementing fallback strategies
- Converting errors to success values
- Selective error recovery based on error type

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming), [via](#via)

---

## TrampolinePath

**Definition:** A path over `Trampoline<A>`: a stack-safe recursive computation. Deep recursion runs in a loop instead of growing the call stack. `run()` returns the value.

**Factories:** `Path.trampolineDone(value)`, `Path.trampolineDefer(supplier)` and `Path.trampoline(trampoline)`.

**Operations:** `map`, `via` and `toLazyPath()`.

**Related:** [TrampolinePath documentation](../effect/path_trampoline.md), [Path](#path), [LazyPath](#lazypath), [Trampoline Documentation](../monads/trampoline_monad.md)

---

## via

**Definition:** The chaining operation on Effect Paths, equivalent to `flatMap` on monads. Applies a function that returns a new Path, allowing dependent operations to be sequenced. If the current Path is on the failure track, `via` is bypassed and the failure propagates.

**Signature:** `Path<E, A>.via(Function<A, Path<E, B>> f) → Path<E, B>`

**Example:**
<!-- verify -->
```java
// Each step depends on the previous result
EitherPath<AppError, Order> orderPath =
    Path.<AppError, String>right(userId)
        .via(id -> loadUser(id))           // Returns EitherPath<AppError, User>
        .via(user -> getCart(user))        // Returns EitherPath<AppError, Cart>
        .via(cart -> validateCart(cart))   // Returns EitherPath<AppError, ValidatedCart>
        .via(valid -> createOrder(valid)); // Returns EitherPath<AppError, Order>

// Compare to map (which doesn't chain Paths):
EitherPath<AppError, String> mapped = userPath.map(user -> user.name());
// map: A -> B (simple transformation)
// via: A -> Path<E, B> (operation that may fail)
```

**When To Use:**
- Chaining operations where each step may fail
- Sequencing dependent computations
- Building pipelines of effectful operations

**Contrast with map:**
- `map(A -> B)` - Transform the value, stay on same track
- `via(A -> Path<E, B>)` - Chain to operation that may switch tracks

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming), [recover](#recover)

---

## VResultPath

**Definition:** A first-class railway for `VTask<Either<E, A>>`: asynchronous work, run on a virtual thread, that can fail with a typed domain error `E`. It composes `VTaskPath` (async) and `EitherPath` (typed error) into one path, so neither `Kind` ceremony nor a hand-rolled `EitherT` bridge ever surfaces. It speaks the full family vocabulary (`map`/`via`/`then`, `mapError`/`recover`/`recoverWith`/`bimap`).

**Example:**
<!-- verify -->
```java
VResultPath<OrderError, OrderResult> process(OrderRequest request) {
    return validateAddress(request.address())      // VResultPath<OrderError, Address>
        .via(address -> reserveStock(address))    // chain a fallible async step
        .recover(err -> OrderResult.rejected(err));  // handle the typed Left
}

VTask<Either<OrderError, OrderResult>> carrier = process(req).run();    // execute on a virtual thread
```

**Factories:** `Path.vresultRight`/`vresultLeft` (decided), `Path.vresultEither` (lift a decided `Either`), `Path.vresult` (lift a `VTask<Either<E, A>>`), and `Path.vresultDefer` (defer the decision itself).

**Two channels, kept apart:** a business failure travels in the value channel as `Left`, whilst an unexpected defect stays on the `VTask` channel. The outcome-aware structured-concurrency combinators (`firstSuccess`, `allSucceed`, `allSucceedAccumulating`, `withTimeout`, `bracketOutcome`) preserve that split, and the `with*` resilience combinators are railway-aware.

**Related:** [VResultPath](../effect/path_vresult.md), [Path](#path), [Railway-Oriented Programming](#railway-oriented-programming), [Either](data-effects.md#either), [Resilience Combinators](concurrency.md#resilience-combinators)

---

## VTaskPath

**Definition:** A path over `VTask<A>`: a deferred computation that runs on a virtual thread. It suits I/O-bound work that needs to scale. `unsafeRun()` runs it, `runAsync()` returns a future, and `run()` returns the underlying `VTask`.

**Factories:** `Path.vtask(callable)`, `Path.vtaskExec(runnable)`, `Path.vtaskPure(value)`, `Path.vtaskFail(throwable)` and `Path.vtaskPath(vtask)`.

**Operations:** `map`, `via`, `handleError`, `timeout`, `withRetry`, `withCircuitBreaker` and `withBulkhead`.

**Related:** [VTaskPath documentation](../effect/path_vtask.md), [Path](#path), [VResultPath](#vresultpath), [Virtual Thread](concurrency.md#virtual-thread), [Resilience Combinators](concurrency.md#resilience-combinators)

---

## WithStatePath

**Definition:** A path over `State<S, A>`: a computation that threads a state through its steps. Each step can read or replace the state. Nothing runs until it is given an initial state.

**Factories:** `Path.state(state)`, `Path.statePure(value)`, `Path.getState()`, `Path.setState(newState)` and `Path.modifyState(f)`.

**Operations:** `map` and `via`. `run(s0)` returns a `StateTuple`, `evalState(s0)` returns the value and `execState(s0)` returns the final state.

**Related:** [WithStatePath documentation](../effect/advanced_effects.md#withstatepath-computation-with-memory), [Path](#path), [State](data-effects.md#state), [State Documentation](../monads/state_monad.md)

---

## WriterPath

**Definition:** A path over `Writer<W, A>`: a computation that returns a value together with an accumulated log. A `Monoid<W>` combines the log entries as the steps run.

**Factories:** `Path.writer(writer, monoid)`, `Path.writerPure(value, monoid)` and `Path.tell(log, monoid)`.

**Operations:** `map` and `via`; `censor(f)` rewrites the log, and `listen(additionalLog)` appends to it. `value()` and `written()` read the two parts, and `run()` returns the `Writer`.

**Related:** [WriterPath documentation](../effect/advanced_effects.md#writerpath-accumulating-output), [Path](#path), [Writer](data-effects.md#writer), [Monoid](type-classes.md#monoid), [Writer Documentation](../monads/writer_monad.md)

---

**Previous:** [Data & Effect Types](data-effects.md)
**Next:** [Optics & Mapping](optics.md)

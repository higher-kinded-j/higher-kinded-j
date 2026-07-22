# Glossary: Effect Paths & Effect Handlers

~~~admonish info title="What This Page Covers"
- The Effect Path API and the Free-monad effect-handler machinery.
- Part of the [Glossary](../glossary.md); see it for the other categories.
~~~

## BoundSet

**Definition:** A generated container holding `Bound` instances for each effect algebra in a composition. Each `Bound` provides smart constructors that automatically inject operations into the correct position in the composed `EitherF` chain. Obtained from the generated `*Wiring.boundSet()` method.

**Example:**
```java
var bounds = AppEffectsWiring.boundSet();
var console = bounds.console();  // Bound<ComposedType> for ConsoleOp
var db = bounds.db();            // Bound<ComposedType> for DbOp

Free<ComposedType, String> program =
    console.readLine(Function.identity())
        .flatMap(name -> db.save(name, Function.identity()));
```

**Related:** [@ComposeEffects](#composeeffects), [Inject](#inject)

---

## @ComposeEffects

**Definition:** An annotation processor that generates composition infrastructure for multiple effect algebras: `Inject` instances, a composed `Functor`, and a `BoundSet` for program construction. Annotate a record whose fields are `Class<?>` references to the effect algebras being composed.

**Example:**
```java
@ComposeEffects
public record AppEffects(
    Class<ConsoleOp<?>> console,
    Class<DbOp<?>> db) {}
// Generates: AppEffectsWiring with boundSet(), interpret(), etc.
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
```java
// Create paths using the Path factory
EitherPath<Error, User> userPath = Path.either(findUser(id));
MaybePath<Config> configPath = Path.maybe(loadConfig());
TryPath<Data> dataPath = Path.tryOf(() -> parseJson(input));
IOPath<String> ioPath = Path.io(() -> readFile(path));

// All paths share the same fluent API
EitherPath<Error, String> result = userPath
    .map(User::name)                    // Transform success value
    .via(name -> validateName(name))    // Chain dependent operation
    .recover(err -> "Anonymous");       // Handle errors

// Execute and get result
String name = result.run().orElse("Unknown");
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
```java
// Fetch user (effect) then navigate to nested data (optics)
EitherPath<Error, String> city = userService.findById(userId)  // Effect: fetch
    .focus(UserFocus.address())                                 // Optics: navigate
    .focus(AddressFocus.city())                                 // Optics: deeper
    .map(String::toUpperCase);                                  // Transform

// Modify nested data within an effectful context
EitherPath<Error, User> updated = userService.findById(userId)
    .focusAndModify(
        UserFocus.address().andThen(AddressFocus.postcode()),
        postcode -> postcode.toUpperCase()
    );

// Combine multiple effect sources with optic navigation
EitherPath<Error, Report> report =
    Path.of(loadCompany(id))
        .focus(CompanyFocus.departments())     // Traverse to departments
        .via(dept -> loadMetrics(dept.id()))   // Effect for each
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

## foldMap

**Definition:** The method that interprets a Free monad program by traversing its instruction tree, applying a natural transformation (interpreter) to each `Suspend` node, and combining results using the target monad's `flatMap`. Stack-safe via internal trampolining.

**Example:**
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
```java
// Building a Free program from effect algebra operations
Free<G, String> program =
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

## Inject

**Definition:** A type class witnessing that one effect algebra `F` can be embedded into a composed effect type `G` (a right-nested `EitherF` chain). Generated by `@ComposeEffects`. Provides the `inj` method that wraps an instruction in the appropriate `EitherF` position.

**When to Use:** Automatically generated and used by the `BoundSet` smart constructors. You rarely interact with `Inject` directly.

**Related:** [EitherF](#eitherf), [BoundSet](#boundset)

---

## Interpreter (Effect Handler)

**Definition:** A natural transformation that converts effect algebra instructions into a target monad (e.g., `IO` for production, `Id` for testing). Extends the abstract skeleton generated by `@EffectAlgebra`. Multiple interpreters are combined using `Interpreters.combine()`.

**Example:**
```java
public class IOConsoleInterpreter extends ConsoleOpInterpreter<IOKind.Witness> {
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
Path.of(nullableValue)              // Wrap nullable as EitherPath

// Try paths
Path.tryOf(() -> riskyOperation())  // Wrap exception-throwing code
Path.success(value)                 // Successful TryPath
Path.failure(exception)             // Failed TryPath

// IO paths
Path.io(() -> sideEffect())         // Wrap side-effecting code
Path.ioOf(value)                    // Pure value in IO context

// Validation paths
Path.valid(value)                   // Valid result
Path.invalid(error)                 // Invalid with error
```

**Example:**
```java
// Building a complete workflow using Path factory
public EitherPath<OrderError, Receipt> processOrder(OrderRequest request) {
    return Path.maybe(customerRepository.find(request.customerId()))
        .toEitherPath(() -> new OrderError.CustomerNotFound())
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
```java
// Traditional approach: manual error checking at each step
User user = findUser(id);
if (user == null) return error("User not found");
Account account = getAccount(user);
if (account == null) return error("Account not found");
if (!account.isActive()) return error("Account inactive");
return success(account.getBalance());

// Railway-oriented: automatic track switching
EitherPath<Error, BigDecimal> balance =
    Path.of(findUser(id))
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

## recover

**Definition:** The error recovery operation on Effect Paths, equivalent to `handleErrorWith` on MonadError. Allows switching from the failure track back to the success track by providing an alternative value or computation.

**Signature:** `Path<E, A>.recover(Function<E, A> handler) → Path<E, A>`

**Example:**
```java
// Simple recovery with default value
EitherPath<Error, Config> config = loadConfig()
    .recover(error -> Config.defaults());

// Recovery that inspects the error
EitherPath<ApiError, User> user = fetchUser(id)
    .recover(error -> switch (error) {
        case NotFound _ -> User.guest();
        case RateLimited _ -> User.cached(id);
        default -> throw new RuntimeException(error);  // Re-throw unrecoverable
    });

// Recovery with a new Path (recoverWith)
EitherPath<Error, Data> data = primarySource()
    .recoverWith(error -> fallbackSource());  // Try alternative on failure

// Partial recovery - only handle specific errors
EitherPath<Error, Value> result = operation()
    .recover(error -> {
        if (error instanceof Retryable) {
            return retryOperation();
        }
        throw error;  // Propagate non-retryable errors
    });
```

**When To Use:**
- Providing default values when operations fail
- Implementing fallback strategies
- Converting errors to success values
- Selective error recovery based on error type

**Related:** [Effect Path](#effect-path), [Railway-Oriented Programming](#railway-oriented-programming), [via](#via)

---

## via

**Definition:** The chaining operation on Effect Paths, equivalent to `flatMap` on monads. Applies a function that returns a new Path, allowing dependent operations to be sequenced. If the current Path is on the failure track, `via` is bypassed and the failure propagates.

**Signature:** `Path<E, A>.via(Function<A, Path<E, B>> f) → Path<E, B>`

**Example:**
```java
// Each step depends on the previous result
EitherPath<Error, Order> orderPath =
    Path.of(userId)
        .via(id -> findUser(id))           // Returns EitherPath<Error, User>
        .via(user -> getCart(user))        // Returns EitherPath<Error, Cart>
        .via(cart -> validateCart(cart))   // Returns EitherPath<Error, ValidatedCart>
        .via(valid -> createOrder(valid)); // Returns EitherPath<Error, Order>

// Compare to map (which doesn't chain Paths):
EitherPath<Error, String> mapped = userPath.map(user -> user.name());
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
```java
VResultPath<OrderError, OrderResult> process(OrderRequest request) {
    return Path.vresultDefer(() -> validateAddress(request.address()))  // VResultPath<OrderError, Address>
        .via(address -> reserveStock(address))                          // chain a fallible async step
        .recover(err -> OrderResult.rejected(err));                     // handle the typed Left
}

VTask<Either<OrderError, OrderResult>> carrier = process(req).run();    // execute on a virtual thread
```

**Factories:** `Path.vresultRight`/`vresultLeft` (decided), `Path.vresultEither` (lift a decided `Either`), `Path.vresult` (lift a `VTask<Either<E, A>>`), and `Path.vresultDefer` (defer the decision itself).

**Two channels, kept apart:** a business failure travels in the value channel as `Left`, whilst an unexpected defect stays on the `VTask` channel. The outcome-aware structured-concurrency combinators (`firstSuccess`, `allSucceed`, `allSucceedAccumulating`, `withTimeout`, `bracketOutcome`) preserve that split, and the `with*` resilience combinators are railway-aware.

**Related:** [VResultPath](../effect/path_vresult.md), [Path](#path), [Railway-Oriented Programming](#railway-oriented-programming), [Either](data-effects.md#either), [Resilience Combinators](concurrency.md#resilience-combinators)


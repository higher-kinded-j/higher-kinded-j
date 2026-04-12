---
name: hkj-effects
description: "Build HKJ effect handlers: @EffectAlgebra sealed interfaces, @ComposeEffects composition, Free monad programs, interpreters, natural transformations, foldMap, program analysis, multiple interpretation modes (production, test, audit, dry-run), mock-free testing"
---

# Higher-Kinded-J Effect Handlers

You are helping a developer build effect handlers using HKJ's algebraic-effect-style programming. Effect handlers separate *description* from *execution*: business logic becomes a data structure (Free monad program), and interpreters decide how to run it.

## When to load supporting files

- If the user wants a **complete worked example**, load `reference/payment-example.md`
- If the user asks about **interpreter patterns** (production, test, audit, replay), load `reference/interpreter-patterns.md`
- If the user is **unfamiliar with Free monads**, load `reference/free-monad-basics.md`
- For **Effect Path API** (MaybePath, EitherPath, etc.), suggest `/hkj-guide`
- For **architecture patterns** (functional core / imperative shell), suggest `/hkj-arch`

---

## When to Use Effect Handlers vs Simpler Approaches

| Scenario | Recommendation |
|----------|---------------|
| Simple error handling | Use Effect Path API (`EitherPath`, `TryPath`) |
| Single execution mode only | Use dependency injection (Spring DI) |
| Need to inspect workflow before execution | **Effect Handlers** |
| Multiple interpretation modes (prod, test, audit) | **Effect Handlers** |
| Need compile-time exhaustive operation checking | **Effect Handlers** |
| Mock-free testing | **Effect Handlers** (via `Id` monad interpreters) |
| Performance-critical hot paths | Measure first; Free monad allocates intermediate objects |
| Teams new to FP | Start with Effect Paths and Optics, add handlers later |

---

## Step 1: Define an Effect Algebra

An effect algebra is a `sealed interface` where each permitted `record` represents a domain operation. Operations use continuation-passing style (CPS): a `Function` parameter maps the natural result to `A`.

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

### What `@EffectAlgebra` Generates

| Generated Class | Purpose |
|----------------|---------|
| `{Op}Kind` | HKT marker type + `Witness` inner class |
| `{Op}KindHelper` | `widen()` / `narrow()` conversions |
| `{Op}Functor` | Functor instance (delegates to `mapK`) |
| `{Op}Ops` | Smart constructors + `Bound` inner class |
| `{Op}Interpreter<M>` | Abstract interpreter skeleton with one `handle{Variant}` method per record |

### Pattern for Each Record

```java
record OperationName<A>(
    ParamType1 param1,           // operation parameters
    ParamType2 param2,
    Function<ResultType, A> k    // CPS continuation (ResultType = natural result)
) implements MyOp<A> {
    @Override
    public <B> MyOp<B> mapK(Function<? super A, ? extends B> f) {
        return new OperationName<>(param1, param2, k.andThen(f));
    }
}
```

**Key**: The `Function<ResultType, A> k` parameter is the continuation. `ResultType` is what the operation naturally produces. `A` is the generic type parameter after applying transformations.

---

## Step 2: Compose Multiple Effects

When your program uses multiple effect algebras:

```java
@ComposeEffects
public record AppEffects(
    Class<ConsoleOp<?>> console,
    Class<DbOp<?>> db,
    Class<LogOp<?>> log) {}
```

### What `@ComposeEffects` Generates

| Generated Class | Purpose |
|----------------|---------|
| `{Effects}Support` | `injectXxx()` methods, `functor()`, `boundSet()` |
| `{Effects}BoundSet` | Record holding `Bound` instances for all effect types |

The processor takes the record name and appends `Support` (e.g., `AppEffects` -> `AppEffectsSupport`).

---

## Step 3: Write Programs

Programs use `Bound` instances from the generated `BoundSet`. Pass `Function.identity()` as the continuation to get the natural result type:

```java
var bounds = AppEffectsSupport.boundSet();
var console = bounds.console();
var db = bounds.db();

Free<AppEffectsSupport.ComposedType, String> program =
    console.printLine("Enter name:", Function.identity())
        .flatMap(_ -> console.readLine(Function.identity()))
        .flatMap(name -> db.save(name, Function.identity()))
        .map(id -> "Saved with id: " + id);
```

---

## Step 4: Write Interpreters

Each interpreter extends the generated abstract skeleton. Handle each operation variant by computing the result and applying `op.k()`:

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

### Test Interpreter (Id Monad, No Mocks)

```java
public class TestConsoleInterpreter extends ConsoleOpInterpreter<IdKind.Witness> {
    private final Queue<String> inputs;
    private final List<String> outputs = new ArrayList<>();

    @Override
    protected <A> Kind<IdKind.Witness, A> handleReadLine(ConsoleOp.ReadLine<A> op) {
        return IdKindHelper.ID.widen(Id.of(op.k().apply(inputs.poll())));
    }

    @Override
    protected <A> Kind<IdKind.Witness, A> handlePrintLine(ConsoleOp.PrintLine<A> op) {
        outputs.add(op.message());
        return IdKindHelper.ID.widen(Id.of(op.k().apply(Unit.INSTANCE)));
    }
}
```

---

## Step 5: Combine and Interpret

`Interpreters.combine()` takes interpreter instances directly (not inject/interpreter pairs):

```java
// Combine interpreters for composed effects
var interpreter = Interpreters.combine(consoleInterp, dbInterp, logInterp);

// Run with target monad
IO<String> result = IOKindHelper.IO_OP.narrow(
    program.foldMap(interpreter, IOMonad.INSTANCE));

// Execute
String value = result.unsafeRunSync();
```

### For Testing (Id Monad)

```java
var testInterpreter = Interpreters.combine(
    testConsoleInterp, testDbInterp, testLogInterp);

Id<String> result = IdKindHelper.ID.narrow(
    program.foldMap(testInterpreter, IdMonad.INSTANCE));

assertEquals("expected", result.value());
```

---

## EffectBoundary: Simplified Execution

`EffectBoundary` encapsulates the interpret-and-execute pattern, replacing manual `foldMap` + `narrow` + `unsafeRun` ceremony:

```java
// Create a boundary with composed interpreters
EffectBoundary<AppEffects> boundary = EffectBoundary.of(
    Interpreters.combine(consoleInterp, dbInterp, logInterp));

// Execute a program (multiple options)
String result       = boundary.run(program);           // Synchronous, may throw
Try<String> safe    = boundary.runSafe(program);       // Synchronous, safe
IOPath<String> io   = boundary.runIO(program);         // Deferred IO (for Spring controllers)
CompletableFuture<String> async = boundary.runAsync(program); // Virtual thread async

// Lift raw IO actions into the effect algebra for composition
Free<AppEffects, String> lifted = boundary.embed(IO.delay(() -> "value"));
```

### Key Methods

| Method | Returns | When to Use |
|--------|---------|-------------|
| `run(program)` | `A` | Direct execution, may throw |
| `runSafe(program)` | `Try<A>` | Safe execution, captures exceptions |
| `runIO(program)` | `IOPath<A>` | Deferred; return from Spring controllers |
| `runAsync(program)` | `CompletableFuture<A>` | Virtual thread async execution |
| `embed(IO<A>)` | `Free<F, A>` | Lift a raw `IO` action into the effect algebra |
| `embedPath(IO<A>, Functor<F>)` | `FreePath<F, A>` | Lift a raw `IO` action as a `FreePath` |

### TestBoundary for Pure Tests

`TestBoundary` targets the `Id` monad for deterministic, no-IO testing:

```java
TestBoundary<AppEffects> testBoundary = TestBoundary.of(
    Interpreters.combine(testConsoleInterp, testDbInterp, testLogInterp));

// Pure execution: no IO, no Spring context, no network
String result = testBoundary.run(program);

// Analyse program structure before execution
ProgramAnalysis analysis = testBoundary.analyse(program);
```

For Spring integration with `@EnableEffectBoundary` and `@Interpreter` beans, see `/hkj-spring`.

---

## Program Analysis

Inspect programs before execution. `TestBoundary.analyse()` produces a `ProgramAnalysis` record describing the program's structure:

```java
TestBoundary<AppEffects> testBoundary = TestBoundary.of(
    Interpreters.combine(testInterp1, testInterp2));

ProgramAnalysis analysis = testBoundary.analyse(program);

// Inspect structure
Set<String> effects     = analysis.effectsUsed();       // Effect op class names invoked
int instructions        = analysis.totalInstructions(); // Number of Suspend nodes
int recoveryPoints      = analysis.recoveryPoints();    // Number of HandleError nodes
int applicativeBlocks   = analysis.applicativeBlocks(); // Number of Ap nodes
```

The lower-level `ProgramAnalyser` utility (in `hkj.free`) exposes `analyse()` as a static method:

```java
org.higherkindedj.hkt.free.ProgramAnalysis lowLevel =
    ProgramAnalyser.analyse(program);
// Fields: suspendCount(), recoveryPoints(), parallelScopes(), flatMapDepth(), hasOpaqueRegions()
```

---

## Error Recovery

`handleError` is an instance method on `Free` that wraps sub-programs with recovery strategies. It takes an error class and a recovery function:

```java
Free<F, A> programWithRecovery = riskyProgram.handleError(
    MyError.class,
    error -> fallbackProgram);
```

**Warning**: `handleError` only works when the target monad supports `MonadError`. With `Id` monad (which has no error channel), recovery is silently skipped.

---

## Key Warnings

1. **Monad transformer limitation**: `foldMap` uses an eager optimisation that discards monadic context for strict target monads. Use lazy outer monads (`WriterT<IO, ...>`) instead of `WriterT<Id, ...>`.
2. **CPS continuation is required**: Every record must have a `Function<ResultType, A> k` parameter and implement `mapK`.
3. **Exhaustive handling**: The generated interpreter skeleton has one abstract method per record variant. The compiler enforces that all operations are handled.

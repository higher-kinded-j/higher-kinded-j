# Effect Handler Reference

Algebraic-effect-style programming via Free monads and interpreters.

---

~~~admonish note title="Start Here"
New to effect handlers? Read the [introduction](effect_handlers_intro.md) first
for motivation, terminology, and how this fits alongside the Effect Path API and Optics.
~~~

~~~admonish example title="See It in Action"
The [Payment Processing example](../examples/payment_processing.md) demonstrates a complete
workflow with four interpretation modes: production, testing, quote, and high-risk decline.
~~~

~~~admonish info title="What You'll Learn"
- How to define effect algebras with `@EffectAlgebra`
- How to compose effects with `@ComposeEffects`
- How to write and interpret Free monad programs
- How error recovery works with `HandleError`
- How `ProgramAnalyser` inspects programs before execution
~~~

## Overview

Effect handlers in higher-kinded-j follow the "programs as data" principle: a program describes **what** to do; interpreters decide **how**. This is built on three existing foundations:

**Free monad**
: Represents programs as data structures

**Natural transformations**
: Interpreters that transform effect instructions

**EitherF**
: Composes multiple effect types into one

## Defining Effects

An effect algebra is a sealed interface where each permitted record represents an operation.
Operations use continuation-passing style (CPS): a `Function` parameter maps the natural result
type to `A`, enabling proper type inference at call sites.

~~~admonish tip title="CPS in Java Terms"
Each record carries a `Function` that transforms the operation's result, just like
`CompletableFuture.thenApply` chains a transformation onto a value that does not exist yet.
See the [terminology bridge](effect_handlers_intro.md#terminology-bridge-fp-concepts-in-java-terms) for more.
~~~

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

~~~admonish tip title="What Does mapK Do?"
The `mapK` method composes the continuation function with a new transformation. The generated
Functor delegates to `mapK` rather than using unsafe casts. Think of it as `Stream.map` applied
to a single instruction rather than a collection.
~~~

The `@EffectAlgebra` processor generates:

| Generated Class | Purpose |
|---|---|
| `ConsoleOpKind` | HKT marker + Witness |
| `ConsoleOpKindHelper` | widen/narrow conversions |
| `ConsoleOpFunctor` | Functor instance (delegates to `mapK`) |
| `ConsoleOpOps` | Smart constructors + `Bound` class |
| `ConsoleOpInterpreter` | Abstract interpreter skeleton |

## Composing Effects

For programs using multiple effects, `@ComposeEffects` generates composition infrastructure.
A `PaymentEffectsWiring` class provides inject instances, a composed functor, and a `BoundSet`:

```java
@ComposeEffects
public record AppEffects(
    Class<ConsoleOp<?>> console,
    Class<DbOp<?>> db) {}
```

## Writing Programs

Programs use `Bound` instances from the `BoundSet`. The `Function.identity()` continuation
returns the natural result type directly:

```java
var bounds = PaymentEffectsWiring.boundSet();
var console = bounds.console();
var db = bounds.db();

Free<ComposedType, String> program =
    console.readLine(Function.identity())
        .flatMap(name -> db.save(name, Function.identity()));
```

## Interpreting Programs

Each interpreter is a *natural transformation*: a function that converts DSL instructions into
actions in a target monad. Interpreters extend the generated abstract skeleton and apply the
operation's continuation `op.k()` to the computed result:

```java
public class IOConsoleInterpreter extends ConsoleOpInterpreter<IOKind.Witness> {
  @Override
  protected <A> Kind<IOKind.Witness, A> handleReadLine(ConsoleOp.ReadLine<A> op) {
    return IOKindHelper.IO_OP.widen(
        IO.delay(() -> op.k().apply(scanner.nextLine())));
  }
}
```

Interpreters are combined and used with `foldMap`:

```java
var interpreter = Interpreters.combine(consoleInterp, dbInterp);
IO<String> result = IOKindHelper.IO_OP.narrow(
    program.foldMap(interpreter, IOMonad.INSTANCE));
```

~~~admonish warning title="Monad Transformer Limitation"
`foldMap` uses an eager optimisation that discards the monadic context for strict target
monads. Interpreters targeting `Id`-based transformers (e.g. `WriterT<Id, W, A>`) will
silently lose accumulated state like log entries. Use a lazy outer monad (`WriterT<IO, ...>`)
or mutable recording interpreters instead. See the [Free Monad](../monads/free_monad.md)
chapter for details and workarounds.
~~~

## Error Recovery

`Free.HandleError` wraps sub-programs with recovery strategies:

```java
Free<G, A> safe = riskyOperation
    .handleError(Throwable.class, e -> Free.pure(defaultValue));
```

During interpretation:
- If the target monad is a `MonadError`, the handler is used on failure
- If the target monad is not a `MonadError`, the handler is silently ignored

~~~admonish warning title="Silent Ignore Behaviour"
When testing with an `Id` interpreter (which is not a `MonadError`), error recovery paths are never exercised. Verify recovery logic with error-capable interpreters like `IO` or `Try`.
~~~

## Program Analysis

`ProgramAnalyser` traverses the program tree without executing it:

```java
ProgramAnalysis analysis = ProgramAnalyser.analyse(program);

analysis.suspendCount();    // Number of instructions
analysis.recoveryPoints();  // Number of HandleError nodes
analysis.parallelScopes();  // Number of Ap nodes
analysis.hasOpaqueRegions(); // FlatMapped continuations present
```

All counts are **lower bounds**: `FlatMapped` continuations are opaque functions that cannot be inspected without a value.

~~~admonish tip title="See Also"
- [Effect Handlers Introduction](effect_handlers_intro.md): motivation, terminology, and comparison with dependency injection
- [EitherF](../monads/eitherf.md): how effects are composed
- [FreePath](path_free.md): fluent API for Free monad programs
- [Payment Processing](../examples/payment_processing.md): complete worked example
~~~

---

**Previous:** [Effect Handlers Introduction](effect_handlers_intro.md)

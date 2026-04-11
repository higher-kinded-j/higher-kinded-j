# Free Monad Basics Reference

Practical guide to Free monads for Java developers.

Source: `hkj-book/src/monads/free_monad.md`

---

## Core Idea: Programs as Data

A Free monad turns instructions into a data structure. Nothing executes until you interpret it.

```
Shopping list analogy:
  List describes what to buy    -->  Free program describes what to do
  Execute at supermarket        -->  Production interpreter (IO)
  Price check without buying    -->  Quote interpreter (Id)
  Check pantry inventory        -->  Test interpreter (Id)
```

One program. Multiple interpreters. Zero code changes between them.

---

## The Free Type

`Free<F, A>` is a sealed interface with three variants:

| Constructor | Meaning | Java Record |
|------------|---------|-------------|
| `Pure(a)` | Finished -- holds result `a` | `Free.Pure<F, A>` |
| `Suspend(fa)` | One instruction `fa` of type `F<A>` | `Free.Suspend<F, A>` |
| `FlatMapped(sub, fn)` | Sequenced: run `sub`, then apply `fn` to continue | `Free.FlatMapped<F, B, A>` |

```java
// Conceptual structure (simplified)
public sealed interface Free<F, A> {
  record Pure<F, A>(A value) implements Free<F, A> {}
  record Suspend<F, A>(Kind<F, A> instruction) implements Free<F, A> {}
  record FlatMapped<F, B, A>(Free<F, B> sub, Function<B, Free<F, A>> fn) implements Free<F, A> {}
}
```

Programs build up as nested `FlatMapped` nodes:

```
FlatMapped
  +-- Suspend(PrintLine("Name?"))
  +-- fn: _ ->
        FlatMapped
          +-- Suspend(ReadLine())
          +-- fn: name ->
                Suspend(PrintLine("Hello, " + name))
```

---

## Building a DSL: Step by Step

### Step 1: Define the Instruction Set

A sealed interface where each record is an operation:

```java
public sealed interface ConsoleOp<A> {
  record PrintLine(String text) implements ConsoleOp<Unit> {}
  record ReadLine()             implements ConsoleOp<String> {}
}
```

With `@EffectAlgebra`, use CPS (continuation-passing style) instead:

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

### Step 2: HKT Plumbing

`@EffectAlgebra` generates this automatically. Without the annotation, you need:

| Component | Purpose |
|-----------|---------|
| `ConsoleOpKind<A>` | HKT marker implementing `Kind<Witness, A>` |
| `ConsoleOpKindHelper` | `widen(ConsoleOp<A>)` and `narrow(Kind<Witness, A>)` |
| `ConsoleOpFunctor` | Functor instance for the instruction type |

### Step 3: Smart Constructors

Wrap `Free.liftF` in friendly methods:

```java
public static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
  return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(
      new ConsoleOp.PrintLine(text)), FUNCTOR);
}

public static Free<ConsoleOpKind.Witness, String> readLine() {
  return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(
      new ConsoleOp.ReadLine()), FUNCTOR);
}
```

With `@EffectAlgebra`, smart constructors are generated as `ConsoleOpOps` methods.

### Step 4: Write Programs

```java
Free<ConsoleOpKind.Witness, Unit> program =
    printLine("What is your name?")
        .flatMap(_ -> readLine()
            .flatMap(name -> printLine("Hello, " + name + "!")));
```

**Nothing has executed.** This is a data structure describing future computation.

---

## Interpretation via foldMap

`foldMap` walks the program tree and converts each instruction into a target monad:

```
Free program (tree):               foldMap walks it:
  FlatMapped                       1. Interpret Suspend -> target monad
    +-- Suspend(PrintLine("Hi"))   2. Apply continuation (flatMap fn)
    +-- fn: name ->                3. Interpret next Suspend
        Suspend(PrintLine(...))    4. Combine via target monad's flatMap
                                   5. Return final result in target monad
```

```java
// Signature
<M> Kind<M, A> foldMap(Natural<F, M> interpreter, Monad<M> monad)
```

- `Natural<F, M>` = natural transformation from instruction type F to target monad M
- `Monad<M>` = monad instance for sequencing in the target
- Stack-safe via internal Trampoline

### IO Interpreter Example

```java
public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
  Natural<ConsoleOpKind.Witness, IOKind.Witness> transform = kind -> {
    ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
    return switch (op) {
      case ConsoleOp.PrintLine p -> IOKindHelper.IO_OP.widen(
          IO.delay(() -> { System.out.println(p.text()); return Unit.INSTANCE; }));
      case ConsoleOp.ReadLine r  -> IOKindHelper.IO_OP.widen(
          IO.delay(() -> scanner.nextLine()));
    };
  };
  IO<A> io = IOKindHelper.IO_OP.narrow(program.foldMap(transform, IOMonad.INSTANCE));
  return io.unsafeRunSync();
}
```

### Test Interpreter Example

```java
public class TestInterpreter {
  private final List<String> input;
  private final List<String> output = new ArrayList<>();
  private int inputIndex = 0;

  public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
    Natural<ConsoleOpKind.Witness, IdKind.Witness> transform = kind -> {
      ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
      return switch (op) {
        case ConsoleOp.PrintLine p -> { output.add(p.text()); yield new Id<>(Unit.INSTANCE); }
        case ConsoleOp.ReadLine r  -> new Id<>(input.get(inputIndex++));
      };
    };
    return IdKindHelper.ID.<A>narrow(
        program.foldMap(transform, IdMonad.instance())).value();
  }
}
```

---

## Key Operations

| Operation | What It Does | Example |
|-----------|-------------|---------|
| `Free.pure(a)` | Wrap a value (no instruction) | `Free.pure(42)` |
| `Free.liftF(fa, functor)` | Lift one instruction into Free | `Free.liftF(widen(new ReadLine()), functor)` |
| `.flatMap(a -> ...)` | Sequence: use result of one step in the next | `readLine().flatMap(name -> printLine(name))` |
| `.map(a -> b)` | Transform result without new instruction | `readLine().map(String::toUpperCase)` |
| `.foldMap(nat, monad)` | Interpret: walk tree, apply transformation | `program.foldMap(interpreter, IOMonad.INSTANCE)` |
| `.handleError(cls, handler)` | Attach recovery for errors during interpretation | `.handleError(Throwable.class, e -> Free.pure(fallback))` |

---

## Composing Programs

Free programs compose like any monadic value:

```java
// Reusable building block
Free<W, String> askQuestion(String question) {
  return printLine(question).flatMap(_ -> readLine());
}

// Composed program
Free<W, Unit> registration() {
  return askQuestion("Username:")
      .flatMap(user -> askQuestion("Email:")
          .flatMap(email -> printLine("Registered: " + user + " <" + email + ">")));
}
```

---

## FreeFactory: Fixing Type Inference

Java cannot infer the functor type `F` when chaining on `Free.pure()`:

```java
Free.pure(2).map(x -> x * 2);  // COMPILE ERROR: cannot infer F

FreeFactory<IdKind.Witness> FREE = FreeFactory.of();
FREE.pure(2).map(x -> x * 2);  // Works -- F is captured once
```

---

## Relation to Java Sealed Interfaces

Free monads map naturally onto Java's type system:

| FP Concept | Java Equivalent |
|-----------|----------------|
| Algebraic data type (ADT) | `sealed interface` |
| Data constructors | `record` variants in permits clause |
| Pattern matching | `switch` expression on sealed type |
| Exhaustiveness | Compiler checks all permitted variants handled |
| Programs as data | `Free<F, A>` tree of sealed records |

The sealed interface guarantees:
- Every operation variant is known at compile time
- Interpreters must handle all variants (exhaustive switch)
- No new operations can be added outside the sealed hierarchy

---

## When to Use Free Monad

| Use Free When | Avoid Free When |
|--------------|----------------|
| Same logic needs multiple interpreters (prod/test/audit) | Single interpretation, no testing benefit |
| Testing complex workflows without real side effects | Simple operations (read file, call API) |
| Need to inspect/analyze programs before execution | Performance-critical hot paths |
| Building a domain-specific language | Overhead not justified |

Performance: Free has ~2-10x interpretation overhead vs direct code. Real workloads involve I/O that dominates compute time, making this negligible.

---

## Warning: Monad Transformer Limitation

`foldMap` uses an eager optimization that discards monadic context for strict target monads. `WriterT<Id, W, A>` will silently lose accumulated log entries.

**Workarounds**:
- Use lazy outer monad: `WriterT<IO, W, A>`
- Use mutable recording interpreters targeting `Id`
- Target `IO` and compose effects outside the Free interpreter

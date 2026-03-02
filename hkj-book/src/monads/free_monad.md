# The Free Monad:
## _Programs as Data, Interpreters as Plug-Ins_

~~~admonish info title="What You'll Learn"
- Why separating "what to do" from "how to do it" changes everything
- Building a domain-specific language (DSL) as a data structure
- Running the same program with production, test, and logging interpreters
- Using `pure`, `suspend`, `liftF`, and `foldMap` to build and run Free programs
- When Free monads solve real architectural problems (and when they don't)
~~~

~~~ admonish example title="See Example Code:"
- [ConsoleProgram.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/free/ConsoleProgram.java)
- [FreePathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/FreePathExample.java)
- [FreeMonadTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free/FreeMonadTest.java)
~~~

## The Idea: Your Program Is a Shopping List

Imagine writing a shopping list. The list *describes* what to buy — it doesn't actually buy anything. The same list could be:

- **Executed** at the supermarket (production)
- **Priced up** without buying anything (dry-run)
- **Checked** against the pantry (testing)
- **Delegated** to someone else (different interpreter)

The Free monad works the same way. Instead of executing side effects directly, you build a **program as a data structure**. Then you choose an **interpreter** to run it:

```
Program (data structure):            Interpreter 1 (real I/O):
  printLine("What is your name?")      → System.out.println(...)
  readLine()                           → scanner.nextLine()
  printLine("Hello, " + name + "!")    → System.out.println(...)

                                     Interpreter 2 (testing):
  (same program, zero changes)         → output.add(...)
                                       → return "Alice" from list
                                       → output.add(...)

                                     Interpreter 3 (logging):
  (same program, zero changes)         → log.info(...)
                                       → return cached input
                                       → log.info(...)
```

One program. Three interpreters. Zero code changes between them.

## The Payoff: See It Before You Build It

Before diving into infrastructure, here is the punchline. Given this program:

```java
Free<ConsoleOpKind.Witness, Unit> program =
    printLine("What is your name?")
        .flatMap(ignored -> readLine()
            .flatMap(name -> printLine("Hello, " + name + "!")));
```

Run it with a **real** interpreter — it talks to the console:

```java
ioInterpreter.run(program);
// Console: What is your name?
// User types: Alice
// Console: Hello, Alice!
```

Run the **same program** with a **test** interpreter — no console, pure assertions:

```java
TestInterpreter test = new TestInterpreter(List.of("Alice"));
test.run(program);

assertEquals(List.of("What is your name?", "Hello, Alice!"), test.getOutput());
```

That is the Free monad's value: **write once, interpret many ways**.

## Core Components

**The Free Structure**

![free_structure.svg](../images/puml/free_structure.svg)

**The HKT Bridge for Free**

![free_kind.svg](../images/puml/free_kind.svg)

**Type Classes for Free**

![free_monad.svg](../images/puml/free_monad.svg)

| Component | Role |
|-----------|------|
| `Free<F, A>` | A program built from instructions `F` that produces `A`. Sealed: `Pure`, `Suspend`, `FlatMapped` |
| `FreeKind<F, A>` / `FreeKindHelper` | HKT bridge: `widen()`, `narrow()` |
| `FreeFunctor<F>` / `FreeMonad<F>` | Type class instances: `map`, `flatMap`, `of` |
| `FreeFactory<F>` | Captures the functor type once — fixes Java's type inference for chained operations |
| `Free.liftF(fa, functor)` | Lifts a single instruction into a Free program |
| `free.foldMap(transform, monad)` | Interprets the program using a natural transformation — stack-safe via Trampoline |

~~~admonish note title="How foldMap Works"
```
Free program (tree):                  foldMap walks it:
  FlatMapped                          1. Interpret Suspend → target monad
    ├── Suspend(PrintLine("Hi"))      2. Apply continuation (flatMap fn)
    └── fn: name →                    3. Interpret next Suspend
        Suspend(PrintLine("Hello,     4. Combine via target monad's flatMap
                " + name))           5. Return final result in target monad
```
`foldMap` converts each instruction from your DSL (`F`) into a target monad (`M`) using a natural transformation, then sequences the results. It uses Higher-Kinded-J's own `Trampoline` internally for stack safety.
~~~

## Building a DSL: Step by Step

### Step 1: Define Your Instruction Set

Create a sealed interface for every operation in your domain:

```java
public sealed interface ConsoleOp<A> {
    record PrintLine(String text) implements ConsoleOp<Unit> {}
    record ReadLine()             implements ConsoleOp<String> {}
}
```

This is your vocabulary. `PrintLine` returns `Unit` (like void). `ReadLine` returns `String`.

### Step 2: HKT Plumbing

~~~admonish tip title="HKT Bridge (expand to see boilerplate)"
Every instruction set needs an HKT bridge and a Functor. This is mechanical:

```java
// HKT marker
public interface ConsoleOpKind<A> extends Kind<ConsoleOpKind.Witness, A> {
    final class Witness { private Witness() {} }
}

// Widen/narrow helper
public enum ConsoleOpKindHelper {
    CONSOLE;
    record ConsoleOpHolder<A>(ConsoleOp<A> op) implements ConsoleOpKind<A> {}
    public <A> Kind<ConsoleOpKind.Witness, A> widen(ConsoleOp<A> op) {
        return new ConsoleOpHolder<>(op);
    }
    public <A> ConsoleOp<A> narrow(Kind<ConsoleOpKind.Witness, A> kind) {
        return ((ConsoleOpHolder<A>) kind).op();
    }
}

// Functor instance
public class ConsoleOpFunctor implements Functor<ConsoleOpKind.Witness> {
    @Override
    public <A, B> Kind<ConsoleOpKind.Witness, B> map(
            Function<? super A, ? extends B> f,
            Kind<ConsoleOpKind.Witness, A> fa) {
        return (Kind<ConsoleOpKind.Witness, B>) fa;
    }
}
```
~~~

### Step 3: Create DSL Helpers

Wrap `liftF` calls in friendly methods:

```java
public class ConsoleOps {
    private static final ConsoleOpFunctor FUNCTOR = new ConsoleOpFunctor();

    public static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
        return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(
            new ConsoleOp.PrintLine(text)), FUNCTOR);
    }

    public static Free<ConsoleOpKind.Witness, String> readLine() {
        return Free.liftF(ConsoleOpKindHelper.CONSOLE.widen(
            new ConsoleOp.ReadLine()), FUNCTOR);
    }
}
```

### Step 4: Write Programs

Now build programs using familiar `flatMap` chains:

```java
import static ConsoleOps.*;

Free<ConsoleOpKind.Witness, Unit> greetingProgram =
    printLine("What is your name?")
        .flatMap(ignored -> readLine()
            .flatMap(name -> printLine("Hello, " + name + "!")));
```

**Nothing has executed.** You have built a data structure that *describes* what should happen.

## Multiple Interpreters: The Payoff

~~~admonish example title="IO Interpreter — Real Execution"
```java
public class IOInterpreter {
    private static final Scanner scanner = new Scanner(System.in);

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
        Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
            kind -> {
                ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
                Object result = switch (op) {
                    case ConsoleOp.PrintLine p -> { System.out.println(p.text()); yield Unit.INSTANCE; }
                    case ConsoleOp.ReadLine r  -> scanner.nextLine();
                };
                return Id.of(result);
            };
        return IdKindHelper.ID.narrow(
            program.foldMap(transform, IdMonad.instance())).value();
    }
}
```
~~~

~~~admonish example title="Test Interpreter — Pure Assertions"
```java
public class TestInterpreter {
    private final List<String> input;
    private final List<String> output = new ArrayList<>();
    private int inputIndex = 0;

    public TestInterpreter(List<String> input) { this.input = input; }

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
        Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
            kind -> {
                ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(kind);
                Object result = switch (op) {
                    case ConsoleOp.PrintLine p -> { output.add(p.text()); yield Unit.INSTANCE; }
                    case ConsoleOp.ReadLine r  -> input.get(inputIndex++);
                };
                return Id.of(result);
            };
        return IdKindHelper.ID.narrow(
            program.foldMap(transform, IdMonad.instance())).value();
    }

    public List<String> getOutput() { return output; }
}

// Pure test — no console, no I/O, no flakiness
@Test void testGreeting() {
    var test = new TestInterpreter(List.of("Alice"));
    test.run(greetingProgram);
    assertEquals(List.of("What is your name?", "Hello, Alice!"), test.getOutput());
}
```
~~~

## Composing Programs from Building Blocks

Free programs compose like Lego. Build small pieces, snap them together:

```java
// Reusable building blocks
Free<ConsoleOpKind.Witness, String> askQuestion(String question) {
    return printLine(question).flatMap(ignored -> readLine());
}

Free<ConsoleOpKind.Witness, Unit> confirm(String action) {
    return askQuestion(action + " — sure? (yes/no)")
        .flatMap(answer -> answer.equalsIgnoreCase("yes")
            ? printLine("Confirmed.")
            : printLine("Cancelled."));
}

// Composed program — built from pieces
Free<ConsoleOpKind.Witness, Unit> registration() {
    return askQuestion("Username:")
        .flatMap(user -> askQuestion("Email:")
            .flatMap(email -> confirm("Register " + user + "?")
                .flatMap(ignored -> printLine("Done! Welcome, " + user))));
}
```

Each building block is independently testable and reusable.

## FreeFactory: Fixing Java's Type Inference

Java struggles to infer the functor type `F` when chaining operations on `Free.pure()`:

```java
// Fails to compile — Java can't infer F
Free<IdKind.Witness, Integer> result = Free.pure(2).map(x -> x * 2); // ERROR

// FreeFactory captures F once, then inference works everywhere
FreeFactory<IdKind.Witness> FREE = FreeFactory.of();

Free<IdKind.Witness, Integer> result = FREE.pure(2).map(x -> x * 2); // Works!

Free<IdKind.Witness, Integer> program = FREE.pure(10)
    .map(x -> x + 1)
    .flatMap(x -> FREE.pure(x * 2))
    .map(x -> x - 5);
```

## When to Use Free Monad

| Scenario | Use |
|----------|-----|
| Building a DSL for your domain (workflows, queries, rules) | Free monad |
| Same logic needs production, test, and logging modes | Free monad — one program, many interpreters |
| Testing complex logic without real side effects | Free monad — pure, deterministic tests |
| Analysing or optimising programs before running them | Free monad — programs are inspectable data |
| Simple side effects (read file, call API) | Prefer [IO](./io_monad.md) — less boilerplate |
| Single interpretation, no testing benefit | Direct code — Free adds unnecessary complexity |
| Performance-critical hot paths | Direct code — Free has ~2-10x interpretation overhead |

## Free Monad vs Traditional Java Patterns

| Pattern | What It Does | Free Monad Advantage |
|---------|-------------|---------------------|
| Strategy | Swap one algorithm at runtime | Free swaps **entire program interpretation** |
| Command | Encapsulate a single action as an object | Free composes **workflows** with sequencing and data flow |
| Observer | React to events with multiple listeners | Free makes event streams **first-class, composable, replayable** |

All three patterns solve part of the problem. Free monad unifies them: your entire program is data that can be inspected, composed, transformed, and interpreted in multiple ways.

## Advanced Topics

~~~admonish tip title="Free Applicative"
When operations are **independent** (not dependent on previous results), use Free Applicative instead of Free Monad. This enables:
- Parallel execution or batching of independent operations
- Upfront analysis of all operations before execution
- Query optimisation (e.g., batching database reads)

See [Free Applicative](free_applicative.md) for details.
~~~

~~~admonish tip title="Coyoneda Optimisation"
Don't want to write a Functor for your instruction set? The **Coyoneda lemma** gives you one for free. It also enables map fusion — consecutive `.map(f).map(g).map(h)` calls collapse into a single `.map(f.andThen(g).andThen(h))`.

See [Coyoneda](coyoneda.md) for details.
~~~

~~~admonish tip title="Tagless Final: The Alternative"
Tagless Final achieves similar goals (multiple interpreters, testability) without building programs as data:

| Aspect | Free Monad | Tagless Final |
|--------|-----------|---------------|
| Programs | Data structures (inspectable) | Abstract functions (opaque) |
| Performance | Slower (interpretation overhead) | Faster (direct execution) |
| Boilerplate | More (HKT bridges, helpers) | Less (just interfaces) |
| Inspection | Can analyse before execution | Cannot inspect programs |

Choose **Free** when you need program inspection/optimisation. Choose **Tagless Final** when you want less boilerplate and better performance.
~~~

~~~admonish important title="Key Points"
- `Free<F, A>` turns any instruction set `F` into a monad — programs become composable data structures.
- **`foldMap`** is the engine: it walks the program tree, transforms each instruction via a natural transformation, and sequences results in a target monad. Stack-safe via Trampoline.
- **Testability** is the killer feature: write one program, test it with a pure interpreter, run it with a real one.
- **FreeFactory** solves Java's type inference limitations when chaining `map`/`flatMap` on `Free.pure()`.
- Programs compose with `flatMap` — build small DSL operations, snap them together into complex workflows.
- Free monad has overhead (~2-10x vs direct code). Use it when testability and flexibility outweigh raw performance.
~~~

---

~~~ admonish tip title="Further Reading"
For deeper exploration of Free monads and their applications:

- **Gabriel Gonzalez**: [Why free monads matter](https://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html) - An intuitive introduction to the concept
- **Runar Bjarnason**: [Stackless Scala With Free Monads](https://blog.higher-order.com/assets/trampolines.pdf) - Stack-safe execution patterns
- **Cats Documentation**: [Free Monad](https://typelevel.org/cats/datatypes/freemonad.html) - Scala implementation and examples
- **John A De Goes**: [Modern Functional Programming (Part 2)](https://degoes.net/articles/modern-fp-part-2) - Practical applications in real systems

**Related Higher-Kinded-J Documentation:**
- [Natural Transformation](../functional/natural_transformation.md) - Polymorphic functions used for Free monad interpretation
- [Free Applicative](free_applicative.md) - For independent computations that can run in parallel
- [Coyoneda](coyoneda.md) - Automatic Functor instances and map fusion
~~~

~~~admonish example title="Benchmarks"
Free has dedicated JMH benchmarks measuring interpretation cost and stack safety. Key expectations:

- **Deep recursion (10,000+)** completes without `StackOverflowError` — Free monad interpretation is stack-safe via trampolining
- **Performance scaling is linear with depth** — interpretation cost grows proportionally, not exponentially
- Abstraction overhead of 50-200x vs raw Java is expected and acceptable — real workloads involve I/O that dominates compute time

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*FreeBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details and how to interpret results.
~~~

---

**Previous:** [Trampoline](trampoline_monad.md)
**Next:** [Free Applicative](free_applicative.md)

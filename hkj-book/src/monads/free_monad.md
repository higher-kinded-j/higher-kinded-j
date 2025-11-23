# The Free Monad:
## _Building Composable DSLs and Interpreters_

~~~admonish info title="What You'll Learn"
- How to build domain-specific languages (DSLs) as data structures
- Separating program description from execution
- Creating multiple interpreters for the same program
- Using `pure`, `suspend`, and `liftF` to construct Free programs
- Implementing stack-safe interpreters with `foldMap`
- When Free monads solve real architectural problems
- Comparing Free monads with traditional Java patterns
~~~

~~~ admonish example title="See Example Code:"
- [ConsoleProgram.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/free/ConsoleProgram.java)
- [FreeMonadTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free/FreeMonadTest.java)
- [FreeFactoryTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free/FreeFactoryTest.java) - Demonstrates improved type inference with FreeFactory
~~~

~~~ admonish tip title="Further Reading"
For deeper exploration of Free monads and their applications:

- **Gabriel Gonzalez**: [Why free monads matter](http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html) - An intuitive introduction to the concept
- **Runar Bjarnason**: [Stackless Scala With Free Monads](http://blog.higher-order.com/assets/trampolines.pdf) - Stack-safe execution patterns
- **Cats Documentation**: [Free Monad](https://typelevel.org/cats/datatypes/freemonad.html) - Scala implementation and examples
- **John A De Goes**: [Modern Functional Programming (Part 2)](http://degoes.net/articles/modern-fp-part-2) - Practical applications in real systems
~~~

## Purpose

In traditional Java programming, when you want to execute side effects (like printing to the console, reading files, or making database queries), you directly execute them:

```java
// Traditional imperative approach
System.out.println("What is your name?");
String name = scanner.nextLine();
System.out.println("Hello, " + name + "!");
```

This approach tightly couples **what** you want to do with **how** it's done. The **Free monad** provides a fundamentally different approach: instead of executing effects immediately, you build programs as **data structures** that can be interpreted in different ways.

Think of it like writing a recipe (the data structure) versus actually cooking the meal (the execution). The recipe can be:
- Executed in a real kitchen (production)
- Simulated for testing
- Optimised before cooking
- Translated to different cuisines

The Free monad enables this separation in functional programming. A `Free<F, A>` represents a program built from instructions of type `F` that, when interpreted, will produce a value of type `A`.

### Key Benefits

1. **Testability**: Write pure tests without actual side effects. Test database code without a database.
2. **Multiple Interpretations**: One program, many interpreters (production, testing, logging, optimisation).
3. **Composability**: Build complex programs from simple, reusable building blocks.
4. **Inspection**: Programs are data, so you can analyse, optimise, or transform them before execution.
5. **Stack Safety**: Interpretation uses constant stack space, preventing `StackOverflowError`.

### Comparison with Traditional Java Patterns

If you're familiar with the **Strategy pattern**, Free monads extend this concept:

**Strategy Pattern**: Choose algorithm at runtime
```java
interface PaymentStrategy {
    void pay(int amount);
}
// Pick one: creditCardStrategy, payPalStrategy, etc.
```

**Free Monad**: Build an **entire program** as data, then pick how to execute it
```java
Free<PaymentOp, Receipt> program = ...;
// Pick interpreter: realPayment, testPayment, loggingPayment, etc.
```

Similarly, the **Command pattern** encapsulates actions as objects:

**Command Pattern**: Single action as object
```java
interface Command {
    void execute();
}
```

**Free Monad**: Entire workflows with sequencing, branching, and composition
```java
Free<Command, Result> workflow =
    sendEmail(...)
        .flatMap(receipt -> saveToDatabase(...))
        .flatMap(id -> sendNotification(...));
// Interpret with real execution or test mock
```

## Core Components

**The Free Structure**

![free_structure.svg](../images/puml/free_structure.svg)

**The HKT Bridge for Free**

![free_kind.svg](../images/puml/free_kind.svg)

**Type Classes for Free**

![free_monad.svg](../images/puml/free_monad.svg)

The `Free` functionality is built upon several related components:

1. **`Free<F, A>`**: The core sealed interface representing a program. It has three constructors:
   * `Pure<F, A>`: Represents a terminal value—the final result.
   * `Suspend<F, A>`: Represents a suspended computation—an instruction `Kind<F, Free<F, A>>` to be interpreted later.
   * `FlatMapped<F, X, A>`: Represents monadic sequencing—chains computations together in a stack-safe manner.

2. **`FreeKind<F, A>`**: The HKT marker interface (`Kind<FreeKind.Witness<F>, A>`) for `Free`. This allows `Free` to be treated as a generic type constructor in type classes. The witness type is `FreeKind.Witness<F>`, where `F` is the instruction set functor.

3. **`FreeKindHelper`**: The essential utility class for working with `Free` in the HKT simulation. It provides:
   * `widen(Free<F, A>)`: Wraps a concrete `Free<F, A>` instance into its HKT representation.
   * `narrow(Kind<FreeKind.Witness<F>, A>)`: Unwraps a `FreeKind<F, A>` back to the concrete `Free<F, A>`.

4. **`FreeFunctor<F>`**: Implements `Functor<FreeKind.Witness<F>>`. Provides the `map` operation to transform result values.

5. **`FreeMonad<F>`**: Extends `FreeFunctor<F>` and implements `Monad<FreeKind.Witness<F>>`. Provides `of` (to lift a pure value) and `flatMap` (to sequence Free computations).

## Purpose and Usage

* **Building DSLs**: Create domain-specific languages as composable data structures.
* **Natural Transformations**: Write interpreters as transformations from your instruction set `F` to a target monad `M`.
* **Stack-Safe Execution**: The `foldMap` method uses Higher-Kinded-J's own `Trampoline` monad internally, demonstrating the library's composability whilst preventing stack overflow.
* **Multiple Interpreters**: Execute the same program with different interpreters (production vs. testing vs. logging).
* **program Inspection**: Since programs are data, you can analyse, optimise, or transform them before execution.

**Key Methods:**
- `Free.pure(value)`: Creates a terminal computation holding a final value.
- `Free.suspend(computation)`: Suspends a computation for later interpretation.
- `Free.liftF(fa, functor)`: Lifts a functor value into a Free monad.
- `free.map(f)`: Transforms the result value without executing.
- `free.flatMap(f)`: Sequences Free computations whilst maintaining stack safety.
- `free.foldMap(transform, monad)`: Interprets the Free program using a natural transformation.

**FreeFactory for Improved Type Inference:**

Java's type inference can struggle when chaining operations directly on `Free.pure()`:

```java
// This fails to compile - Java can't infer F
Free<IdKind.Witness, Integer> result = Free.pure(2).map(x -> x * 2); // ERROR

// Workaround: explicit type parameters (verbose)
Free<IdKind.Witness, Integer> result = Free.<IdKind.Witness, Integer>pure(2).map(x -> x * 2);
```

The `FreeFactory<F>` class solves this by capturing the functor type parameter once:

```java
// Create a factory with your functor type
FreeFactory<IdKind.Witness> FREE = FreeFactory.of();
// or with a monad instance for clarity:
FreeFactory<IdKind.Witness> FREE = FreeFactory.withMonad(IdMonad.instance());

// Now type inference works perfectly
Free<IdKind.Witness, Integer> result = FREE.pure(2).map(x -> x * 2); // Works!

// Chain operations fluently
Free<IdKind.Witness, Integer> program = FREE.pure(10)
    .map(x -> x + 1)
    .flatMap(x -> FREE.pure(x * 2))
    .map(x -> x - 5);

// Other factory methods
Free<F, A> pure = FREE.pure(value);
Free<F, A> suspended = FREE.suspend(computation);
Free<F, A> lifted = FREE.liftF(fa, functor);
```

`FreeFactory` is particularly useful in:
- Test code where you build many Free programs
- DSL implementations where type inference is important
- Any code that chains `map`/`flatMap` operations on `Free.pure()`

~~~admonish example title="Example 1: Building a Console DSL"

Let's build a simple DSL for console interactions. We'll define instructions, build programs, and create multiple interpreters.

### Step 1: Define Your Instruction Set

First, create a sealed interface representing all possible operations in your DSL:

```java
public sealed interface ConsoleOp<A> {
    record PrintLine(String text) implements ConsoleOp<Unit> {}
    record ReadLine() implements ConsoleOp<String> {}
}

public record Unit() {
    public static final Unit INSTANCE = new Unit();
}
```

This is your vocabulary. `PrintLine` returns `Unit` (like `void`), `ReadLine` returns `String`.

### Step 2: Create HKT Bridge for Your DSL

To use your DSL with the Free monad, you need the HKT simulation components:

```java
public interface ConsoleOpKind<A> extends Kind<ConsoleOpKind.Witness, A> {
    final class Witness {
        private Witness() {}
    }
}

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
```

### Step 3: Create a Functor for Your DSL

The Free monad requires a `Functor` for your instruction set:

```java
public class ConsoleOpFunctor implements Functor<ConsoleOpKind.Witness> {
    private static final ConsoleOpKindHelper CONSOLE = ConsoleOpKindHelper.CONSOLE;

    @Override
    public <A, B> Kind<ConsoleOpKind.Witness, B> map(
            Function<? super A, ? extends B> f,
            Kind<ConsoleOpKind.Witness, A> fa) {
        ConsoleOp<A> op = CONSOLE.narrow(fa);
        // For immutable operations, mapping is identity
        // (actual mapping happens during interpretation)
        return (Kind<ConsoleOpKind.Witness, B>) fa;
    }
}
```

### Step 4: Create DSL Helper Functions

Provide convenient methods for building Free programs:

```java
public class ConsoleOps {
    /** Prints a line to the console. */
    public static Free<ConsoleOpKind.Witness, Unit> printLine(String text) {
        ConsoleOp<Unit> op = new ConsoleOp.PrintLine(text);
        Kind<ConsoleOpKind.Witness, Unit> kindOp =
            ConsoleOpKindHelper.CONSOLE.widen(op);
        return Free.liftF(kindOp, new ConsoleOpFunctor());
    }

    /** Reads a line from the console. */
    public static Free<ConsoleOpKind.Witness, String> readLine() {
        ConsoleOp<String> op = new ConsoleOp.ReadLine();
        Kind<ConsoleOpKind.Witness, String> kindOp =
            ConsoleOpKindHelper.CONSOLE.widen(op);
        return Free.liftF(kindOp, new ConsoleOpFunctor());
    }

    /** Pure value in the Free monad. */
    public static <A> Free<ConsoleOpKind.Witness, A> pure(A value) {
        return Free.pure(value);
    }
}
```

Now you can build programs using familiar Java syntax:

```java
Free<ConsoleOpKind.Witness, Unit> program =
    ConsoleOps.printLine("What is your name?")
        .flatMap(ignored ->
            ConsoleOps.readLine()
                .flatMap(name ->
                    ConsoleOps.printLine("Hello, " + name + "!")));
```

**Key Insight**: At this point, **nothing has executed**. You've built a data structure describing what should happen.
~~~

~~~admonish example title="Example 2: Building Programs with map and flatMap"

The Free monad supports `map` and `flatMap`, making it easy to compose programs:

```java
import static org.higherkindedj.example.free.ConsoleProgram.ConsoleOps.*;

// Simple sequence
Free<ConsoleOpKind.Witness, String> getName =
    printLine("Enter your name:")
        .flatMap(ignored -> readLine());

// Using map to transform results
Free<ConsoleOpKind.Witness, String> getUpperName =
    getName.map(String::toUpperCase);

// Building complex workflows
Free<ConsoleOpKind.Witness, Unit> greetingWorkflow =
    printLine("Welcome to the application!")
        .flatMap(ignored -> getName)
        .flatMap(name -> printLine("Hello, " + name + "!"))
        .flatMap(ignored -> printLine("Have a great day!"));

// Calculator example with error handling
Free<ConsoleOpKind.Witness, Unit> calculator =
    printLine("Enter first number:")
        .flatMap(ignored1 -> readLine())
        .flatMap(num1 ->
            printLine("Enter second number:")
                .flatMap(ignored2 -> readLine())
                .flatMap(num2 -> {
                    try {
                        int sum = Integer.parseInt(num1) + Integer.parseInt(num2);
                        return printLine("Sum: " + sum);
                    } catch (NumberFormatException e) {
                        return printLine("Invalid numbers!");
                    }
                }));
```

**Composability**: Notice how we can build `getName` once and reuse it in multiple programs. This promotes code reuse and testability.
~~~

~~~admonish example title="Example 3: IO Interpreter for Real Execution"

Now let's create an interpreter that actually executes console operations:

```java
public class IOInterpreter {
    private final Scanner scanner = new Scanner(System.in);

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
        // Create a natural transformation from ConsoleOp to IO
        Function<Kind<ConsoleOpKind.Witness, ?>, Kind<IOKind.Witness, ?>> transform =
            kind -> {
                ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(
                    (Kind<ConsoleOpKind.Witness, Object>) kind);

                // Execute the instruction and wrap result in Free.pure
                Free<ConsoleOpKind.Witness, ?> freeResult = switch (op) {
                    case ConsoleOp.PrintLine print -> {
                        System.out.println(print.text());
                        yield Free.pure(Unit.INSTANCE);
                    }
                    case ConsoleOp.ReadLine read -> {
                        String line = scanner.nextLine();
                        yield Free.pure(line);
                    }
                };

                // Wrap the Free result in the target monad (IO)
                return IOKindHelper.IO.widen(new IO<>(freeResult));
            };

        // Interpret the program using foldMap
        Kind<IOKind.Witness, A> result = program.foldMap(transform, new IOMonad());
        return IOKindHelper.IO.narrow(result).value();
    }
}

// Simple IO type for the interpreter
record IO<A>(A value) {}

// Run the program
IOInterpreter interpreter = new IOInterpreter();
interpreter.run(greetingProgram());
// Actual console interaction happens here!
```

**Natural Transformation**: The `transform` function is a natural transformation—it converts each `ConsoleOp` instruction into an `IO` operation whilst preserving structure.

**Critical Detail**: Notice we wrap instruction results in `Free.pure()`. This is essential—the natural transformation receives `Kind<F, Free<F, A>>` and must return `Kind<M, Free<F, A>>`, not just the raw result.
~~~

~~~admonish example title="Example 4: Test Interpreter for Pure Testing"

One of the most powerful aspects of Free monads is testability. Create a test interpreter that doesn't perform real I/O:

```java
public class TestInterpreter {
    private final List<String> input;
    private final List<String> output = new ArrayList<>();
    private int inputIndex = 0;

    public TestInterpreter(List<String> input) {
        this.input = input;
    }

    public <A> A run(Free<ConsoleOpKind.Witness, A> program) {
        // Create natural transformation to TestResult
        Function<Kind<ConsoleOpKind.Witness, ?>, Kind<TestResultKind.Witness, ?>> transform =
            kind -> {
                ConsoleOp<?> op = ConsoleOpKindHelper.CONSOLE.narrow(
                    (Kind<ConsoleOpKind.Witness, Object>) kind);

                // Simulate the instruction
                Free<ConsoleOpKind.Witness, ?> freeResult = switch (op) {
                    case ConsoleOp.PrintLine print -> {
                        output.add(print.text());
                        yield Free.pure(Unit.INSTANCE);
                    }
                    case ConsoleOp.ReadLine read -> {
                        String line = inputIndex < input.size()
                            ? input.get(inputIndex++)
                            : "";
                        yield Free.pure(line);
                    }
                };

                return TestResultKindHelper.TEST.widen(new TestResult<>(freeResult));
            };

        Kind<TestResultKind.Witness, A> result =
            program.foldMap(transform, new TestResultMonad());
        return TestResultKindHelper.TEST.narrow(result).value();
    }

    public List<String> getOutput() {
        return output;
    }
}

// Pure test - no actual I/O!
@Test
void testGreetingProgram() {
    TestInterpreter interpreter = new TestInterpreter(List.of("Alice"));
    interpreter.run(Programs.greetingProgram());

    List<String> output = interpreter.getOutput();
    assertEquals(2, output.size());
    assertEquals("What is your name?", output.get(0));
    assertEquals("Hello, Alice!", output.get(1));
}
```

**Testability**: The same `greetingProgram()` can be tested without any actual console I/O. You control inputs and verify outputs deterministically.
~~~

~~~admonish example title="Example 5: Composing Larger programs from Smaller Ones"

The real power emerges when building complex programs from simple, reusable pieces:

```java
// Reusable building blocks
Free<ConsoleOpKind.Witness, String> askQuestion(String question) {
    return printLine(question)
        .flatMap(ignored -> readLine());
}

Free<ConsoleOpKind.Witness, Unit> confirmAction(String action) {
    return printLine(action + " - Are you sure? (yes/no)")
        .flatMap(ignored -> readLine())
        .flatMap(response ->
            response.equalsIgnoreCase("yes")
                ? printLine("Confirmed!")
                : printLine("Cancelled."));
}

// Composed program
Free<ConsoleOpKind.Witness, Unit> userRegistration() {
    return askQuestion("Enter username:")
        .flatMap(username ->
            askQuestion("Enter email:")
                .flatMap(email ->
                    confirmAction("Register user " + username)
                        .flatMap(ignored ->
                            printLine("Registration complete for " + username))));
}

// Even more complex composition
Free<ConsoleOpKind.Witness, List<String>> gatherMultipleInputs(int count) {
    Free<ConsoleOpKind.Witness, List<String>> start = Free.pure(new ArrayList<>());

    for (int i = 0; i < count; i++) {
        final int index = i;
        start = start.flatMap(list ->
            askQuestion("Enter item " + (index + 1) + ":")
                .map(item -> {
                    list.add(item);
                    return list;
                }));
    }

    return start;
}
```

**Modularity**: Each function returns a `Free` program that can be:
- Tested independently
- Composed with others
- Interpreted in different ways
- Reused across your application
~~~

~~~admonish example title="Example 6: Using Free.liftF for Single Operations"

The `liftF` method provides a convenient way to lift single functor operations into Free:

```java
// Instead of manually creating Suspend
Free<ConsoleOpKind.Witness, String> createManualReadLine() {
    ConsoleOp<String> op = new ConsoleOp.ReadLine();
    Kind<ConsoleOpKind.Witness, String> kindOp =
        ConsoleOpKindHelper.CONSOLE.widen(op);
    return Free.suspend(
        new ConsoleOpFunctor().map(Free::pure, kindOp)
    );
}

// Using liftF (simpler!)
Free<ConsoleOpKind.Witness, String> createLiftedReadLine() {
    ConsoleOp<String> op = new ConsoleOp.ReadLine();
    Kind<ConsoleOpKind.Witness, String> kindOp =
        ConsoleOpKindHelper.CONSOLE.widen(op);
    return Free.liftF(kindOp, new ConsoleOpFunctor());
}

// Even simpler with helper method
Free<ConsoleOpKind.Witness, String> simpleReadLine =
    ConsoleOps.readLine();
```

**Best Practice**: Create helper methods (like `ConsoleOps.readLine()`) that use `liftF` internally. This provides a clean API for building programs.
~~~

## When to Use Free Monad

### Use Free Monad When:

1. **Building DSLs**: You need a domain-specific language for your problem domain (financial calculations, workflow orchestration, build systems, etc.).

2. **Multiple Interpretations**: The same logic needs different execution modes:
   - Production (real database, real network)
   - Testing (mocked, pure)
   - Logging (record all operations)
   - Optimisation (analyse before execution)
   - Dry-run (validate without executing)

3. **Testability is Critical**: You need to test complex logic without actual side effects. Example: testing database transactions without a database.

4. **program Analysis**: You need to inspect, optimise, or transform programs before execution:
   - Query optimisation
   - Batch operations
   - Caching strategies
   - Cost analysis

5. **Separation of Concerns**: Business logic must be decoupled from execution details. Example: workflow definition separate from workflow engine.

6. **Stack Safety Required**: Your DSL involves deep recursion or many sequential operations (verified with 10,000+ operations).

### Avoid Free Monad When:

1. **Simple Effects**: For straightforward side effects, use `IO`, `Reader`, or `State` directly. Free adds unnecessary complexity.

2. **Performance Critical**: Free monads have overhead:
   - Heap allocation for program structure
   - Interpretation overhead
   - Not suitable for hot paths or tight loops

3. **Single Interpretation**: If you only ever need one way to execute your program, traditional imperative code or simpler monads are clearer.

4. **Team Unfamiliarity**: Free monads require understanding of:
   - Algebraic data types
   - Natural transformations
   - Monadic composition

   If your team isn't comfortable with these concepts, simpler patterns might be more maintainable.

5. **Small Scale**: For small scripts or simple applications, the architectural benefits don't justify the complexity.

### Comparison with Alternatives

**Free Monad vs. Direct Effects**:
- Free: Testable, multiple interpreters, program inspection
- Direct: Simpler, better performance, easier to understand

**Free Monad vs. Tagless Final**:
- Free: programs are data structures, can be inspected
- Tagless Final: Better performance, less boilerplate, but programs aren't inspectable

**Free Monad vs. Effect Systems (like ZIO/Cats Effect)**:
- Free: Simpler concept, custom DSLs
- Effect Systems: More powerful, better performance, ecosystem support

## Advanced Topics

### Free Applicative vs. Free Monad

The **Free Applicative** is a related but distinct structure:

```java
// Free Monad: Sequential, dependent operations
Free<F, C> sequential =
    operationA()                           // A
        .flatMap(a ->                      // depends on A
            operationB(a)                  // B
                .flatMap(b ->              // depends on B
                    operationC(a, b)));    // C

// Free Applicative: Independent, parallel operations
Applicative<F, C> parallel =
    map3(
        operationA(),                      // A (independent)
        operationB(),                      // B (independent)
        operationC(),                      // C (independent)
        (a, b, c) -> combine(a, b, c)
    );
```

**When to use Free Applicative**:
- Operations are **independent** and can run in parallel
- You want to **analyse** all operations upfront (batch database queries, parallel API calls)
- **Optimisation**: Can reorder, batch, or parallelise operations

**When to use Free Monad**:
- Operations are **dependent** on previous results
- Need full monadic **sequencing** power
- Building workflows with conditional logic

**Example**: Fetching data from multiple independent sources

```java
// Free Applicative can batch these into a single round-trip
Applicative<DatabaseQuery, Report> report =
    map3(
        fetchUsers(),           // Independent
        fetchOrders(),          // Independent
        fetchProducts(),        // Independent
        (users, orders, products) -> generateReport(users, orders, products)
    );

// Interpreter can optimise: "SELECT * FROM users, orders, products"
```

### Coyoneda Optimisation

The **Coyoneda lemma** states that every type constructor can be made into a Functor. This allows Free monads to work with non-functor instruction sets:

```java
// Without Coyoneda: instruction set must be a Functor
public sealed interface DatabaseOp<A> {
    record Query(String sql) implements DatabaseOp<ResultSet> {}
    record Update(String sql) implements DatabaseOp<Integer> {}
}

// Must implement Functor<DatabaseOp> - can be tedious!

// With Coyoneda: automatic functor lifting
class Coyoneda<F, A> {
    Kind<F, Object> fa;
    Function<Object, A> f;

    static <F, A> Coyoneda<F, A> lift(Kind<F, A> fa) {
        return new Coyoneda<>(fa, Function.identity());
    }
}

// Now you can use any F without writing a Functor instance!
Free<Coyoneda<DatabaseOp, ?>, Result> program = ...;
```

**Benefits**:
- Less boilerplate (no manual Functor implementation)
- Works with any instruction set
- **Trade-off**: Slightly more complex interpretation

**When to use**: Large DSLs where writing Functor instances for every instruction type is burdensome.

### Tagless Final Style (Alternative Approach)

An alternative to Free monads is the **Tagless Final** encoding:

```java
// Free Monad approach
sealed interface ConsoleOp<A> { ... }
Free<ConsoleOp, Result> program = ...;

// Tagless Final approach
interface Console<F> {
    Kind<F, Unit> printLine(String text);
    Kind<F, String> readLine();
}

<F> Kind<F, Unit> program(Console<F> console, Monad<F> monad) {
    Kind<F, Unit> printName = console.printLine("What is your name?");
    Kind<F, String> readName = monad.flatMap(ignored -> console.readLine(), printName);
    return monad.flatMap(name -> console.printLine("Hello, " + name + "!"), readName);
}

// Different interpreters
Kind<IO.Witness, Unit> prod = program(ioConsole, ioMonad);
Kind<Test.Witness, Unit> test = program(testConsole, testMonad);
```

**Tagless Final vs. Free Monad**:

| Aspect | Free Monad | Tagless Final |
|--------|------------|---------------|
| **programs** | Data structures | Abstract functions |
| **Inspection** | ✅ Can analyse before execution | ❌ Cannot inspect |
| **Performance** | Slower (interpretation overhead) | Faster (direct execution) |
| **Boilerplate** | More (HKT bridges, helpers) | Less (just interfaces) |
| **Flexibility** | ✅ Multiple interpreters, transformations | ✅ Multiple interpreters |
| **Learning Curve** | Steeper | Moderate |

**When to use Tagless Final**:
- Performance matters
- Don't need program inspection
- Prefer less boilerplate

**When to use Free Monad**:
- Need to analyse/optimise programs before execution
- Want programs as first-class values
- Building complex DSLs with transformations

## Performance Characteristics

Understanding the performance trade-offs of Free monads is crucial for production use:

**Stack Safety**: O(1) stack space regardless of program depth
- Uses Higher-Kinded-J's `Trampoline` monad internally for `foldMap`
- Demonstrates library composability: Free uses Trampoline for stack safety
- Verified with 10,000+ sequential operations without stack overflow

**Heap Allocation**: O(n) where n is program size
- Each `flatMap` creates a `FlatMapped` node
- Each `suspend` creates a `Suspend` node
- **Consideration**: For very large programs (millions of operations), this could be significant

**Interpretation Time**: O(n) where n is program size
- Each operation must be pattern-matched and interpreted
- Additional indirection compared to direct execution
- **Rough estimate**: 2-10x slower than direct imperative code (depends on interpreter complexity)

**Optimisation Strategies**:

1. **Batch Operations**: Accumulate independent operations and execute in bulk
   ```java
   // Instead of 1000 individual database inserts
   Free<DB, Unit> manyInserts = ...;

   // Batch into single multi-row insert
   interpreter.optimise(program); // Detects pattern, batches
   ```

2. **Fusion**: Combine consecutive `map` operations
   ```java
   program.map(f).map(g).map(h)
   // Optimiser fuses to: program.map(f.andThen(g).andThen(h))
   ```

3. **Short-Circuiting**: Detect early termination
   ```java
   // If program returns early, skip remaining operations
   ```

4. **Caching**: Memoize pure computations
   ```java
   // Cache results of expensive pure operations
   ```

**Benchmarks** (relative to direct imperative code):
- Simple programs (< 100 operations): 2-3x slower
- Complex programs (1000+ operations): 3-5x slower
- With optimisation: Can approach parity for batch operations

## Implementation Notes

The `foldMap` method leverages Higher-Kinded-J's own `Trampoline` monad to ensure stack-safe execution. This elegant design demonstrates that the library's abstractions are practical and composable:

```java
public <M> Kind<M, A> foldMap(
        Function<Kind<F, ?>, Kind<M, ?>> transform,
        Monad<M> monad) {
    // Delegate to Trampoline for stack-safe execution
    return interpretFree(this, transform, monad).run();
}

private static <F, M, A> Trampoline<Kind<M, A>> interpretFree(
        Free<F, A> free,
        Function<Kind<F, ?>, Kind<M, ?>> transform,
        Monad<M> monad) {

    return switch (free) {
        case Pure<F, A> pure ->
            // Terminal case: lift the pure value into the target monad
            Trampoline.done(monad.of(pure.value()));

        case Suspend<F, A> suspend -> {
            // Transform the suspended computation and recursively interpret
            Kind<M, Free<F, A>> transformed =
                (Kind<M, Free<F, A>>) transform.apply(suspend.computation());

            yield Trampoline.done(
                monad.flatMap(
                    innerFree -> interpretFree(innerFree, transform, monad).run(),
                    transformed));
        }

        case FlatMapped<F, ?, A> flatMapped -> {
            // Handle FlatMapped by deferring the interpretation
            FlatMapped<F, Object, A> fm = (FlatMapped<F, Object, A>) flatMapped;

            yield Trampoline.defer(() ->
                interpretFree(fm.sub(), transform, monad)
                    .map(kindOfX ->
                        monad.flatMap(
                            x -> {
                                Free<F, A> next = fm.continuation().apply(x);
                                return interpretFree(next, transform, monad).run();
                            },
                            kindOfX)));
        }
    };
}
```

**Key Design Decisions**:

1. **Trampoline Integration**: Uses `Trampoline.done()` for terminal cases and `Trampoline.defer()` for recursive cases, ensuring stack safety.

2. **Library Composability**: Demonstrates that Higher-Kinded-J's abstractions are practical—Free monad uses Trampoline internally.

3. **Pattern Matching**: Uses sealed interface with switch expressions for type-safe case handling.

4. **Separation of Concerns**: Trampoline handles stack safety; Free handles DSL interpretation.

5. **Type Safety**: Uses careful casting to maintain type safety whilst leveraging Trampoline's proven stack-safe execution.

**Benefits of Using Trampoline**:
- Single source of truth for stack-safe recursion
- Proven implementation with 100% test coverage
- Elegant demonstration of library cohesion
- Improvements to Trampoline automatically benefit Free monad

## Comparison with Traditional Java Patterns

Let's see how Free monads compare to familiar Java patterns:

### Strategy Pattern

**Traditional Strategy**:
```java
interface SortStrategy {
    void sort(List<Integer> list);
}

class QuickSort implements SortStrategy { ... }
class MergeSort implements SortStrategy { ... }

// Choose algorithm at runtime
SortStrategy strategy = useQuickSort ? new QuickSort() : new MergeSort();
strategy.sort(myList);
```

**Free Monad Equivalent**:
```java
sealed interface SortOp<A> {
    record Compare(int i, int j) implements SortOp<Boolean> {}
    record Swap(int i, int j) implements SortOp<Unit> {}
}

Free<SortOp, Unit> quickSort(List<Integer> list) {
    // Build program as data
    return ...;
}

// Multiple interpreters
interpreter1.run(program); // In-memory sort
interpreter2.run(program); // Log operations
interpreter3.run(program); // Visualise algorithm
```

**Advantage of Free**: The **entire algorithm** is a data structure that can be inspected, optimised, or visualised.

### Command Pattern

**Traditional Command**:
```java
interface Command {
    void execute();
}

class SendEmailCommand implements Command { ... }
class SaveToDBCommand implements Command { ... }

List<Command> commands = List.of(
    new SendEmailCommand(...),
    new SaveToDBCommand(...)
);

commands.forEach(Command::execute);
```

**Free Monad Equivalent**:
```java
sealed interface AppOp<A> {
    record SendEmail(String to, String body) implements AppOp<Receipt> {}
    record SaveToDB(Data data) implements AppOp<Id> {}
}

Free<AppOp, Result> workflow =
    sendEmail("user@example.com", "Welcome!")
        .flatMap(receipt -> saveToDatabase(receipt))
        .flatMap(id -> sendNotification(id));

// One program, many interpreters
productionInterpreter.run(workflow); // Real execution
testInterpreter.run(workflow);       // Pure testing
loggingInterpreter.run(workflow);    // Audit trail
```

**Advantage of Free**: Commands compose with `flatMap`, results flow between commands, and you get multiple interpreters for free.

### Observer Pattern

**Traditional Observer**:
```java
interface Observer {
    void update(Event event);
}

class Logger implements Observer { ... }
class Notifier implements Observer { ... }

subject.registerObserver(logger);
subject.registerObserver(notifier);
subject.notifyObservers(event);
```

**Free Monad Equivalent**:
```java
sealed interface EventOp<A> {
    record Emit(Event event) implements EventOp<Unit> {}
    record React(Event event) implements EventOp<Unit> {}
}

Free<EventOp, Unit> eventStream =
    emit(userLoggedIn)
        .flatMap(ignored -> emit(pageViewed))
        .flatMap(ignored -> emit(itemPurchased));

// Different observation strategies
loggingInterpreter.run(eventStream);     // Log to file
analyticsInterpreter.run(eventStream);   // Send to analytics
testInterpreter.run(eventStream);        // Collect for assertions
```

**Advantage of Free**: Event streams are first-class values that can be composed, transformed, and replayed.

## Summary

The Free monad provides a powerful abstraction for building domain-specific languages in Java:

- **Separation of Concerns**: program description (data) vs. execution (interpreters)
- **Testability**: Pure testing without actual side effects
- **Flexibility**: Multiple interpreters for the same program
- **Stack Safety**: Handles deep recursion without stack overflow (verified with 10,000+ operations)
- **Composability**: Build complex programs from simple building blocks

**When to use**:
- Building DSLs
- Need multiple interpretations
- Testability is critical
- program analysis/optimisation required

**When to avoid**:
- Performance-critical code
- Simple, single-interpretation effects
- Team unfamiliar with advanced functional programming

For detailed implementation examples and complete working code, see:
- [ConsoleProgram.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/free/ConsoleProgram.java) - Complete DSL with multiple interpreters
- [FreeMonadTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/test/java/org/higherkindedj/hkt/free/FreeMonadTest.java) - Comprehensive test suite including monad laws and stack safety

The Free monad represents a sophisticated approach to building composable, testable, and maintainable programs in Java. Whilst it requires understanding of advanced functional programming concepts, it pays dividends in large-scale applications where flexibility and testability are paramount.

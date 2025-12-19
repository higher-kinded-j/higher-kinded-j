# FreePath

`FreePath<F, A>` wraps `Free<F, A>` for building **domain-specific languages**
(DSLs). It separates the *description* of a program from its *execution*,
enabling multiple interpreters for the same program.

~~~admonish info title="What You'll Learn"
- Creating FreePath instances
- Building DSL operations
- Writing interpreters
- When to use (and when not to)
~~~

---

## The Idea

Free monads let you:
1. **Describe** operations as data structures
2. **Compose** descriptions into programs
3. **Interpret** programs with different strategies

This enables testing with mock interpreters, swapping implementations, and
reasoning about programs as data.

---

## Defining a DSL

First, define your operations as a sum type (algebra):

```java
// Console operations
sealed interface ConsoleOp<A> permits Ask, Tell {}

record Ask<A>(String prompt, Function<String, A> next) implements ConsoleOp<A> {}
record Tell<A>(String message, A next) implements ConsoleOp<A> {}
```

---

## Creating Programs

Lift operations into `FreePath`:

```java
FreePath<ConsoleOp.Witness, String> ask(String prompt) {
    return Path.freeLiftF(new Ask<>(prompt, Function.identity()));
}

FreePath<ConsoleOp.Witness, Void> tell(String message) {
    return Path.freeLiftF(new Tell<>(message, null));
}
```

Compose into programs:

```java
FreePath<ConsoleOp.Witness, String> greetUser =
    ask("What is your name?").via(name ->
        tell("Hello, " + name + "!").map(v -> name));
```

---

## Core Operations

```java
// Pure value (no operations)
FreePath<ConsoleOp.Witness, Integer> pure = Path.freePure(42);

// Transform results
FreePath<ConsoleOp.Witness, String> asString = pure.map(n -> "Value: " + n);

// Chain operations
FreePath<ConsoleOp.Witness, Integer> chained = pure.via(n ->
    ask("Continue?").map(s -> n + s.length()));
```

---

## Interpreters

An interpreter is a natural transformation from your algebra to a target monad:

```java
// Real console interpreter
NaturalTransformation<ConsoleOp.Witness, IO.Witness> realInterpreter =
    new NaturalTransformation<>() {
        public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
            ConsoleOp<A> op = ConsoleOpHelper.narrow(fa);
            return switch (op) {
                case Ask<A> a -> IO.of(() -> {
                    System.out.print(a.prompt() + " ");
                    return a.next().apply(scanner.nextLine());
                });
                case Tell<A> t -> IO.of(() -> {
                    System.out.println(t.message());
                    return t.next();
                });
            };
        }
    };

// Test interpreter (uses predefined responses)
NaturalTransformation<ConsoleOp.Witness, State.Witness> testInterpreter = ...;
```

---

## Running Programs

```java
FreePath<ConsoleOp.Witness, String> program = greetUser;

// Get the Free structure
Free<ConsoleOp.Witness, String> free = program.run();

// Interpret to IO
Kind<IO.Witness, String> io = free.foldMap(realInterpreter, ioMonad);

// Execute
String result = IOKindHelper.narrow(io).unsafeRunSync();
```

---

## When to Use

`FreePath` is right when:
- You want to separate description from execution
- Multiple interpreters for the same program (prod/test/mock)
- Building embedded DSLs for domain operations
- You need to inspect or transform programs before running them

`FreePath` is wrong when:
- Simple direct effects suffice → use [IOPath](path_io.md)
- You don't need multiple interpreters
- Performance is critical (free monads have overhead)
- Operations can be parallelized → consider [FreeApPath](path_freeap.md)

~~~admonish example title="Testing with Mock Interpreter"
```java
// Production: real database
NaturalTransformation<DbOp.Witness, IO.Witness> prodInterpreter = ...;

// Test: in-memory map
NaturalTransformation<DbOp.Witness, State.Witness> testInterpreter = ...;

// Same program, different interpreters
FreePath<DbOp.Witness, User> program = findUser(userId);
Kind<IO.Witness, User> prod = program.run().foldMap(prodInterpreter, ioMonad);
Kind<State.Witness, User> test = program.run().foldMap(testInterpreter, stateMonad);
```
~~~

~~~admonish tip title="See Also"
- [Free Monad](../monads/free_monad.md) - Underlying type for FreePath
- [FreeApPath](path_freeap.md) - Applicative variant for parallel operations
~~~

---

**Previous:** [TrampolinePath](path_trampoline.md)
**Next:** [FreeApPath](path_freeap.md)

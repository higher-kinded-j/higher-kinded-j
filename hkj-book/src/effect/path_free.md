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

<!-- verify -->
```java
// Console operations. Extending Kind gives the algebra a witness, which is what
// FreePath is parameterised by.
sealed interface ConsoleOp<A> extends Kind<ConsoleOp.Witness, A> permits Ask, Tell {
    interface Witness extends WitnessArity<TypeArity.Unary> {}
}

record Ask<A>(String prompt, Function<String, A> next) implements ConsoleOp<A> {}
record Tell<A>(String message, A next) implements ConsoleOp<A> {}
```

Every algebra also needs a `Functor`, so `FreePath` can map over whatever an
operation carries next:

<!-- verify -->
```java
Functor<ConsoleOp.Witness> consoleFunctor = new Functor<>() {
    @Override
    public <A, B> Kind<ConsoleOp.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<ConsoleOp.Witness, A> fa) {
        return switch ((ConsoleOp<A>) fa) {
            case Ask<A>(String prompt, Function<String, A> next) ->
                new Ask<B>(prompt, s -> f.apply(next.apply(s)));
            case Tell<A>(String message, A next) ->
                new Tell<B>(message, f.apply(next));
        };
    }
};
```

~~~admonish tip title="Don't want to write one?"
The [Coyoneda](../monads/coyoneda.md) lemma derives a `Functor` for any
instruction set, at the cost of an extra wrapper.
~~~

---

## Creating Programs

Lift operations into `FreePath`:

<!-- verify -->
```java
FreePath<ConsoleOp.Witness, String> ask(String prompt) {
    return Path.freeLift(new Ask<>(prompt, Function.identity()), consoleFunctor);
}

FreePath<ConsoleOp.Witness, Void> tell(String message) {
    return Path.freeLift(new Tell<>(message, null), consoleFunctor);
}
```

Compose into programs:

<!-- verify -->
```java
FreePath<ConsoleOp.Witness, String> greetUser =
    ask("What is your name?").via(name ->
        tell("Hello, " + name + "!").map(v -> name));
```

---

## Core Operations

<!-- verify -->
```java
// Pure value (no operations)
FreePath<ConsoleOp.Witness, Integer> pure = Path.freePure(42, consoleFunctor);

// Transform results
FreePath<ConsoleOp.Witness, String> asString = pure.map(n -> "Value: " + n);

// Chain operations
FreePath<ConsoleOp.Witness, Integer> chained = pure.via(n ->
    ask("Continue?").map(s -> n + s.length()));
```

---

## Interpreters

An interpreter is a natural transformation from your algebra to a target monad:

<!-- verify -->
```java
// Real console interpreter
NaturalTransformation<ConsoleOp.Witness, IO.Witness> realInterpreter =
    new NaturalTransformation<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
            return switch ((ConsoleOp<A>) fa) {
                case Ask<A> a -> IO.delay(() -> {
                    System.out.print(a.prompt() + " ");
                    return a.next().apply(scanner.nextLine());
                });
                case Tell<A> t -> IO.delay(() -> {
                    System.out.println(t.message());
                    return t.next();
                });
            };
        }
    };

// Test interpreter: the same algebra, answered from canned input
NaturalTransformation<ConsoleOp.Witness, IO.Witness> testInterpreter =
    new NaturalTransformation<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConsoleOp.Witness, A> fa) {
            return switch ((ConsoleOp<A>) fa) {
                case Ask<A> a -> IO.delay(() -> a.next().apply("Alice"));
                case Tell<A> t -> IO.delay(t::next);
            };
        }
    };
```

---

## Running Programs

<!-- verify -->
```java
FreePath<ConsoleOp.Witness, String> program = greetUser;

// Interpret the whole path in one step
GenericPath<IO.Witness, String> interpreted =
    program.foldMapWith(realInterpreter, ioMonad);

// Execute
String result = IOKindHelper.IO_OP.narrow(interpreted.runKind()).unsafeRunSync();

// Or take the Free structure out first, if you want to fold it yourself
Free<ConsoleOp.Witness, String> free = program.toFree();
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
- Operations can be parallelised → consider [FreeApPath](path_freeap.md)

~~~admonish example title="Testing with Mock Interpreter"
<!-- verify -->
```java
// One program, two interpreters. Nothing about `greetUser` changes.
GenericPath<IO.Witness, String> live = greetUser.foldMapWith(realInterpreter, ioMonad);
GenericPath<IO.Witness, String> underTest = greetUser.foldMapWith(testInterpreter, ioMonad);

String answered = IOKindHelper.IO_OP.narrow(underTest.runKind()).unsafeRunSync();
```
~~~

~~~admonish tip title="See Also"
- [Free Monad](../monads/free_monad.md) - Underlying type for FreePath
- [FreeApPath](path_freeap.md) - Applicative variant for parallel operations
~~~

---

**Previous:** [TrampolinePath](path_trampoline.md)
**Next:** [FreeApPath](path_freeap.md)

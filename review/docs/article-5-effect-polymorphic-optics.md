# Building an Expression Language: Part 3, Effect-Polymorphic Optics

*Part 5 of the Functional Optics for Modern Java series*

In Article 4, we built traversals that visit every node in our expression tree. We implemented constant folding, identity simplification, and dead branch elimination. But all our transformations were pure: they took an expression and returned a new expression, with no side effects.

Real compilers and interpreters need more. Type checking should report *all* errors, not just the first one. Interpretation must track variable bindings as it descends through the tree. Logging might help debug complex transformations. These are *effects*, and they change everything about how we structure our code.

This is where Higher-Kinded-J reveals its full potential. The same traversals we wrote in Article 4 will work unchanged with effectful operations. We simply swap the effect type. It's rather like discovering your trusty Swiss Army knife also works underwater.

---

## The Problem with Effects

Consider type checking. A naïve approach fails on the first error:

```java
public Type typeCheck(Expr expr, Environment env) throws TypeError {
    return switch (expr) {
        case Literal(Integer _) -> Type.INT;
        case Literal(Boolean _) -> Type.BOOL;
        case Variable(var name) -> {
            Type t = env.lookup(name);
            if (t == null) throw new TypeError("Undefined variable: " + name);
            yield t;
        }
        case Binary(var left, var op, var right) -> {
            Type leftType = typeCheck(left, env);   // Might throw
            Type rightType = typeCheck(right, env); // Never reached if left fails
            yield checkBinaryOp(op, leftType, rightType);
        }
        // ...
    };
}
```

The problem: if `left` has an error, we never check `right`. Users see one error, fix it, recompile, see another error, and repeat. Anyone who's wrestled with a particularly unhelpful C++ template error message knows this frustrating cycle.

We want *error accumulation*: collect all type errors in a single pass, then report them together. But this requires threading an error collection through every recursive call. The code becomes cluttered with accumulator parameters.

Similarly, interpretation needs environment threading:

```java
public Object interpret(Expr expr, Environment env) {
    return switch (expr) {
        case Literal(var v) -> v;
        case Variable(var name) -> env.lookup(name);
        case Binary(var left, var op, var right) -> {
            Object leftVal = interpret(left, env);
            Object rightVal = interpret(right, env);
            return applyOp(op, leftVal, rightVal);
        }
        case Conditional(var cond, var then_, var else_) -> {
            Object condVal = interpret(cond, env);
            if ((Boolean) condVal) {
                return interpret(then_, env);
            } else {
                return interpret(else_, env);
            }
        }
    };
}
```

This looks clean, but what if we add `let` bindings that extend the environment? Or mutable references? The environment threading becomes explicit and error-prone. Pass the wrong environment to a recursive call, and you've got a subtle bug that only manifests in deeply nested expressions.

---

## Effect Polymorphism: The Core Idea

Effect polymorphism means writing code once that works with many different effects. Instead of hardcoding error handling or state threading, we abstract over the *computational context*.

In Higher-Kinded-J, this abstraction is the `Kind<F, A>` type: a value of type `A` wrapped in some effect `F`. Different choices of `F` give different behaviours:

| Effect Type | `Kind<F, A>` represents | Behaviour |
|-------------|------------------------|-----------|
| `Id` | Just `A` | Pure computation, no effects |
| `Maybe` | `A` or nothing | Computation that might fail silently |
| `Either<E, ?>` | `A` or error `E` | Fail-fast error handling |
| `Validated<E, ?>` | `A` or accumulated errors | Error accumulation |
| `State<S, ?>` | `A` with state `S` | Stateful computation |
| `IO` | Deferred `A` | Side effects |

The key insight: if we write our traversals to work with any `Kind<F, A>`, we get all these behaviours for free. Write once, run with any effect. It's polymorphism, but for computational context rather than data types.

For Java developers used to thinking in terms of `Optional<T>` or `CompletableFuture<T>`, this is the next level: abstracting over *which* wrapper type you're using, not just what's inside it.

---

## The modifyF Operation

Every optic in Higher-Kinded-J supports `modifyF`, which lifts a transformation into an effectful context:

```java
public interface Traversal<S, A> {
    /**
     * Apply an effectful transformation to all focused elements.
     *
     * @param f the effectful transformation
     * @param source the structure to transform
     * @param applicative the Applicative instance for effect F
     * @return the transformed structure wrapped in effect F
     */
    <F> Kind<F, S> modifyF(
        Function<A, Kind<F, A>> f,
        S source,
        Applicative<F> applicative
    );
}
```

The `Applicative<F>` parameter provides two essential operations:

1. **`of(a)`**: Wrap a pure value in the effect (also called `pure`)
2. **`map2(fa, fb, combine)`**: Combine two effectful values

With just these two operations, we can sequence independent computations whilst accumulating their effects. For dependent computations (where the result of one affects what we do next), we need `Monad` and its `flatMap`.

### Example: Pure Transformation

For pure transformations, we can use the optics library's `Traversals.modify` utility, which handles the `Id` effect internally:

```java
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.util.Traversals;

Traversal<Expr, Expr> children = ExprTraversal.children();

// Pure transformation: double all literals
Expr result = Traversals.modify(children, e -> {
    if (e instanceof Literal(Integer i)) {
        return new Literal(i * 2);
    }
    return e;
}, expression);
```

### Example: Stateful Transformation

Using Higher-Kinded-J's `State` monad, transformations can track state. Here's where it gets interesting:

```java
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateMonad;
import org.higherkindedj.hkt.state.StateTuple;

StateMonad<Integer> stateMonad = new StateMonad<>();

// Count and transform literals using modifyF with State effect
Kind<StateKind.Witness<Integer>, Expr> stateKind = children.modifyF(
    e -> {
        if (e instanceof Literal(Integer i)) {
            // State.modify returns Unit, so we use _ to indicate the unused parameter
            State<Integer, Expr> countAndTransform =
                State.<Integer>modify(count -> count + 1).map(_ -> new Literal(i * 10));
            return STATE.widen(countAndTransform);
        }
        // Use STATE.pure() directly - it returns Kind, avoiding manual widen()
        return STATE.pure(e);
    },
    expr,
    stateMonad);

StateTuple<Integer, Expr> stateResult = STATE.narrow(stateKind).run(0);
System.out.printf("Transformed: %s, count = %d%n",
    stateResult.value().format(), stateResult.state());
```

Notice the `_` in `.map(_ -> new Literal(i * 10))`. Since `State.modify` returns `Unit` (it modifies state but produces no meaningful value), we use Java's unnamed variable pattern to indicate we're deliberately ignoring it. It signals intent clearly to anyone reading the code.

Also note `STATE.pure(e)` instead of `STATE.widen(State.pure(e))`. The `StateKindHelper` provides convenience methods that return `Kind` directly, saving you from the ceremony of manual widening. It's the little things that make a library pleasant to use.

---

## Type Checking with Validated

`Validated<E, A>` is the key to error accumulation. Unlike `Either`, which short-circuits on the first error, `Validated` collects all errors before failing.

### The Validated Type

```java
public sealed interface Validated<E, A> {
    record Valid<E, A>(A value) implements Validated<E, A> {}
    record Invalid<E, A>(E errors) implements Validated<E, A> {}
}
```

The crucial difference from `Either`: `Validated` forms an `Applicative` but *not* a `Monad`. This isn't a limitation; it's the feature. Without `flatMap`, independent validations run in parallel (logically), accumulating all their errors. You can't accidentally short-circuit because there's no way to express sequential dependency.

### Building a Type Checker with ValidatedMonad

First, define our type and error types:

```java
public enum Type { INT, BOOL, STRING }

/**
 * A type error with a descriptive message.
 */
public record TypeError(String message) {

    /**
     * Get a Semigroup for combining lists of TypeErrors.
     * Uses Higher-Kinded-J's built-in list semigroup.
     */
    public static Semigroup<List<TypeError>> semigroup() {
        return Semigroups.list();
    }

    /**
     * Create a single-element error list.
     */
    public static List<TypeError> single(String message) {
        return List.of(new TypeError(message));
    }
}
```

Now the type checker. This is where Higher-Kinded-J really shines. We use `ValidatedMonad` with its `map2` and `map3` methods to combine multiple validations whilst accumulating all errors:

```java
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;

public final class ExprTypeChecker {

    private static final Semigroup<List<TypeError>> ERROR_SEMIGROUP = TypeError.semigroup();

    /** ValidatedMonad instance for applicative-style error accumulation. */
    private static final ValidatedMonad<List<TypeError>> VALIDATED_MONAD =
        ValidatedMonad.instance(ERROR_SEMIGROUP);

    public static Validated<List<TypeError>, Type> typeCheck(Expr expr, TypeEnv env) {
        return switch (expr) {
            case Literal(var value) -> typeCheckLiteral(value);
            case Variable(var name) -> typeCheckVariable(name, env);
            case Binary(var left, var op, var right) -> typeCheckBinary(left, op, right, env);
            case Conditional(var cond, var then_, var else_) ->
                typeCheckConditional(cond, then_, else_, env);
        };
    }

    private static Validated<List<TypeError>, Type> typeCheckLiteral(Object value) {
        return switch (value) {
            case Integer _ -> Validated.valid(Type.INT);
            case Boolean _ -> Validated.valid(Type.BOOL);
            case String _ -> Validated.valid(Type.STRING);
            default -> Validated.invalid(
                TypeError.single("Unknown literal type: %s"
                    .formatted(value.getClass().getSimpleName())));
        };
    }

    private static Validated<List<TypeError>, Type> typeCheckVariable(String name, TypeEnv env) {
        return env.lookup(name)
            .map(Validated::<List<TypeError>, Type>valid)
            .orElseGet(() -> Validated.invalid(TypeError.single("Undefined variable: " + name)));
    }

    private static Validated<List<TypeError>, Type> typeCheckBinary(
            Expr left, BinaryOp op, Expr right, TypeEnv env) {
        Validated<List<TypeError>, Type> leftResult = typeCheck(left, env);
        Validated<List<TypeError>, Type> rightResult = typeCheck(right, env);

        // Use Higher-Kinded-J's ValidatedMonad.map2 for applicative-style error accumulation.
        // map2 combines two Validated values: if both are Valid, it applies the combining
        // function; if either (or both) are Invalid, it accumulates all errors using the
        // semigroup. We then flatMap to handle the type constraint validation.
        Kind<ValidatedKind.Witness<List<TypeError>>, Validated<List<TypeError>, Type>> combined =
            VALIDATED_MONAD.map2(
                VALIDATED.widen(leftResult),
                VALIDATED.widen(rightResult),
                (lt, rt) -> checkBinaryTypes(op, lt, rt));

        // Flatten the nested Validated: if sub-expression checking succeeded, return the type check
        return VALIDATED.narrow(combined).flatMap(innerResult -> innerResult);
    }

    private static Validated<List<TypeError>, Type> typeCheckConditional(
            Expr cond, Expr then_, Expr else_, TypeEnv env) {
        Validated<List<TypeError>, Type> condResult = typeCheck(cond, env);
        Validated<List<TypeError>, Type> thenResult = typeCheck(then_, env);
        Validated<List<TypeError>, Type> elseResult = typeCheck(else_, env);

        // Use Higher-Kinded-J's ValidatedMonad.map3 for applicative-style error accumulation.
        // This elegantly handles three sub-expressions: if any have errors, all errors are
        // accumulated. Only when all three are Valid do we proceed to check the constraints.
        Kind<ValidatedKind.Witness<List<TypeError>>, Validated<List<TypeError>, Type>> combined =
            VALIDATED_MONAD.map3(
                VALIDATED.widen(condResult),
                VALIDATED.widen(thenResult),
                VALIDATED.widen(elseResult),
                ExprTypeChecker::checkConditionalTypes);

        return VALIDATED.narrow(combined).flatMap(innerResult -> innerResult);
    }

    // ... checkBinaryTypes and checkConditionalTypes methods
}
```

### The Elegance of map2 and map3

Look at what `map2` and `map3` buy us. Without them, we'd write tedious nested pattern matching:

```java
// The manual approach - what ValidatedMonad.map2 does for us automatically
return switch (leftResult) {
    case Valid(var lt) -> switch (rightResult) {
        case Valid(var rt) -> checkBinaryTypes(op, lt, rt);
        case Invalid(var errors) -> Validated.invalid(errors);
    };
    case Invalid(var leftErrors) -> switch (rightResult) {
        case Valid(_) -> Validated.invalid(leftErrors);
        case Invalid(var rightErrors) ->
            Validated.invalid(ERROR_SEMIGROUP.combine(leftErrors, rightErrors));
    };
};
```

That's four cases for two operands. For three operands (like a conditional), you'd have eight cases. For four operands, sixteen. The pattern is clear.

Higher-Kinded-J's `map2` and `map3` abstract this pattern entirely. They handle the error accumulation logic once, correctly, and you simply provide the combining function.

### Running the Type Checker

```java
// Build an expression with multiple errors: (1 + true) * (false && 42)
Expr leftError = new Binary(new Literal(1), BinaryOp.ADD, new Literal(true));
Expr rightError = new Binary(new Literal(false), BinaryOp.AND, new Literal(42));
Expr expr = new Binary(leftError, BinaryOp.MUL, rightError);

Validated<List<TypeError>, Type> result = ExprTypeChecker.typeCheck(expr, TypeEnv.empty());

switch (result) {
    case Valid(var type) -> System.out.println("Type: " + type);
    case Invalid(var errors) -> {
        System.out.println("Type errors:");
        for (TypeError error : errors) {
            System.out.println("  - " + error.message());
        }
    }
}
```

Output:
```
Type errors:
  - Arithmetic operator '+' requires INT operands, got INT and BOOL
  - Logical operator '&&' requires BOOL operands, got BOOL and INT
```

Both errors are reported in a single pass. The user can fix them both at once. This is the power of `Validated` with `Applicative`, and `ValidatedMonad.map2` makes it straightforward to use.

---

## Interpretation with State

For interpretation, we need to thread an environment through the computation. The `State` monad captures this pattern elegantly.

### Higher-Kinded-J's State Type

Higher-Kinded-J provides `State<S, A>` in `org.higherkindedj.hkt.state`:

```java
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;

// Key operations:
State.pure(value)           // Wrap a value without changing state
State.<S>get()              // Access the current state
State.<S>modify(f)          // Transform the state
state.flatMap(f)            // Chain dependent computations
state.map(f)                // Transform the result
state.run(initialState)     // Run and get StateTuple<S, A>
```

The result of `run()` is a `StateTuple<S, A>` with `value()` for the result and `state()` for the final state.

### Building an Interpreter

```java
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;

public final class ExprInterpreter {

    public static State<Environment, Object> interpret(Expr expr) {
        return switch (expr) {
            case Literal(var value) -> State.pure(value);
            case Variable(var name) -> State.<Environment>get().map(env -> env.lookup(name));
            case Binary(var left, var op, var right) -> interpretBinary(left, op, right);
            case Conditional(var cond, var then_, var else_) ->
                interpretConditional(cond, then_, else_);
        };
    }

    private static State<Environment, Object> interpretBinary(
            Expr left, BinaryOp op, Expr right) {
        return interpret(left)
            .flatMap(leftVal -> interpret(right)
                .map(rightVal -> applyBinaryOp(op, leftVal, rightVal)));
    }

    private static State<Environment, Object> interpretConditional(
            Expr cond, Expr then_, Expr else_) {
        return interpret(cond)
            .flatMap(condVal -> interpret((Boolean) condVal ? then_ : else_));
    }

    private static Object applyBinaryOp(BinaryOp op, Object left, Object right) {
        return switch (op) {
            case ADD -> (Integer) left + (Integer) right;
            case SUB -> (Integer) left - (Integer) right;
            case MUL -> (Integer) left * (Integer) right;
            case DIV -> (Integer) left / (Integer) right;
            case AND -> (Boolean) left && (Boolean) right;
            case OR -> (Boolean) left || (Boolean) right;
            case EQ -> left.equals(right);
            case NE -> !left.equals(right);
            case LT -> (Integer) left < (Integer) right;
            case LE -> (Integer) left <= (Integer) right;
            case GT -> (Integer) left > (Integer) right;
            case GE -> (Integer) left >= (Integer) right;
        };
    }

    /** Convenience method to interpret directly with an environment. */
    public static Object eval(Expr expr, Environment env) {
        StateTuple<Environment, Object> result = interpret(expr).run(env);
        return result.value();
    }
}
```

### Running the Interpreter

```java
// Build expression: (x + 1) * 2
Expr expr = new Binary(
    new Binary(new Variable("x"), BinaryOp.ADD, new Literal(1)),
    BinaryOp.MUL,
    new Literal(2)
);
Environment env = Environment.of("x", 10);

Object result = ExprInterpreter.eval(expr, env);
// result = 22

// Or using State directly for more control:
StateTuple<Environment, Object> tuple = ExprInterpreter.interpret(expr).run(env);
Object value = tuple.value();      // 22
Environment finalEnv = tuple.state();  // unchanged environment
```

The environment is threaded implicitly through `flatMap`. We never pass it explicitly after the initial `eval` call. The State monad handles all the plumbing, and we focus on the logic. It's rather liberating.

---

## Combining Validated and State

Our type checker uses `Validated` for error accumulation, whilst our interpreter uses `State` for environment threading. Each effect serves a distinct purpose:

- **Validated**: Independent sub-expression checks that accumulate all errors
- **State**: Sequential interpretation where results flow through `flatMap`

This separation is intentional. Type checking sub-expressions is *independent*: the type of the left operand doesn't determine whether we check the right. Interpretation is *sequential*: we must evaluate the condition before choosing a branch.

Higher-Kinded-J's design makes this distinction explicit through its type class hierarchy: `Validated` is an `Applicative` (for independent combination), whilst `State` is a `Monad` (for sequential dependency). Choosing the right abstraction isn't just academic; it affects whether errors accumulate or short-circuit. Get it wrong, and your users will notice.

---

## Optics and Effects Together

The real power comes when we combine our traversals with effects. Remember our `children()` traversal from Article 4:

```java
public static Traversal<Expr, Expr> children() {
    return new Traversal<>() {
        @Override
        public <F> Kind<F, Expr> modifyF(
                Function<Expr, Kind<F, Expr>> f,
                Expr source,
                Applicative<F> applicative) {
            return switch (source) {
                case Literal _ -> applicative.of(source);
                case Variable _ -> applicative.of(source);
                case Binary(var l, var op, var r) ->
                    applicative.map2(f.apply(l), f.apply(r),
                        (newL, newR) -> new Binary(newL, op, newR));
                case Conditional(var c, var t, var e) ->
                    applicative.map3(f.apply(c), f.apply(t), f.apply(e),
                        (newC, newT, newE) -> new Conditional(newC, newT, newE));
            };
        }
    };
}
```

This same traversal works with:

1. **Identity**: Pure transformations (our Article 4 optimiser)
2. **Maybe**: Transformations that might fail silently
3. **Validated**: Transformations that accumulate errors
4. **State**: Transformations that need context
5. **IO**: Transformations with side effects

We wrote the traversal once. Higher-Kinded-J's abstraction gives us all these behaviours. The `Applicative<F>` parameter is the magic switch that determines which effect system we're using. It's polymorphism at the effect level, and it's remarkably powerful.

### Example: Collecting Variables with State

```java
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;

// Define a collector that adds variable names to an immutable Set.
// State.modify returns Unit, so we use _ to indicate the unused parameter.
Function<Expr, State<Set<String>, Expr>> collector = e ->
    e instanceof Variable(var name)
        ? State.<Set<String>>modify(vars ->
              Stream.concat(vars.stream(), Stream.of(name))
                  .collect(Collectors.toUnmodifiableSet()))
            .map(_ -> e)  // _ indicates Unit is unused
        : State.pure(e);

// Use a recursive approach to visit all nodes
private static State<Set<String>, Expr> collectVariablesRecursive(
        Expr expr, Function<Expr, State<Set<String>, Expr>> collector) {
    State<Set<String>, Expr> thisNode = collector.apply(expr);
    return thisNode.flatMap(e -> switch (e) {
        case Literal _, Variable _ -> State.pure(e);
        case Binary(var l, var op, var r) ->
            collectVariablesRecursive(l, collector).flatMap(newL ->
                collectVariablesRecursive(r, collector).map(newR ->
                    new Binary(newL, op, newR)));
        case Conditional(var c, var t, var el) ->
            collectVariablesRecursive(c, collector).flatMap(newC ->
                collectVariablesRecursive(t, collector).flatMap(newT ->
                    collectVariablesRecursive(el, collector).map(newE ->
                        new Conditional(newC, newT, newE))));
    });
}

// Run the collection
StateTuple<Set<String>, Expr> result =
    collectVariablesRecursive(expr, collector).run(new HashSet<>());
Set<String> variables = result.state();
```

---

## Modern Java and Higher-Kinded-J: The Complete Picture

This article demonstrates something remarkable: Java can express the same effect-polymorphic patterns found in Haskell and Scala. Higher-Kinded-J makes this possible through a carefully designed encoding of higher-kinded types using witness types and defunctionalisation.

### Why This Matters for Data-Oriented Programming

Modern Java (21+) has embraced data-oriented programming:

- **Records** give us immutable data carriers
- **Sealed interfaces** enable exhaustive sum types
- **Pattern matching** makes destructuring elegant
- **Switch expressions** replace verbose visitor patterns

Higher-Kinded-J adds the missing piece: *effect-polymorphic operations* over these data structures. You can define a transformation once and run it with different effect systems (pure, stateful, error-accumulating, or asynchronous) without changing the core logic.

This is the "special sauce" that takes Java's data-oriented features to the next level. Records and sealed interfaces define your data. Pattern matching reads it. Optics write it. And Higher-Kinded-J lets you do all of this with effects cleanly abstracted away. It's data-oriented programming with superpowers.

### What Sets Higher-Kinded-J Apart

1. **Genuine abstraction, not simulation**: The `Kind<F, A>` encoding isn't a workaround; it's a principled approach that preserves the full power of higher-kinded polymorphism. When you write `modifyF`, you're writing truly generic code that works with any effect, not code that pattern-matches on a fixed set of cases.

2. **Lawful type classes**: The `Applicative` and `Monad` instances in Higher-Kinded-J satisfy their mathematical laws. This means your intuitions transfer directly from functional programming literature. `map2` on `Validated` accumulates errors because that's what the Applicative laws require for a type that isn't a Monad.

3. **Composition scales**: We've now seen optics compose with optics (Article 2), traversals compose with transformations (Article 4), and effects compose with optics (this article). Each composition multiplies capability without multiplying complexity. This compositional scaling is Higher-Kinded-J's central achievement.

4. **Java remains Java**: Despite these powerful abstractions, the code remains idiomatic Java. Records, sealed interfaces, pattern matching, and switch expressions all work naturally with Higher-Kinded-J's types. You're not fighting the language; you're extending its reach.

The expression language we've built across these articles now has type checking that reports all errors, interpretation that threads state cleanly, and optimisation that composes declaratively. All of this runs on the same traversal infrastructure, demonstrating that effect polymorphism is practical engineering that makes real code better.

---

## Summary

This article explored effect-polymorphic optics, where the same structural code works with different computational effects:

1. **Effect polymorphism**: Abstract over computational context with `Kind<F, A>`
2. **Applicative and Monad**: The type classes that enable effect composition
3. **Validated**: Accumulate all errors instead of failing fast
4. **State**: Thread context through computations implicitly
5. **modifyF**: The bridge between optics and effects
6. **ValidatedMonad.map2/map3**: The idiomatic way to combine validations in Higher-Kinded-J

The key takeaway: Higher-Kinded-J's abstractions eliminate boilerplate, prevent bugs, and make your code more expressive. The `map2` and `map3` methods alone save you from writing dozens of lines of error-prone pattern matching.

---

## What's Next

We've built a substantial expression language: AST definition, optics generation, tree traversals, optimisation passes, type checking, and interpretation. The foundation is solid, but there's more to explore.

In Article 6, we'll step back and reflect on what we've built:

- **The complete pipeline**: From source text through parsing, type checking, optimisation, and evaluation
- **Design patterns**: Common patterns for effect-polymorphic code
- **Performance considerations**: When to use optics and when simpler approaches suffice
- **Real-world applications**: Applying these techniques beyond expression languages

Higher-Kinded-J has shown us that Java can be a first-class functional programming language without abandoning its object-oriented roots. The combination of modern Java's data-oriented features with Higher-Kinded-J's effect abstractions creates a powerful toolkit for building robust, maintainable software. It's rather exciting, actually.

---

*Next: [Article 6: Retrospective and Real-World Applications](article-6-retrospective.md)*

# Building an Expression Language: Part 3, Effect-Polymorphic Optics

*Part 5 of the Functional Optics for Modern Java series*

In Article 4, we built traversals that visit every node in our expression tree. We implemented constant folding, identity simplification, and dead branch elimination. But all our transformations were pure: they took an expression and returned a new expression, with no side effects.

Real compilers and interpreters need more. Type checking should report *all* errors, not just the first one. Interpretation must track variable bindings as it descends through the tree. Logging might help debug complex transformations. These are *effects*, and they change everything about how we structure our code.

This is where Higher-Kinded-J reveals its full potential. The same traversals we wrote in Article 4 will work unchanged with effectful operations. We simply swap the effect type.

---

## The Problem with Effects

Consider type checking. A naive approach fails on the first error:

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

The problem: if `left` has an error, we never check `right`. Users see one error, fix it, recompile, see another error, and repeat. This frustrating cycle is avoidable.

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

This looks clean, but what if we add `let` bindings that extend the environment? Or mutable references? The environment threading becomes explicit and error-prone.

---

## Effect Polymorphism: The Core Idea

Effect polymorphism means writing code once that works with many different effects. Instead of hardcoding error handling or state threading, we abstract over the *computational context*.

In Higher-Kinded-J, this abstraction is the `Kind<F, A>` type: a value of type `A` wrapped in some effect `F`. Different choices of `F` give different behaviours:

| Effect Type | `Kind<F, A>` represents | Behaviour |
|-------------|------------------------|-----------|
| `Identity` | Just `A` | Pure computation, no effects |
| `Optional` | `A` or nothing | Computation that might fail |
| `Either<E, ?>` | `A` or error `E` | Fail-fast error handling |
| `Validated<E, ?>` | `A` or accumulated errors | Error accumulation |
| `State<S, ?>` | `A` with state `S` | Stateful computation |
| `IO` | Deferred `A` | Side effects |

The key insight: if we write our traversals to work with any `Kind<F, A>`, we get all these behaviours for free.

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

With just these two operations, we can sequence independent computations while accumulating their effects. For dependent computations (where the result of one affects what we do next), we need `Monad` and its `flatMap`.

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

Using Higher-Kinded-J's `State` monad, transformations can track state:

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
            State<Integer, Expr> countAndTransform =
                State.<Integer>modify(count -> count + 1).map(v -> new Literal(i * 10));
            return STATE.widen(countAndTransform);
        }
        return STATE.widen(State.pure(e));
    },
    expr,
    stateMonad);

StateTuple<Integer, Expr> stateResult = STATE.narrow(stateKind).run(0);
System.out.printf("Transformed: %s, count = %d%n",
    stateResult.value().format(), stateResult.state());
```

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

The crucial difference from `Either`: `Validated` forms an `Applicative` but *not* a `Monad`. This isn't a limitation; it's the feature. Without `flatMap`, independent validations run in parallel (logically), accumulating all their errors.

### Building a Type Checker

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

Now the type checker. We use Java 21+ pattern matching on `Valid`/`Invalid` to accumulate errors:

```java
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.validated.Invalid;
import org.higherkindedj.hkt.validated.Valid;
import org.higherkindedj.hkt.validated.Validated;

public final class ExprTypeChecker {

    private static final Semigroup<List<TypeError>> ERROR_SEMIGROUP = TypeError.semigroup();

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

        // Use Java 21+ pattern matching on Valid/Invalid to accumulate errors
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
    }

    private static Validated<List<TypeError>, Type> typeCheckConditional(
            Expr cond, Expr then_, Expr else_, TypeEnv env) {
        Validated<List<TypeError>, Type> condResult = typeCheck(cond, env);
        Validated<List<TypeError>, Type> thenResult = typeCheck(then_, env);
        Validated<List<TypeError>, Type> elseResult = typeCheck(else_, env);

        // Accumulate all sub-expression errors
        List<TypeError> subExprErrors = collectErrors(condResult, thenResult, elseResult);
        if (!subExprErrors.isEmpty()) {
            return Validated.invalid(subExprErrors);
        }

        // All sub-expressions valid - extract types and check constraints
        Type ct = ((Valid<List<TypeError>, Type>) condResult).value();
        Type tt = ((Valid<List<TypeError>, Type>) thenResult).value();
        Type et = ((Valid<List<TypeError>, Type>) elseResult).value();
        return checkConditionalTypes(ct, tt, et);
    }

    @SafeVarargs
    private static List<TypeError> collectErrors(Validated<List<TypeError>, Type>... results) {
        List<TypeError> errors = List.of();
        for (var result : results) {
            if (result instanceof Invalid(var errs)) {
                errors = ERROR_SEMIGROUP.combine(errors, errs);
            }
        }
        return errors;
    }

    private static Validated<List<TypeError>, Type> checkBinaryTypes(
            BinaryOp op, Type left, Type right) {
        return switch (op) {
            case ADD, SUB, MUL, DIV -> (left == Type.INT && right == Type.INT)
                ? Validated.valid(Type.INT)
                : Validated.invalid(TypeError.single(
                    "Arithmetic operator '%s' requires INT operands, got %s and %s"
                        .formatted(op.symbol(), left, right)));
            case AND, OR -> (left == Type.BOOL && right == Type.BOOL)
                ? Validated.valid(Type.BOOL)
                : Validated.invalid(TypeError.single(
                    "Logical operator '%s' requires BOOL operands, got %s and %s"
                        .formatted(op.symbol(), left, right)));
            case EQ, NE -> (left == right)
                ? Validated.valid(Type.BOOL)
                : Validated.invalid(TypeError.single(
                    "Equality operator '%s' requires matching types, got %s and %s"
                        .formatted(op.symbol(), left, right)));
            case LT, LE, GT, GE -> (left == Type.INT && right == Type.INT)
                ? Validated.valid(Type.BOOL)
                : Validated.invalid(TypeError.single(
                    "Comparison operator '%s' requires INT operands, got %s and %s"
                        .formatted(op.symbol(), left, right)));
        };
    }

    private static Validated<List<TypeError>, Type> checkConditionalTypes(
            Type cond, Type then_, Type else_) {
        List<TypeError> errors = List.of();

        if (cond != Type.BOOL) {
            errors = ERROR_SEMIGROUP.combine(errors,
                TypeError.single("Conditional requires BOOL condition, got %s".formatted(cond)));
        }

        if (then_ != else_) {
            errors = ERROR_SEMIGROUP.combine(errors,
                TypeError.single("Conditional branches must have same type, got %s and %s"
                    .formatted(then_, else_)));
        }

        return errors.isEmpty() ? Validated.valid(then_) : Validated.invalid(errors);
    }
}
```

### Running the Type Checker

```java
// Build an expression: (1 + true) - type error in left operand
Expr expr = new Binary(new Literal(1), BinaryOp.ADD, new Literal(true));
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
```

Both errors are reported in a single pass. This is the power of `Validated` with `Applicative`.

---

## Interpretation with State

For interpretation, we need to thread an environment through the computation. The `State` monad captures this pattern.

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

// Or using State directly:
StateTuple<Environment, Object> tuple = ExprInterpreter.interpret(expr).run(env);
Object value = tuple.value();      // 22
Environment finalEnv = tuple.state();  // unchanged environment
```

The environment is threaded implicitly through `flatMap`. We never pass it explicitly after the initial `eval` call.

---

## Combining Validated and State

Our type checker uses `Validated` for error accumulation, while our interpreter uses `State` for environment threading. Each effect serves a distinct purpose:

- **Validated**: Independent sub-expression checks that accumulate all errors
- **State**: Sequential interpretation where results flow through `flatMap`

This separation is intentional. Type checking sub-expressions is *independent*—the type of the left operand doesn't determine whether we check the right. Interpretation is *sequential*—we must evaluate the condition before choosing a branch.

Higher-Kinded-J's design makes this distinction explicit through its type class hierarchy: `Validated` is an `Applicative` (for independent combination), while `State` is a `Monad` (for sequential dependency).

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
2. **Optional**: Transformations that might fail
3. **Validated**: Transformations that accumulate errors
4. **State**: Transformations that need context
5. **IO**: Transformations with side effects

We wrote the traversal once. Higher-Kinded-J's abstraction gives us all these behaviours.

### Example: Collecting Variables with State

```java
import static org.higherkindedj.hkt.state.StateKindHelper.STATE;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateTuple;

// Define a collector that adds variable names to an immutable Set
Function<Expr, State<Set<String>, Expr>> collector = e ->
    e instanceof Variable(var name)
        ? State.<Set<String>>modify(vars ->
              Stream.concat(vars.stream(), Stream.of(name))
                  .collect(Collectors.toUnmodifiableSet()))
            .map(v -> e)
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

## Summary

This article explored effect-polymorphic optics, where the same structural code works with different computational effects:

1. **Effect polymorphism**: Abstract over computational context with `Kind<F, A>`
2. **Applicative and Monad**: The type classes that enable effect composition
3. **Validated**: Accumulate all errors instead of failing fast
4. **State**: Thread context through computations implicitly
5. **modifyF**: The bridge between optics and effects

### Higher-Kinded-J: Unlocking Java's Functional Potential

This article demonstrates something remarkable: Java can express the same effect-polymorphic patterns found in Haskell and Scala. Higher-Kinded-J makes this possible through a carefully designed encoding of higher-kinded types using witness types and defunctionalisation.

What sets Higher-Kinded-J apart is not just that it provides these abstractions, but *how* it provides them:

1. **Genuine abstraction, not simulation**: The `Kind<F, A>` encoding isn't a workaround; it's a principled approach that preserves the full power of higher-kinded polymorphism. When you write `modifyF`, you're writing truly generic code that works with any effect, not code that pattern-matches on a fixed set of cases.

2. **Lawful type classes**: The `Applicative` and `Monad` instances in Higher-Kinded-J satisfy their mathematical laws. This means your intuitions transfer directly from functional programming literature. `map2` on `Validated` accumulates errors because that's what the Applicative laws require for a type that isn't a Monad.

3. **Composition scales**: We've now seen optics compose with optics (Article 2), traversals compose with transformations (Article 4), and effects compose with optics (this article). Each composition multiplies capability without multiplying complexity. This compositional scaling is Higher-Kinded-J's central achievement.

4. **Java remains Java**: Despite these powerful abstractions, the code remains idiomatic Java. Records, sealed interfaces, pattern matching, and switch expressions all work naturally with Higher-Kinded-J's types. You're not fighting the language; you're extending its reach.

The expression language we've built across these articles now has type checking that reports all errors, interpretation that threads state cleanly, and optimisation that composes declaratively. All of this runs on the same traversal infrastructure, demonstrating that effect polymorphism isn't academic abstraction; it's practical engineering.

---

## What's Next

We've built a substantial expression language: AST definition, optics generation, tree traversals, optimisation passes, type checking, and interpretation. The foundation is solid, but there's more to explore.

In Article 6, we'll tackle parsing and complete the picture:

- **Parser combinators**: Build a parser using Higher-Kinded-J's applicative style
- **Error recovery**: Parse malformed input while collecting syntax errors
- **Source locations**: Thread position information through the entire pipeline
- **End-to-end demonstration**: From source text to evaluated result

Higher-Kinded-J's applicative functors prove invaluable for parsing. Parser combinators are inherently applicative: they combine independent parsers for different parts of the input. We'll see how the same `map2` and `map3` operations we used for type checking apply directly to parsing, creating a unified approach to combining computations.

We'll also explore how Higher-Kinded-J's `Alternative` type class enables choice and repetition in parsers, completing the toolkit for building practical expression parsers without external dependencies.

---

*Next: [Article 6: Parser Combinators and the Complete Pipeline](article-6-parser-combinators.md)*

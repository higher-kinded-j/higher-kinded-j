# Building an Expression Language: Part 1, The AST and Basic Optics

*Part 3 of the Functional Optics for Modern Java series*

In Articles 1 and 2, we established why optics matter and how they work. Now it's time to apply them to a real domain: an expression language interpreter.

Expression languages are the backbone of modern Java infrastructure, from Spring Expression Language (SpEL) to rule engines like Drools. If you've ever configured a complex Spring application or written business rules, you've used one. This article shows how Java 25's data-oriented programming features, combined with higher-kinded-j optics, make building such systems remarkably clean.

This is the canonical showcase for optics: the domain where they truly shine.

Over the next three articles, we'll build a complete expression language with parsing, type checking, optimisation, and interpretation. Along the way, you'll see how optics transform what would otherwise be tedious tree manipulation into elegant, composable operations.

---

## The Expression Language Domain

What exactly are we building? A small but powerful expression language suitable for:

- **Configuration expressions**: `if (env == "prod") then timeout * 2 else timeout`
- **Rule engines**: `price > 100 && customer.tier == "gold"`
- **Template systems**: `"Hello, " + user.name + "!"`
- **Domain-specific calculations**: `principal * (1 + rate) ^ years`

The language will support:
- Literal values (integers, booleans, strings)
- Variables with lexical scoping
- Binary operations (arithmetic, comparison, logical)
- Conditional expressions (if-then-else)

Our design goals are:
1. **Type-safe**: The compiler catches structural errors
2. **Immutable**: Expressions never change; transformations produce new trees
3. **Transformable**: Easy to analyse, optimise, and rewrite

This third goal is where optics become essential. An expression tree is a recursive structure where any node might contain arbitrarily nested sub-expressions. Transforming such trees manually (with pattern matching and reconstruction) quickly becomes unwieldy. Optics provide a disciplined approach.

### Building on Data-Oriented Foundations

Article 1 introduced Java 25's data-oriented programming features. As Brian Goetz articulates in *Data-Oriented Programming in Java*, the core insight is that "data is just data": immutable, transparent, and separate from behaviour. Records, sealed interfaces, and pattern matching embody this philosophy beautifully.

Eric Normand's work on data-oriented programming (from the Clojure tradition) takes this further: behaviour should be implemented as pure functions over immutable data, with polymorphism achieved through pattern matching rather than method dispatch. Java 25 now supports this style naturally.

Yet there's a tension that pure DOP doesn't fully address. When behaviour is separate from data, *where do structural operations live*? Functions that transform nested trees must understand the shape of every node. In Normand's terms, we've separated "what the data is" from "what we do with it", but tree transformations blur this boundary: they're operations intimately tied to structure.

This is precisely where optics prove their worth. They're not behaviour embedded in data (that would violate DOP principles). They're *reified access paths*: first-class values representing the structure of your types. The structure of a record implies its lenses; the variants of a sealed interface imply its prisms. Optics make this correspondence explicit and composable.

---

## Designing the AST

An Abstract Syntax Tree (AST) represents the structure of code as a tree of nodes. Each node type corresponds to a language construct.

### Start Simple

We'll begin with a minimal four-variant AST:

```java
public sealed interface Expr {
    record Literal(Object value) implements Expr {}
    record Variable(String name) implements Expr {}
    record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}
    record Conditional(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {}
}

public enum BinaryOp {
    ADD, SUB, MUL, DIV,    // Arithmetic
    EQ, NE, LT, LE, GT, GE, // Comparison
    AND, OR                 // Logical
}
```

This covers more than you might expect:
- `Literal(42)`: integer constant
- `Literal(true)`: boolean constant
- `Variable("x")`: variable reference
- `Binary(Variable("a"), ADD, Literal(1))`: `a + 1`
- `Conditional(Variable("flag"), Literal(1), Literal(0))`: `if flag then 1 else 0`

The recursive nature is already apparent: `Binary` contains two `Expr` children, and `Conditional` contains three. Any transformation must handle this recursion.

### Why Sealed Interfaces?

The `sealed` keyword is Java's answer to *sum types*: a closed set of variants that the compiler understands completely. When we write a switch over `Expr`, Java 25 guarantees exhaustiveness:

```java
String describe(Expr expr) {
    return switch (expr) {
        case Literal(var v) -> "Literal: " + v;
        case Variable(var n) -> "Variable: " + n;
        case Binary(var l, var op, var r) -> "Binary: " + op;
        case Conditional(_, _, _) -> "Conditional";
    };  // No default needed; compiler knows this is exhaustive
}
```

Notice the unnamed pattern `_` for components we don't need, a Java 22+ feature that reduces noise. If we later add a new variant like `FunctionCall`, the compiler flags every incomplete switch. This compile-time safety is essential for language implementations where missing a case means silent bugs.

### Why Records?

Records give us:
- Immutability by default
- Automatic `equals()`, `hashCode()`, `toString()`
- Pattern matching with deconstruction
- A natural fit for optics (each component becomes a lens target)

The combination of sealed interfaces and records creates what functional programmers call an *algebraic data type* (ADT): a sum of products that's both type-safe and pattern-matchable.

### The Visitor Pattern: A DOP Perspective

The traditional Visitor pattern embeds traversal logic into your data types via `accept()` methods. This violates a core DOP principle: data should be transparent and behaviour-free. As Goetz notes, the Visitor pattern exists largely because Java lacked pattern matching; it's a workaround, not a solution.

With sealed interfaces and pattern matching, we can write operations as standalone functions:

```java
Expr foldConstants(Expr expr) {
    return switch (expr) {
        case Binary(Literal(var l), var op, Literal(var r)) ->
            evaluate(l, op, r).map(Literal::new).orElse(expr);
        case Binary(var l, var op, var r) ->
            new Binary(foldConstants(l), op, foldConstants(r));
        case Conditional(var c, var t, var e) ->
            new Conditional(foldConstants(c), foldConstants(t), foldConstants(e));
        case Literal _, Variable _ -> expr;
    };
}
```

No interfaces to implement, no `accept()` methods polluting our data types, and the compiler verifies exhaustiveness. This is DOP in action: pure data, external functions, pattern-matched polymorphism.

Yet notice the manual reconstruction in the `Binary` and `Conditional` cases. This recursive boilerplate appears in *every* tree transformation. DOP gives us elegant reading; the reconstruction cascade remains.

---

## Generating Optics for the AST

This reconstruction problem is precisely what optics solve. With two annotations, Higher-Kinded-J generates a complete toolkit for navigating and transforming our AST:

```java
@GeneratePrisms  // Generates prisms for each sealed variant
public sealed interface Expr {
    @GenerateLenses record Literal(Object value) implements Expr {}
    @GenerateLenses record Variable(String name) implements Expr {}
    @GenerateLenses record Binary(Expr left, BinaryOp op, Expr right) implements Expr {}
    @GenerateLenses record Conditional(Expr cond, Expr thenBranch, Expr elseBranch) implements Expr {}
}
```

At compile time, higher-kinded-j's annotation processor generates:

### Prisms for Each Variant

```java
// Generated: ExprPrisms.java
public final class ExprPrisms {
    public static Prism<Expr, Literal> literal() { ... }
    public static Prism<Expr, Variable> variable() { ... }
    public static Prism<Expr, Binary> binary() { ... }
    public static Prism<Expr, Conditional> conditional() { ... }
}
```

Each prism lets us:
- Check if an `Expr` is a specific variant
- Extract the variant if it matches
- Transform just that variant, leaving others unchanged

### Lenses for Each Field

```java
// Generated: LiteralLenses.java
public final class LiteralLenses {
    public static Lens<Literal, Object> value() { ... }
}

// Generated: BinaryLenses.java
public final class BinaryLenses {
    public static Lens<Binary, Expr> left() { ... }
    public static Lens<Binary, BinaryOp> op() { ... }
    public static Lens<Binary, Expr> right() { ... }
}
```

Each lens lets us:
- Get a field from a node
- Set a field, producing a new node
- Modify a field with a function

### Composition: Where Optics Earn Their Keep

The payoff comes when we compose these optics:

```java
// Focus on the left operand's value (if it's a literal)
Optional<Binary, Object> leftLiteralValue =
    BinaryLenses.left()
        .andThen(ExprPrisms.literal())
        .andThen(LiteralLenses.value());

// Check if a binary expression has a literal on the left
Binary expr = new Binary(new Literal(5), ADD, new Variable("x"));
Optional<Object> value = leftLiteralValue.getOptional(expr);
// Optional[5]
```

We've navigated from `Binary` → `left` (Expr) → as `Literal` → `value`, all type-safe and composable.

### Why Optics Instead of Just Pattern Matching?

You might wonder: "Java 25's pattern matching is already elegant; why add another abstraction?"

Three compelling reasons:

**1. Composition Across Depth**

Pattern matching handles one level at a time. To reach a deeply nested value, you nest matches:

```java
// Pattern matching: explicit nesting
if (expr instanceof Binary(Binary(var ll, _, _), _, _)) {
    // Access left-of-left
}
```

Optics compose to arbitrary depth with a single expression:

```java
// Optics: composed path
var leftOfLeft = ExprPrisms.binary()
    .andThen(BinaryLenses.left())
    .andThen(ExprPrisms.binary())
    .andThen(BinaryLenses.left());
```

**2. Bidirectional Access**

Pattern matching *reads* data. Optics *read and write*:

```java
// Read the left operand
Optional<Expr> left = leftLens.getOptional(expr);

// Replace the left operand (returns new immutable tree)
Expr updated = leftLens.set(newLeft, expr);

// Transform the left operand
Expr transformed = leftLens.modify(this::optimize, expr);
```

The `modify` operation is particularly powerful: it extracts, transforms, and rebuilds in one step, handling all the immutable reconstruction automatically.

**3. Reusable Paths**

A pattern match is code you write inline. An optic is a *value* you can store, pass, and reuse:

```java
// Define once
private static final Lens<Binary, Expr> leftOperand = BinaryLenses.left();

// Use anywhere
Expr opt1 = leftOperand.modify(this::fold, expr1);
Expr opt2 = leftOperand.modify(this::fold, expr2);
```

This becomes invaluable when the same traversal pattern appears across multiple transformations, which happens constantly in real compilers and interpreters.

---

## Basic Transformations

Let's implement some fundamental AST transformations using optics.

### Transforming Literals

Suppose we want to increment all integer literals by one:

```java
public static Expr incrementLiterals(Expr expr) {
    Prism<Expr, Literal> literalPrism = ExprPrisms.literal();

    return literalPrism.modify(lit -> {
        if (lit.value() instanceof Integer i) {
            return new Literal(i + 1);
        }
        return lit;
    }, expr);
}
```

But wait: this only transforms the top-level expression. If the literal is nested inside a `Binary`, it won't be touched. We need recursion.

### The Recursive Challenge

Here's the manual approach to transforming all literals in a tree:

```java
public static Expr incrementAllLiterals(Expr expr) {
    return switch (expr) {
        case Literal(var v) ->
            v instanceof Integer i ? new Literal(i + 1) : expr;
        case Variable(_) -> expr;
        case Binary(var l, var op, var r) ->
            new Binary(incrementAllLiterals(l), op, incrementAllLiterals(r));
        case Conditional(var c, var t, var e) ->
            new Conditional(
                incrementAllLiterals(c),
                incrementAllLiterals(t),
                incrementAllLiterals(e));
    };
}
```

This works, but it's tedious. Every transformation requires the same recursive boilerplate. We're manually threading the transformation through every node type.

### A Reusable Transformation Pattern

We can extract the recursion into a reusable function:

```java
public static Expr transformExpr(Expr expr, Function<Expr, Expr> transform) {
    // First, recursively transform children
    Expr transformed = switch (expr) {
        case Literal(_), Variable(_) -> expr;
        case Binary(var l, var op, var r) ->
            new Binary(transformExpr(l, transform), op, transformExpr(r, transform));
        case Conditional(var c, var t, var e) ->
            new Conditional(
                transformExpr(c, transform),
                transformExpr(t, transform),
                transformExpr(e, transform));
    };

    // Then apply the transformation to this node
    return transform.apply(transformed);
}
```

Now our increment becomes:

```java
Expr result = transformExpr(expr, e ->
    ExprPrisms.literal().modify(lit ->
        lit.value() instanceof Integer i ? new Literal(i + 1) : lit,
        e));
```

In Article 4, we'll see how traversals make this even more elegant. For now, let's work with what we have.

---

## Working with the Sum Type

Prisms shine when working with the variants of our sealed interface.

### Safe Type Checking

Traditional instanceof checks are verbose and error-prone:

```java
if (expr instanceof Binary binary) {
    if (binary.left() instanceof Literal leftLit) {
        // do something with leftLit
    }
}
```

With prisms, we compose the checks:

```java
Optional<Expr, Object> leftLiteralValue =
    ExprPrisms.binary()
        .andThen(BinaryLenses.left())
        .andThen(ExprPrisms.literal())
        .andThen(LiteralLenses.value());

Optional<Object> value = leftLiteralValue.getOptional(expr);
```

The composed optic handles all the type checking internally.

### Conditional Transformation

Prisms let us transform specific variants while leaving others unchanged:

```java
// Double all integer literals, leave everything else alone
Expr doubled = ExprPrisms.literal().modify(lit -> {
    if (lit.value() instanceof Integer i) {
        return new Literal(i * 2);
    }
    return lit;
}, expr);
```

If `expr` isn't a `Literal`, it's returned unchanged. No explicit instanceof check needed.

### Pattern-Based Matching

We can combine pattern matching with optics for complex structural tests:

```java
// Match: Binary with ADD operator and Literal(0) on the right
// This is the pattern for "x + 0" which we can simplify to "x"
public static Optional<Expr> matchAddZero(Expr expr) {
    return switch (expr) {
        case Binary(var left, BinaryOp.ADD, Literal(Integer v)) when v == 0 ->
            Optional.of(left);
        default -> Optional.empty();
    };
}
```

This pattern matching is where Java's native features work well alongside optics. Use pattern matching for complex structural tests; use optics for transformations and deep access.

---

## Building a Simple Optimiser

Let's put everything together to build a constant folder: an optimiser that evaluates constant expressions at compile time.

### Constant Folding

The idea is simple: if both operands of a binary expression are literals, we can compute the result:

```java
public static Expr foldConstants(Expr expr) {
    return transformExpr(expr, ExprOptimiser::foldBinary);
}

private static Expr foldBinary(Expr expr) {
    // Java 25: Switch with nested record patterns
    return switch (expr) {
        case Binary(Literal(Object lv), BinaryOp op, Literal(Object rv)) -> {
            Object result = evaluate(lv, op, rv);
            yield result != null ? new Literal(result) : expr;
        }
        default -> expr;
    };
}

private static Object evaluate(Object left, BinaryOp op, Object right) {
    return switch (left) {
        case Integer l when right instanceof Integer r -> switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> r != 0 ? l / r : null;
            case EQ -> l.equals(r);
            case NE -> !l.equals(r);
            case LT -> l < r;
            case LE -> l <= r;
            case GT -> l > r;
            case GE -> l >= r;
            default -> null;
        };
        case Boolean l when right instanceof Boolean r -> switch (op) {
            case AND -> l && r;
            case OR -> l || r;
            case EQ -> l.equals(r);
            case NE -> !l.equals(r);
            default -> null;
        };
        default -> null;
    };
}
```

### Example: Folding `(1 + 2) * 3`

```java
Expr expr = new Binary(
    new Binary(new Literal(1), ADD, new Literal(2)),
    MUL,
    new Literal(3)
);

System.out.println("Before: " + format(expr));
// Before: ((1 + 2) * 3)

Expr optimised = foldConstants(expr);
System.out.println("After: " + format(optimised));
// After: 9
```

The optimiser transforms `((1 + 2) * 3)` to `9` in a single pass. The inner `1 + 2` is folded to `3`, then `3 * 3` is folded to `9`.

### Identity Simplification

We can add more optimisations using Java 25's enhanced pattern matching with guards:

```java
private static Expr simplifyIdentities(Expr expr) {
    return switch (expr) {
        // x + 0 = x, x * 1 = x
        case Binary(var left, BinaryOp.ADD, Literal(Integer v)) when v == 0 -> left;
        case Binary(var left, BinaryOp.MUL, Literal(Integer v)) when v == 1 -> left;
        // x * 0 = 0
        case Binary(_, BinaryOp.MUL, Literal(Integer v)) when v == 0 -> new Literal(0);
        // 0 + x = x, 1 * x = x
        case Binary(Literal(Integer v), BinaryOp.ADD, var right) when v == 0 -> right;
        case Binary(Literal(Integer v), BinaryOp.MUL, var right) when v == 1 -> right;
        // 0 * x = 0
        case Binary(Literal(Integer v), BinaryOp.MUL, _) when v == 0 -> new Literal(0);
        default -> expr;
    };
}
```

### Composing Optimisations

Multiple optimisation passes compose naturally:

```java
public static Expr optimise(Expr expr) {
    Expr result = expr;
    Expr previous;

    // Run until fixed point (no more changes)
    do {
        previous = result;
        result = transformExpr(result, e ->
            simplifyIdentities(foldBinary(e)));
    } while (!result.equals(previous));

    return result;
}
```

This runs both optimisations repeatedly until the expression stops changing. The immutability of our AST makes equality checking trivial; we can use `equals()` directly.

---

## Summary

We've built a solid foundation for expression language development using Java 25's data-oriented programming features:

- **Sealed interfaces** define a closed universe of expression types
- **Records** provide immutable, transparent data carriers
- **Pattern matching** enables elegant, exhaustive case analysis
- **Optics** (via Higher-Kinded-J) add composable, bidirectional transformations

### Optics and the DOP Philosophy

There's a deeper point here worth pausing on. Critics of pure data-oriented programming sometimes observe that while DOP excels at *describing* data, it offers less guidance on *transforming* it. Pattern matching destructures beautifully but reconstructs tediously. The purity of "data separate from behaviour" runs into friction when behaviour must intimately understand data's shape.

Optics resolve this tension elegantly. They're not methods on your data types (that would embed behaviour). They're not external functions that pattern-match (that leads to reconstruction cascades). They're something new: *structural correspondences* reified as values.

The `@GenerateLenses` and `@GeneratePrisms` annotations embody this insight. The structure of a record *implies* its lenses; the variants of a sealed interface *imply* its prisms. Higher-Kinded-J makes these implications explicit and composable. You're not adding behaviour to data; you're deriving navigation from structure.

This is why optics feel natural alongside DOP rather than against it. Both emphasise that structure should be transparent and operations should compose. Optics simply extend these principles from reading to writing.

---

## What's Next

There's a limitation in our current approach: the `transformExpr` function is hand-written boilerplate. Every time we add a new expression type, we must update it. This violates the DRY principle and risks bugs when someone forgets.

In Article 4, we'll introduce *traversals*, optics that focus on multiple values simultaneously. With a traversal over all sub-expressions, we can:

- **Eliminate the manual recursion**: One traversal replaces the switch-based boilerplate
- **Collect all variables** in an expression with a single composed optic
- **Implement sophisticated rewrites**: Pattern-based transformations become declarative

Higher-Kinded-J's traversal support will prove essential here. The library provides not just the `Traversal` type, but utilities for folding over focused values, filtering by predicates, and composing traversals with lenses and prisms. This means our expression-walking logic becomes a composable value we can store, pass around, and reuse, rather than ad-hoc code scattered throughout the codebase.

We'll also tackle dead code elimination and common subexpression elimination, transformations that showcase how optics scale to real compiler optimisations.

---

## Further Reading

### Algebraic Data Types in Java

- **Brian Goetz, ["Towards Better Serialization"](https://openjdk.org/projects/amber/design-notes/towards-better-serialization)**: Explores how records and sealed types create ADTs, with implications for pattern matching.

- **Gavin King, ["Algebraic Data Types in Ceylon and Java"](https://ceylon-lang.org/blog/2015/12/07/algebraic-data-types/)**: A comparative look at ADTs across JVM languages, illuminating what Java 25 now provides natively.

### Expression Languages and AST Design

- **Terence Parr, *Language Implementation Patterns*** (Pragmatic Bookshelf, 2010): The definitive guide to building interpreters and compilers, covering AST design patterns we use here.

- **Robert Nystrom, [*Crafting Interpreters*](https://craftinginterpreters.com/)**: A freely available, beautifully written guide to interpreter construction. The "tree-walk interpreter" chapters parallel our approach.

### DOP and the Expression Problem

- **Philip Wadler, ["The Expression Problem"](http://homepages.inf.ed.ac.uk/wadler/papers/expression/expression.txt)** (1998): The classic formulation of the tension between adding new data types versus new operations. DOP with optics offers one resolution.

- The expression problem asks: can you add both new variants *and* new operations without modifying existing code? Sealed interfaces make adding operations easy (just write a function); optics make the operations themselves composable and reusable.

### Higher-Kinded-J

- **[Prism Documentation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/optics/Prism.java)**: API reference for working with sum types.

- **[@GeneratePrisms Annotation](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-processor-plugins)**: How the annotation processor generates prisms for sealed interfaces.

---

*Next: [Article 4: Tree Traversals and Pattern Rewrites](article-4-traversals-rewrites.md)*

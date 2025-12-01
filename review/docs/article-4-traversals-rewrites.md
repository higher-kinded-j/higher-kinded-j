# Building an Expression Language: Part 2, Tree Traversals and Pattern Rewrites

*Part 4 of the Functional Optics for Modern Java series*

In Article 3, we built our expression language AST and applied basic optics (lenses for field access and prisms for variant matching). We even created a simple optimiser. But there's a fundamental limitation we need to address: how do we visit *all* nodes in a tree, not just the top level?

This is where traversals become essential. A traversal focuses on zero or more elements within a structure, making it perfect for recursive tree operations.

---

## The Recursive Challenge

Consider our expression AST. When we write `(x + 1) * (y + 2)`, we get:

```java
new Binary(
    new Binary(new Variable("x"), ADD, new Literal(1)),
    MUL,
    new Binary(new Variable("y"), ADD, new Literal(2))
)
```

This tree has seven nodes: three `Binary` expressions, two `Variable` nodes, and two `Literal` nodes. If we want to find all variables, we can't just look at the top level; we need to descend into every branch.

As we discussed in Article 3, the traditional Visitor pattern requires substantial boilerplate: an interface, accept methods in each node, and a visitor implementation for every operation. Even with Java 25's pattern matching, we still face the reconstruction cascade for transformations.

Traversals offer something better: define the traversal structure once, then use it for any operation.

---

## Building a Universal Expression Traversal

A `Traversal<S, A>` focuses on zero or more `A` values within an `S` structure. For expressions, we want `Traversal<Expr, Expr>`: a traversal that visits all sub-expressions within an expression.

First, let's define what "all sub-expressions" means for each variant:

| Expression Type | Sub-expressions |
|-----------------|-----------------|
| `Literal` | None |
| `Variable` | None |
| `Binary` | `left`, `right` |
| `Conditional` | `cond`, `then_`, `else_` |

Here's the traversal implementation:

```java
public final class ExprTraversal {

    /**
     * A traversal targeting all immediate children of an expression.
     * Does not descend recursively; use with transform utilities for full tree traversal.
     */
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
}
```

This is effect-polymorphic: the same traversal works with any `Applicative` functor. We can use it for pure transformations, error-accumulating validation, or stateful operations.

For simpler use cases, Higher-Kinded-J provides the `Traversals.modify` utility:

```java
import org.higherkindedj.optics.util.Traversals;

// Apply a pure transformation to all children
Expr result = Traversals.modify(children(), f, expr);
```

This handles the `Id` effect internally, giving you a clean API for pure transformations.

### Deep Traversal: Visiting All Descendants

The `children()` traversal only visits immediate children. For full tree traversal, we combine it with recursive descent:

```java
/**
 * Transform all nodes in the tree from leaves to root (bottom-up).
 * Each node is transformed after its children.
 */
public static Expr transformBottomUp(Expr expr, Function<Expr, Expr> f) {
    // First transform all children recursively
    Expr transformed = Traversals.modify(
        children(),
        child -> transformBottomUp(child, f),
        expr
    );
    // Then transform this node
    return f.apply(transformed);
}

/**
 * Transform all nodes in the tree from root to leaves (top-down).
 * Each node is transformed before its children.
 */
public static Expr transformTopDown(Expr expr, Function<Expr, Expr> f) {
    // First transform this node
    Expr transformed = f.apply(expr);
    // Then transform all children recursively
    return Traversals.modify(
        children(),
        child -> transformTopDown(child, f),
        transformed
    );
}
```

The choice between bottom-up and top-down matters:

- **Bottom-up**: Children are transformed first. Use this when transformations depend on the structure of children (like constant folding, where we need to fold `1 + 2` before we can simplify `(1 + 2) * 3`).

- **Top-down**: The node is transformed first. Use this when you want to pattern-match on the original structure before children change (like macro expansion).

---

## Collecting Information

Traversals aren't just for modification; they're equally powerful for extraction. We use *folds* to aggregate information from all focused elements.

### Finding All Variables

```java
public static Set<String> findVariables(Expr expr) {
    Set<String> vars = new HashSet<>();
    collectVariables(expr, vars);
    return vars;
}

private static void collectVariables(Expr expr, Set<String> accumulator) {
    switch (expr) {
        case Variable(var name) -> accumulator.add(name);
        case Literal _ -> { }
        case Binary(var l, _, var r) -> {
            collectVariables(l, accumulator);
            collectVariables(r, accumulator);
        }
        case Conditional(var c, var t, var e) -> {
            collectVariables(c, accumulator);
            collectVariables(t, accumulator);
            collectVariables(e, accumulator);
        }
    }
}
```

With traversals and prisms, we can make this more compositional:

```java
public static Set<String> findVariables(Expr expr) {
    return foldMap(
        expr,
        e -> ExprPrisms.variable().getOptional(e)
            .map(v -> Set.of(v.name()))
            .orElse(Set.of()),
        Sets::union
    );
}
```

The `foldMap` function traverses the entire tree, extracts data from each node, and combines results using a monoid (here, set union).

### Counting Nodes by Type

```java
public record NodeCounts(int literals, int variables, int binaries, int conditionals) {
    public static NodeCounts ZERO = new NodeCounts(0, 0, 0, 0);

    public NodeCounts add(NodeCounts other) {
        return new NodeCounts(
            literals + other.literals,
            variables + other.variables,
            binaries + other.binaries,
            conditionals + other.conditionals
        );
    }
}

public static NodeCounts countNodes(Expr expr) {
    return foldMap(
        expr,
        e -> switch (e) {
            case Literal _ -> new NodeCounts(1, 0, 0, 0);
            case Variable _ -> new NodeCounts(0, 1, 0, 0);
            case Binary _ -> new NodeCounts(0, 0, 1, 0);
            case Conditional _ -> new NodeCounts(0, 0, 0, 1);
        },
        NodeCounts::add
    );
}
```

### The foldMap Implementation

```java
public static <A> A foldMap(Expr expr, Function<Expr, A> extract, BinaryOperator<A> combine) {
    A current = extract.apply(expr);
    return switch (expr) {
        case Literal _, Variable _ -> current;
        case Binary(var l, _, var r) ->
            combine.apply(current,
                combine.apply(foldMap(l, extract, combine),
                              foldMap(r, extract, combine)));
        case Conditional(var c, var t, var e) ->
            combine.apply(current,
                combine.apply(foldMap(c, extract, combine),
                    combine.apply(foldMap(t, extract, combine),
                                  foldMap(e, extract, combine))));
    };
}
```

---

## Filtered Traversals

Sometimes we only want to focus on certain nodes. Filtered traversals combine a traversal with a predicate or prism.

### Targeting Only Binary Additions

```java
public static Expr doubleAllAdditions(Expr expr) {
    return transformBottomUp(expr, e -> {
        if (e instanceof Binary(var l, BinaryOp.ADD, var r)) {
            // Transform a + b into (a + b) + (a + b)
            return new Binary(e, BinaryOp.ADD, e);
        }
        return e;
    });
}
```

### Using Prisms for Type-Safe Filtering

```java
public static List<BinaryOp> findAllOperators(Expr expr) {
    List<BinaryOp> ops = new ArrayList<>();
    collectWhere(expr, ExprPrisms.binary(), bin -> ops.add(bin.op()));
    return ops;
}

private static <A> void collectWhere(Expr expr, Prism<Expr, A> prism, Consumer<A> action) {
    prism.getOptional(expr).ifPresent(action);
    switch (expr) {
        case Binary(var l, _, var r) -> {
            collectWhere(l, prism, action);
            collectWhere(r, prism, action);
        }
        case Conditional(var c, var t, var e) -> {
            collectWhere(c, prism, action);
            collectWhere(t, prism, action);
            collectWhere(e, prism, action);
        }
        default -> { }
    }
}
```

---

## Implementing Optimisation Passes

With traversals in place, we can build sophisticated optimisation passes. Each pass is a transformation function; the optimiser composes them.

### Pass 1: Constant Folding

Evaluate operations where both operands are literals:

```java
public static Expr foldConstants(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::foldConstant);
}

private static Expr foldConstant(Expr expr) {
    if (expr instanceof Binary(Literal(var l), var op, Literal(var r))) {
        return evaluateBinary(l, op, r)
            .map(result -> (Expr) new Literal(result))
            .orElse(expr);
    }
    return expr;
}

private static Optional<Object> evaluateBinary(Object left, BinaryOp op, Object right) {
    return switch (op) {
        case ADD -> evaluateAdd(left, right);
        case SUB -> evaluateSub(left, right);
        case MUL -> evaluateMul(left, right);
        case DIV -> evaluateDiv(left, right);
        case AND -> evaluateAnd(left, right);
        case OR -> evaluateOr(left, right);
        case EQ -> Optional.of(left.equals(right));
        case NE -> Optional.of(!left.equals(right));
        case LT, LE, GT, GE -> evaluateComparison(left, op, right);
    };
}
```

### Pass 2: Identity Simplification

Remove operations that don't change the result:

```java
public static Expr simplifyIdentities(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::simplifyIdentity);
}

private static Expr simplifyIdentity(Expr expr) {
    return switch (expr) {
        // x + 0 → x, 0 + x → x
        case Binary(var x, ADD, Literal(Integer i)) when i == 0 -> x;
        case Binary(Literal(Integer i), ADD, var x) when i == 0 -> x;

        // x * 1 → x, 1 * x → x
        case Binary(var x, MUL, Literal(Integer i)) when i == 1 -> x;
        case Binary(Literal(Integer i), MUL, var x) when i == 1 -> x;

        // x * 0 → 0, 0 * x → 0
        case Binary(_, MUL, Literal(Integer i)) when i == 0 -> new Literal(0);
        case Binary(Literal(Integer i), MUL, _) when i == 0 -> new Literal(0);

        // x && true → x, true && x → x
        case Binary(var x, AND, Literal(Boolean b)) when b -> x;
        case Binary(Literal(Boolean b), AND, var x) when b -> x;

        // x || false → x, false || x → x
        case Binary(var x, OR, Literal(Boolean b)) when !b -> x;
        case Binary(Literal(Boolean b), OR, var x) when !b -> x;

        // x && false → false
        case Binary(_, AND, Literal(Boolean b)) when !b -> new Literal(false);
        case Binary(Literal(Boolean b), AND, _) when !b -> new Literal(false);

        // x || true → true
        case Binary(_, OR, Literal(Boolean b)) when b -> new Literal(true);
        case Binary(Literal(Boolean b), OR, _) when b -> new Literal(true);

        default -> expr;
    };
}
```

### Pass 3: Dead Branch Elimination

Remove conditional branches with constant conditions:

```java
public static Expr eliminateDeadBranches(Expr expr) {
    return transformBottomUp(expr, ExprOptimiser::eliminateDeadBranch);
}

private static Expr eliminateDeadBranch(Expr expr) {
    return switch (expr) {
        case Conditional(Literal(Boolean b), var t, var e) -> b ? t : e;
        default -> expr;
    };
}
```

### Pass 4: Common Subexpression Detection

Identify repeated subexpressions (useful for let-binding in future articles):

```java
public static Map<Expr, Integer> findCommonSubexpressions(Expr expr) {
    Map<Expr, Integer> counts = new HashMap<>();
    countSubexpressions(expr, counts);
    // Return only expressions that appear more than once
    return counts.entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
}

private static void countSubexpressions(Expr expr, Map<Expr, Integer> counts) {
    counts.merge(expr, 1, Integer::sum);
    switch (expr) {
        case Binary(var l, _, var r) -> {
            countSubexpressions(l, counts);
            countSubexpressions(r, counts);
        }
        case Conditional(var c, var t, var e) -> {
            countSubexpressions(c, counts);
            countSubexpressions(t, counts);
            countSubexpressions(e, counts);
        }
        default -> { }
    }
}
```

---

## Extending the AST: Source Locations

Real compilers need to track where expressions come from for error messages. Let's extend our AST with source location metadata.

```java
public record SourceLocation(String file, int line, int column) {
    @Override
    public String toString() {
        return file + ":" + line + ":" + column;
    }
}

public record Located<T>(T value, SourceLocation location) {
    public <U> Located<U> map(Function<T, U> f) {
        return new Located<>(f.apply(value), location);
    }
}
```

Now we can create `Located<Expr>` to track positions. But there's a challenge: when we transform an expression, what happens to the location?

### Preserving Locations Through Transformations

```java
public static Located<Expr> transformPreservingLocation(
        Located<Expr> located,
        Function<Expr, Expr> f) {
    return located.map(f);
}
```

For more sophisticated location handling (like updating locations when inlining code), indexed optics become valuable. We'll explore those in Article 5.

---

## The Complete Optimiser

Combining all passes into a single optimiser:

```java
public final class ExprOptimiser {

    /**
     * Run all optimisation passes until the expression stops changing.
     */
    public static Expr optimise(Expr expr) {
        Expr current = expr;
        Expr previous;

        do {
            previous = current;
            current = runAllPasses(current);
        } while (!current.equals(previous));

        return current;
    }

    private static Expr runAllPasses(Expr expr) {
        Expr result = expr;
        result = foldConstants(result);
        result = simplifyIdentities(result);
        result = eliminateDeadBranches(result);
        return result;
    }
}
```

The fixed-point iteration ensures we catch cascading simplifications. For example:

```
(0 * x) + (1 + 2)
→ 0 + 3         (constant folding, identity)
→ 3             (identity simplification)
```

### Example: Complex Optimisation

```java
// if (true && (1 < 2)) then (x + 0) * 1 else y
Expr complex = new Conditional(
    new Binary(new Literal(true), AND,
        new Binary(new Literal(1), LT, new Literal(2))),
    new Binary(
        new Binary(new Variable("x"), ADD, new Literal(0)),
        MUL,
        new Literal(1)),
    new Variable("y")
);

Expr optimised = ExprOptimiser.optimise(complex);
// Result: Variable("x")
```

The optimiser:
1. Folds `1 < 2` → `true`
2. Simplifies `true && true` → `true`
3. Eliminates the dead else-branch
4. Simplifies `x + 0` → `x`
5. Simplifies `x * 1` → `x`

All through composable, declarative transformations.

---

## Summary

This article introduced traversals for recursive AST manipulation:

1. **Universal Traversals**: Visit all children of any expression variant
2. **Deep Traversal**: Bottom-up and top-down recursive descent
3. **Information Collection**: Extract data with folds and monoids
4. **Filtered Traversals**: Target specific node types with prisms
5. **Optimisation Passes**: Constant folding, identity simplification, dead branch elimination
6. **Composable Optimiser**: Multiple passes running to fixed point

The key insight: traversals separate *what* to visit from *what* to do. Define the traversal once; use it for any operation. This is the optics philosophy: composable, reusable abstractions for structural manipulation.

### The Higher-Kinded-J Advantage for Tree Operations

What makes Higher-Kinded-J particularly powerful for AST manipulation is how it elevates tree traversal from ad-hoc recursion to principled, composable abstractions. Consider what we achieved in this article:

1. **Effect-polymorphic traversals**: The `children()` traversal's `modifyF` signature works with any `Applicative`. This means the same traversal definition handles pure transformations, error accumulation, and stateful operations without modification. Higher-Kinded-J's witness types make this polymorphism type-safe.

2. **Compositional by design**: We composed prisms (for variant matching) with traversals (for tree descent) seamlessly. `ExprPrisms.binary()` focuses on Binary nodes; compose it with our traversal and you can target all Binary nodes in the entire tree. This compositional power comes directly from Higher-Kinded-J's optics implementation.

3. **Separation of concerns**: The traversal infrastructure (how to walk the tree) is completely separate from the operations (what to do at each node). This separation means adding a new AST variant requires updating only the traversal, not every operation that uses it.

4. **Reusable building blocks**: Our `transformBottomUp`, `transformTopDown`, and `foldMap` utilities work for any expression tree operation. These aren't special-cased functions; they're generic combinators built on Higher-Kinded-J's traversal foundations.

The elegance here is that Higher-Kinded-J lets us write code that describes *what* we want to do (visit all nodes, collect variables, fold constants) rather than *how* to do it (manual recursion, visitor boilerplate). The library handles the structural plumbing, letting us focus on the domain logic.

---

## What's Next

We've built a powerful foundation for tree manipulation. Our traversals can visit every node, our folds can aggregate information, and our optimisation passes compose cleanly.

But we've been working with pure transformations. Real compilers need effects:

- **Type checking** should accumulate errors, not fail on the first one
- **Interpretation** needs to track variable bindings (state)
- **Optimisation** might need logging for debugging

In Article 5, we'll explore effect-polymorphic optics with `modifyF`. We'll build:

- A type checker using `Validated` to collect all errors
- An interpreter using `State` to manage environments
- A Free monad DSL for composable, interpretable transformations

Higher-Kinded-J's true power emerges here. The `modifyF` method we've seen on our traversals accepts any `Applicative` (or `Monad` for operations requiring sequencing). This means the same traversal code we wrote in this article will work unchanged with `Validated` for error accumulation, `State` for environment threading, or `IO` for side effects. Higher-Kinded-J provides the type-class instances and witness types that make this polymorphism possible in Java.

We'll see how Higher-Kinded-J's `Validated` type differs from `Either` (accumulating all errors rather than short-circuiting), how `State` threads environment bindings through interpretation, and how these effects compose with our existing optics. The expression language will gain a full type system and interpreter, all built on the same traversal foundations.

---

## Further Reading

### Tree Traversals and Recursion Schemes

- **Patrick Thomson, ["An Introduction to Recursion Schemes"](https://blog.sumtypeofway.com/posts/introduction-to-recursion-schemes.html)**: A gentle introduction to the theory behind generic tree traversal patterns like catamorphisms and anamorphisms.

- **Jeremy Gibbons & Bruno Oliveira, ["The Essence of the Iterator Pattern"](https://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf)** (JFP, 2009): The seminal paper showing how `Applicative` (which Higher-Kinded-J provides) enables effect-polymorphic traversals.

### Compiler Optimisation

- **Andrew Appel, *Modern Compiler Implementation in ML***: Classic text covering constant folding, dead code elimination, and the other optimisations we've implemented. Available in Java and C editions.

- **Keith Cooper & Linda Torczon, *Engineering a Compiler*** (2nd ed.): Comprehensive coverage of program analysis and transformation passes.

### Term Rewriting

- **Franz Baader & Tobias Nipkow, *Term Rewriting and All That***: For readers interested in the theoretical foundations of pattern-based rewriting systems.

- The optimisation passes in this article are simple term rewrites. Industrial strength rewriting (like in GHC's rule system) adds phases, termination proofs, and confluent rule ordering.

### Higher-Kinded-J

- **[Traversal Documentation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/optics/Traversal.java)**: API reference for working with multi-focus optics.

- **[Traversals Utility Class](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/optics/util/Traversals.java)**: Helper methods for `getAll`, `modify`, and fold operations.

---

*Next: [Article 5: Effect-Polymorphic Optics](article-5-effect-polymorphic-optics.md)*

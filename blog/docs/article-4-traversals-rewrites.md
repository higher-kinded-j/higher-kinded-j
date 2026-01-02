# The Focus DSL: Traversals and Pattern Rewrites

*Part 4 of the Functional Optics for Modern Java series*

In Article 3, we built our expression language AST and applied basic optics (lenses for field access and prisms for variant matching). We even created a simple optimiser. But there's a fundamental limitation we need to address: how do we visit *all* nodes in a tree, not just the top level?

This is where traversals become essential. A traversal focuses on zero or more elements within a structure, making it perfect for recursive tree operations.

The [Focus DSL](https://higher-kinded-j.github.io/latest/optics/ch4_intro.html) provides `TraversalPath`: a fluent wrapper around traversals that makes collection navigation elegant and composable. By the end of this article, you'll be writing code like:

```java
// Navigate all employees in all departments, modify their salaries
Company updated = CompanyFocus.departments()
    .each()                              // Traverse each department
    .employees()                         // Navigate to employees list
    .each()                              // Traverse each employee
    .salary()                            // Focus on salary field
    .modifyAll(s -> s.multiply(1.1), company);
```

No manual recursion. No reconstruction boilerplate. Just a fluent path that describes what you want.

---

## Traversals as Garden Pruning

Before diving into code, consider an analogy. Imagine you're pruning a tree in a garden. You need to visit every branch, decide what to do at each one, and rebuild the tree with your changes. You have two strategies:

- **Bottom-up (leaves first)**: Start at the leaf tips, prune outward branches, then work your way down. Each branch is trimmed after its sub-branches are done. This is perfect when you need to see the final state of sub-branches before deciding on the parent.

- **Top-down (trunk first)**: Start at the trunk, make decisions about major branches first, then work outward. Each branch is examined before its sub-branches. This works when you want to make early decisions that affect the whole subtree.

In programming, a *traversal* is exactly this: a systematic way to visit every node in a tree structure, with a strategy for when to act on each node relative to its children. The Focus DSL makes this as natural as describing which branches to visit.

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

Visualised as a tree:

```
                    Binary(*)
                   /         \
            Binary(+)        Binary(+)
           /        \       /        \
     Variable(x)  Literal(1)  Variable(y)  Literal(2)
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

### The Focus DSL Alternative: TraversalPath

While the raw `Traversal` interface provides maximum control, the Focus DSL offers a more ergonomic API for everyday use. `TraversalPath` wraps a traversal and provides fluent methods:

```java
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.focus.FocusPaths;

// Create a TraversalPath from our children traversal
TraversalPath<Expr, Expr> childrenPath = TraversalPath.fromTraversal(children());

// Now we have fluent operations:
List<Expr> allChildren = childrenPath.getAll(expr);           // Collect all children
Expr modified = childrenPath.modifyAll(this::optimise, expr); // Transform all children
```

The Focus DSL becomes even more powerful with collection navigation. For lists, use `each()`:

```java
// Given a department with a list of employees
FocusPath<Department, List<Employee>> staffPath = DepartmentFocus.staff();

// Navigate into the list with each()
TraversalPath<Department, Employee> allStaff = staffPath.each();

// Now we can operate on all employees:
List<Employee> employees = allStaff.getAll(department);
Department updated = allStaff.modifyAll(Employee::promote, department);
```

The `each()` method is the key: it transforms a `FocusPath<S, List<A>>` into a `TraversalPath<S, A>`. This pattern scales to nested structures, as we'll see throughout this article.

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

Here's the traversal order visualised:

```
Bottom-up (leaves → root):              Top-down (root → leaves):

        Binary(*)  ⑦                            Binary(*)  ①
       /         \                             /         \
   Binary(+) ④   Binary(+) ⑥              Binary(+) ②   Binary(+) ⑤
   /       \     /        \               /       \     /        \
 x ①    1 ②    y ③     2 ⑤             x ③    1 ④    y ⑥     2 ⑦
```

For constant folding, bottom-up is essential: we need to evaluate `1 + 2` at the leaves before we can recognise that the parent is now `3 * 5`.

### Focus DSL: The Practical Choice

For most use cases, the Focus DSL provides what you need without writing custom traversals. Here's the pattern for nested structures:

```java
@GenerateFocus(generateNavigators = true)
record Company(String name, List<Department> departments) {}

@GenerateFocus(generateNavigators = true)
record Department(String name, List<Employee> employees, Employee manager) {}

@GenerateFocus(generateNavigators = true)
record Employee(String name, BigDecimal salary, Address address) {}

// Navigate all the way to employee addresses:
TraversalPath<Company, Address> allEmployeeAddresses = CompanyFocus
    .departments().each()          // Into each department
    .employees().each()            // Into each employee
    .address();                    // To their address

// Get all addresses
List<Address> addresses = allEmployeeAddresses.getAll(company);

// Relocate all employees
Company relocated = allEmployeeAddresses.modifyAll(
    addr -> new Address("New HQ", addr.city(), addr.postcode()),
    company
);
```

With navigators enabled, cross-type navigation happens automatically. No explicit `via()` calls, no manual composition. The path reads like English: "company's departments, each one's employees, each one's address."

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

With the Focus DSL, we can make this more elegant:

```java
public static Set<String> findVariables(Expr expr) {
    // Create a TraversalPath that focuses on all Variable nodes
    TraversalPath<Expr, Expr> allNodes = ExprFocus.universe();  // All descendants

    // Filter to variables and extract names
    return allNodes.getAll(expr).stream()
        .filter(e -> e instanceof Variable)
        .map(e -> ((Variable) e).name())
        .collect(Collectors.toSet());
}
```

Or using `foldMap` on the TraversalPath for a more functional approach:

```java
public static Set<String> findVariables(Expr expr) {
    TraversalPath<Expr, Expr> allNodes = ExprFocus.universe();
    return allNodes.foldMap(
        Monoids.set(),
        e -> e instanceof Variable(var name) ? Set.of(name) : Set.of(),
        expr
    );
}
```

The `foldMap` method on `TraversalPath` traverses all focused elements and combines results using a monoid (here, set union).

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

## Filtered Traversals and Conditional Updates

Sometimes we only want to focus on certain elements. The Focus DSL provides `modifyWhen()` for conditional updates and filtering capabilities for targeted navigation.

### Conditional Modification with modifyWhen

The `modifyWhen` method is perfect for targeted updates:

```java
// Give a raise only to employees earning below a threshold
TraversalPath<Company, Employee> allEmployees = CompanyFocus
    .departments().each()
    .employees().each();

Company updated = allEmployees.modifyWhen(
    emp -> emp.salary().compareTo(threshold) < 0,  // Predicate
    emp -> emp.withSalary(emp.salary().multiply(1.15)),  // Transformation
    company
);
```

This is cleaner than filtering after the fact: the predicate and transformation are co-located, making the intent clear.

### Targeting Only Binary Additions

For our expression language, we might want to target specific node types:

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

The Focus DSL integrates beautifully with prisms for sum type navigation:

```java
public static List<BinaryOp> findAllOperators(Expr expr) {
    // Navigate to all Binary nodes using instanceOf
    TraversalPath<Expr, Binary> allBinaries = ExprFocus.universe()
        .via(AffinePath.instanceOf(Binary.class));

    return allBinaries.getAll(expr).stream()
        .map(Binary::op)
        .collect(Collectors.toList());
}
```

The `AffinePath.instanceOf()` creates an affine path that focuses on elements of a specific type. When composed with a traversal, it filters to only matching elements.

### Sum Type Navigation with instanceOf

For sealed interfaces, `instanceOf` is particularly powerful:

```java
// Focus on all Circle shapes in a drawing
TraversalPath<Drawing, Circle> circles = DrawingFocus.shapes()
    .each()
    .via(AffinePath.instanceOf(Circle.class));

// Double all circle radii
Drawing updated = circles.modifyAll(
    c -> new Circle(c.radius() * 2),
    drawing
);
```

This pattern combines the type safety of sealed interfaces with the composability of the Focus DSL. The compiler ensures exhaustiveness while the DSL provides elegant navigation.

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

The fixed-point iteration ensures we catch cascading simplifications:

```
┌─────────────────────────────────────────────────────────────┐
│                    Optimisation Pipeline                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Input ──▶ [Constant Fold] ──▶ [Simplify] ──▶ [Dead Branch] │
│              │                                     │         │
│              └─────────────◀── Loop ◀─────────────┘         │
│                           (until fixed point)                │
│                                  │                           │
│                                  ▼                           │
│                               Output                         │
└─────────────────────────────────────────────────────────────┘
```

For example:

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

## Bridging to the [Effect Path API](https://higher-kinded-j.github.io/latest/effect/ch_intro.html)

The TraversalPath we've built throughout this article navigates and transforms data structures. But what happens when transformations might fail, or when we need to accumulate errors from multiple elements?

Higher-Kinded-J's **Effect Path API** provides the answer. Effect Paths wrap computations in contexts like `Maybe` (optional), `Either` (fail-fast errors), or `Validated` (accumulated errors). The Focus DSL bridges directly to these effect types.

### From Focus Paths to Effect Paths

Every Focus path type provides bridge methods to enter the effect world:

```java
// FocusPath<S, A> bridges to effects
FocusPath<Employee, String> namePath = EmployeeFocus.name();

MaybePath<String> maybeName = namePath.toMaybePath(employee);
EitherPath<Error, String> eitherName = namePath.toEitherPath(employee);
```

For AffinePath (which might not find a value), the bridge handles the missing case:

```java
// AffinePath<S, A> handles optionality
AffinePath<User, String> nicknamePath = UserFocus.nickname();  // Optional field

MaybePath<String> maybeNickname = nicknamePath.toMaybePath(user);
// Returns Maybe.just(name) or Maybe.nothing()

EitherPath<String, String> eitherNickname =
    nicknamePath.toEitherPath(user, "No nickname set");
// Returns Either.right(name) or Either.left("No nickname set")
```


### What's Ahead: The Effect Path API

Beyond optics and the Focus DSL, Higher-Kinded-J provides the **Effect Path API**: a fluent interface for computations that might fail, accumulate errors, or require deferred execution. The Effect Path types follow the "railway" metaphor where values travel along success or failure tracks:

```
Success Track:  ----[value]----> [transform] ----> [result]
                        \            |
Failure Track:           `--------->[error]------> [accumulated errors]
```

The core Effect Path types include:

| Effect Path | Wraps | Use Case |
|-------------|-------|----------|
| `MaybePath<A>` | `Maybe` | Optional values (might be absent) |
| `EitherPath<E, A>` | `Either` | Fail-fast error handling |
| `ValidationPath<E, A>` | `Validated` | Error accumulation |
| `TryPath<A>` | `Try` | Exception handling |
| `IOPath<A>` | `IO` | Deferred side effects |

**Understanding the underlying effect types:**

- **[Maybe](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/maybe/Maybe.java)**: Represents a value that might be absent, similar to `Optional` but with richer composition. Use when a value simply might not exist.

- **[Either](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/either/Either.java)**: Represents a value that is either a `Left` (typically an error) or a `Right` (the success value). Fails fast on the first error encountered.

- **[Validated](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/validated/Validated.java)**: Like `Either`, but designed for error *accumulation*. When combining multiple validations, collects all errors rather than stopping at the first.

- **[Try](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/trymonad/Try.java)**: Captures computations that might throw exceptions. Converts exception-throwing code into values you can compose safely.

- **[IO](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/io/IO.java)**: Represents a deferred side effect. The computation is described but not executed until explicitly run, enabling pure functional composition of effectful operations.

These types integrate seamlessly with Focus paths via bridge methods:

```java
// Navigate to a field, then enter the Effect Path world
FocusPath<User, String> emailPath = UserFocus.email();
```

### Effect Paths with Traversals

TraversalPath works with Effect Paths through `modifyF`. This is the bridge to effect-polymorphic operations:

```java
// Validate all employees in a company
TraversalPath<Company, Employee> allEmployees = CompanyFocus
    .departments().each()
    .employees().each();

// Bridge to ValidationPath for error accumulation
ValidationPath<List<Error>, Company> validated = Path.valid(company, Semigroups.list())
    .via(c -> {
        // Use modifyF with Validated applicative for error accumulation
        Kind<ValidatedKind.Witness<List<Error>>, Company> result = allEmployees.modifyF(
            emp -> validateEmployee(emp),
            c,
            validatedApplicative
        );
        return VALIDATED.narrow(result);
    });
```

### The Railway Model

Effect Paths follow the "railway" pattern where values travel along success or failure tracks:

```
Input: Company with employees to validate
         |
         v
   +--[Employee 1]---> [validate] ---> Valid ----+
   |                                              |
   +--[Employee 2]---> [validate] ---> Invalid --+---> Accumulate
   |                                              |
   +--[Employee 3]---> [validate] ---> Valid ----+
         |
         v
Output: ValidationPath<List<Error>, Company>
        (all errors collected, or valid company)
```

This pattern enables comprehensive validation rather than fail-fast behaviour. The `ValidationPath` accumulates all errors using a `Semigroup`, so users see every problem at once.

### Preview: Effect-Polymorphic Traversals

In Article 5, we'll explore the Effect Path API in depth. The key insight is that the same Focus paths work with different effect types:

```java
// Same path, different effects
TraversalPath<Expr, Expr> allChildren = ExprFocus.children();

// Pure transformation
Expr optimised = allChildren.modifyAll(this::optimise, expr);

// With Maybe (might fail)
MaybePath<Expr> maybeResult = Path.just(expr)
    .via(e -> allChildren.modifyF(this::tryOptimise, e, maybeApplicative));

// With Validated (accumulate errors)
ValidationPath<List<Error>, Expr> validated = Path.valid(expr, Semigroups.list())
    .via(e -> allChildren.modifyF(this::validateNode, e, validatedApplicative));
```

The Effect Path API turns the theoretical power of `modifyF` into a practical, fluent interface.

---

## Summary

This article introduced traversals and the Focus DSL's `TraversalPath` for recursive manipulation:

1. **TraversalPath**: Fluent wrapper for traversals with `getAll()`, `modifyAll()`, `foldMap()`
2. **Collection Navigation**: `each()` for lists, `at()` for indices, `atKey()` for maps
3. **Conditional Updates**: `modifyWhen()` for predicate-based transformations
4. **Sum Type Navigation**: `AffinePath.instanceOf()` for type-safe variant targeting
5. **Deep Traversal**: Bottom-up and top-down recursive descent
6. **Composable Optimiser**: Multiple passes running to fixed point

The key insight: the Focus DSL separates *what* to visit from *what* to do. Build paths declaratively, then apply operations fluently. This is the optics philosophy made accessible.

### The Higher-Kinded-J Advantage for Tree Operations

The Focus DSL transforms what could be tedious tree manipulation into expressive, readable code. Consider what we achieved:

1. **Fluent navigation with TraversalPath**: Instead of writing custom traversals, we chain methods: `.departments().each().employees().each().salary()`. The path describes the data shape, not the mechanics of traversal.

2. **Collection navigation built-in**: Methods like `each()`, `at()`, and `atKey()` handle the common cases. No need to write `Traversals.list()` and compose it manually.

3. **Conditional updates with modifyWhen**: Target specific elements with predicates directly on the path. The intent is clear and the implementation is correct by construction.

4. **Sum type integration**: `AffinePath.instanceOf()` bridges sealed interfaces with the Focus DSL. Navigate to specific variants type-safely.

5. **Effect polymorphism underneath**: The Focus DSL wraps Higher-Kinded-J's effect-polymorphic traversals. When you need `modifyF` for validation or state, the same paths work.

The Focus DSL embodies the principle: common things should be easy, powerful things should be possible. Most operations use the fluent API; when you need maximum control, the underlying traversals are always accessible.

---

## What's Next

We've built a powerful foundation for tree manipulation with the Focus DSL. Our `TraversalPath` chains can visit every node, `foldMap` can aggregate information, and optimisation passes compose cleanly.

But we've been working with pure transformations. Real compilers need effects:

- **Type checking** should accumulate errors, not fail on the first one
- **Interpretation** needs to track variable bindings (state)
- **Optimisation** might need logging for debugging

In Article 5, we'll explore effect-polymorphic optics with `modifyF`. The Focus DSL integrates seamlessly:

```java
// Preview: effectful modification through Focus paths
Kind<ValidatedKind.Witness<List<TypeError>>, Employee> result =
    employeePath.modifyF(
        emp -> validateEmployee(emp),  // Returns Validated
        employee,
        validatedApplicative
    );
```

The same Focus paths we've built work with `Validated` for error accumulation, `State` for environment threading, and any other effect. Higher-Kinded-J provides the type-class instances that make this polymorphism possible.

We'll see how `Validated` differs from `Either` (accumulating all errors rather than short-circuiting), how `State` threads environment bindings through interpretation, and how these effects compose with Focus paths. The expression language will gain a full type system and interpreter, all built on the same Focus DSL foundations.

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

### Algebraic Data Types in Java

- **["Algebraic Data Types with Java"](https://blog.scottlogic.com/2025/01/20/algebraic-data-types-with-java.html)** (Scott Logic, 2025): A thorough introduction to algebraic data types using Java's sealed interfaces and records. Covers how sum types (like our expression AST) and product types compose to model complex domains.

### Higher-Kinded-J

- **[Traversal Documentation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/optics/Traversal.java)**: API reference for working with multi-focus optics.

- **[Traversals Utility Class](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/optics/util/Traversals.java)**: Helper methods for `getAll`, `modify`, and fold operations.

- **[Focus DSL Guide](https://higher-kinded-j.github.io/latest/optics/ch4_intro.html)**: Fluent navigation with FocusPath, AffinePath, and TraversalPath.

- **[Effect Path API Guide](https://higher-kinded-j.github.io/latest/effect/ch_intro.html)**: Railway-style error handling with MaybePath, EitherPath, and ValidationPath.

---

*Next: [Article 5: The Effect Path API: Railway-Style Error Handling](article-5-effect-polymorphic-optics.md)*

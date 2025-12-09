# Tutorial Solutions Guide

~~~admonish info title="What You'll Learn"
- When to consult solutions versus working through problems independently
- How to learn effectively from solutions without just copy-pasting code
- Common patterns used throughout solution files (widen-operate-narrow, typeclass instances, optic composition)
- Debugging techniques for compilation and runtime errors
- How to experiment with variations and connect solutions to documentation
~~~

## Philosophy: When to Use Solutions

The solution files exist to help you learn, not to short-circuit the learning process. Here's how to use them effectively.

### ✅ Good Reasons to Check Solutions

1. **After Multiple Genuine Attempts**: You've tried for 10+ minutes and exhausted your ideas
2. **To Verify Your Approach**: You have a working solution but want to compare approaches
3. **To Learn Idioms**: You want to see the "idiomatic" way to use the library
4. **When Completely Stuck**: You're blocked on a fundamental concept and can't progress

### ❌ Poor Reasons to Check Solutions

1. **Immediately When Confused**: Give yourself time to think through the problem
2. **To Save Time**: The struggle is where learning happens; shortcuts lead to shallow understanding
3. **Copy-Pasting for Green Tests**: You'll pass the tutorial but won't retain the knowledge
4. **Because It's Available**: Resist the temptation!

> **Rule of Thumb**: If you haven't spent at least 5 minutes thinking about the problem, you're not ready for the solution.

## How to Learn from Solutions

When you do consult a solution, approach it systematically:

### 1. Don't Just Copy-Paste

**Instead**:
- Read the solution carefully
- Understand *why* it works
- Close the solution file
- Re-implement it yourself from memory
- Run the test to verify understanding

### 2. Compare Approaches

If you have a working solution that differs from the provided one:
- Are they functionally equivalent?
- Is one more idiomatic?
- Is one more efficient?
- What trade-offs exist between them?

**Example**:
```java
// Your solution (verbose but clear)
Either<String, Integer> result = value1.flatMap(a ->
    value2.map(b -> a + b)
);

// Provided solution (using map2 - more idiomatic for Applicative)
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(EITHER.widen(value1), EITHER.widen(value2), (a, b) -> a + b)
);
```

Both are correct, but the second uses the Applicative abstraction more idiomatically.

### 3. Identify Patterns

Solutions often reveal reusable patterns:

**Pattern: Typeclass Access**
```java
// Pattern you'll see repeatedly
SomeMonad<ErrorType> monad = SomeMonad.instance();
ConcreteType<ErrorType, ValueType> result = HELPER.narrow(
    monad.operationName(HELPER.widen(input), ...)
);
```

**Pattern: Optic Composition**
```java
// Pattern: Build paths from small pieces
var outerToInner = OuterLenses.middle()
    .andThen(MiddleLenses.inner())
    .andThen(InnerLenses.field());
```

### 4. Annotate Solutions

When studying a solution, add your own comments explaining what each part does:

```java
// Create the Applicative instance for Either with String errors
EitherMonad<String> applicative = EitherMonad.instance();

// Widen both Either values to Kind for generic processing
// Combine them using map2 (because they're independent)
// Narrow the result back to concrete Either type
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(
        EITHER.widen(value1),  // First independent value
        EITHER.widen(value2),  // Second independent value
        (a, b) -> a + b        // Combining function
    )
);
```

## Understanding Common Solution Patterns

### Pattern 1: Widen → Operate → Narrow

**Why**: Generic operations work on `Kind<F, A>`, not concrete types.

```java
// 1. Start with concrete type
Either<String, Integer> either = Either.right(42);

// 2. Widen to Kind for generic operation
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);

// 3. Perform generic operation (e.g., Functor.map)
Kind<EitherKind.Witness<String>, String> mapped = functor.map(Object::toString, kind);

// 4. Narrow back to concrete type
Either<String, String> result = EITHER.narrow(mapped);
```

**When you see this**: Core Types tutorials use this pattern extensively.

### Pattern 2: Typeclass Instance Retrieval

**Why**: Typeclasses provide the implementation for generic operations.

```java
// Get the Monad instance for Either with String errors
EitherMonad<String> monad = EitherMonad.instance();

// Use it to perform monadic operations
monad.flatMap(...);
```

**When you see this**: Tutorials 02-05 in Core Types track.

### Pattern 3: Optic Composition Chains

**Why**: Small, focused optics compose into powerful transformations.

```java
// Build a path through nested structures
var leagueToPlayerScores = LeagueTraversals.teams()      // League → Teams
    .andThen(TeamTraversals.players())                    // Team → Players
    .andThen(PlayerLenses.score().asTraversal());        // Player → Score
```

**When you see this**: Optics tutorials 02, 04, 05, 07.

### Pattern 4: Manual Lens Creation

**Why**: Annotation processor can't generate lenses for local classes.

```java
class ProductLenses {
    public static Lens<Product, String> name() {
        return Lens.of(
            Product::name,                                           // Getter
            (product, newName) -> new Product(                       // Setter
                product.id(),
                newName,
                product.price()
            )
        );
    }
}
```

**When you see this**: Optics tutorials with local record definitions.

### Pattern 5: Traversal Creation

**Why**: Custom containers need custom traversals.

```java
public static Traversal<Order, LineItem> items() {
    return new Traversal<>() {
        @Override
        public <F> Kind<F, Order> modifyF(
            Function<LineItem, Kind<F, LineItem>> f,
            Order order,
            Applicative<F> applicative
        ) {
            // Traverse items list, applying f to each element
            Kind<F, List<LineItem>> updatedItems =
                ListTraverse.instance().traverse(applicative, f, order.items());

            // Map the result back to Order
            return applicative.map(
                newItems -> new Order(order.id(), newItems, order.status()),
                updatedItems
            );
        }
    };
}
```

**When you see this**: Optics Tutorial 07.

## Debugging Your Solutions

### Common Compilation Errors

#### Error: "cannot find symbol: method answerRequired()"
**Cause**: You haven't imported or defined the helper method.

**Fix**: Ensure this exists at the top of the file:
```java
private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
}
```

#### Error: "incompatible types: ... cannot be converted to Kind<...>"
**Cause**: Forgot to widen before passing to generic code.

**Fix**: Wrap with the appropriate helper:
```java
EITHER.widen(eitherValue)
MAYBE.widen(maybeValue)
LIST.widen(listValue)
```

#### Error: "cannot find symbol: variable SomeLenses"
**Cause**: Annotation processor hasn't run or class isn't eligible for generation.

**Fix**:
1. Rebuild project: `./gradlew clean build`
2. Check annotation is on top-level or static class (not local class)
3. Verify `@GenerateLenses` import is correct

#### Error: "method mapN in interface Applicative<F> cannot be applied"
**Cause**: Wrong number of arguments or incorrect type parameters.

**Fix**: Check you're using the right `map2`/`map3`/`map4`/`map5` for the number of values you're combining.

### Common Runtime Errors

#### Error: "Answer required" exception
**Cause**: You haven't replaced the placeholder with a solution.

**Fix**: This is expected! Replace `answerRequired()` with working code.

#### Error: "KindUnwrapException"
**Cause**: Trying to narrow a `Kind<F, A>` that wasn't created from the expected type.

**Fix**: Ensure the witness type matches. `EITHER.narrow()` only works on `Kind<EitherKind.Witness<L>, R>`.

#### Error: NullPointerException in Free Monad validation
**Cause**: Validation interpreter returns `null` for `get` operations.

**Fix**: Add null checks:
```java
.flatMap(value -> {
    if (value != null && value.equals("expected")) {
        // ...
    } else {
        // ...
    }
})
```

## Solution File Organisation

Solutions are organised to mirror the tutorial structure:

```
hkj-examples/src/test/java/org/higherkindedj/tutorial/solutions/
├── coretypes/
│   ├── Tutorial01_KindBasics_Solution.java
│   ├── Tutorial02_FunctorMapping_Solution.java
│   ├── Tutorial03_ApplicativeCombining_Solution.java
│   ├── Tutorial04_MonadChaining_Solution.java
│   ├── Tutorial05_MonadErrorHandling_Solution.java
│   ├── Tutorial06_ConcreteTypes_Solution.java
│   └── Tutorial07_RealWorld_Solution.java
└── optics/
    ├── Tutorial01_LensBasics_Solution.java
    ├── Tutorial02_LensComposition_Solution.java
    ├── Tutorial03_PrismBasics_Solution.java
    ├── Tutorial04_TraversalBasics_Solution.java
    ├── Tutorial05_OpticsComposition_Solution.java
    ├── Tutorial06_GeneratedOptics_Solution.java
    ├── Tutorial07_RealWorldOptics_Solution.java
    ├── Tutorial08_FluentOpticsAPI_Solution.java
    └── Tutorial09_AdvancedOpticsDSL_Solution.java
```

Each solution file:
- Contains complete, working implementations for all exercises
- Includes explanatory comments
- Demonstrates idiomatic usage patterns
- Compiles and passes all tests

## Learning Beyond Solutions

### Experiment with Variations

Once you understand a solution, try variations:

**Original solution**:
```java
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(EITHER.widen(value1), EITHER.widen(value2), (a, b) -> a + b)
);
```

**Variations to try**:
1. What if one value is `Left`? (Test the error path)
2. Can you use `flatMap` instead? (Understand the difference)
3. How would this work with `Validated` instead of `Either`? (See error accumulation)

### Connect to Documentation

Each solution references specific documentation sections. Follow these links to deepen understanding:

- If a solution uses `Functor.map` → Read the [Functor Guide](../functional/functor.md)
- If a solution composes optics → Read [Composing Optics](../optics/composing_optics.md)
- If a solution uses a specific monad → Read its dedicated guide

### Build Your Own Exercises

After mastering the tutorials, create your own scenarios:

1. Define a domain model from your work
2. Write tests that require optics or monads
3. Solve them using the patterns you've learned
4. Compare your solutions to the tutorial patterns

## When Solutions Don't Help

If you've read the solution but still don't understand:

1. **Go back to fundamentals**: Re-read the introduction to that tutorial
2. **Check prerequisites**: Maybe you need to understand an earlier concept first
3. **Read the documentation**: Solutions show *how*, documentation explains *why*
4. **Ask for help**: Use [GitHub Discussions](https://github.com/higher-kinded-j/higher-kinded-j/discussions)

## Remember

> The solution is not the destination; understanding is.

A copied solution gets you a green test today but leaves you unprepared for tomorrow's challenges. The effort you put into solving exercises yourself directly translates to your ability to apply these patterns in production code.

Take your time. Struggle productively. Learn deeply.

---

**Previous:** [Optics Track](optics_track.md)
**Next:** [Troubleshooting](troubleshooting.md)

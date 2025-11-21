# Alternative

The `Alternative` type class represents applicative functors that support choice and failure. It extends the `Applicative` interface with operations for combining alternatives and representing empty/failed computations. Alternative sits at the same level as `Applicative` in the type class hierarchy, providing a more general abstraction than `MonadZero`.

The interface for Alternative in hkj-api extends Applicative:

```java
public interface Alternative<F> extends Applicative<F> {
    <A> Kind<F, A> empty();
    <A> Kind<F, A> orElse(Kind<F, A> fa, Supplier<Kind<F, A>> fb);
    default Kind<F, Unit> guard(boolean condition);
}
```

### Why is it useful?

An `Applicative` provides a way to apply functions within a context and combine multiple values. An `Alternative` adds two critical operations to this structure:

* `empty()`: Returns the "empty" or "failure" element for the applicative functor.
* `orElse(fa, fb)`: Combines two alternatives, preferring the first if it succeeds, otherwise evaluating and returning the second.

These operations enable:
* **Choice and fallback mechanisms**: Try one computation, if it fails, try another
* **Non-deterministic computation**: Represent multiple possible results (e.g., List concatenation)
* **Parser combinators**: Essential for building flexible parsers that try alternatives
* **Conditional effects**: Using the `guard()` helper for filtering

### Relationship with MonadZero

In higher-kinded-j, `MonadZero` extends both `Monad` and `Alternative`:

```java
public interface MonadZero<F> extends Monad<F>, Alternative<F> {
    <A> Kind<F, A> zero();

    @Override
    default <A> Kind<F, A> empty() {
        return zero();
    }
}
```

This means:
* Every `MonadZero` is also an `Alternative`
* The `zero()` method provides the implementation for `empty()`
* Types that are MonadZero (List, Maybe, Optional, Stream) automatically get Alternative operations

### Key Implementations in this Project

For different types, Alternative has different semantics:

* **Maybe**: `empty()` returns `Nothing`. `orElse()` returns the first `Just`, or the second if the first is `Nothing`.
* **Optional**: `empty()` returns `Optional.empty()`. `orElse()` returns the first present value, or the second if the first is empty.
* **List**: `empty()` returns an empty list `[]`. `orElse()` concatenates both lists (non-deterministic choice).
* **Stream**: `empty()` returns an empty stream. `orElse()` concatenates both streams lazily.

### Primary Uses

#### 1. Fallback Chains with Maybe/Optional

Try multiple sources, using the first successful one:

```java
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe.Maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

// Get the Alternative instance for Maybe
final Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;

// Simulate trying multiple configuration sources
Kind<MaybeKind.Witness, String> fromEnv = MAYBE.nothing();      // Not found
Kind<MaybeKind.Witness, String> fromFile = MAYBE.just("config.txt");  // Found!
Kind<MaybeKind.Witness, String> fromDefault = MAYBE.just("default");

// Try sources in order
Kind<MaybeKind.Witness, String> config = alt.orElse(
    fromEnv,
    () -> alt.orElse(
        fromFile,
        () -> fromDefault
    )
);

Maybe<String> result = MAYBE.narrow(config);
System.out.println("Config: " + result.get()); // "config.txt"
```

**Using `orElseAll()` for cleaner syntax:**

```java
Kind<MaybeKind.Witness, String> config = alt.orElseAll(
    fromEnv,
    () -> fromFile,
    () -> fromDefault
);
```

#### 2. Non-Deterministic Computation with List

Represent all possible outcomes:

```java
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;

import java.util.Arrays;
import java.util.List;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

// Get the Alternative instance for List
final Alternative<ListKind.Witness> alt = ListMonad.INSTANCE;

// Possible actions
Kind<ListKind.Witness, String> actions1 = LIST.widen(Arrays.asList("move_left", "move_right"));
Kind<ListKind.Witness, String> actions2 = LIST.widen(Arrays.asList("jump", "duck"));

// Combine all possibilities
Kind<ListKind.Witness, String> allActions = alt.orElse(actions1, () -> actions2);

List<String> result = LIST.narrow(allActions);
System.out.println("All actions: " + result);
// Output: [move_left, move_right, jump, duck]
```

#### 3. Conditional Success with guard()

Filter based on conditions:

```java
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe.Maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

final Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;

// Check authentication
boolean isAuthenticated = true;
Kind<MaybeKind.Witness, Unit> authCheck = alt.guard(isAuthenticated);

Maybe<Unit> result = MAYBE.narrow(authCheck);
System.out.println("Authenticated: " + result.isJust()); // true

// guard(false) returns empty()
Kind<MaybeKind.Witness, Unit> failedCheck = alt.guard(false);
System.out.println("Failed: " + MAYBE.narrow(failedCheck).isNothing()); // true
```

#### 4. Lazy Evaluation

The second argument to `orElse()` is provided via `Supplier`, enabling lazy evaluation:

```java
final Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;

Kind<MaybeKind.Witness, String> primary = MAYBE.just("found");

Kind<MaybeKind.Witness, String> result = alt.orElse(
    primary,
    () -> {
        System.out.println("Computing fallback...");
        return MAYBE.just("fallback");
    }
);

// "Computing fallback..." is never printed because primary succeeded
System.out.println("Result: " + MAYBE.narrow(result).get()); // "found"
```

For **Maybe** and **Optional**, the second alternative is only evaluated if the first is empty.

For **List** and **Stream**, both alternatives are always evaluated (to concatenate them), but the `Supplier` still provides control over when the second collection is created.

### Alternative Laws

Alternative instances must satisfy these laws:

1. **Left Identity**: `orElse(empty(), () -> fa) ≡ fa`
   - empty is the left identity for orElse

2. **Right Identity**: `orElse(fa, () -> empty()) ≡ fa`
   - empty is the right identity for orElse

3. **Associativity**: `orElse(fa, () -> orElse(fb, () -> fc)) ≡ orElse(orElse(fa, () -> fb), () -> fc)`
   - The order of combining alternatives doesn't matter

4. **Left Absorption**: `ap(empty(), fa) ≡ empty()`
   - Applying an empty function gives empty

5. **Right Absorption**: `ap(ff, empty()) ≡ empty()`
   - Applying any function to empty gives empty

### Practical Example: Configuration Loading

Here's a complete example showing how Alternative enables elegant fallback chains:

```java
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.maybe.Maybe;

import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

public class ConfigLoader {
    private final Alternative<MaybeKind.Witness> alt = MaybeMonad.INSTANCE;

    public Kind<MaybeKind.Witness, String> loadConfig(String key) {
        return alt.orElseAll(
            readFromEnvironment(key),
            () -> readFromConfigFile(key),
            () -> readFromDatabase(key),
            () -> getDefaultValue(key)
        );
    }

    private Kind<MaybeKind.Witness, String> readFromEnvironment(String key) {
        String value = System.getenv(key);
        return value != null ? MAYBE.just(value) : MAYBE.nothing();
    }

    private Kind<MaybeKind.Witness, String> readFromConfigFile(String key) {
        // Simulate file reading
        return MAYBE.nothing(); // Not found
    }

    private Kind<MaybeKind.Witness, String> readFromDatabase(String key) {
        // Simulate database query
        return MAYBE.just("db-value-" + key);
    }

    private Kind<MaybeKind.Witness, String> getDefaultValue(String key) {
        return MAYBE.just("default-" + key);
    }
}

// Usage
ConfigLoader loader = new ConfigLoader();
Kind<MaybeKind.Witness, String> config = loader.loadConfig("APP_NAME");
Maybe<String> result = MAYBE.narrow(config);
System.out.println("Config value: " + result.get()); // "db-value-APP_NAME"
```

### Comparison: Alternative vs MonadZero

| Aspect | Alternative | MonadZero |
|--------|-------------|-----------|
| Extends | Applicative | Monad (and Alternative) |
| Power Level | Less powerful | More powerful |
| Core Methods | `empty()`, `orElse()` | `zero()`, inherits `orElse()` |
| Use Case | Choice, fallback, alternatives | Filtering, monadic zero |
| Examples | Parser combinators, fallback chains | For-comprehension filtering |

In practice, since `MonadZero` extends `Alternative` in higher-kinded-j, types like List, Maybe, Optional, and Stream have access to both sets of operations.

### When to Use Alternative

Use Alternative when you need to:
* **Try multiple alternatives** with fallback behaviour
* **Combine all possibilities** (for List/Stream)
* **Conditionally proceed** based on boolean conditions (`guard()`)
* **Build parser combinators** or similar choice-based systems
* Work at the **Applicative level** without requiring full Monad power

Alternative provides a principled, composable way to handle choice and failure in functional programming.

## Complete Working Example

For a complete, runnable example demonstrating Alternative with configuration loading, see:

**[AlternativeConfigExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/alternative/AlternativeConfigExample.java)**

This example demonstrates:
- Basic `orElse()` fallback patterns
- `orElseAll()` for multiple fallback sources
- `guard()` for conditional validation
- Lazy evaluation benefits
- Parser combinator patterns using Alternative

# MonadZero

The `MonadZero` is a more advanced type class extends the `Monad` interface to include the concept of a "zero" or "empty" element. It is designed for monads that can represent failure, absence, or emptiness, allowing them to be used in filtering operations.

The interface for MonadZero in hkj-api extends Monad:

```java
public interface MonadZero<F> extends Monad<F> {
    <A> Kind<F, A> zero();
}
```

### Why is it useful?

A `Monad` provides a way to sequence computations within a context (`flatMap`, `map`, `of`). A `MonadZero` adds one critical operation to this structure:

* `zero()`: Returns the "empty" or "zero" element for the monad.

This `zero` element acts as an absorbing element in a monadic sequence, similar to how multiplying by zero results in zero. If a computation results in a `zero`, subsequent operations in the chain are typically skipped.

`MonadZero` is particularly useful for making for-comprehensions more powerful. When you are working with a monad that has a `MonadZero` instance, you can use a `when()` clause to filter results within the comprehension.

**Key Implementations in this Project:**

* For **List**, `zero()` returns an empty list `[]`.
* For **Maybe**, `zero()` returns `Nothing`.
* For **Optional**, `zero()` returns `Optional.empty()`.

### Primary Uses

The main purpose of `MonadZero` is to enable filtering within monadic comprehensions. It allows you to discard results that don't meet a certain criterion.

#### 1. Filtering in For-Comprehensions

As already mentioned the most powerful application in this codebase is within the **`For` comprehension builder**. The builder has two entry points:

* `For.from(monad, ...)`: For any standard `Monad`.
* `For.from(monadZero, ...)`: An overloaded version specifically for a `MonadZero`.

Only the version that accepts a `MonadZero` provides the `.when(predicate)` filtering step. When the predicate in a `.when()` clause evaluates to `false`, the builder internally calls `monad.zero()` to terminate that specific computational path.

#### 2. Generic Functions

It allows you to write generic functions that can operate over any monad that has a concept of "failure" or "emptiness," such as `List`, `Maybe`, or `Optional`.

### Code Example: `For` Comprehension with `ListMonad`

The following example demonstrates how `MonadZero` enables filtering.

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import java.util.Arrays;
import java.util.List;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

// 1. Get the MonadZero instance for List
final ListMonad listMonad = ListMonad.INSTANCE;

// 2. Define the initial data sources
final Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));
final Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(10, 20));

// 3. Build the comprehension using the filterable 'For'
final Kind<ListKind.Witness, String> result =
    For.from(listMonad, list1)                       // Start with a MonadZero
        .from(a -> list2)                            // Generator (flatMap)
        .when(t -> (t._1() + t._2()) % 2 != 0)        // Filter: if the sum is odd
        .let(t -> "Sum: " + (t._1() + t._2()))        // Binding (map)
        .yield((a, b, c) -> a + " + " + b + " = " + c); // Final projection

// 4. Unwrap the result
final List<String> narrow = LIST.narrow(result);
System.out.println("Result of List comprehension: " + narrow);
```


**Explanation:**

* The comprehension iterates through all pairs of `(a, b)` from `list1` and `list2`.
* The `.when(...)` clause checks if the sum `a + b` is odd.
* If the sum is even, the `monad.zero()` method (which returns an empty list) is invoked for that path, effectively discarding it.
* If the sum is odd, the computation continues to the `.let()` and `.yield()` steps.

**Output:**

```Result of List comprehension: [1 + 10 = Sum: 11, 1 + 20 = Sum: 21, 3 + 10 = Sum: 13, 3 + 20 = Sum: 23]```
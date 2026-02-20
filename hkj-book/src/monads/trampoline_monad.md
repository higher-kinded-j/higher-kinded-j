# The Trampoline Monad:
## _Stack-Safe Recursion in Java_

~~~admonish info title="What You'll Learn"
- How to convert deeply recursive algorithms to stack-safe iterative ones
- Implementing mutually recursive functions without stack overflow
- Using `Trampoline.done` and `Trampoline.defer` to build trampolined computations
- Composing recursive operations using `map` and `flatMap`
- When to use Trampoline vs. traditional recursion
- Leveraging `TrampolineUtils` for stack-safe applicative operations
~~~

~~~ admonish example title="See Example Code:"
[TrampolineExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trampoline/TrampolineExample.java)
~~~

In functional programming, recursion is a natural way to express iterative algorithms. However, the JVM's call stack has a limited depth, and deeply recursive computations can cause `StackOverflowError`. The JVM lacks tail-call optimisation, which means even tail-recursive functions will consume stack space.

The `Trampoline<A>` type in `higher-kinded-j` solves this problem by converting recursive calls into data structures that are evaluated iteratively. Instead of making recursive calls directly (which grow the call stack), you return a `Trampoline` value that describes the next step of the computation. The `run()` method then processes these steps in a loop, using constant stack space regardless of recursion depth.

## Core Components

**The Trampoline Structure**

![trampoline_structure.svg](../images/puml/trampoline_structure.svg)

**The HKT Bridge for Trampoline**

![trampoline_kind.svg](../images/puml/trampoline_kind.svg)

**Typeclasses for Trampoline**

![trampoline_monad.svg](../images/puml/trampoline_monad.svg)

The `Trampoline` functionality is built upon several related components:

1. **`Trampoline<A>`**: The core sealed interface representing a stack-safe computation. It has three constructors:
   * `Done<A>`: Represents a completed computation holding a final value.
   * `More<A>`: Represents a suspended computation (deferred thunk) that will be evaluated later.
   * `FlatMap<A, B>`: Represents a sequenced computation resulting from monadic bind operations.

2. **`TrampolineKind<A>`**: The HKT marker interface (`Kind<TrampolineKind.Witness, A>`) for `Trampoline`. This allows `Trampoline` to be treated as a generic type constructor `F` in type classes like `Functor` and `Monad`. The witness type is `TrampolineKind.Witness`.

3. **`TrampolineKindHelper`**: The essential utility class for working with `Trampoline` in the HKT simulation. It provides:
   * `widen(Trampoline<A>)`: Wraps a concrete `Trampoline<A>` instance into its HKT representation `TrampolineKind<A>`.
   * `narrow(Kind<TrampolineKind.Witness, A>)`: Unwraps a `TrampolineKind<A>` back to the concrete `Trampoline<A>`. Throws `KindUnwrapException` if the input Kind is invalid.
   * `done(A value)`: Creates a `TrampolineKind<A>` representing a completed computation.
   * `defer(Supplier<Trampoline<A>> next)`: Creates a `TrampolineKind<A>` representing a deferred computation.
   * `run(Kind<TrampolineKind.Witness, A>)`: Executes the trampoline and returns the final result.

4. **`TrampolineFunctor`**: Implements `Functor<TrampolineKind.Witness>`. Provides the `map` operation to transform the result value of a trampoline computation.

5. **`TrampolineMonad`**: Extends `TrampolineFunctor` and implements `Monad<TrampolineKind.Witness>`. Provides `of` (to lift a pure value into `Trampoline`) and `flatMap` (to sequence trampoline computations).

6. **`TrampolineUtils`**: Utility class providing guaranteed stack-safe applicative operations:
   * `traverseListStackSafe`: Stack-safe list traversal for any applicative.
   * `map2StackSafe`: Stack-safe map2 for chaining many operations.
   * `sequenceStackSafe`: Stack-safe sequence operation.

## Purpose and Usage

* **Stack Safety**: Converts recursive calls into data structures processed iteratively, preventing `StackOverflowError` on deep recursion (verified with 100,000+ iterations).
* **Tail Call Optimisation**: Effectively provides tail-call optimisation for Java, which lacks native support for it.
* **Lazy Evaluation**: Computations are not executed until `run()` is explicitly called.
* **Composability**: Trampolined computations can be chained using `map` and `flatMap`.

**Key Methods:**
- `Trampoline.done(value)`: Creates a completed computation with a final value.
- `Trampoline.defer(supplier)`: Defers a computation by wrapping it in a supplier.
- `trampoline.run()`: Executes the trampoline iteratively and returns the final result.
- `trampoline.map(f)`: Transforms the result without executing the trampoline.
- `trampoline.flatMap(f)`: Sequences trampolines whilst maintaining stack safety.

~~~admonish example title="Example 1: Stack-Safe Factorial"

The classic factorial function is a simple example of recursion. For large numbers, naive recursion will cause stack overflow:

```java
import org.higherkindedj.hkt.trampoline.Trampoline;
import java.math.BigInteger;

public class FactorialExample {
    // Naive recursive factorial - WILL OVERFLOW for large n
    static BigInteger factorialNaive(BigInteger n) {
        if (n.compareTo(BigInteger.ZERO) <= 0) {
            return BigInteger.ONE;
        }
        return n.multiply(factorialNaive(n.subtract(BigInteger.ONE)));
    }

    // Stack-safe trampolined factorial - safe for very large n
    static Trampoline<BigInteger> factorial(BigInteger n, BigInteger acc) {
        if (n.compareTo(BigInteger.ZERO) <= 0) {
            return Trampoline.done(acc);
        }
        // Instead of recursive call, return a deferred computation
        return Trampoline.defer(() ->
            factorial(n.subtract(BigInteger.ONE), n.multiply(acc))
        );
    }

    public static void main(String[] args) {
        // This would overflow: factorialNaive(BigInteger.valueOf(10000));

        // This is stack-safe
        BigInteger result = factorial(
            BigInteger.valueOf(10000),
            BigInteger.ONE
        ).run();

        System.out.println("Factorial computed safely!");
        System.out.println("Result has " + result.toString().length() + " digits");
    }
}
```
~~~

**Key Insight:** Instead of making a direct recursive call (which pushes a new frame onto the call stack), we return `Trampoline.defer(() -> ...)` which creates a data structure. The `run()` method then evaluates these structures iteratively.

~~~admonish example title="Example 2: Mutual Recursion - isEven/isOdd"

Mutually recursive functions are another classic case where stack overflow occurs easily:

```java
import org.higherkindedj.hkt.trampoline.Trampoline;

public class MutualRecursionExample {
    // Naive mutual recursion - WILL OVERFLOW for large n
    static boolean isEvenNaive(int n) {
        if (n == 0) return true;
        return isOddNaive(n - 1);
    }

    static boolean isOddNaive(int n) {
        if (n == 0) return false;
        return isEvenNaive(n - 1);
    }

    // Stack-safe trampolined versions
    static Trampoline<Boolean> isEven(int n) {
        if (n == 0) return Trampoline.done(true);
        return Trampoline.defer(() -> isOdd(n - 1));
    }

    static Trampoline<Boolean> isOdd(int n) {
        if (n == 0) return Trampoline.done(false);
        return Trampoline.defer(() -> isEven(n - 1));
    }

    public static void main(String[] args) {
        // This would overflow: isEvenNaive(1000000);

        // This is stack-safe
        boolean result = isEven(1000000).run();
        System.out.println("1000000 is even: " + result); // true

        boolean result2 = isOdd(999999).run();
        System.out.println("999999 is odd: " + result2); // true
    }
}
```
~~~

~~~admonish example title="Example 3: Fibonacci with Trampoline"

Computing Fibonacci numbers recursively is inefficient and stack-unsafe. With trampolining, we achieve stack safety (though we'd still want memoisation for efficiency):

```java
import org.higherkindedj.hkt.trampoline.Trampoline;
import java.math.BigInteger;

public class FibonacciExample {
    // Stack-safe Fibonacci using tail recursion with accumulator
    static Trampoline<BigInteger> fibonacci(int n, BigInteger a, BigInteger b) {
        if (n == 0) return Trampoline.done(a);
        if (n == 1) return Trampoline.done(b);

        return Trampoline.defer(() ->
            fibonacci(n - 1, b, a.add(b))
        );
    }

    public static void main(String[] args) {
        // Compute the 10,000th Fibonacci number - stack-safe!
        BigInteger fib10000 = fibonacci(
            10000,
            BigInteger.ZERO,
            BigInteger.ONE
        ).run();

        System.out.println("Fibonacci(10000) has " +
            fib10000.toString().length() + " digits");
    }
}
```
~~~

~~~admonish example title="Example 4: Using map and flatMap"

Trampoline is a monad, so you can compose computations using `map` and `flatMap`:

```java
import org.higherkindedj.hkt.trampoline.Trampoline;

public class TrampolineCompositionExample {
    static Trampoline<Integer> countDown(int n) {
        if (n <= 0) return Trampoline.done(0);
        return Trampoline.defer(() -> countDown(n - 1));
    }

    public static void main(String[] args) {
        // Use map to transform the result
        Trampoline<String> countWithMessage = countDown(100000)
            .map(result -> "Countdown complete! Final: " + result);

        System.out.println(countWithMessage.run());

        // Use flatMap to sequence trampolines
        Trampoline<Integer> sequenced = countDown(50000)
            .flatMap(first -> countDown(50000)
                .map(second -> first + second));

        System.out.println("Sequenced result: " + sequenced.run());
    }
}
```
~~~

~~~admonish example title="Example 5: Integration with TrampolineUtils"

When traversing large collections with custom applicatives, use `TrampolineUtils` for guaranteed stack safety:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.trampoline.TrampolineUtils;
import org.higherkindedj.hkt.id.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TrampolineUtilsExample {
    public static void main(String[] args) {
        // Create a large list
        List<Integer> largeList = IntStream.range(0, 100000)
            .boxed()
            .collect(Collectors.toList());

        // Traverse it safely
        Kind<IdKind.Witness, List<String>> result =
            TrampolineUtils.traverseListStackSafe(
                largeList,
                i -> Id.of("item-" + i),
                IdMonad.instance()
            );

        List<String> unwrapped = IdKindHelper.ID.narrow(result).value();
        System.out.println("Traversed " + unwrapped.size() + " elements safely");
    }
}
```

See [`TrampolineUtils` documentation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-core/src/main/java/org/higherkindedj/hkt/trampoline/TrampolineUtils.java) for more details on stack-safe applicative operations.
~~~

## When to Use Trampoline

**Use Trampoline when:**

1. **Deep Recursion**: Processing data structures or algorithms that recurse deeply (>1,000 levels).
2. **Tail Recursion**: Converting tail-recursive algorithms that would otherwise overflow.
3. **Mutual Recursion**: Implementing mutually recursive functions.
4. **Stack Safety Guarantee**: When you absolutely must prevent `StackOverflowError`.
5. **Large Collections**: When using `TrampolineUtils` to traverse large collections (>10,000 elements) with custom applicatives.

**Avoid Trampoline when:**

1. **Shallow Recursion**: For recursion depth <1,000, the overhead isn't justified.
2. **Performance Critical**: Trampoline adds overhead compared to direct recursion or iteration.
3. **Simple Iteration**: If you can write a simple loop, that's usually clearer and faster.
4. **Standard Collections**: For standard applicatives (Id, Optional, Either, etc.) on moderate-sized lists (<10,000 elements), regular traverse is sufficient.

## Performance Characteristics

- **Stack Space**: O(1) - constant stack space regardless of recursion depth
- **Heap Space**: O(n) - creates data structures for deferred computations
- **Time Overhead**: Small constant overhead per recursive step compared to direct recursion
- **Throughput**: Slower than native tail-call optimisation (if it existed in Java) but faster than stack overflow recovery

**Benchmarks**: The implementation has been verified to handle:
- 100,000+ iterations in factorial computations
- 1,000,000+ iterations in mutual recursion (isEven/isOdd)
- 100,000+ element list traversals (via `TrampolineUtils`)

## Implementation Notes

The `run()` method uses an iterative algorithm with an explicit continuation stack (implemented with `ArrayDeque`) to process the trampoline structure. This algorithm:

1. Starts with the current trampoline
2. If it's `More`, unwraps it and continues
3. If it's `FlatMap`, pushes the function onto the stack and processes the sub-computation
4. If it's `Done`, applies any pending continuations from the stack
5. Repeats until there are no more continuations and we have a final `Done` value

This design ensures that regardless of how deeply nested the recursive calls were in the original algorithm, the execution happens in constant stack space.

## Type Safety Considerations

The implementation uses a `Continuation` wrapper to safely handle heterogeneous types on the continuation stack. This design confines the necessary unsafe cast to a single, controlled location in the code, making the type erasure explicit, documented, and verified to be safe.

## Summary

The `Trampoline` monad provides a practical solution to Java's lack of tail-call optimisation. By converting recursive algorithms into trampolined form, you can:

- Write naturally recursive code that's guaranteed stack-safe
- Compose recursive computations functionally using `map` and `flatMap`
- Leverage `TrampolineUtils` for stack-safe applicative operations on large collections
- Maintain clarity and correctness whilst preventing `StackOverflowError`

For detailed implementation examples and more advanced use cases, see the [TrampolineExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/trampoline/TrampolineExample.java) in the examples module.

---

~~~ admonish tip title="Further Reading"
For a comprehensive exploration of recursion, thunks, and trampolines in Java and Scala, see Scott Logic's blog post: [Recursion, Thunks and Trampolines with Java and Scala](https://blog.scottlogic.com/2025/05/02/recursion-thunks-trampolines-with-java-and-scala.html).
~~~

~~~admonish example title="Benchmarks"
Trampoline has dedicated JMH benchmarks measuring stack-safe recursion overhead and scaling behaviour. Key expectations:

- **Deep recursion (10,000+)** completes without `StackOverflowError` — stack-safe trampolining is working
- **Performance scaling is linear with depth** — no exponential blowup or quadratic complexity
- **`factorialTrampoline` vs `factorialNaive`** show similar performance at depth 100 — trampoline overhead is minimal for moderate depths
- Non-linear scaling is a warning sign suggesting memory leak or quadratic complexity

```bash
./gradlew :hkj-benchmarks:jmh --includes=".*TrampolineBenchmark.*"
```
See [Benchmarks & Performance](../benchmarks.md) for full details and how to interpret results.
~~~

---

**Previous:** [Stream](stream_monad.md)
**Next:** [Free](free_monad.md)

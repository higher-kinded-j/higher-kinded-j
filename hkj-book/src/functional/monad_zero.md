# MonadZero: Filtering in Monadic Chains

~~~admonish info title="What You'll Learn"
- How MonadZero combines Monad and Alternative capabilities
- The role of `zero()` as an absorbing element in monadic sequences
- Enabling filtering in for-comprehensions with `when()`
- Practical examples using List, Maybe, and Optional
- Writing generic functions for monads with failure semantics
~~~

## The Problem: No Way to Filter Mid-Chain

You're building a for-comprehension that iterates over combinations of values, but you need to discard certain results mid-chain. With a plain `Monad`, there is no way to say "skip this iteration":

```java
// Without MonadZero: you must push filtering to the end and wrap in a conditional
var result = For.from(monad, list1)
    .from(a -> list2)
    .yield((a, b) -> {
        if ((a + b) % 2 != 0) {        // awkward: filtering inside yield
            return a + " + " + b;
        }
        return null;                     // no clean way to discard
    });
```

What you really want is a guard clause that eliminates unwanted paths *before* they reach the final projection.

---

## The Solution: `zero()` and `when()`

**`MonadZero`** extends both `Monad` and `Alternative`, adding a `zero()` element that acts as an absorbing value. When a computation produces `zero`, subsequent operations in the chain are skipped, exactly like multiplying by zero.

```
  For-comprehension with MonadZero:

  list1: [1, 2, 3]  x  list2: [10, 20]

  (1,10)  sum=11 odd? YES --> yield "1 + 10 = Sum: 11"
  (1,20)  sum=21 odd? YES --> yield "1 + 20 = Sum: 21"
  (2,10)  sum=12 odd? NO  --> zero() (discarded)
  (2,20)  sum=22 odd? NO  --> zero() (discarded)
  (3,10)  sum=13 odd? YES --> yield "3 + 10 = Sum: 13"
  (3,20)  sum=23 odd? YES --> yield "3 + 20 = Sum: 23"
```

~~~admonish note title="Interface Signature"
```java
public interface MonadZero<F> extends Monad<F>, Alternative<F> {
    <A> Kind<F, A> zero();

    @Override
    default <A> Kind<F, A> empty() {
        return zero();
    }
}
```
~~~

---

## Filtering in For-Comprehensions

The most powerful application of `MonadZero` is the `.when()` clause in the `For` comprehension builder. The builder has two entry points:

* `For.from(monad, ...)` -- For any standard `Monad`.
* `For.from(monadZero, ...)` -- An overloaded version that unlocks `.when(predicate)`.

When the predicate evaluates to `false`, the builder internally calls `monad.zero()` to terminate that specific computational path.

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import java.util.Arrays;
import java.util.List;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

final ListMonad listMonad = ListMonad.INSTANCE;

final Kind<ListKind.Witness, Integer> list1 = LIST.widen(Arrays.asList(1, 2, 3));
final Kind<ListKind.Witness, Integer> list2 = LIST.widen(Arrays.asList(10, 20));

final Kind<ListKind.Witness, String> result =
    For.from(listMonad, list1)
        .from(a -> list2)
        .when(t -> (t._1() + t._2()) % 2 != 0)        // Guard: keep only odd sums
        .let(t -> "Sum: " + (t._1() + t._2()))
        .yield((a, b, c) -> a + " + " + b + " = " + c);

final List<String> narrow = LIST.narrow(result);
System.out.println("Result: " + narrow);
// [1 + 10 = Sum: 11, 1 + 20 = Sum: 21, 3 + 10 = Sum: 13, 3 + 20 = Sum: 23]
```

---

## Key Implementations

For different types, `zero()` has the natural "empty" semantics:

| Type | `zero()` returns | Effect of filtering |
|------|-----------------|---------------------|
| **List** | `[]` (empty list) | Discards that combination from the result list |
| **Maybe** | `Nothing` | Short-circuits to `Nothing` |
| **Optional** | `Optional.empty()` | Short-circuits to empty |
| **Stream** | Empty stream | Skips that element lazily |

---

## Writing Generic Functions

`MonadZero` allows you to write generic functions that work over any monad with a concept of "emptiness":

```java
// A generic "filter" that works with List, Maybe, Optional, or Stream
public static <F, A> Kind<F, A> filterM(
    MonadZero<F> mz, Kind<F, A> fa, Predicate<A> predicate) {
    return mz.flatMap(
        a -> predicate.test(a) ? mz.of(a) : mz.zero(),
        fa
    );
}
```

---

~~~admonish info title="Key Takeaways"
* **`zero()` is the absorbing element** that terminates a computational path, like multiplying by zero
* **`.when()` in for-comprehensions** is the primary use case, enabling mid-chain filtering
* **Requires `MonadZero`**, not just `Monad`; use `For.from(monadZero, ...)` to unlock filtering
* **Works uniformly** across List, Maybe, Optional, and Stream
~~~

~~~admonish tip title="See Also"
- [Alternative](alternative.md) - The type class that MonadZero extends alongside Monad
- [For Comprehension](for_comprehension.md) - Using MonadZero's filtering capabilities in comprehensions
~~~

---

**Previous:** [Foldable and Traverse](foldable_and_traverse.md)
**Next:** [Selective](selective.md)

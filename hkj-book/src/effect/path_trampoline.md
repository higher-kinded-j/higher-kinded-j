# TrampolinePath

`TrampolinePath<A>` wraps `Trampoline<A>` for **stack-safe recursion**. Deep
recursive algorithms that would blow the stack with direct recursion become
safe with trampolining.

~~~admonish info title="What You'll Learn"
- Creating TrampolinePath instances
- Stack-safe recursive patterns
- When to use (and when not to)
~~~

---

## Creation

```java
// Immediate value (already computed)
TrampolinePath<Integer> done = Path.trampolineDone(42);

// Suspended computation (thunked)
TrampolinePath<Integer> suspended = Path.trampolineSuspend(() -> 42);

// From existing Trampoline
TrampolinePath<Integer> path = Path.trampoline(trampoline);
```

---

## Core Operations

```java
TrampolinePath<Integer> start = Path.trampolineDone(10);

TrampolinePath<Integer> doubled = start.map(n -> n * 2);  // 20

TrampolinePath<Integer> chained = start.via(n ->
    Path.trampolineDone(n + 5));  // 15
```

---

## Stack-Safe Recursion

The real power is in recursive algorithms. Here's factorial without stack overflow:

```java
TrampolinePath<Long> factorial(long n) {
    return factorialHelper(n, 1L);
}

TrampolinePath<Long> factorialHelper(long n, long acc) {
    if (n <= 1) {
        return Path.trampolineDone(acc);
    }
    // Suspend to avoid stack growth
    return Path.trampolineSuspend(() ->
        factorialHelper(n - 1, n * acc).run());
}

// Compute factorial of 10000 - no stack overflow!
Long result = factorial(10000L).run().run();
```

Compare with naive recursion:

```java
// This WILL overflow the stack for large n
long naiveFactorial(long n) {
    if (n <= 1) return 1;
    return n * naiveFactorial(n - 1);  // Stack frame per call!
}
```

---

## Mutual Recursion

Trampolining also handles mutual recursion:

```java
TrampolinePath<Boolean> isEven(int n) {
    if (n == 0) return Path.trampolineDone(true);
    return Path.trampolineSuspend(() -> isOdd(n - 1).run());
}

TrampolinePath<Boolean> isOdd(int n) {
    if (n == 0) return Path.trampolineDone(false);
    return Path.trampolineSuspend(() -> isEven(n - 1).run());
}

// Works for any depth
Boolean result = isEven(1_000_000).run().run();  // true
```

---

## Extraction

```java
TrampolinePath<Integer> path = Path.trampolineDone(42);

// Get the Trampoline
Trampoline<Integer> trampoline = path.run();

// Execute (runs the trampoline to completion)
Integer value = trampoline.run();

// Or chain: path.run().run()
```

---

## When to Use

`TrampolinePath` is right when:
- You have deep recursive algorithms that could overflow the stack
- Tree traversal, graph algorithms, or mathematical computations
- Mutual recursion patterns
- You need guaranteed stack safety regardless of input size

`TrampolinePath` is wrong when:
- Recursion depth is bounded and small (regular recursion is simpler)
- You're not doing recursion at all
- Performance is critical and you can use iteration instead

~~~admonish tip title="How It Works"
Trampolining converts recursive calls into a loop. Instead of each recursive
call adding a stack frame, it returns a "continue" instruction. The trampoline
runner loops until it gets a "done" instruction. Result: O(1) stack space
regardless of recursion depth.
~~~

~~~admonish tip title="See Also"
- [Trampoline](../monads/trampoline.md) - Underlying type for TrampolinePath
~~~

---

**Previous:** [GenericPath](path_generic.md)
**Next:** [FreePath](path_free.md)

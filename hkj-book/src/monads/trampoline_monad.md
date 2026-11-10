<!-- description: Run deep recursion in Java without overflowing the stack using Trampoline: a sum a million calls deep, two functions that call each other, and a long list traversed under IO. -->

# Trampoline: Deep Recursion in a Loop

_Hold each step as data and run them in one loop, so deep recursion costs heap, not stack._

~~~admonish abstract title="Trampoline at a glance"
| | |
|---|---|
| **Models** | A recursive computation held as steps, which `run()` works through one at a time |
| **Replaces** | A recursive method that overflows the stack, or a loop that keeps its own stack of work |
| **Effect Path** | [`TrampolinePath`](../glossary/effect-paths.md#trampolinepath) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:trampoline_monad}}
| **Choose it when** | Recursion can go thousands of calls deep, or two functions call each other; otherwise keep the plain recursion or loop you have, or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Explain why a recursive sum overflows the stack, and what sets the depth it reaches
- Hold the same recursion as steps on a `TrampolinePath`, and run it a million calls deep
- Turn each kind of call into a step: a base case, a tail call, and a call whose result you combine
- Run two functions that call each other in one loop
- Traverse a long list under `IO` without nesting a call for each element
~~~

~~~admonish example title="See Example Code"
**The code on this page is [TrampolineBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java)**: the page includes it, so the build compiles and runs it. [TrampolineBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBookTest.java) holds what the page says each method does.
~~~

---

## A sum that overflows {#a-sum-that-overflows}

The sums on this page use plain numbers, since the depth of the recursion is what matters. The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.util.List;
import java.util.stream.IntStream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TrampolinePath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trampoline.TrampolineUtils;
```

Each call to this sum waits for the deeper call it makes to return, so each holds a frame on the thread's stack:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:naive}}
```

`sum(1_000)` gives `500500`, but `sum(1_000_000)` throws `StackOverflowError`. A thread's stack has a fixed size, 1 MB by default: 5,000 of these frames fit, and 100,000 do not. Where between the two it overflows depends on whether the JIT has compiled the method yet. The JVM never reuses a frame for a call in tail position, so rewriting the method as a tail call does not help. You can catch the error, but the sum is lost.

## The same sum, held as steps {#the-same-sum-held-as-steps}

A `TrampolinePath` holds the recursion instead of making it. `Path.trampolineDefer` holds the deeper call as a step to take later, and `map` holds what to do with that call's result:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:path}}
```

`run()` works through the steps in one loop. Each pending addition waits on a list on the heap, so the stack stays flat:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:path_run}}
```

The name comes from the loop. Each step bounces back to it with the next step to take, where a plain recursion would go one frame deeper:

<pre class="hkj-ascii-diagram" role="img" aria-label="On the left, plain recursion: sum(5) calls sum(4), down to sum(0), and each call stays on the stack until the one it made returns, so the stack grows with n and the additions happen as the calls return, giving 15. On the right, the trampoline: run takes a deferred step for sum(5), which hands back a deferred step for sum(4) and an addition to make later, and so on down to sum(0), which is done with 0. The loop keeps the pending additions on a list on the heap, then applies them, 0 + 1 + 2 + 3 + 4 + 5, giving 15. The stack stays flat.">
  Plain recursion                     On a trampoline

  sum(5)                              run():
    sum(4)                              +--> sum(5): defer sum(4), then add 5
      sum(3)                            |      |
        sum(2)                          |      v
          sum(1)                        +--> sum(4): defer sum(3), then add 4
            sum(0)                      |      |
            return 0                    |      v
          return 1                      +--> ...
        return 3                        |      |
      return 6                          |      v
    return 10                           +--> sum(0) is done: 0
  return 15                             then the held additions:
                                        0 + 1 + 2 + 3 + 4 + 5 = 15

  Stack grows with n                  Stack stays flat; additions wait on the heap
</pre>

~~~admonish tip title="Influence: Recursion, Thunks and Trampolines"
Higher-Kinded-J's trampoline grew out of Scott Logic's blog post [Recursion, Thunks and Trampolines with Java and Scala](https://blog.scottlogic.com/2025/05/02/recursion-thunks-trampolines-with-java-and-scala.html). The post builds the same idea from two cases, `Done` for a result and `More` for a step still to take, run by one loop. Higher-Kinded-J adds a third case, `FlatMap`, for sequencing, and an instance, so a `Trampoline` takes part in code written for any monad.
~~~

## Each kind of call, as a step {#each-kind-of-call-as-a-step}

A recursive method becomes a trampolined one call by call:

| In the recursive method | On a trampoline |
|---|---|
| The return type `T` | `Trampoline<T>`, or `TrampolinePath<T>` |
| A base case, `return value` | `Trampoline.done(value)` |
| A tail call, `return f(x)` | `Trampoline.defer(() -> f(x))` |
| A call whose result you combine, `return n + f(x)` | `Trampoline.defer(() -> f(x)).map(r -> n + r)` |
| Two calls, `return f(x) + f(y)` | `Trampoline.defer(() -> f(x)).flatMap(a -> f(y).map(b -> a + b))` |

`map` and `flatMap` are what keep a combined call safe: `defer` alone would leave the addition outside the trampoline. When the running total can travel with the call, a tail call needs only `defer`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:tail}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:tail_run}}
```

## Two functions that call each other {#two-functions-that-call-each-other}

Two functions that call each other overflow the stack as fast as one deep function. They are awkward to merge into one loop by hand, since the loop must track which function it is in. On a trampoline, each stays as it is written, and defers to the other:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:mutual}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:mutual_run}}
```

## A long list under `IO` {#core-components}

`traverse` runs a function that returns an effect on each element of a list, and gathers the results into one effect holding a list, as [Foldable and Traverse](../functional/foldable_and_traverse.md) explains. Under a lazy applicative such as `IO`, a traverse that combines the elements one after another nests one call per element, so running the result for a long list can overflow. `TrampolineUtils.traverseListStackSafe` combines them as a balanced tree instead, and `sequenceStackSafe` does the same for a list of effects:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/trampoline/TrampolineBook.java:traverse}}
```

They sit beside `Trampoline` in its package, but need no trampoline, since the tree's nesting grows with the log of the list's length.

## A trampoline always gives a value {#a-trampoline-always-gives-a-value}

Nothing runs until `run()`. A trampoline always gives a value, so `done(null)` throws `NullPointerException`, as [A Task Always Gives a Value](vtask_monad.md#a-task-always-gives-a-value) sets out for every lazy type.

## Creating instances {#creating-instances}

`Path.trampolineDone(value)` and `Path.trampolineDefer(supplier)` start a `TrampolinePath`, and `Path.trampoline(trampoline)` wraps a `Trampoline`. `Trampoline.done(value)` and `Trampoline.defer(supplier)` build a `Trampoline` directly.

## Methods {#methods}

| Method | What it does |
|---|---|
| `defer(supplier)` | Holds a step to take later |
| `map(f)`, `flatMap(f)` | Hold what to do with the result; on a `TrampolinePath`, `flatMap` is `via` |
| `zipWith(other, f)` | Combines two trampolines' results, on a `TrampolinePath` |
| `run()` | Works through every step in a loop, and gives the result |
| `toTrampoline()`, `toIOPath()`, `toLazyPath()` | Turn a `TrampolinePath` into a `Trampoline`, an `IOPath` or a `LazyPath` |

## Working with Kind {#working-with-kind}

Generic code takes `Trampoline` through its `Monad`, from `Instances.monad(trampoline())`, which is `TrampolineMonad.INSTANCE`. Its `flatMap` holds the step as `Trampoline.flatMap` does, so a deep chain built through the instance runs in the loop too. `TRAMPOLINE.widen` and `TRAMPOLINE.narrow`, from `TrampolineKindHelper`, convert between a `Trampoline` and its `Kind`.

## When to use `Trampoline` {#when-to-use-trampoline}

| You need | Use |
|---|---|
| Recursion that can go thousands of calls deep, such as a walk over a deep tree | `Trampoline`, or [`TrampolinePath`](../glossary/effect-paths.md#trampolinepath) |
| Two functions that call each other | `Trampoline`: each stays as it is written |
| A traverse of a long list under `IO` or another lazy applicative | `TrampolineUtils` |
| Recursion that stays a few hundred calls deep | Plain recursion |
| A loop that is already a loop | The loop: a trampoline adds an object per step |

**Benchmarks:** `TrampolineBenchmark` measures building and running trampolines, and deep recursion against plain recursion. [Benchmarks & Performance](../benchmarks.md) says how to run it.

---

**Previous:** [Identity](identity.md)
**Next:** [Free](free_monad.md)

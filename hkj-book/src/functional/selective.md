<!-- description: Run an effect only when an earlier result calls for it with select, whenS and ifS, every branch given up front, and see what that buys. -->

# Selective: Conditional Effects

_Run an effect only when an earlier result calls for it, with every branch named up front._

~~~admonish info title="What You'll Learn"
- Apply a function only to a value that still needs it, with `select`
- Run an effect only when a condition holds with `whenS`, and pick one of two with `ifS`
- Say what giving every branch up front buys, and where a program can be inspected before it runs
- Get the `Selective` instance for each type that has one
- Check an instance against the two laws `hkj-test` checks
~~~

~~~admonish example title="See Example Code"
**The code on this page is [SelectiveBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java)**: the page includes it, so the build compiles and runs it. [SelectiveBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/selective/SelectiveBookTest.java) holds what the page says each operation runs, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## Between Applicative and Monad {#between-applicative-and-monad}

[Applicative](applicative.md)'s `map2` combines effects that do not depend on one another: no value one effect gives can decide whether another runs. [Monad](monad.md)'s `flatMap` lets a step choose the next effect from the last result, but the next effect does not exist until the step runs. `Selective` sits between them: an earlier result decides whether an effect runs, and every effect that might run is named up front.

`Selective` extends `Applicative`, and every `Selective` in the library is also a `Monad`. Choosing among `Applicative`, `Selective` and `Monad` for a task is [Choosing Abstraction Levels](abstraction_levels.md)'s subject. On an [Effect Path](../effect/ch_intro.md), a `via` step with an `if` does this job for one container.

The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOSelective;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeSelective;
```

Import the library's `IO` by name: Java 25 has a `java.lang.IO` too.

## `select`: apply a function only where it is needed {#select}

A `Choice<A, B>` is a `Left` holding an `A` or a `Right` holding a `B`, and `Selective.left` and `Selective.right` build one. Here `Left` means not done yet, not failure.

`select(fab, ff)` takes a container of `Choice<A, B>` and a container of a function from `A` to `B`. A `Left(a)` still needs the function, so `select` applies it. A `Right(b)` is already the answer, so `select` returns it untouched. With `ap`, a `Nothing` function would make any result `Nothing`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:select}}
```

## `whenS`: run an effect only when a condition holds {#whens}

The condition is itself an effect: an `IO` that, when it runs, says whether an order's total is over 100.00.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:large}}
```

`whenS(condition, effect)` runs the effect only when the condition gives `true`, and gives `Unit` either way. Flagging an order for review runs only for a large one:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:review}}
```

Run for an order of 50.00 and one of 120.00, it flags only the second:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:review_run}}
```

`whenS_` does the same for an effect that gives a value, and drops the value.

## `ifS`: pick one of two effects {#ifs}

`ifS(condition, then, else)` gives `then` when the condition gives `true` and `else` when it gives `false`. Both are given up front, and `IO`'s `ifS` runs the condition, then only the branch it picks. A large order goes by freight, and any other by post:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:courier}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/selective/SelectiveBook.java:courier_run}}
```

A condition that holds more than one answer gives more than one branch. `List`'s `ifS` gives both branches' results for `[true, false]`. `Validated`'s gathers both branches' errors when the condition is itself invalid.

## Other operations {#other-operations}

| Operation | What it does |
|---|---|
| `branch(fab, onLeft, onRight)` | Applies `onLeft` to a `Left` and `onRight` to a `Right`, running only the handler it applies |
| `orElse(alternatives)` | Gives the first `Right` in a list of `Choice` containers, or the last `Left` when none is a `Right` |
| `apS(initial, steps)` | Runs each step on the value while it is a `Right`, and stops at the first `Left` |

This `orElse` takes `Choice` containers. The [Alternative](alternative.md) on the next page has an `orElse` of its own, a different method.

## What "given up front" buys {#what-given-up-front-buys}

With `flatMap`, the next effect is built by a function from the last result, so nothing can see it before the program runs. With `Selective`, every effect that might run is a value you passed in, so a program held as data could be inspected before it runs. Holding a `Selective` program as data is not supported yet. A [Free Applicative](../monads/free_applicative.md) program has no conditions, and `FreeApAnalyzer` lists every effect it will run.

An instance such as `IOSelective` or `MaybeSelective` runs a program, and inspects nothing. None of them runs two branches at the same time.

## Laws {#laws}

The [Selective Applicative Functors](https://dl.acm.org/doi/10.1145/3341694) paper gives `select` three laws: identity, distributivity and associativity. They do not require a `select` to skip the function for a `Right`. Checking them with `hkj-test` is not supported yet. `SelectiveLaws` checks two stricter properties, which the library's instances keep.

~~~admonish note title="Laws"
- **Left pure: a known `Left` applies the function.** `select(of(Left(a)), ff)` equals `ap(ff, of(a))`.
- **Right pure: a known `Right` skips the function.** `select(of(Right(b)), ff)` equals `of(b)`, even when `ff` has failed.

Two containers are equal when they hold equal values, or equal errors, in the same shape. The checks assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

`SelectiveLaws`, in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency)'s `org.higherkindedj.hkt.laws` package, checks both on any instance. `KindEquivalence.byEqualsAfter`, in `org.higherkindedj.hkt.assertions`, builds the equality from a function it applies to both sides before comparing them with `equals`. The test uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherSelective;
import org.higherkindedj.hkt.laws.SelectiveLaws;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeSelective;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:selective_laws}}
```
~~~

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"A `Selective` program is analysed before it runs."** An instance such as `IOSelective` runs it, and holding a `Selective` program as data is not supported yet.
- **"`ifS` runs both branches."** `IO`'s runs the condition and then the chosen branch. Only a condition that holds more than one answer, as a `List` can, gives both.
- **"Both branches run at the same time."** No instance runs them together. For `VTask`s that run at once, use [`Par`](../monads/vtask_monad.md#parallel-composition-with-par).
- **"`whenS` needs a `Monad`."** It needs only `select`, which a `Selective` provides.
~~~

---

## The fine print {#the-fine-print}

### Which types have a `Selective` {#which-types-have-a-selective}

| Type | Instance |
|---|---|
| `Maybe` | `MaybeSelective.INSTANCE` |
| `Optional` | `OptionalSelective.INSTANCE` |
| `List` | `ListSelective.INSTANCE` |
| `IO` | `IOSelective.INSTANCE` |
| `Either<E, ?>` | `EitherSelective.instance()` |
| `Reader<R, ?>` | `ReaderSelective.instance()` |
| `Id` | `IdSelective.instance()` |
| `Validated<E, ?>` | `ValidatedSelective.instance(semigroup)`, whose semigroup joins the errors `select` meets |

`Instances` has no `selective` factory, so ask the class for its instance.

### A `Selective` of your own {#a-selective-of-your-own}

An instance needs `select`, with `of` and `ap` from `Applicative` and `map` from `Functor`. The defaults for `whenS`, `ifS` and `branch` run a guarded effect exactly when your `select` runs the function's effect. Skip that effect for a `Right`, as the library's instances do, and the defaults run only the chosen effect. A `select` built from `ap` runs every effect.

---

~~~admonish tip title="See Also"
- [Choosing Abstraction Levels](abstraction_levels.md): `Applicative`, `Selective` or `Monad` for a task
- [Free Applicative](../monads/free_applicative.md): programs that can be inspected before they run
- [Applicative](applicative.md): combining effects that do not depend on one another
~~~

~~~admonish tip title="Further Reading"
- **Andrey Mokhov, Georgy Lukyanov, Simon Marlow and Jeremie Dimino**, [Selective Applicative Functors](https://dl.acm.org/doi/10.1145/3341694): the paper that introduced `Selective`
~~~

---

**Previous:** [Natural Transformation](natural_transformation.md)
**Next:** [Alternative](alternative.md)

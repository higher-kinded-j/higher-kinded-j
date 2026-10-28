<!-- description: Write a chain of flatMap calls as steps with For: bind, filter with when, mark independent steps with par, and traverse a collection inside the chain. -->

# For-Comprehensions

_Write a chain of `flatMap` calls as a list of steps, with filters, independent steps and traversals._

~~~admonish info title="What You'll Learn"
- Turn nested `flatMap` calls into steps with `For.from`, `from`, `let` and `yield`
- Filter a chain with `when`, given a `MonadZero`
- Mark independent steps with `par`, and say when they run at the same time
- Traverse a collection inside a chain with `traverse`, `sequence` and `flatTraverse`
- Choose between `For`, `ForPath` and `ForState`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ForBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java)**: the page includes it, so the build compiles and runs it. [ForBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBookTest.java) holds what the page says each step does.
~~~

---

## When nested `flatMap` calls get deep {#when-nested-flatmap-calls-get-deep}

A receipt needs three things in turn: the order, its customer's verified email, and a confirmation from charging the order's total. Each step needs what an earlier step found, so [Monad](monad.md)'s `flatMap` chains them. With one `Optional` you would nest `flatMap` calls inside one another, and the same happens over `Kind`.

For one container, an [Effect Path](../effect/ch_intro.md) chains the same steps with [`ForPath`](../effect/forpath_comprehension.md), such as `ForPath.from(eitherPath)`. Application code stays on the Paths. This page is for `For`, a chain written once for any container that has a `Monad`. Written as nested calls, the receipt is:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:nested}}
```

The receipt is in the innermost lambda, and `total` has to be a local variable so that a later step can still see it. A `For` comprehension writes the same chain as a list of steps.

The code on this page uses these imports, with `Order` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
```

## Steps: `from`, `let` and `yield` {#from-let-and-yield}

`For.from(monad, source)` starts the chain, and each later step adds values, filters the chain, or ends it:

| Step | What it does | Built on |
|---|---|---|
| `from(f)` | Adds the value inside the container `f` gives back | `flatMap` |
| `let(f)` | Adds a plain value worked out from the values so far | `map` |
| `when(test)` | Gives the instance's empty value when `test` fails | `MonadZero`'s `filter` |
| `par(f1, f2)` | Adds two to four values that do not need one another | `flatMap`, then `map2` to `map4` |
| `yield(f)` | Ends the chain with a result made from every value | `map` |

The first step's function receives the value bound so far. From the second value on, a step receives a tuple holding the values in the order they were bound, read as `t._1()` for the first, `t._2()` for the second, and so on. `yield` takes either the tuple or one parameter for each value. Written with `For`, the receipt reads top to bottom, and in it `t._1()` is the order and `t._2()` the total that `let` added:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:generic}}
```

Run it with `Either`'s `Monad`, once with an email that is verified and once with one that is not. `ORDER` is any order of two lines, totalling 50.00:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:instances}}
```

What happens when a step has no value is the instance's, as on the [Monad](monad.md#what-each-instance-does) page: `Either`'s stops at the `Left`, and the charge never runs.

## Filtering with `when` {#filtering-with-when}

`when` needs a [`MonadZero`](monad_zero.md): a `Monad` with an empty value, such as an empty list or `Nothing`, to give back when a test fails. The type you start the chain from decides. `Instances.monadZero(list())` gives a chain with `when`, and `Instances.monad(list())` gives one without:

<!-- verify:rejects "cannot find symbol" -->
```java
For.from(Instances.monad(list()), orders).when(order -> order.status() == OrderStatus.SHIPPED);
```

javac refuses it with "cannot find symbol", because a chain started from a plain `Monad` has no `when`. The instance decides what the empty value is: `List`'s drops the element, and `Maybe`'s gives `Nothing`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:when}}
```

## Independent steps with `par` {#independent-steps-with-par}

`par` binds values that do not need one another. `For.par(monad, …)` starts a chain with two to five of them, and a step's `par(f1, …)` fans out two to four from the values bound before it. Its result is an ordinary step, so `from`, `let`, `when` or another `par` can follow.

`par` combines its values with the instance's `map2` to `map5`, where `from` uses `flatMap`. So it does what the instance's [Applicative](applicative.md#what-each-instance-does) does: with `Validated`'s instance, `par` reports both errors, and `from` stops at the first. The step's `par` fans out from the order:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:par}}
```

`par` says the values are independent, and it runs them at the same time only where the instance's `map2` does. No instance's `map2` starts its values together: `IO`'s and `VTask`'s run them one after the other, and `CompletableFuture`'s waits for futures that may already be running. To run two `VTask`s at the same time, use [`ForPath.par`](../effect/forpath_par.md) on two `VTaskPath`s, which calls `Par.map2`, or call [`Par.map2`](../monads/vtask_monad.md#parallel-composition-with-par) yourself.

## Traversing inside a chain {#traversing-inside-a-chain}

A step can run an effect on every element of a collection the chain holds, and bind the collected results:

| Step | What it binds | Built on |
|---|---|---|
| `traverse(traversable, extractor, f)` | `f` run on every element, the results collected | [`Traverse`](foldable_and_traverse.md)'s `traverse` |
| `sequence(traversable, extractor)` | A collection of containers, turned into one container of a collection | `Traverse`'s `sequenceA`, which is `traverse` with the identity function |
| `flatTraverse(traversable, innerMonad, extractor, f)` | `f` run on every element, the nested collections flattened | `traverse`, then the collection's own `flatMap` |

`traversable` is the collection's `Traverse`, such as `ListTraverse.INSTANCE`, and `innerMonad` the collection's own `Monad`. `extractor` picks the collection out of the values bound so far; here the chain starts from it, so it is `lines -> lines`. `LIST.widen` puts the list in, and `.map(LIST::narrow)` takes the result out. `priceInStock` prices a line, or gives `Nothing` for a line with no stock, so one missing line makes the whole result `Nothing`. With `maybeMonad` from the `when` example:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forcomprehension/ForBook.java:traverse}}
```

## Which comprehension should you use? {#which-comprehension}

| You are writing | Use |
|---|---|
| Application code over one Effect Path | [`ForPath`](../effect/forpath_comprehension.md), which takes and gives back Paths |
| A method for any container, over `Kind` | `For`, on this page |
| A chain whose values are easier to name than to number | [`ForState`](forstate_comprehension.md), with named fields in a record, or `toState` from `For` ([MTL and ForState](for_mtl.md#bridging-to-forstate-with-tostate)) |
| Steps that read a field, convert a value or match a case | `focus`, `through` and `match`, as [For with Optics](for_optics.md) shows: `through` works on the first step only, and `match` needs a `MonadZero` |

A monad transformer, such as `StateT`, is a `Monad` over `Kind`, so `For` chains it as it does any other: the [StateT](../transformers/statet_transformer.md) page shows one.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`par` runs the steps at the same time."** It marks them as independent, and the instance's `map2` decides how they run. `ForPath.par` on `VTaskPath` is the one that runs them at once.
- **"`List` has `when`, so my chain does."** Only a chain started from a `MonadZero`, such as `Instances.monadZero(list())`, has `when`. Started from `Instances.monad(list())`, it has none.
- **"`For` is Java's version of Scala's `for`."** It is a library builder, with no compiler support. Each step is a method call you can read in the source.
~~~

~~~admonish tip title="You can ship now"
You can now write a chain of dependent steps once for any container, as `receipt` is, with plain values between the steps. You can filter it with `when`, mark independent steps with `par` knowing what each instance does with them, and traverse a collection inside it. The rest of this page is for how many values a chain holds, and for readers who know Scala's `for`.
~~~

---

## The fine print {#the-fine-print}

### Twelve values, and their tuples {#twelve-values-and-their-tuples}

A chain holds up to twelve values, from any mix of `from`, `let`, `par` and the optic steps. The first step is written by hand, and the steps that hold two to twelve values are generated when Higher-Kinded-J is built, so your build needs no processor for them. The twelfth has no room for another value, so it can only `yield`, call `toState`, or filter with `when` in a chain started from a `MonadZero`. Past four or five values, positions such as `t._4()` get hard to follow, and `toState` moves the values into a record with named fields.

### Coming from Scala {#coming-from-scala}

Scala builds the same chain into its syntax:

```scala
for {
  order        <- found
  total         = order.total
  email        <- verify(order.customer)
  confirmation <- charge(total)
} yield s"receipt $confirmation for $total to $email"
```

The compiler turns every `<-` but the last into `flatMap` and the last into `map`, an `if` line into `withFilter`, and `=` into a `map` that carries the value. `For` gives the same steps as methods: `from`, `when`, `let` and `yield`.

---

~~~admonish tip title="See Also"
- [Monad](monad.md): the `flatMap` that `from` is built on
- [Applicative](applicative.md): the `map2` that `par` is built on
- [ForPath Comprehension](../effect/forpath_comprehension.md): the same steps over Effect Paths, with [ForPath Traverse](../effect/forpath_traverse.md) for collections
- [ForState](forstate_comprehension.md): a chain whose values are named fields
- [For with Optics](for_optics.md): `focus`, `match`, `ForTraversal` and `ForIndexed`
- [MTL and ForState](for_mtl.md): reader, state and writer capabilities, and `toState`
- [Order Workflow](../hkts/order-walkthrough.md): a longer chain, end to end
~~~

~~~admonish tip title="Further Reading"
- **Scala documentation**, [For Comprehensions](https://docs.scala-lang.org/tour/for-comprehensions.html): the syntax `For` follows
~~~

~~~admonish info title="Hands-On Learning"
Practise `par` on Paths in the [ForPath Parallel journey](../tutorials/expression/forpath_parallel_journey.md) (9 exercises), and `traverse` in [Tutorial 03: For Traverse Comprehension](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/expression/Tutorial03_ForTraverseComprehension.java) (14 exercises).
~~~

---

**Previous:** [MonadError](monad_error.md)
**Next:** [Higher-Kinded Types](../hkts/ch_intro.md)

<!-- description: Keep a chain's values in a record with named fields, and change them through lenses with ForState: guards, effects, traversals, Isos and zoom. -->

# ForState: Named State Comprehensions

_Keep a chain's values in a record's named fields, and change them through lenses._

<!-- source: Ludwig Wittgenstein, Tractatus Logico-Philosophicus (1922), proposition 5.6, translated by C. K. Ogden; text checked against Project Gutenberg eBook #5740 -->
> _"The limits of my language mean the limits of my world."_
>
> — Ludwig Wittgenstein, *Tractatus Logico-Philosophicus* (5.6, trans. C. K. Ogden)

A tuple gives a chain a language of `_1` to `_12`, and what you can say about each value shrinks to match. `ForState` gives each value a name.

~~~admonish info title="What You'll Learn"
- Start a `ForState` from a value you have with `withState`, or from a `For` chain with `toState`
- Change a record's fields through lenses with `update`, `modify` and `modifyVia`
- Guard the chain with `when`, given a `MonadZero`
- Edit a collection field with `traverse`, a field in another form with `modifyVia`, and part of the record with `zoom`
- Say when each step runs, and how `ForState` differs from the State monad
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ForStateBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java)**, with [Checkout.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/Checkout.java): the page includes both, so the build compiles and runs them. [ForStateBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/forstate/ForStateBookTest.java) holds what the page says each step does.
~~~

---

## When tuple positions stop reading well {#when-tuple-positions-stop-reading-well}

In plain Java, each step's result goes in a local variable with a name. A [`For`](for_comprehension.md) chain keeps its values in a tuple instead, and by the fourth or fifth value `t._4()` says nothing about what it holds. Insert a step, and every later position moves. `ForState` keeps the values in a record you declare, and each step reads and writes them by name, through [lenses](../optics/lenses.md). A `For` chain stops at twelve values, and a record has as many fields as you give it.

An [Effect Path](../effect/ch_intro.md) has no `ForState`. On a Path, `map` with a lens changes a record, as in `path.map(order -> OrderLenses.status().set(OrderStatus.PAID, order))`, and `via` runs a step with an effect. This page is for a chain over `Kind`, written once for any container with a `Monad`.

The code on this page uses these imports, with `Order`, `Customer` and `EmailAddress` from the order records the [Optics](../optics/ch_intro.md) chapter introduces, and their generated lenses:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.foroptics.ForOpticsBook;
import org.higherkindedj.example.book.optics.cast.Customer;
import org.higherkindedj.example.book.optics.cast.CustomerLenses;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.util.Traversals;
```

## Two ways in {#two-ways-in}

`ForState` starts from a whole value, so every field of the record has a value before the first step runs. There are two ways in:

- **`ForState.withState(monad, value)`** starts from a value you already have, such as an order to update.
- **`toState(build)`**, as a step of a `For` chain, builds the record from the values the chain has bound, taking one parameter per value, or the tuple. It is there at every arity, from one value to twelve.

Never fill a field with `null` for a later step to set. Gather the values with `For` first, and build the record with `toState` once you have them all, so that every field holds a value from the start, as [Values, never null](../hkts/core-concepts.md#values-never-null) asks.

Given a [`MonadZero`](monad_zero.md), `withState` and `toState` give a chain with `when` and `matchThen`, as `For.from` gives one with `when`.

## Updating an order {#updating-an-order}

`inStock` checks a line against what the warehouse holds:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java:in_stock}}
```

`pay` starts from the order, with `MAYBE_MONAD` as `Instances.monadZero(maybe())`, and its steps run in order:

1. `when` lets only a new order through.
2. `traverse` checks every line with `inStock`, and one `Nothing` ends the chain.
3. `modifyVia` lower-cases the customer's email as text. `andThen` joins two lenses, from the order to its customer and on to the email.
4. `update` marks the order paid.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java:pay}}
```

`EMAIL_TEXT` is the Iso from [For with Optics](for_optics.md#optic-steps-in-a-for-chain):

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:email_text}}
```

`ORDER` is a new order of a lamp and four bulbs, its email typed with capitals. `SHIPPED` has shipped, and `BACKORDERED` asks for a chair:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java:run_pay}}
```

## Gathering, then naming {#gathering-then-naming}

A receipt needs an order, a verified email and a total, and the first two come from steps with an effect. `For` gathers them, and `toState` names them in a `Checkout`, with `total` summing each line's price times its quantity. `@GenerateLenses` generates the checkout's lenses as `CheckoutLenses`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/Checkout.java:checkout}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java:receipt}}
```

`zoom` narrows the chain to the order inside the checkout, so the `update` after it takes a lens on `Order`, and `endZoom` widens it back to the whole `Checkout`. With `Either`'s instance, a `Left` from `verify` stops the chain before `toState` builds anything:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/forstate/ForStateBook.java:run_receipt}}
```

## Every step {#every-step}

| Step | What it does | Built on |
|---|---|---|
| `update(lens, value)` | Sets a field | `map` |
| `modify(lens, f)` | Runs `f` on a field | `map` |
| `from(f)` | Runs an effect, and keeps the record as it was | `flatMap` |
| `fromThen(f, lens)` | Runs an effect, and stores its value through the lens | `flatMap` |
| `when(test)` | Gives the instance's empty value when `test` fails | `MonadZero`'s `filter` |
| `matchThen(source, prism, target)` | Reads the `source` field, matches it with the prism, and stores the case through `target`; gives the empty value on a miss | `flatMap` |
| `matchThen(extract, target)` | The same, with a function that gives an `Optional` | `flatMap` |
| `traverse(lens, traversal, f)` | Runs `f`, which has an effect, on every element of a collection field, and stores the results | the traversal's `modifyF`, which runs `f` on each element inside the instance |
| `traverseOver(traversal, f)` | The same, where the record is itself the collection | the traversal's `modifyF` |
| `modifyThrough(traversal, f)` | Runs a plain `f` on every element the traversal reaches; a second form takes a lens into each element | `map` |
| `modifyVia(lens, iso, f)` | Runs `f` on the field in the Iso's other form, as `modify(lens.andThen(iso), f)` would | `map` |
| `updateVia(lens, iso, value)` | Sets the field to the Iso's `reverseGet` of `value` | `map` |
| `zoom(optic)` … `endZoom()` | Between the two, `update`, `modify` and `fromThen` take lenses on the part the optic reaches | the optic's `get` and `set` |
| `yield()` or `yield(f)` | Ends the chain with the record, or with `f` of it | Nothing, or `map` |

`when` and both `matchThen` steps are there only on a chain that started from a `MonadZero`. `zoom` takes a `Lens`, a `FocusPath` (the lens path `@GenerateFocus` writes) or an `Iso`. On a chain started from a `MonadZero` it also takes an `AffinePath`, a path to a part that may be absent, and gives the empty value when it is.

`For` has a `traverse` step too, which takes a `Traverse` instance and binds the collected results as a new value, as [Traversing inside a chain](for_comprehension.md#traversing-inside-a-chain) shows. `ForState`'s takes a lens and a traversal optic, and writes the results back into the record.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`ForState` is the State monad."** It carries a record through any `Monad`'s `map` and `flatMap`, `Either`'s included. For the State effect, see `MonadState` in [For with MTL Capabilities](for_mtl.md#threading-state-with-monadstate).
- **"The steps run at `yield`."** Each step calls the instance's `map` or `flatMap` as you add it. Over `Maybe` or `Either`, a step's function runs then. Over `IO`, nothing runs until you run the result.
- **"`ForState` is better than `For` past three steps."** It needs a record and a lens for each field. For a short chain, `For`'s tuple is less to write, and [Which comprehension should you use?](for_comprehension.md#which-comprehension) helps you choose.
~~~

---

~~~admonish tip title="See Also"
- [For Comprehensions](for_comprehension.md): the chain `toState` starts from
- [For with Optics](for_optics.md): `focus`, `through` and `match` in a `For` chain, and `ForTraversal`
- [Lenses](../optics/lenses.md) and [Isomorphisms](../optics/iso.md): the optics the steps take
~~~

~~~admonish info title="Hands-On Learning"
Practise `ForState` in the [ForState journey](../tutorials/expression/forstate_journey.md) (13 exercises), and its traversal and Iso steps, with `For`'s `through`, in [Tutorial 04: Enhanced Optics Integration](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/expression/Tutorial04_EnhancedOpticsIntegration.java) (10 exercises).
~~~

---

**Previous:** [MTL & ForState Bridge](for_mtl.md)
**Next:** [Core Types](../monads/ch_intro.md)

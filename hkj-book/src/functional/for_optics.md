<!-- description: Read a field, convert a value or match a case as a step in a For comprehension, and edit every element an optic reaches with ForTraversal and ForIndexed. -->

# For with Optics

_Use lenses, Isos and prisms as `For` steps, and edit every element a traversal reaches._

<!-- source: William Blake, "Auguries of Innocence" (c. 1803), in Poems of William Blake, edited by Alice Meynell (1911); text checked against Project Gutenberg eBook #79363 -->
> _"To see the world in a grain of sand, / And a heaven in a wild flower; / Hold infinity in the palm of your hand, / And eternity in an hour."_
>
> — William Blake, *Auguries of Innocence*

Blake finds the whole world in one grain. An optic step goes the other way: from a whole record, it brings one part into the chain, as `focus` brings out a consignment's state.

~~~admonish info title="What You'll Learn"
- Add a field to a `For` chain with `focus`, a converted value with `through`, and a matched case with `match`
- Say what each optic step is short for, and which of them take an optic
- Edit every element an optic reaches with `ForTraversal`, and by position with `ForIndexed`
- Say what `ForTraversal`'s `filter` tests, and why its `Applicative` adds no effect
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ForOpticsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java)**: the page includes it, so the build compiles and runs it. [ForOpticsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBookTest.java) holds what the page says each step does.
~~~

---

## Optic steps in a `For` chain {#optic-steps-in-a-for-chain}

In plain Java you read a field with its accessor, convert a value with a method, and test for one case of a sealed type with `instanceof`. An optic does each of these as a value you can pass around. A `Lens` reads one field, an `Iso` converts a value to another type and back, and a `Prism` picks one case. `@GenerateLenses`, `@GeneratePrisms` and `@GenerateTraversals` write them for your records, as the [Optics](../optics/ch_intro.md) chapter explains.

On an [Effect Path](../effect/ch_intro.md), [`ForPath`](../effect/forpath_comprehension.md)'s `focus` takes a `FocusPath`, such as `ConsignmentFocus.state()`. A `ForPath` over a `MaybePath` or an `OptionalPath` has `match`, and so does one over a `NonDetPath` after its first step. This page is for `For`, over `Kind`.

| Step | In plain Java | Short for | Takes |
|---|---|---|---|
| `focus(lens)` | `consignment.state()` | `let(lens::get)` | A `Lens` straight after `For.from`, and a function of the tuple after that |
| `through(iso)` | `email.value()` | `let(iso::get)` | An `Iso`, straight after `For.from` only |
| `match(prism)` | `state instanceof ConsignmentState.Dispatched sent` | A `from` that gives the instance's `zero()` when the prism misses | A `Prism` straight after `For.from`, and a function of the tuple giving an `Optional` after that |

Each step adds the value it reads as the chain's next value. The three steps only read. `through` never calls the Iso's way back, so it adds a second value beside the first, as `let` would. `match` needs an empty value to give on a miss, so, like `when`, it is there only on a chain started from a [`MonadZero`](monad_zero.md), as [Filtering with `when`](for_comprehension.md#filtering-with-when) explains.

The code on this page uses these imports, with `EmailAddress`, `Order` and `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.higherkindedj.example.book.optics.cast.ConsignmentLenses;
import org.higherkindedj.example.book.optics.cast.ConsignmentStatePrisms;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderTraversals;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForIndexed;
import org.higherkindedj.hkt.expression.ForTraversal;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.indexed.Pair;
import org.higherkindedj.optics.util.IndexedTraversals;
import org.higherkindedj.optics.util.Traversals;
```

A consignment is the parcel an order ships in, with the address it goes to and the state it has reached. Its state is one case of a sealed interface:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/Consignment.java:consignment}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/cast/ConsignmentState.java:consignment_state}}
```

`CONSIGNMENTS` holds three: one dispatched to Leeds, one waiting to go to York, and one returned from Hull. The chain reads each one's state and keeps the dispatched one, and `List`'s instance drops the others:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:steps}}
```

`focus` comes straight after `For.from`, so it takes the lens. By `match`, the chain holds two values, so `match` takes a function of the tuple that says which value the prism reads: `t._2()` is the state `focus` added.

An `EmailAddress` holds one `String`, so an Iso converts between the two without loss, and `through` adds the text beside the address. `ORDER` is an order of one lamp at 40.00 and four bulbs at 2.50:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:email_text}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:through}}
```

## Editing every element with `ForTraversal` {#bulk-operations-with-fortraversal}

In plain Java, editing some of an order's lines means streaming the lines, changing the ones you want, and building a new order around the new list. `ForTraversal.over(traversal, source, applicative)` does it as a list of steps, over every element a [Traversal](../optics/traversals.md) reaches, such as `OrderTraversals.lines()`:

| Step | What it does |
|---|---|
| `filter(test)` | Makes the `modify` and `set` steps after it change only the elements that pass |
| `modify(lens, f)` | Runs `f` on the field the lens reads, in each element |
| `set(lens, value)` | Sets that field in each element |
| `run()` | Gives the edited whole, inside the instance |
| `toList()` | Gives the elements that pass every filter |

The examples price lines with these helpers:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:helpers}}
```

Here the lines with a quantity of two or more get ten per cent off, and `toList` names which lines those are:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:for_traversal}}
```

The chain runs over `Id`, the container that adds nothing, as [Identity](../monads/identity.md) explains, so `ID.unwrap` takes the order out. Every step takes a plain function, so no step has an effect to run, and the `Applicative` only wraps the result with its `of`. Over `List`'s instance, `run` gives a list of one order.

### What `filter` and `toList` read {#collecting-results}

`filter` tests each element as it was in the source, not as an earlier `modify` left it. A `modify` before the `filter` still changes every element, and a `filter` that follows it sees the values from before that change. `toList` reads the source too, so it lists the elements that pass the filters with none of the edits.

~~~admonish tip title="Plain optics win for one edit"
`Traversals.modify` runs one plain function through an optic, with no `Applicative` to pass and nothing to unwrap. `filtered` narrows the traversal to the lines that pass, and `andThen` goes on from each line to its price:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:plain}}
```

`ForTraversal` reads better when several edits share a filter.
~~~

## Editing by position with `ForIndexed` {#position-aware-traversals-with-forindexed}

`ForIndexed.overIndexed(indexedTraversal, source, applicative)` has the same steps, with each element's index beside it. `filterIndex` tests the index, and `filter` the index and the element together. `modify` passes the index to its function, `set` takes a function of the index, and `toIndexedList` gives each element that passes the filters with its index. `IndexedTraversals.forList()` counts from 0, so a packing slip adds 1:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:slip_line}}
```

The slip numbers every line. Then every second line goes half price: `filterIndex` keeps the odd indexes, and `modify`'s function takes the index beside the price:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foroptics/ForOpticsBook.java:for_indexed}}
```

`ForIndexed`'s steps are plain functions too, and its `filter` tests the source element, as `ForTraversal`'s does. [Indexed Optics](../optics/indexed_optics.md) explains the indexed traversals.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`through` keeps the two values in step."** It calls the Iso's `get` once, as `let(iso::get)` would. The way back never runs.
- **"Every chain has `match`."** Only a chain started from a `MonadZero` has it, since a miss gives the instance's empty value.
- **"`filter` tests what an earlier `modify` left."** It tests each element as it was in the source.
- **"`ForTraversal` runs an effect on each element."** Its steps are plain functions, and the `Applicative` only wraps the result.
~~~

---

~~~admonish tip title="See Also"
- [For Comprehensions](for_comprehension.md): the steps the optic steps join
- [ForState](forstate_comprehension.md): a chain whose values live in a record, changed through lenses, Isos and traversals
- [ForPath Comprehension](../effect/forpath_comprehension.md): `focus` and `match` on Effect Paths
- [Lenses](../optics/lenses.md), [Prisms](../optics/prisms.md), [Isomorphisms](../optics/iso.md) and [Traversals](../optics/traversals.md): the optics the steps take
~~~

---

**Previous:** [Profunctor](profunctor.md)
**Next:** [MTL & ForState Bridge](for_mtl.md)

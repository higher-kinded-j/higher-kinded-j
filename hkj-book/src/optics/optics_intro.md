# What a Path Is Made Of

_Every path wraps an optic: take it out, compose it with `andThen`, and know when you need it._

![A camera lens, close up against a dark background](../images/optics.jpg)

~~~admonish info title="What You'll Learn"
- Name the optic each path type wraps, and take it out
- Write a lens by hand, and see that it is an accessor and a copy
- Compose optics with `andThen`, and predict the type it returns
- Choose an optic by how many values it reaches and whether it writes
- Know when to hand over the optic rather than the path
~~~

~~~admonish example title="See Example Code"
**The code on this page is [PathPartsBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java) and its [PathPartsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/optics/parts/PathPartsBookTest.java)**: the page includes them, so the build compiles and runs them.
~~~

Nearly every update so far has been a path, such as `UserFocus.address().street().name()`. A path is a thin wrapper around an *optic*: an object that reads one or more parts of a structure, and rebuilds the structure with them replaced. Most code never opens the wrapper. This page opens it for the few jobs that need it. [When you need the optic](#when-you-need-the-optic) lists them: an API that takes an optic, a type the processor cannot reach, an optic with no path type, and a compiler message that names an optic type.

---

## Each path type wraps an optic {#each-path-type-wraps-an-optic}

Each of the [three path types](focus_dsl.md#the-three-path-types) wraps the optic of the same reach: a `FocusPath` a `Lens`, an `AffinePath` an `Affine`, and a `TraversalPath` a `Traversal`. Each hands its optic over with one call. The records are the Quickstart's, and an order with a sealed payment:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:records}}
```

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:order_records}}
```

Each path gives up its optic:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:to_optic}}
```

The optic reads and writes exactly what its path does: setting the street name through `streetName` gives the same `User` as setting it through `streetPath`. `FocusPath.of(lens)` goes the other way, and wraps an optic you already hold in a path.

---

## What a lens is {#what-a-lens-is}

A `Lens<S, A>` is the pair you would write by hand for one record component: the accessor, and a copy that replaces that component. `Lens.of` takes exactly those two functions:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:by_hand}}
```

`UserLenses.address()` is this lens, generated, and a generated lens class holds one per component, as the Quickstart's [What the processor wrote](quickstart.md#1-annotate-then-update) shows. A lens is a wither you can pass around, and it does what a wither cannot: compose. [Why a lens, when you have `@With`?](lenses.md#lens-or-wither) makes that case in full.

---

## Composing optics with `andThen` {#composing-optics-with-andthen}

`andThen` joins two optics end to end, as `.via(...)` joins a path to its next hop. The result reaches as many values as the two steps allow together. A lens then a lens is a `Lens`. A lens then a zero-or-one step is an `Affine`, even when that step is a `Prism`. Any of the five read-write optics followed by a `Traversal` is a `Traversal`. An `Iso` takes the type of whatever follows it:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:and_then}}
```

`.via(...)` on a path is `andThen` on the optic inside, so `OrderFocus.payment().via(PaymentPrisms.card())` and `OrderLenses.payment().andThen(PaymentPrisms.card())` find the same card. The composed `streetName` is the copy-and-rebuild cascade written once: a set through it rebuilds the `Street`, the `Address` and the `User`, and reuses every value off the path. [Composition Rules](composition_rules.md#composition-rules-table) gives the result type for every pair.

---

## Choosing an optic {#choosing-an-optic}

Two questions pick the optic: how many values it reaches, and whether you may write through it.

| | exactly one | zero or one | zero or more |
|---|---|---|---|
| **read and write** | [Lens](lenses.md), or an [Iso](iso.md) when it converts both ways | [Affine](affine.md), or a [Prism](prisms.md) when it picks a variant of a sealed type | [Traversal](traversals.md) |
| **read only** | [Getter](getters.md) | a [Fold](folds.md) that finds at most one | [Fold](folds.md) |
| **write only** | | | [Setter](setters.md) |

Each plays the part of a Java idiom you already write:

| Optic | Plays the part of | What it adds |
|---|---|---|
| Lens | an accessor and a `withX` copy, as one value | composes through nested records |
| Affine | an accessor that returns `Optional`, with a copy that writes the value | composes, and `modify` leaves an absent value alone |
| Prism | an `instanceof` pattern, and the variant's constructor | composes, and builds the variant back |
| Iso | a wrapper record's constructor and accessor, such as `new Sku(text)` and `sku.text()`, which lose nothing | composes, and turns around with `reverse()` |
| Traversal | `stream().map(f).toList()` over a list field, put back with a wither | does the rebuild for you, at any depth |
| Fold | a `Stream` over the same values, which only reads | composes, and says in its type that it never writes |
| Getter | a derived accessor, such as a `fullName()` computed from two fields | composes with other getters, and with any optic through `asFold()` |
| Setter | `stream().map(f).toList()` put back with a wither, with nothing read out first | composes, for a value you change without reading |

The [chapter introduction](ch_intro.md#how-the-optic-types-relate) draws how the eight types relate, and [Decision Trees](decision_trees.md#tree-1-which-optic-do-i-need) asks the same two questions as a tree.

---

## Using an optic directly {#using-an-optic-directly}

A raw optic carries the operations its type allows. A `Traversal` is the exception that surprises people: its reads and writes live in the `Traversals` utility rather than on the traversal itself, and it reads as a `Fold` through `asFold()`:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/parts/PathPartsBook.java:raw_use}}
```

For an order of quantities 1 and 2 paid by card, `doubled` holds 2 and 4, `totalQuantity` is 3, and `paidByCard` holds the card. A `TraversalPath` has `modifyAll` and `getAll` of its own, which is one reason to stay on the path. Mind the argument order, which differs by home: a path's methods take the source last, `path.modify(f, source)`; `Traversals.modify(traversal, f, source)` puts the optic first; and `OpticOps`, on the next page, puts the source first.

---

## When you need the optic, not the path {#when-you-need-the-optic}

Stay on the path by default. It has every read and write, its field names locate a validation error, and it can hand its value on to an [Effect Path](../effect/ch_intro.md). Take the optic out when one of these is true:

| When | What to do |
|---|---|
| An API takes an optic: `OpticOps`, which [Updates That Can Fail](fluent_api.md) uses, or hkj-test's `LensLaws`, which checks that a hand-written lens behaves | Hand it `path.toLens()`, `toAffine()` or `toTraversal()` |
| The processor cannot reach the type, because you cannot annotate the class | Write the lens with `Lens.of`, as in [What a lens is](#what-a-lens-is), and check it with `LensLaws`; or generate the optics with `@ImportOptics`, which [Optics for External Types](importing_optics.md) covers, and [when to write a lens by hand](lenses.md#use-manual-lens-creation-when) compares |
| The optic has no path type: an `Iso`, a `Getter`, a `Fold` or a `Setter` | Use it directly. An iso still joins a path through `.via(iso)`, and every path gives you its fold with `asFold()` |
| A compiler message names an optic type, such as `Affine<Order, Card>` | Read it as the optic inside the matching path type; the grid says what that optic can do, and [Compiler Errors](compiler_errors.md) lists the common messages |

---

~~~admonish info title="Key Takeaways"
* **A path is an optic with a friendlier API.** `FocusPath` wraps a `Lens`, `AffinePath` an `Affine` and `TraversalPath` a `Traversal`; `toLens()`, `toAffine()` and `toTraversal()` take it out.
* **A lens is an accessor and a copy.** `Lens.of(getter, setter)` is the whole definition, and the processor writes one per record component.
* **`andThen` is `.via()` for optics.** The result reaches as many values as both steps allow together, an `Iso` takes the type of what follows it, and Composition Rules tabulates every pair.
* **Two questions pick the optic.** How many values it reaches, and whether it may write.
* **Stay on the path until an API, a type or a message needs the optic.**
~~~

~~~admonish info title="Hands-On Learning"
- [Tutorial01_LensBasics.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial01_LensBasics.java) (7 exercises): lenses by hand and generated
- [Tutorial06_OpticsComposition.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial06_OpticsComposition.java) (7 exercises): composing different optic types
~~~

~~~admonish tip title="See Also"
- [The Optic Types](ch1_intro.md): each optic type in depth
- [Composition Rules](composition_rules.md): the type `andThen` returns for every pair
- [Optic Capabilities](optic_capabilities.md): which operations each optic type declares
- [Profunctor Optics](profunctor_optics.md): changing the types an optic works between
~~~

---

**Previous:** [Collections, Optionals and Sealed Types](focus_navigation.md)
**Next:** [Updates That Can Fail](fluent_api.md)

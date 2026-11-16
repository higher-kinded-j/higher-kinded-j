<!-- description: Read with code written to build, in Java with Const: one traversal of an order's lines rebuilds them in Id and adds them up in Const. -->

# Const: The Value That Map Leaves Alone

_A `Const<C, A>` holds a `C` and no `A`, so code written to build an `A` reads a `C` instead._

<!-- source: Verbal Kint, The Usual Suspects (1995), directed by Bryan Singer, screenplay by Christopher McQuarrie; wording checked against Wikiquote's The Usual Suspects page -->
> _"The greatest trick the Devil ever pulled was convincing the world he didn't exist."_
>
> — Verbal Kint, *The Usual Suspects*

Verbal Kint is talking about Keyser Söze, a name that runs through every story he tells. The `A` in `Const<C, A>` is that kind of name: it is in every signature, and no object holds it.

~~~admonish abstract title="Const at a glance"
| | |
|---|---|
| **Models** | A value of type `C`, carried through code that expects an `A` it never holds |
| **Replaces** | A second loop written beside a traversal, to sum, count or collect what it visits |
| **Effect Path** | None |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:const_type}}
| **Choose it when** | Code written for any `Applicative`, such as a traversal or a [Free Applicative](free_applicative.md) program, should read rather than build; otherwise [Id](identity.md), which rebuilds, or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Hold a value whose second type is only a type, which every map leaves alone
- Run one traversal two ways: in `Id` to rebuild an order's lines, and in `Const` to add them up
- Explain why `Const`'s `Applicative` needs a `Monoid`, and what it combines
- Recognise `Const` behind a traversal's `foldMap`, a lens's read and a Free Applicative program's analysis
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ConstBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java)**: the page includes it, so the build compiles and runs it. [ConstBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/constant/ConstBookTest.java) holds what the page says each method does.
~~~

~~~admonish tip title="Where Const fits"
You rarely build a `Const` yourself. A traversal's `foldMap`, `getAll` and `exists` run the traversal in a `Const` of their own, and `FreeApAnalyzer.analyseWith` runs a program in this one. This page shows the idea they share, for when you write code over any `Applicative` and want it to read.
~~~

---

## A value, and a type that holds nothing {#a-value-and-a-type-that-holds-nothing}

A `Const<C, A>` holds one value, of type `C`. Its `A` is a [phantom type](../glossary/type-system.md#phantom-type): it appears in the type and is stored nowhere. Mapping the `A` therefore has nothing to change. The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstApplicative;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.constant.ConstKind;
import org.higherkindedj.hkt.constant.ConstKind2;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java:held}}
```

`mapSecond` never calls its function, and the `Functor`'s `map` leaves the value as it is too.

## One traversal, two jobs {#one-traversal-two-jobs}

[Foldable and Traverse](../functional/foldable_and_traverse.md) wrote a traversal once and ran it with `Validated` and with `Either`. `eachLine` is written the same way, for any `Applicative`: it visits every line, and rebuilds the list inside `F`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java:each_line}}
```

Run in [`Id`](identity.md), it rebuilds the lines. The basket holds a lamp at 40.00 and four bulbs at 2.50 each, and `VAT` is 1.20:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java:rebuild}}
```

Run in `Const`, the same traversal builds nothing, as a `reduce` over the same loop would. Each visit gives a `Const` holding the line's total before VAT, and the `Applicative` adds the totals with `Monoids.bigDecimalAddition()`, a [Monoid](../functional/semigroup_and_monoid.md) that sums money exactly: 40.00 + 4 × 2.50. The list it would have rebuilt is only a type:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java:fold}}
```

## Why the Applicative needs a Monoid {#why-the-applicative-needs-a-monoid}

An `Applicative`'s `of` must give a value with nothing in it, and its `ap` must combine two. A `Const` has only its `C` to do that with, so `ConstApplicative` takes a `Monoid<C>`. `of` gives the monoid's empty value, so an empty basket comes to zero. `ap` combines the two sides' values, the left one first, so a list monoid collects what each line gives in the order of the lines. There is no `flatMap`: a next step would need the `A`, and there is none.

## Const behind folds, reads and analyses {#const-behind-folds-reads-and-analyses}

The library uses the same idea in two places:

- A [traversal's `foldMap`](../optics/folds.md#step-4-aggregation-with-foldmap-and-monoids), and the `getAll` and `exists` built on it, run the traversal in a constant functor. A Focus path's `foldMap` uses this `Const`; a plain `Traversal`'s fold uses the optics module's own copy of it, which behaves the same.
- [Free Applicative](free_applicative.md#reading-a-program-before-it-runs) analyses a program the same way, with an interpreter that gives each instruction's SKU in a `Const`.

The idea also explains a lens. A lens written once for any `Functor`, in the van Laarhoven style, reads its focus when it runs in `Const`, and sets it when it runs in `Id`. A lens's `modifyF` run in `Const` gives the focus and rebuilds nothing.

## A Const always holds a value {#a-const-always-holds-a-value}

`new Const<>(null)` throws `NullPointerException`, and so do `mapFirst` and `bimap` when their function returns `null`. A traversal's fold therefore fails at the element whose function returned `null`, rather than passing the `null` to its monoid. [Values, never null](../hkts/core-concepts.md#values-never-null) sets out the rule.

## Creating instances {#creating-instances}

`new Const<>(value)` holds a value, and an applicative's `of(anything)` gives one holding the monoid's empty value. `new ConstApplicative<>(monoid)` builds the `Applicative` for a `Monoid<C>`, since `Const` has no `Instances` entry: its instance depends on the monoid you choose.

## Methods {#methods}

| Method | What it does |
|---|---|
| `constant.value()` | Get the value held |
| `mapFirst(f)` | Change the value |
| `mapSecond(g)` | Change only the phantom type; `g` is never called |
| `bimap(f, g)` | Both at once; only `f` touches the value |
| `new ConstApplicative<>(monoid)` | The `Applicative` that combines values with the monoid |
| `ConstBifunctor.INSTANCE` | The `Bifunctor`, over `ConstKind2` |

## Working with Kind {#working-with-kind}

A `Const` is already its own `Kind` twice over. As a `Kind<ConstKind.Witness<C>, A>`, with the value's type fixed, it takes `ConstApplicative`; `CONST.widen` and `CONST.narrow` are casts. As a `Kind2<ConstKind2.Witness, C, A>` it takes `ConstBifunctor`, through `CONST.widen2` and `CONST.narrow2`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/constant/ConstBook.java:kind}}
```

## When to use `Const` {#when-to-use-const}

| You need | Use |
|---|---|
| To sum, count or collect what code written for any `Applicative` visits | `Const` with the matching monoid |
| To read through a traversal or a Focus path | Its `foldMap`, `getAll` or `exists`, which use `Const` for you |
| To read a Free Applicative program before it runs | `FreeApAnalyzer.analyseWith` |
| Two values that both hold data | A record |
| One value with no effect | [Id](identity.md) |

---

**Previous:** [Coyoneda](coyoneda.md)
**Next:** [Supported Types](supported-types.md)

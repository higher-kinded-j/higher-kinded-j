<!-- description: Fold any container to one value with foldMap and a monoid, and check every element with traverse, where the instance decides what a failure does. -->

# Foldable & Traverse: Reducing and Reshaping Structures

_Fold a container into one value, or run a check on each element and get one result._

<!-- source: Alfred, Lord Tennyson, "Ulysses", in Poems (1842), line 18; text checked against Project Gutenberg eBook #8601, The Early Poems of Alfred Lord Tennyson, ed. John Churton Collins -->
> _"I am a part of all that I have met;"_
>
> — Alfred, Lord Tennyson, *Ulysses*

A fold's result is a part of every element it met: `foldMap` turns an order's line items into one total.

~~~admonish info title="What You'll Learn"
- Fold any container to one value with `foldMap` and a [monoid](semigroup_and_monoid.md)
- Get a total, a check and a list from the same fold by swapping the monoid
- Run a check on every element with `traverse`, and get one result for the whole container
- Predict whether a traversal keeps the first failure or reports them all, from the instance you pass
- Say when `sequenceA` fits, and check a `Traverse` against its identity law
~~~

~~~admonish example title="See Example Code"
**The code on this page is [FoldableBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java)**: the page includes it, so the build compiles and runs it. [FoldableBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/foldable/FoldableBookTest.java) holds what the page says each instance does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the law.
~~~

---

## Foldable: one structure, many summaries {#foldable-one-structure-many-summaries}

Summing a list, joining strings and checking that every element passes a test are one walk with three answers. Each answer is a [monoid](semigroup_and_monoid.md): a way to combine two values, and an empty value to start from. `Foldable` names the walk, and its one method, `foldMap(monoid, f, fa)`, turns each element of `fa` into a monoid value with `f` and combines them. For a `List`, it is `stream().map(f).reduce(monoid.empty(), monoid::combine)`.

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces. `LINES` and `lineTotal` come from [Semigroup and Monoid](semigroup_and_monoid.md#starting-from-an-empty-value): `LINES` holds a lamp at 40.00 and four bulbs at 2.50, and `lineTotal` multiplies a line's price by its quantity.

<!-- verify -->
```java
import static org.higherkindedj.example.book.foundations.monoid.MonoidBook.LINES;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
```

On an [Effect Path](../effect/ch_intro.md), `ListPath` and `StreamPath` have `foldMap` themselves, and application code stays there:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:path}}
```

This page is for a method written once for any container.

### Same data, three summaries {#same-data-three-summaries}

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:summaries}}
```

The difference between the three is one argument: the monoid. `ListTraverse.INSTANCE` is `List`'s `Foldable`, and `Traverse` extends `Foldable`, so every type with a `Traverse` can be folded.

### One method for any container {#one-method-for-any-container}

`total` is written over `Foldable`: it takes the `Foldable` for `F`, and calls `foldMap` with the monoid for `BigDecimal` addition.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:generic}}
```

Run on a `List` and on a `Maybe`, it gives:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:instances}}
```

A `Maybe` holds at most one element, so its total is that line's, and an empty `Maybe` totals to the monoid's empty value.

---

## Traverse: effectful iteration {#traverse-effectful-iteration}

`Traverse` extends `Foldable` and `Functor` with `traverse(applicative, f, ta)`. It runs `f` on every element of `ta`, where each call returns a value in a second container, such as a `Validated`, which this page calls an effect. It combines the results with the `Applicative` you pass, into one effect holding a container of every value: a `Kind<T, A>` and a function `A -> Kind<G, B>` give a `Kind<G, Kind<T, B>>`. Once `f` has run on each element, the list holds `Validated`s, and `traverse` turns it inside out:

<pre class="hkj-ascii-diagram" role="img" aria-label="traverse turns a list of Validated values into one Validated holding a list. A list of Valid(1), Valid(2) and Valid(3) becomes Valid of the list 1, 2, 3. A list of Valid(1), Invalid(e1) and Valid(3) becomes Invalid(e1).">
   Before: List&lt; Validated&lt;E, A&gt; &gt;
           ┌──────────┬──────────┬──────────┐
           │ Valid(1) │ Valid(2) │ Valid(3) │
           └──────────┴──────────┴──────────┘

   After:  Validated&lt; E, List&lt;A&gt; &gt;
           Valid( [1, 2, 3] )

   With an error:
           ┌──────────┬─────────────┬──────────┐
           │ Valid(1) │ Invalid(e1) │ Valid(3) │
           └──────────┴─────────────┴──────────┘

   becomes:
           Invalid( e1 )
</pre>

Validating every entry in a form, fetching a record for every id or parsing every line of a file all want this. Written by hand, each needs a loop, a list to collect errors and a flag for whether anything failed. `traverse` is that loop.

### Checking every line of an order {#checking-every-line-of-an-order}

An order can ship only when every line asks for at least one unit. The check runs on one line:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:check}}
```

This order's chair and desk lines ask for nothing:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:zero}}
```

Traversing its lines runs the check on each. The instance decides what a failure does:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:traverse}}
```

`Validated`'s `Applicative` joins the errors of every failing line. `Either`'s keeps the first error and drops the rest, though the check still runs on every line. When every line passes, both give the whole list of lines. The [Applicative](applicative.md#what-each-instance-does) page says what each instance's combining does.

### `sequenceA` {#sequencea}

`sequenceA` is `traverse` with the identity function, for a container whose elements already are effects. A `List` of `Maybe`s becomes a `Maybe` of a `List`: a `Just` of every value when each is present, or `Nothing` when any is missing.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/foldable/FoldableBook.java:sequence}}
```

---

## Traverse inside a `For` comprehension {#traverse-inside-for-comprehensions}

`For` has `traverse`, `sequence` and `flatTraverse` steps, so a chain can check every element of a collection without leaving the comprehension. [Traversing inside a chain](for_comprehension.md#traversing-inside-a-chain) shows them, and [ForPath Traverse](../effect/forpath_traverse.md) the same steps on Effect Paths.

---

## The traverse laws {#the-traverse-laws}

`Foldable`'s one law ties it to `Traverse`: for a type with both, `foldMap` gives what `traverse` gives with [`Const`](../monads/const_type.md)'s applicative. `hkj-test` does not check it. `Traverse` keeps three laws.

~~~admonish note title="Laws"
- **Identity: traversing with an effect that does nothing changes nothing.** `traverse` with the [`Id`](../monads/identity.md) applicative and `Id::of` gives back `ta`, wrapped in an `Id`.
- **Naturality: converting the effect afterwards equals converting each step.** For an applicative transformation `t`, a [natural transformation](natural_transformation.md) that keeps `of` and `map2`, `t` applied to `traverse(f)` equals `traverse` of `f` followed by `t`.
- **Composition: two traversals equal one traversal through both effects.** Traversing with `f` and then with `g` equals one traversal with the two applicatives composed.

Two containers are equal when they hold equal values in the same shape. The identity law pins down the result's shape: it holds every element once, in its place.

`TraverseLaws`, in [`hkj-test`](../tooling/test_assertions.md#adding-the-dependency)'s `org.higherkindedj.hkt.laws` package, checks the identity law on any instance. Checking naturality and composition with `hkj-test` is not supported yet. `KindEquivalence.byEqualsAfter`, in `org.higherkindedj.hkt.assertions`, builds the equality from a function it applies to both sides before comparing them with `equals`. `ORDER` is any `Order`, and the test uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeTraverse;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:traverse_laws}}
```
~~~

---

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`Foldable` is `Stream.reduce` with extra steps."** It is the same idea, for any container with a `Foldable` instance: `List`, `Maybe`, `Either` or your own. `Stream.reduce` works on a `Stream`, and `foldMap` on whatever you pass it.
- **"`traverse` runs its steps at the same time."** It runs them in the container's order, and the `Applicative` decides how their results combine. `IO`'s runs each step after the one before. `Validated`'s joins every error, and `Either`'s keeps the first. For `VTask`s that run at once, use [`Par.traverse`](../monads/vtask_monad.md#parallel-composition-with-par).
- **"`sequenceA` and `traverse` do different things."** `sequenceA(applicative, ta)` is `traverse(applicative, x -> x, ta)`. They differ only in whether there is a function to run on the way.
- **"A `Monoid` and a `Foldable` are one type class."** They are two. `foldMap` pairs them: the `Foldable` walks the container, and the monoid combines what it finds.
~~~

---

~~~admonish tip title="See Also"
- [Semigroup and Monoid](semigroup_and_monoid.md): the monoids that `foldMap` combines with
- [Applicative](applicative.md): what each instance does when `traverse` combines its steps
- [Validated](../monads/validated_monad.md): the type that reports every error
- [Effect Path Cheatsheet](../cheatsheet.md): the fold methods on `ListPath`, `StreamPath` and `VStreamPath`
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**, [Java Stream reduce()](https://www.baeldung.com/java-stream-reduce): Java's own fold, the idea `Foldable` generalises
- **Mojang**, [DataFixerUpper](https://github.com/Mojang/DataFixerUpper): Minecraft's Java library, which uses traversals to migrate data between game versions
~~~

---

**Previous:** [Semigroup and Monoid](semigroup_and_monoid.md)
**Next:** [Natural Transformation](natural_transformation.md)

<!-- description: Convert one container into another with Natural, without looking at the value inside, check the naturality law, and see where interpreters use it. -->

# Natural Transformation

_Turn one container into another, such as a `Maybe` into a `List`, whatever value it holds._

<!-- source: Franz Kafka, Metamorphosis (1915), opening sentence, translated by David Wyllie; text checked against Project Gutenberg eBook #5200 -->
> _"One morning, when Gregor Samsa woke from troubled dreams, he found himself transformed in his bed into a horrible vermin."_
>
> — Franz Kafka, *Metamorphosis* (trans. David Wyllie)

Gregor wakes in a new shell with the same thoughts: `maybeToList` gives a line item a new container, and leaves the item as it was.

~~~admonish info title="What You'll Learn"
- Say what a natural transformation is, and how it differs from `map`
- Write one as a generic method and use it as a `Natural` through a method reference
- Chain transformations with `andThen`, and start a chain from `Natural.identity()`
- Check a transformation against the naturality law
- Find the interpreters and conversions in the library that take a `Natural`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [NaturalBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/natural/NaturalBook.java)**: the page includes it, so the build compiles and runs it. [NaturalBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/natural/NaturalBookTest.java) holds what the page says each transformation does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## The container changes, never the cargo {#the-container-changes-never-the-cargo}

A natural transformation turns every `F<A>` into a `G<A>`, for any `A`, without looking at the value inside. It changes the container, never the cargo. [Functor](functor.md)'s `map` does the opposite: it changes the value and keeps the container.

You have called some already. `optional.stream()` turns an `Optional` into a `Stream`, and `list.stream().findFirst()` turns a `List` into an `Optional`. The library adds `maybe.toOptional()`, `Try.toEither(mapper)` and, on an [Effect Path](../effect/ch_intro.md), `MaybePath.toEitherPath(error)`. Application code stays with methods like these.

This page writes three over the `Kind` encoding, as an interpreter needs:

| From | To | The rule |
|---|---|---|
| `Maybe<A>` | `List<A>` | `Nothing` becomes `[]`, and `Just(x)` becomes `[x]` |
| `List<A>` | `Maybe<A>` | Keep the first element, if there is one |
| `Either<E, A>` | `Maybe<A>` | `Left` becomes `Nothing`, and `Right(x)` becomes `Just(x)` |

Each rule is about the shape of its input, and none needs to know what the input holds.

## Write one as a generic method {#writing-one}

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

`Natural<F, G>` has one abstract method, `<A> Kind<G, A> apply(Kind<F, A> fa)`. Its `<A>` is on the method, not the interface, so one `Natural` works for every element type. That also means a lambda cannot implement it, since a Java lambda cannot be generic:

<!-- verify:rejects "invalid functional descriptor" -->
```java
Natural<MaybeKind.Witness, ListKind.Witness> toList = fa -> NaturalBook.maybeToList(fa);
```

javac refuses it with "invalid functional descriptor for lambda expression". Write each conversion as a generic method:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/natural/NaturalBook.java:transformations}}
```

A method reference makes one a `Natural`. `LAMP` is a lamp line item: `Just(LAMP)` becomes a one-element list, and `Nothing` an empty one.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/natural/NaturalBook.java:apply}}
```

## Chaining {#chaining}

`andThen` runs one transformation and then another, and `compose` runs them the other way round. `Natural.identity()` returns its input unchanged, so it can start a chain built in a loop. Here a `Right` becomes a one-element list, and a `Left` an empty one, its error dropped:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/natural/NaturalBook.java:compose}}
```

## The naturality law {#the-naturality-law}

One rule makes a transformation natural: for any function `f`, mapping `f` and then converting gives what converting and then mapping gives.

<pre class="hkj-ascii-diagram" role="img" aria-label="The naturality square. From F of A, mapping f then applying nat reaches G of B, and so does applying nat then mapping f. Both routes give the same result.">
            map(f)
   F&lt;A&gt;  ──────────▶  F&lt;B&gt;
    │                   │
   nat                 nat
    ▼                   ▼
   G&lt;A&gt;  ──────────▶  G&lt;B&gt;
            map(f)
</pre>

Both routes to the bottom-right corner give the same answer. That is what lets you move a conversion before or after a `map` without changing the result.

~~~admonish note title="Laws"
- **Naturality: converting and mapping can swap places.** `nat.apply(map(f, fa))` equals `map(f, nat.apply(fa))`, for every function `f` and value `fa`.
- **Identity: the identity changes nothing, on either side.** `Natural.identity().andThen(nat).apply(fa)` equals `nat.apply(fa)`, and so does `nat.andThen(Natural.identity()).apply(fa)`.
- **Associativity: grouping a chain differently changes nothing.** `f.andThen(g).andThen(h).apply(fa)` equals `f.andThen(g.andThen(h)).apply(fa)`.

The laws compare what each side gives for the same input. Two `Natural`s are different objects, so `equals` between them says nothing. The laws assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one. A transformation that looks at the value inside, such as one that treats an `Integer` differently from a `String`, breaks the naturality law, and is a function of another kind.

Checking these laws with `hkj-test` is not supported yet, so the test checks them by hand, for `maybeToList` and `firstOf`. It uses these imports, with JUnit's, and `ORDER` is any `Order`:

<!-- verify -->
```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.natural.NaturalBook;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:natural_laws}}
```
~~~

## Where the library takes one {#where-the-library-takes-one}

A [Free](../monads/free_monad.md) program holds its steps, its instructions, as data. An interpreter runs it by turning each instruction into a real effect, and that turning is a `Natural`.

- **A Free program's interpreter is a `Natural`.** `program.foldMap(interpreter, monad)` runs a program by converting each instruction with a `Natural<F, M>`, and a `FreeAp` program's `foldMap` does the same with an applicative. A test interpreter is another `Natural` for the same program, and `FreePath.foldMap` takes one too.
- **`Free.translate(program, nat, functor)` moves a program from one instruction set to another.** [EitherF](../monads/eitherf.md#freetranslate-lifting-between-effect-types) shows it.
- **`Interpreters.combine` joins two interpreters into one.** A program over two instruction sets needs it, as [EitherF](../monads/eitherf.md) shows.
- **`EffectBoundary.of` takes the interpreter to `IO` that runs a program at the edge.**
- **A transformer's `mapT` takes `nat::apply`.** `EitherT`, `MaybeT`, `OptionalT`, `ReaderT`, `StateT` and `WriterT` each have one. It changes the outer container, for example from `IO` to `VTask`, and leaves the inner value alone.

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"A `Natural` is a lambda."** Its `apply` is generic, and a Java lambda cannot be. Write a generic method, and pass it as a method reference.
- **"I can branch on `A`."** Not in a natural transformation. A body that inspects the value, with `instanceof` for example, compiles, but it breaks the naturality law.
- **"A natural transformation keeps every value."** It keeps the values it keeps unchanged, but it may drop some: `firstOf` keeps only the first element, and `eitherToMaybe` drops the error. Both are natural.
- **"Naturality is theory I can ignore."** It is what lets you move a conversion past a `map`. An interpreter that breaks it gives a program a meaning that depends on where its steps were mapped.
~~~

---

~~~admonish tip title="See Also"
- [Functor](functor.md): the `map` that the naturality law is stated in
- [Free Monad](../monads/free_monad.md): programs that a `Natural` interprets
- [Free Applicative](../monads/free_applicative.md): independent programs that a `Natural` interprets
~~~

~~~admonish tip title="Further Reading"
- **Bartosz Milewski**, [Natural Transformations](https://bartoszmilewski.com/2015/04/07/natural-transformations/): an introduction with diagrams
~~~

~~~admonish info title="Hands-On Learning"
Practise natural transformations in [Tutorial 08: Natural Transformation](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial08_NaturalTransformation.java) (5 exercises).
~~~

---

**Previous:** [Foldable and Traverse](foldable_and_traverse.md)
**Next:** [Selective](selective.md)

<!-- description: Adapt what a function takes and gives with Profunctor's lmap, rmap and dimap, and see why lmap's function goes from the new input to the old one. -->

# Profunctor: Building Adaptable Data Pipelines

_Adapt what a function takes with `lmap`, what it gives with `rmap`, and both at once with `dimap`._

<!-- source: Lewis Carroll, Through the Looking-Glass, and What Alice Found There (1871), chapter V, "Wool and Water"; text checked against Project Gutenberg eBook #12 -->
> _"It's a poor sort of memory that only works backwards," the Queen remarked._
>
> — Lewis Carroll, *Through the Looking-Glass*

A profunctor's adapters point both ways. `rmap`'s goes forwards, from the old output to the new one, and `lmap`'s goes backwards, from the new input to the old one.

~~~admonish info title="What You'll Learn"
- Adapt a function's input with `lmap`, its output with `rmap`, and both with `dimap`
- Say why `lmap`'s function goes from the new input to the old one, which is what contravariant means
- Write the same adaptation with `Function`'s `compose` and `andThen`, and know which to reach for
- Say how Higher-Kinded-J's optics relate to `Profunctor`
- Check the two Profunctor laws by hand
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ProfunctorBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/profunctor/ProfunctorBook.java)**, which uses `lineTotal` from [MonoidBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/monoid/MonoidBook.java): the page includes it, so the build compiles and runs it. [ProfunctorBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/profunctor/ProfunctorBookTest.java) holds what the page says, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws. [ProfunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/profunctor/ProfunctorExample.java) adapts functions for an API, a validation pipeline and a data transformation.
~~~

---

## A function has two ends {#a-function-has-two-ends}

You adapt a `Function<A, B>` at both ends already. `andThen` runs a function after it, from `B` to the new output. `compose` runs one before it, from the new input to `A`. The second points backwards: the input type changes from `A` to the new one, but the function you supply goes from the new input to `A`. So a function's input side is contravariant and its output side covariant, as [variance in the glossary](../glossary/type-system.md#contravariant) explains. A [Bifunctor](bifunctor.md)'s two sides are both outputs.

For one function, `compose` and `andThen` are all you need, as [Plain Java does the same](#plain-java-does-the-same) shows. On an [Effect Path](../effect/ch_intro.md), a `ReaderPath` is a function from an environment: its `local` adapts the input, and its `map` the output. `Profunctor` names the two adaptations for any type with an input and an output:

<!-- verify -->
```java
public interface Profunctor<P extends WitnessArity<TypeArity.Binary>> {
  <A, B, C, D> Kind2<P, C, D> dimap(
      Function<? super C, ? extends A> f, Function<? super B, ? extends D> g, Kind2<P, A, B> pab);

  default <A, B, C> Kind2<P, C, B> lmap(Function<? super C, ? extends A> f, Kind2<P, A, B> pab) {
    return dimap(f, Function.identity(), pab);
  }

  default <A, B, C> Kind2<P, A, C> rmap(Function<? super B, ? extends C> g, Kind2<P, A, B> pab) {
    return dimap(Function.identity(), g, pab);
  }
}
```

`lmap` and `rmap` are `dimap` with `Function.identity()` on the end they leave alone. For a function, `dimap(f, g, p)` runs `f`, then `p`, then `g`. Higher-Kinded-J ships one instance, `FunctionProfunctor.INSTANCE`, for `Function`, and `Instances` has no entry for it.

## Adapting a function {#adapting-a-function}

The code on this page uses these imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.math.BigDecimal;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;
```

[Semigroup and Monoid](semigroup_and_monoid.md)'s `lineTotal` takes a `LineItem` and gives its price times its quantity. The warehouse's feed sends each line as text, and a card payment is taken in pence:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/profunctor/ProfunctorBook.java:adapters}}
```

`lmap` lets `lineTotal` take the feed's text, `rmap` gives its result in pence, and `dimap` does both:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/profunctor/ProfunctorBook.java:adapt}}
```

`FunctionProfunctor`'s `lmap`, `rmap` and `dimap` run nothing when you call them. Each builds a new function, and the adapters run every time that function is applied.

## Plain Java does the same {#plain-java-does-the-same}

For one function you hold, `Function`'s own `compose` and `andThen` give the same result, with no `Kind2` to widen or unwrap:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/profunctor/ProfunctorBook.java:plain}}
```

~~~admonish tip title="Plain Java wins here"
To adapt one `Function`, use `compose` and `andThen`. `Profunctor` earns its place only in code written for any type with an input and an output, such as a parser or an encoder of your own that you give an instance.
~~~

## Optics adapt the same way, but are not built on it {#optics-adapt-the-same-way}

Higher-Kinded-J's optics are van Laarhoven optics. Each is built on one method, `modifyF`, which runs a function on the part the optic focuses on inside a `Functor` or `Applicative`, and rebuilds the whole. An `Optic`'s `modifyF` takes an `Applicative`, and a `Lens` also has one that needs only a `Functor`.

`Optic` has its own `contramap`, `map` and `dimap`, which adapt an optic's source and result as `lmap`, `rmap` and `dimap` adapt a function. [Profunctor Optics](../optics/profunctor_optics.md) shows them: that page is named for these adapters, not for the way the optics are built. `Optic` is not a `Profunctor` instance.

## Two laws {#two-laws}

A `Profunctor` keeps two laws: adapting with identities changes nothing, and two adaptations in turn equal one.

~~~admonish note title="Laws"
- **Identity: adapting both ends with `a -> a` changes nothing.** `dimap(a -> a, b -> b, p)` equals `p`.
- **Composition: two dimaps equal one dimap of both pairs.** `dimap(f2, g2, dimap(f1, g1, p))` equals `dimap(f2.andThen(f1), g1.andThen(g2), p)`. The input adapters join in the opposite order to the output ones, since the outer `dimap`'s `f2` runs first.

Two functions are equal when they give equal results for every input. A test cannot try every input, so it tries a few. The laws assume functions that never return `null`.

Checking the `Profunctor` laws with `hkj-test` is not supported yet, so the test checks them by hand, on a few inputs. It uses these imports, with JUnit's:

<!-- verify -->
```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.foundations.profunctor.ProfunctorBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.func.FunctionKind;
import org.higherkindedj.hkt.func.FunctionProfunctor;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:profunctor_laws}}
```
~~~

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`lmap`'s function runs after the original."** It runs before, turning the input you have into the one the original takes.
- **"`Either` is a profunctor."** Both of its sides are results, so it is a [Bifunctor](bifunctor.md). A profunctor's first side is an input.
- **"Higher-Kinded-J's optics are profunctor optics."** They are van Laarhoven optics. `Optic.dimap` has the shape of `Profunctor`'s `dimap`, as a method on the optic.
- **"`dimap` runs its adapters when you call it."** `FunctionProfunctor`'s `dimap` builds a new function, and the adapters run each time that function is applied.
~~~

---

~~~admonish tip title="See Also"
- [Bifunctor](bifunctor.md): two type arguments, both of them results
- [Profunctor Optics](../optics/profunctor_optics.md): adapting an optic with `contramap`, `map` and `dimap`
- [Semigroup and Monoid](semigroup_and_monoid.md): where `lineTotal` comes from
- [Glossary](../glossary/type-system.md#variance-summary-table): covariant, contravariant and invariant, with Java examples
~~~

~~~admonish tip title="Further Reading"
- **Mojang**, [DataFixerUpper](https://github.com/Mojang/DataFixerUpper): Minecraft's Java library for migrating saved data, built on profunctor optics
- **Hablapps**, [Don't Fear the Profunctor Optics](https://github.com/hablapps/DontFearTheProfunctorOptics/blob/master/ProfunctorOptics.md): an introduction to profunctor optics, the encoding Higher-Kinded-J's optics do not use
~~~

---

**Previous:** [Bifunctor](bifunctor.md)
**Next:** [Optics Integration](for_optics.md)

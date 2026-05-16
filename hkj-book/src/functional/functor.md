# Functor: The "Mappable" Type Class

~~~admonish info title="What We'll Learn"
- How `Functor` formalises an operation we have already used a hundred times
- The two laws every honest `Functor` obeys, and why the laws are easier than they look
- How a single `map` method dispatches to `Optional`, `List`, `Either`, `IO`, `MaybePath`, and friends
- Where `Functor` shows up inside the Foundations one-liner
~~~

~~~admonish example title="Hands-On Practice"
[Tutorial02_FunctorMapping.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial02_FunctorMapping.java)
~~~

We have all written this code:

```java
optional.map(String::length);
list.stream().map(String::length).toList();
completableFuture.thenApply(String::length);
maybePath.map(String::length);
```

Four method calls, four containers, the same shape. `Functor` is the name of that shape. If we can write `map` for some container `F` such that the laws below hold, then `F` *is* a `Functor`. That is the entire idea, and most of the type classes in this chapter follow the same pattern: they name a capability we already use without naming.

---

## What `Functor` Lets Us Do

A `Functor` is a container `F` that supports a single operation:

> Given a function `A -> B` and an `F<A>`, produce an `F<B>` by applying the function inside the container.

The container's *shape* never changes. A `List` of three elements maps to a `List` of three elements. An `Optional.empty()` maps to an `Optional.empty()`. A failed `Try` maps to the same failed `Try`. The function only ever sees the values, never the container.

~~~admonish note title="Interface Signature"
```java
public interface Functor<F extends WitnessArity<TypeArity.Unary>> {
  <A, B> @NonNull Kind<F, B> map(
      Function<? super A, ? extends B> f,
      Kind<F, A> fa);
}
```

`F` is a witness type standing in for the container. `Kind<F, A>` is "some container `F` holding an `A`". The `Functor<F>` instance is the implementation that knows how to map inside that specific container.
~~~

---

## Two Laws That Keep Us Honest

A `Functor` instance has to obey two rules, and once we have read them we will realise we have always assumed them anyway.

**1. Identity.** Mapping with the identity function changes nothing.

```java
functor.map(x -> x, fa).equals(fa);
```

**2. Composition.** Mapping with `f` and then with `g` is the same as mapping once with `g.compose(f)`.

```java
functor.map(g, functor.map(f, fa))
    .equals(functor.map(g.compose(f), fa));
```

Together, these rules say *map only transforms the values; it never tampers with the container*. Anything that violated them would surprise us in unpleasant ways: a `Functor` that quietly dropped elements during identity mapping, or that produced different results depending on whether we composed before or after mapping. We are unlikely to write one by accident, but the laws are worth keeping in mind when defining instances for our own types.

---

## A Single `map`, Five Different Containers

The reason `Functor` earns its keep in Higher-Kinded-J is that we can write code once and dispatch to whichever container the caller hands us.

```java
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

Function<String, Integer> stringLength = String::length;

Functor<OptionalKind.Witness> optFunctor = OptionalFunctor.INSTANCE;
Functor<ListKind.Witness>     listFunctor = ListFunctor.INSTANCE;

Kind<OptionalKind.Witness, String> presentName = OPTIONAL.widen(Optional.of("Hello"));
Kind<OptionalKind.Witness, String> absentName  = OPTIONAL.widen(Optional.empty());
Kind<ListKind.Witness, String>     names       = LIST.widen(List.of("one", "two", "three"));

Kind<OptionalKind.Witness, Integer> presentLength = optFunctor.map(stringLength, presentName);
Kind<OptionalKind.Witness, Integer> absentLength  = optFunctor.map(stringLength, absentName);
Kind<ListKind.Witness, Integer>     nameLengths   = listFunctor.map(stringLength, names);

OPTIONAL.narrow(presentLength); // Optional[5]
OPTIONAL.narrow(absentLength);  // Optional.empty
LIST.narrow(nameLengths);       // [3, 3, 5]
```

Same function, three different `Kind` flows, and not a single conditional. The container takes care of "what does it mean to map an empty thing", because that is what `OptionalFunctor` is *for*.

---

## Generic Code, For Free

Once we accept `Functor<F>` as currency, we can write helpers that work for any container at all.

```java
public static <F extends WitnessArity<TypeArity.Unary>, A>
    Kind<F, String> describe(Functor<F> functor, Kind<F, A> fa) {
  return functor.map(a -> "value = " + a, fa);
}
```

`describe(optionalFunctor, presentName)` returns `Optional[value = Hello]`. `describe(listFunctor, names)` returns `["value = one", "value = two", "value = three"]`. The body of `describe` does not know, and does not need to know, which container it is operating on. That is the payoff.

---

## Back to the One-Liner

Recall the line from [One Line, Six Layers](../hkts/one_line_six_layers.md):

```java
repo.find(id)
    .toEitherPath()
    .focus().attributes().at(key)
    .modify(spec::validateAndCoerce)   // <-- Functor at work
    .flatMap(repo::save);
```

`.modify(...)` runs a `Functor` `map` inside the optic, against whatever container the path is currently carrying. When we are on the right rail of an `EitherPath`, `EitherFunctor.map` rewrites the focused attribute and leaves the rest of the record alone. When we are on the left rail, the same `map` is a no-op, because the `Functor` for `Either` knows that mapping a `Left` returns the same `Left`.

Two different runtime behaviours, one method name, no `if` in sight. That is `Functor` doing its job.

---

## Things People Get Wrong

~~~admonish warning title="Common Misunderstandings"
- **"Mapping iterates."** Not in general. `map` on `Optional` runs the function at most once. `map` on `Either` runs it only on the right rail. The "for each element" intuition only fits collections.
- **"`map` runs the function eagerly."** Often, but not always. `LazyFunctor.map` defers; `IOFunctor.map` builds an unevaluated `IO` action. `Functor` says nothing about *when* the function runs, only that it eventually does and that the container's shape is preserved.
- **"It's just `Stream.map` with extra steps."** The point of the abstraction is not to be cleverer than `Stream.map`; it is to let one piece of code talk about *all* mappable containers at once. We pay a little ceremony at the boundary so that the body of a generic function can be written exactly once.
- **"Then I can call `.map` on a `Kind` directly."** No: `Kind` is a marker interface and has no methods. We always go through a `Functor<F>` instance, which is the thing that actually knows how to map inside `F`.
~~~

---

~~~admonish info title="Key Takeaways"
* `Functor` formalises the "anything with a sensible `map`" pattern that already lives in `Optional`, `List`, `CompletableFuture`, and most of the JDK
* The two laws (identity, composition) say "map transforms values, never the container"
* A `Functor<F>` instance lets us write generic code that works for every supported container without conditionals
* It is the first rung of the type-class ladder; everything else in this section either extends it or stands beside it
~~~

~~~admonish tip title="See Also"
- [Applicative](applicative.md) - The next step up: combining independent computations
- [Monad](monad.md) - Sequencing dependent computations with `flatMap`
- [Bifunctor](bifunctor.md) - Mapping over types with two parameters
- [One Line, Six Layers](../hkts/one_line_six_layers.md) - Where this fits in the wider Foundations picture
~~~

~~~admonish tip title="Further Reading"
- **Scott Logic**: [Functors and Monads with Java and Scala](https://blog.scottlogic.com/2025/03/31/functors-monads-with-java-and-scala.html) - Practical guide to functors and monads in Java
- **Bartosz Milewski**: [Functors](https://bartoszmilewski.com/2015/01/20/functors/) - Comprehensive explanation of functors from category theory to code
- **Mark Seemann**: [Functors](https://blog.ploeh.dk/2018/03/22/functors/) - A practical introduction with examples in Java-adjacent languages
~~~

~~~admonish info title="Hands-On Learning"
Practice Functor mapping in [Tutorial 02: Functor Mapping](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/coretypes/Tutorial02_FunctorMapping.java) (6 exercises, ~8 minutes).
~~~

---

**Previous:** [Obtaining Instances](instances_facade.md)
**Next:** [Applicative](applicative.md)

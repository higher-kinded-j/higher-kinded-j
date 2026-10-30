<!-- description: Fall back from one container to another with Alternative's orElse, which Maybe reads as a fallback and List as joining, and check the Alternative laws. -->

# Alternative: Choosing Between Containers

_Try one value and fall back to another, with `empty` for a value that is not there._

~~~admonish info title="What You'll Learn"
- Fall back from one `Maybe` to another with `orElse`, building the fallback only when it is needed
- Predict what `orElse` does for each instance: fall back for `Maybe` and `Optional`, join for `List`
- Try any number of sources with `orElseAll`
- Turn a test into a value with `guard`
- Check an instance against the Alternative laws
~~~

~~~admonish example title="See Example Code"
**The code on this page is [AlternativeBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java)**: the page includes it, so the build compiles and runs it. [AlternativeBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBookTest.java) holds what the page says each instance does, and [FoundationsLawsBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java) checks the laws.
~~~

---

## Trying a fallback {#trying-a-fallback}

`Alternative` extends [Applicative](applicative.md) with two methods. `empty()` gives a container with nothing in it, and `orElse(fa, fb)` picks between two containers. `fb` is a `Supplier`, so an instance can build the fallback only when it needs it. If you have written `optional.or(() -> other)`, you have used one.

On an [Effect Path](../effect/ch_intro.md), `MaybePath.orElse` falls back in the same way, and application code stays there. This page uses the `Kind` encoding, which lets one method serve any container.

The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

`Instances.alternative(maybe())` gives `Maybe`'s instance. A SKU's price comes from today's offers if there is one, and otherwise from the catalogue:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:sources}}
```

`Maybe`'s `orElse` gives the first `Just`, and asks for the fallback only when the first is `Nothing`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:fallback}}
```

## One method, two meanings {#one-method-two-meanings}

`List`'s `orElse` does not fall back: it joins the two lists, and so it always builds the second.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:join}}
```

| Type | `empty()` | `orElse(fa, fb)` | Instance |
|---|---|---|---|
| `Maybe` | `Nothing` | `fa` if it is a `Just`, otherwise `fb` | `Instances.alternative(maybe())` |
| `Optional` | `Optional.empty()` | `fa` if it is present, otherwise `fb` | `Instances.alternative(optional())` |
| `List` | `[]` | `fa`'s elements, then `fb`'s | `Instances.alternative(list())` |
| `Stream` | An empty stream | `fa`'s elements, then `fb`'s, lazily | `Instances.alternative(stream())` |
| `VStream` | An empty stream | `fa`'s elements, then `fb`'s, lazily | `VStreamAlternative.INSTANCE`, since `Instances.alternative(vstream())` holds a plain monad |

`MaybeMonad`, `OptionalMonad`, `ListMonad` and `StreamMonad` are each a [MonadZero](monad_zero.md), which extends both `Monad` and `Alternative`.

## Many sources {#many-sources}

`orElseAll(fa, fb, more...)` tries any number of sources in order, and stops at the first present value for `Maybe`. Each source after the first is a `Supplier`, so a source after that value is never built.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:all}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:all_run}}
```

A call to this form draws javac's unchecked warning for a generic varargs array, since Java allows no `@SafeVarargs` on an interface's default method. Suppress it on the method that makes the call, as the example does. `orElseAll(Iterable)` takes a list built at run time, with no warning. Its sources are already built, and it walks the whole list, so it never ends on an infinite one. For `Maybe` it gives the first present source, and `List`, `Stream` and `VStream` join them all.

## Turning a test into a value {#turning-a-test-into-a-value}

`guard(test)` gives `of(Unit)` when the test holds and `empty()` when it fails. `()` is how `Unit` prints:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/alternative/AlternativeBook.java:guard}}
```

A monad with a zero can continue from it, so a failed test ends the chain. [MonadZero](monad_zero.md)'s `filter` and a `For` comprehension's `when` work this way.

## Laws {#laws}

An `Alternative` keeps five laws. The first three make `orElse` and `empty` behave like joining strings and the empty string, and the last two make `empty` absorb `ap`.

~~~admonish note title="Laws"
- **Left identity: an empty first choice changes nothing.** `orElse(empty(), () -> fa)` equals `fa`.
- **Right identity: an empty fallback changes nothing.** `orElse(fa, () -> empty())` equals `fa`.
- **Associativity: grouping three choices differently changes nothing.** `orElse(orElse(fa, () -> fb), () -> fc)` equals `orElse(fa, () -> orElse(fb, () -> fc))`. The order of the choices still matters: for `List`, `orElse(fa, () -> fb)` and `orElse(fb, () -> fa)` give the elements in different orders.
- **Left absorption: an empty function gives empty.** `ap(empty(), fa)` equals `empty()`.
- **Right absorption: an empty value gives empty.** `ap(ff, empty())` equals `empty()`.

Two containers are equal when they hold equal values in the same shape. The laws assume functions that never return `null`, and [Values, never null](../hkts/core-concepts.md#values-never-null) says what each type does with one.

Checking these laws with `hkj-test` is not supported yet, so the test checks them by hand, for `Maybe` and `List`. It uses these imports, with JUnit's:

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
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.MaybeKind;
```

```java
{{#include ../../../hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/FoundationsLawsBookTest.java:alternative_laws}}
```
~~~

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`orElse` always falls back."** `Maybe`'s and `Optional`'s do. `List`'s, `Stream`'s and `VStream`'s join both containers.
- **"`orElse` picks whichever value is present."** It prefers the first. Two present prices give the offer, not the catalogue's.
- **"Every type with a `Monad` has an `Alternative`."** Only `Maybe`, `Optional`, `List`, `Stream` and `VStream` have one. `Instances.alternative(either())` compiles, and throws `ClassCastException` when it runs, as [what the compiler checks](instances_facade.md#what-the-compiler-checks) explains.
- **"This `orElse` is `Selective`'s."** [Selective](selective.md)'s `orElse` takes a list of `Choice` containers, and is a different method.
~~~

---

~~~admonish tip title="See Also"
- [MonadZero](monad_zero.md): the `Alternative` that is also a `Monad`, and filtering with its zero
- [Applicative](applicative.md): the type class `Alternative` extends
- [AlternativeConfigExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/alternative/AlternativeConfigExample.java): fallback chains for configuration, with both `orElseAll` forms
~~~

~~~admonish tip title="Further Reading"
- **Baeldung**, [Java Optional orElse Patterns](https://www.baeldung.com/java-optional-or-else-optional): Java's own fallbacks, the idea `Alternative` generalises
~~~

---

**Previous:** [Selective](selective.md)
**Next:** [MonadZero](monad_zero.md)

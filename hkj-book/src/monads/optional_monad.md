<!-- description: Use Java's Optional with Higher-Kinded-J: read Optional fields through OptionalPath, pass Optional to generic code, and choose between Optional and Maybe. -->

# Optional: Java's Optional in Generic Code

_Keep Java's own `Optional`, with an Effect Path and the same instances as `Maybe`._

~~~admonish abstract title="Optional at a glance"
| | |
|---|---|
| **Models** | A value, or empty, in Java's own type |
| **Replaces** | Nothing: it is Java's own type |
| **Effect Path** | [`OptionalPath`](../effect/path_optional.md) |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:optional_monad}}
| **Choose it when** | The code already gives you an `Optional`; otherwise [Maybe](maybe_monad.md), or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Read a record's `Optional` fields with a fallback, through an `OptionalPath`
- Pass an `Optional` to code written once for any container, through its `MonadError` instance
- Say what widening an `Optional` costs, and why `Maybe` pays nothing
- Choose between `Optional` and `Maybe`, and convert between them
~~~

~~~admonish example title="See Example Code"
**The code on this page is [OptionalBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/optional/OptionalBook.java)**: the page includes it, so the build compiles and runs it. [OptionalBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/optional/OptionalBookTest.java) holds what the page says each method does.
~~~

---

## Java's own type {#javas-own-type}

`java.util.Optional` is everywhere: repositories return it, configuration lookups return it, and records hold it in fields that may be empty. Higher-Kinded-J does not replace it. It gives `Optional` an Effect Path, `OptionalPath`, for application code, and a `MonadError` instance, so that a method written once for any container accepts an `Optional` too. [Maybe](maybe_monad.md) is the library's own type for the same job.

The code on this page uses these imports, with `CustomerProfile` and `EmailAddress` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import java.util.Optional;
import org.higherkindedj.example.book.optics.cast.CustomerProfile;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
```

## Optional fields on an `OptionalPath` {#optional-fields-on-an-optionalpath}

A `CustomerProfile` holds a `nickname` and a second email address, `altEmail`, each as an `Optional`. `Path.optional` takes one, `map` changes the value when there is one, and `getOrElse` reads it with a fallback:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/optional/OptionalBook.java:greeting}}
```

`ADA` has the nickname `"Countess"` and no second address, and `CHARLES` has a second address and no nickname:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/optional/OptionalBook.java:greeting_outcomes}}
```

`forAda` is `"Hello, Countess"`, and `forCharles` is `"Hello, Charles"`. `Optional`'s own `map` and `orElse` do as much for one field. The Path pays off when `via` chains it into another Path, or `toEitherPath` gives the absence a reason. `run()` gives the `Optional` back, and `toMaybePath()` turns the Path into a `MaybePath`.

## Methods {#methods}

`Optional`'s own methods are the JDK's. The library adds these:

| Method | What it does |
|---|---|
| `Path.optional(optional)`, `Path.present(a)`, `Path.absent()` | Build an `OptionalPath` |
| `map(f)`, `via(f)`, `filter(p)` | Change the value, chain a step, or keep the value only if it passes; a `map` function that returns `null` gives an empty Path |
| `getOrElse(a)`, `orElsePath(a)` | Read the value with a fallback, or keep the Path, filling an empty one with the fallback |
| `run()`, `isPresent()`, `isEmpty()` | Give the `Optional` back, or say which case it holds |
| `toMaybePath()`, `toEitherPath(error)` | Convert to a `MaybePath`, or to an `EitherPath` whose error says why |
| `Maybe.fromOptional(optional)`, `maybe.toOptional()` | Convert between `Optional` and `Maybe` |
| `OPTIONAL.widen(optional)`, `OPTIONAL.narrow(kind)` | Move an `Optional` in and out of `Kind` |

## Working with Kind {#working-with-kind}

Generic code, written once for any container, takes `Optional` through its `MonadError` instance, as [MonadError](../functional/monad_error.md) shows. Its error type is `Unit`: an empty `Optional` is the error, and it carries no detail. `Maybe`'s instance has the same shape, so one method serves both. These are the extra imports for writing the method and calling it:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.optional.OptionalKind;
```

`F` stands for any container, and its [`WitnessArity` bound](../hkts/core-concepts.md#the-witnessarity-bound) says the container holds one type:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/optional/OptionalBook.java:generic}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/optional/OptionalBook.java:kind}}
```

`Optional` is a JDK class, so it cannot implement `OptionalKind`. `OPTIONAL.widen` wraps it in a small holder object each time, and `narrow` unwraps it, where widening a `Maybe` allocates nothing. The instance's `of(null)` gives an empty `Optional`, and so does a `map` whose function returns `null`, as `Optional.map` does. `Optional` has instances of other type classes too, such as `Selective` and `Traverse`, each an object of its own, and *Optional at a glance*, at the head of the page, lists them.

## Should you use `Optional` or `Maybe`? {#when-to-use-optional-vs-maybe}

Both model a value that may be missing, treat `null` alike, and have the same instances. They differ in who owns the type, and so in what widening costs:

| You need | Use |
|---|---|
| To work with APIs and records that already use `Optional` | `Optional`, with `OptionalPath` or its instance |
| A value that may be missing, in code you control | [Maybe](maybe_monad.md): it is its own `Kind`, so widening allocates nothing, and `toEither` gives its absence a reason |
| Generic code that must accept an `Optional` from callers | `Optional`'s instance, widening with `OPTIONAL.widen` |
| To move between the two | `Maybe.fromOptional(optional)` and `maybe.toOptional()` |
| A missing value that must say why | [Either](either_monad.md), through `toEitherPath(error)` |

---

**Previous:** [Maybe](maybe_monad.md)
**Next:** [IO](io_monad.md)

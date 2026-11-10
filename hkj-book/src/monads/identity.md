<!-- description: Run code written for any monad with no effect in Java with Id: a step that cannot fail, a delivery charge tested with Id and run with IO, and a StateT over Id. -->

# Identity: The Monad That Adds Nothing

_An `Id` holds a value and adds nothing, so code written for any monad can run with no effect._

~~~admonish abstract title="Identity at a glance"
| | |
|---|---|
| **Models** | A value and nothing else: no failure, no absence, no delay |
| **Replaces** | A plain value, where code written for any monad needs one |
| **Effect Path** | [`IdPath`](../glossary/effect-paths.md#idpath), a step that cannot fail |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:identity}}
| **Choose it when** | You run code written for any monad with no effect, in a test or as a transformer's inner monad; otherwise a plain value, or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Hold a step that cannot fail on an `IdPath`, and join it to a chain that can
- Test code written for any monad by running it with `Id`
- Run a transformer over `Id`, and choose between it and the plain type
- Predict what `Id` does with `null`, which `IdPath` refuses
- Use `Id` in generic code through its `Monad` and `Traverse` instances
~~~

~~~admonish example title="See Example Code"
**The code on this page is [IdentityBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java)**: the page includes it, so the build compiles and runs it. [IdentityBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/identity/IdentityBookTest.java) holds what the page says each method does.
~~~

---

## The monad that adds nothing {#the-monad-that-adds-nothing}

Code written for any monad takes a `Monad<F>` and values of type `Kind<F, A>`, and never says which monad `F` is. Something must fill `F` when you want no effect at all, such as a test that checks only the arithmetic, or a transformer whose inner monad has nothing to do. `Id` fills it. An `Id<A>` holds one value, its `map` and `flatMap` apply the function to that value, and nothing else happens. On a Path it is an `IdPath`, a step that cannot fail.

The code on this page uses these imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

import java.math.BigDecimal;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
```

The basket and its lines are the ones the [State](state_monad.md#adding-to-a-basket) page fills: a lamp at 40.00, four bulbs at 2.50, a sofa at 450.00, and a limit of 100.00. The basket's total cannot fail, so it is an `IdPath`. `get()` gives the value, and `toEitherPath()` makes it the first step of a chain that can fail:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:path}}
```

`toMaybePath()` starts a chain that can be empty in the same way.

## Testing code written for any monad {#testing-code-written-for-any-monad}

This method adds a delivery charge to a total under 50.00. It is written for any monad, so the service can run it in [IO](io_monad.md) and a test in `Id`. Its bound, `F extends WitnessArity<TypeArity.Unary>`, says only that `F` holds one type, as [The WitnessArity bound](../hkts/core-concepts.md#the-witnessarity-bound) explains:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:delivery}}
```

In a test, `Id` runs it with nothing around the value, so the test reads the total with `ID.narrow(...).value()`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:delivery_test}}
```

The service passes `IO`, and the same method adds the charge when the `IO` runs:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:delivery_io}}
```

## A transformer over `Id` {#a-transformer-over-id}

A transformer takes the monad it wraps as a parameter: `StateT<S, F, A>` threads a state through steps in `F`. With `Id` as `F` it adds nothing beyond the state, so a `StateT<Basket, IdKind.Witness, Boolean>` runs as the State page's `State<Basket, Boolean>` does. Here is that page's basket step, written as a `StateT` over `Id`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:state_t}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/identity/IdentityBook.java:state_t_run}}
```

The library ships `State`, `Reader` and `Writer` as types of their own, which are shorter to write than a transformer over `Id`. Reach for the transformer when the code is written for any inner monad, and a test or a caller fixes that monad to `Id`.

## `Id` holds a null {#id-holds-a-null}

A traversal's `modify` runs through `Id`, so `Id` holds whatever an element holds. `Id.of(null)` holds the null, and the monad's `map` keeps a `null` that its function returns. `IdPath` refuses one: `Path.id(null)` throws `NullPointerException`, and so does `Path.idPath` given an `Id` that holds `null`. [Values, never null](../hkts/core-concepts.md#values-never-null) sets out the rule for each type.

## Creating instances {#creating-instances}

`Id.of(value)` wraps a value, and `Instances.monad(id()).of(value)` does the same through the instance. `Path.id(value)` starts an `IdPath`, and `Path.idPath(id)` turns an `Id` into one.

## Methods {#methods}

| Method | What it does |
|---|---|
| `id.value()` | Gives the value |
| `id.map(f)`, `id.flatMap(f)` | Apply `f` to the value; `flatMap`'s function returns an `Id` |
| `idPath.map(f)`, `via(f)`, `zipWith(other, f)` | The same on the Path; `via`'s function returns an `IdPath` |
| `idPath.get()`, `run()` | Give the value, or the `Id` |
| `idPath.toEitherPath()`, `toMaybePath()` | Start a chain that can fail, or be empty, with the value |

## Working with Kind {#working-with-kind}

Generic code takes `Id` through its `Monad`, from `Instances.monad(id())`, which is `IdMonad.instance()`. `IdTraverse.INSTANCE` traverses an `Id` with any `Applicative`, and is its `Foldable` too, and `IdSelective.instance()` is its `Selective`. `Id` implements its own `Kind`, so `Id.of(value)` needs no `widen`, and `ID.narrow` gives the `Id` back, as [Testing code written for any monad](#testing-code-written-for-any-monad) shows.

## When to use `Id` {#when-to-use-id}

| You need | Use |
|---|---|
| A test of code written for any monad, with nothing to set up | `Id` |
| A transformer's inner monad, when the code is written for any | `Id`, as in `StateT<S, IdKind.Witness, A>` |
| A state, an environment or a log threaded through steps | [State](state_monad.md), [Reader](reader_monad.md) or [Writer](writer_monad.md) |
| A step that cannot fail, in a chain of Paths | [`IdPath`](../glossary/effect-paths.md#idpath) |
| A value in ordinary code | The value itself: `Id` adds nothing |

~~~admonish info title="Hands-On Learning"
Run transformers over `Id` in [Tutorial 04: Polymorphic Capabilities](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial04_PolymorphicCapabilities.java) (14 exercises).
~~~

---

**Previous:** [Stream](stream_monad.md)
**Next:** [Trampoline](trampoline_monad.md)

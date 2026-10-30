<!-- description: Chain a capability's operations in a For comprehension: read settings with MonadReader, change a basket with MonadState and keep a log with MonadWriter. -->

# For with MTL Capabilities

_Write a chain once for any container that can read settings, carry state or keep a log._

~~~admonish info title="What You'll Learn"
- Pass a `MonadReader`, `MonadState` or `MonadWriter` to `For.from`, and use its operations as steps
- Write a method for any container with a capability, and run it over `ReaderT`, `StateT` or `WriterT`
- Tell `MonadState`'s state from the record `ForState` carries
~~~

~~~admonish example title="See Example Code"
**The code on this page is [ForMtlBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java)**, with `PricingConfig`, `Basket` and `AuditEntry` from the [Foundations examples](https://github.com/higher-kinded-j/higher-kinded-j/tree/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations): the page includes them, so the build compiles and runs them. [ForMtlBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBookTest.java) holds what the page says.
~~~

---

## A capability is a `Monad` {#a-capability-is-a-monad}

In plain Java, a method that needs the VAT rate takes it as a parameter, one that changes a basket gives back a new basket, and one that logs is handed a logger. A capability puts the same need in the type. `MonadReader<F, R>` reads an `R`, `MonadState<F, S>` reads and replaces an `S`, and `MonadWriter<F, W>` adds to a `W`. The interfaces follow Haskell's mtl, the monad transformer library, and each names one thing a container can do. [MTL Capabilities](../transformers/mtl_capabilities.md) explains why a method asks for a capability rather than a transformer.

On an [Effect Path](../effect/ch_intro.md), `ReaderPath`, `WithStatePath` and `WriterPath` give their own versions of these operations for one effect, such as `ReaderPath.asks(PricingConfig::vatRate)`, and application code stays there. This page is for a method written once for any container with the capability.

Each capability extends `Monad`, so `For.from` takes it, and its operations give the values the steps bind:

| Capability | Main operations | Provided by |
|---|---|---|
| [`MonadReader<F, R>`](../transformers/mtl_reader.md) | `ask()`, `asks(f)` and `local(f, ma)` | `ReaderTMonadReader`, for [`ReaderT`](../transformers/readert_transformer.md) |
| [`MonadState<F, S>`](../transformers/mtl_state.md) | `get()`, `put(s)`, `modify(f)` and `gets(f)` | `StateTMonadState`, for [`StateT`](../transformers/statet_transformer.md) |
| [`MonadWriter<F, W>`](../transformers/mtl_writer.md) | `tell(w)`, `listen(ma)` and `censor(f, ma)` | `WriterTMonad`, for [`WriterT`](../transformers/writert_transformer.md) |

`ask`, `asks`, `get` and `gets` give a value to bind, and `put`, `modify` and `tell` give `Unit`. `local`, `listen` and `censor` each take a whole computation, `ma`, and run it with a changed environment, beside its log, or with its log changed. Each capability's page lists the rest.

`StateTMonadState` takes the state type first and the container second, `StateTMonadState<Basket, IdKind.Witness>`, the other way round from `ReaderTMonadReader<IdKind.Witness, PricingConfig>` and `WriterTMonad`.

The code on this page uses these imports, with `Order` and `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.reader_t.ReaderTKindHelper.READER_T;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.foundations.Basket;
import org.higherkindedj.example.book.foundations.PricingConfig;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterTMonad;
```

## Reading settings with `MonadReader` {#reading-configuration-with-monadreader}

Pricing reads its settings from the environment:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/PricingConfig.java:pricing_config}}
```

`toPay` asks for the settings, works out the delivery charge from them, and adds VAT to the whole:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:reader}}
```

The caller picks the container. Here it is `ReaderT` over `Id`, the container that adds nothing, and the settings arrive when the result is run:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:run_reader}}
```

The same `toPay` runs unchanged over `ReaderT` on `CompletableFuture` in production, or on `Maybe`, since only the caller names the container.

## Changing a basket with `MonadState` {#threading-state-with-monadstate}

A basket gives back a new basket for each line added. `LAMP` is one lamp at 40.00, and `BULBS` four bulbs at 2.50:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/Basket.java:basket}}
```

Each step sees the basket the step before it left. `modify` gives `Unit`, so the next step's parameter is `_`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:state}}
```

`runStateT` starts from an empty basket, and gives the value with the basket the steps left. This state is the State effect itself: it lives in the container, and only `get`, `put`, `modify` and `gets` reach it.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:run_state}}
```

## Keeping a log with `MonadWriter` {#accumulating-output-with-monadwriter}

The audit log is a list of entries:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/AuditEntry.java:audit_entry}}
```

`ship` tells the log one entry for packing and one for shipping, and gives the order's new status:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:writer}}
```

`WriterTMonad` takes the log's `Monoid`, here `Monoids.list()`, which joins the entries in the order they were told. Running gives the value and the log as a `Pair`, for `ORDER`, a paid order:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/formtl/ForMtlBook.java:run_writer}}
```

## Things people get wrong {#things-people-get-wrong}

~~~admonish warning title="Common Misunderstandings"
- **"`MonadState` and `ForState` are the same thing."** `MonadState`'s state lives in the container. [`ForState`](forstate_comprehension.md) carries a record of your own through any `Monad`, `Either`'s included, and changes it through lenses.
- **"A chain can take only one capability."** `For.from` takes one, but a step can call another's operations over the same `F`. [Combining Capabilities](../transformers/mtl_combining.md) shows a method that takes two.
- **"`modify` gives the new state."** It gives `Unit`. Read the state with `get` or `gets` in a later step, as `addBoth` does.
~~~

---

~~~admonish tip title="See Also"
- [For Comprehensions](for_comprehension.md): the steps the capabilities' operations fill
- [MTL Capabilities](../transformers/mtl_capabilities.md), with [MonadReader](../transformers/mtl_reader.md), [MonadState](../transformers/mtl_state.md) and [MonadWriter](../transformers/mtl_writer.md): each capability in full
- [ForState](forstate_comprehension.md): a chain whose values live in a record with named fields
~~~

~~~admonish info title="Hands-On Learning"
Practise writing methods against the capabilities in [Tutorial 04: Polymorphic Capabilities (MTL)](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/transformers/Tutorial04_PolymorphicCapabilities.java) (14 exercises).
~~~

---

**Previous:** [For with Optics](for_optics.md)
**Next:** [ForState Comprehension](forstate_comprehension.md)

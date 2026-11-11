<!-- description: Use Java's Stream with Higher-Kinded-J: rerun a sequence on a StreamPath, pass an endless Stream to generic code, read it once, and number it with StreamOps. -->

# Stream: Java's Stream in Generic Code

_Keep Java's own `Stream`, with an Effect Path you can run more than once, and instances for generic code._

~~~admonish abstract title="Stream at a glance"
| | |
|---|---|
| **Models** | A sequence that makes each element as it is read, in Java's own type |
| **Replaces** | Nothing: it is Java's own type |
| **Effect Path** | [`StreamPath`](../glossary/effect-paths.md#streampath), which reads a finite stream into memory so it can run again, or [takes a supplier](#on-a-streampath) for an endless one |
{{#include ../../../hkj-examples/src/test/resources/golden/foundations-capabilities.md.golden:stream_monad}}
| **Choose it when** | A method you call already returns a `Stream`; otherwise [List](list_monad.md), which can be read again, or compare every type in [Choosing a Type](supported-types.md#choosing-a-type) |
~~~

~~~admonish info title="What You'll Learn"
- Run the same sequence more than once on a `StreamPath`, even an endless one
- Pass an endless `Stream` to code written once for any container, making only the elements you take
- Say what single use means for a `Kind` that holds a stream, and which operation reads it early
- Number and combine streams with `StreamOps`
- Fold a stream through `StreamTraverse`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [StreamBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java)**: the page includes it, so the build compiles and runs it. [StreamBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/stream/StreamBookTest.java) holds what the page says each method does.
~~~

---

## Java's own type {#javas-own-type}

`java.util.stream.Stream` makes each element only when something reads it, so a stream can run without end and still be read in part. Higher-Kinded-J does not replace it. It gives `Stream` an Effect Path, `StreamPath`, for application code, and a `MonadZero` instance, so that a method written once for any container accepts a stream too. A Java `Stream` can be read only once; a `StreamPath` can be run again.

The code on this page uses these imports:

<!-- verify -->
```java
import java.util.List;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
```

## On a `StreamPath` {#on-a-streampath}

`Path.streamIterate(seed, f)` builds a fresh stream each time the path runs. So `take` limits an endless one, and the same invoices come back on every run:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:path}}
```

`Path.stream(stream)` reads the whole stream when it is called, so that stream must be finite, and the path can then be run again. `StreamPath.generate(supplier)` and `StreamPath.fromSupplier(supplier)` build a fresh stream on each run, as `streamIterate` does. A `StreamPath`'s `zipWith` gives every combination of two paths' elements, as a [`NonDetPath`](list_monad.md#every-combination-or-position-by-position)'s does, and unlike `StreamOps.zip`. It reads both paths whole on each run, so `take` from an endless path first.

## Invoice numbers without end {#invoice-numbers-without-end}

Code written once for any container takes a stream widened to a `Kind<StreamKind.Witness, A>`. The [Foundations](../hkts/foundations_intro.md) introduction's "Which level am I working at?" tip says when you need that. The stream keeps its laziness through the instance's `map` and `flatMap`. These are the extra imports, with `LineItem` from the order records the [Optics](../optics/ch_intro.md) chapter introduces:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamOps;
import org.higherkindedj.hkt.stream.StreamTraverse;
```

Invoice numbers can come from a stream with no end:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:invoice_numbers}}
```

`map` adds the prefix to each number as it is read, and `StreamOps.take` stops after three:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:invoices}}
```

Nothing is made until `toList` reads the stream, and then only three invoice numbers are. `flatMap` keeps the laziness too, so it reads an endless stream only as far as the consumer needs.

`ap` is the exception. Each function in its first stream needs every value in the second, so `ap` reads the whole second stream when it is called, and with an endless second stream it never returns.

## A stream is read once {#a-stream-is-read-once}

`ORDER_LINES` holds the lines of two orders:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:order_lines}}
```

`flatMap` turns each order's lines into a stream of their SKUs, and joins those streams into one:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:lines}}
```

A Java `Stream` can be used only once, and a `Kind` that holds one holds that one stream. Passing `skus` to any further operation, `map` as much as `toList`, throws `IllegalStateException`: `stream has already been operated upon or closed`. So build a fresh stream for each use, keep the elements in a [List](list_monad.md), or use a [`StreamPath`](#on-a-streampath).

## Numbering and combining with `StreamOps` {#numbering-and-combining-with-streamops}

`StreamOps` gives the common operations over a widened stream. `zipWithIndex` pairs each element with its position, counting from 0, which numbers a pick list:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:numbered}}
```

`zip` pairs two streams element by element, and `concat` puts one after another. The Methods table lists the rest.

## Creating instances {#creating-instances}

`STREAM.widen(stream)` gives a `Kind` that holds a `Stream`, and `StreamOps.fromArray(...)`, `fromIterable(iterable)`, `range(from, to)` and `rangeClosed(from, to)` start one. The instance's `of(value)` gives a stream of one element, `of(null)` an empty stream, as `Optional`'s and `List`'s do, and `zero()` an empty stream. On a Path, `Path.stream(stream)`, `Path.streamFromList(list)`, `Path.streamPure(value)`, `Path.streamEmpty()` and `Path.streamIterate(seed, f)` start a `StreamPath`.

## Methods {#methods}

| Method | What it does |
|---|---|
| `streams.map(f, stream)`, `streams.flatMap(f, stream)` | Change each element, or join the stream `f` gives for each, as the stream is read |
| `streams.ap(functions, values)` | Applies every function to every value, reading every value at the call |
| `StreamOps.take(n, stream)`, `drop(n, stream)`, `filter(p, stream)` | Narrow the stream |
| `StreamOps.zip(a, b, f)`, `zipWithIndex(stream)`, `concat(a, b)` | Pair by position, number, or join streams |
| `StreamOps.toList(stream)`, `toSet(stream)`, `forEach(action, stream)` | Read the stream |
| `StreamOps.tap(action, stream)` | Runs `action` on each element as it passes |
| `streamPath.map(f)`, `via(f)`, `filter(p)`, `take(n)` | Build the next path |
| `streamPath.zipWith(other, f)` | Combines every element with every element of `other`, reading both whole |
| `streamPath.toList()`, `run()` | Give the elements as a `List`, or a fresh `Stream` |

## Working with Kind {#working-with-kind}

Generic code takes `Stream` through its [`MonadZero`](../functional/monad_zero.md) instance, from `Instances.monadZero(stream())`. `Stream` is a JDK type, so `STREAM.widen` wraps it in a small holder, and `STREAM.narrow` gives the stream back. The [`Alternative`](../functional/alternative.md) from `Instances.alternative(stream())` puts one stream after another with `orElse`. `StreamTraverse.INSTANCE` is its `Traverse`, and so its `Foldable`, and reads the stream to fold or traverse it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/stream/StreamBook.java:kind}}
```

The two orders hold six items: a lamp, four bulbs and a shade.

## When to use `Stream` {#when-to-use-stream}

| You need | Use |
|---|---|
| A Java `Stream` handed to code written over `Kind` | `Stream`, through `Instances.monadZero(stream())` |
| A sequence that is run more than once | A [`StreamPath`](#on-a-streampath), or a [List](list_monad.md) |
| Elements produced by I/O, on virtual threads | [VStream](vstream.md) |
| Every combination of several choices, held in memory | [List](list_monad.md) |
| One pass over a stream in one method | The stream's own methods: nothing else needs the `Kind` |

---

**Previous:** [List](list_monad.md)
**Next:** [Identity](identity.md)

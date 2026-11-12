<!-- description: Connect a VStream to other APIs: optics over its elements, a Flow.Publisher in or out, List and Stream conversions, Path.from, and answers without a VTask. -->

# Advanced Features: Connecting VStream to Other APIs

_Bridge a stream to optics, `Flow.Publisher`, `List`, `Stream` and Paths, or run it without a `VTask`._

~~~admonish info title="What You'll Learn"
- Change a field of every element in a list or a stream with a `StreamTraversal`, reading a stream only as far as the consumer pulls
- Hand a stream to a `Flow.Subscriber`, and read a `Flow.Publisher` as a stream
- Convert between `VStream`, `List` and Java's `Stream`, and move a Path between them with a `Natural`
- Find a Path for a `Kind` with `Path.from`, and register a provider of your own
- Get a stream's answer directly, with no `VTask` to run, through `VStreamContext`
~~~

~~~admonish example title="See Example Code"
**The code on this page is [VStreamAdvancedBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java)**: the page includes it, so the build compiles and runs it. [VStreamAdvancedBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBookTest.java) holds what the page says each method does.
~~~

---

Each section stands alone: [optics over a stream](#streamtraversal-lazy-optics), [a `Flow.Publisher`](#reactive-interop-flowpublisher-bridge), [`List` and `Stream`](#natural-transformations), [answers without a `VTask`](#vstreamcontext-layer-2-api), and, for code that builds Paths generically, [`Path.from`](#pathprovider-spi-registration).

The code on this page uses the order feed from the [VStream](vstream.md#reading-a-feed-one-page-at-a-time) page. `VStreamBook.PagedFeed` is a feed that records each page it is asked for, and `VStreamBook.orders(feed)` is the stream of its orders. `customer` and `paid` read an order. These are the page's imports:

<!-- verify -->
```java
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.capability.Chainable;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.extensions.StreamTraversal;
import org.higherkindedj.optics.util.Traversals;
```

## Optics over a stream {#streamtraversal-lazy-optics}

To change one field of every element a list or a stream gives, such as each order's status, use a `StreamTraversal`. Like a [`Traversal`](../optics/traversals.md) from the [Optics](../optics/ch_intro.md) chapter, it reaches every focus in a structure. A `Traversal` reads them all at once, through an `Applicative`. A `StreamTraversal` gives them as a `VStream`, so over a stream it reads only as far as the consumer pulls.

`forList()` focuses on each element of a list, and `andThen` with a `Lens` narrows each focus to one field, here an order line's price:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:prices}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:prices_run}}
```

`forVStream()` focuses on each element of a stream, so with `OrderLenses.status()` it reaches the status of every order the feed gives:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:statuses}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:statuses_run}}
```

Over a stream, `modify` gives a new stream, which changes each element as it is pulled. Two orders are taken, so only the feed's first page is fetched, and Grace's order, which was new, comes out paid. An endless stream works the same way. Written by hand, this is `orders.map(order -> OrderLenses.status().modify(...))`. A `StreamTraversal` is one value that serves a list or a stream, composes with lenses, and converts to a `Traversal`.

`modifyVTask` changes each focus with a task, and starts no thread of its own. Over a list, it returns one task that runs the element tasks in turn. Over a stream, that task gives the new stream at once, and each element's task runs as the element is pulled, on the thread that pulls it.

| | `Traversal` | `StreamTraversal` |
|---|---|---|
| Reads the focuses | All at once, through an `Applicative` | As a `VStream`; from `forVStream()`, as the consumer pulls |
| An endless stream | Never finishes reading it | From `forVStream()`, `stream`, `modify` and `modifyVTask` read it as pulled |
| Effects | Any `Applicative` | `VTask` |

### Converting to a Traversal {#conversion-to-standard-traversal}

`toTraversal()` gives a `Traversal` for code that takes one, such as `Traversals.getAll`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:to_traversal}}
```

`getAll` gives the two unit prices, which sum to 42.50. Over a stream, the `Traversal` reads the stream once, and the stream `Traversals.modify` gives can be read again. `StreamTraversal.fromTraversal(traversal)` goes the other way, and reads every focus as soon as `stream` is called.

## Exchanging elements with a `Flow.Publisher` {#reactive-interop-flowpublisher-bridge}

Java's `Flow.Publisher` sends elements to a subscriber as fast as the subscriber asks for them, which is how reactive libraries hand data on. A `VStream` is pulled instead. `VStreamReactive` joins the two.

### VStream to Publisher {#vstream-to-publisher}

`VStreamReactive.toPublisher(stream)` gives a publisher that pulls an element only when its subscriber has asked for one, plus one more to notice the end of the stream. Each subscriber reads the stream from the start. When a subscriber cancels, or the stream fails, the publisher closes what is left of the stream, so its finalisers run. A subscriber that cancels before it asks for anything closes nothing, since it has read nothing.

### Publisher to VStream {#publisher-to-vstream}

`VStreamReactive.fromPublisher(publisher, bufferSize)` reads a publisher as a stream. Here the stream of customers goes out through a publisher and is read back:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:publisher}}
```

- **Each run subscribes at its first pull**, so building the stream subscribes nothing, and a stream read twice subscribes twice.
- **A hot publisher**, such as a `SubmissionPublisher`, sends a run only what it publishes after the run subscribes. Publish once `getNumberOfSubscribers()` shows the run has subscribed.
- **Incoming elements are held in a buffer of `bufferSize`**, 256 if you leave it out. The stream first asks for that many, then for one more as it takes each element.
- **A run that stops early cancels its subscription**, so `take(3)` stops the publisher once it has its three elements.

The Flow rules forbid a null element, and so does a `VStream`. A publisher that sends one has its subscription cancelled, and the stream fails with a `NullPointerException`: `VStream.fromPublisher element cannot be null`.

On a `VStreamPath`, `Path.vstreamFromPublisher(publisher, bufferSize)` reads a publisher, and `path.toPublisher()` hands the stream to one.

## Converting between VStream, List and Stream {#natural-transformations}

For one stream, `toList().run()` gives a `List`. `VStreamTransformations` is for code written over `Kind`, which takes a conversion as a value. It gives a [`Natural`](../functional/natural_transformation.md) for each direction between `VStream` and `List`, and between `VStream` and Java's `Stream`. A `Natural` converts one container to another whatever its elements, and `GenericPath.mapK` takes one, to move a path onto another container. The [Foundations](../hkts/foundations_intro.md) introduction's "Which level am I working at?" tip says when code works over `Kind`.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:naturals}}
```

`listToVStream` and `streamToVStream` read nothing until the stream is pulled. `vstreamToList` and `vstreamToStream` read the whole stream when they are applied, on the calling thread, so take a prefix of an endless stream first. A Java `Stream` can be read only once, so each run of a `VStream` made from one carries on from where the last run stopped, and after a full run it gives nothing.

## Finding a Path, and registering a provider {#pathprovider-spi-registration}

`Path.from(kind, witness)` asks the `PathProvider` registered for `witness` for a Path that holds `kind`. `VStreamPathProvider` answers for a `VStream`:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:path_from}}
```

The registry cannot know which Path a provider builds, so the result's [family](../effect/capabilities.md#the-family-parameter) is open, the `?` in `Chainable<?, Order>`. It can be mapped, but `via` and `zipWith` accept no step. To chain, build the Path directly with `Path.vstream(...)`.

The provider is registered in `META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider` and in the `provides` clause of `hkj-core`'s `module-info`, so `Path.from()` finds it whether `hkj-core` is on the classpath or the module path. To register a `PathProvider` of your own, list it in the same services file, which `ServiceLoader` reads when the jar is on the classpath. In a module with a `module-info`, also declare `provides org.higherkindedj.hkt.effect.spi.PathProvider with ...;`, which is all it reads on the module path.

A provider whose effect can fail overrides `monadError()` to return its `MonadError` in an `Optional`. The default is empty, as for `VStream`, and `supportsRecovery()` returns whether one is present. `PathRegistry`, which holds the providers, gives the one it removed from `unregister` in an `Optional` too.

## Answers without a `VTask`: `VStreamContext` {#vstreamcontext-layer-2-api}

A `VStream`'s terminal operations, such as `toList`, give a `VTask` to run. A `VStreamContext` runs the stream itself, on the calling thread, and returns the answer. It works like the [Effect Contexts](../effect/effect_contexts.md#the-three-layer-architecture), such as `VTaskContext`, and has many of a `VStream`'s operations: `map`, `via`, `filter`, `take`, `parEvalMap` and `chunk` among them:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamAdvancedBook.java:context}}
```

A stream that fails throws from the terminal operation, with a checked exception wrapped in a `VTaskExecutionException`. `toVStream()` and `toPath()` give the stream, and a `VStreamPath` over it, when you need them.

## Methods {#methods}

| Method | What it does |
|---|---|
| `StreamTraversal.forList()`, `forVStream()` | Focuses on each element of a list, or of a stream |
| `streamTraversal.andThen(lens)`, `andThen(other)` | Narrows each focus to a field, or to another `StreamTraversal`'s focuses |
| `streamTraversal.stream(source)` | Gives the focuses as a `VStream` |
| `streamTraversal.modify(f, source)` | Changes each focus with `f` |
| `streamTraversal.modifyVTask(f, source)` | Changes each focus with a task |
| `streamTraversal.toTraversal()`, `StreamTraversal.fromTraversal(traversal)` | Converts to and from a `Traversal` |
| `VStreamReactive.toPublisher(stream)` | Hands the stream to each subscriber that asks |
| `VStreamReactive.fromPublisher(publisher, bufferSize)`, `fromPublisher(publisher)` | Reads a publisher as a stream, holding up to `bufferSize` elements, or 256 |
| `Path.vstreamFromPublisher(publisher, bufferSize)`, `vstreamPath.toPublisher()` | Do the same on a `VStreamPath` |
| `VStreamTransformations.listToVStream()`, `vstreamToList()`, `streamToVStream()`, `vstreamToStream()` | Give the `Natural`s between `VStream`, `List` and `Stream` |
| `genericPath.mapK(natural, monad)` | Moves a path onto another container |
| `Path.from(kind, witness)` | Finds a Path for `kind` through the provider registered for `witness` |
| `VStreamContext.fromList(list)`, `of(stream)`, `range(from, to)` | Start a context |
| `context.map(f)`, `via(f)`, `filter(p)`, `take(n)` | Build the next context |
| `context.toList()`, `count()`, `fold(identity, op)`, `find(p)` | Run the stream, and give the answer |
| `context.toVStream()`, `toPath()` | Give the stream, or a `VStreamPath` over it |

## When to use each bridge {#when-to-use-them}

| You need | Use |
|---|---|
| One field of every element changed, in a list or a stream | `StreamTraversal`, with a `Lens` |
| Elements from a reactive library, or handed to one | `VStreamReactive` |
| A `VStream`'s elements as a `List` or a `Stream`, or the other way | `toList()`, or `VStreamTransformations` for code over `Kind` |
| A stream's answer with no `VTask` to run | `VStreamContext` |
| A Path for a `Kind` your code cannot name | `Path.from`, or `Path.vstream` to chain |
| Every focus of a structure in memory, read at once | A [`Traversal`](../optics/traversals.md) |

~~~admonish info title="Hands-On Learning"
Practise `StreamTraversal`, the publisher bridge, the transformations and `VStreamContext` in Parts 2 to 5 of [TutorialVStreamAdvanced](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStreamAdvanced.java) (exercises 4 to 12), and optics over a `VStream` in [Tutorial 17: VStream Optics](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial17_VStreamOptics.java) (8 exercises).
~~~

---

**Previous:** [Resource-Safe Streaming](vstream_resources.md)
**Next:** [Reader](reader_monad.md)

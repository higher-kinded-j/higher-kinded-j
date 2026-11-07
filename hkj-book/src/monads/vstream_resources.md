<!-- description: Release what a VStream opens with bracket and onFinalize: once for each run, however the run ends, even when the consumer stops early. -->

# Resource-Safe Streaming: Release What a Stream Opens

_Open a connection when a run starts, and close it however the run ends._

~~~admonish info title="What You'll Learn"
- Open a resource for each run with `bracket`, and say when it is released, even when `take` stops early
- Predict what a run reports when reading fails, and when the release itself fails
- Nest brackets, and read one resource after another
- Attach cleanup to any stream with `onFinalize`
- Close a stream you pull by hand
~~~

~~~admonish example title="See Example Code"
**The code on this page is [VStreamResourcesBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java) and [VStreamLinesBook.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamLinesBook.java):** the page includes them, so the build compiles and runs them. [VStreamResourcesBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBookTest.java) holds what the page says about each release.
~~~

---

## A stream that holds a connection {#a-stream-that-holds-a-connection}

A stream reads nothing until it runs, so a `try`-with-resources block around the code that builds it closes the connection before the first element is read. The connection has to be opened by the run, and closed when the run ends.

The code reuses the order feed from the [VStream](vstream.md#reading-a-feed-one-page-at-a-time) page. `orders(feed)` streams every order in a feed, and `PagedFeed` serves `ORDERS`, six orders of which four are paid, two to a page. `new PagedFeed(ORDERS, 2)` fails at page 2, and `shippingQuotes(orders, courier)` asks a courier to quote for each paid order. With `Order` from the [Optics](../optics/ch_intro.md) chapter, these are the imports:

<!-- verify -->
```java
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
```

The feed is now read over a connection, which a service opens. `OrderFeed` and `Courier` are the VStream page's services:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:services}}
```

## `bracket` opens and closes {#bracket-opens-and-closes}

`VStream.bracket(acquire, use, release)` opens a resource when a run starts, reads the stream `use` builds from it, and releases it however the run ends:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:orders_over}}
```

`LoggedFeedService(log)` serves `ORDERS` over each connection it opens, and writes each open, page and close to `log`, so the log shows what a run did:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:bracket_used}}
```

Building the stream opened nothing. The run opened the connection, read every page up to the empty page 4, which ends the feed, and closed it. Each run opens a connection of its own, and closes it:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:run_twice}}
```

## Stopping early {#stopping-early}

A consumer that stops before the end still closes the connection:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:stopped_early}}
```

`take(2)` closes the rest of the stream once it has its two elements, so page 3 is never read. `takeWhile`, `zipWith`, `headOption`, `find`, `exists` and `forAll` close the rest in the same way when they stop.

## A file, a line at a time {#a-file-a-line-at-a-time}

A file is read the same way, with `java.nio.file.Files`, `java.nio.file.Path` and `java.util.Optional`. The reader opens when a run starts, and closes when the run reaches the end of the file, fails, or stops early:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamLinesBook.java:lines}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamLinesBook.java:lines_used}}
```

`java.nio.file.Path` and the library's `Path` share a name, so code that uses both imports one and writes the other in full. The example keeps the file in a class of its own.

## When something fails {#when-something-fails}

A failure closes the connection too, and a close that fails is reported. `new LoggedFeedService(log, feed)` serves another feed, and `LoggedFeedService.failingToClose(log, feed)` gives connections whose close throws:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:failures}}
```

| What fails | What happens |
|---|---|
| Reading, such as a page of the feed | The connection is closed, then the stream fails with the reading's own exception |
| `use`, while it builds the stream | The connection is closed, then the stream fails with `use`'s exception |
| The release, after a run that succeeded | The operation that ended the run, such as `toList`, fails with the release's exception |
| The release, after a run that failed | Its exception is suppressed onto the run's failure, in `getSuppressed()` |

`runSafe()` gives each failure as it was thrown. `run()` hands a checked one over inside a `VTaskExecutionException`, with the original as its cause, as [Running a task](vtask_monad.md#running-a-task) explains.

## Nested brackets, and one resource after another {#nested-brackets}

A bracket inside another holds its resource inside the outer one's, and releases it first. Here a courier session, whose quotes are 4.99 as `COURIER`'s are, is opened inside the feed connection:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:nested}}
```

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:nested_used}}
```

To read one resource after another, join the brackets outside each other, with `concat`. Each connection is closed before the next is opened:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:one_after_another}}
```

A `use` such as `connection -> orders(connection).concat(next)` instead keeps the first connection open until `next` has been read too.

## `onFinalize`: cleanup for any stream {#onfinalize-lightweight-cleanup}

`onFinalize(task)` runs a task when the stream's run ends, with nothing to open. It suits cleanup such as a log line or a metric:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:finalisers}}
```

- **A finaliser runs once for each run**, when the run completes, fails, or is closed early by an operation that stops.
- **Several finalisers run in the order they were attached.**
- **After a recovered failure, the finaliser runs when the stream ends.** When a `recover` reads on after a failed `mapTask` task, the run goes on, and the finaliser waits for its end.
- **A failing finaliser is reported as a failing release is**, as [When something fails](#when-something-fails) shows. When several fail, the others still run, the first one's exception is thrown, and each later one is suppressed onto it.

`bracket` is built on `onFinalize`: its release is the finaliser of the stream that `use` gives.

## On a `VStreamPath` {#on-a-vstreampath}

[`VStreamPath`](../effect/path_vstream.md) has both: `Path.vstreamBracket` builds one, and `onFinalize` attaches a finaliser:

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/foundations/vstream/VStreamResourcesBook.java:path}}
```

The connection's release runs before the finaliser attached after it.

## Pulling by hand {#pulling-by-hand}

~~~admonish warning title="Close a stream you pull by hand"
A release runs when its run completes, fails or is closed. Pull steps by hand, as [What a pull gives](vstream.md#what-a-pull-gives) shows, and stop part-way, and the connection stays open until you close the stream you pulled, or the tail you hold. The first pull of `ordersOver` opens the connection and gives a `Skip`, so even one pull leaves something to close. A pull that throws releases what it opened by itself.

`take` closes the rest only on the pull after its last element, so a hand puller who stops at that `Emit` still closes. A `Fail` step, for a failed `mapTask` task, holds the rest of the stream in its tail: close it, or read on from it, as you would an `Emit`'s. Running a pulled `VTask` twice opens two connections, and closing the stream you called `pull()` on closes only the latest run's, so close the tail each run gave you.
~~~

## Which thread releases {#which-thread-releases}

A release, or a finaliser, runs on the thread that makes the stream's last pull or closes it. That is the thread running the terminal operation, unless a parallel operation reads the stream on a thread of its own, as `parEvalMapUnordered` and `merge` do.

If that thread was interrupted before the release starts, the interrupt is held back while the release runs, and set again afterwards, so a release that blocks still finishes. An interrupt that arrives while the release runs, as a timeout's can, still reaches it.

## Methods {#methods}

| Method | What it does |
|---|---|
| `VStream.bracket(acquire, use, release)` | Opens a resource for each run, reads the stream `use` builds, and releases it however the run ends |
| `stream.onFinalize(task)` | Runs `task` once for each run, when the run ends |
| `stream.close()` | Closes the stream: runs the finalisers upstream of it, and releases what its latest run opened |
| `Path.vstreamBracket(acquire, use, release)`, `path.onFinalize(task)` | The same, on a `VStreamPath` |

## When to use bracket and onFinalize {#when-to-use-them}

| You need | Use |
|---|---|
| A resource opened for each run of a stream, and closed after | `bracket` |
| Cleanup with nothing to open, such as a log line or a metric | `onFinalize` |
| Several resources read one after another | Brackets joined with `concat` |
| A resource for one task, not a stream | [Resource](vtask_resource.md) |

~~~admonish info title="Hands-On Learning"
Practise `bracket` and `onFinalize` in Part 1 of [TutorialVStreamAdvanced](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStreamAdvanced.java) (exercises 1 to 3).
~~~

---

**Previous:** [Performance](vstream_performance.md)
**Next:** [Advanced Features](vstream_advanced.md)

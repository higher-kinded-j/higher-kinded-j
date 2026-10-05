# VStream: Resource-Safe Streaming
## _Acquire, Stream, Release: Guaranteed Cleanup_

~~~admonish info title="What You'll Learn"
- How to use `bracket` for resource-safe streaming with guaranteed cleanup
- How `onFinalize` attaches cleanup actions to any stream
- The exactly-once release guarantee and how it works
- Common patterns for file I/O, database cursors, and network connections
- Limitations of pull-based resource management
~~~

~~~admonish example title="See Example Code"
[VStreamAdvancedExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamAdvancedExample.java)
~~~

## The Problem: Streams That Leak Resources

Streaming over external resources, such as files, database cursors, or network connections,
requires careful lifecycle management. The resource must be acquired before the first element
is produced and released when the stream finishes, whether that finish is normal completion,
an error, or the consumer simply stopping early.

Without language-level support, this burden falls on the caller. Forget a `finally` block and
the file handle leaks. Wrap the entire pipeline in `try-with-resources` and the resource is
released before lazy processing begins. The tension between laziness and resource safety is
a fundamental challenge in pull-based streaming.

## bracket: The Solution

`VStream.bracket(acquire, use, release)` solves this by tying resource lifecycle to stream
lifecycle:

<!-- verify -->
```java
import java.nio.file.Path;

Path path = Path.of("data.txt"); // the file to stream

VStream<String> lines = VStream.bracket(
    // Acquire: open the resource (runs lazily, each time the stream is consumed)
    VTask.of(() -> Files.newBufferedReader(path)),

    // Use: produce a stream from the resource
    reader -> VStream.unfold(reader, r ->
        VTask.of(() -> {
            String line = r.readLine();
            return line == null
                ? Optional.empty()
                : Optional.of(new Seed<>(line, r));
        })),

    // Release: close the resource (guaranteed). `close()` is checked, so this is
    // VTask.of rather than VTask.exec, whose Runnable cannot throw.
    reader -> VTask.of(() -> {
        reader.close();
        return Unit.INSTANCE;
    })
);
```

Three key properties make this safe:

1. **Lazy acquisition**: The resource is acquired when the `VTask` that `pull()` returns runs,
   afresh on each run, not when `bracket` is called. This means creating the stream is free; the
   resource only opens when consumption begins.

2. **Guaranteed release**: The release function runs once for each acquisition, regardless of
   how the stream terminates, whether by normal completion, error, a `use` function that throws,
   or partial consumption via `take`, `headOption`, or `find`.

3. **Exactly-once semantics**: An internal `AtomicBoolean` ensures the release function
   cannot run twice, even if multiple terminal paths converge.

### Partial Consumption

One of the most important properties of `bracket` is that partial consumption still triggers
release:

<!-- verify -->
```java
// Only read first 10 lines, then close the file
List<String> firstTen = lines.take(10).toList().run();
// File handle is closed when take(10) triggers the finaliser
```

This works because `take(n)` closes the rest of the stream after its nth element, and closing
runs the release.

### Nested Brackets

Multiple bracket regions can be nested via composition. Inner resources are released before
outer resources:

<!-- verify -->
```java
// Assuming: Connection openConnection(), Cursor openCursor(Connection),
//           VStream<String> streamFromCursor(Cursor)
VStream<String> pipeline = VStream.bracket(
    VTask.of(() -> openConnection()),
    conn -> VStream.bracket(
        VTask.of(() -> openCursor(conn)),
        cursor -> streamFromCursor(cursor),
        cursor -> VTask.exec(() -> cursor.close())
    ),
    conn -> VTask.exec(() -> conn.close())
);
// cursor closed first, then connection
```

## onFinalize: Lightweight Cleanup {#onfinalize-lightweight-cleanup}

For cases where you do not need a full acquire-use-release cycle, `onFinalize` attaches a
cleanup action to any existing stream:

<!-- verify -->
```java
VStream<String> stream = VStream.of("a", "b", "c")
    .onFinalize(VTask.exec(() -> System.out.println("Stream completed")));
```

The finaliser runs once each time the stream is consumed: when that consumption completes,
fails, or stops early. An operation that stops early, such as `take`, `takeWhile`, `zipWith`,
`headOption`, `find`, `exists` or `forAll`, closes the rest of the stream, and so does a terminal
operation that fails. Closing reaches every finaliser upstream that the stream has started
reading, through any operator in between.
Multiple finalisers can be chained; they execute in the order they were attached:

<!-- verify -->
```java
VStream<Integer> stream = VStream.of(1, 2, 3)
    .onFinalize(VTask.exec(() -> System.out.println("first finaliser")))
    .onFinalize(VTask.exec(() -> System.out.println("second finaliser")));
```

### Error Handling in Finalisers

If the finaliser itself throws an exception and the stream also failed, the original error
is preserved and the finaliser error is added as a suppressed exception. If the stream did not
fail, a finaliser that throws fails the operation that completed or closed it, such as `toList`
or `headOption`. The other finalisers still run, and a later failure is suppressed onto the
first:

<!-- verify -->
```java
// Original error preserved; finaliser error becomes suppressed
try {
    stream.toList().run();
} catch (RuntimeException e) {
    // e is the original stream error
    // e.getSuppressed() contains the finaliser error
}
```

## VStreamPath Integration

The Path API provides fluent access to resource management:

<!-- verify -->
```java
import org.higherkindedj.hkt.effect.Path;

// Assuming: BufferedReader openReader(), VStream<String> streamLines(BufferedReader)
// bracket via Path factory
VStreamPath<String> lines = Path.vstreamBracket(
    VTask.of(() -> openReader()),
    reader -> streamLines(reader),
    reader -> VTask.of(() -> {
        reader.close();
        return Unit.INSTANCE;
    })
);

// onFinalize on existing path
VStreamPath<String> withCleanup = lines.onFinalize(
    VTask.exec(() -> System.out.println("cleanup"))
);
```

## Known Limitations

~~~admonish warning title="Abandoned Streams"
A finaliser runs when its stream completes, fails or is closed. If you pull steps by hand and
drop the tail without draining it or calling `close()`, the finaliser never runs. Every terminal
operation, `take` and `takeWhile` handle this for you. Closing a `bracket` head closes only its
latest run, so if you run its pulled `VTask` more than once, close the tail of each run.
~~~

## Key Takeaways

~~~admonish tip title="Key Takeaways"
- `bracket(acquire, use, release)` ties resource lifecycle to stream lifecycle
- Resources are acquired lazily, each time the stream is consumed, and released once for each acquisition
- Partial consumption (take, headOption, find) still triggers release
- `onFinalize` provides lightweight cleanup for streams that do not need full bracket
- Nested brackets release in reverse order (inner before outer)
- A finaliser error is suppressed onto the stream's own failure; otherwise it fails the operation that completed or closed the stream
~~~

## See Also

- [VStream: Lazy Pull-Based Streaming](vstream.md) for core VStream operations
- [VStream: Parallel Operations](vstream_parallel.md) for concurrent processing
- [VStream: Advanced Features](vstream_advanced.md) for StreamTraversal, reactive interop

---

**Previous:** [VStream: Performance](vstream_performance.md) | **Next:** [VStream: Advanced Features](vstream_advanced.md)

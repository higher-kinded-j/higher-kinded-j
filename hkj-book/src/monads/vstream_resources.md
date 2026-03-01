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

```java
Path path = Path.of("data.txt"); // the file to stream

VStream<String> lines = VStream.bracket(
    // Acquire: open the resource (runs lazily on first pull)
    VTask.of(() -> Files.newBufferedReader(path)),

    // Use: produce a stream from the resource
    reader -> VStream.unfold(reader, r ->
        VTask.of(() -> {
            String line = r.readLine();
            return line == null
                ? Optional.empty()
                : Optional.of(new Seed<>(line, r));
        })),

    // Release: close the resource (guaranteed)
    reader -> VTask.exec(() -> reader.close())
);
```

Three key properties make this safe:

1. **Lazy acquisition**: The resource is acquired on the first `pull()`, not when `bracket`
   is called. This means creating the stream is free; the resource only opens when consumption
   begins.

2. **Guaranteed release**: The release function runs exactly once, regardless of how the
   stream terminates, whether by normal completion, error, or partial consumption via `take`,
   `headOption`, or `find`.

3. **Exactly-once semantics**: An internal `AtomicBoolean` ensures the release function
   cannot run twice, even if multiple terminal paths converge.

### Partial Consumption

One of the most important properties of `bracket` is that partial consumption still triggers
release:

```java
// Only read first 10 lines, then close the file
List<String> firstTen = lines.take(10).toList().run();
// File handle is closed when take(10) triggers the finaliser
```

This works because `take(n)` wraps the stream with a counter that produces `Done` after n
elements, and `Done` triggers the release.

### Nested Brackets

Multiple bracket regions can be nested via composition. Inner resources are released before
outer resources:

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

## onFinalize: Lightweight Cleanup

For cases where you do not need a full acquire-use-release cycle, `onFinalize` attaches a
cleanup action to any existing stream:

```java
VStream<String> stream = VStream.of("a", "b", "c")
    .onFinalize(VTask.exec(() -> System.out.println("Stream completed")));
```

The finaliser runs when the stream completes or encounters an error. Multiple finalisers
can be chained; they execute in the order they were attached:

```java
VStream<Integer> stream = VStream.of(1, 2, 3)
    .onFinalize(VTask.exec(() -> System.out.println("first finaliser")))
    .onFinalize(VTask.exec(() -> System.out.println("second finaliser")));
```

### Error Handling in Finalisers

If the finaliser itself throws an exception and the stream also failed, the original error
is preserved and the finaliser error is added as a suppressed exception:

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

```java
// Assuming: BufferedReader openReader(), VStream<String> streamLines(BufferedReader)
// bracket via Path factory
VStreamPath<String> lines = Path.vstreamBracket(
    VTask.of(() -> openReader()),
    reader -> streamLines(reader),
    reader -> VTask.exec(() -> reader.close())
);

// onFinalize on existing path
VStreamPath<String> withCleanup = lines.onFinalize(
    VTask.exec(() -> System.out.println("cleanup"))
);
```

## Known Limitations

~~~admonish warning title="GC-Dependent Release"
If the consumer simply stops pulling without running the stream to completion and without
using a terminal operation that triggers the finaliser (such as `toList`, `take`, `headOption`,
`find`, `fold`, etc.), the release depends on garbage collection. This is a known limitation
of pull-based streams. Always use terminal operations to ensure cleanup runs promptly.
~~~

## Key Takeaways

~~~admonish tip title="Key Takeaways"
- `bracket(acquire, use, release)` ties resource lifecycle to stream lifecycle
- Resources are acquired lazily and released exactly once
- Partial consumption (take, headOption, find) still triggers release
- `onFinalize` provides lightweight cleanup for streams that do not need full bracket
- Nested brackets release in reverse order (inner before outer)
- Finaliser errors are suppressed; original errors are preserved
~~~

## See Also

- [VStream: Lazy Pull-Based Streaming](vstream.md) for core VStream operations
- [VStream: Parallel Operations](vstream_parallel.md) for concurrent processing
- [VStream: Advanced Features](vstream_advanced.md) for StreamTraversal, reactive interop

---

**Previous:** [VStream: Performance](vstream_performance.md) | **Next:** [VStream: Advanced Features](vstream_advanced.md)

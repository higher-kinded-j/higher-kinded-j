<!-- description: Where a VStream's time goes: what a pull costs, where threads start, when chunking pays, and how to measure it with JMH. -->

# Performance: Where a Stream's Time Goes

_Each element costs a call on your own thread, and threads start only where you ask for them._

~~~admonish info title="What You'll Learn"
- Say what each pull costs, and on which thread it runs
- Know which operations start virtual threads, and when that cost pays for itself
- Say what chunking saves, and what it does not
- Choose between a `VStream` and a Java `Stream` for a pipeline
- Run `VStreamBenchmark`, and read what it measures
~~~

~~~admonish example title="See Example Code"
[VStreamPerformanceBookTest.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/example/book/foundations/vstream/VStreamPerformanceBookTest.java) holds what this page says about where each cost falls, and [VStreamBenchmark](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-benchmarks/src/jmh/java/org/higherkindedj/benchmarks/VStreamBenchmark.java) measures the costs.
~~~

---

## What a pull costs {#what-a-pull-costs}

Each element reaches the consumer through a pull: a call that returns a `VTask`, which gives a step when it runs. A terminal operation run with `run()` or `runSafe()` makes every pull on that thread, and starts none. Each operator between the source and the consumer adds a task, a step and a call for every element: nanoseconds, where starting a thread takes microseconds. [What a pull gives](vstream.md#what-a-pull-gives) traces the pulls of a small pipeline.

Building a pipeline runs nothing. `map`, `filter`, `flatMap` and `mapTask` each wrap the stream they are given, and their functions run only when a terminal operation pulls.

## Against a Java `Stream` {#against-a-java-stream}

A Java `Stream` pushes each element through its operations in one loop, and makes no task or step for each element. So for work on data already in memory, such as mapping a list, it is faster than a `VStream`. A `VStream` earns its cost when the work for an element waits: a call to another service takes milliseconds, and a pull's nanoseconds vanish beside it.

| | `VStream` | Java `Stream` |
|---|---|---|
| Runs | Each time its terminal task runs | Once, when its terminal operation is called |
| An element | Pulled by the consumer | Pushed through the pipeline |
| An effect for an element | `mapTask`, on the thread that reads the stream | By hand, inside a function |
| A failure | A failed `mapTask` task gives a `Fail` step, which `recover` replaces and reads on from | An exception, which ends the pipeline |
| Cleanup | `bracket` and `onFinalize`, however the run ends | `onClose`, when the stream is closed |
| Parallel work | `VStreamPar`, a bounded number of tasks at once | `parallel()`, on the common pool |
| Suits | Work that waits, effects, and resources | Work on data in memory |

A stream read from an iterator or a Java `Stream` is used up by its first run, and a `bracket` opens its resource again for each run. [Advanced Features](vstream_advanced.md) covers a stream read from a `Flow.Publisher`.

## Where threads start {#where-threads-start}

| Operation | Threads it starts |
|---|---|
| `map`, `filter`, `flatMap`, `mapTask`, `take`, `chunk`, and every terminal operation run with `run()` or `runSafe()` | None: each runs on the thread that runs the terminal operation |
| `runAsync()` | One virtual thread, for the whole run |
| `VStreamPar.parEvalMap` | A virtual thread for each task |
| `VStreamPar.parEvalFlatMap` | A virtual thread for each task that builds a stream; the streams it builds are read on the reading thread |
| `VStreamPar.parEvalMapUnordered` | A virtual thread that reads the source, and one for each task |
| `VStreamPar.merge` of several streams | A virtual thread for each source, one that gathers them, and one that closes them when the consumer stops early |
| `VStreamReactive.toPublisher` | Virtual threads that pull for its subscribers |

Starting a virtual thread and waiting for it costs far more than a pull. So a parallel operation pays when each task waits, on I/O or a slow service. For a task that only computes something small, `map` or `mapTask` is faster. `parEvalMap` also waits for the slowest task in each batch; when the order of the results does not matter, `parEvalMapUnordered` lets each one go as its task completes, as [Parallel Operations](vstream_parallel.md) shows.

## What chunking saves {#what-chunking-saves}

`chunk(size)` still pulls the source once for every element, and groups what it pulls into batches. So it adds a little to each element, rather than saving anything there. It pays when the batch is what costs: one call to a warehouse or a database for ten orders, in place of ten calls. With `parEvalMap` after it, that is also one task, and one thread, for each batch rather than each element. A larger batch means fewer calls, more elements held at once, and a longer wait for the first result.

## Keeping a pipeline cheap {#keeping-a-pipeline-cheap}

- **Keep work that only computes** in `map` and `filter`, on the reading thread, where each adds a little to every element: far less than a task on a thread of its own.
- **Put work that waits in a task:** `mapTask` for one element at a time, and [`VStreamPar`](vstream_parallel.md) for several at once.
- **Batch a call that takes several elements** with `chunk`, then `parEvalMap`, as [Chunking operations](vstream_parallel.md#chunking-operations) shows.
- **Set the concurrency** to what the service accepts, as [Parallel Operations](vstream_parallel.md) explains.

## Measuring it yourself {#measuring-it-yourself}

`VStreamBenchmark`, in `hkj-benchmarks`, measures these costs with JMH:

```bash
./gradlew :hkj-benchmarks:jmh -Pincludes=".*VStreamBenchmark.*"
```

That run is quick, with the defaults the [Options](../benchmarks.md#options) table gives. For figures worth comparing, run more iterations in more forks, and add the GC profiler to see what each operation allocates:

```bash
./gradlew :hkj-benchmarks:jmh -Pincludes=".*VStreamBenchmark.*" \
  -Pjmh.warmupIterations=3 -Pjmh.iterations=5 -Pjmh.fork=2 -Pjmh.profilers=gc
```

The results go to `hkj-benchmarks/build/reports/jmh/`. Each score is operations per microsecond, so higher is faster, as [Reading the Output](../benchmarks.md#reading-the-output) explains. A filtered run like these overwrites `results.json`, and the next build's benchmark tests then fail on it, so delete the file before you build.

| Benchmarks | What they compare |
|---|---|
| `constructEmpty`, `constructSingle`, `constructFromList`, `constructRange`, and each `…Construction` | Building a stream or a pipeline, which runs nothing |
| `mapExecution`, `filterExecution`, `flatMapExecution`, `takeExecution`, `concatExecution`, `toListExecution`, `foldLeftExecution`, `countExecution` | Running one operation |
| `pipelineFilterMap`, `pipelineMapFilterTake`, `deepMapChainExecution`, `deepFlatMapChainExecution`, `realWorldTransformPipeline`, `realWorldFlatMapFilter` | Running pipelines |
| `baselineJavaStreamMap`, `baselineJavaStreamFilter`, `baselineJavaStreamPipeline`, `baselineJavaStreamFold` | The same work as a Java `Stream` |
| `mapTaskExecution`, `recoverThroughOperators` | A task for each element, and recovering through operators |
| `parEvalMapConcurrency2`, `parEvalMapConcurrency4`, `parEvalMapUnbounded`, `parEvalMapUnorderedConcurrency4` | Parallel tasks |
| `chunkExecution`, `mapChunkedExecution`, `realWorldChunkParallel` | Chunking |
| `existsEarlyMatch`, `findEarlyMatch`, `existsNoMatch` | Stopping early, against reading to the end |

Every benchmark works on data in memory, so it measures the stream's own cost. None waits on I/O, so none shows what a parallel operation gains for work that waits: the parallel benchmarks measure the cost of the threads. To see what parallel work gains for your pipeline, time it against the service it calls.

---

**Previous:** [Parallel Operations](vstream_parallel.md)
**Next:** [Resource-Safe Streaming](vstream_resources.md)

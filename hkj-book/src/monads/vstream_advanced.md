# VStream: Advanced Features
## _StreamTraversal, Reactive Interop, Natural Transformations, and VStreamContext_

~~~admonish info title="What You'll Learn"
- How StreamTraversal provides lazy optics for streaming data
- Converting between VStream and Flow.Publisher for reactive interop
- Natural transformations between VStream, List, and Stream types
- PathProvider SPI registration for VStream
- VStreamContext: the Layer 2 API that hides HKT complexity
~~~

~~~admonish example title="See Example Code"
[VStreamAdvancedExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/vstream/VStreamAdvancedExample.java)
~~~

## StreamTraversal: Lazy Optics

Standard `Traversal` materialises all elements via `Applicative` to combine effects. For
large or infinite streams, this is impractical. `StreamTraversal` provides a lazy alternative
that integrates directly with VStream's pull-based model.

### Core Operations

```java
public interface StreamTraversal<S, A> {
    VStream<A> stream(S source);          // Lazy element access
    S modify(Function<A, A> f, S source); // Pure modification
    VTask<S> modifyVTask(                 // Effectful modification
        Function<A, VTask<A>> f, S source);
}
```

- `stream()` returns a lazy `VStream<A>` of focused elements; nothing is materialised
  until the consumer pulls
- `modify()` applies a pure function to all focused elements, reconstructing the structure
- `modifyVTask()` applies an effectful function on virtual threads

### Built-in Instances

```java
// For VStream elements (identity traversal, fully lazy)
StreamTraversal<VStream<Integer>, Integer> vstreamST =
    StreamTraversal.forVStream();

// For List elements (materialises on modify, lazy on stream)
StreamTraversal<List<String>, String> listST =
    StreamTraversal.forList();
```

### Composition

StreamTraversal composes with other StreamTraversals and with Lens:

```java
// Compose two StreamTraversals: list of lists -> all inner elements
StreamTraversal<List<List<Integer>>, List<Integer>> outer =
    StreamTraversal.forList();
StreamTraversal<List<Integer>, Integer> inner =
    StreamTraversal.forList();
StreamTraversal<List<List<Integer>>, Integer> composed =
    outer.andThen(inner);

// Compose with Lens: list of records -> focused field
record Person(String name, int age) {}
Lens<Person, String> nameLens =
    Lens.of(Person::name, (n, p) -> new Person(n, p.age()));

StreamTraversal<List<Person>, String> namesST =
    StreamTraversal.forList().andThen(nameLens);

// Stream all names lazily
List<Person> people = List.of(new Person("Alice", 30), new Person("Bob", 25));
VStream<String> names = namesST.stream(people);
```

### Conversion to Standard Traversal

```java
// Convert to standard Traversal (materialising)
Traversal<List<Integer>, Integer> traversal = listST.toTraversal();

// Convert from standard Traversal
Traversal<VStream<String>, String> existingTraversal = /* ... */;
StreamTraversal<VStream<String>, String> fromExisting =
    StreamTraversal.fromTraversal(existingTraversal);
```

### Key Difference from Traversal

| Aspect | Traversal | StreamTraversal |
|--------|-----------|-----------------|
| Element access | Materialises via Applicative | Lazy VStream |
| Infinite data | Not supported | Fully supported |
| Effect model | Generic Applicative F | VTask directly |
| Memory | Proportional to structure size | Constant (streaming) |

## Reactive Interop: Flow.Publisher Bridge

`VStreamReactive` bridges VStream's pull-based model with Java's `Flow.Publisher`/`Subscriber`
push-based reactive model. This enables integration with reactive frameworks and libraries.

### VStream to Publisher

```java
VStream<String> stream = VStream.of("a", "b", "c");
Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);
```

The publisher respects backpressure: elements are only pulled from the VStream when the
subscriber has outstanding demand via `request(n)`. Each subscriber receives all elements.

### Publisher to VStream

```java
Flow.Publisher<Event> eventPublisher = getEventSource(); // your push-based source
VStream<Event> events = VStreamReactive.fromPublisher(eventPublisher, 64);
```

The publisher is subscribed to immediately. Incoming elements are buffered in a bounded queue
(configurable via the `bufferSize` parameter). The VStream pulls from this queue, converting
push-based delivery to pull-based consumption.

### Round-Trip

```java
// VStream -> Publisher -> VStream preserves elements
VStream<Integer> original = VStream.of(1, 2, 3);
Flow.Publisher<Integer> pub = VStreamReactive.toPublisher(original);
VStream<Integer> roundTripped = VStreamReactive.fromPublisher(pub, 16);
List<Integer> result = roundTripped.toList().run();
// result: [1, 2, 3]
```

### Path Integration

```java
Flow.Publisher<Event> eventPublisher = getEventSource(); // your push-based source

// Create VStreamPath from a publisher
VStreamPath<Event> eventPath =
    Path.vstreamFromPublisher(eventPublisher, 64);

// Convert VStreamPath to publisher
Flow.Publisher<Event> pub = eventPath.toPublisher();
```

## Natural Transformations

`VStreamTransformations` provides natural transformations between VStream and other
collection types:

```java
// List -> VStream (lazy)
NaturalTransformation<ListKind.Witness, VStreamKind.Witness> listToVStream =
    VStreamTransformations.listToVStream();

// VStream -> List (materialises)
NaturalTransformation<VStreamKind.Witness, ListKind.Witness> vstreamToList =
    VStreamTransformations.vstreamToList();

// Stream -> VStream (lazy via iterator)
NaturalTransformation<StreamKind.Witness, VStreamKind.Witness> streamToVStream =
    VStreamTransformations.streamToVStream();

// VStream -> Stream (materialises)
NaturalTransformation<VStreamKind.Witness, StreamKind.Witness> vstreamToStream =
    VStreamTransformations.vstreamToStream();
```

These compose via `andThen`:

```java
// Stream -> VStream -> List in one step
NaturalTransformation<StreamKind.Witness, ListKind.Witness> streamToList =
    streamToVStream.andThen(vstreamToList);
```

~~~admonish warning title="Materialisation"
Transformations to `Stream` or `List` materialise the entire VStream. For infinite streams,
use `take()` first.
~~~

## PathProvider SPI Registration

`VStreamPathProvider` registers VStream with the PathProvider SPI, enabling dynamic path
creation via `Path.from()`:

```java
// Discover VStreamPathProvider automatically via ServiceLoader
Kind<VStreamKind.Witness, String> kind =
    VSTREAM.widen(VStream.of("a", "b"));

Optional<Chainable<String>> path =
    Path.from(kind, VStreamKind.Witness.class);
// Returns a VStreamPath wrapping the VStream
```

The provider is registered in `META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider`.

## VStreamContext: Layer 2 API

`VStreamContext<A>` provides a clean, HKT-free API for stream processing. It wraps VStream
internally and exposes simple operations with blocking terminal semantics, consistent with
`VTaskContext`.

### Creating Contexts

```java
VStreamContext<Integer> ctx = VStreamContext.fromList(List.of(1, 2, 3));
VStreamContext<String> single = VStreamContext.pure("hello");
VStreamContext<Integer> range = VStreamContext.range(1, 100);
VStreamContext<Integer> empty = VStreamContext.empty();
```

### Building Pipelines

```java
List<String> result = VStreamContext.range(1, 20)
    .filter(n -> n % 2 == 0)
    .map(n -> "Even: " + n)
    .take(3)
    .toList();
// ["Even: 2", "Even: 4", "Even: 6"]
```

### Terminal Operations

Terminal operations block until the result is available:

```java
long count = ctx.count();
boolean hasEven = ctx.exists(n -> n % 2 == 0);
boolean allPositive = ctx.forAll(n -> n > 0);
Optional<Integer> first = ctx.headOption();
Integer sum = ctx.fold(0, Integer::sum);
Optional<Integer> found = ctx.find(n -> n > 10);
```

### Monadic Composition

```java
List<Integer> result = VStreamContext.fromList(List.of(1, 2, 3))
    .via(x -> VStreamContext.fromList(List.of(x, x * 10)))
    .toList();
// [1, 10, 2, 20, 3, 30]
```

### Escape Hatches

When you need access to the underlying types:

```java
VStream<Integer> stream = ctx.toVStream();
VStreamPath<Integer> path = ctx.toPath();
```

## Key Takeaways

~~~admonish tip title="Key Takeaways"
- StreamTraversal provides lazy optics that work with infinite streams
- VStreamReactive bridges pull-based VStream and push-based Flow.Publisher
- Natural transformations convert between VStream, List, and Stream types
- VStreamPathProvider enables dynamic path creation via ServiceLoader
- VStreamContext provides a clean Layer 2 API hiding HKT complexity
~~~

## See Also

- [VStream: Lazy Pull-Based Streaming](vstream.md) for core operations
- [VStream: Resource-Safe Streaming](vstream_resources.md) for bracket and onFinalize
- [VStream: Parallel Operations](vstream_parallel.md) for concurrent processing
- [VStream: HKT and Type Classes](vstream_hkt.md) for Functor, Monad, Traverse instances

---

**Previous:** [VStream: Resource-Safe Streaming](vstream_resources.md) | **Next:** [Writer](writer_monad.md)

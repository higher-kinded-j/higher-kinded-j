# VStreamPath

`VStreamPath<A>` wraps `VStream<A>` for **lazy, pull-based streaming on virtual
threads**. It brings VStream's consumer-driven evaluation model into the Effect
Path API, letting you compose stream pipelines with the same fluent syntax used by
VTaskPath, IOPath, and every other path type.

> *"The stream of time sweeps away errors, and leaves the truth for the inheritance of posterity."*
>
> -- Georg Brandes

VStreamPath occupies a unique position among path types: it is the only one that
models a sequence of zero or more values produced lazily over time. Where VTaskPath
wraps a single deferred value, VStreamPath wraps an entire pipeline of deferred
values. Terminal operations on a VStreamPath return a `VTaskPath`, bridging the
multi-value world back to single-value effects.

~~~admonish info title="What You'll Learn"
- Creating VStreamPath instances via the Path factory
- Composing stream pipelines with map, via, filter, and take
- Terminal operations that bridge to VTaskPath
- Optics focus bridge for navigating into stream elements
- Conversions to StreamPath, ListPath, and NonDetPath
- When to choose VStreamPath over StreamPath or VTaskPath
~~~

~~~admonish info title="Hands-On Learning"
[TutorialVStreamPath.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/concurrency/TutorialVStreamPath.java)
~~~

~~~admonish example title="See Example Code"
[VStreamPathExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/effect/VStreamPathExample.java)
~~~

---

## Where VStreamPath Fits

VStreamPath implements `Chainable<A>`, the monadic composition capability in the
Effect Path hierarchy. It does not implement `Effectful` (no side-effect execution
model) or `Recoverable` (error handling is on the underlying VStream).

```
                  ┌────────────────────┐
                  │    Composable<A>   │  map, peek
                  └─────────┬──────────┘
                            │
              ┌─────────────┼─────────────┐
              │             │             │
   ┌──────────▼──────┐  ┌──▼───────┐  ┌──▼──────────┐
   │  Combinable<A>  │  │Chainable │  │ Effectful<A> │
   │  zipWith        │  │ via,then │  │ run, runSafe │
   └─────────────────┘  └──┬───────┘  └──────────────┘
                            │
       Implemented by:      │
       ┌────────────────────┼────────────────────┐
       │                    │                    │
  VTaskPath<A>      VStreamPath<A>        IOPath<A>
  (single value)    (lazy stream)     (single value)
```

**Package**: `org.higherkindedj.hkt.effect`
**Module**: `hkj-core`

---

## Creation

```java
// From an existing VStream
VStreamPath<String> fromStream = Path.vstream(myVStream);

// From values (varargs)
VStreamPath<Integer> numbers = Path.vstreamOf(1, 2, 3, 4, 5);

// From a list (lazy; does not copy)
VStreamPath<String> fromList = Path.vstreamFromList(List.of("a", "b", "c"));

// Single element
VStreamPath<String> single = Path.vstreamPure("hello");

// Empty stream
VStreamPath<Integer> empty = Path.vstreamEmpty();

// Integer range [start, end)
VStreamPath<Integer> range = Path.vstreamRange(1, 100);

// Infinite stream from seed and step
VStreamPath<Integer> powers = Path.vstreamIterate(1, n -> n * 2);

// Infinite stream from supplier
VStreamPath<Double> randoms = Path.vstreamGenerate(Math::random);

// Effectful unfolding (e.g. paginated API)
VStreamPath<Page> pages = Path.vstreamUnfold(1, pageNum ->
    VTask.of(() -> {
        if (pageNum > lastPage) return Optional.empty();
        return Optional.of(new VStream.Seed<>(fetchPage(pageNum), pageNum + 1));
    }));
```

Like VTaskPath, nothing runs until a terminal operation executes. Construction only
describes the pipeline.

---

## Core Operations

### map

Transform each element lazily:

```java
VStreamPath<String> tags = Path.vstreamOf(1, 2, 3)
    .map(n -> "#" + n);
// Lazy: no elements produced yet
```

### via (flatMap)

Replace each element with a sub-stream and flatten. This is the monadic bind,
and the mapper must return a `VStreamPath`:

```java
VStreamPath<Integer> expanded = Path.vstreamOf(1, 2, 3)
    .via(n -> Path.vstreamOf(n, n * 10));
// [1, 10, 2, 20, 3, 30] when materialised
```

### then

Sequence two streams, discarding the first result:

```java
VStreamPath<String> withSetup = Path.vstreamOf("setup")
    .then(() -> Path.vstreamOf("a", "b", "c"));
// ["a", "b", "c"] when materialised
```

### peek

Observe elements without modifying them:

```java
VStreamPath<Integer> logged = Path.vstreamOf(1, 2, 3)
    .peek(n -> System.out.println("Processing: " + n));
```

### zipWith

Pair elements positionally from two streams. Stops at the shorter stream:

```java
VStreamPath<String> zipped = Path.vstreamOf("a", "b", "c")
    .zipWith(Path.vstreamOf(1, 2, 3), (s, n) -> s + n);
// ["a1", "b2", "c3"]
```

---

## Stream-Specific Operations

These operations are unique to VStreamPath and have no equivalent on VTaskPath or
MaybePath.

```java
VStreamPath<Integer> pipeline = Path.vstreamRange(1, 100)
    .filter(n -> n % 2 == 0)     // keep evens
    .take(10)                     // first 10
    .map(n -> n * 3);             // multiply by 3

// Equivalent to: [6, 12, 18, 24, 30, 36, 42, 48, 54, 60]
```

| Operation | Description |
|-----------|-------------|
| `filter(predicate)` | Keep elements matching the predicate |
| `take(n)` | Limit to the first n elements |
| `drop(n)` | Skip the first n elements |
| `takeWhile(predicate)` | Take while predicate holds |
| `dropWhile(predicate)` | Skip while predicate holds |
| `distinct()` | Remove duplicate elements |
| `concat(other)` | Append another VStreamPath after this one |

~~~admonish warning title="Infinite Streams"
`distinct()` tracks seen elements in memory. On infinite streams, this grows
without bound. Combine with `take()` or `takeWhile()` to limit memory usage.
~~~

---

## Terminal Operations: Bridging to VTaskPath

Every terminal operation on VStreamPath returns a `VTaskPath`, bridging the
multi-value stream world back to a single deferred value. This means you can
continue composing with VTaskPath operations after collecting results.

```
  VStreamPath ──── terminal operation ────▶ VTaskPath
  (many values,                             (single value,
   lazy)                                     deferred)
```

```java
VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

// Collect all elements
VTaskPath<List<Integer>> all = stream.toList();
List<Integer> result = all.unsafeRun();
// [1, 2, 3, 4, 5]

// Fold with seed
VTaskPath<Integer> sum = stream.fold(0, Integer::sum);
// 15

// First element
VTaskPath<Optional<Integer>> head = stream.headOption();
// Optional.of(1)

// Check if any match
VTaskPath<Boolean> hasEven = stream.exists(n -> n % 2 == 0);
// true (short-circuits after finding 2)
```

| Terminal Operation | Return Type | Description |
|-------------------|-------------|-------------|
| `toList()` | `VTaskPath<List<A>>` | Collect all elements |
| `fold(identity, op)` | `VTaskPath<A>` | Left fold with seed |
| `foldLeft(identity, f)` | `VTaskPath<B>` | Left fold with accumulator |
| `foldMap(monoid, f)` | `VTaskPath<M>` | Map and combine via monoid |
| `headOption()` | `VTaskPath<Optional<A>>` | First element or empty |
| `lastOption()` | `VTaskPath<Optional<A>>` | Last element or empty |
| `count()` | `VTaskPath<Long>` | Count elements |
| `exists(predicate)` | `VTaskPath<Boolean>` | Any match (short-circuits) |
| `forAll(predicate)` | `VTaskPath<Boolean>` | All match (short-circuits) |
| `find(predicate)` | `VTaskPath<Optional<A>>` | First matching element |
| `forEach(consumer)` | `VTaskPath<Unit>` | Side effect per element |

### Chaining After Terminal Operations

Because terminal operations return VTaskPath, you can chain further:

```java
String summary = Path.vstreamRange(1, 1001)
    .filter(n -> n % 7 == 0)
    .take(10)
    .toList()                            // VTaskPath<List<Integer>>
    .map(list -> "Found " + list.size() + " multiples of 7")
    .unsafeRun();
// "Found 10 multiples of 7"
```

---

## Optics Focus Bridge

VStreamPath provides `focus` methods that let you navigate into each element using
optics. This bridges the streaming and optics worlds.

### focus with FocusPath (Lens)

Extract a field from every element using a lens:

```java
record User(String name, int age) {}

FocusPath<User, String> nameLens = ...;

VStreamPath<String> names = Path.vstreamOf(
    new User("Alice", 30),
    new User("Bob", 25)
).focus(nameLens);
// ["Alice", "Bob"]
```

### focus with AffinePath (Optional/Prism)

Extract a field that may not exist. Elements where the affine does not match are
silently excluded from the stream:

```java
AffinePath<Object, String> stringPrism = ...;

VStreamPath<String> strings = Path.vstreamOf("hello", 42, "world")
    .focus(stringPrism);
// ["hello", "world"]  (42 is excluded)
```

### Composing Focus with Stream Operations

```java
VStreamPath<String> activeUserNames = Path.vstream(userStream)
    .filter(user -> user.isActive())
    .focus(userNameLens)
    .map(String::toUpperCase)
    .take(10);
```

---

## Conversions

VStreamPath can be converted to other path types. Conversions that produce a
single value return a VTaskPath. Conversions that produce a collection materialise
the stream.

```java
VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3);

// First/last element as VTaskPath (fails if empty)
VTaskPath<Integer> first = stream.first();
VTaskPath<Integer> last = stream.last();

// Materialise to eager collection paths
StreamPath<Integer> streamPath = stream.toStreamPath();
ListPath<Integer> listPath = stream.toListPath();
NonDetPath<Integer> nonDetPath = stream.toNonDetPath();
```

~~~admonish note title="Materialisation"
`toStreamPath()`, `toListPath()`, and `toNonDetPath()` all materialise the entire
stream into memory. The resulting path types are eager (all elements are collected).
For infinite streams, bound the stream with `take()` or `takeWhile()` first.
~~~

---

## From Each Traversal

VStreamPath can be created from an `Each` traversal, turning any traversable
structure into a lazy stream of its elements:

```java
Each<List<String>, String> listEach = ...;
List<String> data = List.of("alpha", "beta", "gamma");

VStreamPath<String> elements = VStreamPath.fromEach(data, listEach);
// Lazy stream: ["alpha", "beta", "gamma"]
```

---

## Choosing the Right Path Type

| Aspect | VStreamPath | StreamPath | VTaskPath |
|--------|-------------|------------|-----------|
| **Values** | Zero or more, lazy | Zero or more, eager | Exactly one, deferred |
| **Evaluation** | Pull-based (lazy) | Eager (materialised list) | Deferred |
| **Execution** | Virtual threads | Synchronous | Virtual threads |
| **Infinite sources** | Yes | No | N/A |
| **Terminal result** | VTaskPath | Direct access | Direct access |
| **Backpressure** | Natural (pull model) | N/A (eager) | N/A (single value) |
| **Reusability** | Reusable | Reusable | Reusable |

**Choose VStreamPath when:**
- You need lazy streaming with virtual thread execution
- Elements come from effectful sources (paginated APIs, databases, sensors)
- The data source may be infinite
- You want natural backpressure through pull-based consumption

**Choose StreamPath when:**
- You have an already-materialised list
- You want fluent Path API composition over a known collection
- No I/O or effectful element production is needed

**Choose VTaskPath when:**
- You have a single deferred value (not a sequence)
- The computation produces exactly one result

---

## Real-World Example

```java
// Paginated API with fluent composition, focus, and terminal bridging
VStreamPath<String> userEmails = Path.vstreamUnfold(1, page ->
        VTask.of(() -> {
            Page<User> result = userService.listUsers(page);
            if (result.isEmpty()) return Optional.empty();
            return Optional.of(new VStream.Seed<>(result, page + 1));
        }))
    .via(page -> Path.vstreamFromList(page.items()))
    .filter(User::isActive)
    .focus(userEmailLens)
    .take(100);

// Terminal: collect and continue in VTaskPath
VTaskPath<List<String>> task = userEmails.toList();
Try<List<String>> result = task.runSafe();
```

---

~~~admonish info title="Key Takeaways"
* **VStreamPath** wraps VStream for fluent, lazy stream composition in the Effect Path API
* **Terminal operations return VTaskPath**, bridging multi-value streams to single-value effects
* **Stream-specific operations** (filter, take, drop, distinct, concat) have no equivalent on other path types
* **Focus bridge** lets you navigate into stream elements with lenses and prisms
* **Conversions** materialise the stream to StreamPath, ListPath, or NonDetPath
* **Everything is lazy**: no elements are produced until a terminal operation executes
* **Chainable but not Effectful**: compose with via and map; execute via terminal then VTaskPath.run
~~~

~~~admonish tip title="See Also"
- [VStream](../monads/vstream.md) - Core VStream type: Step protocol, factories, combinators
- [VStream HKT](../monads/vstream_hkt.md) - Type class instances for generic programming
- [VTaskPath](path_vtask.md) - Single-value virtual thread path (terminal operations return this)
- [StreamPath](../monads/stream_monad.md) - Eager list-based streaming path
- [Effect Path Overview](effect_path_overview.md) - How all path types fit together
- [Focus-Effect Integration](focus_integration.md) - Optics meet Effect Path
- [Each Typeclass](../optics/each_typeclass.md) - Canonical element-wise traversal (includes VStream)
~~~

---

**Previous:** [VTaskPath](path_vtask.md)
**Next:** [Composition Patterns](composition.md)

# The StreamMonad:
## _Lazy, Potentially Infinite Sequences with Functional Operations_

~~~admonish info title="What You'll Learn"
- How to work with Streams as contexts for lazy, potentially infinite sequences
- Understanding Stream's **single-use semantics** and how to work with them
- Using `map`, `flatMap`, and `ap` for lazy functional composition
- Leveraging StreamOps utilities for common stream operations
- Building efficient data processing pipelines with monadic operations
- When to choose Stream over List for sequential processing
~~~

~~~ admonish example title="See Example Code:"
[StreamExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/StreamExample.java)
~~~

## Purpose

The `StreamMonad` in the `Higher-Kinded-J` library provides a monadic interface for Java's standard `java.util.stream.Stream`. It allows developers to work with streams in a functional style, enabling operations like `map`, `flatMap`, and `ap` within the higher-kinded type system. This is particularly useful for processing sequences of data lazily, handling potentially infinite sequences, and composing stream operations in a type-safe manner.

Key benefits include:

* **Lazy Evaluation:** Operations are not performed until a terminal operation is invoked, allowing for efficient processing of large or infinite sequences.
* **HKT Integration:** `StreamKind` (the higher-kinded wrapper for `Stream`) and `StreamMonad` allow `Stream` to be used with generic functions and type classes expecting `Kind<F, A>` where `F extends WitnessArity<?>`, along with type classes like `Functor<F>`, `Applicative<F>`, or `Monad<F>` where `F extends WitnessArity<TypeArity.Unary>`.
* **MonadZero Instance:** Provides an empty stream via `zero()`, useful for filtering and conditional logic.
* **Functional Composition:** Easily chain operations on streams where each operation maintains laziness and allows composition of complex data transformations.

It implements `MonadZero<StreamKind.Witness>`, inheriting from `Monad`, `Applicative`, and `Functor`.

~~~admonish warning title="Important: Single-Use Semantics"
Java Streams have **single-use semantics**. Once a terminal operation has been performed on a stream (including operations that narrow and inspect the stream), that stream cannot be reused. Attempting to operate on a consumed stream throws `IllegalStateException`.

**Best Practice:** Create fresh stream instances for each operation sequence. Don't store and reuse `Kind<StreamKind.Witness, A>` instances after they've been consumed.
~~~

## Structure

![stream_monad.svg](../images/puml/stream_monad.svg)

## How to Use `StreamMonad` and `StreamKind`

### Creating Instances

`StreamKind<A>` is the higher-kinded type representation for `java.util.stream.Stream<A>`. You create `StreamKind` instances using the `StreamKindHelper` utility class, the `of` method from `StreamMonad`, or the convenient factory methods in `StreamOps`.

~~~admonish title="_STREAM.widen(Stream<A>)_"

Converts a standard `java.util.stream.Stream<A>` into a `Kind<StreamKind.Witness, A>`.

```java
Stream<String> stringStream = Stream.of("a", "b", "c");
Kind<StreamKind.Witness, String> streamKind1 = STREAM.widen(stringStream);

Stream<Integer> intStream = Stream.of(1, 2, 3);
Kind<StreamKind.Witness, Integer> streamKind2 = STREAM.widen(intStream);

Stream<Object> emptyStream = Stream.empty();
Kind<StreamKind.Witness, Object> streamKindEmpty = STREAM.widen(emptyStream);
```
~~~


~~~admonish title="_streamMonad.of(A value)_"

Lifts a single value into the `StreamKind` context, creating a singleton stream. A `null` input value results in an empty `StreamKind`.

```java
StreamMonad streamMonad = StreamMonad.INSTANCE;

Kind<StreamKind.Witness, String> streamKindOneItem = streamMonad.of("hello"); // Contains a stream with one element: "hello"
Kind<StreamKind.Witness, Integer> streamKindAnotherItem = streamMonad.of(42);  // Contains a stream with one element: 42
Kind<StreamKind.Witness, Object> streamKindFromNull = streamMonad.of(null); // Contains an empty stream
```
~~~

~~~admonish title="_streamMonad.zero()_"

Creates an empty `StreamKind`, useful for filtering operations or providing a "nothing" value in monadic computations.

```java
StreamMonad streamMonad = StreamMonad.INSTANCE;

Kind<StreamKind.Witness, String> emptyStreamKind = streamMonad.zero(); // Empty stream
```
~~~

~~~admonish title="_STREAM.narrow()_"

To get the underlying `java.util.stream.Stream<A>` from a `Kind<StreamKind.Witness, A>`, use `STREAM.narrow()`:

```java
Kind<StreamKind.Witness, String> streamKind = STREAM.widen(Stream.of("example"));
Stream<String> unwrappedStream = STREAM.narrow(streamKind); // Returns Stream containing "example"

// You can then perform terminal operations on the unwrapped stream
List<String> result = unwrappedStream.collect(Collectors.toList());
System.out.println(result); // [example]
```
~~~

~~~admonish title="_StreamOps Factory Methods_"

The `StreamOps` utility class provides convenient factory methods for creating `StreamKind` instances:

```java
// Create from varargs
Kind<StreamKind.Witness, Integer> numbers = fromArray(1, 2, 3, 4, 5);

// Create a range (exclusive end)
Kind<StreamKind.Witness, Integer> range = range(1, 11); // 1 through 10

// Create from collection
List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
Kind<StreamKind.Witness, String> nameStream = fromIterable(names);

// Create empty stream
Kind<StreamKind.Witness, String> empty = empty();
```
~~~

### Key Operations

The `StreamMonad` provides standard monadic operations, all maintaining lazy evaluation:

~~~admonish  title="_map(Function<A, B> f, Kind<StreamKind.Witness, A> fa)_"
**`map(Function<A, B> f, Kind<StreamKind.Witness, A> fa)`:**

Applies a function `f` to each element of the stream within `fa`, returning a new `StreamKind` containing the transformed elements. The transformation is **lazy** and won't execute until a terminal operation is performed.

```java
StreamMonad streamMonad = StreamMonad.INSTANCE;
Kind<StreamKind.Witness, Integer> numbers = STREAM.widen(Stream.of(1, 2, 3));

Function<Integer, String> intToString = i -> "Number: " + i;
Kind<StreamKind.Witness, String> strings = streamMonad.map(intToString, numbers);

// At this point, no transformation has occurred yet (lazy)
// Terminal operation triggers execution:
List<String> result = STREAM.narrow(strings).collect(Collectors.toList());
System.out.println(result);
// Output: [Number: 1, Number: 2, Number: 3]
```
~~~

~~~admonish  title="_flatMap(Function<A, Kind<StreamKind.Witness, B>> f, Kind<StreamKind.Witness, A> ma)_"
**`flatMap(Function<A, Kind<StreamKind.Witness, B>> f, Kind<StreamKind.Witness, A> ma)`:**

Applies a function `f` to each element of the stream within `ma`. The function `f` itself returns a `StreamKind<B>`. `flatMap` then flattens all these resulting streams into a single `StreamKind<B>`. Evaluation remains lazy.

```java
StreamMonad streamMonad = StreamMonad.INSTANCE;
Kind<StreamKind.Witness, Integer> initialValues = STREAM.widen(Stream.of(1, 2, 3));

// Function that takes an integer and returns a stream of itself and itself + 10
Function<Integer, Kind<StreamKind.Witness, Integer>> replicateAndAddTen =
    i -> STREAM.widen(Stream.of(i, i + 10));

Kind<StreamKind.Witness, Integer> flattenedStream = streamMonad.flatMap(replicateAndAddTen, initialValues);

// Lazy - evaluation happens at terminal operation
List<Integer> result = STREAM.narrow(flattenedStream).collect(Collectors.toList());
System.out.println(result);
// Output: [1, 11, 2, 12, 3, 13]

// Example with conditional logic
Function<Integer, Kind<StreamKind.Witness, String>> toWordsIfEven =
    i -> (i % 2 == 0) ?
         STREAM.widen(Stream.of("even", String.valueOf(i))) :
         streamMonad.zero(); // Empty stream for odd numbers

Kind<StreamKind.Witness, String> wordStream = streamMonad.flatMap(toWordsIfEven, initialValues);
List<String> words = STREAM.narrow(wordStream).collect(Collectors.toList());
System.out.println(words);
// Output: [even, 2]
```
~~~

~~~admonish  title="_ap(Kind<StreamKind.Witness, Function<A, B>> ff, Kind<StreamKind.Witness, A> fa)_"
**`ap(Kind<StreamKind.Witness, Function<A, B>> ff, Kind<StreamKind.Witness, A> fa)`:**

Applies a stream of functions `ff` to a stream of values `fa`. This results in a new stream where each function from `ff` is applied to each value in `fa` (Cartesian product style). Evaluation remains lazy.

```java
StreamMonad streamMonad = StreamMonad.INSTANCE;

Function<Integer, String> addPrefix = i -> "Val: " + i;
Function<Integer, String> multiplyAndString = i -> "Mul: " + (i * 2);

Kind<StreamKind.Witness, Function<Integer, String>> functions =
    STREAM.widen(Stream.of(addPrefix, multiplyAndString));
Kind<StreamKind.Witness, Integer> values = STREAM.widen(Stream.of(10, 20));

Kind<StreamKind.Witness, String> appliedResults = streamMonad.ap(functions, values);

// Lazy - collects when terminal operation is performed
List<String> result = STREAM.narrow(appliedResults).collect(Collectors.toList());
System.out.println(result);
// Output: [Val: 10, Val: 20, Mul: 20, Mul: 40]
```
~~~

## StreamOps Utility Documentation

The `StreamOps` class provides a rich set of static utility methods for working with `StreamKind` instances. These operations complement the monadic interface with practical stream manipulation functions.

### Creation Operations

~~~admonish title="Factory Methods"
```java
// Create from varargs
Kind<StreamKind.Witness, T> fromArray(T... elements)

// Create from Iterable
Kind<StreamKind.Witness, T> fromIterable(Iterable<T> iterable)

// Create a range [start, end)
Kind<StreamKind.Witness, Integer> range(int start, int end)

// Create empty stream
Kind<StreamKind.Witness, T> empty()
```

**Examples:**
```java
Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
Kind<StreamKind.Witness, Integer> numbers = range(1, 101); // 1 to 100
Kind<StreamKind.Witness, String> emptyStream = empty();
```
~~~

### Filtering and Selection

~~~admonish title="Filtering Operations"
```java
// Keep only elements matching predicate
Kind<StreamKind.Witness, A> filter(Predicate<A> predicate, Kind<StreamKind.Witness, A> stream)

// Take first n elements
Kind<StreamKind.Witness, A> take(long n, Kind<StreamKind.Witness, A> stream)

// Skip first n elements
Kind<StreamKind.Witness, A> drop(long n, Kind<StreamKind.Witness, A> stream)
```

**Examples:**
```java
Kind<StreamKind.Witness, Integer> numbers = range(1, 101);

// Get only even numbers
Kind<StreamKind.Witness, Integer> evens = filter(n -> n % 2 == 0, numbers);

// Get first 10 elements
Kind<StreamKind.Witness, Integer> first10 = take(10, range(1, 1000));

// Skip first 5 elements
Kind<StreamKind.Witness, Integer> afterFirst5 = drop(5, range(1, 20));
```
~~~

### Combination Operations

~~~admonish title="Combining Streams"
```java
// Concatenate two streams sequentially
Kind<StreamKind.Witness, A> concat(Kind<StreamKind.Witness, A> stream1, Kind<StreamKind.Witness, A> stream2)

// Zip two streams element-wise with combiner function
Kind<StreamKind.Witness, C> zip(Kind<StreamKind.Witness, A> stream1, Kind<StreamKind.Witness, B> stream2, BiFunction<A, B, C> combiner)

// Pair each element with its index (starting from 0)
Kind<StreamKind.Witness, Tuple2<Integer, A>> zipWithIndex(Kind<StreamKind.Witness, A> stream)
```

**Examples:**
```java
Kind<StreamKind.Witness, Integer> first = range(1, 4);   // 1, 2, 3
Kind<StreamKind.Witness, Integer> second = range(10, 13); // 10, 11, 12

// Sequential concatenation
Kind<StreamKind.Witness, Integer> combined = concat(first, second);
// Result: 1, 2, 3, 10, 11, 12

// Element-wise combination
Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
Kind<StreamKind.Witness, Integer> ages = fromArray(25, 30, 35);
Kind<StreamKind.Witness, String> profiles = zip(names, ages,
    (name, age) -> name + " is " + age);
// Result: "Alice is 25", "Bob is 30", "Charlie is 35"

// Index pairing
Kind<StreamKind.Witness, String> items = fromArray("apple", "banana", "cherry");
Kind<StreamKind.Witness, Tuple2<Integer, String>> indexed = zipWithIndex(items);
// Result: (0, "apple"), (1, "banana"), (2, "cherry")
```
~~~

### Terminal Operations

~~~admonish title="Consuming Streams"
```java
// Collect to List
List<A> toList(Kind<StreamKind.Witness, A> stream)

// Collect to Set
Set<A> toSet(Kind<StreamKind.Witness, A> stream)

// Execute side effect for each element
void forEach(Consumer<A> action, Kind<StreamKind.Witness, A> stream)
```

**Examples:**
```java
Kind<StreamKind.Witness, Integer> numbers = range(1, 6);

// Collect to List
List<Integer> numberList = toList(numbers); // [1, 2, 3, 4, 5]

// Collect to Set (removes duplicates)
Kind<StreamKind.Witness, String> words = fromArray("a", "b", "a", "c");
Set<String> uniqueWords = toSet(words); // {"a", "b", "c"}

// Execute side effects
Kind<StreamKind.Witness, String> messages = fromArray("Hello", "World");
forEach(System.out::println, messages);
// Prints:
// Hello
// World
```
~~~

### Side Effects and Debugging

~~~admonish title="Observation Operations"
```java
// Execute side effect for each element while passing through
Kind<StreamKind.Witness, A> tap(Consumer<A> action, Kind<StreamKind.Witness, A> stream)
```

**Example:**
```java
List<String> log = new ArrayList<>();

Kind<StreamKind.Witness, Integer> pipeline = tap(
    n -> log.add("Processing: " + n),
    StreamMonad.INSTANCE.map(n -> n * 2, range(1, 4))
);

// Side effects haven't executed yet (lazy)
System.out.println("Log size: " + log.size()); // 0

// Terminal operation triggers execution
List<Integer> result = toList(pipeline);
System.out.println("Log size: " + log.size()); // 3
System.out.println("Log: " + log); // [Processing: 2, Processing: 4, Processing: 6]
System.out.println("Result: " + result); // [2, 4, 6]
```
~~~

## Important Constraints: Single-Use Semantics

~~~admonish danger title="Critical: Stream Single-Use Limitation"
Unlike `List` or `Optional`, Java Streams can only be consumed **once**. This is a fundamental characteristic of `java.util.stream.Stream` that is preserved in the HKT representation.

**What This Means:**
- Once you perform a terminal operation on a stream (including `narrow()` followed by collection), that stream is consumed
- Attempting to reuse a consumed stream throws `IllegalStateException`
- Each `Kind<StreamKind.Witness, A>` instance can only flow through one pipeline to completion

**Correct Approach:**
```java
// Create fresh stream for each independent operation
Kind<StreamKind.Witness, Integer> stream1 = range(1, 4);
List<Integer> result1 = toList(stream1); // ✓ First use

Kind<StreamKind.Witness, Integer> stream2 = range(1, 4); // Create new stream
List<Integer> result2 = toList(stream2); // ✓ Second use with fresh stream
```

**Incorrect Approach:**
```java
// DON'T DO THIS - Will throw IllegalStateException
Kind<StreamKind.Witness, Integer> stream = range(1, 4);
List<Integer> result1 = toList(stream);  // ✓ First use
List<Integer> result2 = toList(stream);  // ✗ ERROR: stream already consumed!
```

**Design Implications:**
- Don't store `StreamKind` instances in fields for reuse
- Create streams on-demand when needed
- Use factory methods or suppliers to generate fresh streams
- Consider using `List` if you need to process data multiple times
~~~

## Practical Example: Complete Usage

~~~admonish example title="Comprehensive Stream Example"
Here's a complete example demonstrating various Stream operations:

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamMonad;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.stream.StreamOps.*;

import java.util.List;
import java.util.function.Function;

public class StreamUsageExample {
   public static void main(String[] args) {
      StreamMonad streamMonad = StreamMonad.INSTANCE;

      // 1. Create a StreamKind using range
      Kind<StreamKind.Witness, Integer> numbersKind = range(1, 11); // 1 through 10

      // 2. Use map to transform (lazy)
      Function<Integer, String> numberToString = n -> "Item-" + n;
      Kind<StreamKind.Witness, String> stringsKind = streamMonad.map(numberToString, numbersKind);

      System.out.println("Mapped: " + toList(stringsKind));
      // Expected: [Item-1, Item-2, Item-3, ..., Item-10]

      // 3. Create fresh stream for flatMap example
      Kind<StreamKind.Witness, Integer> numbersKind2 = range(1, 6);

      // flatMap: duplicate even numbers, skip odd numbers
      Function<Integer, Kind<StreamKind.Witness, Integer>> duplicateIfEven = n -> {
         if (n % 2 == 0) {
            return fromArray(n, n); // Duplicate even numbers
         } else {
            return streamMonad.zero(); // Skip odd numbers
         }
      };

      Kind<StreamKind.Witness, Integer> flatMappedKind = streamMonad.flatMap(duplicateIfEven, numbersKind2);
      System.out.println("FlatMapped: " + toList(flatMappedKind));
      // Expected: [2, 2, 4, 4]

      // 4. Use of to create singleton
      Kind<StreamKind.Witness, String> singleValueKind = streamMonad.of("hello world");
      System.out.println("From 'of': " + toList(singleValueKind));
      // Expected: [hello world]

      // 5. Use zero to create empty stream
      Kind<StreamKind.Witness, String> emptyKind = streamMonad.zero();
      System.out.println("From 'zero': " + toList(emptyKind));
      // Expected: []

      // 6. StreamOps: filter and take
      Kind<StreamKind.Witness, Integer> largeRange = range(1, 101);
      Kind<StreamKind.Witness, Integer> evensFirst10 = take(10, filter(n -> n % 2 == 0, largeRange));
      System.out.println("First 10 evens: " + toList(evensFirst10));
      // Expected: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20]

      // 7. Zip two streams
      Kind<StreamKind.Witness, String> names = fromArray("Alice", "Bob", "Charlie");
      Kind<StreamKind.Witness, Integer> scores = fromArray(95, 87, 92);
      Kind<StreamKind.Witness, String> results = zip(names, scores,
          (name, score) -> name + ": " + score);

      System.out.println("Results: " + toList(results));
      // Expected: [Alice: 95, Bob: 87, Charlie: 92]

      // 8. Demonstrating single-use constraint
      Kind<StreamKind.Witness, Integer> streamOnce = range(1, 4);
      List<Integer> firstUse = toList(streamOnce);
      System.out.println("First use: " + firstUse);
      // Expected: [1, 2, 3]

      // Must create new stream for second use
      Kind<StreamKind.Witness, Integer> streamTwice = range(1, 4);
      List<Integer> secondUse = toList(streamTwice);
      System.out.println("Second use (new stream): " + secondUse);
      // Expected: [1, 2, 3]
   }
}
```
~~~

## When to Use StreamMonad

**Choose `StreamMonad` when:**
- Processing large datasets where lazy evaluation provides memory efficiency
- Working with potentially infinite sequences
- Building complex data transformation pipelines
- You need intermediate laziness and only want to materialise results at the end
- Single-pass processing is sufficient for your use case

**Choose `ListMonad` instead when:**
- You need to process the same data multiple times
- Random access to elements is required
- The entire dataset fits comfortably in memory
- You need to store the result for later reuse

**Key Difference:** `List` is eager and reusable; `Stream` is lazy and single-use.

---

**Previous:** [State](state_monad.md)
**Next:** [Trampoline](trampoline_monad.md)

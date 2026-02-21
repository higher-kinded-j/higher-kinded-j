// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.PathOps;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;

/**
 * Examples demonstrating VStreamPath for lazy, pull-based streaming on virtual threads.
 *
 * <p>VStreamPath wraps VStream to provide a fluent Effect Path API for composing lazy stream
 * pipelines. All intermediate operations are lazy; execution is deferred until a terminal operation
 * (toList, fold, count, etc.) is called.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Creating VStreamPath instances via Path factory methods
 *   <li>Composing lazy pipelines with map, filter, flatMap, take, drop
 *   <li>Terminal operations that bridge to VTaskPath for single-value results
 *   <li>Zipping multiple streams positionally
 *   <li>Optics focus bridge for navigating into stream elements
 *   <li>Converting to other path types (StreamPath, ListPath, NonDetPath)
 *   <li>Sequence and traverse operations via PathOps
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.VStreamPathExample}
 *
 * @see org.higherkindedj.hkt.effect.VStreamPath
 * @see org.higherkindedj.hkt.vstream.VStream
 */
public class VStreamPathExample {

  public static void main(String[] args) {
    System.out.println("=== VStreamPath: Lazy Streaming on Virtual Threads ===\n");

    factoryMethods();
    lazyComposition();
    terminalOperations();
    zippingStreams();
    focusBridge();
    conversions();
    sequenceAndTraverse();
    realWorldPipeline();
  }

  // ============================================================
  // Factory Methods
  // ============================================================

  private static void factoryMethods() {
    System.out.println("--- Factory Methods ---\n");

    // From varargs
    VStreamPath<String> names = Path.vstreamOf("Alice", "Bob", "Charlie");
    System.out.println("vstreamOf: " + names.toList().unsafeRun());

    // From a list
    VStreamPath<Integer> fromList = Path.vstreamFromList(List.of(10, 20, 30));
    System.out.println("vstreamFromList: " + fromList.toList().unsafeRun());

    // Single element
    VStreamPath<Integer> single = Path.vstreamPure(42);
    System.out.println("vstreamPure: " + single.toList().unsafeRun());

    // Empty stream
    VStreamPath<Integer> empty = Path.vstreamEmpty();
    System.out.println("vstreamEmpty: " + empty.toList().unsafeRun());

    // Integer range [1, 6)
    VStreamPath<Integer> range = Path.vstreamRange(1, 6);
    System.out.println("vstreamRange(1, 6): " + range.toList().unsafeRun());

    // Infinite stream, take first 5
    VStreamPath<Integer> naturals = Path.vstreamIterate(1, n -> n + 1).take(5);
    System.out.println("vstreamIterate (first 5): " + naturals.toList().unsafeRun());

    // Effectful unfold
    VStreamPath<Integer> unfolded =
        Path.vstreamUnfold(
            1,
            state ->
                VTask.succeed(
                    state > 3
                        ? Optional.empty()
                        : Optional.of(new VStream.Seed<>(state, state + 1))));
    System.out.println("vstreamUnfold (1..3): " + unfolded.toList().unsafeRun());

    System.out.println();
  }

  // ============================================================
  // Lazy Composition
  // ============================================================

  private static void lazyComposition() {
    System.out.println("--- Lazy Composition ---\n");

    // Map: transform elements
    VStreamPath<String> upper = Path.vstreamOf("hello", "world").map(String::toUpperCase);
    System.out.println("map: " + upper.toList().unsafeRun());

    // Filter: keep matching elements
    VStreamPath<Integer> evens = Path.vstreamRange(1, 11).filter(n -> n % 2 == 0);
    System.out.println("filter (evens): " + evens.toList().unsafeRun());

    // FlatMap: expand each element into a sub-stream
    VStreamPath<Integer> expanded = Path.vstreamOf(1, 2, 3).flatMap(n -> Path.vstreamOf(n, n * 10));
    System.out.println("flatMap: " + expanded.toList().unsafeRun());

    // Take, drop, takeWhile, dropWhile
    VStreamPath<Integer> base = Path.vstreamRange(1, 11);
    System.out.println("take(3): " + base.take(3).toList().unsafeRun());
    System.out.println("drop(7): " + base.drop(7).toList().unsafeRun());
    System.out.println("takeWhile(<5): " + base.takeWhile(n -> n < 5).toList().unsafeRun());
    System.out.println("dropWhile(<5): " + base.dropWhile(n -> n < 5).toList().unsafeRun());

    // Distinct
    VStreamPath<Integer> distinct = Path.vstreamOf(1, 2, 2, 3, 1, 3, 4).distinct();
    System.out.println("distinct: " + distinct.toList().unsafeRun());

    // Concat
    VStreamPath<Integer> combined = Path.vstreamOf(1, 2).concat(Path.vstreamOf(3, 4));
    System.out.println("concat: " + combined.toList().unsafeRun());

    // Complex pipeline: all operations are lazy until toList() triggers execution
    List<String> pipeline =
        Path.vstreamRange(1, 100)
            .filter(n -> n % 2 == 0)
            .map(n -> "Even: " + n)
            .take(5)
            .toList()
            .unsafeRun();
    System.out.println("pipeline: " + pipeline);

    System.out.println();
  }

  // ============================================================
  // Terminal Operations
  // ============================================================

  private static void terminalOperations() {
    System.out.println("--- Terminal Operations ---\n");

    VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3, 4, 5);

    // Fold with identity and operator
    Integer sum = stream.fold(0, Integer::sum).unsafeRun();
    System.out.println("fold (sum): " + sum);

    // FoldLeft with accumulator
    String csv =
        stream
            .foldLeft("", (acc, n) -> acc.isEmpty() ? String.valueOf(n) : acc + "," + n)
            .unsafeRun();
    System.out.println("foldLeft (csv): " + csv);

    // FoldMap with monoid
    Monoid<Integer> sumMonoid =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };
    Integer doubleSum =
        Path.vstreamOf("ab", "cde", "f").foldMap(sumMonoid, String::length).unsafeRun();
    System.out.println("foldMap (string lengths): " + doubleSum);

    // Count
    Long count = stream.count().unsafeRun();
    System.out.println("count: " + count);

    // Exists and forAll
    Boolean hasEven = stream.exists(n -> n % 2 == 0).unsafeRun();
    Boolean allPositive = stream.forAll(n -> n > 0).unsafeRun();
    System.out.println("exists (even): " + hasEven);
    System.out.println("forAll (positive): " + allPositive);

    // Find
    Optional<Integer> firstBig = stream.find(n -> n > 3).unsafeRun();
    System.out.println("find (>3): " + firstBig);

    // Head and last
    Optional<Integer> head = stream.headOption().unsafeRun();
    Optional<Integer> last = stream.lastOption().unsafeRun();
    System.out.println("headOption: " + head);
    System.out.println("lastOption: " + last);

    System.out.println();
  }

  // ============================================================
  // Zipping Streams
  // ============================================================

  private static void zippingStreams() {
    System.out.println("--- Zipping Streams ---\n");

    VStreamPath<Integer> nums = Path.vstreamOf(1, 2, 3);
    VStreamPath<String> labels = Path.vstreamOf("a", "b", "c");

    // zipWith: pairs positionally
    VStreamPath<String> zipped = nums.zipWith(labels, (n, s) -> n + s);
    System.out.println("zipWith: " + zipped.toList().unsafeRun());

    // zipWith3: three-way zip
    VStreamPath<Boolean> flags = Path.vstreamOf(true, false, true);
    VStreamPath<String> zipped3 = nums.zipWith3(labels, flags, (n, s, b) -> n + s + "(" + b + ")");
    System.out.println("zipWith3: " + zipped3.toList().unsafeRun());

    // Stops at shortest
    VStreamPath<Integer> long_ = Path.vstreamRange(1, 100);
    VStreamPath<String> short_ = Path.vstreamOf("x", "y");
    VStreamPath<String> shortened = long_.zipWith(short_, (n, s) -> n + s);
    System.out.println("zipWith (stops at shortest): " + shortened.toList().unsafeRun());

    System.out.println();
  }

  // ============================================================
  // Focus Bridge (Optics Integration)
  // ============================================================

  record Person(String name, int age) {}

  private static void focusBridge() {
    System.out.println("--- Focus Bridge ---\n");

    Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age));
    Lens<Person, Integer> ageLens = Lens.of(Person::age, (p, a) -> new Person(p.name, a));
    FocusPath<Person, String> nameFocus = FocusPath.of(nameLens);
    FocusPath<Person, Integer> ageFocus = FocusPath.of(ageLens);

    VStreamPath<Person> people =
        Path.vstreamOf(new Person("Alice", 30), new Person("Bob", 25), new Person("Charlie", 35));

    // FocusPath: extract field from every element
    List<String> names = people.focus(nameFocus).toList().unsafeRun();
    System.out.println("focus (names): " + names);

    // Combine focus with stream operations
    List<Integer> adultAges = people.focus(ageFocus).filter(age -> age >= 30).toList().unsafeRun();
    System.out.println("focus + filter (ages >= 30): " + adultAges);

    System.out.println();
  }

  // ============================================================
  // Conversions
  // ============================================================

  private static void conversions() {
    System.out.println("--- Conversions ---\n");

    VStreamPath<Integer> stream = Path.vstreamOf(1, 2, 3);

    // To single elements via VTaskPath
    Integer first = stream.first().unsafeRun();
    Integer last = stream.last().unsafeRun();
    System.out.println("first: " + first);
    System.out.println("last: " + last);

    // To other path types (materialises the stream)
    StreamPath<Integer> streamPath = stream.toStreamPath();
    System.out.println("toStreamPath: " + streamPath.toList());

    ListPath<Integer> listPath = stream.toListPath();
    System.out.println("toListPath: " + listPath.run());

    NonDetPath<Integer> nonDetPath = stream.toNonDetPath();
    System.out.println("toNonDetPath: " + nonDetPath.run());

    System.out.println();
  }

  // ============================================================
  // Concat, Traverse, and Flatten (PathOps)
  // ============================================================

  private static void sequenceAndTraverse() {
    System.out.println("--- Concat, Traverse, and Flatten (PathOps) ---\n");

    // Concat: List<VStreamPath<A>> -> VStreamPath<A> (sequential concatenation)
    List<VStreamPath<Integer>> streams =
        List.of(Path.vstreamOf(1, 2), Path.vstreamOf(10, 20), Path.vstreamOf(100, 200));
    VStreamPath<Integer> concatenated = PathOps.concatVStream(streams);
    System.out.println("concatVStream: " + concatenated.toList().unsafeRun());

    // Traverse: List<A> -> (A -> VStreamPath<B>) -> VStreamPath<B> (map + concat)
    List<Integer> inputs = List.of(1, 2, 3);
    VStreamPath<String> traversed =
        PathOps.traverseVStream(inputs, n -> Path.vstreamOf("v" + n, "w" + n));
    System.out.println("traverseVStream: " + traversed.toList().unsafeRun());

    // Flatten: VStreamPath<VStreamPath<A>> -> VStreamPath<A>
    VStreamPath<VStreamPath<Integer>> nested =
        Path.vstreamOf(Path.vstreamOf(10, 20), Path.vstreamOf(30));
    VStreamPath<Integer> flattened = PathOps.flattenVStream(nested);
    System.out.println("flattenVStream: " + flattened.toList().unsafeRun());

    System.out.println();
  }

  // ============================================================
  // Real-World Pipeline
  // ============================================================

  record LogEntry(String level, String message, long timestamp) {}

  private static void realWorldPipeline() {
    System.out.println("--- Real-World Pipeline: Log Processing ---\n");

    // Simulate a stream of log entries
    VStreamPath<LogEntry> logs =
        Path.vstreamOf(
            new LogEntry("INFO", "Application started", 1000),
            new LogEntry("DEBUG", "Connecting to database", 1001),
            new LogEntry("INFO", "User login: alice", 1002),
            new LogEntry("WARN", "Slow query detected", 1003),
            new LogEntry("ERROR", "Connection timeout", 1004),
            new LogEntry("INFO", "User login: bob", 1005),
            new LogEntry("DEBUG", "Cache hit ratio: 0.85", 1006),
            new LogEntry("ERROR", "Disk space low", 1007),
            new LogEntry("INFO", "Batch job completed", 1008));

    // Pipeline: extract error messages
    List<String> errorMessages =
        logs.filter(log -> "ERROR".equals(log.level())).map(LogEntry::message).toList().unsafeRun();
    System.out.println("Errors: " + errorMessages);

    // Pipeline: count entries by level
    Long warnAndAbove =
        logs.filter(log -> "WARN".equals(log.level()) || "ERROR".equals(log.level()))
            .count()
            .unsafeRun();
    System.out.println("Warnings and errors: " + warnAndAbove);

    // Pipeline: check if any errors exist
    Boolean hasErrors = logs.exists(log -> "ERROR".equals(log.level())).unsafeRun();
    System.out.println("Has errors: " + hasErrors);

    // Pipeline: find first error
    Optional<String> firstError =
        logs.filter(log -> "ERROR".equals(log.level()))
            .map(LogEntry::message)
            .headOption()
            .unsafeRun();
    System.out.println("First error: " + firstError);

    // Pipeline: build summary using fold
    String summary =
        logs.filter(log -> "INFO".equals(log.level()))
            .map(LogEntry::message)
            .foldLeft("", (acc, msg) -> acc.isEmpty() ? msg : acc + "; " + msg)
            .unsafeRun();
    System.out.println("Info summary: " + summary);

    System.out.println();
  }
}

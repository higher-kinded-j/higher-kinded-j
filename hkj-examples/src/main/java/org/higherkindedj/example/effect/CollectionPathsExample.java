// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.effect;

import java.util.List;
import java.util.stream.Stream;
import org.higherkindedj.hkt.effect.ListPath;
import org.higherkindedj.hkt.effect.StreamPath;

/**
 * Examples demonstrating ListPath and StreamPath for collection-based effects.
 *
 * <p>This example shows:
 *
 * <ul>
 *   <li>{@link ListPath} for batch operations with positional zipping
 *   <li>{@link StreamPath} for lazy sequence processing
 *   <li>Transformation pipelines on collections
 *   <li>FlatMap semantics (concatenation) vs positional zip
 * </ul>
 *
 * <p>Run with: {@code ./gradlew :hkj-examples:run
 * -PmainClass=org.higherkindedj.example.effect.CollectionPathsExample}
 */
public class CollectionPathsExample {

  public static void main(String[] args) {
    System.out.println("=== Effect Path API: Collection Paths ===\n");

    listPathBasics();
    listPathZipping();
    listPathFlatMap();
    streamPathBasics();
    streamPathLazyEvaluation();
    streamPathInfiniteSequences();
    practicalPatterns();
  }

  // ===== ListPath Examples =====

  private static void listPathBasics() {
    System.out.println("--- ListPath Basics ---");

    // Creating ListPaths
    ListPath<Integer> fromList = ListPath.of(List.of(1, 2, 3, 4, 5));
    ListPath<String> fromVarargs = ListPath.of("apple", "banana", "cherry");
    ListPath<Integer> single = ListPath.pure(42);
    ListPath<Integer> empty = ListPath.empty();

    System.out.println("From list: " + fromList.run());
    System.out.println("From varargs: " + fromVarargs.run());
    System.out.println("Single: " + single.run());
    System.out.println("Empty: " + empty.run());

    // Mapping
    ListPath<Integer> doubled = fromList.map(n -> n * 2);
    System.out.println("Doubled: " + doubled.run()); // [2, 4, 6, 8, 10]

    // Filtering
    ListPath<Integer> evens = fromList.filter(n -> n % 2 == 0);
    System.out.println("Evens: " + evens.run()); // [2, 4]

    // Peek for side effects
    fromList.peek(n -> System.out.print(n + " ")).map(n -> n * 2);
    System.out.println();

    System.out.println();
  }

  private static void listPathZipping() {
    System.out.println("--- ListPath Positional Zipping ---");

    // Positional zip combines corresponding elements
    ListPath<Integer> numbers = ListPath.of(1, 2, 3);
    ListPath<String> letters = ListPath.of("a", "b", "c");

    ListPath<String> zipped = numbers.zipWith(letters, (n, s) -> n + s);
    System.out.println("Zipped: " + zipped.run()); // [1a, 2b, 3c]

    // Shortest list wins
    ListPath<Integer> shorter = ListPath.of(1, 2);
    ListPath<String> longer = ListPath.of("a", "b", "c", "d");

    ListPath<String> truncated = shorter.zipWith(longer, (n, s) -> n + s);
    System.out.println("Truncated: " + truncated.run()); // [1a, 2b]

    // Three-way zip
    ListPath<Integer> nums = ListPath.of(1, 2, 3);
    ListPath<String> ops = ListPath.of("+", "-", "*");
    ListPath<Integer> vals = ListPath.of(10, 20, 30);

    ListPath<String> expressions = nums.zipWith3(ops, vals, (n, op, v) -> n + " " + op + " " + v);
    System.out.println("Expressions: " + expressions.run());
    // [1 + 10, 2 - 20, 3 * 30]

    // Practical: Combining parallel data structures
    ListPath<String> names = ListPath.of("Alice", "Bob", "Charlie");
    ListPath<Integer> ages = ListPath.of(30, 25, 35);
    ListPath<String> cities = ListPath.of("London", "Paris", "Berlin");

    record Person(String name, int age, String city) {}

    ListPath<Person> people = names.zipWith3(ages, cities, Person::new);
    people.run().forEach(p -> System.out.println("  " + p));

    System.out.println();
  }

  private static void listPathFlatMap() {
    System.out.println("--- ListPath FlatMap (via) ---");

    // via performs flatMap - results are concatenated
    ListPath<Integer> numbers = ListPath.of(1, 2, 3);

    // Each number expands to itself and its double
    ListPath<Integer> expanded = numbers.via(n -> ListPath.of(n, n * 10));
    System.out.println("Expanded: " + expanded.run()); // [1, 10, 2, 20, 3, 30]

    // Practical: Processing nested data
    record Order(String id, List<String> items) {}

    List<Order> orders =
        List.of(
            new Order("ord-1", List.of("Widget", "Gadget")),
            new Order("ord-2", List.of("Gizmo")),
            new Order("ord-3", List.of("Thingamajig", "Doohickey", "Whatsit")));

    ListPath<String> allItems = ListPath.of(orders).via(order -> ListPath.of(order.items()));

    System.out.println("All items: " + allItems.run());
    // [Widget, Gadget, Gizmo, Thingamajig, Doohickey, Whatsit]

    // Chained flatMaps - flatten a list of lists
    ListPath<List<Integer>> matrix = ListPath.of(List.of(1, 2), List.of(3, 4), List.of(5, 6));
    ListPath<Integer> flattened = matrix.via(ListPath::of);
    System.out.println("Flattened matrix: " + flattened.run());
    // [1, 2, 3, 4, 5, 6]

    System.out.println();
  }

  // ===== StreamPath Examples =====

  private static void streamPathBasics() {
    System.out.println("--- StreamPath Basics ---");

    // Creating StreamPaths
    StreamPath<Integer> fromStream = StreamPath.of(Stream.of(1, 2, 3, 4, 5));
    StreamPath<String> fromList = StreamPath.fromList(List.of("x", "y", "z"));
    StreamPath<Integer> single = StreamPath.pure(99);
    StreamPath<Integer> empty = StreamPath.empty();

    System.out.println("From stream: " + fromStream.toList());
    System.out.println("From list: " + fromList.toList());
    System.out.println("Single: " + single.toList());
    System.out.println("Empty: " + empty.toList());

    // Mapping and filtering
    StreamPath<Integer> processed = fromStream.map(n -> n * n).filter(n -> n > 5);
    System.out.println("Squares > 5: " + processed.toList()); // [9, 16, 25]

    // Multiple terminal operations (StreamPath supports this unlike raw Stream)
    StreamPath<Integer> nums = StreamPath.of(Stream.of(1, 2, 3, 4, 5));
    System.out.println("Count: " + nums.count());
    System.out.println("Sum: " + nums.toList().stream().mapToInt(i -> i).sum());
    System.out.println("First: " + nums.headOption()); // Can call again!

    System.out.println();
  }

  private static void streamPathLazyEvaluation() {
    System.out.println("--- StreamPath Lazy Evaluation ---");

    // Operations are lazy until terminal operation
    System.out.println("Building pipeline...");

    StreamPath<Integer> pipeline =
        StreamPath.of(Stream.of(1, 2, 3, 4, 5))
            .map(
                n -> {
                  System.out.println("  Mapping: " + n);
                  return n * 2;
                })
            .filter(
                n -> {
                  System.out.println("  Filtering: " + n);
                  return n > 4;
                });

    System.out.println("Pipeline created (nothing executed yet)");

    System.out.println("Taking first 2...");
    List<Integer> result = pipeline.take(2).toList();
    System.out.println("Result: " + result);

    System.out.println();
  }

  private static void streamPathInfiniteSequences() {
    System.out.println("--- StreamPath Infinite Sequences ---");

    // Infinite sequence of natural numbers
    StreamPath<Integer> naturals = StreamPath.iterate(1, n -> n + 1);

    // Take first 10
    List<Integer> firstTen = naturals.take(10).toList();
    System.out.println("First 10 naturals: " + firstTen);

    // Infinite Fibonacci
    record FibPair(int a, int b) {}

    StreamPath<Integer> fibonacci =
        StreamPath.iterate(new FibPair(0, 1), p -> new FibPair(p.b(), p.a() + p.b()))
            .map(FibPair::a);

    List<Integer> first15Fib = fibonacci.take(15).toList();
    System.out.println("First 15 Fibonacci: " + first15Fib);

    // Powers of 2
    StreamPath<Long> powersOf2 = StreamPath.iterate(1L, n -> n * 2);
    System.out.println("Powers of 2: " + powersOf2.take(10).toList());

    // Generate random numbers (limited!)
    StreamPath<Double> randoms = StreamPath.generate(Math::random);
    System.out.println(
        "5 random numbers: "
            + randoms.take(5).toList().stream().map(d -> String.format("%.3f", d)).toList());

    System.out.println();
  }

  // ===== Practical Patterns =====

  private static void practicalPatterns() {
    System.out.println("--- Practical Patterns ---");

    // Pattern 1: Batch processing with progress
    System.out.println("Batch processing:");

    record Task(String id, String description) {}

    ListPath<Task> tasks =
        ListPath.of(
            new Task("T1", "Setup"),
            new Task("T2", "Build"),
            new Task("T3", "Test"),
            new Task("T4", "Deploy"));

    ListPath<String> processed =
        tasks
            .peek(t -> System.out.println("  Processing: " + t.id()))
            .map(t -> t.id() + " completed");

    System.out.println("Results: " + processed.run());

    // Pattern 2: Data transformation pipeline
    System.out.println("\nData pipeline:");

    record RawRecord(String name, String value) {}
    record CleanRecord(String name, int value) {}

    ListPath<RawRecord> rawData =
        ListPath.of(
            new RawRecord("alpha", "10"),
            new RawRecord("beta", "invalid"),
            new RawRecord("gamma", "30"),
            new RawRecord("delta", "40"));

    ListPath<CleanRecord> cleanData =
        rawData
            .filter(r -> r.value().matches("\\d+")) // Filter parseable
            .map(r -> new CleanRecord(r.name(), Integer.parseInt(r.value())));

    System.out.println("Clean records: " + cleanData.run());

    // Pattern 3: Streaming aggregation
    System.out.println("\nStreaming aggregation:");

    StreamPath<Integer> salesData = StreamPath.of(Stream.of(100, 250, 175, 300, 225, 150));

    int total = salesData.toList().stream().mapToInt(i -> i).sum();
    double average = salesData.toList().stream().mapToInt(i -> i).average().orElse(0);
    int max = salesData.toList().stream().mapToInt(i -> i).max().orElse(0);

    System.out.println("  Total: " + total);
    System.out.println("  Average: " + average);
    System.out.println("  Max: " + max);

    System.out.println();
  }
}

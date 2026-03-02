// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.basic.vstream;

import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.effect.NaturalTransformation;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.extensions.StreamTraversal;

/**
 * Demonstrates advanced VStream features: resource-safe streaming with bracket and onFinalize,
 * StreamTraversal for lazy optics, reactive interop with Flow.Publisher, natural transformations
 * between stream types, and the VStreamContext Layer 2 API.
 *
 * <p>These features build on the core VStream operations shown in {@link VStreamBasicExample} and
 * provide integration with the wider Higher-Kinded-J ecosystem.
 *
 * <p>See <a href="https://higher-kinded-j.github.io/usage-guide.html">Usage Guide</a>
 */
public class VStreamAdvancedExample {

  public static void main(String[] args) {
    VStreamAdvancedExample example = new VStreamAdvancedExample();

    System.out.println("=== Resource-Safe Streaming (bracket) ===");
    example.bracketResourceManagement();

    System.out.println("\n=== onFinalize for Cleanup ===");
    example.onFinalizeCleanup();

    System.out.println("\n=== StreamTraversal (Lazy Optics) ===");
    example.streamTraversalBasics();

    System.out.println("\n=== StreamTraversal Composition ===");
    example.streamTraversalComposition();

    System.out.println("\n=== Reactive Interop (Flow.Publisher) ===");
    example.reactiveInterop();

    System.out.println("\n=== Natural Transformations ===");
    example.naturalTransformations();

    System.out.println("\n=== VStreamContext (Layer 2 API) ===");
    example.vstreamContext();
  }

  /** Demonstrates bracket for resource-safe streaming with guaranteed cleanup. */
  public void bracketResourceManagement() {
    // Simulate a resource that tracks open/closed state
    AtomicBoolean resourceOpen = new AtomicBoolean(false);
    AtomicReference<String> log = new AtomicReference<>("");

    VStream<String> stream =
        VStream.bracket(
            // Acquire: open the resource
            VTask.of(
                () -> {
                  resourceOpen.set(true);
                  log.set(log.get() + "opened ");
                  return "resource-handle";
                }),
            // Use: produce a stream from the resource
            handle -> VStream.of(handle + "-item1", handle + "-item2", handle + "-item3"),
            // Release: close the resource (guaranteed)
            handle ->
                VTask.exec(
                    () -> {
                      resourceOpen.set(false);
                      log.set(log.get() + "closed");
                    }));

    // Resource not yet acquired (bracket is lazy)
    System.out.println("Before pull - resource open: " + resourceOpen.get());

    // Consume the stream - resource acquired and released automatically
    List<String> items = stream.toList().run();
    System.out.println("Items: " + items);
    System.out.println("After consumption - resource open: " + resourceOpen.get());
    System.out.println("Lifecycle log: " + log.get());

    // Partial consumption also releases the resource
    AtomicBoolean partialOpen = new AtomicBoolean(false);
    VStream<Integer> partialStream =
        VStream.bracket(
            VTask.of(
                () -> {
                  partialOpen.set(true);
                  return 42;
                }),
            _ -> VStream.of(1, 2, 3, 4, 5),
            _ -> VTask.exec(() -> partialOpen.set(false)));

    // Only take 2 elements - resource still released
    List<Integer> partial = partialStream.take(2).toList().run();
    System.out.println("Partial consumption (take 2): " + partial);
    System.out.println("Resource released after partial: " + !partialOpen.get());
  }

  /** Demonstrates onFinalize for attaching cleanup actions to any stream. */
  public void onFinalizeCleanup() {
    AtomicReference<String> log = new AtomicReference<>("");

    VStream<String> stream =
        VStream.of("a", "b", "c").onFinalize(VTask.exec(() -> log.set(log.get() + "finalised")));

    List<String> result = stream.toList().run();
    System.out.println("Elements: " + result);
    System.out.println("Finaliser ran: " + log.get());

    // Multiple finalisers compose
    AtomicReference<String> multiLog = new AtomicReference<>("");
    VStream<Integer> multiStream =
        VStream.of(1, 2, 3)
            .onFinalize(VTask.exec(() -> multiLog.set(multiLog.get() + "first ")))
            .onFinalize(VTask.exec(() -> multiLog.set(multiLog.get() + "second")));

    multiStream.toList().run();
    System.out.println("Multiple finalisers: " + multiLog.get());
  }

  /** Demonstrates StreamTraversal for lazy element access and modification. */
  public void streamTraversalBasics() {
    // StreamTraversal for VStream elements
    StreamTraversal<VStream<Integer>, Integer> vstreamST = StreamTraversal.forVStream();

    VStream<Integer> source = VStream.of(1, 2, 3, 4, 5);

    // stream(): lazy access to all elements
    List<Integer> elements = vstreamST.stream(source).toList().run();
    System.out.println("Stream elements: " + elements);

    // modify(): transform all elements
    VStream<Integer> doubled = vstreamST.modify(x -> x * 2, source);
    System.out.println("Modified (doubled): " + doubled.toList().run());

    // modifyVTask(): effectful transformation on virtual threads
    VTask<VStream<Integer>> enriched = vstreamST.modifyVTask(x -> VTask.of(() -> x + 100), source);
    System.out.println("Effectful modify: " + enriched.run().toList().run());

    // StreamTraversal for List elements
    StreamTraversal<List<String>, String> listST = StreamTraversal.forList();
    List<String> names = List.of("alice", "bob", "charlie");

    List<String> uppercased = listST.modify(String::toUpperCase, names);
    System.out.println("List modify: " + uppercased);
  }

  /** Demonstrates StreamTraversal composition with Lens and other StreamTraversals. */
  public void streamTraversalComposition() {
    record Person(String name, int age) {}

    // Lens for Person.name
    Lens<Person, String> nameLens = Lens.of(Person::name, (p, n) -> new Person(n, p.age()));

    // Compose: list of persons -> stream of names
    StreamTraversal<List<Person>, Person> listST = StreamTraversal.forList();
    StreamTraversal<List<Person>, String> namesTraversal = listST.andThen(nameLens);

    List<Person> people = List.of(new Person("alice", 30), new Person("bob", 25));

    // Stream names lazily
    List<String> names = namesTraversal.stream(people).toList().run();
    System.out.println("Names: " + names);

    // Modify names through the composition
    List<Person> uppercased = namesTraversal.modify(String::toUpperCase, people);
    System.out.println("Uppercased: " + uppercased);

    // Compose two StreamTraversals: list of lists -> stream of all elements
    StreamTraversal<List<List<Integer>>, List<Integer>> outerST = StreamTraversal.forList();
    StreamTraversal<List<Integer>, Integer> innerST = StreamTraversal.forList();
    StreamTraversal<List<List<Integer>>, Integer> flatST = outerST.andThen(innerST);

    List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3, 4, 5));
    List<Integer> flattened = flatST.stream(nested).toList().run();
    System.out.println("Flattened: " + flattened);
  }

  /** Demonstrates bidirectional conversion between VStream and Flow.Publisher. */
  public void reactiveInterop() {
    // VStream -> Publisher
    VStream<String> stream = VStream.of("alpha", "beta", "gamma");
    Flow.Publisher<String> publisher = VStreamReactive.toPublisher(stream);
    System.out.println("Created Flow.Publisher from VStream");

    // Publisher -> VStream (round-trip)
    VStream<String> fromPub = VStreamReactive.fromPublisher(publisher, 16);
    List<String> roundTrip = fromPub.toList().run();
    System.out.println("Round-trip result: " + roundTrip);

    // Using default buffer size
    VStream<Integer> numbers = VStream.of(1, 2, 3, 4, 5);
    Flow.Publisher<Integer> numPub = VStreamReactive.toPublisher(numbers);
    VStream<Integer> fromNumPub = VStreamReactive.fromPublisher(numPub);
    List<Integer> numResult = fromNumPub.toList().run();
    System.out.println("Default buffer round-trip: " + numResult);
  }

  /** Demonstrates natural transformations between stream types. */
  public void naturalTransformations() {
    // List -> VStream
    NaturalTransformation<ListKind.Witness, VStreamKind.Witness> listToVStream =
        VStreamTransformations.listToVStream();

    Kind<ListKind.Witness, String> listKind = LIST.widen(List.of("hello", "world"));
    Kind<VStreamKind.Witness, String> vstreamKind = listToVStream.apply(listKind);
    VStream<String> vstream = VSTREAM.narrow(vstreamKind);
    System.out.println("List -> VStream: " + vstream.toList().run());

    // VStream -> List
    NaturalTransformation<VStreamKind.Witness, ListKind.Witness> vstreamToList =
        VStreamTransformations.vstreamToList();

    Kind<VStreamKind.Witness, Integer> numbersKind = VSTREAM.widen(VStream.of(10, 20, 30));
    Kind<ListKind.Witness, Integer> listResult = vstreamToList.apply(numbersKind);
    List<Integer> list = LIST.narrow(listResult);
    System.out.println("VStream -> List: " + list);

    // Composition via andThen
    NaturalTransformation<ListKind.Witness, ListKind.Witness> roundTrip =
        listToVStream.andThen(vstreamToList);
    Kind<ListKind.Witness, String> result = roundTrip.apply(listKind);
    System.out.println("Round-trip (List -> VStream -> List): " + LIST.narrow(result));
  }

  /** Demonstrates VStreamContext for HKT-free streaming. */
  public void vstreamContext() {
    // Simple pipeline without HKT types
    List<String> result =
        VStreamContext.range(1, 20).filter(n -> n % 2 == 0).map(n -> "Even: " + n).take(3).toList();
    System.out.println("Pipeline: " + result);

    // FlatMap / via
    List<Integer> flatMapped =
        VStreamContext.fromList(List.of(1, 2, 3))
            .via(x -> VStreamContext.fromList(List.of(x, x * 10)))
            .toList();
    System.out.println("FlatMap: " + flatMapped);

    // Terminal operations
    long count = VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).count();
    System.out.println("Count: " + count);

    boolean hasEven = VStreamContext.fromList(List.of(1, 3, 5, 6)).exists(n -> n % 2 == 0);
    System.out.println("Has even: " + hasEven);

    Integer sum = VStreamContext.fromList(List.of(1, 2, 3, 4)).fold(0, Integer::sum);
    System.out.println("Sum: " + sum);

    // Parallel processing
    List<Integer> parResult =
        VStreamContext.fromList(List.of(1, 2, 3, 4, 5))
            .parEvalMap(2, n -> VTask.of(() -> n * 100))
            .toList();
    System.out.println("Parallel: " + parResult);

    // Chunking
    List<List<Integer>> chunks = VStreamContext.fromList(List.of(1, 2, 3, 4, 5)).chunk(2).toList();
    System.out.println("Chunks: " + chunks);
  }
}

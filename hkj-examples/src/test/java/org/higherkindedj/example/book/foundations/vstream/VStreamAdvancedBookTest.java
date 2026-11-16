// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.vstream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.higherkindedj.example.book.foundations.vstream.VStreamAdvancedBook.LINES;
import static org.higherkindedj.example.book.foundations.vstream.VStreamAdvancedBook.PRICES;
import static org.higherkindedj.example.book.foundations.vstream.VStreamAdvancedBook.STATUSES;
import static org.higherkindedj.example.book.foundations.vstream.VStreamBook.ORDERS;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.vstream;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.vstream.VStreamKindHelper.VSTREAM;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VStreamPath;
import org.higherkindedj.hkt.effect.VStreamTransformations;
import org.higherkindedj.hkt.effect.context.VStreamContext;
import org.higherkindedj.hkt.effect.spi.PathProvider;
import org.higherkindedj.hkt.effect.spi.PathRegistry;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamReactive;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskExecutionException;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.extensions.StreamTraversal;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Advanced Features page makes. */
@DisplayName("VStream advanced features: optics, publishers, conversions and contexts")
class VStreamAdvancedBookTest {

  private static final Duration POLL = Duration.ofMillis(5);
  private static final Duration PATIENCE = Duration.ofSeconds(30);

  @Nested
  @DisplayName("a StreamTraversal")
  class Traversing {

    @Test
    @DisplayName("streams an order's prices, and raises them all by ten per cent")
    void prices() {
      assertThat(PRICES.stream(LINES).toList().run())
          .containsExactly(new BigDecimal("40.00"), new BigDecimal("2.50"));

      List<LineItem> raised = PRICES.modify(VStreamAdvancedBook::withTenPercent, LINES);

      assertThat(raised)
          .extracting(LineItem::price)
          .containsExactly(new BigDecimal("44.00"), new BigDecimal("2.75"));
      assertThat(raised).extracting(LineItem::sku).containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName("over a stream, modify reads the feed only as far as the consumer pulls")
    void statusesAreLazy() {
      VStreamBook.PagedFeed feed = new VStreamBook.PagedFeed(ORDERS);
      VStream<Order> allPaid =
          STATUSES.modify(VStreamAdvancedBook::markPaid, VStreamBook.orders(feed));

      assertThat(feed.fetched()).isEmpty();
      assertThat(allPaid.take(2).map(Order::status).toList().run())
          .containsExactly(OrderStatus.PAID, OrderStatus.PAID);
      assertThat(feed.fetched()).containsExactly(1);
    }

    @Test
    @DisplayName("over an endless stream, modify gives a stream a consumer can take from")
    void endless() {
      StreamTraversal<VStream<Integer>, Integer> each = StreamTraversal.forVStream();

      assertThat(each.modify(n -> n * 10, VStream.iterate(1, n -> n + 1)).take(3).toList().run())
          .containsExactly(10, 20, 30);
    }

    @Test
    @DisplayName("over a stream, modifyVTask's tasks run on the thread that pulls each element")
    void modifyVTaskOverAStream() {
      List<Thread> ran = new CopyOnWriteArrayList<>();
      VTask<VStream<Integer>> modified =
          StreamTraversal.<Integer>forVStream()
              .modifyVTask(
                  n ->
                      VTask.of(
                          () -> {
                            ran.add(Thread.currentThread());
                            return n + 1;
                          }),
                  VStream.of(1, 2, 3));

      VStream<Integer> stream = modified.run();
      assertThat(ran).isEmpty();
      assertThat(stream.toList().run()).containsExactly(2, 3, 4);
      assertThat(ran).hasSize(3).allMatch(thread -> thread == Thread.currentThread());
    }

    @Test
    @DisplayName("over a list, modifyVTask's tasks run in turn, on the thread that runs its task")
    void modifyVTaskOverAList() {
      List<Thread> ran = new CopyOnWriteArrayList<>();
      VTask<List<Integer>> modified =
          StreamTraversal.<Integer>forList()
              .modifyVTask(
                  n ->
                      VTask.of(
                          () -> {
                            ran.add(Thread.currentThread());
                            return n + 1;
                          }),
                  List.of(1, 2, 3));

      assertThat(ran).isEmpty();
      assertThat(modified.run()).containsExactly(2, 3, 4);
      assertThat(ran).hasSize(3).allMatch(thread -> thread == Thread.currentThread());
    }
  }

  @Nested
  @DisplayName("converting to and from a Traversal")
  class Converting {

    @Test
    @DisplayName("toTraversal reads the prices, which come to 42.50")
    void toTraversal() {
      Traversal<List<LineItem>, BigDecimal> asTraversal = PRICES.toTraversal();

      assertThat(
              Traversals.getAll(asTraversal, LINES).stream()
                  .reduce(BigDecimal.ZERO, BigDecimal::add))
          .isEqualByComparingTo("42.50");
    }

    @Test
    @DisplayName("over a stream, toTraversal reads it once, and its result can be read again")
    void toTraversalOverAStreamReplays() {
      AtomicInteger pulls = new AtomicInteger();
      VStream<Integer> source = VStream.of(1, 2, 3).peek(_ -> pulls.incrementAndGet());
      Traversal<VStream<Integer>, Integer> each =
          StreamTraversal.<Integer>forVStream().toTraversal();

      VStream<Integer> modified = Traversals.modify(each, n -> n + 100, source);

      assertThat(modified.toList().run()).containsExactly(101, 102, 103);
      assertThat(modified.toList().run()).containsExactly(101, 102, 103);
      assertThat(pulls).hasValue(3);
    }

    @Test
    @DisplayName("fromTraversal reads every focus when stream is called")
    void fromTraversalReadsAtOnce() {
      List<Integer> read = new ArrayList<>();
      Traversal<List<Integer>, Integer> each = Traversals.forList();
      Traversal<List<Integer>, Integer> watched =
          new Traversal<>() {
            @Override
            public <F extends WitnessArity<TypeArity.Unary>> Kind<F, List<Integer>> modifyF(
                Function<Integer, Kind<F, Integer>> f,
                List<Integer> source,
                Applicative<F> applicative) {
              return each.modifyF(
                  n -> {
                    read.add(n);
                    return f.apply(n);
                  },
                  source,
                  applicative);
            }
          };

      VStream<Integer> stream = StreamTraversal.fromTraversal(watched).stream(List.of(1, 2, 3));

      assertThat(read).containsExactly(1, 2, 3);
      assertThat(stream.toList().run()).containsExactly(1, 2, 3);
    }
  }

  @Nested
  @DisplayName("a Flow.Publisher")
  class Publishing {

    @Test
    @DisplayName("a stream handed to a publisher reads back the first three customers")
    void roundTrip() {
      VStream<String> customers = VStream.fromList(ORDERS).map(VStreamBook::customer);
      VStream<String> readBack =
          VStreamReactive.fromPublisher(VStreamReactive.toPublisher(customers), 16);

      assertThat(readBack.take(3).toList().run()).containsExactly("Ada", "Grace", "Alan");
    }

    @Test
    @DisplayName("each run subscribes again, so the stream can be read twice")
    void eachRunSubscribes() {
      VStream<Integer> readBack =
          VStreamReactive.fromPublisher(VStreamReactive.toPublisher(VStream.of(1, 2, 3)), 16);

      assertThat(readBack.toList().run()).containsExactly(1, 2, 3);
      assertThat(readBack.toList().run()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("a hot publisher sends a run what it publishes after the run subscribes")
    void hotPublisher() throws Exception {
      try (SubmissionPublisher<Integer> hot = new SubmissionPublisher<>()) {
        hot.submit(0);
        VStream<Integer> events = VStreamReactive.fromPublisher(hot, 4);
        CompletableFuture<List<Integer>> run =
            CompletableFuture.supplyAsync(() -> events.take(3).toList().run());
        await().pollInterval(POLL).atMost(PATIENCE).until(() -> hot.getNumberOfSubscribers() == 1);
        for (int i = 1; i <= 10; i++) {
          hot.submit(i);
        }

        assertThat(run.get(30, TimeUnit.SECONDS)).containsExactly(1, 2, 3);
        // A cancel only marks the subscription; the publisher's executor closes it a moment later
        await()
            .pollInterval(POLL)
            .atMost(PATIENCE)
            .untilAsserted(() -> assertThat(hot.getNumberOfSubscribers()).isZero());
      }
    }

    @Test
    @DisplayName("a publisher that sends null has its subscription cancelled, and fails the stream")
    void nullElement() {
      AtomicInteger cancels = new AtomicInteger();
      Flow.Publisher<String> sendsNull =
          subscriber ->
              subscriber.onSubscribe(
                  new Flow.Subscription() {
                    private boolean sent;

                    @Override
                    public void request(long n) {
                      if (!sent) {
                        sent = true;
                        subscriber.onNext(null);
                      }
                    }

                    @Override
                    public void cancel() {
                      cancels.incrementAndGet();
                    }
                  });

      assertThatThrownBy(() -> VStreamReactive.fromPublisher(sendsNull, 4).toList().run())
          .isInstanceOf(NullPointerException.class)
          .hasMessage("VStream.fromPublisher element cannot be null");
      assertThat(cancels).hasValue(1);
    }

    @Test
    @DisplayName(
        "fromPublisher subscribes at the first pull, asks for its buffer, then one per take")
    void fromPublisherDemand() {
      AtomicInteger subscribes = new AtomicInteger();
      List<Long> requests = new CopyOnWriteArrayList<>();
      Flow.Publisher<Integer> fivePublisher =
          subscriber -> {
            subscribes.incrementAndGet();
            subscriber.onSubscribe(
                new Flow.Subscription() {
                  private int sent;
                  private boolean done;

                  @Override
                  public void request(long n) {
                    requests.add(n);
                    for (long i = 0; i < n && sent < 5; i++) {
                      subscriber.onNext(++sent);
                    }
                    if (sent == 5 && !done) {
                      done = true;
                      subscriber.onComplete();
                    }
                  }

                  @Override
                  public void cancel() {}
                });
          };

      VStream<Integer> stream = VStreamReactive.fromPublisher(fivePublisher, 2);
      assertThat(subscribes).hasValue(0);

      assertThat(stream.toList().run()).containsExactly(1, 2, 3, 4, 5);
      assertThat(subscribes).hasValue(1);
      assertThat(requests.getFirst()).isEqualTo(2);
      assertThat(requests.subList(1, 4)).containsOnly(1L);

      List<Long> defaultRequests = new CopyOnWriteArrayList<>();
      VStreamReactive.fromPublisher(
              subscriber ->
                  subscriber.onSubscribe(
                      new Flow.Subscription() {
                        @Override
                        public void request(long n) {
                          defaultRequests.add(n);
                          subscriber.onComplete();
                        }

                        @Override
                        public void cancel() {}
                      }))
          .toList()
          .run();
      assertThat(defaultRequests.getFirst()).isEqualTo(256);
    }

    @Test
    @DisplayName("a VStreamPath reads from a publisher and hands one on")
    void onAPath() {
      VStreamPath<Integer> path =
          Path.vstreamFromPublisher(VStreamReactive.toPublisher(VStream.of(1, 2)), 8);

      assertThat(VStreamReactive.fromPublisher(path.toPublisher(), 8).toList().run())
          .containsExactly(1, 2);
    }
  }

  @Nested
  @DisplayName("Naturals between VStream, List and Stream")
  class Naturals {

    @Test
    @DisplayName("mapK moves the orders onto a stream, where four are paid")
    void mapK() {
      long paid =
          VSTREAM
              .narrow(
                  GenericPath.of(LIST.widen(ORDERS), Instances.monad(list()))
                      .mapK(VStreamTransformations.listToVStream(), Instances.monad(vstream()))
                      .runKind())
              .filter(VStreamBook::paid)
              .count()
              .run();

      assertThat(paid).isEqualTo(4);
    }

    @Test
    @DisplayName("vstreamToList reads the whole stream when it is applied")
    void vstreamToListReadsAtApply() {
      AtomicInteger pulls = new AtomicInteger();
      VStream<Integer> counted = VStream.of(1, 2, 3).peek(_ -> pulls.incrementAndGet());
      Natural<VStreamKind.Witness, ListKind.Witness> toList =
          VStreamTransformations.vstreamToList();

      Kind<ListKind.Witness, Integer> list = toList.apply(VSTREAM.widen(counted));

      assertThat(pulls).hasValue(3);
      assertThat(LIST.narrow(list)).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("streamToVStream reads nothing from the Java Stream until the stream is pulled")
    void streamToVStreamIsLazy() {
      AtomicInteger read = new AtomicInteger();
      Stream<Integer> source = Stream.of(1, 2, 3).peek(_ -> read.incrementAndGet());

      Kind<VStreamKind.Witness, Integer> converted =
          VStreamTransformations.streamToVStream().apply(STREAM.widen(source));

      assertThat(read).hasValue(0);
      assertThat(VSTREAM.narrow(converted).toList().run()).containsExactly(1, 2, 3);
      assertThat(read).hasValue(3);
    }

    @Test
    @DisplayName("vstreamToStream reads the whole stream when it is applied, on the calling thread")
    void vstreamToStreamReadsAtApply() {
      List<Thread> pulls = new CopyOnWriteArrayList<>();
      VStream<Integer> counted = VStream.of(1, 2, 3).peek(_ -> pulls.add(Thread.currentThread()));

      Kind<StreamKind.Witness, Integer> converted =
          VStreamTransformations.vstreamToStream().apply(VSTREAM.widen(counted));

      assertThat(pulls).hasSize(3).allMatch(thread -> thread == Thread.currentThread());
      assertThat(STREAM.narrow(converted).toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("a stream made from a Java Stream carries on where the last run stopped")
    void streamToVStreamCarriesOn() {
      Natural<StreamKind.Witness, VStreamKind.Witness> fromStream =
          VStreamTransformations.streamToVStream();
      VStream<Integer> stream =
          VSTREAM.narrow(fromStream.apply(STREAM.widen(Stream.of(1, 2, 3, 4))));

      assertThat(stream.take(2).toList().run()).containsExactly(1, 2);
      assertThat(stream.toList().run()).containsExactly(3, 4);
      assertThat(stream.toList().run()).isEmpty();
    }

    @Test
    @DisplayName("listToVStream then vstreamToList gives the six orders back")
    void roundTrip() {
      Natural<ListKind.Witness, ListKind.Witness> both =
          VStreamTransformations.listToVStream().andThen(VStreamTransformations.vstreamToList());

      assertThat(LIST.narrow(both.apply(LIST.widen(ORDERS)))).isEqualTo(ORDERS);
    }
  }

  @Nested
  @DisplayName("Path.from")
  class PathFrom {

    @Test
    @DisplayName("finds a VStreamPath for a VStream's Kind, which can be mapped")
    void findsAPath() {
      var found = Path.from(VSTREAM.widen(VStream.fromList(ORDERS)), VStreamKind.Witness.class);

      assertThat(found).isPresent();
      assertThat(found.get()).isInstanceOf(VStreamPath.class);
      var names = found.get().map(VStreamBook::customer);
      assertThat(names).isInstanceOf(VStreamPath.class);
      assertThat(((VStreamPath<?>) names).toList().unsafeRun()).hasSize(6);
    }

    @Test
    @DisplayName("the VStream provider has no MonadError, and unregister answers in an Optional")
    void theProvider() {
      PathProvider<?> provider = PathRegistry.getProvider(VStreamKind.Witness.class).orElseThrow();

      assertThat(provider.monadError()).isEmpty();
      assertThat(provider.supportsRecovery()).isFalse();
      assertThat(PathRegistry.unregister(String.class)).isEmpty();
    }
  }

  @Nested
  @DisplayName("a VStreamContext")
  class Context {

    @Test
    @DisplayName("gives the first three paid customers, and counts one shipped order")
    void pipeline() {
      assertThat(
              VStreamContext.fromList(ORDERS)
                  .filter(VStreamBook::paid)
                  .map(VStreamBook::customer)
                  .take(3)
                  .toList())
          .containsExactly("Ada", "Alan", "Edsger");
      assertThat(
              VStreamContext.fromList(ORDERS)
                  .filter(order -> order.status() == OrderStatus.SHIPPED)
                  .count())
          .isEqualTo(1);
    }

    @Test
    @DisplayName("a terminal operation runs the stream on the calling thread")
    void runsOnTheCaller() {
      List<Thread> threads = new CopyOnWriteArrayList<>();
      VStreamContext.fromList(ORDERS).peek(_ -> threads.add(Thread.currentThread())).toList();

      assertThat(threads).hasSize(6).allMatch(thread -> thread == Thread.currentThread());
    }

    @Test
    @DisplayName("a failing stream throws from the terminal operation, a checked failure wrapped")
    void failure() {
      VStreamContext<Order> failing = VStreamContext.of(VStream.fail(new IOException("feed down")));

      assertThatThrownBy(failing::toList)
          .isInstanceOf(VTaskExecutionException.class)
          .hasCauseInstanceOf(IOException.class);
    }
  }
}

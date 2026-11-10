// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.stream.StreamBook.ORDER_LINES;
import static org.higherkindedj.example.book.foundations.stream.StreamBook.invoiceNumbers;
import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.StreamPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.stream.StreamOps;
import org.higherkindedj.hkt.stream.StreamTraverse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Stream page makes about a Java Stream in generic code. */
@DisplayName("Stream: a sequence produced as it is read, once")
class StreamBookTest {

  private static final MonadZero<StreamKind.Witness> STREAMS = Instances.monadZero(stream());

  @Nested
  @DisplayName("reading an endless stream")
  class Endless {

    @Test
    @DisplayName("the first three invoice numbers, made only when read")
    void firstThree() {
      AtomicInteger made = new AtomicInteger();
      Kind<StreamKind.Witness, String> invoices =
          STREAMS.map(
              n -> {
                made.incrementAndGet();
                return "INV-" + n;
              },
              invoiceNumbers());

      assertThat(made).hasValue(0);
      assertThat(StreamOps.toList(StreamOps.take(3, invoices)))
          .containsExactly("INV-1001", "INV-1002", "INV-1003");
      assertThat(made).hasValue(3);
    }

    @Test
    @DisplayName("flatMap over an endless stream reads only as far as it must")
    void flatMapIsLazy() {
      AtomicInteger read = new AtomicInteger();
      Kind<StreamKind.Witness, Integer> numbers =
          STREAM.widen(Stream.iterate(1001, n -> n + 1).peek(_ -> read.incrementAndGet()));
      Kind<StreamKind.Witness, Integer> doubled =
          STREAMS.flatMap(n -> STREAM.widen(Stream.of(n, n)), numbers);

      assertThat(StreamOps.toList(StreamOps.take(3, doubled))).containsExactly(1001, 1001, 1002);
      assertThat(read).hasValue(2);
    }
  }

  @Nested
  @DisplayName("an order's lines")
  class Lines {

    @Test
    @DisplayName("flatMap reads every line of every order")
    void skus() {
      Kind<StreamKind.Witness, String> skus =
          STREAMS.flatMap(
              lines -> STREAM.widen(lines.stream().map(LineItem::sku)),
              STREAM.widen(ORDER_LINES.stream()));

      assertThat(StreamOps.toList(skus)).containsExactly("LAMP", "BULB", "SHADE");
    }

    @Test
    @DisplayName("a stream used again, by map as much as by toList, throws IllegalStateException")
    void singleUse() {
      Kind<StreamKind.Witness, String> skus = StreamOps.fromArray("LAMP", "BULB");
      StreamOps.toList(skus);

      assertThatThrownBy(() -> StreamOps.toList(skus))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("stream has already been operated upon or closed");

      Kind<StreamKind.Witness, String> others = StreamOps.fromArray("SHADE");
      STREAMS.map(String::toLowerCase, others);
      assertThatThrownBy(() -> StreamOps.toList(others)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("zip pairs two streams element by element, and concat joins them")
    void zipAndConcat() {
      assertThat(
              StreamOps.toList(
                  StreamOps.zip(
                      StreamOps.fromArray("LAMP", "BULB"),
                      StreamOps.fromArray(1, 4, 9),
                      (sku, quantity) -> quantity + " x " + sku)))
          .containsExactly("1 x LAMP", "4 x BULB");
      assertThat(
              StreamOps.toList(
                  StreamOps.concat(StreamOps.fromArray("LAMP"), StreamOps.fromArray("BULB"))))
          .containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName("zipWithIndex numbers the pick list from 0")
    void numbered() {
      Kind<StreamKind.Witness, String> numbered =
          STREAMS.map(
              pair -> (pair._1() + 1) + ". " + pair._2(),
              StreamOps.zipWithIndex(StreamOps.fromArray("LAMP", "BULB", "SHADE")));

      assertThat(StreamOps.toList(numbered)).containsExactly("1. LAMP", "2. BULB", "3. SHADE");
    }
  }

  @Nested
  @DisplayName("ap")
  class Ap {

    @Test
    @DisplayName("ap reads every value when it is called, and pairs every function with each")
    void apReadsValuesAtOnce() {
      AtomicInteger read = new AtomicInteger();
      Kind<StreamKind.Witness, Integer> values =
          STREAM.widen(Stream.of(10, 20).peek(_ -> read.incrementAndGet()));
      Kind<StreamKind.Witness, Function<Integer, String>> functions =
          STREAM.widen(Stream.of(n -> "a" + n, n -> "b" + n));

      Kind<StreamKind.Witness, String> applied = STREAMS.ap(functions, values);

      assertThat(read).hasValue(2);
      assertThat(StreamOps.toList(applied)).containsExactly("a10", "a20", "b10", "b20");
    }
  }

  @Nested
  @DisplayName("on a StreamPath")
  class OnAPath {

    @Test
    @DisplayName("a path from streamIterate gives the same invoices each time it is read")
    void readTwice() {
      StreamPath<String> nextInvoices =
          Path.streamIterate(1001, n -> n + 1).take(3).map(n -> "INV-" + n);

      assertThat(nextInvoices.toList()).containsExactly("INV-1001", "INV-1002", "INV-1003");
      assertThat(nextInvoices.toList()).containsExactly("INV-1001", "INV-1002", "INV-1003");
    }

    @Test
    @DisplayName("Path.stream reads the whole stream when it is called")
    void pathStreamReadsAtOnce() {
      AtomicInteger read = new AtomicInteger();

      StreamPath<Integer> path = Path.stream(Stream.of(1, 2, 3).peek(_ -> read.incrementAndGet()));

      assertThat(read).hasValue(3);
      assertThat(path.toList()).containsExactly(1, 2, 3);
      assertThat(path.toList()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("fromSupplier builds a fresh stream each time the path runs")
    void fromSupplierRunsFresh() {
      AtomicInteger built = new AtomicInteger();
      StreamPath<String> path =
          StreamPath.fromSupplier(
              () -> {
                built.incrementAndGet();
                return Stream.of("LAMP", "BULB");
              });

      assertThat(path.toList()).containsExactly("LAMP", "BULB");
      assertThat(path.toList()).containsExactly("LAMP", "BULB");
      assertThat(built).hasValue(2);
    }

    @Test
    @DisplayName("generate builds a fresh stream each run, and run gives a fresh Stream")
    void generateAndRun() {
      AtomicInteger made = new AtomicInteger();
      StreamPath<Integer> path = StreamPath.generate(made::incrementAndGet).take(2);

      assertThat(path.toList()).containsExactly(1, 2);
      assertThat(path.toList()).containsExactly(3, 4);
      assertThat(path.run()).isNotSameAs(path.run());
    }

    @Test
    @DisplayName("StreamPath's zipWith gives every combination")
    void zipWith() {
      assertThat(
              Path.stream(Stream.of(1, 2))
                  .zipWith(Path.stream(Stream.of(10, 20)), Integer::sum)
                  .toList())
          .containsExactly(11, 21, 12, 22);
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("of(null) gives an empty stream, and zero is empty")
    void ofNullAndZero() {
      assertThat(StreamOps.toList(STREAMS.of(null))).isEmpty();
      assertThat(StreamOps.toList(STREAMS.<String>zero())).isEmpty();
    }

    @Test
    @DisplayName("widen wraps the stream, and narrow gives back the same stream")
    void widenNarrow() {
      Stream<String> skus = Stream.of("LAMP");
      Kind<StreamKind.Witness, String> widened = STREAM.widen(skus);

      assertThat((Object) widened).isNotSameAs(skus);
      assertThat(STREAM.narrow(widened)).isSameAs(skus);
    }

    @Test
    @DisplayName("orElse puts one stream after another")
    void orElse() {
      Alternative<StreamKind.Witness> alternative = Instances.alternative(stream());

      assertThat(
              StreamOps.toList(
                  alternative.orElse(
                      StreamOps.fromArray("LAMP"), () -> StreamOps.fromArray("BULB"))))
          .containsExactly("LAMP", "BULB");
    }

    @Test
    @DisplayName("StreamTraverse folds the six items in the two orders")
    void fold() {
      Kind<StreamKind.Witness, Integer> quantities =
          STREAM.widen(ORDER_LINES.stream().flatMap(List::stream).map(LineItem::quantity));

      assertThat(StreamTraverse.INSTANCE.foldMap(Monoids.integerAddition(), q -> q, quantities))
          .isEqualTo(6);
    }
  }
}

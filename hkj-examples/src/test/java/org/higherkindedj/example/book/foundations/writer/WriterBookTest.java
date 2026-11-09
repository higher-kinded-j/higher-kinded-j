// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.AUDIT;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.NEW_ORDER;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.emailCustomer;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.fulfil;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.fulfilOnPath;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.pay;
import static org.higherkindedj.example.book.foundations.writer.WriterBook.ship;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.optics.cast.Order;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterBifunctor;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer.WriterKind2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Writer page makes about an order's audit log. */
@DisplayName("Writer: a result with a log that each step adds to")
class WriterBookTest {

  private static List<String> events(List<AuditEntry> log) {
    return log.stream().map(AuditEntry::event).toList();
  }

  @Nested
  @DisplayName("an audit log for an order")
  class AuditLog {

    @Test
    @DisplayName("fulfilling the order ships it and logs three entries, in order")
    void fulfilling() {
      Writer<List<AuditEntry>, Order> fulfilled = fulfil(NEW_ORDER);

      assertThat(fulfilled.run().status()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(events(fulfilled.exec())).containsExactly("paid", "shipped", "customer emailed");
      assertThat(fulfilled.exec()).allMatch(entry -> entry.orderId().equals(NEW_ORDER.id()));
    }

    @Test
    @DisplayName("each step returns the changed order with one entry")
    void eachStep() {
      Writer<List<AuditEntry>, Order> paid = pay(NEW_ORDER);

      assertThat(paid.value().status()).isEqualTo(OrderStatus.PAID);
      assertThat(events(paid.log())).containsExactly("paid");
      assertThat(events(ship(paid.value()).log())).containsExactly("shipped");
    }

    @Test
    @DisplayName("tell logs an entry and gives Unit, and map leaves the log as it is")
    void tellAndMap() {
      Writer<List<AuditEntry>, Unit> emailed = emailCustomer(NEW_ORDER);
      assertThat(emailed.value()).isEqualTo(Unit.INSTANCE);
      assertThat(events(emailed.log())).containsExactly("customer emailed");

      Writer<List<AuditEntry>, Order> mapped = emailed.map(_ -> NEW_ORDER);
      assertThat(mapped.log()).isEqualTo(emailed.log());
    }

    @Test
    @DisplayName("value starts with the Monoid's empty log")
    void valueStartsEmpty() {
      assertThat(Writer.value(AUDIT, NEW_ORDER).log()).isEmpty();
    }
  }

  @Nested
  @DisplayName("each step's log joins the log before it")
  class Joining {

    @Test
    @DisplayName("a Writer runs as it is built: flatMap calls the next step at once")
    void eager() {
      List<String> calls = new ArrayList<>();
      Writer<List<AuditEntry>, Order> built =
          pay(NEW_ORDER)
              .flatMap(
                  AUDIT,
                  paid -> {
                    calls.add("ship");
                    return ship(paid);
                  });

      assertThat(calls).containsExactly("ship");
      built.run();
      built.run();
      assertThat(calls).containsExactly("ship");
    }

    @Test
    @DisplayName("Monoids.list joins two logs into a new list and leaves both as they were")
    void listMonoidCopies() {
      List<AuditEntry> paid = pay(NEW_ORDER).log();
      List<AuditEntry> shipped = ship(NEW_ORDER).log();

      List<AuditEntry> both = AUDIT.combine(paid, shipped);

      assertThat(events(both)).containsExactly("paid", "shipped");
      assertThat(both).isNotSameAs(paid).isNotSameAs(shipped);
      assertThat(paid).hasSize(1);
      assertThat(shipped).hasSize(1);
      assertThat(AUDIT.empty()).isEmpty();
    }

    @Test
    @DisplayName("another Monoid accumulates another kind of log")
    void otherMonoids() {
      Monoid<BigDecimal> money = Monoids.bigDecimalAddition();
      assertThat(
              Writer.of(new BigDecimal("40.00"), "lamp")
                  .flatMap(money, s -> Writer.of(new BigDecimal("4.95"), s + ", delivery"))
                  .log())
          .isEqualByComparingTo("44.95");

      Monoid<Integer> count = Monoids.integerAddition();
      Writer<Integer, String> counted =
          Writer.of(1, "paid").flatMap(count, s -> Writer.of(1, s + ", shipped"));
      assertThat(counted.log()).isEqualTo(2);

      Monoid<String> text = Monoids.string();
      assertThat(Writer.of("paid; ", 1).flatMap(text, n -> Writer.of("shipped; ", n)).log())
          .isEqualTo("paid; shipped; ");
    }
  }

  @Nested
  @DisplayName("long chains")
  class LongChains {

    private static final int STEPS = 100_000;

    @Test
    @DisplayName("a chain of 100,000 steps built in a loop runs without growing the stack")
    void aLoopIsStackSafe() {
      Monoid<Integer> count = Monoids.integerAddition();
      Writer<Integer, Integer> counted = Writer.value(count, 0);
      WriterPath<Integer, Integer> onPath = Path.writerPure(0, count);
      for (int i = 0; i < STEPS; i++) {
        counted = counted.flatMap(count, n -> Writer.of(1, n + 1));
        onPath = onPath.via(n -> Path.writer(Writer.of(1, n + 1), count));
      }

      assertThat(counted).isEqualTo(new Writer<>(STEPS, STEPS));
      assertThat(onPath.run()).isEqualTo(new Writer<>(STEPS, STEPS));
    }
  }

  @Nested
  @DisplayName("on a WriterPath")
  class OnAPath {

    @Test
    @DisplayName("the path gives the same Writer as fulfil")
    void sameAsWriter() {
      WriterPath<List<AuditEntry>, Order> onPath = fulfilOnPath(NEW_ORDER);

      assertThat(onPath.run()).isEqualTo(fulfil(NEW_ORDER));
      assertThat(onPath.value().status()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(events(onPath.written())).containsExactly("paid", "shipped", "customer emailed");
      assertThat(onPath.monoid()).isSameAs(AUDIT);
    }

    @Test
    @DisplayName("Path.tell logs only, and censor rewrites the whole log")
    void tellAndCensor() {
      WriterPath<List<AuditEntry>, Unit> told =
          Path.tell(List.of(new AuditEntry(NEW_ORDER.id(), "noted")), AUDIT);
      assertThat(events(told.written())).containsExactly("noted");
      assertThat(told.value()).isEqualTo(Unit.INSTANCE);

      WriterPath<List<AuditEntry>, Order> shippedOnly =
          fulfilOnPath(NEW_ORDER)
              .censor(
                  log -> log.stream().filter(entry -> entry.event().equals("shipped")).toList());
      assertThat(events(shippedOnly.written())).containsExactly("shipped");
      assertThat(Path.writerPure(NEW_ORDER, AUDIT).written()).isEmpty();
    }

    @Test
    @DisplayName("zipWith joins both paths' logs, the first path's entries first")
    void zipWith() {
      WriterPath<List<AuditEntry>, String> both =
          Path.writer(pay(NEW_ORDER), AUDIT)
              .zipWith(
                  Path.writer(ship(NEW_ORDER), AUDIT), (a, b) -> a.status() + "/" + b.status());

      assertThat(both.value()).isEqualTo("PAID/SHIPPED");
      assertThat(events(both.written())).containsExactly("paid", "shipped");
    }
  }

  @Nested
  @DisplayName("a Writer always holds a log and a value")
  class AlwaysBoth {

    @Test
    @DisplayName("a null log or value is refused at the call")
    void refusedAtTheCall() {
      assertThatThrownBy(() -> Writer.of(null, NEW_ORDER)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Writer.of(List.of(), null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Writer.tell(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a map function that returns null fails at the call, since Writer is eager")
    void mapNullFailsAtTheCall() {
      Writer<List<AuditEntry>, Order> paid = pay(NEW_ORDER);

      assertThatThrownBy(() -> paid.map(_ -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f must not return null");
      assertThatThrownBy(() -> paid.mapWritten(_ -> null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Writer.construction log cannot be null");
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("the monad joins the two steps' logs, and exec gives two entries")
    void theMonad() {
      Monad<WriterKind.Witness<List<AuditEntry>>> audited = Instances.writer(AUDIT);
      Kind<WriterKind.Witness<List<AuditEntry>>, Order> paid = WRITER.widen(pay(NEW_ORDER));
      Kind<WriterKind.Witness<List<AuditEntry>>, Order> thenShipped =
          audited.flatMap(order -> WRITER.widen(ship(order)), paid);

      assertThat(events(WRITER.exec(thenShipped))).containsExactly("paid", "shipped");
      assertThat(WRITER.run(thenShipped).status()).isEqualTo(OrderStatus.SHIPPED);
      assertThat(WRITER.runWriter(thenShipped))
          .isEqualTo(pay(NEW_ORDER).flatMap(AUDIT, WriterBook::ship));
    }

    @Test
    @DisplayName("widening allocates nothing: narrow gives back the same Writer")
    void widenIsTheSameObject() {
      Writer<List<AuditEntry>, Order> paid = pay(NEW_ORDER);

      assertThat(WRITER.widen(paid)).isSameAs(paid);
      assertThat(WRITER.narrow(WRITER.widen(paid))).isSameAs(paid);
    }

    @Test
    @DisplayName("the Bifunctor maps the log and the value together")
    void bifunctor() {
      Bifunctor<WriterKind2.Witness> bifunctor = WriterBifunctor.INSTANCE;

      Writer<Integer, String> mapped =
          WRITER.narrow2(
              bifunctor.bimap(
                  List::size, order -> order.status().name(), WRITER.widen2(fulfil(NEW_ORDER))));

      assertThat(mapped).isEqualTo(new Writer<>(3, "SHIPPED"));
    }
  }
}

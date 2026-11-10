// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.example.book.foundations.free.FreeBook.LIMIT;
import static org.higherkindedj.example.book.foundations.free.FreeBook.ORDER;
import static org.higherkindedj.example.book.foundations.free.FreeBook.ORDER_OPS;
import static org.higherkindedj.example.book.foundations.free.FreeBook.cancelIfOver;
import static org.higherkindedj.example.book.foundations.free.FreeBook.priceOf;
import static org.higherkindedj.hkt.free.FreeKindHelper.FREE;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.AuditEntry;
import org.higherkindedj.example.book.foundations.OrderOpKind;
import org.higherkindedj.example.book.foundations.OrderOpOps;
import org.higherkindedj.example.book.foundations.free.FreeBook.AuditedOrders;
import org.higherkindedj.example.book.foundations.free.FreeBook.Decision;
import org.higherkindedj.example.book.foundations.free.FreeBook.InMemoryOrders;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free.FreeFactory;
import org.higherkindedj.hkt.free.ProgramAnalyser;
import org.higherkindedj.hkt.free.ProgramAnalysis;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Holds the claims the Free page makes about programs held as data. */
@DisplayName("Free: programs as data")
class FreeBookTest {

  @Nested
  @DisplayName("an order program")
  class Program {

    @Test
    @DisplayName("a lamp at 40.00, over the 30.00 limit, cancels the order")
    void cancelsOverTheLimit() {
      InMemoryOrders store = new InMemoryOrders();

      Decision lamp =
          ID.narrow(
                  cancelIfOver(ORDER, "LAMP", LIMIT)
                      .foldMap(store, Instances.monad(id()))
                      .runKind())
              .value();

      assertThat(lamp).isEqualTo(new Decision("LAMP", new BigDecimal("40.00"), true));
      assertThat(store.cancelled).containsExactly(ORDER);
    }

    @Test
    @DisplayName("a bulb at 2.50 keeps the order, and nothing is cancelled")
    void keepsUnderTheLimit() {
      InMemoryOrders store = new InMemoryOrders();

      Decision bulb =
          ID.narrow(
                  cancelIfOver(ORDER, "BULB", LIMIT)
                      .foldMap(store, Instances.monad(id()))
                      .runKind())
              .value();

      assertThat(bulb).isEqualTo(new Decision("BULB", new BigDecimal("2.50"), false));
      assertThat(store.cancelled).isEmpty();
    }

    @Test
    @DisplayName("building the program cancels nothing: one program runs on two stores")
    void programIsData() {
      FreePath<OrderOpKind.Witness, Decision> program = cancelIfOver(ORDER, "LAMP", LIMIT);
      InMemoryOrders first = new InMemoryOrders();
      InMemoryOrders second = new InMemoryOrders();

      program.foldMap(first, Instances.monad(id()));
      assertThat(second.cancelled).isEmpty();
      program.foldMap(second, Instances.monad(id()));

      assertThat(first.cancelled).containsExactly(ORDER);
      assertThat(second.cancelled).containsExactly(ORDER);
    }

    @Test
    @DisplayName("the generated interpreter is a Natural, as foldMap takes")
    void interpreterIsANatural() {
      assertThat(new InMemoryOrders()).isInstanceOf(Natural.class);
    }
  }

  @Nested
  @DisplayName("an audit of the same program")
  class Audit {

    @Test
    @DisplayName("the Writer's log holds an entry for each instruction, in order")
    void logsEachInstruction() {
      Writer<List<AuditEntry>, Decision> audited =
          WRITER.narrow(
              cancelIfOver(ORDER, "LAMP", LIMIT)
                  .foldMap(new AuditedOrders(ORDER), Instances.writer(Monoids.list()))
                  .runKind());

      assertThat(audited.log())
          .containsExactly(
              new AuditEntry(ORDER, "priced LAMP at 40.00"), new AuditEntry(ORDER, "would cancel"));
      assertThat(audited.value()).isEqualTo(new Decision("LAMP", new BigDecimal("40.00"), true));
    }

    @Test
    @DisplayName("a line under the limit logs only its price")
    void logsOnlyThePrice() {
      Writer<List<AuditEntry>, Decision> audited =
          WRITER.narrow(
              cancelIfOver(ORDER, "BULB", LIMIT)
                  .foldMap(new AuditedOrders(ORDER), Instances.writer(Monoids.list()))
                  .runKind());

      assertThat(audited.log())
          .extracting(AuditEntry::event)
          .containsExactly("priced BULB at 2.50");
    }
  }

  @Nested
  @DisplayName("what can be seen before the program runs")
  class Inspect {

    @Test
    @DisplayName("only the first instruction is seen, and the rest is hidden behind its price")
    void onlyTheFirstInstruction() {
      ProgramAnalysis analysis =
          ProgramAnalyser.analyse(cancelIfOver(ORDER, "LAMP", LIMIT).toFree());

      assertThat(analysis.suspendCount()).isEqualTo(1);
      assertThat(analysis.hasOpaqueRegions()).isTrue();
    }

    @Test
    @DisplayName("a single instruction hides nothing")
    void singleInstruction() {
      ProgramAnalysis analysis = ProgramAnalyser.analyse(priceOf("LAMP").toFree());

      assertThat(analysis.suspendCount()).isEqualTo(1);
      assertThat(analysis.hasOpaqueRegions()).isFalse();
    }
  }

  @Nested
  @DisplayName("a program always gives a value")
  class Values {

    @Test
    @DisplayName("Free.pure(null) and Path.freePure(null, functor) throw at the call")
    void pureRefusesNull() {
      assertThatThrownBy(() -> Free.pure(null)).isInstanceOf(NullPointerException.class);
      assertThatThrownBy(() -> Path.freePure(null, ORDER_OPS))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("a map function that returns null fails the program when it is interpreted")
    void mapReturningNullFailsAtFoldMap() {
      FreePath<OrderOpKind.Witness, String> nothing = priceOf("LAMP").map(_ -> null);

      assertThatThrownBy(() -> nothing.foldMap(new InMemoryOrders(), Instances.monad(id())))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("working with Kind")
  class WorkingWithKind {

    @Test
    @DisplayName("widen and narrow give back the same Free")
    void widenIsACast() {
      Free<OrderOpKind.Witness, BigDecimal> lamp = OrderOpOps.priceOf("LAMP", Function.identity());

      assertThat(FREE.widen(lamp)).isSameAs(lamp);
      assertThat(FREE.narrow(FREE.widen(lamp))).isSameAs(lamp);
    }

    @Test
    @DisplayName("a FreeFactory's chain prices the lamp's fee at 4.00")
    void factoryChain() {
      FreeFactory<OrderOpKind.Witness> orders = FreeFactory.of();
      Free<OrderOpKind.Witness, BigDecimal> fee =
          orders.pure(new BigDecimal("40.00")).map(price -> price.multiply(new BigDecimal("0.10")));

      assertThat(ID.narrow(fee.foldMap(new InMemoryOrders(), Instances.monad(id()))).value())
          .isEqualByComparingTo("4.00");
    }
  }
}

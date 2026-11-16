// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.coyoneda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.coyoneda.CoyonedaKindHelper.COYONEDA;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdMonad;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Coyoneda foldMap and interpreter")
class CoyonedaFoldMapTest {

  /**
   * An instruction set with no Functor: each record fixes its result type, and none carries a
   * continuation that a {@code map} could extend.
   */
  sealed interface StockOp<A> extends Kind<StockOp.Witness, A> {
    final class Witness implements WitnessArity<TypeArity.Unary> {
      private Witness() {}
    }

    record Reserve(String sku, int quantity) implements StockOp<Boolean> {}

    record Available(String sku) implements StockOp<Integer> {}
  }

  private static final IdMonad ID_MONAD = IdMonad.instance();

  /** Runs each instruction against a fixed stock level, and records the instructions it ran. */
  private static final class InMemoryStock implements Natural<StockOp.Witness, IdKind.Witness> {
    private final Map<String, Integer> levels = Map.of("LAMP", 3, "BULB", 0);
    private final List<StockOp<?>> ran = new ArrayList<>();

    @Override
    @SuppressWarnings("unchecked") // each record fixes A; the switch cannot say so
    public <A> Kind<IdKind.Witness, A> apply(Kind<StockOp.Witness, A> fa) {
      StockOp<A> op = (StockOp<A>) fa;
      ran.add(op);
      Object result =
          switch (op) {
            case StockOp.Reserve r -> levels.getOrDefault(r.sku(), 0) >= r.quantity();
            case StockOp.Available a -> levels.getOrDefault(a.sku(), 0);
          };
      return (Kind<IdKind.Witness, A>) Id.of(result);
    }
  }

  static Stream<Arguments> lists() {
    return Stream.of(
        Arguments.of(List.of()), Arguments.of(List.of(4)), Arguments.of(List.of(7, 8, 9)));
  }

  /** Id's Functor, counting the calls to map. */
  private static final class CountingIdFunctor implements Functor<IdKind.Witness> {
    private int maps;

    @Override
    public <A, B> Kind<IdKind.Witness, B> map(
        Function<? super A, ? extends B> f, Kind<IdKind.Witness, A> fa) {
      maps++;
      return ID_MONAD.map(f, fa);
    }
  }

  private static Kind<StockOp.Witness, Boolean> reserve(String sku, int quantity) {
    return new StockOp.Reserve(sku, quantity);
  }

  private static Kind<StockOp.Witness, Integer> available(String sku) {
    return new StockOp.Available(sku);
  }

  @Nested
  @DisplayName("foldMap")
  class FoldMapTests {

    @Test
    @DisplayName("runs the natural transformation, then applies every held map in one map")
    void runsTheNaturalThenMapsOnce() {
      InMemoryStock stock = new InMemoryStock();
      CountingIdFunctor functor = new CountingIdFunctor();

      Coyoneda<StockOp.Witness, String> held =
          Coyoneda.lift(available("LAMP")).map(n -> n * 10).map(n -> n + 1).map(n -> "n=" + n);

      Kind<IdKind.Witness, String> result = held.foldMap(stock, functor);

      assertThat(ID.narrow(result).value()).isEqualTo("n=31");
      assertThat(functor.maps).isEqualTo(1);
      assertThat(stock.ran).containsExactly(new StockOp.Available("LAMP"));
    }

    @Test
    @DisplayName("interprets an instruction set that has no Functor")
    void interpretsAnInstructionSetWithNoFunctor() {
      Coyoneda<StockOp.Witness, String> lamp =
          Coyoneda.lift(reserve("LAMP", 2)).map(ok -> ok ? "reserved" : "short");
      Coyoneda<StockOp.Witness, String> bulb =
          Coyoneda.lift(reserve("BULB", 1)).map(ok -> ok ? "reserved" : "short");

      assertThat(ID.narrow(lamp.foldMap(new InMemoryStock(), ID_MONAD)).value())
          .isEqualTo("reserved");
      assertThat(ID.narrow(bulb.foldMap(new InMemoryStock(), ID_MONAD)).value()).isEqualTo("short");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("org.higherkindedj.hkt.coyoneda.CoyonedaFoldMapTest#lists")
    @DisplayName("agrees with lowering first and then interpreting, for a natural List to Maybe")
    void agreesWithLowerThenInterpret(List<Integer> values) {
      Natural<ListKind.Witness, MaybeKind.Witness> head =
          new Natural<>() {
            @Override
            public <A> Kind<MaybeKind.Witness, A> apply(Kind<ListKind.Witness, A> fa) {
              List<A> list = LIST.narrow(fa);
              return MAYBE.widen(list.isEmpty() ? Maybe.nothing() : Maybe.just(list.getFirst()));
            }
          };
      Coyoneda<ListKind.Witness, String> held =
          Coyoneda.lift(LIST.widen(values)).map(n -> n * 10).map(n -> "n=" + n);

      Maybe<String> viaFoldMap = MAYBE.narrow(held.foldMap(head, MaybeMonad.INSTANCE));
      Maybe<String> viaLower = MAYBE.narrow(head.apply(held.lower(ListMonad.INSTANCE)));

      assertThat(viaFoldMap).isEqualTo(viaLower);
    }

    @Test
    @DisplayName("rejects a null natural transformation or Functor")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void rejectsNulls() {
      Coyoneda<StockOp.Witness, Integer> held = Coyoneda.lift(available("LAMP"));

      assertThatNullPointerException()
          .isThrownBy(() -> held.foldMap(null, ID_MONAD))
          .withMessage("Natural transformation cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> held.foldMap(new InMemoryStock(), null))
          .withMessage("Functor cannot be null");
    }
  }

  @Nested
  @DisplayName("A long chain of maps")
  class DeepChainTests {

    @Test
    @DisplayName("runs through foldMap without overflowing the stack")
    void foldMapsWithoutOverflow() {
      Coyoneda<StockOp.Witness, Integer> held = Coyoneda.lift(available("LAMP"));
      for (int i = 0; i < 100_000; i++) {
        held = held.map(n -> n + 1);
      }

      assertThat(ID.narrow(held.foldMap(new InMemoryStock(), ID_MONAD)).value())
          .isEqualTo(3 + 100_000);
    }
  }

  @Nested
  @DisplayName("interpreter")
  class InterpreterTests {

    @Test
    @DisplayName("runs a Free program whose instructions are Coyoneda values")
    void runsAFreeProgramOverCoyoneda() {
      CoyonedaFunctor<StockOp.Witness> functor = CoyonedaFunctor.instance();
      InMemoryStock stock = new InMemoryStock();

      FreePath<CoyonedaKind.Witness<StockOp.Witness>, String> program =
          Path.freeLift(COYONEDA.lift(available("LAMP")), functor)
              .via(
                  n ->
                      Path.freeLift(COYONEDA.lift(reserve("LAMP", n)), functor)
                          .map(ok -> ok ? "reserved " + n : "short"));

      Kind<IdKind.Witness, String> result =
          program.foldMap(Coyoneda.interpreter(stock, ID_MONAD), ID_MONAD).runKind();

      assertThat(ID.narrow(result).value()).isEqualTo("reserved 3");
      assertThat(stock.ran)
          .containsExactly(new StockOp.Available("LAMP"), new StockOp.Reserve("LAMP", 3));
    }

    @Test
    @DisplayName("gives each instruction's interpreted value, with the held maps applied")
    void appliesTheHeldMaps() {
      Natural<CoyonedaKind.Witness<StockOp.Witness>, IdKind.Witness> interpreter =
          Coyoneda.interpreter(new InMemoryStock(), ID_MONAD);

      Kind<IdKind.Witness, String> result =
          interpreter.apply(COYONEDA.widen(Coyoneda.lift(available("BULB")).map(n -> n + " left")));

      assertThat(ID.narrow(result).value()).isEqualTo("0 left");
    }

    @Test
    @DisplayName("rejects a null natural transformation or Functor")
    @SuppressWarnings("DataFlowIssue") // null is passed deliberately to verify rejection
    void rejectsNulls() {
      assertThatNullPointerException()
          .isThrownBy(() -> Coyoneda.interpreter(null, ID_MONAD))
          .withMessage("Natural transformation cannot be null");
      assertThatNullPointerException()
          .isThrownBy(() -> Coyoneda.interpreter(new InMemoryStock(), null))
          .withMessage("Functor cannot be null");
    }
  }
}

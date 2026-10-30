// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.profunctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.example.book.foundations.TypeClassInstances;
import org.higherkindedj.example.book.foundations.monoid.MonoidBook;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.effect.ReaderPath;
import org.higherkindedj.hkt.func.FunctionProfunctor;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Optic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Holds the claims the Profunctor page makes about adapting a function's input and output. */
@DisplayName("Profunctor: adapting what a function takes and what it gives")
class ProfunctorBookTest {

  private static final FunctionProfunctor PROFUNCTOR = FunctionProfunctor.INSTANCE;
  private static final List<String> FEED = List.of("BULB,4,2.50", "LAMP,1,40.00", "SHADE,2,12.25");

  @Test
  @DisplayName("lmap's function runs first, turning the new input into the one the function takes")
  void lmapRunsBefore() {
    List<String> ran = new ArrayList<>();
    Function<String, BigDecimal> fromFeed =
        FUNCTION.getFunction(
            PROFUNCTOR.lmap(
                (String line) -> {
                  ran.add("parse");
                  return ProfunctorBook.parse(line);
                },
                FUNCTION.widen(
                    (LineItem line) -> {
                      ran.add("total");
                      return MonoidBook.lineTotal(line);
                    })));

    assertThat(fromFeed.apply("BULB,4,2.50")).isEqualTo(new BigDecimal("10.00"));
    assertThat(ran).containsExactly("parse", "total");
  }

  @Test
  @DisplayName("rmap's function runs last, on the function's result")
  void rmapRunsAfter() {
    List<String> ran = new ArrayList<>();
    Function<LineItem, Integer> inPence =
        FUNCTION.getFunction(
            PROFUNCTOR.rmap(
                (BigDecimal total) -> {
                  ran.add("pence");
                  return ProfunctorBook.pence(total);
                },
                FUNCTION.widen(
                    (LineItem line) -> {
                      ran.add("total");
                      return MonoidBook.lineTotal(line);
                    })));

    assertThat(inPence.apply(new LineItem("LAMP", 1, new BigDecimal("40.00")))).isEqualTo(4000);
    assertThat(ran).containsExactly("total", "pence");
  }

  @Test
  @DisplayName("dimap gives what compose and andThen give, on every line of the feed")
  void dimapIsComposeAndThen() {
    Function<LineItem, BigDecimal> lineTotal = MonoidBook::lineTotal;
    Function<String, Integer> viaDimap =
        FUNCTION.getFunction(
            PROFUNCTOR.dimap(
                ProfunctorBook::parse, ProfunctorBook::pence, FUNCTION.widen(lineTotal)));
    Function<String, Integer> plainJava =
        lineTotal.compose(ProfunctorBook::parse).andThen(ProfunctorBook::pence);

    for (String line : FEED) {
      assertThat(viaDimap.apply(line)).isEqualTo(plainJava.apply(line));
    }
  }

  @Test
  @DisplayName("dimap runs nothing when called: its functions run each time the result is applied")
  void dimapRunsNothingUntilApplied() {
    List<String> ran = new ArrayList<>();
    Function<String, Integer> feedToPence =
        FUNCTION.getFunction(
            PROFUNCTOR.dimap(
                (String line) -> {
                  ran.add("parse");
                  return ProfunctorBook.parse(line);
                },
                ProfunctorBook::pence,
                FUNCTION.widen(MonoidBook::lineTotal)));

    assertThat(ran).isEmpty();
    feedToPence.apply("BULB,4,2.50");
    feedToPence.apply("BULB,4,2.50");
    assertThat(ran).containsExactly("parse", "parse");
  }

  @Test
  @DisplayName(
      "a ReaderPath adapts its input with local and its output with map, as lmap and rmap do")
  void readerPathIsTheEffectPathEquivalent() {
    ReaderPath<String, Integer> feedToPence =
        ReaderPath.<LineItem, BigDecimal>asks(MonoidBook::lineTotal)
            .local(ProfunctorBook::parse)
            .map(ProfunctorBook::pence);

    assertThat(feedToPence.run("BULB,4,2.50")).isEqualTo(1000);
  }

  @Test
  @DisplayName("FunctionProfunctor is the only Profunctor instance hkj-core ships")
  void functionIsTheOnlyInstance() {
    assertThat(TypeClassInstances.implementingDirectly(Profunctor.class, FunctionProfunctor.class))
        .containsExactly("FunctionProfunctor");
  }

  @Test
  @DisplayName(
      "an Optic is no Profunctor: its modifyF takes an Applicative, a Lens's a Functor, and it has its own dimap")
  void opticsAreVanLaarhoven() throws NoSuchMethodException {
    assertThat(Profunctor.class.isAssignableFrom(Optic.class)).isFalse();
    assertThat(Optic.class.getMethod("modifyF", Function.class, Object.class, Applicative.class))
        .isNotNull();
    assertThat(Optic.class.getMethod("dimap", Function.class, Function.class)).isNotNull();
    assertThat(Lens.class.getMethod("modifyF", Function.class, Object.class, Functor.class))
        .isNotNull();
  }
}

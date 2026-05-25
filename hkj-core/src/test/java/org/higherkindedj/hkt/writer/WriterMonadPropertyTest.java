// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer;

import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;

/** Property-based Functor- and Monad-law verification for Writer (String log). */
class WriterMonadPropertyTest {

  private static final Monoid<String> STRING_MONOID =
      new Monoid<>() {
        @Override
        public String empty() {
          return "";
        }

        @Override
        public String combine(String a, String b) {
          return a + b;
        }
      };

  private final Monad<WriterKind.Witness<String>> monad = new WriterMonad<>(STRING_MONOID);

  private final BiPredicate<
          Kind<WriterKind.Witness<String>, ?>, Kind<WriterKind.Witness<String>, ?>>
      eq = KindEquivalence.byEqualsAfter(WRITER::narrow);

  @Provide
  Arbitrary<Kind<WriterKind.Witness<String>, Integer>> writerKinds() {
    Arbitrary<Integer> values = Arbitraries.integers().between(-100, 100);
    Arbitrary<String> logs = Arbitraries.strings().alpha().ofMaxLength(5);
    return Combinators.combine(logs, values).as((log, v) -> WRITER.widen(new Writer<>(log, v)));
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return Arbitraries.of(i -> "v:" + i, i -> String.valueOf(i * 2), Object::toString);
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return Arbitraries.of(String::length, s -> s.isEmpty() ? 0 : 1, String::hashCode);
  }

  @Provide
  Arbitrary<Function<Integer, Kind<WriterKind.Witness<String>, String>>> intToWriterString() {
    return Arbitraries.of(
        i -> WRITER.widen(new Writer<>("f1;", "v:" + i)),
        i -> WRITER.widen(new Writer<>("f2;", String.valueOf(i * 2))),
        i -> WRITER.widen(new Writer<>("", Integer.toBinaryString(i))));
  }

  @Provide
  Arbitrary<Function<String, Kind<WriterKind.Witness<String>, String>>> stringToWriterString() {
    return Arbitraries.of(
        s -> WRITER.widen(new Writer<>("g1;", s + "!")),
        s -> WRITER.widen(new Writer<>("g2;", s.toUpperCase())),
        s -> WRITER.widen(new Writer<>("", "x:" + s)));
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(@ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> fa) {
    FunctorLaws.assertIdentity(monad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(monad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToWriterString") Function<Integer, Kind<WriterKind.Witness<String>, String>> f) {
    MonadLaws.assertLeftIdentity(monad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(@ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> m) {
    MonadLaws.assertRightIdentity(monad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("writerKinds") Kind<WriterKind.Witness<String>, Integer> m,
      @ForAll("intToWriterString") Function<Integer, Kind<WriterKind.Witness<String>, String>> f,
      @ForAll("stringToWriterString")
          Function<String, Kind<WriterKind.Witness<String>, String>> g) {
    MonadLaws.assertAssociativity(monad, m, f, g, eq);
  }
}

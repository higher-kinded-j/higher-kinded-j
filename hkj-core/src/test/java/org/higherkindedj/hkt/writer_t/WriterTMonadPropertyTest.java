// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.writer_t;

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.function.BiPredicate;
import java.util.function.Function;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.assertions.KindEquivalence;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.FunctorLaws;
import org.higherkindedj.hkt.laws.MonadLaws;
import org.higherkindedj.hkt.typeclass.StringMonoid;

/**
 * Property-based Functor- and Monad-law verification for WriterT over Id (String log). Equality
 * runs both sides to the inner {@code Id<Pair<A, log>>} and compares.
 */
@SuppressWarnings("unused") // referenced reflectively by jqwik
class WriterTMonadPropertyTest {

  private final Monad<IdKind.Witness> idMonad = Instances.monad(id());
  private final Monad<WriterTKind.Witness<IdKind.Witness, String>> writerTMonad =
      Instances.writerT(idMonad, new StringMonoid());

  private <A> Pair<A, String> run(Kind<WriterTKind.Witness<IdKind.Witness, String>, A> kind) {
    WriterT<IdKind.Witness, String, A> writerT = WRITER_T.narrow(kind);
    Id<Pair<A, String>> id = IdKindHelper.ID.narrow(writerT.run());
    return id.value();
  }

  private final BiPredicate<
          Kind<WriterTKind.Witness<IdKind.Witness, String>, ?>,
          Kind<WriterTKind.Witness<IdKind.Witness, String>, ?>>
      eq = KindEquivalence.byEqualsAfter(this::run);

  @Provide
  Arbitrary<Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer>> writerTKinds() {
    return WriterTArbitraries.writerTKinds();
  }

  @Provide
  Arbitrary<Function<Integer, String>> intToString() {
    return WriterTArbitraries.intToString();
  }

  @Provide
  Arbitrary<Function<String, Integer>> stringToInt() {
    return WriterTArbitraries.stringToInt();
  }

  @Provide
  Arbitrary<Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>>>
      intToTKindString() {
    return WriterTArbitraries.intToTKindString();
  }

  @Provide
  Arbitrary<Function<String, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>>>
      stringToTKindString() {
    return WriterTArbitraries.stringToTKindString();
  }

  @Property(tries = 50)
  @Label("Functor identity: map(id, fa) == fa")
  void functorIdentity(
      @ForAll("writerTKinds") Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> fa) {
    FunctorLaws.assertIdentity(writerTMonad, fa, eq);
  }

  @Property(tries = 50)
  @Label("Functor composition: map(g∘f, fa) == map(g, map(f, fa))")
  void functorComposition(
      @ForAll("writerTKinds") Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> fa,
      @ForAll("intToString") Function<Integer, String> f,
      @ForAll("stringToInt") Function<String, Integer> g) {
    FunctorLaws.assertComposition(writerTMonad, fa, f, g, eq);
  }

  @Property(tries = 50)
  @Label("Monad left identity: flatMap(f, of(a)) == f(a)")
  void leftIdentity(
      @ForAll @IntRange(min = -50, max = 50) int value,
      @ForAll("intToTKindString")
          Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> f) {
    MonadLaws.assertLeftIdentity(writerTMonad, value, f, eq);
  }

  @Property(tries = 50)
  @Label("Monad right identity: flatMap(of, m) == m")
  void rightIdentity(
      @ForAll("writerTKinds") Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> m) {
    MonadLaws.assertRightIdentity(writerTMonad, m, eq);
  }

  @Property(tries = 50)
  @Label("Monad associativity")
  void associativity(
      @ForAll("writerTKinds") Kind<WriterTKind.Witness<IdKind.Witness, String>, Integer> m,
      @ForAll("intToTKindString")
          Function<Integer, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> f,
      @ForAll("stringToTKindString")
          Function<String, Kind<WriterTKind.Witness<IdKind.Witness, String>, String>> g) {
    MonadLaws.assertAssociativity(writerTMonad, m, f, g, eq);
  }
}

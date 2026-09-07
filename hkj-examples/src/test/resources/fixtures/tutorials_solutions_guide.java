// Fixture for hkj-book/src/tutorials/solutions_guide.md
//
// The guide quotes the shapes the tutorial solutions settle on - widen/narrow around a generic
// operation, a composed optic path, a hand-written lens. The values and the nested model those
// shapes read are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GenerateTraversals;

@GenerateLenses
record Inner(String field) {}

@GenerateLenses
record Middle(Inner inner) {}

@GenerateLenses
record Outer(Middle middle) {}

@GenerateLenses
record Player(String name, int score) {}

@GenerateLenses
@GenerateTraversals
record Team(String name, List<Player> players) {}

@GenerateLenses
@GenerateTraversals
record League(String name, List<Team> teams) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Either<String, Integer> value1 = Either.right(1);

  static final Either<String, Integer> value2 = Either.right(2);

  static final Either<String, Integer> eitherValue = value1;

  static final Maybe<Integer> maybeValue = Maybe.just(1);

  static final List<Integer> listValue = List.of(1);

  static final Functor<EitherKind.Witness<String>> functor = Instances.monadError(either());

  static final MonadError<EitherKind.Witness<String>, String> applicative =
      Instances.monadError(either());
}

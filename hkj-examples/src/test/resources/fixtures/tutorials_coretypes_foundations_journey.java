// Fixture for hkj-book/src/tutorials/coretypes/foundations_journey.md
//
// The journey widens an Either and combines two of them; only the imports and the two starting
// values are elided.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.either;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.instances.Instances;

class Fixture {

  static final Either<String, Integer> value1 = Either.right(1);

  static final Either<String, Integer> value2 = Either.right(2);
}

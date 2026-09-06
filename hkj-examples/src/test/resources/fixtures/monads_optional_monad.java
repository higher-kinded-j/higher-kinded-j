// Fixture for hkj-book/src/monads/optional_monad.md
//
// One JDK lookup returning Optional, carried into the Kind world and back out again.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;

record User(String name) {}

class Fixture {

  static final String id = "u-1";

  static final Kind<OptionalKind.Witness, String> absent =
      OPTIONAL.widen(Optional.<String>empty());

  <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> lookupAndFormat(
      Kind<F, User> userKind, Monad<F> monad) {
    return monad.map(u -> u.name().toUpperCase(), userKind);
  }

  static final MonadError<OptionalKind.Witness, Unit> optionalMonad =
      Instances.monadError(optional());

  static final Repository repository = new Repository();

  static Optional<User> findUser(String id) {
    return Optional.of(new User("Ada"));
  }

  static final class Repository {

    Optional<User> findById(String id) {
      return Optional.of(new User("Ada"));
    }
  }
}

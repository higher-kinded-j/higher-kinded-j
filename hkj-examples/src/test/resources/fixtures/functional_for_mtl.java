// Fixture for hkj-book/src/functional/for_mtl.md
//
// The page writes three capability-polymorphic comprehensions, then bridges For into ForState. The
// state records, the monads and the lenses are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.expression.ForState;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.optics.Lens;

record AppConfig(String dbUrl, int maxRetries) {}

record Counter(int count, int total) {}

record Dashboard(String user, int count, boolean ready) {}

class Fixture {

  static final Lens<Dashboard, Boolean> readyLens =
      Lens.of(Dashboard::ready, (d, v) -> new Dashboard(d.user(), d.count(), v));

  static final Lens<Dashboard, Integer> countLens =
      Lens.of(Dashboard::count, (d, v) -> new Dashboard(d.user(), v, d.ready()));


  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  // A MonadZero, because the toState example relies on `when()` being available.
  static final MonadZero<MaybeKind.Witness> maybeMonad = Instances.monadZero(maybe());
}

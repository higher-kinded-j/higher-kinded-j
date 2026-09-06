// Fixture for hkj-book/src/functional/alternative.md
//
// The page tries several configuration sources in turn, and several search strategies. The sources
// and strategies are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.List;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;

record Result(String value) {}

interface SearchStrategy {

  Kind<MaybeKind.Witness, Result> search(String query);
}

class Fixture {

  static final Alternative<MaybeKind.Witness> alt = Instances.alternative(maybe());

  static final Kind<MaybeKind.Witness, String> fromEnv = MAYBE.just("env");

  static final Kind<MaybeKind.Witness, String> fromFile = MAYBE.just("file");

  static final Kind<MaybeKind.Witness, String> fromDefault = MAYBE.just("default");

  static final String query = "widgets";

  static final List<SearchStrategy> searchStrategies = List.of(q -> MAYBE.just(new Result(q)));

  static Kind<MaybeKind.Witness, String> readFromEnvironment(String key) {
    return MAYBE.just("env");
  }

  static Kind<MaybeKind.Witness, String> readFromConfigFile(String key) {
    return MAYBE.nothing();
  }

  static Kind<MaybeKind.Witness, String> readFromDatabase(String key) {
    return MAYBE.nothing();
  }

  static Kind<MaybeKind.Witness, String> getDefaultValue(String key) {
    return MAYBE.just("default");
  }
}

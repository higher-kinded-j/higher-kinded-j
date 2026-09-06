// Fixture for hkj-book/src/effect/path_freeap.md
//
// The page loads configuration through one applicative DSL. As on path_free.md the algebra is a
// `Kind` in its own right, so an operation can be lifted straight off the page, and it carries a
// continuation so the `Functor` has something to map.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.io;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.FreeApPath;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.free_ap.FreeApAnalyzer;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKindHelper;

sealed interface ConfigOp<A> extends Kind<ConfigOp.Witness, A> permits GetConfig {
  interface Witness extends WitnessArity<TypeArity.Unary> {}
}

record GetConfig<A>(String key, Function<String, A> next) implements ConfigOp<A> {}

record DbConfig(String host, int port) {}

record DbSettings(String host, int port, String name) {}

class Fixture {

  static final Map<String, String> settings =
      Map.of("db.host", "localhost", "db.port", "5432", "db.name", "app", "host", "localhost");

  static final Functor<ConfigOp.Witness> configFunctor =
      new Functor<>() {
        @Override
        public <A, B> Kind<ConfigOp.Witness, B> map(
            Function<? super A, ? extends B> f, Kind<ConfigOp.Witness, A> fa) {
          return switch ((ConfigOp<A>) fa) {
            case GetConfig<A>(String key, Function<String, A> next) ->
                new GetConfig<B>(key, s -> f.apply(next.apply(s)));
          };
        }
      };

  static final Applicative<IO.Witness> ioApplicative = Instances.monad(io());

  static final Natural<ConfigOp.Witness, IO.Witness> interpreter =
      new Natural<>() {
        @Override
        public <A> Kind<IO.Witness, A> apply(Kind<ConfigOp.Witness, A> fa) {
          return switch ((ConfigOp<A>) fa) {
            case GetConfig<A>(String key, Function<String, A> next) ->
                IO.delay(() -> next.apply(settings.get(key)));
          };
        }
      };

  static final Natural<ConfigOp.Witness, IO.Witness> batchingInterpreter = interpreter;

  static String loadConfig(String key) {
    return settings.get(key);
  }

  static FreeApPath<ConfigOp.Witness, String> getConfig(String key) {
    return Path.freeApLift(new GetConfig<>(key, Function.identity()), configFunctor);
  }

  static FreePath<ConfigOp.Witness, String> readConfig(String key) {
    return Path.freeLift(new GetConfig<>(key, Function.identity()), configFunctor);
  }
}

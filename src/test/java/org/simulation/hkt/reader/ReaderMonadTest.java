package org.simulation.hkt.reader;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.simulation.hkt.Kind;
import org.simulation.hkt.function.Function3;
import org.simulation.hkt.function.Function4;
import org.simulation.hkt.reader.ReaderKind;
import org.simulation.hkt.reader.ReaderMonad;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.simulation.hkt.reader.ReaderKindHelper.*;

/**
 * Tests for ReaderMonad<R>.
 */
@DisplayName("ReaderMonad Tests")
class ReaderMonadTest {

  // Simple environment for tests
  record Config(String url, int connections) {}
  final Config testConfig = new Config("db://localhost", 5);

  // Monad instance for Reader<Config, ?>
  private ReaderMonad<Config> readerMonad;

  @BeforeEach
  void setUp() {
    readerMonad = new ReaderMonad<>();
  }

  // Helper to run and get result
  private <A> A run(Kind<ReaderKind<Config, ?>, A> kind) {
    return runReader(kind, testConfig);
  }

  // --- Basic Operations ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldCreateConstantReader() {
      Kind<ReaderKind<Config, ?>, String> kind = readerMonad.of("constantValue");
      assertThat(run(kind)).isEqualTo("constantValue");
      // Verify it ignores environment
      assertThat(runReader(kind, new Config("other", 0))).isEqualTo("constantValue");
    }

    @Test
    void of_shouldAllowNullValue() {
      Kind<ReaderKind<Config, ?>, String> kind = readerMonad.of(null);
      assertThat(run(kind)).isNull();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionToResult() {
      Kind<ReaderKind<Config, ?>, String> urlReaderKind = reader(Config::url);
      Kind<ReaderKind<Config, ?>, Integer> lengthReaderKind = readerMonad.map(String::length, urlReaderKind);
      assertThat(run(lengthReaderKind)).isEqualTo(testConfig.url().length());
    }

    @Test
    void map_shouldChainFunctions() {
      Kind<ReaderKind<Config, ?>, Integer> connectionsReaderKind = reader(Config::connections);
      // Map connections -> double -> string
      Kind<ReaderKind<Config, ?>, String> mappedKind = readerMonad.map(
          conns -> "Connections: " + conns,
          readerMonad.map(c -> c * 2.0, connectionsReaderKind)
      );
      assertThat(run(mappedKind)).isEqualTo("Connections: " + (testConfig.connections() * 2.0));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    @Test
    void ap_shouldApplyReaderFunctionToReaderValue() {
      // Reader<Config, Function<Integer, String>>
      Kind<ReaderKind<Config, ?>, Function<Integer, String>> funcKind =
          reader(config -> (Integer i) -> config.url() + ":" + i);

      // Reader<Config, Integer>
      Kind<ReaderKind<Config, ?>, Integer> valKind = reader(Config::connections);

      Kind<ReaderKind<Config, ?>, String> resultKind = readerMonad.ap(funcKind, valKind);

      assertThat(run(resultKind)).isEqualTo(testConfig.url() + ":" + testConfig.connections());
    }

    @Test
    void ap_shouldWorkWithConstantFunctionAndValue() {
      Kind<ReaderKind<Config, ?>, Function<Integer, String>> funcKind = readerMonad.of(i -> "Num" + i);
      Kind<ReaderKind<Config, ?>, Integer> valKind = readerMonad.of(100);
      Kind<ReaderKind<Config, ?>, String> resultKind = readerMonad.ap(funcKind, valKind);
      assertThat(run(resultKind)).isEqualTo("Num100");
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    @Test
    void flatMap_shouldSequenceComputations() {
      // Get URL, then based on URL, get connections
      Kind<ReaderKind<Config, ?>, String> urlKind = reader(Config::url);

      Function<String, Kind<ReaderKind<Config, ?>, Integer>> getConnectionsBasedOnUrl =
          url -> url.startsWith("db:")
              ? reader(Config::connections) // Use original env
              : constant(-1); // Return constant if URL doesn't match

      Kind<ReaderKind<Config, ?>, Integer> resultKind = readerMonad.flatMap(getConnectionsBasedOnUrl, urlKind);

      assertThat(run(resultKind)).isEqualTo(testConfig.connections());

      // Test with different config
      Config otherConfig = new Config("http://server", 10);
      assertThat(runReader(resultKind, otherConfig)).isEqualTo(-1);
    }

    @Test
    void flatMap_shouldPassEnvironmentCorrectly() {
      // Reader 1: Get connections
      Kind<ReaderKind<Config, ?>, Integer> connKind = reader(Config::connections);
      // Reader 2 (depends on connections): Get URL and append connections
      Function<Integer, Kind<ReaderKind<Config, ?>, String>> getUrlAndAppendConns =
          conns -> reader(env -> env.url() + " [" + conns + "]"); // Uses env passed to *its* run

      Kind<ReaderKind<Config, ?>, String> resultKind = readerMonad.flatMap(getUrlAndAppendConns, connKind);

      // run(resultKind) is equivalent to:
      // r -> { int c = connKind.run(r); Reader<R,String> r2 = getUrlAndAppendConns(c); return r2.run(r); }
      // r -> { int c = r.connections(); Reader<R,String> r2 = env -> env.url() + " [" + c + "]"; return r2.run(r); }
      // r -> { int c = r.connections(); return r.url() + " [" + c + "]"; }
      assertThat(run(resultKind)).isEqualTo(testConfig.url() + " [" + testConfig.connections() + "]");
    }
  }

  // --- Law Tests ---

  // Helper functions for laws
  final Function<Integer, String> intToString = Object::toString;
  final Function<String, String> appendWorld = s -> s + " world";

  // Kind<ReaderKind<Config, ?>, Integer>
  final Kind<ReaderKind<Config, ?>, Integer> mValue = reader(Config::connections);
  // Function Integer -> Kind<ReaderKind<Config, ?>, String>
  final Function<Integer, Kind<ReaderKind<Config, ?>, String>> f =
      i -> reader(env -> env.url() + ":" + i);
  // Function String -> Kind<ReaderKind<Config, ?>, String>
  final Function<String, Kind<ReaderKind<Config, ?>, String>> g =
      s -> reader(env -> s + " (" + env.connections() + ")");


  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<ReaderKind<Config, ?>, Integer> fa = reader(Config::connections);
      Kind<ReaderKind<Config, ?>, Integer> result = readerMonad.map(Function.identity(), fa);
      // Compare results when run
      assertThat(run(result)).isEqualTo(run(fa));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<ReaderKind<Config, ?>, Integer> fa = reader(Config::connections);
      Function<Integer, String> fMap = i -> "v" + i;
      Function<String, String> gMap = s -> s + "!";
      Function<Integer, String> gComposeF = gMap.compose(fMap);

      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.map(gComposeF, fa);
      Kind<ReaderKind<Config, ?>, String> rightSide = readerMonad.map(gMap, readerMonad.map(fMap, fa));

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<ReaderKind<Config, ?>, Integer> v = reader(Config::connections);
    Kind<ReaderKind<Config, ?>, Function<Integer, String>> fKind = reader(env -> i -> env.url() + i);
    Kind<ReaderKind<Config, ?>, Function<String, String>> gKind = reader(env -> s -> s + env.connections());

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<ReaderKind<Config, ?>, Function<Integer, Integer>> idFuncKind = readerMonad.of(Function.identity());
      assertThat(run(readerMonad.ap(idFuncKind, v))).isEqualTo(run(v));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> func = i -> "X" + i;
      Kind<ReaderKind<Config, ?>, Function<Integer, String>> apFunc = readerMonad.of(func);
      Kind<ReaderKind<Config, ?>, Integer> apVal = readerMonad.of(x);

      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.ap(apFunc, apVal);
      Kind<ReaderKind<Config, ?>, String> rightSide = readerMonad.of(func.apply(x));

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.ap(fKind, readerMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<ReaderKind<Config, ?>, Function<Function<Integer, String>, String>> evalKind = readerMonad.of(evalWithY);
      Kind<ReaderKind<Config, ?>, String> rightSide = readerMonad.ap(evalKind, fKind);

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<Function<String, String>, Function<Function<Integer, String>, Function<Integer, String>>> composeMap =
          gg -> ff -> gg.compose(ff);

      Kind<ReaderKind<Config, ?>, Function<Function<Integer, String>, Function<Integer, String>>> mappedCompose =
          readerMonad.map(composeMap, gKind);
      Kind<ReaderKind<Config, ?>, Function<Integer, String>> ap1 =
          readerMonad.ap(mappedCompose, fKind);
      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.ap(ap1, v);

      Kind<ReaderKind<Config, ?>, String> innerAp = readerMonad.ap(fKind, v);
      Kind<ReaderKind<Config, ?>, String> rightSide = readerMonad.ap(gKind, innerAp);

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      int value = 5;
      Kind<ReaderKind<Config, ?>, Integer> ofValue = readerMonad.of(value);
      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.flatMap(f, ofValue);
      Kind<ReaderKind<Config, ?>, String> rightSide = f.apply(value);

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<ReaderKind<Config, ?>, Integer>> ofFunc = i -> readerMonad.of(i);
      Kind<ReaderKind<Config, ?>, Integer> leftSide = readerMonad.flatMap(ofFunc, mValue);
      assertThat(run(leftSide)).isEqualTo(run(mValue));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<ReaderKind<Config, ?>, String> innerFlatMap = readerMonad.flatMap(f, mValue);
      Kind<ReaderKind<Config, ?>, String> leftSide = readerMonad.flatMap(g, innerFlatMap);

      Function<Integer, Kind<ReaderKind<Config, ?>, String>> rightSideFunc =
          a -> readerMonad.flatMap(g, f.apply(a));
      Kind<ReaderKind<Config, ?>, String> rightSide = readerMonad.flatMap(rightSideFunc, mValue);

      assertThat(run(leftSide)).isEqualTo(run(rightSide));
    }
  }

  // --- mapN Tests --- (Using default Applicative implementations)
  @Nested
  @DisplayName("mapN tests")
  class MapNTests {
    Kind<ReaderKind<Config, ?>, Integer> r1 = reader(Config::connections);
    Kind<ReaderKind<Config, ?>, String> r2 = reader(Config::url);
    Kind<ReaderKind<Config, ?>, Double> r3 = reader(env -> env.connections() * 1.5);
    Kind<ReaderKind<Config, ?>, Boolean> r4 = reader(env -> env.url().startsWith("db"));

    @Test
    void map2_combinesResults() {
      Kind<ReaderKind<Config, ?>, String> result = readerMonad.map2(
          r1, r2, (conns, url) -> url + " (" + conns + ")"
      );
      assertThat(run(result)).isEqualTo(testConfig.url() + " (" + testConfig.connections() + ")");
    }

    @Test
    void map3_combinesResults() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);
      Kind<ReaderKind<Config, ?>, String> result = readerMonad.map3(r1, r2, r3, f3);
      assertThat(run(result)).isEqualTo(String.format("I:%d S:%s D:%.1f",
          testConfig.connections(), testConfig.url(), testConfig.connections() * 1.5));
    }

    @Test
    void map4_combinesResults() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);
      Kind<ReaderKind<Config, ?>, String> result = readerMonad.map4(r1, r2, r3, r4, f4);
      assertThat(run(result)).isEqualTo(String.format("I:%d S:%s D:%.1f B:%b",
          testConfig.connections(), testConfig.url(), testConfig.connections() * 1.5, testConfig.url().startsWith("db")));
    }
  }
}


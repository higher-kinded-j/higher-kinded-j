// Fixture for hkj-book/src/functional/functor.md
//
// The page opens with one function mapped over four containers. The values it maps are declared
// here, with the types Functor's signature names. The page's other code is included from compiled
// examples.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources/fixtures so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;

class Fixture {

  static final Optional<String> optional = Optional.of("Hello");

  static final List<String> list = List.of("one", "two", "three");

  static final CompletableFuture<String> completableFuture =
      CompletableFuture.completedFuture("Hello");

  static final MaybePath<String> maybePath = Path.just("Hello");
}

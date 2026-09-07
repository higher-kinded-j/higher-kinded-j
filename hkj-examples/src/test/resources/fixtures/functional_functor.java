// Fixture for hkj-book/src/functional/functor.md
//
// The page maps one function over four containers. The JDK values it opens with are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.optional.OptionalFunctor;
import org.higherkindedj.hkt.optional.OptionalKind;

class Fixture {

  static final Optional<String> optional = Optional.of("Hello");

  static final List<String> list = List.of("one", "two", "three");

  static final CompletableFuture<String> completableFuture =
      CompletableFuture.completedFuture("Hello");

  static final MaybePath<String> maybePath = Path.just("Hello");
}

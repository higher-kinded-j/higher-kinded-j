// Fixture for hkj-book/src/transformers/mtl_combining.md
//
// The page writes functions that need two capabilities at once and runs them under Id- and
// future-backed stacks. The domain and the two outer monads are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.reader_t.ReaderTMonadReader;
import org.higherkindedj.hkt.state_t.StateTMonadState;
import org.higherkindedj.hkt.writer_t.WriterTKind;

record AppConfig(String dbUrl) {}

record Counter(int count) {}

record ServiceConfig(String apiUrl) {}

record UserProfile(String userId) {}

record Account(String id, BigDecimal balance) {

  Account credit(BigDecimal amount) {
    return new Account(id, balance.add(amount));
  }
}

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final MonadError<CompletableFutureKind.Witness, Throwable> futureMonad =
      Instances.monadError(completableFuture());

  static final Monoid<List<String>> listMonoid = Monoids.list();

  static UserProfile fetchFromApi(String apiUrl, String userId) {
    return new UserProfile(userId);
  }
}

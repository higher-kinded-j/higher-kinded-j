// Fixture for hkj-book/src/transformers/writert_transformer.md
//
// The page prices one basket while accumulating an audit log, three ways: passing the log along by
// hand, a WriterPath, and WriterT over Id. The monoid and the log-carrying monad the later
// snippets pick up are declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.WriterPath;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.id.IdKindHelper;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;

class Fixture {

  static final Monad<IdKind.Witness> idMonad = Instances.monad(id());

  static final Monoid<List<String>> listMonoid = Monoids.list();

  static final MonadWriter<WriterTKind.Witness<IdKind.Witness, List<String>>, List<String>> audit =
      Instances.writerT(idMonad, listMonoid);
}

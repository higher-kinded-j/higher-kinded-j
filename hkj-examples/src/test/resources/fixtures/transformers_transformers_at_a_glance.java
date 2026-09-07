// Fixture for hkj-book/src/transformers/transformers_at_a_glance.md
//
// Six one-liners, one per transformer. Only the ReaderT line names a domain type.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.completableFuture;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.future.CompletableFutureKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.writer_t.WriterT;

record AppConfig(String dbUrl) {}

class Fixture {}

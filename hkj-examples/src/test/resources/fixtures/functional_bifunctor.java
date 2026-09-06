// Fixture for hkj-book/src/functional/bifunctor.md
//
// Three worked examples - Either, Tuple2 and Validated - each self-contained. Only the witnesses
// the snippets name without importing are supplied here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.either.EitherKind2;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Bifunctor;
import org.higherkindedj.hkt.tuple.Tuple2Kind2;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;
import org.higherkindedj.hkt.validated.ValidatedKind2;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind2;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.constant.ConstKind2;

record UserData(String name, String email) {}

record ApiError(String code, String message, int status) {}

record ApiResponse(String name, String email, int status) {}

class Fixture {}

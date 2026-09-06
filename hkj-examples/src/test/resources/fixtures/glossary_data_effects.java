// Fixture for hkj-book/src/glossary/data-effects.md
//
// The glossary defines a data type and then shows it in use, so each entry's snippet elides the
// domain it acts on. What little there is of one is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.constant.ConstKindHelper.CONST;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.constant.ConstBifunctor;
import org.higherkindedj.hkt.constant.ConstKind2;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.time.TimeSource;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.tuple.Tuple;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple3;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.util.ListPrisms;
import org.higherkindedj.optics.indexed.Pair;

record User(String name, Integer age, String email) {}

record Order(String id) {}

record Reservation(String orderId, Instant until) {}

class Fixture {

  static final Scanner scanner = new Scanner(System.in);

  static final Instant instant = Instant.EPOCH;

  static final Duration hold = Duration.ofMinutes(15);

  static final Order order = new Order("ORD-1");
}

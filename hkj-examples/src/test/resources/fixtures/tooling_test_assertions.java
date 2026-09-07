// Fixture for hkj-book/src/tooling/test_assertions.md
//
// The page shows one assertion per type, each over a subject it does not build. Those subjects are
// declared here, along with the annotated records whose generated optics the law snippets check.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.IOAssert.assertThatIO;
import static org.higherkindedj.hkt.assertions.LazyAssert.assertThatLazy;
import static org.higherkindedj.hkt.assertions.MaybeAssert.assertThatMaybe;
import static org.higherkindedj.hkt.assertions.StateAssert.assertThatStateTuple;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.assertions.VResultPathAssert.assertThatVResultPath;
import static org.higherkindedj.hkt.assertions.VStreamAssert.assertThatVStream;
import static org.higherkindedj.hkt.assertions.VTaskAssert.assertThatVTask;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.instances.Witnesses.optional;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.VResultPath;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.laws.LensLaws;
import org.higherkindedj.optics.laws.PrismLaws;
import org.junit.jupiter.api.Test;

record Order(String id) {}

sealed interface DomainError {
  record ValidationFailure(String field) implements DomainError {}

  record NotFound(String id) implements DomainError {}
}

@GenerateLenses
record User(String name, int age) {}

@GeneratePrisms
sealed interface Shape permits Circle, Square {}

record Circle(double radius) implements Shape {}

record Square(double side) implements Shape {}

final class OrderService {

  Either<DomainError, Order> process(String request) {
    return Either.right(new Order("order-1"));
  }
}

final class OrderLoader {

  Either<DomainError, Order> load(String orderId) {
    return Either.right(new Order(orderId));
  }
}

class Fixture {

  static final String request = "order-request";

  static final String orderId = "order-1";

  static final Order expectedOrder = new Order("order-1");

  static final OrderService orderService = new OrderService();

  static final OrderLoader service = new OrderLoader();

  static final Either<DomainError, Order> result = Either.right(new Order("order-1"));

  static final Either<DomainError, Order> failure =
      Either.left(new DomainError.ValidationFailure("email"));

  static final Maybe<String> value = Maybe.just("alice");

  static final Maybe<String> lookup = Maybe.just("alice");

  static final Try<Integer> computation = Try.success(1);

  static final Validated<List<String>, String> form =
      Validated.invalid(List.of("name required", "email invalid"));

  static final Lazy<Integer> failing =
      Lazy.defer(
          () -> {
            throw new IllegalStateException("kaboom");
          });

  static final Writer<String, Integer> writer = Writer.of("computed: ", 42);

  static final StateTuple<Integer, String> tuple = StateTuple.of(5, "processed");

  static final Integer expected = 42;

  static final VResultPath<DomainError, Order> failingPath =
      Path.vresultDefer(() -> Either.left(new DomainError.NotFound("order-1")));

  static final VResultPath<DomainError, Order> defective =
      Path.vresultDefer(
          () -> {
            throw new IllegalStateException("defect");
          });

  static final VStream<Integer> failingStream =
      VStream.fail(new IllegalStateException("stream failed"));

  static final MonadError<OptionalKind.Witness, Unit> outerMonad =
      Instances.monadError(optional());

  static Integer compute() {
    return 42;
  }

  static Integer heavyComputation() {
    return 42;
  }
}

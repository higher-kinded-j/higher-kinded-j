// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The variance each Path family gives a step. The error families accept a step that fails with a
 * subtype of the chain's error, and {@code ReaderPath} a step that needs a supertype of its
 * environment. Each test compiles only because of that variance, and checks the chain still runs.
 */
@DisplayName("Path family variance")
class PathFamilyVarianceTest {

  sealed interface AppError permits NotFound, Denied, Combined {}

  record NotFound(String id) implements AppError {}

  record Denied(String reason) implements AppError {}

  record Combined(AppError first, AppError second) implements AppError {}

  private static final Semigroup<AppError> APP_ERRORS = Combined::new;

  private static final NotFound NOT_FOUND = new NotFound("x");

  private static final Denied DENIED = new Denied("no");

  @Nested
  @DisplayName("EitherPath accepts a step that fails with a subtype of E")
  class EitherPathCovariance {

    private final EitherPath<AppError, Integer> one = Path.right(1);

    @Test
    @DisplayName("via(), then() and zipWith()")
    void chaining() {
      assertThat(one.via(n -> Path.<NotFound, Integer>left(NOT_FOUND)).run())
          .isEqualTo(Either.left(NOT_FOUND));
      assertThat(one.then(() -> Path.<Denied, Integer>right(2)).run()).isEqualTo(Either.right(2));
      assertThat(one.zipWith(Path.<NotFound, Integer>right(2), Integer::sum).run())
          .isEqualTo(Either.right(3));
    }

    @Test
    @DisplayName("zipWith3() takes each other path on its own subtype")
    void zipWith3() {
      assertThat(
              one.zipWith3(
                      Path.<NotFound, Integer>right(2),
                      Path.<Denied, Integer>left(DENIED),
                      (a, b, c) -> a + b + c)
                  .run())
          .isEqualTo(Either.left(DENIED));
    }

    @Test
    @DisplayName("recoverWith() and orElse()")
    void recovery() {
      EitherPath<AppError, Integer> failed = Path.left(DENIED);
      assertThat(failed.recoverWith(e -> Path.<NotFound, Integer>left(NOT_FOUND)).run())
          .isEqualTo(Either.left(NOT_FOUND));
      assertThat(failed.orElse(() -> Path.<NotFound, Integer>right(2)).run())
          .isEqualTo(Either.right(2));
    }
  }

  @Nested
  @DisplayName("VResultPath accepts a step that fails with a subtype of E")
  class VResultPathCovariance {

    private final VResultPath<AppError, Integer> one = VResultPath.pure(1);

    @Test
    @DisplayName("via() and zipWith3()")
    void chaining() {
      assertThat(one.via(n -> VResultPath.<NotFound, Integer>raiseError(NOT_FOUND)).run().run())
          .isEqualTo(Either.left(NOT_FOUND));
      assertThat(
              one.zipWith3(
                      VResultPath.<NotFound, Integer>pure(2),
                      VResultPath.<Denied, Integer>pure(3),
                      (a, b, c) -> a + b + c)
                  .run()
                  .run())
          .isEqualTo(Either.right(6));
    }

    @Test
    @DisplayName("recoverWith()")
    void recovery() {
      VResultPath<AppError, Integer> failed = VResultPath.raiseError(DENIED);
      assertThat(failed.recoverWith(e -> VResultPath.<NotFound, Integer>pure(2)).run().run())
          .isEqualTo(Either.right(2));
    }
  }

  @Nested
  @DisplayName("ValidationPath accepts a path that fails with a subtype of E")
  class ValidationPathCovariance {

    private final ValidationPath<AppError, Integer> denied = Path.invalid(DENIED, APP_ERRORS);

    private final ValidationPath<NotFound, Integer> notFound =
        Path.invalid(NOT_FOUND, (a, b) -> new NotFound(a.id() + b.id()));

    @Test
    @DisplayName("zipWithAccum(), andAlso() and andThen() accumulate with this path's semigroup")
    void accumulating() {
      AppError both = new Combined(DENIED, NOT_FOUND);
      assertThat(denied.zipWithAccum(notFound, Integer::sum).run())
          .isEqualTo(Validated.invalid(both));
      assertThat(denied.andAlso(notFound).run()).isEqualTo(Validated.invalid(both));
      assertThat(denied.andThen(notFound).run()).isEqualTo(Validated.invalid(both));
    }

    @Test
    @DisplayName("zipWith3Accum() and zipWith3()")
    void threeWay() {
      ValidationPath<Denied, Integer> two = Path.valid(2, (a, b) -> a);
      assertThat(denied.zipWith3Accum(two, notFound, (a, b, c) -> a + b + c).run())
          .isEqualTo(Validated.invalid(new Combined(DENIED, NOT_FOUND)));
      assertThat(
              Path.<AppError, Integer>valid(1, APP_ERRORS)
                  .zipWith3(two, notFound, (a, b, c) -> a + b + c)
                  .run())
          .isEqualTo(Validated.invalid(NOT_FOUND));
    }
  }

  @Nested
  @DisplayName("EitherOrBothPath accepts a path that warns with a subtype of L")
  class EitherOrBothPathCovariance {

    private final EitherOrBothPath<AppError, Integer> warned = Path.both(DENIED, 1, APP_ERRORS);

    private final EitherOrBothPath<NotFound, Integer> alsoWarned =
        Path.both(NOT_FOUND, 2, (a, b) -> new NotFound(a.id() + b.id()));

    @Test
    @DisplayName("zipWithAccum() and zipWith3()")
    void combining() {
      assertThat(warned.zipWithAccum(alsoWarned, Integer::sum).run())
          .isEqualTo(EitherOrBoth.both(new Combined(DENIED, NOT_FOUND), 3));
      assertThat(
              warned
                  .zipWith3(
                      alsoWarned,
                      Path.<Denied, Integer>right(3, (a, b) -> a),
                      (a, b, c) -> a + b + c)
                  .run())
          .isEqualTo(EitherOrBoth.both(new Combined(DENIED, NOT_FOUND), 6));
    }
  }

  /** An environment whose reader needs only part of it. */
  interface Named {
    String name();
  }

  record Config(String name, int port) implements Named {}

  private static final Config CONFIG = new Config("svc", 8080);

  @Nested
  @DisplayName("ReaderPath accepts a step that needs a supertype of its environment")
  class ReaderPathContravariance {

    private final ReaderPath<Config, Integer> port = ReaderPath.asks(Config::port);

    private final ReaderPath<Named, String> name = ReaderPath.asks(Named::name);

    @Test
    @DisplayName("via(), then(), zipWith() and zipWith3()")
    void chaining() {
      assertThat(port.via(p -> name.map(n -> n + ":" + p)).run(CONFIG)).isEqualTo("svc:8080");
      assertThat(port.then(() -> name).run(CONFIG)).isEqualTo("svc");
      assertThat(port.zipWith(name, (p, n) -> n + ":" + p).run(CONFIG)).isEqualTo("svc:8080");
      assertThat(
              port.zipWith3(
                      name, ReaderPath.<Object, Integer>pure(1), (p, n, one) -> n + ":" + (p + one))
                  .run(CONFIG))
          .isEqualTo("svc:8081");
    }
  }
}

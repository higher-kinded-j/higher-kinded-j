// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link DetectNullErrorValuesRecipe}. */
class DetectNullErrorValuesRecipeTest implements RewriteTest {

  // Stub the real types so the type-attributed matchers resolve calls and receiver types.
  private static final String[] STUBS = {
    "package org.higherkindedj.hkt; public interface Kind<F, A> {}",
    "package org.higherkindedj.hkt; public interface MonadError<F, E> {"
        + " <A> Kind<F, A> raiseError(E error); }",
    "package org.higherkindedj.hkt; public enum Unit { INSTANCE }",
    "package org.higherkindedj.hkt.either; public interface EitherKind<L, R> {"
        + " final class Witness<L> {} }",
    "package org.higherkindedj.hkt.either; import java.util.function.Function;"
        + " public interface Either<L, R> {"
        + " static <L, R> Either<L, R> left(L value) { return null; }"
        + " static <L, R> Either<L, R> right(R value) { return null; }"
        + " default <L2> Either<L2, R> mapLeft(Function<? super L, ? extends L2> f) {"
        + " return null; }"
        + " default <L2, R2> Either<L2, R2> bimap("
        + " Function<? super L, ? extends L2> f, Function<? super R, ? extends R2> g) {"
        + " return null; } }",
    "package org.higherkindedj.hkt.either; import org.higherkindedj.hkt.*;"
        + " public class EitherMonad<L> implements MonadError<EitherKind.Witness<L>, L> {"
        + " public <A> Kind<EitherKind.Witness<L>, A> raiseError(L error) { return null; } }",
    "package org.higherkindedj.hkt.either; public final class EitherSelective<L>"
        + " extends EitherMonad<L> {}",
    "package org.higherkindedj.hkt.maybe; public interface Maybe<T> {"
        + " default <L> org.higherkindedj.hkt.either.Either<L, T> toEither(L l) { return null; } }",
    "package org.higherkindedj.optics.extensions; public final class LensExtensions {"
        + " public static <E, S, A> Object getEither(Object lens, E errorValue, S source) {"
        + " return null; } }",
    "package org.higherkindedj.hkt.either_t; public final class EitherT<F, L, R> {"
        + " public static <F, L, R> EitherT<F, L, R> left(Object monad, L l) { return null; } }",
    "package org.higherkindedj.hkt.optional; public interface OptionalKind<A> {"
        + " final class Witness {} }",
    "package org.higherkindedj.hkt.optional; import org.higherkindedj.hkt.*;"
        + " public final class OptionalMonad implements MonadError<OptionalKind.Witness, Unit> {"
        + " public <A> Kind<OptionalKind.Witness, A> raiseError(Unit error) { return null; } }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectNullErrorValuesRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(STUBS));
  }

  @DocumentExample
  @Test
  void marksNullErrors() {
    rewriteRun(
        java(
            """
            package test;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.MonadError;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.either.EitherKind;
            import org.higherkindedj.hkt.either.EitherMonad;
            import org.higherkindedj.hkt.either.EitherSelective;
            import org.higherkindedj.hkt.either_t.EitherT;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.optics.extensions.LensExtensions;

            public class Orders {
                Either<String, Integer> find(String id) {
                    return Either.left(null);
                }

                Either<Void, Integer> check() {
                    return Either.left((Void) null);
                }

                void more(
                        EitherMonad<String> monad,
                        EitherSelective<String> selective,
                        MonadError<EitherKind.Witness<String>, String> errors,
                        Maybe<Integer> maybe,
                        Either<String, Integer> found) {
                    EitherT<Object, String, Integer> lifted = EitherT.left(new Object(), null);
                    Kind<EitherKind.Witness<String>, Integer> chosen = selective.raiseError(null);
                    Either<String, Integer> converted = maybe.toEither((String) null);
                    Object read = LensExtensions.getEither(new Object(), null, "source");
                    Kind<EitherKind.Witness<String>, Integer> raised = monad.raiseError(null);
                    Kind<EitherKind.Witness<String>, Integer> typed = errors.raiseError(null);
                    Either<String, Integer> blanked = found.mapLeft(e -> null);
                    Either<String, String> both = found.bimap(e -> {
                        System.out.println(e);
                        return null;
                    }, n -> "n");
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.MonadError;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.either.EitherKind;
            import org.higherkindedj.hkt.either.EitherMonad;
            import org.higherkindedj.hkt.either.EitherSelective;
            import org.higherkindedj.hkt.either_t.EitherT;
            import org.higherkindedj.hkt.maybe.Maybe;
            import org.higherkindedj.optics.extensions.LensExtensions;

            public class Orders {
                Either<String, Integer> find(String id) {
                    return /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/Either.left(null);
                }

                /*~~(Void has no value for an error to hold: use Unit)~~>*/Either<Void, Integer> check() {
                    return /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/Either.left((Void) null);
                }

                void more(
                        EitherMonad<String> monad,
                        EitherSelective<String> selective,
                        MonadError<EitherKind.Witness<String>, String> errors,
                        Maybe<Integer> maybe,
                        Either<String, Integer> found) {
                    EitherT<Object, String, Integer> lifted = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/EitherT.left(new Object(), null);
                    Kind<EitherKind.Witness<String>, Integer> chosen = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/selective.raiseError(null);
                    Either<String, Integer> converted = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/maybe.toEither((String) null);
                    Object read = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/LensExtensions.getEither(new Object(), null, "source");
                    Kind<EitherKind.Witness<String>, Integer> raised = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/monad.raiseError(null);
                    Kind<EitherKind.Witness<String>, Integer> typed = /*~~(A Left always holds an error: pass one that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/errors.raiseError(null);
                    Either<String, Integer> blanked = /*~~(Returns null, which now throws: return an error that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/found.mapLeft(e -> null);
                    Either<String, String> both = /*~~(Returns null, which now throws: return an error that says what went wrong, or Unit.INSTANCE where the error type is Unit)~~>*/found.bimap(e -> {
                        System.out.println(e);
                        return null;
                    }, n -> "n");
                }
            }
            """));
  }

  @Test
  void leavesRealErrorsAndOtherMonadsAlone() {
    rewriteRun(
        java(
            """
            package test;

            import org.higherkindedj.hkt.Kind;
            import org.higherkindedj.hkt.Unit;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.optional.OptionalKind;
            import org.higherkindedj.hkt.optional.OptionalMonad;

            public class Fine {
                Either<String, Integer> find(String id) {
                    return Either.left("not found: " + id);
                }

                void more(OptionalMonad optional, Either<String, Integer> found, Runnable r) {
                    Kind<OptionalKind.Witness, Integer> empty = optional.raiseError(null);
                    Either<Unit, Integer> plain = Either.left(Unit.INSTANCE);
                    Either<String, Integer> loud = found.mapLeft(e -> e + "!");
                    Either<String, Integer> right = Either.right(null);
                    Either<String, Integer> nested = found.mapLeft(e -> {
                        Runnable inner = () -> {};
                        return e;
                    });
                }
            }
            """));
  }
}

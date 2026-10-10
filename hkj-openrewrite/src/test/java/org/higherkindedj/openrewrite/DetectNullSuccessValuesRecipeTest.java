// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link DetectNullSuccessValuesRecipe}. */
class DetectNullSuccessValuesRecipeTest implements RewriteTest {

  // Stub the real types so the type-attributed matchers resolve calls and type references.
  private static final String[] STUBS = {
    "package org.higherkindedj.hkt.either; public interface Either<L, R> {"
        + " static <L, R> Either<L, R> right(R value) { return null; }"
        + " static <L, R> Either<L, R> left(L value) { return null; } }",
    "package org.higherkindedj.hkt.trymonad; public interface Try<T> {"
        + " static <T> Try<T> success(T value) { return null; }"
        + " static <T> Try<T> of(java.util.function.Supplier<? extends T> s) { return null; } }",
    "package org.higherkindedj.hkt.effect; public final class EitherPath<E, A> {}",
    "package org.higherkindedj.hkt.effect; public final class TryPath<A> {}",
    "package org.higherkindedj.hkt.effect; public final class Path {"
        + " public static <E, A> EitherPath<E, A> right(A value) { return null; }"
        + " public static <L, A> EitherPath<L, A> right(A value, Object semigroup) { return null; }"
        + " public static <A> TryPath<A> success(A value) { return null; }"
        + " public static <A> TryPath<A> tryOf(java.util.function.Supplier<A> s) { return null; } }",
    "package org.higherkindedj.hkt; public enum Unit { INSTANCE }",
  };

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new DetectNullSuccessValuesRecipe())
        .parser(JavaParser.fromJavaVersion().dependsOn(STUBS));
  }

  @DocumentExample
  @Test
  void marksNullSuccessesAndVoidTypes() {
    rewriteRun(
        java(
            """
            package test;

            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;

            public class Orders {
                Either<String, Void> cancel(String id) {
                    return Either.right(null);
                }

                void more() {
                    EitherPath<String, String> path = Path.right(null);
                    Try<String> attempt = Try.success(null);
                    Try<String> lookup = Try.of(() -> null);
                    Try<Void> work = Try.of(() -> {
                        System.out.println("working");
                        return null;
                    });
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;

            public class Orders {
                /*~~(Void has no value for a success to hold: use Unit)~~>*/Either<String, Void> cancel(String id) {
                    return /*~~(A success always holds a value: pass Unit.INSTANCE for a step with nothing to return)~~>*/Either.right(null);
                }

                void more() {
                    EitherPath<String, String> path = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/Path.right(null);
                    Try<String> attempt = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/Try.success(null);
                    Try<String> lookup = /*~~(Returns null, which now gives a Failure: return a value, or fail with an error that says why)~~>*/Try.of(() -> null);
                    /*~~(Void has no value for a success to hold: use Unit)~~>*/Try<Void> work = /*~~(Returns null, which now gives a Failure: return Unit.INSTANCE for a step with nothing to return)~~>*/Try.of(() -> {
                        System.out.println("working");
                        return null;
                    });
                }
            }
            """));
  }

  @Test
  void leavesNonNullSuccessesAndOtherTypesAlone() {
    rewriteRun(
        java(
            """
            package test;

            import java.util.concurrent.Callable;
            import org.higherkindedj.hkt.Unit;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.trymonad.Try;

            public class Fine {
                Either<String, Unit> done() {
                    return Either.right(Unit.INSTANCE);
                }

                void more(Callable<Void> task) {
                    Either<String, String> left = Either.left(null);
                    Object both = Path.right(null, "semigroup");
                    Try<String> lookup = Try.of(() -> "value");
                    Try<String> branch = Try.of(() -> {
                        Runnable r = () -> {};
                        return "value";
                    });
                }
            }
            """));
  }

  @Test
  void marksALazyTypeTypedWithVoid() {
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion()
                    .dependsOn(
                        "package org.higherkindedj.hkt.vtask; public interface VTask<A> {"
                            + " A execute() throws Throwable; }")),
        java(
            """
            package test;

            import org.higherkindedj.hkt.vtask.VTask;

            public class Jobs {
                VTask<Void> purge() {
                    return () -> null;
                }

                VTask<String> name() {
                    return () -> "job";
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.vtask.VTask;

            public class Jobs {
                /*~~(Void still runs here, but runSafe and the conversions to Try or Either now fail on its null: use Unit, as VTask.exec, Path.vtaskExec and Path.ioRunnable give)~~>*/VTask<Void> purge() {
                    return () -> null;
                }

                VTask<String> name() {
                    return () -> "job";
                }
            }
            """));
  }
}

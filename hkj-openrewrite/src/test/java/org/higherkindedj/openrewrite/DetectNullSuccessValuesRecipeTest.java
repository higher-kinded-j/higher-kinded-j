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
                /*~~(Void has no value for a success to hold: use Unit)~~>*/VTask<Void> purge() {
                    return () -> null;
                }

                VTask<String> name() {
                    return () -> "job";
                }
            }
            """));
  }

  @Test
  void marksNullLazyValuesAndLambdas() {
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion()
                    .dependsOn(
                        "package org.higherkindedj.hkt.vtask; public interface VTask<A> {"
                            + " A execute() throws Throwable;"
                            + " static <A> VTask<A> succeed(A value) { return null; }"
                            + " static <A> VTask<A> of(java.util.concurrent.Callable<A> c) {"
                            + " return null; }"
                            + " default <B> VTask<B> map(java.util.function.Function<? super A,"
                            + " ? extends B> f) { return null; } }",
                        "package org.higherkindedj.hkt.io; public interface IO<A> {"
                            + " A unsafeRunSync();"
                            + " static <A> IO<A> delay(java.util.function.Supplier<A> s) {"
                            + " return null; } }",
                        "package org.higherkindedj.hkt.vstream; public interface VStream<A> {"
                            + " @SafeVarargs static <A> VStream<A> of(A... values) { return null; } }",
                        "package org.higherkindedj.hkt.effect.capability;"
                            + " public interface Composable<A> {"
                            + " <B> Composable<B> map(java.util.function.Function<? super A,"
                            + " ? extends B> mapper); }",
                        "package org.higherkindedj.hkt.effect;"
                            + " public final class IOPath<A>"
                            + " implements org.higherkindedj.hkt.effect.capability.Composable<A> {"
                            + " public <B> IOPath<B> map(java.util.function.Function<? super A,"
                            + " ? extends B> mapper) { return null; } }",
                        "package org.higherkindedj.hkt.effect; public final class WriterPath<W, A> {}",
                        "package org.higherkindedj.hkt.effect; public final class Path {"
                            + " public static <A> IOPath<A> ioPure(A value) { return null; }"
                            + " public static <W, A> WriterPath<W, A> writerPure(A value,"
                            + " Object monoid) { return null; } }",
                        "package org.higherkindedj.hkt; public enum Unit { INSTANCE }")),
        java(
            """
            package test;

            import org.higherkindedj.hkt.Unit;
            import org.higherkindedj.hkt.effect.IOPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.WriterPath;
            import org.higherkindedj.hkt.io.IO;
            import org.higherkindedj.hkt.vstream.VStream;
            import org.higherkindedj.hkt.vtask.VTask;

            public class Jobs {
                void run(VTask<String> task, IOPath<String> path) {
                    VTask<String> missing = VTask.succeed(null);
                    VTask<Unit> work = VTask.of(() -> {
                        System.out.println("working");
                        return null;
                    });
                    VTask<Integer> length = task.map(name -> null);
                    IO<String> read = IO.delay(() -> null);
                    VStream<String> names = VStream.of("a", null);
                    IOPath<String> pure = Path.ioPure(null);
                    WriterPath<String, String> logged = Path.writerPure(null, "monoid");
                    IOPath<Integer> mapped = path.map(name -> null);
                    VTask<String> fine = VTask.succeed("job");
                    IOPath<Integer> sized = path.map(String::length);
                }
            }
            """,
            """
            package test;

            import org.higherkindedj.hkt.Unit;
            import org.higherkindedj.hkt.effect.IOPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.effect.WriterPath;
            import org.higherkindedj.hkt.io.IO;
            import org.higherkindedj.hkt.vstream.VStream;
            import org.higherkindedj.hkt.vtask.VTask;

            public class Jobs {
                void run(VTask<String> task, IOPath<String> path) {
                    VTask<String> missing = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/VTask.succeed(null);
                    VTask<Unit> work = /*~~(Returns null, which now fails when it runs: return Unit.INSTANCE for a step with nothing to return)~~>*/VTask.of(() -> {
                        System.out.println("working");
                        return null;
                    });
                    VTask<Integer> length = /*~~(Returns null, which now fails when it runs: return a value, or fail with an error that says why)~~>*/task.map(name -> null);
                    IO<String> read = /*~~(Returns null, which now fails when it runs: return a value, or fail with an error that says why)~~>*/IO.delay(() -> null);
                    VStream<String> names = /*~~(A stream holds no null element: leave it out, or use a Maybe)~~>*/VStream.of("a", null);
                    IOPath<String> pure = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/Path.ioPure(null);
                    WriterPath<String, String> logged = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/Path.writerPure(null, "monoid");
                    IOPath<Integer> mapped = /*~~(Returns null, which now fails when it runs: return a value, or fail with an error that says why)~~>*/path.map(name -> null);
                    VTask<String> fine = VTask.succeed("job");
                    IOPath<Integer> sized = path.map(String::length);
                }
            }
            """));
  }

  @Test
  void marksTheOtherValueSitesAndLeavesNullTolerantMapsAlone() {
    rewriteRun(
        spec ->
            spec.parser(
                JavaParser.fromJavaVersion()
                    .dependsOn(
                        "package org.higherkindedj.hkt.context; public interface Context<R, A> {"
                            + " static <R, A> Context<R, A> succeed(A value) { return null; } }",
                        "package org.higherkindedj.hkt.vstream; public interface VStream<A> {"
                            + " static <A> VStream<A> iterate(A seed,"
                            + " java.util.function.UnaryOperator<A> f) { return null; } }",
                        "package org.higherkindedj.hkt.vtask; public enum VTaskKindHelper { VTASK;"
                            + " public <A> Object succeed(A value) { return null; } }",
                        "package org.higherkindedj.hkt.state;"
                            + " public record StateTuple<S, A>(A value, S state) {}",
                        "package org.higherkindedj.hkt.writer;"
                            + " public record Writer<W, A>(W log, A value) {"
                            + " public <B> Writer<W, B> map(java.util.function.Function<? super A,"
                            + " ? extends B> f) { return null; } }",
                        "package org.higherkindedj.hkt.effect;"
                            + " public final class CompletableFuturePath<A> {}",
                        "package org.higherkindedj.hkt.effect; public final class MaybePath<A> {"
                            + " public <B> MaybePath<B> map(java.util.function.Function<? super A,"
                            + " ? extends B> f) { return null; } }",
                        "package org.higherkindedj.hkt.effect; public final class Path {"
                            + " public static <A> CompletableFuturePath<A> future("
                            + "java.util.concurrent.CompletableFuture<A> future) {"
                            + " return null; } }")),
        java(
            """
            package test;

            import java.util.concurrent.CompletableFuture;
            import org.higherkindedj.hkt.context.Context;
            import org.higherkindedj.hkt.effect.CompletableFuturePath;
            import org.higherkindedj.hkt.effect.MaybePath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.state.StateTuple;
            import org.higherkindedj.hkt.vstream.VStream;
            import org.higherkindedj.hkt.vtask.VTaskKindHelper;
            import org.higherkindedj.hkt.writer.Writer;

            public class More {
                void run(MaybePath<String> maybe, Writer<String, Integer> writer) {
                    Context<String, String> context = Context.succeed(null);
                    VStream<Integer> counted = VStream.iterate(null, n -> n + 1);
                    Object kind = VTaskKindHelper.VTASK.succeed(null);
                    StateTuple<Integer, String> tuple = new StateTuple<Integer, String>(null, 1);
                    Writer<String, String> logged = new Writer<String, String>("log", null);
                    Writer<String, String> renamed = writer.map(n -> null);
                    CompletableFuturePath<String> started =
                        Path.future(CompletableFuture.runAsync(() -> {}).thenApply(v -> "done"));
                    Object done = Path.future(CompletableFuture.runAsync(() -> {}));
                    MaybePath<Integer> empty = maybe.map(s -> null);
                }
            }
            """,
            """
            package test;

            import java.util.concurrent.CompletableFuture;
            import org.higherkindedj.hkt.context.Context;
            import org.higherkindedj.hkt.effect.CompletableFuturePath;
            import org.higherkindedj.hkt.effect.MaybePath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.state.StateTuple;
            import org.higherkindedj.hkt.vstream.VStream;
            import org.higherkindedj.hkt.vtask.VTaskKindHelper;
            import org.higherkindedj.hkt.writer.Writer;

            public class More {
                void run(MaybePath<String> maybe, Writer<String, Integer> writer) {
                    Context<String, String> context = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/Context.succeed(null);
                    VStream<Integer> counted = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/VStream.iterate(null, n -> n + 1);
                    Object kind = /*~~(A success always holds a value: pass Unit.INSTANCE for a step with nothing to return)~~>*/VTaskKindHelper.VTASK.succeed(null);
                    StateTuple<Integer, String> tuple = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/new StateTuple<Integer, String>(null, 1);
                    Writer<String, String> logged = /*~~(A success always holds a value: use a Maybe for a value that may be missing, or an error that says why)~~>*/new Writer<String, String>("log", null);
                    Writer<String, String> renamed = /*~~(Returns null, which now throws: return a value, or use a Maybe for one that may be missing)~~>*/writer.map(n -> null);
                    CompletableFuturePath<String> started =
                        Path.future(CompletableFuture.runAsync(() -> {}).thenApply(v -> "done"));
                    Object done = /*~~(A future with nothing to return completes with null, which now fails the path: map it to Unit with thenApply(_ -> Unit.INSTANCE) first)~~>*/Path.future(CompletableFuture.runAsync(() -> {}));
                    MaybePath<Integer> empty = maybe.map(s -> null);
                }
            }
            """));
  }
}

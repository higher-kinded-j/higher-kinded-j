// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static org.higherkindedj.optics.processing.NullAwayCompiler.compile;
import static org.higherkindedj.optics.processing.NullAwayCompiler.source;

import com.google.testing.compile.Compilation;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.NullAwayCompiler.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Which nulls NullAway reports when a consumer's lambda or method reference returns one through a
 * factory's functional interface, as the table in the book's "What a nullness checker sees" says.
 *
 * <p>In JSpecify mode alone, NullAway checks a lambda against {@code Function} and its
 * subinterfaces, and against no other functional interface from the JDK, so it never checks what a
 * {@code Supplier}, {@code Callable} or {@code BiFunction} returns. These tests pin NullAway's
 * models as the build resolves them: a release that changes them fails here, and the book's table
 * needs the same change.
 */
@DisplayName("A nullness checker sees a null returned through a JDK functional interface")
class JdkFunctionalInterfaceNullnessTest {

  /**
   * A null returned through each factory: first those NullAway checks in JSpecify mode alone, which
   * take the library's own interfaces, a {@code UnaryOperator} or a {@code Function}, then lambdas
   * and method references through those taking another JDK interface, then a JDK call the JDK
   * models annotate.
   */
  private static final JavaFileObject FACTORIES =
      source(
          "com.example.Factories",
          """
          package com.example;

          import java.io.StringReader;
          import org.higherkindedj.hkt.effect.Path;
          import org.higherkindedj.hkt.effect.TrampolinePath;
          import org.higherkindedj.hkt.either.Either;
          import org.higherkindedj.hkt.io.IO;
          import org.higherkindedj.hkt.lazy.Lazy;
          import org.higherkindedj.hkt.trymonad.Try;
          import org.higherkindedj.hkt.trymonad.TryKindHelper;
          import org.higherkindedj.hkt.vstream.VStream;
          import org.higherkindedj.hkt.vtask.Resource;
          import org.higherkindedj.hkt.vtask.VTask;
          import org.jspecify.annotations.Nullable;

          class Factories {
            static void checkedInJSpecifyMode(@Nullable String raw) {
              Try.attempt(() -> raw);
              Lazy.defer(() -> raw);
              VStream.iterate("x", x -> raw);
              Either.<String, String>right("x").map(x -> raw);
              Try.attempt(Factories::raw);
              Lazy.defer(Factories::raw);
            }

            static void lambdas(
                @Nullable String raw,
                @Nullable StringReader reader,
                @Nullable VTask<String> task,
                @Nullable VStream<String> stream,
                @Nullable Either<String, String> result,
                @Nullable TrampolinePath<String> trampoline) {
              Try.of(() -> raw);
              TryKindHelper.TRY.tryOf(() -> raw);
              VTask.of(() -> raw);
              VTask.delay(() -> raw);
              VTask.blocking(() -> raw);
              IO.delay(() -> raw);
              Path.tryOf(() -> raw);
              Path.io(() -> raw);
              Path.vtask(() -> raw);
              Path.lazyDefer(() -> raw);
              Path.futureAsync(() -> raw);
              Path.vstreamGenerate(() -> raw);
              VStream.generate(() -> raw);
              Resource.make(() -> raw, value -> {});
              Resource.fromAutoCloseable(() -> reader);
              VTask.succeed("x").then(() -> task);
              VStream.defer(() -> stream);
              Path.vresultDefer(() -> result);
              Path.trampolineDefer(() -> trampoline);
              VStream.of("x").zipWith(VStream.of("y"), (x, y) -> raw);
              VStream.of("x").foldLeft("", (sum, x) -> raw);
              VTask<Void> done = VTask.of(() -> { return null; });
            }

            static void references() {
              Try.of(Factories::raw);
              TryKindHelper.TRY.tryOf(Factories::raw);
              VTask.of(Factories::raw);
              VTask.delay(Factories::raw);
              VTask.blocking(Factories::raw);
              IO.delay(Factories::raw);
              Path.tryOf(Factories::raw);
              Path.io(Factories::raw);
              Path.vtask(Factories::raw);
              Path.lazyDefer(Factories::raw);
              Path.futureAsync(Factories::raw);
              Path.vstreamGenerate(Factories::raw);
              VStream.generate(Factories::raw);
              Resource.make(Factories::raw, value -> {});
              Resource.fromAutoCloseable(Factories::reader);
              VTask.succeed("x").then(Factories::task);
              VStream.defer(Factories::stream);
              Path.vresultDefer(Factories::result);
              Path.trampolineDefer(Factories::trampoline);
              VStream.of("x").zipWith(VStream.of("y"), Factories::pair);
            }

            static int home() {
              return System.getProperty("user.home").length();
            }

            static @Nullable String raw() {
              return null;
            }

            static @Nullable String pair(String x, String y) {
              return null;
            }

            static @Nullable StringReader reader() {
              return null;
            }

            static @Nullable VTask<String> task() {
              return null;
            }

            static @Nullable VStream<String> stream() {
              return null;
            }

            static @Nullable Either<String, String> result() {
              return null;
            }

            static @Nullable TrampolinePath<String> trampoline() {
              return null;
            }
          }
          """);

  /**
   * A supplier variable of a nullable value, passed to each factory: where the parameter is {@code
   * Supplier<A>} or {@code Callable<A>}, then where it is {@code Supplier<? extends A>}, then as a
   * method reference.
   */
  private static final JavaFileObject HELD =
      source(
          "com.example.Held",
          """
          package com.example;

          import java.io.StringReader;
          import java.util.concurrent.Callable;
          import java.util.function.Supplier;
          import org.higherkindedj.hkt.effect.Path;
          import org.higherkindedj.hkt.io.IO;
          import org.higherkindedj.hkt.trymonad.Try;
          import org.higherkindedj.hkt.trymonad.TryKindHelper;
          import org.higherkindedj.hkt.vstream.VStream;
          import org.higherkindedj.hkt.vtask.Resource;
          import org.higherkindedj.hkt.vtask.VTask;
          import org.jspecify.annotations.Nullable;

          class Held {
            static void pass(
                Supplier<@Nullable String> s,
                Callable<@Nullable String> c,
                Callable<@Nullable StringReader> r) {
              VTask.of(c);
              VTask.delay(s);
              VTask.blocking(c);
              IO.delay(s);
              Path.io(s);
              Path.vtask(c);
              Path.futureAsync(s);
              Path.vstreamGenerate(s);
              VStream.generate(s);
              Resource.make(c, value -> {});
              Resource.fromAutoCloseable(r);
              Try.of(s);
              TryKindHelper.TRY.tryOf(s);
              Path.tryOf(s);
              Path.lazyDefer(s);
              Try.of(s::get);
              VTask.of(c::call);
            }
          }
          """);

  private static final String RETURNS_NULLABLE =
      "returning @Nullable expression from method with @NonNull return type";
  private static final String REFERENCES_NULLABLE = "referenced method returns @Nullable";
  private static final String INCOMPATIBLE = "[NullAway] incompatible types";

  @Test
  @DisplayName(
      "in JSpecify mode alone, NullAway checks only Function's and the library's own interfaces")
  void jspecifyModeChecksOnlyFunctionAndTheLibrarysOwnInterfaces() {
    Compilation compilation = compile(Configuration.JSPECIFY_MODE_ALONE, FACTORIES);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(6);
    for (int line = 18; line <= 21; line++) {
      assertThat(compilation).hadErrorContaining(RETURNS_NULLABLE).inFile(FACTORIES).onLine(line);
    }
    for (int line = 22; line <= 23; line++) {
      assertThat(compilation)
          .hadErrorContaining(REFERENCES_NULLABLE)
          .inFile(FACTORIES)
          .onLine(line);
    }
  }

  @Test
  @DisplayName("with both options, NullAway checks a lambda through every JDK interface too")
  void bothOptionsCheckEveryJdkInterface() {
    Compilation compilation = compile(Configuration.BOTH_OPTIONS, FACTORIES);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(49);
    for (int line = 18; line <= 21; line++) {
      assertThat(compilation).hadErrorContaining(RETURNS_NULLABLE).inFile(FACTORIES).onLine(line);
    }
    for (int line = 22; line <= 23; line++) {
      assertThat(compilation)
          .hadErrorContaining(REFERENCES_NULLABLE)
          .inFile(FACTORIES)
          .onLine(line);
    }
    for (int line = 33; line <= 54; line++) {
      assertThat(compilation).hadErrorContaining(RETURNS_NULLABLE).inFile(FACTORIES).onLine(line);
    }
    for (int line = 58; line <= 77; line++) {
      assertThat(compilation)
          .hadErrorContaining(REFERENCES_NULLABLE)
          .inFile(FACTORIES)
          .onLine(line);
    }
    // The models annotate the whole JDK, so the consumer's own JDK calls are held to them too
    assertThat(compilation)
        .hadErrorContaining("dereferenced expression")
        .inFile(FACTORIES)
        .onLine(81);
  }

  @Test
  @DisplayName(
      "in JSpecify mode alone, a nullable Supplier variable is refused unless a wildcard takes it")
  void jspecifyModeRefusesANullableSupplierVariableWithoutAWildcard() {
    Compilation compilation = compile(Configuration.JSPECIFY_MODE_ALONE, HELD);

    assertThat(compilation).failed();
    assertThat(compilation).hadErrorCount(11);
    for (int line = 20; line <= 30; line++) {
      assertThat(compilation).hadErrorContaining(INCOMPATIBLE).inFile(HELD).onLine(line);
    }
  }

  @Test
  @DisplayName("with both options, a nullable Supplier variable is refused unless passed as s::get")
  void bothOptionsRefuseANullableSupplierVariableUnlessReferenced() {
    Compilation compilation = compile(Configuration.BOTH_OPTIONS, HELD);

    assertThat(compilation).failed();
    // NullAway does not carry the variable's @Nullable through s::get or c::call
    assertThat(compilation).hadErrorCount(15);
    for (int line = 20; line <= 34; line++) {
      assertThat(compilation).hadErrorContaining(INCOMPATIBLE).inFile(HELD).onLine(line);
    }
  }
}

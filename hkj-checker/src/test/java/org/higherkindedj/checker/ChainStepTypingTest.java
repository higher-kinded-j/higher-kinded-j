// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * What javac alone, without the checker, accepts as a chain step. The capability interfaces carry
 * the Path's family, so a step of another kind of Path, or with another error, environment, state,
 * log or witness type, is a compile error, while a step that fails with a subtype of the chain's
 * error, or needs a supertype of its environment, compiles.
 *
 * <p>Each group is compiled once, each case as its own class, and reported as one test per case.
 */
@DisplayName("javac checks each chain step's Path family")
class ChainStepTypingTest {

  private static final String DOMAIN =
      """
      sealed interface AppError permits NotFound, Denied {}
      record NotFound(String id) implements AppError {}
      record Denied(String reason) implements AppError {}
      interface Named { String name(); }
      record Config(String name) implements Named {}
      static final Semigroup<String> JOIN = (a, b) -> a + b;
      static final Semigroup<AppError> FIRST = (a, b) -> a;
      static final Semigroup<NotFound> FIRST_NOT_FOUND = (a, b) -> a;
      """;

  /** Steps javac rejects, by the method and the type parameter they get wrong. */
  private static final Map<String, String> REJECTED;

  /** Steps javac accepts. */
  private static final Map<String, String> ACCEPTED;

  static {
    Map<String, String> rejected = new LinkedHashMap<>();
    Map<String, String> accepted = new LinkedHashMap<>();
    // Another kind of Path, in every chaining method
    rejected.put("KindVia", "Object run() { return Path.just(1).via(x -> Path.io(() -> x)); }");
    rejected.put("KindThen", "Object run() { return Path.just(1).then(() -> Path.io(() -> 2)); }");
    rejected.put(
        "KindFlatMap", "Object run() { return Path.just(1).flatMap(x -> Path.right(x)); }");
    rejected.put(
        "KindZipWith",
        "Object run() { return Path.just(1).zipWith(Path.io(() -> 2), Integer::sum); }");
    rejected.put(
        "KindZipWith3",
        "Object run() { return Path.just(1).zipWith3(Path.just(2), Path.io(() -> 3),"
            + " (a, b, c) -> a); }");
    rejected.put(
        "KindRecoverWith",
        "Object run() { return Path.<String, Integer>left(\"e\").recoverWith(e -> Path.just(1));"
            + " }");
    rejected.put(
        "KindOrElse",
        "Object run() { return Path.<String, Integer>left(\"e\").orElse(() -> Path.just(1)); }");
    rejected.put(
        "KindZipWithAccum",
        "Object run() { return Path.valid(1, JOIN).zipWithAccum(Path.right(2, JOIN),"
            + " Integer::sum); }");
    rejected.put(
        "KindAndAlso", "Object run() { return Path.valid(1, JOIN).andAlso(Path.right(2, JOIN)); }");
    rejected.put(
        "KindAndThen", "Object run() { return Path.valid(1, JOIN).andThen(Path.right(2, JOIN)); }");
    rejected.put(
        "KindListPathToNonDet",
        "Object run() { return Path.listPath(List.of(1)).via(x -> Path.list(List.of(x))); }");

    // Another error type, in every chaining method
    String either = "EitherPath<AppError, Integer> p; EitherPath<String, Integer> s;";
    rejected.put("ErrorVia", either + " Object run() { return p.via(x -> s); }");
    rejected.put("ErrorThen", either + " Object run() { return p.then(() -> s); }");
    rejected.put("ErrorFlatMap", either + " Object run() { return p.flatMap(x -> s); }");
    rejected.put("ErrorZipWith", either + " Object run() { return p.zipWith(s, Integer::sum); }");
    rejected.put(
        "ErrorZipWith3", either + " Object run() { return p.zipWith3(p, s, (a, b, c) -> a); }");
    rejected.put("ErrorRecoverWith", either + " Object run() { return p.recoverWith(e -> s); }");
    rejected.put("ErrorOrElse", either + " Object run() { return p.orElse(() -> s); }");
    rejected.put(
        "ErrorZipWithAccum",
        "ValidationPath<AppError, Integer> v; ValidationPath<String, Integer> w;"
            + " Object run() { return v.zipWithAccum(w, Integer::sum); }");
    rejected.put(
        "ErrorVResult",
        "VResultPath<AppError, Integer> p; VResultPath<String, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "ErrorEitherOrBoth",
        "EitherOrBothPath<AppError, Integer> p; EitherOrBothPath<String, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "ErrorSibling",
        "EitherPath<NotFound, Integer> p; EitherPath<Denied, Integer> s;"
            + " Object run() { return p.via(x -> s); }");

    // Another environment, state, log or witness type
    rejected.put(
        "EnvironmentNeedsMore",
        "ReaderPath<Named, Integer> p; ReaderPath<Config, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "State",
        "WithStatePath<String, Integer> p; WithStatePath<Integer, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "Log",
        "WriterPath<String, Integer> p; WriterPath<Integer, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "Witness",
        "GenericPath<MaybeKind.Witness, Integer> p; GenericPath<IdKind.Witness, Integer> s;"
            + " Object run() { return p.via(x -> s); }");
    rejected.put(
        "FreeWitness",
        "FreePath<MaybeKind.Witness, Integer> p; FreePath<IdKind.Witness, Integer> s;"
            + " Object run() { return p.via(x -> s); }");

    // A step on a subtype of the error, or a supertype of the environment
    String variants = "EitherPath<AppError, Integer> p; EitherPath<NotFound, Integer> n;";
    accepted.put(
        "Variants",
        variants
            + """
             EitherPath<AppError, Integer> via() { return p.via(x -> n); }
             EitherPath<AppError, Integer> then() { return p.then(() -> n); }
             EitherPath<AppError, Integer> zip() { return p.zipWith(n, Integer::sum); }
             EitherPath<AppError, Integer> zip3() { return p.zipWith3(n, n, (a, b, c) -> a); }
             EitherPath<AppError, Integer> recover() { return p.recoverWith(e -> n); }
             EitherPath<AppError, Integer> orElse() { return p.orElse(() -> n); }
            """);
    accepted.put(
        "AccumulatingVariant",
        "ValidationPath<AppError, Integer> v = Path.valid(1, FIRST);"
            + " ValidationPath<NotFound, Integer> w = Path.valid(2, FIRST_NOT_FOUND);"
            + " ValidationPath<AppError, Integer> run() { return v.zipWithAccum(w, Integer::sum);"
            + " }");
    accepted.put(
        "EnvironmentNeedsLess",
        "ReaderPath<Config, Integer> p; ReaderPath<Named, Integer> s;"
            + " ReaderPath<Config, Integer> run() { return p.via(x -> s); }");
    accepted.put(
        "ForPathVariants",
        "ForPath.EitherPathSteps1<AppError, Integer> f; EitherPath<NotFound, Integer> n;"
            + " EitherPath<AppError, Integer> run() { return f.from(x -> n).yield((a, b) -> a);"
            + " }");
    accepted.put(
        "EffectfulRecoveryIsOpen",
        "IOPath<Integer> run() { return Path.io(() -> 1).handleErrorWith(t -> Path.vtask(() ->"
            + " 2)); }");
    REJECTED = Collections.unmodifiableMap(rejected);
    ACCEPTED = Collections.unmodifiableMap(accepted);
  }

  private static JavaFileObject source(String name, String members) {
    return JavaFileObjects.forSourceString(
        "test." + name,
        """
        package test;
        import java.util.List;
        import org.higherkindedj.hkt.Semigroup;
        import org.higherkindedj.hkt.effect.*;
        import org.higherkindedj.hkt.expression.ForPath;
        import org.higherkindedj.hkt.id.IdKind;
        import org.higherkindedj.hkt.maybe.MaybeKind;
        public class %s {
        %s
        %s
        }
        """
            .formatted(name, DOMAIN, members));
  }

  private static Compilation compileAll(Map<String, String> cases) {
    return javac()
        .withOptions("--enable-preview", "--release", "25")
        .compile(cases.entrySet().stream().map(e -> source(e.getKey(), e.getValue())).toList());
  }

  private static List<String> errorsIn(Compilation compilation, String name) {
    return compilation.errors().stream()
        .filter(
            d -> d.getSource() != null && d.getSource().getName().endsWith("/" + name + ".java"))
        .map(d -> d.getMessage(null))
        .toList();
  }

  @TestFactory
  @DisplayName("a step of another family is a compile error")
  Stream<DynamicTest> rejected() {
    Compilation compilation = compileAll(REJECTED);
    return REJECTED.keySet().stream()
        .map(
            name ->
                DynamicTest.dynamicTest(
                    name,
                    () ->
                        assertThat(errorsIn(compilation, name))
                            .as("%s is rejected for the step's type", name)
                            .anyMatch(
                                message ->
                                    message.contains("incompatible types")
                                        || message.contains("cannot be applied to given types"))));
  }

  @TestFactory
  @DisplayName("a step on a variant of the error, or needing less environment, compiles")
  Stream<DynamicTest> accepted() {
    Compilation compilation = compileAll(ACCEPTED);
    return ACCEPTED.keySet().stream()
        .map(
            name ->
                DynamicTest.dynamicTest(
                    name,
                    () ->
                        assertThat(errorsIn(compilation, name))
                            .as(
                                "%s: %s",
                                name,
                                compilation.diagnostics().stream()
                                    .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                                    .map(d -> d.getMessage(null))
                                    .toList())
                            .isEmpty()));
  }
}

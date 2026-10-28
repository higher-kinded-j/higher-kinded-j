// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.checker;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ErrorTypeMismatchChecker")
class ErrorTypeMismatchCheckerTest {

  private Compilation compile(String pluginArgs, JavaFileObject source) {
    return javac()
        .withOptions(
            "-Xplugin:HKJChecker" + (pluginArgs.isEmpty() ? "" : " " + pluginArgs),
            "--enable-preview",
            "--release",
            "25")
        .compile(source);
  }

  private Compilation compile(JavaFileObject source) {
    return compile("", source);
  }

  /** A class holding the given members, beside a sealed error and an unrelated one. */
  private static JavaFileObject fixture(String name, String members) {
    return JavaFileObjects.forSourceString(
        "test." + name,
        """
        package test;
        import org.higherkindedj.hkt.Semigroup;
        import org.higherkindedj.hkt.effect.*;
        public class %s {
            sealed interface OrderError permits NotFound, PaymentFailed {}
            record NotFound(String id) implements OrderError {}
            record PaymentFailed(Throwable cause) implements OrderError {}
            record DbError(String query) {}
            record User(String id) {}
            static final Semigroup<OrderError> FIRST = (a, b) -> a;
            static final Semigroup<NotFound> FIRST_NOT_FOUND = (a, b) -> a;
            static final Semigroup<DbError> FIRST_DB = (a, b) -> a;
            EitherPath<DbError, User> lookup(String id) {
                return Path.left(new DbError(id));
            }
        %s
        }
        """
            .formatted(name, members));
  }

  private static boolean mentionsMismatch(Compilation c) {
    return c.diagnostics().stream()
        .anyMatch(d -> String.valueOf(d.getMessage(null)).contains("Error type mismatch"));
  }

  @Nested
  @DisplayName("a chain typed too narrowly: names the common supertype and where to write it")
  class NarrowChain {

    @Test
    @DisplayName("a helper typed on one variant opens the chain")
    void helperReceiver() {
      Compilation c =
          compile(
              fixture(
                  "Helper",
                  """
                  EitherPath<NotFound, User> findUser(String id) {
                      return Path.left(new NotFound(id));
                  }
                  EitherPath<OrderError, User> charge(User u) { return Path.right(u); }
                  EitherPath<OrderError, User> run(String id) {
                      return findUser(id).via(u -> charge(u));
                  }
                  """));
      assertThat(c).failed(); // javac rejects the step; the checker explains it
      assertThat(c)
          .hadWarningContaining(
              "Error type mismatch in via(): the chain is typed on NotFound, and this step fails"
                  + " with OrderError. Type the chain on OrderError where it is typed: write"
                  + " findUser(...).<OrderError>mapError(e -> e).");
      assertThat(c).hadWarningContaining("effect/compiler_errors.html");
    }

    @Test
    @DisplayName(
        "mapError(Variant::new) opens an inner chain; the step's error comes from its factory")
    void mapErrorOpensTheChain() {
      // javac cannot infer the step's error type, so the checker reads PaymentFailed off the
      // constructor reference passed to toEitherPath
      Compilation c =
          compile(
              fixture(
                  "MapErrorSite",
                  """
                  EitherPath<OrderError, String> run(EitherPath<String, String> v) {
                      return v.mapError(NotFound::new)
                          .via(s -> Path.tryOf(() -> s).toEitherPath(PaymentFailed::new));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "the chain is typed on NotFound, and this step fails with PaymentFailed. Type the"
                  + " chain on OrderError where it is typed: write .<OrderError>mapError(...).");
    }

    @Test
    @DisplayName("toEitherPath(variant) opens the chain")
    void toEitherPathSite() {
      Compilation c =
          compile(
              fixture(
                  "ToEitherSite",
                  """
                  EitherPath<OrderError, User> charge(User u) { return Path.right(u); }
                  EitherPath<OrderError, User> run(String id) {
                      return Path.maybe(new User(id))
                          .toEitherPath(new NotFound(id))
                          .via(u -> charge(u));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("write .<OrderError>toEitherPath(...).");
    }

    @Test
    @DisplayName("a ValidationPath is widened with mapErrorWith, which takes the new semigroup")
    void validationPath() {
      Compilation c =
          compile(
              fixture(
                  "NarrowValidation",
                  """
                  ValidationPath<OrderError, User> charge(User u) {
                      return Path.valid(u, FIRST);
                  }
                  ValidationPath<OrderError, User> run(ValidationPath<NotFound, User> found) {
                      return found.via(u -> charge(u));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "the chain is typed on NotFound, and this step fails with OrderError. Type the chain"
                  + " on OrderError where it is typed: write found.mapErrorWith(e -> e,"
                  + " semigroup), with a semigroup for OrderError.");
    }

    @Test
    @DisplayName("a factory whose argument fixes the error type gets the widening, not a witness")
    void factoryArgumentFixesTheError() {
      // A witness on Path.either would not compile: the Either argument fixes E as NotFound
      Compilation c =
          compile(
              fixture(
                  "EitherFactory",
                  """
                  EitherPath<OrderError, User> charge(User u) { return Path.right(u); }
                  EitherPath<OrderError, User> run(org.higherkindedj.hkt.either.Either<NotFound, User> e) {
                      return Path.either(e).via(u -> charge(u));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("write Path.either(...).<OrderError>mapError(e -> e).");
    }
  }

  @Nested
  @DisplayName("a step whose own chain has no error type: names where it starts")
  class UntypedStep {

    @Test
    @DisplayName("Path.right(...) opens the step's chain with E inferred as Object")
    void phantomStart() {
      Compilation c =
          compile(
              fixture(
                  "Phantom",
                  """
                  EitherPath<OrderError, User> charge(User u) { return Path.right(u); }
                  EitherPath<OrderError, User> run(EitherPath<OrderError, User> p) {
                      return p.via(u -> Path.right(u).via(this::charge));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "Error type mismatch in via(): this step is typed on Object, because nothing gave its"
                  + " chain an error type. Type it on OrderError where it starts: write"
                  + " Path.<OrderError, User>right(...).");
    }

    @Test
    @DisplayName("a step held in a local variable is followed to the variable's initialiser")
    void localVariable() {
      Compilation c =
          compile(
              fixture(
                  "PhantomLocal",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      var loose = Path.right(new User("x"));
                      return p.via(id -> loose);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("write Path.<OrderError, User>right(...).");
    }

    @Test
    @DisplayName("a step whose start cannot be named gets no witness")
    void parameter() {
      Compilation c =
          compile(
              fixture(
                  "PhantomParameter",
                  """
                  EitherPath<OrderError, User> run(
                      EitherPath<OrderError, String> p, EitherPath<Object, User> loose) {
                      return p.via(id -> loose);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "because nothing gave its chain an error type. Type it on OrderError where it"
                  + " starts. See:");
    }
  }

  @Nested
  @DisplayName("an unrelated error: says to map it inside the step")
  class UnrelatedError {

    private static final String MAP_IT =
        "Map it into the chain's error type inside the step, with .mapError(...).";

    @Test
    @DisplayName("via lambda")
    void via() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedVia",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.via(id -> lookup(id));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "Error type mismatch in via(): this step fails with DbError, which does not extend"
                  + " OrderError. "
                  + MAP_IT);
    }

    @Test
    @DisplayName("via method reference, read from the referenced method's return type")
    void methodReference() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedRef",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.via(this::lookup);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in via(): this step fails with DbError");
    }

    @Test
    @DisplayName("then supplier")
    void then() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedThen",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.then(() -> lookup("x"));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in then(): this step fails with DbError");
    }

    @Test
    @DisplayName("zipWith argument")
    void zipWith() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedZip",
                  """
                  EitherPath<OrderError, String> run(EitherPath<OrderError, String> p) {
                      return p.zipWith(lookup("x"), (s, u) -> s);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in zipWith(): this step fails with DbError");
    }

    @Test
    @DisplayName("zipWith3's second argument")
    void zipWith3() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedZip3",
                  """
                  EitherPath<OrderError, String> run(EitherPath<OrderError, String> p) {
                      return p.zipWith3(p, lookup("x"), (a, b, u) -> a);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in zipWith3(): this step fails with DbError");
    }

    @Test
    @DisplayName("recoverWith and orElse")
    void recovery() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedRecovery",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, User> p) {
                      return p.recoverWith(e -> lookup("x"));
                  }
                  EitherPath<OrderError, User> alt(EitherPath<OrderError, User> p) {
                      return p.orElse(() -> lookup("x"));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in recoverWith(): this step fails with DbError");
      assertThat(c).hadWarningContaining("in orElse(): this step fails with DbError");
    }

    @Test
    @DisplayName("either branch of a conditional, read from the factory's argument")
    void conditional() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedBranch",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, User> p) {
                      return p.via(u -> u.id().isEmpty() ? Path.left("no id") : Path.right(u));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in via(): this step fails with String");
    }

    @Test
    @DisplayName("a ValidationPath, VResultPath or EitherOrBothPath step")
    void otherErrorTypedPaths() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedOthers",
                  """
                  ValidationPath<OrderError, User> v(ValidationPath<OrderError, User> p) {
                      return p.via(u -> Path.<DbError, User>invalid(new DbError("q"), FIRST_DB));
                  }
                  VResultPath<OrderError, User> r(VResultPath<OrderError, User> p) {
                      return p.via(u -> VResultPath.<DbError, User>raiseError(new DbError("q")));
                  }
                  EitherOrBothPath<OrderError, User> b(EitherOrBothPath<OrderError, User> p) {
                      return p.via(u -> Path.<DbError, User>left(new DbError("q"), FIRST_DB));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "this step fails with DbError, which does not extend OrderError. Map it into the"
                  + " chain's error type inside the step, with .mapErrorWith(...).");
      Assertions.assertThat(
              c.warnings().stream()
                  .filter(d -> String.valueOf(d.getMessage(null)).contains("fails with DbError")))
          .hasSize(3);
    }

    @Test
    @DisplayName("an accumulating method's other path")
    void accumulating() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedAccumulating",
                  """
                  ValidationPath<OrderError, User> run(ValidationPath<OrderError, User> p) {
                      return p.andAlso(Path.<DbError, User>invalid(new DbError("q"), FIRST_DB));
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("in andAlso(): this step fails with DbError");
    }

    @Test
    @DisplayName("a parameterised error type is named with its type arguments")
    void typeArguments() {
      Compilation c =
          compile(
              fixture(
                  "UnrelatedList",
                  """
                  ValidationPath<java.util.List<OrderError>, User> run(
                      ValidationPath<java.util.List<OrderError>, User> p,
                      ValidationPath<java.util.List<DbError>, User> q) {
                      return p.via(u -> q);
                  }
                  """));
      assertThat(c).failed();
      assertThat(c)
          .hadWarningContaining(
              "this step fails with List<DbError>, which does not extend List<OrderError>.");
    }

    @Test
    @DisplayName("a nested lambda's return is ignored: the outer lambda's return drives the check")
    void nestedScopeReturnIsIgnored() {
      // A nested lambda whose return precedes the outer lambda's must not be mistaken for it
      Compilation c =
          compile(
              fixture(
                  "NestedScope",
                  """
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.via(id -> {
                          java.util.function.Supplier<String> nested = () -> { return "nested"; };
                          return lookup(id);
                      });
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("this step fails with DbError");
    }

    @Test
    @DisplayName("a block lambda whose first return is correct but a later return is not")
    void mixedReturnsLaterMismatch() {
      Compilation c =
          compile(
              fixture(
                  "MixedReturns",
                  """
                  EitherPath<OrderError, User> ok(String id) { return Path.right(new User(id)); }
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.via(id -> {
                          if (id.isEmpty()) {
                              return ok(id);
                          }
                          return lookup(id);
                      });
                  }
                  """));
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("this step fails with DbError");
    }
  }

  @Nested
  @DisplayName("no false positives")
  class NoFalsePositives {

    @Test
    @DisplayName("the same error type across the chain")
    void sameE() {
      Compilation c =
          compile(
              fixture(
                  "SameE",
                  """
                  EitherPath<OrderError, User> charge(User u) { return Path.right(u); }
                  EitherPath<OrderError, User> run(EitherPath<OrderError, User> p) {
                      return p.via(u -> charge(u));
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("a step that fails with a subtype of the chain's error")
    void subtypeStep() {
      Compilation c =
          compile(
              fixture(
                  "SubtypeStep",
                  """
                  EitherPath<NotFound, User> findUser(String id) {
                      return Path.left(new NotFound(id));
                  }
                  EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                      return p.via(id -> findUser(id));
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("a generic error type (type variable)")
    void genericE() {
      Compilation c =
          compile(
              fixture(
                  "GenericE",
                  """
                  <E> EitherPath<E, Integer> g(String s) { return Path.<E, Integer>right(1); }
                  <E> EitherPath<E, Integer> f(EitherPath<E, String> p) {
                      return p.via(s -> g(s));
                  }
                  """));
      assertThat(c).succeeded();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }

    @Test
    @DisplayName("another kind of Path is path-type-mismatch's concern, not this check's")
    void differentCategory() {
      Compilation c =
          compile(
              "disable=path-type-mismatch",
              fixture(
                  "DiffCat",
                  """
                  MaybePath<Integer> toMaybe(String s) { return Path.just(1); }
                  void run(EitherPath<OrderError, String> v) {
                      var r = v.via(id -> toMaybe(id));
                  }
                  """));
      assertThat(c).failed(); // javac rejects the step
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }
  }

  @Nested
  @DisplayName("configuration")
  class Configuration {

    private static final JavaFileObject MISMATCH =
        fixture(
            "Cfg",
            """
            EitherPath<OrderError, User> run(EitherPath<OrderError, String> p) {
                return p.via(id -> lookup(id));
            }
            """);

    @Test
    @DisplayName("by default the note is a warning beside javac's error")
    void warnDefault() {
      Compilation c = compile(MISMATCH);
      assertThat(c).failed();
      assertThat(c).hadWarningContaining("Error type mismatch in via()");
    }

    @Test
    @DisplayName("severity:error-type-mismatch=error reports it as an error")
    void promoted() {
      Compilation c = compile("severity:error-type-mismatch=error", MISMATCH);
      assertThat(c).hadErrorContaining("Error type mismatch in via()");
    }

    @Test
    @DisplayName("disable=error-type-mismatch leaves javac's error alone")
    void disabled() {
      Compilation c = compile("disable=error-type-mismatch", MISMATCH);
      assertThat(c).failed();
      Assertions.assertThat(mentionsMismatch(c)).isFalse();
    }
  }
}

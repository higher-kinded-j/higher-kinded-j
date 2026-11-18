// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.openrewrite;

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

/** Tests for {@link ReplaceVoidSuccessWithUnitRecipe}, parsed against the real library. */
class ReplaceVoidSuccessWithUnitRecipeTest implements RewriteTest {

  @Override
  public void defaults(RecipeSpec spec) {
    spec.recipe(new ReplaceVoidSuccessWithUnitRecipe())
        .parser(JavaParser.fromJavaVersion().classpath("hkj-core", "hkj-api", "jspecify"));
  }

  @DocumentExample
  @Test
  @DisplayName("Rewrites each shape an adopter's code had: types, right(null) and a VTask lambda")
  void rewritesTheShapesOfAnAdoptersCode() {
    rewriteRun(
        java(
            """
            package com.example;

            import static org.higherkindedj.hkt.either.Either.right;

            import java.util.List;
            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.vtask.VTask;

            class Orders {
                private Either<String, Void> last = right(null);

                Either<String, Void> cancel(String id) {
                    return Either.right((Void) null);
                }

                void record(Either<String, Void> outcome) {
                    last = outcome;
                }

                VTask<Void> purge() {
                    return VTask.of(() -> {
                        System.out.println("purging");
                        return null;
                    });
                }

                EitherPath<String, Void> confirm() {
                    return Path.right(null);
                }

                List<Either<String, Void>> all() {
                    return List.of(right(null), Either.right(null));
                }
            }
            """,
            """
            package com.example;

            import static org.higherkindedj.hkt.either.Either.right;

            import java.util.List;

            import org.higherkindedj.hkt.Unit;
            import org.higherkindedj.hkt.effect.EitherPath;
            import org.higherkindedj.hkt.effect.Path;
            import org.higherkindedj.hkt.either.Either;
            import org.higherkindedj.hkt.vtask.VTask;

            class Orders {
                private Either<String, Unit> last = right(Unit.INSTANCE);

                Either<String, Unit> cancel(String id) {
                    return Either.right(Unit.INSTANCE);
                }

                void record(Either<String, Unit> outcome) {
                    last = outcome;
                }

                VTask<Unit> purge() {
                    return VTask.of(() -> {
                        System.out.println("purging");
                        return Unit.INSTANCE;
                    });
                }

                EitherPath<String, Unit> confirm() {
                    return Path.right(Unit.INSTANCE);
                }

                List<Either<String, Unit>> all() {
                    return List.of(right(Unit.INSTANCE), Either.right(Unit.INSTANCE));
                }
            }
            """));
  }

  @Nested
  @DisplayName("Rewritten")
  class Rewritten {

    @Test
    @DisplayName("A null a lambda returns where its function gives the value")
    void aNullALambdaReturnsAsTheValue() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.util.function.Supplier;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;
              import org.higherkindedj.hkt.effect.FreePath;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.io.IO;
              import org.higherkindedj.hkt.reader.Reader;
              import org.higherkindedj.hkt.trymonad.Try;
              import org.higherkindedj.hkt.vtask.VTask;

              class Steps {
                  Either<String, Void> clear(Either<String, Integer> count) {
                      return count.map(n -> null);
                  }

                  Try<Void> attempt() {
                      return Try.of(() -> null);
                  }

                  VTask<Void> task() {
                      VTask<Void> t = () -> {
                          Supplier<String> name = () -> null;
                          if (name.get() == null) {
                              return null;
                          }
                          return null;
                      };
                      return t;
                  }

                  IO<Void> io() {
                      return () -> null;
                  }

                  Reader<String, Void> reader() {
                      return env -> null;
                  }

                  <F extends WitnessArity<TypeArity.Unary>> FreePath<F, Void> print(FreePath<F, String> line) {
                      return line.map(_ -> null);
                  }
              }
              """,
              """
              package com.example;

              import java.util.function.Supplier;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.WitnessArity;
              import org.higherkindedj.hkt.effect.FreePath;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.io.IO;
              import org.higherkindedj.hkt.reader.Reader;
              import org.higherkindedj.hkt.trymonad.Try;
              import org.higherkindedj.hkt.vtask.VTask;

              class Steps {
                  Either<String, Unit> clear(Either<String, Integer> count) {
                      return count.map(n -> Unit.INSTANCE);
                  }

                  Try<Unit> attempt() {
                      return Try.of(() -> Unit.INSTANCE);
                  }

                  VTask<Unit> task() {
                      VTask<Unit> t = () -> {
                          Supplier<String> name = () -> null;
                          if (name.get() == null) {
                              return Unit.INSTANCE;
                          }
                          return Unit.INSTANCE;
                      };
                      return t;
                  }

                  IO<Unit> io() {
                      return () -> Unit.INSTANCE;
                  }

                  Reader<String, Unit> reader() {
                      return env -> Unit.INSTANCE;
                  }

                  <F extends WitnessArity<TypeArity.Unary>> FreePath<F, Unit> print(FreePath<F, String> line) {
                      return line.map(_ -> Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A Kind over a success type's witness, adding no import a wildcard covers")
    void aKindOverASuccessWitness() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.util.function.Function;
              import org.higherkindedj.hkt.*;
              import org.higherkindedj.hkt.either.EitherKind;
              import org.higherkindedj.hkt.state.StateTuple;
              import org.higherkindedj.hkt.state_t.StateTKind;

              class Stack<S, F extends WitnessArity<TypeArity.Unary>> {
                  Kind<StateTKind.Witness<S, F>, Void> set(S newState, Monad<F> monadF) {
                      Function<S, Kind<F, StateTuple<S, Void>>> runFn =
                          s -> monadF.of(StateTuple.of(newState, (Void) null));
                      return null;
                  }

                  Kind<EitherKind.Witness<String>, Void> done(Monad<EitherKind.Witness<String>> monad) {
                      return monad.of(null);
                  }
              }
              """,
              """
              package com.example;

              import java.util.function.Function;
              import org.higherkindedj.hkt.*;
              import org.higherkindedj.hkt.either.EitherKind;
              import org.higherkindedj.hkt.state.StateTuple;
              import org.higherkindedj.hkt.state_t.StateTKind;

              class Stack<S, F extends WitnessArity<TypeArity.Unary>> {
                  Kind<StateTKind.Witness<S, F>, Unit> set(S newState, Monad<F> monadF) {
                      Function<S, Kind<F, StateTuple<S, Unit>>> runFn =
                          s -> monadF.of(StateTuple.of(newState, Unit.INSTANCE));
                      return null;
                  }

                  Kind<EitherKind.Witness<String>, Unit> done(Monad<EitherKind.Witness<String>> monad) {
                      return monad.of(Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A null passed as the value to a project's own wrapper, and only as the value")
    void aNullPassedToAWrapper() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.util.function.Supplier;
              import org.higherkindedj.hkt.either.Either;

              class Results {
                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  static <T> Either<String, T> pair(String label, T value) {
                      return Either.right(value);
                  }

                  static <A, B> Either<String, B> tagged(A tag, B value) {
                      return Either.right(value);
                  }

                  static <A> Either<String, A> after(Supplier<Void> before, A value) {
                      before.get();
                      return Either.right(value);
                  }

                  Either<String, Void> purge() {
                      return ok(null);
                  }

                  Either<String, Void> labelled() {
                      return pair(null, null);
                  }

                  Either<String, Void> tagged() {
                      return tagged((Void) null, null);
                  }

                  Either<String, Void> afterwards() {
                      return after(() -> null, null);
                  }
              }
              """,
              """
              package com.example;

              import java.util.function.Supplier;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;

              class Results {
                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  static <T> Either<String, T> pair(String label, T value) {
                      return Either.right(value);
                  }

                  static <A, B> Either<String, B> tagged(A tag, B value) {
                      return Either.right(value);
                  }

                  static <A> Either<String, A> after(Supplier<Void> before, A value) {
                      before.get();
                      return Either.right(value);
                  }

                  Either<String, Unit> purge() {
                      return ok(Unit.INSTANCE);
                  }

                  Either<String, Unit> labelled() {
                      return pair(null, Unit.INSTANCE);
                  }

                  Either<String, Unit> tagged() {
                      return tagged((Void) null, Unit.INSTANCE);
                  }

                  Either<String, Unit> afterwards() {
                      return after(() -> null, Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A null given to the overload the call resolves to, not to another of its arity")
    void aNullGivenToTheOverloadTheCallResolvesTo() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Overloads {
                  static <T> Either<String, T> ok(T value, Void ignored, int tag) {
                      return Either.right(value);
                  }

                  static <T> Either<String, T> ok(String note, T value, int tag) {
                      return Either.right(value);
                  }

                  Either<String, Void> first() {
                      return Overloads.<Void>ok((Void) null, null, 0);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;

              class Overloads {
                  static <T> Either<String, T> ok(T value, Void ignored, int tag) {
                      return Either.right(value);
                  }

                  static <T> Either<String, T> ok(String note, T value, int tag) {
                      return Either.right(value);
                  }

                  Either<String, Unit> first() {
                      return Overloads.<Unit>ok(Unit.INSTANCE, null, 0);
                  }
              }
              """));
    }

    @Test
    @DisplayName("The type, but not a null that another overload of the same shape may take")
    void theTypeButNotANullAnotherOverloadMayTake() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Overloads {
                  static Either<String, Void> ok(Void nothing) {
                      return Either.left("nothing");
                  }

                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  Object done() {
                      var result = ok(null);
                      return result;
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;

              class Overloads {
                  static Either<String, Unit> ok(Void nothing) {
                      return Either.left("nothing");
                  }

                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  Object done() {
                      var result = ok(null);
                      return result;
                  }
              }
              """),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Reversed {
                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  static Either<String, Void> ok(Void nothing) {
                      return Either.left("nothing");
                  }

                  Object done() {
                      var result = ok(null);
                      return result;
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;

              class Reversed {
                  static <T> Either<String, T> ok(T value) {
                      return Either.right(value);
                  }

                  static Either<String, Unit> ok(Void nothing) {
                      return Either.left("nothing");
                  }

                  Object done() {
                      var result = ok(null);
                      return result;
                  }
              }
              """));
    }

    @Test
    @DisplayName("A null in brackets or given by a conditional, and a lambda in brackets")
    void aNullInBracketsOrAConditional() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.vtask.VTask;

              class Bracketed {
                  VTask<Void> wrapped() {
                      return VTask.of((() -> null));
                  }

                  VTask<Void> chosen(boolean ready) {
                      return () -> ready ? null : (null);
                  }

                  Either<String, Void> picked(boolean ready) {
                      return Either.right(ready ? (null) : null);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.vtask.VTask;

              class Bracketed {
                  VTask<Unit> wrapped() {
                      return VTask.of((() -> Unit.INSTANCE));
                  }

                  VTask<Unit> chosen(boolean ready) {
                      return () -> ready ? Unit.INSTANCE : (Unit.INSTANCE);
                  }

                  Either<String, Unit> picked(boolean ready) {
                      return Either.right(ready ? (Unit.INSTANCE) : Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A null given to a success type's own method, whose type variable is its class's")
    void aNullGivenToASuccessTypesOwnMethod() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.trymonad.Try;

              class Recovery {
                  Try<Void> quietly(Try<Void> attempt) {
                      return attempt.recover(e -> null);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.trymonad.Try;

              class Recovery {
                  Try<Unit> quietly(Try<Unit> attempt) {
                      return attempt.recover(e -> Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A type argument written on a call, a constructor's value, and an annotated Void")
    void witnessesConstructorsAndAnnotatedVoid() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.state.StateTuple;
              import org.higherkindedj.hkt.writer.Writer;

              class Built {
                  @Target(ElementType.TYPE_USE)
                  @interface Nullable {}

                  @Target(ElementType.TYPE_USE)
                  @interface Audited {}

                  Either<String, java.lang.Void> explicit() {
                      return Either.<String, Void>right(null);
                  }

                  StateTuple<String, Void> tuple() {
                      return new StateTuple<>(null, "state");
                  }

                  Writer<String, Void> written() {
                      return new Writer<String, Void>("log", null);
                  }

                  Either<String, @Nullable Void> nullable() {
                      return Either.right(null);
                  }

                  Either<String, @Nullable @Audited Void> audited() {
                      return Either.right(null);
                  }

                  Either<String, ? extends Void> bounded() {
                      return Either.right(null);
                  }
              }
              """,
              """
              package com.example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.state.StateTuple;
              import org.higherkindedj.hkt.writer.Writer;

              class Built {
                  @Target(ElementType.TYPE_USE)
                  @interface Nullable {}

                  @Target(ElementType.TYPE_USE)
                  @interface Audited {}

                  Either<String, Unit> explicit() {
                      return Either.<String, Unit>right(Unit.INSTANCE);
                  }

                  StateTuple<String, Unit> tuple() {
                      return new StateTuple<>(Unit.INSTANCE, "state");
                  }

                  Writer<String, Unit> written() {
                      return new Writer<String, Unit>("log", Unit.INSTANCE);
                  }

                  Either<String, Unit> nullable() {
                      return Either.right(Unit.INSTANCE);
                  }

                  Either<String, @Audited Unit> audited() {
                      return Either.right(Unit.INSTANCE);
                  }

                  Either<String, ? extends Unit> bounded() {
                      return Either.right(Unit.INSTANCE);
                  }
              }
              """));
    }

    @Test
    @DisplayName(
        "A method overriding a success's own, directly or through a class, and a lambda typed by an"
            + " interface over one")
    void anOverridingMethodAndASubInterfaceLambda() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import org.higherkindedj.hkt.vtask.VTask;

              class Tasks {
                  @Target(ElementType.TYPE_USE)
                  @interface Nullable {}

                  interface Job extends VTask<Void> {}

                  VTask<Void> anonymous() {
                      return new VTask<Void>() {
                          @Override
                          public Void execute() {
                              Runnable r = () -> {};
                              return null;
                          }
                      };
                  }

                  VTask<Void> checked() {
                      return new VTask<Void>() {
                          @Override
                          public @Nullable Void execute() {
                              return null;
                          }
                      };
                  }

                  static final class Flush implements VTask<Void> {
                      @Override
                      public Void execute() throws Throwable {
                          return null;
                      }
                  }

                  static class BaseJob implements VTask<Void> {
                      @Override
                      public Void execute() throws Throwable {
                          return null;
                      }
                  }

                  static final class Purge extends BaseJob {
                      @Override
                      public Void execute() throws Throwable {
                          return null;
                      }
                  }

                  Job job() {
                      return () -> null;
                  }
              }
              """,
              """
              package com.example;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.vtask.VTask;

              class Tasks {
                  @Target(ElementType.TYPE_USE)
                  @interface Nullable {}

                  interface Job extends VTask<Unit> {}

                  VTask<Unit> anonymous() {
                      return new VTask<Unit>() {
                          @Override
                          public Unit execute() {
                              Runnable r = () -> {};
                              return Unit.INSTANCE;
                          }
                      };
                  }

                  VTask<Unit> checked() {
                      return new VTask<Unit>() {
                          @Override
                          public Unit execute() {
                              return Unit.INSTANCE;
                          }
                      };
                  }

                  static final class Flush implements VTask<Unit> {
                      @Override
                      public Unit execute() throws Throwable {
                          return Unit.INSTANCE;
                      }
                  }

                  static class BaseJob implements VTask<Unit> {
                      @Override
                      public Unit execute() throws Throwable {
                          return Unit.INSTANCE;
                      }
                  }

                  static final class Purge extends BaseJob {
                      @Override
                      public Unit execute() throws Throwable {
                          return Unit.INSTANCE;
                      }
                  }

                  Job job() {
                      return () -> Unit.INSTANCE;
                  }
              }
              """));
    }

    @Test
    @DisplayName("The type of a success whose null is not its value, which stays for its own mark")
    void theTypeOfASuccessWhoseNullIsNotTheValue() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.util.concurrent.CompletableFuture;
              import org.higherkindedj.hkt.effect.CompletableFuturePath;
              import org.higherkindedj.hkt.effect.Path;
              import org.higherkindedj.hkt.state.State;

              class Shapes {
                  State<String, Void> state() {
                      return s -> null;
                  }

                  CompletableFuturePath<Void> wrapped(CompletableFuture<Void> sent) {
                      return Path.future(sent);
                  }
              }
              """,
              """
              package com.example;

              import java.util.concurrent.CompletableFuture;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.effect.CompletableFuturePath;
              import org.higherkindedj.hkt.effect.Path;
              import org.higherkindedj.hkt.state.State;

              class Shapes {
                  State<String, Unit> state() {
                      return s -> null;
                  }

                  CompletableFuturePath<Unit> wrapped(CompletableFuture<Void> sent) {
                      return Path.future(sent);
                  }
              }
              """));
    }

    @Test
    @DisplayName("Keeping the line breaks and comments around a replaced null")
    void keepingTheLayoutAroundAReplacedNull() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.state.StateTuple;

              class Layout {
                  StateTuple<String, Void> tuple(String state) {
                      return StateTuple.of(
                          state,
                          null
                      );
                  }

                  Either<String, Void> pending() {
                      return Either.right(null /* nothing yet */);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.state.StateTuple;

              class Layout {
                  StateTuple<String, Unit> tuple(String state) {
                      return StateTuple.of(
                          state,
                          Unit.INSTANCE
                      );
                  }

                  Either<String, Unit> pending() {
                      return Either.right(Unit.INSTANCE /* nothing yet */);
                  }
              }
              """));
    }

    @Test
    @DisplayName(
        "Unit in full where the file uses another type, a variable or a constant named Unit")
    void unitInFullBesideAnotherUnit() {
      rewriteRun(
          java(
              """
              package com.example.measure;

              public enum Unit { KILOGRAM, POUND }
              """),
          java(
              """
              package com.example.measure;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import org.higherkindedj.hkt.either.Either;

              class Scale {
                  @Target(ElementType.TYPE_USE)
                  @interface Audited {}

                  Unit unit = Unit.KILOGRAM;

                  Either<String, Void> reset() {
                      return Either.right(null);
                  }

                  Either<String, @Audited Void> audited() {
                      return Either.right(null);
                  }
              }
              """,
              """
              package com.example.measure;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import org.higherkindedj.hkt.either.Either;

              class Scale {
                  @Target(ElementType.TYPE_USE)
                  @interface Audited {}

                  Unit unit = Unit.KILOGRAM;

                  Either<String, org.higherkindedj.hkt.Unit> reset() {
                      return Either.right(org.higherkindedj.hkt.Unit.INSTANCE);
                  }

                  Either<String, org.higherkindedj.hkt.@Audited Unit> audited() {
                      return Either.right(org.higherkindedj.hkt.Unit.INSTANCE);
                  }
              }
              """),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Generic {
                  <Unit> Unit same(Unit value) {
                      return value;
                  }

                  Either<String, Void> reset() {
                      return Either.right(null);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Generic {
                  <Unit> Unit same(Unit value) {
                      return value;
                  }

                  Either<String, org.higherkindedj.hkt.Unit> reset() {
                      return Either.right(org.higherkindedj.hkt.Unit.INSTANCE);
                  }
              }
              """),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Shadowed {
                  Either<String, Void> reset(int Unit) {
                      return Either.right(null);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Shadowed {
                  Either<String, org.higherkindedj.hkt.Unit> reset(int Unit) {
                      return Either.right(org.higherkindedj.hkt.Unit.INSTANCE);
                  }
              }
              """),
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              enum Pack {
                  Unit, Pair;

                  Either<String, Void> reset() {
                      return Either.right(null);
                  }
              }
              """,
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              enum Pack {
                  Unit, Pair;

                  Either<String, org.higherkindedj.hkt.Unit> reset() {
                      return Either.right(org.higherkindedj.hkt.Unit.INSTANCE);
                  }
              }
              """));
    }
  }

  @Nested
  @DisplayName("Left alone")
  class LeftAlone {

    @Test
    @DisplayName("Void outside a success type, an error typed Void, and a null typed otherwise")
    void otherVoidsAndOtherNulls() {
      rewriteRun(
          java(
              """
              package com.example;

              import java.net.http.HttpResponse;
              import java.util.Map;
              import java.util.concurrent.Callable;
              import java.util.concurrent.CompletableFuture;
              import java.util.concurrent.ExecutorService;
              import java.util.concurrent.Future;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.vtask.VTask;

              class Elsewhere {
                  CompletableFuture<Void> sent;
                  HttpResponse<Void> response;
                  Map<String, Void> seen;

                  Future<Void> submit(ExecutorService executor) {
                      Callable<Void> work = () -> null;
                      return executor.submit(() -> {
                          System.out.println("working");
                          return null;
                      });
                  }

                  Callable<Void> anonymous() {
                      return new Callable<Void>() {
                          @Override
                          public Void call() {
                              return null;
                          }
                      };
                  }

                  Either<Void, String> failed() {
                      return Either.left(null);
                  }

                  Either<String, String> missing() {
                      return Either.right(null);
                  }

                  Either<String, Void[]> array() {
                      return Either.right(null);
                  }

                  Object untyped() {
                      var task = VTask.of(() -> {
                          System.out.println("working");
                          return null;
                      });
                      return task;
                  }
              }
              """));
    }

    @Test
    @DisplayName("A null whose type variable a receiver of another type fixes")
    void aNullWhoseTypeVariableAnotherReceiverFixes() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.either.Either;

              class Store<V> {
                  Either<String, V> save(String id, V value) {
                      return Either.right(value);
                  }

                  static void mark(Store<Void> markers) {
                      markers.save("seen", null);
                  }
              }
              """));
    }

    @Test
    @DisplayName("A Kind whose witness is a type variable")
    void aKindOverATypeVariable() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.Monad;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              class Generic<F extends WitnessArity<TypeArity.Unary>> {
                  Kind<F, Void> generic(Monad<F> monad) {
                      return monad.of(null);
                  }
              }
              """));
    }

    @Test
    @DisplayName("Code that already holds Unit, so a second run changes nothing")
    void codeAlreadyOnUnit() {
      rewriteRun(
          java(
              """
              package com.example;

              import org.higherkindedj.hkt.Unit;
              import org.higherkindedj.hkt.either.Either;
              import org.higherkindedj.hkt.vtask.VTask;

              class Done {
                  Either<String, Unit> cancel() {
                      return Either.right(Unit.INSTANCE);
                  }

                  VTask<Unit> purge() {
                      return VTask.of(() -> Unit.INSTANCE);
                  }

                  VTask<Unit> anonymous() {
                      return new VTask<Unit>() {
                          @Override
                          public Unit execute() {
                              return Unit.INSTANCE;
                          }
                      };
                  }
              }
              """));
    }
  }
}

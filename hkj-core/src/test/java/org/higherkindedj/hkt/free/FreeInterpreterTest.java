// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.EitherAssert.assertThatEither;
import static org.higherkindedj.hkt.assertions.EitherOrBothAssert.assertThatEitherOrBoth;
import static org.higherkindedj.hkt.assertions.TryAssert.assertThatTry;
import static org.higherkindedj.hkt.assertions.WriterAssert.assertThatWriter;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.eitherorboth.EitherOrBothKindHelper.EITHER_OR_BOTH;
import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.either;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.io;
import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.instances.Witnesses.vtask;
import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;
import static org.higherkindedj.hkt.vtask.VTaskKindHelper.VTASK;
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;
import static org.higherkindedj.hkt.writer_t.WriterTKindHelper.WRITER_T;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Pair;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.eitherorboth.EitherOrBothKind;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.vtask.VTaskKind;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterKind;
import org.higherkindedj.hkt.writer_t.WriterT;
import org.higherkindedj.hkt.writer_t.WriterTKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What {@code foldMap} keeps of each instruction's result, and how deep a program it runs, for each
 * kind of target monad.
 */
@DisplayName("Free.foldMap: every instruction's result reaches the target monad")
@SuppressWarnings("migration-nudge") // exercises the Free primitives directly
class FreeInterpreterTest {

  // Far deeper than the default thread stack holds when each step nests the one before
  private static final int DEPTH = 100_000;

  /** An instruction that gives its label, with Id as the instruction set. */
  private static Free<IdKind.Witness, String> step(String label) {
    return Free.liftF(ID.widen(Id.of(label)), Instances.functor(id()));
  }

  /** Two instructions: "a", then "b", giving "ab". */
  private static Free<IdKind.Witness, String> twoSteps() {
    return step("a").flatMap(a -> step("b").map(b -> a + b));
  }

  private static final Function<String, Free<IdKind.Witness, String>> TWO_STEPS_AFTER =
      _ -> twoSteps();

  private static Free<IdKind.Witness, String> rightNested(int n) {
    return n == 0 ? step("x") : step("x").flatMap(_ -> rightNested(n - 1));
  }

  private static Free<IdKind.Witness, Integer> countdown(int n) {
    return n == 0 ? Free.pure(0) : step("x").flatMap(_ -> countdown(n - 1)).map(r -> r + 1);
  }

  private static final Natural<IdKind.Witness, IdKind.Witness> TO_ID =
      new Natural<>() {
        @Override
        public <A> Kind<IdKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return fa;
        }
      };

  /** Logs the label of each instruction it runs. */
  private static final Natural<IdKind.Witness, WriterKind.Witness<List<String>>> TO_WRITER =
      new Natural<>() {
        @Override
        public <A> Kind<WriterKind.Witness<List<String>>, A> apply(Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          return WRITER.widen(Writer.of(List.of("ran " + label(next)), next));
        }
      };

  /** Gives each instruction's result twice. */
  private static final Natural<IdKind.Witness, ListKind.Witness> TO_TWO_RESULTS =
      new Natural<>() {
        @Override
        public <A> Kind<ListKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          return LIST.widen(List.of(next, next));
        }
      };

  /** Gives each instruction's result once, in a list. */
  private static final Natural<IdKind.Witness, ListKind.Witness> TO_ONE_RESULT =
      new Natural<>() {
        @Override
        public <A> Kind<ListKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          return LIST.widen(List.of(ID.narrow(fa).value()));
        }
      };

  /** Warns about each instruction it runs, and still gives its result. */
  private static final Natural<IdKind.Witness, EitherOrBothKind.Witness<List<String>>> TO_WARNINGS =
      new Natural<>() {
        @Override
        public <A> Kind<EitherOrBothKind.Witness<List<String>>, A> apply(
            Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          return label(next).equals("stop")
              ? EITHER_OR_BOTH.widen(EitherOrBoth.left(List.of("stopped")))
              : EITHER_OR_BOTH.widen(EitherOrBoth.both(List.of("checked " + label(next)), next));
        }
      };

  /** Counts the instructions it runs, in a Writer's log. */
  private static final Natural<IdKind.Witness, WriterKind.Witness<Integer>> TO_COUNTING_WRITER =
      new Natural<>() {
        @Override
        public <A> Kind<WriterKind.Witness<Integer>, A> apply(Kind<IdKind.Witness, A> fa) {
          return WRITER.widen(Writer.of(1, ID.narrow(fa).value()));
        }
      };

  /** Counts the instructions it runs, in a WriterT over Id. */
  private static final Natural<IdKind.Witness, WriterTKind.Witness<IdKind.Witness, Integer>>
      TO_COUNTING_WRITER_T =
          new Natural<>() {
            @Override
            public <A> Kind<WriterTKind.Witness<IdKind.Witness, Integer>, A> apply(
                Kind<IdKind.Witness, A> fa) {
              return WRITER_T.widen(
                  WriterT.writer(Instances.monad(id()), ID.narrow(fa).value(), 1));
            }
          };

  /** Counts the instructions it runs, in an EitherOrBoth's left side. */
  private static final Natural<IdKind.Witness, EitherOrBothKind.Witness<Integer>> TO_COUNTED =
      new Natural<>() {
        @Override
        public <A> Kind<EitherOrBothKind.Witness<Integer>, A> apply(Kind<IdKind.Witness, A> fa) {
          return EITHER_OR_BOTH.widen(EitherOrBoth.both(1, ID.narrow(fa).value()));
        }
      };

  private static final Natural<IdKind.Witness, EitherKind.Witness<String>> TO_RIGHT =
      new Natural<>() {
        @Override
        public <A> Kind<EitherKind.Witness<String>, A> apply(Kind<IdKind.Witness, A> fa) {
          return EITHER.widen(Either.right(ID.narrow(fa).value()));
        }
      };

  private static final Natural<IdKind.Witness, IOKind.Witness> TO_IO =
      new Natural<>() {
        @Override
        public <A> Kind<IOKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          return IO_OP.widen(IO.delay(() -> next));
        }
      };

  /** Runs each instruction when the task runs; one labelled "boom" fails then. */
  private static final Natural<IdKind.Witness, VTaskKind.Witness> TO_VTASK =
      new Natural<>() {
        @Override
        public <A> Kind<VTaskKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          return VTASK.widen(
              VTask.delay(
                  () -> {
                    if (label(next).equals("boom")) {
                      throw new IllegalStateException("boom");
                    }
                    return next;
                  }));
        }
      };

  /** Gives each instruction's result; the interpreter itself throws for one labelled "boom". */
  private static final Natural<IdKind.Witness, TryKind.Witness> TO_TRY =
      new Natural<>() {
        @Override
        public <A> Kind<TryKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
          A next = ID.narrow(fa).value();
          if (label(next).equals("boom")) {
            throw new IllegalStateException("boom");
          }
          return TRY.widen(Try.success(next));
        }
      };

  /** Each step is wrapped in a handleError, as a retry loop builds it, nested n deep. */
  private static Free<IdKind.Witness, String> retried(int n) {
    Free<IdKind.Witness, String> program = step("x");
    for (int i = 0; i < n; i++) {
      program = program.flatMap(_ -> step("x")).handleError(String.class, _ -> step("again"));
    }
    return program;
  }

  /** An instruction's value is the next program: the label it was lifted with, held as a Pure. */
  private static String label(Object next) {
    return String.valueOf(((Free.Pure<?, ?>) next).value());
  }

  @Nested
  @DisplayName("a value that holds more than the next program")
  class ValuesWithMore {

    @Test
    @DisplayName("a Writer keeps every instruction's log entry, in order")
    void writerKeepsTheLog() {
      assertThatWriter(twoSteps().foldMap(TO_WRITER, Instances.writer(Monoids.list())))
          .hasLog(List.of("ran a", "ran b"))
          .hasValue("ab");
    }

    @Test
    @DisplayName("a List keeps every branch: two instructions of two results each give four")
    void listKeepsEveryBranch() {
      List<String> results =
          LIST.narrow(twoSteps().foldMap(TO_TWO_RESULTS, Instances.monad(list())));

      assertThat(results).containsExactly("ab", "ab", "ab", "ab");
    }

    @Test
    @DisplayName("an EitherOrBoth keeps every instruction's warning")
    void eitherOrBothKeepsTheWarnings() {
      assertThatEitherOrBoth(
              twoSteps().foldMap(TO_WARNINGS, Instances.eitherOrBoth(Semigroups.list())))
          .hasBoth(List.of("checked a", "checked b"), "ab");
    }

    @Test
    @DisplayName("an EitherOrBoth's warnings so far reach a later value with no result")
    void warningsReachALaterLeft() {
      Free<IdKind.Witness, String> program =
          step("a").flatMap(_ -> step("stop")).flatMap(_ -> step("never"));

      assertThatEitherOrBoth(
              program.foldMap(TO_WARNINGS, Instances.eitherOrBoth(Semigroups.list())))
          .hasLeft(List.of("checked a", "stopped"));
    }

    @Test
    @DisplayName("a Writer keeps the log entries on both sides of a HandleError")
    void writerAcrossHandleError() {
      Free<IdKind.Witness, String> program =
          twoSteps()
              .handleError(IllegalStateException.class, _ -> step("recovered"))
              .flatMap(_ -> step("c"));

      assertThatWriter(program.foldMap(TO_WRITER, Instances.writer(Monoids.list())))
          .hasLog(List.of("ran a", "ran b", "ran c"))
          .hasValue("c");
    }
  }

  @Nested
  @DisplayName("stack safety")
  class StackSafety {

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into Id")
    void rightNestedIntoId() {
      String result = ID.narrow(rightNested(DEPTH).foldMap(TO_ID, Instances.monad(id()))).value();

      assertThat(result).isEqualTo("x");
    }

    @Test
    @DisplayName("a recursive program that maps each result runs 100,000 deep into Id")
    void countdownIntoId() {
      Integer result = ID.narrow(countdown(DEPTH).foldMap(TO_ID, Instances.monad(id()))).value();

      assertThat(result).isEqualTo(DEPTH);
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into a Writer, logging each")
    void rightNestedIntoWriter() {
      assertThatWriter(
              rightNested(DEPTH)
                  .foldMap(TO_COUNTING_WRITER, Instances.writer(Monoids.integerAddition())))
          .hasLog(DEPTH + 1)
          .hasValue("x");
    }

    @Test
    @DisplayName("a recursive program that maps each result runs 100,000 deep into a Writer")
    void countdownIntoWriter() {
      assertThatWriter(
              countdown(DEPTH)
                  .foldMap(TO_COUNTING_WRITER, Instances.writer(Monoids.integerAddition())))
          .hasLog(DEPTH)
          .hasValue(DEPTH);
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into a WriterT over Id")
    void rightNestedIntoWriterT() {
      WriterT<IdKind.Witness, Integer, String> result =
          WRITER_T.narrow(
              rightNested(DEPTH)
                  .foldMap(
                      TO_COUNTING_WRITER_T,
                      Instances.writerT(Instances.monad(id()), Monoids.integerAddition())));
      Pair<String, Integer> run = ID.narrow(result.run()).value();

      assertThat(run.second()).isEqualTo(DEPTH + 1);
      assertThat(run.first()).isEqualTo("x");
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into an EitherOrBoth")
    void rightNestedIntoEitherOrBoth() {
      assertThatEitherOrBoth(
              rightNested(DEPTH)
                  .foldMap(TO_COUNTED, Instances.eitherOrBoth(Monoids.integerAddition())))
          .hasBoth(DEPTH + 1, "x");
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into Either")
    void rightNestedIntoEither() {
      assertThatEither(rightNested(DEPTH).foldMap(TO_RIGHT, Instances.monad(either())))
          .isRight()
          .hasRight("x");
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into a list of one result")
    void rightNestedIntoSingleResultList() {
      List<String> results =
          LIST.narrow(rightNested(DEPTH).foldMap(TO_ONE_RESULT, Instances.monad(list())));

      assertThat(results).containsExactly("x");
    }

    @Test
    @DisplayName("100,000 nested handleErrors run into Either, which uses each handler")
    void nestedHandleErrorsIntoEither() {
      assertThatEither(retried(DEPTH).foldMap(TO_RIGHT, Instances.monadError(either())))
          .hasRight("x");
    }

    @Test
    @DisplayName("100,000 nested handleErrors run into Id, which has no use for the handlers")
    void nestedHandleErrorsIntoId() {
      String result = ID.narrow(retried(DEPTH).foldMap(TO_ID, Instances.monad(id()))).value();

      assertThat(result).isEqualTo("x");
    }

    @Test
    @DisplayName("a right-nested program of 100,000 instructions runs into IO")
    void rightNestedIntoIo() {
      String result =
          IO_OP.narrow(rightNested(DEPTH).foldMap(TO_IO, Instances.monad(io()))).unsafeRunSync();

      assertThat(result).isEqualTo("x");
    }
  }

  @Nested
  @DisplayName("when the steps run")
  class WhenStepsRun {

    @Test
    @DisplayName("into IO, nothing in the program runs until the IO does")
    void ioStaysLazy() {
      List<String> ran = new ArrayList<>();
      Free<IdKind.Witness, String> program =
          Free.<IdKind.Witness, String>pure("a")
              .map(
                  a -> {
                    ran.add("mapped " + a);
                    return a;
                  })
              .flatMap(TWO_STEPS_AFTER);

      IO<String> io = IO_OP.narrow(program.foldMap(TO_IO, Instances.monad(io())));

      assertThat(ran).isEmpty();
      assertThat(io.unsafeRunSync()).isEqualTo("ab");
      assertThat(ran).containsExactly("mapped a");
    }

    @Test
    @DisplayName("into Try, a step that throws gives a failed Try")
    void tryHoldsAThrow() {
      IllegalStateException boom = new IllegalStateException("boom");
      Free<IdKind.Witness, String> program =
          step("a")
              .flatMap(
                  _ -> {
                    throw boom;
                  });

      assertThatTry(program.foldMap(TO_TRY, Instances.monad(try_()))).hasException(boom);
    }

    @Test
    @DisplayName("into Try, an interpreter that throws after the first step gives a failed Try")
    void tryHoldsAnInterpreterThrow() {
      Free<IdKind.Witness, String> program = step("a").flatMap(_ -> step("boom"));

      assertThatTry(program.foldMap(TO_TRY, Instances.monad(try_())))
          .hasExceptionOfType(IllegalStateException.class);
    }

    @Test
    @DisplayName("an interpreter that throws at the first step ends foldMap with it, as before")
    void interpreterThrowAtTheFirstStep() {
      Monad<TryKind.Witness> tryMonad = Instances.monad(try_());

      assertThatThrownBy(() -> step("boom").foldMap(TO_TRY, tryMonad))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("boom");
    }

    @Test
    @DisplayName("into Try, a handleError recovers an interpreter that throws inside it")
    void handleErrorRecoversAnInterpreterThrow() {
      Free<IdKind.Witness, String> program =
          step("a")
              .flatMap(_ -> step("boom"))
              .handleError(IllegalStateException.class, _ -> step("recovered"));

      assertThatTry(program.foldMap(TO_TRY, Instances.monadError(try_()))).hasValue("recovered");
    }

    @Test
    @DisplayName("into VTask, a handleError recovers a step that fails when the task runs")
    void handleErrorRecoversInsideVTask() {
      Free<IdKind.Witness, String> program =
          step("a")
              .flatMap(_ -> step("boom"))
              .handleError(IllegalStateException.class, _ -> step("recovered"))
              .flatMap(r -> step(r + "!"));
      MonadError<VTaskKind.Witness, Throwable> vtask = Instances.monadError(vtask());

      assertThat(VTASK.narrow(program.foldMap(TO_VTASK, vtask)).run()).isEqualTo("recovered!");
    }

    @Test
    @DisplayName("into VTask, a handleError recovers a continuation that throws when the task runs")
    void handleErrorRecoversAContinuationInsideVTask() {
      Free<IdKind.Witness, String> program =
          Free.<IdKind.Witness, String>pure("a")
              .<String>flatMap(
                  _ -> {
                    throw new IllegalStateException("boom");
                  })
              .handleError(IllegalStateException.class, _ -> step("recovered"));
      MonadError<VTaskKind.Witness, Throwable> vtask = Instances.monadError(vtask());

      assertThat(VTASK.narrow(program.foldMap(TO_VTASK, vtask)).run()).isEqualTo("recovered");
    }

    @Test
    @DisplayName(
        "into Try, an interpreter that throws as a handleError's program starts fails the step"
            + " around it, as before")
    void interpreterThrowBeforeTheHandlerStarts() {
      Free<IdKind.Witness, String> program =
          Free.<IdKind.Witness, String>pure("a")
              .flatMap(
                  _ ->
                      step("boom")
                          .handleError(IllegalStateException.class, _ -> step("recovered")));

      assertThatTry(program.foldMap(TO_TRY, Instances.monadError(try_())))
          .hasExceptionOfType(IllegalStateException.class);
    }

    @Test
    @DisplayName("into a Writer, which holds no failure, a step that throws ends foldMap with it")
    void writerRethrows() {
      IllegalStateException boom = new IllegalStateException("boom");
      Free<IdKind.Witness, String> program =
          twoSteps()
              .flatMap(
                  _ -> {
                    throw boom;
                  });
      Monad<WriterKind.Witness<List<String>>> writer = Instances.writer(Monoids.list());

      assertThatThrownBy(() -> program.foldMap(TO_WRITER, writer)).isSameAs(boom);
    }
  }

  @Nested
  @DisplayName("HandleError and Ap nodes in the middle of a program")
  class NodesInTheMiddle {

    @Test
    @DisplayName("the steps after a HandleError run on its result")
    void afterHandleError() {
      Free<IdKind.Witness, String> recovered =
          Free.<IdKind.Witness, String>liftF(ID.widen(Id.of("ignored")), Instances.functor(id()))
              .<String>flatMap(
                  _ -> {
                    throw new IllegalStateException("boom");
                  })
              .handleError(IllegalStateException.class, _ -> step("recovered"));
      Free<IdKind.Witness, String> program = recovered.flatMap(r -> step(r + " then more"));

      assertThatTry(program.foldMap(TO_TRY, Instances.monadError(try_())))
          .hasValue("recovered then more");
    }

    @Test
    @DisplayName("an Ap whose result is null hands the null on, as before")
    void apWithNullResult() {
      Free<IdKind.Witness, String> program =
          new Free.Ap<>(FreeAp.<IdKind.Witness, String>lift(ID.widen(Id.of(null))))
              .flatMap(value -> Free.pure("after " + value));

      String result = ID.narrow(program.foldMap(TO_ID, Instances.monad(id()))).value();

      assertThat(result).isEqualTo("after null");
    }

    @Test
    @DisplayName("an Ap whose result is a list holding null gives that list as it is")
    void apWithAListHoldingNull() {
      Natural<IdKind.Witness, ListKind.Witness> toNullableList =
          new Natural<>() {
            @Override
            public <A> Kind<ListKind.Witness, A> apply(Kind<IdKind.Witness, A> fa) {
              return LIST.widen(Collections.singletonList(ID.narrow(fa).value()));
            }
          };
      Free<IdKind.Witness, String> program =
          new Free.Ap<>(FreeAp.<IdKind.Witness, String>lift(ID.widen(Id.of(null))));

      List<String> result = LIST.narrow(program.foldMap(toNullableList, Instances.monad(list())));

      assertThat(result).containsExactly((String) null);
    }

    @Test
    @DisplayName("the steps after an Ap run on its result")
    void afterAp() {
      FreeAp<IdKind.Witness, Integer> both =
          FreeAp.lift(ID.widen(Id.of(2))).map2(FreeAp.lift(ID.widen(Id.of(3))), Integer::sum);
      Free<IdKind.Witness, Integer> program = new Free.Ap<>(both).flatMap(n -> Free.pure(n * 10));

      Integer result = ID.narrow(program.foldMap(TO_ID, Instances.monad(id()))).value();

      assertThat(result).isEqualTo(50);
    }
  }
}

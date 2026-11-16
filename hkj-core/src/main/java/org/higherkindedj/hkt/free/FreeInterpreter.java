// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.jspecify.annotations.Nullable;

/**
 * Interprets a {@link Free} program into a target monad, for {@link Free#foldMap}.
 *
 * <p>The interpreter walks the program in a loop, holding the {@code flatMap} continuations still
 * to run, and the {@code handleError} handlers around them, on a stack of its own, so a deep
 * program does not deepen the call stack. Each instruction gives a value in the monad, and the loop
 * maps over it to see what it holds:
 *
 * <ul>
 *   <li>When the mapper runs once, the value holds one result, and the loop carries on with it.
 *       Anything else the value holds, such as a {@code Writer}'s log or an {@code EitherOrBoth}'s
 *       warning, joins a context value that each later instruction's value is sequenced after. By
 *       the monad's associativity, that gives what nesting each instruction inside the last would.
 *   <li>Otherwise the rest of the program, up to the nearest handler, runs inside the monad's
 *       {@code flatMap}. A value with no result, such as a {@code Left}, ends that part of the
 *       program. A lazy monad, such as {@code IO}, runs it later. A value with several results,
 *       such as a {@code List} of two, runs it once for each, and each such instruction deepens the
 *       call stack.
 * </ul>
 *
 * <p>At the top of a program interpreted into a lazy monad, each continuation runs inside the
 * monad's {@code flatMap}, so nothing in the program runs before the monad's own value does. Inside
 * a {@code flatMap}, and for a strict monad, the loop runs continuations itself. A throw lands
 * where it would if each step ran inside the one before: a continuation that throws fails in the
 * monad's {@code flatMap}, and so does an instruction that throws once a continuation has run, as
 * the failure of that continuation. So a {@code Try} holds it, and a {@code handleError} around
 * that continuation recovers it.
 */
final class FreeInterpreter {

  private FreeInterpreter() {}

  /** A frame on the interpreter's own stack: a continuation still to run, or a handler. */
  private sealed interface Frame<F extends WitnessArity<TypeArity.Unary>> permits Then, Recover {

    /** The frames below this one. */
    @Nullable Frame<F> rest();

    /** The nearest handler at or below this frame. */
    @Nullable Recover<F> handler();
  }

  /** A {@code flatMap} continuation still to run. */
  private record Then<F extends WitnessArity<TypeArity.Unary>>(
      Function<Object, Free<F, ?>> continuation,
      @Nullable Frame<F> rest,
      @Nullable Recover<F> handler)
      implements Frame<F> {}

  /**
   * A {@code handleError} whose program is running. Its handler recovers that program's result, and
   * {@code outer} holds the context from before the program began.
   */
  private record Recover<F extends WitnessArity<TypeArity.Unary>>(
      Free.HandleError<F, Object, Object> node, @Nullable Kind<?, ?> outer, @Nullable Frame<F> rest)
      implements Frame<F> {

    @Override
    public Recover<F> handler() {
      return this;
    }
  }

  /** What a program is interpreted into. */
  private record Target<
      F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>>(
      Natural<F, M> transform, Monad<M> monad) {}

  /**
   * What mapping over a value found: how often the mapper ran, the result it last saw, and whether
   * the value holds nothing besides that one result.
   */
  private record Probe(int calls, @Nullable Object seen, boolean nothingElse) {}

  /** Throws {@code failure} as it is, so the monad's {@code flatMap} sees what was thrown. */
  private record Rethrow(Throwable failure) {
    @SuppressWarnings("unchecked") // T is erased, so the cast checks nothing
    <T extends Throwable, R> R raise(Object ignored) throws T {
      throw (T) failure;
    }
  }

  /**
   * Interprets {@code program} into {@code monad}.
   *
   * @param program the program to interpret
   * @param transform the natural transformation from the program's instructions to the monad
   * @param monad the target monad
   * @param <F> the instruction witness
   * @param <M> the target monad witness
   * @param <A> the program's result type
   * @return the program's result in the target monad
   */
  static <F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>, A>
      Kind<M, A> foldMap(Free<F, A> program, Natural<F, M> transform, Monad<M> monad) {
    return interpret(program, null, null, isStrict(monad), new Target<>(transform, monad));
  }

  /** Whether {@code monad}'s {@code map} runs its function at once, rather than deferring it. */
  private static <M extends WitnessArity<TypeArity.Unary>> boolean isStrict(Monad<M> monad) {
    boolean[] ran = {false};
    monad.map(
        value -> {
          ran[0] = true;
          return value;
        },
        monad.of(Boolean.TRUE));
    return ran[0];
  }

  /** Interprets {@code program}, then the frames down to {@code stop}. */
  private static <
          F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>, A>
      Kind<M, A> interpret(
          Free<F, ?> program,
          @Nullable Frame<F> frames,
          @Nullable Recover<F> stop,
          boolean direct,
          Target<F, M> target) {
    return run(program, false, null, frames, stop, direct, target);
  }

  /**
   * The interpreter's loop. It walks {@code program}, or starts by handing {@code value} on, and
   * returns when it reaches {@code stop}, the handler of the program it is part of. When {@code
   * direct}, it runs continuations itself; otherwise it runs each inside the monad's {@code
   * flatMap}.
   */
  @SuppressWarnings("unchecked") // each frame takes the value the node before it gave
  private static <
          F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>, A>
      Kind<M, A> run(
          @Nullable Free<F, ?> program,
          boolean handing,
          @Nullable Object value,
          @Nullable Frame<F> frames,
          @Nullable Recover<F> stop,
          boolean direct,
          Target<F, M> target) {
    Monad<M> monad = target.monad();
    Free<F, ?> current = program;
    Frame<F> rest = frames;
    // What the values so far held besides their results; null while they held nothing else
    Kind<M, ?> context = null;
    // A value in the monad still to hand on, whether its result is the next program, and whether
    // it goes straight into the monad's flatMap without being looked into
    Kind<M, ?> step = null;
    boolean stepIsProgram = false;
    boolean deferred = false;
    // Whether a continuation has run in this loop, and the frames and context it left, where an
    // instruction that throws after it fails
    boolean bound = false;
    Frame<F> boundRest = null;
    Kind<M, ?> boundContext = null;
    while (true) {
      if (step != null) {
        Kind<M, ?> looked = step;
        step = null;
        if (deferred) {
          deferred = false;
        } else if (!stepIsProgram && rest == stop) {
          return withContext(context, (Kind<M, A>) looked, monad);
        } else if (!stepIsProgram && rest instanceof Recover<F> handler) {
          step = recover(withContext(context, (Kind<M, Object>) looked, monad), handler, target);
          context = (Kind<M, ?>) handler.outer();
          rest = handler.rest();
          continue;
        } else {
          Probe probe = probe(looked, monad);
          if (probe.calls() == 1) {
            if (!probe.nothingElse()) {
              context = withContext(context, looked, monad);
            }
            if (stepIsProgram) {
              current = (Free<F, ?>) probe.seen();
            } else {
              value = probe.seen();
              handing = true;
            }
            continue;
          }
        }
        Recover<F> handler = handlerWithin(rest, stop);
        Kind<M, Object> remainder =
            withContext(
                context,
                continueIn(looked, stepIsProgram, rest, handler != null ? handler : stop, target),
                monad);
        if (handler == null) {
          return (Kind<M, A>) remainder;
        }
        step = recover(remainder, handler, target);
        stepIsProgram = false;
        context = (Kind<M, ?>) handler.outer();
        rest = handler.rest();
      } else if (handing) {
        handing = false;
        if (direct && rest instanceof Then<F> then) {
          rest = then.rest();
          try {
            current = then.continuation().apply(value);
            bound = true;
            boundRest = rest;
            boundContext = context;
          } catch (Throwable failure) {
            step = failed(failure, monad);
            stepIsProgram = false;
          }
        } else {
          // In the monad: back from this loop, to a handler, or into a lazy target's flatMap
          step = monad.of(value);
          stepIsProgram = false;
          deferred = rest instanceof Then;
        }
      } else if (current instanceof Free.Pure<F, ?> pure) {
        value = pure.value();
        handing = true;
      } else if (current instanceof Free.FlatMapped<F, ?, ?> flatMapped) {
        rest =
            new Then<>(
                (Function<Object, Free<F, ?>>) (Function<?, ?>) flatMapped.continuation(),
                rest,
                rest == null ? null : rest.handler());
        current = flatMapped.sub();
      } else if (current instanceof Free.HandleError<F, ?, ?> handleError) {
        if (monad instanceof MonadError<?, ?>) {
          rest = new Recover<>((Free.HandleError<F, Object, Object>) handleError, context, rest);
          context = null;
        }
        current = handleError.program();
      } else {
        // A Suspend or an Ap, which the natural transformation gives a value in the monad
        stepIsProgram = current instanceof Free.Suspend;
        try {
          step = instruction(current, target);
        } catch (Throwable failure) {
          if (!bound) {
            throw failure;
          }
          step = failed(failure, monad);
          stepIsProgram = false;
          rest = boundRest;
          context = boundContext;
        }
      }
    }
  }

  /**
   * The value an instruction gives in the monad: for a {@code Suspend}, holding the next program;
   * for an {@code Ap}, holding its result.
   */
  private static <F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>>
      Kind<M, ?> instruction(Free<F, ?> node, Target<F, M> target) {
    return node instanceof Free.Suspend<F, ?> suspend
        ? target.transform().apply(suspend.computation())
        : ((Free.Ap<F, ?>) node).applicative().foldMap(target.transform(), target.monad());
  }

  /**
   * Maps over {@code value} to see how many results it holds, and whether it holds anything else.
   */
  private static <M extends WitnessArity<TypeArity.Unary>> Probe probe(
      Kind<M, ?> value, Monad<M> monad) {
    Object marker = new Object();
    Object[] seen = new Object[1];
    int[] calls = {0};
    Kind<M, Object> marked =
        monad.map(
            result -> {
              seen[0] = result;
              calls[0]++;
              return marker;
            },
            value);
    return new Probe(calls[0], seen[0], calls[0] == 1 && marked.equals(monad.of(marker)));
  }

  /** The nearest handler in {@code frames} that belongs to this loop, one above {@code stop}. */
  private static <F extends WitnessArity<TypeArity.Unary>> @Nullable Recover<F> handlerWithin(
      @Nullable Frame<F> frames, @Nullable Recover<F> stop) {
    Recover<F> handler = frames == null ? null : frames.handler();
    return handler == stop ? null : handler;
  }

  /**
   * Runs the rest of the program, down to {@code stop}, inside the monad on what {@code step}
   * holds.
   */
  @SuppressWarnings("unchecked") // a Suspend's value holds the next program
  private static <F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>>
      Kind<M, Object> continueIn(
          Kind<M, ?> step,
          boolean stepIsProgram,
          @Nullable Frame<F> rest,
          @Nullable Recover<F> stop,
          Target<F, M> target) {
    return target
        .monad()
        .flatMap(
            next ->
                stepIsProgram
                    ? interpret((Free<F, ?>) next, rest, stop, true, target)
                    : run(null, true, next, rest, stop, true, target),
            step);
  }

  /**
   * Recovers {@code result} with {@code handler}'s node: a failure of its error type runs the
   * handler's program, and any other failure is raised again.
   */
  @SuppressWarnings("unchecked") // a handler frame is pushed only for a MonadError target
  private static <F extends WitnessArity<TypeArity.Unary>, M extends WitnessArity<TypeArity.Unary>>
      Kind<M, Object> recover(Kind<M, Object> result, Recover<F> handler, Target<F, M> target) {
    MonadError<M, Object> monadError = (MonadError<M, Object>) target.monad();
    Free.HandleError<F, Object, Object> node = handler.node();
    return monadError.handleErrorWith(
        result,
        error ->
            node.errorType().isInstance(error)
                ? interpret(node.handler().apply(error), null, null, true, target)
                : monadError.raiseError(error));
  }

  /** A value in the monad that fails with {@code failure}, as a step that throws it would. */
  private static <M extends WitnessArity<TypeArity.Unary>> Kind<M, Object> failed(
      Throwable failure, Monad<M> monad) {
    return monad.flatMap(
        new Rethrow(failure)::<RuntimeException, Kind<M, Object>>raise, monad.of(Unit.INSTANCE));
  }

  /** Sequences {@code result} after {@code context}, keeping what {@code context} holds. */
  private static <M extends WitnessArity<TypeArity.Unary>, B> Kind<M, B> withContext(
      @Nullable Kind<M, ?> context, Kind<M, B> result, Monad<M> monad) {
    return context == null ? result : monad.flatMap(_ -> result, context);
  }
}

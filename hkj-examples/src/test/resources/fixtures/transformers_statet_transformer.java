// Fixture for hkj-book/src/transformers/statet_transformer.md
//
// The page pushes and pops one stack three ways: threading the state by hand, a WithStatePath, and
// StateT over Optional. The stack helpers each way needs are declared here; the worked example
// declares its own `push`/`pop`, which hide these.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.id.IdKindHelper.ID;
import static org.higherkindedj.hkt.instances.Witnesses.id;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.optional.OptionalKindHelper.OPTIONAL;
import static org.higherkindedj.hkt.state_t.StateTKindHelper.STATE_T;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.WithStatePath;
import org.higherkindedj.hkt.expression.For;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.id.IdKind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.state.StateTuple;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.state_t.StateTKind;
import org.higherkindedj.hkt.state_t.StateTMonad;

class Fixture {

  static final MonadError<OptionalKind.Witness, Unit> optionalMonad =
      Instances.monadError(optional());

  static final MonadError<OptionalKind.Witness, Unit> optMonad = optionalMonad;

  static final Monad<StateTKind.Witness<Integer, OptionalKind.Witness>> stateTMonad =
      Instances.stateT(optionalMonad);

  static final StateT<Integer, OptionalKind.Witness, String> computation =
      StateT.create(
          currentState ->
              currentState < 0
                  ? OPTIONAL.widen(Optional.empty())
                  : OPTIONAL.widen(
                      Optional.of(StateTuple.of(currentState + 1, "Value: " + currentState))),
          optionalMonad);

  static final StateT<Integer, OptionalKind.Witness, String> optStateT = computation;

  /** Threading the stack by hand. */
  static StateTuple<List<Integer>, Unit> push(List<Integer> stack, Integer value) {
    return StateTuple.of(prepend(stack, value), Unit.INSTANCE);
  }

  static List<Integer> prepend(List<Integer> stack, Integer value) {
    var newStack = new LinkedList<>(stack);
    newStack.add(0, value);
    return newStack;
  }

  /** The StateT-over-Optional stack the comprehension composes. */
  static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Unit> push(Integer value) {
    return STATE_T.stateT(
        stack -> OPTIONAL.widen(Optional.of(StateTuple.of(prepend(stack, value), Unit.INSTANCE))),
        optionalMonad);
  }

  static Kind<StateTKind.Witness<List<Integer>, OptionalKind.Witness>, Integer> pop() {
    return STATE_T.stateT(
        stack -> {
          if (stack.isEmpty()) {
            return OPTIONAL.widen(Optional.empty());
          }
          var newStack = new LinkedList<>(stack);
          Integer popped = newStack.remove(0);
          return OPTIONAL.widen(Optional.of(StateTuple.of(newStack, popped)));
        },
        optionalMonad);
  }
}

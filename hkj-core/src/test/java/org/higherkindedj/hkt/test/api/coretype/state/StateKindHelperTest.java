// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.test.api.coretype.state;

import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state.StateKind;
import org.higherkindedj.hkt.state.StateKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for State type.
 *
 * <p>This class provides convenient testing for State's KindHelper implementation with automatic
 * helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.stateKindHelper(State.get())
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.stateKindHelper(State.pure(42))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <S> The state type
 * @param <A> The value type
 */
public final class StateKindHelperTest<S, A>
    extends BaseKindHelperConfig<State<S, A>, StateKind.Witness<S>, A> {

  private static final StateKindHelper STATE = StateKindHelper.STATE;

  public StateKindHelperTest(State<S, A> instance) {
    super(instance, getStateClass(), state -> STATE.widen(state), kind -> STATE.narrow(kind));
  }

  @SuppressWarnings("unchecked")
  private static <S, A> Class<State<S, A>> getStateClass() {
    return (Class<State<S, A>>) (Class<?>) State.class;
  }

  @Override
  protected StateKindHelperTest<S, A> self() {
    return this;
  }
}

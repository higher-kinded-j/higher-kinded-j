/**
 * State core type testing with progressive disclosure API.
 *
 * <p>This package provides fluent APIs for testing the State type:
 *
 * <ul>
 *   <li>Factory operations (pure, get, set, modify, inspect)
 *   <li>run evaluation returning StateTuple
 *   <li>map and flatMap transformations
 *   <li>State threading through computations
 *   <li>KindHelper implementation
 * </ul>
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Test Core Type Operations:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.state(State.class)
 *     .withState(State.get())
 *     .withInitialState(initialState)
 *     .withMappers(Object::toString)
 *     .testAll();
 * }</pre>
 *
 * <h3>Test KindHelper:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.stateKindHelper(State.pure(42))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 *
 * <pre>{@code
 * CoreTypeTest.state(State.class)
 *     .withState(stateInstance)
 *     .withInitialState(initialState)
 *     .withMappers(mapper)
 *     .skipEdgeCases()
 *     .testAll();
 * }</pre>
 *
 * @see org.higherkindedj.hkt.test.api.CoreTypeTest
 */
@NullMarked
package org.higherkindedj.hkt.test.api.coretype.state;

import org.jspecify.annotations.NullMarked;

/**
 * Provides the {@link org.higherkindedj.hkt.state_op.StateOp} effect algebra for
 * optics-parameterised state operations within Free monad programs.
 *
 * <p>{@code StateOp<S, A>} is a library-provided effect that composes via {@link
 * org.higherkindedj.hkt.eitherf.EitherF} like any other effect algebra. Unlike traditional state
 * effects that offer coarse-grained {@code get}/{@code put}, StateOp operations are parameterised
 * by optics (Getter, Lens, Prism, Traversal), enabling fine-grained state access, static analysis,
 * and composability.
 *
 * <p>Two standard interpreters are provided:
 *
 * <ul>
 *   <li>{@link org.higherkindedj.hkt.state_op.StateOpInterpreter} — interprets into the State monad
 *   <li>{@link org.higherkindedj.hkt.state_op.IOStateOpInterpreter} — interprets into IO using an
 *       AtomicReference for thread-safe mutable state
 * </ul>
 */
@NullMarked
package org.higherkindedj.hkt.state_op;

import org.jspecify.annotations.NullMarked;

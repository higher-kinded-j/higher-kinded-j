/**
 * Provides {@link org.higherkindedj.hkt.inject.Inject}, which witnesses that an effect type can be
 * embedded into a larger composed effect type.
 *
 * <p>{@code Inject<F, G>} is the type-level equivalent of an interface constraint: {@code
 * Inject<ConsoleOp, G>} means "G supports console operations." Standard instances are provided for
 * {@link org.higherkindedj.hkt.eitherf.EitherF} composition.
 *
 * <p>Includes {@link org.higherkindedj.hkt.inject.Inject} and {@link
 * org.higherkindedj.hkt.inject.InjectInstances}.
 */
@NullMarked
package org.higherkindedj.hkt.inject;

import org.jspecify.annotations.NullMarked;

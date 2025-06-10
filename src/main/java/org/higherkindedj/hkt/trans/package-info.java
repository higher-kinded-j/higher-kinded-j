/**
 * Contains monad transformers, which are type constructors that add computational context to an
 * existing monad.
 *
 * <p>Monad transformers, such as {@code EitherT}, {@code MaybeT}, and {@code StateT}, allow for the
 * functionality of different monads (like error handling or state management) to be layered on top
 * of a base monad (like {@code IO} or {@code CompletableFuture}). This enables the creation of
 * powerful, composite monads tailored to specific needs without manual nesting.
 */
@org.jspecify.annotations.NullMarked
package org.higherkindedj.hkt.trans;

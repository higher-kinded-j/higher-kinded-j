// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.effect.capability.Chainable;

/**
 * Service Provider Interface for creating Path instances from Kind values.
 *
 * <p>Implement this interface to provide Path support for custom effect types. Implementations are
 * discovered via {@link java.util.ServiceLoader}.
 *
 * <h2>Implementation Example</h2>
 *
 * <pre>{@code
 * public class ApiResultPathProvider implements PathProvider<ApiResultKind.Witness> {
 *
 *     @Override
 *     public Class<?> witnessType() {
 *         return ApiResultKind.Witness.class;
 *     }
 *
 *     @Override
 *     public <A> Chainable<A> createPath(Kind<ApiResultKind.Witness, A> kind) {
 *         return GenericPath.of(kind, ApiResultMonad.INSTANCE);
 *     }
 *
 *     @Override
 *     public Monad<ApiResultKind.Witness> monad() {
 *         return ApiResultMonad.INSTANCE;
 *     }
 * }
 * }</pre>
 *
 * <h2>Registration</h2>
 *
 * <p>Register in {@code META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider}:
 *
 * <pre>
 * com.example.ApiResultPathProvider
 * </pre>
 *
 * @param <F> the witness type of the effect
 */
public interface PathProvider<F extends WitnessArity<TypeArity.Unary>> {

  /**
   * Returns the witness type class this provider handles.
   *
   * @return the witness type class
   */
  Class<?> witnessType();

  /**
   * Creates a Path from a Kind value.
   *
   * @param kind the Kind value to wrap; must not be null
   * @param <A> the value type
   * @return a Chainable path wrapping the Kind value
   */
  <A> Chainable<A> createPath(Kind<F, A> kind);

  /**
   * Returns the Monad instance for this effect type.
   *
   * @return the Monad instance
   */
  Monad<F> monad();

  /**
   * Returns the MonadError instance if this effect supports error handling.
   *
   * <p>Default implementation returns null, indicating no error handling support.
   *
   * @param <E> the error type
   * @return the MonadError instance, or null if not supported
   */
  default <E> MonadError<F, E> monadError() {
    return null;
  }

  /**
   * Returns whether this provider supports error recovery operations.
   *
   * <p>When true, the {@link #monadError()} method returns a valid MonadError instance.
   *
   * @return true if error recovery is supported
   */
  default boolean supportsRecovery() {
    return monadError() != null;
  }

  /**
   * Returns a human-readable name for this provider.
   *
   * <p>Used in error messages and debugging.
   *
   * @return the provider name
   */
  default String name() {
    return witnessType().getSimpleName() + "PathProvider";
  }
}

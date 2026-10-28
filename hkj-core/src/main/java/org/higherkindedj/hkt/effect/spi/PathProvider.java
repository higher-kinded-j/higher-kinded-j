// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.spi;

import java.util.Optional;
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
 *     public <A> GenericPath<ApiResultKind.Witness, A> createPath(
 *             Kind<ApiResultKind.Witness, A> kind) {
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
 * <p>Declare the concrete Path type as the return type of {@link #createPath(Kind)}, as the example
 * does. Code that holds the provider can then chain the result, which a {@code Chainable<?, A>}
 * does not allow, because its family is open.
 *
 * <h2>Registration</h2>
 *
 * <p>Register in {@code META-INF/services/org.higherkindedj.hkt.effect.spi.PathProvider}:
 *
 * <pre>
 * com.example.ApiResultPathProvider
 * </pre>
 *
 * <p>A module with a {@code module-info} also lists the provider in its {@code provides} clause. On
 * the module path, {@link java.util.ServiceLoader} reads only that clause, so a provider registered
 * in the services file alone is not found:
 *
 * <pre>{@code
 * provides org.higherkindedj.hkt.effect.spi.PathProvider with com.example.ApiResultPathProvider;
 * }</pre>
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
   * @return a path wrapping the Kind value; an implementation declares its concrete Path type
   */
  <A> Chainable<?, A> createPath(Kind<F, A> kind);

  /**
   * Returns the Monad instance for this effect type.
   *
   * @return the Monad instance
   */
  Monad<F> monad();

  /**
   * Returns the MonadError instance if this effect supports error handling.
   *
   * <p>The default implementation returns an empty {@code Optional}, meaning the effect has no
   * error handling.
   *
   * @param <E> the error type
   * @return an Optional containing the MonadError, or an empty Optional if not supported
   */
  default <E> Optional<MonadError<F, E>> monadError() {
    return Optional.empty();
  }

  /**
   * Returns whether this provider supports error recovery operations.
   *
   * <p>When true, the {@link #monadError()} method returns an Optional holding a MonadError.
   *
   * @return true if error recovery is supported
   */
  default boolean supportsRecovery() {
    return monadError().isPresent();
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

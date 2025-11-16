// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.prism;

import java.util.Optional;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.optics.Prism;
import org.jspecify.annotations.NullMarked;

/**
 * Provides standard {@link Prism} instances for common Java types.
 *
 * <p>This class contains factory methods that create {@code Prism} instances for:
 *
 * <ul>
 *   <li>{@link Optional} - focusing on the present case
 * </ul>
 *
 * <h3>Usage with At:</h3>
 *
 * <p>The {@link #some()} prism is particularly useful when composing with {@code At} to access
 * values through optional layers:
 *
 * <pre>{@code
 * // Compose At with Prism for deep access
 * Lens<User, Map<String, Address>> addressMapLens = ...;
 * At<Map<String, Address>, String, Address> mapAt = AtInstances.mapAt();
 * Prism<Optional<Address>, Address> somePrism = Prisms.some();
 *
 * // Create a Traversal that focuses on the home address (if present)
 * Traversal<User, Address> homeAddressTraversal =
 *     addressMapLens
 *         .andThen(mapAt.at("home"))      // Lens<User, Optional<Address>>
 *         .asTraversal()
 *         .andThen(somePrism.asTraversal());
 *
 * // Now you can modify the address if it exists
 * User updatedUser = Traversals.modify(
 *     homeAddressTraversal,
 *     addr -> addr.withCity("New York"),
 *     user
 * );
 * }</pre>
 */
@NullMarked
public final class Prisms {

  /** Private constructor to prevent instantiation. */
  private Prisms() {}

  /**
   * Creates a {@link Prism} that focuses on the present case of an {@link Optional}.
   *
   * <p>This prism has the following semantics:
   *
   * <ul>
   *   <li>{@code getOptional(Optional.of(x))} returns {@code Optional.of(x)}
   *   <li>{@code getOptional(Optional.empty())} returns {@code Optional.empty()}
   *   <li>{@code build(x)} returns {@code Optional.of(x)}
   * </ul>
   *
   * <p>This is useful for composing through optional layers, such as when working with {@code At}
   * which returns {@code Lens<S, Optional<A>>}.
   *
   * @param <A> The type contained in the Optional
   * @return A Prism focusing on the present case of Optional
   */
  public static <A> Prism<Optional<A>, A> some() {
    return Prism.of(opt -> opt, Optional::of);
  }

  /**
   * Creates a {@link Prism} that focuses on the empty case of an {@link Optional}.
   *
   * <p>This prism matches when the Optional is empty:
   *
   * <ul>
   *   <li>{@code getOptional(Optional.empty())} returns {@code Optional.of(Unit.INSTANCE)}
   *   <li>{@code getOptional(Optional.of(x))} returns {@code Optional.empty()}
   *   <li>{@code build(Unit.INSTANCE)} returns {@code Optional.empty()}
   * </ul>
   *
   * <p>This is less commonly used than {@link #some()}, but can be useful for checking absence.
   *
   * @param <A> The type that would be contained in the Optional (phantom type)
   * @return A Prism focusing on the empty case of Optional
   */
  public static <A> Prism<Optional<A>, Unit> none() {
    return Prism.of(
        opt -> opt.isEmpty() ? Optional.of(Unit.INSTANCE) : Optional.empty(),
        _unit -> Optional.empty());
  }
}

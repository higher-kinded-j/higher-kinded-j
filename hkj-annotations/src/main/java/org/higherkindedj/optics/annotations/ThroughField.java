// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the traversal should go through a specific field.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a traversal
 * optic that composes a lens to a container field with a traversal for that container type.
 *
 * <p>The processor will generate code like:
 *
 * <pre>{@code
 * // Explicit traversal for List<Player>
 * playersLens().composeTraversal(Traversals.list())
 * }</pre>
 *
 * <p><strong>Note:</strong> The {@link #traversal()} parameter is currently required for the
 * processor to generate working code. Auto-detection based on container type is planned for a
 * future version.
 *
 * <p>Planned auto-detection will support the following container types:
 *
 * <ul>
 *   <li>{@code List<X>} → {@code Traversals.list()}
 *   <li>{@code Set<X>} → {@code Traversals.set()}
 *   <li>{@code Optional<X>} → {@code Affines.optional()}
 *   <li>{@code X[]} → {@code Traversals.array()}
 *   <li>{@code Map<K, V>} → {@code Traversals.mapValues()}
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * // External class
 * public class Team {
 *     private final String name;
 *     private final List<Player> players;
 *     // getters, toBuilder, etc.
 * }
 *
 * @ImportOptics
 * interface TeamOptics extends OpticsSpec<Team> {
 *
 *     @ViaBuilder
 *     Lens<Team, String> name();
 *
 *     @ViaBuilder
 *     Lens<Team, List<Player>> players();
 *
 *     // Traverse through players field with explicit traversal
 *     @ThroughField(field = "players", traversal = "org.higherkindedj.optics.Traversals.list()")
 *     Traversal<Team, Player> eachPlayer();
 *
 *     // Custom container with explicit traversal
 *     @ThroughField(field = "tree", traversal = "com.mycompany.TreeTraverse.preOrder()")
 *     Traversal<Team, Node> eachNode();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see TraverseWith
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ThroughField {

  /**
   * The field name to traverse through.
   *
   * <p>The field must have a container type (List, Set, Optional, array, Map) or the {@link
   * #traversal()} must be specified explicitly.
   *
   * @return the field name
   */
  String field();

  /**
   * The traversal to use for the container.
   *
   * <p><strong>Currently required:</strong> While the default value is empty, you must specify this
   * parameter for the processor to generate working code. Auto-detection based on container type is
   * planned for a future version. Omitting this parameter results in generated code with a TODO
   * placeholder.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "org.higherkindedj.optics.Traversals.list()"}
   *   <li>{@code "org.higherkindedj.optics.Traversals.set()"}
   *   <li>{@code "org.higherkindedj.optics.Affines.optional()"}
   *   <li>{@code "com.mycompany.TreeTraverse.INSTANCE"}
   * </ul>
   *
   * @return the fully qualified traversal reference
   */
  String traversal() default "";
}

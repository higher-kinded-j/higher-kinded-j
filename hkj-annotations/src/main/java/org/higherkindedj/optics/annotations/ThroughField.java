// Copyright (c) 2025 - 2026 Magnus Smith
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
 * // Auto-detected traversal for List<Player>
 * players().andThen(Traversals.forList())
 * }</pre>
 *
 * <h2>Auto-Detection</h2>
 *
 * <p>When the {@link #traversal()} parameter is omitted, the processor detects the traversal from
 * the field's declared container type. The match is on the interface itself:
 *
 * <ul>
 *   <li>{@code List<X>} &rarr; {@code Traversals.forList()}
 *   <li>{@code Set<X>} &rarr; {@code Traversals.forSet()}
 *   <li>{@code Collection<X>} &rarr; {@code Traversals.forCollection()}
 *   <li>{@code Optional<X>} &rarr; {@code Traversals.forOptional()}
 *   <li>{@code X[]} arrays of a reference type &rarr; {@code Traversals.forArray()} (an array of a
 *       primitive is refused, as the traversal walks an {@code Object[]})
 *   <li>{@code Map<K, V>} &rarr; {@code Traversals.forMapValues()}
 * </ul>
 *
 * <p>A field declared as something narrower than the interface, a concrete container ({@code
 * ArrayList<X>}, {@code HashSet<X>}, {@code TreeMap<K, V>}) or another interface ({@code Deque},
 * {@code SortedSet}), is refused at the declaration: the standard traversal promises no more than
 * the interface type, and the field could not take that value back. Name a {@link #traversal()}
 * that rebuilds the declared type, for example one built with {@code
 * Traversals.forIterableCollecting} or {@code Traversals.forMapValuesCollecting}, or, where the
 * type is yours, declare the field as the interface. A field of any other type draws a compile-time
 * error asking for {@link #traversal()} as well.
 *
 * <h2>Examples</h2>
 *
 * <pre>{@code
 * // External class
 * public record Team(String name, List<Player> players) {}
 *
 * @ImportOptics
 * interface TeamOptics extends OpticsSpec<Team> {
 *
 *     Lens<Team, String> name();
 *
 *     Lens<Team, List<Player>> players();
 *
 *     // Auto-detected traversal for List<Player>
 *     @ThroughField(field = "players")
 *     Traversal<Team, Player> eachPlayer();
 *
 *     // Explicit traversal for custom container
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
   * <p>The field must exist on the source type with an accessor method (record-style {@code
   * fieldName()} or JavaBean-style {@code getFieldName()}) or as a public field.
   *
   * <p>If the field is declared as a recognised container interface (List, Set, Collection,
   * Optional, Map) or an array, the traversal is auto-detected. Otherwise, the {@link #traversal()}
   * parameter must be specified explicitly.
   *
   * @return the field name
   */
  String field();

  /**
   * The traversal to use for the container.
   *
   * <p><strong>Optional:</strong> If omitted, the processor automatically detects the appropriate
   * traversal based on the field's container type. Specify this parameter explicitly for custom
   * container types that are not auto-detected.
   *
   * <p>Examples of explicit traversal references:
   *
   * <ul>
   *   <li>{@code "org.higherkindedj.optics.util.Traversals.forList()"}
   *   <li>{@code "org.higherkindedj.optics.util.Traversals.forSet()"}
   *   <li>{@code "org.higherkindedj.optics.util.Traversals.forOptional()"}
   *   <li>{@code "org.higherkindedj.optics.util.Traversals.forArray()"}
   *   <li>{@code "org.higherkindedj.optics.util.Traversals.forMapValues()"}
   *   <li>{@code "com.mycompany.TreeTraverse.INSTANCE"} (custom traversal)
   * </ul>
   *
   * @return the fully qualified traversal reference, or empty for auto-detection
   */
  String traversal() default "";
}

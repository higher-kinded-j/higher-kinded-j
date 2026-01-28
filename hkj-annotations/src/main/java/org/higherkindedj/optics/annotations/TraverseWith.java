// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the traversal instance to use for generating a traversal optic.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a traversal
 * optic using an explicit traversal reference. Use this when you need a custom traversal that
 * cannot be auto-detected from the field type.
 *
 * <p>The processor will generate code that composes a lens to the field with the specified
 * traversal:
 *
 * <pre>{@code
 * // If the method also specifies a field via composition
 * fieldLens().composeTraversal(Traversals.list())
 *
 * // Or directly uses the traversal
 * Traversals.list()
 * }</pre>
 *
 * <p>Example:
 *
 * <pre>{@code
 * @ImportOptics
 * interface TeamOptics extends OpticsSpec<Team> {
 *
 *     @ViaBuilder
 *     Lens<Team, List<Player>> players();
 *
 *     // Compose lens to players with list traversal
 *     @TraverseWith("org.higherkindedj.optics.Traversals.list()")
 *     Traversal<Team, Player> eachPlayer();
 * }
 *
 * // For custom tree traversal
 * @ImportOptics
 * interface TreeOptics<T> extends OpticsSpec<Tree<T>> {
 *
 *     @TraverseWith("com.mycompany.TreeTraverse.preOrder()")
 *     Traversal<Tree<T>, T> allNodes();
 *
 *     @TraverseWith("com.mycompany.TreeTraverse.INSTANCE")
 *     Traversal<Tree<T>, T> allNodesViaField();
 * }
 * }</pre>
 *
 * <p><strong>Note:</strong> References to external traversals cannot be validated at compile time.
 * The processor will emit a warning but generate the code optimistically. If the reference is
 * invalid, compilation of the generated code will fail with a clear error.
 *
 * @see OpticsSpec
 * @see ThroughField
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface TraverseWith {

  /**
   * Fully qualified reference to a Traversal instance or factory method.
   *
   * <p>This can be either:
   *
   * <ul>
   *   <li>A static method call: {@code "org.higherkindedj.optics.Traversals.list()"}
   *   <li>A static field reference: {@code "com.mycompany.TreeTraverse.INSTANCE"}
   * </ul>
   *
   * <p>The reference must resolve to a {@code Traversal<?, ?>} at compile time.
   *
   * @return the fully qualified traversal reference
   */
  String value();
}

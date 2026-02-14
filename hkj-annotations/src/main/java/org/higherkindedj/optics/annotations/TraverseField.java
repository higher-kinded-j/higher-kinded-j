// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures how a {@code Kind<F, A>} field should be processed by the Focus DSL generator.
 *
 * <p>This annotation enables support for custom Kind types that are not part of the Higher-Kinded-J
 * library. When applied to a record component, the annotation processor will use the specified
 * Traverse instance to generate appropriate traversal code.
 *
 * <h2>Usage</h2>
 *
 * <p>Apply this annotation to record components that use custom Kind types:
 *
 * <pre>{@code
 * // Custom Kind type
 * public class TreeKind {
 *     public enum Witness {}
 * }
 *
 * // Custom Traverse instance
 * public enum TreeTraverse implements Traverse<TreeKind.Witness> {
 *     INSTANCE;
 *     // ... implementation
 * }
 *
 * // Use @TraverseField to register the mapping
 * @GenerateFocus
 * public record Forest(
 *     String name,
 *     @TraverseField(
 *         traverse = "com.example.TreeTraverse.INSTANCE",
 *         semantics = KindSemantics.ZERO_OR_MORE
 *     )
 *     Kind<TreeKind.Witness, Tree> trees
 * ) {}
 * }</pre>
 *
 * <p>The generated code will automatically call {@code traverseOver()} with the specified Traverse
 * instance:
 *
 * <pre>{@code
 * public static TraversalPath<Forest, Tree> trees() {
 *     return FocusPath.of(...)
 *         .<TreeKind.Witness, Tree>traverseOver(com.example.TreeTraverse.INSTANCE);
 * }
 * }</pre>
 *
 * <h2>Library Types</h2>
 *
 * <p>For standard Higher-Kinded-J types (ListKind, MaybeKind, etc.), this annotation is not
 * required. The processor automatically recognises these types and generates appropriate code.
 *
 * @see KindSemantics
 * @see GenerateFocus
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.SOURCE)
public @interface TraverseField {

  /**
   * The fully qualified expression for the Traverse instance.
   *
   * <p>This should be a static field or method call that returns a {@code Traverse<F>} instance for
   * the Kind's witness type.
   *
   * <h3>Examples</h3>
   *
   * <ul>
   *   <li>Enum singleton: {@code "com.example.TreeTraverse.INSTANCE"}
   *   <li>Factory method: {@code "com.example.EitherTraverse.instance()"}
   *   <li>Static field: {@code "com.example.MyTraverse.TRAVERSE"}
   * </ul>
   *
   * @return the fully qualified Traverse instance expression
   */
  String traverse();

  /**
   * The semantic classification of this Kind type.
   *
   * <p>This determines which path type is generated:
   *
   * <ul>
   *   <li>{@link KindSemantics#EXACTLY_ONE} - generates {@code AffinePath} (type-safe narrowing)
   *   <li>{@link KindSemantics#ZERO_OR_ONE} - generates {@code AffinePath}
   *   <li>{@link KindSemantics#ZERO_OR_MORE} - generates {@code TraversalPath}
   * </ul>
   *
   * <p>Default is {@code ZERO_OR_MORE}, which is appropriate for most collection-like types.
   *
   * @return the semantic classification
   */
  KindSemantics semantics() default KindSemantics.ZERO_OR_MORE;
}

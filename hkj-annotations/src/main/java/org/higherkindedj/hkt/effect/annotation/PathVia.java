// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method within a {@link GeneratePathBridge} interface for inclusion in the generated Path
 * bridge class.
 *
 * <p>Methods annotated with {@code @PathVia} will have corresponding Path-returning methods
 * generated in the bridge class. The generated method wraps the original method's return type in
 * the appropriate Path type.
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * @GeneratePathBridge
 * public interface OrderService {
 *
 *     @PathVia
 *     Optional<Order> findOrder(String orderId);
 *
 *     @PathVia(name = "fetchOrderPath")
 *     Either<OrderError, Order> fetchOrder(String orderId);
 *
 *     @PathVia(doc = "Validates order details before processing")
 *     Validated<List<String>, Order> validateOrder(Order order);
 *
 *     // Methods without @PathVia are not included in the bridge
 *     void cancelOrder(String orderId);
 * }
 * }</pre>
 *
 * <h2>Return Type Mapping</h2>
 *
 * <p>The processor automatically maps the return type to the appropriate Path type:
 *
 * <table border="1">
 *   <tr><th>Method Return Type</th><th>Generated Path Type</th></tr>
 *   <tr><td>{@code Optional<T>}</td><td>{@code OptionalPath<T>}</td></tr>
 *   <tr><td>{@code Maybe<T>}</td><td>{@code MaybePath<T>}</td></tr>
 *   <tr><td>{@code Either<E, T>}</td><td>{@code EitherPath<E, T>}</td></tr>
 *   <tr><td>{@code Try<T>}</td><td>{@code TryPath<T>}</td></tr>
 *   <tr><td>{@code Validated<E, T>}</td><td>{@code ValidationPath<E, T>}</td></tr>
 *   <tr><td>{@code IO<T>}</td><td>{@code IOPath<T>}</td></tr>
 * </table>
 *
 * <h2>Validated Methods</h2>
 *
 * <p>Methods returning {@code Validated<E, T>} generate bridge methods with an additional {@code
 * Semigroup<E>} parameter, as ValidationPath requires a Semigroup for error accumulation:
 *
 * <pre>{@code
 * // Original method:
 * @PathVia
 * Validated<List<String>, Order> validateOrder(Order order);
 *
 * // Generated bridge method:
 * public ValidationPath<List<String>, Order> validateOrder(
 *     Order order, Semigroup<List<String>> semigroup) {
 *     return Path.validated(delegate.validateOrder(order), semigroup);
 * }
 * }</pre>
 *
 * @see GeneratePathBridge
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface PathVia {

  /**
   * Custom name for the generated method. If empty (the default), the original method name is used.
   *
   * @return the custom method name, or empty to use the original name
   */
  String name() default "";

  /**
   * Documentation to include in the generated method's Javadoc. If provided, this becomes the
   * method description in the generated code.
   *
   * @return the documentation string, or empty for default documentation
   */
  String doc() default "";

  /**
   * Whether this method should be included in composed operation helpers. When {@code true}, the
   * processor may generate additional utility methods for composing this operation with others.
   *
   * <p>This is reserved for future use and currently has no effect.
   *
   * @return {@code true} to include in composition helpers, {@code false} otherwise
   */
  boolean composable() default false;
}

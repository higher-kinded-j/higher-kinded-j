// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that the lens should use an all-args constructor for copying.
 *
 * <p>Apply this to abstract methods in an {@link OpticsSpec} interface to generate a lens that uses
 * a constructor to create modified copies of the source object.
 *
 * <p>The processor will generate code that calls the constructor with all field values,
 * substituting the new value for the target field:
 *
 * <pre>{@code
 * Lens.of(
 *     source -> source.getFieldName(),
 *     (source, newValue) -> new SourceType(
 *         source.getField1(),
 *         newValue,  // substituted for target field
 *         source.getField3()
 *     )
 * )
 * }</pre>
 *
 * <p>This strategy works well for simple immutable classes with a canonical constructor. The
 * processor will attempt to match constructor parameters with getter methods to determine the
 * correct argument order.
 *
 * <p>Where the class declares more than one constructor taking as many arguments, the call binds by
 * the types the getters hand back. A lens focuses a primitive boxed, so the focus is unboxed to its
 * getter's type wherever one of those constructors takes exactly that type; where none does, it is
 * passed as it is, and a getter that hands back a wrapper where the constructor takes the primitive
 * can reach an overload taking a supertype.
 *
 * <p>Example:
 *
 * <pre>{@code
 * // External class with all-args constructor
 * public class Point {
 *     private final int x;
 *     private final int y;
 *
 *     public Point(int x, int y) { this.x = x; this.y = y; }
 *     public int getX() { return x; }
 *     public int getY() { return y; }
 * }
 *
 * @ImportOptics
 * interface PointOptics extends OpticsSpec<Point> {
 *
 *     // The lens method is named after the accessor it reads, since this strategy names no
 *     // getter of its own.
 *     @ViaConstructor
 *     Lens<Point, Integer> getX();
 *
 *     @ViaConstructor
 *     Lens<Point, Integer> getY();
 * }
 * }</pre>
 *
 * @see OpticsSpec
 * @see ViaBuilder
 * @see Wither
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ViaConstructor {

  /**
   * The order of parameters in the constructor.
   *
   * <p>If empty (the default), the processor attempts to auto-detect the parameter order by
   * matching constructor parameters with getter methods based on name and type.
   *
   * <p>Specify this explicitly when auto-detection fails or when the constructor parameter names
   * differ from the getter method names.
   *
   * <p>Example: {@code @ViaConstructor(parameterOrder = {"x", "y", "z"})}
   *
   * @return array of field names in constructor parameter order, or empty for auto-detection
   */
  String[] parameterOrder() default {};
}

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
 * <p>This strategy works well for simple immutable classes with a canonical constructor. The order
 * of the arguments is {@link #parameterOrder}, or, where that is left empty, the order the
 * constructor's parameter names give.
 *
 * <p>Where the class declares more than one constructor taking as many arguments, the call binds by
 * the types the getters hand back. A lens focuses a primitive boxed, so the focus is unboxed to its
 * getter's type wherever one of those constructors takes exactly that type; where none does, it is
 * passed as it is, and a getter that hands back a wrapper where the constructor takes the primitive
 * can reach an overload taking a supertype. A focus wider than the wrapper, {@code Number} over an
 * {@code int} getter, is never unboxed, since it may hold a value of another kind; declare the
 * focus as the wrapper for a constructor that takes the primitive.
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
 *     // getter of its own. Point(int x, int y) names its parameters after getX() and getY(),
 *     // so the order is read from it: new Point(newValue, source.getY()).
 *     @ViaConstructor
 *     Lens<Point, Integer> getX();
 *
 *     // The same order, written out.
 *     @ViaConstructor(parameterOrder = {"getX", "getY"})
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
   * The accessors the constructor's arguments are read through, in the order it takes them, with
   * the lens method's own name where it takes the new value.
   *
   * <p>The generated lens rebuilds through {@code new S(source.x(), newValue, source.z())}, and the
   * order is held to the constructor that call binds: one no constructor takes is refused at the
   * spec method, and so is one that passes an accessor's value where a parameter named after
   * another accessor stands. Those names are known for a constructor compiled from source, and for
   * one read from a class file compiled with {@code -parameters} or {@code -g}; a parameter named
   * after nothing the type reads leaves its place to the order.
   *
   * <p>If empty (the default), the order is read from the parameter names: from the one constructor
   * each of whose parameters is named after an accessor that reads a value it takes, as {@code
   * x()}, {@code getX()} or {@code isX()} for a parameter {@code x}, and one of them the lens
   * method's own. Where no constructor gives such an order, more than one gives a different one, or
   * a longer constructor than the one that gives it might hold more, the order has to be written; a
   * record's canonical constructor holds every value, and is read by its components' names. Where
   * every parameter is named after another accessor, no order can pass the lens's value, and the
   * lens needs another strategy.
   *
   * <p>Example: {@code @ViaConstructor(parameterOrder = {"x", "y", "z"})}
   *
   * @return the accessors in the constructor's parameter order, or empty to read the order from the
   *     parameter names
   */
  String[] parameterOrder() default {};
}

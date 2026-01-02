// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.domain;

import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;

/**
 * A sealed interface representing different shapes.
 *
 * <p>The {@code @GeneratePrisms} annotation generates {@code ShapePrisms} with static prism methods
 * for each variant. Each variant uses {@code @GenerateLenses} to generate lens accessors.
 */
@GeneratePrisms
public sealed interface Shape permits Shape.Circle, Shape.Rectangle, Shape.Triangle {

  /** A circle with a radius. */
  @GenerateLenses
  record Circle(double radius) implements Shape {}

  /** A rectangle with width and height. */
  @GenerateLenses
  record Rectangle(double width, double height) implements Shape {}

  /** A triangle with three sides. */
  @GenerateLenses
  record Triangle(double a, double b, double c) implements Shape {}

  /** Calculate the area of this shape. */
  default double area() {
    return switch (this) {
      case Circle(var r) -> Math.PI * r * r;
      case Rectangle(var w, var h) -> w * h;
      case Triangle(var a, var b, var c) -> {
        // Heron's formula
        double s = (a + b + c) / 2;
        yield Math.sqrt(s * (s - a) * (s - b) * (s - c));
      }
    };
  }
}

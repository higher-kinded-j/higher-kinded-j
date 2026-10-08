// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.composition;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/composition_rules.html">Composition
 * Rules</a> page, in its Prism-then-Lens example. The page {@code {{#include}}}s the anchored
 * regions, and {@code CompositionRulesBookTest} holds the values their comments claim. The page's
 * Lens-then-Prism example is the Affines page's, in {@code AffineBook}.
 */
public final class CompositionRulesBook {

  private CompositionRulesBook() {}

  /** The four values the example computes, in the order the page shows them. */
  static List<Object> prismThenLens() {
    // ANCHOR: prism_lens
    // The Prism may or may not match Circle
    Prism<Shape, Circle> circlePrism =
        Prism.of(shape -> shape instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);

    // The Lens always gets the radius from a Circle
    Lens<Circle, Double> radiusLens = Lens.of(Circle::radius, (c, r) -> new Circle(r, c.colour()));

    // Composition: Prism.andThen(Lens) = Affine
    Affine<Shape, Double> circleRadiusAffine = circlePrism.andThen(radiusLens);

    // Usage
    Shape circle = new Circle(5.0, "red");
    Optional<Double> radius = circleRadiusAffine.getOptional(circle);
    // radius = Optional[5.0]

    Shape rectangle = new Rectangle(10.0, 20.0, "blue");
    Optional<Double> empty = circleRadiusAffine.getOptional(rectangle);
    // empty = Optional.empty, since the prism did not match

    // Modification only affects circles
    Shape modified = circleRadiusAffine.modify(r -> r * 2, circle);
    // modified = Circle[radius=10.0, colour=red]

    Shape unchanged = circleRadiusAffine.modify(r -> r * 2, rectangle);
    // unchanged = Rectangle[width=10.0, height=20.0, colour=blue], as it was
    // ANCHOR_END: prism_lens
    return List.of(radius, empty, modified, unchanged);
  }
}

// ANCHOR: shape_model
// Domain model with a sealed interface
sealed interface Shape permits Circle, Rectangle {}

record Circle(double radius, String colour) implements Shape {}

record Rectangle(double width, double height, String colour) implements Shape {}

// ANCHOR_END: shape_model

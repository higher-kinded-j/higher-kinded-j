// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.article2.domain.CircleLenses;
import org.higherkindedj.article2.domain.Shape;
import org.higherkindedj.article2.domain.Shape.Circle;
import org.higherkindedj.article2.domain.Shape.Rectangle;
import org.higherkindedj.article2.domain.Shape.Triangle;
import org.higherkindedj.article2.domain.ShapePrisms;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Prism;

/**
 * Demonstrates prism operations from Article 2.
 *
 * <p>This demo shows:
 *
 * <ul>
 *   <li>Basic prism operations: getOptional, build, matches, modify
 *   <li>Composing prisms with lenses
 *   <li>Type-safe downcasting pattern
 * </ul>
 *
 * @see <a href="../../docs/article-2-optics-fundamentals.md">Article 2: Optics Fundamentals</a>
 */
public final class PrismDemo {

  public static void main(String[] args) {
    System.out.println("=== Prism Demo (Article 2) ===\n");

    basicPrismOperations();
    prismWithLensComposition();
    typeSafeDowncasting();
  }

  private static void basicPrismOperations() {
    System.out.println("--- Basic Prism Operations ---\n");

    // Generated prism from @GeneratePrisms annotation
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    Shape circle = new Circle(5.0);
    Shape rectangle = new Rectangle(3.0, 4.0);

    // getOptional: extract the variant if it matches
    Optional<Circle> maybeCircle1 = circlePrism.getOptional(circle);
    Optional<Circle> maybeCircle2 = circlePrism.getOptional(rectangle);
    System.out.println("getOptional(circle): " + maybeCircle1);
    System.out.println("getOptional(rectangle): " + maybeCircle2);

    // build: construct the sum type from the variant (always succeeds)
    Shape builtCircle = circlePrism.build(new Circle(10.0));
    System.out.println("build(Circle(10.0)): " + builtCircle);

    // matches: check if the prism matches
    boolean isCircle1 = circlePrism.matches(circle);
    boolean isCircle2 = circlePrism.matches(rectangle);
    System.out.println("matches(circle): " + isCircle1);
    System.out.println("matches(rectangle): " + isCircle2);

    // modify: transform if it matches, leave unchanged otherwise
    Shape doubled1 = circlePrism.modify(c -> new Circle(c.radius() * 2), circle);
    Shape doubled2 = circlePrism.modify(c -> new Circle(c.radius() * 2), rectangle);
    System.out.println("modify(double radius) on circle: " + doubled1);
    System.out.println("modify(double radius) on rectangle: " + doubled2 + " (unchanged)");
    System.out.println();
  }

  private static void prismWithLensComposition() {
    System.out.println("--- Prism + Lens Composition ---\n");

    // Prism: Shape -> Circle, then Lens: Circle -> radius
    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    // Compose into an Affine (0 or 1 focus)
    Affine<Shape, Double> shapeRadius = circlePrism.andThen(CircleLenses.radius());

    Shape circle = new Circle(5.0);
    Shape rectangle = new Rectangle(3.0, 4.0);

    // Get the radius if it's a circle
    Optional<Double> radius1 = shapeRadius.getOptional(circle);
    Optional<Double> radius2 = shapeRadius.getOptional(rectangle);
    System.out.println("getOptional radius of circle: " + radius1);
    System.out.println("getOptional radius of rectangle: " + radius2);

    // Double the radius if it's a circle
    Shape modified1 = shapeRadius.modify(r -> r * 2, circle);
    Shape modified2 = shapeRadius.modify(r -> r * 2, rectangle);
    System.out.println("modify radius on circle: " + modified1);
    System.out.println("modify radius on rectangle: " + modified2 + " (unchanged)");
    System.out.println();
  }

  private static void typeSafeDowncasting() {
    System.out.println("--- Type-Safe Downcasting Pattern ---\n");

    List<Shape> shapes =
        List.of(
            new Circle(1.0),
            new Rectangle(2.0, 3.0),
            new Circle(4.0),
            new Triangle(3.0, 4.0, 5.0),
            new Circle(5.0));

    Prism<Shape, Circle> circlePrism = ShapePrisms.circle();

    // Traditional approach with instanceof
    System.out.println("Traditional instanceof approach:");
    for (Shape shape : shapes) {
      if (shape instanceof Circle c) {
        System.out.println("  Found circle with radius: " + c.radius());
      }
    }

    // Prism approach - more composable
    System.out.println("\nPrism approach (composable):");
    shapes.stream()
        .map(circlePrism::getOptional)
        .flatMap(Optional::stream)
        .forEach(c -> System.out.println("  Found circle with radius: " + c.radius()));

    // Transform all circles (double radius), leave others unchanged
    System.out.println("\nTransform all circles (double radius):");
    List<Shape> transformed =
        shapes.stream().map(s -> circlePrism.modify(c -> new Circle(c.radius() * 2), s)).toList();
    transformed.forEach(s -> System.out.println("  " + s));
    System.out.println();
  }
}

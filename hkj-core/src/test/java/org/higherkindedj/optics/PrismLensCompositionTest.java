// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for composing {@link Prism} with {@link Lens} to produce an {@link Affine}.
 *
 * <p>This composition follows the standard optic composition rule: Prism >>> Lens = Affine.
 */
@DisplayName("Prism.andThen(Lens) Composition Tests")
class PrismLensCompositionTest {

  // Test domain using sealed interfaces
  sealed interface Shape permits Circle, Rectangle {}

  record Circle(double radius, String colour) implements Shape {}

  record Rectangle(double width, double height, String colour) implements Shape {}

  // Optics
  static final Prism<Shape, Circle> circlePrism =
      Prism.of(shape -> shape instanceof Circle c ? Optional.of(c) : Optional.empty(), c -> c);

  static final Prism<Shape, Rectangle> rectanglePrism =
      Prism.of(shape -> shape instanceof Rectangle r ? Optional.of(r) : Optional.empty(), r -> r);

  static final Lens<Circle, Double> radiusLens =
      Lens.of(Circle::radius, (circle, radius) -> new Circle(radius, circle.colour()));

  static final Lens<Circle, String> circleColourLens =
      Lens.of(Circle::colour, (circle, colour) -> new Circle(circle.radius(), colour));

  @Nested
  @DisplayName("Prism.andThen(Lens) produces Affine")
  class PrismAndThenLens {

    @Test
    @DisplayName("should compose to an Affine")
    void composesToAffine() {
      Affine<Shape, Double> affine = circlePrism.andThen(radiusLens);

      assertThat(affine).isNotNull();
    }

    @Test
    @DisplayName("getOptional should return value when prism matches")
    void affine_getOptional_returnsValueWhenPrismMatches() {
      Affine<Shape, Double> radiusAffine = circlePrism.andThen(radiusLens);
      Shape circle = new Circle(5.0, "red");

      Optional<Double> result = radiusAffine.getOptional(circle);

      assertThat(result).contains(5.0);
    }

    @Test
    @DisplayName("getOptional should return empty when prism does not match")
    void affine_getOptional_returnsEmptyWhenPrismDoesNotMatch() {
      Affine<Shape, Double> radiusAffine = circlePrism.andThen(radiusLens);
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      Optional<Double> result = radiusAffine.getOptional(rectangle);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should apply function when prism matches")
    void affine_modify_appliesWhenPrismMatches() {
      Affine<Shape, Double> radiusAffine = circlePrism.andThen(radiusLens);
      Shape circle = new Circle(5.0, "red");

      Shape result = radiusAffine.modify(r -> r * 2, circle);

      assertThat(result).isEqualTo(new Circle(10.0, "red"));
    }

    @Test
    @DisplayName("modify should return unchanged when prism does not match")
    void affine_modify_returnsUnchangedWhenPrismDoesNotMatch() {
      Affine<Shape, Double> radiusAffine = circlePrism.andThen(radiusLens);
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      Shape result = radiusAffine.modify(r -> r * 2, rectangle);

      assertThat(result).isEqualTo(rectangle);
    }

    @Test
    @DisplayName("modify should preserve other fields")
    void affine_modify_preservesOtherFields() {
      Affine<Shape, String> colourAffine = circlePrism.andThen(circleColourLens);
      Shape circle = new Circle(5.0, "red");

      Shape result = colourAffine.modify(String::toUpperCase, circle);

      assertThat(result).isEqualTo(new Circle(5.0, "RED"));
    }
  }

  @Nested
  @DisplayName("Complex composition scenarios")
  class ComplexCompositionTests {

    // More complex domain
    sealed interface ApiResponse permits Success, Failure {}

    record Success(ResponseData data, String timestamp) implements ApiResponse {}

    record Failure(String error, int code) implements ApiResponse {}

    record ResponseData(String message, int count) {}

    static final Prism<ApiResponse, Success> successPrism =
        Prism.of(resp -> resp instanceof Success s ? Optional.of(s) : Optional.empty(), s -> s);

    static final Lens<Success, ResponseData> dataLens =
        Lens.of(Success::data, (success, data) -> new Success(data, success.timestamp()));

    static final Lens<ResponseData, String> messageLens =
        Lens.of(ResponseData::message, (data, msg) -> new ResponseData(msg, data.count()));

    @Test
    @DisplayName("should chain affines from prism-lens composition")
    void chainsAffines() {
      Affine<ApiResponse, ResponseData> dataAffine = successPrism.andThen(dataLens);
      Affine<ApiResponse, String> messageAffine = dataAffine.andThen(messageLens);

      ApiResponse success = new Success(new ResponseData("hello", 1), "2024-01-01");

      Optional<String> message = messageAffine.getOptional(success);
      assertThat(message).contains("hello");

      ApiResponse modified = messageAffine.modify(String::toUpperCase, success);
      assertThat(modified).isEqualTo(new Success(new ResponseData("HELLO", 1), "2024-01-01"));
    }

    @Test
    @DisplayName("should modify nested data correctly")
    void modifiesNestedData() {
      Affine<ApiResponse, ResponseData> dataAffine = successPrism.andThen(dataLens);
      ApiResponse success = new Success(new ResponseData("success", 1), "2024-01-01");

      ApiResponse modified =
          dataAffine.modify(
              data -> new ResponseData(data.message().toUpperCase(), data.count() + 100), success);

      assertThat(modified).isInstanceOf(Success.class);
      Success modifiedSuccess = (Success) modified;
      assertThat(modifiedSuccess.data().message()).isEqualTo("SUCCESS");
      assertThat(modifiedSuccess.data().count()).isEqualTo(101);
      assertThat(modifiedSuccess.timestamp()).isEqualTo("2024-01-01");
    }

    @Test
    @DisplayName("should leave failure response unchanged")
    void leavesFailureUnchanged() {
      Affine<ApiResponse, ResponseData> dataAffine = successPrism.andThen(dataLens);
      ApiResponse failure = new Failure("Not Found", 404);

      ApiResponse result = dataAffine.modify(data -> new ResponseData("Modified", 999), failure);

      assertThat(result).isSameAs(failure);
    }
  }

  @Nested
  @DisplayName("Comparison with asTraversal approach")
  class ComparisonTests {

    @Test
    @DisplayName(
        "andThen(Lens) Affine should behave same as asTraversal().andThen(lens.asTraversal())")
    void behavesLikeAsTraversalComposition() {
      Shape circle = new Circle(5.0, "red");
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      // Direct composition returns Affine
      Affine<Shape, Double> direct = circlePrism.andThen(radiusLens);

      // Manual composition via asTraversal returns Traversal
      Traversal<Shape, Double> manual = circlePrism.asTraversal().andThen(radiusLens.asTraversal());

      // Both should produce same results for getOptional/getAll
      assertThat(direct.getOptional(circle).stream().toList())
          .isEqualTo(Traversals.getAll(manual, circle));
      assertThat(direct.getOptional(rectangle).stream().toList())
          .isEqualTo(Traversals.getAll(manual, rectangle));

      // Both should produce same results for modify
      assertThat(direct.modify(r -> r * 2, circle))
          .isEqualTo(Traversals.modify(manual, r -> r * 2, circle));
      assertThat(direct.modify(r -> r * 2, rectangle))
          .isEqualTo(Traversals.modify(manual, r -> r * 2, rectangle));
    }
  }
}

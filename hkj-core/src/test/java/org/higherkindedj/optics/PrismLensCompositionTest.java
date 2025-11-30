// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for composing {@link Prism} with {@link Lens} to produce a {@link Traversal}.
 *
 * <p>This composition follows the standard optic composition rule: Prism >>> Lens = Traversal.
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
  @DisplayName("Prism.andThen(Lens) produces Traversal")
  class PrismAndThenLens {

    @Test
    @DisplayName("should compose to a Traversal")
    void composesToTraversal() {
      Traversal<Shape, Double> traversal = circlePrism.andThen(radiusLens);

      assertThat(traversal).isNotNull();
    }

    @Test
    @DisplayName("toList should return value when prism matches")
    void traversal_toList_returnsValueWhenPrismMatches() {
      Traversal<Shape, Double> radiusTraversal = circlePrism.andThen(radiusLens);
      Shape circle = new Circle(5.0, "red");

      List<Double> result = Traversals.getAll(radiusTraversal, circle);

      assertThat(result).containsExactly(5.0);
    }

    @Test
    @DisplayName("toList should return empty when prism does not match")
    void traversal_toList_returnsEmptyWhenPrismDoesNotMatch() {
      Traversal<Shape, Double> radiusTraversal = circlePrism.andThen(radiusLens);
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      List<Double> result = Traversals.getAll(radiusTraversal, rectangle);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("modify should apply function when prism matches")
    void traversal_modify_appliesWhenPrismMatches() {
      Traversal<Shape, Double> radiusTraversal = circlePrism.andThen(radiusLens);
      Shape circle = new Circle(5.0, "red");

      Shape result = Traversals.modify(radiusTraversal, r -> r * 2, circle);

      assertThat(result).isEqualTo(new Circle(10.0, "red"));
    }

    @Test
    @DisplayName("modify should return unchanged when prism does not match")
    void traversal_modify_returnsUnchangedWhenPrismDoesNotMatch() {
      Traversal<Shape, Double> radiusTraversal = circlePrism.andThen(radiusLens);
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      Shape result = Traversals.modify(radiusTraversal, r -> r * 2, rectangle);

      assertThat(result).isEqualTo(rectangle);
    }

    @Test
    @DisplayName("modify should preserve other fields")
    void traversal_modify_preservesOtherFields() {
      Traversal<Shape, String> colourTraversal = circlePrism.andThen(circleColourLens);
      Shape circle = new Circle(5.0, "red");

      Shape result = Traversals.modify(colourTraversal, String::toUpperCase, circle);

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
    @DisplayName("should chain traversals from prism-lens composition")
    void chainsTraversals() {
      Traversal<ApiResponse, ResponseData> dataTraversal = successPrism.andThen(dataLens);
      Traversal<ApiResponse, String> messageTraversal =
          dataTraversal.andThen(messageLens.asTraversal());

      ApiResponse success = new Success(new ResponseData("hello", 1), "2024-01-01");

      List<String> messages = Traversals.getAll(messageTraversal, success);
      assertThat(messages).containsExactly("hello");

      ApiResponse modified = Traversals.modify(messageTraversal, String::toUpperCase, success);
      assertThat(modified).isEqualTo(new Success(new ResponseData("HELLO", 1), "2024-01-01"));
    }

    @Test
    @DisplayName("should modify nested data correctly")
    void modifiesNestedData() {
      Traversal<ApiResponse, ResponseData> dataTraversal = successPrism.andThen(dataLens);
      ApiResponse success = new Success(new ResponseData("success", 1), "2024-01-01");

      ApiResponse modified =
          Traversals.modify(
              dataTraversal,
              data -> new ResponseData(data.message().toUpperCase(), data.count() + 100),
              success);

      assertThat(modified).isInstanceOf(Success.class);
      Success modifiedSuccess = (Success) modified;
      assertThat(modifiedSuccess.data().message()).isEqualTo("SUCCESS");
      assertThat(modifiedSuccess.data().count()).isEqualTo(101);
      assertThat(modifiedSuccess.timestamp()).isEqualTo("2024-01-01");
    }

    @Test
    @DisplayName("should leave failure response unchanged")
    void leavesFailureUnchanged() {
      Traversal<ApiResponse, ResponseData> dataTraversal = successPrism.andThen(dataLens);
      ApiResponse failure = new Failure("Not Found", 404);

      ApiResponse result =
          Traversals.modify(dataTraversal, data -> new ResponseData("Modified", 999), failure);

      assertThat(result).isSameAs(failure);
    }
  }

  @Nested
  @DisplayName("Comparison with asTraversal approach")
  class ComparisonTests {

    @Test
    @DisplayName("andThen(Lens) should behave same as asTraversal().andThen(lens.asTraversal())")
    void behavesLikeAsTraversalComposition() {
      Shape circle = new Circle(5.0, "red");
      Shape rectangle = new Rectangle(10.0, 20.0, "blue");

      // Direct composition
      Traversal<Shape, Double> direct = circlePrism.andThen(radiusLens);

      // Manual composition via asTraversal
      Traversal<Shape, Double> manual = circlePrism.asTraversal().andThen(radiusLens.asTraversal());

      // Both should produce same results for getAll
      assertThat(Traversals.getAll(direct, circle)).isEqualTo(Traversals.getAll(manual, circle));
      assertThat(Traversals.getAll(direct, rectangle))
          .isEqualTo(Traversals.getAll(manual, rectangle));

      // Both should produce same results for modify
      assertThat(Traversals.modify(direct, r -> r * 2, circle))
          .isEqualTo(Traversals.modify(manual, r -> r * 2, circle));
      assertThat(Traversals.modify(direct, r -> r * 2, rectangle))
          .isEqualTo(Traversals.modify(manual, r -> r * 2, rectangle));
    }
  }
}

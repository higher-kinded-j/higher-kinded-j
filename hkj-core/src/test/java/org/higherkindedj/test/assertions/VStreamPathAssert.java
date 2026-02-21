// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.test.assertions;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.higherkindedj.hkt.effect.VStreamPath;

/**
 * Custom AssertJ assertions for {@link VStreamPath} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code VStreamPath}
 * instances. Since VStreamPath wraps a lazy pull-based VStream, assertions handle materialisation
 * (collecting to a list via VTask execution) for verification.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.test.assertions.VStreamPathAssert.assertThatVStreamPath;
 *
 * VStreamPath<Integer> path = Path.vstreamOf(1, 2, 3);
 * assertThatVStreamPath(path).producesElements(1, 2, 3);
 *
 * VStreamPath<String> empty = Path.vstreamEmpty();
 * assertThatVStreamPath(empty).isEmpty();
 * }</pre>
 *
 * @param <A> The type of elements produced by the VStreamPath
 */
public class VStreamPathAssert<A> extends AbstractAssert<VStreamPathAssert<A>, VStreamPath<A>> {

  /**
   * Creates a new {@code VStreamPathAssert} instance.
   *
   * @param <A> The type of elements produced by the VStreamPath
   * @param actual The VStreamPath instance to make assertions on
   * @return A new VStreamPathAssert instance
   */
  public static <A> VStreamPathAssert<A> assertThatVStreamPath(VStreamPath<A> actual) {
    return new VStreamPathAssert<>(actual);
  }

  private List<A> materialisedElements;
  private Throwable materialisedException;
  private boolean hasMaterialised = false;

  protected VStreamPathAssert(VStreamPath<A> actual) {
    super(actual, VStreamPathAssert.class);
  }

  private void materialise() {
    if (!hasMaterialised) {
      isNotNull();
      try {
        materialisedElements = actual.toList().unsafeRun();
        materialisedException = null;
      } catch (Throwable t) {
        materialisedElements = null;
        materialisedException = t;
      }
      hasMaterialised = true;
    }
  }

  /**
   * Verifies that the VStreamPath produces the expected elements in order.
   *
   * @param expected The expected elements
   * @return This assertion object for method chaining
   */
  @SafeVarargs
  public final VStreamPathAssert<A> producesElements(A... expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStreamPath to produce elements but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (!Objects.equals(materialisedElements, List.of(expected))) {
      failWithMessage(
          "Expected VStreamPath to produce %s but produced %s",
          List.of(expected), materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStreamPath produces elements matching the expected list.
   *
   * @param expected The expected element list
   * @return This assertion object for method chaining
   */
  public VStreamPathAssert<A> producesElementsInOrder(List<A> expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStreamPath to produce elements but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (!Objects.equals(materialisedElements, expected)) {
      failWithMessage(
          "Expected VStreamPath to produce %s but produced %s", expected, materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStreamPath is empty (produces no elements).
   *
   * @return This assertion object for method chaining
   */
  public VStreamPathAssert<A> isEmpty() {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStreamPath to be empty but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (materialisedElements != null && !materialisedElements.isEmpty()) {
      failWithMessage(
          "Expected VStreamPath to be empty but it produced %d elements: %s",
          materialisedElements.size(), materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStreamPath produces the expected number of elements.
   *
   * @param expected The expected element count
   * @return This assertion object for method chaining
   */
  public VStreamPathAssert<A> hasCount(long expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStreamPath to have %d elements but it threw: %s",
          expected,
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    long actual = materialisedElements != null ? materialisedElements.size() : 0;
    if (actual != expected) {
      failWithMessage("Expected VStreamPath to have %d elements but had %d", expected, actual);
    }
    return this;
  }

  /**
   * Verifies that the VStreamPath produces elements satisfying the given requirements.
   *
   * @param requirements the requirements for the element list
   * @return This assertion object for method chaining
   */
  public VStreamPathAssert<A> satisfies(Consumer<List<A>> requirements) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStreamPath to produce elements but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    requirements.accept(materialisedElements);
    return this;
  }

  /**
   * Verifies that the VStreamPath fails when materialised.
   *
   * @return This assertion object for method chaining
   */
  public VStreamPathAssert<A> failsOnMaterialise() {
    materialise();
    if (materialisedException == null) {
      failWithMessage(
          "Expected VStreamPath to fail but it succeeded with: %s", materialisedElements);
    }
    return this;
  }
}

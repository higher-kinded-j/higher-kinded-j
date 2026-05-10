// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.List;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.VStreamPath;

/**
 * Custom AssertJ assertions for {@link VStreamPath} instances.
 *
 * <p>Materialises the stream (collecting to a list via VTask execution) on first use and caches the
 * result for subsequent assertions.
 *
 * @param <A> The type of elements produced by the VStreamPath
 */
public class VStreamPathAssert<A> extends AbstractAssert<VStreamPathAssert<A>, VStreamPath<A>> {

  /** Entry point. */
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

  private void requireSuccessfulMaterialise(String contextMessage) {
    Assertions.assertThat(materialisedException)
        .withFailMessage(
            "%s but it threw: %s",
            contextMessage,
            materialisedException == null
                ? null
                : materialisedException.getClass().getName()
                    + ": "
                    + materialisedException.getMessage())
        .isNull();
  }

  /** Verifies that the VStreamPath produces the expected elements in order. */
  @SafeVarargs
  public final VStreamPathAssert<A> producesElements(A... expected) {
    materialise();
    requireSuccessfulMaterialise("Expected VStreamPath to produce elements");
    Assertions.assertThat(materialisedElements)
        .withFailMessage(
            "Expected VStreamPath to produce %s but produced %s",
            List.of(expected), materialisedElements)
        .isEqualTo(List.of(expected));
    return this;
  }

  /** Verifies that the VStreamPath produces elements matching the expected list. */
  public VStreamPathAssert<A> producesElementsInOrder(List<A> expected) {
    materialise();
    requireSuccessfulMaterialise("Expected VStreamPath to produce elements");
    Assertions.assertThat(materialisedElements)
        .withFailMessage(
            "Expected VStreamPath to produce %s but produced %s", expected, materialisedElements)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VStreamPath is empty (produces no elements). */
  public VStreamPathAssert<A> isEmpty() {
    materialise();
    requireSuccessfulMaterialise("Expected VStreamPath to be empty");
    Assertions.assertThat(materialisedElements)
        .withFailMessage(
            "Expected VStreamPath to be empty but it produced %d elements: %s",
            materialisedElements.size(), materialisedElements)
        .isEmpty();
    return this;
  }

  /** Verifies that the VStreamPath produces the expected number of elements. */
  public VStreamPathAssert<A> hasCount(long expected) {
    materialise();
    requireSuccessfulMaterialise(
        String.format("Expected VStreamPath to have %d elements", expected));
    long actualCount = materialisedElements.size();
    Assertions.assertThat(actualCount)
        .withFailMessage(
            "Expected VStreamPath to have %d elements but had %d", expected, actualCount)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VStreamPath produces elements satisfying the given requirements. */
  public VStreamPathAssert<A> satisfies(Consumer<List<A>> requirements) {
    materialise();
    requireSuccessfulMaterialise("Expected VStreamPath to produce elements");
    requirements.accept(materialisedElements);
    return this;
  }

  /** Verifies that the VStreamPath fails when materialised. */
  public VStreamPathAssert<A> failsOnMaterialise() {
    materialise();
    Assertions.assertThat(materialisedException)
        .withFailMessage(
            "Expected VStreamPath to fail but it succeeded with: %s", materialisedElements)
        .isNotNull();
    return this;
  }
}

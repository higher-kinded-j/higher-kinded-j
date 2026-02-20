// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.vstream;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractAssert;

/**
 * Custom AssertJ assertions for {@link VStream} instances.
 *
 * <p>Provides fluent assertion methods specifically designed for testing {@code VStream} instances.
 * Since VStream represents lazy pull-based streams, these assertions handle materialisation
 * (collecting to a list) for verification.
 *
 * <h2>Usage Examples:</h2>
 *
 * <pre>{@code
 * import static org.higherkindedj.hkt.vstream.VStreamAssert.assertThatVStream;
 *
 * VStream<Integer> stream = VStream.of(1, 2, 3);
 * assertThatVStream(stream).producesElements(1, 2, 3);
 *
 * VStream<String> empty = VStream.empty();
 * assertThatVStream(empty).isEmpty();
 *
 * VStream<String> failing = VStream.fail(new RuntimeException("Error"));
 * assertThatVStream(failing).failsOnPull();
 * }</pre>
 *
 * @param <A> The type of elements produced by the VStream
 */
public class VStreamAssert<A> extends AbstractAssert<VStreamAssert<A>, VStream<A>> {

  /**
   * Creates a new {@code VStreamAssert} instance.
   *
   * @param <A> The type of elements produced by the VStream
   * @param actual The VStream instance to make assertions on
   * @return A new VStreamAssert instance
   */
  public static <A> VStreamAssert<A> assertThatVStream(VStream<A> actual) {
    return new VStreamAssert<>(actual);
  }

  private List<A> materialisedElements;
  private Throwable materialisedException;
  private boolean hasMaterialised = false;

  protected VStreamAssert(VStream<A> actual) {
    super(actual, VStreamAssert.class);
  }

  private void materialise() {
    if (!hasMaterialised) {
      isNotNull();
      try {
        materialisedElements = actual.toList().run();
        materialisedException = null;
      } catch (Throwable t) {
        materialisedElements = null;
        materialisedException = t;
      }
      hasMaterialised = true;
    }
  }

  /**
   * Verifies that the VStream produces the expected elements in order.
   *
   * @param expected The expected elements
   * @return This assertion object for method chaining
   */
  @SafeVarargs
  public final VStreamAssert<A> producesElements(A... expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStream to produce elements but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (!Objects.equals(materialisedElements, List.of(expected))) {
      failWithMessage(
          "Expected VStream to produce %s but produced %s",
          List.of(expected), materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStream produces elements matching the expected list.
   *
   * @param expected The expected element list
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> producesElementsInOrder(List<A> expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStream to produce elements but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (!Objects.equals(materialisedElements, expected)) {
      failWithMessage(
          "Expected VStream to produce %s but produced %s", expected, materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStream is empty (produces no elements).
   *
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> isEmpty() {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStream to be empty but it threw: %s",
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (materialisedElements != null && !materialisedElements.isEmpty()) {
      failWithMessage(
          "Expected VStream to be empty but it produced %d elements: %s",
          materialisedElements.size(), materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStream produces the expected number of elements.
   *
   * @param expected The expected element count
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> hasCount(long expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStream to have %d elements but it threw: %s",
          expected,
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    long actual = materialisedElements != null ? materialisedElements.size() : 0;
    if (actual != expected) {
      failWithMessage("Expected VStream to have %d elements but had %d", expected, actual);
    }
    return this;
  }

  /**
   * Verifies that the VStream's first element matches the expected value.
   *
   * @param expected The expected first element
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> firstElement(A expected) {
    materialise();
    if (materialisedException != null) {
      failWithMessage(
          "Expected VStream first element to be <%s> but it threw: %s",
          expected,
          materialisedException.getClass().getName() + ": " + materialisedException.getMessage());
    }
    if (materialisedElements == null || materialisedElements.isEmpty()) {
      failWithMessage("Expected VStream first element to be <%s> but stream was empty", expected);
    } else if (!Objects.equals(materialisedElements.getFirst(), expected)) {
      failWithMessage(
          "Expected VStream first element to be <%s> but was <%s>",
          expected, materialisedElements.getFirst());
    }
    return this;
  }

  /**
   * Verifies that the VStream fails when pulled.
   *
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> failsOnPull() {
    materialise();
    if (materialisedException == null) {
      failWithMessage("Expected VStream to fail but it succeeded with: %s", materialisedElements);
    }
    return this;
  }

  /**
   * Verifies that the VStream fails with an exception of the given type.
   *
   * @param type The expected exception type
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> failsWithExceptionType(Class<? extends Throwable> type) {
    failsOnPull();
    if (!type.isInstance(materialisedException)) {
      failWithMessage(
          "Expected VStream to fail with <%s> but threw <%s>",
          type.getName(), materialisedException.getClass().getName());
    }
    return this;
  }

  /**
   * Verifies that the given counter has not been incremented, indicating no execution has occurred.
   *
   * @param counter The counter to check
   * @return This assertion object for method chaining
   */
  public VStreamAssert<A> hasNotExecuted(AtomicInteger counter) {
    if (counter.get() != 0) {
      failWithMessage("Expected no execution but counter was %d (expected 0)", counter.get());
    }
    return this;
  }
}

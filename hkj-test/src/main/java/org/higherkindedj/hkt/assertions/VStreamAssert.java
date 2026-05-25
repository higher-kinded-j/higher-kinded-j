// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vstream.VStreamKind;
import org.higherkindedj.hkt.vstream.VStreamKindHelper;

/**
 * Custom AssertJ assertions for {@link VStream} instances.
 *
 * @param <A> The type of elements produced by the VStream
 */
public class VStreamAssert<A> extends AbstractAssert<VStreamAssert<A>, VStream<A>> {

  /** Entry point accepting a {@code Kind<VStreamKind.Witness, A>}. */
  public static <A> VStreamAssert<A> assertThatVStream(Kind<VStreamKind.Witness, A> actual) {
    return new VStreamAssert<>(VStreamKindHelper.VSTREAM.narrow(actual));
  }

  /** Entry point. */
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

  /** Verifies that the VStream produces the expected elements in order. */
  @SafeVarargs
  public final VStreamAssert<A> producesElements(A... expected) {
    return producesElementsInOrder(List.of(expected));
  }

  /** Verifies that the VStream produces elements matching the expected list. */
  public VStreamAssert<A> producesElementsInOrder(List<A> expected) {
    materialise();
    Assertions.assertThat(materialisedException).as("VStream materialisation exception").isNull();
    Assertions.assertThat(materialisedElements)
        .withFailMessage(
            "Expected VStream to produce %s but produced %s", expected, materialisedElements)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VStream is empty (produces no elements). */
  public VStreamAssert<A> isEmpty() {
    materialise();
    Assertions.assertThat(materialisedException).as("VStream materialisation exception").isNull();
    Assertions.assertThat(materialisedElements).as("VStream materialised elements").isEmpty();
    return this;
  }

  /** Verifies that the VStream produces the expected number of elements. */
  public VStreamAssert<A> hasCount(long expected) {
    materialise();
    Assertions.assertThat(materialisedException).as("VStream materialisation exception").isNull();
    long actual = materialisedElements.size();
    Assertions.assertThat(actual)
        .withFailMessage("Expected VStream to have %d elements but had %d", expected, actual)
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VStream's first element matches the expected value. */
  public VStreamAssert<A> firstElement(A expected) {
    materialise();
    Assertions.assertThat(materialisedException).as("VStream materialisation exception").isNull();
    Assertions.assertThat(materialisedElements).as("VStream materialised elements").isNotEmpty();
    Assertions.assertThat(materialisedElements.getFirst())
        .withFailMessage(
            "Expected VStream first element to be <%s> but was <%s>",
            expected, materialisedElements.getFirst())
        .isEqualTo(expected);
    return this;
  }

  /** Verifies that the VStream fails when pulled. */
  public VStreamAssert<A> failsOnPull() {
    materialise();
    Assertions.assertThat(materialisedException)
        .withFailMessage("Expected VStream to fail but it succeeded with: %s", materialisedElements)
        .isNotNull();
    return this;
  }

  /** Verifies that the VStream fails with an exception of the given type. */
  public VStreamAssert<A> failsWithExceptionType(Class<? extends Throwable> type) {
    failsOnPull();
    Assertions.assertThat(materialisedException)
        .withFailMessage(
            "Expected VStream to fail with <%s> but threw <%s>",
            type.getName(), materialisedException.getClass().getName())
        .isInstanceOf(type);
    return this;
  }

  /** Verifies that the given counter has not been incremented (no execution has occurred). */
  public VStreamAssert<A> hasNotExecuted(AtomicInteger counter) {
    Assertions.assertThat(counter.get())
        .withFailMessage("Expected no execution but counter was %d (expected 0)", counter.get())
        .isZero();
    return this;
  }
}

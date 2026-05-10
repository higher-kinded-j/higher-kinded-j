// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.higherkindedj.hkt.assertions.VStreamAssert.assertThatVStream;

import java.util.List;
import org.higherkindedj.hkt.vstream.VStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link org.higherkindedj.hkt.assertions.VStreamAssert}. */
@DisplayName("VStreamAssert showcase")
class VStreamAssertExample {

  @Test
  @DisplayName("producesElements() asserts the stream emits the expected values")
  void emitsElements() {
    VStream<Integer> stream = VStream.of(1, 2, 3);

    assertThatVStream(stream).producesElements(1, 2, 3);
  }

  @Test
  @DisplayName("producesElementsInOrder() compares against a List of expected values")
  void emitsInOrder() {
    VStream<String> stream = VStream.fromList(List.of("a", "b", "c"));

    assertThatVStream(stream).producesElementsInOrder(List.of("a", "b", "c"));
  }

  @Test
  @DisplayName("isEmpty() asserts the stream produces nothing")
  void emptyStream() {
    VStream<Integer> stream = VStream.empty();

    assertThatVStream(stream).isEmpty();
  }

  @Test
  @DisplayName("failsWithExceptionType() asserts a failing stream propagates the right exception")
  void failingStream() {
    VStream<Integer> stream = VStream.fail(new IllegalStateException("bad"));

    assertThatVStream(stream).failsWithExceptionType(IllegalStateException.class);
  }
}

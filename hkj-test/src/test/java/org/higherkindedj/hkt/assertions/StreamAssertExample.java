// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.hkt.assertions.StreamAssert.assertThatStream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.stream.StreamKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Showcase for {@link StreamAssert}. */
@DisplayName("StreamAssert showcase")
class StreamAssertExample {

  @Test
  @DisplayName("isNotEmpty().hasSize().containsExactly() chains over a Kind<StreamKind.Witness, T>")
  void chainOnStream() {
    Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2, 3));
    assertThatStream(stream).isNotEmpty().hasSize(3).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName("isEmpty() asserts an empty Stream Kind")
  void chainOnEmptyStream() {
    Kind<StreamKind.Witness, String> empty = STREAM.widen(Stream.empty());
    assertThatStream(empty).isEmpty();
  }

  @Test
  @DisplayName("satisfies() exposes the materialised list to a delegated AssertJ block")
  void satisfiesDelegate() {
    Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(2, 4, 6));
    assertThatStream(stream).satisfies(elements -> assertThat(elements).allMatch(n -> n % 2 == 0));
  }
}

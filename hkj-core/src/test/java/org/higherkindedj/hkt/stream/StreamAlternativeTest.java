// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.stream;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Unit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StreamMonad Alternative Operations Test Suite")
class StreamAlternativeTest {

  private Alternative<StreamKind.Witness> alternative;

  @BeforeEach
  void setUpAlternative() {
    alternative = StreamMonad.INSTANCE;
  }

  @Nested
  @DisplayName("empty() Tests")
  class EmptyTests {

    @Test
    @DisplayName("empty() returns empty stream")
    void emptyReturnsEmptyStream() {
      Kind<StreamKind.Witness, Integer> empty = alternative.empty();

      Stream<Integer> stream = STREAM.narrow(empty);
      List<Integer> list = stream.collect(Collectors.toList());
      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("empty() is polymorphic")
    void emptyIsPolymorphic() {
      Kind<StreamKind.Witness, String> emptyString = alternative.empty();
      Kind<StreamKind.Witness, Integer> emptyInt = alternative.empty();

      assertThat(STREAM.narrow(emptyString).collect(Collectors.toList())).isEmpty();
      assertThat(STREAM.narrow(emptyInt).collect(Collectors.toList())).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElse() Tests")
  class OrElseTests {

    @Test
    @DisplayName("orElse() concatenates two non-empty streams")
    void orElseConcatenatesStreams() {
      Kind<StreamKind.Witness, Integer> stream1 = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> stream2 = STREAM.widen(Stream.of(3, 4));

      Kind<StreamKind.Witness, Integer> result = alternative.orElse(stream1, () -> stream2);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).containsExactly(1, 2, 3, 4);
    }

    @Test
    @DisplayName("orElse() with empty first stream")
    void orElseWithEmptyFirst() {
      Kind<StreamKind.Witness, Integer> empty = alternative.empty();
      Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2));

      Kind<StreamKind.Witness, Integer> result = alternative.orElse(empty, () -> stream);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).containsExactly(1, 2);
    }

    @Test
    @DisplayName("orElse() with empty second stream")
    void orElseWithEmptySecond() {
      Kind<StreamKind.Witness, Integer> stream = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> empty = alternative.empty();

      Kind<StreamKind.Witness, Integer> result = alternative.orElse(stream, () -> empty);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).containsExactly(1, 2);
    }

    @Test
    @DisplayName("orElse() with both empty streams")
    void orElseWithBothEmpty() {
      Kind<StreamKind.Witness, Integer> empty1 = alternative.empty();
      Kind<StreamKind.Witness, Integer> empty2 = alternative.empty();

      Kind<StreamKind.Witness, Integer> result = alternative.orElse(empty1, () -> empty2);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).isEmpty();
    }

    @Test
    @DisplayName("orElse() is lazy - doesn't evaluate second until stream is consumed")
    void orElseIsLazy() {
      Kind<StreamKind.Witness, Integer> stream1 = STREAM.widen(Stream.of(1, 2));
      boolean[] evaluated = {false};

      Kind<StreamKind.Witness, Integer> result =
          alternative.orElse(
              stream1,
              () -> {
                evaluated[0] = true;
                return STREAM.widen(Stream.of(3, 4));
              });

      // Supplier not called yet
      assertThat(evaluated[0]).isFalse();

      // Now consume the stream
      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());

      // Now the supplier has been called
      assertThat(evaluated[0]).isTrue();
      assertThat(list).containsExactly(1, 2, 3, 4);
    }
  }

  @Nested
  @DisplayName("guard() Tests")
  class GuardTests {

    @Test
    @DisplayName("guard(true) returns stream with Unit")
    void guardTrueReturnsStreamWithUnit() {
      Kind<StreamKind.Witness, Unit> result = alternative.guard(true);

      Stream<Unit> stream = STREAM.narrow(result);
      List<Unit> list = stream.collect(Collectors.toList());
      assertThat(list).hasSize(1);
      assertThat(list.get(0)).isEqualTo(Unit.INSTANCE);
    }

    @Test
    @DisplayName("guard(false) returns empty stream")
    void guardFalseReturnsEmptyStream() {
      Kind<StreamKind.Witness, Unit> result = alternative.guard(false);

      Stream<Unit> stream = STREAM.narrow(result);
      List<Unit> list = stream.collect(Collectors.toList());
      assertThat(list).isEmpty();
    }
  }

  @Nested
  @DisplayName("orElseAll() Tests")
  class OrElseAllTests {

    @Test
    @DisplayName("orElseAll() concatenates all streams")
    void orElseAllConcatenatesAll() {
      Kind<StreamKind.Witness, Integer> stream1 = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> stream2 = STREAM.widen(Stream.of(3, 4));
      Kind<StreamKind.Witness, Integer> stream3 = STREAM.widen(Stream.of(5, 6));

      Kind<StreamKind.Witness, Integer> result =
          alternative.orElseAll(stream1, () -> stream2, () -> stream3);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    @DisplayName("orElseAll() with some empty streams")
    void orElseAllWithSomeEmpty() {
      Kind<StreamKind.Witness, Integer> stream1 = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> empty = alternative.empty();
      Kind<StreamKind.Witness, Integer> stream2 = STREAM.widen(Stream.of(3, 4));

      Kind<StreamKind.Witness, Integer> result =
          alternative.orElseAll(stream1, () -> empty, () -> stream2);

      Stream<Integer> resultStream = STREAM.narrow(result);
      List<Integer> list = resultStream.collect(Collectors.toList());
      assertThat(list).containsExactly(1, 2, 3, 4);
    }
  }

  @Nested
  @DisplayName("Alternative Laws")
  class AlternativeLaws {

    @Test
    @DisplayName("Left Identity: orElse(empty(), () -> fa) == fa")
    void leftIdentityLaw() {
      Kind<StreamKind.Witness, Integer> fa = STREAM.widen(Stream.of(1, 2, 3));
      Kind<StreamKind.Witness, Integer> result = alternative.orElse(alternative.empty(), () -> fa);

      List<Integer> resultList = STREAM.narrow(result).collect(Collectors.toList());
      // Create a fresh stream for comparison since streams can only be consumed once
      List<Integer> expected = java.util.Arrays.asList(1, 2, 3);
      assertThat(resultList).isEqualTo(expected);
    }

    @Test
    @DisplayName("Right Identity: orElse(fa, () -> empty()) == fa")
    void rightIdentityLaw() {
      Kind<StreamKind.Witness, Integer> fa = STREAM.widen(Stream.of(1, 2, 3));
      Kind<StreamKind.Witness, Integer> result = alternative.orElse(fa, alternative::empty);

      List<Integer> resultList = STREAM.narrow(result).collect(Collectors.toList());
      // Create a fresh stream for comparison since streams can only be consumed once
      List<Integer> expected = java.util.Arrays.asList(1, 2, 3);
      assertThat(resultList).isEqualTo(expected);
    }

    @Test
    @DisplayName(
        "Associativity: orElse(fa, () -> orElse(fb, () -> fc)) == orElse(orElse(fa, () -> fb), ()"
            + " -> fc)")
    void associativityLaw() {
      Kind<StreamKind.Witness, Integer> fa = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> fb = STREAM.widen(Stream.of(3, 4));
      Kind<StreamKind.Witness, Integer> fc = STREAM.widen(Stream.of(5, 6));

      Kind<StreamKind.Witness, Integer> left =
          alternative.orElse(fa, () -> alternative.orElse(fb, () -> fc));

      // Need fresh streams for right side since streams can only be consumed once
      Kind<StreamKind.Witness, Integer> fa2 = STREAM.widen(Stream.of(1, 2));
      Kind<StreamKind.Witness, Integer> fb2 = STREAM.widen(Stream.of(3, 4));
      Kind<StreamKind.Witness, Integer> fc2 = STREAM.widen(Stream.of(5, 6));

      Kind<StreamKind.Witness, Integer> right =
          alternative.orElse(alternative.orElse(fa2, () -> fb2), () -> fc2);

      List<Integer> leftList = STREAM.narrow(left).collect(Collectors.toList());
      List<Integer> rightList = STREAM.narrow(right).collect(Collectors.toList());
      assertThat(leftList).isEqualTo(rightList);
    }
  }
}

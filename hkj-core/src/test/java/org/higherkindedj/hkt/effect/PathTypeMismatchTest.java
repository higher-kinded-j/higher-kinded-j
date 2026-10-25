// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.capability.Composable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PathTypeMismatch: the message a Path throws when handed a different Path type")
class PathTypeMismatchTest {

  private static final String HINT =
      " Use conversion methods (toEitherPath, toMaybePath, etc.) to change types.";

  @Test
  @DisplayName("Each operation names itself, both types and the conversion hint")
  void wordsEachOperation() {
    assertThat(PathTypeMismatch.VIA.exception(MaybePath.class, Path.right(1)))
        .hasMessage(
            "Type mismatch in via(): expected MaybePath but mapper returned EitherPath."
                + " Each Path type can only chain with the same type."
                + HINT);
    assertThat(PathTypeMismatch.THEN.exception(MaybePath.class, Path.right(1)))
        .hasMessage(
            "Type mismatch in then(): expected MaybePath but supplier returned EitherPath."
                + " Each Path type can only chain with the same type."
                + HINT);
    assertThat(PathTypeMismatch.ZIP_WITH.exception(MaybePath.class, Path.right(1)))
        .hasMessage(
            "Type mismatch in zipWith(): expected MaybePath but other was EitherPath."
                + " Each Path type can only combine with the same type."
                + HINT);
  }

  @Test
  @DisplayName("The expected type names VTaskPath and VStreamPath, not their implementations")
  void expectedTypeNamesTheInterface() {
    assertThat(PathTypeMismatch.VIA.exception(DefaultVTaskPath.class, Path.just(1)))
        .hasMessageStartingWith("Type mismatch in via(): expected VTaskPath but mapper returned");
    assertThat(PathTypeMismatch.ZIP_WITH.exception(DefaultVStreamPath.class, Path.just(1)))
        .hasMessageStartingWith("Type mismatch in zipWith(): expected VStreamPath but other was");
  }

  @Test
  @DisplayName("A class with no simple name is named by its binary name")
  void anonymousClassNamedByBinaryName() {
    Composable<Integer> anonymous = new Elsewhere.MaybePath() {};

    assertThat(PathTypeMismatch.ZIP_WITH.exception(MaybePath.class, anonymous))
        .hasMessageContaining(
            "expected MaybePath but other was " + anonymous.getClass().getName() + ".");
  }

  @Test
  @DisplayName("Two types that share a simple name are both named by their binary names")
  void sharedSimpleNameNamedByBinaryNames() {
    assertThat(PathTypeMismatch.ZIP_WITH.exception(MaybePath.class, new Elsewhere.MaybePath()))
        .hasMessageContaining(
            "expected "
                + MaybePath.class.getName()
                + " but other was "
                + Elsewhere.MaybePath.class.getName()
                + ".");
  }

  /** Holds a caller's own Path type that shares a simple name with a library one. */
  static final class Elsewhere {

    private Elsewhere() {}

    static class MaybePath implements Composable<Integer> {
      @Override
      public <B> Composable<B> map(Function<? super Integer, ? extends B> mapper) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Composable<Integer> peek(Consumer<? super Integer> consumer) {
        throw new UnsupportedOperationException();
      }
    }
  }
}

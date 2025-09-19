// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.util.ErrorHandling.*;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Kind Narrowing Tests")
class NarrowKindTest {

  // Test Kind implementations
  private interface MyKindWitness {}

  private interface AnotherWitness {}

  private record MyKind<A>(A value) implements Kind<MyKindWitness, A> {}

  private record AnotherKind<A>(A value) implements Kind<MyKindWitness, A> {}

  private record DifferentWitnessKind<A>(A value) implements Kind<AnotherWitness, A> {}

  @Nested
  @DisplayName("narrowKind() Tests")
  class NarrowKindTests {

    private final Kind<MyKindWitness, String> myKind = new MyKind<>("test");
    private final String targetTypeName = "MyKind";
    private final Function<Kind<MyKindWitness, String>, MyKind<String>> narrower =
        k -> (MyKind<String>) k;

    @Test
    void shouldReturnNarrowedInstanceOnSuccess() {
      MyKind<String> result = narrowKind(myKind, targetTypeName, narrower);
      assertThat(result).isSameAs(myKind);
    }

    @Test
    void shouldThrowKindUnwrapExceptionForNullKind() {
      assertThatThrownBy(() -> narrowKind(null, targetTypeName, narrower))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for MyKind");
    }

    @Test
    void shouldWrapClassCastExceptionInKindUnwrapException() {
      Kind<MyKindWitness, String> wrongKind = new AnotherKind<>("wrong");
      assertThatThrownBy(
              () ->
                  narrowKind(
                      wrongKind,
                      targetTypeName,
                      k -> {
                        throw new ClassCastException("Test exception");
                      }))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance is not a MyKind: " + wrongKind.getClass().getName())
          .hasCauseInstanceOf(ClassCastException.class);
    }

    @Test
    void shouldPassThroughRuntimeException() {
      Kind<MyKindWitness, String> kind = new MyKind<>("test");

      // RuntimeException should pass through unwrapped
      assertThatThrownBy(
              () ->
                  narrowKind(
                      kind,
                      "MyKind",
                      k -> {
                        throw new RuntimeException("Unexpected error");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Unexpected error");
    }

    @Test
    void shouldHandleExceptionWithNullMessage() {
      Kind<MyKindWitness, String> wrongKind = new AnotherKind<>("wrong");
      assertThatThrownBy(
              () ->
                  narrowKind(
                      wrongKind,
                      targetTypeName,
                      k -> {
                        throw new ClassCastException(); // No message
                      }))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance is not a MyKind: " + wrongKind.getClass().getName())
          .hasCauseInstanceOf(ClassCastException.class);
    }

    @Test
    void shouldHandleNullTargetTypeName() {
      Kind<MyKindWitness, String> kind = new MyKind<>("test");
      Function<Kind<MyKindWitness, String>, String> extractor = k -> "result";

      String result = narrowKind(kind, null, extractor);
      assertThat(result).isEqualTo("result");

      // Test with null kind to verify error message with null typeName
      assertThatThrownBy(() -> narrowKind(null, null, extractor))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for null");
    }

    @Test
    void shouldThrowForNullExtractor() {
      Kind<MyKindWitness, String> kind = new MyKind<>("test");

      assertThatThrownBy(() -> narrowKind(kind, "MyType", null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("narrowKindWithTypeCheck() Tests")
  class NarrowKindWithTypeCheckTests {

    private final MyKind<Integer> myKind = new MyKind<>(123);
    private final String targetTypeName = "MyKind";

    @Test
    void shouldReturnCastedInstanceOnSuccess() {
      MyKind<Integer> result = narrowKindWithTypeCheck(myKind, MyKind.class, targetTypeName);
      assertThat(result).isSameAs(myKind);
    }

    @Test
    void shouldThrowKindUnwrapExceptionForNullKind() {
      assertThatThrownBy(() -> narrowKindWithTypeCheck(null, MyKind.class, targetTypeName))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for MyKind");
    }

    @Test
    void shouldThrowKindUnwrapExceptionForWrongType() {
      Kind<MyKindWitness, Integer> wrongKind = new AnotherKind<>(456);
      assertThatThrownBy(() -> narrowKindWithTypeCheck(wrongKind, MyKind.class, targetTypeName))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance is not a MyKind: " + wrongKind.getClass().getName());
    }

    @Test
    void shouldHandleInterfaceImplementations() {
      Kind<MyKindWitness, String> myKindInstance = new MyKind<>("test");
      Kind<MyKindWitness, String> anotherKindInstance = new AnotherKind<>("test");

      // MyKind should match MyKind.class
      MyKind<String> result1 = narrowKindWithTypeCheck(myKindInstance, MyKind.class, "MyKind");
      assertThat(result1).isSameAs(myKindInstance);

      // AnotherKind should match AnotherKind.class
      AnotherKind<String> result2 =
          narrowKindWithTypeCheck(anotherKindInstance, AnotherKind.class, "AnotherKind");
      assertThat(result2).isSameAs(anotherKindInstance);

      // AnotherKind should NOT match MyKind.class
      assertThatThrownBy(() -> narrowKindWithTypeCheck(anotherKindInstance, MyKind.class, "MyKind"))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a MyKind");
    }

    @Test
    void shouldThrowForNullTargetClass() {
      Kind<MyKindWitness, String> kind = new MyKind<>("test");

      assertThatThrownBy(() -> narrowKindWithTypeCheck(kind, null, "MyType"))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldHandleNullTargetTypeNameGracefully() {
      Kind<MyKindWitness, String> kind = new MyKind<>("test");

      // With null typeName but valid class, it should work
      MyKind<String> result = narrowKindWithTypeCheck(kind, MyKind.class, null);
      assertThat(result).isSameAs(kind);

      // Test error message with null typeName
      Kind<MyKindWitness, String> wrongKind = new AnotherKind<>("wrong");
      assertThatThrownBy(() -> narrowKindWithTypeCheck(wrongKind, MyKind.class, null))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Kind instance is not a null: " + wrongKind.getClass().getName());
    }
  }

  @Nested
  @DisplayName("narrowKindWithMatchers() Tests")
  class NarrowKindWithMatchersTests {

    private final MyKind<String> myKind = new MyKind<>("my");
    private final AnotherKind<String> anotherKind = new AnotherKind<>("another");

    @Test
    void shouldSucceedWithFirstMatcher() {
      String result =
          ErrorHandling.narrowKindWithMatchers(
              myKind,
              "Target",
              createMatcher(MyKind.class, "Matched MyKind"),
              createMatcher(AnotherKind.class, "Matched AnotherKind"));

      assertThat(result).isEqualTo("Matched MyKind");
    }

    @Test
    void shouldSucceedWithSubsequentMatcher() {
      String result =
          ErrorHandling.narrowKindWithMatchers(
              anotherKind,
              "Target",
              createMatcher(MyKind.class, "Matched MyKind"),
              createMatcher(AnotherKind.class, "Matched AnotherKind"));
      assertThat(result).isEqualTo("Matched AnotherKind");
    }

    @Test
    void shouldThrowWhenNoMatcherSucceeds() {
      Kind<MyKindWitness, String> unknownKind = new Kind<MyKindWitness, String>() {};
      assertThatThrownBy(
              () ->
                  ErrorHandling.narrowKindWithMatchers(
                      unknownKind,
                      "Target",
                      createMatcher(MyKind.class, "Matched MyKind"),
                      createMatcher(AnotherKind.class, "Matched AnotherKind")))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a Target");
    }

    @Test
    void shouldThrowForNullKind() {
      assertThatThrownBy(
              () ->
                  ErrorHandling.narrowKindWithMatchers(
                      null, "Target", createMatcher(MyKind.class, "Matched MyKind")))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessage("Cannot narrow null Kind for Target");
    }

    @Test
    void shouldHandleEmptyMatchersArray() {
      assertThatThrownBy(
              () ->
                  ErrorHandling.<MyKindWitness, String, String>narrowKindWithMatchers(
                      myKind, "Target"))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining("Kind instance is not a Target");
    }

    @Test
    void shouldWorkWithSingleMatcher() {
      String result =
          ErrorHandling.narrowKindWithMatchers(
              myKind, "Target", createMatcher(MyKind.class, "Single Match"));
      assertThat(result).isEqualTo("Single Match");
    }

    @Test
    void shouldHandleMatcherThatThrowsExceptionInExtract() {
      TypeMatcher<Kind<MyKindWitness, String>, String> throwingMatcher =
          new TypeMatcher<Kind<MyKindWitness, String>, String>() {
            @Override
            public boolean matches(Kind<MyKindWitness, String> source) {
              return true;
            }

            @Override
            public String extract(Kind<MyKindWitness, String> source) {
              throw new RuntimeException("Extract failed");
            }
          };

      assertThatThrownBy(
              () -> ErrorHandling.narrowKindWithMatchers(myKind, "Target", throwingMatcher))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Extract failed");
    }

    @Test
    void shouldHandleMatcherThatThrowsExceptionInMatches() {
      TypeMatcher<Kind<MyKindWitness, String>, String> throwingMatcher =
          new TypeMatcher<Kind<MyKindWitness, String>, String>() {
            @Override
            public boolean matches(Kind<MyKindWitness, String> source) {
              throw new IllegalStateException("Matches failed");
            }

            @Override
            public String extract(Kind<MyKindWitness, String> source) {
              return "should not reach";
            }
          };

      assertThatThrownBy(
              () -> ErrorHandling.narrowKindWithMatchers(myKind, "Target", throwingMatcher))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Matches failed");
    }

    @Test
    void shouldHandleNullTargetTypeName() {
      String result =
          ErrorHandling.narrowKindWithMatchers(
              myKind, null, createMatcher(MyKind.class, "Matched"));
      assertThat(result).isEqualTo("Matched");

      // Test error case with null target type name
      assertThatThrownBy(
              () ->
                  ErrorHandling.narrowKindWithMatchers(
                      anotherKind, null, createMatcher(MyKind.class, "Matched")))
          .hasMessage("Kind instance is not a null: " + anotherKind.getClass().getName());
    }

    private TypeMatcher<Kind<MyKindWitness, String>, String> createMatcher(
        Class<?> targetClass, String result) {
      return new TypeMatcher<Kind<MyKindWitness, String>, String>() {
        @Override
        public boolean matches(Kind<MyKindWitness, String> source) {
          return targetClass.isInstance(source);
        }

        @Override
        public String extract(Kind<MyKindWitness, String> source) {
          return result;
        }
      };
    }
  }
}

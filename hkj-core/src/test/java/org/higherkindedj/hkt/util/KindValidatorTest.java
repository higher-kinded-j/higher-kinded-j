// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.util.validation.KindValidator.KIND_VALIDATOR;
import static org.higherkindedj.hkt.util.validation.Operation.*;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("KindValidator")
class KindValidatorTest {

  private static final class TestWitness {}

  private static final class TestType {}

  private record TestKind<A>(A value) implements Kind<TestWitness, A> {}

  private record DifferentKind<A>(A value) implements Kind<TestWitness, A> {}

  @Nested
  @DisplayName("narrow")
  class Narrow {

    @Test
    @DisplayName("should narrow Kind to target type successfully")
    void shouldNarrowKindSuccessfully() {
      var kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.narrow(kind, TestKind.class, k -> (TestKind<String>) k);

      assertThat(result).isEqualTo(kind);
      assertThat(result.value()).isEqualTo("test");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is null")
    void shouldThrowWhenKindIsNull() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(() -> KIND_VALIDATOR.narrow(null, TestKind.class, k -> (TestKind<?>) k))
          .withMessage("Cannot narrow null Kind for TestKind");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when narrowing fails")
    void shouldThrowWhenNarrowingFails() {
      var kind = new DifferentKind<>("test");

      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(() -> KIND_VALIDATOR.narrow(kind, TestKind.class, k -> (TestKind<?>) k))
          .withMessageContaining("Kind instance is not a TestKind")
          .withMessageContaining("DifferentKind")
          .withCauseInstanceOf(ClassCastException.class);
    }

    @Test
    @DisplayName("should throw KindUnwrapException when narrower function throws exception")
    void shouldThrowWhenNarrowerThrowsException() {
      var kind = new TestKind<>("test");

      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  KIND_VALIDATOR.narrow(
                      kind,
                      TestKind.class,
                      k -> {
                        throw new RuntimeException("Narrower failed");
                      }))
          .withMessageContaining("Failed to narrow Kind to TestKind")
          .withMessageContaining("Narrower failed")
          .withCauseInstanceOf(RuntimeException.class);
    }
  }

  @Nested
  @DisplayName("narrowWithTypeCheck")
  class NarrowWithTypeCheck {

    @Test
    @DisplayName("should narrow Kind using instanceof check successfully")
    void shouldNarrowWithTypeCheckSuccessfully() {
      var kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.narrowWithTypeCheck(kind, TestKind.class);

      assertThat(result).isEqualTo(kind);
      assertThat(result.value()).isEqualTo("test");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is null")
    void shouldThrowWhenKindIsNull() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(() -> KIND_VALIDATOR.narrowWithTypeCheck(null, TestKind.class))
          .withMessage("Cannot narrow null Kind for TestKind");
    }

    @Test
    @DisplayName("should throw KindUnwrapException when kind is not instance of target type")
    void shouldThrowWhenKindIsNotInstanceOfTargetType() {
      var kind = new DifferentKind<>("test");

      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(() -> KIND_VALIDATOR.narrowWithTypeCheck(kind, TestKind.class))
          .withMessageContaining("Kind instance is not a TestKind")
          .withMessageContaining("DifferentKind");
    }
  }

  @Nested
  @DisplayName("requireForWiden")
  class RequireForWiden {

    @Test
    @DisplayName("should return non-null input for widening")
    void shouldReturnNonNullInput() {
      var input = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireForWiden(input, TestKind.class);

      assertThat(result).isEqualTo(input);
    }

    @Test
    @DisplayName("should throw NullPointerException when input is null")
    void shouldThrowWhenInputIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireForWiden(null, TestKind.class))
          .withMessage("Input TestKind cannot be null for widen");
    }
  }

  @Nested
  @DisplayName("requireNonNull with class-based context")
  class RequireNonNullWithClass {

    @Test
    @DisplayName("should return non-null Kind")
    void shouldReturnNonNullKind() {
      Kind<TestWitness, String> kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireNonNull(kind, TestType.class, MAP);

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should throw NullPointerException when Kind is null")
    void shouldThrowWhenKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, TestType.class, MAP))
          .withMessage("Kind for TestType.map cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when context class is null")
    void shouldThrowWhenContextClassIsNull() {
      Kind<TestWitness, String> kind = new TestKind<>("test");

      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(kind, null, MAP))
          .withMessage("contextClass cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when operation is null")
    void shouldThrowWhenOperationIsNull() {
      Kind<TestWitness, String> kind = new TestKind<>("test");

      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(kind, TestType.class, null))
          .withMessage("operation cannot be null");
    }
  }

  @Nested
  @DisplayName("requireNonNull with operation context")
  class RequireNonNullWithOperation {

    @Test
    @DisplayName("should return non-null Kind")
    void shouldReturnNonNullKind() {
      Kind<TestWitness, String> kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireNonNull(kind, MAP);

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should throw NullPointerException when Kind is null")
    void shouldThrowWhenKindIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, MAP))
          .withMessage("Kind for map cannot be null");
    }
  }

  @Nested
  @DisplayName("requireNonNull with descriptor")
  class RequireNonNullWithDescriptor {

    @Test
    @DisplayName("should return non-null Kind with descriptor")
    void shouldReturnNonNullKindWithDescriptor() {
      Kind<TestWitness, String> kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireNonNull(kind, TestType.class, AP, "function");

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should include descriptor in error message")
    void shouldIncludeDescriptorInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, TestType.class, AP, "function"))
          .withMessage("Kind for TestType.ap (function) cannot be null");
    }

    @Test
    @DisplayName("should work without descriptor")
    void shouldWorkWithoutDescriptor() {
      Kind<TestWitness, String> kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireNonNull(kind, TestType.class, AP, null);

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should not include descriptor in error message when null")
    void shouldNotIncludeDescriptorWhenNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, TestType.class, AP, null))
          .withMessage("Kind for TestType.ap cannot be null");
    }

    @Test
    @DisplayName("should distinguish between multiple parameters using descriptors")
    void shouldDistinguishBetweenParametersUsingDescriptors() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, TestType.class, AP, "argument"))
          .withMessage("Kind for TestType.ap (argument) cannot be null");

      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, TestType.class, AP, "function"))
          .withMessage("Kind for TestType.ap (function) cannot be null");
    }
  }

  @Nested
  @DisplayName("requireNonNull with operation and descriptor")
  class RequireNonNullWithOperationAndDescriptor {

    @Test
    @DisplayName("should return non-null Kind with descriptor")
    void shouldReturnNonNullKindWithDescriptor() {
      Kind<TestWitness, String> kind = new TestKind<>("test");
      var result = KIND_VALIDATOR.requireNonNull(kind, FLAT_MAP, "source");

      assertThat(result).isEqualTo(kind);
    }

    @Test
    @DisplayName("should include descriptor in error message")
    void shouldIncludeDescriptorInErrorMessage() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, FLAT_MAP, "source"))
          .withMessage("Kind for flatMap (source) cannot be null");
    }

    @Test
    @DisplayName("should work without descriptor")
    void shouldWorkWithoutDescriptor() {
      assertThatNullPointerException()
          .isThrownBy(() -> KIND_VALIDATOR.requireNonNull(null, FLAT_MAP, null))
          .withMessage("Kind for flatMap cannot be null");
    }
  }

  @Nested
  @DisplayName("KindContext")
  class KindContextTest {

    @Test
    @DisplayName("should create narrow context")
    void shouldCreateNarrowContext() {
      var context = KindValidator.KindContext.narrow(TestKind.class);

      assertThat(context.targetType()).isEqualTo(TestKind.class);
      assertThat(context.operation()).isEqualTo("narrow");
    }

    @Test
    @DisplayName("should create widen context")
    void shouldCreateWidenContext() {
      var context = KindValidator.KindContext.widen(TestKind.class);

      assertThat(context.targetType()).isEqualTo(TestKind.class);
      assertThat(context.operation()).isEqualTo("widen");
    }

    @Test
    @DisplayName("should generate correct null parameter message")
    void shouldGenerateCorrectNullParameterMessage() {
      var context = new KindValidator.KindContext(TestKind.class, "narrow");

      assertThat(context.nullParameterMessage()).isEqualTo("Cannot narrow null Kind for TestKind");
    }

    @Test
    @DisplayName("should generate correct null input message")
    void shouldGenerateCorrectNullInputMessage() {
      var context = new KindValidator.KindContext(TestKind.class, "widen");

      assertThat(context.nullInputMessage()).isEqualTo("Input TestKind cannot be null for widen");
    }

    @Test
    @DisplayName("should generate correct invalid type message")
    void shouldGenerateCorrectInvalidTypeMessage() {
      var context = new KindValidator.KindContext(TestKind.class, "narrow");

      assertThat(context.invalidTypeMessage("DifferentKind"))
          .isEqualTo("Kind instance is not a TestKind: DifferentKind");
    }

    @Test
    @DisplayName("should throw NullPointerException when target type is null")
    void shouldThrowWhenTargetTypeIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new KindValidator.KindContext(null, "narrow"))
          .withMessage("targetType cannot be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when operation is null")
    void shouldThrowWhenOperationIsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> new KindValidator.KindContext(TestKind.class, null))
          .withMessage("operation cannot be null");
    }
  }
}

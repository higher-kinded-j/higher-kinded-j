// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.util.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import static org.higherkindedj.hkt.instances.Witnesses.*;
import static org.higherkindedj.hkt.util.validation.Operation.CONSTRUCTION;
import static org.higherkindedj.hkt.util.validation.Operation.MAP;

import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.instances.Instances;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Bulk validators and recent additions")
class BulkValidatorTest {

  private static final Function<String, Integer> F = String::length;
  private static final Function<String, Kind<EitherKind.Witness<String>, Integer>> KF =
      s -> EITHER.widen(Either.<String, Integer>right(s.length()));
  private static final Kind<EitherKind.Witness<String>, String> KA =
      EITHER.widen(Either.<String, String>right("hi"));
  private static final Kind<EitherKind.Witness<String>, Function<String, Integer>> KFF =
      EITHER.widen(Either.<String, Function<String, Integer>>right(F));

  // ==================== FunctionValidator bulk helpers ====================

  @Nested
  @DisplayName("FunctionValidator.validateMap")
  class ValidateMap {
    @Test
    void happyPath() {
      Validation.function().validateMap(F, KA);
    }

    @Test
    void rejectsNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateMap(null, KA))
          .withMessage("f for map cannot be null");
    }

    @Test
    void rejectsNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateMap(F, null))
          .withMessage("Kind for map cannot be null");
    }
  }

  @Nested
  @DisplayName("FunctionValidator.validateFlatMap")
  class ValidateFlatMap {
    @Test
    void happyPath() {
      Validation.function().validateFlatMap(KF, KA);
    }

    @Test
    void rejectsNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateFlatMap(null, KA))
          .withMessage("f for flatMap cannot be null");
    }

    @Test
    void rejectsNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateFlatMap(KF, null))
          .withMessage("Kind for flatMap cannot be null");
    }
  }

  @Nested
  @DisplayName("FunctionValidator.validateTraverse")
  class ValidateTraverse {
    private final Applicative<EitherKind.Witness<String>> applicative =
        Instances.monadError(either());

    @Test
    void happyPath() {
      Validation.function().validateTraverse(applicative, KF, KA);
    }

    @Test
    void rejectsNullApplicative() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateTraverse(null, KF, KA))
          .withMessage("applicative for traverse cannot be null");
    }

    @Test
    void rejectsNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateTraverse(applicative, null, KA))
          .withMessage("f for traverse cannot be null");
    }

    @Test
    void rejectsNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateTraverse(applicative, KF, null))
          .withMessage("Kind for traverse cannot be null");
    }
  }

  @Nested
  @DisplayName("FunctionValidator.validateFoldMap")
  class ValidateFoldMap {
    private final Monoid<Integer> monoid =
        new Monoid<>() {
          @Override
          public Integer empty() {
            return 0;
          }

          @Override
          public Integer combine(Integer a, Integer b) {
            return a + b;
          }
        };

    @Test
    void happyPath() {
      Validation.function().validateFoldMap(monoid, F, KA);
    }

    @Test
    void rejectsNullMonoid() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateFoldMap(null, F, KA))
          .withMessage("monoid for foldMap cannot be null");
    }

    @Test
    void rejectsNullFunction() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateFoldMap(monoid, null, KA))
          .withMessage("f for foldMap cannot be null");
    }

    @Test
    void rejectsNullKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateFoldMap(monoid, F, null))
          .withMessage("Kind for foldMap cannot be null");
    }
  }

  @Nested
  @DisplayName("FunctionValidator.validateHandleErrorWith")
  class ValidateHandleErrorWith {
    private final Function<String, Kind<EitherKind.Witness<String>, String>> handler =
        e -> EITHER.widen(Either.right("recovered"));

    @Test
    void happyPath() {
      Validation.function().validateHandleErrorWith(KA, handler);
    }

    @Test
    void rejectsNullSource() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateHandleErrorWith(null, handler))
          .withMessage("Kind for handleErrorWith (source) cannot be null");
    }

    @Test
    void rejectsNullHandler() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.function().validateHandleErrorWith(KA, null))
          .withMessage("handler for handleErrorWith cannot be null");
    }
  }

  // ==================== KindValidator additions ====================

  @Nested
  @DisplayName("KindValidator.validateAp")
  class ValidateAp {
    @Test
    void happyPath() {
      Validation.kind().validateAp(KFF, KA);
    }

    @Test
    void rejectsNullFunctionKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.kind().validateAp(null, KA))
          .withMessage("Kind for ap (function) cannot be null");
    }

    @Test
    void rejectsNullArgumentKind() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.kind().validateAp(KFF, null))
          .withMessage("Kind for ap (argument) cannot be null");
    }
  }

  @Nested
  @DisplayName("KindValidator.narrowHolder")
  class NarrowHolder {
    @Test
    void unwrapsViaAccessor() {
      var either = Either.<String, Integer>right(42);
      var kind = EITHER.widen(either);

      // Use real EITHER.narrow exercising narrowHolder under the hood
      Either<String, Integer> result = EITHER.narrow(kind);

      assertThat(result).isEqualTo(either);
    }

    @Test
    void throwsForNullKind() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(() -> EITHER.narrow(null))
          .withMessageContaining("narrow")
          .withMessageContaining("null");
    }
  }

  // Fixture types for exercising the deprecated narrowWithPattern method directly,
  // since the 12 KindHelpers have all migrated to narrowHolder.
  interface FixtureWitness extends WitnessArity<TypeArity.Unary> {}

  record FixtureHolder<A>(A value) implements Kind<FixtureWitness, A> {}

  record OtherHolder<A>(A value) implements Kind<FixtureWitness, A> {}

  @Nested
  @DisplayName("KindValidator.narrowWithPattern (deprecated)")
  @SuppressWarnings({"removal", "unchecked", "rawtypes"})
  class NarrowWithPattern {

    private final Class<FixtureHolder> holderClass = FixtureHolder.class;

    @Test
    void unwrapsViaExtractor() {
      Kind<FixtureWitness, String> kind = new FixtureHolder<>("hello");

      Object result =
          Validation.KIND.narrowWithPattern(kind, Object.class, holderClass, FixtureHolder::value);

      assertThat(result).isEqualTo("hello");
    }

    @Test
    void throwsForNullKind() {
      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  Validation.KIND.narrowWithPattern(
                      null, Object.class, holderClass, FixtureHolder::value))
          .withMessageContaining("narrow")
          .withMessageContaining("null");
    }

    @Test
    void throwsForWrongHolderType() {
      Kind<FixtureWitness, String> wrongKind = new OtherHolder<>("hello");

      assertThatExceptionOfType(KindUnwrapException.class)
          .isThrownBy(
              () ->
                  Validation.KIND.narrowWithPattern(
                      wrongKind, Object.class, holderClass, FixtureHolder::value))
          .withMessageContaining("OtherHolder");
    }
  }

  // ==================== TransformerValidator additions ====================

  private static final class FakeT {}

  @Nested
  @DisplayName("TransformerValidator.requireOuterApplicative")
  class RequireOuterApplicative {
    private final Applicative<EitherKind.Witness<String>> applicative =
        Instances.monadError(either());

    @Test
    void returnsApplicative() {
      var result =
          Validation.transformer().requireOuterApplicative(applicative, FakeT.class, CONSTRUCTION);
      assertThat(result).isSameAs(applicative);
    }

    @Test
    void rejectsNull() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  Validation.transformer().requireOuterApplicative(null, FakeT.class, CONSTRUCTION))
          .withMessage("Outer Applicative cannot be null for FakeT construction");
    }
  }

  @Nested
  @DisplayName("TransformerValidator.requireOuterFunctor")
  class RequireOuterFunctor {
    private final Functor<EitherKind.Witness<String>> functor = Instances.monadError(either());

    @Test
    void returnsFunctor() {
      var result = Validation.transformer().requireOuterFunctor(functor, FakeT.class, MAP);
      assertThat(result).isSameAs(functor);
    }

    @Test
    void rejectsNull() {
      assertThatNullPointerException()
          .isThrownBy(() -> Validation.transformer().requireOuterFunctor(null, FakeT.class, MAP))
          .withMessage("Outer Functor cannot be null for FakeT map");
    }
  }

  // ==================== Validation static field aliases ====================

  @Nested
  @DisplayName("Validation static field aliases match accessor methods")
  class StaticFieldAliases {
    @Test
    void kind() {
      assertThat(Validation.KIND).isSameAs(Validation.kind());
    }

    @Test
    void function() {
      assertThat(Validation.FUNCTION).isSameAs(Validation.function());
    }

    @Test
    void transformer() {
      assertThat(Validation.TRANSFORMER).isSameAs(Validation.transformer());
    }

    @Test
    void core() {
      assertThat(Validation.CORE).isSameAs(Validation.coreType());
    }
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.instances.Witnesses.maybe;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Unit;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.laws.TraverseLaws;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.test.contract.Category;
import org.higherkindedj.hkt.test.contract.TypeClassContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("ValidatedTraverse")
class ValidatedTraverseTest extends ValidatedTestBase {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  private ValidatedTraverse<String> traverse;
  private MonadError<MaybeKind.Witness, Unit> applicative;
  private Monoid<String> monoid;

  @BeforeEach
  void setUpTraverse() {
    traverse = ValidatedTraverse.instance();
    applicative = Instances.monadError(maybe());
    monoid = Monoids.string();
  }

  @Nested
  @DisplayName("Laws")
  class Laws {

    @ParameterizedTest(name = "identity holds on {0}")
    @MethodSource("org.higherkindedj.hkt.validated.ValidatedLawFixtures#kinds")
    void identity(String label, Kind<ValidatedKind.Witness<String>, Integer> fa) {
      TraverseLaws.assertIdentity(traverse, fa, equalityChecker);
    }
  }

  @Test
  @DisplayName("Traverse contract — operations, validations & exceptions (laws verified above)")
  void traverseContract() {
    TypeClassContract.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
        .<Integer>instance(traverse)
        .<String>withKind(validKind)
        .withMapper(validMapper)
        .withApplicative(applicative, i -> MAYBE.widen(Maybe.just(i.toString())))
        .withFoldable(monoid, Object::toString)
        .verifyOnly(Category.OPERATIONS, Category.VALIDATIONS, Category.EXCEPTIONS);
  }

  @Nested
  @DisplayName("Map Operations")
  class MapOperations {

    @Test
    @DisplayName("Map transforms Valid values")
    void mapTransformsValidValues() {
      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(validMapper, validKind);
      assertThatValidated(result).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("Map preserves Invalid values")
    void mapPreservesInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(validMapper, invalid);
      assertThatValidated(result).isInvalid().hasError(DEFAULT_ERROR).hasErrorOfType(String.class);
    }
  }

  @Nested
  @DisplayName("Traverse Operations")
  class TraverseOperations {

    @Test
    @DisplayName("Traverse transforms Valid values")
    void traverseTransformsValidValues() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(applicative, f, validKind);

      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatValidated(maybe.get()).isValid().hasValue("42");
    }

    @Test
    @DisplayName("Traverse lifts Invalid into applicative")
    void traverseLiftsInvalidIntoApplicative() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(applicative, f, invalid);

      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatValidated(maybe.get()).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Traverse handles Nothing in transformation")
    void traverseHandlesNothingInTransformation() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f = _ -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(applicative, f, validKind);

      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Exception Propagation Tests")
  class ExceptionPropagationTests {

    @Test
    @DisplayName("Map propagates exceptions from mapper")
    void mapPropagatesExceptionsFromMapper() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, String> throwingMapper =
          _ -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.map(throwingMapper, validKind)).isSameAs(testException);
    }

    @Test
    @DisplayName("Traverse propagates exceptions from function")
    void traversePropagatesExceptionsFromFunction() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunction =
          _ -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.traverse(applicative, throwingFunction, validKind))
          .isSameAs(testException);
    }
  }

  @Nested
  @DisplayName("Edge Cases Tests")
  class EdgeCasesTests {

    @Test
    @DisplayName("ValidatedTraverse is a singleton")
    void validatedTraverseIsASingleton() {
      ValidatedTraverse<String> instance1 = ValidatedTraverse.instance();
      ValidatedTraverse<String> instance2 = ValidatedTraverse.instance();

      assertThat(instance1).isSameAs(instance2);
    }

    @Test
    @DisplayName("Map preserves structure for Valid")
    void mapPreservesStructureForValid() {
      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(validMapper, validKind);

      assertThat(result).isNotNull();
      assertThat(result).isInstanceOf(ValidatedKind.class);
    }

    @Test
    @DisplayName("Traverse preserves applicative structure")
    void traversePreservesApplicativeStructure() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(applicative, f, validKind);

      assertThat(result).isNotNull();
      assertThat(result).isInstanceOf(MaybeKind.class);
    }

    @Test
    @DisplayName("Multiple traversals compose correctly")
    void multipleTraversalsComposeCorrectly() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result1 =
          traverse.traverse(applicative, f, validKind);

      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybe1 = MAYBE.narrow(result1);
      assertThat(maybe1.isJust()).isTrue();

      Kind<ValidatedKind.Witness<String>, String> validated1 = maybe1.get();

      Function<String, Kind<MaybeKind.Witness, Integer>> g =
          s -> MAYBE.widen(Maybe.just(s.length()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, Integer>> result2 =
          traverse.traverse(applicative, g, validated1);

      Maybe<Kind<ValidatedKind.Witness<String>, Integer>> maybe2 = MAYBE.narrow(result2);
      assertThat(maybe2.isJust()).isTrue();
      assertThatValidated(maybe2.get()).isValid().hasValue(2); // "42".length()
    }
  }

  @Nested
  @DisplayName("Type Variance Tests")
  class TypeVarianceTests {

    @Test
    @DisplayName("Traverse works with different error types")
    void traverseWorksWithDifferentErrorTypes() {
      Kind<ValidatedKind.Witness<Integer>, String> intErrorValid =
          VALIDATED.widen(Validated.valid("test"));

      ValidatedTraverse<Integer> intTraverse = ValidatedTraverse.instance();

      Function<String, Kind<MaybeKind.Witness, Integer>> f =
          s -> MAYBE.widen(Maybe.just(s.length()));

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<Integer>, Integer>> result =
          intTraverse.traverse(applicative, f, intErrorValid);

      Maybe<Kind<ValidatedKind.Witness<Integer>, Integer>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isJust()).isTrue();
      assertThatValidated(maybe.get()).isValid().hasValue(4);
    }
  }
}

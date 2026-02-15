// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.validated.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.function.Function;
import org.higherkindedj.hkt.*;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.hkt.test.api.TypeClassTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ValidatedTraverse Complete Test Suite")
class ValidatedTraverseTest extends ValidatedTestBase {

  private static final MaybeKindHelper MAYBE = MaybeKindHelper.MAYBE;

  private ValidatedTraverse<String> traverse;
  private MaybeMonad applicative;
  private Monoid<String> monoid;

  @BeforeEach
  void setUpTraverse() {
    traverse = ValidatedTraverse.instance();
    applicative = MaybeMonad.INSTANCE;
    monoid = Monoids.string();
  }

  @Nested
  @DisplayName("Complete Test Suite")
  class CompleteTestSuite {

    @Test
    @DisplayName("Run complete ValidatedTraverse test pattern")
    void runCompleteValidatedTraverseTestPattern() {
      TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(applicative, i -> MAYBE.widen(Maybe.just(i.toString())))
          .withFoldableOperations(monoid, Object::toString)
          .testAll();
    }
  }

  @Nested
  @DisplayName("Functor Operations")
  class FunctorOperations {

    @Test
    @DisplayName("Map transforms Valid values")
    void mapTransformsValidValues() {
      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(validMapper, validKind);

      Validated<String, String> validated = narrowToValidated(result);
      assertThatValidated(validated).isValid().hasValue("42").hasValueOfType(String.class);
    }

    @Test
    @DisplayName("Map preserves Invalid values")
    void mapPreservesInvalidValues() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      Kind<ValidatedKind.Witness<String>, String> result = traverse.map(validMapper, invalid);

      Validated<String, String> validated = narrowToValidated(result);
      assertThatValidated(validated)
          .isInvalid()
          .hasError(DEFAULT_ERROR)
          .hasErrorOfType(String.class);
    }

    @Test
    @DisplayName("Map validates mapper is non-null")
    void mapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> traverse.map(null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("map");
    }

    @Test
    @DisplayName("Map validates Kind is non-null")
    void mapValidatesKindIsNonNull() {
      assertThatThrownBy(() -> traverse.map(validMapper, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("map");
    }
  }

  @Nested
  @DisplayName("Foldable Operations")
  class FoldableOperations {

    @Test
    @DisplayName("FoldMap reduces Valid to monoid value")
    void foldMapReducesValidToMonoidValue() {
      String result = traverse.foldMap(monoid, Object::toString, validKind);

      assertThat(result).isEqualTo("42");
    }

    @Test
    @DisplayName("FoldMap returns empty for Invalid")
    void foldMapReturnsEmptyForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);

      String result = traverse.foldMap(monoid, Object::toString, invalid);

      assertThat(result).isEqualTo("");
    }

    @Test
    @DisplayName("FoldMap validates monoid is non-null")
    void foldMapValidatesMonoidIsNonNull() {
      assertThatThrownBy(() -> traverse.foldMap(null, Object::toString, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("monoid")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("foldMap");
    }

    @Test
    @DisplayName("FoldMap validates mapper is non-null")
    void foldMapValidatesMapperIsNonNull() {
      assertThatThrownBy(() -> traverse.foldMap(monoid, null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("foldMap");
    }

    @Test
    @DisplayName("FoldMap validates Kind is non-null")
    void foldMapValidatesKindIsNonNull() {
      assertThatThrownBy(() -> traverse.foldMap(monoid, Object::toString, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("foldMap");
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

      Validated<String, String> validated = narrowToValidated(maybe.get());
      assertThatValidated(validated).isValid().hasValue("42");
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

      Validated<String, String> validated = narrowToValidated(maybe.get());
      assertThatValidated(validated).isInvalid().hasError(DEFAULT_ERROR);
    }

    @Test
    @DisplayName("Traverse handles Nothing in transformation")
    void traverseHandlesNothingInTransformation() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f = i -> MAYBE.widen(Maybe.nothing());

      Kind<MaybeKind.Witness, Kind<ValidatedKind.Witness<String>, String>> result =
          traverse.traverse(applicative, f, validKind);

      Maybe<Kind<ValidatedKind.Witness<String>, String>> maybe = MAYBE.narrow(result);
      assertThat(maybe.isNothing()).isTrue();
    }

    @Test
    @DisplayName("Traverse validates applicative is non-null")
    void traverseValidatesApplicativeIsNonNull() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      assertThatThrownBy(() -> traverse.traverse(null, f, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("applicative")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("traverse");
    }

    @Test
    @DisplayName("Traverse validates function is non-null")
    void traverseValidatesFunctionIsNonNull() {
      assertThatThrownBy(() -> traverse.traverse(applicative, null, validKind))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("f")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("traverse");
    }

    @Test
    @DisplayName("Traverse validates Kind is non-null")
    void traverseValidatesKindIsNonNull() {
      Function<Integer, Kind<MaybeKind.Witness, String>> f =
          i -> MAYBE.widen(Maybe.just(i.toString()));

      assertThatThrownBy(() -> traverse.traverse(applicative, f, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("Kind")
          .hasMessageContaining("ValidatedTraverse")
          .hasMessageContaining("traverse");
    }
  }

  @Nested
  @DisplayName("Individual Components")
  class IndividualComponents {

    @Test
    @DisplayName("Test operations only")
    void testOperationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(applicative, i -> MAYBE.widen(Maybe.just(i.toString())))
          .withFoldableOperations(monoid, Object::toString)
          .testOperations();
    }

    @Test
    @DisplayName("Test validations only")
    void testValidationsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(applicative, i -> MAYBE.widen(Maybe.just(i.toString())))
          .withFoldableOperations(monoid, Object::toString)
          .testValidations();
    }

    @Test
    @DisplayName("Test exceptions only")
    void testExceptionsOnly() {
      TypeClassTest.<ValidatedKind.Witness<String>>traverse(ValidatedTraverse.class)
          .<Integer>instance(traverse)
          .<String>withKind(validKind)
          .withOperations(validMapper)
          .withApplicative(applicative, i -> MAYBE.widen(Maybe.just(i.toString())))
          .withFoldableOperations(monoid, Object::toString)
          .testExceptions();
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
          i -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.map(throwingMapper, validKind)).isSameAs(testException);
    }

    @Test
    @DisplayName("FoldMap propagates exceptions from mapper")
    void foldMapPropagatesExceptionsFromMapper() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, String> throwingMapper =
          i -> {
            throw testException;
          };

      assertThatThrownBy(() -> traverse.foldMap(monoid, throwingMapper, validKind))
          .isSameAs(testException);
    }

    @Test
    @DisplayName("Traverse propagates exceptions from function")
    void traversePropagatesExceptionsFromFunction() {
      RuntimeException testException = new RuntimeException("Test exception");
      Function<Integer, Kind<MaybeKind.Witness, String>> throwingFunction =
          i -> {
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
    @DisplayName("FoldMap with identity function returns value for Valid")
    void foldMapWithIdentityFunctionReturnsValueForValid() {
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Function<Integer, Integer> identity = i -> i;

      Integer result = traverse.foldMap(intMonoid, identity, validKind);

      assertThat(result).isEqualTo(DEFAULT_VALID_VALUE);
    }

    @Test
    @DisplayName("FoldMap with identity function returns empty for Invalid")
    void foldMapWithIdentityFunctionReturnsEmptyForInvalid() {
      Kind<ValidatedKind.Witness<String>, Integer> invalid = invalidKind(DEFAULT_ERROR);
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Function<Integer, Integer> identity = i -> i;

      Integer result = traverse.foldMap(intMonoid, identity, invalid);

      assertThat(result).isEqualTo(0);
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

      Validated<String, Integer> validated2 = narrowToValidated(maybe2.get());
      assertThatValidated(validated2).isValid().hasValue(2); // "42".length()
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

      Validated<Integer, Integer> validated = VALIDATED.narrow(maybe.get());
      assertThatValidated(validated).isValid().hasValue(4);
    }

    @Test
    @DisplayName("FoldMap works with different monoid types")
    void foldMapWorksWithDifferentMonoidTypes() {
      Monoid<Integer> intMonoid = Monoids.integerAddition();
      Function<Integer, Integer> doubler = i -> i * 2;

      Integer result = traverse.foldMap(intMonoid, doubler, validKind);

      assertThat(result).isEqualTo(84);
    }
  }
}

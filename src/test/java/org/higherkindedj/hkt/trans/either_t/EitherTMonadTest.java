package org.higherkindedj.hkt.trans.either_t;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("EitherT Monad Tests (Outer: Optional, Left: String)")
class EitherTMonadTest {

  // Outer Monad (F = OptionalKind<?>)
  // OptionalMonad itself implements MonadError<OptionalKind<?>, Void>
  private final MonadError<OptionalKind<?>, Void> outerMonad = new OptionalMonad();

  // EitherT Monad (G = EitherTKind<OptionalKind<?>, String, ?>)
  // The EitherTMonad takes a Monad<F> for its operations.
  private final MonadError<EitherTKind<OptionalKind<?>, String, ?>, String> eitherTMonad =
      new EitherTMonad<>(outerMonad);

  // --- Helper Methods ---

  // Unwrap Kind<EitherTKind<OptionalKind<?>, String, ?>, A> -> Optional<Either<String, A>>
  private <A> Optional<Either<String, A>> unwrapKindToOptionalEither(
      Kind<EitherTKind<OptionalKind<?>, String, ?>, A> kind) {
    // Use the helper to unwrap to the concrete EitherT
    EitherT<OptionalKind<?>, String, A> eitherT = EitherTKindHelper.unwrap(kind);
    // Then get the inner Kind<OptionalKind<?>, Either<String, A>>
    Kind<OptionalKind<?>, Either<String, A>> outerKind = eitherT.value();
    // Unwrap the OptionalKind
    return OptionalKindHelper.unwrap(outerKind);
  }

  // Create a wrapped EitherT(Optional.of(Right(value)))
  private <A> Kind<EitherTKind<OptionalKind<?>, String, ?>, A> rightT(A value) {
    EitherT<OptionalKind<?>, String, A> concreteEitherT = EitherT.right(outerMonad, value);
    return EitherTKindHelper.wrap(concreteEitherT); // Wrap using the helper
  }

  // Create a wrapped EitherT(Optional.of(Left(error)))
  private <A> Kind<EitherTKind<OptionalKind<?>, String, ?>, A> leftT(String error) {
    EitherT<OptionalKind<?>, String, A> concreteEitherT = EitherT.left(outerMonad, error);
    return EitherTKindHelper.wrap(concreteEitherT); // Wrap using the helper
  }

  // Create a wrapped EitherT(Optional.empty())
  private <A> Kind<EitherTKind<OptionalKind<?>, String, ?>, A> emptyT() {
    Kind<OptionalKind<?>, Either<String, A>> emptyOuter = OptionalKindHelper.wrap(Optional.empty());
    EitherT<OptionalKind<?>, String, A> concreteEitherT = EitherT.fromKind(emptyOuter);
    return EitherTKindHelper.wrap(concreteEitherT); // Wrap using the helper
  }

  // Helper Functions for Laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Function a -> M b (Integer -> Kind<EitherTKind<OptionalKind<?>, String, ?>, String>)
  private final Function<Integer, Kind<EitherTKind<OptionalKind<?>, String, ?>, String>> fT =
      i -> rightT("v" + i);
  // Function b -> M c (String -> Kind<EitherTKind<OptionalKind<?>, String, ?>, String>)
  private final Function<String, Kind<EitherTKind<OptionalKind<?>, String, ?>, String>> gT =
      s -> rightT(s + "!");

  // --- Basic Operations Tests ---

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsRightInOptional() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> kind = eitherTMonad.of(10);
      Optional<Either<String, Integer>> result = unwrapKindToOptionalEither(kind);
      assertThat(result).isPresent().contains(Either.right(10));
    }

    @Test
    void of_shouldWrapNullAsRightInOptional() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> kind = eitherTMonad.of(null);
      Optional<Either<String, Integer>> result = unwrapKindToOptionalEither(kind);
      assertThat(result).isPresent().contains(Either.right(null));
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenRight() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> input = rightT(5);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.map(Object::toString, input);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right("5"));
    }

    @Test
    void map_shouldPropagateLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> input = leftT("Error1");
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.map(Object::toString, input);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.left("Error1"));
    }

    @Test
    void map_shouldPropagateEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> input = emptyT();
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.map(Object::toString, input);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    @Test
    void map_shouldHandleMappingToNullAsRightNull() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> input = rightT(5);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.map(x -> null, input);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(null));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> funcKindRight =
        rightT(x -> "N" + x);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> funcKindLeft =
        leftT("FuncError");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> funcKindEmpty =
        emptyT();

    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> valKindRight = rightT(10);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> valKindLeft = leftT("ValError");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> valKindEmpty = emptyT();

    @Test
    void ap_shouldApplyRightFuncToRightValue() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindRight, valKindRight);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right("N10"));
    }

    @Test
    void ap_shouldReturnLeftIfFuncIsLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindLeft, valKindRight);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.left("FuncError"));
    }

    @Test
    void ap_shouldReturnLeftIfValueIsLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindRight, valKindLeft);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.left("ValError"));
    }

    @Test
    void ap_shouldReturnFirstLeftIfBothAreLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindLeft, valKindLeft);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.left("FuncError"));
    }

    @Test
    void ap_shouldReturnEmptyIfFuncIsEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindEmpty, valKindRight);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfValueIsEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindRight, valKindEmpty);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    @Test
    void ap_shouldReturnEmptyIfBothAreEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> result =
          eitherTMonad.ap(funcKindEmpty, valKindEmpty);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {
    Function<Integer, Kind<EitherTKind<OptionalKind<?>, String, ?>, Double>> safeDivideT =
        num -> (num == 0) ? leftT("DivZero") : rightT(100.0 / num);
    Function<Integer, Kind<EitherTKind<OptionalKind<?>, String, ?>, Double>> emptyResultT =
        num -> emptyT();

    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> rightValue = rightT(5);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> zeroValue = rightT(0);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> leftValue = leftT("InitialError");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> emptyValue = emptyT();

    @Test
    void flatMap_shouldApplyFuncWhenRight() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Double> result =
          eitherTMonad.flatMap(safeDivideT, rightValue);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(20.0));
    }

    @Test
    void flatMap_shouldReturnLeftWhenFuncReturnsLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Double> result =
          eitherTMonad.flatMap(safeDivideT, zeroValue);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.left("DivZero"));
    }

    @Test
    void flatMap_shouldPropagateLeftWhenInputIsLeft() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Double> result =
          eitherTMonad.flatMap(safeDivideT, leftValue);
      assertThat(unwrapKindToOptionalEither(result))
          .isPresent()
          .contains(Either.left("InitialError"));
    }

    @Test
    void flatMap_shouldPropagateEmptyWhenInputIsEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Double> result =
          eitherTMonad.flatMap(safeDivideT, emptyValue);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    @Test
    void flatMap_shouldPropagateEmptyWhenFuncReturnsEmpty() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Double> result =
          eitherTMonad.flatMap(emptyResultT, rightValue);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }
  }

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> fa = rightT(10);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> faLeft = leftT("L");
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> faEmpty = emptyT();

      assertThat(unwrapKindToOptionalEither(eitherTMonad.map(Function.identity(), fa)))
          .isEqualTo(unwrapKindToOptionalEither(fa));
      assertThat(unwrapKindToOptionalEither(eitherTMonad.map(Function.identity(), faLeft)))
          .isEqualTo(unwrapKindToOptionalEither(faLeft));
      assertThat(unwrapKindToOptionalEither(eitherTMonad.map(Function.identity(), faEmpty)))
          .isEqualTo(unwrapKindToOptionalEither(faEmpty));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> fa = rightT(10);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> faLeft = leftT("L");
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> faEmpty = emptyT();

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide =
          eitherTMonad.map(intToStringAppendWorld, fa);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide =
          eitherTMonad.map(appendWorld, eitherTMonad.map(intToString, fa));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideLeft =
          eitherTMonad.map(intToStringAppendWorld, faLeft);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideLeft =
          eitherTMonad.map(appendWorld, eitherTMonad.map(intToString, faLeft));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideEmpty =
          eitherTMonad.map(intToStringAppendWorld, faEmpty);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideEmpty =
          eitherTMonad.map(appendWorld, eitherTMonad.map(intToString, faEmpty));

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));
      assertThat(unwrapKindToOptionalEither(leftSideLeft))
          .isEqualTo(unwrapKindToOptionalEither(rightSideLeft));
      assertThat(unwrapKindToOptionalEither(leftSideEmpty))
          .isEqualTo(unwrapKindToOptionalEither(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> v = rightT(5);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> vLeft = leftT("V_ERR");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> vEmpty = emptyT();
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> fKind =
        rightT(intToString);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> fKindLeft =
        leftT("F_ERR");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<String, String>> gKind =
        rightT(appendWorld);

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, Integer>> idFuncKind =
          eitherTMonad.of(Function.identity());
      assertThat(unwrapKindToOptionalEither(eitherTMonad.ap(idFuncKind, v)))
          .isEqualTo(unwrapKindToOptionalEither(v));
      assertThat(unwrapKindToOptionalEither(eitherTMonad.ap(idFuncKind, vLeft)))
          .isEqualTo(unwrapKindToOptionalEither(vLeft));
      assertThat(unwrapKindToOptionalEither(eitherTMonad.ap(idFuncKind, vEmpty)))
          .isEqualTo(unwrapKindToOptionalEither(vEmpty));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> apFunc =
          eitherTMonad.of(f);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> apVal = eitherTMonad.of(x);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide =
          eitherTMonad.ap(apFunc, apVal);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide = eitherTMonad.of(f.apply(x));

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide =
          eitherTMonad.ap(fKind, eitherTMonad.of(y));
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideFuncLeft =
          eitherTMonad.ap(fKindLeft, eitherTMonad.of(y));

      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Function<Integer, String>, String>>
          evalKind = eitherTMonad.of(evalWithY);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide =
          eitherTMonad.ap(evalKind, fKind);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideFuncLeft =
          eitherTMonad.ap(evalKind, fKindLeft);

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));
      assertThat(unwrapKindToOptionalEither(leftSideFuncLeft))
          .isEqualTo(unwrapKindToOptionalEither(rightSideFuncLeft));
    }

    @Test
    @DisplayName("4. Composition: ap(ap(map(compose, gKind), fKind), v) == ap(gKind, ap(fKind, v))")
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap = gg -> ff -> gg.compose(ff);

      Kind<
              EitherTKind<OptionalKind<?>, String, ?>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = eitherTMonad.map(composeMap, gKind);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Function<Integer, String>> ap1 =
          eitherTMonad.ap(mappedCompose, fKind);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide = eitherTMonad.ap(ap1, v);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> innerAp = eitherTMonad.ap(fKind, v);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide =
          eitherTMonad.ap(gKind, innerAp);

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideLeft =
          eitherTMonad.ap(eitherTMonad.ap(mappedCompose, fKindLeft), v);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideLeft =
          eitherTMonad.ap(gKind, eitherTMonad.ap(fKindLeft, v));
      assertThat(unwrapKindToOptionalEither(leftSideLeft))
          .isEqualTo(unwrapKindToOptionalEither(rightSideLeft));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideEmpty =
          eitherTMonad.ap(eitherTMonad.ap(mappedCompose, fKind), vEmpty);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideEmpty =
          eitherTMonad.ap(gKind, eitherTMonad.ap(fKind, vEmpty));
      assertThat(unwrapKindToOptionalEither(leftSideEmpty))
          .isEqualTo(unwrapKindToOptionalEither(rightSideEmpty));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {
    int value = 5;
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> mValue = rightT(value);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> mLeft = leftT("M_ERR");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> mEmpty = emptyT();

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> ofValue = eitherTMonad.of(value);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide =
          eitherTMonad.flatMap(fT, ofValue);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide = fT.apply(value);

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      Function<Integer, Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>> ofFunc =
          i -> eitherTMonad.of(i);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> leftSide =
          eitherTMonad.flatMap(ofFunc, mValue);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> leftSideLeft =
          eitherTMonad.flatMap(ofFunc, mLeft);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> leftSideEmpty =
          eitherTMonad.flatMap(ofFunc, mEmpty);

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(mValue));
      assertThat(unwrapKindToOptionalEither(leftSideLeft))
          .isEqualTo(unwrapKindToOptionalEither(mLeft));
      assertThat(unwrapKindToOptionalEither(leftSideEmpty))
          .isEqualTo(unwrapKindToOptionalEither(mEmpty));
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> innerFlatMap =
          eitherTMonad.flatMap(fT, mValue);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSide =
          eitherTMonad.flatMap(gT, innerFlatMap);

      Function<Integer, Kind<EitherTKind<OptionalKind<?>, String, ?>, String>> rightSideFunc =
          a -> eitherTMonad.flatMap(gT, fT.apply(a));
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSide =
          eitherTMonad.flatMap(rightSideFunc, mValue);

      assertThat(unwrapKindToOptionalEither(leftSide))
          .isEqualTo(unwrapKindToOptionalEither(rightSide));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> innerFlatMapLeft =
          eitherTMonad.flatMap(fT, mLeft);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideLeft =
          eitherTMonad.flatMap(gT, innerFlatMapLeft);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideLeft =
          eitherTMonad.flatMap(rightSideFunc, mLeft);
      assertThat(unwrapKindToOptionalEither(leftSideLeft))
          .isEqualTo(unwrapKindToOptionalEither(rightSideLeft));

      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> innerFlatMapEmpty =
          eitherTMonad.flatMap(fT, mEmpty);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> leftSideEmpty =
          eitherTMonad.flatMap(gT, innerFlatMapEmpty);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, String> rightSideEmpty =
          eitherTMonad.flatMap(rightSideFunc, mEmpty);
      assertThat(unwrapKindToOptionalEither(leftSideEmpty))
          .isEqualTo(unwrapKindToOptionalEither(rightSideEmpty));
    }
  }

  // --- MonadError Tests ---

  @Nested
  @DisplayName("MonadError Tests")
  class MonadErrorTests {
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> rightVal = rightT(100);
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> leftVal = leftT("E404");
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> emptyVal = emptyT();

    String raisedErrorMsg = "E500";
    Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> raisedErrorKind =
        eitherTMonad.raiseError(raisedErrorMsg);

    @Test
    void raiseError_shouldCreateLeftInOptional() {
      Optional<Either<String, Integer>> result = unwrapKindToOptionalEither(raisedErrorKind);
      assertThat(result).isPresent().contains(Either.left("E500"));
    }

    @Test
    void handleErrorWith_shouldHandleLeft() {
      Function<String, Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>> handler =
          err -> rightT(Integer.parseInt(err.substring(1))); // "E404" -> Right(404)

      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleErrorWith(leftVal, handler);

      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(404));
    }

    @Test
    void handleErrorWith_shouldHandleLeftWithNewLeft() {
      Function<String, Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>> handler =
          err -> leftT("Recovered_" + err);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleErrorWith(leftVal, handler);

      assertThat(unwrapKindToOptionalEither(result))
          .isPresent()
          .contains(Either.left("Recovered_E404"));
    }

    @Test
    void handleErrorWith_shouldIgnoreRight() {
      Function<String, Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>> handler =
          err -> rightT(-1);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleErrorWith(rightVal, handler);

      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(rightVal));
    }

    @Test
    void handleErrorWith_shouldIgnoreEmpty() {
      Function<String, Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer>> handler =
          err -> rightT(-1);

      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleErrorWith(emptyVal, handler);
      assertThat(unwrapKindToOptionalEither(result)).isEmpty();
    }

    @Test
    void handleError_shouldHandleLeftWithPureValue() {
      Function<String, Integer> handler = err -> -99;
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleError(leftVal, handler);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(-99));
    }

    @Test
    void handleError_shouldIgnoreRight() {
      Function<String, Integer> handler = err -> -1;
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.handleError(rightVal, handler);
      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(rightVal));
    }

    @Test
    void recoverWith_shouldReplaceLeftWithFallbackKind() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> fallback = rightT(0);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.recoverWith(leftVal, fallback);
      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(fallback));
    }

    @Test
    void recoverWith_shouldReplaceLeftWithLeftFallbackKind() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> fallback = leftT("FallbackError");
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.recoverWith(leftVal, fallback);
      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(fallback));
    }

    @Test
    void recoverWith_shouldIgnoreRight() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> fallback = rightT(0);
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.recoverWith(rightVal, fallback);
      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(rightVal));
    }

    @Test
    void recover_shouldReplaceLeftWithOfValue() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.recover(leftVal, 0);
      assertThat(unwrapKindToOptionalEither(result)).isPresent().contains(Either.right(0));
    }

    @Test
    void recover_shouldIgnoreRight() {
      Kind<EitherTKind<OptionalKind<?>, String, ?>, Integer> result =
          eitherTMonad.recover(rightVal, 0);
      assertThat(unwrapKindToOptionalEither(result))
          .isEqualTo(unwrapKindToOptionalEither(rightVal));
    }
  }
}

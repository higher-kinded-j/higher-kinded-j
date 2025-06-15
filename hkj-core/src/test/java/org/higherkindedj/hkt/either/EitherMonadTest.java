// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.either;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.higherkindedj.hkt.function.Function3;
import org.higherkindedj.hkt.function.Function4;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

// Define a simple error type for testing
record TestError(String code) {}

class EitherMonadTest {

  // Monad instance for Either<TestError, ?>
  private final EitherMonad<TestError> eitherMonad = EitherMonad.instance();

  // Helper Functions
  private <R> Either<TestError, R> narrow(Kind<EitherKind.Witness<TestError>, R> kind) {
    return EITHER.narrow(kind);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> widen(Either<TestError, R> either) {
    return EITHER.widen(either);
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> right(R value) {
    return widen(Either.right(value));
  }

  private <R> Kind<EitherKind.Witness<TestError>, R> left(String errorCode) {
    return widen(Either.left(new TestError(errorCode)));
  }

  // Functions for laws
  private final Function<Integer, String> intToString = Object::toString;
  private final Function<String, String> appendWorld = s -> s + " world";
  private final Function<Integer, String> intToStringAppendWorld = intToString.andThen(appendWorld);

  // Dummy Kind implementation for unwrap robustness test
  record DummyEitherKind<L, R>() implements Kind<EitherKind<L, ?>, R> {}

  @Nested
  @DisplayName("Applicative 'of' tests")
  class OfTests {
    @Test
    void of_shouldWrapValueAsRight() {
      var kind = eitherMonad.of("success");
      Either<TestError, String> either = narrow(kind);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("success");
    }

    @Test
    void of_shouldWrapNullValueAsRight() {
      // Either.Right allows null by default in this impl
      Kind<EitherKind.Witness<TestError>, String> kind = eitherMonad.of(null);
      Either<TestError, String> either = narrow(kind);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isNull();
    }
  }

  @Nested
  @DisplayName("Functor 'map' tests")
  class MapTests {
    @Test
    void map_shouldApplyFunctionWhenRight() {
      Kind<EitherKind.Witness<TestError>, Integer> input = right(10);
      Kind<EitherKind.Witness<TestError>, Integer> result = eitherMonad.map(x -> x * 2, input);
      Either<TestError, Integer> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(20);
    }

    @Test
    void map_shouldPropagateLeftWhenLeft() {
      Kind<EitherKind.Witness<TestError>, Integer> input = left("E1");
      Kind<EitherKind.Witness<TestError>, Integer> result = eitherMonad.map(x -> x * 2, input);
      Either<TestError, Integer> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("E1"));
    }
  }

  @Nested
  @DisplayName("Applicative 'ap' tests")
  class ApTests {
    Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKindRight =
        eitherMonad.of(x -> "N" + x);
    Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKindLeft = left("FE");
    Kind<EitherKind.Witness<TestError>, Integer> valueKindRight = right(10);
    Kind<EitherKind.Witness<TestError>, Integer> valueKindLeft = left("VE");

    @Test
    void ap_shouldApplyRightFunctionToRightValue() {
      Kind<EitherKind.Witness<TestError>, String> result =
          eitherMonad.ap(funcKindRight, valueKindRight);
      Either<TestError, String> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo("N10");
    }

    @Test
    void ap_shouldReturnLeftIfFunctionIsLeft() {
      var result = eitherMonad.ap(funcKindLeft, valueKindRight);
      Either<TestError, String> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("FE")); // Function's error propagates
    }

    @Test
    void ap_shouldReturnLeftIfValueIsLeft() {
      var result = eitherMonad.ap(funcKindRight, valueKindLeft);
      Either<TestError, String> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("VE")); // Value's error propagates
    }

    @Test
    void ap_shouldReturnFirstLeftIfBothAreLeft() {
      var result = eitherMonad.ap(funcKindLeft, valueKindLeft);
      Either<TestError, String> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft())
          .isEqualTo(
              new TestError("FE")); // Function's error propagates first due to flatMap structure
    }

    @Test
    @DisplayName("ap should propagate exception from function application")
    void ap_shouldPropagateFunctionException() {
      RuntimeException applyException = new RuntimeException("Apply failed!");
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> funcKindThrows =
          widen(
              Either.right(
                  i -> {
                    throw applyException;
                  }));
      Kind<EitherKind.Witness<TestError>, Integer> valueKindRight = right(10);

      // Exception occurs during the f.apply(a) inside the flatMap/map of 'ap'
      assertThatThrownBy(() -> eitherMonad.ap(funcKindThrows, valueKindRight))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(applyException);
    }
  }

  @Nested
  @DisplayName("Monad 'flatMap' tests")
  class FlatMapTests {

    Function<Integer, Kind<EitherKind.Witness<TestError>, Double>> safeDivideEither =
        num -> (num == 0) ? left("DIV0") : right(100.0 / num);

    Kind<EitherKind.Witness<TestError>, Integer> rightValue = right(5);
    Kind<EitherKind.Witness<TestError>, Integer> zeroValue = right(0);
    Kind<EitherKind.Witness<TestError>, Integer> leftValue = left("INIT_ERR");

    @Test
    void flatMap_shouldApplyFunctionWhenRight() {
      Kind<EitherKind.Witness<TestError>, Double> result =
          eitherMonad.flatMap(safeDivideEither, rightValue);
      Either<TestError, Double> either = narrow(result);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(20.0);
    }

    @Test
    void flatMap_shouldReturnLeftWhenFunctionReturnsLeft() {
      var result = eitherMonad.flatMap(safeDivideEither, zeroValue);
      Either<TestError, Double> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("DIV0"));
    }

    @Test
    void flatMap_shouldPropagateLeftWhenInputIsLeft() {
      var result = eitherMonad.flatMap(safeDivideEither, leftValue);
      Either<TestError, Double> either = narrow(result);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("INIT_ERR")); // Initial error propagates
    }

    @Test
    void flatMap_chainingExample() {
      var initial = right(" 20 "); // String needs parsing

      Function<String, Kind<EitherKind.Witness<TestError>, Integer>> parseIntEither =
          s -> {
            try {
              return right(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
              return left("PARSE_ERR");
            }
          };

      Kind<EitherKind.Witness<TestError>, Double> finalResult =
          eitherMonad.flatMap(
              str ->
                  eitherMonad.flatMap(
                      safeDivideEither, parseIntEither.apply(str)), // nested flatMap
              initial);

      Either<TestError, Double> either = narrow(finalResult);
      assertThat(either.isRight()).isTrue();
      assertThat(either.getRight()).isEqualTo(5.0); // 100.0 / 20
    }

    @Test
    void flatMap_chainingWithLeftPropagation() {
      var initial = right(" abc "); // Invalid string

      Function<String, Kind<EitherKind.Witness<TestError>, Integer>> parseIntEither =
          s -> {
            try {
              return right(Integer.parseInt(s.trim()));
            } catch (NumberFormatException e) {
              return left("PARSE_ERR"); // This error should propagate
            }
          };

      Kind<EitherKind.Witness<TestError>, Double> finalResult =
          eitherMonad.flatMap(
              str ->
                  eitherMonad.flatMap(
                      safeDivideEither,
                      parseIntEither.apply(str)), // safeDivideEither is never reached
              initial);

      Either<TestError, Double> either = narrow(finalResult);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(new TestError("PARSE_ERR"));
    }

    @Test
    @DisplayName("flatMap should propagate exception thrown by mapper function")
    void flatMap_shouldPropagateMapperException() {
      var rightInput = right(10);
      RuntimeException mapperException = new RuntimeException("Mapper failed!");
      Function<Integer, Kind<EitherKind.Witness<TestError>, Integer>> throwingMapper =
          i -> {
            throw mapperException;
          };

      // Either.Right.flatMap does not catch exceptions from the mapper
      assertThatThrownBy(() -> eitherMonad.flatMap(throwingMapper, rightInput))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(mapperException);
    }

    @Test
    @DisplayName("flatMap should throw KindUnwrapException if mapper returns null Kind")
    void flatMap_shouldThrowWhenMapperReturnsNullKind() {
      var rightInput = right(10); // Assuming right() helper exists
      // Mapper function explicitly returns null
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> nullReturningMapper =
          i -> null;

      // Assert that calling flatMap with this mapper throws the exception directly
      assertThatThrownBy(() -> eitherMonad.flatMap(nullReturningMapper, rightInput))
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(
              EitherKindHelper
                  .INVALID_KIND_NULL_MSG); // Exception originates from unwrap(null) call
    }
  }

  // --- Law Tests ---

  @Nested
  @DisplayName("Functor Laws")
  class FunctorLaws {
    @Test
    @DisplayName("1. Identity: map(id, fa) == fa")
    void identity() {
      var fa = right(10);
      var faLeft = left("E1");

      assertThat(narrow(eitherMonad.map(Function.identity(), fa))).isEqualTo(narrow(fa));
      assertThat(narrow(eitherMonad.map(Function.identity(), faLeft))).isEqualTo(narrow(faLeft));
    }

    @Test
    @DisplayName("2. Composition: map(g.compose(f), fa) == map(g, map(f, fa))")
    void composition() {
      Kind<EitherKind.Witness<TestError>, Integer> fa = right(10);
      Kind<EitherKind.Witness<TestError>, Integer> faLeft = left("E1");

      var leftSide = eitherMonad.map(appendWorld.compose(intToString), fa);
      var rightSide = eitherMonad.map(appendWorld, eitherMonad.map(intToString, fa));

      var leftSideLeft = eitherMonad.map(appendWorld.compose(intToString), faLeft);
      var rightSideLeft = eitherMonad.map(appendWorld, eitherMonad.map(intToString, faLeft));

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
      assertThat(narrow(leftSideLeft)).isEqualTo(narrow(rightSideLeft));
    }
  }

  @Nested
  @DisplayName("Applicative Laws")
  class ApplicativeLaws {

    Kind<EitherKind.Witness<TestError>, Integer> v = right(5);
    Kind<EitherKind.Witness<TestError>, Integer> vLeft = left("V_ERR");
    Kind<EitherKind.Witness<TestError>, Function<Integer, String>> fKind = right(intToString);
    Kind<EitherKind.Witness<TestError>, Function<Integer, String>> fKindLeft = left("F_ERR");
    Kind<EitherKind.Witness<TestError>, Function<String, String>> gKind = right(appendWorld);
    Kind<EitherKind.Witness<TestError>, Function<String, String>> gKindLeft = left("G_ERR");

    @Test
    @DisplayName("1. Identity: ap(of(id), v) == v")
    void identity() {
      Kind<EitherKind.Witness<TestError>, Function<Integer, Integer>> idFuncKind =
          eitherMonad.of(Function.identity());
      assertThat(narrow(eitherMonad.ap(idFuncKind, v))).isEqualTo(narrow(v));
      assertThat(narrow(eitherMonad.ap(idFuncKind, vLeft))).isEqualTo(narrow(vLeft));
    }

    @Test
    @DisplayName("2. Homomorphism: ap(of(f), of(x)) == of(f(x))")
    void homomorphism() {
      int x = 10;
      Function<Integer, String> f = intToString;
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> apFunc = eitherMonad.of(f);
      Kind<EitherKind.Witness<TestError>, Integer> apVal = eitherMonad.of(x);

      var leftSide = eitherMonad.ap(apFunc, apVal);
      var rightSide = eitherMonad.of(f.apply(x));

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
    }

    @Test
    @DisplayName("3. Interchange: ap(fKind, of(y)) == ap(of(f -> f(y)), fKind)")
    void interchange() {
      int y = 20;
      // Left Side: ap(fKind, of(y))
      var leftSide = eitherMonad.ap(fKind, eitherMonad.of(y));
      var leftSideFuncLeft = eitherMonad.ap(fKindLeft, eitherMonad.of(y));

      // Right Side: ap(of(f -> f(y)), fKind)
      Function<Function<Integer, String>, String> evalWithY = fn -> fn.apply(y);
      Kind<EitherKind.Witness<TestError>, Function<Function<Integer, String>, String>> evalKind =
          eitherMonad.of(evalWithY);

      var rightSide = eitherMonad.ap(evalKind, fKind);
      var rightSideFuncLeft = eitherMonad.ap(evalKind, fKindLeft);

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
      assertThat(narrow(leftSideFuncLeft)).isEqualTo(narrow(rightSideFuncLeft));
    }

    @Test
    @DisplayName(
        "4. Composition: ap(map(compose, fKind), gKind) == ap(fKind, ap(gKind, v)) - Adjusted for"
            + " types")
    // Standard law: ap ( ap ( map (compose) u ) v ) w = ap u ( ap v w )
    // Let u = map(compose, fKind) = map(g -> f.compose(g), fKind) -> This doesn't type check easily
    // here.
    // Let's use the structure map(g, map(f, x)) = map(g.compose(f), x) derived via ap
    // ap(of(g), ap(of(f), of(x))) == ap(of(g.compose(f)), of(x)) -> Homomorphism
    // Let's try: ap(fg, ap(ga, value)) == ap(ap(map(compose, fg), ga), value) where compose is
    // (b->c) -> (a->b) -> (a->c)
    void composition() {
      Function<
              Function<String, String>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          composeMap =
              g -> g::compose; // (String -> String) -> (Integer -> String) -> (Integer -> String)

      Kind<EitherKind.Witness<TestError>, Integer> value = right(10);

      // Left side: ap(ap(map(composeMap, gKind), fKind), value)
      Kind<
              EitherKind.Witness<TestError>,
              Function<Function<Integer, String>, Function<Integer, String>>>
          mappedCompose = eitherMonad.map(composeMap, gKind);
      Kind<EitherKind.Witness<TestError>, Function<Integer, String>> ap1 =
          eitherMonad.ap(mappedCompose, fKind);
      var leftSide = eitherMonad.ap(ap1, value);

      // Right side: ap(gKind, ap(fKind, value))
      var innerAp = eitherMonad.ap(fKind, value);
      var rightSide = eitherMonad.ap(gKind, innerAp);

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));

      // Test with Left propagation
      var leftSideGLeft =
          eitherMonad.ap(eitherMonad.ap(eitherMonad.map(composeMap, gKindLeft), fKind), value);
      var rightSideGLeft = eitherMonad.ap(gKindLeft, eitherMonad.ap(fKind, value));
      assertThat(narrow(leftSideGLeft)).isEqualTo(narrow(rightSideGLeft));

      var leftSideFLeft =
          eitherMonad.ap(eitherMonad.ap(eitherMonad.map(composeMap, gKind), fKindLeft), value);
      var rightSideFLeft = eitherMonad.ap(gKind, eitherMonad.ap(fKindLeft, value));
      assertThat(narrow(leftSideFLeft)).isEqualTo(narrow(rightSideFLeft));

      Kind<EitherKind.Witness<TestError>, Integer> valueLeft = left("VAL_ERR");
      var leftSideValLeft =
          eitherMonad.ap(eitherMonad.ap(eitherMonad.map(composeMap, gKind), fKind), valueLeft);
      var rightSideValLeft = eitherMonad.ap(gKind, eitherMonad.ap(fKind, valueLeft));
      assertThat(narrow(leftSideValLeft)).isEqualTo(narrow(rightSideValLeft));
    }
  }

  @Nested
  @DisplayName("Monad Laws")
  class MonadLaws {

    int value = 5;
    Kind<EitherKind.Witness<TestError>, Integer> mValue = right(value);
    Kind<EitherKind.Witness<TestError>, Integer> mValueLeft = left("M_ERR");

    // Function a -> M b (Integer -> Kind<..., String>)
    Function<Integer, Kind<EitherKind.Witness<TestError>, String>> f = i -> right("v" + i);
    // Function b -> M c (String -> Kind<..., String>)
    Function<String, Kind<EitherKind.Witness<TestError>, String>> g = s -> right(s + "!");

    @Test
    @DisplayName("1. Left Identity: flatMap(of(a), f) == f(a)")
    void leftIdentity() {
      var ofValue = eitherMonad.of(value);
      var leftSide = eitherMonad.flatMap(f, ofValue);
      var rightSide = f.apply(value);

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));
    }

    @Test
    @DisplayName("2. Right Identity: flatMap(m, of) == m")
    void rightIdentity() {
      // Function a -> Kind<..., a>
      Function<Integer, Kind<EitherKind.Witness<TestError>, Integer>> ofFunc = eitherMonad::of;

      Kind<EitherKind.Witness<TestError>, Integer> leftSide = eitherMonad.flatMap(ofFunc, mValue);
      Kind<EitherKind.Witness<TestError>, Integer> leftSideLeft =
          eitherMonad.flatMap(ofFunc, mValueLeft);

      assertThat(narrow(leftSide)).isEqualTo(narrow(mValue));
      assertThat(narrow(leftSideLeft)).isEqualTo(narrow(mValueLeft)); // Check Left propagation
    }

    @Test
    @DisplayName("3. Associativity: flatMap(flatMap(m, f), g) == flatMap(m, a -> flatMap(f(a), g))")
    void associativity() {
      // Left Side: flatMap(flatMap(m, f), g)
      var innerFlatMap = eitherMonad.flatMap(f, mValue);
      var leftSide = eitherMonad.flatMap(g, innerFlatMap);

      // Right Side: flatMap(m, a -> flatMap(f(a), g))
      Function<Integer, Kind<EitherKind.Witness<TestError>, String>> rightSideFunc =
          a -> eitherMonad.flatMap(g, f.apply(a));
      var rightSide = eitherMonad.flatMap(rightSideFunc, mValue);

      assertThat(narrow(leftSide)).isEqualTo(narrow(rightSide));

      // Check Left propagation
      var innerFlatMapLeft = eitherMonad.flatMap(f, mValueLeft); // Should be Left
      var leftSideLeft = eitherMonad.flatMap(g, innerFlatMapLeft); // Should be Left

      var rightSideLeft = eitherMonad.flatMap(rightSideFunc, mValueLeft); // Should be Left

      assertThat(narrow(leftSideLeft)).isEqualTo(narrow(rightSideLeft));
    }
  }

  // --- mapN Tests ---

  @Nested
  @DisplayName("mapN tests")
  class MapNTests {

    Kind<EitherKind.Witness<TestError>, Integer> r1 = right(10);
    Kind<EitherKind.Witness<TestError>, String> r2 = right("hello");
    Kind<EitherKind.Witness<TestError>, Double> r3 = right(1.5);
    Kind<EitherKind.Witness<TestError>, Boolean> r4 = right(true);

    Kind<EitherKind.Witness<TestError>, Integer> l1 = left("L1");
    Kind<EitherKind.Witness<TestError>, String> l2 = left("L2");
    Kind<EitherKind.Witness<TestError>, Double> l3 = left("L3");
    Kind<EitherKind.Witness<TestError>, Boolean> l4 = left("L4");

    @Test
    void map3_allRight() {
      Function3<Integer, String, Double, String> f3 =
          (i, s, d) -> String.format("I:%d S:%s D:%.1f", i, s, d);

      var result = eitherMonad.map3(r1, r2, r3, f3);

      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo("I:10 S:hello D:1.5");
    }

    @Test
    void map3_firstLeft() {
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
      var result = eitherMonad.map3(l1, r2, r3, f3);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L1"));
    }

    @Test
    void map3_middleLeft() {
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
      var result = eitherMonad.map3(r1, l2, r3, f3);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L2"));
    }

    @Test
    void map3_lastLeft() {
      Function3<Integer, String, Double, String> f3 = (i, s, d) -> "Should not execute";
      var result = eitherMonad.map3(r1, r2, l3, f3);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L3"));
    }

    @Test
    void map4_allRight() {
      Function4<Integer, String, Double, Boolean, String> f4 =
          (i, s, d, b) -> String.format("I:%d S:%s D:%.1f B:%b", i, s, d, b);

      var result = eitherMonad.map4(r1, r2, r3, r4, f4);

      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo("I:10 S:hello D:1.5 B:true");
    }

    @Test
    void map4_firstLeft() {
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
      var result = eitherMonad.map4(l1, r2, r3, r4, f4);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L1"));
    }

    @Test
    void map4_middleLeft() {
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
      var result = eitherMonad.map4(r1, l2, r3, r4, f4);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L2"));
    }

    @Test
    void map4_thirdLeft() { // Test specifically hitting the check for fc
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";

      // Inputs: Right, Right, Left, Right (fd doesn't matter here)
      var result =
          eitherMonad.map4(
              r1, // Right(10)
              r2, // Right("hello")
              l3, // Left("L3") <-- This should cause the short-circuit
              r4, // Right(true)
              f4);

      // Assert that the result is the Left from the third argument (l3)
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L3"));
    }

    @Test
    void map4_lastLeft() {
      Function4<Integer, String, Double, Boolean, String> f4 = (i, s, d, b) -> "Should not execute";
      var result = eitherMonad.map4(r1, r2, r3, l4, f4);
      assertThat(narrow(result).isLeft()).isTrue();
      assertThat(narrow(result).getLeft()).isEqualTo(new TestError("L4"));
    }
  }

  @Nested
  @DisplayName("MonadError tests")
  class MonadErrorTests {

    Kind<EitherKind.Witness<TestError>, Integer> rightVal = right(100);
    Kind<EitherKind.Witness<TestError>, Integer> leftVal = left("E404");
    TestError raisedError = new TestError("E500");
    Kind<EitherKind.Witness<TestError>, Integer> raisedErrorKind =
        eitherMonad.raiseError(raisedError);

    @Test
    void raiseError_shouldCreateLeft() {
      Either<TestError, Integer> either = narrow(raisedErrorKind);
      assertThat(either.isLeft()).isTrue();
      assertThat(either.getLeft()).isEqualTo(raisedError);
    }

    @Test
    void handleErrorWith_shouldHandleLeft() {
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler =
          err -> right(Integer.parseInt(err.code().substring(1))); // "E404" -> Right(404)

      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.handleErrorWith(leftVal, handler);

      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo(404);
    }

    @Test
    void handleErrorWith_shouldIgnoreRight() {
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> handler =
          err -> right(-1); // Should not be called

      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.handleErrorWith(rightVal, handler);

      assertThat(result).isSameAs(rightVal); // Should return original Kind instance
      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo(100);
    }

    @Test
    void handleError_shouldHandleLeftWithPureValue() {
      Function<TestError, Integer> handler = err -> -99;

      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.handleError(leftVal, handler);

      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo(-99);
    }

    @Test
    void handleError_shouldIgnoreRight() {
      Function<TestError, Integer> handler = err -> -1; // Should not be called

      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.handleError(rightVal, handler);

      assertThat(result).isSameAs(rightVal); // Should return original Kind instance
      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo(100);
    }

    @Test
    void recoverWith_shouldReplaceLeftWithFallbackKind() {
      Kind<EitherKind.Witness<TestError>, Integer> fallback = right(0);
      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.recoverWith(leftVal, fallback);
      assertThat(result).isSameAs(fallback);
    }

    @Test
    void recoverWith_shouldIgnoreRight() {
      Kind<EitherKind.Witness<TestError>, Integer> fallback = right(0);
      Kind<EitherKind.Witness<TestError>, Integer> result =
          eitherMonad.recoverWith(rightVal, fallback);
      assertThat(result).isSameAs(rightVal);
    }

    @Test
    void recover_shouldReplaceLeftWithOfValue() {
      Kind<EitherKind.Witness<TestError>, Integer> result = eitherMonad.recover(leftVal, 0);
      assertThat(narrow(result).isRight()).isTrue();
      assertThat(narrow(result).getRight()).isEqualTo(0);
    }

    @Test
    void recover_shouldIgnoreRight() {
      Kind<EitherKind.Witness<TestError>, Integer> result = eitherMonad.recover(rightVal, 0);
      assertThat(result).isSameAs(rightVal);
    }

    @Test
    @DisplayName("handleErrorWith should propagate exception thrown by handler function")
    void handleErrorWith_shouldPropagateHandlerException() {
      Kind<EitherKind.Witness<TestError>, Integer> leftVal = left("E1");
      RuntimeException handlerException = new RuntimeException("Handler failed!");
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> throwingHandler =
          err -> {
            throw handlerException;
          };

      // Exception happens during handler.apply(leftValue) within the fold
      assertThatThrownBy(() -> eitherMonad.handleErrorWith(leftVal, throwingHandler))
          .isInstanceOf(RuntimeException.class)
          .isSameAs(handlerException);
    }

    @Test
    @DisplayName(
        "handleErrorWith should result in Exception when unwrapping null Kind from handler")
    void handleErrorWith_shouldThrowWhenUnwrappingNullHandlerResult() {
      Kind<EitherKind.Witness<TestError>, Integer> leftVal =
          left("E1"); // Assuming left() helper exists
      // Handler function explicitly returns null Kind
      Function<TestError, Kind<EitherKind.Witness<TestError>, Integer>> nullReturningHandler =
          err -> null;

      // Call the method under test
      Kind<EitherKind.Witness<TestError>, Integer> resultKind =
          eitherMonad.handleErrorWith(leftVal, nullReturningHandler);

      // Assert that the resultKind itself is null (because the handler returned null)
      assertThat(resultKind).isNull();

      // Assert that attempting to unwrap this null result throws KindUnwrapException
      assertThatThrownBy(() -> EITHER.narrow(resultKind)) // Pass the null resultKind to unwrap
          .isInstanceOf(KindUnwrapException.class)
          .hasMessageContaining(EitherKindHelper.INVALID_KIND_NULL_MSG);
    }
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.assertj.core.api.Assertions.*;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;
import static org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.effect.FreePath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.free.FreeKind;
import org.higherkindedj.hkt.free.FreeKindHelper;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.list.ListTraverse;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for FreePath integration with {@link ForPath} comprehensions.
 *
 * <p>Uses {@code MaybeKind.Witness} as the effect functor F, with an identity natural
 * transformation for interpretation.
 */
@DisplayName("ForPath FreePath Comprehension Tests")
class ForPathFreePathTest {

  private static final MaybeMonad FUNCTOR = MaybeMonad.INSTANCE;
  private static final Monad<MaybeKind.Witness> MONAD = MaybeMonad.INSTANCE;
  private static final Natural<MaybeKind.Witness, MaybeKind.Witness> ID_NAT = Natural.identity();

  // Helpers for creating FreePath values backed by Maybe
  private static <A> FreePath<MaybeKind.Witness, A> just(A value) {
    return FreePath.liftF(MAYBE.just(value), FUNCTOR);
  }

  private static <A> FreePath<MaybeKind.Witness, A> nothing() {
    return FreePath.liftF(MAYBE.nothing(), FUNCTOR);
  }

  // Interpret a FreePath to extract the Maybe result
  private static <A> Maybe<A> interpret(FreePath<MaybeKind.Witness, A> path) {
    GenericPath<MaybeKind.Witness, A> result = path.foldMap(ID_NAT, MONAD);
    return MAYBE.narrow(result.runKind());
  }

  // Test data
  record User(String name, Address address) {}

  record Address(String city, String country) {}

  static final Lens<User, Address> addressLens =
      Lens.of(User::address, (u, a) -> new User(u.name(), a));

  static final FocusPath<User, Address> addressFocus = FocusPath.of(addressLens);

  @Nested
  @DisplayName("Steps1 - Single Binding")
  class Steps1Tests {

    @Test
    @DisplayName("yield() on Steps1 maps the single value")
    void yieldOnSteps1() {
      FreePath<MaybeKind.Witness, Integer> result = ForPath.from(just(10)).yield(a -> a * 2);

      assertThat(interpret(result)).isEqualTo(Maybe.just(20));
    }

    @Test
    @DisplayName("yield() on Steps1 with nothing short-circuits")
    void yieldOnSteps1Nothing() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(ForPathFreePathTest.<Integer>nothing()).yield(a -> a * 2);

      assertThat(interpret(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("from() chains to Steps2")
    void fromChainsToSteps2() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(5)).from(a -> just(a * 2)).yield((a, b) -> a + b);

      assertThat(interpret(result)).isEqualTo(Maybe.just(15));
    }

    @Test
    @DisplayName("let() adds a pure computation binding")
    void letAddsPureBinding() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ",b=" + b);

      assertThat(interpret(result)).isEqualTo(Maybe.just("a=10,b=20"));
    }

    @Test
    @DisplayName("focus() extracts through a FocusPath")
    void focusExtractsThroughOptic() {
      User user = new User("Alice", new Address("NYC", "USA"));

      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertThat(interpret(result)).isEqualTo(Maybe.just("NYC"));
    }
  }

  @Nested
  @DisplayName("Steps2-3 - Multiple Bindings")
  class MultipleBindingTests {

    @Test
    @DisplayName("three generators chain correctly")
    void threeGenerators() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1)).from(a -> just(2)).from(t -> just(3)).yield((a, b, c) -> a + b + c);

      assertThat(interpret(result)).isEqualTo(Maybe.just(6));
    }

    @Test
    @DisplayName("nothing in middle generator short-circuits")
    void nothingInMiddleShortCircuits() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .<Integer>from(a -> nothing())
              .from(t -> just(3))
              .yield((a, b, c) -> a + b + c);

      assertThat(interpret(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("from and let can be mixed")
    void fromAndLetMixed() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(5))
              .let(a -> a * 10)
              .from(t -> just("hello"))
              .yield((a, b, c) -> c + ":" + a + "+" + b);

      assertThat(interpret(result)).isEqualTo(Maybe.just("hello:5+50"));
    }

    @Test
    @DisplayName("dependent bindings use previous values")
    void dependentBindings() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(3))
              .from(a -> just(a + 7))
              .from(t -> just(t._1() * t._2()))
              .yield((a, b, c) -> c);

      // a=3, b=10, c=3*10=30
      assertThat(interpret(result)).isEqualTo(Maybe.just(30));
    }
  }

  @Nested
  @DisplayName("Yield with Tuple Function")
  class YieldTupleFunctionTests {

    @Test
    @DisplayName("yield with tuple function on Steps2")
    void yieldWithTupleSteps2() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(5)).from(a -> just(10)).yield(t -> "sum=" + (t._1() + t._2()));

      assertThat(interpret(result)).isEqualTo(Maybe.just("sum=15"));
    }

    @Test
    @DisplayName("yield with tuple function on Steps3")
    void yieldWithTupleSteps3() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .from(a -> just(2))
              .from(t -> just(3))
              .yield(t -> t._1() + t._2() + t._3());

      assertThat(interpret(result)).isEqualTo(Maybe.just(6));
    }
  }

  @Nested
  @DisplayName("Null Safety")
  class NullSafetyTests {

    @Test
    @DisplayName("ForPath.from(null) throws NullPointerException")
    void fromNullThrows() {
      assertThatNullPointerException()
          .isThrownBy(() -> ForPath.from((FreePath<MaybeKind.Witness, ?>) null))
          .withMessageContaining("source must not be null");
    }

    @Test
    @DisplayName("yield with null result throws NullPointerException")
    void yieldNullResultThrows() {
      FreePath<MaybeKind.Witness, Object> path =
          ForPath.from(just(5)).from(a -> just(10)).yield((a, b) -> null);

      // Null yields are caught by the generated code's Objects.requireNonNull
      assertThatNullPointerException().isThrownBy(() -> interpret(path));
    }
  }

  @Nested
  @DisplayName("Extended Arity (4+ bindings)")
  class ExtendedArityTests {

    @Test
    @DisplayName("four bindings chain correctly")
    void fourBindings() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .from(a -> just(2))
              .from(t -> just(3))
              .from(t -> just(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertThat(interpret(result)).isEqualTo(Maybe.just(10));
    }

    @Test
    @DisplayName("five bindings with let and from mixed")
    void fiveBindingsMixed() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(1))
              .let(a -> a * 2)
              .from(t -> just("x"))
              .let(t -> t._3().toUpperCase())
              .from(t -> just(100))
              .yield((a, b, c, d, e) -> a + "," + b + "," + c + "," + d + "," + e);

      assertThat(interpret(result)).isEqualTo(Maybe.just("1,2,x,X,100"));
    }
  }

  @Nested
  @DisplayName("Top-Level Par Entry Points")
  class TopLevelParTests {

    @Test
    @DisplayName("par(a, b) combines two independent FreePaths")
    void parTwoTopLevel() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.par(just(10), just(20)).yield((a, b) -> a + b);

      assertThat(interpret(result)).isEqualTo(Maybe.just(30));
    }

    @Test
    @DisplayName("par(a, b, c) combines three independent FreePaths")
    void parThreeTopLevel() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.par(just(10), just(20), just(30)).yield((a, b, c) -> a + b + c);

      assertThat(interpret(result)).isEqualTo(Maybe.just(60));
    }

    @Test
    @DisplayName("top-level par short-circuits on nothing")
    void parTopLevelShortCircuits() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.par(just(10), ForPathFreePathTest.<Integer>nothing()).yield((a, b) -> a + b);

      assertThat(interpret(result).isNothing()).isTrue();
    }
  }

  @Nested
  @DisplayName("Par - Parallel Bindings on Steps")
  class ParTests {

    @Test
    @DisplayName("par with two independent computations")
    void parTwoComputations() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .from(a -> just(2))
              .<Integer, Integer>par(t -> just(t._1() * 10), t -> just(t._2() * 10))
              .yield((a, b, c, d) -> a + b + c + d);

      // a=1, b=2, c=10, d=20
      assertThat(interpret(result)).isEqualTo(Maybe.just(33));
    }

    @Test
    @DisplayName("par short-circuits on nothing")
    void parShortCircuitsOnNothing() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .from(a -> just(2))
              .<Integer, Integer>par(t -> just(t._1() * 10), t -> nothing())
              .yield((a, b, c, d) -> a + b + c + d);

      assertThat(interpret(result).isNothing()).isTrue();
    }

    @Test
    @DisplayName("par with three independent computations")
    void parThreeComputations() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(1))
              .from(a -> just(2))
              .<Integer, Integer, Integer>par(t -> just(10), t -> just(20), t -> just(30))
              .yield((a, b, c, d, e) -> a + b + c + d + e);

      assertThat(interpret(result)).isEqualTo(Maybe.just(63));
    }
  }

  @Nested
  @DisplayName("Integration with Free Monad Features")
  class IntegrationTests {

    @Test
    @DisplayName("FreePath via() chains work within ForPath from()")
    void viaChains() {
      FreePath<MaybeKind.Witness, Integer> result =
          ForPath.from(just(5)).from(a -> just(a).via(x -> just(x * 2))).yield((a, b) -> a + b);

      // a=5, b=5*2=10
      assertThat(interpret(result)).isEqualTo(Maybe.just(15));
    }

    @Test
    @DisplayName("FreePath map works within ForPath from()")
    void mapChains() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(5)).from(a -> just(a).map(x -> x * 3)).yield((a, b) -> a + ":" + b);

      assertThat(interpret(result)).isEqualTo(Maybe.just("5:15"));
    }

    @Test
    @DisplayName("FreePath pure creates values within ForPath")
    void pureInFrom() {
      FreePath<MaybeKind.Witness, String> result =
          ForPath.from(just(42))
              .from(a -> FreePath.pure("answer=" + a, FUNCTOR))
              .yield((a, b) -> b);

      assertThat(interpret(result)).isEqualTo(Maybe.just("answer=42"));
    }
  }

  @Nested
  @DisplayName("Traverse, Sequence, FlatTraverse")
  class TraverseTests {

    private final ListTraverse listTraverse = ListTraverse.INSTANCE;

    @Test
    @DisplayName("traverse: should traverse a list within FreePath")
    void traverseListInFree() {
      FreePath<MaybeKind.Witness, List<Integer>> result =
          ForPath.from(just(Arrays.asList(1, 2, 3)))
              .traverse(
                  listTraverse,
                  list -> LIST.widen(list),
                  (Integer i) -> FreePath.liftF(MAYBE.just(i * 2), FUNCTOR).runKind())
              .yield((original, traversed) -> LIST.narrow(traversed));

      assertThat(interpret(result)).isEqualTo(Maybe.just(List.of(2, 4, 6)));
    }

    @Test
    @DisplayName("sequence: should sequence a list of Free values within FreePath")
    void sequenceListInFree() {
      List<Kind<FreeKind.Witness<MaybeKind.Witness>, Integer>> listOfFrees =
          Arrays.asList(just(10).runKind(), just(20).runKind(), just(30).runKind());
      Kind<ListKind.Witness, Kind<FreeKind.Witness<MaybeKind.Witness>, Integer>> kindList =
          LIST.widen(listOfFrees);

      FreePath<MaybeKind.Witness, List<Integer>> result =
          ForPath.from(just(kindList))
              .sequence(listTraverse, Function.identity())
              .yield((original, sequenced) -> LIST.narrow(sequenced));

      assertThat(interpret(result)).isEqualTo(Maybe.just(List.of(10, 20, 30)));
    }

    @Test
    @DisplayName("flatTraverse: should traverse and flatten within FreePath")
    void flatTraverseListInFree() {
      FreePath<MaybeKind.Witness, List<Integer>> result =
          ForPath.from(just(Arrays.asList(1, 2, 3)))
              .flatTraverse(
                  listTraverse,
                  ListMonad.INSTANCE,
                  list -> LIST.widen(list),
                  (Integer i) ->
                      FreePath.<MaybeKind.Witness, Kind<ListKind.Witness, Integer>>liftF(
                              MAYBE.just(LIST.widen(Arrays.asList(i, i * 10))), FUNCTOR)
                          .runKind())
              .yield((original, traversed) -> LIST.narrow(traversed));

      assertThat(interpret(result)).isEqualTo(Maybe.just(List.of(1, 10, 2, 20, 3, 30)));
    }
  }

  @Nested
  @DisplayName("FreePath.runKind()")
  class RunKindTests {

    @Test
    @DisplayName("runKind() returns widened Kind at FreeKind.Witness level")
    void runKindReturnsWidenedKind() {
      FreePath<MaybeKind.Witness, Integer> path = just(42);
      Kind<FreeKind.Witness<MaybeKind.Witness>, Integer> kind = path.runKind();

      // Narrow back and verify the round-trip
      var free = FreeKindHelper.FREE.<MaybeKind.Witness, Integer>narrow(kind);
      assertThat(free).isEqualTo(path.toFree());
    }

    @Test
    @DisplayName("runKind() on pure FreePath returns widened pure Free")
    void runKindOnPure() {
      FreePath<MaybeKind.Witness, String> path = FreePath.pure("hello", FUNCTOR);
      Kind<FreeKind.Witness<MaybeKind.Witness>, String> kind = path.runKind();

      var free = FreeKindHelper.FREE.<MaybeKind.Witness, String>narrow(kind);
      // Interpret and verify the value
      Maybe<String> result = MAYBE.narrow(free.foldMap(ID_NAT, MONAD));
      assertThat(result).isEqualTo(Maybe.just("hello"));
    }
  }
}

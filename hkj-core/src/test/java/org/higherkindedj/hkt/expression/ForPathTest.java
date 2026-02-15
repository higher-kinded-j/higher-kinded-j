// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.expression;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.GenericPath;
import org.higherkindedj.hkt.effect.IOPath;
import org.higherkindedj.hkt.effect.IdPath;
import org.higherkindedj.hkt.effect.MaybePath;
import org.higherkindedj.hkt.effect.NonDetPath;
import org.higherkindedj.hkt.effect.OptionalPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.effect.TryPath;
import org.higherkindedj.hkt.effect.VTaskPath;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for {@link ForPath} - the Path-native for-comprehension builder.
 *
 * <p>These tests verify that ForPath correctly bridges the For comprehension system with Effect
 * Path types, allowing fluent composition while returning Path types directly.
 */
class ForPathTest {

  // Test data classes
  record User(String name, Address address, String email) {
    User(String name, Address address) {
      this(name, address, null);
    }
  }

  record Address(String city, String country) {}

  // Lenses for testing focus()
  static final Lens<User, Address> addressLens =
      Lens.of(User::address, (u, a) -> new User(u.name(), a, u.email()));

  static final Lens<Address, String> cityLens =
      Lens.of(Address::city, (a, c) -> new Address(c, a.country()));

  // FocusPath for testing
  static final FocusPath<User, Address> addressFocus = FocusPath.of(addressLens);

  static final FocusPath<Address, String> cityFocus = FocusPath.of(cityLens);

  // AffinePath for testing match()
  static final AffinePath<User, String> emailAffine =
      AffinePath.of(
          Affine.of(
              u -> Optional.ofNullable(u.email()), (u, e) -> new User(u.name(), u.address(), e)));

  @Nested
  @DisplayName("MaybePath Comprehension")
  class MaybePathTests {

    @Test
    @DisplayName("should chain generators with from()")
    void chainsGenerators() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(5)).from(a -> Path.just(a * 2)).yield((a, b) -> a + b);

      assertEquals(Maybe.just(15), result.run()); // 5 + 10
    }

    @Test
    @DisplayName("should short-circuit on Nothing")
    void shortCircuitsOnNothing() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(5)).<Integer>from(a -> Path.nothing()).yield((a, b) -> a + b);

      assertTrue(result.run().isNothing());
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      MaybePath<String> result =
          ForPath.from(Path.just(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertEquals(Maybe.just("a=10, b=20"), result.run());
    }

    @Test
    @DisplayName("should support when() for filtering")
    void supportsWhen() {
      MaybePath<Integer> result = ForPath.from(Path.just(5)).when(a -> a > 10).yield(a -> a);

      assertTrue(result.run().isNothing());

      MaybePath<Integer> result2 = ForPath.from(Path.just(15)).when(a -> a > 10).yield(a -> a);

      assertEquals(Maybe.just(15), result2.run());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      MaybePath<String> result =
          ForPath.from(Path.just(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertEquals(Maybe.just("NYC"), result.run());
    }

    @Test
    @DisplayName("should support match() with AffinePath")
    void supportsMatch() {
      User userWithEmail = new User("Alice", new Address("NYC", "USA"), "alice@example.com");
      User userWithoutEmail = new User("Bob", new Address("LA", "USA"));

      MaybePath<String> result1 =
          ForPath.from(Path.just(userWithEmail)).match(emailAffine).yield((user, email) -> email);

      assertEquals(Maybe.just("alice@example.com"), result1.run());

      MaybePath<String> result2 =
          ForPath.from(Path.just(userWithoutEmail))
              .match(emailAffine)
              .yield((user, email) -> email);

      assertTrue(result2.run().isNothing());
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(a + 1))
              .from(t -> Path.just(t._2() + 1))
              .yield((a, b, c) -> a + b + c);

      assertEquals(Maybe.just(6), result.run()); // 1 + 2 + 3
    }

    @Test
    @DisplayName("should support four generators")
    void supportsFourGenerators() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(Maybe.just(10), result.run());
    }

    @Test
    @DisplayName("should support five generators")
    void supportsFiveGenerators() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .yield((a, b, c, d, e) -> a + b + c + d + e);

      assertEquals(Maybe.just(15), result.run());
    }
  }

  @Nested
  @DisplayName("OptionalPath Comprehension")
  class OptionalPathTests {

    @Test
    @DisplayName("should chain generators")
    void chainsGenerators() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(5)).from(a -> Path.present(a * 2)).yield((a, b) -> a + b);

      assertEquals(Optional.of(15), result.run());
    }

    @Test
    @DisplayName("should short-circuit on empty")
    void shortCircuitsOnEmpty() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(5)).<Integer>from(a -> Path.absent()).yield((a, b) -> a + b);

      assertTrue(result.run().isEmpty());
    }

    @Test
    @DisplayName("should support when() for filtering")
    void supportsWhen() {
      OptionalPath<Integer> result = ForPath.from(Path.present(5)).when(a -> a > 10).yield(a -> a);

      assertTrue(result.run().isEmpty());

      // Test success case where predicate passes
      OptionalPath<Integer> result2 =
          ForPath.from(Path.present(15)).when(a -> a > 10).yield(a -> a);

      assertEquals(Optional.of(15), result2.run());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      OptionalPath<String> result =
          ForPath.from(Path.present(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertEquals(Optional.of("NYC"), result.run());
    }

    @Test
    @DisplayName("should support match() with AffinePath")
    void supportsMatch() {
      User userWithEmail = new User("Alice", new Address("NYC", "USA"), "alice@example.com");

      OptionalPath<String> result =
          ForPath.from(Path.present(userWithEmail))
              .match(emailAffine)
              .yield((user, email) -> email);

      assertEquals(Optional.of("alice@example.com"), result.run());
    }
  }

  @Nested
  @DisplayName("EitherPath Comprehension")
  class EitherPathTests {

    @Test
    @DisplayName("should chain generators on Right")
    void chainsGeneratorsOnRight() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(5))
              .from(a -> Path.<String, Integer>right(a * 2))
              .yield((a, b) -> a + b);

      assertTrue(result.run().isRight());
      assertEquals(15, result.run().getRight());
    }

    @Test
    @DisplayName("should short-circuit on Left")
    void shortCircuitsOnLeft() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(5))
              .from(a -> Path.<String, Integer>left("Error!"))
              .yield((a, b) -> a + b);

      assertTrue(result.run().isLeft());
      assertEquals("Error!", result.run().getLeft());
    }

    @Test
    @DisplayName("should propagate first Left")
    void propagatesFirstLeft() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>left("First error"))
              .from(a -> Path.<String, Integer>right(10))
              .yield((a, b) -> a + b);

      assertTrue(result.run().isLeft());
      assertEquals("First error", result.run().getLeft());
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      EitherPath<String, String> result =
          ForPath.from(Path.<String, Integer>right(10))
              .let(a -> a * 2)
              .yield((a, b) -> "a=" + a + ", b=" + b);

      assertTrue(result.run().isRight());
      assertEquals("a=10, b=20", result.run().getRight());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      EitherPath<String, String> result =
          ForPath.from(Path.<String, User>right(user))
              .focus(addressFocus)
              .yield((u, addr) -> addr.city());

      assertTrue(result.run().isRight());
      assertEquals("NYC", result.run().getRight());
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .yield((a, b, c) -> a + b + c);

      assertTrue(result.run().isRight());
      assertEquals(6, result.run().getRight());
    }
  }

  @Nested
  @DisplayName("TryPath Comprehension")
  class TryPathTests {

    @Test
    @DisplayName("should chain generators on Success")
    void chainsGeneratorsOnSuccess() {
      TryPath<Integer> result =
          ForPath.from(Path.success(5)).from(a -> Path.success(a * 2)).yield((a, b) -> a + b);

      assertTrue(result.run().isSuccess());
      assertEquals(15, result.run().orElse(null));
    }

    @Test
    @DisplayName("should short-circuit on Failure")
    void shortCircuitsOnFailure() {
      RuntimeException error = new RuntimeException("Test error");

      TryPath<Integer> result =
          ForPath.from(Path.success(5))
              .from(a -> Path.<Integer>failure(error))
              .yield((a, b) -> a + b);

      assertTrue(result.run().isFailure());
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      TryPath<String> result =
          ForPath.from(Path.success(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertTrue(result.run().isSuccess());
      assertEquals("a=10, b=20", result.run().orElse(null));
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      TryPath<String> result =
          ForPath.from(Path.success(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertTrue(result.run().isSuccess());
      assertEquals("NYC", result.run().orElse(null));
    }
  }

  @Nested
  @DisplayName("IOPath Comprehension")
  class IOPathTests {

    @Test
    @DisplayName("should chain generators")
    void chainsGenerators() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(5)).from(a -> Path.ioPure(a * 2)).yield((a, b) -> a + b);

      assertEquals(15, result.unsafeRun());
    }

    @Test
    @DisplayName("should defer computations")
    void defersComputations() {
      int[] counter = {0};

      IOPath<Integer> result =
          ForPath.from(
                  Path.io(
                      () -> {
                        counter[0]++;
                        return 5;
                      }))
              .from(
                  a ->
                      Path.io(
                          () -> {
                            counter[0]++;
                            return a * 2;
                          }))
              .yield((a, b) -> a + b);

      // Nothing executed yet
      assertEquals(0, counter[0]);

      // Execute
      assertEquals(15, result.unsafeRun());
      assertEquals(2, counter[0]);
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      IOPath<String> result =
          ForPath.from(Path.ioPure(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertEquals("a=10, b=20", result.unsafeRun());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      IOPath<String> result =
          ForPath.from(Path.ioPure(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertEquals("NYC", result.unsafeRun());
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .yield((a, b, c) -> a + b + c);

      assertEquals(6, result.unsafeRun());
    }
  }

  @Nested
  @DisplayName("IdPath Comprehension")
  class IdPathTests {

    @Test
    @DisplayName("should chain generators")
    void chainsGenerators() {
      IdPath<Integer> result =
          ForPath.from(Path.id(5)).from(a -> Path.id(a * 2)).yield((a, b) -> a + b);

      assertEquals(15, result.run().value());
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      IdPath<String> result =
          ForPath.from(Path.id(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertEquals("a=10, b=20", result.run().value());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      IdPath<String> result =
          ForPath.from(Path.id(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertEquals("NYC", result.run().value());
    }
  }

  @Nested
  @DisplayName("NonDetPath Comprehension (Cartesian Product)")
  class NonDetPathTests {

    @Test
    @DisplayName("should produce Cartesian product")
    void producesCartesianProduct() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list("a", "b"))
              .yield((num, letter) -> num + letter);

      assertEquals(List.of("1a", "1b", "2a", "2b"), result.run());
    }

    @Test
    @DisplayName("should support when() for filtering")
    void supportsWhen() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1, 2, 3, 4, 5)).when(a -> a % 2 == 0).yield(a -> a);

      assertEquals(List.of(2, 4), result.run());
    }

    @Test
    @DisplayName("should support filtering in multi-generator comprehensions")
    void supportsFilteringMultiGenerator() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2, 3))
              .from(a -> Path.list(10, 20, 30))
              .when(t -> (t._1() + t._2()) > 20)
              .yield((a, b) -> a + "+" + b);

      assertEquals(List.of("1+20", "1+30", "2+20", "2+30", "3+20", "3+30"), result.run());
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2, 3)).let(a -> a * 10).yield((a, b) -> a + "->" + b);

      assertEquals(List.of("1->10", "2->20", "3->30"), result.run());
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list("a", "b"))
              .from(t -> Path.list(true, false))
              .yield((num, letter, bool) -> num + letter + (bool ? "!" : "?"));

      assertEquals(List.of("1a!", "1a?", "1b!", "1b?", "2a!", "2a?", "2b!", "2b?"), result.run());
    }
  }

  @Nested
  @DisplayName("Complex Scenarios")
  class ComplexScenarios {

    @Test
    @DisplayName("should handle nested path operations")
    void handlesNestedOperations() {
      // Simulate a multi-step validation flow
      MaybePath<String> result =
          ForPath.from(Path.just("alice"))
              .from(
                  name ->
                      name.length() > 3 ? Path.just(name.toUpperCase()) : Path.<String>nothing())
              .let(t -> t._2().length())
              .when(t -> t._3() < 10)
              .yield((name, upper, len) -> upper + " (" + len + " chars)");

      assertEquals(Maybe.just("ALICE (5 chars)"), result.run());
    }

    @Test
    @DisplayName("should compose focus paths")
    void composesFocusPaths() {
      User user = new User("Alice", new Address("NYC", "USA"));

      // Compose two focus paths
      FocusPath<User, String> userCityFocus = addressFocus.via(cityFocus);

      MaybePath<String> result =
          ForPath.from(Path.just(user)).focus(userCityFocus).yield((u, city) -> city);

      assertEquals(Maybe.just("NYC"), result.run());
    }

    @Test
    @DisplayName("should work with real-world validation scenario")
    void worksWithValidationScenario() {
      // Simulating: validate user -> get address -> validate address -> format result

      Function<User, MaybePath<String>> formatUser =
          user ->
              ForPath.from(Path.just(user))
                  .when(u -> u.name() != null && !u.name().isEmpty())
                  .focus(addressFocus)
                  .when(t -> t._2().city() != null)
                  .yield((u, addr) -> u.name() + " from " + addr.city());

      User validUser = new User("Alice", new Address("NYC", "USA"));
      User invalidUser = new User("", new Address("NYC", "USA"));
      User noCity = new User("Bob", new Address(null, "USA"));

      assertEquals(Maybe.just("Alice from NYC"), formatUser.apply(validUser).run());
      assertTrue(formatUser.apply(invalidUser).run().isNothing());
      assertTrue(formatUser.apply(noCity).run().isNothing());
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("should throw on null MaybePath source")
    void throwsOnNullMaybePathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((MaybePath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null OptionalPath source")
    void throwsOnNullOptionalPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((OptionalPath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null EitherPath source")
    void throwsOnNullEitherPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((EitherPath<Object, Object>) null));
    }

    @Test
    @DisplayName("should throw on null TryPath source")
    void throwsOnNullTryPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((TryPath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null IOPath source")
    void throwsOnNullIOPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((IOPath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null IdPath source")
    void throwsOnNullIdPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((IdPath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null NonDetPath source")
    void throwsOnNullNonDetPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((NonDetPath<Object>) null));
    }

    @Test
    @DisplayName("should throw on null GenericPath source")
    void throwsOnNullGenericPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from((GenericPath<MaybeKind.Witness, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in MaybePath")
    void throwsOnNullFocusPath() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.just(1)).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in OptionalPath")
    void throwsOnNullFocusPathOptional() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.present(1)).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in EitherPath")
    void throwsOnNullFocusPathEither() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () ->
                  ForPath.from(Path.<String, Integer>right(1))
                      .focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in TryPath")
    void throwsOnNullFocusPathTry() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.success(1)).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in IOPath")
    void throwsOnNullFocusPathIO() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.ioPure(1)).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in IdPath")
    void throwsOnNullFocusPathId() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.id(1)).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null focusPath in GenericPath")
    void throwsOnNullFocusPathGeneric() {
      GenericPath<MaybeKind.Witness, Integer> genericPath =
          GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE);
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(genericPath).focus((FocusPath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null affinePath in MaybePath match")
    void throwsOnNullAffinePath() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.just(1)).match((AffinePath<Integer, Object>) null));
    }

    @Test
    @DisplayName("should throw on null affinePath in OptionalPath match")
    void throwsOnNullAffinePathOptional() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class,
              () -> ForPath.from(Path.present(1)).match((AffinePath<Integer, Object>) null));
    }
  }

  // ========================================================================
  // Additional MaybePath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("MaybePath Steps2 Additional Tests")
  class MaybePathSteps2Tests {

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(5))
              .from(a -> Path.just(a * 2))
              .let(t -> t._1() + t._2())
              .yield((a, b, c) -> c);

      assertEquals(Maybe.just(15), result.run());
    }

    @Test
    @DisplayName("should support focus() in Steps2 with extractor function")
    void supportsFocusInSteps2() {
      User user = new User("Alice", new Address("NYC", "USA"));

      MaybePath<String> result =
          ForPath.from(Path.just(user))
              .let(u -> u.address())
              .focus(t -> t._2().city())
              .yield((u, addr, city) -> city);

      assertEquals(Maybe.just("NYC"), result.run());
    }

    @Test
    @DisplayName("should support match() in Steps2 with Optional extractor")
    void supportsMatchInSteps2() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(10))
              .let(a -> a * 2)
              .match(t -> t._1() > 5 ? Optional.of(t._2() * 2) : Optional.empty())
              .yield((a, b, c) -> c);

      assertEquals(Maybe.just(40), result.run());

      // Test short-circuit case
      MaybePath<Integer> result2 =
          ForPath.from(Path.just(3))
              .let(a -> a * 2)
              .match(t -> t._1() > 5 ? Optional.of(t._2() * 2) : Optional.empty())
              .yield((a, b, c) -> c);

      assertTrue(result2.run().isNothing());
    }

    @Test
    @DisplayName("should support yield with tuple function")
    void supportsYieldWithTupleFunction() {
      MaybePath<String> result =
          ForPath.from(Path.just(5))
              .from(a -> Path.just(a * 2))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertEquals(Maybe.just("sum=15"), result.run());
    }
  }

  @Nested
  @DisplayName("MaybePath Steps3 Additional Tests")
  class MaybePathSteps3Tests {

    @Test
    @DisplayName("should support let() in Steps3")
    void supportsLetInSteps3() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .let(t -> t._1() + t._2() + t._3())
              .yield((a, b, c, sum) -> sum);

      assertEquals(Maybe.just(6), result.run());
    }

    @Test
    @DisplayName("should support when() in Steps3")
    void supportsWhenInSteps3() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .when(t -> t._1() + t._2() + t._3() > 10)
              .yield((a, b, c) -> a + b + c);

      assertTrue(result.run().isNothing());

      MaybePath<Integer> result2 =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .when(t -> t._1() + t._2() + t._3() > 0)
              .yield((a, b, c) -> a + b + c);

      assertEquals(Maybe.just(6), result2.run());
    }

    @Test
    @DisplayName("should support yield with tuple function")
    void supportsYieldWithTupleFunction() {
      MaybePath<String> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertEquals(Maybe.just("sum=6"), result.run());
    }
  }

  @Nested
  @DisplayName("MaybePath Steps4 Additional Tests")
  class MaybePathSteps4Tests {

    @Test
    @DisplayName("should support let() in Steps4")
    void supportsLetInSteps4() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .let(t -> t._1() + t._2() + t._3() + t._4())
              .yield((a, b, c, d, sum) -> sum);

      assertEquals(Maybe.just(10), result.run());
    }

    @Test
    @DisplayName("should support when() in Steps4")
    void supportsWhenInSteps4() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .when(t -> t._4() > 10)
              .yield((a, b, c, d) -> a + b + c + d);

      assertTrue(result.run().isNothing());

      MaybePath<Integer> result2 =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .when(t -> t._4() < 10)
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(Maybe.just(10), result2.run());
    }

    @Test
    @DisplayName("should support yield with tuple function")
    void supportsYieldWithTupleFunction() {
      MaybePath<String> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3() + t._4()));

      assertEquals(Maybe.just("sum=10"), result.run());
    }
  }

  @Nested
  @DisplayName("MaybePath Steps5 Additional Tests")
  class MaybePathSteps5Tests {

    @Test
    @DisplayName("should support when() in Steps5")
    void supportsWhenInSteps5() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .when(t -> t._5() > 10)
              .yield((a, b, c, d, e) -> a + b + c + d + e);

      assertTrue(result.run().isNothing());

      MaybePath<Integer> result2 =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .when(t -> t._5() < 10)
              .yield((a, b, c, d, e) -> a + b + c + d + e);

      assertEquals(Maybe.just(15), result2.run());
    }

    @Test
    @DisplayName("should support yield with tuple function")
    void supportsYieldWithTupleFunction() {
      MaybePath<String> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3() + t._4() + t._5()));

      assertEquals(Maybe.just("sum=15"), result.run());
    }
  }

  // ========================================================================
  // Extended Arity Tests (6-8) — Generated Step Classes
  // ========================================================================

  @Nested
  @DisplayName("MaybePath Extended Arity (6-12)")
  class MaybePathExtendedArityTests {

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(Maybe.just(21), result.run());
    }

    @Test
    @DisplayName("Arity 6: should yield with tuple function")
    void arity6_yieldTuple() {
      MaybePath<String> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3() + t._4() + t._5() + t._6()));

      assertEquals(Maybe.just("sum=21"), result.run());
    }

    @Test
    @DisplayName("Arity 6: should support when() filter")
    void arity6_when() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .when(t -> t._6() > 10)
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertTrue(result.run().isNothing());
    }

    @Test
    @DisplayName("Arity 6: should support let()")
    void arity6_let() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .let(t -> t._1() + t._2() + t._3() + t._4() + t._5())
              .yield((a, b, c, d, e, sum) -> sum);

      assertEquals(Maybe.just(15), result.run());
    }

    @Test
    @DisplayName("Arity 7: should chain seven generators and yield")
    void arity7_yield() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .from(t -> Path.just(7))
              .yield((a, b, c, d, e, f, g) -> a + b + c + d + e + f + g);

      assertEquals(Maybe.just(28), result.run());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .from(t -> Path.just(7))
              .from(t -> Path.just(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(Maybe.just(36), result.run());
    }

    @Test
    @DisplayName("Arity 8: should short-circuit on Nothing")
    void arity8_shortCircuit() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.<Integer>nothing())
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .from(t -> Path.just(7))
              .from(t -> Path.just(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertTrue(result.run().isNothing());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .from(t -> Path.just(7))
              .from(t -> Path.just(8))
              .from(t -> Path.just(9))
              .from(t -> Path.just(10))
              .from(t -> Path.just(11))
              .from(t -> Path.just(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(Maybe.just(78), result.run());
    }

    @Test
    @DisplayName("Arity 12: should support when() filter")
    void arity12_when() {
      MaybePath<Integer> result =
          ForPath.from(Path.just(1))
              .from(a -> Path.just(2))
              .from(t -> Path.just(3))
              .from(t -> Path.just(4))
              .from(t -> Path.just(5))
              .from(t -> Path.just(6))
              .from(t -> Path.just(7))
              .from(t -> Path.just(8))
              .from(t -> Path.just(9))
              .from(t -> Path.just(10))
              .from(t -> Path.just(11))
              .from(t -> Path.just(12))
              .when(t -> t._12() > 100)
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertTrue(result.run().isNothing());
    }
  }

  @Nested
  @DisplayName("OptionalPath Extended Arity (4-12)")
  class OptionalPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .from(t -> Path.present(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(Optional.of(10), result.run());
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .from(t -> Path.present(4))
              .from(t -> Path.present(5))
              .from(t -> Path.present(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(Optional.of(21), result.run());
    }

    @Test
    @DisplayName("Arity 6: should support when() filter")
    void arity6_when() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .from(t -> Path.present(4))
              .from(t -> Path.present(5))
              .from(t -> Path.present(6))
              .when(t -> t._6() > 10)
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertTrue(result.run().isEmpty());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .from(t -> Path.present(4))
              .from(t -> Path.present(5))
              .from(t -> Path.present(6))
              .from(t -> Path.present(7))
              .from(t -> Path.present(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(Optional.of(36), result.run());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .from(t -> Path.present(4))
              .from(t -> Path.present(5))
              .from(t -> Path.present(6))
              .from(t -> Path.present(7))
              .from(t -> Path.present(8))
              .from(t -> Path.present(9))
              .from(t -> Path.present(10))
              .from(t -> Path.present(11))
              .from(t -> Path.present(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(Optional.of(78), result.run());
    }
  }

  @Nested
  @DisplayName("EitherPath Extended Arity (4-12)")
  class EitherPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .from(t -> Path.<String, Integer>right(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertTrue(result.run().isRight());
      assertEquals(10, result.run().getRight());
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .from(t -> Path.<String, Integer>right(4))
              .from(t -> Path.<String, Integer>right(5))
              .from(t -> Path.<String, Integer>right(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertTrue(result.run().isRight());
      assertEquals(21, result.run().getRight());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .from(t -> Path.<String, Integer>right(4))
              .from(t -> Path.<String, Integer>right(5))
              .from(t -> Path.<String, Integer>right(6))
              .from(t -> Path.<String, Integer>right(7))
              .from(t -> Path.<String, Integer>right(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertTrue(result.run().isRight());
      assertEquals(36, result.run().getRight());
    }

    @Test
    @DisplayName("Arity 6: should short-circuit on Left")
    void arity6_shortCircuit() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>left("Error at step 3"))
              .from(t -> Path.<String, Integer>right(4))
              .from(t -> Path.<String, Integer>right(5))
              .from(t -> Path.<String, Integer>right(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertTrue(result.run().isLeft());
      assertEquals("Error at step 3", result.run().getLeft());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .from(t -> Path.<String, Integer>right(4))
              .from(t -> Path.<String, Integer>right(5))
              .from(t -> Path.<String, Integer>right(6))
              .from(t -> Path.<String, Integer>right(7))
              .from(t -> Path.<String, Integer>right(8))
              .from(t -> Path.<String, Integer>right(9))
              .from(t -> Path.<String, Integer>right(10))
              .from(t -> Path.<String, Integer>right(11))
              .from(t -> Path.<String, Integer>right(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertTrue(result.run().isRight());
      assertEquals(78, result.run().getRight());
    }
  }

  @Nested
  @DisplayName("TryPath Extended Arity (4-12)")
  class TryPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      TryPath<Integer> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .from(t -> Path.success(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertTrue(result.run().isSuccess());
      assertEquals(10, result.run().orElse(null));
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      TryPath<Integer> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .from(t -> Path.success(4))
              .from(t -> Path.success(5))
              .from(t -> Path.success(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertTrue(result.run().isSuccess());
      assertEquals(21, result.run().orElse(null));
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      TryPath<Integer> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .from(t -> Path.success(4))
              .from(t -> Path.success(5))
              .from(t -> Path.success(6))
              .from(t -> Path.success(7))
              .from(t -> Path.success(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertTrue(result.run().isSuccess());
      assertEquals(36, result.run().orElse(null));
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      TryPath<Integer> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .from(t -> Path.success(4))
              .from(t -> Path.success(5))
              .from(t -> Path.success(6))
              .from(t -> Path.success(7))
              .from(t -> Path.success(8))
              .from(t -> Path.success(9))
              .from(t -> Path.success(10))
              .from(t -> Path.success(11))
              .from(t -> Path.success(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertTrue(result.run().isSuccess());
      assertEquals(78, result.run().orElse(null));
    }
  }

  @Nested
  @DisplayName("IOPath Extended Arity (4-12)")
  class IOPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .from(t -> Path.ioPure(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(10, result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .from(t -> Path.ioPure(4))
              .from(t -> Path.ioPure(5))
              .from(t -> Path.ioPure(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(21, result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .from(t -> Path.ioPure(4))
              .from(t -> Path.ioPure(5))
              .from(t -> Path.ioPure(6))
              .from(t -> Path.ioPure(7))
              .from(t -> Path.ioPure(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(36, result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .from(t -> Path.ioPure(4))
              .from(t -> Path.ioPure(5))
              .from(t -> Path.ioPure(6))
              .from(t -> Path.ioPure(7))
              .from(t -> Path.ioPure(8))
              .from(t -> Path.ioPure(9))
              .from(t -> Path.ioPure(10))
              .from(t -> Path.ioPure(11))
              .from(t -> Path.ioPure(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(78, result.unsafeRun());
    }
  }

  @Nested
  @DisplayName("IdPath Extended Arity (4-12)")
  class IdPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      IdPath<Integer> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .from(t -> Path.id(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(10, result.run().value());
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      IdPath<Integer> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .from(t -> Path.id(4))
              .from(t -> Path.id(5))
              .from(t -> Path.id(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(21, result.run().value());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      IdPath<Integer> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .from(t -> Path.id(4))
              .from(t -> Path.id(5))
              .from(t -> Path.id(6))
              .from(t -> Path.id(7))
              .from(t -> Path.id(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(36, result.run().value());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      IdPath<Integer> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .from(t -> Path.id(4))
              .from(t -> Path.id(5))
              .from(t -> Path.id(6))
              .from(t -> Path.id(7))
              .from(t -> Path.id(8))
              .from(t -> Path.id(9))
              .from(t -> Path.id(10))
              .from(t -> Path.id(11))
              .from(t -> Path.id(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(78, result.run().value());
    }
  }

  @Nested
  @DisplayName("NonDetPath Extended Arity (4-12)")
  class NonDetPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should produce Cartesian product")
    void arity4_yield() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list(10))
              .from(t -> Path.list(100))
              .from(t -> Path.list(1000))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(List.of(1111, 1112), result.run());
    }

    @Test
    @DisplayName("Arity 6: should produce Cartesian product")
    void arity6_yield() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list(10))
              .from(t -> Path.list(100))
              .from(t -> Path.list(1000))
              .from(t -> Path.list(10000))
              .from(t -> Path.list(100000))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(List.of(111111, 111112), result.run());
    }

    @Test
    @DisplayName("Arity 6: should support when() filter")
    void arity6_when() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1, 2, 3))
              .from(a -> Path.list(10))
              .from(t -> Path.list(100))
              .from(t -> Path.list(1000))
              .from(t -> Path.list(10000))
              .from(t -> Path.list(100000))
              .when(t -> t._1() == 2)
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(List.of(111112), result.run());
    }

    @Test
    @DisplayName("Arity 8: should produce Cartesian product")
    void arity8_yield() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1))
              .from(a -> Path.list(2))
              .from(t -> Path.list(3))
              .from(t -> Path.list(4))
              .from(t -> Path.list(5))
              .from(t -> Path.list(6))
              .from(t -> Path.list(7))
              .from(t -> Path.list(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(List.of(36), result.run());
    }

    @Test
    @DisplayName("Arity 12: should produce Cartesian product")
    void arity12_yield() {
      NonDetPath<Integer> result =
          ForPath.from(Path.list(1))
              .from(a -> Path.list(2))
              .from(t -> Path.list(3))
              .from(t -> Path.list(4))
              .from(t -> Path.list(5))
              .from(t -> Path.list(6))
              .from(t -> Path.list(7))
              .from(t -> Path.list(8))
              .from(t -> Path.list(9))
              .from(t -> Path.list(10))
              .from(t -> Path.list(11))
              .from(t -> Path.list(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(List.of(78), result.run());
    }
  }

  @Nested
  @DisplayName("VTaskPath Extended Arity (6-12)")
  class VTaskPathExtendedArityTests {

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .from(t -> Path.vtaskPure(6))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      assertEquals(21, result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .from(t -> Path.vtaskPure(6))
              .from(t -> Path.vtaskPure(7))
              .from(t -> Path.vtaskPure(8))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      assertEquals(36, result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 8: should yield with tuple function")
    void arity8_yieldTuple() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .from(t -> Path.vtaskPure(6))
              .from(t -> Path.vtaskPure(7))
              .from(t -> Path.vtaskPure(8))
              .yield(
                  t ->
                      "sum="
                          + (t._1() + t._2() + t._3() + t._4() + t._5() + t._6() + t._7()
                              + t._8()));

      assertEquals("sum=36", result.unsafeRun());
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .from(t -> Path.vtaskPure(6))
              .from(t -> Path.vtaskPure(7))
              .from(t -> Path.vtaskPure(8))
              .from(t -> Path.vtaskPure(9))
              .from(t -> Path.vtaskPure(10))
              .from(t -> Path.vtaskPure(11))
              .from(t -> Path.vtaskPure(12))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      assertEquals(78, result.unsafeRun());
    }
  }

  @Nested
  @DisplayName("GenericPath Extended Arity (4-12)")
  class GenericPathExtendedArityTests {

    @Test
    @DisplayName("Arity 4: should chain four generators and yield")
    void arity4_yield() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(4), MaybeMonad.INSTANCE))
              .yield((a, b, c, d) -> a + b + c + d);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(10), maybeResult);
    }

    @Test
    @DisplayName("Arity 6: should chain six generators and yield")
    void arity6_yield() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(4), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(6), MaybeMonad.INSTANCE))
              .yield((a, b, c, d, e, f) -> a + b + c + d + e + f);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(21), maybeResult);
    }

    @Test
    @DisplayName("Arity 8: should chain eight generators and yield")
    void arity8_yield() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(4), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(6), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(7), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(8), MaybeMonad.INSTANCE))
              .yield((a, b, c, d, e, f, g, h) -> a + b + c + d + e + f + g + h);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(36), maybeResult);
    }

    @Test
    @DisplayName("Arity 12: should chain twelve generators and yield")
    void arity12_yield() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(4), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(6), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(7), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(8), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(9), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(11), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(12), MaybeMonad.INSTANCE))
              .yield(
                  (a, b, c, d, e, f, g, h, i, j, k, l) ->
                      a + b + c + d + e + f + g + h + i + j + k + l);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(78), maybeResult);
    }
  }

  // ========================================================================
  // Additional OptionalPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("OptionalPath Steps Additional Tests")
  class OptionalPathStepsTests {

    @Test
    @DisplayName("should support let() in Steps1")
    void supportsLetInSteps1() {
      OptionalPath<String> result =
          ForPath.from(Path.present(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertEquals(Optional.of("a=10, b=20"), result.run());
    }

    @Test
    @DisplayName("should support from() in Steps2")
    void supportsFromInSteps2() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .yield((a, b, c) -> a + b + c);

      assertEquals(Optional.of(6), result.run());
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(5))
              .from(a -> Path.present(10))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      assertEquals(Optional.of(15), result.run());
    }

    @Test
    @DisplayName("should support when() in Steps2")
    void supportsWhenInSteps2() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(5))
              .from(a -> Path.present(10))
              .when(t -> t._1() + t._2() > 20)
              .yield((a, b) -> a + b);

      assertTrue(result.run().isEmpty());

      OptionalPath<Integer> result2 =
          ForPath.from(Path.present(5))
              .from(a -> Path.present(10))
              .when(t -> t._1() + t._2() > 10)
              .yield((a, b) -> a + b);

      assertEquals(Optional.of(15), result2.run());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      OptionalPath<String> result =
          ForPath.from(Path.present(5))
              .from(a -> Path.present(10))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertEquals(Optional.of("sum=15"), result.run());
    }

    @Test
    @DisplayName("should support when() in Steps3")
    void supportsWhenInSteps3() {
      OptionalPath<Integer> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .when(t -> t._1() + t._2() + t._3() > 10)
              .yield((a, b, c) -> a + b + c);

      assertTrue(result.run().isEmpty());

      // Test success case where predicate passes
      OptionalPath<Integer> result2 =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .when(t -> t._1() + t._2() + t._3() > 0)
              .yield((a, b, c) -> a + b + c);

      assertEquals(Optional.of(6), result2.run());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      OptionalPath<String> result =
          ForPath.from(Path.present(1))
              .from(a -> Path.present(2))
              .from(t -> Path.present(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertEquals(Optional.of("sum=6"), result.run());
    }
  }

  // ========================================================================
  // Additional EitherPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("EitherPath Steps Additional Tests")
  class EitherPathStepsTests {

    @Test
    @DisplayName("should support simple yield in Steps1")
    void supportsSimpleYieldInSteps1() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(10)).yield(a -> a * 2);

      assertTrue(result.run().isRight());
      assertEquals(20, result.run().getRight());
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      EitherPath<String, Integer> result =
          ForPath.from(Path.<String, Integer>right(5))
              .from(a -> Path.<String, Integer>right(10))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      assertTrue(result.run().isRight());
      assertEquals(15, result.run().getRight());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      EitherPath<String, String> result =
          ForPath.from(Path.<String, Integer>right(5))
              .from(a -> Path.<String, Integer>right(10))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertTrue(result.run().isRight());
      assertEquals("sum=15", result.run().getRight());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      EitherPath<String, String> result =
          ForPath.from(Path.<String, Integer>right(1))
              .from(a -> Path.<String, Integer>right(2))
              .from(t -> Path.<String, Integer>right(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertTrue(result.run().isRight());
      assertEquals("sum=6", result.run().getRight());
    }
  }

  // ========================================================================
  // Additional TryPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("TryPath Steps Additional Tests")
  class TryPathStepsTests {

    @Test
    @DisplayName("should support simple yield in Steps1")
    void supportsSimpleYieldInSteps1() {
      TryPath<Integer> result = ForPath.from(Path.success(10)).yield(a -> a * 2);

      assertTrue(result.run().isSuccess());
      assertEquals(20, result.run().orElse(null));
    }

    @Test
    @DisplayName("should support from() in Steps2")
    void supportsFromInSteps2() {
      TryPath<Integer> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .yield((a, b, c) -> a + b + c);

      assertTrue(result.run().isSuccess());
      assertEquals(6, result.run().orElse(null));
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      TryPath<Integer> result =
          ForPath.from(Path.success(5))
              .from(a -> Path.success(10))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      assertTrue(result.run().isSuccess());
      assertEquals(15, result.run().orElse(null));
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      TryPath<String> result =
          ForPath.from(Path.success(5))
              .from(a -> Path.success(10))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertTrue(result.run().isSuccess());
      assertEquals("sum=15", result.run().orElse(null));
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      TryPath<String> result =
          ForPath.from(Path.success(1))
              .from(a -> Path.success(2))
              .from(t -> Path.success(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertTrue(result.run().isSuccess());
      assertEquals("sum=6", result.run().orElse(null));
    }
  }

  // ========================================================================
  // Additional IOPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("IOPath Steps Additional Tests")
  class IOPathStepsTests {

    @Test
    @DisplayName("should support simple yield in Steps1")
    void supportsSimpleYieldInSteps1() {
      IOPath<Integer> result = ForPath.from(Path.ioPure(10)).yield(a -> a * 2);

      assertEquals(20, result.unsafeRun());
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      IOPath<Integer> result =
          ForPath.from(Path.ioPure(5))
              .from(a -> Path.ioPure(10))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      assertEquals(15, result.unsafeRun());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      IOPath<String> result =
          ForPath.from(Path.ioPure(5))
              .from(a -> Path.ioPure(10))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertEquals("sum=15", result.unsafeRun());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      IOPath<String> result =
          ForPath.from(Path.ioPure(1))
              .from(a -> Path.ioPure(2))
              .from(t -> Path.ioPure(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertEquals("sum=6", result.unsafeRun());
    }
  }

  // ========================================================================
  // Additional IdPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("IdPath Steps Additional Tests")
  class IdPathStepsTests {

    @Test
    @DisplayName("should support simple yield in Steps1")
    void supportsSimpleYieldInSteps1() {
      IdPath<Integer> result = ForPath.from(Path.id(10)).yield(a -> a * 2);

      assertEquals(20, result.run().value());
    }

    @Test
    @DisplayName("should support from() in Steps2")
    void supportsFromInSteps2() {
      IdPath<Integer> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .yield((a, b, c) -> a + b + c);

      assertEquals(6, result.run().value());
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      IdPath<Integer> result =
          ForPath.from(Path.id(5))
              .from(a -> Path.id(10))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      assertEquals(15, result.run().value());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      IdPath<String> result =
          ForPath.from(Path.id(5)).from(a -> Path.id(10)).yield(t -> "sum=" + (t._1() + t._2()));

      assertEquals("sum=15", result.run().value());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      IdPath<String> result =
          ForPath.from(Path.id(1))
              .from(a -> Path.id(2))
              .from(t -> Path.id(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertEquals("sum=6", result.run().value());
    }
  }

  // ========================================================================
  // Additional NonDetPath Step Tests
  // ========================================================================

  @Nested
  @DisplayName("NonDetPath Steps Additional Tests")
  class NonDetPathStepsTests {

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list("a", "b"))
              .let(t -> t._1() + t._2())
              .yield((num, letter, combined) -> combined);

      assertEquals(List.of("1a", "1b", "2a", "2b"), result.run());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list("a", "b"))
              .yield(t -> t._1() + "-" + t._2());

      assertEquals(List.of("1-a", "1-b", "2-a", "2-b"), result.run());
    }

    @Test
    @DisplayName("should support when() in Steps3")
    void supportsWhenInSteps3() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list(10, 20))
              .from(t -> Path.list("x", "y"))
              .when(t -> (t._1() + t._2()) > 15)
              .yield((a, b, c) -> a + "+" + b + c);

      // Filter: (a + b) > 15 -> 1+10=11(no), 1+20=21(yes), 2+10=12(no), 2+20=22(yes)
      assertEquals(List.of("1+20x", "1+20y", "2+20x", "2+20y"), result.run());
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      NonDetPath<String> result =
          ForPath.from(Path.list(1, 2))
              .from(a -> Path.list("a", "b"))
              .from(t -> Path.list(true))
              .yield(t -> t._1() + t._2() + (t._3() ? "!" : "?"));

      assertEquals(List.of("1a!", "1b!", "2a!", "2b!"), result.run());
    }
  }

  // ========================================================================
  // GenericPath Complete Test Suite
  // ========================================================================

  @Nested
  @DisplayName("GenericPath Comprehension")
  class GenericPathTests {

    @Test
    @DisplayName("should support simple yield in Steps1")
    void supportsSimpleYieldInSteps1() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .yield(a -> a * 2);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(20), maybeResult);
    }

    @Test
    @DisplayName("should chain generators with from()")
    void chainsGenerators() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(a * 2), MaybeMonad.INSTANCE))
              .yield((a, b) -> a + b);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(15), maybeResult);
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      GenericPath<MaybeKind.Witness, String> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .let(a -> a * 2)
              .yield((a, b) -> "a=" + a + ", b=" + b);

      Maybe<String> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just("a=10, b=20"), maybeResult);
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      GenericPath<MaybeKind.Witness, String> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(user), MaybeMonad.INSTANCE))
              .focus(addressFocus)
              .yield((u, addr) -> addr.city());

      Maybe<String> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just("NYC"), maybeResult);
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .yield((a, b, c) -> a + b + c);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(6), maybeResult);
    }

    @Test
    @DisplayName("should support yield with BiFunction in Steps2")
    void supportsYieldWithBiFunctionInSteps2() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .yield((a, b) -> a + b);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(15), maybeResult);
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps2")
    void supportsYieldWithTupleInSteps2() {
      GenericPath<MaybeKind.Witness, String> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .yield(t -> "sum=" + (t._1() + t._2()));

      Maybe<String> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just("sum=15"), maybeResult);
    }

    @Test
    @DisplayName("should support let() in Steps2")
    void supportsLetInSteps2() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(10), MaybeMonad.INSTANCE))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> sum);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(15), maybeResult);
    }

    @Test
    @DisplayName("should support from() in Steps2")
    void supportsFromInSteps2() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .yield((a, b, c) -> a + b + c);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just(6), maybeResult);
    }

    @Test
    @DisplayName("should support yield with tuple function in Steps3")
    void supportsYieldWithTupleInSteps3() {
      GenericPath<MaybeKind.Witness, String> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(1), MaybeMonad.INSTANCE))
              .from(a -> GenericPath.of(MaybeKindHelper.MAYBE.just(2), MaybeMonad.INSTANCE))
              .from(t -> GenericPath.of(MaybeKindHelper.MAYBE.just(3), MaybeMonad.INSTANCE))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      Maybe<String> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertEquals(Maybe.just("sum=6"), maybeResult);
    }

    @Test
    @DisplayName("should short-circuit on Nothing")
    void shortCircuitsOnNothing() {
      GenericPath<MaybeKind.Witness, Integer> result =
          ForPath.from(GenericPath.of(MaybeKindHelper.MAYBE.just(5), MaybeMonad.INSTANCE))
              .<Integer>from(
                  a ->
                      GenericPath.of(MaybeKindHelper.MAYBE.<Integer>nothing(), MaybeMonad.INSTANCE))
              .yield((a, b) -> a + b);

      Maybe<Integer> maybeResult = MaybeKindHelper.MAYBE.narrow(result.runKind());
      assertTrue(maybeResult.isNothing());
    }
  }

  // ========================================================================
  // VTaskPath Comprehension
  // ========================================================================

  @Nested
  @DisplayName("VTaskPath Comprehension")
  class VTaskPathTests {

    @Test
    @DisplayName("should chain generators with from()")
    void chainsGenerators() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(5)).from(a -> Path.vtaskPure(a * 2)).yield((a, b) -> a + b);

      assertEquals(15, result.unsafeRun()); // 5 + 10
    }

    @Test
    @DisplayName("should propagate errors")
    void propagatesErrors() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(5))
              .<Integer>from(
                  a ->
                      Path.vtask(
                          () -> {
                            throw new RuntimeException("test error");
                          }))
              .yield((a, b) -> a + b);

      var _ = assertThrowsExactly(RuntimeException.class, result::unsafeRun);
    }

    @Test
    @DisplayName("should support let() for pure computations")
    void supportsLet() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(10)).let(a -> a * 2).yield((a, b) -> "a=" + a + ", b=" + b);

      assertEquals("a=10, b=20", result.unsafeRun());
    }

    @Test
    @DisplayName("should support focus() with FocusPath")
    void supportsFocus() {
      User user = new User("Alice", new Address("NYC", "USA"));

      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(user)).focus(addressFocus).yield((u, addr) -> addr.city());

      assertEquals("NYC", result.unsafeRun());
    }

    @Test
    @DisplayName("should support three generators")
    void supportsThreeGenerators() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(a + 1))
              .from(t -> Path.vtaskPure(t._2() + 1))
              .yield((a, b, c) -> a + b + c);

      assertEquals(6, result.unsafeRun()); // 1 + 2 + 3
    }

    @Test
    @DisplayName("should support four generators")
    void supportsFourGenerators() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .yield((a, b, c, d) -> a + b + c + d);

      assertEquals(10, result.unsafeRun());
    }

    @Test
    @DisplayName("should support five generators")
    void supportsFiveGenerators() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .yield((a, b, c, d, e) -> a + b + c + d + e);

      assertEquals(15, result.unsafeRun());
    }

    @Test
    @DisplayName("should support yield with tuple function")
    void supportsYieldWithTupleFunction() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(5))
              .from(a -> Path.vtaskPure(a * 2))
              .yield(t -> "sum=" + (t._1() + t._2()));

      assertEquals("sum=15", result.unsafeRun());
    }

    @Test
    @DisplayName("should throw on null VTaskPath source")
    void throwsOnNullVTaskPathSource() {
      var _ =
          assertThrowsExactly(
              NullPointerException.class, () -> ForPath.from((VTaskPath<Object>) null));
    }

    // ===== Additional tests for complete coverage of VTaskPathSteps1-5 =====

    @Test
    @DisplayName("VTaskPathSteps2 let() should work at step 2")
    void vtaskPathSteps2Let() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(5))
              .from(a -> Path.vtaskPure(a * 2))
              .let(t -> t._1() + t._2())
              .yield((a, b, sum) -> "a=" + a + ", b=" + b + ", sum=" + sum);

      assertEquals("a=5, b=10, sum=15", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps3 let() should work at step 3")
    void vtaskPathSteps3Let() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .let(t -> t._1() + t._2() + t._3())
              .yield((a, b, c, sum) -> "1+2+3=" + sum);

      assertEquals("1+2+3=6", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps4 let() should work at step 4")
    void vtaskPathSteps4Let() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .let(t -> t._1() + t._2() + t._3() + t._4())
              .yield((a, b, c, d, sum) -> "sum=" + sum);

      assertEquals("sum=10", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps3 yield with tuple function")
    void vtaskPathSteps3YieldWithTupleFunction() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3()));

      assertEquals("sum=6", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps4 yield with tuple function")
    void vtaskPathSteps4YieldWithTupleFunction() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3() + t._4()));

      assertEquals("sum=10", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps5 yield with tuple function")
    void vtaskPathSteps5YieldWithTupleFunction() {
      VTaskPath<String> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .from(t -> Path.vtaskPure(3))
              .from(t -> Path.vtaskPure(4))
              .from(t -> Path.vtaskPure(5))
              .yield(t -> "sum=" + (t._1() + t._2() + t._3() + t._4() + t._5()));

      assertEquals("sum=15", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps1 yield() with single value function")
    void vtaskPathSteps1YieldWithSingleValueFunction() {
      VTaskPath<String> result = ForPath.from(Path.vtaskPure(42)).yield(a -> "value=" + a);

      assertEquals("value=42", result.unsafeRun());
    }

    @Test
    @DisplayName("VTaskPathSteps1 from() chains to VTaskPathSteps2")
    void vtaskPathSteps1FromChainsToSteps2() {
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(10)).from(a -> Path.vtaskPure(a + 5)).yield((a, b) -> a * b);

      assertEquals(150, result.unsafeRun()); // 10 * 15
    }

    @Test
    @DisplayName("Error propagation through multiple steps")
    void errorPropagationThroughMultipleSteps() {
      RuntimeException error = new RuntimeException("Step 3 failed");
      VTaskPath<Integer> result =
          ForPath.from(Path.vtaskPure(1))
              .from(a -> Path.vtaskPure(2))
              .<Integer>from(
                  t ->
                      Path.vtask(
                          () -> {
                            throw error;
                          }))
              .yield((a, b, c) -> a + b + c);

      RuntimeException thrown = assertThrowsExactly(RuntimeException.class, result::unsafeRun);
      assertEquals("Step 3 failed", thrown.getMessage());
    }
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.solutions.coretypes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKindHelper;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.junit.jupiter.api.Test;

/**
 * Solution for Tutorial06 ConcreteTypes — teaching-solution format.
 *
 * <p>This solution file follows the chapter's <em>teaching solution</em> conventions established by
 * the Foundations journey: read the working code first, then the commentary on <em>why</em> the
 * chosen form is idiomatic. The complete-with-commentary template (Why this is idiomatic /
 * Alternative / Common wrong attempt on every exercise) lives in the Foundations solutions
 * coretypes/Tutorial01_KindBasics_Solution.java as the canonical reference.
 *
 * <p>The exercise bodies below are correct working code. Per-exercise teaching commentary is being
 * rolled out across the chapter; if this file does not yet have it, treat the reference code as the
 * answer and consult the pilot solution for the format guide.
 *
 * <p>For the chapter-level guidance on how to learn from a solution, see the <a
 * href="../../../../../../../../../hkj-book/src/tutorials/solutions_guide.md">Solutions Guide</a>
 * in the book.
 */
public class Tutorial06_ConcreteTypes_Solution {

  /**
   * Why this is idiomatic: a guarded ladder of validations that returns a typed {@code Left} on the
   * first failing rule keeps the success path unindented and gives each error its own short, stable
   * string. The caller sees one "valid" branch and one "invalid" branch with a reason.
   *
   * <p>Alternative: collect all the failures with {@code Validated} and a {@code Semigroup}
   * (Exercise 5). Pick that when the user wants every reason at once; pick {@code Either} when
   * "first reason wins" is good enough.
   *
   * <p>Common wrong attempt: returning {@code null} or throwing {@code IllegalArgumentException}
   * for invalid inputs. Both choices push the failure handling out of the type system — {@code
   * Either<String, Integer>} states "either an error reason or an age" right in the signature.
   */
  @Test
  void exercise1_eitherForErrorHandling() {
    Function<Integer, Either<String, Integer>> validateAge =
        age -> {
          // Solution: Validate age with multiple conditions
          if (age < 0 || age > 150) {
            return Either.left("Invalid age");
          } else if (age < 18) {
            return Either.left("Too young");
          } else {
            return Either.right(age);
          }
        };

    assertThat(validateAge.apply(25).isRight()).isTrue();
    assertThat(validateAge.apply(25).getRight()).isEqualTo(25);

    assertThat(validateAge.apply(15).isLeft()).isTrue();
    assertThat(validateAge.apply(15).getLeft()).isEqualTo("Too young");

    assertThat(validateAge.apply(-5).isLeft()).isTrue();
    assertThat(validateAge.apply(-5).getLeft()).isEqualTo("Invalid age");
  }

  /**
   * Why this is idiomatic: {@code Maybe.just(...)} and {@code Maybe.nothing()} say "present" and
   * "absent" without inventing a reason. Lookups are exactly that shape — the key is there or it is
   * not — so {@code Maybe} fits without ceremony.
   *
   * <p>Alternative: {@code Optional<String>} from the JDK. Same operational meaning; reach for
   * {@code Maybe} when the surrounding code already speaks higher-kinded-j typeclasses (so {@code
   * map}/{@code flatMap} compose with the rest of the pipeline) and {@code Optional} when interop
   * with vanilla Java code dominates.
   *
   * <p>Common wrong attempt: returning {@code null} for missing keys. The signature {@code String}
   * now lies — every caller has to remember to null-check, and one missed check NPEs at the next
   * dereference.
   */
  @Test
  void exercise2_maybeForOptionalValues() {
    Function<String, Maybe<String>> lookup =
        key -> {
          // Solution: Return just for "key1", nothing otherwise
          if (key.equals("key1")) {
            return Maybe.just("value");
          } else {
            return Maybe.nothing();
          }
        };

    Maybe<String> found = lookup.apply("key1");
    assertThat(found.isJust()).isTrue();
    assertThat(found.get()).isEqualTo("value");

    Maybe<String> notFound = lookup.apply("key2");
    assertThat(notFound.isNothing()).isTrue();
  }

  /**
   * Why this is idiomatic: {@code orElse(default)} collapses the {@code Maybe} into a plain {@code
   * String} in one move — exactly the spelling needed at the boundary where the downstream code
   * expects a value, not a wrapper.
   *
   * <p>Alternative: {@code maybe.fold(() -> "Default", v -> v)}. Equivalent; choose {@code fold}
   * when the default needs to be lazily computed or depends on something other than a constant.
   *
   * <p>Common wrong attempt: {@code maybe.get()} guarded by an {@code if (maybe.isJust())}. Two
   * statements, two dereferences of the same value, and the {@code get} on the absent branch is a
   * runtime error if the guard ever drifts. {@code orElse} is total by construction.
   */
  @Test
  void exercise3_maybeWithDefault() {
    Maybe<String> present = Maybe.just("Hello");
    Maybe<String> absent = Maybe.nothing();

    // Solution: Use orElse to provide default values
    String result1 = present.orElse("Default");
    String result2 = absent.orElse("Default");

    assertThat(result1).isEqualTo("Hello");
    assertThat(result2).isEqualTo("Default");
  }

  /**
   * Why this is idiomatic: {@code stream().filter(...).map(...).collect(toList())} is the JDK's
   * canonical filter-then-transform pipeline. Each stage is one concern, and the result is an
   * eagerly evaluated {@code List}.
   *
   * <p>Alternative: {@code .toList()} (Java 16+) instead of {@code Collectors.toList()}. The
   * shorter form returns an unmodifiable list — prefer it for new code unless something downstream
   * genuinely mutates the result.
   *
   * <p>Common wrong attempt: looping with an {@code int} index and an {@code ArrayList}. It works,
   * but every readable line of intent ("keep evens", "scale by 10") becomes scaffolding around the
   * actual logic; the stream pipeline is the smaller surface area.
   */
  @Test
  void exercise4_listOperations() {
    List<Integer> numbers = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

    // Solution: Stream operations - filter even, map to multiply by 10, collect
    List<Integer> result =
        numbers.stream().filter(n -> n % 2 == 0).map(n -> n * 10).collect(Collectors.toList());

    assertThat(result).containsExactly(20, 40, 60, 80, 100);
  }

  /**
   * Why this is idiomatic: {@code Validated} with a {@code Semigroup<String>} is the canonical
   * "tell me everything that's wrong" shape. {@code map3} runs all three validators independently
   * and combines failures with the semigroup — one call, every error visible.
   *
   * <p>Alternative: chain the validators with {@code Either.flatMap}. Easier to read but fail-fast;
   * the user only sees the first error and re-submits to discover the next one.
   *
   * <p>Common wrong attempt: using {@code map3} without supplying a {@code Semigroup}. The
   * applicative needs to know how to combine errors; passing the wrong instance (e.g. a monad that
   * short-circuits on first failure) silently switches behaviour back to fail-fast.
   */
  @Test
  void exercise5_validatedAccumulatesErrors() {
    record User(String name, int age, String email) {}

    Function<String, Validated<String, String>> validateName =
        name -> name.length() >= 2 ? Validated.valid(name) : Validated.invalid("Name too short");

    Function<Integer, Validated<String, Integer>> validateAge =
        age -> age >= 18 ? Validated.valid(age) : Validated.invalid("Must be 18+");

    Function<String, Validated<String, String>> validateEmail =
        email -> email.contains("@") ? Validated.valid(email) : Validated.invalid("Invalid email");

    // Valid case
    Validated<String, String> validName = validateName.apply("Alice");
    Validated<String, Integer> validAge = validateAge.apply(25);
    Validated<String, String> validEmail = validateEmail.apply("alice@example.com");

    // Solution: Use ValidatedMonad to combine all three validations
    Semigroup<String> stringSemigroup = Semigroups.string(", ");
    ValidatedMonad<String> applicative = ValidatedMonad.instance(stringSemigroup);
    Validated<String, User> validUser =
        ValidatedKindHelper.VALIDATED.narrow(
            applicative.map3(
                ValidatedKindHelper.VALIDATED.widen(validName),
                ValidatedKindHelper.VALIDATED.widen(validAge),
                ValidatedKindHelper.VALIDATED.widen(validEmail),
                User::new));

    assertThat(validUser.isValid()).isTrue();

    // Invalid case - multiple errors
    Validated<String, String> invalidName = validateName.apply("A");
    Validated<String, Integer> invalidAge = validateAge.apply(15);
    Validated<String, String> invalidEmail = validateEmail.apply("not-an-email");

    // Solution: Combine invalid validations to see error accumulation
    Validated<String, User> invalidUser =
        ValidatedKindHelper.VALIDATED.narrow(
            applicative.map3(
                ValidatedKindHelper.VALIDATED.widen(invalidName),
                ValidatedKindHelper.VALIDATED.widen(invalidAge),
                ValidatedKindHelper.VALIDATED.widen(invalidEmail),
                User::new));

    assertThat(invalidUser.isInvalid()).isTrue();
    // Validated accumulates errors (implementation-dependent on how Semigroup works)
  }

  /**
   * Why this is idiomatic: pairing the {@code Maybe} state check with the corresponding {@code
   * Either} constructor makes the conversion total — every {@code Maybe} maps to exactly one {@code
   * Either}, and the error string lives at the conversion site rather than inside the {@code
   * Maybe}.
   *
   * <p>Alternative: {@code maybe.toEither("Not found")} where the helper exists; the inline ternary
   * shown here is the explicit form when the named bridge is unavailable or when the error is
   * computed lazily.
   *
   * <p>Common wrong attempt: calling {@code maybe.get()} unconditionally and catching the resulting
   * exception to map it to {@code Either.left}. That uses exceptions for routine control flow on
   * values the type system already exposes; branch on {@code isJust} (or call {@code fold})
   * instead.
   */
  @Test
  void exercise6_convertingTypes() {
    Maybe<String> present = Maybe.just("value");
    Maybe<String> absent = Maybe.nothing();

    // Solution: Convert Maybe to Either using conditional
    Either<String, String> either1 =
        present.isJust() ? Either.right(present.get()) : Either.left("Not found");
    Either<String, String> either2 =
        absent.isJust() ? Either.right(absent.get()) : Either.left("Not found");

    assertThat(either1.isRight()).isTrue();
    assertThat(either1.getRight()).isEqualTo("value");

    assertThat(either2.isLeft()).isTrue();
    assertThat(either2.getLeft()).isEqualTo("Not found");
  }

  /**
   * Why this is idiomatic: the same operation gets two signatures because the choice is about the
   * caller's needs, not the operation. {@code Either<String, Integer>} offers a reason; {@code
   * Maybe<Integer>} offers brevity. Both are total — neither throws, neither returns {@code null}.
   *
   * <p>Alternative: a single {@code Either<DivisionError, Integer>} with an enum error and a helper
   * to drop to {@code Maybe}. Better when there is more than one failure mode and you want both
   * shapes from a single source of truth.
   *
   * <p>Common wrong attempt: using {@code int} and returning a sentinel like {@code -1} for "by
   * zero". Sentinels collide with valid results, are easy to forget to check, and the type does not
   * warn the caller. Pick whichever wrapper above matches the caller's appetite for detail.
   */
  @Test
  void exercise7_choosingTheRightType() {
    // Option 1: Use Either to provide an error message
    Function<Integer, Function<Integer, Either<String, Integer>>> safeDivideEither =
        a ->
            b -> {
              // Solution: Either-based division with error message
              if (b == 0) {
                return Either.left("Division by zero");
              } else {
                return Either.right(a / b);
              }
            };

    assertThat(safeDivideEither.apply(10).apply(2).getRight()).isEqualTo(5);
    assertThat(safeDivideEither.apply(10).apply(0).getLeft()).isEqualTo("Division by zero");

    // Option 2: Use Maybe if you don't need an error message
    Function<Integer, Function<Integer, Maybe<Integer>>> safeDivideMaybe =
        a ->
            b -> {
              // Solution: Maybe-based division without error message
              if (b == 0) {
                return Maybe.nothing();
              } else {
                return Maybe.just(a / b);
              }
            };

    assertThat(safeDivideMaybe.apply(10).apply(2).get()).isEqualTo(5);
    assertThat(safeDivideMaybe.apply(10).apply(0).isNothing()).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 06: Concrete Types
   *
   * <p>You now understand: ✓ When to use Either (explicit error handling) ✓ When to use Maybe
   * (optional values without error details) ✓ When to use List (working with multiple values) ✓
   * When to use Validated (accumulating all errors) ✓ How to convert between different types ✓ How
   * to choose the right type for your use case
   *
   * <p>Next: Tutorial 07 - Real World Examples
   */
}

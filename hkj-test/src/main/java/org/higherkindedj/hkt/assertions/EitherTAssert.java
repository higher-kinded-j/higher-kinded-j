// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.assertions;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherTKind;
import org.higherkindedj.hkt.either_t.EitherTKindHelper;

/** Custom AssertJ assertion for EitherT transformer types. */
public final class EitherTAssert {

  private EitherTAssert() {}

  /** Entry point for EitherT assertions where the outer monad unwraps to Optional. */
  public static <F extends WitnessArity<TypeArity.Unary>, E, A>
      EitherTOptionalAssert<F, E, A> assertThatEitherT(
          Kind<EitherTKind.Witness<F, E>, A> actual,
          Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper) {
    return new EitherTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for EitherT with an Optional-shaped outer monad. */
  public static class EitherTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, E, A>
      extends AbstractAssert<EitherTOptionalAssert<F, E, A>, Kind<EitherTKind.Witness<F, E>, A>> {

    private final Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper;

    protected EitherTOptionalAssert(
        Kind<EitherTKind.Witness<F, E>, A> actual,
        Function<Kind<F, Either<E, A>>, Optional<Either<E, A>>> outerUnwrapper) {
      super(actual, EitherTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Either<E, A>> unwrap() {
      var eitherT = EitherTKindHelper.EITHER_T.narrow(actual);
      Kind<F, Either<E, A>> outerKind = eitherT.value();
      return outerUnwrapper.apply(outerKind);
    }

    /** Verifies that the outer monad is empty. */
    public EitherTOptionalAssert<F, E, A> isEmpty() {
      isNotNull();
      Optional<Either<E, A>> u = unwrap();
      Assertions.assertThat(u)
          .withFailMessage(
              () ->
                  "Expected outer monad to be empty but was present with: <" + u.orElse(null) + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the outer monad is present (not empty). */
    public EitherTOptionalAssert<F, E, A> isPresent() {
      isNotNull();
      Assertions.assertThat(unwrap())
          .withFailMessage("Expected outer monad to be present but was empty")
          .isPresent();
      return this;
    }

    /** Verifies that the outer monad is present and contains a Right value. */
    public EitherTOptionalAssert<F, E, A> isPresentRight() {
      isPresent();
      Either<E, A> either = unwrap().get();
      Assertions.assertThat(either.isRight())
          .withFailMessage(
              () ->
                  "Expected outer monad to contain Right but was Left with: <"
                      + either.getLeft()
                      + ">")
          .isTrue();
      return this;
    }

    /** Verifies that the outer monad is present and contains a Left value. */
    public EitherTOptionalAssert<F, E, A> isPresentLeft() {
      isPresent();
      Either<E, A> either = unwrap().get();
      Assertions.assertThat(either.isLeft())
          .withFailMessage(
              () ->
                  "Expected outer monad to contain Left but was Right with: <"
                      + either.getRight()
                      + ">")
          .isTrue();
      return this;
    }

    /** Verifies that the EitherT contains a Right value equal to the expected value. */
    public EitherTOptionalAssert<F, E, A> hasRightValue(A expected) {
      isPresentRight();
      A actualV = unwrap().get().getRight();
      Assertions.assertThat(actualV)
          .withFailMessage("Expected Right value to be <%s> but was <%s>", expected, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the EitherT contains a Left value equal to the expected error. */
    public EitherTOptionalAssert<F, E, A> hasLeftValue(E expected) {
      isPresentLeft();
      E actualV = unwrap().get().getLeft();
      Assertions.assertThat(actualV)
          .withFailMessage("Expected Left value to be <%s> but was <%s>", expected, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the EitherT contains a Right value satisfying the given requirements. */
    public EitherTOptionalAssert<F, E, A> satisfiesRight(Consumer<? super A> requirements) {
      isPresentRight();
      requirements.accept(unwrap().get().getRight());
      return this;
    }

    /** Verifies that the EitherT contains a Left value satisfying the given requirements. */
    public EitherTOptionalAssert<F, E, A> satisfiesLeft(Consumer<? super E> requirements) {
      isPresentLeft();
      requirements.accept(unwrap().get().getLeft());
      return this;
    }

    /** Verifies that the Right value matches the given predicate. */
    public EitherTOptionalAssert<F, E, A> rightMatches(Predicate<? super A> predicate) {
      isPresentRight();
      A value = unwrap().get().getRight();
      Assertions.assertThat(predicate.test(value))
          .withFailMessage("Right value <%s> did not match predicate", value)
          .isTrue();
      return this;
    }

    /** Verifies that the Left value matches the given predicate. */
    public EitherTOptionalAssert<F, E, A> leftMatches(Predicate<? super E> predicate) {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      Assertions.assertThat(predicate.test(error))
          .withFailMessage("Left value <%s> did not match predicate", error)
          .isTrue();
      return this;
    }

    /** Verifies that the Right value is not null. */
    public EitherTOptionalAssert<F, E, A> hasNonNullRightValue() {
      isPresentRight();
      Assertions.assertThat(unwrap().get().getRight()).as("Right value").isNotNull();
      return this;
    }

    /** Verifies that the Left value is not null. */
    public EitherTOptionalAssert<F, E, A> hasNonNullLeftValue() {
      isPresentLeft();
      Assertions.assertThat(unwrap().get().getLeft()).as("Left value").isNotNull();
      return this;
    }

    /** Verifies that the Right value is an instance of the specified type. */
    public EitherTOptionalAssert<F, E, A> hasRightValueOfType(Class<?> type) {
      isPresentRight();
      A value = unwrap().get().getRight();
      Assertions.assertThat(value)
          .withFailMessage(
              "Expected Right value to be of type <%s> but was <%s>",
              type.getName(), value == null ? "null" : value.getClass().getName())
          .isInstanceOf(type);
      return this;
    }

    /** Verifies that the Left value is an instance of the specified type. */
    public EitherTOptionalAssert<F, E, A> hasLeftValueOfType(Class<?> type) {
      isPresentLeft();
      E error = unwrap().get().getLeft();
      Assertions.assertThat(error)
          .withFailMessage(
              "Expected Left value to be of type <%s> but was <%s>",
              type.getName(), error == null ? "null" : error.getClass().getName())
          .isInstanceOf(type);
      return this;
    }

    /** Verifies that this EitherT is equal to another EitherT by comparing unwrapped values. */
    public EitherTOptionalAssert<F, E, A> isEqualToEitherT(
        Kind<EitherTKind.Witness<F, E>, A> other) {
      isNotNull();
      Assertions.assertThat(other).as("other EitherT").isNotNull();
      var otherEitherT = EitherTKindHelper.EITHER_T.narrow(other);
      Kind<F, Either<E, A>> otherOuterKind = otherEitherT.value();
      Optional<Either<E, A>> otherUnwrapped = outerUnwrapper.apply(otherOuterKind);
      Optional<Either<E, A>> thisUnwrapped = unwrap();
      Assertions.assertThat(thisUnwrapped)
          .withFailMessage(
              "Expected EitherT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped)
          .isEqualTo(otherUnwrapped);
      return this;
    }
  }
}

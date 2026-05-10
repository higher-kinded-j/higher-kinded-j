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
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeTKind;
import org.higherkindedj.hkt.maybe_t.MaybeTKindHelper;

/** Custom AssertJ assertion for MaybeT transformer types. */
public final class MaybeTAssert {

  private MaybeTAssert() {}

  /** Entry point for MaybeT assertions when the outer monad unwraps to Optional. */
  public static <F extends WitnessArity<TypeArity.Unary>, A>
      MaybeTOptionalAssert<F, A> assertThatMaybeT(
          Kind<MaybeTKind.Witness<F>, A> actual,
          Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper) {
    return new MaybeTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for MaybeT with an Optional-shaped outer monad. */
  public static class MaybeTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<MaybeTOptionalAssert<F, A>, Kind<MaybeTKind.Witness<F>, A>> {

    private final Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper;

    protected MaybeTOptionalAssert(
        Kind<MaybeTKind.Witness<F>, A> actual,
        Function<Kind<F, Maybe<A>>, Optional<Maybe<A>>> outerUnwrapper) {
      super(actual, MaybeTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Maybe<A>> unwrap() {
      var maybeT = MaybeTKindHelper.MAYBE_T.narrow(actual);
      return outerUnwrapper.apply(maybeT.value());
    }

    /** Verifies that the outer monad is empty. */
    public MaybeTOptionalAssert<F, A> isEmpty() {
      isNotNull();
      Optional<Maybe<A>> u = unwrap();
      Assertions.assertThat(u)
          .withFailMessage(
              () ->
                  "Expected outer monad to be empty but was present with: <" + u.orElse(null) + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the outer monad is present (not empty). */
    public MaybeTOptionalAssert<F, A> isPresent() {
      isNotNull();
      Assertions.assertThat(unwrap())
          .withFailMessage("Expected outer monad to be present but was empty")
          .isPresent();
      return this;
    }

    /** Verifies that the outer monad is present and contains a Just value. */
    public MaybeTOptionalAssert<F, A> isPresentJust() {
      isPresent();
      Maybe<A> maybe = unwrap().get();
      Assertions.assertThat(maybe.isJust())
          .withFailMessage("Expected outer monad to contain Just but was Nothing")
          .isTrue();
      return this;
    }

    /** Verifies that the outer monad is present and contains Nothing. */
    public MaybeTOptionalAssert<F, A> isPresentNothing() {
      isPresent();
      Maybe<A> maybe = unwrap().get();
      Assertions.assertThat(maybe.isNothing())
          .withFailMessage(
              () ->
                  "Expected outer monad to contain Nothing but was Just with: <"
                      + maybe.get()
                      + ">")
          .isTrue();
      return this;
    }

    /** Verifies that the MaybeT contains a Just value equal to the expected value. */
    public MaybeTOptionalAssert<F, A> hasJustValue(A expected) {
      isPresentJust();
      A actualV = unwrap().get().get();
      Assertions.assertThat(actualV)
          .withFailMessage("Expected Just value to be <%s> but was <%s>", expected, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the MaybeT contains a Just value satisfying the given requirements. */
    public MaybeTOptionalAssert<F, A> satisfiesJust(Consumer<? super A> requirements) {
      isPresentJust();
      requirements.accept(unwrap().get().get());
      return this;
    }

    /** Verifies that the Just value matches the given predicate. */
    public MaybeTOptionalAssert<F, A> justMatches(Predicate<? super A> predicate) {
      isPresentJust();
      A value = unwrap().get().get();
      Assertions.assertThat(predicate.test(value))
          .withFailMessage("Just value <%s> did not match predicate", value)
          .isTrue();
      return this;
    }

    /** Verifies that the Just value is not null. */
    public MaybeTOptionalAssert<F, A> hasNonNullJustValue() {
      isPresentJust();
      Assertions.assertThat(unwrap().get().get()).as("Just value").isNotNull();
      return this;
    }

    /** Verifies that the Just value is an instance of the specified type. */
    public MaybeTOptionalAssert<F, A> hasJustValueOfType(Class<?> type) {
      isPresentJust();
      // Maybe.Just guarantees a non-null value, so we never need a null branch in the message.
      A value = unwrap().get().get();
      Assertions.assertThat(value)
          .withFailMessage(
              "Expected Just value to be of type <%s> but was <%s>",
              type.getName(), value.getClass().getName())
          .isInstanceOf(type);
      return this;
    }

    /** Verifies that this MaybeT is equal to another MaybeT by comparing unwrapped values. */
    public MaybeTOptionalAssert<F, A> isEqualToMaybeT(Kind<MaybeTKind.Witness<F>, A> other) {
      isNotNull();
      Assertions.assertThat(other).as("other MaybeT").isNotNull();
      var otherMaybeT = MaybeTKindHelper.MAYBE_T.narrow(other);
      Optional<Maybe<A>> otherUnwrapped = outerUnwrapper.apply(otherMaybeT.value());
      Optional<Maybe<A>> thisUnwrapped = unwrap();
      Assertions.assertThat(thisUnwrapped)
          .withFailMessage(
              "Expected MaybeT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped)
          .isEqualTo(otherUnwrapped);
      return this;
    }
  }
}

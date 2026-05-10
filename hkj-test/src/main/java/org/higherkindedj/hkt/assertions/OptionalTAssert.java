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
import org.higherkindedj.hkt.optional_t.OptionalTKind;
import org.higherkindedj.hkt.optional_t.OptionalTKindHelper;

/** Custom AssertJ assertion for OptionalT transformer types. */
public final class OptionalTAssert {

  private OptionalTAssert() {}

  /** Entry point for OptionalT assertions when the outer monad unwraps to Optional. */
  public static <F extends WitnessArity<TypeArity.Unary>, A>
      OptionalTOptionalAssert<F, A> assertThatOptionalT(
          Kind<OptionalTKind.Witness<F>, A> actual,
          Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper) {
    return new OptionalTOptionalAssert<>(actual, outerUnwrapper);
  }

  /** Specialised assertion for OptionalT with an Optional-shaped outer monad. */
  public static class OptionalTOptionalAssert<F extends WitnessArity<TypeArity.Unary>, A>
      extends AbstractAssert<OptionalTOptionalAssert<F, A>, Kind<OptionalTKind.Witness<F>, A>> {

    private final Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper;

    protected OptionalTOptionalAssert(
        Kind<OptionalTKind.Witness<F>, A> actual,
        Function<Kind<F, Optional<A>>, Optional<Optional<A>>> outerUnwrapper) {
      super(actual, OptionalTOptionalAssert.class);
      this.outerUnwrapper = outerUnwrapper;
    }

    private Optional<Optional<A>> unwrap() {
      var optionalT = OptionalTKindHelper.OPTIONAL_T.narrow(actual);
      return outerUnwrapper.apply(optionalT.value());
    }

    /** Verifies that the outer monad is empty. */
    public OptionalTOptionalAssert<F, A> isEmpty() {
      isNotNull();
      Optional<Optional<A>> u = unwrap();
      Assertions.assertThat(u)
          .withFailMessage(
              () ->
                  "Expected outer monad to be empty but was present with: <" + u.orElse(null) + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the outer monad is present (not empty). */
    public OptionalTOptionalAssert<F, A> isPresent() {
      isNotNull();
      Assertions.assertThat(unwrap())
          .withFailMessage("Expected outer monad to be present but was empty")
          .isPresent();
      return this;
    }

    /** Verifies that the outer monad is present and the inner Optional contains a value. */
    public OptionalTOptionalAssert<F, A> isPresentSome() {
      isPresent();
      Assertions.assertThat(unwrap().get())
          .withFailMessage("Expected outer monad to contain Some but was None")
          .isPresent();
      return this;
    }

    /** Verifies that the outer monad is present and the inner Optional is empty. */
    public OptionalTOptionalAssert<F, A> isPresentNone() {
      isPresent();
      Optional<A> inner = unwrap().get();
      Assertions.assertThat(inner)
          .withFailMessage(
              () ->
                  "Expected outer monad to contain None but was Some with: <"
                      + inner.orElse(null)
                      + ">")
          .isEmpty();
      return this;
    }

    /** Verifies that the OptionalT contains a Some value equal to the expected value. */
    public OptionalTOptionalAssert<F, A> hasSomeValue(A expected) {
      isPresentSome();
      A actualV = unwrap().get().get();
      Assertions.assertThat(actualV)
          .withFailMessage("Expected Some value to be <%s> but was <%s>", expected, actualV)
          .isEqualTo(expected);
      return this;
    }

    /** Verifies that the OptionalT contains a Some value satisfying the given requirements. */
    public OptionalTOptionalAssert<F, A> satisfiesSome(Consumer<? super A> requirements) {
      isPresentSome();
      requirements.accept(unwrap().get().get());
      return this;
    }

    /** Verifies that the Some value matches the given predicate. */
    public OptionalTOptionalAssert<F, A> someMatches(Predicate<? super A> predicate) {
      isPresentSome();
      A value = unwrap().get().get();
      Assertions.assertThat(predicate.test(value))
          .withFailMessage("Some value <%s> did not match predicate", value)
          .isTrue();
      return this;
    }

    /** Verifies that the Some value is not null. */
    public OptionalTOptionalAssert<F, A> hasNonNullSomeValue() {
      isPresentSome();
      Assertions.assertThat(unwrap().get().get()).as("Some value").isNotNull();
      return this;
    }

    /** Verifies that the Some value is an instance of the specified type. */
    public OptionalTOptionalAssert<F, A> hasSomeValueOfType(Class<?> type) {
      isPresentSome();
      // Optional.Some guarantees a non-null value, so no null branch is needed in the message.
      A value = unwrap().get().get();
      Assertions.assertThat(value)
          .withFailMessage(
              "Expected Some value to be of type <%s> but was <%s>",
              type.getName(), value.getClass().getName())
          .isInstanceOf(type);
      return this;
    }

    /** Verifies that this OptionalT is equal to another OptionalT by comparing unwrapped values. */
    public OptionalTOptionalAssert<F, A> isEqualToOptionalT(
        Kind<OptionalTKind.Witness<F>, A> other) {
      isNotNull();
      Assertions.assertThat(other).as("other OptionalT").isNotNull();
      var otherOptionalT = OptionalTKindHelper.OPTIONAL_T.narrow(other);
      Optional<Optional<A>> otherUnwrapped = outerUnwrapper.apply(otherOptionalT.value());
      Optional<Optional<A>> thisUnwrapped = unwrap();
      Assertions.assertThat(thisUnwrapped)
          .withFailMessage(
              "Expected OptionalT to be equal to <%s> but was <%s>", otherUnwrapped, thisUnwrapped)
          .isEqualTo(otherUnwrapped);
      return this;
    }
  }
}

// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.maybe;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.Nullable;

/**
 * Enum implementing {@link MaybeConverterOps} for widen/narrow operations, and providing additional
 * factory instance methods for {@link Maybe} types.
 *
 * <p>Access these operations via the singleton {@code MAYBE}. For example: {@code
 * MaybeKindHelper.MAYBE.widen(Maybe.just("value"));} Or, with static import: {@code import static
 * org.higherkindedj.hkt.maybe.MaybeKindHelper.MAYBE; MAYBE.widen(...);}
 */
public enum MaybeKindHelper implements MaybeConverterOps {
  MAYBE; // Singleton instance named MAYBE

  /** Error message for when a null Kind is passed to narrow. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot narrow null Kind for Maybe";

  /** Error message prefix for when the Kind instance is not the expected MaybeHolder type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeHolder: ";

  /** Error message for when a MaybeHolder internally contains a null Maybe, which is invalid. */
  public static final String INVALID_HOLDER_STATE_MSG = "MaybeHolder contained null Maybe instance";

  /**
   * Internal record implementing {@link MaybeKind} to hold the concrete {@link Maybe} instance.
   * Changed to package-private for potential test access.
   *
   * @param <A> The type of the value potentially held by the {@code maybe}.
   * @param maybe The {@link Maybe} instance being wrapped.
   */
  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> {}

  /**
   * Widens a concrete {@link Maybe}&lt;A&gt; instance into its HKT representation, {@link
   * Kind}&lt;{@link MaybeKind.Witness}, A&gt;. Implements {@link MaybeConverterOps#widen}.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param maybe The concrete {@link Maybe}&lt;A&gt; instance to widen. Must be non-null.
   * @return The {@link Kind<MaybeKind.Witness, A>} representation of the input {@code maybe}.
   * @throws NullPointerException if {@code maybe} is {@code null}.
   */
  @Override
  public <A> Kind<MaybeKind.Witness, A> widen(Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for widen");
    return new MaybeHolder<>(maybe);
  }

  /**
   * Narrows a {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt; back to its concrete {@link
   * Maybe}&lt;A&gt; representation. Implements {@link MaybeConverterOps#narrow}.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param kind The {@code Kind} instance to narrow. May be {@code null}.
   * @return The underlying, non-null {@link Maybe}&lt;A&gt; instance.
   * @throws KindUnwrapException if {@code kind} is {@code null}, if {@code kind} is not an instance
   *     of {@link MaybeHolder}, or if the {@code MaybeHolder} internally contains a {@code null}
   *     {@link Maybe} instance.
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A> Maybe<A> narrow(@Nullable Kind<MaybeKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case MaybeKindHelper.MaybeHolder<?> holder -> {
        Maybe<?> maybe = holder.maybe();
        if (maybe == null) {
          // This state implies incorrect construction of MaybeHolder if it bypassed 'widen'
          // or if MaybeHolder's contract regarding non-null 'maybe' was violated.
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // The cast is safe if Kind<MaybeKind.Witness, A> correctly corresponds to Maybe<A>.
        yield (Maybe<A>) maybe;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * A convenience factory method that creates a {@link Maybe#just(Object)} from the given non-null
   * value and then wraps it into a {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt;.
   *
   * @param <A> The element type. The provided {@code value} must conform to this type.
   * @param value The non-null value to be wrapped in a {@link Just} and then as a {@link Kind}.
   * @return A {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt; representing {@code Just(value)}.
   * @throws NullPointerException if {@code value} is {@code null}.
   */
  public <A> Kind<MaybeKind.Witness, A> just(A value) {
    return this.widen(Maybe.just(value));
  }

  /**
   * A convenience factory method that retrieves the singleton {@link Maybe#nothing()} instance and
   * wraps it into a {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt;.
   *
   * @param <A> The phantom element type for the {@code Nothing} state.
   * @return A {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt; representing {@code Nothing}.
   */
  public <A> Kind<MaybeKind.Witness, A> nothing() {
    return this.widen(Maybe.nothing());
  }
}

package org.higherkindedj.hkt.maybe;

import java.util.Objects;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing static helper methods for working with {@link MaybeKind} in the context
 * of Higher-Kinded Type (HKT) simulation.
 *
 * <p>This class facilitates the conversion between the concrete {@link Maybe} type and its HKT
 * representation {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt; (which is what {@link
 * MaybeKind}&lt;A&gt; effectively is). It offers methods for:
 *
 * <ul>
 *   <li>Wrapping a {@link Maybe} into a {@link MaybeKind}.
 *   <li>Unwrapping a {@link MaybeKind} back to a {@link Maybe}.
 *   <li>Convenience factory methods for creating {@link MaybeKind} instances representing {@link
 *       Just} or {@link Nothing}.
 * </ul>
 *
 * This class is final and cannot be instantiated.
 *
 * @see Maybe
 * @see MaybeKind
 * @see Kind
 * @see MaybeKind.Witness
 */
public final class MaybeKindHelper {

  /** Error message for when a null Kind is passed to unwrap. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Maybe";

  /** Error message prefix for when the Kind instance is not the expected MaybeHolder type. */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a MaybeHolder: ";

  /** Error message for when a MaybeHolder internally contains a null Maybe, which is invalid. */
  public static final String INVALID_HOLDER_STATE_MSG = "MaybeHolder contained null Maybe instance";

  private MaybeKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * The concrete implementation of {@link MaybeKind} that holds the actual {@link Maybe} instance.
   * This record is used internally by the helper methods to bridge between {@link Maybe} and {@link
   * Kind}.
   *
   * @param <A> The type of the value potentially held by the {@code maybe}.
   * @param maybe The {@link Maybe} instance being wrapped. While {@link #wrap(Maybe)} ensures this
   *     is non-null upon creation through standard means, the record component itself is not marked
   *     {@code @NonNull} to allow for testing scenarios (e.g., reflection) and to emphasize the
   *     check within {@link #unwrap(Kind)}.
   */
  record MaybeHolder<A>(Maybe<A> maybe) implements MaybeKind<A> {}

  /**
   * Unwraps a {@link Kind}&lt;{@link MaybeKind.Witness}, A&gt; (which is effectively a {@link
   * MaybeKind}&lt;A&gt;) back to its concrete {@link Maybe}&lt;A&gt; representation.
   *
   * <p>This method expects the provided {@code kind} to be an instance of {@link MaybeHolder} that
   * was created by this helper class, containing a valid (non-null) {@link Maybe} instance.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param kind The {@code Kind} instance to unwrap. May be {@code null}, in which case an
   *     exception is thrown. It should be a {@code MaybeKind<A>} (specifically a {@code
   *     MaybeHolder<A>}).
   * @return The underlying, non-null {@link Maybe}&lt;A&gt; instance.
   * @throws KindUnwrapException if {@code kind} is {@code null}, if {@code kind} is not an instance
   *     of {@link MaybeHolder}, or if the {@code MaybeHolder} internally contains a {@code null}
   *     {@link Maybe} instance (which indicates an invalid state).
   */
  public static <A> @NonNull Maybe<A> unwrap(@Nullable Kind<MaybeKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case MaybeKindHelper.MaybeHolder<A> holder -> {
        Maybe<A> maybe = holder.maybe();
        if (maybe == null) {
          // This state implies incorrect construction of MaybeHolder,
          // as `wrap` methods ensure the inner Maybe is non-null.
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        yield maybe;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Maybe}&lt;A&gt; instance into its HKT representation, {@link
   * MaybeKind}&lt;A&gt;. The input {@code maybe} instance must not be {@code null}.
   *
   * @param <A> The element type of the {@code Maybe}.
   * @param maybe The concrete {@link Maybe}&lt;A&gt; instance to wrap. Must be non-null.
   * @return The {@link MaybeKind}&lt;A&gt; representation of the input {@code maybe}. Will not be
   *     {@code null}.
   * @throws NullPointerException if {@code maybe} is {@code null}.
   */
  public static <A> @NonNull MaybeKind<A> wrap(@NonNull Maybe<A> maybe) {
    Objects.requireNonNull(maybe, "Input Maybe cannot be null for wrap");
    return new MaybeHolder<>(maybe);
  }

  /**
   * A convenience factory method that creates a {@link Maybe#just(Object)} from the given non-null
   * value and then wraps it into a {@link MaybeKind}&lt;A&gt;.
   *
   * @param <A> The element type. The provided {@code value} must conform to this type.
   * @param value The non-null value to be wrapped in a {@link Just} and then as a {@link
   *     MaybeKind}. Must not be {@code null}.
   * @return A {@link MaybeKind}&lt;A&gt; representing {@code Just(value)}. Will not be {@code
   *     null}.
   * @throws NullPointerException if {@code value} is {@code null}, consistent with {@link
   *     Maybe#just(Object)}.
   */
  public static <A> @NonNull MaybeKind<A> just(@NonNull A value) {
    // Relies on Maybe.just(value) to throw NullPointerException if value is null.
    return wrap(Maybe.just(value));
  }

  /**
   * A convenience factory method that retrieves the singleton {@link Maybe#nothing()} instance and
   * wraps it into a {@link MaybeKind}&lt;A&gt;.
   *
   * @param <A> The phantom element type for the {@code Nothing} state.
   * @return A {@link MaybeKind}&lt;A&gt; representing {@code Nothing}. Will not be {@code null}.
   */
  public static <A> @NonNull MaybeKind<A> nothing() {
    return wrap(Maybe.nothing());
  }
}

package org.higherkindedj.hkt.trymonad;

import java.util.Objects;
import java.util.function.Supplier;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * A utility class providing static helper methods for working with {@link Try} in the context of
 * Higher-Kinded Type (HKT) simulation.
 *
 * <p>This class facilitates the conversion between the concrete {@link Try} type and its HKT
 * representation {@link Kind}&lt;{@link TryKind.Witness}, A&gt; (which is what {@link
 * TryKind}&lt;A&gt; effectively is). It offers methods for:
 *
 * <ul>
 *   <li>Wrapping a {@link Try} into a {@link TryKind}.
 *   <li>Unwrapping a {@link TryKind} back to a {@link Try}.
 *   <li>Convenience factory methods for creating {@link TryKind} instances representing {@link
 *       Try.Success} or {@link Try.Failure}, or from a {@link Supplier}.
 * </ul>
 *
 * The HKT marker (witness type) used for {@code Try} is {@link TryKind.Witness}. This class is
 * final and cannot be instantiated.
 *
 * @see Try
 * @see TryKind
 * @see TryKind.Witness
 * @see Kind
 */
public final class TryKindHelper {

  // Error Messages
  /** Error message for when a null {@link Kind} is passed to {@link #unwrap(Kind)}. */
  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Try";

  /**
   * Error message prefix for when the {@link Kind} instance is not the expected {@link TryHolder}
   * type.
   */
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not a TryHolder: ";

  /**
   * Error message for when a {@link TryHolder} internally contains a null {@link Try} instance,
   * which is invalid.
   */
  public static final String INVALID_HOLDER_STATE_MSG = "TryHolder contained null Try instance";

  private TryKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * An internal record that implements {@link TryKind}&lt;A&gt; to hold the concrete {@link
   * Try}&lt;A&gt; instance.
   *
   * <p>This serves as the carrier for {@code Try} objects within the HKT simulation. By
   * implementing {@code TryKind<A>}, it also conforms to {@code Kind<TryKind.Witness, A>}, allowing
   * it to be used with generic HKT abstractions.
   *
   * @param <A> The result type of the {@link Try} computation (e.g., the type of the value in a
   *     {@link Try.Success}).
   * @param tryInstance The actual {@link Try}&lt;A&gt; instance being wrapped. While {@link
   *     #wrap(Try)} enforces that this is non-null upon creation through standard means, this
   *     Javadoc notes that direct instantiation or reflection could bypass this, hence the check in
   *     {@link #unwrap(Kind)}.
   */
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> {}

  /**
   * Unwraps a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; (which is effectively a {@link
   * TryKind}&lt;A&gt;) back to its concrete {@link Try}&lt;A&gt; representation.
   *
   * <p>This method expects the provided {@code kind} to be an instance of {@link TryHolder} that
   * was created by this helper class, containing a valid (non-null) {@link Try} instance. The
   * {@code @SuppressWarnings("unchecked")} is used because the type {@code A} of the inner {@code
   * Try} is recovered via a cast from {@code Try<?>} (obtained from {@code TryHolder<?>}). This
   * cast is considered safe under the assumption that {@code Kind<TryKind.Witness, A>} was
   * correctly constructed to correspond to a {@code Try<A>}.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param kind The {@code Kind<TryKind.Witness, A>} instance to unwrap. May be {@code null}, in
   *     which case an exception is thrown.
   * @return The underlying, non-null {@link Try}&lt;A&gt; instance.
   * @throws KindUnwrapException if {@code kind} is {@code null}, if {@code kind} is not an instance
   *     of {@link TryHolder}, or if the {@code TryHolder} internally contains a {@code null} {@link
   *     Try} instance (which indicates an invalid state).
   */
  @SuppressWarnings("unchecked")
  public static <A> @NonNull Try<A> unwrap(@Nullable Kind<TryKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      // Pattern match ensures 'holder' is a TryHolder. Its type parameter is wildcarded here.
      case TryKindHelper.TryHolder<?> holder -> {
        Try<?> internalTry = holder.tryInstance(); // This is Try<?>
        if (internalTry == null) {
          // This state implies incorrect construction of TryHolder,
          // as `wrap` methods ensure the inner Try is non-null.
          throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
        }
        // This cast is safe if the Kind<TryKind.Witness, A> was correctly formed
        // such that the A in Kind matches the A in the underlying Try<A>.
        yield (Try<A>) internalTry;
      }
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  /**
   * Wraps a concrete {@link Try}&lt;A&gt; instance into its HKT representation, {@link
   * TryKind}&lt;A&gt;.
   *
   * <p>The returned {@code TryKind<A>} also implements {@code Kind<TryKind.Witness, A>}, making it
   * usable in generic HKT contexts. The input {@code tryInstance} must not be {@code null}.
   *
   * @param <A> The result type of the {@code Try} computation.
   * @param tryInstance The concrete {@link Try}&lt;A&gt; instance to wrap. Must be non-null.
   * @return A non-null {@link TryKind}&lt;A&gt; representing the wrapped {@code Try} computation.
   * @throws NullPointerException if {@code tryInstance} is {@code null}.
   */
  public static <A> @NonNull TryKind<A> wrap(@NonNull Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Creates a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing a successful computation
   * with the given value. This is equivalent to calling {@link Try#success(Object)} and then {@link
   * #wrap(Try)}.
   *
   * @param <A> The type of the successful value.
   * @param value The successful value. May be {@code null} if {@code A} is a nullable type and
   *     {@link Try.Success} can hold nulls (depends on {@link Try} implementation).
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the successful computation.
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> success(@Nullable A value) {
    return wrap(Try.success(value));
  }

  /**
   * Creates a {@link Kind}&lt;{@link TryKind.Witness}, A&gt; representing a failed computation with
   * the given {@link Throwable}. This is equivalent to calling {@link Try#failure(Throwable)} and
   * then {@link #wrap(Try)}.
   *
   * @param <A> The phantom type parameter representing the value type of the {@code Try}, as a
   *     failure does not hold a value of type {@code A}.
   * @param throwable The non-null {@link Throwable} representing the failure.
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the failed computation.
   * @throws NullPointerException if {@code throwable} is {@code null} (this check is typically
   *     delegated to {@link Try#failure(Throwable)}).
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> failure(@NonNull Throwable throwable) {
    // Try.failure should handle null check for throwable
    return wrap(Try.failure(throwable));
  }

  /**
   * Executes a {@link Supplier} and wraps its outcome (a successfully returned value or an
   * exception thrown) into a {@link Kind}&lt;{@link TryKind.Witness}, A&gt;. This is equivalent to
   * calling {@link Try#of(Supplier)} and then {@link #wrap(Try)}.
   *
   * @param <A> The type of the value supplied by the {@code supplier}.
   * @param supplier The non-null {@link Supplier} to execute. Its {@code get} method may return a
   *     value or throw an exception.
   * @return A non-null {@code Kind<TryKind.Witness, A>} representing the outcome of the supplier's
   *     execution (either a {@link Try.Success} with the value or a {@link Try.Failure} with the
   *     exception).
   * @throws NullPointerException if {@code supplier} is {@code null} (this check is typically
   *     delegated to {@link Try#of(Supplier)}).
   */
  public static <A> @NonNull Kind<TryKind.Witness, A> tryOf(
      @NonNull Supplier<? extends A> supplier) {
    // Try.of should handle null check for supplier
    return wrap(Try.of(supplier));
  }
}

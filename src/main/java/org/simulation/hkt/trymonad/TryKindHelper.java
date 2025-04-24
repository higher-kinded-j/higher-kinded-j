package org.simulation.hkt.trymonad;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Kind;
import java.util.Objects;
import java.util.function.Supplier;


public final class TryKindHelper {


  private TryKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  /**
   * Unwraps a TryKind back to the concrete Try<A> type.
   * Returns Failure if the Kind is null, not a TryHolder, or the holder contains null.
   * @param kind The Kind instance (Nullable).
   * @return The underlying Try or a Failure instance (NonNull).
   */
  @SuppressWarnings("unchecked") // For casting TryHolder
  public static <A> @NonNull Try<A> unwrap(@Nullable Kind<TryKind<?>, A> kind) {
    return switch(kind) {
      case TryHolder<?> holder -> {
        // Explicitly check if the held Try is null
        Try<?> heldTry = holder.tryInstance();
        if (heldTry == null) {
          yield Try.failure(new NullPointerException("TryHolder contained null Try instance"));
        } else {
          // Safe cast because TryHolder<X> implements TryKind<X>
          yield (Try<A>) heldTry;
        }
      }
      case null -> Try.failure(new NullPointerException("Cannot unwrap null Kind for Try"));
      default -> Try.failure(new IllegalArgumentException("Kind instance is not a TryHolder: " + kind.getClass().getName()));
    };
  }


  /**
   * Wraps a concrete Try<A> value into the TryKind simulation type.
   * Requires a non-null Try instance as input.
   * @param tryInstance The Try instance to wrap (NonNull).
   * @return The wrapped TryKind (NonNull).
   */
  public static <A> @NonNull TryKind<A> wrap(@NonNull Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Wraps a successful value directly into TryKind.
   * @param value The value (Nullable).
   * @return The wrapped TryKind (NonNull).
   */
  public static <A> @NonNull Kind<TryKind<?>, A> success(@Nullable A value) {
    return wrap(Try.success(value));
  }

  /**
   * Wraps a failure directly into TryKind.
   * @param throwable The exception (NonNull).
   * @return The wrapped TryKind (NonNull).
   */
  public static <A> @NonNull Kind<TryKind<?>, A> failure(@NonNull Throwable throwable) {
    return wrap(Try.failure(throwable));
  }

  /**
   * Executes a supplier and wraps the result or exception in TryKind.
   * @param supplier The supplier (NonNull).
   * @return The wrapped TryKind (NonNull).
   */
  public static <A> @NonNull Kind<TryKind<?>, A> tryOf(@NonNull Supplier<? extends A> supplier) {
    return wrap(Try.of(supplier));
  }


  // Internal holder record - field assumed NonNull based on wrap check
  record TryHolder<A>(@NonNull Try<A> tryInstance) implements TryKind<A> { }
}
package org.simulation.hkt.trymonad;

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
   */
  public static <A> Try<A> unwrap(Kind<TryKind<?>, A> kind) {
    return switch(kind) {
      case TryHolder<A> holder -> holder.tryInstance() != null
          ? holder.tryInstance()
          : Try.failure(new NullPointerException("TryHolder contained null Try instance"));
      case null -> Try.failure(new NullPointerException("Cannot unwrap null Kind for Try"));
      default -> Try.failure(new IllegalArgumentException("Kind instance is not a TryHolder: " + kind.getClass().getName()));
    };
  }

  /**
   * Wraps a concrete Try<A> value into the TryKind simulation type.
   * Requires a non-null Try instance as input.
   */
  public static <A> TryKind<A> wrap(Try<A> tryInstance) {
    Objects.requireNonNull(tryInstance, "Input Try cannot be null for wrap");
    return new TryHolder<>(tryInstance);
  }

  /**
   * Wraps a successful value directly into TryKind.
   */
  public static <A> Kind<TryKind<?>, A> success(A value) {
    return wrap(Try.success(value));
  }

  /**
   * Wraps a failure directly into TryKind.
   */
  public static <A> Kind<TryKind<?>, A> failure(Throwable throwable) {
    return wrap(Try.failure(throwable));
  }

  /**
   * Executes a supplier and wraps the result or exception in TryKind.
   */
  public static <A> Kind<TryKind<?>, A> tryOf(Supplier<? extends A> supplier) {
    return wrap(Try.of(supplier));
  }


  // Internal holder record
  record TryHolder<A>(Try<A> tryInstance) implements TryKind<A> { }
}

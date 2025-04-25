package org.simulation.hkt.io;

import org.simulation.hkt.Kind;
import org.simulation.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Objects;
import java.util.function.Supplier;


public final class IOKindHelper {

  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for IO";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an IOHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG = "IOHolder contained null IO instance";

  private IOKindHelper() {
    // prevent instantiation via reflection
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  // Internal holder
  record IOHolder<A>(@NonNull IO<A> ioInstance) implements IOKind<A> {}

  // Wrap
  public static <A> @NonNull IOKind<A> wrap(@NonNull IO<A> io) {
    Objects.requireNonNull(io, "Input IO cannot be null for wrap");
    return new IOHolder<>(io);
  }

  // Unwrap
  @SuppressWarnings("unchecked")
  public static <A> @NonNull IO<A> unwrap(@Nullable Kind<IOKind<?>, A> kind) {
    if (kind == null) {
      throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
    }
    if (kind instanceof IOHolder<?> holder) {
      IO<?> internalIO = holder.ioInstance();
      if (internalIO == null) {
        throw new KindUnwrapException(INVALID_HOLDER_STATE_MSG);
      }
      return (IO<A>) internalIO; // Cast is safe here
    } else {
      throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    }
  }

  // Convenience factory using IO.delay
  public static <A> @NonNull Kind<IOKind<?>, A> delay(@NonNull Supplier<A> thunk) {
    return wrap(IO.delay(thunk));
  }

  // Convenience for running the IO via the Kind
  public static <A> A unsafeRunSync(@NonNull Kind<IOKind<?>, A> kind) {
    return unwrap(kind).unsafeRunSync();
  }
}
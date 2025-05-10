package org.higherkindedj.hkt.optional;

import static java.util.Objects.requireNonNull;

import java.util.Optional;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.exception.KindUnwrapException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public final class OptionalKindHelper {

  public static final String INVALID_KIND_NULL_MSG = "Cannot unwrap null Kind for Optional";
  public static final String INVALID_KIND_TYPE_MSG = "Kind instance is not an OptionalHolder: ";
  public static final String INVALID_HOLDER_STATE_MSG =
      "OptionalHolder contained null Optional instance";

  private OptionalKindHelper() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  record OptionalHolder<A>(@NonNull Optional<A> optional) implements OptionalKind<A> {
    OptionalHolder {
      requireNonNull(optional, "Wrapped Optional instance cannot be null in OptionalHolder");
    }
  }

  @SuppressWarnings("unchecked")
  public static <A> @NonNull Optional<A> unwrap(@Nullable Kind<OptionalKind.Witness, A> kind) {
    return switch (kind) {
      case null -> throw new KindUnwrapException(INVALID_KIND_NULL_MSG);
      case OptionalKindHelper.OptionalHolder<?>(var optional) -> (Optional<A>) optional;
      default -> throw new KindUnwrapException(INVALID_KIND_TYPE_MSG + kind.getClass().getName());
    };
  }

  public static <A> @NonNull OptionalKind<A> wrap(@NonNull Optional<A> optional) {
    requireNonNull(optional, "Input Optional cannot be null for wrap");
    return new OptionalHolder<>(optional);
  }
}

package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class OptionalFunctor implements Functor<OptionalKind<?>> {

  /**
   * Maps a function over the value contained in the OptionalKind. If the OptionalKind is empty, or
   * if the function f returns null, the result is an empty OptionalKind.
   *
   * @param f The function to apply. (NonNull, but allowed to return null)
   * @param fa The OptionalKind input. (NonNull)
   * @param <A> The input value type.
   * @param <B> The output value type.
   * @return An OptionalKind containing the result of applying f, or empty. (NonNull)
   */
  @Override
  public <A, B> @NonNull OptionalKind<B> map(
      @NonNull Function<A, @Nullable B> f, @NonNull Kind<OptionalKind<?>, A> fa) {

    Optional<A> optionalA = unwrap(fa);

    if (optionalA.isEmpty()) {
      return wrap(Optional.empty());
    } else {
      A a = optionalA.get();
      B b = f.apply(a);
      if (b == null) {
        return wrap(Optional.empty());
      } else {
        return wrap(Optional.of(b));
      }
    }
  }
}

package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class OptionalFunctor implements Functor<OptionalKind.Witness> {

  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> map(
      @NonNull Function<A, @Nullable B> f, @NonNull Kind<OptionalKind.Witness, A> fa) {
    Optional<A> optionalA = unwrap(fa);
    Optional<B> resultOptional = optionalA.map(f);
    return wrap(resultOptional);
  }
}

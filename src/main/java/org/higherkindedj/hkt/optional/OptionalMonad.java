package org.higherkindedj.hkt.optional;

import static org.higherkindedj.hkt.optional.OptionalKindHelper.*;

import java.util.Optional;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class OptionalMonad extends OptionalFunctor
    implements MonadError<OptionalKind.Witness, Void> {

  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> of(@Nullable A value) {
    return wrap(Optional.ofNullable(value));
  }

  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> flatMap(
      @NonNull Function<A, Kind<OptionalKind.Witness, B>> f,
      @NonNull Kind<OptionalKind.Witness, A> ma) {
    Optional<A> optA = unwrap(ma);
    Optional<B> resultOpt =
        optA.flatMap(
            a -> {
              Kind<OptionalKind.Witness, B> kindB = f.apply(a);
              return unwrap(kindB);
            });
    return wrap(resultOpt);
  }

  @Override
  public <A, B> @NonNull Kind<OptionalKind.Witness, B> ap(
      @NonNull Kind<OptionalKind.Witness, Function<A, B>> ff,
      @NonNull Kind<OptionalKind.Witness, A> fa) {
    Optional<Function<A, B>> optF = unwrap(ff);
    Optional<A> optA = unwrap(fa);
    Optional<B> resultOpt = optF.flatMap(optA::map);
    return wrap(resultOpt);
  }

  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> raiseError(@Nullable Void error) {
    return wrap(Optional.empty());
  }

  @Override
  public <A> @NonNull Kind<OptionalKind.Witness, A> handleErrorWith(
      @NonNull Kind<OptionalKind.Witness, A> ma,
      @NonNull Function<Void, Kind<OptionalKind.Witness, A>> handler) {
    Optional<A> optional = unwrap(ma);
    if (optional.isEmpty()) {
      return handler.apply(null);
    } else {
      return ma;
    }
  }
}

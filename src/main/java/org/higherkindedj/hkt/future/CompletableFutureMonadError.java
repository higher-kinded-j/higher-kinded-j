package org.higherkindedj.hkt.future;

import static org.higherkindedj.hkt.future.CompletableFutureKindHelper.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.jspecify.annotations.NonNull;

/** MonadError implementation for CompletableFutureKind. The error type E is Throwable. */
public class CompletableFutureMonadError extends CompletableFutureMonad
    implements MonadError<CompletableFutureKind<?>, Throwable> {

  @Override
  public <A> @NonNull Kind<CompletableFutureKind<?>, A> raiseError(@NonNull Throwable error) {
    return wrap(CompletableFuture.failedFuture(error));
  }

  @Override
  public <A> @NonNull Kind<CompletableFutureKind<?>, A> handleErrorWith(
      @NonNull Kind<CompletableFutureKind<?>, A> ma,
      @NonNull Function<Throwable, Kind<CompletableFutureKind<?>, A>> handler) {
    CompletableFuture<A> futureA = unwrap(ma);

    // Check if the future is already successfully completed. If so, return original Kind.
    if (futureA.isDone() && !futureA.isCompletedExceptionally()) {
      return ma;
    }

    // If not successfully completed, apply the handler using exceptionallyCompose
    CompletableFuture<A> recoveredFuture =
        futureA.exceptionallyCompose(
            throwable -> {
              // Extract the real cause if wrapped in CompletionException
              Throwable cause =
                  (throwable instanceof CompletionException && throwable.getCause() != null)
                      ? throwable.getCause()
                      : throwable; // cause is NonNull
              Kind<CompletableFutureKind<?>, A> recoveryKind = handler.apply(cause);
              return unwrap(recoveryKind);
            });

    // Wrap the potentially new future returned by exceptionallyCompose
    return wrap(recoveredFuture);
  }
}

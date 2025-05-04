package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


/**
 * Monad instance for the ReaderT transformer.
 * Requires a Monad instance for the outer context F.
 * The fixed type R represents the environment.
 *
 * @param <F> The witness type of the outer Monad.
 * @param <R> The fixed environment type.
 */
public class ReaderTMonad<F, R> implements Monad<ReaderTKind<F, R, ?>> {

    private final @NonNull Monad<F> outerMonad;

    public ReaderTMonad(@NonNull Monad<F> outerMonad) {
        this.outerMonad = Objects.requireNonNull(outerMonad, "Outer Monad instance cannot be null for ReaderTMonad");
    }

    @Override
    public <A> @NonNull Kind<ReaderTKind<F, R, ?>, A> of(@Nullable A value) {
        ReaderT<F, R, A> readerT = new ReaderT<>(r -> outerMonad.of(value));
        return ReaderTKindHelper.wrap(readerT);
    }

    @Override
    public <A, B> @NonNull Kind<ReaderTKind<F, R, ?>, B> ap(
            @NonNull Kind<ReaderTKind<F, R, ?>, Function<A, B>> ff,
            @NonNull Kind<ReaderTKind<F, R, ?>, A> fa) {

        ReaderT<F, R, Function<A, B>> ffT = ReaderTKindHelper.unwrap(ff);
        ReaderT<F, R, A> faT = ReaderTKindHelper.unwrap(fa);

        // Cast is safe because constructor ensures outerMonad is a Monad, which extends Applicative
        Applicative<F> outerApplicative = (Applicative<F>) outerMonad;

        Function<R, Kind<F, B>> newRun = r -> {
            Kind<F, Function<A, B>> funcKind = ffT.run().apply(r);
            Kind<F, A> valKind = faT.run().apply(r);
            // The actual call to the outer monad's ap happens here
            return outerApplicative.ap(funcKind, valKind);
        };

        ReaderT<F, R, B> resultReaderT = new ReaderT<>(newRun);
        return ReaderTKindHelper.wrap(resultReaderT);
    }

    @Override
    public <A, B> @NonNull Kind<ReaderTKind<F, R, ?>, B> map(
            @NonNull Function<A, B> f, @NonNull Kind<ReaderTKind<F, R, ?>, A> fa) {

        ReaderT<F, R, A> faT = ReaderTKindHelper.unwrap(fa);

        Function<R, Kind<F, B>> newRun = r -> {
            Kind<F, A> kindA = faT.run().apply(r);
            // The actual call to the outer monad's map happens here
            return outerMonad.map(f, kindA);
        };

        ReaderT<F, R, B> resultReaderT = new ReaderT<>(newRun);
        return ReaderTKindHelper.wrap(resultReaderT);
    }

    @Override
    public <A, B> @NonNull Kind<ReaderTKind<F, R, ?>, B> flatMap(
            @NonNull Function<A, Kind<ReaderTKind<F, R, ?>, B>> f,
            @NonNull Kind<ReaderTKind<F, R, ?>, A> ma) {

        ReaderT<F, R, A> maT = ReaderTKindHelper.unwrap(ma);

        Function<R, Kind<F, B>> newRun = r -> {
            Kind<F, A> kindA = maT.run().apply(r); // F<A>

            Function<A, Kind<F, B>> innerFlatMapFunc = a -> {
                Kind<ReaderTKind<F, R, ?>, B> resultReaderTKind = f.apply(a); // ReaderTKind<F,R,B>
                ReaderT<F, R, B> nextReaderT = ReaderTKindHelper.unwrap(resultReaderTKind); // ReaderT<F,R,B>
                Kind<F, B> resultKindB = nextReaderT.run().apply(r); // F<B>
                return resultKindB;
            };
            // The actual call to the outer monad's flatMap happens here
            return outerMonad.flatMap(innerFlatMapFunc, kindA); // (A -> F<B>) -> F<A> -> F<B>
        };

        ReaderT<F, R, B> resultReaderT = new ReaderT<>(newRun);
        return ReaderTKindHelper.wrap(resultReaderT);
    }
}
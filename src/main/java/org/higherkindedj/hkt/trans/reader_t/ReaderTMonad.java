package org.higherkindedj.hkt.trans.reader_t;

import java.util.Objects;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monad;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;


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

        if (!(outerMonad instanceof Applicative)) {
            throw new UnsupportedOperationException("Outer Monad must be Applicative for ReaderT.ap");
        }
        Applicative<F> outerApplicative = (Applicative<F>) outerMonad;

        Function<R, Kind<F, B>> newRun = r -> {
            Kind<F, Function<A, B>> funcKind = ffT.run().apply(r);
            Kind<F, A> valKind = faT.run().apply(r);
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
            return outerMonad.map(f, kindA);
        };

        ReaderT<F, R, B> resultReaderT = new ReaderT<>(newRun);
        return ReaderTKindHelper.wrap(resultReaderT);
    }

    @Override
    public <A, B> @NonNull Kind<ReaderTKind<F, R, ?>, B> flatMap(
            @NonNull Function<A, Kind<ReaderTKind<F, R, ?>, B>> f,
            @NonNull Kind<ReaderTKind<F, R, ?>, A> ma) {

        // This is the version *before* the last debug attempt, which compiled
        // but still had runtime errors in the test.
        ReaderT<F, R, A> maT = ReaderTKindHelper.unwrap(ma);

        Function<R, Kind<F, B>> newRun = r -> {
            Kind<F, A> kindA = maT.run().apply(r); // F<A>

            Function<A, Kind<F, B>> innerFlatMapFunc = a -> {
                Kind<ReaderTKind<F, R, ?>, B> resultReaderTKind = f.apply(a); // ReaderTKind<F,R,B>
                ReaderT<F, R, B> nextReaderT = ReaderTKindHelper.unwrap(resultReaderTKind); // ReaderT<F,R,B>
                Kind<F, B> resultKindB = nextReaderT.run().apply(r); // F<B>
                // We previously added a null check here which didn't solve the Objects.246 NPE
                return resultKindB;
            };

            return outerMonad.flatMap(innerFlatMapFunc, kindA); // (A -> F<B>) -> F<A> -> F<B>
        };

        ReaderT<F, R, B> resultReaderT = new ReaderT<>(newRun);
        return ReaderTKindHelper.wrap(resultReaderT); // Wrap final result
    }
}
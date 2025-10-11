package org.higherkindedj.hkt.test.api.coretype.either;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherKind;
import org.higherkindedj.hkt.either.EitherKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for Either type.
 *
 * <p>This class provides convenient testing for Either's KindHelper implementation
 * with automatic helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 * <pre>{@code
 * CoreTypeTest.eitherKindHelper(Either.right("test"))
 *     .test();
 * }</pre>
 *
 * <h3>Selective Testing:</h3>
 * <pre>{@code
 * CoreTypeTest.eitherKindHelper(Either.right("test"))
 *     .skipValidations()
 *     .withPerformanceTests()
 *     .test();
 * }</pre>
 *
 * @param <L> The Left type
 * @param <R> The Right type
 */
public final class EitherKindHelperTest<L, R>
        extends BaseKindHelperConfig<Either<L, R>, EitherKind.Witness<L>, R> {

    private static final EitherKindHelper EITHER = EitherKindHelper.EITHER;

    public EitherKindHelperTest(Either<L, R> instance) {
        super(
                instance,
                getEitherClass(),
                either -> EITHER.widen(either),
                kind -> EITHER.narrow(kind)
        );
    }

    @SuppressWarnings("unchecked")
    private static <L, R> Class<Either<L, R>> getEitherClass() {
        return (Class<Either<L, R>>) (Class<?>) Either.class;
    }

    @Override
    protected EitherKindHelperTest<L, R> self() {
        return this;
    }
}
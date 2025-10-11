package org.higherkindedj.hkt.test.api.coretype.io;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.test.api.typeclass.kind.KindHelperTestStage.BaseKindHelperConfig;

/**
 * KindHelper testing for IO type.
 *
 * <p>This class provides convenient testing for IO's KindHelper implementation
 * with automatic helper detection.
 *
 * <h2>Usage Examples:</h2>
 *
 * <h3>Basic Testing:</h3>
 * <pre>{@code
 * CoreTypeTest.ioKindHelper(IO.delay(() -> "test"))
 *     .test();
 * }</pre>
 *
 * <h3>With Performance and Concurrency Tests:</h3>
 * <pre>{@code
 * CoreTypeTest.ioKindHelper(IO.delay(() -> "test"))
 *     .withPerformanceTests()
 *     .withConcurrencyTests()
 *     .test();
 * }</pre>
 *
 * @param <A> The value type
 */
public final class IOKindHelperTest<A>
        extends BaseKindHelperConfig<IO<A>, IOKind.Witness, A> {

    private static final IOKindHelper IO_OP = IOKindHelper.IO_OP;

    public IOKindHelperTest(IO<A> instance) {
        super(
                instance,
                getIOClass(),
                io -> IO_OP.widen(io),
                kind -> IO_OP.narrow(kind)
        );
    }

    @SuppressWarnings("unchecked")
    private static <A> Class<IO<A>> getIOClass() {
        return (Class<IO<A>>) (Class<?>) IO.class;
    }

    @Override
    protected IOKindHelperTest<A> self() {
        return this;
    }
}
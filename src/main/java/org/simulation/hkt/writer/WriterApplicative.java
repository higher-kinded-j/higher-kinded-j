package org.simulation.hkt.writer;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.simulation.hkt.Applicative;
import org.simulation.hkt.Kind;
import org.simulation.hkt.typeclass.Monoid;

import java.util.Objects;
import java.util.function.Function;
import static org.simulation.hkt.writer.WriterKindHelper.*;

public class WriterApplicative<W> extends WriterFunctor<W> implements Applicative<WriterKind<W, ?>> {

  protected final @NonNull Monoid<W> monoidW;

  public WriterApplicative(@NonNull Monoid<W> monoidW) {
    this.monoidW = Objects.requireNonNull(monoidW, "Monoid<W> cannot be null");
  }

  @Override
  public <A> @NonNull Kind<WriterKind<W, ?>, A> of(@Nullable A value) {
    // 'of' creates a Writer with an empty log and the given value.
    return WriterKindHelper.value(monoidW, value);
  }

  @Override
  public <A, B> @NonNull Kind<WriterKind<W, ?>, B> ap(
      @NonNull Kind<WriterKind<W, ?>, Function<A, B>> ff,
      @NonNull Kind<WriterKind<W, ?>, A> fa) {

    Writer<W, Function<A, B>> writerF = unwrap(ff);
    Writer<W, A> writerA = unwrap(fa);

    // Combine logs from both writers
    W combinedLog = monoidW.combine(writerF.log(), writerA.log());

    // Apply the function from writerF to the value from writerA
    Function<A, B> func = writerF.value(); // Function might be null if W is complex
    A val = writerA.value();             // Value might be null

    // Handle potential null function - decide if this should fail or map to null/default
    // Assuming function should ideally be non-null for 'ap' to make sense.
    // If func could be null, add a check. For now, assume it's valid if log isn't empty.
    Objects.requireNonNull(func, "Function wrapped in Writer for 'ap' was null");
    B resultValue = func.apply(val);

    // Create the result Writer
    return wrap(Writer.create(combinedLog, resultValue));
  }
}

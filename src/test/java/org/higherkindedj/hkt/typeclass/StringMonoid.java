package org.higherkindedj.hkt.typeclass;

import org.higherkindedj.hkt.Monoid;
import org.jspecify.annotations.NonNull;

/** A concrete Monoid implementation for String concatenation. */
public class StringMonoid implements Monoid<String> {

  @Override
  public @NonNull String empty() {
    return "";
  }

  @Override
  public @NonNull String combine(@NonNull String x, @NonNull String y) {
    // Ensure inputs are not null if the Monoid contract assumes non-nulls
    // String concatenation handles nulls by converting them to "null",
    // but for a strict monoid, you might want explicit checks.
    // Objects.requireNonNull(x, "Monoid combine operand x cannot be null");
    // Objects.requireNonNull(y, "Monoid combine operand y cannot be null");
    return x + y;
  }
}

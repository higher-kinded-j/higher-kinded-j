// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.func;

import java.util.function.Function;
import org.higherkindedj.hkt.Kind2;
import org.jspecify.annotations.NullMarked;

/**
 * A helper class for working with {@link FunctionKind}. This class provides methods for widening
 * and narrowing {@link FunctionKind} instances.
 */
@NullMarked
public final class FunctionKindHelper {

  /** The single instance of this class. */
  public static final FunctionKindHelper FUNCTION = new FunctionKindHelper();

  private FunctionKindHelper() {}

  /**
   * Widens a {@link Function} to a {@link Kind2} of {@link FunctionKind.Witness}.
   *
   * @param function the function to widen
   * @param <A> the input type of the function
   * @param <B> the output type of the function
   * @return the widened function
   */
  public <A, B> Kind2<FunctionKind.Witness, A, B> widen(Function<A, B> function) {
    return new FunctionKind<>(function);
  }

  /**
   * Narrows a {@link Kind2} of {@link FunctionKind.Witness} to a {@link FunctionKind}.
   *
   * @param kind the kind to narrow
   * @param <A> the input type of the function
   * @param <B> the output type of the function
   * @return the narrowed function
   */
  public <A, B> FunctionKind<A, B> narrow(Kind2<FunctionKind.Witness, A, B> kind) {
    return (FunctionKind<A, B>) kind;
  }

  /**
   * Extracts the underlying {@link Function} from a {@link Kind2} of {@link FunctionKind.Witness}.
   *
   * @param kind the kind to extract the function from
   * @param <A> the input type of the function
   * @param <B> the output type of the function
   * @return the underlying function
   */
  public <A, B> Function<A, B> getFunction(Kind2<FunctionKind.Witness, A, B> kind) {
    return narrow(kind).getFunction();
  }
}

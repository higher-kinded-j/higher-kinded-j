// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.console;

import static org.higherkindedj.hkt.free.console.ConsoleInstructionKindHelper.CONSOLE;

import java.util.function.Function;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.jspecify.annotations.Nullable;

/**
 * Functor instance for ConsoleInstruction.
 *
 * <p>Since ConsoleInstruction operations either return String (ReadLine) or Void (WriteLine),
 * mapping is straightforward - we can map over the phantom result type.
 */
public class ConsoleInstructionFunctor implements Functor<ConsoleInstructionKind.Witness> {

  /** Singleton instance. */
  public static final ConsoleInstructionFunctor INSTANCE = new ConsoleInstructionFunctor();

  private ConsoleInstructionFunctor() {}

  /**
   * Maps a function over a ConsoleInstruction.
   *
   * <p>Note: Since ConsoleInstruction is a sealed interface with specific result types, this
   * implementation cannot actually transform the instruction's result type at runtime. Instead, it
   * maintains the instruction as-is but changes the type signature.
   *
   * <p>For a real implementation, you would need to store the mapping function and apply it during
   * interpretation.
   *
   * @param <A> The input type
   * @param <B> The output type
   * @param f The mapping function
   * @param fa The ConsoleInstruction to map over
   * @return A mapped ConsoleInstruction (implementation simplified for testing)
   */
  @Override
  @SuppressWarnings("unchecked")
  public <A, B> Kind<ConsoleInstructionKind.Witness, B> map(
      Function<? super A, ? extends @Nullable B> f, Kind<ConsoleInstructionKind.Witness, A> fa) {
    // For testing purposes, we create a wrapper that will apply the function later
    // In a real implementation, you'd want to store the mapping
    ConsoleInstruction<A> instruction = CONSOLE.narrow(fa);

    // Since we can't actually change the instruction type, we use an unchecked cast
    // This is a limitation of this simple test DSL implementation
    // A more sophisticated approach would wrap the instruction with the mapping function
    return (Kind<ConsoleInstructionKind.Witness, B>) fa;
  }
}

// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.console;

import org.higherkindedj.hkt.Kind;

/** Helper for converting between ConsoleInstruction and Kind representations. */
public enum ConsoleInstructionKindHelper {
  /** Singleton instance. */
  CONSOLE;

  /**
   * Holder record that wraps a ConsoleInstruction and implements ConsoleInstructionKind.
   *
   * @param <A> The result type
   */
  record ConsoleInstructionHolder<A>(ConsoleInstruction<A> instruction)
      implements ConsoleInstructionKind<A> {}

  /**
   * Widens a ConsoleInstruction to its Kind representation.
   *
   * @param instruction The instruction to widen
   * @param <A> The result type
   * @return The Kind representation
   */
  public <A> Kind<ConsoleInstructionKind.Witness, A> widen(ConsoleInstruction<A> instruction) {
    return new ConsoleInstructionHolder<>(instruction);
  }

  /**
   * Narrows a Kind back to a ConsoleInstruction.
   *
   * @param kind The Kind to narrow
   * @param <A> The result type
   * @return The concrete ConsoleInstruction
   */
  public <A> ConsoleInstruction<A> narrow(Kind<ConsoleInstructionKind.Witness, A> kind) {
    return ((ConsoleInstructionHolder<A>) kind).instruction();
  }
}

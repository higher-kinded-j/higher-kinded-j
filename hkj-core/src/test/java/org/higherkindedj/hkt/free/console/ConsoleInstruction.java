// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.console;

/**
 * Sealed interface representing Console DSL instructions.
 *
 * <p>This is a simple DSL for console operations used to demonstrate the Free monad. Each
 * instruction represents a console operation that can be composed into programs using the Free
 * monad.
 *
 * @param <A> The result type of the instruction
 */
public sealed interface ConsoleInstruction<A>
    permits ConsoleInstruction.ReadLine, ConsoleInstruction.WriteLine {

  /** Instruction to read a line from the console. */
  record ReadLine() implements ConsoleInstruction<String> {}

  /**
   * Instruction to write a line to the console.
   *
   * @param message The message to write
   */
  record WriteLine(String message) implements ConsoleInstruction<Void> {}
}

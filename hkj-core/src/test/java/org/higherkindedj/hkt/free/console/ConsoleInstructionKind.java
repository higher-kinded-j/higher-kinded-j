// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.free.console;

import org.higherkindedj.hkt.Kind;

/**
 * Kind interface for ConsoleInstruction, enabling higher-kinded type representation.
 *
 * @param <A> The result type
 */
public interface ConsoleInstructionKind<A> extends Kind<ConsoleInstructionKind.Witness, A> {

  /** Witness type for ConsoleInstruction. */
  final class Witness {
    private Witness() {
      // Phantom type - prevents instantiation
    }
  }
}

// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import java.util.Scanner;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;

class InputHandler {
  private static final Scanner scanner = new Scanner(System.in);

  static Kind<IOKind.Witness, Either<GameError, MoveCommand>> readMoveCommand() {
    return IOKindHelper.IO_OP.delay(
        () -> {
          System.out.print("Enter move for " + " (e.g., 'a3 b4') or 'quit': ");
          String line = scanner.nextLine();

          if ("quit".equalsIgnoreCase(line.trim())) {
            return Either.left(new GameError("Player quit the game."));
          }

          String[] parts = line.trim().split("\\s+");
          if (parts.length != 2) {
            return Either.left(
                new GameError("Invalid input. Use 'from to' format (e.g., 'c3 d4')."));
          }
          try {
            Square from = parseSquare(parts[0]);
            Square to = parseSquare(parts[1]);
            return Either.right(new MoveCommand(from, to));
          } catch (IllegalArgumentException e) {
            return Either.left(new GameError(e.getMessage()));
          }
        });
  }

  private static Square parseSquare(String s) throws IllegalArgumentException {
    if (s == null || s.length() != 2)
      throw new IllegalArgumentException("Invalid square format: " + s);
    char colChar = s.charAt(0);
    char rowChar = s.charAt(1);
    if (colChar < 'a' || colChar > 'h' || rowChar < '1' || rowChar > '8') {
      throw new IllegalArgumentException("Square out of bounds (a1-h8): " + s);
    }
    int col = colChar - 'a';
    int row = rowChar - '1';
    return new Square(row, col);
  }
}

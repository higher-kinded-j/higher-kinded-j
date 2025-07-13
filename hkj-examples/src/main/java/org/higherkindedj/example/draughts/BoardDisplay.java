// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.draughts;

import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.io.IOKind;
import org.higherkindedj.hkt.io.IOKindHelper;
import org.higherkindedj.hkt.unit.Unit;

public class BoardDisplay {

  public static Kind<IOKind.Witness, Unit> displayBoard(GameState gameState) {
    return IOKindHelper.IO_OP.delay(
        () -> {
          System.out.println("\n  a b c d e f g h");
          System.out.println(" +-----------------+");
          for (int r = 7; r >= 0; r--) { // Print from row 8 down to 1
            System.out.print((r + 1) + "| ");
            for (int c = 0; c < 8; c++) {
              Piece p = gameState.board().get(new Square(r, c));
              if (p == null) {
                System.out.print(". ");
              } else {
                char pieceChar = (p.owner() == Player.RED) ? 'r' : 'b';
                if (p.type() == PieceType.KING) pieceChar = Character.toUpperCase(pieceChar);
                System.out.print(pieceChar + " ");
              }
            }
            System.out.println("|" + (r + 1));
          }
          System.out.println(" +-----------------+");
          System.out.println("  a b c d e f g h");
          System.out.println("\n" + gameState.message());
          if (!gameState.isGameOver()) {
            System.out.println("Current Player: " + gameState.currentPlayer());
          }
          return Unit.INSTANCE;
        });
  }
}

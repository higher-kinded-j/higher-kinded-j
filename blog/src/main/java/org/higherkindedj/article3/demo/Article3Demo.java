// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article3.demo;

/**
 * Main entry point for Article 3 demonstrations.
 *
 * <p>Runs all demos showing the Expression Language AST and basic optics:
 *
 * <ul>
 *   <li>ExprDemo — Building expressions, using prisms and lenses
 *   <li>OptimiserDemo — Constant folding and identity simplification
 * </ul>
 */
public final class Article3Demo {

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║        Article 3: AST and Basic Optics                           ║");
    System.out.println("║        Functional Optics for Modern Java                         ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    System.out.println();

    // Run Expression AST demo
    ExprDemo.main(args);

    System.out.println();
    System.out.println("────────────────────────────────────────────────────────────────────");
    System.out.println();

    // Run Optimiser demo
    OptimiserDemo.main(args);

    System.out.println();
    System.out.println("════════════════════════════════════════════════════════════════════");
    System.out.println("Article 3 demonstrations complete.");
    System.out.println("════════════════════════════════════════════════════════════════════");
  }
}

// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article4.demo;

/**
 * Main entry point for Article 4 demonstrations.
 *
 * <p>Runs all demos showing tree traversals and pattern rewrites:
 *
 * <ul>
 *   <li>TraversalDemo — Children traversal, bottom-up/top-down transforms, folds
 *   <li>OptimiserDemo — Constant folding, identity simplification, cascading optimisation
 * </ul>
 */
public final class Article4Demo {

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║        Article 4: Tree Traversals and Pattern Rewrites          ║");
    System.out.println("║        Functional Optics for Modern Java                        ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    System.out.println();

    // Run Traversal demo
    TraversalDemo.main(args);

    System.out.println();
    System.out.println("────────────────────────────────────────────────────────────────────");
    System.out.println();

    // Run Optimiser demo
    OptimiserDemo.main(args);

    System.out.println();
    System.out.println("════════════════════════════════════════════════════════════════════");
    System.out.println("Article 4 demonstrations complete.");
    System.out.println("════════════════════════════════════════════════════════════════════");
  }
}

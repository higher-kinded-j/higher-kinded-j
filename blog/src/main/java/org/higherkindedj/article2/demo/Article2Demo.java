// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article2.demo;

/**
 * Main entry point to run all Article 2 demos.
 *
 * <p>These demos accompany Article 2: Optics Fundamentals, demonstrating lenses, prisms, and
 * traversals using Higher-Kinded-J's annotation-driven code generation.
 *
 * <p>Run with: {@code ./gradlew :blog:run}
 *
 * @see <a href="../../docs/article-2-optics-fundamentals.md">Article 2: Optics Fundamentals</a>
 */
public final class Article2Demo {

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════╗");
    System.out.println("║     Article 2: Optics Fundamentals - Runnable Examples       ║");
    System.out.println("║     Using Higher-Kinded-J Generated Lenses and Prisms        ║");
    System.out.println("╚══════════════════════════════════════════════════════════════╝");
    System.out.println();

    System.out.println("Running all demos...\n");
    System.out.println("════════════════════════════════════════════════════════════════\n");

    LensDemo.main(args);
    System.out.println("════════════════════════════════════════════════════════════════\n");

    PrismDemo.main(args);
    System.out.println("════════════════════════════════════════════════════════════════\n");

    TraversalDemo.main(args);
    System.out.println("════════════════════════════════════════════════════════════════\n");

    CompositionDemo.main(args);
    System.out.println("════════════════════════════════════════════════════════════════\n");

    ExpressionPreviewDemo.main(args);
    System.out.println("════════════════════════════════════════════════════════════════\n");

    System.out.println("All demos completed!");
    System.out.println();
    System.out.println("Next: Read Article 3 to build the full Expression Language AST");
    System.out.println("      with recursive traversals and tree transformations.");
  }
}

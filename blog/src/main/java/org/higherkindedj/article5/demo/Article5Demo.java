// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.article5.demo;

/**
 * Main entry point for Article 5 demonstrations.
 *
 * <p>Runs all demos showing the Effect Path API and effect-polymorphic optics:
 *
 * <ul>
 *   <li>EffectPathDemo: The Effect Path API (MaybePath, EitherPath, ValidationPath, etc.)
 *   <li>TypeCheckerDemo: Type checking with error accumulation using HKJ's Validated
 *   <li>InterpreterDemo: Expression evaluation with HKJ's State monad
 *   <li>EffectPolymorphicDemo: Same traversal with different HKJ effects
 * </ul>
 *
 * <p>This demonstrates the full power of Higher-Kinded-J for effect polymorphism in Java.
 */
public final class Article5Demo {

  public static void main(String[] args) {
    System.out.println("╔══════════════════════════════════════════════════════════════════╗");
    System.out.println("║     Article 5: The Effect Path API with Higher-Kinded-J          ║");
    System.out.println("║                 Functional Optics for Modern Java                ║");
    System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    System.out.println();

    // Run Effect Path API demo (the primary API for Article 5)
    EffectPathDemo.main(args);

    System.out.println();
    System.out.println("────────────────────────────────────────────────────────────────────");
    System.out.println();

    // Run Type Checker demo using Higher-Kinded-J's Validated
    TypeCheckerDemo.main(args);

    System.out.println();
    System.out.println("────────────────────────────────────────────────────────────────────");
    System.out.println();

    // Run Interpreter demo using Higher-Kinded-J's State
    InterpreterDemo.main(args);

    System.out.println();
    System.out.println("────────────────────────────────────────────────────────────────────");
    System.out.println();

    // Run Effect Polymorphic demo with Higher-Kinded-J's Kind<F, A>
    EffectPolymorphicDemo.main(args);

    System.out.println();
    System.out.println("════════════════════════════════════════════════════════════════════");
    System.out.println("Article 5 demonstrations complete.");
    System.out.println("════════════════════════════════════════════════════════════════════");
  }
}

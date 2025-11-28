// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing Higher-Kinded Type witness patterns.
 *
 * <p>In higher-kinded-j, witness types are used to simulate higher-kinded types in Java:
 *
 * <ul>
 *   <li>Each Kind implementation has a nested {@code Witness} class
 *   <li>Witness classes are marker types that should never be instantiated
 *   <li>Witness classes have private constructors to prevent instantiation
 * </ul>
 *
 * <p>Example pattern:
 *
 * <pre>{@code
 * public interface EitherKind<L, R> extends Kind<EitherKind.Witness<L>, R> {
 *     final class Witness<TYPE_L> {
 *         private Witness() {} // Non-instantiable marker type
 *     }
 * }
 * }</pre>
 */
@DisplayName("Witness Type Rules")
class WitnessTypeRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Witness classes must be final.
   *
   * <p>Witness types are marker types used for type-level programming and should not be extended.
   */
  @Test
  @DisplayName("Witness classes should be final (marker types should not be extended)")
  void witness_classes_should_be_final() {
    classes()
        .that()
        .haveSimpleName("Witness")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Witness classes must have only private constructors.
   *
   * <p>Witness types should never be instantiated - they exist only as type markers for the
   * higher-kinded type simulation.
   *
   * <p>Excludes FunctionKind.Witness which is a special case for Kind2 profunctors - it uses the
   * default public constructor as the class is primarily used as a type marker in generic contexts.
   */
  @Test
  @DisplayName("Witness classes should have only private constructors (non-instantiable)")
  void witness_classes_should_have_private_constructors() {
    classes()
        .that()
        .haveSimpleName("Witness")
        .and()
        .resideOutsideOfPackage("..func..") // Exclude FunctionKind.Witness (profunctor type)
        .should()
        .haveOnlyPrivateConstructors()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * KindHelper classes should be final utility classes.
   *
   * <p>KindHelper classes provide static widen/narrow utilities and should not be extended. Enums
   * are implicitly final, so they are excluded from this check.
   */
  @Test
  @DisplayName("KindHelper classes should be final utility classes")
  void kind_helper_classes_should_be_final() {
    classes()
        .that()
        .haveSimpleNameEndingWith("KindHelper")
        .and()
        .areNotEnums() // Enums are implicitly final
        .should()
        .haveModifier(JavaModifier.FINAL)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * KindHelper classes should have private constructors.
   *
   * <p>KindHelper classes contain only static methods and should not be instantiated. Enums have
   * implicit private constructors, so they are excluded from this check.
   */
  @Test
  @DisplayName("KindHelper classes should have only private constructors (utility classes)")
  void kind_helper_classes_should_have_private_constructors() {
    classes()
        .that()
        .haveSimpleNameEndingWith("KindHelper")
        .and()
        .areNotEnums() // Enums have implicit private constructors
        .should()
        .haveOnlyPrivateConstructors()
        .allowEmptyShould(true)
        .check(classes);
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing module dependency constraints.
 *
 * <p>These rules ensure clean separation between:
 *
 * <ul>
 *   <li>API interfaces and core implementations
 *   <li>Different type implementations (Either, Maybe, Try, etc.)
 *   <li>Core library and annotation processing
 * </ul>
 */
@DisplayName("Module Dependency Rules")
class ModuleDependencyRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Ensures no circular dependencies exist between type implementation packages.
   *
   * <p>Each HKT type (either, maybe, try, list, etc.) should be independent and not create cycles
   * with other type packages.
   */
  @Test
  @DisplayName("Type implementation packages should be free of cycles")
  void no_cycles_between_type_packages() {
    slices()
        .matching("org.higherkindedj.hkt.(*)..")
        .should()
        .beFreeOfCycles()
        .allowEmptyShould(true) // Allow if no matching slices found
        .check(classes);
  }

  /**
   * Ensures monad transformer packages don't depend on each other cyclically.
   *
   * <p>Transformers like EitherT, MaybeT, StateT should be independent.
   */
  @Test
  @DisplayName("Monad transformer packages should be free of cycles")
  void no_cycles_between_transformer_packages() {
    slices()
        .matching("org.higherkindedj.hkt.(*_t)..")
        .should()
        .beFreeOfCycles()
        .allowEmptyShould(true) // Allow if no matching slices found
        .check(classes);
  }

  /**
   * Ensures core optics implementation classes don't depend on specific HKT implementations.
   *
   * <p>Core optics (Lens, Prism, Traversal, etc.) should work through the generic Kind interface.
   * However, utility and extension classes in optics.util and optics.extensions are specifically
   * designed to provide convenient operations for specific HKT types (like EitherTraversals,
   * MaybeTraversals, etc.) and are excluded from this rule.
   */
  @Test
  @DisplayName("Optics classes should not depend on specific HKT type implementations")
  void optics_should_not_depend_on_specific_hkt_types() {
    noClasses()
        .that()
        .resideInAPackage("..optics..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        // Exclude utility and extension classes that are designed for specific types
        .and()
        .resideOutsideOfPackage("..optics.util..")
        .and()
        .resideOutsideOfPackage("..optics.extensions..")
        .and()
        .resideOutsideOfPackage("..optics.fluent..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..hkt.either..", "..hkt.maybe..", "..hkt.trymonad..", "..hkt.list..", "..hkt.io..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Ensures example code doesn't leak into core packages.
   *
   * <p>Example code should only exist in example packages, not in core implementation.
   */
  @Test
  @DisplayName("Core HKT classes should not depend on example code")
  void no_example_dependencies_in_core() {
    noClasses()
        .that()
        .resideInAPackage("..hkt..")
        .and()
        .resideOutsideOfPackage("..example..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..example..")
        .allowEmptyShould(true)
        .check(classes);
  }
}

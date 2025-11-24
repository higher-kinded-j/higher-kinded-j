// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing dependency layers between packages.
 *
 * <p>These rules ensure proper layering and prevent inappropriate dependencies:
 *
 * <ul>
 *   <li>Core types shouldn't depend on transformers
 *   <li>Optics shouldn't depend on specific monad implementations
 *   <li>API types shouldn't depend on implementation details
 * </ul>
 */
@DisplayName("Dependency Layer Rules")
class DependencyLayerRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Core Maybe type should not depend on MaybeT transformer.
   *
   * <p>The base Maybe monad should be independent of its transformer variant.
   */
  @Test
  @DisplayName("Maybe package should not depend on MaybeT transformer")
  void maybe_should_not_depend_on_maybe_t() {
    noClasses()
        .that()
        .resideInAPackage("..hkt.maybe..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.maybe_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Core Either type should not depend on EitherT transformer.
   *
   * <p>The base Either monad should be independent of its transformer variant.
   */
  @Test
  @DisplayName("Either package should not depend on EitherT transformer")
  void either_should_not_depend_on_either_t() {
    noClasses()
        .that()
        .resideInAPackage("..hkt.either..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.either_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Core State type should not depend on StateT transformer.
   *
   * <p>The base State monad should be independent of its transformer variant.
   */
  @Test
  @DisplayName("State package should not depend on StateT transformer")
  void state_should_not_depend_on_state_t() {
    noClasses()
        .that()
        .resideInAPackage("..hkt.state..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.state_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Core Optional type should not depend on OptionalT transformer.
   *
   * <p>The base Optional wrapper should be independent of its transformer variant.
   */
  @Test
  @DisplayName("Optional package should not depend on OptionalT transformer")
  void optional_should_not_depend_on_optional_t() {
    noClasses()
        .that()
        .resideInAPackage("..hkt.optional..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.optional_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Optics core should not depend on specific monad implementations.
   *
   * <p>Optics should work with any monad through the type class interfaces, not concrete types.
   */
  @Test
  @DisplayName("Optics should not depend on State monad implementation")
  void optics_should_not_depend_on_state_implementation() {
    noClasses()
        .that()
        .resideInAPackage("..optics..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.state..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Optics core should not depend on IO monad implementation.
   *
   * <p>Optics should work through abstractions, not concrete IO types.
   */
  @Test
  @DisplayName("Optics should not depend on IO monad implementation")
  void optics_should_not_depend_on_io_implementation() {
    noClasses()
        .that()
        .resideInAPackage("..optics..")
        .and()
        .haveSimpleNameNotContaining("Test")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.io..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * HKT implementations should depend on the API module interfaces.
   *
   * <p>All HKT types should implement the core interfaces from the API.
   */
  @Test
  @DisplayName("HKT implementations should use Kind interface")
  void hkt_implementations_should_use_kind_interface() {
    classes()
        .that()
        .resideInAPackage("..hkt..")
        .and()
        .haveSimpleNameEndingWith("Kind")
        .and()
        .areInterfaces()
        .should()
        .beAssignableTo(org.higherkindedj.hkt.Kind.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Transformer packages should depend on their base types.
   *
   * <p>EitherT should depend on Either, MaybeT on Maybe, etc.
   */
  @Test
  @DisplayName("EitherT should depend on Either")
  void either_t_should_depend_on_either() {
    classes()
        .that()
        .resideInAPackage("..hkt.either_t..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.either..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** MaybeT should depend on Maybe. */
  @Test
  @DisplayName("MaybeT should depend on Maybe")
  void maybe_t_should_depend_on_maybe() {
    classes()
        .that()
        .resideInAPackage("..hkt.maybe_t..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..hkt.maybe..")
        .allowEmptyShould(true)
        .check(classes);
  }
}

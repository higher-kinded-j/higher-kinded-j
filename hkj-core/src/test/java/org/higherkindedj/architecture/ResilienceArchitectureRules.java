// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing resilience package conventions.
 *
 * <p>These rules ensure:
 *
 * <ul>
 *   <li>Configuration classes are records
 *   <li>Exception classes extend RuntimeException
 *   <li>Utility classes are final
 *   <li>Resilience does not depend on effect package
 *   <li>Resilience does not depend on specific HKT implementations
 * </ul>
 */
@DisplayName("Resilience Architecture Rules")
class ResilienceArchitectureRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  @Nested
  @DisplayName("Resilience Type Conventions")
  class TypeConventionTests {

    @Test
    @DisplayName("Configuration records in resilience package should be records")
    void configuration_classes_should_be_records() {
      classes()
          .that()
          .haveSimpleNameEndingWith("Config")
          .and()
          .resideInAPackage("..resilience..")
          .should()
          .beAssignableTo(Record.class)
          .allowEmptyShould(true)
          .check(classes);
    }

    @Test
    @DisplayName("Exception classes in resilience package should extend RuntimeException")
    void exception_classes_should_extend_runtime_exception() {
      classes()
          .that()
          .haveSimpleNameEndingWith("Exception")
          .and()
          .resideInAPackage("..resilience..")
          .should()
          .beAssignableTo(RuntimeException.class)
          .allowEmptyShould(true)
          .check(classes);
    }

    @Test
    @DisplayName("Resilience utility classes should be final")
    void resilience_utility_classes_should_be_final() {
      classes()
          .that()
          .haveSimpleNameEndingWith("Resilience")
          .or()
          .haveSimpleNameEndingWith("Retry")
          .and()
          .resideInAPackage("..resilience..")
          .should()
          .haveModifier(JavaModifier.FINAL)
          .allowEmptyShould(true)
          .check(classes);
    }
  }

  @Nested
  @DisplayName("Resilience Dependency Rules")
  class DependencyTests {

    @Test
    @DisplayName("Resilience classes should not depend on effect package")
    void resilience_should_not_depend_on_effect() {
      noClasses()
          .that()
          .resideInAPackage("..resilience..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..effect..")
          .allowEmptyShould(true)
          .check(classes);
    }

    @Test
    @DisplayName("Resilience classes should only depend on vtask and core hkt packages")
    void resilience_should_not_depend_on_specific_hkt_implementations() {
      noClasses()
          .that()
          .resideInAPackage("..resilience..")
          .and()
          .haveNameNotMatching(".*Saga.*")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..hkt.either..", "..hkt.maybe..", "..hkt.trymonad..", "..hkt.io..")
          .allowEmptyShould(true)
          .check(classes);
    }
  }
}

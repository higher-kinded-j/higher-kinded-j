// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing method signature conventions.
 *
 * <p>These rules ensure that utility classes have required methods:
 *
 * <ul>
 *   <li>KindHelper classes must have widen() method
 *   <li>KindHelper classes must have narrow() method
 * </ul>
 */
@DisplayName("Method Signature Rules")
class MethodSignatureRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * KindHelper classes must have a widen() method.
   *
   * <p>The widen() method converts a concrete type to its Kind representation. This is a core part
   * of the HKT simulation pattern.
   *
   * <p>Note: Kind2-based helpers (like Tuple2KindHelper) use widen2/narrow2 instead.
   */
  @Test
  @DisplayName("KindHelper classes should have widen() method")
  void kind_helper_should_have_widen_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("KindHelper")
        .and()
        .haveSimpleNameNotContaining("Tuple2") // Tuple2KindHelper uses widen2/narrow2 for Kind2
        .should(haveMethodNamed("widen"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * KindHelper classes must have a narrow() method.
   *
   * <p>The narrow() method converts a Kind back to its concrete type. This is a core part of the
   * HKT simulation pattern.
   *
   * <p>Note: Kind2-based helpers (like Tuple2KindHelper) use widen2/narrow2 instead.
   */
  @Test
  @DisplayName("KindHelper classes should have narrow() method")
  void kind_helper_should_have_narrow_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("KindHelper")
        .and()
        .haveSimpleNameNotContaining("Tuple2") // Tuple2KindHelper uses widen2/narrow2 for Kind2
        .should(haveMethodNamed("narrow"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Monad implementations should have flatMap method.
   *
   * <p>The flatMap operation is the defining operation of a Monad.
   */
  @Test
  @DisplayName("Monad classes should have flatMap method")
  void monad_should_have_flatmap_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Monad")
        .and()
        .areNotInterfaces()
        .should(haveMethodNamed("flatMap"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Functor implementations should have map method.
   *
   * <p>The map operation is the defining operation of a Functor.
   */
  @Test
  @DisplayName("Functor classes should have map method")
  void functor_should_have_map_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Functor")
        .and()
        .areNotInterfaces()
        .should(haveMethodNamed("map"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Applicative implementations should have ap method.
   *
   * <p>The ap (apply) operation is the defining operation of an Applicative.
   */
  @Test
  @DisplayName("Applicative classes should have ap method")
  void applicative_should_have_ap_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Applicative")
        .and()
        .areNotInterfaces()
        .should(haveMethodNamed("ap"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Custom condition that checks if a class has a method with the given name.
   *
   * @param methodName the name of the method to check for
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveMethodNamed(String methodName) {
    return new ArchCondition<>("have method named '" + methodName + "'") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasMethod =
            javaClass.getMethods().stream().anyMatch(method -> method.getName().equals(methodName));

        if (!hasMethod) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s does not have required method '%s'",
                      javaClass.getName(), methodName)));
        }
      }
    };
  }
}

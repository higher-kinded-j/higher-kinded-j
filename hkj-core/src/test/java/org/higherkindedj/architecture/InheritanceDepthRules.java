// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing inheritance depth limits.
 *
 * <p>Deep inheritance hierarchies make code harder to understand and maintain. These rules ensure:
 *
 * <ul>
 *   <li>Class inheritance depth is limited (prefer composition)
 *   <li>Interface hierarchies remain manageable
 *   <li>Favor sealed hierarchies over deep inheritance
 * </ul>
 */
@DisplayName("Inheritance Depth Rules")
class InheritanceDepthRules {

  private static JavaClasses classes;

  /** Maximum allowed inheritance depth for classes (excluding java.lang.Object). */
  private static final int MAX_CLASS_INHERITANCE_DEPTH = 3;

  /** Maximum allowed interface implementation depth. */
  private static final int MAX_INTERFACE_DEPTH = 5;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Classes in HKT packages should not have deep inheritance hierarchies.
   *
   * <p>Functional programming favors composition over inheritance. Deep hierarchies indicate
   * potential design issues.
   */
  @Test
  @DisplayName("HKT classes should have limited inheritance depth (max 3)")
  void hkt_classes_should_have_limited_inheritance_depth() {
    classes()
        .that()
        .resideInAPackage("..hkt..")
        .and()
        .areNotInterfaces()
        .and()
        .areNotRecords()
        .and()
        .areNotEnums()
        .should(haveInheritanceDepthAtMost(MAX_CLASS_INHERITANCE_DEPTH))
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Optics classes should not have deep inheritance hierarchies. */
  @Test
  @DisplayName("Optics classes should have limited inheritance depth (max 3)")
  void optics_classes_should_have_limited_inheritance_depth() {
    classes()
        .that()
        .resideInAPackage("..optics..")
        .and()
        .areNotInterfaces()
        .and()
        .areNotRecords()
        .and()
        .areNotEnums()
        .should(haveInheritanceDepthAtMost(MAX_CLASS_INHERITANCE_DEPTH))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Type class interfaces should have reasonable hierarchy depth.
   *
   * <p>While Functor -> Applicative -> Monad is acceptable, overly deep hierarchies should be
   * avoided.
   */
  @Test
  @DisplayName("Type class interfaces should have manageable hierarchy depth (max 5)")
  void type_class_interfaces_should_have_limited_depth() {
    classes()
        .that()
        .areInterfaces()
        .and()
        .resideInAPackage("..hkt..")
        .and()
        .haveSimpleNameEndingWith("Functor")
        .or()
        .haveSimpleNameEndingWith("Applicative")
        .or()
        .haveSimpleNameEndingWith("Monad")
        .should(haveInterfaceDepthAtMost(MAX_INTERFACE_DEPTH))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Custom condition checking class inheritance depth.
   *
   * @param maxDepth the maximum allowed inheritance depth
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveInheritanceDepthAtMost(int maxDepth) {
    return new ArchCondition<>("have inheritance depth at most " + maxDepth) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        int depth = calculateInheritanceDepth(javaClass);
        if (depth > maxDepth) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s has inheritance depth %d (max allowed: %d)",
                      javaClass.getName(), depth, maxDepth)));
        }
      }
    };
  }

  /**
   * Custom condition checking interface hierarchy depth.
   *
   * @param maxDepth the maximum allowed interface depth
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveInterfaceDepthAtMost(int maxDepth) {
    return new ArchCondition<>("have interface hierarchy depth at most " + maxDepth) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        int depth = calculateInterfaceDepth(javaClass);
        if (depth > maxDepth) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Interface %s has hierarchy depth %d (max allowed: %d)",
                      javaClass.getName(), depth, maxDepth)));
        }
      }
    };
  }

  /**
   * Calculate the inheritance depth of a class.
   *
   * <p>Counts the number of superclasses excluding java.lang.Object.
   *
   * @param javaClass the class to check
   * @return the inheritance depth
   */
  private static int calculateInheritanceDepth(JavaClass javaClass) {
    int depth = 0;
    JavaClass current = javaClass;

    while (current.getSuperclass().isPresent()) {
      JavaType superclassType = current.getSuperclass().get();
      JavaClass superclass = superclassType.toErasure();
      if (superclass.getFullName().equals("java.lang.Object")) {
        break;
      }
      depth++;
      current = superclass;
    }

    return depth;
  }

  /**
   * Calculate the maximum interface hierarchy depth.
   *
   * @param javaClass the interface to check
   * @return the maximum interface depth
   */
  private static int calculateInterfaceDepth(JavaClass javaClass) {
    if (!javaClass.isInterface()) {
      return 0;
    }
    return calculateMaxInterfaceDepth(javaClass, 0);
  }

  /**
   * Recursively calculate the maximum interface depth.
   *
   * @param javaClass the current interface
   * @param currentDepth the current depth
   * @return the maximum depth found
   */
  private static int calculateMaxInterfaceDepth(JavaClass javaClass, int currentDepth) {
    int maxDepth = currentDepth;

    for (JavaClass superInterface : javaClass.getRawInterfaces()) {
      // Skip java.* interfaces
      if (superInterface.getFullName().startsWith("java.")) {
        continue;
      }
      int depth = calculateMaxInterfaceDepth(superInterface, currentDepth + 1);
      maxDepth = Math.max(maxDepth, depth);
    }

    return maxDepth;
  }
}

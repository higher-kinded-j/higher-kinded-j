// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing type class instance patterns.
 *
 * <p>Type class instances in higher-kinded-j follow specific patterns:
 *
 * <ul>
 *   <li>Singleton pattern with {@code instance()} factory method or {@code INSTANCE} constant
 *   <li>Private constructors to enforce singleton usage
 *   <li>Stateless implementations (no instance fields)
 * </ul>
 */
@DisplayName("Type Class Pattern Rules")
class TypeClassPatternRules {

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * Monad implementations should provide a static factory method or constant.
   *
   * <p>This ensures consistent instantiation patterns across the codebase. Acceptable patterns:
   *
   * <ul>
   *   <li>{@code public static <T> XxxMonad<T> instance()} method
   *   <li>{@code public static final INSTANCE} constant
   * </ul>
   */
  @Test
  @DisplayName("Monad implementations should have instance() method or INSTANCE constant")
  void monad_implementations_should_have_factory() {
    classes()
        .that()
        .implement(Monad.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .should(haveStaticFactoryOrInstance())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Functor implementations should provide a static factory method or constant.
   *
   * <p>Excludes classes that also implement Monad (they follow Monad patterns).
   */
  @Test
  @DisplayName("Functor implementations should have instance() method or INSTANCE constant")
  void functor_implementations_should_have_factory() {
    classes()
        .that()
        .implement(Functor.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .and()
        .doNotImplement(Monad.class) // Monads tested separately
        .should(haveStaticFactoryOrInstance())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Type class implementations should be stateless.
   *
   * <p>Type class instances should not hold mutable state. They may have:
   *
   * <ul>
   *   <li>Static final fields (for singleton instances)
   *   <li>No instance fields at all
   * </ul>
   */
  @Test
  @DisplayName("Type class implementations should be stateless (no instance fields)")
  void type_class_implementations_should_be_stateless() {
    classes()
        .that()
        .implement(Functor.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .should(haveNoNonStaticFields())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Custom condition that checks for static factory method or INSTANCE constant.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveStaticFactoryOrInstance() {
    return new ArchCondition<>("have static instance() method or INSTANCE constant") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasInstanceMethod =
            javaClass.getMethods().stream()
                .anyMatch(
                    method ->
                        method.getName().equals("instance")
                            && method.getModifiers().contains(JavaModifier.STATIC));

        boolean hasInstanceConstant =
            javaClass.getFields().stream()
                .anyMatch(
                    field ->
                        field.getName().equals("INSTANCE")
                            && field.getModifiers().contains(JavaModifier.STATIC)
                            && field.getModifiers().contains(JavaModifier.FINAL));

        if (!hasInstanceMethod && !hasInstanceConstant) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s does not have instance() method or INSTANCE constant",
                      javaClass.getName())));
        }
      }
    };
  }

  /**
   * Custom condition that checks for no non-static fields (stateless).
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveNoNonStaticFields() {
    return new ArchCondition<>("have no non-static fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
            .forEach(
                field ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Class %s has non-static field '%s' (type class instances should be"
                                    + " stateless)",
                                javaClass.getName(), field.getName()))));
      }
    };
  }
}

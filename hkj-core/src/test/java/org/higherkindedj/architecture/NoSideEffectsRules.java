// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing functional purity patterns.
 *
 * <p>Pure functional types should not have side effects. These rules ensure:
 *
 * <ul>
 *   <li>Kind implementations don't use System.out/System.err
 *   <li>Pure types don't have mutable static fields
 *   <li>Type class instances are side-effect free
 * </ul>
 */
@DisplayName("No Side Effects Rules")
class NoSideEffectsRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Kind implementations should not use System.out.
   *
   * <p>Pure functional types should not perform console I/O as a side effect.
   */
  @Test
  @DisplayName("Kind implementations should not use System.out")
  void kind_implementations_should_not_use_system_out() {
    noClasses()
        .that()
        .implement(Kind.class)
        .should()
        .accessClassesThat()
        .haveFullyQualifiedName("java.io.PrintStream")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Monad implementations should not use System.out.
   *
   * <p>Type class instances should be pure and not perform I/O.
   */
  @Test
  @DisplayName("Monad classes should not use System.out")
  void monad_classes_should_not_use_system_out() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Monad")
        .and()
        .areNotInterfaces()
        .should()
        .accessClassesThat()
        .haveFullyQualifiedName("java.io.PrintStream")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Functor implementations should not use System.out. */
  @Test
  @DisplayName("Functor classes should not use System.out")
  void functor_classes_should_not_use_system_out() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Functor")
        .and()
        .areNotInterfaces()
        .should()
        .accessClassesThat()
        .haveFullyQualifiedName("java.io.PrintStream")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Kind implementations should not have mutable static fields.
   *
   * <p>Mutable static state breaks referential transparency.
   */
  @Test
  @DisplayName("Kind implementations should not have mutable static fields")
  void kind_implementations_should_not_have_mutable_static_fields() {
    noClasses()
        .that()
        .implement(Kind.class)
        .and()
        .areNotInterfaces()
        .should(haveMutableStaticFields())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Type class implementations should not have mutable static fields.
   *
   * <p>Monad, Functor, etc. implementations should be stateless.
   */
  @Test
  @DisplayName("Type class implementations should not have mutable static fields")
  void type_class_implementations_should_not_have_mutable_static_fields() {
    noClasses()
        .that()
        .haveSimpleNameEndingWith("Monad")
        .or()
        .haveSimpleNameEndingWith("Functor")
        .or()
        .haveSimpleNameEndingWith("Applicative")
        .and()
        .areNotInterfaces()
        .should(haveMutableStaticFields())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Pure algebraic data types should not throw arbitrary RuntimeExceptions.
   *
   * <p>Instead of throwing, use Either/Try/Validated for error handling.
   *
   * <p>Note: This rule only checks Just, not Nothing. Nothing.get() intentionally throws
   * NoSuchElementException following Java's standard pattern for absence-of-value (similar to
   * Optional.get()). This is correct FP behavior where the caller should check isJust() first.
   */
  @Test
  @DisplayName("Just should not directly throw RuntimeException")
  void just_should_not_throw_runtime_exception() {
    noClasses()
        .that()
        .haveSimpleName("Just")
        .should()
        .dependOnClassesThat()
        .areAssignableTo(RuntimeException.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Custom condition checking for mutable static fields.
   *
   * <p>A field is considered mutable if it is static and not final.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveMutableStaticFields() {
    return new ArchCondition<>("have mutable static fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> field.getModifiers().contains(JavaModifier.STATIC))
            .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
            .filter(field -> !field.getName().startsWith("$")) // Exclude synthetic fields
            .forEach(
                field ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Class %s has mutable static field '%s' (breaks referential"
                                    + " transparency)",
                                javaClass.getName(), field.getName()))));
      }
    };
  }
}

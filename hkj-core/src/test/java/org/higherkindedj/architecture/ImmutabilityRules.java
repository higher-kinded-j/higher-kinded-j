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
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing immutability patterns.
 *
 * <p>Functional programming relies heavily on immutability. These rules ensure:
 *
 * <ul>
 *   <li>HKT data types (Kind implementations) are immutable
 *   <li>Fields in functional types are final
 *   <li>Records are used where appropriate for data types
 * </ul>
 */
@DisplayName("Immutability Rules")
class ImmutabilityRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Kind implementations should have only final fields.
   *
   * <p>HKT types like Either, Maybe, Try should be immutable to support referential transparency.
   *
   * <p>Note: This excludes interfaces (which have no fields) and inner/nested classes that may have
   * synthetic fields.
   */
  @Test
  @DisplayName("Kind implementations should have only final fields (immutability)")
  void kind_implementations_should_have_final_fields() {
    classes()
        .that()
        .implement(Kind.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .and()
        .areNotMemberClasses() // Exclude inner classes with potential synthetic fields
        .should(haveOnlyFinalInstanceFields())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Custom condition checking that all instance fields are final.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveOnlyFinalInstanceFields() {
    return new ArchCondition<>("have only final instance fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
            .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
            .filter(field -> !field.getName().startsWith("this$")) // Exclude synthetic outer refs
            .forEach(
                field ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Class %s has non-final instance field '%s' (should be immutable)",
                                javaClass.getName(), field.getName()))));
      }
    };
  }

  /**
   * Classes representing values should be final or sealed.
   *
   * <p>Value types should not be extended arbitrarily to maintain the integrity of the type system.
   */
  @Test
  @DisplayName("Value classes (Just, Nothing, Left, Right, etc.) should be final")
  void value_classes_should_be_final_or_sealed() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Value")
        .or()
        .haveSimpleName("Just")
        .or()
        .haveSimpleName("Nothing")
        .or()
        .haveSimpleName("Left")
        .or()
        .haveSimpleName("Right")
        .or()
        .haveSimpleName("Success")
        .or()
        .haveSimpleName("Failure")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .orShould()
        .beRecords() // Records are implicitly final
        .allowEmptyShould(true)
        .check(classes);
  }
}

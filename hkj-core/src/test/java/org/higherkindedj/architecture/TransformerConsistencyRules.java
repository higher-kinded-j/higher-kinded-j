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
 * Architecture rules enforcing monad transformer consistency patterns.
 *
 * <p>Monad transformers (*T classes) should follow consistent patterns:
 *
 * <ul>
 *   <li>Have a lift method to lift values from the base monad
 *   <li>Have static factory methods (of, lift, etc.)
 *   <li>Have TKind interfaces and TKindHelper classes
 * </ul>
 *
 * <p>Note: Package location rules for transformers are in {@link PackageStructureRules}.
 */
@DisplayName("Transformer Consistency Rules")
class TransformerConsistencyRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Transformer monad implementations should have lift method.
   *
   * <p>The lift method allows lifting a value from the base monad into the transformer.
   */
  @Test
  @DisplayName("Transformer monad classes should have lift method")
  void transformer_monads_should_have_lift_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("TMonad")
        .and()
        .areNotInterfaces()
        .should(haveMethodNamed("lift"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * EitherT should have a static factory method.
   *
   * <p>Transformers should provide a convenient way to create instances.
   */
  @Test
  @DisplayName("EitherT should have static factory (of or lift)")
  void either_t_should_have_factory() {
    classes()
        .that()
        .haveSimpleName("EitherT")
        .and()
        .areNotInterfaces()
        .should(haveStaticMethodNamed("of"))
        .orShould(haveStaticMethodNamed("lift"))
        .orShould(haveStaticMethodNamed("right"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /** MaybeT should have a static factory method. */
  @Test
  @DisplayName("MaybeT should have static factory (of or lift)")
  void maybe_t_should_have_factory() {
    classes()
        .that()
        .haveSimpleName("MaybeT")
        .and()
        .areNotInterfaces()
        .should(haveStaticMethodNamed("of"))
        .orShould(haveStaticMethodNamed("lift"))
        .orShould(haveStaticMethodNamed("just"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /** StateT should have a static factory method. */
  @Test
  @DisplayName("StateT should have static factory (of or lift)")
  void state_t_should_have_factory() {
    classes()
        .that()
        .haveSimpleName("StateT")
        .and()
        .areNotInterfaces()
        .should(haveStaticMethodNamed("of"))
        .orShould(haveStaticMethodNamed("lift"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Transformer Kind interfaces should follow naming convention.
   *
   * <p>Each transformer should have a Kind interface (e.g., EitherTKind, MaybeTKind).
   */
  @Test
  @DisplayName("Transformer packages should have TKind interfaces")
  void transformer_packages_should_have_kind_interfaces() {
    classes()
        .that()
        .haveSimpleNameEndingWith("TKind")
        .should()
        .beInterfaces()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Transformer KindHelper classes should exist.
   *
   * <p>Each transformer should have a KindHelper for widen/narrow operations.
   */
  @Test
  @DisplayName("Transformer packages should have TKindHelper classes")
  void transformer_packages_should_have_kind_helpers() {
    classes()
        .that()
        .haveSimpleNameEndingWith("TKindHelper")
        .should()
        .haveOnlyPrivateConstructors()
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

  /**
   * Custom condition that checks if a class has a static method with the given name.
   *
   * @param methodName the name of the static method to check for
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveStaticMethodNamed(String methodName) {
    return new ArchCondition<>("have static method named '" + methodName + "'") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        boolean hasMethod =
            javaClass.getMethods().stream()
                .anyMatch(
                    method ->
                        method.getName().equals(methodName)
                            && method
                                .getModifiers()
                                .contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC));

        if (!hasMethod) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s does not have static method '%s'",
                      javaClass.getName(), methodName)));
        }
      }
    };
  }
}

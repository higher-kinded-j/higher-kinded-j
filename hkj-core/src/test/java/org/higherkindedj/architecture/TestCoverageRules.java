// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;
import static org.higherkindedj.architecture.ArchitectureTestBase.getTestClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.higherkindedj.hkt.Monad;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing test coverage patterns.
 *
 * <p>These rules ensure that type class implementations have corresponding test classes:
 *
 * <ul>
 *   <li>Monad implementations should have corresponding MonadTest classes
 *   <li>Test classes should verify type class laws
 * </ul>
 */
@DisplayName("Test Coverage Rules")
class TestCoverageRules {

  private static JavaClasses productionClasses;
  private static JavaClasses testClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
    testClasses = getTestClasses();
  }

  /**
   * Monad implementations should have corresponding test classes.
   *
   * <p>Each Monad implementation (e.g., MaybeMonad) should have a corresponding test class (e.g.,
   * MaybeMonadTest) to verify monad laws and behavior.
   */
  @Test
  @DisplayName("Monad implementations should have corresponding test classes")
  void monad_implementations_should_have_test_classes() {
    classes()
        .that()
        .implement(Monad.class)
        .and()
        .areNotInterfaces()
        .and()
        .areNotAnonymousClasses()
        .and()
        .haveSimpleNameEndingWith("Monad")
        .should(haveCorrespondingTestClass())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Custom condition that checks if a class has a corresponding test class.
   *
   * <p>The test class should be named {ClassName}Test and exist in the test classes.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveCorrespondingTestClass() {
    return new ArchCondition<>("have a corresponding test class") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        String expectedTestClassName = javaClass.getSimpleName() + "Test";
        String baseClassName = javaClass.getSimpleName();

        boolean hasTestClass =
            testClasses.stream()
                .anyMatch(
                    testClass ->
                        testClass.getSimpleName().equals(expectedTestClassName)
                            || testClass.getSimpleName().equals(baseClassName + "LawsTest")
                            || testClass.getSimpleName().contains(baseClassName));

        // Also check if covered by a test factory
        boolean coveredByTestFactory =
            testClasses.stream()
                .anyMatch(
                    testClass ->
                        testClass.getSimpleName().equals("MonadLawsTestFactory")
                            || testClass.getSimpleName().equals("FunctorLawsTestFactory"));

        if (!hasTestClass && !coveredByTestFactory) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  String.format(
                      "Class %s does not have a corresponding test class (expected %s or coverage"
                          + " in a TestFactory)",
                      javaClass.getName(), expectedTestClassName)));
        }
      }
    };
  }
}

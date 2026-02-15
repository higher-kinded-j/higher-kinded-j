// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.Traverse;
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

  private static final Set<String> PARAMETERIZED_MONADS =
      Set.of("StateMonad", "WriterMonad", "ReaderMonad", "FreeMonad", "EitherMonad");

  private static final Set<String> PARAMETERIZED_FUNCTORS_APPLICATIVES =
      Set.of(
          "StateFunctor",
          "StateApplicative",
          "WriterFunctor",
          "WriterApplicative",
          "ReaderFunctor",
          "ReaderApplicative",
          "FreeFunctor",
          "EitherFunctor",
          "EitherApplicative",
          "ValidatedFunctor",
          "ValidatedApplicative",
          "ValidatedSelective", // Requires Semigroup parameter
          "ConstFunctor",
          "ConstApplicative",
          // Base Functor/Applicative classes designed to be extended (not used directly)
          "TryFunctor",
          "TryApplicative",
          "OptionalFunctor",
          "CompletableFutureFunctor",
          "CompletableFutureApplicative");

  private static final Set<String> PARAMETERIZED_TYPE_CLASSES =
      Set.of(
          "StateFunctor",
          "StateApplicative",
          "StateMonad",
          "WriterFunctor",
          "WriterApplicative",
          "WriterMonad",
          "ReaderFunctor",
          "ReaderApplicative",
          "ReaderMonad",
          "FreeFunctor",
          "FreeMonad",
          "EitherFunctor",
          "EitherApplicative",
          "EitherMonad",
          "ValidatedFunctor",
          "ValidatedApplicative",
          "ValidatedMonad",
          "ValidatedSelective", // Has Semigroup field for error accumulation
          "ConstFunctor",
          "ConstApplicative");

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
   *
   * <p>Excludes:
   *
   * <ul>
   *   <li>Transformer monads (EitherTMonad, etc.) - require underlying monad parameter
   *   <li>Parameterized monads (StateMonad, WriterMonad, etc.) - require type parameters
   * </ul>
   */
  @Test
  @DisplayName("Monad implementations should have instance() method or INSTANCE constant")
  void monad_implementations_should_have_factory() {
    classes()
        .that(isNonParameterizedMonadImplementation())
        .should(haveStaticFactoryOrInstance())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Functor implementations should provide a static factory method or constant.
   *
   * <p>Excludes:
   *
   * <ul>
   *   <li>Classes that also implement Monad (they follow Monad patterns)
   *   <li>Parameterized functors (StateFunctor, etc.) - require type parameters
   * </ul>
   */
  @Test
  @DisplayName("Functor implementations should have instance() method or INSTANCE constant")
  void functor_implementations_should_have_factory() {
    classes()
        .that(isNonParameterizedFunctorImplementation())
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
   *
   * <p>Excludes:
   *
   * <ul>
   *   <li>Transformer monads (EitherTMonad, etc.) - need to store outer monad reference
   *   <li>Parameterized type classes (StateMonad, etc.) - need constructor parameters
   * </ul>
   */
  @Test
  @DisplayName("Type class implementations should be stateless (no instance fields)")
  void type_class_implementations_should_be_stateless() {
    classes()
        .that(isStatelessTypeClassCandidate())
        .should(haveNoNonStaticFields())
        .allowEmptyShould(true)
        .check(productionClasses);
  }

  /**
   * Predicate for non-parameterized Monad implementations.
   *
   * <p>Matches classes that:
   *
   * <ul>
   *   <li>Implement Monad
   *   <li>Are not interfaces
   *   <li>Are not anonymous classes
   *   <li>Don't end with "TMonad" (transformer monads)
   *   <li>Are not in the parameterized monads list
   * </ul>
   */
  private static DescribedPredicate<JavaClass> isNonParameterizedMonadImplementation() {
    return DescribedPredicate.describe(
        "is a non-parameterized Monad implementation",
        javaClass -> {
          if (javaClass.isInterface() || javaClass.isAnonymousClass()) {
            return false;
          }
          String simpleName = javaClass.getSimpleName();
          return javaClass.isAssignableTo(Monad.class)
              && !simpleName.endsWith("TMonad")
              && !PARAMETERIZED_MONADS.contains(simpleName);
        });
  }

  /**
   * Predicate for non-parameterized Functor implementations (excluding Monads and Traverse).
   *
   * <p>Matches classes that:
   *
   * <ul>
   *   <li>Implement Functor but not Monad or Traverse
   *   <li>Are not interfaces
   *   <li>Are not anonymous classes
   *   <li>Are not in the parameterized functors/applicatives list
   * </ul>
   */
  private static DescribedPredicate<JavaClass> isNonParameterizedFunctorImplementation() {
    return DescribedPredicate.describe(
        "is a non-parameterized Functor implementation (excluding Monads and Traverse)",
        javaClass -> {
          if (javaClass.isInterface() || javaClass.isAnonymousClass()) {
            return false;
          }
          // Monads are tested separately; Traverse implementations follow their own patterns
          return javaClass.isAssignableTo(Functor.class)
              && !javaClass.isAssignableTo(Monad.class)
              && !javaClass.isAssignableTo(Traverse.class)
              && !PARAMETERIZED_FUNCTORS_APPLICATIVES.contains(javaClass.getSimpleName());
        });
  }

  /**
   * Predicate for type class implementations that should be stateless.
   *
   * <p>Matches classes that:
   *
   * <ul>
   *   <li>Implement Functor but not Traverse
   *   <li>Are not interfaces
   *   <li>Are not anonymous classes
   *   <li>Don't end with "TMonad" (transformer monads store outer monad)
   *   <li>Are not in the parameterized type classes list
   * </ul>
   *
   * <p>Traverse implementations are excluded as they follow their own patterns and have separate
   * naming convention tests.
   */
  private static DescribedPredicate<JavaClass> isStatelessTypeClassCandidate() {
    return DescribedPredicate.describe(
        "is a type class implementation that should be stateless",
        javaClass -> {
          if (javaClass.isInterface() || javaClass.isAnonymousClass()) {
            return false;
          }
          // Traverse implementations follow their own patterns
          String simpleName = javaClass.getSimpleName();
          return javaClass.isAssignableTo(Functor.class)
              && !javaClass.isAssignableTo(Traverse.class)
              && !simpleName.endsWith("TMonad")
              && !PARAMETERIZED_TYPE_CLASSES.contains(simpleName);
        });
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

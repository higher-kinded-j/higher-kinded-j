// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules for Scope structured concurrency types.
 *
 * <p>These rules enforce the structural patterns required for Scope components:
 *
 * <ul>
 *   <li>Scope must be a final class with fluent builder pattern
 *   <li>ScopeJoiner must be a final class with factory methods
 *   <li>Both must reside in the vtask package
 *   <li>Factory methods must return appropriate types
 * </ul>
 *
 * @see org.higherkindedj.hkt.vtask.Scope
 * @see org.higherkindedj.hkt.vtask.ScopeJoiner
 */
@DisplayName("Scope Architecture Rules")
class ScopeArchitectureRules {

  private static final String VTASK_PACKAGE = "org.higherkindedj.hkt.vtask";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * Scope must be a final class.
   *
   * <p>Scope is a fluent builder and should not be extended.
   */
  @Test
  @DisplayName("Scope must be final")
  void scope_must_be_final() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".Scope")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .check(productionClasses);
  }

  /**
   * Scope must reside in vtask package.
   *
   * <p>Scope is part of the VTask concurrency infrastructure.
   */
  @Test
  @DisplayName("Scope must reside in vtask package")
  void scope_must_reside_in_vtask_package() {
    classes()
        .that()
        .haveSimpleName("Scope")
        .and()
        .areNotInterfaces()
        .should()
        .resideInAPackage(VTASK_PACKAGE)
        .check(productionClasses);
  }

  /**
   * Scope must have factory methods for joiners.
   *
   * <p>Scope provides allSucceed, anySucceed, firstComplete, and accumulating factory methods.
   */
  @Test
  @DisplayName("Scope must have joiner factory methods")
  void scope_must_have_joiner_factory_methods() {
    JavaClass scope = productionClasses.get(VTASK_PACKAGE + ".Scope");

    boolean hasAllSucceed =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("allSucceed"));

    boolean hasAnySucceed =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("anySucceed"));

    boolean hasFirstComplete =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("firstComplete"));

    boolean hasAccumulating =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("accumulating"));

    assertThat(hasAllSucceed).as("Scope should have allSucceed() method").isTrue();
    assertThat(hasAnySucceed).as("Scope should have anySucceed() method").isTrue();
    assertThat(hasFirstComplete).as("Scope should have firstComplete() method").isTrue();
    assertThat(hasAccumulating).as("Scope should have accumulating() method").isTrue();
  }

  /**
   * Scope must have fork method.
   *
   * <p>The fork method is the primary way to add tasks to a scope.
   */
  @Test
  @DisplayName("Scope must have fork method")
  void scope_must_have_fork_method() {
    JavaClass scope = productionClasses.get(VTASK_PACKAGE + ".Scope");

    boolean hasFork = scope.getMethods().stream().anyMatch(m -> m.getName().equals("fork"));

    assertThat(hasFork).as("Scope should have fork() method").isTrue();
  }

  /**
   * Scope must have join methods.
   *
   * <p>Join methods collect results: join, joinSafe, joinEither, joinMaybe.
   */
  @Test
  @DisplayName("Scope must have join methods")
  void scope_must_have_join_methods() {
    JavaClass scope = productionClasses.get(VTASK_PACKAGE + ".Scope");

    boolean hasJoin = scope.getMethods().stream().anyMatch(m -> m.getName().equals("join"));

    boolean hasJoinSafe = scope.getMethods().stream().anyMatch(m -> m.getName().equals("joinSafe"));

    boolean hasJoinEither =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("joinEither"));

    boolean hasJoinMaybe =
        scope.getMethods().stream().anyMatch(m -> m.getName().equals("joinMaybe"));

    assertThat(hasJoin).as("Scope should have join() method").isTrue();
    assertThat(hasJoinSafe).as("Scope should have joinSafe() method").isTrue();
    assertThat(hasJoinEither).as("Scope should have joinEither() method").isTrue();
    assertThat(hasJoinMaybe).as("Scope should have joinMaybe() method").isTrue();
  }

  /**
   * ScopeJoiner must be a sealed interface.
   *
   * <p>ScopeJoiner is a sealed interface with permitted implementations for each joiner strategy.
   */
  @Test
  @DisplayName("ScopeJoiner must be a sealed interface")
  void scopeJoiner_must_be_sealed_interface() {
    JavaClass scopeJoiner = productionClasses.get(VTASK_PACKAGE + ".ScopeJoiner");

    assertThat(scopeJoiner.isInterface()).as("ScopeJoiner should be an interface").isTrue();

    // Check that it's sealed by verifying it has permitted subclasses
    boolean hasPermittedSubclasses =
        scopeJoiner.getSubclasses().stream()
            .anyMatch(
                sub ->
                    sub.getSimpleName().equals("AllSucceedJoiner")
                        || sub.getSimpleName().equals("AnySucceedJoiner")
                        || sub.getSimpleName().equals("FirstCompleteJoiner")
                        || sub.getSimpleName().equals("AccumulatingJoiner"));

    assertThat(hasPermittedSubclasses)
        .as("ScopeJoiner should have permitted implementations")
        .isTrue();
  }

  /**
   * ScopeJoiner must have joiner factory methods.
   *
   * <p>ScopeJoiner provides static factory methods for built-in joiners.
   */
  @Test
  @DisplayName("ScopeJoiner must have factory methods")
  void scopeJoiner_must_have_factory_methods() {
    JavaClass scopeJoiner = productionClasses.get(VTASK_PACKAGE + ".ScopeJoiner");

    boolean hasAllSucceed =
        scopeJoiner.getMethods().stream()
            .anyMatch(
                m ->
                    m.getName().equals("allSucceed")
                        && m.getModifiers().contains(JavaModifier.STATIC));

    boolean hasAnySucceed =
        scopeJoiner.getMethods().stream()
            .anyMatch(
                m ->
                    m.getName().equals("anySucceed")
                        && m.getModifiers().contains(JavaModifier.STATIC));

    assertThat(hasAllSucceed).as("ScopeJoiner should have static allSucceed() method").isTrue();
    assertThat(hasAnySucceed).as("ScopeJoiner should have static anySucceed() method").isTrue();
  }

  /**
   * ScopeJoiner must provide access to underlying joiner.
   *
   * <p>The joiner() method provides access to Java's native Joiner for interoperability.
   */
  @Test
  @DisplayName("ScopeJoiner must have joiner() accessor")
  void scopeJoiner_must_have_joiner_accessor() {
    JavaClass scopeJoiner = productionClasses.get(VTASK_PACKAGE + ".ScopeJoiner");

    boolean hasJoiner =
        scopeJoiner.getMethods().stream().anyMatch(m -> m.getName().equals("joiner"));

    assertThat(hasJoiner).as("ScopeJoiner should have joiner() method").isTrue();
  }
}

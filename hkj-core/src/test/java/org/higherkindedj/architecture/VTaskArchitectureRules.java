// Copyright (c) 2025 Magnus Smith
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
 * Architecture rules specific to the VTask virtual thread effect type.
 *
 * <p>These rules enforce the structural patterns required for VTask components:
 *
 * <ul>
 *   <li>VTaskKind.Witness must be final with private constructor
 *   <li>VTaskKindHelper must be an enum utility class
 *   <li>VTaskMonad must follow singleton pattern (INSTANCE constant)
 *   <li>VTaskMonad must be stateless (no instance fields)
 *   <li>Par must be a utility class (final with private constructor)
 * </ul>
 *
 * @see org.higherkindedj.hkt.vtask.VTask
 * @see org.higherkindedj.hkt.vtask.VTaskMonad
 * @see org.higherkindedj.hkt.vtask.Par
 */
@DisplayName("VTask Architecture Rules")
class VTaskArchitectureRules {

  private static final String VTASK_PACKAGE = "org.higherkindedj.hkt.vtask";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * VTaskKind.Witness must be final.
   *
   * <p>Witness types are marker types for the HKT simulation and should never be extended.
   */
  @Test
  @DisplayName("VTaskKind.Witness must be final")
  void vtask_witness_must_be_final() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".VTaskKind$Witness")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .check(productionClasses);
  }

  /**
   * VTaskKind.Witness must have only private constructors.
   *
   * <p>Witness types should never be instantiated - they exist only as type markers.
   */
  @Test
  @DisplayName("VTaskKind.Witness must have only private constructors")
  void vtask_witness_must_have_private_constructors() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".VTaskKind$Witness")
        .should()
        .haveOnlyPrivateConstructors()
        .check(productionClasses);
  }

  /**
   * VTaskKindHelper must be an enum.
   *
   * <p>KindHelper classes in higher-kinded-j use the enum singleton pattern for zero-overhead type
   * conversions.
   */
  @Test
  @DisplayName("VTaskKindHelper must be an enum")
  void vtask_kind_helper_must_be_enum() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".VTaskKindHelper")
        .should()
        .beEnums()
        .check(productionClasses);
  }

  /**
   * VTaskMonad must have INSTANCE constant.
   *
   * <p>Type class instances follow the singleton pattern with a static INSTANCE constant.
   */
  @Test
  @DisplayName("VTaskMonad must have INSTANCE constant")
  void vtask_monad_must_have_instance_constant() {
    JavaClass vtaskMonad = productionClasses.get(VTASK_PACKAGE + ".VTaskMonad");

    boolean hasInstance =
        vtaskMonad.getFields().stream()
            .anyMatch(
                field ->
                    field.getName().equals("INSTANCE")
                        && field.getModifiers().contains(JavaModifier.STATIC)
                        && field.getModifiers().contains(JavaModifier.FINAL));

    assertThat(hasInstance).as("VTaskMonad should have a static final INSTANCE field").isTrue();
  }

  /**
   * VTaskMonad must be stateless.
   *
   * <p>Type class instances should have no instance fields (only static fields allowed).
   */
  @Test
  @DisplayName("VTaskMonad must be stateless (no instance fields)")
  void vtask_monad_must_be_stateless() {
    JavaClass vtaskMonad = productionClasses.get(VTASK_PACKAGE + ".VTaskMonad");

    long nonStaticFieldCount =
        vtaskMonad.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
            .count();

    assertThat(nonStaticFieldCount).as("VTaskMonad should have no instance fields").isZero();
  }

  /**
   * VTaskFunctor must have INSTANCE constant.
   *
   * <p>Functor instances follow the singleton pattern.
   */
  @Test
  @DisplayName("VTaskFunctor must have INSTANCE constant")
  void vtask_functor_must_have_instance_constant() {
    JavaClass vtaskFunctor = productionClasses.get(VTASK_PACKAGE + ".VTaskFunctor");

    boolean hasInstance =
        vtaskFunctor.getFields().stream()
            .anyMatch(
                field ->
                    field.getName().equals("INSTANCE")
                        && field.getModifiers().contains(JavaModifier.STATIC)
                        && field.getModifiers().contains(JavaModifier.FINAL));

    assertThat(hasInstance).as("VTaskFunctor should have a static final INSTANCE field").isTrue();
  }

  /**
   * VTaskApplicative must have INSTANCE constant.
   *
   * <p>Applicative instances follow the singleton pattern.
   */
  @Test
  @DisplayName("VTaskApplicative must have INSTANCE constant")
  void vtask_applicative_must_have_instance_constant() {
    JavaClass vtaskApplicative = productionClasses.get(VTASK_PACKAGE + ".VTaskApplicative");

    boolean hasInstance =
        vtaskApplicative.getFields().stream()
            .anyMatch(
                field ->
                    field.getName().equals("INSTANCE")
                        && field.getModifiers().contains(JavaModifier.STATIC)
                        && field.getModifiers().contains(JavaModifier.FINAL));

    assertThat(hasInstance)
        .as("VTaskApplicative should have a static final INSTANCE field")
        .isTrue();
  }

  /**
   * Par must be a final utility class.
   *
   * <p>Par provides static combinators for parallel VTask composition and should not be
   * instantiated or extended.
   */
  @Test
  @DisplayName("Par must be final")
  void par_must_be_final() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".Par")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .check(productionClasses);
  }

  /**
   * Par must have only private constructors.
   *
   * <p>Utility classes should not be instantiated.
   */
  @Test
  @DisplayName("Par must have only private constructors")
  void par_must_have_private_constructors() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".Par")
        .should()
        .haveOnlyPrivateConstructors()
        .check(productionClasses);
  }

  /**
   * Core VTask classes should reside in the vtask package.
   *
   * <p>Ensures core VTask-related classes are properly organized. Effect Path API classes
   * (VTaskPath, DefaultVTaskPath, VTaskContext, VTaskPathSteps*) are excluded as they correctly
   * reside in the effect package following the same pattern as IOPath.
   */
  @Test
  @DisplayName("Core VTask classes should reside in vtask package")
  void vtask_classes_should_reside_in_vtask_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("VTask")
        .and()
        .haveNameNotMatching(".*VTaskPath.*")
        .and()
        .haveNameNotMatching(".*VTaskContext.*")
        .should()
        .resideInAPackage(VTASK_PACKAGE)
        .check(productionClasses);
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing sealed type hierarchy conventions.
 *
 * <p>In higher-kinded-j, sealed types (algebraic data types) follow specific patterns:
 *
 * <ul>
 *   <li>Sealed interface variants should be in the same package as the sealed interface
 *   <li>Or nested within the sealed interface (like Either.Left, Either.Right)
 *   <li>Variant names should match their semantic role
 * </ul>
 *
 * <p>Examples:
 *
 * <ul>
 *   <li>{@code Maybe} sealed interface with {@code Just} and {@code Nothing} variants
 *   <li>{@code Either} sealed interface with nested {@code Left} and {@code Right} records
 *   <li>{@code Try} sealed interface with {@code Success} and {@code Failure} variants
 * </ul>
 */
@DisplayName("Sealed Type Hierarchy Rules")
class SealedTypeRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Just class should be in the maybe package.
   *
   * <p>The Just variant of Maybe represents a present value.
   */
  @Test
  @DisplayName("Just class should be in maybe package")
  void just_should_be_in_maybe_package() {
    classes()
        .that()
        .haveSimpleName("Just")
        .should()
        .resideInAPackage("..maybe..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Nothing class should be in the maybe package.
   *
   * <p>The Nothing variant of Maybe represents an absent value.
   */
  @Test
  @DisplayName("Nothing class should be in maybe package")
  void nothing_should_be_in_maybe_package() {
    classes()
        .that()
        .haveSimpleName("Nothing")
        .should()
        .resideInAPackage("..maybe..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Success class should be in the trymonad package.
   *
   * <p>The Success variant of Try represents a successful computation.
   */
  @Test
  @DisplayName("Success class should be in trymonad package")
  void success_should_be_in_try_package() {
    classes()
        .that()
        .haveSimpleName("Success")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..trymonad..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Failure class should be in the trymonad package.
   *
   * <p>The Failure variant of Try represents a failed computation with an exception.
   */
  @Test
  @DisplayName("Failure class should be in trymonad package")
  void failure_should_be_in_try_package() {
    classes()
        .that()
        .haveSimpleName("Failure")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..trymonad..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Valid class should be in the validated package.
   *
   * <p>The Valid variant of Validated represents valid data.
   */
  @Test
  @DisplayName("Valid class should be in validated package")
  void valid_should_be_in_validated_package() {
    classes()
        .that()
        .haveSimpleName("Valid")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..validated..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Invalid class should be in the validated package.
   *
   * <p>The Invalid variant of Validated represents accumulated validation errors.
   */
  @Test
  @DisplayName("Invalid class should be in validated package")
  void invalid_should_be_in_validated_package() {
    classes()
        .that()
        .haveSimpleName("Invalid")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..validated..")
        .allowEmptyShould(true)
        .check(classes);
  }
}

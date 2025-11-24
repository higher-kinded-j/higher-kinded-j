// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.higherkindedj.architecture.ArchitectureTestBase.getProductionClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing record usage for data types.
 *
 * <p>In higher-kinded-j, immutable data types should prefer records:
 *
 * <ul>
 *   <li>Algebraic data type variants should be records
 *   <li>Value types should be records or final classes
 *   <li>Data carriers in HKT packages should be records
 * </ul>
 */
@DisplayName("Record Preference Rules")
class RecordPreferenceRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Just variant of Maybe should be a record.
   *
   * <p>Records provide automatic equals/hashCode/toString and immutability.
   */
  @Test
  @DisplayName("Just should be a record")
  void just_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Just")
        .and()
        .resideInAPackage("..hkt.maybe..")
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Nothing variant of Maybe should be a record or final class.
   *
   * <p>Nothing is a singleton but should still be a value type.
   */
  @Test
  @DisplayName("Nothing should be a record or final class")
  void nothing_should_be_record_or_final() {
    classes()
        .that()
        .haveSimpleName("Nothing")
        .and()
        .resideInAPackage("..hkt.maybe..")
        .should()
        .beRecords()
        .orShould()
        .haveModifier(JavaModifier.FINAL)
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Success variant of Try should be a record. */
  @Test
  @DisplayName("Success should be a record")
  void success_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Success")
        .and()
        .resideInAPackage("..hkt.trymonad..")
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Failure variant of Try should be a record. */
  @Test
  @DisplayName("Failure should be a record")
  void failure_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Failure")
        .and()
        .resideInAPackage("..hkt.trymonad..")
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Valid variant of Validated should be a record. */
  @Test
  @DisplayName("Valid should be a record")
  void valid_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Valid")
        .and()
        .resideInAPackage("..hkt.validated..")
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Invalid variant of Validated should be a record. */
  @Test
  @DisplayName("Invalid should be a record")
  void invalid_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Invalid")
        .and()
        .resideInAPackage("..hkt.validated..")
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Tuple types should be records.
   *
   * <p>Tuple2, Tuple3, etc. are pure data carriers and should be records.
   */
  @Test
  @DisplayName("Tuple types should be records")
  void tuple_types_should_be_records() {
    classes()
        .that()
        .haveSimpleNameStartingWith("Tuple")
        .and()
        .resideInAPackage("..hkt.tuple..")
        .and()
        .areNotInterfaces()
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Id wrapper should be a record.
   *
   * <p>The identity monad wrapper is a simple data carrier.
   */
  @Test
  @DisplayName("Id should be a record")
  void id_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Id")
        .and()
        .resideInAPackage("..hkt.id..")
        .and()
        .areNotInterfaces()
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Const wrapper should be a record.
   *
   * <p>The constant functor wrapper is a simple data carrier.
   */
  @Test
  @DisplayName("Const should be a record")
  void const_should_be_record() {
    classes()
        .that()
        .haveSimpleName("Const")
        .and()
        .resideInAPackage("..hkt.constant..")
        .and()
        .areNotInterfaces()
        .should()
        .beRecords()
        .allowEmptyShould(true)
        .check(classes);
  }
}

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
 * Architecture rules enforcing package structure conventions.
 *
 * <p>These rules ensure consistent organization:
 *
 * <ul>
 *   <li>Each HKT type resides in its own package
 *   <li>Monad transformers use {@code _t} suffix in package names
 *   <li>Related classes are co-located in their type's package
 * </ul>
 */
@DisplayName("Package Structure Rules")
class PackageStructureRules {

  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes = getProductionClasses();
  }

  /**
   * Either-related classes should be in the either package.
   *
   * <p>All classes with "Either" in the name (except transformers) should be in
   * org.higherkindedj.hkt.either.
   */
  @Test
  @DisplayName("Either classes should reside in the either package")
  void either_classes_in_either_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("Either")
        .and()
        .haveSimpleNameNotContaining("EitherT") // Transformer is separate
        .and()
        .haveSimpleNameNotContaining("Path") // Effect Path API classes in effect package
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..either..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Maybe-related classes should be in the maybe package. */
  @Test
  @DisplayName("Maybe classes should reside in the maybe package")
  void maybe_classes_in_maybe_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("Maybe")
        .and()
        .haveSimpleNameNotContaining("MaybeT") // Transformer is separate
        .and()
        .haveSimpleNameNotContaining("Path") // Effect Path API classes in effect package
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..maybe..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Try-related classes should be in the trymonad package.
   *
   * <p>Note: Package is named 'trymonad' to avoid conflict with Java's 'try' keyword.
   */
  @Test
  @DisplayName("Try classes should reside in the trymonad package")
  void try_classes_in_try_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("Try")
        .and()
        .haveSimpleNameNotContaining("Path") // Effect Path API classes in effect package
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..trymonad..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** IO-related classes should be in the io package. */
  @Test
  @DisplayName("IO classes should reside in the io package")
  void io_classes_in_io_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("IO")
        .and()
        .resideInAPackage("..hkt..")
        .and()
        .haveSimpleNameNotContaining("IOException") // Standard Java exception
        .and()
        .haveSimpleNameNotContaining("Path") // Effect Path API classes in effect package
        .should()
        .resideInAPackage("..io..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * State-related classes should be in the state package.
   *
   * <p>StateT transformer is in state_t package.
   */
  @Test
  @DisplayName("State classes should reside in the state package")
  void state_classes_in_state_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("State")
        .and()
        .haveSimpleNameNotContaining("StateT") // Transformer is separate
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..state..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * EitherT transformer classes should be in either_t package.
   *
   * <p>Note: Excludes classes like EitherTraverse/EitherTraversals which are not transformers.
   */
  @Test
  @DisplayName("EitherT classes should reside in the either_t package")
  void either_t_classes_in_either_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("EitherT")
        .and()
        .haveSimpleNameNotContaining("Traverse") // EitherTraverse is not a transformer
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..either_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * MaybeT transformer classes should be in maybe_t package.
   *
   * <p>Note: Excludes classes like MaybeTraversals which are not transformers.
   */
  @Test
  @DisplayName("MaybeT classes should reside in the maybe_t package")
  void maybe_t_classes_in_maybe_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("MaybeT")
        .and()
        .haveSimpleNameNotContaining("Traverse") // MaybeTraversals is not a transformer
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..maybe_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * StateT transformer classes should be in state_t package.
   *
   * <p>Note: Excludes classes like StateTupleLensesManual which are not transformers.
   */
  @Test
  @DisplayName("StateT classes should reside in the state_t package")
  void state_t_classes_in_state_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("StateT")
        .and()
        .haveSimpleNameNotContaining("Tuple") // StateTupleLensesManual is not a transformer
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..state_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** ReaderT transformer classes should be in reader_t package. */
  @Test
  @DisplayName("ReaderT classes should reside in the reader_t package")
  void reader_t_classes_in_reader_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("ReaderT")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..reader_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** WriterT transformer classes should be in writer_t package. */
  @Test
  @DisplayName("WriterT classes should reside in the writer_t package")
  void writer_t_classes_in_writer_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("WriterT")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..writer_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * OptionalT transformer classes should be in optional_t package.
   *
   * <p>Note: Excludes classes like OptionalTraverse which are not transformers.
   */
  @Test
  @DisplayName("OptionalT classes should reside in the optional_t package")
  void optional_t_classes_in_optional_t_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("OptionalT")
        .and()
        .haveSimpleNameNotContaining("Traverse") // OptionalTraverse is not a transformer
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..optional_t..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** List-related classes should be in the list package. */
  @Test
  @DisplayName("List HKT classes should reside in the list package")
  void list_classes_in_list_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("List")
        .and()
        .resideInAPackage("..hkt..")
        .and()
        .resideOutsideOfPackage("..effect..") // Exclude effect package (ListPath is an Effect Path)
        .and()
        .haveSimpleNameNotContaining("java.util") // Exclude java.util.List
        .should()
        .resideInAPackage("..list..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Validated-related classes should be in the validated package. */
  @Test
  @DisplayName("Validated classes should reside in the validated package")
  void validated_classes_in_validated_package() {
    classes()
        .that()
        .haveSimpleNameStartingWith("Validated")
        .and()
        .resideInAPackage("..hkt..")
        .should()
        .resideInAPackage("..validated..")
        .allowEmptyShould(true)
        .check(classes);
  }
}

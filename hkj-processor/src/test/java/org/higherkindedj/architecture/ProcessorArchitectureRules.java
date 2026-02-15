// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import javax.annotation.processing.AbstractProcessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Architecture rules enforcing annotation processor patterns.
 *
 * <p>These rules ensure processors follow consistent patterns:
 *
 * <ul>
 *   <li>Processor classes must extend AbstractProcessor
 *   <li>Processor classes must be annotated with @AutoService
 *   <li>Processor naming conventions are followed
 *   <li>SPI interfaces are properly structured
 * </ul>
 */
@DisplayName("Processor Architecture Rules")
class ProcessorArchitectureRules {

  private static final String BASE_PACKAGE = "org.higherkindedj";
  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  /**
   * Processor classes must extend AbstractProcessor.
   *
   * <p>All annotation processors should extend the standard AbstractProcessor class.
   */
  @Test
  @DisplayName("Processor classes should extend AbstractProcessor")
  void processor_classes_should_extend_abstract_processor() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Processor")
        .and()
        .resideInAPackage("..processing..")
        .and()
        .areNotInterfaces()
        .should()
        .beAssignableTo(AbstractProcessor.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processor classes should follow naming convention.
   *
   * <p>Processors should be named {Feature}Processor (e.g., LensProcessor, PrismProcessor).
   */
  @Test
  @DisplayName("Classes extending AbstractProcessor should end with 'Processor'")
  void abstract_processor_subclasses_should_end_with_processor() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .haveSimpleNameEndingWith("Processor")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should be in the processing package.
   *
   * <p>All annotation processors should reside in the optics.processing package.
   */
  @Test
  @DisplayName("Processors should reside in processing package")
  void processors_should_be_in_processing_package() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .resideInAPackage("..optics.processing..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * SPI interfaces should be in the spi sub-package.
   *
   * <p>Service Provider Interfaces should be isolated in their own package.
   */
  @Test
  @DisplayName("SPI interfaces should be in spi package")
  void spi_interfaces_should_be_in_spi_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Generator")
        .and()
        .areInterfaces()
        .should()
        .resideInAPackage("..spi..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should not have mutable instance fields.
   *
   * <p>Annotation processors should be stateless to ensure thread safety.
   */
  @Test
  @DisplayName("Processors should not have mutable instance fields")
  void processors_should_not_have_mutable_instance_fields() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should(haveOnlyFinalOrStaticFields())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should be public.
   *
   * <p>Annotation processors need to be public to be discovered by the service loader.
   */
  @Test
  @DisplayName("Processors should be public classes")
  void processors_should_be_public() {
    classes()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .and()
        .areNotInterfaces()
        .and()
        .doNotHaveSimpleName("AbstractProcessor")
        .should()
        .bePublic()
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Processors should not depend on runtime HKT implementations.
   *
   * <p>Processors should only depend on the API module, not specific implementations.
   */
  @Test
  @DisplayName("Processors should not depend on HKT runtime implementations")
  void processors_should_not_depend_on_hkt_implementations() {
    noClasses()
        .that()
        .areAssignableTo(AbstractProcessor.class)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("..hkt.maybe..", "..hkt.either..", "..hkt.trymonad..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Generator interfaces should define required methods.
   *
   * <p>SPI generators should have supports() and generate*() methods.
   */
  @Test
  @DisplayName("Generator interfaces should have supports method")
  void generator_interfaces_should_have_supports_method() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Generator")
        .and()
        .areInterfaces()
        .should(haveMethodNamed("supports"))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Custom condition checking for final or static fields only.
   *
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> haveOnlyFinalOrStaticFields() {
    return new ArchCondition<>("have only final or static fields") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getFields().stream()
            .filter(field -> !field.getModifiers().contains(JavaModifier.STATIC))
            .filter(field -> !field.getModifiers().contains(JavaModifier.FINAL))
            .filter(field -> !field.getName().startsWith("$")) // Exclude synthetic
            .forEach(
                field ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "Processor %s has mutable instance field '%s'",
                                javaClass.getName(), field.getName()))));
      }
    };
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
                      "Interface %s does not have required method '%s'",
                      javaClass.getName(), methodName)));
        }
      }
    };
  }
}

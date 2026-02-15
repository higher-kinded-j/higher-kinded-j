// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;

/**
 * Architecture rules enforcing Spring Boot integration patterns.
 *
 * <p>These rules ensure the Spring integration follows best practices:
 *
 * <ul>
 *   <li>Auto-configuration classes in correct packages
 *   <li>Return value handlers implement correct interfaces
 *   <li>Proper separation between web, security, and actuator concerns
 *   <li>No circular dependencies between Spring components
 * </ul>
 */
@DisplayName("Spring Architecture Rules")
class SpringArchitectureRules {

  private static final String BASE_PACKAGE = "org.higherkindedj.spring";
  private static JavaClasses classes;

  @BeforeAll
  static void setup() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);
  }

  /**
   * Auto-configuration classes should be in autoconfigure package.
   *
   * <p>Spring Boot auto-configurations should be properly organized.
   */
  @Test
  @DisplayName("AutoConfiguration classes should be in autoconfigure package")
  void autoconfiguration_classes_should_be_in_autoconfigure_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("AutoConfiguration")
        .should()
        .resideInAPackage("..autoconfigure..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Return value handlers should implement HandlerMethodReturnValueHandler.
   *
   * <p>Custom return value handlers must implement the Spring interface.
   */
  @Test
  @DisplayName("ReturnValueHandler classes should implement HandlerMethodReturnValueHandler")
  void return_value_handlers_should_implement_interface() {
    classes()
        .that()
        .haveSimpleNameEndingWith("ReturnValueHandler")
        .and()
        .resideInAPackage("..web.returnvalue..")
        .should()
        .implement(HandlerMethodReturnValueHandler.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Return value handlers should be in the web.returnvalue package. */
  @Test
  @DisplayName("ReturnValueHandler classes should be in web.returnvalue package")
  void return_value_handlers_should_be_in_correct_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("ReturnValueHandler")
        .should()
        .resideInAPackage("..web.returnvalue..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Jackson serializers should be in the json package. */
  @Test
  @DisplayName("Serializer classes should be in json package")
  void serializers_should_be_in_json_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Serializer")
        .or()
        .haveSimpleNameEndingWith("Deserializer")
        .should()
        .resideInAPackage("..json..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Security classes should be in the security package. */
  @Test
  @DisplayName("Security integration classes should be in security package")
  void security_classes_should_be_in_security_package() {
    classes()
        .that()
        .haveSimpleNameContaining("Authentication")
        .or()
        .haveSimpleNameContaining("Authorization")
        .or()
        .haveSimpleNameContaining("UserDetails")
        .should()
        .resideInAPackage("..security..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /** Actuator classes should be in the actuator package. */
  @Test
  @DisplayName("Actuator integration classes should be in actuator package")
  void actuator_classes_should_be_in_actuator_package() {
    classes()
        .that()
        .haveSimpleNameContaining("Metrics")
        .or()
        .haveSimpleNameContaining("HealthIndicator")
        .or()
        .haveSimpleNameContaining("Endpoint")
        .and()
        .resideInAPackage("..spring..")
        .should()
        .resideInAPackage("..actuator..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Configuration properties should follow naming convention.
   *
   * <p>Properties classes should be named *Properties and be in autoconfigure package.
   */
  @Test
  @DisplayName("Properties classes should be in autoconfigure package")
  void properties_classes_should_be_in_autoconfigure_package() {
    classes()
        .that()
        .haveSimpleNameEndingWith("Properties")
        .and()
        .resideInAPackage("..spring..")
        .should()
        .resideInAPackage("..autoconfigure..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Web layer should not depend on security implementation details.
   *
   * <p>Return value handlers should not directly use security classes.
   */
  @Test
  @DisplayName("Web return value handlers should not depend on security classes")
  void web_handlers_should_not_depend_on_security() {
    noClasses()
        .that()
        .resideInAPackage("..web.returnvalue..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..security..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Security should not depend on actuator.
   *
   * <p>Security classes should not depend on monitoring/metrics classes.
   */
  @Test
  @DisplayName("Security classes should not depend on actuator")
  void security_should_not_depend_on_actuator() {
    noClasses()
        .that()
        .resideInAPackage("..security..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..actuator..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Actuator should not depend on web handlers.
   *
   * <p>Metrics and health indicators should not depend on HTTP handling details.
   */
  @Test
  @DisplayName("Actuator classes should not depend on web handlers")
  void actuator_should_not_depend_on_web_handlers() {
    noClasses()
        .that()
        .resideInAPackage("..actuator..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..web.returnvalue..")
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Auto-configuration should use conditional annotations.
   *
   * <p>Auto-configuration classes should be conditionally activated.
   */
  @Test
  @DisplayName("AutoConfiguration classes should be annotated with @AutoConfiguration")
  void autoconfiguration_should_have_annotation() {
    classes()
        .that()
        .haveSimpleNameEndingWith("AutoConfiguration")
        .should()
        .beAnnotatedWith(AutoConfiguration.class)
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * HKJ classes should start with Hkj prefix.
   *
   * <p>Spring integration classes should follow the Hkj naming convention.
   */
  @Test
  @DisplayName("Spring integration classes should follow Hkj naming convention")
  void spring_classes_should_follow_naming_convention() {
    classes()
        .that()
        .resideInAPackage("..autoconfigure..")
        .and()
        .haveSimpleNameNotEndingWith("Test")
        .and()
        .areNotAnonymousClasses()
        .and()
        .areNotMemberClasses()
        .should()
        .haveSimpleNameStartingWith("Hkj")
        .allowEmptyShould(true)
        .check(classes);
  }
}

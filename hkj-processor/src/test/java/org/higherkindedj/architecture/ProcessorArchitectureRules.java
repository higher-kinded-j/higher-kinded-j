// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Set;
import java.util.stream.Stream;
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

  /** The SPI generator lookup that every widening site must reach through the guard. */
  private static final String SPI_LOOKUP = "findSpiGenerator";

  /** The methods that may reach the lookup directly: the guard itself, and the diagnostic walk. */
  private static final Set<String> SPI_LOOKUP_CALLERS =
      Set.of("wideningGenerator", "reportUndenotableWidening", "widensUndenotableSpiContainer");

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
   * Every widening site must read its SPI generator through the guard.
   *
   * <p>A generator widens by handing the path an optic instance whose type arguments javac infers
   * from the field type, which a raw or wildcard-carrying container gives it no way to do (#718).
   * {@code wideningGenerator} is the one place such a container is turned away, so a site that
   * looks a generator up itself would widen it anyway and emit source that cannot compile. The
   * diagnostic walk is exempt: it exists to report the container the guard turned away.
   */
  @Test
  @DisplayName("SPI generator lookups should go through the widening guard")
  void spi_generator_lookups_should_go_through_the_widening_guard() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(lookUpSpiGeneratorsOnlyFrom(SPI_LOOKUP_CALLERS))
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * Every generator choice must come from the registry.
   *
   * <p>{@code GeneratorRegistry} is the one place that reads {@code supports()} and {@code
   * priority()}, which is what makes priority mean the same thing on every route
   * ({@code @GenerateFocus}, {@code @GenerateTraversals}, {@code @ImportOptics}). A site looping
   * over generators itself would reintroduce first-match resolution, where a {@code
   * PRIORITY_OVERRIDE} provider wins or loses by where its {@code META-INF/services} entry lands
   * (#774).
   */
  @Test
  @DisplayName("Generator selection should go through the registry")
  void generator_selection_should_go_through_the_registry() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(chooseSpiGeneratorsOnlyFromTheRegistry())
        .allowEmptyShould(true)
        .check(classes);
  }

  /**
   * The registry answers only the route lookups.
   *
   * <p>The widening guard (#718) keys on {@code findSpiGenerator}, which is now a delegate, so a
   * site could otherwise read a generator straight from {@code GeneratorRegistry.generatorFor} and
   * skip {@code wideningGenerator}'s turn-away of raw and wildcard-carrying containers. The
   * delegate and the two non-widening route sites are the only permitted readers.
   */
  @Test
  @DisplayName("Registry reads should stay behind the route lookups")
  void registry_reads_should_stay_behind_the_route_lookups() {
    classes()
        .that()
        .resideInAPackage("..processing..")
        .should(readTheRegistryOnlyFrom(REGISTRY_READERS))
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
   * Custom condition that no method outside {@code allowed} looks an SPI generator up directly.
   *
   * @param allowed the names of the methods that may call the lookup
   * @return the arch condition
   */
  private static ArchCondition<JavaClass> lookUpSpiGeneratorsOnlyFrom(Set<String> allowed) {
    return new ArchCondition<>("look SPI generators up only from " + allowed) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        javaClass.getMethodCallsFromSelf().stream()
            .filter(call -> call.getTarget().getName().equals(SPI_LOOKUP))
            .filter(call -> !allowed.contains(call.getOrigin().getName()))
            .forEach(
                call ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s calls %s directly. Read the generator from"
                                    + " wideningGenerator instead, so that a raw or"
                                    + " wildcard-carrying container is turned away rather than"
                                    + " widened into source that cannot compile.",
                                javaClass.getSimpleName(),
                                call.getOrigin().getName(),
                                SPI_LOOKUP))));
      }
    };
  }

  /** The interface whose choosing methods only the registry may consult. */
  private static final String TRAVERSABLE_GENERATOR =
      "org.higherkindedj.optics.processing.spi.TraversableGenerator";

  /** The single home for generator selection. */
  private static final String GENERATOR_REGISTRY =
      "org.higherkindedj.optics.processing.GeneratorRegistry";

  /** The methods that may read a choice from the registry: the delegate and the two route sites. */
  private static final Set<String> REGISTRY_READERS =
      Set.of("findSpiGenerator", "generateTraversalsFile", "createTraversalMethod");

  /** Method calls and method references from {@code javaClass}, which decide targets alike. */
  private static Stream<JavaAccess<?>> callsAndReferencesFrom(JavaClass javaClass) {
    return Stream.concat(
        javaClass.getMethodCallsFromSelf().stream(),
        javaClass.getMethodReferencesFromSelf().stream());
  }

  private static ArchCondition<JavaClass> chooseSpiGeneratorsOnlyFromTheRegistry() {
    return new ArchCondition<>(
        "consult TraversableGenerator.supports/priority only from GeneratorRegistry") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        if (javaClass.getName().equals(GENERATOR_REGISTRY)) {
          return;
        }
        // Method references count (the deleted pre-sort was TraversableGenerator::priority), and
        // the owner is matched by assignability: a call through a concrete generator's own type
        // resolves to the subtype in bytecode.
        callsAndReferencesFrom(javaClass)
            .filter(access -> access.getTarget().getOwner().isAssignableTo(TRAVERSABLE_GENERATOR))
            .filter(access -> Set.of("supports", "priority").contains(access.getTarget().getName()))
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s picks a generator itself. Read the choice from"
                                    + " GeneratorRegistry.generatorFor, so that priority() keeps"
                                    + " meaning the same thing on every route.",
                                javaClass.getSimpleName(), access.getOrigin().getName()))));
      }
    };
  }

  private static ArchCondition<JavaClass> readTheRegistryOnlyFrom(Set<String> allowed) {
    return new ArchCondition<>("read GeneratorRegistry.generatorFor only from " + allowed) {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        callsAndReferencesFrom(javaClass)
            .filter(access -> access.getTarget().getOwner().isAssignableTo(GENERATOR_REGISTRY))
            .filter(access -> access.getTarget().getName().equals("generatorFor"))
            .filter(access -> !allowed.contains(access.getOrigin().getName()))
            .forEach(
                access ->
                    events.add(
                        SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                "%s.%s reads a generator straight from the registry. Go through"
                                    + " wideningGenerator or the route's one lookup, so that a"
                                    + " raw or wildcard-carrying container is turned away rather"
                                    + " than widened into source that cannot compile.",
                                javaClass.getSimpleName(), access.getOrigin().getName()))));
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

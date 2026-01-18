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
 * Architecture rules for Resource bracket pattern type.
 *
 * <p>These rules enforce the structural patterns required for Resource:
 *
 * <ul>
 *   <li>Resource must be a final class implementing the bracket pattern
 *   <li>Resource must reside in the vtask package
 *   <li>Resource must have factory methods for creation
 *   <li>Resource must have use methods for executing with guaranteed release
 * </ul>
 *
 * @see org.higherkindedj.hkt.vtask.Resource
 */
@DisplayName("Resource Architecture Rules")
class ResourceArchitectureRules {

  private static final String VTASK_PACKAGE = "org.higherkindedj.hkt.vtask";

  private static JavaClasses productionClasses;

  @BeforeAll
  static void setup() {
    productionClasses = getProductionClasses();
  }

  /**
   * Resource must be a final class.
   *
   * <p>Resource implements the bracket pattern and should not be extended.
   */
  @Test
  @DisplayName("Resource must be final")
  void resource_must_be_final() {
    classes()
        .that()
        .haveFullyQualifiedName(VTASK_PACKAGE + ".Resource")
        .should()
        .haveModifier(JavaModifier.FINAL)
        .check(productionClasses);
  }

  /**
   * Resource must reside in vtask package.
   *
   * <p>Resource is part of the VTask concurrency infrastructure.
   */
  @Test
  @DisplayName("Resource must reside in vtask package")
  void resource_must_reside_in_vtask_package() {
    classes()
        .that()
        .haveSimpleName("Resource")
        .and()
        .areNotInterfaces()
        .and()
        .resideInAPackage("..vtask..")
        .should()
        .resideInAPackage(VTASK_PACKAGE)
        .check(productionClasses);
  }

  /**
   * Resource must have factory methods.
   *
   * <p>Resource provides make, fromAutoCloseable, and pure factory methods.
   */
  @Test
  @DisplayName("Resource must have factory methods")
  void resource_must_have_factory_methods() {
    JavaClass resource = productionClasses.get(VTASK_PACKAGE + ".Resource");

    boolean hasMake =
        resource.getMethods().stream()
            .anyMatch(
                m -> m.getName().equals("make") && m.getModifiers().contains(JavaModifier.STATIC));

    boolean hasFromAutoCloseable =
        resource.getMethods().stream()
            .anyMatch(
                m ->
                    m.getName().equals("fromAutoCloseable")
                        && m.getModifiers().contains(JavaModifier.STATIC));

    boolean hasPure =
        resource.getMethods().stream()
            .anyMatch(
                m -> m.getName().equals("pure") && m.getModifiers().contains(JavaModifier.STATIC));

    assertThat(hasMake).as("Resource should have static make() method").isTrue();
    assertThat(hasFromAutoCloseable)
        .as("Resource should have static fromAutoCloseable() method")
        .isTrue();
    assertThat(hasPure).as("Resource should have static pure() method").isTrue();
  }

  /**
   * Resource must have use methods.
   *
   * <p>The use and useSync methods execute computations with guaranteed resource release.
   */
  @Test
  @DisplayName("Resource must have use methods")
  void resource_must_have_use_methods() {
    JavaClass resource = productionClasses.get(VTASK_PACKAGE + ".Resource");

    boolean hasUse = resource.getMethods().stream().anyMatch(m -> m.getName().equals("use"));

    boolean hasUseSync =
        resource.getMethods().stream().anyMatch(m -> m.getName().equals("useSync"));

    assertThat(hasUse).as("Resource should have use() method").isTrue();
    assertThat(hasUseSync).as("Resource should have useSync() method").isTrue();
  }

  /**
   * Resource must have composition methods.
   *
   * <p>Resource supports map, flatMap, and and for composition.
   */
  @Test
  @DisplayName("Resource must have composition methods")
  void resource_must_have_composition_methods() {
    JavaClass resource = productionClasses.get(VTASK_PACKAGE + ".Resource");

    boolean hasMap = resource.getMethods().stream().anyMatch(m -> m.getName().equals("map"));

    boolean hasFlatMap =
        resource.getMethods().stream().anyMatch(m -> m.getName().equals("flatMap"));

    boolean hasAnd = resource.getMethods().stream().anyMatch(m -> m.getName().equals("and"));

    assertThat(hasMap).as("Resource should have map() method").isTrue();
    assertThat(hasFlatMap).as("Resource should have flatMap() method").isTrue();
    assertThat(hasAnd).as("Resource should have and() method").isTrue();
  }

  /**
   * Resource must have finaliser support.
   *
   * <p>The withFinalizer method adds cleanup actions that run after release.
   */
  @Test
  @DisplayName("Resource must have withFinalizer method")
  void resource_must_have_finalizer_support() {
    JavaClass resource = productionClasses.get(VTASK_PACKAGE + ".Resource");

    boolean hasWithFinalizer =
        resource.getMethods().stream().anyMatch(m -> m.getName().equals("withFinalizer"));

    assertThat(hasWithFinalizer).as("Resource should have withFinalizer() method").isTrue();
  }
}

// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FocusProcessor Navigator Generation Tests")
public class FocusProcessorNavigatorTest {

  @Nested
  @DisplayName("Basic Navigator Generation")
  class BasicNavigatorGeneration {

    @Test
    @DisplayName("should generate navigator class for navigable field")
    void shouldGenerateNavigatorClassForNavigableField() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      // Expected: Navigator method returns navigator instead of FocusPath
      final String expectedNavigatorMethod =
          """
          public static HeadquartersNavigator<Company> headquarters() {
          """;

      // Expected: Navigator inner class
      final String expectedNavigatorClass =
          """
          public static final class HeadquartersNavigator<S> {
          """;

      // Expected: Delegate field
      final String expectedDelegateField =
          """
          private final FocusPath<S, Address> delegate;
          """;

      // Expected: Navigation methods for Address fields
      final String expectedStreetNavigation =
          """
          public FocusPath<S, String> street() {
          """;

      final String expectedCityNavigation =
          """
          public FocusPath<S, String> city() {
          """;

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompanyFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedNavigatorMethod);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedNavigatorClass);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedDelegateField);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedStreetNavigation);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedCityNavigation);
    }

    @Test
    @DisplayName("should generate standard FocusPath for non-navigable fields")
    void shouldGenerateStandardFocusPathForNonNavigableFields() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, int employeeCount) {}
              """);

      // Non-navigable fields should still get standard FocusPath methods
      final String expectedNameMethod =
          """
          public static FocusPath<Company, String> name() {
          """;

      final String expectedCountMethod =
          """
          public static FocusPath<Company, Integer> employeeCount() {
          """;

      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(companySource);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompanyFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedNameMethod);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedCountMethod);
    }

    @Test
    @DisplayName("should not generate navigators when generateNavigators is false")
    void shouldNotGenerateNavigatorsWhenDisabled() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = false)
              public record Company(String name, Address headquarters) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus
              public record Address(String street, String city) {}
              """);

      // Should generate standard FocusPath, not navigator
      final String expectedStandardMethod =
          """
          public static FocusPath<Company, Address> headquarters() {
          """;

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompanyFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedStandardMethod);
    }
  }

  @Nested
  @DisplayName("Navigator Delegate Methods")
  class NavigatorDelegateMethods {

    @Test
    @DisplayName("should generate FocusPath delegate methods")
    void shouldGenerateFocusPathDelegateMethods() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      // Expected delegate methods
      final String expectedGetMethod =
          """
          public Address get(S source) {
              return delegate.get(source);
          }
          """;

      final String expectedSetMethod =
          """
          public S set(Address value, S source) {
              return delegate.set(value, source);
          }
          """;

      final String expectedModifyMethod =
          """
          public S modify(java.util.function.Function<Address, Address> f, S source) {
              return delegate.modify(f, source);
          }
          """;

      final String expectedToPathMethod =
          """
          public FocusPath<S, Address> toPath() {
              return delegate;
          }
          """;

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      final String generatedClassName = "com.example.CompanyFocus";
      assertGeneratedCodeContains(compilation, generatedClassName, expectedGetMethod);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedSetMethod);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedModifyMethod);
      assertGeneratedCodeContains(compilation, generatedClassName, expectedToPathMethod);
    }
  }

  @Nested
  @DisplayName("Depth Limiting")
  class DepthLimiting {

    @Test
    @DisplayName("should respect maxNavigatorDepth setting")
    void shouldRespectMaxNavigatorDepth() {
      final JavaFileObject level1Source =
          JavaFileObjects.forSourceString(
              "com.example.Level1",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 1)
              public record Level1(String name, Level2 nested) {}
              """);

      final JavaFileObject level2Source =
          JavaFileObjects.forSourceString(
              "com.example.Level2",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Level2(String value, Level3 deeper) {}
              """);

      final JavaFileObject level3Source =
          JavaFileObjects.forSourceString(
              "com.example.Level3",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Level3(String data) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(level1Source, level2Source, level3Source);

      assertThat(compilation).succeeded();

      // Level1Focus should have a navigator for Level2
      final String expectedNestedNavigator =
          """
          public static NestedNavigator<Level1> nested() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.Level1Focus", expectedNestedNavigator);
    }
  }

  @Nested
  @DisplayName("Field Filtering")
  class FieldFiltering {

    @Test
    @DisplayName("should only include fields specified in includeFields")
    void shouldOnlyIncludeSpecifiedFields() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, includeFields = {"headquarters"})
              public record Company(String name, Address headquarters, Address backup) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Should have navigator for headquarters
      final String expectedHeadquartersNavigator =
          """
          public static HeadquartersNavigator<Company> headquarters() {
          """;

      // backup should be standard FocusPath (not in includeFields)
      final String expectedBackupStandard =
          """
          public static FocusPath<Company, Address> backup() {
          """;

      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", expectedHeadquartersNavigator);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedBackupStandard);
    }

    @Test
    @DisplayName("should exclude fields specified in excludeFields")
    void shouldExcludeSpecifiedFields() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, excludeFields = {"backup"})
              public record Company(String name, Address headquarters, Address backup) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Should have navigator for headquarters (not excluded)
      final String expectedHeadquartersNavigator =
          """
          public static HeadquartersNavigator<Company> headquarters() {
          """;

      // backup should be standard FocusPath (excluded)
      final String expectedBackupStandard =
          """
          public static FocusPath<Company, Address> backup() {
          """;

      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", expectedHeadquartersNavigator);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedBackupStandard);
    }
  }

  @Nested
  @DisplayName("Path Type Widening")
  class PathTypeWidening {

    @Test
    @DisplayName("should detect PathKind correctly for regular types")
    void shouldDetectPathKindForRegularTypes() {
      // This tests that regular types result in FOCUS path kind
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Navigation through FocusPath should return FocusPath
      final String expectedFocusPathReturn =
          """
          public FocusPath<S, String> street() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedFocusPathReturn);
    }
  }

  @Nested
  @DisplayName("Nested Navigation")
  class NestedNavigation {

    @Test
    @DisplayName("should generate navigation for deeply nested types")
    void shouldGenerateNavigationForDeeplyNestedTypes() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Company(String name, Department mainDept) {}
              """);

      final JavaFileObject departmentSource =
          JavaFileObjects.forSourceString(
              "com.example.Department",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Department(String name, Team leadTeam) {}
              """);

      final JavaFileObject teamSource =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Team(String name, int size) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(companySource, departmentSource, teamSource);

      assertThat(compilation).succeeded();

      // Company should have MainDeptNavigator
      final String expectedMainDeptNavigator =
          """
          public static MainDeptNavigator<Company> mainDept() {
          """;

      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", expectedMainDeptNavigator);
    }
  }

  @Nested
  @DisplayName("Javadoc Generation")
  class JavadocGeneration {

    @Test
    @DisplayName("should include navigator note in class javadoc when enabled")
    void shouldIncludeNavigatorNoteInJavadoc() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // The Javadoc should mention navigator classes (use raw assertion to preserve comments)
      final String expectedJavadocNote = "navigator classes for fluent cross-type navigation";

      assertGeneratedCodeContainsRaw(compilation, "com.example.CompanyFocus", expectedJavadocNote);
    }
  }
}

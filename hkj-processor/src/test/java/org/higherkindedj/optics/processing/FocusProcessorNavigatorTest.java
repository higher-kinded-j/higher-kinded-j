// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContainsRaw;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
  @DisplayName("SPI-Aware Navigator Path Widening")
  class SpiAwareNavigatorPathWidening {

    @Test
    @DisplayName("should widen navigator field to TraversalPath for Map fields via SPI")
    void shouldWidenNavigatorFieldToTraversalPathForMapViaSpi() {
      // When a navigator's target record has a Map field, the navigation method for that
      // field should return TraversalPath (Map is recognised via MapValueGenerator SPI)
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
              import java.util.Map;

              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Address(String street, Map<String, String> metadata) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Inside HeadquartersNavigator, the metadata() method should return TraversalPath
      // with the inner value type (Map values are String), because Map is recognised as
      // ZERO_OR_MORE by the SPI and Address widens its collections.
      final String expectedMetadataTraversal =
          """
          public TraversalPath<S, String> metadata() {
          """;

      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", expectedMetadataTraversal);
      // The static method on the declaring record reports the same path type.
      assertGeneratedCodeContains(
          compilation, "com.example.AddressFocus", "TraversalPath<Address, String> metadata()");
    }

    @Test
    @DisplayName("should leave a Map field a FocusPath in a navigator without widenCollections")
    void shouldLeaveMapFieldAFocusPathWithoutWidenCollections() {
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
              import java.util.Map;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Map<String, String> metadata) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // A ZERO_OR_MORE SPI container is left un-widened until the record that declares it says
      // otherwise, and the navigator reads that record's setting rather than its own (#719).
      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", "FocusPath<S, Map<String, String>> metadata()");
      assertGeneratedCodeContains(
          compilation,
          "com.example.AddressFocus",
          "FocusPath<Address, Map<String, String>> metadata()");
    }

    @Test
    @DisplayName("should widen navigator field to AffinePath for Either fields via SPI")
    void shouldWidenNavigatorFieldToAffinePathForEitherViaSpi() {
      // When a navigator's target record has an Either field, the navigation method should
      // return AffinePath (Either is recognised via EitherGenerator SPI with ZERO_OR_ONE)
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
              import org.higherkindedj.hkt.either.Either;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Either<String, String> validated) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Inside HeadquartersNavigator, the validated() method should return AffinePath
      // with the inner type (Either<String,String> focuses on String at index 1),
      // because Either is recognised as ZERO_OR_ONE by the SPI and the navigator
      // applies .some(opticExpr) to unwrap.
      final String expectedEitherAffine =
          """
          public AffinePath<S, String> validated() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedEitherAffine);
    }

    @Test
    @DisplayName("should widen navigator field to AffinePath for Try fields via SPI")
    void shouldWidenNavigatorFieldToAffinePathForTryViaSpi() {
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
              import org.higherkindedj.hkt.trymonad.Try;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Try<String> verifiedCity) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Try<String> focuses on String (the success type), so the navigator method
      // returns AffinePath with the inner type after applying .some(opticExpr).
      final String expectedTryAffine =
          """
          public AffinePath<S, String> verifiedCity() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedTryAffine);
    }

    @Test
    @DisplayName("should widen navigator field to AffinePath for Validated fields via SPI")
    void shouldWidenNavigatorFieldToAffinePathForValidatedViaSpi() {
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
              import org.higherkindedj.hkt.validated.Validated;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Validated<String, String> checkedCity) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Validated<String, String> focuses on String (the valid type at index 1),
      // so the navigator method returns AffinePath with the inner type.
      final String expectedValidatedAffine =
          """
          public AffinePath<S, String> checkedCity() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedValidatedAffine);
    }

    @Test
    @DisplayName("should widen SPI AFFINE and TRAVERSAL fields within the same navigator")
    void shouldWidenMultipleSpiFieldsWithinNavigator() {
      // A navigable record with both AFFINE (Either) and TRAVERSAL (Map) SPI fields.
      // Inside the navigator, each field should have the correct widened return type.
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
              import org.higherkindedj.hkt.either.Either;
              import java.util.Map;

              @GenerateFocus(generateNavigators = true, widenCollections = true)
              public record Address(
                  String street,
                  Either<String, String> validated,
                  Map<String, String> metadata) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Inside HeadquartersNavigator (FOCUS delegate):
      // validated() should be AffinePath with inner type (Either focuses on String at index 1)
      final String expectedEitherAffine =
          """
          public AffinePath<S, String> validated() {
          """;

      // metadata() should be TraversalPath with inner value type (Map focuses on String at index 1)
      final String expectedMapTraversal =
          """
          public TraversalPath<S, String> metadata() {
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedEitherAffine);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedMapTraversal);
    }

    @Test
    @DisplayName("should widen to AffinePath for Either field via SPI")
    void shouldWidenToAffinePathForEitherField() {
      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.hkt.either.Either;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Either<String, Address> result) {}
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

      // The navigator delegate for an Either field should use AffinePath
      final String expectedNavigatorClass =
          """
          public static final class ResultNavigator<S> {
          """;

      final String expectedDelegateField =
          """
          private final AffinePath<S, Address> delegate;
          """;

      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedNavigatorClass);
      assertGeneratedCodeContains(compilation, "com.example.CompanyFocus", expectedDelegateField);
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

  @Nested
  @DisplayName("Cross-Package Navigator Generation")
  class CrossPackageNavigatorGeneration {

    @Test
    @DisplayName("should compile when navigator references record in different package")
    void shouldCompileWithCrossPackageNavigator() {
      // Record in package org.example.a
      final JavaFileObject recordASource =
          JavaFileObjects.forSourceString(
              "org.example.a.MyRecordInA",
              """
              package org.example.a;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record MyRecordInA(String someField) {}
              """);

      // Record in package org.example.b that references the record in org.example.a
      final JavaFileObject recordBSource =
          JavaFileObjects.forSourceString(
              "org.example.b.MyRecordInB",
              """
              package org.example.b;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.example.a.MyRecordInA;

              @GenerateFocus(generateNavigators = true)
              public record MyRecordInB(MyRecordInA crossReference) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(recordASource, recordBSource);

      // The critical assertion: the generated code must compile successfully.
      // Before the fix, the generated MyRecordInBFocus referenced MyRecordInAFocus
      // without importing it, causing a compilation error.
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("should generate import for cross-package Focus class in navigator via statement")
    void shouldGenerateImportForCrossPackageFocusClass() {
      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "org.example.a.Address",
              """
              package org.example.a;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, String city) {}
              """);

      final JavaFileObject companySource =
          JavaFileObjects.forSourceString(
              "org.example.b.Company",
              """
              package org.example.b;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.example.a.Address;

              @GenerateFocus(generateNavigators = true)
              public record Company(String name, Address headquarters) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(addressSource, companySource);

      assertThat(compilation).succeeded();

      // Verify the generated code imports AddressFocus from the other package
      assertGeneratedCodeContainsRaw(
          compilation, "org.example.b.CompanyFocus", "import org.example.a.AddressFocus;");

      // Verify the navigator's via statement references AddressFocus (not unqualified)
      assertGeneratedCodeContainsRaw(
          compilation, "org.example.b.CompanyFocus", "AddressFocus.street()");

      assertGeneratedCodeContainsRaw(
          compilation, "org.example.b.CompanyFocus", "AddressFocus.city()");
    }

    @Test
    @DisplayName("should compile with deeply nested cross-package navigation")
    void shouldCompileWithDeeplyNestedCrossPackageNavigation() {
      final JavaFileObject citySource =
          JavaFileObjects.forSourceString(
              "org.example.geo.City",
              """
              package org.example.geo;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record City(String name, int population) {}
              """);

      final JavaFileObject addressSource =
          JavaFileObjects.forSourceString(
              "org.example.contact.Address",
              """
              package org.example.contact;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.example.geo.City;

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, City city) {}
              """);

      final JavaFileObject personSource =
          JavaFileObjects.forSourceString(
              "org.example.people.Person",
              """
              package org.example.people;

              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.example.contact.Address;

              @GenerateFocus(generateNavigators = true, maxNavigatorDepth = 3)
              public record Person(String name, Address home) {}
              """);

      Compilation compilation =
          javac()
              .withProcessors(new FocusProcessor())
              .compile(citySource, addressSource, personSource);

      // Must compile: Person -> Address (different package) -> City (yet another package)
      assertThat(compilation).succeeded();

      // PersonFocus should import AddressFocus
      assertGeneratedCodeContainsRaw(
          compilation,
          "org.example.people.PersonFocus",
          "import org.example.contact.AddressFocus;");
    }
  }

  /**
   * A record annotated in one module stays navigable from another module's {@code Focus}, because
   * {@code @GenerateFocus} is retained in the class file. A navigator into such a record composes
   * what that record's own module published, read from its generated {@code Focus} class, so it
   * agrees with the dependency however the dependency was built. Where it cannot be completed the
   * processor says why in a note against the field.
   */
  @Nested
  @DisplayName("Across a class-file boundary")
  class AcrossAClassFileBoundary {

    @TempDir Path tmp;

    private static final JavaFileObject ADDRESS =
        JavaFileObjects.forSourceString(
            "com.upstream.Address",
            """
            package com.upstream;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Address(String street, String city) {}
            """);

    private static final JavaFileObject COMPANY =
        JavaFileObjects.forSourceString(
            "com.downstream.Company",
            """
            package com.downstream;

            import com.upstream.Address;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Company(String name, Address headquarters) {}
            """);

    private static final JavaFileObject LEAF =
        JavaFileObjects.forSourceString(
            "com.up.Leaf",
            """
            package com.up;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Leaf(String value) {}
            """);

    private static final JavaFileObject NODE =
        JavaFileObjects.forSourceString(
            "com.up.Node",
            """
            package com.up;

            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Node(String name, Leaf leaf) {}
            """);

    private static final JavaFileObject ROOT =
        JavaFileObjects.forSourceString(
            "com.down.Root",
            """
            package com.down;

            import com.up.Node;
            import org.higherkindedj.optics.annotations.GenerateFocus;

            @GenerateFocus(generateNavigators = true)
            public record Root(String id, Node node) {}
            """);

    private static final JavaFileObject MONEY =
        JavaFileObjects.forSourceString(
            "com.a.Money",
            """
            package com.a;

            public record Money(long cents) {}
            """);

    /** Compiles one module, with or without the processor, against other modules' class files. */
    private static Compilation compile(
        boolean processor, List<Path> dependencies, JavaFileObject... sources) {
      return (processor ? javac().withProcessors(new FocusProcessor()) : javac().withProcessors())
          .withClasspath(classpathWith(dependencies.toArray(Path[]::new)))
          .compile(sources);
    }

    /** Compiles a module that must succeed and lays its class files out as a jar would be. */
    private Path module(
        String name, boolean processor, List<Path> dependencies, JavaFileObject... sources)
        throws IOException {
      Compilation compilation = compile(processor, dependencies, sources);
      assertThat(compilation).succeeded();
      return classDirectory(compilation, tmp.resolve(name));
    }

    private static String source(Compilation compilation, String name) throws IOException {
      return compilation.generatedSourceFile(name).orElseThrow().getCharContent(true).toString();
    }

    @Test
    @DisplayName("a dependency's annotated record is navigable, as a sibling source file is")
    void aDependencysRecordIsNavigable() throws IOException {
      Compilation downstream =
          compile(true, List.of(module("upstream", true, List.of(), ADDRESS)), COMPANY);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.CompanyFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static HeadquartersNavigator<Company> headquarters() {");
      assertGeneratedCodeContains(
          downstream, focus, "public static final class HeadquartersNavigator<S> {");
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> street() {");
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> city() {");
    }

    @Test
    @DisplayName("a navigator across the boundary is the one a single compilation generates")
    void aNavigatorAgreesWithOneCompilation() throws IOException {
      JavaFileObject node =
          JavaFileObjects.forSourceString(
              "com.up.Node",
              """
              package com.up;

              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.jspecify.annotations.Nullable;

              @GenerateFocus(generateNavigators = true)
              public record Node(
                  String name,
                  Leaf leaf,
                  List<String> tags,
                  Optional<Leaf> spare,
                  List<@Nullable String> maybes) {}
              """);
      Compilation across =
          compile(true, List.of(module("upstream", true, List.of(), LEAF, node)), ROOT);
      Compilation together = compile(true, List.of(), LEAF, node, ROOT);
      assertThat(across).succeeded();
      assertThat(together).succeeded();

      // The dependency's own nested navigator is composed by the name it published, every tier
      // reads the same from its companion as from the analysis a single compilation runs, and a
      // type-use annotation on an element survives the read.
      assertGeneratedCodeContains(
          across, "com.down.RootFocus", "public NodeFocus.LeafNavigator<S> leaf() {");
      Assertions.assertThat(source(across, "com.down.RootFocus"))
          .contains("TraversalPath<S, @Nullable String> maybes()")
          .isEqualTo(source(together, "com.down.RootFocus"));
    }

    @Test
    @DisplayName("a widened component of a dependency's record keeps its own path type")
    void aWidenedComponentKeepsItsPathType() throws IOException {
      JavaFileObject depot =
          JavaFileObjects.forSourceString(
              "com.upstream.Depot",
              """
              package com.upstream;

              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Depot(String code, List<String> tags, Optional<String> note) {}
              """);
      JavaFileObject region =
          JavaFileObjects.forSourceString(
              "com.downstream.Region",
              """
              package com.downstream;

              import com.upstream.Depot;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Region(Depot depot) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", true, List.of(), depot)), region);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.RegionFocus";
      assertGeneratedCodeContains(downstream, focus, "public TraversalPath<S, String> tags() {");
      assertGeneratedCodeContains(downstream, focus, "public AffinePath<S, String> note() {");
    }

    @Test
    @DisplayName("a Kind component composes the path type its own module's @TraverseField decided")
    void aKindComponentComposesThePublishedPathType() throws IOException {
      JavaFileObject crate =
          JavaFileObjects.forSourceString(
              "com.upstream.Crate",
              """
              package com.upstream;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.list.ListKind;
              import org.higherkindedj.optics.annotations.GenerateFocus;
              import org.higherkindedj.optics.annotations.KindSemantics;
              import org.higherkindedj.optics.annotations.TraverseField;

              @GenerateFocus(generateNavigators = true)
              public record Crate(
                  String label,
                  @TraverseField(
                      traverse = "org.higherkindedj.hkt.list.ListTraverse.INSTANCE",
                      semantics = KindSemantics.ZERO_OR_ONE)
                  Kind<ListKind.Witness, String> items) {}
              """);
      JavaFileObject yard =
          JavaFileObjects.forSourceString(
              "com.downstream.Yard",
              """
              package com.downstream;

              import com.upstream.Crate;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Yard(Crate crate) {}
              """);
      Compilation upstream = compile(true, List.of(), crate);
      assertThat(upstream).succeeded();
      // The semantics override makes the dependency's own method an AffinePath, where the
      // registry alone would say TraversalPath.
      assertGeneratedCodeContains(
          upstream, "com.upstream.CrateFocus", "public static AffinePath<Crate, String> items() {");

      Compilation downstream =
          compile(true, List.of(classDirectory(upstream, tmp.resolve("upstream"))), yard);
      // The consumer reads the path type from the method the dependency published rather than
      // analysing the component again.
      assertThat(downstream).succeeded();
      assertGeneratedCodeContains(
          downstream, "com.downstream.YardFocus", "public AffinePath<S, String> items() {");
    }

    @Test
    @DisplayName("a dependency's own settings decide what a consumer composes")
    void aDependencysSettingsReachItsConsumers() throws IOException {
      JavaFileObject node =
          JavaFileObjects.forSourceString(
              "com.up.Node",
              """
              package com.up;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, excludeFields = "leaf")
              public record Node(String name, Leaf leaf) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", true, List.of(), LEAF, node)), ROOT);
      assertThat(downstream).succeeded();

      // The dependency chose no navigator for 'leaf', so it published a plain path, and that is
      // what the consumer composes.
      assertGeneratedCodeContains(
          downstream, "com.down.RootFocus", "public FocusPath<S, Leaf> leaf() {");
      assertGeneratedCodeDoesNotContain(downstream, "com.down.RootFocus", "LeafNavigator");
    }

    @Test
    @DisplayName("a dependency that published a plain path is composed as one")
    void aPublishedPlainPathIsComposedAsOne() throws IOException {
      Compilation node =
          compile(true, List.of(module("leaf-without", false, List.of(), LEAF)), NODE);
      assertThat(node).succeeded();
      // Leaf had no companion when Node was built, so Node published a plain path for it.
      assertGeneratedCodeContains(
          node, "com.up.NodeFocus", "public static FocusPath<Node, Leaf> leaf() {");

      Compilation downstream =
          compile(
              true,
              List.of(
                  classDirectory(node, tmp.resolve("node")),
                  module("leaf-with", true, List.of(), LEAF)),
              ROOT);
      // Leaf is navigable from here, so working the shape out again would compose a
      // LeafNavigator that Node's companion does not have.
      assertThat(downstream).succeeded();
      assertGeneratedCodeContains(
          downstream, "com.down.RootFocus", "public FocusPath<S, Leaf> leaf() {");
      assertGeneratedCodeDoesNotContain(downstream, "com.down.RootFocus", "LeafNavigator");
    }

    @Test
    @DisplayName("a dependency that published a navigator is composed as one")
    void aPublishedNavigatorIsComposedAsOne() throws IOException {
      Compilation node = compile(true, List.of(module("leaf-with", true, List.of(), LEAF)), NODE);
      assertThat(node).succeeded();
      assertGeneratedCodeContains(
          node, "com.up.NodeFocus", "public static LeafNavigator<Node> leaf() {");

      Compilation downstream =
          compile(
              true,
              List.of(
                  classDirectory(node, tmp.resolve("node")),
                  module("leaf-without", false, List.of(), LEAF)),
              ROOT);
      // LeafFocus is not visible from here, so working the shape out again would compose a plain
      // path over a method that returns a navigator.
      assertThat(downstream).succeeded();
      assertGeneratedCodeContains(
          downstream, "com.down.RootFocus", "public NodeFocus.LeafNavigator<S> leaf() {");
    }

    @Test
    @DisplayName("a field naming a type this module cannot see is left out, with a note")
    void aFieldThisModuleCannotSeeIsLeftOut() throws IOException {
      JavaFileObject invoice =
          JavaFileObjects.forSourceString(
              "com.b.Invoice",
              """
              package com.b;

              import com.a.Money;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Invoice(String reference, Money total) {}
              """);
      JavaFileObject ledger =
          JavaFileObjects.forSourceString(
              "com.c.Ledger",
              """
              package com.c;

              import com.b.Invoice;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Ledger(String id, Invoice invoice) {}
              """);
      Path money = module("a", false, List.of(), MONEY);
      // Only Invoice's module is on the consumer's classpath: Money is its implementation detail.
      Compilation downstream =
          compile(true, List.of(module("b", true, List.of(money), invoice)), ledger);
      assertThat(downstream).succeeded();

      final String focus = "com.c.LedgerFocus";
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> reference() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "total()");
      assertThat(downstream)
          .hadNoteContaining(
              "Navigator for field 'invoice' has no 'total' method: com.b.InvoiceFocus.total()"
                  + " names com.a.Money, which is not on this module's compile classpath");
    }

    @Test
    @DisplayName("a companion older than its record leaves the new field out, with a note")
    void aStaleCompanionLeavesTheNewFieldOut() throws IOException {
      JavaFileObject before =
          JavaFileObjects.forSourceString(
              "com.up.Parcel",
              """
              package com.up;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Parcel(String label) {}
              """);
      JavaFileObject after =
          JavaFileObjects.forSourceString(
              "com.up.Parcel",
              """
              package com.up;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Parcel(String label, String weight) {}
              """);
      JavaFileObject depot =
          JavaFileObjects.forSourceString(
              "com.down.Depot",
              """
              package com.down;

              import com.up.Parcel;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Depot(Parcel parcel) {}
              """);
      Path published = module("published", true, List.of(), before);
      // Parcel changed and was recompiled without regenerating its companion; the newer record
      // comes first on the classpath.
      Path recompiled = module("recompiled", false, List.of(), after);
      Compilation downstream = compile(true, List.of(recompiled, published), depot);
      assertThat(downstream).succeeded();

      final String focus = "com.down.DepotFocus";
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> label() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "weight()");
      assertThat(downstream)
          .hadNoteContaining(
              "Navigator for field 'parcel' has no 'weight' method: com.up.ParcelFocus has no"
                  + " public static weight() returning a path or navigator over Parcel");
    }

    @Test
    @DisplayName("a companion of another shape is read for what it publishes, not assumed")
    void aCompanionOfAnotherShapeIsReadNotAssumed() throws IOException {
      JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.h.Box",
              """
              package com.h;

              public record Box(String value) {}
              """);
      JavaFileObject crate =
          JavaFileObjects.forSourceString(
              "com.h.Crate",
              """
              package com.h;

              public record Crate(String value) {}
              """);
      JavaFileObject thing =
          JavaFileObjects.forSourceString(
              "com.h.Thing",
              """
              package com.h;

              import com.a.Money;
              import java.util.List;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Thing(
                  String label,
                  List<?> anything,
                  int count,
                  List<String> names,
                  Map<String, String> pairs,
                  String elsewhere,
                  String remark,
                  String generic,
                  Money[] amounts,
                  List<? extends Money> upper,
                  List<? super Money> lower,
                  Box box,
                  Crate crate,
                  String shelf,
                  String inner,
                  String pair,
                  String raw,
                  String spare,
                  String hidden,
                  String tag,
                  String sketch,
                  String mark) {}
              """);
      // Stands in for a companion something other than hkj-processor wrote: each method meets one
      // way a published method can fail to be named, or composed, from generated code elsewhere.
      JavaFileObject thingFocus =
          JavaFileObjects.forSourceString(
              "com.h.ThingFocus",
              """
              package com.h;

              import com.a.Money;
              import java.util.List;
              import java.util.Map;
              import org.higherkindedj.optics.focus.AffinePath;
              import org.higherkindedj.optics.focus.FocusPath;

              public final class ThingFocus {
                private ThingFocus() {}

                public static FocusPath<Thing, Integer> label(String ignored) { return null; }
                public static FocusPath<Thing, String> label() { return null; }
                public static FocusPath<Thing, List<?>> anything() { return null; }

                public static int count() { return 0; }
                public static List<String> names() { return null; }
                public static Map<Thing, String> pairs() { return null; }
                public static FocusPath<Box, String> elsewhere() { return null; }
                public FocusPath<Thing, String> remark() { return null; }
                public static <T> FocusPath<Thing, T> generic() { return null; }

                public static FocusPath<Thing, Money[]> amounts() { return null; }
                public static FocusPath<Thing, List<? extends Money>> upper() { return null; }
                public static FocusPath<Thing, List<? super Money>> lower() { return null; }

                public static BoxNavigator<Thing> box() { return null; }
                public static CrateNavigator<Thing> crate() { return null; }
                public static ShelfNavigator<Thing> shelf() { return null; }
                public static InnerNavigator<Thing> inner() { return null; }
                public static PairNavigator<Thing, Thing> pair() { return null; }
                @SuppressWarnings("rawtypes")
                public static RawNavigator raw() { return null; }
                public static SpareNavigator<Box> spare() { return null; }
                public static HiddenNavigator<Thing> hidden() { return null; }
                public static TagNavigator<Thing> tag() { return null; }
                public static SketchNavigator<Thing> sketch() { return null; }
                public static MarkNavigator<Thing> mark() { return null; }

                public static final class BoxNavigator<S> {}

                public static final class CrateNavigator<S> {
                  public String get() { return null; }
                  public FocusPath<S, Integer> toPath(int ignored) { return null; }
                  public FocusPath<S, Money> toPath() { return null; }
                }

                public interface ShelfNavigator<S> {
                  FocusPath<S, String> toPath();
                }

                public final class InnerNavigator<S> {
                  public FocusPath<S, String> toPath() { return null; }
                }

                public static final class PairNavigator<S, T> {
                  public FocusPath<S, String> toPath() { return null; }
                }

                public static final class RawNavigator<S> {
                  public FocusPath<S, String> toPath() { return null; }
                }

                public static final class SpareNavigator<S> {
                  public SpareNavigator(FocusPath<S, String> path) {}
                  public FocusPath<S, String> toPath() { return null; }
                }

                public static final class HiddenNavigator<S> {
                  FocusPath<S, String> toPath() { return null; }
                }

                public static final class TagNavigator<S> {
                  private TagNavigator(FocusPath<S, String> path) {}
                  public TagNavigator(FocusPath<S, String> first, FocusPath<S, String> second) {}
                  public TagNavigator(AffinePath<S, String> path) {}
                  public FocusPath<S, String> toPath() { return null; }
                }

                public abstract static class SketchNavigator<S> {
                  public SketchNavigator(FocusPath<S, String> path) {}
                  public FocusPath<S, String> toPath() { return null; }
                }

                public static final class MarkNavigator<S> {
                  public MarkNavigator(FocusPath<S, Integer> path) {}
                  public FocusPath<S, String> toPath() { return null; }
                }
              }
              """);
      JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.c.Holder",
              """
              package com.c;

              import com.h.Thing;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Holder(Thing thing) {}
              """);
      Path money = module("a", false, List.of(), MONEY);
      Path things = module("h", false, List.of(money), box, crate, thing, thingFocus);
      Compilation downstream = compile(true, List.of(things), holder);
      assertThat(downstream).succeeded();

      final String focus = "com.c.HolderFocus";
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> label() {");
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, List<?>> anything() {");
      for (String unrecognised :
          List.of(
              "count",
              "names",
              "pairs",
              "elsewhere",
              "remark",
              "generic",
              "box",
              "shelf",
              "inner",
              "pair",
              "raw",
              "spare",
              "hidden",
              "tag",
              "sketch",
              "mark")) {
        assertGeneratedCodeDoesNotContain(downstream, focus, " " + unrecognised + "()");
        assertThat(downstream)
            .hadNoteContaining(
                "has no '"
                    + unrecognised
                    + "' method: com.h.ThingFocus has no public static "
                    + unrecognised
                    + "() returning a path or navigator over Thing");
      }
      for (String unresolvable : List.of("amounts", "upper", "lower", "crate")) {
        assertGeneratedCodeDoesNotContain(downstream, focus, " " + unresolvable + "()");
        assertThat(downstream)
            .hadNoteContaining(
                "has no '"
                    + unresolvable
                    + "' method: com.h.ThingFocus."
                    + unresolvable
                    + "() names com.a.Money");
      }
    }

    @Test
    @DisplayName("a dependency that never ran the processor keeps the plain path, with a note")
    void aDependencyWithoutTheProcessorKeepsThePlainPath() throws IOException {
      // The annotation survives compilation, so it is present on a record whose module never
      // generated a Focus class. Navigating into it would compose a class nobody wrote.
      Compilation downstream =
          compile(true, List.of(module("upstream", false, List.of(), ADDRESS)), COMPANY);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.CompanyFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Company, Address> headquarters() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "HeadquartersNavigator");
      assertThat(downstream)
          .hadNoteContaining(
              "Navigator for field 'headquarters' is not generated: com.upstream.Address carries"
                  + " @GenerateFocus, but no public com.upstream.AddressFocus is on this module's"
                  + " compile classpath");
    }

    @Test
    @DisplayName("a companion this module cannot access is not composed, with a note")
    void aCompanionThisModuleCannotAccessIsNotComposed() throws IOException {
      JavaFileObject spot =
          JavaFileObjects.forSourceString(
              "com.hid.Spot",
              """
              package com.hid;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Spot(String name) {}
              """);
      // Present, but package-private, so generated code in another package cannot name it.
      JavaFileObject spotFocus =
          JavaFileObjects.forSourceString(
              "com.hid.SpotFocus",
              """
              package com.hid;

              import org.higherkindedj.optics.focus.FocusPath;

              final class SpotFocus {
                private SpotFocus() {}

                static FocusPath<Spot, String> name() { return null; }
              }
              """);
      JavaFileObject atlas =
          JavaFileObjects.forSourceString(
              "com.down.Atlas",
              """
              package com.down;

              import com.hid.Spot;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Atlas(Spot spot) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("hid", false, List.of(), spot, spotFocus)), atlas);
      assertThat(downstream).succeeded();

      final String focus = "com.down.AtlasFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Atlas, Spot> spot() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "SpotNavigator");
      assertThat(downstream)
          .hadNoteContaining(
              "but no public com.hid.SpotFocus is on this module's compile classpath");
    }

    @Test
    @DisplayName("an unpublished record inside a container draws the same note")
    void anUnpublishedElementDrawsTheSameNote() throws IOException {
      JavaFileObject directory =
          JavaFileObjects.forSourceString(
              "com.downstream.Directory",
              """
              package com.downstream;

              import com.upstream.Address;
              import java.util.Map;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Directory(Map<String, Address> offices) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", false, List.of(), ADDRESS)), directory);
      assertThat(downstream).succeeded();

      assertGeneratedCodeDoesNotContain(
          downstream, "com.downstream.DirectoryFocus", "OfficesNavigator");
      assertThat(downstream)
          .hadNoteContaining(
              "Navigator for field 'offices' is not generated: com.upstream.Address carries"
                  + " @GenerateFocus, but no public com.upstream.AddressFocus");
    }

    @Test
    @DisplayName("a field the record itself excludes draws no note over an unpublished record")
    void anExcludedFieldDrawsNoNote() throws IOException {
      JavaFileObject company =
          JavaFileObjects.forSourceString(
              "com.downstream.Company",
              """
              package com.downstream;

              import com.upstream.Address;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true, excludeFields = "headquarters")
              public record Company(String name, Address headquarters) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", false, List.of(), ADDRESS)), company);
      assertThat(downstream).succeeded();

      // The record turned this navigator off itself, so there is nothing to explain.
      Assertions.assertThat(downstream.notes())
          .noneMatch(note -> note.getMessage(null).contains("is not generated"));
    }

    @Test
    @DisplayName("an unpublished generic record draws no note, since it would never be navigable")
    void anUnpublishedGenericRecordDrawsNoNote() throws IOException {
      JavaFileObject boxed =
          JavaFileObjects.forSourceString(
              "com.upstream.Boxed",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Boxed<T>(T value) {}
              """);
      JavaFileObject shelf =
          JavaFileObjects.forSourceString(
              "com.downstream.Shelf",
              """
              package com.downstream;

              import com.upstream.Boxed;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Shelf(Boxed<String> boxed) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", false, List.of(), boxed)), shelf);
      assertThat(downstream).succeeded();

      // A generic record gets no navigator even when its companion is published, so telling the
      // author to publish one would be advice that changes nothing.
      Assertions.assertThat(downstream.notes())
          .noneMatch(note -> note.getMessage(null).contains("is not generated"));
    }

    @Test
    @DisplayName("an unannotated record from a dependency is not navigable")
    void anUnannotatedRecordIsNotNavigable() throws IOException {
      JavaFileObject plain =
          JavaFileObjects.forSourceString(
              "com.upstream.Plain",
              """
              package com.upstream;

              public record Plain(String value) {}
              """);
      JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.downstream.Holder",
              """
              package com.downstream;

              import com.upstream.Plain;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Holder(Plain plain) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", true, List.of(), plain)), holder);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.HolderFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Holder, Plain> plain() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "PlainNavigator");
    }

    @Test
    @DisplayName("an annotated non-record read from a class file is not navigable")
    void anAnnotatedNonRecordIsNotNavigable() throws IOException {
      // The annotation is refused on anything but a record where it is declared, so a class file
      // carrying it elsewhere came from a module that never checked. A companion is present too,
      // so only the record check keeps the enum from being navigated into.
      JavaFileObject colour =
          JavaFileObjects.forSourceString(
              "com.upstream.Colour",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public enum Colour { RED, BLUE }
              """);
      JavaFileObject colourFocus =
          JavaFileObjects.forSourceString(
              "com.upstream.ColourFocus",
              """
              package com.upstream;

              public final class ColourFocus {
                private ColourFocus() {}
              }
              """);
      JavaFileObject paint =
          JavaFileObjects.forSourceString(
              "com.downstream.Paint",
              """
              package com.downstream;

              import com.upstream.Colour;
              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public record Paint(Colour colour) {}
              """);
      Compilation downstream =
          compile(true, List.of(module("upstream", false, List.of(), colour, colourFocus)), paint);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.PaintFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Paint, Colour> colour() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "ColourNavigator");
    }
  }
}

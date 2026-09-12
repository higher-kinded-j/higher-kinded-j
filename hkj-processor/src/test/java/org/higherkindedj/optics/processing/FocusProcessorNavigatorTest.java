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
import javax.tools.JavaFileObject;
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
   * {@code @GenerateFocus} and {@code @TraverseField} are retained in the class file. Navigability
   * is decided by asking the component's type for the annotation, so while it was discarded after
   * compilation a dependency's record fell back to a plain {@code FocusPath}, silently.
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

    private Compilation upstream(JavaFileObject... sources) {
      Compilation compilation = javac().withProcessors(new FocusProcessor()).compile(sources);
      assertThat(compilation).succeeded();
      return compilation;
    }

    private Compilation downstreamAgainst(Compilation upstream, JavaFileObject source)
        throws IOException {
      return javac()
          .withProcessors(new FocusProcessor())
          .withClasspath(classpathWith(classDirectory(upstream, tmp.resolve("upstream"))))
          .compile(source);
    }

    @Test
    @DisplayName("a dependency's annotated record is navigable, as a sibling source file is")
    void aDependencysRecordIsNavigable() throws IOException {
      Compilation downstream = downstreamAgainst(upstream(ADDRESS), COMPANY);
      assertThat(downstream).succeeded();

      final String focus = "com.downstream.CompanyFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static HeadquartersNavigator<Company> headquarters() {");
      assertGeneratedCodeContains(
          downstream, focus, "public static final class HeadquartersNavigator<S> {");
      // The navigator's members are read off the dependency's record, which a class file answers
      // as readily as a source file.
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> street() {");
      assertGeneratedCodeContains(downstream, focus, "public FocusPath<S, String> city() {");
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
      Compilation downstream = downstreamAgainst(upstream(depot), region);
      assertThat(downstream).succeeded();

      // A navigation method declares what the target's own Focus method returns (#719), and the
      // target's widening is read from its class file exactly as from its source.
      final String focus = "com.downstream.RegionFocus";
      assertGeneratedCodeContains(downstream, focus, "public TraversalPath<S, String> tags() {");
      assertGeneratedCodeContains(downstream, focus, "public AffinePath<S, String> note() {");
    }

    @Test
    @DisplayName("a dependency's @TraverseField still decides the path type it declares")
    void aTraverseFieldSurvivesTheBoundary() throws IOException {
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
      Compilation upstream = upstream(crate);
      // The semantics override makes the target's own method an AffinePath, where the registry
      // alone would have said TraversalPath.
      assertGeneratedCodeContains(
          upstream, "com.upstream.CrateFocus", "public static AffinePath<Crate, String> items() {");

      Compilation downstream = downstreamAgainst(upstream, yard);
      // Read without the annotation the navigator declared a TraversalPath over the AffinePath it
      // composes, which javac rejects inside the generated file rather than at any declaration.
      assertThat(downstream).succeeded();
      assertGeneratedCodeContains(
          downstream, "com.downstream.YardFocus", "public AffinePath<S, String> items() {");
    }

    @Test
    @DisplayName("a dependency that never ran the processor keeps the plain path")
    void aDependencyWithoutTheProcessorKeepsThePlainPath() throws IOException {
      // The annotation now survives compilation, so it is present on a record whose module never
      // generated a Focus class. Navigating into it would compose a class nobody wrote.
      Compilation withoutProcessor = javac().withProcessors().compile(ADDRESS);
      assertThat(withoutProcessor).succeeded();

      Compilation downstream = downstreamAgainst(withoutProcessor, COMPANY);
      assertThat(downstream).succeeded();
      final String focus = "com.downstream.CompanyFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Company, Address> headquarters() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "HeadquartersNavigator");
    }

    @Test
    @DisplayName("an unannotated record is not navigable, from a class file or from source")
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
      Compilation downstream = downstreamAgainst(upstream(plain), holder);
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
      // carrying it elsewhere came from a module that never checked. Compiled without the
      // processor for exactly that reason.
      JavaFileObject colour =
          JavaFileObjects.forSourceString(
              "com.upstream.Colour",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateFocus;

              @GenerateFocus(generateNavigators = true)
              public enum Colour { RED, BLUE }
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
      Compilation withoutProcessor = javac().withProcessors().compile(colour);
      assertThat(withoutProcessor).succeeded();

      Compilation downstream = downstreamAgainst(withoutProcessor, paint);
      assertThat(downstream).succeeded();
      final String focus = "com.downstream.PaintFocus";
      assertGeneratedCodeContains(
          downstream, focus, "public static FocusPath<Paint, Colour> colour() {");
      assertGeneratedCodeDoesNotContain(downstream, focus, "ColourNavigator");
    }
  }
}

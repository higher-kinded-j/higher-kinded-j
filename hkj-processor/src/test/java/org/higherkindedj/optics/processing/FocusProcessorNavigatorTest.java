// Copyright (c) 2025 - 2026 Magnus Smith
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

              @GenerateFocus(generateNavigators = true)
              public record Address(String street, Map<String, String> metadata) {}
              """);

      Compilation compilation =
          javac().withProcessors(new FocusProcessor()).compile(companySource, addressSource);

      assertThat(compilation).succeeded();

      // Inside HeadquartersNavigator, the metadata() method should return TraversalPath
      // with the inner value type (Map values are String), because Map is recognised as
      // ZERO_OR_MORE by the SPI and the navigator applies .each(opticExpr) to unwrap.
      final String expectedMetadataTraversal =
          """
          public TraversalPath<S, String> metadata() {
          """;

      assertGeneratedCodeContains(
          compilation, "com.example.CompanyFocus", expectedMetadataTraversal);
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

              @GenerateFocus(generateNavigators = true)
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
}

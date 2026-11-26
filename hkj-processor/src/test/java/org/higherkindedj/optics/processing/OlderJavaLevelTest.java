// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A compilation at a Java level older than the types it reads. Below Java 16 javac reads a record
 * from a class file as a plain class, and below Java 17 a sealed interface as an open one. The
 * mapping and merge processors, whose inputs must be records, refuse such a compilation outright;
 * the shape refusals of {@code @ImportOptics} and of a mapping say how javac reads the type. The
 * types they read come from class files compiled at Java 25, as from a jar.
 */
@DisplayName("A compilation at a Java level older than the types it reads")
class OlderJavaLevelTest {

  private static final String AT_RELEASE_25 =
      "at release 25, the release Higher-Kinded-J is built on, with javac's --release 25, Gradle's"
          + " options.release or Maven's maven.compiler.release set to 25.";

  private static final String SEALED_AT_JAVA_16 =
      "This compilation is at Java 16, where javac reads a sealed interface as an open one: if the"
          + " type is a sealed interface, compile "
          + AT_RELEASE_25;

  private static final List<JavaFileObject> UPSTREAM =
      List.of(
          source("com.external.Customer", "public record Customer(String name) {}"),
          source("com.external.CustomerDto", "public record CustomerDto(String name) {}"),
          source("com.external.Account", "public record Account(String id) {}"),
          source("com.external.Shape", "public sealed interface Shape permits Circle, Square {}"),
          source("com.external.Circle", "public record Circle(double r) implements Shape {}"),
          source("com.external.Square", "public record Square(double side) implements Shape {}"),
          source(
              "com.external.ShapeDto",
              "public sealed interface ShapeDto permits CircleDto, SquareDto {}"),
          source(
              "com.external.CircleDto", "public record CircleDto(double r) implements ShapeDto {}"),
          source(
              "com.external.SquareDto",
              "public record SquareDto(double side) implements ShapeDto {}"),
          // A sealed type for protobuf's Value oneof 'kind', with a record named after each member.
          source(
              "com.external.Kind",
              "public sealed interface Kind permits NullValue, NumberValue, StringValue, BoolValue,"
                  + " StructValue, ListValue {}"),
          source("com.external.NullValue", "public record NullValue() implements Kind {}"),
          source("com.external.NumberValue", "public record NumberValue() implements Kind {}"),
          source("com.external.StringValue", "public record StringValue() implements Kind {}"),
          source("com.external.BoolValue", "public record BoolValue() implements Kind {}"),
          source("com.external.StructValue", "public record StructValue() implements Kind {}"),
          source("com.external.ListValue", "public record ListValue() implements Kind {}"));

  private static final JavaFileObject CUSTOMER_MAPPING =
      JavaFileObjects.forSourceString(
          "com.example.CustomerMapping",
          """
          package com.example;

          import com.external.Customer;
          import com.external.CustomerDto;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
          """);

  private static final JavaFileObject DASHBOARD_ASSEMBLY =
      JavaFileObjects.forSourceString(
          "com.example.DashboardAssembly",
          """
          package com.example;

          import com.external.Account;
          import com.external.Customer;
          import com.external.CustomerDto;
          import org.higherkindedj.optics.annotations.GenerateMerge;

          @GenerateMerge
          public interface DashboardAssembly {
            CustomerDto assemble(Customer customer, Account account);
          }
          """);

  @TempDir static Path classes;

  private static List<File> classpath;

  @BeforeAll
  static void compileUpstream() throws IOException {
    Compilation upstream = javac().compile(UPSTREAM);
    assertThat(upstream).succeeded();
    classpath = classpathWith(classDirectory(upstream, classes));
  }

  private static JavaFileObject source(String name, String declaration) {
    String pkg = name.substring(0, name.lastIndexOf('.'));
    return JavaFileObjects.forSourceString(name, "package " + pkg + "; " + declaration);
  }

  private static JavaFileObject importer(String type) {
    return JavaFileObjects.forSourceString(
        "com.example.package-info",
        """
        @ImportOptics({%s.class})
        package com.example;

        import org.higherkindedj.optics.annotations.ImportOptics;
        """
            .formatted(type));
  }

  /** {@code sources} at {@code release}, reading the upstream types from their class files. */
  private static Compilation compiledAt(
      int release, Processor processor, JavaFileObject... sources) {
    // -Xlint:-options keeps javac's warnings about an old -source out of the output.
    return javac()
        .withProcessors(processor)
        .withClasspath(classpath)
        .withOptions(
            "-source",
            Integer.toString(release),
            "-target",
            Integer.toString(release),
            "-Xlint:-options")
        .compile(sources);
  }

  @Nested
  @DisplayName("The mapping and merge processors refuse a compilation below Java 16")
  class Refused {

    @Test
    @DisplayName("@GenerateMapping refuses the spec at Java 8, saying which release to compile at")
    void generateMappingRefusesTheSpecAtJava8() {
      Compilation compilation = compiledAt(8, new MappingProcessor(), CUSTOMER_MAPPING);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "@GenerateMapping: this compilation is at Java 8, where javac reads a record as a"
                  + " plain class. The processor reads records, and javac reads a compiled record"
                  + " as one only from Java 16. Compile "
                  + AT_RELEASE_25)
          .inFile(CUSTOMER_MAPPING)
          .onLine(9);
    }

    @Test
    @DisplayName("@GenerateMapping refuses the spec at Java 15")
    void generateMappingRefusesTheSpecAtJava15() {
      Compilation compilation = compiledAt(15, new MappingProcessor(), CUSTOMER_MAPPING);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining("@GenerateMapping: this compilation is at Java 15")
          .inFile(CUSTOMER_MAPPING)
          .onLine(9);
    }

    @Test
    @DisplayName("@GenerateMerge refuses the spec at Java 8")
    void generateMergeRefusesTheSpecAtJava8() {
      Compilation compilation = compiledAt(8, new MergeProcessor(), DASHBOARD_ASSEMBLY);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining("@GenerateMerge: this compilation is at Java 8")
          .inFile(DASHBOARD_ASSEMBLY)
          .onLine(9);
    }

    @Test
    @DisplayName("@GenerateMerge refuses the spec at Java 11")
    void generateMergeRefusesTheSpecAtJava11() {
      Compilation compilation = compiledAt(11, new MergeProcessor(), DASHBOARD_ASSEMBLY);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining("@GenerateMerge: this compilation is at Java 11")
          .inFile(DASHBOARD_ASSEMBLY)
          .onLine(9);
    }

    @Test
    @DisplayName("@GenerateMapping maps records at Java 16")
    void generateMappingMapsRecordsAtJava16() {
      Compilation compilation = compiledAt(16, new MappingProcessor(), CUSTOMER_MAPPING);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.CustomerMappingImpl");
    }
  }

  @Nested
  @DisplayName("A shape refusal below Java 17 says how javac reads the type")
  class Noted {

    @Test
    @DisplayName("@ImportOptics at Java 11 says javac reads a record as a plain class")
    void importOpticsNotesARecordAtJava11() {
      JavaFileObject importer = importer("com.external.Customer");

      Compilation compilation = compiledAt(11, new ImportOpticsProcessor(), importer);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "type 'com.external.Customer' is not a record, sealed interface, enum, or class with"
                  + " wither methods.")
          .inFile(importer)
          .onLine(2);
      assertThat(compilation)
          .hadErrorContaining(
              "Make the type one of those shapes, or define an OpticsSpec interface for it. This"
                  + " compilation is at Java 11, where javac reads a record as a plain class and a"
                  + " sealed interface as an open one: if the type is a record or a sealed"
                  + " interface, compile "
                  + AT_RELEASE_25);
    }

    @Test
    @DisplayName("@ImportOptics at Java 16 says javac reads a sealed interface as an open one")
    void importOpticsNotesASealedInterfaceAtJava16() {
      Compilation compilation =
          compiledAt(16, new ImportOpticsProcessor(), importer("com.external.Shape"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation).hadErrorContaining(SEALED_AT_JAVA_16);
    }

    @Test
    @DisplayName("@ImportOptics imports a sealed interface at Java 17")
    void importOpticsImportsASealedInterfaceAtJava17() {
      Compilation compilation =
          compiledAt(17, new ImportOpticsProcessor(), importer("com.external.Shape"));

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.ShapePrisms");
    }

    @Test
    @DisplayName("@GenerateMapping at Java 16 says javac reads a sealed pair as open interfaces")
    void generateMappingNotesASealedPairAtJava16() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ShapeMapping",
              """
              package com.example;

              import com.external.Shape;
              import com.external.ShapeDto;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ShapeMapping extends MappingSpec<Shape, ShapeDto> {}
              """);

      Compilation compilation = compiledAt(16, new MappingProcessor(), spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "the MappingSpec type arguments of 'ShapeMapping' must both be records, or both"
                  + " sealed interfaces.")
          .inFile(spec)
          .onLine(9);
      assertThat(compilation).hadErrorContaining(SEALED_AT_JAVA_16);
    }

    @Test
    @DisplayName("@GenerateMapping at Java 16 says javac reads a oneof's sealed type as open")
    void generateMappingNotesAOneofTypeAtJava16() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Doc",
              """
              package com.example;

              import com.external.Kind;
              import com.google.protobuf.Value;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              record Doc(Kind kind) {}

              @GenerateMapping
              interface DocMapping extends MappingSpec<Doc, Value> {}
              """);

      Compilation compilation = compiledAt(16, new MappingProcessor(), spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Doc.kind' is Kind, and names the oneof 'kind' of the protobuf-java"
                  + " message 'Value', which maps to a sealed interface or an Optional of one.");
      assertThat(compilation).hadErrorContaining(SEALED_AT_JAVA_16);
    }
  }
}

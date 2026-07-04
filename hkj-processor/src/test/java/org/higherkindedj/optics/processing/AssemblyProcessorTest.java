// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Structural and diagnostic tests for {@link AssemblyProcessor} (issue #586). hkj-core is on the
 * compile-testing classpath, so generated companions compile to a full {@code SUCCESS}.
 */
@DisplayName("Assembly Processor Tests")
class AssemblyProcessorTest {

  private static Compilation compile(String fqcn, String source) {
    return javac()
        .withProcessors(new AssemblyProcessor())
        .compile(JavaFileObjects.forSourceString(fqcn, source));
  }

  private static String generatedSource(Compilation compilation, String fqcn) throws IOException {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(fqcn);
    assertThat(file).as("Generated file should exist: %s", fqcn).isPresent();
    return file.get().getCharContent(true).toString();
  }

  private static final String USER =
      """
      package com.example;
      import org.higherkindedj.optics.annotations.GenerateAssembly;
      @GenerateAssembly
      public record User(String name, String email, int age) {}
      """;

  @Nested
  @DisplayName("Generated companion")
  class GeneratedCompanion {

    @Test
    @DisplayName("compiles to a full SUCCESS with the expected staged surface")
    void companionSurface() throws IOException {
      Compilation compilation = compile("com.example.User", USER);
      assertThat(compilation).succeeded();

      String source = generatedSource(compilation, "com.example.UserAssembly");
      assertThat(source)
          .contains("@Generated")
          .contains("public static Stage0 fields()")
          .contains("public Stage1 name(")
          .contains("public Stage2 email(")
          .contains("public Stage3 age(")
          .contains("err.at(\"name\")")
          .contains("err.at(\"email\")")
          .contains("err.at(\"age\")")
          .contains("public Validated<NonEmptyList<FieldError>, User> assemble()");
    }

    @Test
    @DisplayName("boxes primitive components and merges via a curried ap chain")
    void boxingAndMerge() throws IOException {
      Compilation compilation = compile("com.example.User", USER);
      String source = generatedSource(compilation, "com.example.UserAssembly");
      assertThat(source)
          .contains("Validated<NonEmptyList<FieldError>, Integer> value")
          .contains("a1 -> a2 -> a3 -> new User(a1, a2, a3)")
          .contains("NonEmptyList.semigroup()");
      // Accumulator on the function side: age (the newest field) is the ap receiver.
      assertThat(source).contains("this.age.ap(this.email.ap(this.name.map(");
    }

    @Test
    @DisplayName("a 13-component record generates with no arity ceiling")
    void thirteenComponents() throws IOException {
      StringBuilder comps = new StringBuilder();
      for (int i = 1; i <= 13; i++) {
        comps.append(i > 1 ? ", " : "").append("String f").append(i);
      }
      Compilation compilation =
          compile(
              "com.example.Wide13",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              @GenerateAssembly
              public record Wide13(%s) {}
              """
                  .formatted(comps));
      assertThat(compilation).succeeded();
      String source = generatedSource(compilation, "com.example.Wide13Assembly");
      assertThat(source).contains("public Stage13 f13(").contains("class Stage13");
    }

    @Test
    @DisplayName("a nested record's companion joins the enclosing simple names")
    void nestedRecordNaming() throws IOException {
      Compilation compilation =
          compile(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              public class Outer {
                @GenerateAssembly
                public record Inner(String a) {}
              }
              """);
      assertThat(compilation).succeeded();
      String source = generatedSource(compilation, "com.example.OuterInnerAssembly");
      assertThat(source).contains("class OuterInnerAssembly").contains("new Outer.Inner(a1)");
    }

    @Test
    @DisplayName("a component typed as another annotated record accepts the sub-companion result")
    void nestedRecordComponent() {
      Compilation compilation =
          javac()
              .withProcessors(new AssemblyProcessor())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Address",
                      """
                      package com.example;
                      import org.higherkindedj.optics.annotations.GenerateAssembly;
                      @GenerateAssembly
                      public record Address(String street, String zip) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Customer",
                      """
                      package com.example;
                      import org.higherkindedj.optics.annotations.GenerateAssembly;
                      @GenerateAssembly
                      public record Customer(String name, Address address) {}
                      """));
      assertThat(compilation).succeeded();
    }
  }

  @Nested
  @DisplayName("Diagnostics (what / why / fix)")
  class Diagnostics {

    @Test
    @DisplayName("a non-record target is rejected")
    void nonRecordRejected() {
      Compilation compilation =
          compile(
              "com.example.NotARecord",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              @GenerateAssembly
              public class NotARecord {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("not a record");
      assertThat(compilation).hadErrorContaining("Validated.fields()");
    }

    @Test
    @DisplayName("a generic record is rejected")
    void genericRecordRejected() {
      Compilation compilation =
          compile(
              "com.example.Box",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              @GenerateAssembly
              public record Box<T>(T value) {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("generic records are not supported");
    }

    @Test
    @DisplayName("a private nested record is rejected")
    void privateRecordRejected() {
      Compilation compilation =
          compile(
              "com.example.Outer",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              public class Outer {
                @GenerateAssembly
                private record Hidden(String a) {}
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is private");
      assertThat(compilation).hadErrorContaining("at least package-private");
    }

    @Test
    @DisplayName("a public record inside a private enclosing class is rejected")
    void privateEnclosingClassRejected() {
      Compilation compilation =
          compile(
              "com.example.Holder",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              public class Holder {
                private static class Wrapper {
                  @GenerateAssembly
                  public record Inner(String a) {}
                }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("enclosing type");
      assertThat(compilation).hadErrorContaining("at least package-private");
    }

    @Test
    @DisplayName("a companion-name collision is reported with the colliding name")
    void companionCollisionReported() {
      Compilation compilation =
          javac()
              .withProcessors(new AssemblyProcessor())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.SoloAssembly",
                      """
                      package com.example;
                      public class SoloAssembly {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Solo",
                      """
                      package com.example;
                      import org.higherkindedj.optics.annotations.GenerateAssembly;
                      @GenerateAssembly
                      public record Solo(String a) {}
                      """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("SoloAssembly already exists");
    }

    @Test
    @DisplayName("an empty record is rejected")
    void emptyRecordRejected() {
      Compilation compilation =
          compile(
              "com.example.Empty",
              """
              package com.example;
              import org.higherkindedj.optics.annotations.GenerateAssembly;
              @GenerateAssembly
              public record Empty() {}
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("no components");
    }
  }
}

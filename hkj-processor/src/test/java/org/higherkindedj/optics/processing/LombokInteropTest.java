// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins Lombok interop for bean-shaped wires: a {@code @Data} class has no source-level getters or
 * setters, so the bean analyser only sees them if Lombok's processor has materialised them in the
 * same javac run, and only if Lombok is listed first on the processor path (within a round, javac
 * invokes processors in listed order, and the bean analyser needs the accessors already
 * materialised). Both orders are pinned: the working one end to end, the broken one by its
 * diagnostic.
 */
@DisplayName("Lombok interop - @Data bean wires")
class LombokInteropTest {

  private static Processor lombok() {
    try {
      return (Processor)
          Class.forName("lombok.launch.AnnotationProcessorHider$AnnotationProcessor")
              .getDeclaredConstructor()
              .newInstance();
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Lombok processor not loadable", e);
    }
  }

  private static String generatedImpl(Compilation compilation) {
    return compilation.generatedSourceFiles().stream()
        .filter(f -> f.getName().contains("UserMappingImpl"))
        .findFirst()
        .map(
            f -> {
              try {
                return f.getCharContent(true).toString();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
            })
        .orElseThrow(() -> new AssertionError("UserMappingImpl not generated"));
  }

  /** The Impl must read and write through the Lombok-materialised accessors, not merely exist. */
  private static void assertUsesLombokAccessors(Compilation compilation) {
    assertThat(compilation).succeeded();
    Assertions.assertThat(generatedImpl(compilation))
        .contains("wire.setName(domain.name());")
        .contains("wire.setAge(domain.age());")
        .contains(".field(\"name\", hkj$ifPresent(wire.getName(), Validated::validNel))")
        .contains(".field(\"age\", Validated.validNel(wire.getAge()))");
  }

  @Test
  @DisplayName("a @Data wire maps when Lombok is listed first; the reverse order is diagnosed")
  void lombokDataBeanWireMaps() {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            public record User(String name, int age) {}
            """);
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.UserDto",
            """
            package com.example;

            @lombok.Data
            public class UserDto {
              private String name;
              private int age;
            }
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.UserMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface UserMapping extends MappingSpec<User, UserDto> {}
            """);
    // Order matters, and both directions are pinned. Lombok first: the accessors exist by the
    // time the bean analyser reads the wire, and the mapping generates against them.
    Compilation lombokFirst =
        javac().withProcessors(lombok(), new MappingProcessor()).compile(domain, wire, spec);
    assertUsesLombokAccessors(lombokFirst);

    // Mapping first: the analyser runs before Lombok has materialised the accessors, and the
    // wire is honestly diagnosed rather than half-mapped. This is why the documented processor
    // path lists Lombok before hkj-processor.
    Compilation mappingFirst =
        javac().withProcessors(new MappingProcessor(), lombok()).compile(domain, wire, spec);
    assertThat(mappingFirst).failed();
    assertThat(mappingFirst).hadErrorContaining("is not a usable bean-shaped wire");

    // And the compiled classes really carry the accessors: a full runtime round trip.
    var result = new RuntimeCompilationHelper.CompiledResult(lombokFirst);
    try {
      Object impl = result.instance("com.example.UserMappingImpl");
      Object user = result.newInstance("com.example.User", "Ada", 36);
      Object dto = RuntimeCompilationHelper.invoke(impl, "build", user);
      Assertions.assertThat(RuntimeCompilationHelper.invoke(dto, "getName")).isEqualTo("Ada");
      Assertions.assertThat(RuntimeCompilationHelper.invoke(dto, "getAge")).isEqualTo(36);
      @SuppressWarnings("unchecked")
      var back =
          (org.higherkindedj.hkt.validated.Validated<
                  org.higherkindedj.hkt.nonemptylist.NonEmptyList<
                      org.higherkindedj.hkt.validated.FieldError>,
                  Object>)
              RuntimeCompilationHelper.invoke(impl, "parse", dto);
      Assertions.assertThat(back.isValid()).isTrue();
      Assertions.assertThat(back.get()).isEqualTo(user);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }
}

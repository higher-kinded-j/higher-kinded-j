// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.effect.ComposeEffectsProcessor;
import org.higherkindedj.optics.processing.effect.EffectAlgebraProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every class file a processor writes carries {@code @Generated}, nested types included.
 *
 * <p>A nested type compiles to a class file of its own, and JaCoCo's generated-code filter reads
 * the annotation from the class file it is measuring, never from an enclosing one. A marker on the
 * outer type alone therefore leaves {@code XOps.Bound}, {@code XKind.Witness} and every other
 * nested companion measured as though someone had written them (#798). The generated source is no
 * witness to this, since {@code contains("@Generated")} is satisfied by the outer annotation alone,
 * so this test reads what the coverage tool reads: the {@code RuntimeInvisibleAnnotations} of each
 * generated class file, and asks for the marker on every one.
 *
 * <p>Each row compiles one generator's fixture and names the nested classes that fixture must
 * produce, so a fixture that stops exercising the shape fails rather than passing vacuously.
 */
@DisplayName("@Generated reaches every nested generated type")
class GeneratedMarkerReachesNestedTypesTest {

  /** The marker's descriptor, as a class file spells it. */
  private static final String MARKER = "Lorg/higherkindedj/optics/annotations/Generated;";

  private static JavaFileObject algebra(String name) {
    return JavaFileObjects.forSourceString(
        "test.pkg." + name,
        """
        package test.pkg;

        import java.util.function.Function;
        import org.higherkindedj.hkt.Unit;
        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface %1$s<A> permits %1$s.Only {
            <B> %1$s<B> mapK(Function<? super A, ? extends B> f);

            record Only<A>(String text, Function<Unit, A> k) implements %1$s<A> {
                @Override
                public <B> %1$s<B> mapK(Function<? super A, ? extends B> f) {
                    return new Only<>(text, k.andThen(f));
                }
            }
        }
        """
            .formatted(name));
  }

  private static final JavaFileObject COMPOSED =
      JavaFileObjects.forSourceString(
          "test.pkg.MyEffects",
          """
          package test.pkg;

          import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

          @ComposeEffects
          public record MyEffects(Class<ConsoleOp<?>> console, Class<DbOp<?>> db) {}
          """);

  private static final JavaFileObject USER =
      JavaFileObjects.forSourceString(
          "com.example.User",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateAssembly;

          @GenerateAssembly
          public record User(String name, String email, int age) {}
          """);

  private static final JavaFileObject FOO_ERROR_CONTEXT =
      JavaFileObjects.forSourceString(
          "com.example.FooErrorContext",
          """
          package com.example;

          public record FooErrorContext(String orderId, String traceId) {}
          """);

  private static final JavaFileObject FOO_ERROR =
      JavaFileObjects.forSourceString(
          "com.example.FooError",
          """
          package com.example;

          import org.higherkindedj.hkt.error.ErrorEnvelope;
          import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

          @GenerateErrorEnvelope
          public sealed interface FooError {
            ErrorEnvelope<FooErrorContext> envelope();

            record OutOfStock(String product, ErrorEnvelope<FooErrorContext> envelope)
                implements FooError {}
          }
          """);

  private static final JavaFileObject COMPANY =
      JavaFileObjects.forSourceString(
          "com.example.Company",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Company(String name, Address headquarters) {}
          """);

  private static final JavaFileObject ADDRESS =
      JavaFileObjects.forSourceString(
          "com.example.Address",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateFocus;

          @GenerateFocus(generateNavigators = true)
          public record Address(String street, String city) {}
          """);

  /** One row per generator that writes a nested type: its processors, fixture and nested output. */
  static Stream<Arguments> generators() {
    return Stream.of(
        Arguments.of(
            "@EffectAlgebra",
            List.of(new EffectAlgebraProcessor()),
            List.of(algebra("ConsoleOp")),
            Set.of(
                "ConsoleOpKind$Witness",
                "ConsoleOpKindHelper$ConsoleOpHolder",
                "ConsoleOpOps$Bound")),
        Arguments.of(
            "@ComposeEffects",
            List.of(new EffectAlgebraProcessor(), new ComposeEffectsProcessor()),
            List.of(algebra("ConsoleOp"), algebra("DbOp"), COMPOSED),
            Set.of("MyEffectsSupport$BoundSet")),
        Arguments.of(
            "@GenerateAssembly",
            List.of(new AssemblyProcessor()),
            List.of(USER),
            Set.of("UserAssembly$Stage0", "UserAssembly$Stage3")),
        Arguments.of(
            "@GenerateErrorEnvelope",
            List.of(new ErrorEnvelopeProcessor()),
            List.of(FOO_ERROR_CONTEXT, FOO_ERROR),
            Set.of("FooErrors$ContextBuilder")),
        Arguments.of(
            "@GenerateFocus navigators",
            List.of(new FocusProcessor()),
            List.of(COMPANY, ADDRESS),
            Set.of("CompanyFocus$HeadquartersNavigator")));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generators")
  @DisplayName("every generated class file carries the marker")
  void everyGeneratedClassFileCarriesTheMarker(
      String generator,
      List<Processor> processors,
      List<JavaFileObject> sources,
      Set<String> expectedNested) {
    Compilation compilation = javac().withProcessors(processors).compile(sources);
    assertThat(compilation).succeeded();

    Set<String> generatedTypes =
        compilation.generatedSourceFiles().stream()
            .map(GeneratedMarkerReachesNestedTypesTest::binaryName)
            .collect(Collectors.toSet());
    List<JavaFileObject> generatedClassFiles =
        compilation.generatedFiles().stream()
            .filter(file -> file.getKind() == JavaFileObject.Kind.CLASS)
            .filter(file -> generatedTypes.contains(outerName(binaryName(file))))
            .toList();

    List<String> nested =
        generatedClassFiles.stream()
            .map(GeneratedMarkerReachesNestedTypesTest::binaryName)
            .filter(name -> name.contains("$"))
            .map(name -> name.substring(name.lastIndexOf('/') + 1))
            .toList();
    assertThat(nested)
        .as("%s must write the nested types this row exists to check", generator)
        .containsAll(expectedNested);

    List<String> unmarked =
        generatedClassFiles.stream()
            .filter(file -> !carriesMarker(file))
            .map(GeneratedMarkerReachesNestedTypesTest::binaryName)
            .toList();
    assertThat(unmarked)
        .as("%s writes class files without @Generated, which a coverage tool measures", generator)
        .isEmpty();
  }

  /** {@code /CLASS_OUTPUT/test/pkg/XOps$Bound.class} as {@code test/pkg/XOps$Bound}. */
  private static String binaryName(JavaFileObject file) {
    String path = file.getName();
    String tail = path.substring(path.indexOf("_OUTPUT/") + "_OUTPUT/".length());
    return tail.substring(0, tail.lastIndexOf('.'));
  }

  /** The top-level type a binary name belongs to: {@code test/pkg/XOps} for its {@code Bound}. */
  private static String outerName(String binaryName) {
    int nested = binaryName.indexOf('$');
    return nested < 0 ? binaryName : binaryName.substring(0, nested);
  }

  /** Reads the class file the way JaCoCo does: the marker on this class file, or nothing. */
  private static boolean carriesMarker(JavaFileObject classFile) {
    try (var in = classFile.openInputStream()) {
      return ClassFile.of()
          .parse(in.readAllBytes())
          .findAttribute(Attributes.runtimeInvisibleAnnotations())
          .map(
              attribute ->
                  attribute.annotations().stream()
                      .anyMatch(annotation -> MARKER.equals(annotation.className().stringValue())))
          .orElse(false);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

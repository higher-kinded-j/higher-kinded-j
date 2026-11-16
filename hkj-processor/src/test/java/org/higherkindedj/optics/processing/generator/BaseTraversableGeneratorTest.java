// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.generator;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeVariableName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseTraversableGenerator}'s helpers, read from real record components. A type argument is
 * named as the argument itself, the bound of an {@code ? extends} wildcard, and {@code Object} for
 * any other wildcard, or where the component has no argument at that index. The constructor
 * arguments pass the new value for the changed component and read every other one from {@code
 * source}.
 *
 * <p>No built-in generator names a type argument in the {@code modifyF} body it writes, but the
 * base class offers these helpers to a generator that does, a plugin's included, so their contract
 * is held here directly.
 */
@DisplayName("BaseTraversableGenerator: helpers read from real record components")
class BaseTraversableGeneratorTest {

  /** The package-private annotation the fixture writes on its element type. */
  private static final AnnotationSpec TAG =
      AnnotationSpec.builder(ClassName.get("com.example", "Tag")).build();

  /** A generator whose helpers are all the test needs. */
  private static final BaseTraversableGenerator BASE =
      new BaseTraversableGenerator() {
        @Override
        public boolean supports(final TypeMirror type) {
          return false;
        }

        @Override
        public CodeBlock generateModifyF(
            final RecordComponentElement component,
            final ClassName recordClassName,
            final List<? extends RecordComponentElement> allComponents) {
          return CodeBlock.builder().build();
        }
      };

  @Test
  @DisplayName("getTypeArgumentName names each argument as the type it stands for")
  void namesEachArgumentAsTheTypeItStandsFor() {
    final var holder =
        JavaFileObjects.forSourceString(
            "com.example.Holder",
            """
            package com.example;

            import java.util.List;

            public record Holder<T>(
                List<String> plain,
                List<T> variable,
                List<? extends Number> bounded,
                List<?> anything,
                List<? super Integer> sink) {}
            """);

    assertThat(read(holder, component -> BASE.getTypeArgumentName(component, 0)))
        .containsOnly(
            entry("plain", ClassName.get(String.class)),
            entry("variable", TypeVariableName.get("T")),
            entry("bounded", ClassName.get(Number.class)),
            // Neither wildcard stands for a type of its own, so each is read as Object.
            entry("anything", ClassName.get(Object.class)),
            entry("sink", ClassName.get(Object.class)));
  }

  @Test
  @DisplayName(
      "getTypeArgumentName keeps an annotation only where the file being written can name it")
  void keepsAnAnnotationOnlyWhereTheFileCanNameIt() {
    final var tagged =
        JavaFileObjects.forSourceString(
            "com.example.Tagged",
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            import java.util.List;

            public record Tagged(List<@Tag String> tags) {}

            @Target(ElementType.TYPE_USE)
            @interface Tag {}
            """);

    // The annotation is package-private to com.example, so a file written there can name it and
    // one written anywhere else cannot.
    assertThat(read(tagged, component -> BASE.getTypeArgumentName(component, 0, "com.example")))
        .containsOnly(entry("tags", ClassName.get(String.class).annotated(TAG)));
    assertThat(read(tagged, component -> BASE.getTypeArgumentName(component, 0, "com.away")))
        .containsOnly(entry("tags", ClassName.get(String.class)));
  }

  @Test
  @DisplayName("a component with no argument at the index is read as Object")
  void aMissingArgumentIsReadAsObject() {
    final var shapes =
        JavaFileObjects.forSourceString(
            "com.example.Shapes",
            """
            package com.example;

            import java.util.List;

            @SuppressWarnings("rawtypes")
            public record Shapes(int primitive, List raw, List<String> one) {}
            """);

    assertThat(read(shapes, BASE::getGenericTypeName))
        .containsOnly(
            entry("primitive", ClassName.get(Object.class)),
            entry("raw", ClassName.get(Object.class)),
            entry("one", ClassName.get(String.class)));
    assertThat(read(shapes, component -> BASE.getTypeArgumentName(component, 1)))
        .containsOnly(
            entry("primitive", ClassName.get(Object.class)),
            entry("raw", ClassName.get(Object.class)),
            entry("one", ClassName.get(Object.class)));
  }

  @Test
  @DisplayName(
      "generateConstructorArgs passes the new value for the changed component and reads the rest")
  void constructorArgumentsReplaceOnlyTheChangedComponent() {
    final var order =
        JavaFileObjects.forSourceString(
            "com.example.Order",
            """
            package com.example;

            import java.util.List;

            public record Order(String id, List<String> lines, int count) {}
            """);
    final List<String> arguments = new ArrayList<>();

    eachTopLevelType(
        order,
        type ->
            arguments.add(
                BASE.generateConstructorArgs("lines", "newLines", type.getRecordComponents())));

    assertThat(arguments).containsExactly("source.id(), newLines, source.count()");
  }

  /** Compiles {@code source} and reads every record component in it through {@code reader}. */
  private static Map<String, TypeName> read(
      final JavaFileObject source, final Function<RecordComponentElement, TypeName> reader) {
    final Map<String, TypeName> names = new HashMap<>();
    eachTopLevelType(
        source,
        type ->
            type.getRecordComponents()
                .forEach(
                    component ->
                        names.put(component.getSimpleName().toString(), reader.apply(component))));
    return Map.copyOf(names);
  }

  /** Compiles {@code source} and hands every type declared at its top level to {@code visit}. */
  private static void eachTopLevelType(
      final JavaFileObject source, final Consumer<TypeElement> visit) {
    final class Reader extends AbstractProcessor {
      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(
          final Set<? extends TypeElement> annotations, final RoundEnvironment round) {
        ElementFilter.typesIn(round.getRootElements()).forEach(visit);
        return false;
      }
    }

    assertThat(javac().withProcessors(new Reader()).compile(source)).succeeded();
  }
}

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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.spi.GenerationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

  /** A record whose element type carries {@link #TAG}, package-private to {@code com.example}. */
  private static final JavaFileObject TAGGED =
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

  /** A record whose first type arguments are a type, a variable and each kind of wildcard. */
  private static final JavaFileObject HOLDER =
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

  @Test
  @DisplayName("getTypeArgumentName names each argument as the type it stands for")
  void namesEachArgumentAsTheTypeItStandsFor() {
    assertThat(read(HOLDER, (component, elements) -> inContext(component, 0, elements)))
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

    // The annotation is package-private to com.example, so a file written there can name it and
    // one written anywhere else cannot.
    assertThat(
            read(
                TAGGED,
                (component, elements) ->
                    BASE.getTypeArgumentName(
                        component, 0, new GenerationContext("com.example", elements))))
        .containsOnly(entry("tags", ClassName.get(String.class).annotated(TAG)));
    assertThat(
            read(
                TAGGED,
                (component, elements) ->
                    BASE.getTypeArgumentName(
                        component, 0, new GenerationContext("com.away", elements))))
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

    assertThat(read(shapes, (component, elements) -> inContext(component, 0, elements)))
        .containsOnly(
            entry("primitive", ClassName.get(Object.class)),
            entry("raw", ClassName.get(Object.class)),
            entry("one", ClassName.get(String.class)));
    assertThat(read(shapes, (component, elements) -> inContext(component, 1, elements)))
        .containsOnly(
            entry("primitive", ClassName.get(Object.class)),
            entry("raw", ClassName.get(Object.class)),
            entry("one", ClassName.get(Object.class)));
  }

  @Test
  @DisplayName("getTypeArgumentName hands the naming it is given each type an argument stands for")
  void namesWithTheNamingItIsGiven() {
    // The naming marks what it names, so each name it did not write is the Object fallback.
    assertThat(
            read(
                HOLDER,
                component ->
                    BASE.getTypeArgumentName(
                        component, 0, BaseTraversableGeneratorTest::taggedName)))
        .containsOnly(
            entry("plain", ClassName.get(String.class).annotated(TAG)),
            entry("variable", TypeVariableName.get("T").annotated(TAG)),
            entry("bounded", ClassName.get(Number.class).annotated(TAG)),
            entry("anything", ClassName.get(Object.class)),
            entry("sink", ClassName.get(Object.class)));
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

  @Test
  @DisplayName(
      "getTypeArgumentName through a context leaves off an annotation a javadoc tag alone"
          + " deprecates")
  @SuppressWarnings("removal") // exercises the deprecated method until its removal
  void throughAContextLeavesOffAJavadocDeprecation() {
    final var dated =
        JavaFileObjects.forSourceString(
            "com.example.Dated",
            """
            package com.example;

            import java.util.List;

            @SuppressWarnings("deprecation")
            public record Dated(List<@Old String> tags) {}
            """);
    final var old =
        JavaFileObjects.forSourceString(
            "com.example.Old",
            """
            package com.example;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            /**
             * A mark.
             *
             * @deprecated mark nothing
             */
            @Target(ElementType.TYPE_USE)
            public @interface Old {}
            """);
    final AnnotationSpec oldMark =
        AnnotationSpec.builder(ClassName.get("com.example", "Old")).build();

    // The form with no context reads @Deprecated alone, so it keeps the mark.
    assertThat(
            read(
                dated,
                (component, elements) -> BASE.getTypeArgumentName(component, 0, "com.example"),
                old))
        .containsOnly(entry("tags", ClassName.get(String.class).annotated(oldMark)));
    assertThat(
            read(
                dated,
                (component, elements) ->
                    BASE.getTypeArgumentName(
                        component, 0, new GenerationContext("com.example", elements)),
                old))
        .containsOnly(entry("tags", ClassName.get(String.class)));
  }

  @Nested
  @DisplayName("the forms without a context")
  @SuppressWarnings("removal") // exercises the deprecated methods until their removal
  class FormsWithoutAContext {

    @Test
    @DisplayName("name each argument as the context form names it, where nothing is tag-deprecated")
    void nameAsTheContextFormNames() {
      final Map<String, TypeName> expected =
          read(HOLDER, (component, elements) -> inContext(component, 0, elements));

      assertThat(read(HOLDER, BASE::getGenericTypeName)).isEqualTo(expected);
      assertThat(read(HOLDER, component -> BASE.getTypeArgumentName(component, 0)))
          .isEqualTo(expected);
      assertThat(read(HOLDER, component -> BASE.getTypeArgumentName(component, 0, "com.example")))
          .isEqualTo(expected);
    }

    @Test
    @DisplayName("the String form keeps an annotation only where its package can name it")
    void theStringFormReadsItsPackage() {
      assertThat(read(TAGGED, component -> BASE.getTypeArgumentName(component, 0, "com.example")))
          .containsOnly(entry("tags", ClassName.get(String.class).annotated(TAG)));
      assertThat(read(TAGGED, component -> BASE.getTypeArgumentName(component, 0, "com.away")))
          .containsOnly(entry("tags", ClassName.get(String.class)));
    }
  }

  /** The argument at {@code index}, named for a file written into the fixtures' package. */
  private static TypeName inContext(
      final RecordComponentElement component, final int index, final Elements elements) {
    return BASE.getTypeArgumentName(
        component, index, new GenerationContext("com.example", elements));
  }

  /** {@code type} as javapoet names it, marked with {@link #TAG}. */
  private static TypeName taggedName(final TypeMirror type) {
    return TypeName.get(type).annotated(TAG);
  }

  /** Compiles {@code source} and reads every record component in it through {@code reader}. */
  private static Map<String, TypeName> read(
      final JavaFileObject source, final Function<RecordComponentElement, TypeName> reader) {
    return read(source, (component, elements) -> reader.apply(component));
  }

  /**
   * Compiles {@code sources} and reads every record component in them through {@code reader}, which
   * is handed the round's element utilities too.
   */
  private static Map<String, TypeName> read(
      final JavaFileObject source,
      final BiFunction<RecordComponentElement, Elements, TypeName> reader,
      final JavaFileObject... others) {
    final Map<String, TypeName> names = new HashMap<>();
    eachTopLevelType(
        source,
        (type, elements) ->
            type.getRecordComponents()
                .forEach(
                    component ->
                        names.put(
                            component.getSimpleName().toString(),
                            reader.apply(component, elements))),
        others);
    return Map.copyOf(names);
  }

  /** Compiles {@code source} and hands every type declared at its top level to {@code visit}. */
  private static void eachTopLevelType(
      final JavaFileObject source, final Consumer<TypeElement> visit) {
    eachTopLevelType(source, (type, elements) -> visit.accept(type));
  }

  /**
   * Compiles {@code source} beside {@code others}, and hands every type {@code source} declares at
   * its top level to {@code visit}, with the round's element utilities.
   */
  private static void eachTopLevelType(
      final JavaFileObject source,
      final BiConsumer<TypeElement, Elements> visit,
      final JavaFileObject... others) {
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
        ElementFilter.typesIn(round.getRootElements()).stream()
            .filter(type -> !isOther(type))
            .forEach(type -> visit.accept(type, processingEnv.getElementUtils()));
        return false;
      }

      private boolean isOther(final TypeElement type) {
        for (JavaFileObject other : others) {
          if (other.isNameCompatible(type.getSimpleName().toString(), JavaFileObject.Kind.SOURCE)) {
            return true;
          }
        }
        return false;
      }
    }

    final JavaFileObject[] all = new JavaFileObject[others.length + 1];
    all[0] = source;
    System.arraycopy(others, 0, all, 1, others.length);
    assertThat(javac().withProcessors(new Reader()).compile(all)).succeeded();
  }
}

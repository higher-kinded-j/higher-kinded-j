// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.util;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.TypeVariableName;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.ElementFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ProcessorUtils - shared processor string helpers")
class ProcessorUtilsTest {

  @Test
  @DisplayName("capitalise upper-cases the first character and leaves the rest alone")
  void capitaliseUpperCasesFirstCharacter() {
    assertThat(ProcessorUtils.capitalise("name")).isEqualTo("Name");
    assertThat(ProcessorUtils.capitalise("alreadyUpper")).isEqualTo("AlreadyUpper");
    assertThat(ProcessorUtils.capitalise("x")).isEqualTo("X");
  }

  @Test
  @DisplayName("capitalise passes null and empty inputs through unchanged")
  void capitalisePassesDegenerateInputsThrough() {
    assertThat(ProcessorUtils.capitalise(null)).isNull();
    assertThat(ProcessorUtils.capitalise("")).isEmpty();
  }

  /**
   * Contract tests for {@link ProcessorUtils#simpleTypeName}, driven through a real compilation so
   * the type mirrors are javac's own. The subject declares one probe method per shape; each
   * method's single parameter type is rendered and captured under the method's name.
   */
  @Nested
  @DisplayName("simpleTypeName")
  class SimpleTypeName {

    /** Renders each probe method's parameter type, keyed by method name. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, String> rendered = new LinkedHashMap<>();
      private final Map<String, TypeKind> kinds = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement subject = processingEnv.getElementUtils().getTypeElement("com.test.Named");
        if (subject == null) {
          return false;
        }
        for (ExecutableElement method : ElementFilter.methodsIn(subject.getEnclosedElements())) {
          VariableElement parameter = method.getParameters().getFirst();
          rendered.put(
              method.getSimpleName().toString(), ProcessorUtils.simpleTypeName(parameter.asType()));
          kinds.put(method.getSimpleName().toString(), parameter.asType().getKind());
        }
        for (TypeKind kind :
            List.of(
                TypeKind.BOOLEAN,
                TypeKind.BYTE,
                TypeKind.SHORT,
                TypeKind.INT,
                TypeKind.LONG,
                TypeKind.CHAR,
                TypeKind.FLOAT,
                TypeKind.DOUBLE)) {
          rendered.put(
              "primitive:" + kind,
              ProcessorUtils.simpleTypeName(processingEnv.getTypeUtils().getPrimitiveType(kind)));
        }
        if (!subject.getTypeParameters().isEmpty()) {
          TypeVariable variable = (TypeVariable) subject.getTypeParameters().getFirst().asType();
          rendered.put(
              "intersectionBound", ProcessorUtils.simpleTypeName(variable.getUpperBound()));
        }
        return false;
      }
    }

    @Test
    @DisplayName("renders by element and arguments: annotations out, packages off, nesting kept")
    void rendersByElementAndArguments() {
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Named",
              """
              package com.test;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import java.util.List;
              import java.util.Map;
              import java.util.Set;

              @SuppressWarnings("rawtypes")
              abstract class Named<T extends CharSequence & Runnable> {
                  @Target(ElementType.TYPE_USE)
                  @interface Nully {}

                  @Target(ElementType.TYPE_USE)
                  @interface Ranged { int from(); }

                  static class Registry<X> {
                      static class Tag {}
                  }

                  class Holder {}

                  abstract void plain(List<String> p);
                  abstract void spacedArguments(Map<String, Integer> p);
                  abstract void annotatedTop(@Nully Set<?> p);
                  abstract void annotatedArgument(List<@Nully String> p);
                  abstract void wildcardExtends(List<? extends Number> p);
                  abstract void wildcardSuper(List<? super Number> p);
                  abstract void typeVariable(T p);
                  abstract void primitive(int p);
                  abstract void annotatedPrimitive(@Nully int p);
                  abstract void annotatedWithArguments(@Ranged(from = 0) int p);
                  abstract void primitiveArray(int[] p);
                  abstract void annotatedArrayComponent(@Nully String[] p);
                  abstract void staticNested(Registry.Tag p);
                  abstract void innerOfGeneric(Named<String>.Holder p);
                  abstract void rawSite(List p);
              }
              """);

      CapturingProcessor processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(subject);

      // The annotated rows pin #759; the rest characterise the rendering the old string form
      // already produced, so a change to any of them is a message change across the module.
      assertThat(processor.rendered)
          .containsEntry("plain", "List<String>")
          .containsEntry("spacedArguments", "Map<String, Integer>")
          .containsEntry("annotatedTop", "Set<?>")
          .containsEntry("annotatedArgument", "List<String>")
          .containsEntry("wildcardExtends", "List<? extends Number>")
          .containsEntry("wildcardSuper", "List<? super Number>")
          .containsEntry("typeVariable", "T")
          .containsEntry("primitive", "int")
          .containsEntry("annotatedPrimitive", "int")
          .containsEntry("annotatedWithArguments", "int")
          .containsEntry("primitiveArray", "int[]")
          .containsEntry("annotatedArrayComponent", "String[]")
          .containsEntry("staticNested", "Named.Registry.Tag")
          .containsEntry("innerOfGeneric", "Named<String>.Holder")
          .containsEntry("rawSite", "List")
          .containsEntry("primitive:BOOLEAN", "boolean")
          .containsEntry("primitive:BYTE", "byte")
          .containsEntry("primitive:SHORT", "short")
          .containsEntry("primitive:INT", "int")
          .containsEntry("primitive:LONG", "long")
          .containsEntry("primitive:CHAR", "char")
          .containsEntry("primitive:FLOAT", "float")
          .containsEntry("primitive:DOUBLE", "double")
          .containsEntry("intersectionBound", "Object&CharSequence&Runnable");
    }

    @Test
    @DisplayName("the annotation scan survives quotes, escapes and nesting a pattern cannot")
    void annotationScanSurvivesQuotesAndNesting() {
      assertThat(ProcessorUtils.stripAnnotations("List<String>")).isEqualTo("List<String>");
      assertThat(ProcessorUtils.stripAnnotations("@A X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations(".@a.b.A X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("@A() X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("@A(\")\") X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("@A(')') X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("@A(\"\\\"\") X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("@Outer(@Inner(1)) X")).isEqualTo("X");
      assertThat(ProcessorUtils.stripAnnotations("Pair<@A X, @B_1 Y>")).isEqualTo("Pair<X, Y>");
      assertThat(ProcessorUtils.stripAnnotations("@A")).isEmpty();
      assertThat(ProcessorUtils.stripAnnotations("@A(")).isEmpty();
      assertThat(ProcessorUtils.stripAnnotations("@A(1)X")).isEqualTo("X");
    }

    @Test
    @DisplayName("renders an unresolvable type by the name it was written under")
    void rendersAnUnresolvableType() {
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Named",
              """
              package com.test;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;

              abstract class Named {
                  @Target(ElementType.TYPE_USE)
                  @interface Ranged { int from(); }

                  @Target(ElementType.TYPE_USE)
                  @interface Labelled { String value(); }

                  abstract void unresolved(Missing p);
                  abstract void unresolvedNested(Missing.Inner p);
                  abstract void unresolvedArguments(Missing<String, Integer> p);
                  abstract void annotatedUnresolved(@Ranged(from = 0) Missing p);
                  abstract void quotedUnresolved(@Labelled(")") Missing p);
              }
              """);

      CapturingProcessor processor = new CapturingProcessor();
      // The round still runs on the failed compilation, and the mirrors really are ErrorTypes:
      // the string form keeps the qualifier on a nested name that an element walk would lose, so
      // these render through the fallback arm, annotations and their arguments stripped.
      Compilation compilation = javac().withProcessors(processor).compile(subject);

      assertThat(compilation).failed();
      assertThat(processor.kinds).containsEntry("unresolved", TypeKind.ERROR);
      assertThat(processor.rendered)
          .containsEntry("unresolved", "Missing")
          .containsEntry("unresolvedNested", "Missing.Inner")
          .containsEntry("unresolvedArguments", "Missing<String, Integer>")
          .containsEntry("annotatedUnresolved", "Missing")
          .containsEntry("quotedUnresolved", "Missing");
    }
  }

  /**
   * Contract tests for {@link ProcessorUtils#mentions}, driven through a real compilation so the
   * type mirrors are javac's own. The subject declares one method per shape; each method's single
   * parameter type is the type to search, and {@code T} the parameter to look for.
   */
  @Nested
  @DisplayName("mentions")
  class Mentions {

    /** Captures each probe method's parameter type, keyed by method name. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, Boolean> mentionsT = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement subject = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
        if (subject == null) {
          return false;
        }
        TypeParameterElement t = subject.getTypeParameters().getFirst();
        for (ExecutableElement method : ElementFilter.methodsIn(subject.getEnclosedElements())) {
          TypeMirror searched = method.getParameters().getFirst().asType();
          mentionsT.put(method.getSimpleName().toString(), ProcessorUtils.mentions(searched, t));
        }
        return false;
      }
    }

    /** Compiles the subject once and hands back the processor holding the answers. */
    private CapturingProcessor probe() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              public class Outer<X> {
                  public class Inner {}
              }
              """);
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              @SuppressWarnings("rawtypes")
              public interface Subject<T, U> {
                  void variable(T p);
                  void otherVariable(U p);
                  void argument(List<T> p);
                  void nestedArgument(List<List<T>> p);
                  void arrayComponent(T[] p);
                  void extendsWildcard(List<? extends T> p);
                  void superWildcard(List<? super T> p);
                  void unboundedWildcard(List<?> p);
                  void enclosingType(Outer<T>.Inner p);
                  void unrelated(String p);
                  void primitive(int p);
                  void rawDeclared(List p);
              }
              """);
      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(outer, subject);
      return processor;
    }

    @Test
    @DisplayName("finds the parameter wherever it is written")
    void findsTheParameterWhereverItIsWritten() {
      assertThat(probe().mentionsT)
          .containsEntry("variable", true)
          .containsEntry("argument", true)
          .containsEntry("nestedArgument", true)
          .containsEntry("arrayComponent", true)
          .containsEntry("extendsWildcard", true)
          .containsEntry("superWildcard", true)
          .containsEntry("enclosingType", true);
    }

    @Test
    @DisplayName("answers false for a type that does not name it")
    void answersFalseForATypeThatDoesNotNameIt() {
      assertThat(probe().mentionsT)
          .containsEntry("otherVariable", false)
          .containsEntry("unboundedWildcard", false)
          .containsEntry("unrelated", false)
          .containsEntry("primitive", false);
    }

    @Test
    @DisplayName("searches every arm of an intersection bound")
    void searchesEveryArmOfAnIntersectionBound() {
      // An intersection reaches mentions through a variable's upper bound. javac's intersection
      // implements DeclaredType, so the wrong arm order would answer false here.
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              public interface Subject<T, V extends Runnable & List<T>> {}
              """);

      var processor =
          new AbstractProcessor() {
            Boolean bounded;
            Boolean unbounded;

            @Override
            public Set<String> getSupportedAnnotationTypes() {
              return Set.of("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
              return SourceVersion.latestSupported();
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
              TypeElement type = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
              if (type == null) {
                return false;
              }
              Element t = type.getTypeParameters().getFirst();
              Element u = type.getTypeParameters().getLast();
              TypeMirror intersection =
                  ((javax.lang.model.type.TypeVariable) u.asType()).getUpperBound();
              bounded = ProcessorUtils.mentions(intersection, t);
              unbounded = ProcessorUtils.mentions(intersection, u);
              return false;
            }
          };
      javac().withProcessors(processor).compile(subject);

      assertThat(processor.bounded).isTrue();
      assertThat(processor.unbounded).isFalse();
    }
  }

  /** Contract tests for {@link ProcessorUtils#sumTypeAsNamedBy}, against javac's own mirrors. */
  @Nested
  @DisplayName("sumTypeAsNamedBy")
  class SumTypeAsNamedBy {

    /** Captures the answer for each permitted subtype, keyed by its simple name. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, String> named = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        var elements = processingEnv.getElementUtils();
        TypeElement shape = elements.getTypeElement("com.test.Shape");
        if (shape == null) {
          return false;
        }
        for (String subtypeName : List.of("com.test.Circle", "com.test.Tagged", "com.test.Loose")) {
          TypeElement subtype = elements.getTypeElement(subtypeName);
          named.put(
              subtype.getSimpleName().toString(),
              ProcessorUtils.sumTypeAsNamedBy(shape, subtype).toString());
        }
        return false;
      }
    }

    @Test
    @DisplayName("answers with the sum type as the subtype's own clause names it")
    void answersWithTheClausesInstantiation() {
      var sources =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;
              public sealed interface Shape<T> permits Circle, Tagged {}
              """);
      var circle =
          JavaFileObjects.forSourceString(
              "com.test.Circle",
              """
              package com.test;
              // Serializable first, so the scan passes an interface that is not the sum type.
              public record Circle<T>(T tag) implements java.io.Serializable, Shape<T> {}
              """);
      var tagged =
          JavaFileObjects.forSourceString(
              "com.test.Tagged",
              """
              package com.test;
              public record Tagged(String label) implements Shape<String> {}
              """);
      // Not permitted by Shape at all: the clause it would be found in does not name it, which is
      // the shape a subtype whose clause fails to resolve presents.
      var loose =
          JavaFileObjects.forSourceString(
              "com.test.Loose",
              """
              package com.test;
              public record Loose(String v) {}
              """);

      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(sources, circle, tagged, loose);

      assertThat(processor.named)
          .containsEntry("Circle", "com.test.Shape<T>")
          .containsEntry("Tagged", "com.test.Shape<java.lang.String>")
          // Nothing to read, so the sum type answers for itself rather than null.
          .containsEntry("Loose", "com.test.Shape<T>");
    }
  }

  /**
   * Contract tests for {@link ProcessorUtils#carriesInstantiation}, against javac's own mirrors.
   *
   * <p>The subject declares one field per shape; each field's type is the type to ask about. The
   * member-of-a-generic-outer case is the one worth having: {@code Holder} carries no arguments of
   * its own, so a reader asking only for those calls it uninstantiated and hands its members back
   * speaking the outer's variables.
   */
  @Nested
  @DisplayName("carriesInstantiation")
  class CarriesInstantiation {

    /** Captures the answer for each probe field, keyed by field name. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, Boolean> carries = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement subject = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
        if (subject == null) {
          return false;
        }
        for (VariableElement field : ElementFilter.fieldsIn(subject.getEnclosedElements())) {
          carries.put(
              field.getSimpleName().toString(),
              ProcessorUtils.carriesInstantiation((DeclaredType) field.asType()));
        }
        return false;
      }
    }

    @Test
    @DisplayName("reads the whole enclosing chain, not the type's own arguments alone")
    void readsTheEnclosingChain() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              public class Outer<X> {
                public class Holder {}
                public static class Nested {}
              }
              """);
      var plain =
          JavaFileObjects.forSourceString(
              "com.test.Plain",
              """
              package com.test;
              public class Plain {}
              """);
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              @SuppressWarnings({"rawtypes", "unused"})
              public class Subject {
                List<String> parameterised;
                List raw;
                Plain nonGeneric;
                Outer<List<String>>.Holder memberOfGenericOuter;
                Outer.Nested staticallyNested;
              }
              """);

      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(outer, plain, subject);

      assertThat(processor.carries)
          .containsEntry("parameterised", true)
          // Raw and non-generic both have nothing to substitute, and asMemberOf would erase the
          // first rather than leave it alone.
          .containsEntry("raw", false)
          .containsEntry("nonGeneric", false)
          // Holder declares no parameters; the instantiation is entirely its outer's.
          .containsEntry("memberOfGenericOuter", true)
          // A static nested class has no enclosing instance type, so the outer cannot reach it.
          .containsEntry("staticallyNested", false);
    }
  }

  /**
   * Contract tests for {@link ProcessorUtils#typeNameOf} and {@link ProcessorUtils#typeVariableOf},
   * driven through a real compilation so the mirrors carry the annotations javac attached. The
   * subject declares one field per shape; each field's type is the type to name.
   */
  @Nested
  @DisplayName("typeNameOf and typeVariableOf")
  class TypeNames {

    /** Renders each probe field's type, keyed by field name, plus the subject's type parameters. */
    private static final class CapturingProcessor extends AbstractProcessor {
      private final Map<String, String> names = new LinkedHashMap<>();
      private final Map<String, String> variables = new LinkedHashMap<>();

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        TypeElement subject = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
        if (subject == null) {
          return false;
        }
        for (VariableElement field : ElementFilter.fieldsIn(subject.getEnclosedElements())) {
          names.put(
              field.getSimpleName().toString(),
              ProcessorUtils.typeNameOf(field.asType()).toString());
        }
        for (TypeParameterElement parameter : subject.getTypeParameters()) {
          variables.put(parameter.getSimpleName().toString(), render(parameter));
        }
        return false;
      }

      /** The declaration as the generated file would write it: annotations, name, then bounds. */
      private static String render(TypeParameterElement parameter) {
        TypeVariableName variable = ProcessorUtils.typeVariableOf(parameter);
        StringBuilder out = new StringBuilder();
        variable.annotations().forEach(annotation -> out.append(annotation).append(" "));
        out.append(variable.name());
        variable.bounds().forEach(bound -> out.append(" extends ").append(bound));
        return out.toString();
      }
    }

    /** Compiles the subject once and hands back the processor holding the rendered names. */
    private CapturingProcessor probe() {
      var outer =
          JavaFileObjects.forSourceString(
              "com.test.Outer",
              """
              package com.test;
              public class Outer<X> {
                  public class Inner {}
                  public class Pair<Y> {}
                  public static class Nested {}
              }
              """);
      var plain =
          JavaFileObjects.forSourceString(
              "com.test.Plain",
              """
              package com.test;
              public class Plain {
                  public class Member {}
                  public class Held<Y> {}
              }
              """);
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              import java.util.Map;
              import org.jspecify.annotations.Nullable;
              @SuppressWarnings("unused")
              public class Subject<T extends @Nullable Object, U extends @Nullable Number, V> {
                  String plain;
                  @Nullable String annotated;
                  List<@Nullable String> annotatedArgument;
                  @Nullable List<String> annotatedWhole;
                  Map<@Nullable String, List<@Nullable Integer>> annotatedDeeply;
                  String @Nullable [] annotatedArray;
                  @Nullable String[] annotatedComponent;
                  int @Nullable [] annotatedPrimitiveArray;
                  int primitive;
                  List<? extends @Nullable Number> extendsWildcard;
                  List<? super @Nullable String> superWildcard;
                  List<?> unboundedWildcard;
                  @Nullable T annotatedVariable;
                  T plainVariable;
                  Outer<@Nullable String>.Inner memberOfAnnotatedOuter;
                  Outer<@Nullable String>.Pair<Integer> parameterisedMemberOfAnnotatedOuter;
                  Outer.Nested staticallyNested;
                  @Nullable Plain.Member annotatedEnclosing;
                  @Nullable Plain.Held<String> annotatedEnclosingOfGeneric;
                  Plain.Member plainEnclosing;
              }
              """);
      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(outer, plain, subject);
      return processor;
    }

    @Test
    @DisplayName("keeps an annotation written on the type itself, and adds none where none was")
    void keepsAnAnnotationWrittenOnTheTypeItself() {
      assertThat(probe().names)
          .containsEntry("plain", "java.lang.String")
          .containsEntry("annotated", "java.lang. @org.jspecify.annotations.Nullable String")
          .containsEntry("primitive", "int")
          .containsEntry("staticallyNested", "com.test.Outer.Nested");
    }

    @Test
    @DisplayName("keeps an annotation on a type argument at any depth")
    void keepsAnAnnotationOnATypeArgument() {
      assertThat(probe().names)
          .containsEntry(
              "annotatedArgument",
              "java.util.List<java.lang. @org.jspecify.annotations.Nullable String>")
          .containsEntry(
              "annotatedWhole",
              "java.util. @org.jspecify.annotations.Nullable List<java.lang.String>")
          .containsEntry(
              "annotatedDeeply",
              "java.util.Map<java.lang. @org.jspecify.annotations.Nullable String,"
                  + " java.util.List<java.lang. @org.jspecify.annotations.Nullable Integer>>");
    }

    @Test
    @DisplayName("keeps the array and its component apart")
    void keepsTheArrayAndItsComponentApart() {
      // `String @Nullable []` is a nullable array of non-null elements; `@Nullable String[]` is a
      // non-null array of nullable ones. Collapsing them would invert one of the two.
      assertThat(probe().names)
          .containsEntry("annotatedArray", "java.lang.String @org.jspecify.annotations.Nullable []")
          .containsEntry(
              "annotatedComponent", "java.lang. @org.jspecify.annotations.Nullable String[]")
          .containsEntry("annotatedPrimitiveArray", "int @org.jspecify.annotations.Nullable []");
    }

    @Test
    @DisplayName("keeps an annotation on either wildcard bound, and leaves an unbounded one alone")
    void keepsAnAnnotationOnAWildcardBound() {
      assertThat(probe().names)
          .containsEntry(
              "extendsWildcard",
              "java.util.List<? extends java.lang. @org.jspecify.annotations.Nullable Number>")
          .containsEntry(
              "superWildcard",
              "java.util.List<? super java.lang. @org.jspecify.annotations.Nullable String>")
          .containsEntry("unboundedWildcard", "java.util.List<?>");
    }

    @Test
    @DisplayName("keeps an annotation on a type variable's use")
    void keepsAnAnnotationOnATypeVariableUse() {
      assertThat(probe().names)
          .containsEntry("annotatedVariable", "@org.jspecify.annotations.Nullable T")
          .containsEntry("plainVariable", "T");
    }

    @Test
    @DisplayName("keeps an annotation on an enclosing type's argument")
    void keepsAnAnnotationOnAnEnclosingTypesArgument() {
      assertThat(probe().names)
          .containsEntry(
              "memberOfAnnotatedOuter",
              "com.test.Outer<java.lang. @org.jspecify.annotations.Nullable String>.Inner")
          .containsEntry(
              "parameterisedMemberOfAnnotatedOuter",
              "com.test.Outer<java.lang. @org.jspecify.annotations.Nullable String>"
                  + ".Pair<java.lang.Integer>");
    }

    @Test
    @DisplayName("keeps an annotation on the enclosing type itself")
    void keepsAnAnnotationOnTheEnclosingTypeItself() {
      // `@Nullable Plain.Member` annotates Plain, not Member: javac hangs it on the enclosing
      // type, and naming the nesting from the element alone carries none of it.
      assertThat(probe().names)
          .containsEntry(
              "annotatedEnclosing", "com.test. @org.jspecify.annotations.Nullable Plain. Member")
          .containsEntry(
              "annotatedEnclosingOfGeneric",
              "com.test. @org.jspecify.annotations.Nullable Plain. Held<java.lang.String>")
          // An unannotated enclosing rebuilds to exactly the name it had.
          .containsEntry("plainEnclosing", "com.test.Plain.Member");
    }

    @Test
    @DisplayName("keeps an annotated Object bound that javapoet would otherwise strip")
    void keepsAnAnnotatedObjectBound() {
      // `<T extends @Nullable Object>` is the declaration that admits a nullable instantiation.
      // Stripping the bound narrows the generated type below the type it wraps.
      assertThat(probe().variables)
          .containsEntry("T", "T extends java.lang. @org.jspecify.annotations.Nullable Object")
          .containsEntry("U", "U extends java.lang. @org.jspecify.annotations.Nullable Number")
          // A bare Object bound is still stripped: it is the implicit one, and writing it back
          // would be noise.
          .containsEntry("V", "V");
    }

    @Test
    @DisplayName("leaves an annotation written on the type parameter itself behind")
    void leavesAnAnnotationOnTheTypeParameterItselfBehind() {
      // One TypeVariableName both declares a parameter and is written wherever that parameter is
      // named, and generators reuse the same one for both. `Box<@Marked T>` is rejected outright
      // - "annotation @Marked not applicable in this type context" - so carrying a declaration
      // annotation would emit source the consuming build cannot compile. The bound, which is
      // where JSpecify states nullability, is copied.
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.lang.annotation.ElementType;
              import java.lang.annotation.Target;
              import org.jspecify.annotations.Nullable;
              public class Subject<@Subject.Marked T extends @Nullable Object> {
                  @Target(ElementType.TYPE_PARAMETER)
                  public @interface Marked {}
              }
              """);
      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(subject);

      assertThat(processor.variables)
          .containsEntry("T", "T extends java.lang. @org.jspecify.annotations.Nullable Object");
    }

    @Test
    @DisplayName("leaves a type javapoet has no name for to javapoet")
    void leavesATypeJavapoetHasNoNameForToJavapoet() {
      // An intersection reaches here through a variable's upper bound. It implements DeclaredType,
      // so dispatching on the interface rather than the kind would ask one for a class element it
      // does not have instead of leaving javapoet to refuse it.
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              import java.util.List;
              public interface Subject<V extends Runnable & List<String>> {}
              """);

      var processor =
          new AbstractProcessor() {
            Throwable thrown;

            @Override
            public Set<String> getSupportedAnnotationTypes() {
              return Set.of("*");
            }

            @Override
            public SourceVersion getSupportedSourceVersion() {
              return SourceVersion.latestSupported();
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
              TypeElement type = processingEnv.getElementUtils().getTypeElement("com.test.Subject");
              if (type == null) {
                return false;
              }
              TypeMirror intersection =
                  ((javax.lang.model.type.TypeVariable)
                          type.getTypeParameters().getFirst().asType())
                      .getUpperBound();
              thrown = catchThrowable(() -> ProcessorUtils.typeNameOf(intersection));
              return false;
            }
          };
      javac().withProcessors(processor).compile(subject);

      assertThat(processor.thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("names a type javac could not resolve the way a resolved one is named")
    void namesAnUnresolvedTypeTheWayAResolvedOneIsNamed() {
      // A processor runs before every type is resolvable, so an ERROR type reaches the walk. It
      // is a declared type that javac could not find, and naming it as one is what lets a
      // generator emit the name the author wrote and let the next round settle it.
      var subject =
          JavaFileObjects.forSourceString(
              "com.test.Subject",
              """
              package com.test;
              @SuppressWarnings("unused")
              public class Subject {
                  Missing unresolved;
              }
              """);

      var processor = new CapturingProcessor();
      javac().withProcessors(processor).compile(subject);

      assertThat(processor.names).containsEntry("unresolved", "Missing");
    }
  }
}

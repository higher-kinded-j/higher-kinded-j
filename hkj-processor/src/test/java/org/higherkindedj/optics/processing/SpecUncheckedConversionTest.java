// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * A spec lens whose generated read or rebuild would need an unchecked conversion or call is refused
 * at the spec method, where its author can see it: the warning would otherwise land in the
 * generated file, which no suppression its author writes reaches.
 *
 * <p>The external classes are compiled first and read from class files, as a dependency's would be,
 * so that their own raw types warn nowhere. Every refused spec compiles in one run and every
 * accepted one in another, under the lint an unchecked conversion trips as an error. The accepted
 * run includes each refusal's fix followed.
 */
@DisplayName("Spec lenses and unchecked conversions")
class SpecUncheckedConversionTest {

  private static final List<String> LINT = List.of("-Xlint:unchecked,rawtypes,static", "-Werror");

  private static JavaFileObject external(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        "com.external." + simpleName,
        """
        package com.external;

        import java.util.ArrayList;
        import java.util.List;

        """
            + body);
  }

  private static JavaFileObject spec(String simpleName, String source, String body) {
    return JavaFileObjects.forSourceString(
        "com.myapp." + simpleName,
        """
        package com.myapp;

        import com.external.*;
        import java.util.List;
        import org.higherkindedj.optics.Lens;
        import org.higherkindedj.optics.annotations.*;

        @ImportOptics
        public interface %s extends OpticsSpec<%s> {
        %s
        }
        """
            .formatted(simpleName, source, body));
  }

  /** Classes that declare raw accessors, builders and supertypes. */
  private static final List<JavaFileObject> EXTERNAL =
      List.of(
          external(
              "Shelf",
              """
              public final class Shelf {
                  private final List items;
                  private final String name;
                  public Shelf(List<String> contents, String name) { this.items = contents; this.name = name; }
                  public List items() { return items; }
                  public List<String> typed() { return List.of(); }
                  public String name() { return name; }
                  public Legacy legacy() { return new Legacy(); }
                  public List[] rows() { return new List[0]; }
                  public Shelf withItems(List items) { return this; }
                  public Shelf withTyped(List<String> typed) { return this; }
                  public Shelf withRows(List<String>[] rows) { return this; }
                  public Shelf withWild(List<?> wild) { return this; }
                  public Shelf withWildRows(List<?>[] rows) { return this; }
                  @SuppressWarnings("unchecked")
                  public <T extends List> T generic() { return (T) items; }
                  public static final class Legacy extends ArrayList {}
              }
              """),
          external(
              "Docket",
              """
              public final class Docket {
                  private final String id;
                  private Docket(String id) { this.id = id; }
                  public String id() { return id; }
                  public String code() { return id; }
                  public List<String> tags() { return List.of(); }
                  public Builder toBuilder() { return new Builder<String>().id(id); }
                  public static final class Builder<T> {
                      private Object id;
                      public Builder<T> id(T id) { this.id = id; return this; }
                      public Builder<T> code(String code) { this.id = code; return this; }
                      public Builder<T> tags(List<String> tags) { return this; }
                      public Docket build() { return new Docket((String) id); }
                  }
              }
              """),
          external(
              "Tote",
              """
              public final class Tote<T> {
                  private final T value;
                  public Tote(T value) { this.value = value; }
                  public T value() { return value; }
                  public Builder<T> toBuilder() { return new Builder<T>().value(value); }
                  public Builder rawBuilder() { return new Builder<T>().value(value); }
                  public static final class Builder<T> {
                      private T value;
                      public Builder<T> value(T value) { this.value = value; return this; }
                      public Builder<T> label(String label) { return this; }
                      public Builder rawStep(T value) { this.value = value; return this; }
                      public Tote<T> build() { return new Tote<>(value); }
                      public Tote rawBuild() { return new Tote<>(value); }
                  }
              }
              """),
          external(
              "Bag",
              """
              public final class Bag {
                  private List items;
                  private List<String> typed;
                  public Bag() {}
                  public Bag(Bag other) { this.items = other.items; this.typed = other.typed; }
                  public List items() { return items; }
                  public void setItems(List items) { this.items = items; }
                  public List<String> typed() { return typed; }
                  public void setTyped(List<String> typed) { this.typed = typed; }
              }
              """),
          external(
              "Base",
              """
              public class Base<T> {
                  private T value;
                  public void setValue(T value) { this.value = value; }
                  public Doc withValue(T value) { return new Doc(); }
              }
              """),
          external(
              "Memo",
              """
              public final class Memo {
                  private final Object payload;
                  public Memo(Object payload) { this.payload = payload; }
                  @SuppressWarnings("unchecked")
                  public <T> T payload() { return (T) payload; }
                  public Memo withPayload(CharSequence payload) { return new Memo(payload); }
              }
              """),
          external(
              "Pot",
              """
              public final class Pot {
                  private final List items;
                  private final String name;
                  public Pot(List<?> contents, String name) { this.items = (List) contents; this.name = name; }
                  public List items() { return items; }
                  public String name() { return name; }
              }
              """),
          external(
              "Pack",
              """
              public final class Pack {
                  private final List items;
                  private final String name;
                  public Pack(List<String> contents, String name, String... tags) { this.items = contents; this.name = name; }
                  public List items() { return items; }
                  public String name() { return name; }
              }
              """),
          external(
              "Outer",
              """
              public abstract class Outer<T> {
                  protected T value;
                  public T get() { return value; }
                  public Builder toBuilder() { return new Builder(); }
                  public class Builder {
                      public Builder value(T value) { Outer.this.value = value; return this; }
                      public Outer<T> build() { return Outer.this; }
                  }
              }
              """),
          external("Inn", "public final class Inn extends Outer {}\n"),
          external(
              "Doc",
              """
              public final class Doc extends Base {
                  public Doc() {}
                  public Doc(Doc other) {}
                  public String value() { return ""; }
              }
              """));

  /** One spec per shape refused, named by the first column of the refusal rows. */
  private static final List<JavaFileObject> REFUSED_SPECS =
      List.of(
          spec(
              "ShelfRawRead",
              "Shelf",
              """
                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, List<String>> items();
              """),
          spec(
              "ShelfRawSupertypeRead",
              "Shelf",
              """
                  @Wither(value = "withTyped", getter = "legacy")
                  Lens<Shelf, List<String>> legacy();
              """),
          spec(
              "ShelfRawArrayRead",
              "Shelf",
              """
                  @Wither(value = "withRows", getter = "rows")
                  Lens<Shelf, List<String>[]> rows();
              """),
          spec(
              "ShelfWildcardRead",
              "Shelf",
              """
                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, ? extends List<String>> items();
              """),
          spec(
              "ShelfRawFocusWither",
              "Shelf",
              """
                  @SuppressWarnings("rawtypes")
                  @Wither(value = "withTyped", getter = "typed")
                  Lens<Shelf, List> typed();
              """),
          spec(
              "ShelfConstructorRead",
              "Shelf",
              """
                  @ViaConstructor(parameterOrder = {"items", "name"})
                  Lens<Shelf, List<String>> items();
              """),
          spec(
              "ShelfConstructorArgument",
              "Shelf",
              """
                  @ViaConstructor(parameterOrder = {"items", "name"})
                  Lens<Shelf, String> name();
              """),
          spec(
              "ShelfConstructorFocus",
              "Shelf",
              """
                  @SuppressWarnings("rawtypes")
                  @ViaConstructor(parameterOrder = {"typed", "name"})
                  Lens<Shelf, List> typed();
              """),
          spec(
              "DocketRawBuilder",
              "Docket",
              """
                  @ViaBuilder
                  Lens<Docket, String> id();
              """),
          spec(
              "ToteRawBuilder",
              "Tote<String>",
              """
                  @ViaBuilder(getter = "value", toBuilder = "rawBuilder", setter = "label")
                  Lens<Tote<String>, String> label();
              """),
          spec(
              "ShelfExtendsWildcardTarget",
              "Shelf",
              """
                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, List<? extends CharSequence>> chars();
              """),
          spec(
              "ShelfSuperWildcardTarget",
              "Shelf",
              """
                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, List<? super String>> strings();
              """),
          spec(
              "ShelfWildcardConstructor",
              "Shelf",
              """
                  @ViaConstructor(parameterOrder = {"items", "name"})
                  Lens<Shelf, ? extends String> name();
              """),
          spec(
              "PackVariableArity",
              "Pack",
              """
                  @ViaConstructor(parameterOrder = {"items", "name"})
                  Lens<Pack, String> name();
              """),
          spec(
              "DocketWildcardRawBuilder",
              "Docket",
              """
                  @ViaBuilder
                  Lens<Docket, ? extends String> id();
              """),
          spec(
              "InnRawEnclosing",
              "Inn",
              """
                  @ViaBuilder(getter = "get", setter = "value")
                  Lens<Inn, Object> value();
              """),
          spec(
              "DocWildcardWither",
              "Doc",
              """
                  @Wither(value = "withValue", getter = "value")
                  Lens<Doc, ?> any();
              """),
          spec(
              "DocketRawBuilderErasedParameter",
              "Docket",
              """
                  @ViaBuilder
                  Lens<Docket, List<String>> tags();
              """),
          spec(
              "ToteRawStep",
              "Tote<String>",
              """
                  @ViaBuilder(getter = "value", setter = "rawStep")
                  Lens<Tote<String>, String> value();
              """),
          spec(
              "ToteRawBuild",
              "Tote<String>",
              """
                  @ViaBuilder(getter = "value", build = "rawBuild")
                  Lens<Tote<String>, String> value();
              """),
          spec(
              "BagRawRead",
              "Bag",
              """
                  @ViaCopyAndSet(setter = "setItems")
                  Lens<Bag, List<String>> items();
              """),
          spec(
              "BagRawFocus",
              "Bag",
              """
                  @SuppressWarnings("rawtypes")
                  @ViaCopyAndSet(setter = "setTyped")
                  Lens<Bag, List> typed();
              """),
          spec(
              "DocRawSupertypeCall",
              "Doc",
              """
                  @ViaCopyAndSet(setter = "setValue")
                  Lens<Doc, String> value();
              """));

  /** Shapes that convert nothing unchecked, each refusal's fix followed among them. */
  private static final List<JavaFileObject> ACCEPTED_SPECS =
      List.of(
          spec(
              "ShelfAccepted",
              "Shelf",
              """
                  // A raw focus over an accessor and a wither that are raw themselves.
                  @SuppressWarnings("rawtypes")
                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, List> items();

                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, ? super List<String>> loose();

                  @Wither(value = "withItems", getter = "items")
                  Lens<Shelf, ?> any();

                  @Wither(value = "withTyped", getter = "typed")
                  Lens<Shelf, List<String>> typed();

                  // A raw read converts to a reifiable focus with no warning.
                  @Wither(value = "withWild", getter = "items")
                  Lens<Shelf, List<?>> wild();

                  @Wither(value = "withWildRows", getter = "rows")
                  Lens<Shelf, List<?>[]> wildRows();

                  // A generic accessor reads what javac infers from the focus.
                  @Wither(value = "withTyped", getter = "generic")
                  Lens<Shelf, List<String>> generic();
              """),
          spec(
              "ShelfInferredConstructorAccepted",
              "Shelf",
              """
                  @ViaConstructor(parameterOrder = {"generic", "name"})
                  Lens<Shelf, String> name();
              """),
          spec(
              "PotAccepted",
              "Pot",
              """
                  @ViaConstructor(parameterOrder = {"items", "name"})
                  Lens<Pot, String> name();
              """),
          spec(
              "OuterAccepted",
              "Outer<String>",
              """
                  // An inner builder reached through a parameterised outer type keeps its types.
                  @ViaBuilder(getter = "get", setter = "value")
                  Lens<Outer<String>, String> value();
              """),
          spec(
              "ShelfConstructorAccepted",
              "Shelf",
              """
                  @ViaConstructor(parameterOrder = {"typed", "name"})
                  Lens<Shelf, String> name();

                  @ViaConstructor(parameterOrder = {"typed", "name"})
                  Lens<Shelf, List<String>> typed();
              """),
          spec(
              "DocketAccepted",
              "Docket",
              """
                  // A raw builder whose setter's parameter erasure leaves as it is.
                  @ViaBuilder(getter = "code", setter = "code")
                  Lens<Docket, String> code();
              """),
          spec(
              "ToteAccepted",
              "Tote<String>",
              """
                  @ViaBuilder(getter = "value")
                  Lens<Tote<String>, String> value();

                  @ViaBuilder(getter = "value", setter = "value")
                  Lens<Tote<String>, ? extends String> bounded();
              """),
          spec(
              "MemoAccepted",
              "Memo",
              """
                  // A generic accessor reads whatever javac infers, so a wildcard focus leaves it
                  // to javac rather than holding a type variable to the bound.
                  @Wither(value = "withPayload", getter = "payload")
                  Lens<Memo, ? extends CharSequence> payload();
              """),
          spec(
              "BagAccepted",
              "Bag",
              """
                  @ViaCopyAndSet(setter = "setTyped")
                  Lens<Bag, List<String>> typed();
              """));

  @TempDir static Path classes;

  private static Compilation refused;

  private static Compilation accepted;

  @BeforeAll
  static void compile() throws IOException {
    // Parameter names kept, so that a constructor reads as its source declares it. Shelf's first
    // parameter is named after no accessor, so an order may pass any of them there.
    Compilation upstream = javac().withOptions("-parameters").compile(EXTERNAL);
    assertThat(upstream).succeeded();
    List<File> classpath =
        GeneratorTestHelper.classpathWith(
            GeneratorTestHelper.classDirectoryWithout(upstream, classes));
    refused =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .withOptions(LINT)
            .withClasspath(classpath)
            .compile(REFUSED_SPECS);
    accepted =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .withOptions(LINT)
            .withClasspath(classpath)
            .compile(ACCEPTED_SPECS);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiterString = " | ",
      textBlock =
          """
          ShelfRawRead | @Wither: 'items()' reads the raw type 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion. | Point @Wither's 'getter' at an accessor that reads 'List<String>', or declare the lens over 'List', with @SuppressWarnings("rawtypes") on the lens method, and rebuild it through a method that takes the raw 'List'.
          ShelfRawSupertypeRead | @Wither: 'legacy()' reads 'Shelf.Legacy', a raw 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion. | Point @Wither's 'getter' at an accessor that reads 'List<String>', or declare the lens over 'Shelf.Legacy' and rebuild it through a method that takes the raw 'List'.
          ShelfRawArrayRead | @Wither: 'rows()' reads 'List[]', which reaches the lens's focus 'List<String>[]' only through an unchecked conversion. | or declare the lens over 'List[]', with @SuppressWarnings("rawtypes") on the lens method, and rebuild it through a method that takes 'List[]'.
          ShelfWildcardRead | @Wither: 'items()' reads the raw type 'List', which reaches the lens's focus '? extends List<String>' only through an unchecked conversion. | Point @Wither's 'getter' at an accessor that reads 'List<String>'
          ShelfRawFocusWither | @Wither: 'withTyped(List<String>)' takes the lens's focus, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Declare the lens over 'List<String>', the type 'withTyped' takes; otherwise rebuild 'Shelf' with @ViaBuilder, @ViaConstructor or @ViaCopyAndSet.
          ShelfConstructorRead | @ViaConstructor: 'items()' reads the raw type 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion. | Name the lens method after an accessor that reads 'List<String>', or declare the lens over 'List', with @SuppressWarnings("rawtypes") on the lens method, and rebuild it through a constructor that takes the raw 'List'.
          ShelfConstructorArgument | @ViaConstructor: 'Shelf(List<String> contents, String name)' takes what 'items()' reads, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Name an accessor that reads 'List<String>' in its place in 'parameterOrder'; otherwise rebuild 'Shelf' with @Wither, @ViaBuilder or @ViaCopyAndSet.
          ShelfConstructorFocus | @ViaConstructor: 'Shelf(List<String> contents, String name)' takes the lens's focus, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Declare the lens over 'List<String>', the type the constructor takes; otherwise rebuild 'Shelf' with @Wither, @ViaBuilder or @ViaCopyAndSet.
          DocketRawBuilder | @ViaBuilder: 'toBuilder()' hands back the raw type 'Docket.Builder', so the generated call to 'id(T)' on it is unchecked. A raw type erases the parameters of the methods called through it, | Point @ViaBuilder's 'toBuilder' at a method that hands back the builder with its type arguments, or rebuild 'Docket' with @Wither, @ViaConstructor or @ViaCopyAndSet.
          ShelfExtendsWildcardTarget | @Wither: 'items()' reads the raw type 'List', which reaches the lens's focus 'List<? extends CharSequence>' only through an unchecked conversion. | Point @Wither's 'getter' at an accessor that reads 'List<? extends CharSequence>'
          ShelfSuperWildcardTarget | @Wither: 'items()' reads the raw type 'List', which reaches the lens's focus 'List<? super String>' only through an unchecked conversion. | Point @Wither's 'getter' at an accessor that reads 'List<? super String>'
          ShelfWildcardConstructor | @ViaConstructor: 'Shelf(List<String> contents, String name)' takes what 'items()' reads, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Name an accessor that reads 'List<String>' in its place in 'parameterOrder'
          PackVariableArity | @ViaConstructor: 'Pack(List<String> contents, String name, String... tags)' takes what 'items()' reads, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Name an accessor that reads 'List<String>' in its place in 'parameterOrder'
          DocketWildcardRawBuilder | @ViaBuilder: 'toBuilder()' hands back the raw type 'Docket.Builder', so the generated call to 'id(T)' on it is unchecked. | Point @ViaBuilder's 'toBuilder' at a method that hands back the builder with its type arguments
          InnRawEnclosing | @ViaBuilder: 'toBuilder()' hands back the raw type 'Outer.Builder', so the generated call to 'value(T)' on it is unchecked. | Point @ViaBuilder's 'toBuilder' at a method that hands back the builder with its type arguments
          DocWildcardWither | @Wither: 'Doc' inherits 'withValue(T)' from the raw type 'Base', so the generated call to it is unchecked. | Rebuild 'Doc' with @ViaBuilder, @ViaConstructor or @ViaCopyAndSet.
          DocketRawBuilderErasedParameter | @ViaBuilder: 'toBuilder()' hands back the raw type 'Docket.Builder', so the generated call to 'tags(List<String>)' on it is unchecked. | Point @ViaBuilder's 'toBuilder' at a method that hands back the builder with its type arguments
          ToteRawBuilder | @ViaBuilder: 'build()' returns the raw type 'Tote', which reaches the source type 'Tote<String>' only through an unchecked conversion. The generated lens hands what 'build()' returns back as the source type, and on the raw 'Tote.Builder' that 'rawBuilder()' hands back, it returns the erasure of what it declares; | Point @ViaBuilder's 'toBuilder' at a method that hands back the builder with its type arguments, or rebuild 'Tote<String>' with @Wither, @ViaConstructor or @ViaCopyAndSet.
          ToteRawStep | @ViaBuilder: 'build()' returns the raw type 'Tote', which reaches the source type 'Tote<String>' only through an unchecked conversion. The generated lens hands what 'build()' returns back as the source type, and on the raw 'Tote.Builder' that 'rawStep(String)' hands back, it returns the erasure of what it declares; | Point @ViaBuilder's 'setter' at a method that hands back the builder with its type arguments
          ToteRawBuild | @ViaBuilder: 'rawBuild()' returns the raw type 'Tote', which reaches the source type 'Tote<String>' only through an unchecked conversion. The generated lens hands what 'rawBuild()' returns back as the source type; | Point @ViaBuilder's 'build' at a method that returns 'Tote<String>', or rebuild 'Tote<String>' with @Wither, @ViaConstructor or @ViaCopyAndSet.
          BagRawRead | @ViaCopyAndSet: 'items()' reads the raw type 'List', which reaches the lens's focus 'List<String>' only through an unchecked conversion. | Name the lens method after an accessor that reads 'List<String>'
          BagRawFocus | @ViaCopyAndSet: 'setTyped(List<String>)' takes the lens's focus, the raw type 'List', only through an unchecked conversion to 'List<String>'. | Declare the lens over 'List<String>', the type 'setTyped' takes; otherwise rebuild 'Bag' with @Wither, @ViaBuilder or @ViaConstructor.
          DocRawSupertypeCall | @ViaCopyAndSet: 'Doc' inherits 'setValue(T)' from the raw type 'Base', so the generated call to it is unchecked. | Rebuild 'Doc' with @Wither, @ViaBuilder or @ViaConstructor.
          """)
  @DisplayName("an unchecked read, call or conversion is refused at the spec method")
  void anUncheckedConversionIsRefusedAtTheSpec(String spec, String what, String fix) {
    // Each spec stops at its own refusal, so the run reports one error for each, and no file is
    // generated for any of them to warn in.
    assertThat(refused).failed();
    assertThat(refused.errors())
        .filteredOn(error -> error.getSource().getName().endsWith("/" + spec + ".java"))
        .singleElement()
        .extracting(error -> error.getMessage(Locale.ROOT))
        .satisfies(
            message ->
                assertThat(message)
                    .contains(what)
                    .contains(
                        "javac flags that as unchecked in the generated file, which no"
                            + " @SuppressWarnings on your spec reaches.")
                    .contains(fix));
  }

  @Test
  @DisplayName("every refusal is reported, and nothing else is")
  void everyRefusalIsReported() {
    assertThat(refused.errors()).hasSize(REFUSED_SPECS.size());
    assertThat(refused.warnings())
        .extracting(warning -> warning.getMessage(Locale.ROOT))
        .noneMatch(message -> message.contains("unchecked"));
  }

  @Test
  @DisplayName(
      "a shape that converts nothing unchecked compiles cleanly, each fix followed among them")
  void aShapeThatConvertsNothingUncheckedCompilesCleanly() {
    assertThat(accepted).succeededWithoutWarnings();
    assertThat(
            Stream.of(
                "ShelfAcceptedImpl",
                "ShelfConstructorAcceptedImpl",
                "DocketAcceptedImpl",
                "ToteAcceptedImpl",
                "MemoAcceptedImpl",
                "ShelfInferredConstructorAcceptedImpl",
                "PotAcceptedImpl",
                "OuterAcceptedImpl",
                "BagAcceptedImpl"))
        .allSatisfy(
            generated ->
                assertThat(accepted.generatedSourceFile("com.myapp." + generated)).isPresent());
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.external;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TypeKindAnalyser}.
 *
 * <p>These tests verify that the analyser correctly identifies different type kinds and extracts
 * the appropriate information for optics generation.
 */
@DisplayName("TypeKindAnalyser")
class TypeKindAnalyserTest {

  /**
   * A helper processor that runs the TypeKindAnalyser and captures the result. This allows us to
   * test the analyser within the annotation processing environment.
   */
  private static class AnalyserTestProcessor extends AbstractProcessor {
    private final String targetTypeName;
    private TypeAnalysis result;

    AnalyserTestProcessor(String targetTypeName) {
      this.targetTypeName = targetTypeName;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.RELEASE_25;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        return false;
      }

      TypeElement typeElement = processingEnv.getElementUtils().getTypeElement(targetTypeName);
      if (typeElement != null) {
        TypeKindAnalyser analyser = new TypeKindAnalyser(processingEnv.getTypeUtils());
        result = analyser.analyseType(typeElement);
      }

      return false;
    }

    TypeAnalysis getResult() {
      return result;
    }
  }

  private TypeAnalysis analyseType(String typeName, JavaFileObject... sources) {
    AnalyserTestProcessor processor = new AnalyserTestProcessor(typeName);
    Compilation compilation = javac().withProcessors(processor).compile(sources);
    assertThat(compilation).succeeded();
    return processor.getResult();
  }

  @Nested
  @DisplayName("Record Analysis")
  class RecordAnalysis {

    @Test
    @DisplayName("should identify record type")
    void shouldIdentifyRecord() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.TestRecord",
              """
              package com.test;

              public record TestRecord(String name, int age) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.TestRecord", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.RECORD);
      assertThat(analysis.supportsLenses()).isTrue();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should extract field info from record")
    void shouldExtractFieldInfo() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Person",
              """
              package com.test;

              public record Person(String firstName, String lastName, int age) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Person", source);

      assertThat(analysis.fields()).hasSize(3);
      assertThat(analysis.fields().get(0).name()).isEqualTo("firstName");
      assertThat(analysis.fields().get(1).name()).isEqualTo("lastName");
      assertThat(analysis.fields().get(2).name()).isEqualTo("age");
    }

    @Test
    @DisplayName("should detect container fields in record")
    void shouldDetectContainerFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Order",
              """
              package com.test;

              import java.util.List;

              public record Order(String id, List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Order", source);

      assertThat(analysis.fields()).hasSize(2);
      assertThat(analysis.fields().get(0).hasTraversal()).isFalse();
      assertThat(analysis.fields().get(1).hasTraversal()).isTrue();
      assertThat(analysis.fields().get(1).containerType()).isPresent();
      assertThat(analysis.fields().get(1).containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);
    }
  }

  @Nested
  @DisplayName("Sealed Interface Analysis")
  class SealedInterfaceAnalysis {

    @Test
    @DisplayName("should identify sealed interface")
    void shouldIdentifySealedInterface() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Result",
              """
              package com.test;

              public sealed interface Result permits Success, Failure {}
              """);

      var success =
          JavaFileObjects.forSourceString(
              "com.test.Success",
              """
              package com.test;

              public record Success(String value) implements Result {}
              """);

      var failure =
          JavaFileObjects.forSourceString(
              "com.test.Failure",
              """
              package com.test;

              public record Failure(String error) implements Result {}
              """);

      TypeAnalysis analysis = analyseType("com.test.Result", sealedInterface, success, failure);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.SEALED_INTERFACE);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
    }

    @Test
    @DisplayName("should extract permitted subtypes")
    void shouldExtractPermittedSubtypes() {
      var sealedInterface =
          JavaFileObjects.forSourceString(
              "com.test.Shape",
              """
              package com.test;

              public sealed interface Shape permits Circle, Square, Triangle {}
              """);

      var circle =
          JavaFileObjects.forSourceString(
              "com.test.Circle",
              """
              package com.test;

              public record Circle(double radius) implements Shape {}
              """);

      var square =
          JavaFileObjects.forSourceString(
              "com.test.Square",
              """
              package com.test;

              public record Square(double side) implements Shape {}
              """);

      var triangle =
          JavaFileObjects.forSourceString(
              "com.test.Triangle",
              """
              package com.test;

              public record Triangle(double base, double height) implements Shape {}
              """);

      TypeAnalysis analysis =
          analyseType("com.test.Shape", sealedInterface, circle, square, triangle);

      assertThat(analysis.permittedSubtypes()).hasSize(3);
      assertThat(analysis.permittedSubtypes())
          .extracting(te -> te.getSimpleName().toString())
          .containsExactly("Circle", "Square", "Triangle");
    }
  }

  @Nested
  @DisplayName("Enum Analysis")
  class EnumAnalysis {

    @Test
    @DisplayName("should identify enum type")
    void shouldIdentifyEnum() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Colour",
              """
              package com.test;

              public enum Colour { RED, GREEN, BLUE }
              """);

      TypeAnalysis analysis = analyseType("com.test.Colour", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.ENUM);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isTrue();
    }

    @Test
    @DisplayName("should extract enum constants")
    void shouldExtractEnumConstants() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Day",
              """
              package com.test;

              public enum Day { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }
              """);

      TypeAnalysis analysis = analyseType("com.test.Day", source);

      assertThat(analysis.enumConstants())
          .containsExactly(
              "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
    }
  }

  @Nested
  @DisplayName("Wither Class Analysis")
  class WitherClassAnalysis {

    @Test
    @DisplayName("should identify class with wither methods")
    void shouldIdentifyWitherClass() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.ImmutablePoint",
              """
              package com.test;

              public final class ImmutablePoint {
                  private final int x;
                  private final int y;

                  public ImmutablePoint(int x, int y) {
                      this.x = x;
                      this.y = y;
                  }

                  public int getX() { return x; }
                  public int getY() { return y; }

                  public ImmutablePoint withX(int x) { return new ImmutablePoint(x, this.y); }
                  public ImmutablePoint withY(int y) { return new ImmutablePoint(this.x, y); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.ImmutablePoint", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.supportsLenses()).isTrue();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should extract wither method info")
    void shouldExtractWitherInfo() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Config",
              """
              package com.test;

              public final class Config {
                  private final String host;
                  private final int port;

                  public Config(String host, int port) {
                      this.host = host;
                      this.port = port;
                  }

                  public String getHost() { return host; }
                  public int getPort() { return port; }

                  public Config withHost(String host) { return new Config(host, this.port); }
                  public Config withPort(int port) { return new Config(this.host, port); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Config", source);

      assertThat(analysis.witherMethods()).hasSize(2);
      assertThat(analysis.witherMethods().get(0).fieldName()).isEqualTo("host");
      assertThat(analysis.witherMethods().get(0).witherMethodName()).isEqualTo("withHost");
      assertThat(analysis.witherMethods().get(0).getterMethodName()).isEqualTo("getHost");
    }

    @Test
    @DisplayName("should detect mutable fields on wither class")
    void shouldDetectMutableFields() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MutableConfig",
              """
              package com.test;

              public class MutableConfig {
                  private String host;
                  private int port;

                  public String getHost() { return host; }
                  public void setHost(String host) { this.host = host; }

                  public int getPort() { return port; }
                  public void setPort(int port) { this.port = port; }

                  public MutableConfig withHost(String host) {
                      MutableConfig copy = new MutableConfig();
                      copy.host = host;
                      copy.port = this.port;
                      return copy;
                  }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MutableConfig", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.hasMutableFields()).isTrue();
    }

    @Test
    @DisplayName("a word starting 'with' is no wither, nor is with2D, while witheMail is one")
    void wordsAndUnnameableFieldsAreNotWithers() {
      // Each word has a getter for what follows its prefix, so only its spelling keeps it out;
      // with2D is spelt as a wither, but no lens method can be named '2D'.
      var source =
          JavaFileObjects.forSourceString(
              "com.test.CreditLine",
              """
              package com.test;

              public final class CreditLine {
                  private final long limit;
                  private final long draw;
                  private final String eMail;

                  public CreditLine(long limit, long draw, String eMail) {
                      this.limit = limit;
                      this.draw = draw;
                      this.eMail = eMail;
                  }

                  public long getLimit() { return limit; }
                  public long getDraw() { return draw; }
                  public int in() { return 0; }
                  public int out() { return 0; }
                  public int get2D() { return 0; }
                  public String eMail() { return eMail; }

                  public CreditLine withLimit(long limit) {
                      return new CreditLine(limit, draw, eMail);
                  }
                  public CreditLine withdraw(long amount) {
                      return new CreditLine(limit, draw + amount, eMail);
                  }
                  public CreditLine within(int margin) { return this; }
                  public CreditLine without(int margin) { return this; }
                  public CreditLine with2D(int d) { return this; }
                  public CreditLine witheMail(String eMail) {
                      return new CreditLine(limit, draw, eMail);
                  }
                  public void settle(long amount) {}
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.CreditLine", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.WITHER_CLASS);
      assertThat(analysis.witherMethods())
          .extracting(WitherInfo::witherMethodName)
          .containsExactly("withLimit", "witheMail");
      assertThat(analysis.witherMethods())
          .extracting(WitherInfo::fieldName)
          .containsExactly("limit", "eMail");
      // The mutable guard counts any void set method, so it errs towards refusing.
      assertThat(analysis.hasMutableFields()).isTrue();
    }

    @Test
    @DisplayName("a wither pairs with a getter spelt as JavaBeans spells it, and withURL sets uRL")
    void witherPairsWithAJavaBeansGetter() {
      // geteMail and witheMail are the pair Lombok's beanspec capitalisation writes for 'eMail'.
      // withURL sets 'uRL', its first letter in lower case, and not the 'URL' JavaBeans reads.
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Contact",
              """
              package com.test;

              public final class Contact {
                  private final String eMail;
                  private final String url;

                  public Contact(String eMail, String url) {
                      this.eMail = eMail;
                      this.url = url;
                  }

                  public String geteMail() { return eMail; }
                  public String getURL() { return url; }

                  public Contact witheMail(String eMail) { return new Contact(eMail, url); }
                  public Contact withURL(String url) { return new Contact(eMail, url); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Contact", source);

      assertThat(analysis.witherMethods())
          .extracting(WitherInfo::fieldName, WitherInfo::getterMethodName)
          .containsExactly(tuple("eMail", "geteMail"), tuple("uRL", "getURL"));
    }

    @Test
    @DisplayName("a JavaBeans getter pairs only with a wither spelt the same way")
    void javaBeansGetterPairsOnlyWithAWitherSpeltTheSameWay() {
      // JavaBeans reads geteMail and withEMail as two properties, eMail and EMail, and here they
      // are two fields: pairing them would read one and set the other.
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Pair",
              """
              package com.test;

              public final class Pair {
                  private final String eMail;
                  private final String EMail;

                  public Pair(String eMail, String EMail) {
                      this.eMail = eMail;
                      this.EMail = EMail;
                  }

                  public String geteMail() { return eMail; }

                  public Pair withEMail(String v) { return new Pair(eMail, v); }
                  public Pair witheMail(String v) { return new Pair(v, EMail); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Pair", source);

      assertThat(analysis.witherMethods())
          .extracting(WitherInfo::witherMethodName, WitherInfo::getterMethodName)
          .containsExactly(tuple("witheMail", "geteMail"));
      assertThat(analysis.withersLeftOut()).isEmpty();
    }

    @Test
    @DisplayName("of two withers for one field, the one whose getter the rule prefers is generated")
    void twoWithersForOneFieldGenerateThePreferredGetter() {
      // The bare name ranks first, so eMail() is preferred to geteMail() though witheMail is
      // declared first.
      var source =
          JavaFileObjects.forSourceString(
              "com.test.Ranked",
              """
              package com.test;

              public final class Ranked {
                  private final int n;
                  private final String mail;

                  public Ranked(int n, String mail) {
                      this.n = n;
                      this.mail = mail;
                  }

                  public int eMail() { return n; }
                  public String geteMail() { return mail; }

                  public Ranked witheMail(String mail) { return new Ranked(n, mail); }
                  public Ranked withEMail(int n) { return new Ranked(n, mail); }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.Ranked", source);

      assertThat(analysis.witherMethods())
          .extracting(WitherInfo::witherMethodName, WitherInfo::getterMethodName)
          .containsExactly(tuple("withEMail", "eMail"));
      assertThat(analysis.withersLeftOut())
          .extracting(leftOut -> leftOut.skipped().getterMethodName())
          .containsExactly("geteMail");
    }

    @Test
    @DisplayName("two withers the ranks leave tied are told apart by name, not declaration order")
    void withersTheRanksLeaveTiedAreToldApartByName() {
      // The Kelvin sign lower-cases to an ASCII k, so a wither spelt with it reaches the field
      // kMail as withkMail does, and both pair with getKMail() at one rank. Neither is spelt as
      // WITHER spells the field, withKMail with an ASCII K, so only the name tells them apart.
      String ascii = "public KMail withkMail(String m) { return new KMail(m); }";
      String kelvin =
          "public KMail with"
              + Character.toString(0x212A)
              + "Mail(String m) { return new KMail(m); }";

      assertThat(keptWither(ascii, kelvin)).isEqualTo("withkMail");
      assertThat(keptWither(kelvin, ascii)).isEqualTo("withkMail");
    }

    /** The one wither a KMail class declaring the given withers, in that order, is analysed to. */
    private String keptWither(String first, String second) {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.KMail",
              """
              package com.test;

              public final class KMail {
                  private final String m;
                  public KMail(String m) { this.m = m; }
                  public String getKMail() { return m; }
                  %s
                  %s
              }
              """
                  .formatted(first, second));

      TypeAnalysis analysis = analyseType("com.test.KMail", source);

      assertThat(analysis.witherMethods()).hasSize(1);
      return analysis.witherMethods().getFirst().witherMethodName();
    }
  }

  @Nested
  @DisplayName("Unsupported Type Analysis")
  class UnsupportedTypeAnalysis {

    @Test
    @DisplayName("should identify class without withers as unsupported")
    void shouldIdentifyUnsupportedClass() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.PlainClass",
              """
              package com.test;

              public class PlainClass {
                  private String value;

                  public String getValue() { return value; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.PlainClass", source);

      assertThat(analysis).isNotNull();
      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.supportsLenses()).isFalse();
      assertThat(analysis.supportsPrisms()).isFalse();
    }

    @Test
    @DisplayName("should detect mutable class as unsupported with mutable flag")
    void shouldDetectMutableClassAsUnsupported() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.MutablePojo",
              """
              package com.test;

              public class MutablePojo {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      TypeAnalysis analysis = analyseType("com.test.MutablePojo", source);

      assertThat(analysis.typeKind()).isEqualTo(TypeAnalysis.TypeKind.UNSUPPORTED);
      assertThat(analysis.hasMutableFields()).isTrue();
    }
  }

  @Nested
  @DisplayName("Container Type Detection")
  class ContainerTypeDetection {

    @Test
    @DisplayName("should detect List container")
    void shouldDetectListContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithList",
              """
              package com.test;

              import java.util.List;

              public record WithList(List<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithList", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.LIST);
    }

    @Test
    @DisplayName("should detect Set container")
    void shouldDetectSetContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithSet",
              """
              package com.test;

              import java.util.Set;

              public record WithSet(Set<Integer> numbers) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithSet", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.SET);
    }

    @Test
    @DisplayName("should detect Collection container")
    void shouldDetectCollectionContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithCollection",
              """
              package com.test;

              import java.util.Collection;

              public record WithCollection(Collection<Integer> numbers) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithCollection", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.COLLECTION);
    }

    @Test
    @DisplayName("should not detect a raw Collection as a container")
    void shouldNotDetectRawCollectionContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRawCollection",
              """
              package com.test;

              import java.util.Collection;

              @SuppressWarnings("rawtypes")
              public record WithRawCollection(Collection items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRawCollection", source);

      assertThat(analysis.fields().getFirst().containerType()).isEmpty();
    }

    @Test
    @DisplayName("should detect Optional container")
    void shouldDetectOptionalContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithOptional",
              """
              package com.test;

              import java.util.Optional;

              public record WithOptional(Optional<String> maybeValue) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithOptional", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.OPTIONAL);
    }

    @Test
    @DisplayName("should detect array container")
    void shouldDetectArrayContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithArray",
              """
              package com.test;

              public record WithArray(int[] values) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithArray", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      assertThat(analysis.fields().getFirst().containerType().get().kind())
          .isEqualTo(ContainerType.Kind.ARRAY);
    }

    @Test
    @DisplayName("should not detect a concrete container type such as ArrayList")
    void shouldNotDetectArrayListAsContainer() {
      // The standard List traversal rebuilds a List that an ArrayList field cannot take back, so
      // only the interface itself is a container here.
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithArrayList",
              """
              package com.test;

              import java.util.ArrayList;

              public record WithArrayList(ArrayList<String> items) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithArrayList", source);

      assertThat(analysis.fields().getFirst().containerType()).isEmpty();
    }

    @Test
    @DisplayName("should not detect raw List, Set, Map or Optional")
    void shouldNotDetectRawContainers() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithRaw",
              """
              package com.test;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;
              import java.util.Set;

              @SuppressWarnings("rawtypes")
              public record WithRaw(List l, Set s, Map m, Optional o) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithRaw", source);

      assertThat(analysis.fields()).hasSize(4);
      assertThat(analysis.fields()).allSatisfy(f -> assertThat(f.containerType()).isEmpty());
    }

    @Test
    @DisplayName("should detect Map container")
    void shouldDetectMapContainer() {
      var source =
          JavaFileObjects.forSourceString(
              "com.test.WithMap",
              """
              package com.test;

              import java.util.Map;

              public record WithMap(Map<String, Integer> mapping) {}
              """);

      TypeAnalysis analysis = analyseType("com.test.WithMap", source);

      assertThat(analysis.fields().getFirst().containerType()).isPresent();
      ContainerType container = analysis.fields().getFirst().containerType().get();
      assertThat(container.kind()).isEqualTo(ContainerType.Kind.MAP);
      assertThat(container.isMap()).isTrue();
    }
  }
}

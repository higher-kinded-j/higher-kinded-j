// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for spec interface processing in {@link ImportOpticsProcessor}.
 *
 * <p>These tests verify that the processor correctly generates optics from spec interfaces
 * extending {@code OpticsSpec<S>} with various copy strategies and hints.
 */
@DisplayName("Spec Interface Processing")
class SpecInterfaceProcessingTest {

  /** Helper to print compilation diagnostics for debugging. */
  private void printDiagnostics(Compilation compilation) {
    for (var diagnostic : compilation.diagnostics()) {
      System.err.println(diagnostic.getKind() + ": " + diagnostic.getMessage(null));
    }
  }

  /** Helper to check compilation and print errors if it fails. */
  private void assertCompilationSucceeded(Compilation compilation) {
    if (compilation.status() != Compilation.Status.SUCCESS) {
      printDiagnostics(compilation);
      fail("Compilation failed - see diagnostics above");
    }
  }

  /** Helper to check generated file exists and print diagnostics if not. */
  private void assertGeneratedFileExists(Compilation compilation, String fileName) {
    if (compilation.generatedSourceFile(fileName).isEmpty()) {
      System.err.println("Generated file " + fileName + " not found.");
      System.err.println("Generated files:");
      compilation.generatedSourceFiles().forEach(f -> System.err.println("  - " + f.getName()));
      printDiagnostics(compilation);
      fail("Generated file not found: " + fileName);
    }
  }

  @Nested
  @DisplayName("Basic Spec Interface")
  class BasicSpecInterface {

    @Test
    @DisplayName("should recognize spec interface and generate file")
    void shouldRecognizeSpecInterfaceAndGenerateFile() {
      // Minimal test to verify spec interface detection works
      final var externalRecord =
          JavaFileObjects.forSourceString(
              "com.external.SimpleRecord",
              """
              package com.external;

              public record SimpleRecord(String value) {}
              """);

      // Very simple spec interface - just extends OpticsSpec, no methods
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleRecordOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.SimpleRecord;

              @ImportOptics
              public interface SimpleRecordOptics extends OpticsSpec<SimpleRecord> {
                  // No methods - just test that the file is generated
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalRecord, specInterface);

      // Print all diagnostics for debugging FIRST
      System.out.println("=== Compilation Status: " + compilation.status() + " ===");
      System.out.println("=== Compilation Diagnostics ===");
      for (var diagnostic : compilation.diagnostics()) {
        System.out.println(diagnostic.getKind() + ": " + diagnostic.getMessage(null));
      }

      // Only try to access generated files if compilation succeeded
      if (compilation.status() == Compilation.Status.SUCCESS) {
        System.out.println("\n=== Generated Files ===");
        compilation.generatedSourceFiles().forEach(f -> System.out.println("  " + f.getName()));
      }

      assertCompilationSucceeded(compilation);
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      assertGeneratedFileExists(compilation, "com.myapp.SimpleRecordOpticsImpl");
    }

    @Test
    @DisplayName("should generate optics from spec interface extending OpticsSpec")
    void shouldGenerateOpticsFromSpecInterface() {
      // External class with builder pattern
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Person",
              """
              package com.external;

              public final class Person {
                  private final String name;
                  private final int age;

                  private Person(Builder builder) {
                      this.name = builder.name;
                      this.age = builder.age;
                  }

                  public String name() { return name; }
                  public int age() { return age; }

                  public Builder toBuilder() {
                      return new Builder().name(name).age(age);
                  }

                  public static Builder builder() { return new Builder(); }

                  public static class Builder {
                      private String name;
                      private int age;

                      public Builder name(String name) { this.name = name; return this; }
                      public Builder age(int age) { this.age = age; return this; }
                      public Person build() { return new Person(this); }
                  }
              }
              """);

      // Spec interface
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PersonOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Person;

              @ImportOptics
              public interface PersonOptics extends OpticsSpec<Person> {

                  @ViaBuilder
                  Lens<Person, String> name();

                  @ViaBuilder
                  Lens<Person, Integer> age();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertCompilationSucceeded(compilation);
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      assertGeneratedFileExists(compilation, "com.myapp.PersonOpticsImpl");

      final String expectedNameLens = "public static Lens<Person, String> name()";
      final String expectedAgeLens = "public static Lens<Person, Integer> age()";

      assertGeneratedCodeContains(compilation, "com.myapp.PersonOpticsImpl", expectedNameLens);
      assertGeneratedCodeContains(compilation, "com.myapp.PersonOpticsImpl", expectedAgeLens);
    }
  }

  @Nested
  @DisplayName("@ViaBuilder Copy Strategy")
  class ViaBuilderCopyStrategy {

    @Test
    @DisplayName("should generate lens using builder pattern with defaults")
    void shouldGenerateLensWithBuilderDefaults() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Config",
              """
              package com.external;

              public final class Config {
                  private final String host;
                  private final int port;

                  private Config(Builder builder) {
                      this.host = builder.host;
                      this.port = builder.port;
                  }

                  public String host() { return host; }
                  public int port() { return port; }

                  public Builder toBuilder() {
                      return new Builder().host(host).port(port);
                  }

                  public static class Builder {
                      private String host;
                      private int port;

                      public Builder host(String host) { this.host = host; return this; }
                      public Builder port(int port) { this.port = port; return this; }
                      public Config build() { return new Config(this); }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ConfigOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Config;

              @ImportOptics
              public interface ConfigOptics extends OpticsSpec<Config> {

                  @ViaBuilder
                  Lens<Config, String> host();

                  @ViaBuilder
                  Lens<Config, Integer> port();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify builder pattern code is generated
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedBuilderUsage = "source.toBuilder().host(newValue).build()";
      assertGeneratedCodeContains(compilation, "com.myapp.ConfigOpticsImpl", expectedBuilderUsage);
    }

    @Test
    @DisplayName("should generate lens with custom builder method names")
    void shouldGenerateLensWithCustomBuilderMethods() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Request",
              """
              package com.external;

              public final class Request {
                  private final String url;

                  private Request(Builder builder) { this.url = builder.url; }

                  public String getUrl() { return url; }

                  public Builder newBuilder() { return new Builder().withUrl(url); }

                  public static class Builder {
                      private String url;

                      public Builder withUrl(String url) { this.url = url; return this; }
                      public Request create() { return new Request(this); }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.RequestOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Request;

              @ImportOptics
              public interface RequestOptics extends OpticsSpec<Request> {

                  @ViaBuilder(getter = "getUrl", toBuilder = "newBuilder", setter = "withUrl", build = "create")
                  Lens<Request, String> url();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify custom method names are used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedCustomBuilder = "source.newBuilder().withUrl(newValue).create()";
      assertGeneratedCodeContains(
          compilation, "com.myapp.RequestOpticsImpl", expectedCustomBuilder);
    }
  }

  @Nested
  @DisplayName("@Wither Copy Strategy")
  class WitherCopyStrategy {

    @Test
    @DisplayName("should generate lens using wither method")
    void shouldGenerateLensWithWither() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.LocalDate",
              """
              package com.external;

              public final class LocalDate {
                  private final int year;
                  private final int month;
                  private final int day;

                  public LocalDate(int year, int month, int day) {
                      this.year = year;
                      this.month = month;
                      this.day = day;
                  }

                  public int getYear() { return year; }
                  public int getMonthValue() { return month; }
                  public int getDayOfMonth() { return day; }

                  public LocalDate withYear(int year) {
                      return new LocalDate(year, month, day);
                  }

                  public LocalDate withMonth(int month) {
                      return new LocalDate(year, month, day);
                  }

                  public LocalDate withDayOfMonth(int day) {
                      return new LocalDate(year, month, day);
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.LocalDateOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;
              import com.external.LocalDate;

              @ImportOptics
              public interface LocalDateOptics extends OpticsSpec<LocalDate> {

                  @Wither(value = "withYear", getter = "getYear")
                  Lens<LocalDate, Integer> year();

                  @Wither(value = "withMonth", getter = "getMonthValue")
                  Lens<LocalDate, Integer> monthValue();

                  @Wither(value = "withDayOfMonth", getter = "getDayOfMonth")
                  Lens<LocalDate, Integer> dayOfMonth();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();

      // Verify wither method is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedWitherUsage = "source.withYear(newValue)";
      assertGeneratedCodeContains(
          compilation, "com.myapp.LocalDateOpticsImpl", expectedWitherUsage);
    }
  }

  @Nested
  @DisplayName("@ViaCopyAndSet Copy Strategy")
  class ViaCopyAndSetStrategy {

    private static final JavaFileObject OVERLOADED_BASE =
        JavaFileObjects.forSourceString(
            "com.external.Base",
            """
            package com.external;

            public class Base {
                protected String name;
            }
            """);

    // Two constructors: new Node(source) alone would pick Node(Node), so the cast is what
    // reaches Node(Base). Serializable gives the supertype walk a second route to Object, so a
    // search that finds nothing meets the same supertype twice. 'tag' is declared on Node, so
    // Node(Base) cannot see it - which is how a test tells the two copies apart.
    private static final JavaFileObject OVERLOADED_NODE =
        JavaFileObjects.forSourceString(
            "com.external.Node",
            """
            package com.external;

            import java.io.Serializable;

            public class Node extends Base implements Serializable {
                private String tag = "";
                public Node(Base other) { this.name = other.name; }
                public Node(Node other) { this.name = other.name; this.tag = other.tag; }
                public String name() { return name; }
                public void setName(String name) { this.name = name; }
                public String tag() { return tag; }
                public void setTag(String tag) { this.tag = tag; }
            }
            """);

    private static JavaFileObject overloadedSpec(String copyConstructor) {
      return JavaFileObjects.forSourceString(
          "com.myapp.NodeOpticsSpec",
          """
          package com.myapp;

          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.ImportOptics;
          import org.higherkindedj.optics.annotations.OpticsSpec;
          import org.higherkindedj.optics.annotations.ViaCopyAndSet;
          import com.external.Node;

          @ImportOptics
          public interface NodeOpticsSpec extends OpticsSpec<Node> {

              @ViaCopyAndSet(copyConstructor = "%s", setter = "setName")
              Lens<Node, String> name();
          }
          """
              .formatted(copyConstructor));
    }

    @Test
    @DisplayName("should generate lens with @ViaCopyAndSet strategy")
    void shouldGenerateLensWithViaCopyAndSet() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.MutablePoint",
              """
              package com.external;

              public class MutablePoint {
                  private int x;
                  private int y;
                  public MutablePoint() {}
                  public MutablePoint(MutablePoint other) { this.x = other.x; this.y = other.y; }
                  public int x() { return x; }
                  public void setX(int x) { this.x = x; }
                  public int y() { return y; }
                  public void setY(int y) { this.y = y; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.MutablePointOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.MutablePoint;

              @ImportOptics
              public interface MutablePointOpticsSpec extends OpticsSpec<MutablePoint> {

                  @ViaCopyAndSet(setter = "setX")
                  Lens<MutablePoint, Integer> x();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.MutablePointOptics",
          "public static Lens<MutablePoint, Integer> x()");
      assertGeneratedCodeContains(
          compilation, "com.myapp.MutablePointOptics", "new MutablePoint(source)");
    }

    @Test
    @DisplayName("should cast the source to the named copy constructor parameter type")
    void shouldCastToNamedCopyConstructorParameterType() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(OVERLOADED_BASE, OVERLOADED_NODE, overloadedSpec("com.external.Base"));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.NodeOptics", "new Node((Base) source)");
    }

    @Test
    @DisplayName("should name the copy constructor parameter type with its type arguments")
    void shouldNameGenericCopyConstructorParameterType() {
      final var genericBase =
          JavaFileObjects.forSourceString(
              "com.external.Holder",
              """
              package com.external;

              public class Holder<T> {
                  protected String label;
              }
              """);

      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Labelled",
              """
              package com.external;

              public class Labelled extends Holder<String> {
                  public Labelled(Holder<String> other) { this.label = other.label; }
                  public String label() { return label; }
                  public void setLabel(String label) { this.label = label; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.LabelledOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Labelled;

              @ImportOptics
              public interface LabelledOpticsSpec extends OpticsSpec<Labelled> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Holder", setter = "setLabel")
                  Lens<Labelled, String> label();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(genericBase, externalClass, specInterface);

      // The instantiated supertype, not the raw one: a raw cast would be an unchecked conversion
      // at the constructor call.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.LabelledOptics", "new Labelled((Holder<String>) source)");
    }

    @Test
    @DisplayName("should not cast when the copy constructor names the source type itself")
    void shouldOmitCastWhenCopyConstructorNamesSourceType() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(OVERLOADED_BASE, OVERLOADED_NODE, overloadedSpec("com.external.Node"));

      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(compilation, "com.myapp.NodeOptics", "new Node(source)");
      assertGeneratedCodeDoesNotContain(compilation, "com.myapp.NodeOptics", "(Node) source");
    }

    @Test
    @DisplayName("should reject a copyConstructor name that does not resolve")
    void shouldRejectUnresolvableCopyConstructor() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(OVERLOADED_BASE, OVERLOADED_NODE, overloadedSpec("Base"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not resolve to a type");
      assertThat(compilation).hadErrorContaining("not resolved against the spec interface's");
      // One problem, one error: a rejected value must not also draw the missing-strategy error.
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should reject a copyConstructor naming a type the source does not extend")
    void shouldRejectUnrelatedCopyConstructor() {
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(OVERLOADED_BASE, OVERLOADED_NODE, overloadedSpec("java.lang.Thread"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not extend or implement");
    }

    @Test
    @DisplayName("should reject a copyConstructor no constructor of the source accepts")
    void shouldRejectCopyConstructorWithNoMatchingConstructor() {
      // Node implements Serializable, so this passes the supertype check - but no Node
      // constructor takes one, so the cast would fail inside the generated file.
      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(OVERLOADED_BASE, OVERLOADED_NODE, overloadedSpec("java.io.Serializable"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("which no constructor of");
      assertThat(compilation).hadErrorContaining("single-argument constructors taking");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should accept a varargs copy constructor")
    void shouldAcceptVarargsCopyConstructor() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.VarBase",
              """
              package com.external;

              public class VarBase { protected String name; }
              """);
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.VarNode",
              """
              package com.external;

              public class VarNode extends VarBase {
                  // The first varargs constructor does not take a VarBase; the second does.
                  public VarNode(String... labels) { this.name = ""; }
                  public VarNode(VarBase... others) {
                      this.name = others.length == 0 ? "" : others[0].name;
                  }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.VarNodeOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.VarNode;

              @ImportOptics
              public interface VarNodeOpticsSpec extends OpticsSpec<VarNode> {

                  @ViaCopyAndSet(copyConstructor = "com.external.VarBase", setter = "setName")
                  Lens<VarNode, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(base, externalClass, specInterface);

      // new VarNode((VarBase) source) is a varargs invocation with one argument.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.myapp.VarNodeOptics", "new VarNode((VarBase) source)");
    }

    @Test
    @DisplayName("should say so when the source has no single-argument constructor at all")
    void shouldReportWhenNoSingleArgumentConstructorExists() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.PairBase",
              """
              package com.external;

              public class PairBase { protected String name; }
              """);
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Pair",
              """
              package com.external;

              public class Pair extends PairBase {
                  private int count;
                  public Pair() {}
                  public Pair(String name, int count) { this.name = name; this.count = count; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PairOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Pair;

              @ImportOptics
              public interface PairOpticsSpec extends OpticsSpec<Pair> {

                  @ViaCopyAndSet(copyConstructor = "com.external.PairBase", setter = "setName")
                  Lens<Pair, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(base, externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("no single-argument constructor it can call");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should not count a constructor the generated class cannot call")
    void shouldRejectWhenTheOnlyMatchingConstructorIsInaccessible() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.ShutBase",
              """
              package com.external;

              public class ShutBase { protected String name; }
              """);
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Shut",
              """
              package com.external;

              public class Shut extends ShutBase {
                  private Shut(ShutBase other) { this.name = other.name; }
                  Shut(Object other) { this.name = String.valueOf(other); }
                  public Shut(Shut other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ShutOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Shut;

              @ImportOptics
              public interface ShutOpticsSpec extends OpticsSpec<Shut> {

                  @ViaCopyAndSet(copyConstructor = "com.external.ShutBase", setter = "setName")
                  Lens<Shut, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(base, externalClass, specInterface);

      // Shut(ShutBase) fits but is private; Shut(Object) is package-private and com.myapp is not
      // that package. Only the public Shut(Shut) is reachable, and it does not take a ShutBase.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("which no constructor of");
      assertThat(compilation).hadErrorContaining("taking [com.external.Shut]");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should count a package-private constructor when generating into that package")
    void shouldAcceptPackagePrivateConstructorInTheSamePackage() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.NearBase",
              """
              package com.external;

              public class NearBase { protected String name; }
              """);
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Near",
              """
              package com.external;

              public class Near extends NearBase {
                  Near(NearBase other) { this.name = other.name; }
                  public Near(Near other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NearOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Near;

              @ImportOptics(targetPackage = "com.external")
              public interface NearOpticsSpec extends OpticsSpec<Near> {

                  @ViaCopyAndSet(copyConstructor = "com.external.NearBase", setter = "setName")
                  Lens<Near, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(base, externalClass, specInterface);

      // The optics class lands in com.external, so the package-private constructor is callable.
      assertThat(compilation).succeeded();
      assertGeneratedCodeContains(
          compilation, "com.external.NearOptics", "new Near((NearBase) source)");
    }

    @Test
    @DisplayName("should reject a copyConstructor the generated class cannot name")
    void shouldRejectInvisibleCopyConstructor() {
      final var packagePrivateBase =
          JavaFileObjects.forSourceString(
              "com.external.Hidden",
              """
              package com.external;

              class Hidden { protected String name; }
              """);
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Visible",
              """
              package com.external;

              public class Visible extends Hidden {
                  public Visible(Hidden other) { this.name = other.name; }
                  public Visible(Visible other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.VisibleOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Visible;

              @ImportOptics
              public interface VisibleOpticsSpec extends OpticsSpec<Visible> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Hidden", setter = "setName")
                  Lens<Visible, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(packagePrivateBase, externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is not public and so cannot be named from");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("should not blame the attribute when the hierarchy cannot be read")
    void shouldNotRejectWhenASupertypeIsUnresolved() {
      // Node's base is absent from the compilation, so the supertype walk reads no supertypes at
      // all. javac reports the missing type; the attribute must not be blamed for it as well.
      final var brokenNode =
          JavaFileObjects.forSourceString(
              "com.external.Broken",
              """
              package com.external;

              public class Broken extends com.external.Absent {
                  public Broken(Object other) {}
                  public String name() { return null; }
                  public void setName(String name) {}
              }
              """);
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BrokenOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Broken;

              @ImportOptics
              public interface BrokenOpticsSpec extends OpticsSpec<Broken> {

                  @ViaCopyAndSet(copyConstructor = "java.lang.Object", setter = "setName")
                  Lens<Broken, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(brokenNode, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      assertThat(compilation).hadErrorContaining("Absent");
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("@InstanceOf Prism Hint")
  class InstanceOfPrismHint {

    @Test
    @DisplayName("should generate prism using instanceof pattern matching")
    void shouldGeneratePrismWithInstanceOf() {
      final var sealedHierarchy =
          JavaFileObjects.forSourceString(
              "com.external.PaymentMethod",
              """
              package com.external;

              public sealed interface PaymentMethod permits CreditCard, BankTransfer {}
              """);

      final var creditCard =
          JavaFileObjects.forSourceString(
              "com.external.CreditCard",
              """
              package com.external;

              public record CreditCard(String number, String expiry) implements PaymentMethod {}
              """);

      final var bankTransfer =
          JavaFileObjects.forSourceString(
              "com.external.BankTransfer",
              """
              package com.external;

              public record BankTransfer(String iban) implements PaymentMethod {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.PaymentMethodOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.PaymentMethod;
              import com.external.CreditCard;
              import com.external.BankTransfer;

              @ImportOptics
              public interface PaymentMethodOptics extends OpticsSpec<PaymentMethod> {

                  @InstanceOf(CreditCard.class)
                  Prism<PaymentMethod, CreditCard> creditCard();

                  @InstanceOf(BankTransfer.class)
                  Prism<PaymentMethod, BankTransfer> bankTransfer();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(sealedHierarchy, creditCard, bankTransfer, specInterface);

      assertThat(compilation).succeeded();

      // Verify instanceof pattern matching is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedInstanceOf = "source instanceof CreditCard";
      assertGeneratedCodeContains(
          compilation, "com.myapp.PaymentMethodOpticsImpl", expectedInstanceOf);
    }

    @Test
    @DisplayName("should reject @InstanceOf with non-subtype target")
    void shouldRejectNonSubtypeInstanceOf() {
      final var baseClass =
          JavaFileObjects.forSourceString(
              "com.external.Animal",
              """
              package com.external;

              public class Animal {}
              """);

      final var unrelatedClass =
          JavaFileObjects.forSourceString(
              "com.external.Plant",
              """
              package com.external;

              public class Plant {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.AnimalOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.InstanceOf;
              import com.external.Animal;
              import com.external.Plant;

              @ImportOptics
              public interface AnimalOptics extends OpticsSpec<Animal> {

                  @InstanceOf(Plant.class)  // Plant is not a subtype of Animal
                  Prism<Animal, Plant> plant();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(baseClass, unrelatedClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("not a subtype");
      // One problem, one error: a rejected hint must not also draw the missing-hint error.
      assertThat(compilation).hadErrorCount(1);
    }
  }

  @Nested
  @DisplayName("@MatchWhen Prism Hint")
  class MatchWhenPrismHint {

    @Test
    @DisplayName("should generate prism using predicate and getter")
    void shouldGeneratePrismWithMatchWhen() {
      final var jsonNode =
          JavaFileObjects.forSourceString(
              "com.external.JsonNode",
              """
              package com.external;

              public abstract class JsonNode {
                  public abstract boolean isArray();
                  public abstract boolean isObject();
                  public ArrayNode asArray() { throw new IllegalStateException(); }
                  public ObjectNode asObject() { throw new IllegalStateException(); }
              }
              """);

      final var arrayNode =
          JavaFileObjects.forSourceString(
              "com.external.ArrayNode",
              """
              package com.external;

              public class ArrayNode extends JsonNode {
                  @Override public boolean isArray() { return true; }
                  @Override public boolean isObject() { return false; }
                  @Override public ArrayNode asArray() { return this; }
              }
              """);

      final var objectNode =
          JavaFileObjects.forSourceString(
              "com.external.ObjectNode",
              """
              package com.external;

              public class ObjectNode extends JsonNode {
                  @Override public boolean isArray() { return false; }
                  @Override public boolean isObject() { return true; }
                  @Override public ObjectNode asObject() { return this; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.JsonNodeOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.MatchWhen;
              import com.external.JsonNode;
              import com.external.ArrayNode;
              import com.external.ObjectNode;

              @ImportOptics
              public interface JsonNodeOptics extends OpticsSpec<JsonNode> {

                  @MatchWhen(predicate = "isArray", getter = "asArray")
                  Prism<JsonNode, ArrayNode> array();

                  @MatchWhen(predicate = "isObject", getter = "asObject")
                  Prism<JsonNode, ObjectNode> object();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(jsonNode, arrayNode, objectNode, specInterface);

      assertThat(compilation).succeeded();

      // Verify predicate/getter pattern is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedPredicateUsage = "source.isArray()";
      assertGeneratedCodeContains(
          compilation, "com.myapp.JsonNodeOpticsImpl", expectedPredicateUsage);
    }
  }

  @Nested
  @DisplayName("@TraverseWith Traversal Hint")
  class TraverseWithHint {

    @Test
    @DisplayName("should generate traversal using explicit reference")
    void shouldGenerateTraversalWithReference() {
      final var team =
          JavaFileObjects.forSourceString(
              "com.external.Team",
              """
              package com.external;

              import java.util.List;

              public record Team(String name, List<String> members) {}
              """);

      // Stub Traversals class with a method that returns the correct type
      // In real usage, this would be a pre-composed traversal
      final var traversalsStub =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.optics.Traversals",
              """
              package org.higherkindedj.optics;

              public final class Traversals {
                  private Traversals() {}
                  public static <A> Traversal<java.util.List<A>, A> list() { return null; }

                  // Pre-composed traversal for Team.members - realistic usage pattern
                  public static Traversal<com.external.Team, String> teamMembers() { return null; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.TeamOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.TraverseWith;
              import com.external.Team;

              @ImportOptics
              public interface TeamOptics extends OpticsSpec<Team> {

                  @TraverseWith("org.higherkindedj.optics.Traversals.teamMembers()")
                  Traversal<Team, String> eachMember();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(team, traversalsStub, specInterface);

      assertThat(compilation).succeeded();

      // Verify traversal reference is used
      // Generated class has "Impl" suffix since interface doesn't end with "Spec"
      final String expectedTraversalRef = "org.higherkindedj.optics.Traversals.teamMembers()";
      assertGeneratedCodeContains(compilation, "com.myapp.TeamOpticsImpl", expectedTraversalRef);
    }
  }

  @Nested
  @DisplayName("Error Cases")
  class ErrorCases {

    @Test
    @DisplayName("should reject lens method without copy strategy annotation")
    void shouldRejectLensWithoutCopyStrategy() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Simple",
              """
              package com.external;

              public record Simple(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SimpleOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Simple;

              @ImportOptics
              public interface SimpleOptics extends OpticsSpec<Simple> {

                  // Missing copy strategy annotation
                  Lens<Simple, String> value();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("carries no copy strategy annotation");
    }

    @Test
    @DisplayName("should reject prism method without prism hint annotation")
    void shouldRejectPrismWithoutHint() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base {}
              """);

      final var subclass =
          JavaFileObjects.forSourceString(
              "com.external.Sub",
              """
              package com.external;

              public class Sub extends Base {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BaseOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import com.external.Base;
              import com.external.Sub;

              @ImportOptics
              public interface BaseOptics extends OpticsSpec<Base> {

                  // Missing prism hint annotation
                  Prism<Base, Sub> sub();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(externalClass, subclass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("requires a prism hint annotation");
    }

    @Test
    @DisplayName("should reject method with invalid return type")
    void shouldRejectInvalidReturnType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Data",
              """
              package com.external;

              public record Data(String value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.DataOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Data;

              @ImportOptics
              public interface DataOptics extends OpticsSpec<Data> {

                  // Invalid return type - not an optic
                  @ViaBuilder
                  String value();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must return Lens, Prism, Traversal");
    }

    @Test
    @DisplayName("should reject method with parameters")
    void shouldRejectMethodWithParameters() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Item",
              """
              package com.external;

              public record Item(String name) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ItemOptics",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaBuilder;
              import com.external.Item;

              @ImportOptics
              public interface ItemOptics extends OpticsSpec<Item> {

                  // Invalid - optic methods should have no parameters
                  @ViaBuilder
                  Lens<Item, String> name(String unused);
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must have no parameters");
    }

    @Test
    @DisplayName("should report a type variable source type rather than crashing")
    void shouldRejectTypeVariableSourceType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;

              public class Box {
                  private String v;
                  public String getV() { return v; }
                  public void setV(String v) { this.v = v; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BoxOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Box;

              @ImportOptics
              public interface BoxOpticsSpec<S extends Box> extends OpticsSpec<S> {

                  @ViaCopyAndSet(setter = "setV")
                  Lens<S, String> v();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'BoxOpticsSpec' declares OpticsSpec<S>, which is a type variable.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name the type the optics are for as the type argument, here" + " OpticsSpec<Box>.");
    }

    @Test
    @DisplayName("should reject an unbounded type variable without suggesting Object")
    void shouldRejectUnboundedTypeVariableSourceType() {
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.UnboundedOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface UnboundedOpticsSpec<S> extends OpticsSpec<S> {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'UnboundedOpticsSpec' declares OpticsSpec<S>, which is a type variable.");
      assertThat(compilation)
          .hadErrorContaining("Name the type the optics are for as the type argument.");
    }

    @Test
    @DisplayName("should reject an array source type by naming what it is not")
    void shouldRejectArraySourceType() {
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ArrayOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface ArrayOpticsSpec extends OpticsSpec<String[]> {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'ArrayOpticsSpec' declares OpticsSpec<java.lang.String[]>, which is not a class or"
                  + " interface.");
    }
  }
}

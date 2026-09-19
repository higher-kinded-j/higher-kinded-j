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
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
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

    @Test
    @DisplayName("a wither returning the source type raw is refused")
    void witherReturningTheSourceTypeRawIsRefused() {
      final var external =
          JavaFileObjects.forSourceString(
              "com.external.Draft",
              """
              package com.external;

              @SuppressWarnings("rawtypes")
              public final class Draft<T> {
                  private final String id;
                  public Draft(String id) { this.id = id; }
                  public String id() { return id; }
                  public Draft withId(String id) { return new Draft<>(id); }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.DraftOpticsSpec",
              """
              package com.myapp;

              import com.external.Draft;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface DraftOpticsSpec<T> extends OpticsSpec<Draft<T>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Draft<T>, String> id();
              }
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(external, spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Draft', not the source type 'Draft<T>'");
    }

    @Test
    @DisplayName("a wither inherited from a generic supertype, returning it, is refused")
    void witherReturningAGenericSupertypeIsRefused() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<T> {
                  protected final String id;
                  public Base(String id) { this.id = id; }
                  public String id() { return id; }
                  public Base<T> withId(String id) { return new Base<>(id); }
              }
              """);
      final var sub =
          JavaFileObjects.forSourceString(
              "com.external.Sub",
              """
              package com.external;

              public class Sub extends Base<String> {
                  public Sub(String id) { super(id); }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.SubOpticsSpec",
              """
              package com.myapp;

              import com.external.Sub;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface SubOpticsSpec extends OpticsSpec<Sub> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Sub, String> id();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, sub, spec);

      assertThat(compilation).failed();
      // Read on Sub, Base<T>'s return is Base<String>: the instantiation Sub's extends clause
      // names.
      assertThat(compilation)
          .hadErrorContaining("'withId(String)' returns 'Base<String>', not the source type 'Sub'");
    }

    @Test
    @DisplayName("a wither is read under the arguments the spec names")
    void witherIsReadUnderTheArgumentsTheSpecNames() {
      // withId returns Renamed<String>, which is the source type here, though not under a spec
      // over Renamed<T>.
      final var external =
          JavaFileObjects.forSourceString(
              "com.external.Renamed",
              """
              package com.external;

              public final class Renamed<T> {
                  private final String id;
                  public Renamed(String id) { this.id = id; }
                  public String id() { return id; }
                  public Renamed<String> withId(String id) { return new Renamed<>(id); }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.RenamedOpticsSpec",
              """
              package com.myapp;

              import com.external.Renamed;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface RenamedOpticsSpec extends OpticsSpec<Renamed<String>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Renamed<String>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(external, spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.RenamedOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("only a wither the generated call can reach answers for the return")
    void onlyAWitherTheGeneratedCallCanReachAnswersForTheReturn() {
      // Each decoy returns the source type, and none is the method the call binds: a String binds
      // withId(String) ahead of the static withId(Object), the two-parameter one takes no single
      // argument, and the private one cannot be called at all.
      final var external =
          JavaFileObjects.forSourceString(
              "com.external.Renamed",
              """
              package com.external;

              public final class Renamed<T> {
                  private final String id;
                  public Renamed(String id) { this.id = id; }
                  public String id() { return id; }
                  public Renamed<Integer> withId(String id) { return new Renamed<>(id); }
                  public Renamed<T> withId(String id, int copies) { return new Renamed<>(id); }
                  public static Renamed<String> withId(Object id) {
                      return new Renamed<>(String.valueOf(id));
                  }
                  private Renamed<String> withId(Integer id) {
                      return new Renamed<>(String.valueOf(id));
                  }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.RenamedOpticsSpec",
              """
              package com.myapp;

              import com.external.Renamed;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface RenamedOpticsSpec extends OpticsSpec<Renamed<String>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Renamed<String>, String> id();
              }
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(external, spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Renamed<Integer>', not the source type"
                  + " 'Renamed<String>'");
    }

    @Test
    @DisplayName("a retag wither is inferred back to the source type")
    void retagWitherIsInferredBackToTheSourceType() {
      final var external =
          JavaFileObjects.forSourceString(
              "com.external.Draft",
              """
              package com.external;

              public final class Draft<T> {
                  private final String id;
                  public Draft(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U> Draft<U> withId(String id) { return new Draft<>(id); }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.DraftOpticsSpec",
              """
              package com.myapp;

              import com.external.Draft;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface DraftOpticsSpec<T> extends OpticsSpec<Draft<T>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Draft<T>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(external, spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.DraftOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a retag wither whose bound names another of its parameters is inferred back")
    void retagWitherWhoseBoundNamesAnotherOfItsParametersIsInferredBack() {
      // Q's bound is P, which stands for Number here, so Integer answers it as it does at the call.
      final var external =
          JavaFileObjects.forSourceString(
              "com.external.Draft",
              """
              package com.external;

              public final class Draft<A, B> {
                  private final String id;
                  public Draft(String id) { this.id = id; }
                  public String id() { return id; }
                  public <P, Q extends P> Draft<P, Q> withId(String id) { return new Draft<>(id); }
              }
              """);
      final var spec =
          JavaFileObjects.forSourceString(
              "com.myapp.DraftOpticsSpec",
              """
              package com.myapp;

              import com.external.Draft;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface DraftOpticsSpec extends OpticsSpec<Draft<Number, Integer>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Draft<Number, Integer>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(external, spec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.DraftOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a retag's bounds are read under the spec's instantiation")
    void retagBoundsAreReadUnderTheSpecInstantiation() {
      // Tier's U is bounded by the class's own T, which stands for Number here; Rank's bound is an
      // intersection, each part of which Integer has to satisfy.
      final var tier =
          JavaFileObjects.forSourceString(
              "com.external.Tier",
              """
              package com.external;

              public final class Tier<T> {
                  private final String id;
                  public Tier(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U extends T> Tier<U> withId(String id) { return new Tier<>(id); }
              }
              """);
      final var rank =
          JavaFileObjects.forSourceString(
              "com.external.Rank",
              """
              package com.external;

              public final class Rank<T> {
                  private final String id;
                  public Rank(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U extends Number & Comparable<U>> Rank<U> withId(String id) { return new Rank<>(id); }
              }
              """);
      final var tierSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.TierOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface TierOpticsSpec extends OpticsSpec<Tier<Number>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Tier<Number>, String> id();
              }
              """);
      final var rankSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.RankOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface RankOpticsSpec extends OpticsSpec<Rank<Integer>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Rank<Integer>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(tier, rank, tierSpec, rankSpec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.TierOptics", "source.withId(newValue)");
      assertGeneratedCodeContains(compilation, "com.myapp.RankOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a retag is rebuilt through an array, an enclosing type and a repeated parameter")
    void retagIsRebuiltThroughAnArrayAnEnclosingTypeAndARepeatedParameter() {
      // Slot's U also stands inside U[]; Holder's U stands in the enclosing type as well; Twin's U
      // names both arguments, which the spec makes the same.
      final var slot =
          JavaFileObjects.forSourceString(
              "com.external.Slot",
              """
              package com.external;

              public final class Slot<A, B> {
                  private final String id;
                  public Slot(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U> Slot<U, U[]> withId(String id) { return new Slot<>(id); }
              }
              """);
      final var holder =
          JavaFileObjects.forSourceString(
              "com.external.Holder",
              """
              package com.external;

              public class Holder<X> {
                  public final class Tag<P> {
                      private final String id;
                      public Tag(String id) { this.id = id; }
                      public String id() { return id; }
                      public <U> Holder<U>.Tag<U> withId(String id) {
                          return new Holder<U>().new Tag<U>(id);
                      }
                  }
              }
              """);
      final var twin =
          JavaFileObjects.forSourceString(
              "com.external.Twin",
              """
              package com.external;

              public final class Twin<A, B> {
                  private final String id;
                  public Twin(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U> Twin<U, U> withId(String id) { return new Twin<>(id); }
              }
              """);
      final var slotSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.SlotOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface SlotOpticsSpec extends OpticsSpec<Slot<String, String[]>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Slot<String, String[]>, String> id();
              }
              """);
      final var holderSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.HolderOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface HolderOpticsSpec extends OpticsSpec<Holder<String>.Tag<String>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Holder<String>.Tag<String>, String> id();
              }
              """);
      final var twinSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.TwinOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface TwinOpticsSpec extends OpticsSpec<Twin<String, String>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Twin<String, String>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(slot, holder, twin, slotSpec, holderSpec, twinSpec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.SlotOptics", "source.withId(newValue)");
      assertGeneratedCodeContains(compilation, "com.myapp.HolderOptics", "source.withId(newValue)");
      assertGeneratedCodeContains(compilation, "com.myapp.TwinOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a wildcard in the spec's source type binds no retag parameter")
    void wildcardInTheSpecSourceTypeBindsNoRetagParameter() {
      // U is bound where the source names String, not where it names a wildcard, as javac infers
      // it.
      final var twin =
          JavaFileObjects.forSourceString(
              "com.external.Twin",
              """
              package com.external;

              public final class Twin<A, B> {
                  private final String id;
                  public Twin(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U> Twin<U, U> withId(String id) { return new Twin<>(id); }
              }
              """);
      final var twinSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.TwinOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface TwinOpticsSpec extends OpticsSpec<Twin<?, String>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Twin<?, String>, String> id();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(twin, twinSpec);

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.TwinOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a retag the source type's wildcards cannot satisfy is refused")
    void retagTheSourceTypeWildcardsCannotSatisfyIsRefused() {
      // Box's U stands only for a wildcard, so nothing binds it. Pair's bound is read on the
      // captured receiver, where B is a type of its own that String need not be comparable with.
      final var box =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;

              public final class Box<T> {
                  private final String id;
                  public Box(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U extends Comparable<? super U>> Box<U> withId(String id) { return new Box<>(id); }
              }
              """);
      final var pair =
          JavaFileObjects.forSourceString(
              "com.external.Pair",
              """
              package com.external;

              public final class Pair<A, B> {
                  private final String id;
                  public Pair(String id) { this.id = id; }
                  public String id() { return id; }
                  public <U extends Comparable<? super B>> Pair<U, B> withId(String id) { return new Pair<>(id); }
              }
              """);
      final var boxSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.BoxOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface BoxOpticsSpec extends OpticsSpec<Box<? extends Number>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Box<? extends Number>, String> id();
              }
              """);
      final var pairSpec =
          JavaFileObjects.forSourceString(
              "com.myapp.PairOpticsSpec",
              """
              package com.myapp;

              import com.external.*;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface PairOpticsSpec extends OpticsSpec<Pair<String, ?>> {
                  @Wither(value = "withId", getter = "id")
                  Lens<Pair<String, ?>, String> id();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(box, pair, boxSpec, pairSpec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'withId(String)' returns 'Box<U>', not the source type 'Box<? extends Number>'");
      assertThat(compilation).hadErrorContaining("not the source type 'Pair<String, ?>'");
    }
  }

  @Nested
  @DisplayName("@Wither Call Binding")
  class WitherCallBinding {

    /** A class in {@code com.external}, the package every spec here imports from. */
    private static JavaFileObject external(String simpleName, String body) {
      return JavaFileObjects.forSourceString(
          "com.external." + simpleName, "package com.external;\n\n" + body);
    }

    /**
     * A spec interface in {@code com.myapp}, with the external classes and annotations in scope.
     */
    private static JavaFileObject spec(String simpleName, String body) {
      return JavaFileObjects.forSourceString(
          "com.myapp." + simpleName,
          """
          package com.myapp;

          import com.external.*;
          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.*;

          """
              + body);
    }

    /** Compiles under the lint a call bound to the wrong method would trip, as an error. */
    private static Compilation compile(JavaFileObject... sources) {
      return javac()
          .withProcessors(new ImportOpticsProcessor())
          .withOptions("-Xlint:unchecked,rawtypes,static", "-Werror")
          .compile(sources);
    }

    @Test
    @DisplayName("each lens is checked against the overload its focus binds")
    void eachLensIsCheckedAgainstTheOverloadItsFocusBinds() {
      // In each class one overload returns the source type, and it is not the one the call binds:
      // an Integer binds withN(Integer), which needs no unboxing, and a String binds
      // withId(String), the most specific overload that takes one.
      var compilation =
          compile(
              external(
                  "Boxed",
                  """
                  public final class Boxed {
                      private final int n;
                      public Boxed(int n) { this.n = n; }
                      public int n() { return n; }
                      public Boxed withN(int n) { return new Boxed(n); }
                      public Object withN(Integer n) { return this; }
                  }
                  """),
              external(
                  "RawDraft",
                  """
                  @SuppressWarnings("rawtypes")
                  public final class RawDraft<T> {
                      private final String id;
                      public RawDraft(String id) { this.id = id; }
                      public String id() { return id; }
                      public RawDraft withId(String id) { return new RawDraft<>(id); }
                      public RawDraft<T> withId(Object id) {
                          return new RawDraft<>(String.valueOf(id));
                      }
                  }
                  """),
              external(
                  "Tagged",
                  """
                  public final class Tagged<T> {
                      private final String id;
                      public Tagged(String id) { this.id = id; }
                      public String id() { return id; }
                      public Tagged<String> withId(String id) { return new Tagged<>(id); }
                      public Tagged<T> withId(Integer id) {
                          return new Tagged<>(String.valueOf(id));
                      }
                  }
                  """),
              spec(
                  "BoxedOpticsSpec",
                  """
                  @ImportOptics
                  public interface BoxedOpticsSpec extends OpticsSpec<Boxed> {
                      @Wither(value = "withN", getter = "n")
                      Lens<Boxed, Integer> n();
                  }
                  """),
              spec(
                  "RawDraftOpticsSpec",
                  """
                  @ImportOptics
                  public interface RawDraftOpticsSpec<T> extends OpticsSpec<RawDraft<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<RawDraft<T>, String> id();
                  }
                  """),
              spec(
                  "TaggedOpticsSpec",
                  """
                  @ImportOptics
                  public interface TaggedOpticsSpec<T> extends OpticsSpec<Tagged<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Tagged<T>, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withN(Integer)' returns 'Object', not the source type 'Boxed'. The"
                  + " generated lens sets through 'source.withN(newValue)' with the new value"
                  + " typed 'Integer', which binds 'withN(Integer)', and hands its result back as"
                  + " the source type 'Boxed', which 'Object' is not.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'RawDraft', not the source type 'RawDraft<T>'");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Tagged<String>', not the source type"
                  + " 'Tagged<T>'");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("the most specific overload is the one checked")
    void theMostSpecificOverloadIsTheOneChecked() {
      // Counter's Integer reaches no reference overload, so it binds the most specific primitive
      // one, withN(int) over withN(long). Named's String binds withId(String) over the
      // CharSequence and Object overloads. Shape inherits withId(String) from Sized and from
      // Tinted, and the call takes the one with the most specific return.
      var compilation =
          compile(
              external(
                  "Counter",
                  """
                  public final class Counter {
                      private final int n;
                      public Counter(int n) { this.n = n; }
                      public int n() { return n; }
                      public Object withN(long n) { return this; }
                      public Counter withN(int n) { return new Counter(n); }
                  }
                  """),
              external(
                  "Named",
                  """
                  public final class Named {
                      private final String id;
                      public Named(String id) { this.id = id; }
                      public String id() { return id; }
                      public Object withId(Object id) { return this; }
                      public Object withId(CharSequence id) { return this; }
                      public Named withId(String id) { return new Named(id); }
                  }
                  """),
              external("Sized", "public interface Sized { Shape withId(String id); }\n"),
              external("Tinted", "public interface Tinted { Object withId(String id); }\n"),
              external("Shape", "public interface Shape extends Sized, Tinted { String id(); }\n"),
              spec(
                  "CounterOpticsSpec",
                  """
                  @ImportOptics
                  public interface CounterOpticsSpec extends OpticsSpec<Counter> {
                      @Wither(value = "withN", getter = "n")
                      Lens<Counter, Integer> n();
                  }
                  """),
              spec(
                  "NamedOpticsSpec",
                  """
                  @ImportOptics
                  public interface NamedOpticsSpec extends OpticsSpec<Named> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Named, String> id();
                  }
                  """),
              spec(
                  "ShapeOpticsSpec",
                  """
                  @ImportOptics
                  public interface ShapeOpticsSpec extends OpticsSpec<Shape> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Shape, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(compilation, "com.myapp.CounterOptics", "source.withN(newValue)");
      assertGeneratedCodeContains(compilation, "com.myapp.ShapeOptics", "source.withId(newValue)");
    }

    @Test
    @DisplayName("a call that rests on inference is left to javac")
    void callThatRestsOnInferenceIsLeftToJavac() {
      // Label's parameter is one of its own type variables, and Tags' String reaches withFirst
      // only as the element of its array. The one method each call might bind returns the source
      // type, so the check passes it, and javac settles the call.
      var compilation =
          compile(
              external(
                  "Label",
                  """
                  public final class Label {
                      private final String text;
                      public Label(String text) { this.text = text; }
                      public String text() { return text; }
                      public <V extends CharSequence> Label withText(V text) {
                          return new Label(text.toString());
                      }
                  }
                  """),
              external(
                  "Tags",
                  """
                  public final class Tags {
                      private final String first;
                      public Tags(String first) { this.first = first; }
                      public String first() { return first; }
                      public Tags withFirst(String... tags) { return new Tags(tags[0]); }
                  }
                  """),
              spec(
                  "LabelOpticsSpec",
                  """
                  @ImportOptics
                  public interface LabelOpticsSpec extends OpticsSpec<Label> {
                      @Wither(value = "withText", getter = "text")
                      Lens<Label, String> text();
                  }
                  """),
              spec(
                  "TagsOpticsSpec",
                  """
                  @ImportOptics
                  public interface TagsOpticsSpec extends OpticsSpec<Tags> {
                      @Wither(value = "withFirst", getter = "first")
                      Lens<Tags, String> first();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a call that rests on inference is refused when nothing it might bind fits")
    void callThatRestsOnInferenceIsRefusedWhenNothingItMightBindFits() {
      var compilation =
          compile(
              external(
                  "Note",
                  """
                  public final class Note {
                      private final String text;
                      public Note(String text) { this.text = text; }
                      public String text() { return text; }
                      public <V extends CharSequence> Object withText(V text) { return this; }
                  }
                  """),
              spec(
                  "NoteOpticsSpec",
                  """
                  @ImportOptics
                  public interface NoteOpticsSpec extends OpticsSpec<Note> {
                      @Wither(value = "withText", getter = "text")
                      Lens<Note, String> text();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withText(V)' returns 'Object', not the source type 'Note'. The generated"
                  + " lens sets through 'withText(V)' and hands its result back as the source"
                  + " type 'Note', which 'Object' is not.");
    }

    @Test
    @DisplayName("a wither the generated class shares a package with is called")
    void witherTheGeneratedClassSharesAPackageWithIsCalled() {
      // Declared without a modifier, and callable here because the optics are generated into the
      // package that declares it.
      var compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.myapp.Parcel",
                  """
                  package com.myapp;

                  public final class Parcel {
                      private final String id;
                      public Parcel(String id) { this.id = id; }
                      public String id() { return id; }
                      Parcel withId(String id) { return new Parcel(id); }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.myapp.ParcelOpticsSpec",
                  """
                  package com.myapp;

                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.annotations.ImportOptics;
                  import org.higherkindedj.optics.annotations.OpticsSpec;
                  import org.higherkindedj.optics.annotations.Wither;

                  @ImportOptics
                  public interface ParcelOpticsSpec extends OpticsSpec<Parcel> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Parcel, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a wildcard focus reaching no one-parameter method is left to javac")
    void wildcardFocusReachingNoOneParameterMethodIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Pair2",
                  """
                  public final class Pair2 {
                      private final String id;
                      public Pair2(String id) { this.id = id; }
                      public String id() { return id; }
                      public Pair2 withId(String id, int copies) { return new Pair2(id); }
                  }
                  """),
              spec(
                  "Pair2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Pair2OpticsSpec extends OpticsSpec<Pair2> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Pair2, ?> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"));
    }

    @Test
    @DisplayName("a static wither under a wildcard focus is refused, naming what it sets through")
    void staticWitherUnderAWildcardFocusIsRefused() {
      // Which method such a call binds is javac's to settle, so the refusal names the method
      // rather than the value that chose it.
      var compilation =
          compile(
              external(
                  "Seal",
                  """
                  public final class Seal {
                      private final String id;
                      public Seal(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Seal withId(String id) { return new Seal(id); }
                  }
                  """),
              spec(
                  "SealOpticsSpec",
                  """
                  @ImportOptics
                  public interface SealOpticsSpec extends OpticsSpec<Seal> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Seal, ? extends CharSequence> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' is static, so the generated lens cannot rebuild a 'Seal'"
                  + " through it. The generated lens sets through 'withId(String)', and a static"
                  + " method never reads the 'Seal' it is called on.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a wildcard focus is left to javac, which infers it from the getter")
    void wildcardFocusIsLeftToJavac() {
      // A lens whose focus is a wildcard is inferred from its getter as much as from the
      // wildcard: Memo's is Lens<Memo, String>, not the CharSequence its bound names, so
      // withA(String) is the method the call binds. Reading the bound as the argument would
      // refuse every one of these, and each compiles.
      var compilation =
          compile(
              external(
                  "Memo",
                  """
                  public final class Memo {
                      private final String a;
                      private final String b;
                      private final String c;
                      public Memo(String a, String b, String c) {
                          this.a = a;
                          this.b = b;
                          this.c = c;
                      }
                      public String a() { return a; }
                      public String b() { return b; }
                      public String c() { return c; }
                      public Memo withA(String a) { return new Memo(a, b, c); }
                      public Object withA(CharSequence a) { return this; }
                      public Memo withB(String b) { return new Memo(a, b, c); }
                      public Memo withC(String c) { return new Memo(a, b, c); }
                      public Object withC(Object c) { return this; }
                  }
                  """),
              spec(
                  "MemoOpticsSpec",
                  """
                  @ImportOptics
                  public interface MemoOpticsSpec extends OpticsSpec<Memo> {
                      @Wither(value = "withA", getter = "a")
                      Lens<Memo, ? extends CharSequence> a();

                      @Wither(value = "withB", getter = "b")
                      Lens<Memo, ? super String> b();

                      @Wither(value = "withC", getter = "c")
                      Lens<Memo, ?> c();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a wither the source type inherits from a package-private class is called")
    void witherInheritedFromAPackagePrivateClassIsCalled() {
      // The call names no type but Ledger's own, so a public method it inherits is one it can
      // call, wherever that method was declared.
      var compilation =
          compile(
              external(
                  "Ledger",
                  """
                  public final class Ledger extends LedgerBase<String> {
                      public Ledger(String id) { super(id); }
                  }

                  class LedgerBase<T> {
                      private final String id;
                      LedgerBase(String id) { this.id = id; }
                      public String id() { return id; }
                      public Ledger withId(String id) { return new Ledger(id); }
                  }
                  """),
              spec(
                  "LedgerOpticsSpec",
                  """
                  @ImportOptics
                  public interface LedgerOpticsSpec extends OpticsSpec<Ledger> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ledger, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("an overload that loses the choice does not answer for the call")
    void overloadThatLosesTheChoiceDoesNotAnswerForTheCall() {
      // withId(String) is more specific than the generic overload, so the call binds it and its
      // return is the one checked, though the generic one hands the source type back.
      var compilation =
          compile(
              external(
                  "Draft2",
                  """
                  public final class Draft2 {
                      private final String id;
                      public Draft2(String id) { this.id = id; }
                      public String id() { return id; }
                      public <V extends CharSequence> Draft2 withId(V id) {
                          return new Draft2(id.toString());
                      }
                      public Object withId(String id) { return this; }
                  }
                  """),
              spec(
                  "Draft2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Draft2OpticsSpec extends OpticsSpec<Draft2> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Draft2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String)' returns 'Object', not the source type 'Draft2'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a static method the call might bind is refused too")
    void staticMethodTheCallMightBindIsRefusedToo() {
      // A variable-arity call is left to javac, but whatever it settles on has to read the value
      // it is called on, and a static method never does.
      var compilation =
          compile(
              external(
                  "Batch",
                  """
                  public final class Batch {
                      private final String id;
                      public Batch(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Batch withId(String... ids) { return new Batch(ids[0]); }
                  }
                  """),
              spec(
                  "BatchOpticsSpec",
                  """
                  @ImportOptics
                  public interface BatchOpticsSpec extends OpticsSpec<Batch> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Batch, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(String...)' is static, so the generated lens cannot rebuild a"
                  + " 'Batch' through it. The generated lens sets through"
                  + " 'source.withId(newValue)'");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a source type that does not resolve is left to javac")
    void sourceTypeThatDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
              spec(
                  "GhostOpticsSpec",
                  """
                  @ImportOptics
                  public interface GhostOpticsSpec extends OpticsSpec<com.external.Ghost> {
                      @Wither(value = "withId", getter = "id")
                      Lens<com.external.Ghost, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"));
    }

    @Test
    @DisplayName("a focus no method of the name takes is refused, listing them")
    void focusNoMethodOfTheNameTakesIsRefusedListingThem() {
      // A three-parameter variable-arity method needs two arguments before its array, so it takes
      // no single one. Cell's parameter is its T, which the wildcard leaves unknown, so its remedy
      // is the source type; Account's T is the spec's own, and its remedy stays the focus.
      var compilation =
          compile(
              external(
                  "Account",
                  """
                  public final class Account<T> {
                      private final String id;
                      public Account(String id) { this.id = id; }
                      public String id() { return id; }
                      public Account<T> withId(Integer id) {
                          return new Account<>(String.valueOf(id));
                      }
                      public Account<T> withId(String id, int copies) { return new Account<>(id); }
                      public Account<T> withId(String first, String second, String... rest) {
                          return new Account<>(first);
                      }
                  }
                  """),
              external(
                  "Cell",
                  """
                  public final class Cell<T> {
                      private final T value;
                      public Cell(T value) { this.value = value; }
                      public T value() { return value; }
                      public Cell<T> withValue(T value) { return new Cell<>(value); }
                  }
                  """),
              spec(
                  "AccountOpticsSpec",
                  """
                  @ImportOptics
                  public interface AccountOpticsSpec<T> extends OpticsSpec<Account<T>> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Account<T>, String> id();
                  }
                  """),
              spec(
                  "CellOpticsSpec",
                  """
                  @ImportOptics
                  public interface CellOpticsSpec extends OpticsSpec<Cell<?>> {
                      @Wither(value = "withValue", getter = "value")
                      Lens<Cell<?>, Object> value();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: No method 'withId' of 'Account<T>' takes the lens's focus type 'String'."
                  + " The generated lens sets through 'source.withId(newValue)' with the new value"
                  + " typed 'String'.");
      assertThat(compilation).hadErrorContaining("withId(String, String, String...)");
      assertThat(compilation)
          .hadErrorContaining(
              "Name a wither that takes the value the getter reads, or point 'getter' at an"
                  + " accessor one of them takes and declare the focus as its type; otherwise"
                  + " rebuild 'Account<T>' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: No method 'withValue' of 'Cell<?>' takes the lens's focus type 'Object'."
                  + " The generated lens sets through 'source.withValue(newValue)' with the new"
                  + " value typed 'Object'. Found on 'Cell<?>': [withValue(?)]. A parameter a"
                  + " wildcard of 'Cell<?>' stands in takes no value at all, since the type it"
                  + " stands for is unknown. Declare the spec over the type each wildcard stands"
                  + " for, or rebuild 'Cell<?>' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a call javac cannot choose for is refused, and the focus that chooses compiles")
    void callJavacCannotChooseForIsRefusedAndTheFocusThatChoosesCompiles() {
      final var ident =
          external(
              "Ident",
              """
              public final class Ident {
                  private final String id;
                  public Ident(String id) { this.id = id; }
                  public String id() { return id; }
                  public Ident withId(java.io.Serializable id) { return new Ident(id.toString()); }
                  public Ident withId(CharSequence id) { return new Ident(id.toString()); }
              }
              """);

      var ambiguous =
          compile(
              ident,
              spec(
                  "IdentOpticsSpec",
                  """
                  @ImportOptics
                  public interface IdentOpticsSpec extends OpticsSpec<Ident> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ident, String> id();
                  }
                  """));

      assertThat(ambiguous).failed();
      assertThat(ambiguous)
          .hadErrorContaining(
              "@Wither: The generated call to 'withId' cannot choose between"
                  + " 'withId(Serializable)' and 'withId(CharSequence)'. The generated lens sets"
                  + " through 'source.withId(newValue)' with the new value typed 'String', which"
                  + " each of them takes, with no parameter more specific than every other."
                  + " Declare the lens's focus as the parameter type of the one you mean");
      assertThat(ambiguous).hadErrorCount(1);

      // The fix line, followed: a CharSequence focus reaches only withId(CharSequence).
      var chosen =
          compile(
              ident,
              spec(
                  "IdentOpticsSpec",
                  """
                  @ImportOptics
                  public interface IdentOpticsSpec extends OpticsSpec<Ident> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ident, CharSequence> id();
                  }
                  """));

      assertThat(chosen).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a static method the call binds is refused")
    void staticMethodTheCallBindsIsRefused() {
      // A String binds the static withId(CharSequence) ahead of the instance withId(Object).
      var compilation =
          compile(
              external(
                  "Stamp",
                  """
                  public final class Stamp {
                      private final String id;
                      public Stamp(String id) { this.id = id; }
                      public String id() { return id; }
                      public static Stamp withId(CharSequence id) { return new Stamp(id.toString()); }
                      public Object withId(Object id) { return this; }
                  }
                  """),
              spec(
                  "StampOpticsSpec",
                  """
                  @ImportOptics
                  public interface StampOpticsSpec extends OpticsSpec<Stamp> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Stamp, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'withId(CharSequence)' is static, so the generated lens cannot rebuild a"
                  + " 'Stamp' through it. The generated lens sets through 'source.withId(newValue)'"
                  + " with the new value typed 'String', which binds 'withId(CharSequence)', and a"
                  + " static method never reads the 'Stamp' it is called on. Declare the lens's"
                  + " focus as the parameter type of an instance overload, name an instance"
                  + " wither, or rebuild 'Stamp' with @ViaBuilder, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a wither name the source type does not have is refused, offering its withers")
    void witherNameTheSourceTypeDoesNotHaveIsRefusedOfferingItsWithers() {
      // Only a one-parameter instance method that hands Plain back is offered, with the
      // parameter that says which value it takes: not the static one, the two-parameter one or
      // the one returning a String.
      var compilation =
          compile(
              external(
                  "Plain",
                  """
                  public final class Plain {
                      private final String id;
                      private final String name;
                      public Plain(String id, String name) {
                          this.id = id;
                          this.name = name;
                      }
                      public String id() { return id; }
                      public String name() { return name; }
                      public Plain withName(String name) { return new Plain(id, name); }
                      public Plain withId(String id) { return new Plain(id, name); }
                      public Plain withId(Integer id) { return new Plain(String.valueOf(id), name); }
                      public static Plain withDefaults(String id) { return new Plain(id, ""); }
                      public Plain withBoth(String id, String name) { return new Plain(id, name); }
                      public String withSuffix(String suffix) { return id + suffix; }
                  }
                  """),
              external(
                  "Bare",
                  """
                  public final class Bare {
                      private final String id;
                      public Bare(String id) { this.id = id; }
                      public String id() { return id; }
                  }
                  """),
              external(
                  "Hidden",
                  """
                  public final class Hidden {
                      private final String id;
                      public Hidden(String id) { this.id = id; }
                      public String id() { return id; }
                      Hidden withId(String id) { return new Hidden(id); }
                      public Hidden withName(String name) { return new Hidden(name); }
                  }
                  """),
              spec(
                  "PlainOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlainOpticsSpec extends OpticsSpec<Plain> {
                      @Wither(value = "withIdd", getter = "id")
                      Lens<Plain, String> id();
                  }
                  """),
              spec(
                  "PlainNameOpticsSpec",
                  """
                  @ImportOptics
                  public interface PlainNameOpticsSpec extends OpticsSpec<Plain> {
                      @Wither(value = "withIdentifier", getter = "name")
                      Lens<Plain, String> name();
                  }
                  """),
              spec(
                  "BareOpticsSpec",
                  """
                  @ImportOptics
                  public interface BareOpticsSpec extends OpticsSpec<Bare> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Bare, String> id();
                  }
                  """),
              spec(
                  "HiddenOpticsSpec",
                  """
                  @ImportOptics
                  public interface HiddenOpticsSpec extends OpticsSpec<Hidden> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Hidden, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Plain' has no method 'withIdd' for the generated lens to call. The"
                  + " generated lens sets through 'source.withIdd(newValue)', so it needs a method"
                  + " of that name on 'Plain', declared or inherited. Did you mean 'withId'?"
                  + " Withers found on 'Plain': [withId(Integer), withId(String),"
                  + " withName(String)]. Name one of the withers found on 'Plain' that takes the"
                  + " value 'String' the getter reads, or rebuild 'Plain' with @ViaBuilder,"
                  + " @ViaConstructor or @ViaCopyAndSet.");
      // Too far from any wither to be offered as a misspelling of one.
      Assertions.assertThat(compilation.errors())
          .filteredOn(error -> error.getMessage(null).contains("'withIdentifier'"))
          .singleElement()
          .satisfies(
              error ->
                  Assertions.assertThat(error.getMessage(null))
                      .contains(
                          "Withers found on 'Plain': [withId(Integer), withId(String),"
                              + " withName(String)].")
                      .doesNotContain("Did you mean"));
      // Declared, but not where the generated class can call it, and the message says so.
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Hidden' has no method 'withId' for the generated lens to call. The"
                  + " generated lens sets through 'source.withId(newValue)', so it needs a method"
                  + " of that name the generated class in 'com.myapp' can call, and 'withId' is"
                  + " declared where it cannot. Withers found on 'Hidden': [withName(String)].");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Bare' has no method 'withId' for the generated lens to call. The"
                  + " generated lens sets through 'source.withId(newValue)', so it needs a method"
                  + " of that name on 'Bare', declared or inherited. No one-parameter instance"
                  + " method of 'Bare' hands it back. Rebuild 'Bare' with @ViaBuilder,"
                  + " @ViaConstructor or @ViaCopyAndSet.");
      // Reported at the spec, so javac never meets the call in a generated file.
      assertThat(compilation).hadErrorCount(4);
    }
  }

  @Nested
  @DisplayName("Strategy Method Names")
  class StrategyMethodNames {

    /** A class in {@code com.external}, the package every spec here imports from. */
    private static JavaFileObject external(String simpleName, String body) {
      return JavaFileObjects.forSourceString(
          "com.external." + simpleName, "package com.external;\n\n" + body);
    }

    /**
     * A spec interface in {@code com.myapp}, with the external classes and annotations in scope.
     */
    private static JavaFileObject spec(String simpleName, String body) {
      return JavaFileObjects.forSourceString(
          "com.myapp." + simpleName,
          """
          package com.myapp;

          import com.external.*;
          import org.higherkindedj.optics.Lens;
          import org.higherkindedj.optics.annotations.*;

          """
              + body);
    }

    private static Compilation compile(JavaFileObject... sources) {
      return javac()
          .withProcessors(new ImportOpticsProcessor())
          .withOptions("-Xlint:unchecked,rawtypes,static", "-Werror")
          .compile(sources);
    }

    /**
     * A class every strategy can be pointed at, with a builder and a setter that work, beside the
     * shapes the checks turn away: a static {@code stamp()}, a {@code label(int)} that takes an
     * argument, and a {@code size()} that reads another type.
     */
    private static JavaFileObject account() {
      return external(
          "Account",
          """
          public final class Account {
              private final String id;
              private String host;

              public Account(String id) { this.id = id; }
              public Account(Account other) { this.id = other.id; this.host = other.host; }

              public String id() { return id; }
              public String getId() { return id; }
              public int size() { return id.length(); }
              public static String stamp() { return ""; }
              public static void setDefault(String host) {}
              public String label(int index) { return id; }
              public void setHost(String host) { this.host = host; }
              public Account withId(String id) { return new Account(id); }
              public Builder toBuilder() { return new Builder(id); }

              public static final class Builder {
                  private String id;
                  Builder(String id) { this.id = id; }
                  public Builder id(String id) { this.id = id; return this; }
                  public Account build() { return new Account(id); }
              }
          }
          """);
    }

    @Test
    @DisplayName("a getter the source type does not have is refused, whichever strategy reads it")
    void getterTheSourceTypeDoesNotHaveIsRefused() {
      // @Wither and @ViaBuilder name their getter, and are told to point it somewhere real;
      // @ViaCopyAndSet and @ViaConstructor read through the lens method's own name, and carry no
      // attribute that could point anywhere else.
      var compilation =
          compile(
              account(),
              spec(
                  "WitherSpec",
                  """
                  @ImportOptics
                  public interface WitherSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "BuilderSpec",
                  """
                  @ImportOptics
                  public interface BuilderSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "CopySpec",
                  """
                  @ImportOptics
                  public interface CopySpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHost")
                      Lens<Account, String> ident();
                  }
                  """),
              spec(
                  "ConstructorSpec",
                  """
                  @ImportOptics
                  public interface ConstructorSpec extends OpticsSpec<Account> {
                      @ViaConstructor(parameterOrder = {"ident"})
                      Lens<Account, String> ident();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'Account' has no method 'ident()' for the generated lens to read the value"
                  + " it focuses. The generated lens calls 'ident()' on 'source', so it needs a"
                  + " zero-parameter instance method of that name that the generated class in"
                  + " 'com.myapp' can call. Set @Wither's 'getter' to a method 'Account'"
                  + " declares.");
      assertThat(compilation)
          .hadErrorContaining("@ViaBuilder: 'Account' has no method 'ident()' for the generated");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'Account' has no method 'ident()' for the generated lens to read the"
                  + " value it focuses.");
      assertThat(compilation)
          .hadErrorContaining(
              "Name the lens method after a zero-parameter method 'Account' declares, which is the"
                  + " accessor this strategy reads through.");
      assertThat(compilation).hadErrorCount(4);
    }

    @Test
    @DisplayName("a static method, and one that takes an argument, are not accessors")
    void staticMethodAndOneThatTakesAnArgumentAreNotAccessors() {
      var compilation =
          compile(
              account(),
              spec(
                  "StampSpec",
                  """
                  @ImportOptics
                  public interface StampSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "stamp")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "LabelSpec",
                  """
                  @ImportOptics
                  public interface LabelSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "label")
                      Lens<Account, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Wither: 'Account' has no method 'stamp()' for the generated lens");
      assertThat(compilation)
          .hadErrorContaining("@Wither: 'Account' has no method 'label()' for the generated lens");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a getter that reads another type is refused, with the focus it reads")
    void getterThatReadsAnotherTypeIsRefused() {
      // The pairing a spec exists to declare still has to typecheck: LocalDate's withMonth(int)
      // beside getMonth() is this shape.
      var compilation =
          compile(
              account(),
              spec(
                  "SizedSpec",
                  """
                  @ImportOptics
                  public interface SizedSpec extends OpticsSpec<Account> {
                      @Wither(value = "withId", getter = "size")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "SizedCopySpec",
                  """
                  @ImportOptics
                  public interface SizedCopySpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHost")
                      Lens<Account, String> size();
                  }
                  """),
              external(
                  "Dated",
                  """
                  public final class Dated {
                      private final int month;
                      public Dated(int month) { this.month = month; }
                      public String getMonth() { return String.valueOf(month); }
                      public Dated withMonth(int month) { return new Dated(month); }
                  }
                  """),
              spec(
                  "DatedOpticsSpec",
                  """
                  @ImportOptics
                  public interface DatedOpticsSpec extends OpticsSpec<Dated> {
                      @Wither(value = "withMonth", getter = "getMonth")
                      Lens<Dated, Integer> month();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'size()' reads 'int', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.size()' and hands what it reads back as its focus,"
                  + " which 'int' is not. Point @Wither's 'getter' at an accessor that reads"
                  + " 'String', or declare the lens over 'Integer' and rebuild it through a method"
                  + " that takes one.");
      // Read through the lens method's own name, there is no attribute to point anywhere else.
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'size()' reads 'int', not the lens's focus 'String'. The generated"
                  + " lens reads through 'source.size()' and hands what it reads back as its focus,"
                  + " which 'int' is not. Name the lens method after an accessor that reads"
                  + " 'String', or declare the lens over 'Integer' and rebuild it through a method"
                  + " that takes one.");
      // A reference read keeps its own name where the lens could be declared over it.
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'getMonth()' reads 'String', not the lens's focus 'Integer'. The generated"
                  + " lens reads through 'source.getMonth()' and hands what it reads back as its"
                  + " focus, which 'String' is not. Point @Wither's 'getter' at an accessor that"
                  + " reads 'Integer', or declare the lens over 'String' and rebuild it through a"
                  + " method that takes one.");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("each step of a builder chain is held to what the step before it hands back")
    void eachStepOfABuilderChainIsHeldToWhatTheStepBeforeItHandsBack() {
      var compilation =
          compile(
              account(),
              external(
                  "Flat",
                  """
                  public final class Flat {
                      private final String id;
                      public Flat(String id) { this.id = id; }
                      public String id() { return id; }
                      public int toBuilder() { return 0; }
                  }
                  """),
              external(
                  "Odd",
                  """
                  public final class Odd {
                      private final String id;
                      public Odd(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public void id(String id) {}
                          public String build() { return ""; }
                      }
                  }
                  """),
              external(
                  "Half",
                  """
                  public final class Half {
                      private final String id;
                      public Half(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(String id) { return this; }
                          public String build() { return ""; }
                      }
                  }
                  """),
              spec(
                  "MissingBuilderSpec",
                  """
                  @ImportOptics
                  public interface MissingBuilderSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", toBuilder = "builder")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "MissingSetterSpec",
                  """
                  @ImportOptics
                  public interface MissingSetterSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", setter = "ident")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "MissingBuildSpec",
                  """
                  @ImportOptics
                  public interface MissingBuildSpec extends OpticsSpec<Account> {
                      @ViaBuilder(getter = "id", build = "create")
                      Lens<Account, String> id();
                  }
                  """),
              spec(
                  "FlatSpec",
                  """
                  @ImportOptics
                  public interface FlatSpec extends OpticsSpec<Flat> {
                      @ViaBuilder
                      Lens<Flat, String> id();
                  }
                  """),
              spec(
                  "OddSpec",
                  """
                  @ImportOptics
                  public interface OddSpec extends OpticsSpec<Odd> {
                      @ViaBuilder
                      Lens<Odd, String> id();
                  }
                  """),
              spec(
                  "HalfSpec",
                  """
                  @ImportOptics
                  public interface HalfSpec extends OpticsSpec<Half> {
                      @ViaBuilder
                      Lens<Half, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account' has no method 'builder()' for the generated lens to rebuild"
                  + " through.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account.Builder' has no method 'ident' for the generated lens to set"
                  + " through. The generated lens sets through 'ident(newValue)' on"
                  + " 'Account.Builder', so it needs a method of that name there that the generated"
                  + " class in 'com.myapp' can call. Set @ViaBuilder's 'setter' to a method"
                  + " 'Account.Builder' declares.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Account.Builder' has no method 'create()' for the generated lens to"
                  + " finish the value it rebuilds. The generated lens calls 'create()' on the"
                  + " 'Account.Builder' the setter hands back");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'toBuilder()' hands back 'int', which is not a builder to set"
                  + " through.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'id(String)' hands back 'void', which is not a builder to build"
                  + " from.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'build()' returns 'String', not the source type 'Half'. The generated"
                  + " lens finishes with 'build()' and hands its result back as the source type"
                  + " 'Half', which 'String' is not. Set @ViaBuilder's 'build' to the method that"
                  + " finishes a 'Half', or rebuild 'Half' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(6);
    }

    @Test
    @DisplayName("a setter the call cannot bind is refused, on a builder and on the source")
    void setterTheCallCannotBindIsRefused() {
      var compilation =
          compile(
              account(),
              external(
                  "Picky",
                  """
                  public final class Picky {
                      private final String id;
                      public Picky(String id) { this.id = id; }
                      public Picky(Picky other) { this.id = other.id; }
                      public String id() { return id; }
                      public void setId(Integer id) {}
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(java.io.Serializable id) { return this; }
                          public Builder id(CharSequence id) { return this; }
                          public Picky build() { return new Picky(""); }
                      }
                  }
                  """),
              spec(
                  "PickyCopySpec",
                  """
                  @ImportOptics
                  public interface PickyCopySpec extends OpticsSpec<Picky> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Picky, String> id();
                  }
                  """),
              spec(
                  "PickyBuilderSpec",
                  """
                  @ImportOptics
                  public interface PickyBuilderSpec extends OpticsSpec<Picky> {
                      @ViaBuilder
                      Lens<Picky, String> id();
                  }
                  """),
              spec(
                  "MissingCopySetterSpec",
                  """
                  @ImportOptics
                  public interface MissingCopySetterSpec extends OpticsSpec<Account> {
                      @ViaCopyAndSet(setter = "setHots")
                      Lens<Account, String> getId();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'setId' of 'Picky' takes the lens's focus type 'String'."
                  + " The generated lens sets through 'setId(newValue)' with the new value typed"
                  + " 'String'. Found on 'Picky': [setId(Integer)]. Set @ViaCopyAndSet's 'setter'"
                  + " to a method that takes the value the getter reads; otherwise rebuild 'Picky'"
                  + " with @Wither, @ViaBuilder or @ViaConstructor.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: The generated call to 'id' on a 'Picky.Builder' cannot choose between"
                  + " 'id(Serializable)' and 'id(CharSequence)'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'Account' has no method 'setHots' for the generated lens to set"
                  + " through.");
      assertThat(compilation).hadErrorContaining("Did you mean 'setHost'?");
      assertThat(compilation).hadErrorCount(3);
    }

    @Test
    @DisplayName("a static setter the call binds is refused")
    void staticSetterTheCallBindsIsRefused() {
      var compilation =
          compile(
              external(
                  "Fixed",
                  """
                  public final class Fixed {
                      private final String id;
                      public Fixed(String id) { this.id = id; }
                      public Fixed(Fixed other) { this.id = other.id; }
                      public String id() { return id; }
                      public static void setId(String id) {}
                  }
                  """),
              spec(
                  "FixedSpec",
                  """
                  @ImportOptics
                  public interface FixedSpec extends OpticsSpec<Fixed> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Fixed, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: 'setId(String)' is static, so the generated lens cannot set through"
                  + " it on a 'Fixed'. The generated lens sets through 'setId(newValue)' with the"
                  + " new value typed 'String', which binds that method, and a static method never"
                  + " reads the value it is called on. Set @ViaCopyAndSet's 'setter' to an instance"
                  + " method.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a parameterOrder name that reads nothing is refused")
    void parameterOrderNameThatReadsNothingIsRefused() {
      var compilation =
          compile(
              external(
                  "Point",
                  """
                  public final class Point {
                      private final int x;
                      private final int y;
                      public Point(int x, int y) { this.x = x; this.y = y; }
                      public int x() { return x; }
                      public int y() { return y; }
                  }
                  """),
              spec(
                  "PointSpec",
                  """
                  @ImportOptics
                  public interface PointSpec extends OpticsSpec<Point> {
                      @ViaConstructor(parameterOrder = {"x", "yy"})
                      Lens<Point, Integer> x();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'Point' has no method 'yy()' for the generated lens to read the"
                  + " argument 'yy'. The generated lens calls 'yy()' on 'source', so it needs a"
                  + " zero-parameter instance method of that name that the generated class in"
                  + " 'com.myapp' can call. Did you mean 'y'? Name in @ViaConstructor's"
                  + " 'parameterOrder' the accessors 'Point' declares, in the order its"
                  + " constructor takes them.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an accessor reading another type is named in full where the names collide")
    void accessorReadingAnotherTypeIsNamedInFullWhereTheNamesCollide() {
      var compilation =
          compile(
              external(
                  "Ticket",
                  """
                  public final class Ticket {
                      private final Id id;
                      public Ticket(Id id) { this.id = id; }
                      public Id id() { return id; }
                      public Ticket withId(Id id) { return new Ticket(id); }
                  }
                  """),
              external("Id", "public final class Id {}\n"),
              JavaFileObjects.forSourceString(
                  "com.myapp.Id",
                  """
                  package com.myapp;

                  public final class Id {}
                  """),
              spec(
                  "TicketOpticsSpec",
                  """
                  @ImportOptics
                  public interface TicketOpticsSpec extends OpticsSpec<Ticket> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Ticket, com.myapp.Id> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'id()' reads 'com.external.Id', not the lens's focus 'com.myapp.Id'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("an accessor reading nothing, or a type a wildcard stands in, is offered no focus")
    void accessorReadingNothingIsOfferedNoFocus() {
      // A lens can be declared over what an accessor reads, but neither 'void' nor the type a
      // wildcard stands for can be written as a focus, so only the accessor is offered.
      var compilation =
          compile(
              external(
                  "Blank",
                  """
                  public final class Blank {
                      private final String id;
                      public Blank(String id) { this.id = id; }
                      public void id() {}
                      public String label() { return id; }
                      public Blank withId(String id) { return new Blank(id); }
                  }
                  """),
              external(
                  "Cell2",
                  """
                  public final class Cell2<T> {
                      private final T value;
                      public Cell2(T value) { this.value = value; }
                      public T value() { return value; }
                      public Cell2<T> withValue(String value) { return this; }
                  }
                  """),
              spec(
                  "BlankOpticsSpec",
                  """
                  @ImportOptics
                  public interface BlankOpticsSpec extends OpticsSpec<Blank> {
                      @Wither(value = "withId", getter = "id")
                      Lens<Blank, String> id();
                  }
                  """),
              spec(
                  "Cell2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Cell2OpticsSpec extends OpticsSpec<Cell2<?>> {
                      @Wither(value = "withValue", getter = "value")
                      Lens<Cell2<?>, String> value();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'id()' reads 'void', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.id()' and hands what it reads back as its focus, which"
                  + " 'void' is not. Point @Wither's 'getter' at an accessor that reads 'String'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Wither: 'value()' reads '?', not the lens's focus 'String'. The generated lens"
                  + " reads through 'source.value()' and hands what it reads back as its focus,"
                  + " which '?' is not. Point @Wither's 'getter' at an accessor that reads"
                  + " 'String'.");
      assertThat(compilation).hadErrorCount(2);
    }

    @Test
    @DisplayName("a builder setter that takes no focus offers the getter as well as the setter")
    void builderSetterThatTakesNoFocusOffersTheGetterAsWell() {
      // @ViaBuilder names both halves, so the remedy can move either one; the wither's twin says
      // the same, and neither sends the author round in a circle.
      var compilation =
          compile(
              external(
                  "Ledger2",
                  """
                  public final class Ledger2 {
                      private final String id;
                      public Ledger2(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Builder id(Integer id) { return this; }
                          public Ledger2 build() { return new Ledger2(""); }
                      }
                  }
                  """),
              spec(
                  "Ledger2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Ledger2OpticsSpec extends OpticsSpec<Ledger2> {
                      @ViaBuilder(getter = "getId")
                      Lens<Ledger2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: No method 'id' of 'Ledger2.Builder' takes the lens's focus type"
                  + " 'String'. The generated lens sets through 'id(newValue)' with the new value"
                  + " typed 'String'. Found on 'Ledger2.Builder': [id(Integer)]. Set @ViaBuilder's"
                  + " 'setter' to a method that takes the value the getter reads, or point"
                  + " @ViaBuilder's 'getter' at an accessor one of them takes and declare the focus"
                  + " as its type; otherwise rebuild 'Ledger2' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder step declared with a type variable is read on what it is bound to")
    void builderStepDeclaredWithATypeVariableIsReadOnWhatItIsBoundTo() {
      // javac infers such a step to the variable's bound where nothing else pins it down, and the
      // chain is read there too.
      var compilation =
          compile(
              external(
                  "Crate",
                  """
                  public final class Crate {
                      private final String id;
                      public Crate(String id) { this.id = id; }
                      public String getId() { return id; }

                      @SuppressWarnings("unchecked")
                      public <B extends CrateBuilder> B toBuilder() {
                          return (B) new CrateBuilder();
                      }
                  }
                  """),
              external(
                  "CrateBuilder",
                  """
                  public class CrateBuilder {
                      private String id;
                      public CrateBuilder id(String id) { this.id = id; return this; }
                      public Crate build() { return new Crate(id); }
                  }
                  """),
              spec(
                  "CrateOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrateOpticsSpec extends OpticsSpec<Crate> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crate, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertGeneratedCodeContains(
          compilation, "com.myapp.CrateOptics", "source.toBuilder().id(newValue).build()");
    }

    @Test
    @DisplayName("a builder step bounded by more than one type is left to javac")
    void builderStepBoundedByMoreThanOneTypeIsLeftToJavac() {
      // A bound naming more than one type leaves no single type to read the next call on, and
      // javac infers the step perfectly well from the bound itself. Crock's toBuilder is bounded
      // that way, and Crank's setter is.
      var compilation =
          compile(
              external(
                  "Crock",
                  """
                  public final class Crock {
                      private final String id;
                      public Crock(String id) { this.id = id; }
                      public String getId() { return id; }

                      @SuppressWarnings("unchecked")
                      public <B extends CrockBuilder & Cloneable> B toBuilder() {
                          return (B) new CrockBuilder();
                      }
                  }
                  """),
              external(
                  "CrockBuilder",
                  """
                  public class CrockBuilder implements Cloneable {
                      private String id;

                      @SuppressWarnings("unchecked")
                      public <B extends CrockBuilder & Cloneable> B id(String id) {
                          this.id = id;
                          return (B) this;
                      }

                      public Crock build() { return new Crock(id); }
                  }
                  """),
              external(
                  "Crank",
                  """
                  public final class Crank {
                      private final String id;
                      public Crank(String id) { this.id = id; }
                      public String getId() { return id; }
                      public CrankBuilder toBuilder() { return new CrankBuilder(); }
                  }
                  """),
              external(
                  "CrankBuilder",
                  """
                  public class CrankBuilder implements Cloneable {
                      private String id;

                      @SuppressWarnings("unchecked")
                      public <B extends CrankBuilder & Cloneable> B id(String id) {
                          this.id = id;
                          return (B) this;
                      }

                      public Crank build() { return new Crank(id); }
                  }
                  """),
              spec(
                  "CrockOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrockOpticsSpec extends OpticsSpec<Crock> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crock, String> id();
                  }
                  """),
              spec(
                  "CrankOpticsSpec",
                  """
                  @ImportOptics
                  public interface CrankOpticsSpec extends OpticsSpec<Crank> {
                      @ViaBuilder(getter = "getId")
                      Lens<Crank, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a type the setter hands back that cannot be named is refused too")
    void typeTheSetterHandsBackThatCannotBeNamedIsRefused() {
      var compilation =
          compile(
              external(
                  "Staged",
                  """
                  public final class Staged {
                      private final String id;
                      public Staged(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public Step id(String id) { return new Step(); }
                      }
                  }

                  class Step {
                      public Staged build() { return new Staged(""); }
                  }
                  """),
              spec(
                  "StagedOpticsSpec",
                  """
                  @ImportOptics
                  public interface StagedOpticsSpec extends OpticsSpec<Staged> {
                      @ViaBuilder(getter = "getId")
                      Lens<Staged, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Step', which 'id(String)' hands back, cannot be named from"
                  + " 'com.myapp'.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a builder the generated class cannot name is refused, whatever its members are")
    void builderTheGeneratedClassCannotNameIsRefused() {
      var compilation =
          compile(
              external(
                  "Sealed",
                  """
                  public final class Sealed {
                      private final String id;
                      public Sealed(String id) { this.id = id; }
                      public String getId() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      static final class Builder {
                          public Builder id(String id) { return this; }
                          public Sealed build() { return new Sealed(""); }
                      }
                  }
                  """),
              spec(
                  "SealedOpticsSpec",
                  """
                  @ImportOptics
                  public interface SealedOpticsSpec extends OpticsSpec<Sealed> {
                      @ViaBuilder(getter = "getId")
                      Lens<Sealed, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaBuilder: 'Builder', which 'toBuilder()' hands back, cannot be named from"
                  + " 'com.myapp'. The generated lens rebuilds through that type, and a class it"
                  + " cannot see is a compile error in a file its author never wrote. Make"
                  + " 'Builder' public, or rebuild 'Sealed' with @Wither, @ViaConstructor or"
                  + " @ViaCopyAndSet.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a setter with no one-argument overload is refused, whatever the focus is")
    void setterWithNoOneArgumentOverloadIsRefused() {
      // Arity does not depend on the focus, so a wildcard one is no reason to leave this to javac.
      var compilation =
          compile(
              external(
                  "Pairy",
                  """
                  public final class Pairy {
                      private String id;
                      public Pairy() {}
                      public Pairy(Pairy other) { this.id = other.id; }
                      public String getId() { return id; }
                      public void setId(String id, boolean flag) { this.id = id; }
                  }
                  """),
              spec(
                  "PairyOpticsSpec",
                  """
                  @ImportOptics
                  public interface PairyOpticsSpec extends OpticsSpec<Pairy> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Pairy, ? extends CharSequence> getId();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaCopyAndSet: No method 'setId' of 'Pairy' takes one argument. The generated lens"
                  + " sets through 'setId(newValue)', passing the one value it sets. Found on"
                  + " 'Pairy': [setId(String, boolean)]. Set @ViaCopyAndSet's 'setter' to a method"
                  + " that takes the value the lens sets; otherwise rebuild 'Pairy' with @Wither,"
                  + " @ViaBuilder or @ViaConstructor.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName(
        "a parameterOrder that never names the lens is refused, since it would set nothing")
    void parameterOrderThatNeverNamesTheLensIsRefused() {
      var compilation =
          compile(
              external(
                  "Tagged2",
                  """
                  public final class Tagged2 {
                      private final String id;
                      private final String tag;
                      public Tagged2(String id, String tag) {
                          this.id = id;
                          this.tag = tag;
                      }
                      public String id() { return id; }
                      public String tag() { return tag; }
                  }
                  """),
              spec(
                  "Tagged2OpticsSpec",
                  """
                  @ImportOptics
                  public interface Tagged2OpticsSpec extends OpticsSpec<Tagged2> {
                      @ViaConstructor(parameterOrder = {"tag", "tag"})
                      Lens<Tagged2, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@ViaConstructor: 'parameterOrder' names no argument for the lens's own 'id'. The"
                  + " generated lens rebuilds 'Tagged2' from the order given, and passes the value"
                  + " it sets where the lens's own name stands; naming it nowhere would set"
                  + " nothing. Add 'id' to @ViaConstructor's 'parameterOrder', at the place the"
                  + " constructor takes it.");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a setter whose own type does not resolve is left to javac")
    void setterWhoseOwnTypeDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Vanish",
                  """
                  public final class Vanish {
                      private final String id;
                      public Vanish(String id) { this.id = id; }
                      public String id() { return id; }
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          public com.external.Gone id(String id) { return null; }
                          public Vanish build() { return new Vanish(""); }
                      }
                  }
                  """),
              spec(
                  "VanishOpticsSpec",
                  """
                  @ImportOptics
                  public interface VanishOpticsSpec extends OpticsSpec<Vanish> {
                      @ViaBuilder
                      Lens<Vanish, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"));
    }

    @Test
    @DisplayName("a wildcard focus leaves every name that depends on it to javac")
    void wildcardFocusLeavesEveryNameThatDependsOnItToJavac() {
      // The accessors still have to exist; which method the value binds, and what that method
      // hands back, is javac's to settle for a focus it infers.
      var compilation =
          compile(
              external(
                  "Loose",
                  """
                  public final class Loose {
                      private final String id;
                      public Loose(String id) { this.id = id; }
                      public Loose(Loose other) { this.id = other.id; }
                      public String id() { return id; }
                      public void setId(CharSequence id) {}
                      public void setId(CharSequence id, int at) {}
                      public Builder toBuilder() { return new Builder(); }

                      public static final class Builder {
                          // Declared first, and returning something the chain could not go on
                          // from: which overload such a call binds is javac's to settle.
                          public Object id(Integer id) { return this; }
                          public Builder id(CharSequence id) { return this; }
                          public Loose build() { return new Loose(""); }
                      }
                  }
                  """),
              spec(
                  "LooseCopySpec",
                  """
                  @ImportOptics
                  public interface LooseCopySpec extends OpticsSpec<Loose> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Loose, ? extends CharSequence> id();
                  }
                  """),
              spec(
                  "LooseBuilderSpec",
                  """
                  @ImportOptics
                  public interface LooseBuilderSpec extends OpticsSpec<Loose> {
                      @ViaBuilder
                      Lens<Loose, ? extends CharSequence> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    @DisplayName("a builder type that does not resolve is left to javac")
    void builderTypeThatDoesNotResolveIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Absent",
                  """
                  public final class Absent {
                      private final String id;
                      public Absent(String id) { this.id = id; }
                      public String id() { return id; }
                      public com.external.Gone toBuilder() { return null; }
                  }
                  """),
              spec(
                  "AbsentSpec",
                  """
                  @ImportOptics
                  public interface AbsentSpec extends OpticsSpec<Absent> {
                      @ViaBuilder
                      Lens<Absent, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"));
    }

    @Test
    @DisplayName("a source type that does not resolve is left to javac, whatever the strategy")
    void sourceTypeThatDoesNotResolveIsLeftToJavacWhateverTheStrategy() {
      var compilation =
          compile(
              spec(
                  "GhostWitherSpec",
                  """
                  @ImportOptics
                  public interface GhostWitherSpec extends OpticsSpec<com.external.Ghost> {
                      @Wither(value = "withId", getter = "id")
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostBuilderSpec",
                  """
                  @ImportOptics
                  public interface GhostBuilderSpec extends OpticsSpec<com.external.Ghost> {
                      @ViaBuilder
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostCopySpec",
                  """
                  @ImportOptics
                  public interface GhostCopySpec extends OpticsSpec<com.external.Ghost> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<com.external.Ghost, String> id();
                  }
                  """),
              spec(
                  "GhostConstructorSpec",
                  """
                  @ImportOptics
                  public interface GhostConstructorSpec extends OpticsSpec<com.external.Ghost> {
                      @ViaConstructor(parameterOrder = {"id"})
                      Lens<com.external.Ghost, String> id();
                  }
                  """));

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("@Wither:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaBuilder:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaCopyAndSet:"))
          .noneMatch(error -> error.getMessage(null).contains("@ViaConstructor:"));
    }

    @Test
    @DisplayName("a setter whose parameter is inferred is left to javac")
    void setterWhoseParameterIsInferredIsLeftToJavac() {
      var compilation =
          compile(
              external(
                  "Generic",
                  """
                  public final class Generic {
                      private final String id;
                      public Generic(String id) { this.id = id; }
                      public Generic(Generic other) { this.id = other.id; }
                      public String id() { return id; }
                      public <V extends CharSequence> void setId(V id) {}
                  }
                  """),
              spec(
                  "GenericSpec",
                  """
                  @ImportOptics
                  public interface GenericSpec extends OpticsSpec<Generic> {
                      @ViaCopyAndSet(setter = "setId")
                      Lens<Generic, String> id();
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
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
    @DisplayName("should match a copy constructor through the spec's own type parameter")
    void shouldMatchCopyConstructorOnGenericSpec() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  public Node(Base<X> other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NodeOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              // <X> repeats the source type's own parameter name on purpose. Node's X and the
              // spec's X are distinct variables despite the shared spelling, which is the shape
              // the bug had, so renaming this to <U> would prove less than it looks like it does.
              @ImportOptics
              public interface NodeOpticsSpec<X> extends OpticsSpec<Node<X>> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Base", setter = "setName")
                  Lens<Node<X>, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, node, specInterface);

      // Node declares Node(Base<X> other) against its own X; the supertype walk hands over the
      // spec's X. Same name, different variable, so the parameter has to be read under Node<X>'s
      // instantiation rather than as declared.
      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.NodeOptics", "new Node<X>((Base<X>) source)");
    }

    @Test
    @DisplayName("should name the parameter types with the source type's own arguments")
    void shouldNameInstantiatedParameterTypesWhenNoConstructorMatches() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var other =
          JavaFileObjects.forSourceString(
              "com.external.Other",
              """
              package com.external;

              public class Other<X> extends Base<X> {}
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  public Node(Other<X> other) {}
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NodeOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              @ImportOptics
              public interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Base", setter = "setName")
                  Lens<Node<U>, String> name();
              }
              """);

      Compilation compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .compile(base, other, node, specInterface);

      assertThat(compilation).failed();
      // Under Node<U> the parameter is Other<U>; naming it Other<X> would send the author looking
      // for a variable their own declaration does not have.
      assertThat(compilation).hadErrorContaining("single-argument constructors taking");
      assertThat(compilation).hadErrorContaining("Other<U>");
    }

    @Test
    @DisplayName("should substitute the spec's parameter name into the emitted cast")
    void shouldSubstituteSpecParameterNameIntoTheCast() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  public Node(Base<X> other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NodeOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              @ImportOptics
              public interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Base", setter = "setName")
                  Lens<Node<U>, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, node, specInterface);

      // The cast names the spec's U, not Node's own X, which is what pins the direction of the
      // substitution rather than merely that one happened.
      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.NodeOptics", "new Node<U>((Base<U>) source)");
    }

    @Test
    @DisplayName("should match a varargs copy constructor through the source type's instantiation")
    void shouldMatchVarargsCopyConstructorOnGenericSpec() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  @SafeVarargs
                  public Node(Base<X>... others) { this.name = others[0].name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NodeOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              @ImportOptics
              public interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {

                  @ViaCopyAndSet(copyConstructor = "com.external.Base", setter = "setName")
                  Lens<Node<U>, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, node, specInterface);

      // Substituting into an array type gives an array type, so the component is still there to
      // read for the varargs arm.
      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.NodeOptics", "new Node<U>((Base<U>) source)");
    }

    @Test
    @DisplayName("should reject a source type whose constructor call cannot be written")
    void shouldRejectSourceTypeWhoseConstructorCallCannotBeWritten() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  public Node() {}
                  public Node(Base<X> other) { this.name = other.name; }
                  public String name() { return name; }
                  public void setName(String name) { this.name = name; }
                  public Node<X> withName(String name) {
                      Node<X> n = new Node<>();
                      n.name = name;
                      return n;
                  }
              }
              """);

      record Case(String name, String annotation) {}
      var cases =
          List.of(
              new Case(
                  "CopyAndSetSpec",
                  "@ViaCopyAndSet(setter = \"setName\","
                      + " copyConstructor = \"com.external.Base\")"),
              new Case("PlainCopyAndSetSpec", "@ViaCopyAndSet(setter = \"setName\")"));

      for (Case testCase : cases) {
        final var specInterface =
            JavaFileObjects.forSourceString(
                "com.myapp." + testCase.name(),
                """
                package com.myapp;

                import com.external.Node;
                import org.higherkindedj.optics.Lens;
                import org.higherkindedj.optics.annotations.ImportOptics;
                import org.higherkindedj.optics.annotations.OpticsSpec;
                import org.higherkindedj.optics.annotations.ViaCopyAndSet;

                @ImportOptics
                public interface %s extends OpticsSpec<Node<?>> {
                    %s
                    Lens<Node<?>, String> name();
                }
                """
                    .formatted(testCase.name(), testCase.annotation()));

        Compilation compilation =
            javac().withProcessors(new ImportOpticsProcessor()).compile(base, node, specInterface);

        // Reported at the spec, not left to javac inside a file the author never wrote.
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining("is written with a wildcard type argument");
        assertThat(compilation).hadErrorContaining("Name the type the wildcard stands for");
      }
    }

    @Test
    @DisplayName("should reject a wildcard source type for @ViaConstructor too")
    void shouldRejectWildcardSourceTypeForViaConstructor() {
      final var bag =
          JavaFileObjects.forSourceString(
              "com.external.Bag",
              """
              package com.external;

              public record Bag<T>(String name, T value) {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.BagOpticsSpec",
              """
              package com.myapp;

              import com.external.Bag;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaConstructor;

              @ImportOptics
              public interface BagOpticsSpec extends OpticsSpec<Bag<?>> {
                  @ViaConstructor(parameterOrder = {"name", "value"})
                  Lens<Bag<?>, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(bag, specInterface);

      // @ViaConstructor rebuilds the same way, so it is asked the same question.
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@ViaConstructor");
      assertThat(compilation).hadErrorContaining("is written with a wildcard type argument");
    }

    @Test
    @DisplayName("should accept a static nested source type, which needs no enclosing instance")
    void shouldAcceptStaticNestedSourceType() {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer {
                  public static class Nested {
                      private String name;
                      public Nested() {}
                      public Nested(Nested other) { this.name = other.name; }
                      public String name() { return name; }
                      public void setName(String name) { this.name = name; }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NestedOpticsSpec",
              """
              package com.myapp;

              import com.external.Outer;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              @ImportOptics
              public interface NestedOpticsSpec extends OpticsSpec<Outer.Nested> {
                  @ViaCopyAndSet(setter = "setName")
                  Lens<Outer.Nested, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(outer, specInterface);

      // Static, so 'new Outer.Nested(...)' writes perfectly well and the guard stands aside.
      assertCompilationSucceeded(compilation);
    }

    @Test
    @DisplayName("should reject an inner class source type, whose call needs an enclosing instance")
    void shouldRejectInnerClassSourceType() {
      final var outer =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer {
                  public class Inner {
                      protected String name;
                      public Inner() {}
                      public String name() { return name; }
                      public void setName(String name) { this.name = name; }
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.InnerOpticsSpec",
              """
              package com.myapp;

              import com.external.Outer;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;

              @ImportOptics
              public interface InnerOpticsSpec extends OpticsSpec<Outer.Inner> {
                  @ViaCopyAndSet(setter = "setName")
                  Lens<Outer.Inner, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(outer, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is an inner class");
      assertThat(compilation).hadErrorContaining("Declare the source type static");
    }

    @Test
    @DisplayName("should leave a wildcard source type to @Wither, which names no constructor")
    void shouldAllowWildcardSourceTypeWithWither() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.external.Base",
              """
              package com.external;

              public class Base<X> {
                  protected String name;
              }
              """);

      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<X> extends Base<X> {
                  public Node() {}
                  public String name() { return name; }
                  public Node<X> withName(String name) {
                      Node<X> n = new Node<>();
                      n.name = name;
                      return n;
                  }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.WitherOpticsSpec",
              """
              package com.myapp;

              import com.external.Node;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              public interface WitherOpticsSpec extends OpticsSpec<Node<?>> {
                  @Wither("withName")
                  Lens<Node<?>, String> name();
              }
              """);

      Compilation compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(base, node, specInterface);

      // The guard is asked per strategy: a wither rebuilds through a method, so the wildcard the
      // constructor arms cannot write is no obstacle here.
      assertCompilationSucceeded(compilation);
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
      assertThat(compilation).hadErrorContaining("and no constructor accepts");
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
      assertThat(compilation).hadErrorContaining("and no constructor accepts");
      assertThat(compilation).hadErrorContaining("taking [Shut]");
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
              "Name the type the optics are for as the type argument: 'OpticsSpec<Box>'.");
    }

    @Test
    @DisplayName("should not suggest a raw bound as the source type")
    void shouldNotSuggestARawBound() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Crate",
              """
              package com.external;

              public class Crate<X> {
                  private String v;
                  public String getV() { return v; }
                  public void setV(String v) { this.v = v; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.CrateOpticsSpec",
              """
              package com.myapp;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.ViaCopyAndSet;
              import com.external.Crate;

              @ImportOptics
              @SuppressWarnings("rawtypes")
              public interface CrateOpticsSpec<S extends Crate> extends OpticsSpec<S> {

                  @ViaCopyAndSet(setter = "setV")
                  Lens<S, String> v();
              }
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'CrateOpticsSpec' declares OpticsSpec<S>, which is a type variable.");
      // A hint naming the raw Crate would steer straight into the raw-source refusal, so the fix
      // sentence ends unanswered instead.
      assertThat(compilation).hadErrorContainingMatch("type argument\\.$");
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
      // Object bounds every variable, so suggesting it would be no answer at all.
      assertThat(compilation).hadErrorContainingMatch("type argument\\.$");
    }

    @Test
    @DisplayName("should name a parameterised bound in full rather than raw")
    void shouldNameParameterisedBoundInFull() {
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.ListOpticsSpec",
              """
              package com.myapp;

              import java.util.List;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface ListOpticsSpec<S extends List<String>> extends OpticsSpec<S> {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("as the type argument: 'OpticsSpec<List<String>>'.");
    }

    @Test
    @DisplayName("should suggest a bound whose wildcard does not name the variable")
    void shouldSuggestBoundWhoseWildcardDoesNotNameTheVariable() {
      record Case(String name, String wildcard) {}
      var cases =
          List.of(
              new Case("UnboundedWildcardSpec", "?"),
              new Case("ExtendsWildcardSpec", "? extends String"),
              new Case("SuperWildcardSpec", "? super String"));

      for (Case testCase : cases) {
        final var specInterface =
            JavaFileObjects.forSourceString(
                "com.myapp." + testCase.name(),
                """
                package com.myapp;

                import java.util.List;
                import org.higherkindedj.optics.annotations.ImportOptics;
                import org.higherkindedj.optics.annotations.OpticsSpec;

                @ImportOptics
                public interface %s<S extends List<%s>> extends OpticsSpec<S> {}
                """
                    .formatted(testCase.name(), testCase.wildcard()));

        var compilation =
            javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

        assertThat(compilation).failed();
        // A wildcard that does not lead back to S leaves the suggestion usable.
        assertThat(compilation)
            .hadErrorContaining(
                "as the type argument: 'OpticsSpec<List<" + testCase.wildcard() + ">>'.");
      }
    }

    @Test
    @DisplayName("should name a nested bound by its enclosing type so the suggestion resolves")
    void shouldNameNestedBoundWithEnclosingType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer {
                  public static class Inner {}
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.InnerOpticsSpec",
              """
              package com.myapp;

              import com.external.Outer;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface InnerOpticsSpec<S extends Outer.Inner> extends OpticsSpec<S> {}
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("as the type argument: 'OpticsSpec<Outer.Inner>'.");
    }

    @Test
    @DisplayName(
        "should offer no bound when the bound names two types, another variable, or itself")
    void shouldOfferNoBoundWhenItNamesNoSingleType() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Box",
              """
              package com.external;

              public class Box {}
              """);

      // A generic outer with an inner class: the one shape where the variable hides in an
      // enclosing type rather than in a type argument.
      final var outerClass =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer<X> {
                  public class Inner {}
              }
              """);

      record Case(String name, String declaration) {}
      var cases =
          List.of(
              new Case("IntersectionSpec", "<S extends Box & java.io.Serializable>"),
              new Case("SelfReferentialSpec", "<S extends Comparable<S>>"),
              new Case("SelfReferentialArraySpec", "<S extends java.util.List<S[]>>"),
              new Case("SelfReferentialEnclosingSpec", "<S extends Outer<S>.Inner>"),
              new Case("SelfReferentialWildcardSpec", "<S extends java.util.List<? extends S>>"),
              new Case("SelfReferentialSuperWildcardSpec", "<S extends java.util.List<? super S>>"),
              new Case("VariableBoundSpec", "<T extends Box, S extends T>"));

      for (Case testCase : cases) {
        final var specInterface =
            JavaFileObjects.forSourceString(
                "com.myapp." + testCase.name(),
                """
                package com.myapp;

                import com.external.Box;
                import com.external.Outer;
                import org.higherkindedj.optics.annotations.ImportOptics;
                import org.higherkindedj.optics.annotations.OpticsSpec;

                @ImportOptics
                public interface %s%s extends OpticsSpec<S> {}
                """
                    .formatted(testCase.name(), testCase.declaration()));

        var compilation =
            javac()
                .withProcessors(new ImportOpticsProcessor())
                .compile(externalClass, outerClass, specInterface);

        assertThat(compilation).failed();
        // Anchored: the fix sentence must end there, with no suggestion appended after it.
        assertThat(compilation).hadErrorContainingMatch("type argument\\.$");
      }
    }

    @Test
    @DisplayName("should suggest a bound parameterised by another variable the spec declares")
    void shouldSuggestBoundParameterisedByAnotherVariable() {
      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.OtherVariableOpticsSpec",
              """
              package com.myapp;

              import java.util.List;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface OtherVariableOpticsSpec<T, S extends List<T>>
                      extends OpticsSpec<S> {}
              """);

      var compilation = javac().withProcessors(new ImportOpticsProcessor()).compile(specInterface);

      assertThat(compilation).failed();
      // T is declared on the spec, so naming it in the suggestion still yields a valid declaration.
      assertThat(compilation).hadErrorContaining("as the type argument: 'OpticsSpec<List<T>>'.");
    }

    @Test
    @DisplayName(
        "should suggest an enclosing type parameterised by another variable the spec declares")
    void shouldSuggestEnclosingTypeParameterisedByAnotherVariable() {
      final var externalClass =
          JavaFileObjects.forSourceString(
              "com.external.Outer",
              """
              package com.external;

              public class Outer<X> {
                  public class Inner {}
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.EnclosingOpticsSpec",
              """
              package com.myapp;

              import com.external.Outer;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface EnclosingOpticsSpec<T, S extends Outer<T>.Inner>
                      extends OpticsSpec<S> {}
              """);

      var compilation =
          javac().withProcessors(new ImportOpticsProcessor()).compile(externalClass, specInterface);

      assertThat(compilation).failed();
      // Only the variable being replaced makes a suggestion circular; T is the spec's to name.
      assertThat(compilation)
          .hadErrorContaining("as the type argument: 'OpticsSpec<Outer<T>.Inner>'.");
    }

    @Test
    @DisplayName("should reject an array source type by naming the kind it is")
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
              "'ArrayOpticsSpec' declares OpticsSpec<String[]>, which is an array type.");
    }
  }

  @Nested
  @DisplayName("Type Parameters on Generated Methods")
  class TypeParametersOnGeneratedMethods {

    private final JavaFileObject box =
        JavaFileObjects.forSourceString(
            "com.external.Box",
            """
            package com.external;

            public class Box<T> {
                private final T content;
                private final String label;
                public Box(T content, String label) { this.content = content; this.label = label; }
                public T content() { return content; }
                public String label() { return label; }
                public Box<T> withLabel(String label) { return new Box<>(content, label); }
                public Box<T> withContent(T content) { return new Box<>(content, label); }
            }
            """);

    private final JavaFileObject pair =
        JavaFileObjects.forSourceString(
            "com.external.Pair",
            """
            package com.external;

            public class Pair<A, B> {
                private final A left;
                private final B right;
                public Pair(A left, B right) { this.left = left; this.right = right; }
                public A left() { return left; }
                public B right() { return right; }
                public Pair<A, B> withLeft(A left) { return new Pair<>(left, right); }
            }
            """);

    private Compilation compile(String specBody) {
      var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.SubjectOpticsSpec",
              """
              package com.myapp;

              import com.external.Box;
              import com.external.Pair;
              import java.util.List;
              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.Wither;

              @ImportOptics
              %s
              """
                  .formatted(specBody));
      return javac().withProcessors(new ImportOpticsProcessor()).compile(box, pair, specInterface);
    }

    @Test
    @DisplayName("should take the spec's parameter name, not the source type's")
    void shouldUseSpecParameterName() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                  @Wither("withLabel")
                  Lens<Box<U>, String> label();
              }""");

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "public static <U> Lens<Box<U>, String> label()");
    }

    @Test
    @DisplayName("should declare only the parameters the signature reaches, not the source type's")
    void shouldDropParametersTheSignatureDoesNotName() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<A> extends OpticsSpec<Pair<A, String>> {
                  @Wither("withLeft")
                  Lens<Pair<A, String>, A> left();
              }""");

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "public static <A> Lens<Pair<A, String>, A> left()");
      // B is Pair's second parameter: nothing in the signature names it, so nothing could infer it.
      assertGeneratedCodeDoesNotContain(compilation, "com.myapp.SubjectOptics", "<A, B>");
    }

    @Test
    @DisplayName("should generate no type parameters for a concrete instantiation")
    void shouldGenerateNoTypeParametersForConcreteInstantiation() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Box<String>> {
                  @Wither("withLabel")
                  Lens<Box<String>, String> label();
              }""");

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "public static Lens<Box<String>, String> label()");
    }

    @Test
    @DisplayName("should keep a parameter only the focus type reaches")
    void shouldKeepParameterOnlyFocusReaches() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U> extends OpticsSpec<Box<U>> {
                  @Wither("withContent")
                  Lens<Box<U>, U> content();
              }""");

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.SubjectOptics", "public static <U> Lens<Box<U>, U> content()");
    }

    @Test
    @DisplayName("should carry the bound the spec declares")
    void shouldCarryTheSpecBound() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<U extends Comparable<U>>
                      extends OpticsSpec<Box<U>> {
                  @Wither("withLabel")
                  Lens<Box<U>, String> label();
              }""");

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "public static <U extends Comparable<U>> Lens<Box<U>, String> label()");
    }

    @Test
    @DisplayName("should declare a parameter only the focus type names, on a non-generic source")
    void shouldDeclareParameterNamedOnlyByFocusOfNonGenericSource() {
      final var empty =
          JavaFileObjects.forSourceString(
              "com.external.Empty",
              """
              package com.external;

              public class Empty {}
              """);

      // A traversal over no elements is a traversal of any element type, so the element parameter
      // is the spec's to name and the source type never mentions it. That is the shape a signature
      // needs a parameter for that reading the source type alone would not declare.
      final var emptyTraversals =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.optics.EmptyTraversals",
              """
              package org.higherkindedj.optics;

              import com.external.Empty;

              public final class EmptyTraversals {
                  private EmptyTraversals() {}
                  public static <U> Traversal<Empty, U> nothing() { return null; }
              }
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.EmptyOpticsSpec",
              """
              package com.myapp;

              import com.external.Empty;
              import org.higherkindedj.optics.Traversal;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.OpticsSpec;
              import org.higherkindedj.optics.annotations.TraverseWith;

              @ImportOptics
              public interface EmptyOpticsSpec<U> extends OpticsSpec<Empty> {
                  @TraverseWith("org.higherkindedj.optics.EmptyTraversals.nothing()")
                  Traversal<Empty, U> nothing();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(empty, emptyTraversals, specInterface);

      assertCompilationSucceeded(compilation);
      // Empty declares no parameters, so reading them from the source type would declare none.
      assertGeneratedCodeContains(
          compilation, "com.myapp.EmptyOptics", "public static <U> Traversal<Empty, U> nothing()");
    }

    @Test
    @DisplayName("should not suppress warnings for a @MatchWhen prism onto a parameterised target")
    void shouldNotSuppressForMatchWhenPrism() {
      final var node =
          JavaFileObjects.forSourceString(
              "com.external.Node",
              """
              package com.external;

              public class Node<T> {
                  public boolean isLeaf() { return true; }
                  public Leaf<T> asLeaf() { return null; }
              }
              """);

      final var leaf =
          JavaFileObjects.forSourceString(
              "com.external.Leaf",
              """
              package com.external;

              public class Leaf<T> extends Node<T> {}
              """);

      final var specInterface =
          JavaFileObjects.forSourceString(
              "com.myapp.NodeOpticsSpec",
              """
              package com.myapp;

              import com.external.Leaf;
              import com.external.Node;
              import org.higherkindedj.optics.Prism;
              import org.higherkindedj.optics.annotations.ImportOptics;
              import org.higherkindedj.optics.annotations.MatchWhen;
              import org.higherkindedj.optics.annotations.OpticsSpec;

              @ImportOptics
              public interface NodeOpticsSpec<U> extends OpticsSpec<Node<U>> {
                  @MatchWhen(predicate = "isLeaf", getter = "asLeaf")
                  Prism<Node<U>, Leaf<U>> leaf();
              }
              """);

      var compilation =
          javac()
              .withProcessors(new ImportOpticsProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(node, leaf, specInterface);

      assertCompilationSucceeded(compilation);
      assertGeneratedCodeContains(
          compilation, "com.myapp.NodeOptics", "public static <U> Prism<Node<U>, Leaf<U>> leaf()");
      // The source type's own getter does the narrowing, so there is no warning to answer.
      assertGeneratedCodeDoesNotContain(compilation, "com.myapp.NodeOptics", "@SuppressWarnings");
    }

    @Test
    @DisplayName("should reject an optic method that declares its own type parameters")
    void shouldRejectMethodLevelTypeParameters() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec extends OpticsSpec<Box<String>> {
                  @Wither("withContent")
                  <X> Lens<Box<String>, X> content();
              }""");

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'SubjectOpticsSpec.content' declares its own type parameters.");
      assertThat(compilation).hadErrorContaining("Move the parameter to the spec interface");
    }

    @Test
    @DisplayName("should drop a parameter that no kept parameter's bound reaches")
    void shouldDropParameterNoBoundReaches() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<T, V extends List<String>>
                      extends OpticsSpec<Box<V>> {
                  @Wither("withLabel")
                  Lens<Box<V>, String> label();
              }""");

      assertCompilationSucceeded(compilation);
      // V's bound names String, not T, so T is declared by the spec but earns no place here.
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "public static <V extends List<String>> Lens<Box<V>, String> label()");
    }

    @Test
    @DisplayName("should keep a parameter that only a kept parameter's bound reaches")
    void shouldKeepParameterReachedThroughABound() {
      var compilation =
          compile(
              """
              public interface SubjectOpticsSpec<T, V extends List<T>>
                      extends OpticsSpec<Box<V>> {
                  @Wither("withLabel")
                  Lens<Box<V>, String> label();
              }""");

      assertCompilationSucceeded(compilation);
      // V's own bound names T, so T has to be declared alongside it for the bound to resolve.
      assertGeneratedCodeContains(
          compilation,
          "com.myapp.SubjectOptics",
          "public static <T, V extends List<T>> Lens<Box<V>, String> label()");
    }
  }
}

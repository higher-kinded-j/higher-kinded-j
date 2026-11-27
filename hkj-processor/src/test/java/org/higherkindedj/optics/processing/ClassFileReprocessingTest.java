// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.higherkindedj.optics.processing.Reprocessing.Run;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * hkj-processor's generators are registered with Gradle as aggregating, so in an incremental build
 * Gradle hands them each unchanged annotated type of the compilation by name, and javac reads that
 * type from the class file the last build wrote. A generator must write from the class file what it
 * writes from source, or the build after an unrelated change differs from a clean build.
 * compile-testing compiles from source alone, so none of its tests can see the difference.
 */
@DisplayName("Generating from the class files Gradle's incremental build hands over")
class ClassFileReprocessingTest {

  private static final List<String> PARAMETERS = List.of("-parameters");

  private static final JavaFileObject RECORDS =
      JavaFileObjects.forSourceString(
          "com.example.Records",
          """
          package com.example;

          public final class Records {
            public record User(String name, String email) {}

            public record Account(String iban, int balance) {}

            public record Settings(boolean darkMode) {}

            public record EmailAddress(String value) {}

            public record Dashboard(String name, String iban, boolean darkMode) {}

            public record TypedDashboard(String name, EmailAddress email, int balance) {}

            public record Customer(String id, String name) {}

            public record CustomerDto(String id, String fullName) {}
          }
          """);

  private static final JavaFileObject MAPPING =
      JavaFileObjects.forSourceString(
          "com.example.CustomerMapping",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MapField;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface CustomerMapping
              extends MappingSpec<Records.Customer, Records.CustomerDto> {
            @MapField(to = "fullName")
            String name();
          }
          """);

  private static final JavaFileObject MERGE =
      JavaFileObjects.forSourceString(
          "com.example.DashboardAssembly",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateMerge;

          @GenerateMerge
          public interface DashboardAssembly {
            Records.Dashboard assemble(
                Records.User owner, Records.Account account, Records.Settings settings);
          }
          """);

  /** A fallible merge: its leaf is a default method, which the class file carries too. */
  private static final JavaFileObject FALLIBLE_MERGE =
      JavaFileObjects.forSourceString(
          "com.example.TypedAssembly",
          """
          package com.example;

          import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMerge;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMerge
          public interface TypedAssembly {
            Validated<NonEmptyList<FieldError>, Records.TypedDashboard> assemble(
                Records.User user, Records.Account account);

            default ValidatedPrism<String, Records.EmailAddress> email() {
              return ValidatedPrism.of(
                  raw ->
                      raw.contains("@")
                          ? Validated.validNel(new Records.EmailAddress(raw))
                          : Validated.invalidNel(FieldError.of("not an email address")),
                  Records.EmailAddress::value);
            }
          }
          """);

  private static final JavaFileObject ISO =
      JavaFileObjects.forSourceString(
          "com.example.PointConverters",
          """
          package com.example;

          import org.higherkindedj.hkt.tuple.Tuple;
          import org.higherkindedj.hkt.tuple.Tuple2;
          import org.higherkindedj.optics.Iso;
          import org.higherkindedj.optics.annotations.GenerateIsos;

          public class PointConverters {
            public record Point(int x, int y) {}

            @GenerateIsos(targetPackage = "com.example.isos")
            public static Iso<Point, Tuple2<Integer, Integer>> pointToTuple() {
              return Iso.of(
                  point -> Tuple.of(point.x(), point.y()),
                  tuple -> new Point(tuple._1(), tuple._2()));
            }
          }
          """);

  /**
   * An iso in the unnamed package, whose generated class has a simple name for its canonical one.
   */
  private static final JavaFileObject UNNAMED_ISO =
      JavaFileObjects.forSourceString(
          "Unnamed",
          """
          import org.higherkindedj.optics.Iso;
          import org.higherkindedj.optics.annotations.GenerateIsos;

          public class Unnamed {
            public record Celsius(double value) {}

            @GenerateIsos
            public static Iso<Celsius, Double> celsius() {
              return Iso.of(Celsius::value, Celsius::new);
            }
          }
          """);

  private static final List<JavaFileObject> ENVELOPE =
      List.of(
          JavaFileObjects.forSourceString(
              "com.example.FooErrorContext",
              """
              package com.example;

              public record FooErrorContext(String orderId, String traceId) {}
              """),
          JavaFileObjects.forSourceString(
              "com.example.FooError",
              """
              package com.example;

              import java.util.List;
              import java.util.function.UnaryOperator;
              import org.higherkindedj.hkt.error.ErrorEnvelope;
              import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;

              @GenerateErrorEnvelope
              public sealed interface FooError {
                ErrorEnvelope<FooErrorContext> envelope();

                default FooError editContext(UnaryOperator<FooErrors.ContextBuilder> edit) {
                  return FooErrors.editContext(this, edit);
                }

                record OutOfStock(List<String> products, ErrorEnvelope<FooErrorContext> envelope)
                    implements FooError {}

                record PaymentDeclined(
                    ErrorEnvelope<FooErrorContext> envelope, String card, String reason)
                    implements FooError {}

                record SystemHalted(ErrorEnvelope<FooErrorContext> envelope) implements FooError {}
              }
              """));

  private static final List<JavaFileObject> PATH_SOURCE =
      List.of(
          JavaFileObjects.forSourceString(
              "com.example.ApiResultKind",
              """
              package com.example;

              import org.higherkindedj.hkt.Kind;
              import org.higherkindedj.hkt.TypeArity;
              import org.higherkindedj.hkt.WitnessArity;

              public interface ApiResultKind<A> extends Kind<ApiResultKind.Witness, A> {
                final class Witness implements WitnessArity<TypeArity.Unary> {}
              }
              """),
          JavaFileObjects.forSourceString(
              "com.example.ApiResult",
              """
              package com.example;

              import java.util.function.Function;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = ApiResultKind.Witness.class, suffix = "Steps")
              public sealed interface ApiResult<A> permits ApiSuccess, ApiFailure {
                <B> ApiResult<B> map(Function<? super A, ? extends B> f);
              }
              """),
          JavaFileObjects.forSourceString(
              "com.example.ApiSuccess",
              """
              package com.example;

              import java.util.function.Function;

              public record ApiSuccess<A>(A value) implements ApiResult<A> {
                @Override
                public <B> ApiResult<B> map(Function<? super A, ? extends B> f) {
                  return new ApiSuccess<>(f.apply(value));
                }
              }
              """),
          JavaFileObjects.forSourceString(
              "com.example.ApiFailure",
              """
              package com.example;

              import java.util.function.Function;

              public record ApiFailure<A>(String error) implements ApiResult<A> {
                @Override
                public <B> ApiResult<B> map(Function<? super A, ? extends B> f) {
                  return new ApiFailure<>(error);
                }
              }
              """));

  @TempDir Path tmp;

  /** The generators that write no parameter name, with the sources each generates from. */
  static Stream<Arguments> generatorsWritingNoParameterName() {
    return Stream.of(
        arguments("@GenerateMapping, every golden case", MappingGoldenFileTest.fixtures()),
        arguments("@GenerateIsos", List.of(ISO)),
        arguments("@GenerateErrorEnvelope", ENVELOPE),
        arguments("@PathSource", PATH_SOURCE));
  }

  /**
   * The generators whose annotations became CLASS-retained, the merge's two shapes included. The
   * others are not covered yet.
   */
  static Stream<Arguments> generators() {
    return Stream.concat(
        generatorsWritingNoParameterName(),
        Stream.of(arguments("@GenerateMerge, plain and fallible", merges())));
  }

  private static List<JavaFileObject> merges() {
    return List.of(RECORDS, MERGE, FALLIBLE_MERGE);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generators")
  @DisplayName(
      "each generator writes from class files compiled with -parameters what it writes from source")
  void writesFromClassFilesWhatItWritesFromSource(String generator, List<JavaFileObject> sources)
      throws Exception {
    Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, PARAMETERS);
    Run fromClassFiles =
        Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, PARAMETERS);

    assertThat(fromSource.succeeded()).isTrue();
    assertThat(fromSource.generated()).isNotEmpty();
    assertThat(fromClassFiles).isEqualTo(fromSource);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generatorsWritingNoParameterName")
  @DisplayName("a generator writing no parameter name needs no -parameters to write it again")
  void needsNoParameterNames(String generator, List<JavaFileObject> sources) throws Exception {
    Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
    Run fromClassFiles =
        Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, List.of());

    assertThat(fromSource.generated()).isNotEmpty();
    assertThat(fromClassFiles).isEqualTo(fromSource);
  }

  /** Each generator's annotation, the sources it generates from, and the class it writes. */
  static Stream<Arguments> generatedNames() {
    return Stream.of(
        arguments("@GenerateMapping", List.of(RECORDS, MAPPING), "com.example.CustomerMappingImpl"),
        arguments("@GenerateMerge", List.of(RECORDS, MERGE), "com.example.DashboardAssemblyImpl"),
        arguments("@GenerateErrorEnvelope", ENVELOPE, "com.example.FooErrors"),
        arguments("@GenerateIsos", List.of(ISO), "com.example.isos.PointConvertersIsos"),
        arguments("@GenerateIsos", List.of(UNNAMED_ISO), "UnnamedIsos"),
        arguments("@PathSource", PATH_SOURCE, "com.example.ApiResultSteps"),
        arguments("@PathSource", inTheUnnamedPackage(PATH_SOURCE), "ApiResultSteps"));
  }

  /** The same sources moved into the unnamed package, each named by its simple name. */
  private static List<JavaFileObject> inTheUnnamedPackage(List<JavaFileObject> sources) {
    return sources.stream()
        .map(
            source -> {
              String file = source.getName();
              String simpleName =
                  file.substring(file.lastIndexOf('/') + 1, file.length() - ".java".length());
              try {
                return JavaFileObjects.forSourceString(
                    simpleName,
                    source
                        .getCharContent(true)
                        .toString()
                        .replaceFirst("package [\\w.]+;\\s*", ""));
              } catch (IOException unreadable) {
                throw new UncheckedIOException(unreadable);
              }
            })
        .toList();
  }

  /** A hand-written class of the given canonical name, a simple name in the unnamed package. */
  private static JavaFileObject handWritten(String qualifiedName) {
    int dot = qualifiedName.lastIndexOf('.');
    String declaration = "public class " + qualifiedName.substring(dot + 1) + " {}\n";
    return JavaFileObjects.forSourceString(
        qualifiedName,
        dot < 0
            ? declaration
            : "package " + qualifiedName.substring(0, dot) + ";\n\n" + declaration);
  }

  @Nested
  @DisplayName("Generated names")
  class GeneratedNameChecks {

    @ParameterizedTest(name = "{0} writing {2}")
    @MethodSource("org.higherkindedj.optics.processing.ClassFileReprocessingTest#generatedNames")
    @DisplayName("a hand-written class file of the generated name is refused, not replaced")
    void aHandWrittenClassFileIsRefused(String tag, List<JavaFileObject> sources, String generated)
        throws Exception {
      Run run =
          Reprocessing.againstClassFiles(
              tmp, List.of(handWritten(generated)), false, sources, PARAMETERS);

      assertThat(run.succeeded()).isFalse();
      assertThat(run.generated()).doesNotContainKey(generated.replace('.', '/') + ".java");
      assertThat(run.diagnostics())
          .anySatisfy(
              diagnostic ->
                  assertThat(diagnostic)
                      .startsWith("ERROR: " + tag + ":")
                      .contains("'" + generated + "'"));
    }

    @ParameterizedTest(name = "{0} writing {2}")
    @MethodSource("org.higherkindedj.optics.processing.ClassFileReprocessingTest#generatedNames")
    @DisplayName("the class file a previous build generated is written again, as from source")
    void aPreviouslyGeneratedClassFileIsWrittenAgain(
        String tag, List<JavaFileObject> sources, String generated) throws Exception {
      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, PARAMETERS);
      Run again =
          Reprocessing.againstClassFiles(tmp.resolve("again"), sources, true, sources, PARAMETERS);

      assertThat(again.succeeded()).isTrue();
      assertThat(again.generated()).isEqualTo(fromSource.generated());
    }

    @ParameterizedTest(name = "{0} writing {2}")
    @MethodSource("org.higherkindedj.optics.processing.ClassFileReprocessingTest#generatedNames")
    @DisplayName("a hand-written class of the generated name in source still fails the build")
    void aHandWrittenSourceStillFails(String tag, List<JavaFileObject> sources, String generated)
        throws Exception {
      List<JavaFileObject> withHandWritten =
          Stream.concat(sources.stream(), Stream.of(handWritten(generated))).toList();

      Run run = Reprocessing.fromSource(tmp, withHandWritten, PARAMETERS);

      assertThat(run.succeeded()).isFalse();
      assertThat(run.diagnostics())
          .anySatisfy(diagnostic -> assertThat(diagnostic).startsWith("ERROR").contains(generated));
    }
  }

  @Nested
  @DisplayName("Parameter names")
  class ParameterNames {

    @Test
    @DisplayName("a merge whose class file records no parameter names is refused, not written")
    void aMergeWithoutParameterNamesIsRefused() throws Exception {
      Run fromClassFiles = Reprocessing.fromClassNames(tmp, merges(), List.of());

      assertThat(fromClassFiles.succeeded()).isFalse();
      assertThat(fromClassFiles.generated())
          .doesNotContainKeys("com/example/DashboardAssemblyImpl.java")
          .doesNotContainKeys("com/example/TypedAssemblyImpl.java");
      assertThat(fromClassFiles.diagnostics())
          .contains(
              "ERROR: @GenerateMerge: merge method 'com.example.DashboardAssembly.assemble' was"
                  + " read from a class file that records no parameter names. Gradle's incremental"
                  + " build reads an unchanged spec from the class file its last build wrote, and"
                  + " without -parameters javac names the sources 'arg0', 'arg1', so the Impl's"
                  + " null checks would say 'arg0 must not be null' where a build from source names"
                  + " each source. Compile the source set with -parameters, as the HKJ Gradle"
                  + " plugin does.");
    }

    @Test
    @DisplayName("the null check names the source as the spec does, from source or class file")
    void theNullCheckNamesTheSourceAsTheSpecDoes() throws Exception {
      Run fromClassFiles = Reprocessing.fromClassNames(tmp, merges(), PARAMETERS);

      assertThat(fromClassFiles.generated().get("com/example/DashboardAssemblyImpl.java"))
          .contains("Objects.requireNonNull(owner, \"owner must not be null\");");
    }
  }

  @Nested
  @DisplayName("Suppressed warnings")
  class SuppressedWarnings {

    @Test
    @DisplayName("a warning suppressed on a spec is not reported once the spec is a class file")
    void aSuppressedImplConstantStaysQuiet() throws Exception {
      List<JavaFileObject> sources =
          List.of(
              RECORDS,
              JavaFileObjects.forSourceString(
                  "com.example.QuietMapping",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MapField;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  public interface QuietMapping
                      extends MappingSpec<Records.Customer, Records.CustomerDto> {
                    @SuppressWarnings("impl-constant")
                    QuietMappingImpl MAPPER = QuietMappingImpl.INSTANCE;

                    @MapField(to = "fullName")
                    String name();
                  }
                  """));

      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
      Run fromClassFiles =
          Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, List.of());

      assertThat(fromSource.diagnostics()).isEmpty();
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName("a dropped-result warning suppressed on an @ImportOptics lens stays quiet")
    void aSuppressedDroppedResultStaysQuiet() throws Exception {
      List<JavaFileObject> sources =
          List.of(
              JavaFileObjects.forSourceString(
                  "com.example.SprigBase",
                  """
                  package com.example;

                  public class SprigBase {
                    protected String title;

                    public SprigBase withTitle(String title) {
                      SprigBase copy = new SprigBase();
                      copy.title = title;
                      return copy;
                    }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Twig",
                  """
                  package com.example;

                  public class Twig extends SprigBase {
                    public Twig() {}

                    public Twig(Twig other) {
                      title = other.title;
                    }

                    public String getTitle() {
                      return title;
                    }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.TwigOpticsSpec",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.annotations.ImportOptics;
                  import org.higherkindedj.optics.annotations.OpticsSpec;
                  import org.higherkindedj.optics.annotations.ViaCopyAndSet;

                  @ImportOptics
                  public interface TwigOpticsSpec extends OpticsSpec<Twig> {
                    @SuppressWarnings("dropped-result")
                    @ViaCopyAndSet(setter = "withTitle")
                    Lens<Twig, String> getTitle();
                  }
                  """));

      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
      Run fromClassFiles =
          Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, List.of());

      assertThat(fromSource.diagnostics()).isEmpty();
      assertThat(fromSource.generated()).containsKey("com/example/TwigOptics.java");
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName("a warning the build that compiled a spec reported is not reported again")
    void aReportedWarningIsNotRepeated() throws Exception {
      // javac repeats none of its own lint warnings for a class it does not recompile either.
      List<JavaFileObject> sources =
          List.of(
              RECORDS,
              JavaFileObjects.forSourceString(
                  "com.example.LoudMapping",
                  """
                  package com.example;

                  import org.higherkindedj.optics.annotations.GenerateMapping;
                  import org.higherkindedj.optics.annotations.MapField;
                  import org.higherkindedj.optics.annotations.MappingSpec;

                  @GenerateMapping
                  public interface LoudMapping
                      extends MappingSpec<Records.Customer, Records.CustomerDto> {
                    LoudMappingImpl MAPPER = LoudMappingImpl.INSTANCE;

                    @MapField(to = "fullName")
                    String name();
                  }
                  """));

      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
      Run fromClassFiles =
          Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, List.of());

      assertThat(fromSource.diagnostics())
          .singleElement(InstanceOfAssertFactories.STRING)
          .startsWith("WARNING: @GenerateMapping: 'LoudMapping.MAPPER' holds the generated");
      assertThat(fromClassFiles.diagnostics()).isEmpty();
      assertThat(fromClassFiles.generated()).isEqualTo(fromSource.generated());
    }

    @Test
    @DisplayName("a warning suppressed on an @ImportOptics lens stays quiet from its class file")
    void aSuppressedShorterConstructorStaysQuiet() throws Exception {
      List<JavaFileObject> sources =
          List.of(
              JavaFileObjects.forSourceString(
                  "com.example.Fee",
                  """
                  package com.example;

                  public final class Fee {
                    private final long amount;
                    private final String currency;

                    public Fee(long amount, String currency) {
                      this.amount = amount;
                      this.currency = currency;
                    }

                    public Fee(long amount) {
                      this(amount, "GBP");
                    }

                    public long amount() {
                      return amount;
                    }

                    public String currency() {
                      return currency;
                    }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.FeeOpticsSpec",
                  """
                  package com.example;

                  import org.higherkindedj.optics.Lens;
                  import org.higherkindedj.optics.annotations.ImportOptics;
                  import org.higherkindedj.optics.annotations.OpticsSpec;
                  import org.higherkindedj.optics.annotations.ViaConstructor;

                  @ImportOptics
                  public interface FeeOpticsSpec extends OpticsSpec<Fee> {
                    // The shorter constructor fills the currency in, so the lens stays lawful.
                    @SuppressWarnings("shorter-constructor")
                    @ViaConstructor(parameterOrder = {"amount"})
                    Lens<Fee, Long> amount();
                  }
                  """));

      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
      Run fromClassFiles =
          Reprocessing.fromClassNames(tmp.resolve("class-files"), sources, List.of());

      assertThat(fromSource.diagnostics()).isEmpty();
      assertThat(fromSource.generated()).containsKey("com/example/FeeOptics.java");
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }
  }

  @Nested
  @DisplayName("Effect annotation values")
  class EffectValues {

    @Test
    @DisplayName(
        "a @PathSource witness or error type, a @Handles algebra or a @ComposeEffects algebra that a"
            + " class file names and the classpath lacks generates and reports nothing")
    void aClassTheClasspathLacksGeneratesAndReportsNothing() throws IOException {
      // A class literal a class file names reaches the processors as an error type, where a source
      // reference leaves javac's mark, and no later round resolves it. Each processor waits for it
      // all the same, as a round may bring it (see EffectTypesWritten), and none here does.
      Compilation clean =
          javac()
              .withProcessors(GeneratorTestHelper.registeredProcessors().toList())
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.BoxKind",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.Kind;
                      import org.higherkindedj.hkt.TypeArity;
                      import org.higherkindedj.hkt.WitnessArity;

                      public interface BoxKind<A> extends Kind<BoxKind.Witness, A> {
                        final class Witness implements WitnessArity<TypeArity.Unary> {}
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Box",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.PathSource;

                      @PathSource(witness = BoxKind.Witness.class)
                      public interface Box<A> {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Failure",
                      """
                      package com.example;

                      public record Failure(String reason) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Recovering",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.PathSource;

                      @PathSource(
                          witness = ApiResultKind.Witness.class,
                          errorType = Failure.class,
                          capability = PathSource.Capability.RECOVERABLE)
                      public interface Recovering<A> {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.ApiResultKind",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.Kind;
                      import org.higherkindedj.hkt.TypeArity;
                      import org.higherkindedj.hkt.WitnessArity;

                      public interface ApiResultKind<A> extends Kind<ApiResultKind.Witness, A> {
                        final class Witness implements WitnessArity<TypeArity.Unary> {}
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.GoneOp",
                      """
                      package com.example;

                      import java.util.function.Function;
                      import org.higherkindedj.hkt.Unit;
                      import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

                      @EffectAlgebra
                      public sealed interface GoneOp<A> permits GoneOp.Only {
                        <B> GoneOp<B> mapK(Function<? super A, ? extends B> f);

                        record Only<A>(String text, Function<Unit, A> k) implements GoneOp<A> {
                          @Override
                          public <B> GoneOp<B> mapK(Function<? super A, ? extends B> f) {
                            return new Only<>(text, k.andThen(f));
                          }
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.KeptOp",
                      """
                      package com.example;

                      import java.util.function.Function;
                      import org.higherkindedj.hkt.Unit;
                      import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

                      @EffectAlgebra
                      public sealed interface KeptOp<A> permits KeptOp.Only {
                        <B> KeptOp<B> mapK(Function<? super A, ? extends B> f);

                        record Only<A>(String text, Function<Unit, A> k) implements KeptOp<A> {
                          @Override
                          public <B> KeptOp<B> mapK(Function<? super A, ? extends B> f) {
                            return new Only<>(text, k.andThen(f));
                          }
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.AppEffects",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

                      @ComposeEffects
                      public record AppEffects(Class<GoneOp<?>> gone, Class<KeptOp<?>> kept) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.LaterOp",
                      """
                      package com.example;

                      public sealed interface LaterOp<A> permits LaterOp.Only {
                        record Only<A>(String text) implements LaterOp<A> {}
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.LaterInterpreter",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.Handles;

                      @Handles(LaterOp.class)
                      public final class LaterInterpreter {
                        public void handleOnly(LaterOp.Only<?> op) {}
                      }
                      """));
      CompilationSubject.assertThat(clean).succeeded();
      CompilationSubject.assertThat(clean).generatedSourceFile("com.example.BoxPath");
      CompilationSubject.assertThat(clean).generatedSourceFile("com.example.RecoveringPath");
      CompilationSubject.assertThat(clean).generatedSourceFile("com.example.AppEffectsSupport");

      // The clean build's class files, less those of a witness, an error type, two algebras and the
      // Paths.
      Path classes =
          GeneratorTestHelper.classDirectoryWithout(
              clean,
              tmp.resolve("classes"),
              "com.example.BoxKind",
              "com.example.BoxKind$Witness",
              "com.example.Failure",
              "com.example.GoneOp",
              "com.example.GoneOp$Only",
              "com.example.LaterOp",
              "com.example.LaterOp$Only",
              "com.example.BoxPath",
              "com.example.RecoveringPath");
      Run byName =
          Reprocessing.byName(
              tmp.resolve("by-name"),
              classes,
              List.of(
                  "com.example.Box",
                  "com.example.Recovering",
                  "com.example.LaterInterpreter",
                  "com.example.AppEffects"),
              GeneratorTestHelper.registeredProcessors().toList());

      assertThat(byName.succeeded()).isTrue();
      assertThat(byName.generated()).isEmpty();
      assertThat(byName.diagnostics()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Effect types a processor writes")
  class EffectTypesWritten {

    /**
     * Writes one source in its first round, standing in for any processor whose output an effect
     * annotation names.
     */
    private static final class TypeWriter extends AbstractProcessor {

      private final String qualifiedName;
      private final String text;
      private boolean written;

      TypeWriter(String qualifiedName, String text) {
        this.qualifiedName = qualifiedName;
        this.text = text;
      }

      @Override
      public Set<String> getSupportedAnnotationTypes() {
        return Set.of("*");
      }

      @Override
      public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (!written) {
          written = true;
          write();
        }
        return false;
      }

      private void write() {
        try (Writer out = processingEnv.getFiler().createSourceFile(qualifiedName).openWriter()) {
          out.write(text);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    /** One operation, as a plain sealed interface and as an effect algebra. */
    private static final String LATER_OP =
        """
        package com.example;

        public sealed interface LaterOp<A> permits LaterOp.Only {
          record Only<A>(String text) implements LaterOp<A> {}
        }
        """;

    /** The same, gaining a second operation. */
    private static final String LATER_OP_EXTENDED =
        """
        package com.example;

        public sealed interface LaterOp<A> permits LaterOp.Only, LaterOp.Extra {
          record Only<A>(String text) implements LaterOp<A> {}

          record Extra<A>(String text) implements LaterOp<A> {}
        }
        """;

    private static final String LATER_ALGEBRA =
        """
        package com.example;

        import java.util.function.Function;
        import org.higherkindedj.hkt.Unit;
        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;

        @EffectAlgebra
        public sealed interface LaterOp<A> permits LaterOp.Only {
          <B> LaterOp<B> mapK(Function<? super A, ? extends B> f);

          record Only<A>(String text, Function<Unit, A> k) implements LaterOp<A> {
            @Override
            public <B> LaterOp<B> mapK(Function<? super A, ? extends B> f) {
              return new Only<>(text, k.andThen(f));
            }
          }
        }
        """;

    private static final JavaFileObject CONSOLE =
        JavaFileObjects.forSourceString(
            "com.example.Console",
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = ConsoleOpKind.Witness.class)
            public interface Console<A> {}
            """);

    private static final JavaFileObject LATER_INTERPRETER =
        JavaFileObjects.forSourceString(
            "com.example.LaterInterpreter",
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.Handles;

            @Handles(LaterOp.class)
            public final class LaterInterpreter {
              public void handleOnly(LaterOp.Only<?> op) {}
            }
            """);

    private static final JavaFileObject APP_EFFECTS =
        JavaFileObjects.forSourceString(
            "com.example.AppEffects",
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

            @ComposeEffects
            public record AppEffects(Class<LaterOp<?>> later, Class<ConsoleOp<?>> console) {}
            """);

    /** The effect algebra ConsoleOp, carrying {@code annotation} beside its own. */
    private static JavaFileObject consoleOp(String annotation) {
      return JavaFileObjects.forSourceString(
          "com.example.ConsoleOp",
          """
          package com.example;

          import java.util.function.Function;
          import org.higherkindedj.hkt.Unit;
          import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
          import org.higherkindedj.hkt.effect.annotation.PathSource;

          @EffectAlgebra
          %s
          public sealed interface ConsoleOp<A> permits ConsoleOp.Only {
            <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f);

            record Only<A>(String text, Function<Unit, A> k) implements ConsoleOp<A> {
              @Override
              public <B> ConsoleOp<B> mapK(Function<? super A, ? extends B> f) {
                return new Only<>(text, k.andThen(f));
              }
            }
          }
          """
              .formatted(annotation));
    }

    /**
     * A processor that writes the type named as each build's text in turn, the clean build's first.
     */
    private static Supplier<List<Processor>> writing(String qualifiedName, String... builds) {
      Iterator<String> texts = List.of(builds).iterator();
      return () -> List.of(new TypeWriter(qualifiedName, texts.next()));
    }

    private static Supplier<List<Processor>> writingLaterOp(String... builds) {
      return writing("com.example.LaterOp", builds);
    }

    static Stream<Arguments> pathSources() {
      JavaFileObject annotated = consoleOp("@PathSource(witness = ConsoleOpKind.Witness.class)");
      return Stream.of(
          arguments(
              "on the algebra itself",
              List.of(annotated),
              List.of(),
              List.of("com/example/ConsoleOpPath.java")),
          arguments(
              "on a separate type",
              List.of(consoleOp(""), CONSOLE),
              List.of(),
              List.of("com/example/ConsolePath.java")),
          arguments(
              "on a separate type compiled from source beside it",
              List.of(annotated, CONSOLE),
              List.of(CONSOLE),
              List.of("com/example/ConsoleOpPath.java", "com/example/ConsolePath.java")));
    }

    @ParameterizedTest(name = "@PathSource {0}")
    @MethodSource("pathSources")
    @DisplayName(
        "a @PathSource over the witness @EffectAlgebra writes generates and reports what a clean"
            + " build does")
    void aPathSourceOverAWrittenWitness(
        String shape,
        List<JavaFileObject> sources,
        List<JavaFileObject> recompiled,
        List<String> paths)
        throws IOException {
      // The class file names the witness, missing until @EffectAlgebra writes it, so javac answers
      // every processor from its failed lookup there for the rest of the build.
      Run fromSource = Reprocessing.fromSource(tmp.resolve("source"), sources, List.of());
      Run fromClassFiles =
          Reprocessing.fromClassNames(
              tmp.resolve("class-files"), sources, recompiled, List.of(), List::of);

      assertThat(fromSource.generated()).containsKeys(paths.toArray(String[]::new));
      assertThat(fromSource.diagnostics())
          .hasSize(paths.size())
          .allSatisfy(note -> assertThat(note).startsWith("NOTE: @PathSource: '"));
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName(
        "a @Handles over an algebra another processor writes reports a missing handler as a clean"
            + " build does")
    void aHandlesOverAWrittenAlgebra() throws IOException {
      List<JavaFileObject> sources = List.of(LATER_INTERPRETER);
      Run fromSource =
          Reprocessing.fromSource(
              tmp.resolve("source"), sources, List.of(), writingLaterOp(LATER_OP_EXTENDED));
      // The other processor's output gains an operation between the two builds.
      Run fromClassFiles =
          Reprocessing.fromClassNames(
              tmp.resolve("class-files"),
              sources,
              List.of(),
              writingLaterOp(LATER_OP, LATER_OP_EXTENDED));

      assertThat(fromSource.diagnostics())
          .containsExactly(
              "ERROR: No handler for operation: Extra. Expected method:"
                  + " handleExtra(LaterOp.Extra)");
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName(
        "a @ComposeEffects naming a type another processor writes, not an algebra, is refused as a"
            + " clean build refuses it")
    void aCompositionOverAWrittenNonAlgebra() throws IOException {
      List<JavaFileObject> sources = List.of(consoleOp(""), APP_EFFECTS);
      Run fromSource =
          Reprocessing.fromSource(
              tmp.resolve("source"), sources, List.of(), writingLaterOp(LATER_OP));
      // The other processor's output stops being an algebra between the two builds.
      Run fromClassFiles =
          Reprocessing.fromClassNames(
              tmp.resolve("class-files"),
              sources,
              List.of(),
              writingLaterOp(LATER_ALGEBRA, LATER_OP));

      assertThat(fromSource.diagnostics())
          .containsExactly(
              "ERROR: @ComposeEffects field 'later' names com.example.LaterOp, which is not"
                  + " annotated @EffectAlgebra. Composition derives the witness and Ops from that"
                  + " annotation.");
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName(
        "an algebra another processor writes, which a class file names, is refused with why, where"
            + " the clean build generates it")
    void aWrittenAlgebraAClassFileNamesIsRefused() throws IOException {
      List<JavaFileObject> sources = List.of(consoleOp(""), APP_EFFECTS);
      Run fromSource =
          Reprocessing.fromSource(
              tmp.resolve("source"), sources, List.of(), writingLaterOp(LATER_ALGEBRA));
      Run fromClassFiles =
          Reprocessing.fromClassNames(
              tmp.resolve("class-files"),
              sources,
              List.of(),
              writingLaterOp(LATER_ALGEBRA, LATER_ALGEBRA));

      assertThat(fromSource.diagnostics()).isEmpty();
      assertThat(fromSource.generated())
          .containsKeys("com/example/AppEffectsSupport.java", "com/example/LaterOpFunctor.java");
      // Hidden, the algebra shows neither its type parameter nor its mapK, and a Functor guessed
      // without them could map by a cast where it should call mapK.
      assertThat(fromClassFiles.diagnostics())
          .containsExactly(
              "ERROR: @ComposeEffects field 'later' names com.example.LaterOp, which @EffectAlgebra"
                  + " rejects because javac shows annotation processors none of its type"
                  + " parameters or methods in this compilation, so its Kind and Ops are not"
                  + " generated.",
              "ERROR: @EffectAlgebra: the support classes of 'com.example.LaterOp' cannot be"
                  + " generated in this compilation. javac shows annotation processors none of its"
                  + " type parameters or methods. A class file this compilation read, such as one"
                  + " Gradle did not recompile, names it, and another processor writes it. javac"
                  + " looked the name up as the file was written, found nothing, and keeps that"
                  + " answer for every processor until the compilation ends. Build from clean,"
                  + " which compiles every type from source; each incremental build after a change"
                  + " refuses it again. To build incrementally, declare 'LaterOp' in source rather"
                  + " than have a processor write it.");
      assertThat(fromClassFiles.generated())
          .doesNotContainKeys(
              "com/example/LaterOpFunctor.java", "com/example/AppEffectsSupport.java");
    }

    @Test
    @DisplayName(
        "a @PathSource over an error type another processor writes generates what a clean build"
            + " does")
    void aPathSourceOverAWrittenErrorType() throws IOException {
      List<JavaFileObject> sources =
          List.of(
              JavaFileObjects.forSourceString(
                  "com.example.BoxKind",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;

                  public interface BoxKind<A> extends Kind<BoxKind.Witness, A> {
                    final class Witness implements WitnessArity<TypeArity.Unary> {}
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Box",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.PathSource;

                  @PathSource(
                      witness = BoxKind.Witness.class,
                      errorType = Failure.class,
                      capability = PathSource.Capability.RECOVERABLE)
                  public interface Box<A> {}
                  """));
      String failure =
          """
          package com.example;

          public record Failure(String reason) {}
          """;
      Run fromSource =
          Reprocessing.fromSource(
              tmp.resolve("source"), sources, List.of(), writing("com.example.Failure", failure));
      Run fromClassFiles =
          Reprocessing.fromClassNames(
              tmp.resolve("class-files"),
              sources,
              List.of(),
              writing("com.example.Failure", failure, failure));

      assertThat(fromSource.diagnostics()).isEmpty();
      assertThat(fromSource.generated())
          .hasEntrySatisfying(
              "com/example/BoxPath.java", path -> assertThat(path).contains("recoverWith("));
      assertThat(fromClassFiles).isEqualTo(fromSource);
    }

    @Test
    @DisplayName(
        "a misspelt @ComposeEffects algebra is left for javac to report, bare, nested in a type"
            + " the compilation declares, or named like a type of the unnamed package")
    void aMisspeltAlgebraIsLeftForJavac() throws IOException {
      // javac makes a name it cannot find in ConsoleOp an error type belonging to ConsoleOp, which
      // a round brought, and which it shows in full: nothing hides it, so it is waited for. A bare
      // name it cannot find belongs to no package, so the unnamed package's Unnamed, which the
      // other processor writes and a named package cannot see, does not hide it either.
      Run fromSource =
          Reprocessing.fromSource(
              tmp.resolve("source"),
              List.of(
                  consoleOp(""),
                  JavaFileObjects.forSourceString(
                      "com.example.AppEffects",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.ComposeEffects;

                      @ComposeEffects
                      public record AppEffects(
                          Class<ConsoleOpp<?>> bare,
                          Class<ConsoleOp.Typo<?>> nested,
                          Class<Unnamed<?>> unnamed) {}
                      """)),
              List.of(),
              writing("Unnamed", "public interface Unnamed<A> {}\n"));

      assertThat(fromSource.succeeded()).isFalse();
      assertThat(fromSource.diagnostics())
          .hasSize(3)
          .allSatisfy(error -> assertThat(error).startsWith("ERROR: cannot find symbol"));
    }
  }
}

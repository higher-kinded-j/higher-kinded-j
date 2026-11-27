// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
import org.higherkindedj.hkt.effect.annotation.Handles;
import org.higherkindedj.hkt.effect.annotation.PathSource;
import org.higherkindedj.hkt.effect.annotation.PathVia;
import org.higherkindedj.optics.processing.CompanionAnnotationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Effect annotation values set to a constant another annotation processor writes, and algebras
 * another processor writes. javac has neither in the round that writes it: reading such a value
 * through the annotation's proxy throws, which stops javac, and such an algebra reads as an error
 * type. So the element waits for the round that brings it, and generates what it would were it
 * written by hand. Each case compiles beside a processor that writes them in its first round.
 */
@DisplayName("Effect annotation values and algebras a later round brings")
class EffectRoundsTest {

  private static final String PKG = "com.example.rounds";

  private static final String IMPORTS =
      """
      import java.util.Optional;
      import java.util.function.Function;
      import org.higherkindedj.hkt.Kind;
      import org.higherkindedj.hkt.TypeArity;
      import org.higherkindedj.hkt.Unit;
      import org.higherkindedj.hkt.WitnessArity;
      import org.higherkindedj.hkt.effect.annotation.ComposeEffects;
      import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
      import org.higherkindedj.hkt.effect.annotation.GeneratePathBridge;
      import org.higherkindedj.hkt.effect.annotation.Handles;
      import org.higherkindedj.hkt.effect.annotation.PathSource;
      import org.higherkindedj.hkt.effect.annotation.PathVia;

      """;

  /** The constants the annotation values of {@link LaterValue}'s rows name. */
  private static final String NAMES =
      """
      public final class Names {
        private Names() {}

        public static final String SUFFIX = "Bridge";
        public static final String NAME = "lookUp";
        public static final String DOC = "Looks the user up.";
        public static final String PACKAGE = "com.example.rounds.generated";
        public static final boolean FLAG = true;
      }
      """;

  /**
   * Writes the given sources in its first round, standing in for any processor whose output an
   * effect annotation names.
   */
  private static final class TypeWriter extends AbstractProcessor {

    private final Map<String, String> sources;
    private final Set<String> roots = new LinkedHashSet<>();
    private boolean written;

    TypeWriter(Map<String, String> sources) {
      this.sources = sources;
    }

    /**
     * Every type the compilation's rounds were handed as a root: a file any processor writes in one
     * round is a root of the next, so this tells what was written even when the compilation fails.
     */
    Set<String> roots() {
      return roots;
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
      ElementFilter.typesIn(roundEnv.getRootElements())
          .forEach(root -> roots.add(root.getQualifiedName().toString()));
      if (!written) {
        written = true;
        // In name order, so the next round's roots come in the same order on every run.
        new TreeMap<>(sources).forEach(this::write);
      }
      return false;
    }

    /** Whether this writer wrote the file, rather than an effect processor. */
    boolean wrote(JavaFileObject file) {
      return sources.keySet().stream()
          .anyMatch(name -> file.getName().endsWith("/" + name + ".java"));
    }

    private void write(String simpleName, String body) {
      try (Writer out =
          processingEnv.getFiler().createSourceFile(PKG + "." + simpleName).openWriter()) {
        out.write("package " + PKG + ";\n\n" + IMPORTS + body);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  /** A bridged service, with the attributes given to its two annotations. */
  private static JavaFileObject service(String bridge, String via) {
    return source(
        "UserService",
        """
        @GeneratePathBridge(%s)
        public interface UserService {
          @PathVia(%s)
          Optional<String> findById(Long id);
        }
        """
            .formatted(bridge, via));
  }

  /** A one-operation effect algebra, with the attributes given to its annotation. */
  private static JavaFileObject algebra(String name, String attributes) {
    return source(name, algebraBody(name, attributes));
  }

  /** The declaration of {@link #algebra}, for a writer to write. */
  private static String algebraBody(String name, String attributes) {
    return """
        @EffectAlgebra(%s)
        public sealed interface %s<A> permits %s.Only {
          <B> %s<B> mapK(Function<? super A, ? extends B> f);

          record Only<A>(String text, Function<Unit, A> k) implements %s<A> {
            @Override
            public <B> %s<B> mapK(Function<? super A, ? extends B> f) {
              return new Only<>(text, k.andThen(f));
            }
          }
        }
        """
        .formatted(attributes, name, name, name, name, name);
  }

  /** Composes the console and database algebras, with the attributes given to its annotation. */
  private static JavaFileObject composition(String attributes) {
    return source(
        "AppEffects",
        """
        @ComposeEffects(%s)
        public record AppEffects(Class<ConsoleOp<?>> console, Class<DbOp<?>> db) {}
        """
            .formatted(attributes));
  }

  private static final JavaFileObject BOX_KIND =
      source(
          "BoxKind",
          """
          public interface BoxKind<A> extends Kind<BoxKind.Witness, A> {
            final class Witness implements WitnessArity<TypeArity.Unary> {}
          }
          """);

  /** An effect type, with the attributes given to its annotation beside the witness. */
  private static JavaFileObject box(String attributes) {
    return source(
        "Box",
        """
        @PathSource(witness = BoxKind.Witness.class, %s)
        public interface Box<A> {}
        """
            .formatted(attributes));
  }

  /**
   * One shape an effect processor reads an annotation value through, set to a constant another
   * processor writes in its first round, with a type it generates.
   */
  enum LaterValue {
    BRIDGE_SUFFIX(
        "a @GeneratePathBridge suffix", "UserServiceBridge", service("suffix = Names.SUFFIX", "")),
    BRIDGE_PACKAGE(
        "a @GeneratePathBridge targetPackage",
        "generated.UserServicePaths",
        service("targetPackage = Names.PACKAGE", "")),
    VIA_NAME("a @PathVia name", "UserServicePaths", service("", "name = Names.NAME")),
    VIA_DOC("a @PathVia doc", "UserServicePaths", service("", "doc = Names.DOC")),
    VIA_COMPOSABLE(
        "a @PathVia composable flag", "UserServicePaths", service("", "composable = Names.FLAG")),
    INHERITED_VIA_NAME(
        "a @PathVia name on a method the bridged interface inherits",
        "UserServicePaths",
        source(
            "Lookups",
            """
            public interface Lookups {
              @PathVia(name = Names.NAME)
              Optional<String> findById(Long id);
            }
            """),
        source(
            "UserService",
            """
            @GeneratePathBridge
            public interface UserService extends Lookups {}
            """)),
    ALGEBRA_PACKAGE(
        "an @EffectAlgebra targetPackage",
        "generated.ConsoleOpOps",
        algebra("ConsoleOp", "targetPackage = Names.PACKAGE")),
    COMPOSITION_PACKAGE(
        "a @ComposeEffects targetPackage",
        "generated.AppEffectsSupport",
        algebra("ConsoleOp", ""),
        algebra("DbOp", ""),
        composition("targetPackage = Names.PACKAGE")),
    COMPOSED_ALGEBRA_PACKAGE(
        "the @EffectAlgebra targetPackage of an algebra a composition names",
        "AppEffectsSupport",
        algebra("ConsoleOp", "targetPackage = Names.PACKAGE"),
        algebra("DbOp", ""),
        composition("")),
    PATH_SOURCE_SUFFIX("a @PathSource suffix", "BoxBridge", BOX_KIND, box("suffix = Names.SUFFIX")),
    PATH_SOURCE_PACKAGE(
        "a @PathSource targetPackage",
        "generated.BoxPath",
        BOX_KIND,
        box("targetPackage = Names.PACKAGE"));

    private final String description;
    private final String generated;
    private final List<JavaFileObject> sources;

    LaterValue(String description, String generated, JavaFileObject... sources) {
      this.description = description;
      this.generated = PKG + "." + generated;
      this.sources = List.of(sources);
    }

    @Override
    public String toString() {
      return description;
    }
  }

  /**
   * The writer goes first, then the effect processors: javac does not offer a round to a processor
   * supporting every annotation once an earlier processor has claimed that round's annotations.
   */
  private static Compilation compile(TypeWriter writer, List<JavaFileObject> sources) {
    return javac()
        .withProcessors(
            writer,
            new EffectAlgebraProcessor(),
            new ComposeEffectsProcessor(),
            new PathProcessor(),
            new PathSourceProcessor(),
            new CompanionAnnotationProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  /** Every source the effect processors generated, by name, with its content. */
  private static Map<String, String> generatedSources(Compilation compilation, TypeWriter writer) {
    return compilation.generatedSourceFiles().stream()
        .filter(file -> !writer.wrote(file))
        .collect(Collectors.toMap(JavaFileObject::getName, EffectRoundsTest::content));
  }

  private static String content(JavaFileObject file) {
    try {
      return file.getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Nested
  @DisplayName("Each effect annotation value, set to a constant a later round brings")
  class LaterValues {

    @ParameterizedTest(name = "{0}")
    @EnumSource(LaterValue.class)
    @DisplayName("waits for the constant, and generates what it would were it written by hand")
    void anElementWaitsForTheConstant(LaterValue later) {
      TypeWriter writer = new TypeWriter(Map.of("Names", NAMES));
      Compilation compilation = compile(writer, later.sources);
      TypeWriter none = new TypeWriter(Map.of());
      Compilation byHand =
          compile(
              none,
              Stream.concat(later.sources.stream(), Stream.of(source("Names", NAMES))).toList());

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(byHand).succeededWithoutWarnings();
      Assertions.assertThat(writer.roots()).contains(later.generated);
      Assertions.assertThat(generatedSources(compilation, writer))
          .isEqualTo(generatedSources(byHand, none));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LaterValue.class)
    @DisplayName("a constant no round brings generates nothing, and leaves the report to javac")
    void aConstantThatNeverAppearsIsJavacsError(LaterValue later) {
      TypeWriter writer = new TypeWriter(Map.of());
      Compilation compilation = compile(writer, later.sources);

      assertThat(compilation).failed();
      Assertions.assertThat(compilation.errors())
          .isNotEmpty()
          .allSatisfy(
              error -> {
                Assertions.assertThat(error.getMessage(null)).contains("cannot find symbol");
                Assertions.assertThat(error.getSource()).isNotNull();
              });
      Assertions.assertThat(writer.roots()).doesNotContain(later.generated);
    }
  }

  /** An algebra another processor writes for an interpreter to name. */
  private static final String LATER_OP =
      """
      public sealed interface LaterOp<A> permits LaterOp.Only {
        record Only<A>(String text) implements LaterOp<A> {}
      }
      """;

  /** An interpreter for {@link #LATER_OP} that handles none of its operations. */
  private static final JavaFileObject INTERPRETER =
      source(
          "LaterInterpreter",
          """
          @Handles(LaterOp.class)
          public final class LaterInterpreter {}
          """);

  private static List<String> diagnostics(Compilation compilation) {
    return compilation.diagnostics().stream()
        .map(diagnostic -> diagnostic.getKind() + ": " + diagnostic.getMessage(null))
        .toList();
  }

  @Test
  @DisplayName(
      "an interpreter naming an algebra a later round brings is checked in that round, as it would"
          + " be were the algebra written by hand")
  void anInterpreterIsCheckedInTheRoundThatBringsItsAlgebra() {
    Compilation compilation =
        compile(new TypeWriter(Map.of("LaterOp", LATER_OP)), List.of(INTERPRETER));
    Compilation byHand =
        compile(new TypeWriter(Map.of()), List.of(INTERPRETER, source("LaterOp", LATER_OP)));

    assertThat(compilation).hadErrorContaining("No handler for operation: Only");
    Assertions.assertThat(diagnostics(compilation)).isEqualTo(diagnostics(byHand));
  }

  @Test
  @DisplayName(
      "a Path over a witness a helper ties to an algebra still waiting for its targetPackage is"
          + " generated as it would be were the constant written by hand")
  void aPathOverTheWitnessOfAWaitingAlgebra() {
    // A Kind and helper written by hand beside the algebra, not where its targetPackage puts the
    // ones the algebra's processor writes.
    List<JavaFileObject> sources =
        List.of(
            algebra("ConsoleOp", "targetPackage = Names.PACKAGE"),
            source(
                "ConsoleOpKind",
                """
                public interface ConsoleOpKind<A> extends Kind<ConsoleOpKind.Witness, A> {
                  final class Witness implements WitnessArity<TypeArity.Unary> {}
                }
                """),
            source(
                "ConsoleOpKindHelper",
                """
                public final class ConsoleOpKindHelper {
                  private ConsoleOpKindHelper() {}

                  public static <A> ConsoleOp<A> narrow(Kind<ConsoleOpKind.Witness, A> kind) {
                    throw new UnsupportedOperationException();
                  }
                }
                """),
            source(
                "Console",
                """
                @PathSource(witness = ConsoleOpKind.Witness.class)
                public interface Console<A> {}
                """));
    TypeWriter writer = new TypeWriter(Map.of("Names", NAMES));
    Compilation compilation = compile(writer, sources);
    TypeWriter none = new TypeWriter(Map.of());
    Compilation byHand =
        compile(none, Stream.concat(sources.stream(), Stream.of(source("Names", NAMES))).toList());

    assertThat(compilation).succeededWithoutWarnings();
    assertThat(byHand).succeededWithoutWarnings();
    Assertions.assertThat(generatedSources(compilation, writer))
        .isEqualTo(generatedSources(byHand, none));
    Assertions.assertThat(diagnostics(compilation)).isEqualTo(diagnostics(byHand));
  }

  @Test
  @DisplayName(
      "a composition refuses a component that is not a Class at once, rather than waiting for the"
          + " algebra it names")
  void aCompositionRefusesAComponentThatIsNotAClassAtOnce() {
    Compilation compilation =
        compile(
            new TypeWriter(Map.of()),
            List.of(
                algebra("ConsoleOp", "targetPackage = Names.PACKAGE"),
                algebra("DbOp", ""),
                source("Holder", "public interface Holder<T> {}"),
                source(
                    "AppEffects",
                    """
                    @ComposeEffects
                    public record AppEffects(Holder<ConsoleOp<?>> console, Class<DbOp<?>> db) {}
                    """)));

    assertThat(compilation).hadErrorContaining("field 'console' is");
    assertThat(compilation).hadErrorContaining("cannot find symbol");
  }

  @Test
  @DisplayName(
      "a composition naming an algebra a later round brings generates what it would were the"
          + " algebra written by hand")
  void aCompositionWaitsForAnAlgebraALaterRoundBrings() {
    List<JavaFileObject> sources = List.of(algebra("DbOp", ""), composition(""));
    TypeWriter writer = new TypeWriter(Map.of("ConsoleOp", algebraBody("ConsoleOp", "")));
    Compilation compilation = compile(writer, sources);
    TypeWriter none = new TypeWriter(Map.of());
    Compilation byHand =
        compile(
            none, Stream.concat(sources.stream(), Stream.of(algebra("ConsoleOp", ""))).toList());

    assertThat(compilation).succeededWithoutWarnings();
    assertThat(byHand).succeededWithoutWarnings();
    Assertions.assertThat(writer.roots()).contains(PKG + ".AppEffectsSupport");
    Assertions.assertThat(generatedSources(compilation, writer))
        .isEqualTo(generatedSources(byHand, none));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      classes = {
        ComposeEffects.class,
        EffectAlgebra.class,
        GeneratePathBridge.class,
        Handles.class,
        PathSource.class,
        PathVia.class
      })
  @DisplayName(
      "every member of an effect annotation takes one value, which is javac's mark or is not")
  void everyMemberTakesOneValue(Class<?> annotation) {
    // The waiting reads each value as a whole, never an array's elements or a nested annotation's.
    Assertions.assertThat(annotation.getDeclaredMethods())
        .isNotEmpty()
        .allSatisfy(
            member -> {
              Assertions.assertThat(member.getReturnType().isArray()).isFalse();
              Assertions.assertThat(member.getReturnType().isAnnotation()).isFalse();
            });
  }

  @Test
  @DisplayName(
      "a composition refuses each component that resolves while another waits, as it would were"
          + " both written by hand")
  void aCompositionRefusesWhatResolvesWhileAnotherWaits() {
    Compilation compilation =
        compile(
            new TypeWriter(Map.of()),
            List.of(
                source(
                    "AppEffects",
                    """
                    @ComposeEffects
                    public record AppEffects(String bad, Class<ConsolOp<?>> console) {}
                    """)));

    assertThat(compilation).hadErrorContaining("field 'bad' is java.lang.String");
    assertThat(compilation).hadErrorContaining("cannot find symbol");
  }
}

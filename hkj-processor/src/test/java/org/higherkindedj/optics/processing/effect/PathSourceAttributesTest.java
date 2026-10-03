// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing.effect;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.higherkindedj.hkt.effect.annotation.PathSource;
import org.higherkindedj.optics.processing.CompanionAnnotationProcessor;
import org.higherkindedj.optics.processing.GeneratorTestHelper;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What each {@code @PathSource} attribute does to the generated Path: the class name its suffix
 * gives, the attributes refused at the annotation, the kinds of type it applies to, the methods it
 * generates, the attributes that wait for a later round, the note for a witness an effect algebra
 * generates, the notes for recovery asked for by halves, and the deprecated capability levels.
 */
@DisplayName("@PathSource attributes")
class PathSourceAttributesTest {

  private static final JavaFileObject WITNESS =
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
          """);

  private static final JavaFileObject ERROR =
      JavaFileObjects.forSourceString(
          "com.example.Err",
          """
          package com.example;

          public record Err(String message) {}
          """);

  /** Annotates {@code Box} with the witness and each attribute given, one per line. */
  private static JavaFileObject box(String... attributes) {
    return annotated("Box", attributes);
  }

  private static JavaFileObject annotated(String typeName, String... attributes) {
    return JavaFileObjects.forSourceString(
        "com.example." + typeName,
        """
        package com.example;

        import org.higherkindedj.hkt.effect.annotation.PathSource;

        @PathSource(
            witness = BoxKind.Witness.class"""
            + Arrays.stream(attributes).map(",\n    "::concat).collect(Collectors.joining())
            + ")\npublic interface "
            + typeName
            + "<A> {}\n");
  }

  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new PathSourceProcessor(), new CompanionAnnotationProcessor())
        .withOptions("-Xlint:all,-removal", "-Werror")
        .compile(Stream.concat(Stream.of(WITNESS, ERROR), Arrays.stream(sources)).toList());
  }

  /** Writes one source in the first round, so that javac runs a second. */
  @SupportedAnnotationTypes("*")
  private static final class SecondRound extends AbstractProcessor {
    private boolean written;

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (!written) {
        written = true;
        try (Writer writer =
            processingEnv.getFiler().createSourceFile("m1/com.example.Second").openWriter()) {
          writer.write("package com.example; class Second {}");
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      return false;
    }
  }

  private static String generated(Compilation compilation, String className) throws IOException {
    return compilation
        .generatedSourceFile("com.example." + className)
        .orElseThrow()
        .getCharContent(true)
        .toString();
  }

  /** A one-operation effect algebra, with the annotation lines given. */
  private static JavaFileObject consoleOp(String annotations) {
    return JavaFileObjects.forSourceString(
        "com.example.ConsoleOp",
        """
        package com.example;

        import org.higherkindedj.hkt.effect.annotation.EffectAlgebra;
        import org.higherkindedj.hkt.effect.annotation.PathSource;

        """
            + annotations
            + """

            public sealed interface ConsoleOp<A> permits ConsoleOp.PrintLine {
              record PrintLine<A>(String message) implements ConsoleOp<A> {}
            }
            """);
  }

  private static List<String> pathSourceNotes(Compilation compilation) {
    return compilation.notes().stream()
        .map(note -> note.getMessage(null))
        .filter(message -> message.startsWith("@PathSource"))
        .toList();
  }

  @Nested
  @DisplayName("Class name")
  class GeneratedName {

    @Test
    @DisplayName("toString names the generated class, suffix included")
    void toStringNamesTheGeneratedClass() throws IOException {
      final Compilation compilation = compile(box("suffix = \"Effect\""));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(generated(compilation, "BoxEffect"))
          .contains("return \"BoxEffect(\" + kind + \")\";");
    }
  }

  @Nested
  @DisplayName("Refused attributes")
  class Refused {

    @Test
    @DisplayName("an empty suffix that would land the Path on the annotated type itself")
    void emptySuffixOnTheTypeItself() {
      final JavaFileObject box = box("suffix = \"\"");

      final Compilation compilation = compile(box);

      assertThat(compilation)
          .hadErrorContaining(
              "@PathSource: suffix \"\" would name the generated Path 'com.example.Box'. That is"
                  + " the type it is generated for. Give a non-empty suffix, remove suffix to use"
                  + " the default \"Path\", or set a targetPackage to write the Path elsewhere.")
          .inFile(box)
          .onLineContaining("suffix");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a suffix naming a type already declared in the compilation")
    void suffixNamingADeclaredType() {
      final JavaFileObject box = box("suffix = \"Kind\"");

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: suffix \"Kind\" would name the generated Path 'com.example.BoxKind'."
                  + " A type of that name is already declared in the compilation. Set a suffix that"
                  + " names a free class, or rename 'com.example.BoxKind'.")
          .inFile(box)
          .onLineContaining("suffix");
    }

    @Test
    @DisplayName("the default suffix naming a type already declared, reported at the annotation")
    void defaultSuffixNamingADeclaredType() {
      final JavaFileObject box = box();
      final JavaFileObject handWritten =
          JavaFileObjects.forSourceString(
              "com.example.BoxPath", "package com.example; public class BoxPath {}");

      assertThat(compile(box, handWritten))
          .hadErrorContaining(
              "@PathSource: the default suffix \"Path\" would name the generated Path"
                  + " 'com.example.BoxPath'.")
          .inFile(box)
          .onLineContaining("@PathSource");
    }

    @Test
    @DisplayName("not a class file of the Path's name, such as a previous build's on the classpath")
    void notAClassFileOfThePathsName(@TempDir Path previousBuild) throws IOException {
      final Compilation first = compile(box());
      assertThat(first).succeeded();

      // javac itself warns, under -Xlint:processing, that the type is already on the classpath.
      final Compilation again =
          javac()
              .withProcessors(new PathSourceProcessor(), new CompanionAnnotationProcessor())
              .withClasspath(classpathWith(classDirectory(first, previousBuild)))
              .compile(WITNESS, ERROR, box());

      assertThat(again).succeeded();
      assertThat(again.generatedSourceFile("com.example.BoxPath")).isPresent();
    }

    @ParameterizedTest(name = "suffix \"{0}\"")
    @ValueSource(strings = {"-x", ".Path", "​"})
    @DisplayName("a suffix that does not continue a Java identifier")
    void suffixThatIsNoIdentifier(String suffix) {
      final JavaFileObject box = box("suffix = \"" + suffix + "\"");

      final Compilation compilation = compile(box);

      assertThat(compilation)
          .hadErrorContaining(
              "would name the generated Path 'Box" + suffix + "', which is not a Java identifier.")
          .inFile(box)
          .onLineContaining("suffix");
      assertThat(compilation)
          .hadErrorContaining(
              "Remove suffix to use the default \"Path\", or give a suffix of letters, digits, '_'"
                  + " or '$'.");
    }

    @ParameterizedTest(name = "{0} + \"{1}\"")
    @CsvSource({"i, f", "va, r", "recor, d"})
    @DisplayName("a suffix that makes a name Java does not allow for a class")
    void suffixMakingAReservedName(String typeName, String suffix) {
      final JavaFileObject lowercase = annotated(typeName, "suffix = \"" + suffix + "\"");

      assertThat(compile(lowercase))
          .hadErrorContaining(
              "would name the generated Path '"
                  + typeName
                  + suffix
                  + "', which Java does not allow as the name of a class. The Path class is"
                  + " named with the annotated type's name followed by the suffix. Remove suffix"
                  + " to use the default \"Path\", or give a suffix that makes another name.")
          .inFile(lowercase)
          .onLineContaining("suffix");
    }

    @Test
    @DisplayName("a targetPackage that is not a package name")
    void targetPackageThatIsNoPackageName() {
      final JavaFileObject box = box("targetPackage = \"not valid!\"");

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: targetPackage \"not valid!\" is not a package name. The generated Path"
                  + " is written into that package. Remove targetPackage to write it beside 'Box',"
                  + " or give a package name such as \"com.example.paths\".")
          .inFile(box)
          .onLineContaining("targetPackage");
    }

    @ParameterizedTest(name = "witness = {0}.class")
    @ValueSource(strings = {"int", "void", "String[]"})
    @DisplayName("a witness that is not a class")
    void witnessThatIsNoClass(String witness) {
      final JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              "package com.example;\n\n"
                  + "import org.higherkindedj.hkt.effect.annotation.PathSource;\n\n"
                  + "@PathSource(\n"
                  + "    witness = "
                  + witness
                  + ".class)\n"
                  + "public interface Box<A> {}\n");

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: witness '"
                  + witness
                  + "' is not a class. The witness is the marker class the effect's Kind is"
                  + " indexed by, and the generated Path wraps a Kind of it. Name the effect's"
                  + " witness marker, such as BoxKind.Witness.class.")
          .inFile(box)
          .onLineContaining("witness");
    }

    @Test
    @DisplayName("a primitive errorType on RECOVERABLE")
    void primitiveErrorTypeOnRecoverable() {
      final JavaFileObject box =
          box("errorType = int.class", "capability = PathSource.Capability.RECOVERABLE");

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: errorType 'int' is not a reference type. Recovery takes a MonadError"
                  + " over the error type, and a type argument must be a reference type. Use a"
                  + " reference type for the error, such as a record describing it, or remove"
                  + " errorType.")
          .inFile(box)
          .onLineContaining("errorType");
    }

    @ParameterizedTest(name = "errorType = {0}.class")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "java.util.List | List | 'List' is generic",
          "java.util.List[] | List[] | 'List[]' names the generic 'List'",
          "java.util.Map.Entry | Map.Entry | 'Map.Entry' is generic"
        })
    @DisplayName("a generic errorType on RECOVERABLE, which a class literal names only raw")
    void genericErrorTypeOnRecoverable(String errorType, String name, String what) {
      final JavaFileObject box =
          box(
              "errorType = " + errorType + ".class",
              "capability = PathSource.Capability.RECOVERABLE");

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: errorType "
                  + what
                  + ", and a class literal can name only its raw type. The generated of and pure"
                  + " would take a MonadError over the raw type, so a MonadError over a"
                  + " parameterised one could not be passed to them. Use a non-generic error type,"
                  + " such as a record with a '"
                  + name
                  + "' component, or remove errorType.")
          .inFile(box)
          .onLineContaining("errorType");
    }

    @ParameterizedTest(name = "witness = {0}.class")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "org.higherkindedj.hkt.either.EitherKind.Witness | 'EitherKind.Witness' is generic, and a"
              + " class literal can name only its raw type. The generated Path would wrap a Kind of"
              + " the raw witness, which no Kind of a parameterised one could be passed as. Name a"
              + " witness without type parameters, or use GenericPath, which takes a Kind of the"
              + " witness with its type arguments.",
          "String | 'String' is not a witness of one type parameter. The generated Path wraps a"
              + " Kind of it and takes a Monad over it, which both need it to implement"
              + " WitnessArity<TypeArity.Unary>. Name the effect's witness marker, such as"
              + " BoxKind.Witness.class."
        })
    @DisplayName("a generic witness, or a class that is no witness of one type parameter")
    void witnessNoPathCanWrap(String witness, String message) {
      final JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              "package com.example;\n\n"
                  + "import org.higherkindedj.hkt.effect.annotation.PathSource;\n\n"
                  + "@PathSource(\n"
                  + "    witness = "
                  + witness
                  + ".class)\n"
                  + "public interface Box<A> {}\n");

      assertThat(compile(box))
          .hadErrorContaining("@PathSource: witness " + message)
          .inFile(box)
          .onLineContaining("witness");
    }

    @Test
    @DisplayName("an inner class of a generic class, which a class literal names only raw")
    void innerClassOfAGenericClass() {
      final JavaFileObject box =
          box("errorType = Outer.Inner.class", "capability = PathSource.Capability.RECOVERABLE");
      final JavaFileObject outer =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              public class Outer<T> {
                public class Inner {}
              }
              """);

      assertThat(compile(box, outer))
          .hadErrorContaining(
              "@PathSource: errorType 'Outer.Inner' names the generic 'Outer', and a class literal"
                  + " can name only its raw type.")
          .inFile(box)
          .onLineContaining("errorType");
    }

    @Test
    @DisplayName("without hkj-api on the classpath, a witness is not checked for its arity")
    void witnessArityUncheckedWithoutHkjApi() {
      final Compilation compilation =
          javac()
              .withProcessors(new PathSourceProcessor())
              .withClasspath(List.of(GeneratorTestHelper.locationOf(PathSource.class).toFile()))
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Box",
                      """
                      package com.example;

                      import org.higherkindedj.hkt.effect.annotation.PathSource;

                      @PathSource(witness = Box.Witness.class)
                      public interface Box<A> {
                        final class Witness {}
                      }
                      """));

      assertThat(compilation.errors().stream().map(error -> error.getMessage(null)))
          .isNotEmpty()
          .noneMatch(message -> message.startsWith("@PathSource"));
    }

    @Test
    @DisplayName("several at once, each reported")
    void severalReported() {
      final Compilation compilation =
          compile(
              box(
                  "suffix = \"-x\"",
                  "targetPackage = \"not valid!\"",
                  "errorType = void.class",
                  "capability = PathSource.Capability.RECOVERABLE"));

      assertThat(compilation).hadErrorCount(3);
      assertThat(compilation).hadErrorContaining("suffix \"-x\"");
      assertThat(compilation).hadErrorContaining("targetPackage \"not valid!\"");
      assertThat(compilation).hadErrorContaining("errorType 'void' is not a reference type");
    }
  }

  @Nested
  @DisplayName("An empty suffix kept apart from the annotated type")
  class EmptySuffixElsewhere {

    @Test
    @DisplayName("in another package, names the Path after the type")
    void inAnotherPackage() {
      final Compilation compilation =
          compile(box("suffix = \"\"", "targetPackage = \"com.example.paths\""));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation.generatedSourceFile("com.example.paths.Box")).isPresent();
    }

    @Test
    @DisplayName("on a nested type, names the Path after the nested type")
    void onANestedType() {
      final Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Outer",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.PathSource;

                  public class Outer {
                    @PathSource(witness = BoxKind.Witness.class, suffix = "")
                    public interface Inner<A> {}
                  }
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation.generatedSourceFile("com.example.Inner")).isPresent();
    }
  }

  @Nested
  @DisplayName("Annotated kinds")
  class AnnotatedKinds {

    @Test
    @DisplayName("a record generates its Path")
    void recordGeneratesItsPath() {
      final Compilation compilation = compile(declared("public record Box<A>(A value) {}"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation.generatedSourceFile("com.example.BoxPath")).isPresent();
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(
        delimiter = '|',
        quoteCharacter = '"',
        value = {
          "public enum Box { ONE } | an enum | an enum is a fixed set of constants.",
          "public @interface Box {} | an annotation interface | an annotation interface holds"
              + " annotation elements."
        })
    @DisplayName("an enum or an annotation interface is refused")
    void enumOrAnnotationInterfaceRefused(String declaration, String kind, String why) {
      final JavaFileObject box = declared(declaration);

      assertThat(compile(box))
          .hadErrorContaining(
              "@PathSource: 'Box' is "
                  + kind
                  + ". A Path wraps the values of an effect type, and "
                  + why
                  + " Put @PathSource on the class, interface or record whose Kind the witness"
                  + " indexes, or remove it.")
          .inFile(box)
          .onLineContaining("@PathSource");
    }

    /** {@code Box} declared as given, annotated with the witness alone. */
    private static JavaFileObject declared(String declaration) {
      return JavaFileObjects.forSourceString(
          "com.example.Box",
          """
          package com.example;

          import org.higherkindedj.hkt.effect.annotation.PathSource;

          @PathSource(witness = BoxKind.Witness.class)
          """
              + declaration
              + "\n");
    }
  }

  @Nested
  @DisplayName("Generated methods")
  class GeneratedMethods {

    @Test
    @DisplayName("peek runs its action when a lazy effect runs, each time it runs")
    void peekRunsWithTheEffect() throws ReflectiveOperationException {
      final JavaFileObject lazy =
          JavaFileObjects.forSourceString(
              "com.example.Lazy",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;
              import org.higherkindedj.hkt.io.IOKind;

              @PathSource(witness = IOKind.Witness.class)
              public interface Lazy<A> {}
              """);
      final JavaFileObject harness =
          JavaFileObjects.forSourceString(
              "com.example.PeekRuns",
              """
              package com.example;

              import static org.higherkindedj.hkt.io.IOKindHelper.IO_OP;

              import java.util.concurrent.atomic.AtomicInteger;
              import org.higherkindedj.hkt.io.IO;
              import org.higherkindedj.hkt.io.IOMonad;

              public final class PeekRuns {
                /** The actions seen before running, the result, and the actions after two runs. */
                public static int[] run() {
                  AtomicInteger seen = new AtomicInteger();
                  IO<Integer> io =
                      IO_OP.narrow(
                          LazyPath.of(IO_OP.widen(IO.delay(() -> 41)), IOMonad.INSTANCE)
                              .peek(a -> seen.incrementAndGet())
                              .map(a -> a + 1)
                              .run());
                  int before = seen.get();
                  int result = io.unsafeRunSync();
                  io.unsafeRunSync();
                  return new int[] {before, result, seen.get()};
                }
              }
              """);

      final Object seen =
          RuntimeCompilationHelper.compileWith(new PathSourceProcessor(), lazy, harness)
              .loadClass("com.example.PeekRuns")
              .getMethod("run")
              .invoke(null);

      assertThat((int[]) seen).containsExactly(0, 42, 2);
    }

    @Test
    @DisplayName("peek and zipWith say what they return and take")
    void peekAndZipWithSayWhatTheyReturnAndTake() throws IOException {
      assertThat(generated(compile(box()), "BoxPath"))
          .contains(
              "@return a new BoxPath over the same value that performs the action",
              "@param other the other path, which must be a BoxPath; must not be null",
              "@throws IllegalArgumentException if {@code other} is not a BoxPath");
    }
  }

  @Nested
  @DisplayName("Unresolved attributes")
  class Unresolved {

    @Test
    @DisplayName(
        "nothing is written in the last round, which javac makes of the round after an error")
    void nothingWrittenInTheLastRound() {
      final Compilation compilation =
          javac()
              .withProcessors(
                  new EffectAlgebraProcessor(),
                  new PathSourceProcessor(),
                  new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all,-removal")
              .compile(
                  WITNESS,
                  box("suffix = \"-x\""),
                  consoleOp("@EffectAlgebra\n@PathSource(witness = ConsoleOpKind.Witness.class)"));

      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation).hadErrorContaining("suffix \"-x\"");
      assertThat(compilation.warnings().stream().map(warning -> warning.getMessage(null)))
          .noneMatch(message -> message.contains("last round"));
    }

    @Test
    @DisplayName("waiting types two modules declare under one name each wait in their own module")
    void waitingAcrossModules(@TempDir Path dir) throws IOException {
      final JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = Missing.class)
              public interface Box<A> {}
              """);

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          GeneratorTestHelper.compileModules(
              dir,
              // First: javac offers a round to no later processor once every annotation is claimed.
              List.of(new SecondRound(), new PathSourceProcessor()),
              Map.of("m1", List.of(box), "m2", List.of(box)));

      assertThat(diagnostics.stream().map(diagnostic -> diagnostic.getMessage(null)))
          .filteredOn(message -> message.contains("cannot find symbol"))
          .hasSize(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "witness = Missing.class",
          "witness = MISSING",
          "witness = BoxKind.Witness.class, errorType = Missing.class",
          "witness = BoxKind.Witness.class, suffix = Missing.SUFFIX"
        })
    @DisplayName("one that never resolves generates nothing, and leaves the report to javac")
    void neverResolved(String attributes) {
      final Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Box",
                  "package com.example;\n\n"
                      + "import org.higherkindedj.hkt.effect.annotation.PathSource;\n\n"
                      + "@PathSource("
                      + attributes
                      + ")\n"
                      + "public interface Box<A> {}\n"));

      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation).hadErrorContaining("cannot find symbol");
    }
  }

  @Nested
  @DisplayName("A witness an effect algebra generates")
  class EffectAlgebraWitness {

    private static final JavaFileObject CONSOLE_OP = consoleOp("@EffectAlgebra");

    private static final JavaFileObject CONSOLE =
        JavaFileObjects.forSourceString(
            "com.example.Console",
            """
            package com.example;

            import org.higherkindedj.hkt.effect.annotation.PathSource;

            @PathSource(witness = ConsoleOpKind.Witness.class)
            public interface Console<A> {}
            """);

    private static String note(String pathClassName) {
      return "@PathSource: '"
          + pathClassName
          + "' needs a Monad<ConsoleOpKind.Witness>, and the effect algebra 'ConsoleOp' has a"
          + " Functor only. The Path's of and pure take that Monad, and @EffectAlgebra generates"
          + " none, since an algebra's operations are instructions: Free chains them, and an"
          + " interpreter runs them. Build programs from ConsoleOpOps and wrap each in a FreePath"
          + " with Path.free(program, ConsoleOpFunctor.instance()), then remove @PathSource.";
    }

    private static Compilation compileWithAlgebras(JavaFileObject... sources) {
      return javac()
          .withProcessors(
              new EffectAlgebraProcessor(),
              new PathSourceProcessor(),
              new CompanionAnnotationProcessor())
          // An algebra's generated types carry @NullMarked, which no processor here claims.
          .withOptions("-Xlint:all,-removal,-processing", "-Werror")
          .compile(sources);
    }

    @Test
    @DisplayName("on the algebra itself is noted once, at the witness, after the round it waits")
    void onTheAlgebraItself() {
      // The witness on a line of its own tells the value from the annotation.
      final JavaFileObject consoleOp =
          consoleOp("@EffectAlgebra\n@PathSource(\n    witness = ConsoleOpKind.Witness.class)");

      final Compilation compilation = compileWithAlgebras(consoleOp);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(note("ConsoleOpPath"))
          .inFile(consoleOp)
          .onLineContaining("witness");
      assertThat(pathSourceNotes(compilation)).hasSize(1);
      assertThat(compilation.generatedSourceFile("com.example.ConsoleOpPath")).isPresent();
    }

    @Test
    @DisplayName("on another type, with the algebra's types in a targetPackage, is noted at it")
    void onAnotherTypeWithATargetPackage() {
      final JavaFileObject console =
          JavaFileObjects.forSourceString(
              "com.example.Console",
              """
              package com.example;

              import com.example.generated.ConsoleOpKind;
              import org.higherkindedj.hkt.effect.annotation.PathSource;

              @PathSource(witness = ConsoleOpKind.Witness.class)
              public interface Console<A> {}
              """);

      final Compilation compilation =
          compileWithAlgebras(
              consoleOp("@EffectAlgebra(targetPackage = \"com.example.generated\")"), console);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(note("ConsolePath"))
          .inFile(console)
          .onLineContaining("witness");
    }

    @Test
    @DisplayName("from an algebra compiled earlier, as a library's, is noted")
    void fromACompiledAlgebra(@TempDir Path library) throws IOException {
      final Compilation algebra = compileWithAlgebras(CONSOLE_OP);
      assertThat(algebra).succeeded();

      final Compilation compilation =
          javac()
              .withProcessors(new PathSourceProcessor(), new CompanionAnnotationProcessor())
              .withOptions("-Xlint:all,-removal", "-Werror")
              .withClasspath(classpathWith(classDirectory(algebra, library)))
              .compile(CONSOLE);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).hadNoteContaining(note("ConsolePath")).inFile(CONSOLE);
    }

    @Test
    @DisplayName("is the only note, where an errorType would draw one offering RECOVERABLE")
    void inPlaceOfTheRecoveryNote() {
      final Compilation compilation =
          compileWithAlgebras(
              consoleOp(
                  "@EffectAlgebra\n"
                      + "@PathSource(witness = ConsoleOpKind.Witness.class, errorType = String.class)"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(pathSourceNotes(compilation)).containsExactly(note("ConsoleOpPath"));
    }

    @Test
    @DisplayName("is not drawn by another Kind's helper, whatever its narrow overloads return")
    void notForAnotherKindsHelper() {
      final Compilation compilation =
          compileWithAlgebras(
              CONSOLE_OP,
              JavaFileObjects.forSourceString(
                  "com.example.TraceKind",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;

                  public interface TraceKind<A> extends Kind<TraceKind.Witness, A> {
                    final class Witness implements WitnessArity<TypeArity.Unary> {}
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.TraceKindHelper",
                  """
                  package com.example;

                  public final class TraceKindHelper {
                    public static <A> ConsoleOp<A> narrow(TraceKind<A> kind) {
                      throw new UnsupportedOperationException();
                    }

                    public static <T> T narrow(Object kind) {
                      throw new UnsupportedOperationException();
                    }
                  }
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Trace",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.effect.annotation.PathSource;

                  @PathSource(witness = TraceKind.Witness.class)
                  public interface Trace<A> {}
                  """));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(pathSourceNotes(compilation)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        textBlock =
            """
            a witness nested in a Kind with no helper | BoxKind.Witness
            a witness whose helper narrows to a type no algebra is | org.higherkindedj.hkt.maybe.MaybeKind.Witness
            a top-level witness | TopWitness
            """)
    @DisplayName("an ordinary witness draws no note")
    void noNoteForAnOrdinaryWitness(String shape, String witness) {
      final Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.TopWitness",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;

                  public final class TopWitness implements WitnessArity<TypeArity.Unary> {}
                  """),
              JavaFileObjects.forSourceString(
                  "com.example.Box",
                  "package com.example;\n\n"
                      + "import org.higherkindedj.hkt.effect.annotation.PathSource;\n\n"
                      + "@PathSource(witness = "
                      + witness
                      + ".class)\n"
                      + "public interface Box<A> {}\n"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(pathSourceNotes(compilation)).isEmpty();
    }
  }

  @Nested
  @DisplayName("Recovery asked for by halves")
  class RecoveryByHalves {

    @Test
    @DisplayName("an errorType with the default capability is noted, and generates no recovery")
    void errorTypeWithDefaultCapability() throws IOException {
      final JavaFileObject box = box("errorType = Err.class");

      final Compilation compilation = compile(box);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "@PathSource: errorType 'Err' has no effect on the generated 'BoxPath'. The default"
                  + " capability, CHAINABLE, generates no recovery methods; recover, recoverWith"
                  + " and mapError are generated only for RECOVERABLE. For them, set capability ="
                  + " PathSource.Capability.RECOVERABLE: of and pure then take a"
                  + " MonadError<BoxKind.Witness, Err>, so existing calls must pass one. Otherwise"
                  + " remove errorType.")
          .inFile(box)
          .onLineContaining("errorType");
      assertThat(generated(compilation, "BoxPath")).doesNotContain("recover");
    }

    @Test
    @DisplayName("an errorType with a capability written below RECOVERABLE names that capability")
    void errorTypeWithCapabilityBelowRecoverable() {
      final JavaFileObject box =
          box("errorType = Err.class", "capability = PathSource.Capability.COMBINABLE");

      assertThat(compile(box))
          .hadNoteContaining(
              "Capability COMBINABLE generates no recovery methods; recover, recoverWith and"
                  + " mapError are generated only for RECOVERABLE.")
          .inFile(box)
          .onLineContaining("errorType");
    }

    @Test
    @DisplayName("RECOVERABLE without an errorType is noted, and generates CHAINABLE's class")
    void recoverableWithoutErrorType() throws IOException {
      final JavaFileObject box = box("capability = PathSource.Capability.RECOVERABLE");

      final Compilation compilation = compile(box);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "@PathSource: capability RECOVERABLE generates no recovery methods on 'BoxPath'"
                  + " without an errorType. The methods recover, recoverWith and mapError are"
                  + " typed by the error type, so the Path gets CHAINABLE's methods only. Set"
                  + " errorType to the effect's error type, or use capability ="
                  + " PathSource.Capability.CHAINABLE, which generates the same class.")
          .inFile(box)
          .onLineContaining("capability");
      assertThat(generated(compilation, "BoxPath")).isEqualTo(generated(compile(box()), "BoxPath"));
    }

    @Test
    @DisplayName("a primitive errorType with the default capability is noted, offering its removal")
    void primitiveErrorTypeWithDefaultCapability() {
      final JavaFileObject box = box("errorType = int.class");

      final Compilation compilation = compile(box);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "@PathSource: errorType 'int' has no effect on the generated 'BoxPath'. The default"
                  + " capability, CHAINABLE, generates no recovery methods; recover, recoverWith"
                  + " and mapError are generated only for RECOVERABLE. Remove errorType;"
                  + " RECOVERABLE would refuse it, since 'int' is not a reference type.")
          .inFile(box)
          .onLineContaining("errorType");
    }

    @Test
    @DisplayName("a generic errorType with the default capability is noted, offering its removal")
    void genericErrorTypeWithDefaultCapability() {
      final JavaFileObject box = box("errorType = java.util.List.class");

      final Compilation compilation = compile(box);

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation)
          .hadNoteContaining(
              "errorType 'List' has no effect on the generated 'BoxPath'. The default capability,"
                  + " CHAINABLE, generates no recovery methods; recover, recoverWith and mapError"
                  + " are generated only for RECOVERABLE. Remove errorType; RECOVERABLE would refuse"
                  + " it, since 'List' is generic, and a class literal can name only its raw type.");
    }

    @Test
    @DisplayName("no note on a type refused for a witness its Path cannot reach")
    void noNoteWhereThePathIsRefused() {
      final Compilation compilation =
          compile(
              JavaFileObjects.forSourceString(
                  "com.example.Outer",
                  """
                  package com.example;

                  import org.higherkindedj.hkt.Kind;
                  import org.higherkindedj.hkt.TypeArity;
                  import org.higherkindedj.hkt.WitnessArity;
                  import org.higherkindedj.hkt.effect.annotation.PathSource;

                  public class Outer {
                    private interface HiddenKind<A> extends Kind<HiddenKind.Witness, A> {
                      final class Witness implements WitnessArity<TypeArity.Unary> {}
                    }

                    @PathSource(witness = HiddenKind.Witness.class, errorType = Err.class)
                    public interface Inner<A> {}
                  }
                  """));

      assertThat(compilation).failed();
      assertThat(pathSourceNotes(compilation)).isEmpty();
    }

    @Test
    @DisplayName("RECOVERABLE with an errorType draws no note, and generates recovery")
    void recoverableWithErrorType() throws IOException {
      final Compilation compilation =
          compile(box("errorType = Err.class", "capability = PathSource.Capability.RECOVERABLE"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(pathSourceNotes(compilation)).isEmpty();
      assertThat(generated(compilation, "BoxPath"))
          .contains("public BoxPath<A> recover(", "public BoxPath<A> mapError(");
    }
  }

  @Nested
  @DisplayName("Deprecated capability levels")
  class DeprecatedLevels {

    @Test
    @DisplayName("EFFECTFUL is reported for removal, and generates exactly CHAINABLE's class")
    void effectfulGeneratesChainable() throws IOException {
      final JavaFileObject effectful = box("capability = PathSource.Capability.EFFECTFUL");

      final Compilation compilation =
          javac().withProcessors(new PathSourceProcessor()).compile(WITNESS, ERROR, effectful);

      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadWarningContaining("has been deprecated and marked for removal")
          .inFile(effectful)
          .onLineContaining("capability");
      assertThat(compilation.warnings().stream().map(warning -> warning.getMessage(null)))
          .allSatisfy(message -> assertThat(message).startsWith("EFFECTFUL in "));
      assertThat(generated(compilation, "BoxPath"))
          .isEqualTo(
              generated(compile(box("capability = PathSource.Capability.CHAINABLE")), "BoxPath"));
    }

    @Test
    @DisplayName("ACCUMULATING generates exactly RECOVERABLE's class")
    void accumulatingGeneratesRecoverable() throws IOException {
      final Compilation accumulating =
          compile(box("errorType = Err.class", "capability = PathSource.Capability.ACCUMULATING"));
      final Compilation recoverable =
          compile(box("errorType = Err.class", "capability = PathSource.Capability.RECOVERABLE"));

      assertThat(generated(accumulating, "BoxPath")).isEqualTo(generated(recoverable, "BoxPath"));
    }
  }
}

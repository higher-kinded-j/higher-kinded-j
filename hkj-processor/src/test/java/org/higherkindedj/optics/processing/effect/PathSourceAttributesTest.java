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
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.processing.CompanionAnnotationProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * What each {@code @PathSource} attribute does to the generated Path: the class name its suffix
 * gives, the attributes refused at the annotation, the notes for recovery asked for by halves, and
 * the deprecated capability levels.
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

  private static String generated(Compilation compilation, String className) throws IOException {
    return compilation
        .generatedSourceFile("com.example." + className)
        .orElseThrow()
        .getCharContent(true)
        .toString();
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
                  + " and mapError are generated only for RECOVERABLE. Remove errorType.")
          .inFile(box)
          .onLineContaining("errorType");
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
      assertThat(compilation.notes().stream().map(note -> note.getMessage(null)))
          .noneMatch(message -> message.startsWith("@PathSource"));
    }

    @Test
    @DisplayName("RECOVERABLE with an errorType draws no note, and generates recovery")
    void recoverableWithErrorType() throws IOException {
      final Compilation compilation =
          compile(box("errorType = Err.class", "capability = PathSource.Capability.RECOVERABLE"));

      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation.notes().stream().map(note -> note.getMessage(null)))
          .noneMatch(message -> message.startsWith("@PathSource"));
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

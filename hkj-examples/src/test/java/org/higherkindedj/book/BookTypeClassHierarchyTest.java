// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Choice;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.Natural;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.Update;
import org.higherkindedj.hkt.WitnessArity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Draws the type class hierarchy on the Type Classes introduction from {@code hkj-api} itself, and
 * holds the page's diagram, and the paragraph on the type classes the diagram leaves out, to it.
 *
 * <p>Each arrow is an {@code extends}, read from {@link Class#getInterfaces()}, and each box lists
 * the abstract methods its interface declares, in declaration order, read from the class file. The
 * page carries the diagram in a mermaid fence of its own, so the book's mermaid check parses it;
 * this test fails when that fence differs from the rendering, and prints the replacement. Every
 * public top-level interface in {@code hkj-api}'s {@code org.higherkindedj.hkt} package is placed
 * here: drawn, named in the paragraph beside the diagram, or listed as not a type class. A new one
 * fails the test until it is placed.
 */
@DisplayName("the Type Classes hierarchy is read from hkj-api")
class BookTypeClassHierarchyTest {

  /** The diagram's boxes, in the order mermaid lays them out. */
  private static final List<Class<?>> DIAGRAM =
      List.of(
          Functor.class,
          Foldable.class,
          Applicative.class,
          Traverse.class,
          Alternative.class,
          Selective.class,
          Monad.class,
          MonadError.class,
          MonadZero.class);

  /** Type classes over a unary witness that the paragraph names, as extending Monad. */
  private static final List<Class<?>> MTL =
      List.of(MonadReader.class, MonadState.class, MonadWriter.class);

  /** Type classes over a binary witness, which the paragraph says extend no other type class. */
  private static final List<Class<?>> BINARY = List.of(Bifunctor.class, Profunctor.class);

  /** Type classes over a plain type rather than a witness. */
  private static final List<Class<?>> PLAIN = List.of(Semigroup.class, Monoid.class);

  /**
   * The package's other public interfaces: the encoding's own types, the data type {@code
   * Selective} branches on, a function from one container to another, a function on a value, and
   * two holders of ready-made instances.
   */
  private static final List<Class<?>> NOT_TYPE_CLASSES =
      List.of(
          Kind.class,
          Kind2.class,
          WitnessArity.class,
          TypeArity.class,
          Choice.class,
          Natural.class,
          Update.class,
          Monoids.class,
          Semigroups.class);

  private static final Path PAGE =
      Path.of(required("hkj.book.dir")).resolve("functional/ch_intro.md");

  /** The heading id the diagram sits under, which other pages and legacy anchors link to. */
  private static final String HEADING = "{#the-hierarchy}";

  @Test
  @DisplayName("the page's hierarchy diagram is the one hkj-api's extends clauses give")
  void diagramMatchesTheApi() {
    String rendered = render();
    assertThat(String.join("\n", diagramFence()) + "\n")
        .as(
            """
            The hierarchy on hkj-book/src/functional/ch_intro.md no longer matches hkj-api. If \
            the API change is intended, replace the mermaid fence under The hierarchy with:

            %s""",
            rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName(
      "every public interface in hkj-api's hkt package is drawn, named or not a type class")
  void everyInterfaceIsPlaced() {
    assertThat(apiInterfaces())
        .as("public interfaces in hkj-api's hkt package, each placed by one list in this test")
        .containsExactlyInAnyOrderElementsOf(
            Stream.of(DIAGRAM, MTL, BINARY, PLAIN, NOT_TYPE_CLASSES)
                .flatMap(List::stream)
                .toList());
    Stream.concat(DIAGRAM.stream(), MTL.stream())
        .forEach(
            type ->
                assertThat(witnessArity(type))
                    .as("%s takes a unary witness", type.getSimpleName())
                    .isEqualTo(TypeArity.Unary.class));
    BINARY.forEach(
        type ->
            assertThat(witnessArity(type))
                .as("%s takes a binary witness", type.getSimpleName())
                .isEqualTo(TypeArity.Binary.class));
    PLAIN.forEach(
        type ->
            assertThat(witnessArity(type))
                .as("%s takes a plain type", type.getSimpleName())
                .isNull());
  }

  @Test
  @DisplayName("the paragraph beside the diagram says what the other type classes extend")
  void paragraphMatchesTheApi() {
    String paragraph = String.join(" ", besideDiagram());

    MTL.forEach(type -> assertThat(type.getInterfaces()).containsExactly(Monad.class));
    BINARY.forEach(type -> assertThat(type.getInterfaces()).isEmpty());
    assertThat(Semigroup.class.getInterfaces()).isEmpty();
    assertThat(Monoid.class.getInterfaces()).containsExactly(Semigroup.class);
    assertThat(paragraph)
        .contains(
            codes(MTL) + " extend `Monad` too",
            codes(BINARY) + " take a binary witness",
            "extend no other type class",
            codes(PLAIN) + " take a plain type rather than a witness",
            "`Monoid` extends `Semigroup`");
  }

  /** The hierarchy as the page's mermaid fence holds it, fence lines excluded. */
  private static String render() {
    List<String> lines = new ArrayList<>();
    lines.add("flowchart BT");
    lines.add("    accTitle: Which type class extends which");
    lines.add("    accDescr: " + description());
    for (Class<?> type : DIAGRAM) {
      lines.add(
          "    %s[\"%s<br/><small>%s</small>\"]"
              .formatted(
                  type.getSimpleName(),
                  type.getSimpleName(),
                  String.join(", ", abstractMethods(type))));
    }
    for (Class<?> type : DIAGRAM) {
      for (Class<?> parent : parents(type)) {
        lines.add("    %s --> %s".formatted(type.getSimpleName(), parent.getSimpleName()));
      }
    }
    lines.add("    classDef step fill:#8caaee,stroke:#1e66f5,color:#232634");
    lines.add(
        "    class "
            + DIAGRAM.stream().map(Class::getSimpleName).collect(Collectors.joining(","))
            + " step");
    return String.join("\n", lines) + "\n";
  }

  /** What the diagram shows, in words, for a reader who cannot see it. */
  private static String description() {
    List<String> sentences = new ArrayList<>();
    List<String> roots =
        DIAGRAM.stream().filter(type -> parents(type).isEmpty()).map(Class::getSimpleName).toList();
    sentences.add(and(roots) + " extend no other type class.");
    for (Class<?> type : DIAGRAM) {
      List<Class<?>> parents = parents(type);
      if (!parents.isEmpty()) {
        sentences.add(
            type.getSimpleName()
                + " extends "
                + and(parents.stream().map(Class::getSimpleName).toList())
                + ".");
      }
    }
    sentences.add("Each box also lists the abstract methods its type class declares.");
    return String.join(" ", sentences);
  }

  /** The interfaces {@code type} extends, each of which must be a box of its own. */
  private static List<Class<?>> parents(Class<?> type) {
    List<Class<?>> parents = Arrays.asList(type.getInterfaces());
    assertThat(DIAGRAM)
        .as("%s extends a type class the diagram has no box for", type.getSimpleName())
        .containsAll(parents);
    return parents;
  }

  /** The abstract methods {@code type} declares, in the order its source declares them. */
  private static List<String> abstractMethods(Class<?> type) {
    try (InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class")) {
      assertThat(in).as("%s's class file on the test classpath", type.getName()).isNotNull();
      return ClassFile.of().parse(in.readAllBytes()).methods().stream()
          .filter(method -> method.flags().has(AccessFlag.ABSTRACT))
          .map(method -> method.methodName().stringValue())
          .distinct()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** {@code TypeArity.Unary} or {@code Binary} when the first type parameter is bounded by one. */
  private static Class<?> witnessArity(Class<?> type) {
    TypeVariable<?>[] parameters = type.getTypeParameters();
    if (parameters.length == 0) {
      return null;
    }
    for (Type bound : parameters[0].getBounds()) {
      if (bound instanceof ParameterizedType witness
          && witness.getRawType() == WitnessArity.class
          && witness.getActualTypeArguments()[0] instanceof Class<?> arity) {
        return arity;
      }
    }
    return null;
  }

  /**
   * The public top-level interfaces of Functor's package, read from the jar or directory it loads
   * from. Nested ones, such as {@code TypeArity.Unary}, belong to the type that declares them.
   */
  private static List<Class<?>> apiInterfaces() {
    String packageName = Functor.class.getPackageName();
    try {
      Path root =
          Path.of(Functor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root);
          Stream<Path> files =
              Files.list(
                  (jar == null ? root : jar.getPath("/")).resolve(packageName.replace('.', '/')))) {
        List<String> names =
            files
                .map(file -> file.getFileName().toString())
                .filter(name -> name.endsWith(".class"))
                .map(name -> name.substring(0, name.length() - ".class".length()))
                .filter(name -> !name.contains("$") && !name.contains("-"))
                .sorted()
                .toList();
        List<Class<?>> interfaces = new ArrayList<>();
        for (String name : names) {
          Class<?> type = Class.forName(packageName + "." + name);
          if (type.isInterface()
              && !type.isAnnotation()
              && Modifier.isPublic(type.getModifiers())) {
            interfaces.add(type);
          }
        }
        assertThat(interfaces)
            .as("interfaces found in %s", root)
            .contains(Functor.class, Monad.class);
        return interfaces;
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The lines of the mermaid fence under the hierarchy heading, fence lines excluded. */
  private static List<String> diagramFence() {
    List<String> section = section();
    int open = section.indexOf("```mermaid");
    assertThat(open).as("a mermaid fence under %s", HEADING).isNotNegative();
    int close = section.subList(open + 1, section.size()).indexOf("```");
    assertThat(close).as("the mermaid fence under %s is closed", HEADING).isNotNegative();
    return section.subList(open + 1, open + 1 + close);
  }

  /** The prose lines after the diagram, up to the next heading or rule. */
  private static List<String> besideDiagram() {
    List<String> section = section();
    int afterFence = section.indexOf("```mermaid") + diagramFence().size() + 2;
    List<String> prose = new ArrayList<>();
    for (String line : section.subList(afterFence, section.size())) {
      if (line.equals("---")) {
        break;
      }
      prose.add(line);
    }
    return prose;
  }

  /** The lines under the hierarchy heading, up to the next heading. */
  private static List<String> section() {
    List<String> lines = read(PAGE).lines().toList();
    int heading = -1;
    for (int i = 0; i < lines.size() && heading < 0; i++) {
      if (lines.get(i).startsWith("## ") && lines.get(i).contains(HEADING)) {
        heading = i;
      }
    }
    assertThat(heading).as("a heading carrying %s on %s", HEADING, PAGE).isNotNegative();
    int end = heading + 1;
    while (end < lines.size() && !lines.get(end).startsWith("#")) {
      end++;
    }
    return lines.subList(heading + 1, end);
  }

  /** The simple names as code: "`A`", "`A` and `B`", or "`A`, `B` and `C`". */
  private static String codes(List<Class<?>> types) {
    return and(types.stream().map(type -> "`" + type.getSimpleName() + "`").toList());
  }

  /** "A", "A and B", or "A, B and C". */
  private static String and(List<String> names) {
    return names.size() == 1
        ? names.getFirst()
        : String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.getLast();
  }

  private static String read(Path page) {
    try {
      return Files.readString(page).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The directory is supplied by the Gradle task. Run straight from an IDE the property is unset;
   * this turns the opaque failure into a message that says how to run the test.
   */
  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null) {
      throw new IllegalStateException(
          "System property '%s' is not set. Run this via Gradle: `gradle :hkj-examples:bookVerify`."
              .formatted(property));
    }
    return value;
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Alternative;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.Foldable;
import org.higherkindedj.hkt.Functor;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Kind2;
import org.higherkindedj.hkt.Monad;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.writer.Writer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reads which type classes each core type has an instance of from {@code hkj-core}'s class files,
 * and holds the golden capability matrix the Core Types pages include to that reading.
 *
 * <p>Each core type's instances live in one package of {@code hkj-core}, such as {@code
 * org.higherkindedj.hkt.maybe}. Every public, concrete class in that package is loaded without
 * being initialised, and each type class it implements, directly or through another type class,
 * counts for its core type. {@code MonadError} is shown with the error type the instance fixes, so
 * {@code Maybe}'s reads {@code MonadError<Unit>}.
 *
 * <p>The golden file holds the whole matrix under the {@code matrix} anchor, which the Core Types
 * introduction includes, and one {@code Instances} row per type under the anchor of the type's page
 * name, for that page's type card. When an instance is added or removed, this test fails and prints
 * the replacement file. Each counted instance must be over a witness its row's package declares, so
 * a helper for another type cannot be counted, and an interface from {@code org.higherkindedj.hkt}
 * that the matrix has no place for fails the test until it has one. The rows follow the Core Types
 * pages in {@code SUMMARY.md}, so a new page fails here until it has a row.
 */
@DisplayName("the Core Types capability matrix is read from hkj-core")
class BookCapabilityMatrixTest {

  /** One core type: its page in {@code monads/}, its type, and the package of its instances. */
  record Row(String page, Class<?> type, String instances) {
    String anchor() {
      return page.replace(".md", "");
    }
  }

  /** The core types, in the order the Core Types pages appear in {@code SUMMARY.md}. */
  private static final List<Row> ROWS =
      List.of(
          new Row("either_monad.md", Either.class, "either"),
          new Row("validated_monad.md", Validated.class, "validated"),
          new Row("try_monad.md", Try.class, "trymonad"),
          new Row("either_or_both_monad.md", EitherOrBoth.class, "eitherorboth"),
          new Row("nonemptylist_monad.md", NonEmptyList.class, "nonemptylist"),
          new Row("maybe_monad.md", Maybe.class, "maybe"),
          new Row("optional_monad.md", Optional.class, "optional"),
          new Row("io_monad.md", IO.class, "io"),
          new Row("lazy_monad.md", Lazy.class, "lazy"),
          new Row("cf_monad.md", CompletableFuture.class, "future"),
          new Row("vtask_monad.md", VTask.class, "vtask"),
          new Row("vstream.md", VStream.class, "vstream"),
          new Row("reader_monad.md", Reader.class, "reader"),
          new Row("context_scoped.md", Context.class, "context"),
          new Row("state_monad.md", State.class, "state"),
          new Row("writer_monad.md", Writer.class, "writer"),
          new Row("list_monad.md", List.class, "list"),
          new Row("stream_monad.md", Stream.class, "stream"),
          new Row("identity.md", Id.class, "id"),
          new Row("trampoline_monad.md", Trampoline.class, "trampoline"),
          new Row("free_monad.md", Free.class, "free"),
          new Row("free_applicative.md", FreeAp.class, "free_ap"),
          new Row("eitherf.md", EitherF.class, "eitherf"),
          new Row("coyoneda.md", Coyoneda.class, "coyoneda"),
          new Row("const_type.md", Const.class, "constant"));

  /** The type classes a row can list, in the order it lists them. */
  private static final List<Class<?>> TYPE_CLASSES =
      List.of(
          Functor.class,
          Applicative.class,
          Selective.class,
          Monad.class,
          MonadError.class,
          Alternative.class,
          MonadZero.class,
          Foldable.class,
          Traverse.class,
          Bifunctor.class);

  /**
   * The {@code org.higherkindedj.hkt} interfaces a class in a core type's package may implement
   * that the matrix leaves out: the encoding's own types, which the core types and witnesses
   * implement, and the two type classes over a plain type, whose instances are values such as
   * {@code NonEmptyList.semigroup()} rather than classes in the package.
   */
  private static final List<Class<?>> NOT_SHOWN =
      List.of(Kind.class, Kind2.class, WitnessArity.class, Semigroup.class, Monoid.class);

  /** The golden file, under the test resources. */
  private static final String GOLDEN = "/golden/foundations-capabilities.md.golden";

  private static final Path BOOK = Path.of(required("hkj.book.dir"));

  /** The include the Core Types introduction carries for the matrix. */
  private static final String MATRIX_INCLUDE =
      "{{#include ../../../hkj-examples/src/test/resources/golden/"
          + "foundations-capabilities.md.golden:matrix}}";

  @Test
  @DisplayName("the golden matrix is the one hkj-core's instances give")
  void goldenMatchesHkjCore() {
    String rendered = render();
    assertThat(golden())
        .as(
            """
            The capability matrix no longer matches hkj-core's instances. If the change is \
            intended, replace hkj-examples/src/test/resources%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("the Core Types introduction includes the matrix")
  void theIntroductionIncludesTheMatrix() {
    assertThat(read(BOOK.resolve("monads/ch_intro.md"))).contains(MATRIX_INCLUDE);
  }

  @Test
  @DisplayName("there is one row for each Core Types page in SUMMARY.md, in its order")
  void rowsFollowTheSummary() {
    assertThat(ROWS.stream().map(Row::page).toList()).isEqualTo(coreTypePages());
  }

  @Test
  @DisplayName("every core type has at least one instance in its package")
  void everyRowHasAnInstance() {
    for (Row row : ROWS) {
      assertThat(instances(row.instances())).as("instances in %s", row.instances()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("every type class a class in a core type's package implements has a place")
  void everyTypeClassHasAPlace() {
    Set<Class<?>> implemented = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
    for (Row row : ROWS) {
      for (Class<?> type : publicClasses(row.instances())) {
        implemented.addAll(hktInterfaces(type));
      }
    }
    implemented.removeAll(NOT_SHOWN);
    assertThat(implemented)
        .as("interfaces from org.higherkindedj.hkt, each placed in TYPE_CLASSES or NOT_SHOWN")
        .isSubsetOf(TYPE_CLASSES);
  }

  @Test
  @DisplayName("every instance a row counts is over a witness its own package declares")
  void everyInstanceIsOverItsRowsWitness() {
    for (Row row : ROWS) {
      String pkg = "org.higherkindedj.hkt." + row.instances();
      for (Class<?> instance : instances(row.instances())) {
        for (Class<?> typeClass : TYPE_CLASSES) {
          if (!typeClass.isAssignableFrom(instance)) {
            continue;
          }
          Class<?> witness = raw(typeArgument(instance, typeClass, 0));
          assertThat(witness.getSimpleName() + " in " + witness.getPackageName())
              .as(
                  "the witness %s's %s is over",
                  instance.getSimpleName(), typeClass.getSimpleName())
              .isEqualTo("Witness in " + pkg);
        }
      }
    }
  }

  @Test
  @DisplayName("every MonadError names a class or one of its type's own type parameters")
  void everyErrorTypeIsTheTypes() {
    for (Row row : ROWS) {
      List<String> parameters =
          Arrays.stream(row.type().getTypeParameters()).map(TypeVariable::getName).toList();
      for (Class<?> instance : instances(row.instances())) {
        if (MonadError.class.isAssignableFrom(instance)) {
          Type error = typeArgument(instance, MonadError.class, 1);
          if (error instanceof TypeVariable<?> variable) {
            assertThat(parameters)
                .as("%s's error type %s", instance.getSimpleName(), variable)
                .contains(variable.getName());
          } else {
            assertThat(error)
                .as("%s's error type", instance.getSimpleName())
                .isInstanceOf(Class.class);
          }
        }
      }
    }
  }

  /** The golden file as this test would write it. */
  private static String render() {
    StringBuilder out = new StringBuilder();
    out.append(
        "<!-- Written by BookCapabilityMatrixTest from hkj-core's class files. Do not edit:"
            + " the test prints the replacement. -->\n");
    out.append("<!-- ANCHOR: matrix -->\n");
    out.append("| Type | Type classes |\n");
    out.append("|---|---|\n");
    for (Row row : ROWS) {
      out.append("| [`%s`](%s) | %s |\n".formatted(typeName(row.type()), row.page(), cell(row)));
    }
    out.append("<!-- ANCHOR_END: matrix -->\n");
    for (Row row : ROWS) {
      out.append("<!-- ANCHOR: %s -->\n".formatted(row.anchor()));
      out.append("| **Instances** | %s |\n".formatted(cell(row)));
      out.append("<!-- ANCHOR_END: %s -->\n".formatted(row.anchor()));
    }
    return out.toString();
  }

  /** The row's type classes as code, in {@link #TYPE_CLASSES} order. */
  private static String cell(Row row) {
    List<Class<?>> instances = instances(row.instances());
    List<String> names = new ArrayList<>();
    for (Class<?> typeClass : TYPE_CLASSES) {
      List<Class<?>> having = instances.stream().filter(typeClass::isAssignableFrom).toList();
      if (having.isEmpty()) {
        continue;
      }
      if (typeClass == MonadError.class) {
        Set<String> errors = new LinkedHashSet<>();
        having.forEach(
            instance -> errors.add(display(typeArgument(instance, MonadError.class, 1))));
        errors.forEach(error -> names.add("`MonadError<%s>`".formatted(error)));
      } else {
        names.add("`" + typeClass.getSimpleName() + "`");
      }
    }
    return String.join(", ", names);
  }

  /** {@code Either<L, R>}: the simple name and the declared type parameters. */
  private static String typeName(Class<?> type) {
    String parameters =
        Arrays.stream(type.getTypeParameters())
            .map(TypeVariable::getName)
            .collect(Collectors.joining(", "));
    return type.getSimpleName() + "<" + parameters + ">";
  }

  /**
   * The type a class gives one of a supertype's type parameters, such as the error type {@code
   * MaybeMonad} gives {@code MonadError}: a class, a parameterised type, or a type variable of the
   * class's own.
   */
  private static Type typeArgument(Class<?> type, Class<?> supertype, int index) {
    return typeArgument(type, supertype, index, Map.of())
        .orElseThrow(
            () ->
                new AssertionError(
                    "%s gives %s no type argument %d"
                        .formatted(type.getSimpleName(), supertype.getSimpleName(), index)));
  }

  private static Optional<Type> typeArgument(
      Type type, Class<?> supertype, int index, Map<String, Type> bindings) {
    Class<?> raw;
    Map<String, Type> here = new HashMap<>(bindings);
    if (type instanceof ParameterizedType parameterized) {
      raw = (Class<?>) parameterized.getRawType();
      TypeVariable<?>[] variables = raw.getTypeParameters();
      Type[] arguments = parameterized.getActualTypeArguments();
      for (int i = 0; i < variables.length; i++) {
        here.put(variables[i].getName(), resolve(arguments[i], bindings));
      }
      if (raw == supertype) {
        return Optional.of(here.get(variables[index].getName()));
      }
    } else if (type instanceof Class<?> plain) {
      raw = plain;
    } else {
      return Optional.empty();
    }
    List<Type> supertypes = new ArrayList<>(Arrays.asList(raw.getGenericInterfaces()));
    if (raw.getGenericSuperclass() != null) {
      supertypes.add(raw.getGenericSuperclass());
    }
    for (Type next : supertypes) {
      Optional<Type> found = typeArgument(next, supertype, index, here);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  private static Type resolve(Type type, Map<String, Type> bindings) {
    return type instanceof TypeVariable<?> variable
        ? bindings.getOrDefault(variable.getName(), variable)
        : type;
  }

  /** A simple name, with any type arguments: {@code Unit}, {@code L} or {@code List<String>}. */
  private static String display(Type type) {
    return switch (type) {
      case Class<?> plain -> plain.getSimpleName();
      case ParameterizedType parameterized ->
          display(parameterized.getRawType())
              + Arrays.stream(parameterized.getActualTypeArguments())
                  .map(BookCapabilityMatrixTest::display)
                  .collect(Collectors.joining(", ", "<", ">"));
      default -> type.getTypeName();
    };
  }

  private static Class<?> raw(Type type) {
    return switch (type) {
      case Class<?> plain -> plain;
      case ParameterizedType parameterized -> (Class<?>) parameterized.getRawType();
      default -> throw new AssertionError("no class for " + type);
    };
  }

  /** The {@code org.higherkindedj.hkt} interfaces a class implements, however indirectly. */
  private static Set<Class<?>> hktInterfaces(Class<?> type) {
    Set<Class<?>> found = new LinkedHashSet<>();
    List<Class<?>> pending = new ArrayList<>(List.of(type));
    while (!pending.isEmpty()) {
      Class<?> next = pending.removeLast();
      for (Class<?> parent : next.getInterfaces()) {
        if (parent.getPackageName().equals("org.higherkindedj.hkt") && found.add(parent)) {
          pending.add(parent);
        } else if (!parent.getPackageName().equals("org.higherkindedj.hkt")) {
          pending.add(parent);
        }
      }
      if (next.getSuperclass() != null) {
        pending.add(next.getSuperclass());
      }
    }
    return found;
  }

  /** The public, concrete classes in one {@code hkj-core} package that implement a type class. */
  private static List<Class<?>> instances(String pkg) {
    return publicClasses(pkg).stream()
        .filter(
            type -> TYPE_CLASSES.stream().anyMatch(typeClass -> typeClass.isAssignableFrom(type)))
        .toList();
  }

  /**
   * The public, concrete classes in one {@code hkj-core} package, its subpackages left out, read
   * from the jar or directory {@code hkj-core} loads from and loaded without being initialised.
   */
  private static List<Class<?>> publicClasses(String pkg) {
    String prefix = "org/higherkindedj/hkt/" + pkg + "/";
    List<Class<?>> found = new ArrayList<>();
    try {
      Path root = Path.of(Maybe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root);
          Stream<Path> files =
              Files.list((jar == null ? root : jar.getPath("/")).resolve(prefix))) {
        for (String name : files.map(file -> file.getFileName().toString()).sorted().toList()) {
          if (!name.endsWith(".class")) {
            continue;
          }
          String className = (prefix + name.replace(".class", "")).replace('/', '.');
          Class<?> type =
              Class.forName(className, false, BookCapabilityMatrixTest.class.getClassLoader());
          if (isConcrete(type)) {
            found.add(type);
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
    return found;
  }

  private static boolean isConcrete(Class<?> type) {
    return Modifier.isPublic(type.getModifiers())
        && !type.isInterface()
        && !Modifier.isAbstract(type.getModifiers())
        && !type.isAnonymousClass()
        && !type.isSynthetic();
  }

  /** The Core Types pages {@code SUMMARY.md} lists one level under the group, in its order. */
  private static List<String> coreTypePages() {
    List<String> lines = read(BOOK.resolve("SUMMARY.md")).lines().toList();
    String group = "- [Core Types](monads/ch_intro.md)";
    int start = -1;
    for (int i = 0; i < lines.size() && start < 0; i++) {
      if (lines.get(i).strip().equals(group)) {
        start = i;
      }
    }
    assertThat(start).as("the Core Types entry in SUMMARY.md").isNotNegative();
    int indent = lines.get(start).indexOf('-');
    List<String> pages = new ArrayList<>();
    for (String line : lines.subList(start + 1, lines.size())) {
      int depth = line.indexOf('-');
      if (line.isBlank() || depth <= indent) {
        break;
      }
      if (depth == indent + 2) {
        int from = line.indexOf("(monads/") + "(monads/".length();
        pages.add(line.substring(from, line.indexOf(')', from)));
      }
    }
    return pages;
  }

  private static String golden() {
    try (InputStream in = BookCapabilityMatrixTest.class.getResourceAsStream(GOLDEN)) {
      assertThat(in).as("the golden file %s", GOLDEN).isNotNull();
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file).replace("\r\n", "\n");
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

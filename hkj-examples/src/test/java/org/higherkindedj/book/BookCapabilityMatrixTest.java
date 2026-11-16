// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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
import org.higherkindedj.hkt.MonadReader;
import org.higherkindedj.hkt.MonadState;
import org.higherkindedj.hkt.MonadWriter;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Profunctor;
import org.higherkindedj.hkt.Selective;
import org.higherkindedj.hkt.Semigroup;
import org.higherkindedj.hkt.Traverse;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.hkt.constant.Const;
import org.higherkindedj.hkt.context.Context;
import org.higherkindedj.hkt.coyoneda.Coyoneda;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either_t.EitherT;
import org.higherkindedj.hkt.eitherf.EitherF;
import org.higherkindedj.hkt.eitherorboth.EitherOrBoth;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.hkt.free_ap.FreeAp;
import org.higherkindedj.hkt.id.Id;
import org.higherkindedj.hkt.io.IO;
import org.higherkindedj.hkt.lazy.Lazy;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe_t.MaybeT;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.optional_t.OptionalT;
import org.higherkindedj.hkt.reader.Reader;
import org.higherkindedj.hkt.reader_t.ReaderT;
import org.higherkindedj.hkt.state.State;
import org.higherkindedj.hkt.state_t.StateT;
import org.higherkindedj.hkt.trampoline.Trampoline;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.vstream.VStream;
import org.higherkindedj.hkt.vtask.VTask;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer_t.WriterT;
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
 * {@code Maybe}'s reads {@code MonadError<Unit>}, and {@code MonadReader}, {@code MonadState} and
 * {@code MonadWriter} with the environment, state or output type theirs fix.
 *
 * <p>The golden file holds the whole matrix under the {@code matrix} anchor, which Supported Types
 * includes, and one {@code Instances} row per type under the anchor of the type's page name, for
 * that page's type card. The monad transformers, {@code Tuple2} and {@code Function}, which have
 * instances and no Core Types page, are read the same way into the {@code others} anchor, which
 * Supported Types includes after the matrix. When an instance is added or removed, this test fails
 * and prints the replacement file. Each counted instance must be over a witness its row's package
 * declares, so a helper for another type cannot be counted, and an interface from {@code
 * org.higherkindedj.hkt} that the matrix has no place for fails the test until it has one. The core
 * rows follow the Core Types pages in {@code SUMMARY.md}, so a new page fails here until it has a
 * row, and a package of {@code hkj-core} that gains an instance fails here until it has a row or a
 * reason to be left out.
 */
@DisplayName("the Core Types capability matrix is read from hkj-core")
class BookCapabilityMatrixTest {

  /**
   * One type: the page that shows it, as a link from {@code monads/}, its type, and the package of
   * its instances.
   */
  record Row(String page, Class<?> type, String instances) {

    /** The anchor of a core type's {@code Instances} row: its page's name, in {@code monads/}. */
    String anchor() {
      assertThat(page).as("a core type's page, in monads/").doesNotContain("/");
      return page.replace(".md", "");
    }

    /** The package of the row's instances. */
    String packageName() {
      return "org.higherkindedj.hkt." + instances;
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

  /**
   * The types with instances and no Core Types page: the monad transformers, each linked to its
   * page, then the two-parameter types, each linked to the type class page that shows it.
   */
  private static final List<Row> OTHER_ROWS =
      List.of(
          new Row("../transformers/eithert_transformer.md", EitherT.class, "either_t"),
          new Row("../transformers/optionalt_transformer.md", OptionalT.class, "optional_t"),
          new Row("../transformers/maybet_transformer.md", MaybeT.class, "maybe_t"),
          new Row("../transformers/readert_transformer.md", ReaderT.class, "reader_t"),
          new Row("../transformers/statet_transformer.md", StateT.class, "state_t"),
          new Row("../transformers/writert_transformer.md", WriterT.class, "writer_t"),
          new Row("../functional/bifunctor.md", Tuple2.class, "tuple"),
          new Row("../functional/profunctor.md", Function.class, "func"));

  /** The type classes a row can list, in the order it lists them. */
  private static final List<Class<?>> TYPE_CLASSES =
      List.of(
          Functor.class,
          Applicative.class,
          Selective.class,
          Monad.class,
          MonadError.class,
          MonadReader.class,
          MonadState.class,
          MonadWriter.class,
          Alternative.class,
          MonadZero.class,
          Foldable.class,
          Traverse.class,
          Bifunctor.class,
          Profunctor.class);

  /**
   * The type classes a row shows with the type their instance fixes as its second type parameter:
   * the error, the environment, the state or the output.
   */
  private static final List<Class<?>> WITH_PARAMETER =
      List.of(MonadError.class, MonadReader.class, MonadState.class, MonadWriter.class);

  /**
   * The {@code org.higherkindedj.hkt} interfaces a class in a row's package may implement that the
   * matrix leaves out: the encoding's own types, which the core types and witnesses implement, and
   * the two type classes over a plain type, whose instances are values such as {@code
   * NonEmptyList.semigroup()} rather than classes in the package.
   */
  private static final List<Class<?>> NOT_SHOWN =
      List.of(Kind.class, Kind2.class, WitnessArity.class, Semigroup.class, Monoid.class);

  /**
   * The packages of {@code hkj-core} that hold instances and have no row, each with the reason.
   * They hold the instances of instruction sets and helpers that the library's own programs use,
   * rather than types a reader holds.
   */
  private static final Map<String, String> NOT_LISTED =
      Map.of(
          "org.higherkindedj.hkt.error",
          "ErrorOp is an instruction set for Free programs; its Functor serves the interpreter",
          "org.higherkindedj.hkt.state_op",
          "StateOp is an instruction set for Free programs; its Functor serves the interpreter",
          "org.higherkindedj.optics.free",
          "OpticOp is an instruction set for Free programs; its Functor serves the interpreter",
          "org.higherkindedj.optics.fetch",
          "FetchApplicative is handed to an optic to batch its reads; Fetch is not a held type");

  /** The golden file, under the test resources. */
  private static final String GOLDEN = "foundations-capabilities.md.golden";

  private static final Path BOOK = BookFiles.book();

  /** The includes Supported Types carries for the matrix and for the other types. */
  private static final List<String> INCLUDES =
      List.of(BookFiles.include(GOLDEN, "matrix"), BookFiles.include(GOLDEN, "others"));

  @Test
  @DisplayName("the golden matrix is the one hkj-core's instances give")
  void goldenMatchesHkjCore() {
    String rendered = render();
    assertThat(BookFiles.golden(GOLDEN))
        .as(
            """
            The capability matrix no longer matches hkj-core's instances. If the change is \
            intended, replace hkj-examples/src/test/resources/golden/%s with:

            %s""",
            GOLDEN, rendered)
        .isEqualTo(rendered);
  }

  @Test
  @DisplayName("Supported Types includes the matrix and the other types")
  void supportedTypesIncludesBothTables() {
    assertThat(BookFiles.read(BOOK.resolve("monads/supported-types.md"))).contains(INCLUDES);
  }

  @Test
  @DisplayName("there is one row for each Core Types page in SUMMARY.md, in its order")
  void rowsFollowTheSummary() {
    assertThat(ROWS.stream().map(Row::page).toList()).isEqualTo(BookFiles.coreTypePages(BOOK));
  }

  @Test
  @DisplayName("every row links a page the book has")
  void everyRowLinksAPage() {
    for (Row row : allRows()) {
      assertThat(BOOK.resolve("monads").resolve(row.page()).normalize())
          .as("the page %s links", row.type().getSimpleName())
          .isRegularFile();
    }
  }

  @Test
  @DisplayName("every package of hkj-core that holds an instance has a row or a reason")
  void everyPackageWithAnInstanceIsPlaced() {
    Set<String> placed =
        Stream.concat(allRows().stream().map(Row::packageName), NOT_LISTED.keySet().stream())
            .collect(Collectors.toCollection(TreeSet::new));
    Set<String> found = packagesWithInstances();
    assertThat(found)
        .as("packages holding instances, each in ROWS, OTHER_ROWS or NOT_LISTED")
        .isSubsetOf(placed);
    assertThat(found).as("the packages NOT_LISTED names").containsAll(NOT_LISTED.keySet());
  }

  @Test
  @DisplayName("every type has at least one instance in its package")
  void everyRowHasAnInstance() {
    for (Row row : allRows()) {
      assertThat(instances(row.instances())).as("instances in %s", row.instances()).isNotEmpty();
    }
  }

  @Test
  @DisplayName("every type class a class in a type's package implements has a place")
  void everyTypeClassHasAPlace() {
    Set<Class<?>> implemented = new TreeSet<>((a, b) -> a.getName().compareTo(b.getName()));
    for (Row row : allRows()) {
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
    for (Row row : allRows()) {
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
  @DisplayName(
      "every MonadError, MonadReader, MonadState and MonadWriter names a class or one of its"
          + " type's own type parameters")
  void everyShownParameterIsTheTypes() {
    for (Row row : allRows()) {
      List<String> parameters =
          Arrays.stream(row.type().getTypeParameters()).map(TypeVariable::getName).toList();
      for (Class<?> instance : instances(row.instances())) {
        for (Class<?> typeClass : WITH_PARAMETER) {
          if (!typeClass.isAssignableFrom(instance)) {
            continue;
          }
          Type shown = typeArgument(instance, typeClass, 1);
          if (shown instanceof TypeVariable<?> variable) {
            assertThat(parameters)
                .as(
                    "%s's %s type %s",
                    instance.getSimpleName(), typeClass.getSimpleName(), variable)
                .contains(variable.getName());
          } else {
            assertThat(shown)
                .as("%s's %s type", instance.getSimpleName(), typeClass.getSimpleName())
                .isInstanceOf(Class.class);
          }
        }
      }
    }
  }

  /** The core types, then the other types. */
  private static List<Row> allRows() {
    return Stream.concat(ROWS.stream(), OTHER_ROWS.stream()).toList();
  }

  /** The golden file as this test would write it. */
  private static String render() {
    StringBuilder out = new StringBuilder();
    out.append(
        "<!-- Written by BookCapabilityMatrixTest from hkj-core's class files. Do not edit:"
            + " the test prints the replacement. -->\n");
    table(out, "matrix", ROWS);
    table(out, "others", OTHER_ROWS);
    for (Row row : ROWS) {
      out.append("<!-- ANCHOR: %s -->\n".formatted(row.anchor()));
      out.append("| **Instances** | %s |\n".formatted(cell(row)));
      out.append("<!-- ANCHOR_END: %s -->\n".formatted(row.anchor()));
    }
    return out.toString();
  }

  /** One anchored table: each row's type, linked to its page, and its type classes. */
  private static void table(StringBuilder out, String anchor, List<Row> rows) {
    out.append("<!-- ANCHOR: %s -->\n".formatted(anchor));
    out.append("| Type | Type classes |\n");
    out.append("|---|---|\n");
    for (Row row : rows) {
      out.append("| [`%s`](%s) | %s |\n".formatted(typeName(row.type()), row.page(), cell(row)));
    }
    out.append("<!-- ANCHOR_END: %s -->\n".formatted(anchor));
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
      if (WITH_PARAMETER.contains(typeClass)) {
        Set<String> shown = new LinkedHashSet<>();
        having.forEach(instance -> shown.add(display(typeArgument(instance, typeClass, 1))));
        shown.forEach(
            parameter -> names.add("`%s<%s>`".formatted(typeClass.getSimpleName(), parameter)));
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

  /**
   * The packages of {@code hkj-core} with a public, concrete class implementing a type class the
   * matrix lists, read from every class file and loaded without being initialised.
   */
  private static Set<String> packagesWithInstances() {
    Set<String> packages = new TreeSet<>();
    try {
      Path root = Path.of(Maybe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      try (FileSystem jar = Files.isDirectory(root) ? null : FileSystems.newFileSystem(root)) {
        Path base = jar == null ? root : jar.getPath("/");
        try (Stream<Path> files = Files.walk(base.resolve("org/higherkindedj"))) {
          for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
            String className =
                base.relativize(file).toString().replace(".class", "").replace('/', '.');
            Class<?> type =
                Class.forName(className, false, BookCapabilityMatrixTest.class.getClassLoader());
            if (isConcrete(type)
                && TYPE_CLASSES.stream().anyMatch(typeClass -> typeClass.isAssignableFrom(type))) {
              packages.add(type.getPackageName());
            }
          }
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (URISyntaxException | ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
    return packages;
  }
}

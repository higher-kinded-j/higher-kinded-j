// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeContains;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.assertGeneratedCodeDoesNotContain;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A {@code @ThroughField} traversal over a field declared nullable finds no elements in a null one,
 * as a record's traversal does, where the container's traversal would throw on it.
 *
 * <p>The spec's lens for the field may declare its focus nullable, or the getter the lens calls on
 * the source may be declared so: a record's accessor through its component, or a getter on itself
 * or on its return type. Either way the composition steps past a null with {@code
 * Affines.nullable()} before the container's traversal.
 */
@DisplayName("A @ThroughField traversal over a nullable field")
class ThroughFieldNullableTest {

  private static final String CHECKED =
      "return lens.andThen(Affines.nullable()).andThen(org.higherkindedj.optics.util.Traversals.forList());";

  /** A class holding a list, read by {@code getter} and declared by {@code field}. */
  private static JavaFileObject team(String field, String getter) {
    return JavaFileObjects.forSourceString(
        "com.external.Team",
        """
        package com.external;

        import java.util.List;
        import org.jspecify.annotations.Nullable;

        public final class Team {
          %s

          public Team(@Nullable List<String> members) {
            this.members = members;
          }

          %s

          public Team withMembers(@Nullable List<String> members) {
            return new Team(members);
          }
        }
        """
            .formatted(field, getter));
  }

  /** The spec, its lens for the field declared as {@code lens}, read through {@code getter}. */
  private static JavaFileObject spec(String lens, String getter, String throughField) {
    return JavaFileObjects.forSourceString(
        "com.test.TeamSpec",
        """
        package com.test;

        import com.external.Team;
        import java.util.List;
        import org.higherkindedj.optics.Lens;
        import org.higherkindedj.optics.Traversal;
        import org.higherkindedj.optics.annotations.ImportOptics;
        import org.higherkindedj.optics.annotations.OpticsSpec;
        import org.higherkindedj.optics.annotations.ThroughField;
        import org.higherkindedj.optics.annotations.Wither;
        import org.jspecify.annotations.Nullable;

        @ImportOptics
        public interface TeamSpec extends OpticsSpec<Team> {
          @Wither(value = "withMembers", getter = "%s")
          %s members();

          %s
          Traversal<Team, String> eachMember();
        }
        """
            .formatted(getter, lens, throughField));
  }

  private static final String PRIVATE_FIELD = "private final @Nullable List<String> members;";

  private static final String AUTO_DETECTED = "@ThroughField(field = \"members\")";

  private static final String NAMED =
      "@ThroughField(field = \"members\", traversal ="
          + " \"org.higherkindedj.optics.util.Traversals.forList()\")";

  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new ImportOpticsProcessor())
        .withOptions("-Xlint:all,-processing", "-Werror")
        .compile(sources);
  }

  /** Each way of declaring the field nullable, and the sources that declare it so. */
  static Stream<Arguments> nullableFields() {
    return Stream.of(
        Arguments.of(
            "the lens declares its focus nullable",
            team(PRIVATE_FIELD, "public List<String> members() { return members; }"),
            spec("Lens<Team, @Nullable List<String>>", "members", AUTO_DETECTED)),
        Arguments.of(
            "the getter's return type is declared nullable",
            team(PRIVATE_FIELD, "public @Nullable List<String> members() { return members; }"),
            spec("Lens<Team, List<String>>", "members", AUTO_DETECTED)),
        Arguments.of(
            "the getter is declared nullable",
            team(
                PRIVATE_FIELD,
                "@javax.annotation.Nullable public List<String> members() { return members; }"),
            spec("Lens<Team, List<String>>", "members", AUTO_DETECTED)),
        Arguments.of(
            "the getter the lens names is declared nullable",
            team(
                PRIVATE_FIELD,
                "public List<String> members() { return members; }\n"
                    + "  public @Nullable List<String> rawMembers() { return members; }\n"
                    + "  public List<String> rawMembers(int limit) { return members; }"),
            spec("Lens<Team, List<String>>", "rawMembers", AUTO_DETECTED)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("nullableFields")
  @DisplayName("steps past a null before the container's traversal")
  void stepsPastANull(String why, JavaFileObject team, JavaFileObject spec) {
    Compilation compilation = compile(team, spec, Jsr305NullableStub.SOURCE);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(compilation, "com.test.Team", CHECKED);
  }

  @Test
  @DisplayName("steps past a null record component")
  void stepsPastANullRecordComponent() {
    JavaFileObject bag =
        JavaFileObjects.forSourceString(
            "com.external.Bag",
            """
            package com.external;

            import java.util.List;
            import org.jspecify.annotations.Nullable;

            public record Bag(@Nullable List<String> items) {
              public Bag withItems(@Nullable List<String> items) {
                return new Bag(items);
              }
            }
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.test.BagSpec",
            """
            package com.test;

            import com.external.Bag;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            public interface BagSpec extends OpticsSpec<Bag> {
              @Wither("withItems")
              Lens<Bag, List<String>> items();

              @ThroughField(field = "items")
              Traversal<Bag, String> eachItem();
            }
            """);

    Compilation compilation = compile(bag, spec);

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(
        compilation,
        "com.test.Bag",
        "return lens.andThen(Affines.nullable()).andThen(org.higherkindedj.optics.util.Traversals.forList());");
  }

  @Test
  @DisplayName("composes as before for a nullable public field the lens does not read")
  void composesAsBeforeForAFieldTheLensDoesNotRead() {
    // The lens reads readMembers(), which is declared non-null, whatever the field beside it or
    // an overload taking an argument says.
    Compilation compilation =
        compile(
            team(
                "public final @Nullable List<String> members;",
                "public List<String> readMembers() {"
                    + " return members == null ? List.of() : members; }\n"
                    + "  public @Nullable List<String> readMembers(int limit) { return members; }"),
            spec("Lens<Team, List<String>>", "readMembers", AUTO_DETECTED));

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeDoesNotContain(compilation, "com.test.Team", "Affines");
  }

  @Test
  @DisplayName("composes as before for a field nothing declares nullable")
  void composesAsBeforeForANonNullField() {
    Compilation compilation =
        compile(
            team(
                "private final List<String> members;",
                "public List<String> members() { return members; }"),
            spec("Lens<Team, List<String>>", "members", AUTO_DETECTED));

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeContains(
        compilation,
        "com.test.Team",
        "return lens.andThen(org.higherkindedj.optics.util.Traversals.forList());");
    assertGeneratedCodeDoesNotContain(compilation, "com.test.Team", "Affines");
  }

  @Test
  @DisplayName("steps past a null before a traversal the annotation names")
  void stepsPastANullBeforeANamedTraversal() {
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                team(PRIVATE_FIELD, "public @Nullable List<String> members() { return members; }"),
                spec("Lens<Team, List<String>>", "members", NAMED));

    assertThat(compilation).succeeded();
    assertGeneratedCodeContains(
        compilation,
        "com.test.Team",
        "return Team.members().andThen(Affines.nullable()).andThen((Traversal)"
            + " org.higherkindedj.optics.util.Traversals.forList());");
  }

  @Test
  @DisplayName("a raw lens beside a named traversal is refused for itself alone")
  void aRawLensIsRefusedForItselfAlone() {
    // The traversal is declared first, so its nullable reading meets the raw lens before the lens
    // is refused, and reads it as saying nothing.
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.test.TeamSpec",
            """
            package com.test;

            import com.external.Team;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            public interface TeamSpec extends OpticsSpec<Team> {
              %s
              Traversal<Team, String> eachMember();

              @SuppressWarnings("rawtypes") // the raw lens is the shape under test
              @Wither(value = "withMembers", getter = "members")
              Lens members();
            }
            """
                .formatted(NAMED));
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                team(PRIVATE_FIELD, "public List<String> members() { return members; }"), spec);

    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("Cannot determine focus type for method 'members'");
  }

  @Test
  @DisplayName("composes a named traversal as before through a getter declared non-null")
  void composesANamedTraversalAsBeforeThroughANonNullGetter() {
    // The lens calls readMembers(), declared non-null, so neither the lens nor its getter says the
    // field may hold null.
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                team(PRIVATE_FIELD, "public List<String> readMembers() { return members; }"),
                spec("Lens<Team, List<String>>", "readMembers", NAMED));

    assertThat(compilation).succeeded();
    assertGeneratedCodeDoesNotContain(compilation, "com.test.Team", "Affines");
  }

  @Test
  @DisplayName("a named traversal through a lens the spec does not declare is written as named")
  void aNamedTraversalWithoutALensIsWrittenAsNamed() {
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.test.TeamSpec",
            """
            package com.test;

            import com.external.Team;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;

            @ImportOptics
            public interface TeamSpec extends OpticsSpec<Team> {
              %s
              Traversal<Team, String> eachMember();
            }
            """
                .formatted(NAMED));

    // A named traversal is the author's own undertaking, so the processor writes it as named, and
    // javac then reports the lens the composition calls, which the generated class lacks.
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                team(PRIVATE_FIELD, "public @Nullable List<String> members() { return members; }"),
                spec);

    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("cannot find symbol");
  }

  @Test
  @DisplayName("reads the getter javac binds, not a nullable one it passes over")
  void readsTheGetterJavacBinds() {
    // Team inherits a non-null members() from Base and a nullable declaration from HasMembers;
    // the lens's call binds Base's, so the field is read as non-null.
    JavaFileObject hasMembers =
        JavaFileObjects.forSourceString(
            "com.external.HasMembers",
            """
            package com.external;

            import java.util.List;
            import org.jspecify.annotations.Nullable;

            public interface HasMembers {
              @Nullable List<String> members();
            }
            """);
    JavaFileObject base =
        JavaFileObjects.forSourceString(
            "com.external.Base",
            """
            package com.external;

            import java.util.List;

            public class Base {
              protected final List<String> members;

              protected Base(List<String> members) {
                this.members = members;
              }

              public List<String> members() {
                return members;
              }
            }
            """);
    JavaFileObject team =
        JavaFileObjects.forSourceString(
            "com.external.Team",
            """
            package com.external;

            import java.util.List;

            public final class Team extends Base implements HasMembers {
              public Team(List<String> members) {
                super(members);
              }

              public Team withMembers(List<String> members) {
                return new Team(members);
              }
            }
            """);

    Compilation compilation =
        compile(hasMembers, base, team, spec("Lens<Team, List<String>>", "members", NAMED));

    assertThat(compilation).succeededWithoutWarnings();
    assertGeneratedCodeDoesNotContain(compilation, "com.test.Team", "Affines");
  }

  @Test
  @DisplayName("a lens naming a getter the source lacks is refused for itself alone")
  void aLensNamingAMissingGetterIsRefusedForItselfAlone() {
    // The traversal is declared first, so its nullable reading looks for the getter before the
    // lens is refused for naming one the source lacks, and finds none to read.
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.test.TeamSpec",
            """
            package com.test;

            import com.external.Team;
            import java.util.List;
            import org.higherkindedj.optics.Lens;
            import org.higherkindedj.optics.Traversal;
            import org.higherkindedj.optics.annotations.ImportOptics;
            import org.higherkindedj.optics.annotations.OpticsSpec;
            import org.higherkindedj.optics.annotations.ThroughField;
            import org.higherkindedj.optics.annotations.Wither;

            @ImportOptics
            public interface TeamSpec extends OpticsSpec<Team> {
              %s
              Traversal<Team, String> eachMember();

              @Wither(value = "withMembers", getter = "missingMembers")
              Lens<Team, List<String>> members();
            }
            """
                .formatted(NAMED));
    Compilation compilation =
        javac()
            .withProcessors(new ImportOpticsProcessor())
            .compile(
                team(PRIVATE_FIELD, "public List<String> members() { return members; }"), spec);

    assertThat(compilation).hadErrorCount(1);
    assertThat(compilation).hadErrorContaining("missingMembers");
  }

  @Test
  @DisplayName("finds no element in a null field, and hands the source back as it is")
  void findsNoElementInANullField() throws ReflectiveOperationException {
    Compilation compilation =
        compile(
            team(PRIVATE_FIELD, "public @Nullable List<String> members() { return members; }"),
            spec("Lens<Team, List<String>>", "members", AUTO_DETECTED));
    assertThat(compilation).succeeded();
    CompiledResult result = new CompiledResult(compilation);
    Class<?> teamType = result.loadClass("com.external.Team");
    Object none = teamType.getConstructor(List.class).newInstance((Object) null);
    Object one = teamType.getConstructor(List.class).newInstance(List.of("ada"));
    @SuppressWarnings("unchecked") // the generated method's type arguments erase to these
    Traversal<Object, String> eachMember =
        (Traversal<Object, String>) result.invokeStatic("com.test.Team", "eachMember");

    assertThat(Traversals.getAll(eachMember, none)).isEmpty();
    assertThat(Traversals.modify(eachMember, name -> name.toUpperCase(Locale.ROOT), none))
        .isSameAs(none);
    assertThat(
            Traversals.getAll(
                eachMember,
                Traversals.modify(eachMember, name -> name.toUpperCase(Locale.ROOT), one)))
        .containsExactly("ADA");
    assertThat(Traversals.getAll(eachMember, one)).containsExactly("ada");
  }
}

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MappingProcessor - @GenerateMapping")
class MappingProcessorTest {

  private static final JavaFileObject DOMAIN =
      JavaFileObjects.forSourceString(
          "com.example.User",
          """
          package com.example;

          public record User(String name, EmailAddress email, int age) {}
          """);

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.example.EmailAddress",
          """
          package com.example;

          public record EmailAddress(String value) {}
          """);

  private static final JavaFileObject WIRE =
      JavaFileObjects.forSourceString(
          "com.example.UserDto",
          """
          package com.example;

          public record UserDto(String name, String email, int age) {}
          """);

  private static final JavaFileObject SPEC =
      JavaFileObjects.forSourceString(
          "com.example.UserMapping",
          """
          package com.example;

          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          public interface UserMapping extends MappingSpec<User, UserDto> {
            default ValidatedPrism<String, EmailAddress> email() {
              return ValidatedPrism.of(
                  raw ->
                      raw.contains("@")
                          ? Validated.validNel(new EmailAddress(raw))
                          : Validated.invalidNel(FieldError.of("not an email address")),
                  EmailAddress::value);
            }
          }
          """);

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
  }

  @Nested
  @DisplayName("Happy path")
  class HappyPath {

    @Test
    @DisplayName("generates <Spec>Impl with total build and located accumulating parse")
    void generatesImpl() {
      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, SPEC);

      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.UserMappingImpl");
      Assertions.assertThat(generated)
          .contains("public final class UserMappingImpl implements UserMapping")
          .contains("public static final UserMappingImpl INSTANCE")
          .contains("public UserDto build(User domain)")
          .contains("new UserDto(domain.name(), email().build(domain.email()), domain.age())")
          .contains("public Validated<NonEmptyList<FieldError>, User> parse(UserDto wire)")
          .contains("return Validated.fields()")
          .contains(".field(\"name\", hkj$ifPresent(wire.name(), Validated::validNel))")
          .contains(".field(\"email\", hkj$ifPresent(wire.email(), email()::parse))")
          .contains(".field(\"age\", Validated.validNel(wire.age()))")
          .contains(".apply(User::new)")
          .doesNotContain("asIso");
    }
  }

  @Nested
  @DisplayName("Renames and the lossless tier")
  class RenamesAndLossless {

    @Test
    @DisplayName("@MapField renames flow through build/parse, and lossless mappings gain asIso")
    void renamedLosslessMappingGainsAsIso() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;

              public record Person(String name, int age) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.PersonDto",
              """
              package com.example;

              public record PersonDto(String fullName, int age) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PersonMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PersonMapping extends MappingSpec<Person, PersonDto> {
                @MapField(to = "fullName")
                String name();
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.PersonMappingImpl");
      Assertions.assertThat(generated)
          .contains("new PersonDto(domain.name(), domain.age())")
          .contains(".field(\"name\", hkj$ifPresent(wire.fullName(), Validated::validNel))")
          .contains("public Iso<Person, PersonDto> asIso()")
          .contains("return Iso.of(this::build, wire -> new Person(wire.fullName(), wire.age()))");
    }
  }

  @Nested
  @DisplayName("Container lifting")
  class ContainerLifting {

    @Test
    @DisplayName("List and Optional components lift through the element leaf")
    void listAndOptionalLift() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              public record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.TeamDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              public record TeamDto(String name, List<String> members, Optional<String> lead) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TeamMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface TeamMapping extends MappingSpec<Team, TeamDto> {
                default ValidatedPrism<String, EmailAddress> members() {
                  return prism();
                }

                default ValidatedPrism<String, EmailAddress> lead() {
                  return prism();
                }

                private static ValidatedPrism<String, EmailAddress> prism() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TeamMappingImpl");
      Assertions.assertThat(generated)
          .contains("members().buildAll(domain.members())")
          .contains("domain.lead().map(lead()::build)")
          .contains(".field(\"members\", hkj$ifPresent(wire.members(), members()::parseAll))")
          .contains(
              ".field(\"lead\", hkj$ifPresent(wire.lead(), o -> o.map(v -> lead().parse(v).map(Optional::of))")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("a Set component lifts through the element leaf, located by the element itself")
    void setLiftsThroughTheElementLeaf() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Crew",
              """
              package com.example;

              import java.util.Set;

              public record Crew(Set<EmailAddress> members) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.CrewDto",
              """
              package com.example;

              import java.util.Set;

              public record CrewDto(Set<String> members) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CrewMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CrewMapping extends MappingSpec<Crew, CrewDto> {
                default ValidatedPrism<String, EmailAddress> members() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).succeeded();
      // The emitted text is the list form's, verbatim: the bulk forms are overloaded on the
      // container, so the component's own type picks parseAll(Set) and buildAll(Set).
      Assertions.assertThat(generatedSource(compilation, "com.example.CrewMappingImpl"))
          .contains("members().buildAll(domain.members())")
          .contains(".field(\"members\", hkj$ifPresent(wire.members(), members()::parseAll))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CrewMappingImpl");
      Object dto =
          result
              .loadClass("com.example.CrewDto")
              .getDeclaredConstructor(Set.class)
              .newInstance(new LinkedHashSet<>(List.of("ada@corp", "nope")));

      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      assertThatValidated(parsed).isInvalid();
      // A set has no index, so the failing element's own rendering locates it.
      Assertions.assertThat(parsed.getError().toJavaList())
          .containsExactly(new FieldError(List.of("members", "nope"), "not an email address"));
    }

    @Test
    @DisplayName("an identity Set is scanned: a null element is unlocated, a set holding one")
    void identitySetElementsAreScanned() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Labels",
              """
              package com.example;

              import java.util.Set;

              public record Labels(Set<String> names) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LabelsDto",
              """
              package com.example;

              import java.util.Set;

              public record LabelsDto(Set<String> names) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.LabelsMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface LabelsMapping extends MappingSpec<Labels, LabelsDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.LabelsMappingImpl"))
          .contains(".field(\"names\", hkj$allPresent(wire.names()))")
          // the Set overload, not the List one
          .contains("Validated<NonEmptyList<FieldError>, Set<E>> hkj$allPresent(Set<E> values)")
          .doesNotContain("List<E> values")
          .contains("public Iso<Labels, LabelsDto> asIso()");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.LabelsMappingImpl");

      Set<String> names = new LinkedHashSet<>(List.of("a", "b"));
      Object dto =
          result
              .loadClass("com.example.LabelsDto")
              .getDeclaredConstructor(Set.class)
              .newInstance(names);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      assertThatValidated(parsed).isValid();
      // Identity legs copy, they do not rebuild: the same set reference passes through.
      Assertions.assertThat(invoke(parsed.get(), "names")).isSameAs(names);

      Set<String> withNull = new LinkedHashSet<>(List.of("a"));
      withNull.add(null);
      Object badDto =
          result
              .loadClass("com.example.LabelsDto")
              .getDeclaredConstructor(Set.class)
              .newInstance(withNull);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> rejected =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      assertThatValidated(rejected).isInvalid();
      Assertions.assertThat(rejected.getError().toJavaList())
          .containsExactly(new FieldError(List.of("names"), "must not contain a null element"));
    }

    @Test
    @DisplayName("an array component lifts through the element leaf, located by index")
    void arrayLiftsWithIndexLocation() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Roster",
              """
              package com.example;

              public record Roster(EmailAddress[] members, int[] scores) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.RosterDto",
              """
              package com.example;

              public record RosterDto(String[] members, int[] scores) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RosterMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RosterMapping extends MappingSpec<Roster, RosterDto> {
                default ValidatedPrism<String, EmailAddress> members() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.RosterMappingImpl"))
          .contains("members().buildAll(domain.members(), String[]::new)")
          .contains(
              ".field(\"members\", hkj$ifPresent(wire.members(), v -> members().parseAll(v,"
                  + " EmailAddress[]::new)))")
          // A primitive array has no element that could be null, so it carries no element
          // scan - but the component is still a reference, so the read stays null-guarded.
          .contains(".field(\"scores\", hkj$ifPresent(wire.scores(), Validated::validNel))")
          .doesNotContain("hkj$allPresent");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.RosterMappingImpl");
      Object dto =
          result
              .loadClass("com.example.RosterDto")
              .getDeclaredConstructor(String[].class, int[].class)
              .newInstance(new String[] {"ada@corp", "nope"}, new int[] {1, 2});

      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      assertThatValidated(parsed).isInvalid();
      // An array has stable indices, so a failing element locates exactly as a list's does.
      Assertions.assertThat(parsed.getError().toJavaList())
          .containsExactly(new FieldError(List.of("members", "1"), "not an email address"));
    }

    @Test
    @DisplayName("an identity reference array is scanned by index; a primitive array is not")
    void identityArrayElementsAreScanned() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Codes",
              """
              package com.example;

              public record Codes(String[] tags, int[] counts) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.CodesDto",
              """
              package com.example;

              public record CodesDto(String[] tags, int[] counts) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CodesMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CodesMapping extends MappingSpec<Codes, CodesDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CodesMappingImpl"))
          .contains(".field(\"tags\", hkj$allPresent(wire.tags()))")
          .contains("Validated<NonEmptyList<FieldError>, E[]> hkj$allPresent(E[] values)")
          // a primitive array cannot hold a null element, so it takes the plain identity leg
          .contains(".field(\"counts\", hkj$ifPresent(wire.counts(), Validated::validNel))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CodesMappingImpl");
      Object badDto =
          result
              .loadClass("com.example.CodesDto")
              .getDeclaredConstructor(String[].class, int[].class)
              .newInstance(new String[] {"a", null, null}, new int[] {});
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> rejected =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      assertThatValidated(rejected).isInvalid();
      Assertions.assertThat(rejected.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("tags", "1"), "must not be null"),
              new FieldError(List.of("tags", "2"), "must not be null"));
    }

    @Test
    @DisplayName("an array of records lifts through the nested spec")
    void arrayOfNestedRecordsLifts() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Board",
              """
              package com.example;

              public record Board(Seat[] seats) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BoardDto",
              """
              package com.example;

              public record BoardDto(SeatDto[] seats) {}
              """);
      JavaFileObject seat =
          JavaFileObjects.forSourceString(
              "com.example.Seat",
              """
              package com.example;

              public record Seat(String label) {}
              """);
      JavaFileObject seatDto =
          JavaFileObjects.forSourceString(
              "com.example.SeatDto",
              """
              package com.example;

              public record SeatDto(String label) {}
              """);
      JavaFileObject seatSpec =
          JavaFileObjects.forSourceString(
              "com.example.SeatMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SeatMapping extends MappingSpec<Seat, SeatDto> {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BoardMapping extends MappingSpec<Board, BoardDto> {}
              """);

      Compilation compilation = compile(domain, wire, seat, seatDto, seatSpec, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BoardMappingImpl"))
          .contains(
              ".field(\"seats\", hkj$ifPresent(wire.seats(), v ->"
                  + " SeatMappingImpl.INSTANCE.asValidatedPrism().parseAll(v, Seat[]::new)))")
          .contains(
              "SeatMappingImpl.INSTANCE.asValidatedPrism().buildAll(domain.seats(),"
                  + " SeatDto[]::new)");
    }

    @Test
    @DisplayName("a primitive array, or an array against a List, never reaches the lifting route")
    void primitiveAndMismatchedArraysAreNotPairs() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ShapesDto",
              """
              package com.example;

              import java.util.List;

              public record ShapesDto(int[] widths, String[] names, String[] loose) {}
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Shapes",
              """
              package com.example;

              import java.util.List;

              public record Shapes(Integer[] widths, int[] names, List<String> loose) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ShapesMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ShapesMapping extends MappingSpec<Shapes, ShapesDto> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      // int[] vs Integer[] (primitive wire element), String[] vs int[] (primitive domain
      // element) and String[] vs List<String> (different containers) are all plain mismatches.
      assertThat(compilation).hadErrorContaining("has no usable source");
    }

    @Test
    @DisplayName(
        "a List against a Set is not a pair: lifting needs the same container on both sides")
    void aListAgainstASetIsNotAPair() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Mixed",
              """
              package com.example;

              import java.util.Set;

              public record Mixed(Set<EmailAddress> members) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.MixedDto",
              """
              package com.example;

              import java.util.List;

              public record MixedDto(List<String> members) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.MixedMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface MixedMapping extends MappingSpec<Mixed, MixedDto> {
                default ValidatedPrism<String, EmailAddress> members() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no usable source");
    }
  }

  @Nested
  @DisplayName("Map value lifting")
  class MapValueLifting {

    private static final JavaFileObject DIRECTORY =
        JavaFileObjects.forSourceString(
            "com.example.Directory",
            """
            package com.example;

            import java.util.Map;

            public record Directory(Map<String, EmailAddress> entries) {}
            """);

    private static final JavaFileObject DIRECTORY_DTO =
        JavaFileObjects.forSourceString(
            "com.example.DirectoryDto",
            """
            package com.example;

            import java.util.Map;

            public record DirectoryDto(Map<String, String> entries) {}
            """);

    private static final JavaFileObject DIRECTORY_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.DirectoryMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface DirectoryMapping extends MappingSpec<Directory, DirectoryDto> {
              default ValidatedPrism<String, EmailAddress> entries() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    EmailAddress::value);
              }
            }
            """);

    @Test
    @DisplayName("Map components lift their values through the leaf, keys untouched")
    void mapValuesLiftThroughLeaf() {
      Compilation compilation = compile(EMAIL, DIRECTORY, DIRECTORY_DTO, DIRECTORY_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.DirectoryMappingImpl");
      Assertions.assertThat(generated)
          .contains("entries().buildValues(domain.entries())")
          .contains(".field(\"entries\", hkj$ifPresent(wire.entries(), entries()::parseValues))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.DirectoryMappingImpl");
        Map<String, Object> byKey = new LinkedHashMap<>();
        byKey.put("work", result.newInstance("com.example.EmailAddress", "ada@corp.example"));
        byKey.put("home", result.newInstance("com.example.EmailAddress", "ada@home.example"));
        Object domain =
            result
                .loadClass("com.example.Directory")
                .getDeclaredConstructor(Map.class)
                .newInstance(byKey);

        Object dto = invoke(impl, "build", domain);
        Assertions.assertThat(invoke(dto, "entries"))
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsExactly(
                Assertions.entry("work", "ada@corp.example"),
                Assertions.entry("home", "ada@home.example"));

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isValid()).isTrue();
        Assertions.assertThat(parsed.get()).isEqualTo(domain);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("@MapKey converts the keys; both sides lift together through parseEntries")
    void mapKeyLeafConvertsTheKeys() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.Map;

              public record Profile(Map<Tag, EmailAddress> contacts, Map<Tag, String> notes) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ProfileDto",
              """
              package com.example;

              import java.util.Map;

              public record ProfileDto(Map<String, String> contacts, Map<String, String> notes) {}
              """);
      JavaFileObject tag =
          JavaFileObjects.forSourceString(
              "com.example.Tag",
              """
              package com.example;

              public record Tag(String value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ProfileMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
                // values, named after the component
                default ValidatedPrism<String, EmailAddress> contacts() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                // keys of 'contacts' AND of 'notes', each named by its own annotation
                @MapKey("contacts")
                default ValidatedPrism<String, Tag> contactTag() {
                  return tagPrism();
                }

                // Named after its OWN component: a @MapKey leaf converts the key side, so it
                // is never mistaken for the value leaf the same name would otherwise declare.
                @MapKey("notes")
                default ValidatedPrism<String, Tag> notes() {
                  return tagPrism();
                }

                private static ValidatedPrism<String, Tag> tagPrism() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.startsWith("#")
                              ? Validated.validNel(new Tag(raw))
                              : Validated.invalidNel(FieldError.of("not a tag")),
                      Tag::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, tag, domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.ProfileMappingImpl"))
          // both sides: the key prism is the receiver, the value prism the argument
          .contains("contactTag().buildEntries(domain.contacts(), contacts())")
          .contains(
              ".field(\"contacts\", hkj$ifPresent(wire.contacts(), m ->"
                  + " contactTag().parseEntries(m, contacts())))")
          // keys only: the values are copied
          .contains("notes().buildKeys(domain.notes())")
          .contains(".field(\"notes\", hkj$ifPresent(wire.notes(), notes()::parseKeys))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.ProfileMappingImpl");
      Map<String, String> contacts = new LinkedHashMap<>();
      contacts.put("#work", "ada@corp");
      contacts.put("nope", "also-not-an-email");
      Object dto =
          result
              .loadClass("com.example.ProfileDto")
              .getDeclaredConstructor(Map.class, Map.class)
              .newInstance(contacts, new LinkedHashMap<>(Map.of("#n", "hi")));

      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      assertThatValidated(parsed).isInvalid();
      // Both sides of the one entry report, at the SOURCE key's location.
      Assertions.assertThat(parsed.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("contacts", "nope"), "not a tag"),
              new FieldError(List.of("contacts", "nope"), "not an email address"));
    }

    @Test
    @DisplayName("colliding domain keys are located; without a @MapKey the key types must match")
    void mapKeyCollisionsAndTheUnliftedRule() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Registry",
              """
              package com.example;

              import java.util.Map;

              public record Registry(Map<String, String> byCode) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.RegistryDto",
              """
              package com.example;

              import java.util.Map;

              public record RegistryDto(Map<String, String> byCode) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RegistryMapping",
              """
              package com.example;

              import java.util.Locale;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RegistryMapping extends MappingSpec<Registry, RegistryDto> {
                // a normalising key prism: same types on both sides, so it beats the identity copy
                @MapKey("byCode")
                default ValidatedPrism<String, String> codeKey() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(raw.toLowerCase(Locale.ROOT)), code -> code);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.RegistryMappingImpl");
      Map<String, String> colliding = new LinkedHashMap<>();
      colliding.put("AB", "first");
      colliding.put("ab", "second");
      Object dto =
          result
              .loadClass("com.example.RegistryDto")
              .getDeclaredConstructor(Map.class)
              .newInstance(colliding);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      assertThatValidated(parsed).isInvalid();
      // The second entry would be dropped along with its different value, so it is reported.
      Assertions.assertThat(parsed.getError().toJavaList())
          .containsExactly(new FieldError(List.of("byCode", "ab"), "duplicates an earlier key"));
    }

    @Test
    @DisplayName("mismatched keys with no @MapKey are refused, and the fix offers the annotation")
    void mismatchedKeysOfferTheKeyLeaf() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Keyed",
              """
              package com.example;

              import java.util.Map;
              import java.util.UUID;

              public record Keyed(Map<UUID, String> byId) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.KeyedDto",
              """
              package com.example;

              import java.util.Map;

              public record KeyedDto(Map<String, String> byId) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.KeyedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface KeyedMapping extends MappingSpec<Keyed, KeyedDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("key types differ");
      assertThat(compilation)
          .hadErrorContaining(
              "@MapKey(\"byId\") default ValidatedPrism<java.lang.String, java.util.UUID> byIdKey()");
    }

    @Test
    @DisplayName("a raw Map, a duplicate key leaf, and an inherited unmatched @MapKey")
    void mapKeyShapeSweep() {
      JavaFileObject rawDomain =
          JavaFileObjects.forSourceString(
              "com.example.RawKeyed",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record RawKeyed(java.util.Map entries) {}
              """);
      JavaFileObject rawSpec =
          JavaFileObjects.forSourceString(
              "com.example.RawKeyedMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RawKeyedMapping extends MappingSpec<RawKeyed, RawKeyedDto> {
                @MapKey("entries")
                default ValidatedPrism<String, String> entryKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      JavaFileObject rawWire =
          JavaFileObjects.forSourceString(
              "com.example.RawKeyedDto",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record RawKeyedDto(java.util.Map entries) {}
              """);
      Compilation raw = compile(rawDomain, rawWire, rawSpec);
      assertThat(raw).failed();
      assertThat(raw).hadErrorContaining("names a raw Map component");
      assertThat(raw).hadErrorContaining("a raw Map declares none");

      JavaFileObject duplicate =
          JavaFileObjects.forSourceString(
              "com.example.DuplicateKeyMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface DuplicateKeyMapping extends MappingSpec<Directory, DirectoryDto> {
                @MapKey("entries")
                default ValidatedPrism<String, String> oneKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }

                @MapKey("entries")
                default ValidatedPrism<String, String> anotherKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation twice = compile(EMAIL, DIRECTORY, DIRECTORY_DTO, duplicate);
      assertThat(twice).failed();
      assertThat(twice).hadErrorContaining("'entries' has more than one @MapKey leaf");

      // An INHERITED @MapKey naming nothing stays inert: a mix-in may carry key leaves for
      // components a given spec does not have.
      JavaFileObject mixin =
          JavaFileObjects.forSourceString(
              "com.example.KeyVocabulary",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface KeyVocabulary {
                @MapKey("absentHere")
                default ValidatedPrism<String, String> spareKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      JavaFileObject inheriting =
          JavaFileObjects.forSourceString(
              "com.example.InheritingMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface InheritingMapping
                  extends KeyVocabulary, MappingSpec<Directory, DirectoryDto> {
                default ValidatedPrism<String, EmailAddress> entries() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);
      Compilation inherited = compile(EMAIL, DIRECTORY, DIRECTORY_DTO, mixin, inheriting);
      assertThat(inherited).succeeded();
    }

    @Test
    @DisplayName("a key leaf composes with values lifted through a nested spec")
    void mapKeyComposesWithNestedSpecValues() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              import java.util.Map;

              public record Catalogue(Map<Code, Item> items) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              import java.util.Map;

              public record CatalogueDto(Map<String, ItemDto> items) {}
              """);
      JavaFileObject parts =
          JavaFileObjects.forSourceString(
              "com.example.Item",
              """
              package com.example;

              public record Item(String label) {}
              """);
      JavaFileObject partsDto =
          JavaFileObjects.forSourceString(
              "com.example.ItemDto",
              """
              package com.example;

              public record ItemDto(String label) {}
              """);
      JavaFileObject code =
          JavaFileObjects.forSourceString(
              "com.example.Code",
              """
              package com.example;

              public record Code(String value) {}
              """);
      JavaFileObject itemSpec =
          JavaFileObjects.forSourceString(
              "com.example.ItemMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ItemMapping extends MappingSpec<Item, ItemDto> {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {
                @MapKey("items")
                default ValidatedPrism<String, Code> itemCode() {
                  return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, parts, partsDto, code, itemSpec, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CatalogueMappingImpl"))
          .contains(
              ".field(\"items\", hkj$ifPresent(wire.items(), m -> itemCode().parseEntries(m,"
                  + " ItemMappingImpl.INSTANCE.asValidatedPrism())))")
          .contains(
              "itemCode().buildEntries(domain.items(),"
                  + " ItemMappingImpl.INSTANCE.asValidatedPrism())");
    }

    @Test
    @DisplayName("a @MapKey that converts the wrong types is not a key leaf")
    void mapKeyMustConvertTheKeyTypes() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Slots",
              """
              package com.example;

              import java.util.Map;
              import java.util.UUID;

              public record Slots(Map<UUID, String> byId) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.SlotsDto",
              """
              package com.example;

              import java.util.Map;

              public record SlotsDto(Map<String, String> byId) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SlotsMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface SlotsMapping extends MappingSpec<Slots, SlotsDto> {
                // named at the right component, but over the wrong types: it converts nothing
                @MapKey("byId")
                default ValidatedPrism<String, String> idKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("key types differ");
    }

    @Test
    @DisplayName("a key leaf whose values have no source still reports the values")
    void keyLeafWithUnmappableValues() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Prices",
              """
              package com.example;

              import java.util.Map;

              public record Prices(Map<Code, EmailAddress> byCode) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.PricesDto",
              """
              package com.example;

              import java.util.Map;

              public record PricesDto(Map<String, Integer> byCode) {}
              """);
      JavaFileObject code =
          JavaFileObjects.forSourceString(
              "com.example.Code",
              """
              package com.example;

              public record Code(String value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PricesMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface PricesMapping extends MappingSpec<Prices, PricesDto> {
                @MapKey("byCode")
                default ValidatedPrism<String, Code> codeKey() {
                  return ValidatedPrism.of(raw -> Validated.validNel(new Code(raw)), Code::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, domain, wire, code, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no usable source");
    }

    @Test
    @DisplayName("a @MapKey naming no Map component is refused")
    void mapKeyMustNameAMapComponent() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BadKeyMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface BadKeyMapping extends MappingSpec<Directory, DirectoryDto> {
                @MapKey("nosuch")
                default ValidatedPrism<String, String> whatever() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation unmatched = compile(EMAIL, DIRECTORY, DIRECTORY_DTO, spec);
      assertThat(unmatched).failed();
      assertThat(unmatched)
          .hadErrorContaining("@MapKey(\"nosuch\") names no component of Directory");

      JavaFileObject notAMap =
          JavaFileObjects.forSourceString(
              "com.example.ScalarKeyMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ScalarKeyMapping extends MappingSpec<User, UserDto> {
                @MapKey("name")
                default ValidatedPrism<String, String> nameKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation scalar = compile(EMAIL, DOMAIN, WIRE, notAMap);
      assertThat(scalar).failed();
      assertThat(scalar).hadErrorContaining("names a component that is not a Map");
    }

    @Test
    @DisplayName("parse accumulates across bad entries, each failure located by its key")
    void parseAccumulatesAcrossEntriesLocatedByKey() {
      Compilation compilation = compile(EMAIL, DIRECTORY, DIRECTORY_DTO, DIRECTORY_MAPPING);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.DirectoryMappingImpl");
        Map<String, String> byKey = new LinkedHashMap<>();
        byKey.put("work", "bad-one");
        byKey.put("home", "ada@home.example");
        byKey.put("other", "bad-two");
        Object dto =
            result
                .loadClass("com.example.DirectoryDto")
                .getDeclaredConstructor(Map.class)
                .newInstance(byKey);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("entries", "work"), "not an email address"),
                new FieldError(List.of("entries", "other"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a Map of a nested pair lifts through the sibling impl's prism, paths composing")
    void mapThroughNestedSpecComposes() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Team",
              """
              package com.example;

              import java.util.Map;

              public record Team(Map<String, Customer> members) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.TeamDto",
              """
              package com.example;

              import java.util.Map;

              public record TeamDto(Map<String, CustomerDto> members) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TeamMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TeamMapping extends MappingSpec<Team, TeamDto> {}
              """);

      Compilation compilation =
          compile(
              EMAIL,
              NestedRecursion.CUSTOMER,
              NestedRecursion.CUSTOMER_DTO,
              NestedRecursion.CUSTOMER_MAPPING,
              domain,
              wire,
              spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TeamMappingImpl");
      Assertions.assertThat(generated)
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism().buildValues(domain.members())")
          .contains(
              ".field(\"members\", hkj$ifPresent(wire.members(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parseValues))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.TeamMappingImpl");
        Map<String, Object> byKey = new LinkedHashMap<>();
        byKey.put("alice", result.newInstance("com.example.CustomerDto", "Alice", "nope"));
        Object dto =
            result
                .loadClass("com.example.TeamDto")
                .getDeclaredConstructor(Map.class)
                .newInstance(byKey);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("members", "alice", "email"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an Optional of a nested pair lifts through the sibling impl's prism")
    void optionalThroughNestedSpecComposes() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              import java.util.Optional;

              public record Account(Optional<Customer> owner) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              import java.util.Optional;

              public record AccountDto(Optional<CustomerDto> owner) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping extends MappingSpec<Account, AccountDto> {}
              """);

      Compilation compilation =
          compile(
              EMAIL,
              NestedRecursion.CUSTOMER,
              NestedRecursion.CUSTOMER_DTO,
              NestedRecursion.CUSTOMER_MAPPING,
              domain,
              wire,
              spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountMappingImpl");
      Assertions.assertThat(generated)
          .contains("domain.owner().map(CustomerMappingImpl.INSTANCE.asValidatedPrism()::build)")
          .contains(
              "hkj$ifPresent(wire.owner(), o -> o.map(v ->"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism().parse(v)");
    }

    @Test
    @DisplayName("an identity Map (same keys, same values, no leaf) stays a plain copy")
    void identityMapStaysIdentity() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Labels",
              """
              package com.example;

              import java.util.Map;

              public record Labels(Map<String, String> tags) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LabelsDto",
              """
              package com.example;

              import java.util.Map;

              public record LabelsDto(Map<String, String> tags) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.LabelsMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface LabelsMapping extends MappingSpec<Labels, LabelsDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.LabelsMappingImpl");
      Assertions.assertThat(generated)
          .contains("new LabelsDto(domain.tags())")
          // identity maps copy by reference, but parse scans values: a null value is a located
          // invalid under its key, and the guard does not cost the Iso tier
          .contains(".field(\"tags\", hkj$valuesPresent(wire.tags()))")
          .contains("public Iso<Labels, LabelsDto> asIso()")
          .doesNotContain("parseValues")
          .doesNotContain("buildValues");
    }

    @Test
    @DisplayName("differing key types are rejected when no @MapKey converts them")
    void differingKeyTypesRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Directory",
              """
              package com.example;

              import java.util.Map;

              public record Directory(Map<Integer, EmailAddress> entries) {}
              """);

      Compilation compilation = compile(EMAIL, domain, DIRECTORY_DTO, DIRECTORY_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'entries' maps between Maps whose key types differ");
      assertThat(compilation)
          .hadErrorContaining("Keys pass through as identity unless a @MapKey leaf converts them");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare '@MapKey(\"entries\") default ValidatedPrism<java.lang.String,"
                  + " java.lang.Integer> entriesKey()' on the spec, or align the key types");
    }

    @Test
    @DisplayName("a raw wire Map is rejected")
    void rawWireMapRejected() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.DirectoryDto",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record DirectoryDto(java.util.Map entries) {}
              """);

      Compilation compilation = compile(EMAIL, DIRECTORY, wire, DIRECTORY_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'entries' uses a raw Map, which cannot lift");
      assertThat(compilation).hadErrorContaining("Declare both type arguments on each side");
    }

    @Test
    @DisplayName("a raw domain Map is rejected")
    void rawDomainMapRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Directory",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record Directory(java.util.Map entries) {}
              """);

      Compilation compilation = compile(EMAIL, domain, DIRECTORY_DTO, DIRECTORY_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'entries' uses a raw Map, which cannot lift");
    }

    @Test
    @DisplayName("wildcard type arguments on the wire Map are rejected")
    void wildcardWireMapRejected() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.DirectoryDto",
              """
              package com.example;

              import java.util.Map;

              public record DirectoryDto(Map<String, ? extends String> entries) {}
              """);

      Compilation compilation = compile(EMAIL, DIRECTORY, wire, DIRECTORY_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'entries' uses wildcard Map type arguments");
      assertThat(compilation).hadErrorContaining("Declare exact type arguments on both sides");
    }

    @Test
    @DisplayName("wildcard type arguments on the domain Map are rejected")
    void wildcardDomainMapRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Directory",
              """
              package com.example;

              import java.util.Map;

              public record Directory(Map<String, ? extends EmailAddress> entries) {}
              """);

      Compilation compilation = compile(EMAIL, domain, DIRECTORY_DTO, DIRECTORY_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'entries' uses wildcard Map type arguments");
    }
  }

  @Nested
  @DisplayName("Derived wire fields")
  class DerivedWireFields {

    private static JavaFileObject records(String body) {
      return JavaFileObjects.forSourceString(
          "com.example.Records", "package com.example;\n" + body);
    }

    private static JavaFileObject spec(String name, String body) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.Getter;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MapField;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          """
              + body);
    }

    private static final JavaFileObject PERSON =
        JavaFileObjects.forSourceString(
            "com.example.Person",
            """
            package com.example;

            public record Person(String first, String last) {}
            """);

    private static final JavaFileObject PERSON_DTO =
        JavaFileObjects.forSourceString(
            "com.example.PersonDto",
            """
            package com.example;

            public record PersonDto(String first, String last, String displayName) {}
            """);

    private static final JavaFileObject PERSON_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.PersonMapping",
            """
            package com.example;

            import org.higherkindedj.optics.Getter;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PersonMapping extends MappingSpec<Person, PersonDto> {
              default Getter<Person, String> displayName() {
                return Getter.of(p -> p.first() + " " + p.last());
              }
            }
            """);

    @Test
    @DisplayName("build fills a derived wire component; parse ignores it and round-trips the rest")
    void buildFillsDerivedComponentAndParseIgnoresIt() {
      Compilation compilation = compile(PERSON, PERSON_DTO, PERSON_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.PersonMappingImpl");
      Assertions.assertThat(generated)
          .contains("new PersonDto(domain.first(), domain.last(), displayName().get(domain))")
          .contains(".field(\"first\", hkj$ifPresent(wire.first(), Validated::validNel))")
          .contains(".field(\"last\", hkj$ifPresent(wire.last(), Validated::validNel))")
          .doesNotContain(".field(\"displayName\"")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.PersonMappingImpl");
        Object domain = result.newInstance("com.example.Person", "Ada", "Lovelace");

        Object dto = invoke(impl, "build", domain);
        Assertions.assertThat(invoke(dto, "displayName")).isEqualTo("Ada Lovelace");

        // An inconsistent displayName is derivable data: parse ignores it entirely.
        Object inconsistent =
            result.newInstance("com.example.PersonDto", "Ada", "Lovelace", "nonsense");
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", inconsistent);
        Assertions.assertThat(parsed.isValid()).isTrue();
        Assertions.assertThat(parsed.get()).isEqualTo(domain);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an otherwise-lossless spec with a derived field drops from the Iso tier")
    void otherwiseLosslessSpecWithDerivedFieldDropsFromIsoTier() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a) {}

                    public record W(String a, String stamp) {}
                  }
                  """),
              spec(
                  "StampMapping",
                  """
                  public interface StampMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, String> stamp() {
                      return Getter.of(d -> "v1:" + d.a());
                    }
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.StampMappingImpl");
      Assertions.assertThat(generated)
          .contains("stamp().get(domain)")
          .contains(".field(\"a\", hkj$ifPresent(wire.a(), Validated::validNel))")
          .contains("asValidatedPrism")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName(
        "a derived field composes with a leaf and a rename in one spec, even mid-record: buildArgs"
            + " looks components up by name, not position")
    void derivedComposesWithLeafAndRename() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              public record Account(String name, EmailAddress email) {}
              """);
      // The derived component deliberately sits BETWEEN the mapped ones.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              public record AccountDto(String fullName, String label, String email) {}
              """);
      JavaFileObject mapping =
          spec(
              "AccountMapping",
              """
              public interface AccountMapping extends MappingSpec<Account, AccountDto> {
                @MapField(to = "fullName")
                String name();

                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                default Getter<Account, String> label() {
                  return Getter.of(a -> a.name() + " <" + a.email().value() + ">");
                }
              }
              """);

      Compilation compilation = compile(EMAIL, domain, wire, mapping);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountMappingImpl");
      Assertions.assertThat(generated)
          .contains(
              "new AccountDto(domain.name(), label().get(domain),"
                  + " email().build(domain.email()))")
          .contains(".field(\"name\", hkj$ifPresent(wire.fullName(), Validated::validNel))")
          .contains(".field(\"email\", hkj$ifPresent(wire.email(), email()::parse))")
          .doesNotContain(".field(\"label\"")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountMappingImpl");
        Object emailValue = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object account = result.newInstance("com.example.Account", "Ada", emailValue);

        Object dto = invoke(impl, "build", account);
        Assertions.assertThat(invoke(dto, "fullName")).isEqualTo("Ada");
        Assertions.assertThat(invoke(dto, "label")).isEqualTo("Ada <ada@corp.example>");

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isValid()).isTrue();
        Assertions.assertThat(parsed.get()).isEqualTo(account);

        // Accumulation is untouched by the derived field: the bad leaf still locates.
        Object bad = result.newInstance("com.example.AccountDto", "Ada", "ignored", "nope");
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> failed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", bad);
        Assertions.assertThat(failed.isInvalid()).isTrue();
        Assertions.assertThat(failed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("email"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a derived-field spec stays parse-capable, so it nests like any other")
    void derivedSpecStaysParseCapableAndNests() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record Inner(String v) {}

                    public record InnerDto(String v, String tag) {}

                    public record Outer(Inner inner) {}

                    public record OuterDto(InnerDto inner) {}
                  }
                  """),
              spec(
                  "InnerMapping",
                  """
                  public interface InnerMapping extends MappingSpec<Records.Inner, Records.InnerDto> {
                    default Getter<Records.Inner, String> tag() {
                      return Getter.of(i -> "#" + i.v());
                    }
                  }
                  """),
              spec(
                  "OuterMapping",
                  "public interface OuterMapping extends MappingSpec<Records.Outer,"
                      + " Records.OuterDto> {}"));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.OuterMappingImpl");
      Assertions.assertThat(generated)
          .contains("InnerMappingImpl.INSTANCE.asValidatedPrism().build(domain.inner())")
          .contains(
              ".field(\"inner\", hkj$ifPresent(wire.inner(),"
                  + " InnerMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }

    @Test
    @DisplayName("a Getter method named after a domain component is ambiguous and rejected")
    void getterNamedAfterDomainComponentRejected() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String name) {}

                    public record W(String name) {}
                  }
                  """),
              spec(
                  "AmbiguousMapping",
                  """
                  public interface AmbiguousMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, String> name() {
                      return Getter.of(Records.D::name);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "default method 'name' returns a Getter but is named after a domain component");
      assertThat(compilation)
          .hadErrorContaining("reads as a leaf, but a leaf never returns Getter");
      assertThat(compilation)
          .hadErrorContaining("rename the method after the wire-only component it derives");
    }

    @Test
    @DisplayName("a Getter method naming no wire component is rejected")
    void getterNamingNoWireComponentRejected() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a) {}

                    public record W(String a) {}
                  }
                  """),
              spec(
                  "BogusMapping",
                  """
                  public interface BogusMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, String> bogus() {
                      return Getter.of(Records.D::a);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("derived field method 'bogus' names no component of W");
      assertThat(compilation)
          .hadErrorContaining("Rename the method after the wire component it derives");
    }

    private static final JavaFileObject EXTRA_RECORDS =
        records(
            """
            public final class Records {
              public record D(String a) {}

              public record W(String a, String extra) {}
            }
            """);

    @Test
    @DisplayName("a raw Getter return type is rejected with the exact shape to declare")
    void rawGetterRejected() {
      Compilation compilation =
          compile(
              EXTRA_RECORDS,
              spec(
                  "RawGetterMapping",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface RawGetterMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter extra() {
                      return null;
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("derived field 'extra' must return Getter<D, java.lang.String>");
      assertThat(compilation).hadErrorContaining("applying the getter to the whole domain value");
    }

    @Test
    @DisplayName("a Getter whose source type is not the domain record is rejected")
    void getterWithWrongSourceTypeRejected() {
      Compilation compilation =
          compile(
              EXTRA_RECORDS,
              spec(
                  "WrongSourceMapping",
                  """
                  public interface WrongSourceMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<String, String> extra() {
                      return Getter.of(s -> s);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("derived field 'extra' must return Getter<D, java.lang.String>");
      assertThat(compilation)
          .hadErrorContaining("Declare 'default Getter<D, java.lang.String> extra()'");
    }

    @Test
    @DisplayName("a Getter whose target type is not the wire component's is rejected")
    void getterWithWrongTargetTypeRejected() {
      Compilation compilation =
          compile(
              EXTRA_RECORDS,
              spec(
                  "WrongTargetMapping",
                  """
                  public interface WrongTargetMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, Integer> extra() {
                      return Getter.of(d -> 42);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("derived field 'extra' must return Getter<D, java.lang.String>");
    }

    @Test
    @DisplayName("a rename targeting a wire component a derived field fills is rejected")
    void renameTargetingDerivedComponentRejected() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a, String b) {}

                    public record W(String a, String x) {}
                  }
                  """),
              spec(
                  "ClashMapping",
                  """
                  public interface ClashMapping extends MappingSpec<Records.D, Records.W> {
                    @MapField(to = "x")
                    String b();

                    default Getter<Records.D, String> x() {
                      return Getter.of(Records.D::a);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "derived field 'x' fills a wire component the @MapField rename on 'b' also"
                  + " targets");
      assertThat(compilation).hadErrorContaining("Each wire component takes exactly one source");
    }

    @Test
    @DisplayName("a projection combined with a derived field is incoherent and rejected")
    void projectionWithDerivedFieldRejected() {
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a, String b) {}

                    public record W(String a, String d) {}
                  }
                  """),
              spec(
                  "MixedMapping",
                  """
                  public interface MixedMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, String> d() {
                      return Getter.of(x -> x.a() + x.b());
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'W' combines a projection with derived fields");
      assertThat(compilation).hadErrorContaining("The projection write-back (asLens or patch)");
      assertThat(compilation).hadErrorContaining("map the smaller wire as a plain projection");
    }

    @Test
    @DisplayName("a parameterised Getter method is not a derived field; the extras error guides")
    void parameterisedGetterMethodIsNotADerivedField() {
      Compilation compilation =
          compile(
              EXTRA_RECORDS,
              spec(
                  "ParamGetterMapping",
                  """
                  public interface ParamGetterMapping extends MappingSpec<Records.D, Records.W> {
                    default Getter<Records.D, String> extra(boolean strict) {
                      return Getter.of(Records.D::a);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'W' has more components than 'D'");
      assertThat(compilation).hadErrorContaining("declare derived fields");
    }
  }

  @Nested
  @DisplayName("Nested recursion")
  class NestedRecursion {

    private static final JavaFileObject CUSTOMER =
        JavaFileObjects.forSourceString(
            "com.example.Customer",
            """
            package com.example;

            public record Customer(String name, EmailAddress email) {}
            """);

    private static final JavaFileObject CUSTOMER_DTO =
        JavaFileObjects.forSourceString(
            "com.example.CustomerDto",
            """
            package com.example;

            public record CustomerDto(String name, String email) {}
            """);

    private static final JavaFileObject CUSTOMER_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CustomerMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
              default ValidatedPrism<String, EmailAddress> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    EmailAddress::value);
              }
            }
            """);

    @Test
    @DisplayName("a component pair mapped by a sibling spec delegates to its generated impl")
    void nestedComponentDelegatesToSiblingImpl() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Order",
              """
              package com.example;

              public record Order(String id, Customer customer) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.OrderDto",
              """
              package com.example;

              public record OrderDto(String id, CustomerDto customer) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.OrderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderDto> {}
              """);

      Compilation compilation =
          compile(EMAIL, CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING, domain, wire, spec);
      assertThat(compilation).succeeded();
      String customerImpl = generatedSource(compilation, "com.example.CustomerMappingImpl");
      Assertions.assertThat(customerImpl)
          .contains("public ValidatedPrism<CustomerDto, Customer> asValidatedPrism()")
          .contains("return ValidatedPrism.of(this::parse, this::build)");
      String orderImpl = generatedSource(compilation, "com.example.OrderMappingImpl");
      Assertions.assertThat(orderImpl)
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism().build(domain.customer())")
          .contains(
              ".field(\"customer\", hkj$ifPresent(wire.customer(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parse))")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("containers of a nested pair lift through the sibling impl's prism")
    void containerOfNestedLifts() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Company",
              """
              package com.example;

              import java.util.List;

              public record Company(String name, List<Customer> customers) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.CompanyDto",
              """
              package com.example;

              import java.util.List;

              public record CompanyDto(String name, List<CustomerDto> customers) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CompanyMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CompanyMapping extends MappingSpec<Company, CompanyDto> {}
              """);

      Compilation compilation =
          compile(EMAIL, CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING, domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.CompanyMappingImpl");
      Assertions.assertThat(generated)
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism().buildAll(domain.customers())")
          .contains(
              ".field(\"customers\", hkj$ifPresent(wire.customers(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parseAll))");

      // The composed location, end to end: field label, then element index, then the
      // nested spec's own path — customers.1.email, never customers.email.1.
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CompanyMappingImpl");
        Object good =
            result
                .loadClass("com.example.CustomerDto")
                .getDeclaredConstructor(String.class, String.class)
                .newInstance("Ada", "ada@x.com");
        Object bad =
            result
                .loadClass("com.example.CustomerDto")
                .getDeclaredConstructor(String.class, String.class)
                .newInstance("Bob", "nope");
        Object companyDto =
            result
                .loadClass("com.example.CompanyDto")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("Acme", List.of(good, bad));

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", companyDto);
        assertThatValidated(parsed)
            .isInvalid()
            .hasFieldErrors("customers.1.email: not an email address");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a self-recursive record pair terminates: the impl delegates to itself")
    void selfRecursiveSpecTerminates() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Tree",
              """
              package com.example;

              import java.util.List;

              public record Tree(String value, List<Tree> children) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.TreeDto",
              """
              package com.example;

              import java.util.List;

              public record TreeDto(String value, List<TreeDto> children) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TreeMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TreeMapping extends MappingSpec<Tree, TreeDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TreeMappingImpl");
      Assertions.assertThat(generated)
          .contains("TreeMappingImpl.INSTANCE.asValidatedPrism().buildAll(domain.children())")
          .contains(
              ".field(\"children\", hkj$ifPresent(wire.children(),"
                  + " TreeMappingImpl.INSTANCE.asValidatedPrism()::parseAll))");
    }

    @Test
    @DisplayName("two specs for the same pair make a nested component ambiguous")
    void ambiguousNestedSpecsRejected() {
      JavaFileObject duplicate =
          JavaFileObjects.forSourceString(
              "com.example.OtherCustomerMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface OtherCustomerMapping extends MappingSpec<Customer, CustomerDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Order",
              """
              package com.example;

              public record Order(Customer customer) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.OrderDto",
              """
              package com.example;

              public record OrderDto(CustomerDto customer) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.OrderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderDto> {}
              """);

      Compilation compilation =
          compile(EMAIL, CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING, duplicate, domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("field 'customer' matches more than one mapping spec");
      assertThat(compilation)
          .hadErrorContaining("Add a leaf method 'customer()' delegating to the spec you want");
    }
  }

  @Nested
  @DisplayName("Sealed dispatch")
  class SealedDispatch {

    private static final JavaFileObject PAYMENT =
        JavaFileObjects.forSourceString(
            "com.example.Payment",
            """
            package com.example;

            public sealed interface Payment permits Card, Bank {}
            """);

    private static final JavaFileObject CARD =
        JavaFileObjects.forSourceString(
            "com.example.Card",
            """
            package com.example;

            public record Card(String number) implements Payment {}
            """);

    private static final JavaFileObject BANK =
        JavaFileObjects.forSourceString(
            "com.example.Bank",
            """
            package com.example;

            public record Bank(String iban) implements Payment {}
            """);

    private static final JavaFileObject PAYMENT_DTO =
        JavaFileObjects.forSourceString(
            "com.example.PaymentDto",
            """
            package com.example;

            public sealed interface PaymentDto permits CardDto, BankDto {}
            """);

    private static final JavaFileObject CARD_DTO =
        JavaFileObjects.forSourceString(
            "com.example.CardDto",
            """
            package com.example;

            public record CardDto(String number) implements PaymentDto {}
            """);

    private static final JavaFileObject BANK_DTO =
        JavaFileObjects.forSourceString(
            "com.example.BankDto",
            """
            package com.example;

            public record BankDto(String iban) implements PaymentDto {}
            """);

    private static final JavaFileObject CARD_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CardMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CardMapping extends MappingSpec<Card, CardDto> {}
            """);

    private static final JavaFileObject BANK_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.BankMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface BankMapping extends MappingSpec<Bank, BankDto> {}
            """);

    private static final JavaFileObject PAYMENT_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.PaymentMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
            """);

    @Test
    @DisplayName("build and parse switch over the permitted subtype pairs")
    void sealedPairDispatches() {
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              PAYMENT_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.PaymentMappingImpl");
      Assertions.assertThat(generated)
          .contains("return switch (domain) {")
          .contains("case Card v -> CardMappingImpl.INSTANCE.build(v);")
          .contains("case Bank v -> BankMappingImpl.INSTANCE.build(v);")
          .contains("return switch (wire) {")
          .contains("case CardDto v -> CardMappingImpl.INSTANCE.parse(v).map(d -> (Payment) d);")
          .contains("case BankDto v -> BankMappingImpl.INSTANCE.parse(v).map(d -> (Payment) d);")
          .contains("public ValidatedPrism<PaymentDto, Payment> asValidatedPrism()")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("a sealed mapping nests inside a record like any other spec")
    void sealedMappingNestsInRecord() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Wallet",
              """
              package com.example;

              public record Wallet(String owner, Payment payment) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WalletDto",
              """
              package com.example;

              public record WalletDto(String owner, PaymentDto payment) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WalletMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WalletMapping extends MappingSpec<Wallet, WalletDto> {}
              """);

      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              PAYMENT_MAPPING,
              domain,
              wire,
              spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.WalletMappingImpl");
      Assertions.assertThat(generated)
          .contains("PaymentMappingImpl.INSTANCE.asValidatedPrism().build(domain.payment())")
          .contains(
              ".field(\"payment\", hkj$ifPresent(wire.payment(),"
                  + " PaymentMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }

    @Test
    @DisplayName("a domain subtype without a spec is rejected")
    void missingSubtypeSpecRejected() {
      Compilation compilation =
          compile(
              PAYMENT, CARD, BANK, PAYMENT_DTO, CARD_DTO, BANK_DTO, CARD_MAPPING, PAYMENT_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("permitted subtype 'com.example.Bank'");
      assertThat(compilation).hadErrorContaining("has no mapping spec");
    }

    @Test
    @DisplayName("two specs for one domain subtype make the dispatch ambiguous")
    void ambiguousSubtypeSpecsRejected() {
      JavaFileObject duplicate =
          JavaFileObjects.forSourceString(
              "com.example.OtherCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OtherCardMapping extends MappingSpec<Card, CardDto> {}
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              duplicate,
              BANK_MAPPING,
              PAYMENT_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("matches more than one mapping spec");
      assertThat(compilation).hadErrorContaining("Keep exactly one spec per subtype pair");
    }

    @Test
    @DisplayName("two domain subtypes targeting one wire subtype are rejected")
    void duplicateWireTargetRejected() {
      JavaFileObject bankToCard =
          JavaFileObjects.forSourceString(
              "com.example.BankMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BankMapping extends MappingSpec<Bank, CardDto> {
                @MapField(to = "number")
                String iban();
              }
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              bankToCard,
              PAYMENT_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is targeted by more than one domain subtype");
    }

    @Test
    @DisplayName("@MapField on a sealed mapping is rejected")
    void mapFieldOnSealedRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                @MapField(to = "anything")
                String number();
              }
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@MapField has no meaning on a sealed mapping");
    }

    @Test
    @DisplayName("an abstract leaf has no meaning on a sealed mapping either")
    void abstractLeafOnSealedRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                ValidatedPrism<String, String> number();
              }
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("abstract leaf 'number' needs a generic spec");
    }

    @Test
    @DisplayName("local leaves and derived fields have no meaning on a sealed mapping")
    void sealedLocalVocabularyRejected() {
      JavaFileObject leafSpec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                default ValidatedPrism<String, String> number() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }
              }
              """);
      Compilation leaf =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              leafSpec);
      assertThat(leaf).failed();
      assertThat(leaf).hadErrorContaining("leaf 'number' has no meaning on a sealed mapping");
      assertThat(leaf).hadErrorContaining("Move the method onto the subtype pair's own spec");

      JavaFileObject getterSpec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.Getter;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                default Getter<Payment, String> display() {
                  return Getter.of(Object::toString);
                }
              }
              """);
      Compilation getter =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              getterSpec);
      assertThat(getter).failed();
      assertThat(getter)
          .hadErrorContaining("derived field 'display' has no meaning on a sealed mapping");
    }

    @Test
    @DisplayName("plain helpers stay legal on a sealed mapping: only vocabulary shapes bind")
    void sealedPlainHelpersStayLegal() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                static String currency() {
                  return "GBP";
                }

                default int retries() {
                  return 3;
                }

                default String label() {
                  return "payments";
                }
              }
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("inherited vocabulary stays inert on a sealed mapping: shared mix-ins still fit")
    void sealedInheritedVocabularyStaysInert() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.SharedVocabulary",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface SharedVocabulary {
                default ValidatedPrism<String, String> number() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping
                  extends SharedVocabulary, MappingSpec<Payment, PaymentDto> {}
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              vocabulary,
              spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("an inherited @MapField is just as meaningless on a sealed mapping")
    void inheritedMapFieldOnSealedRejected() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.RenameVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface RenameVocabulary {
                @MapField(to = "anything")
                String number();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping
                  extends RenameVocabulary, MappingSpec<Payment, PaymentDto> {}
              """);
      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              vocabulary,
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@MapField has no meaning on a sealed mapping");
    }

    @Test
    @DisplayName("a wire subtype no domain subtype produces is rejected")
    void uncoveredWireSubtypeRejected() {
      JavaFileObject widerWire =
          JavaFileObjects.forSourceString(
              "com.example.PaymentDto",
              """
              package com.example;

              public sealed interface PaymentDto permits CardDto, BankDto, VoucherDto {}
              """);
      JavaFileObject voucherDto =
          JavaFileObjects.forSourceString(
              "com.example.VoucherDto",
              """
              package com.example;

              public record VoucherDto(String code) implements PaymentDto {}
              """);

      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              widerWire,
              CARD_DTO,
              BANK_DTO,
              voucherDto,
              CARD_MAPPING,
              BANK_MAPPING,
              PAYMENT_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("permitted subtype 'com.example.VoucherDto'")
          .inFile(PAYMENT_MAPPING);
      assertThat(compilation).hadErrorContaining("is never produced");
    }

    @Test
    @DisplayName("a default with the generated 'parse' signature is rejected on a sealed mapping")
    void sealedParseCollisionIsRejected() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.PaymentMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {
                default Validated<NonEmptyList<FieldError>, Payment> parse(PaymentDto wire) {
                  return Validated.invalidNel(FieldError.of("never runs"));
                }
              }
              """);

      Compilation compilation =
          compile(
              PAYMENT,
              CARD,
              BANK,
              PAYMENT_DTO,
              CARD_DTO,
              BANK_DTO,
              CARD_MAPPING,
              BANK_MAPPING,
              colliding);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parse(PaymentDto)' collides with the 'parse' member the generated"
                  + " PaymentMappingImpl emits");
      assertThat(compilation).hadErrorContaining("a sealed dispatch mapping");
    }
  }

  @Nested
  @DisplayName("Leaf-carrying projection (validated patch tier)")
  class LeafCarryingProjectionPatchTier {

    private static final JavaFileObject ACCOUNT =
        JavaFileObjects.forSourceString(
            "com.example.Account",
            """
            package com.example;

            public record Account(String id, String email, String notes, int age) {}
            """);

    private static final JavaFileObject ACCOUNT_PATCH_DTO =
        JavaFileObjects.forSourceString(
            "com.example.AccountPatchDto",
            """
            package com.example;

            public record AccountPatchDto(String email, String notes, int age) {}
            """);

    private static final JavaFileObject ACCOUNT_PATCH_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.AccountPatchMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface AccountPatchMapping extends MappingSpec<Account, AccountPatchDto> {
              default ValidatedPrism<String, String> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(raw.toLowerCase(java.util.Locale.ROOT))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    email -> email);
              }
            }
            """);

    @SuppressWarnings("unchecked")
    private Validated<NonEmptyList<FieldError>, Object> patch(
        Object impl, Object domain, Object wire) {
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "patch", domain, wire);
    }

    @Test
    @DisplayName("a leaf-carrying projection emits build + validated patch, no asLens, no parse")
    void leafProjectionEmitsBuildAndPatch() {
      Compilation compilation = compile(ACCOUNT, ACCOUNT_PATCH_DTO, ACCOUNT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("public AccountPatchDto build(Account domain)")
          .contains("public Validated<NonEmptyList<FieldError>, Account> patch(")
          .contains("AccountPatchDto wire)")
          // reference reads are guarded into located FieldErrors; the primitive is not
          .contains(".field(\"email\", hkj$ifPresent(wire.email(), email()::parse))")
          .contains(".field(\"notes\", hkj$ifPresent(wire.notes(), Validated::validNel))")
          .contains(".field(\"age\", Validated.validNel(wire.age()))")
          // projected components bind by name; unprojected read from the domain argument
          .contains(".apply((email, notes, age) -> new Account(domain.id(), email, notes, age))")
          .doesNotContain("asLens() {")
          .doesNotContain("asIso")
          .doesNotContain("asValidatedPrism");
    }

    @Test
    @DisplayName("patch applies a valid wire, normalising through the leaf, keeping the rest")
    void patchAppliesValidWireAndPreservesUnprojected() {
      Compilation compilation = compile(ACCOUNT, ACCOUNT_PATCH_DTO, ACCOUNT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Class<?> accountClass = result.loadClass("com.example.Account");
        Object domain =
            accountClass
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "old@example.com", "keep", 30);
        Object wire =
            result
                .loadClass("com.example.AccountPatchDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("Ada@Example.COM", "fresh notes", 44);

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        Assertions.assertThat(patched.isValid()).isTrue();
        Object account = patched.get();
        Assertions.assertThat(invoke(account, "id")).isEqualTo("7");
        Assertions.assertThat(invoke(account, "email")).isEqualTo("ada@example.com");
        Assertions.assertThat(invoke(account, "notes")).isEqualTo("fresh notes");
        Assertions.assertThat(invoke(account, "age")).isEqualTo(44);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("patch of the domain's own build is identity (the projection round-trip law)")
    void patchOfOwnBuildIsIdentity() {
      Compilation compilation = compile(ACCOUNT, ACCOUNT_PATCH_DTO, ACCOUNT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Account")
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "ada@example.com", "keep", 30);

        Object wire = invoke(impl, "build", domain);
        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        Assertions.assertThat(patched.isValid()).isTrue();
        Assertions.assertThat(patched.get()).isEqualTo(domain);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("every bad projected field accumulates into one located Invalid")
    void patchAccumulatesEveryBadField() {
      Compilation compilation = compile(ACCOUNT, ACCOUNT_PATCH_DTO, ACCOUNT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Account")
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "ada@example.com", "keep", 30);
        Object wire =
            result
                .loadClass("com.example.AccountPatchDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("nope", null, 44);

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        assertThatValidated(patched)
            .isInvalid()
            .hasFieldErrors("email: not an email address", "notes: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an all-null wire is total: every absence located, nothing thrown")
    void nullProjectedComponentsAreLocatedNotThrown() {
      Compilation compilation = compile(ACCOUNT, ACCOUNT_PATCH_DTO, ACCOUNT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Account")
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "ada@example.com", "keep", 30);
        Object wire =
            result
                .loadClass("com.example.AccountPatchDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance(null, null, 44);

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        assertThatValidated(patched)
            .isInvalid()
            .hasFieldErrors("email: must not be null", "notes: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a @MapField rename works in the patch tier; errors locate under domain names")
    void renamedProjectionComponentWorksInPatchTier() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.MailPatchDto",
              """
              package com.example;

              public record MailPatchDto(String email, String memo) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.MailPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface MailPatchMapping extends MappingSpec<Account, MailPatchDto> {
                @MapField(to = "memo")
                String notes();

                default ValidatedPrism<String, String> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(raw)
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      email -> email);
                }
              }
              """);

      Compilation compilation = compile(ACCOUNT, wire, spec);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.MailPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Account")
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "ada@example.com", "keep", 30);
        Class<?> wireClass = result.loadClass("com.example.MailPatchDto");

        // Bad email locates under the DOMAIN component name, not the wire name.
        Validated<NonEmptyList<FieldError>, Object> bad =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(String.class, String.class)
                    .newInstance("nope", "new memo"));
        assertThatValidated(bad).isInvalid().hasFieldErrors("email: not an email address");

        // A valid wire writes the renamed component back to its domain source.
        Validated<NonEmptyList<FieldError>, Object> good =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(String.class, String.class)
                    .newInstance("grace@example.com", "new memo"));
        Assertions.assertThat(good.isValid()).isTrue();
        Assertions.assertThat(invoke(good.get(), "notes")).isEqualTo("new memo");
        Assertions.assertThat(invoke(good.get(), "email")).isEqualTo("grace@example.com");
        Assertions.assertThat(invoke(good.get(), "id")).isEqualTo("7");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a nested-spec projected component composes dotted error paths")
    void nestedSpecProjectionComposesDottedPaths() {
      JavaFileObject address =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              public record Address(String zip) {}
              """);
      JavaFileObject addressDto =
          JavaFileObjects.forSourceString(
              "com.example.AddressDto",
              """
              package com.example;

              public record AddressDto(String zip) {}
              """);
      JavaFileObject addressMapping =
          JavaFileObjects.forSourceString(
              "com.example.AddressMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface AddressMapping extends MappingSpec<Address, AddressDto> {
                default ValidatedPrism<String, String> zip() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.matches("\\\\d{5}")
                              ? Validated.validNel(raw)
                              : Validated.invalidNel(FieldError.of("must be 5 digits")),
                      zip -> zip);
                }
              }
              """);
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              public record Customer(String id, Address address) {}
              """);
      JavaFileObject customerPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchDto",
              """
              package com.example;

              public record CustomerPatchDto(AddressDto address) {}
              """);
      JavaFileObject customerPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerPatchMapping extends MappingSpec<Customer, CustomerPatchDto> {}
              """);

      Compilation compilation =
          compile(
              address,
              addressDto,
              addressMapping,
              customer,
              customerPatchDto,
              customerPatchMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CustomerPatchMappingImpl");
        Object domainAddress =
            result
                .loadClass("com.example.Address")
                .getDeclaredConstructor(String.class)
                .newInstance("12345");
        Object domain =
            result
                .loadClass("com.example.Customer")
                .getDeclaredConstructor(String.class, result.loadClass("com.example.Address"))
                .newInstance("7", domainAddress);
        Object wireAddress =
            result
                .loadClass("com.example.AddressDto")
                .getDeclaredConstructor(String.class)
                .newInstance("nope");
        Object wire =
            result
                .loadClass("com.example.CustomerPatchDto")
                .getDeclaredConstructor(result.loadClass("com.example.AddressDto"))
                .newInstance(wireAddress);

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        assertThatValidated(patched).isInvalid().hasFieldErrors("address.zip: must be 5 digits");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("container lifting works in projections: List elements locate by index")
    void listProjectionLiftsElements() {
      JavaFileObject roster =
          JavaFileObjects.forSourceString(
              "com.example.Roster",
              """
              package com.example;

              import java.util.List;

              public record Roster(String id, List<EmailAddress> emails) {}
              """);
      JavaFileObject rosterPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.RosterPatchDto",
              """
              package com.example;

              import java.util.List;

              public record RosterPatchDto(List<String> emails) {}
              """);
      JavaFileObject rosterPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.RosterPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RosterPatchMapping extends MappingSpec<Roster, RosterPatchDto> {
                default ValidatedPrism<String, EmailAddress> emails() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, roster, rosterPatchDto, rosterPatchMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.RosterPatchMappingImpl");
        Object domainEmail =
            result
                .loadClass("com.example.EmailAddress")
                .getDeclaredConstructor(String.class)
                .newInstance("ada@example.com");
        Object domain =
            result
                .loadClass("com.example.Roster")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("7", List.of(domainEmail));
        Object wire =
            result
                .loadClass("com.example.RosterPatchDto")
                .getDeclaredConstructor(List.class)
                .newInstance(List.of("good@example.com", "nope"));

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        // "nope" at position 1: located by index, making this test's name literal.
        assertThatValidated(patched).isInvalid().hasFieldErrors("emails.1: not an email address");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a projection scans its identity Set and array components too")
    void setAndArrayProjectionsAreScanned() throws Exception {
      JavaFileObject crew =
          JavaFileObjects.forSourceString(
              "com.example.Crew",
              """
              package com.example;

              import java.util.Set;

              public record Crew(String id, EmailAddress email, Set<String> tags, String[] codes) {}
              """);
      JavaFileObject crewPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.CrewPatchDto",
              """
              package com.example;

              import java.util.Set;

              public record CrewPatchDto(String email, Set<String> tags, String[] codes) {}
              """);
      JavaFileObject crewPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.CrewPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              // The leaf makes the projection fallible, so it takes the validated patch tier -
              // where the identity containers beside it still carry their scans.
              @GenerateMapping
              public interface CrewPatchMapping extends MappingSpec<Crew, CrewPatchDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, crew, crewPatchDto, crewPatchMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CrewPatchMappingImpl"))
          .contains("Set<E> values")
          .contains("E[] values");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CrewPatchMappingImpl");
      Object domain =
          result
              .loadClass("com.example.Crew")
              .getDeclaredConstructor(
                  String.class,
                  result.loadClass("com.example.EmailAddress"),
                  Set.class,
                  String[].class)
              .newInstance(
                  "7",
                  result
                      .loadClass("com.example.EmailAddress")
                      .getDeclaredConstructor(String.class)
                      .newInstance("keep@example.com"),
                  Set.of("keep"),
                  new String[] {"keep"});
      Set<String> tags = new LinkedHashSet<>(List.of("fine"));
      tags.add(null);
      Object wire =
          result
              .loadClass("com.example.CrewPatchDto")
              .getDeclaredConstructor(String.class, Set.class, String[].class)
              .newInstance("ada@example.com", tags, new String[] {"ok", null});

      Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
      assertThatValidated(patched)
          .isInvalid()
          .hasFieldErrors("tags: must not contain a null element", "codes.1: must not be null");
    }

    @Test
    @DisplayName("Optional lifting works in projections: present parses, empty stays, null locates")
    void optionalProjectionLifts() {
      JavaFileObject profile =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.Optional;

              public record Profile(String id, Optional<EmailAddress> backup) {}
              """);
      JavaFileObject profilePatchDto =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchDto",
              """
              package com.example;

              import java.util.Optional;

              public record ProfilePatchDto(Optional<String> backup) {}
              """);
      JavaFileObject profilePatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ProfilePatchMapping extends MappingSpec<Profile, ProfilePatchDto> {
                default ValidatedPrism<String, EmailAddress> backup() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, profile, profilePatchDto, profilePatchMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.ProfilePatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Profile")
                .getDeclaredConstructor(String.class, java.util.Optional.class)
                .newInstance("7", java.util.Optional.empty());
        Class<?> wireClass = result.loadClass("com.example.ProfilePatchDto");

        Validated<NonEmptyList<FieldError>, Object> present =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(java.util.Optional.class)
                    .newInstance(java.util.Optional.of("ada@example.com")));
        Assertions.assertThat(present.isValid()).isTrue();
        Assertions.assertThat(invoke(present.get(), "backup"))
            .asInstanceOf(InstanceOfAssertFactories.OPTIONAL)
            .isPresent();

        Validated<NonEmptyList<FieldError>, Object> empty =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(java.util.Optional.class)
                    .newInstance(java.util.Optional.empty()));
        Assertions.assertThat(empty.isValid()).isTrue();
        Assertions.assertThat(invoke(empty.get(), "backup"))
            .asInstanceOf(InstanceOfAssertFactories.OPTIONAL)
            .isEmpty();

        Validated<NonEmptyList<FieldError>, Object> bad =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(java.util.Optional.class)
                    .newInstance(java.util.Optional.of("nope")));
        assertThatValidated(bad).isInvalid().hasFieldErrors("backup: not an email address");

        Validated<NonEmptyList<FieldError>, Object> nullRead =
            patch(
                impl,
                domain,
                wireClass
                    .getDeclaredConstructor(java.util.Optional.class)
                    .newInstance(new Object[] {null}));
        assertThatValidated(nullRead).isInvalid().hasFieldErrors("backup: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("Map value lifting works in projections: failures locate by key")
    void mapProjectionLiftsValues() {
      JavaFileObject book =
          JavaFileObjects.forSourceString(
              "com.example.AddressBook",
              """
              package com.example;

              import java.util.Map;

              public record AddressBook(String id, Map<String, EmailAddress> entries) {}
              """);
      JavaFileObject bookPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.AddressBookPatchDto",
              """
              package com.example;

              import java.util.Map;

              public record AddressBookPatchDto(Map<String, String> entries) {}
              """);
      JavaFileObject bookPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.AddressBookPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface AddressBookPatchMapping
                  extends MappingSpec<AddressBook, AddressBookPatchDto> {
                default ValidatedPrism<String, EmailAddress> entries() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, book, bookPatchDto, bookPatchMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AddressBookPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.AddressBook")
                .getDeclaredConstructor(String.class, Map.class)
                .newInstance("7", Map.of());
        Object wire =
            result
                .loadClass("com.example.AddressBookPatchDto")
                .getDeclaredConstructor(Map.class)
                .newInstance(new LinkedHashMap<>(Map.of("work", "nope")));

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domain, wire);
        assertThatValidated(patched)
            .isInvalid()
            .hasFieldErrors("entries.work: not an email address");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("components named 'domain' or 'wire' do not shadow the patch method parameters")
    void reservedNamesDoNotShadowPatchParameters() {
      JavaFileObject site =
          JavaFileObjects.forSourceString(
              "com.example.Site",
              """
              package com.example;

              public record Site(String id, String domain, String wire) {}
              """);
      JavaFileObject sitePatchDto =
          JavaFileObjects.forSourceString(
              "com.example.SitePatchDto",
              """
              package com.example;

              public record SitePatchDto(String domain, String wire) {}
              """);
      JavaFileObject sitePatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.SitePatchMapping",
              """
              package com.example;

              import java.util.Locale;
              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface SitePatchMapping extends MappingSpec<Site, SitePatchDto> {
                default ValidatedPrism<String, String> domain() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains(".")
                              ? Validated.validNel(raw.toLowerCase(Locale.ROOT))
                              : Validated.invalidNel(FieldError.of("not a dotted host name")),
                      domain -> domain);
                }
              }
              """);

      Compilation compilation = compile(site, sitePatchDto, sitePatchMapping);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.SitePatchMappingImpl");
      Assertions.assertThat(generated)
          .contains(".apply((domain_, wire_) -> new Site(domain.id(), domain_, wire_))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.SitePatchMappingImpl");
        Object domainValue =
            result
                .loadClass("com.example.Site")
                .getDeclaredConstructor(String.class, String.class, String.class)
                .newInstance("7", "example.org", "rss");
        Object wireValue =
            result
                .loadClass("com.example.SitePatchDto")
                .getDeclaredConstructor(String.class, String.class)
                .newInstance("Example.COM", "atom");

        Validated<NonEmptyList<FieldError>, Object> patched = patch(impl, domainValue, wireValue);
        Assertions.assertThat(patched.isValid()).isTrue();
        Assertions.assertThat(invoke(patched.get(), "id")).isEqualTo("7");
        Assertions.assertThat(invoke(patched.get(), "domain")).isEqualTo("example.com");
        Assertions.assertThat(invoke(patched.get(), "wire")).isEqualTo("atom");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a component named 'domain_' pushes the renamed parameter to 'domain__'")
    void suffixedReservedNameKeepsSuffixingUntilFree() {
      JavaFileObject odd =
          JavaFileObjects.forSourceString(
              "com.example.Odd",
              """
              package com.example;

              public record Odd(String id, String domain, String domain_) {}
              """);
      JavaFileObject oddPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.OddPatchDto",
              """
              package com.example;

              public record OddPatchDto(String domain, String domain_) {}
              """);
      JavaFileObject oddPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.OddPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface OddPatchMapping extends MappingSpec<Odd, OddPatchDto> {
                default ValidatedPrism<String, String> domain() {
                  return ValidatedPrism.of(Validated::validNel, value -> value);
                }
              }
              """);

      Compilation compilation = compile(odd, oddPatchDto, oddPatchMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.OddPatchMappingImpl"))
          .contains(".apply((domain__, domain_) -> new Odd(domain.id(), domain__, domain_))");
    }

    @Test
    @DisplayName(
        "a projection wider than one fields() ladder patches through chunked ladders,"
            + " unprojected components still read from the domain")
    void patchBeyondLadderCompilesChunked() {
      String domainComponents =
          IntStream.rangeClosed(1, 18)
              .mapToObj(i -> "String f" + i)
              .collect(Collectors.joining(", "));
      String wireComponents =
          IntStream.rangeClosed(1, 17)
              .mapToObj(i -> "String f" + i)
              .collect(Collectors.joining(", "));
      JavaFileObject wideDomain =
          JavaFileObjects.forSourceString(
              "com.example.WideDomain",
              "package com.example;\n\npublic record WideDomain(" + domainComponents + ") {}\n");
      JavaFileObject wideWire =
          JavaFileObjects.forSourceString(
              "com.example.WideWireDto",
              "package com.example;\n\npublic record WideWireDto(" + wireComponents + ") {}\n");
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WideMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface WideMapping extends MappingSpec<WideDomain, WideWireDto> {
                default ValidatedPrism<String, String> f1() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);

      Compilation compilation = compile(wideDomain, wideWire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.WideMappingImpl");
      Assertions.assertThat(generated)
          .contains("Tuple16::new")
          .contains(".apply(v -> v)")
          .contains("NonEmptyList.semigroup()")
          .contains("domain.f18()");
    }
  }

  @Nested
  @DisplayName("Projection (Lens tier)")
  class ProjectionLensTier {

    private static final JavaFileObject EMPLOYEE =
        JavaFileObjects.forSourceString(
            "com.example.Employee",
            """
            package com.example;

            public record Employee(String name, String department, int age) {}
            """);

    @Test
    @DisplayName("a smaller wire maps as build + asLens write-back, with no parse")
    void projectionEmitsBuildAndLens() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeCardDto",
              """
              package com.example;

              public record EmployeeCardDto(String fullName, String department) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {
                @MapField(to = "fullName")
                String name();
              }
              """);

      Compilation compilation = compile(EMPLOYEE, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.EmployeeCardMappingImpl");
      Assertions.assertThat(generated)
          .contains("public EmployeeCardDto build(Employee domain)")
          .contains("new EmployeeCardDto(domain.name(), domain.department())")
          .contains("public Lens<Employee, EmployeeCardDto> asLens()")
          .contains(
              "Lens.of(this::build, (domain, wire) -> new Employee(wire.fullName(),"
                  + " wire.department(), domain.age()))")
          .doesNotContain("parse(")
          .doesNotContain("asIso")
          .doesNotContain("asValidatedPrism");
    }

    @Test
    @DisplayName("a projection field that changes type without a leaf or nested spec is rejected")
    void projectionTypeChangeRejected() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BadgeDto",
              """
              package com.example;

              public record BadgeDto(int name) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BadgeMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BadgeMapping extends MappingSpec<Employee, BadgeDto> {}
              """);

      Compilation compilation = compile(EMPLOYEE, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'BadgeDto.name' has no usable source");
      assertThat(compilation).hadErrorContaining("no matching leaf method was found");
      assertThat(compilation)
          .hadErrorContaining("maps through the validated patch(domain, wire), never asLens()");
    }

    @Test
    @DisplayName("a wire with more components than the domain is rejected")
    void widerWireRejected() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WideDto",
              """
              package com.example;

              public record WideDto(String name, String department, int age, String extra) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WideMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Employee, WideDto> {}
              """);

      Compilation compilation = compile(EMPLOYEE, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'WideDto' has more components than 'Employee'");
      assertThat(compilation).hadErrorContaining("maps as a projection (Lens tier)");
    }
  }

  @Nested
  @DisplayName("Explicit leaf precedence")
  class ExplicitLeafPrecedence {

    @Test
    @DisplayName("a same-typed component still routes through an explicit validating leaf")
    void sameTypedLeafWinsOverIdentity() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              public record Account(String handle) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              public record AccountDto(String handle) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface AccountMapping extends MappingSpec<Account, AccountDto> {
                default ValidatedPrism<String, String> handle() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.startsWith("@")
                              ? Validated.validNel(raw)
                              : Validated.invalidNel(FieldError.of("handles start with @")),
                      raw -> raw);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountMappingImpl");
      Assertions.assertThat(generated)
          .contains(".field(\"handle\", hkj$ifPresent(wire.handle(), handle()::parse))")
          .contains("handle().build(domain.handle())")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("an identity-typed List still routes through an explicit element leaf")
    void identityTypedListRoutesThroughElementLeaf() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Handles",
              """
              package com.example;

              import java.util.List;

              public record Handles(List<String> tags) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.HandlesDto",
              """
              package com.example;

              import java.util.List;

              public record HandlesDto(List<String> tags) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.HandlesMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface HandlesMapping extends MappingSpec<Handles, HandlesDto> {
                default ValidatedPrism<String, String> tags() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.startsWith("@")
                              ? Validated.validNel(raw)
                              : Validated.invalidNel(FieldError.of("handles start with @")),
                      raw -> raw);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.HandlesMappingImpl");
      Assertions.assertThat(generated)
          .contains("tags().buildAll(domain.tags())")
          .contains(".field(\"tags\", hkj$ifPresent(wire.tags(), tags()::parseAll))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.HandlesMappingImpl");
        Object dto =
            result
                .loadClass("com.example.HandlesDto")
                .getDeclaredConstructor(List.class)
                .newInstance(List.of("@ada", "bob"));

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        // The failing element locates by index: "bob" at position 1.
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("tags", "1"), "handles start with @"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an identity-typed Optional still routes through an explicit element leaf")
    void identityTypedOptionalRoutesThroughElementLeaf() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Nickname",
              """
              package com.example;

              import java.util.Optional;

              public record Nickname(Optional<String> alias) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.NicknameDto",
              """
              package com.example;

              import java.util.Optional;

              public record NicknameDto(Optional<String> alias) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.NicknameMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface NicknameMapping extends MappingSpec<Nickname, NicknameDto> {
                default ValidatedPrism<String, String> alias() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.isBlank()
                              ? Validated.invalidNel(FieldError.of("alias must not be blank"))
                              : Validated.validNel(raw),
                      raw -> raw);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.NicknameMappingImpl");
      Assertions.assertThat(generated)
          .contains("domain.alias().map(alias()::build)")
          .contains(
              ".field(\"alias\", hkj$ifPresent(wire.alias(), o -> o.map(v -> alias().parse(v).map(Optional::of))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.NicknameMappingImpl");
        Object dto =
            result
                .loadClass("com.example.NicknameDto")
                .getDeclaredConstructor(java.util.Optional.class)
                .newInstance(java.util.Optional.of("   "));

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("alias"), "alias must not be blank"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an identity-typed Map still routes its values through an explicit value leaf")
    void identityTypedMapRoutesThroughValueLeaf() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Contacts",
              """
              package com.example;

              import java.util.Map;

              public record Contacts(Map<String, String> emails) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ContactsDto",
              """
              package com.example;

              import java.util.Map;

              public record ContactsDto(Map<String, String> emails) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ContactsMapping",
              """
              package com.example;

              import java.util.Locale;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ContactsMapping extends MappingSpec<Contacts, ContactsDto> {
                default ValidatedPrism<String, String> emails() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(raw.trim().toLowerCase(Locale.ROOT)),
                      value -> value);
                }
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ContactsMappingImpl");
      Assertions.assertThat(generated)
          .contains("emails().buildValues(domain.emails())")
          .contains(".field(\"emails\", hkj$ifPresent(wire.emails(), emails()::parseValues))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.ContactsMappingImpl");
        Object dto =
            result
                .loadClass("com.example.ContactsDto")
                .getDeclaredConstructor(Map.class)
                .newInstance(Map.of("work", "  ADA@CORP.EXAMPLE  "));

        // The identity types would have copied verbatim; the leaf normalising proves it ran.
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isValid()).isTrue();
        Assertions.assertThat(invoke(parsed.get(), "emails"))
            .asInstanceOf(InstanceOfAssertFactories.MAP)
            .containsExactly(Assertions.entry("work", "ada@corp.example"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a parameterised default method is not a leaf, and the hint says why")
    void parameterisedDefaultMethodIsNotALeaf() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ParamLeafMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ParamLeafMapping extends MappingSpec<User, UserDto> {
                default ValidatedPrism<String, EmailAddress> email(boolean strict) {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'UserDto.email' has no usable source");
      assertThat(compilation).hadErrorContaining("declares parameters");
      assertThat(compilation).hadErrorContaining("zero-parameter default method");
    }
  }

  @Nested
  @DisplayName("Spec-shape and collision diagnostics")
  class SpecShapeAndCollisions {

    private static JavaFileObject records(String body) {
      return JavaFileObjects.forSourceString(
          "com.example.Records", "package com.example;\n" + body);
    }

    @Test
    @DisplayName(
        "a type-differing primitive component steers to a wrapper type, never to an"
            + " uncompilable ValidatedPrism over a primitive")
    void primitiveComponentSteersToWrapper() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.OrderMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Records.Order, Records.OrderDto> {
                default ValidatedPrism<String, Integer> quantity() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Integer.valueOf(raw)), String::valueOf);
                }
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record Order(int quantity, String note) {}

                    public record OrderDto(String quantity, String note) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("Make 'quantity' a wrapper type");
      Assertions.assertThat(compilation.errors().toString())
          .doesNotContain("ValidatedPrism<java.lang.String, int>");
    }

    @Test
    @DisplayName("a rename colliding with a same-named component is rejected, not a crash")
    void renameCollisionRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CollidingMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CollidingMapping extends MappingSpec<Records.D, Records.W> {
                @MapField(to = "b")
                String a();
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a, String b) {}

                    public record W(String b, String c) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("domain components 'a' and 'b' both map to wire component 'b'");
      assertThat(compilation).hadErrorContaining("Point the rename at a distinct wire component");
    }

    @Test
    @DisplayName("two renames claiming the same wire component are rejected")
    void duplicateRenameTargetsRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DoubleRenameMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface DoubleRenameMapping extends MappingSpec<Records.D, Records.W> {
                @MapField(to = "x")
                String a();

                @MapField(to = "x")
                String b();
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a, String b) {}

                    public record W(String x, String y) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("targets a wire component another rename already claims");
    }

    @Test
    @DisplayName("a projection sharing one domain source across two wire components is rejected")
    void projectionSharedSourceRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SharedSourceMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SharedSourceMapping extends MappingSpec<Records.D, Records.W> {
                @MapField(to = "displayName")
                String name();
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String name, String alias, int age) {}

                    public record W(String name, String displayName) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("domain component 'name' sources more than one wire component");
      assertThat(compilation).hadErrorContaining("would discard one wire value on write-back");
    }

    @Test
    @DisplayName(
        "a record wider than one fields() ladder parses through chunked ladders (a 17th"
            + " component lands in a singleton trailing chunk)")
    void arityBeyondLadderCompilesChunked() {
      String comps =
          IntStream.rangeClosed(1, 17)
              .mapToObj(i -> "String f" + i)
              .collect(Collectors.joining(", "));
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WideMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Records.D, Records.W> {}
              """);
      Compilation compilation =
          compile(
              records(
                  "public final class Records {\n  public record D("
                      + comps
                      + ") {}\n\n  public record W("
                      + comps
                      + ") {}\n}\n"),
              spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.WideMappingImpl");
      Assertions.assertThat(generated)
          .contains("Tuple16::new")
          .contains(".apply(v -> v)")
          .contains("NonEmptyList.semigroup()")
          .contains(".field(\"f17\"");
    }

    @Test
    @DisplayName("a 16-component record parses in one fields() ladder (no chunking)")
    void sixteenComponentsParseInOneLadder() {
      String comps =
          IntStream.rangeClosed(1, 16)
              .mapToObj(i -> "String f" + i)
              .collect(Collectors.joining(", "));
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WideMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WideMapping extends MappingSpec<Records.D, Records.W> {}
              """);
      Compilation compilation =
          compile(
              records(
                  "public final class Records {\n  public record D("
                      + comps
                      + ") {}\n\n  public record W("
                      + comps
                      + ") {}\n}\n"),
              spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.WideMappingImpl"))
          .contains(".apply(Records.D::new)")
          .doesNotContain("Tuple16::new");
    }

    @Test
    @DisplayName(
        "a concrete instantiation of a generic record is accepted, even with the type"
            + " parameter unused")
    void concretelyInstantiatedGenericRecordAccepted() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoxMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BoxMapping extends MappingSpec<Records.Box<String>, Records.BoxDto> {}
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record Box<T>(String name, int count) {}

                    public record BoxDto(String name, int count) {}
                  }
                  """),
              spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BoxMappingImpl"))
          .contains("public Records.BoxDto build(Records.Box<String> domain)")
          // a lossless instantiation keeps the Iso tier (the issue's tiers-unchanged claim)
          .contains("public Iso<Records.Box<String>, Records.BoxDto> asIso()");
    }

    @Test
    @DisplayName("an abstract method that is neither a rename, a leaf nor a bridge is rejected")
    void abstractHelperRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.HelperMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HelperMapping extends MappingSpec<Records.D, Records.W> {
                String helper(int x);
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a) {}

                    public record W(String a) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("abstract method 'helper' is neither a rename, a leaf, nor a bridge");
    }

    @Test
    @DisplayName("a @MapField method with a body is rejected")
    void mapFieldOnDefaultMethodRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DefaultRenameMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface DefaultRenameMapping extends MappingSpec<Records.D, Records.W> {
                @MapField(to = "b")
                default String a() {
                  return "not a rename";
                }
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a) {}

                    public record W(String b) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@MapField method 'a' must be abstract");
      assertThat(compilation).hadErrorContaining("Remove the body, or remove the @MapField");
    }

    @Test
    @DisplayName("a @MapField method with parameters is rejected")
    void mapFieldWithParametersRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ParamMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ParamMapping extends MappingSpec<Records.D, Records.W> {
                @MapField(to = "b")
                String a(int ignored);
              }
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record D(String a) {}

                    public record W(String b) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@MapField method 'a' must not declare parameters");
    }

    @Test
    @DisplayName("a near-miss leaf (swapped type arguments) is named in the diagnostic")
    void nearMissLeafNamed() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SwappedMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface SwappedMapping extends MappingSpec<Records.D, Records.W> {
                default ValidatedPrism<EmailAddress, String> email() {
                  return ValidatedPrism.of(
                      e -> Validated.validNel(e.value()), raw -> new EmailAddress(raw));
                }
              }
              """);
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}
                  }
                  """),
              spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("A default method 'email()' exists but returns");
      assertThat(compilation).hadErrorContaining("wire first, domain second");
    }

    @Test
    @DisplayName("nesting through a projection spec names the spec and why it cannot nest")
    void projectionSpecNamedInNestingFailure() {
      JavaFileObject projectionSpec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping
                  extends MappingSpec<Records.Customer, Records.CustomerDto> {}
              """);
      JavaFileObject nestingSpec =
          JavaFileObjects.forSourceString(
              "com.example.OrderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Records.Order, Records.OrderDto> {}
              """);
      Compilation compilation =
          compile(
              records(
                  """
                  public final class Records {
                    public record Customer(String name, int age) {}

                    public record CustomerDto(String name) {}

                    public record Order(Customer customer) {}

                    public record OrderDto(CustomerDto customer) {}
                  }
                  """),
              projectionSpec,
              nestingSpec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'CustomerMapping' maps this pair but is a projection (no parse), so it cannot be"
                  + " nested");
    }

    @Test
    @DisplayName("two specs colliding on one generated Impl name get a rename fix")
    void filerCollisionNamed() {
      JavaFileObject nested =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class Outer {
                public record D(String a) {}

                public record W(String a) {}

                @GenerateMapping
                public interface InnerMapping extends MappingSpec<D, W> {}
              }
              """);
      JavaFileObject topLevel =
          JavaFileObjects.forSourceString(
              "com.example.OuterInnerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OuterInnerMapping extends MappingSpec<Outer.D, Outer.W> {}
              """);
      Compilation compilation = compile(nested, topLevel);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("the class already exists");
      assertThat(compilation).hadErrorContaining("Rename one of the colliding specs");
    }

    @Test
    @DisplayName("an interface not directly extending MappingSpec is rejected")
    void indirectMappingSpecRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DetachedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;

              @GenerateMapping
              public interface DetachedMapping {}
              """);
      Compilation compilation = compile(spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'DetachedMapping' does not directly extend MappingSpec");
    }
  }

  @Nested
  @DisplayName("Classification edge cases")
  class ClassificationEdgeCases {

    private static JavaFileObject records(String body) {
      return JavaFileObjects.forSourceString(
          "com.example.Records", "package com.example;\n" + body);
    }

    private static JavaFileObject spec(String name, String body) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MapField;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          """
              + body);
    }

    private static final JavaFileObject PLAIN =
        records(
            """
            public final class Records {
              public record D(String a) {}

              public record W(String a) {}
            }
            """);

    @Test
    @DisplayName("a mix-in that is itself a mapping spec is rejected")
    void specExtendingSpecRejected() {
      JavaFileObject base =
          JavaFileObjects.forSourceString(
              "com.example.BaseMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MappingSpec;

              public interface BaseMapping extends MappingSpec<Records.D, Records.W> {}
              """);
      JavaFileObject inheriting =
          JavaFileObjects.forSourceString(
              "com.example.InheritingMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface InheritingMapping
                  extends BaseMapping, MappingSpec<Records.D, Records.W> {}
              """);
      Compilation compilation = compile(PLAIN, base, inheriting);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("mix-in 'BaseMapping' is itself a mapping spec");
      assertThat(compilation)
          .hadErrorContaining("Move the shared renames and leaves onto a plain interface");
    }

    @Test
    @DisplayName("a raw MappingSpec supertype is rejected")
    void rawMappingSpecRejected() {
      Compilation compilation =
          compile(PLAIN, spec("RawMapping", "public interface RawMapping extends MappingSpec {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not directly extend MappingSpec");
    }

    @Test
    @DisplayName("a spec whose only supertype is unrelated is rejected")
    void unrelatedSupertypeRejected() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "SerialMapping",
                  "public interface SerialMapping extends java.io.Serializable {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not directly extend MappingSpec");
    }

    @Test
    @DisplayName("a record domain with a wire that is neither record nor usable bean is rejected")
    void recordWithNonRecordWireRejected() {
      // String is a concrete class (a candidate bean) but exposes no getter/setter property pair.
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "HalfMapping",
                  "public interface HalfMapping extends MappingSpec<Records.D, String> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'String' is not a usable bean-shaped wire");
      assertThat(compilation).hadErrorContaining("no construction strategy fits it");
    }

    @Test
    @DisplayName("a sealed domain with a record wire is rejected")
    void sealedWithRecordWireRejected() {
      JavaFileObject sealedRecords =
          records(
              """
              public final class Records {
                public sealed interface Pay permits PayA {}

                public record PayA(String v) implements Pay {}

                public record D(String a) {}
              }
              """);
      Compilation compilation =
          compile(
              sealedRecords,
              spec(
                  "MixMapping",
                  "public interface MixMapping extends MappingSpec<Records.Pay, Records.D> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must both be records, or both sealed interfaces");
    }

    @Test
    @DisplayName("an array type argument is rejected")
    void arrayArgumentRejected() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "ArrayMapping",
                  "public interface ArrayMapping extends MappingSpec<int[], Records.D> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must both be records, or both sealed interfaces");
    }

    @Test
    @DisplayName("a plain (non-sealed) interface type argument is rejected")
    void plainInterfaceArgumentRejected() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "RunnableMapping",
                  "public interface RunnableMapping extends MappingSpec<Runnable, Records.D> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must both be records, or both sealed interfaces");
    }

    @Test
    @DisplayName(
        "a generic spec over concrete records is accepted: the unused variable threads"
            + " through harmlessly")
    void genericSpecOverConcreteRecordsAccepted() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "GenMapping",
                  "public interface GenMapping<T> extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.GenMappingImpl"))
          .contains("public final class GenMappingImpl<T> implements GenMapping<T>")
          .contains("public static <T> GenMappingImpl<T> instance()");
    }

    @Test
    @DisplayName("a concretely instantiated generic wire record is accepted")
    void concretelyInstantiatedGenericWireAccepted() {
      JavaFileObject genericWire =
          records(
              """
              public final class Records {
                public record D(String a) {}

                public record WG<T>(String a) {}
              }
              """);
      Compilation compilation =
          compile(
              genericWire,
              spec(
                  "GenWireMapping",
                  "public interface GenWireMapping extends MappingSpec<Records.D,"
                      + " Records.WG<String>> {}"));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.GenWireMappingImpl"))
          .contains("public Records.WG<String> build(Records.D domain)");
    }

    @Test
    @DisplayName("a generic sealed pair is rejected")
    void genericSealedRejected() {
      JavaFileObject genericSealed =
          records(
              """
              public final class Records {
                public sealed interface GS<T> permits GA {}

                public record GA(String v) implements GS<String> {}

                public sealed interface GW<T> permits GB {}

                public record GB(String v) implements GW<String> {}
              }
              """);
      Compilation compilation =
          compile(
              genericSealed,
              spec(
                  "GenSealedMapping",
                  "public interface GenSealedMapping extends MappingSpec<Records.GS<String>,"
                      + " Records.GW<String>> {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'GS' is generic, which this mapper does not support");
    }

    @Test
    @DisplayName("a domain component with no wire counterpart is rejected")
    void missingWireCounterpartRejected() {
      JavaFileObject mismatched =
          records(
              """
              public final class Records {
                public record D(String a) {}

                public record W(String b) {}
              }
              """);
      Compilation compilation =
          compile(
              mismatched,
              spec(
                  "CounterpartMapping",
                  "public interface CounterpartMapping extends MappingSpec<Records.D, Records.W>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("domain field 'D.a' has no wire counterpart named 'a'");
      assertThat(compilation).hadErrorContaining("add a '@MapField(to = ...)' rename");
    }

    @Test
    @DisplayName("a projection wire component with no domain source is rejected")
    void projectionMissingSourceRejected() {
      JavaFileObject projection =
          records(
              """
              public final class Records {
                public record D(String a, String b, String c) {}

                public record W(String x) {}
              }
              """);
      Compilation compilation =
          compile(
              projection,
              spec(
                  "LostMapping",
                  "public interface LostMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("projection field 'W.x' has no domain source");
    }

    @Test
    @DisplayName("a wire List with a non-List domain component falls through to the source error")
    void oneSidedListFallsThrough() {
      JavaFileObject oneSided =
          records(
              """
              public final class Records {
                public record D(String xs) {}

                public record W(java.util.List<String> xs) {}
              }
              """);
      Compilation compilation =
          compile(
              oneSided,
              spec(
                  "OneListMapping",
                  "public interface OneListMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.xs' has no usable source");
    }

    @Test
    @DisplayName("a wire Optional with a non-Optional domain component falls through")
    void oneSidedOptionalFallsThrough() {
      JavaFileObject oneSided =
          records(
              """
              public final class Records {
                public record D(String o) {}

                public record W(java.util.Optional<String> o) {}
              }
              """);
      Compilation compilation =
          compile(
              oneSided,
              spec(
                  "OneOptionalMapping",
                  "public interface OneOptionalMapping extends MappingSpec<Records.D, Records.W>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.o' has no usable source");
    }

    @Test
    @DisplayName("a wire Map with a non-Map domain component falls through")
    void oneSidedMapFallsThrough() {
      JavaFileObject oneSided =
          records(
              """
              public final class Records {
                public record D(String m) {}

                public record W(java.util.Map<String, String> m) {}
              }
              """);
      Compilation compilation =
          compile(
              oneSided,
              spec(
                  "OneMapMapping",
                  "public interface OneMapMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.m' has no usable source");
    }

    @Test
    @DisplayName("a primitive component pair with differing types reports the source error")
    void primitiveComponentFallsThrough() {
      JavaFileObject primitive =
          records(
              """
              public final class Records {
                public record D(int n) {}

                public record W(String n) {}
              }
              """);
      Compilation compilation =
          compile(
              primitive,
              spec(
                  "PrimitiveMapping",
                  "public interface PrimitiveMapping extends MappingSpec<Records.D, Records.W>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.n' has no usable source");
    }

    @Test
    @DisplayName("a raw wire List cannot lift and falls through")
    void rawListFallsThrough() {
      JavaFileObject raw =
          records(
              """
              @SuppressWarnings("rawtypes")
              public final class Records {
                public record D(java.util.List<String> xs) {}

                public record W(java.util.List xs) {}
              }
              """);
      Compilation compilation =
          compile(
              raw,
              spec(
                  "RawListMapping",
                  "public interface RawListMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.xs' has no usable source");
    }

    @Test
    @DisplayName("List components whose elements resolve to nothing fall through")
    void unresolvableListElementsFallThrough() {
      JavaFileObject lists =
          records(
              """
              public final class Records {
                public record D(java.util.List<Integer> xs) {}

                public record W(java.util.List<String> xs) {}
              }
              """);
      Compilation compilation =
          compile(
              lists,
              spec(
                  "IntListMapping",
                  "public interface IntListMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.xs' has no usable source");
    }

    @Test
    @DisplayName("Optional components whose elements resolve to nothing fall through")
    void unresolvableOptionalElementsFallThrough() {
      JavaFileObject optionals =
          records(
              """
              public final class Records {
                public record D(java.util.Optional<Integer> o) {}

                public record W(java.util.Optional<String> o) {}
              }
              """);
      Compilation compilation =
          compile(
              optionals,
              spec(
                  "IntOptionalMapping",
                  "public interface IntOptionalMapping extends MappingSpec<Records.D, Records.W>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.o' has no usable source");
    }

    private static final JavaFileObject ELEMENT_RECORDS =
        records(
            """
            public final class Records {
              public record EmD(String v) {}

              public record EmW(String v) {}

              public record OtherW(String v) {}

              public record D(java.util.List<EmD> xs) {}

              public record W(java.util.List<EmW> xs) {}

              public record OD(java.util.Optional<EmD> o) {}

              public record OW(java.util.Optional<EmW> o) {}

              public record MD(java.util.Map<String, EmD> m) {}

              public record MW(java.util.Map<String, EmW> m) {}

              public record AD(EmD[] xs) {}

              public record AW(EmW[] xs) {}

              public record UnmappedAD(EmD[] xs) {}

              public record UnmappedAW(String[] xs) {}
            }
            """);

    @Test
    @DisplayName("ambiguous element specs inside a List are rejected")
    void ambiguousListElementSpecsRejected() {
      Compilation compilation =
          compile(
              ELEMENT_RECORDS,
              spec(
                  "EmMappingA",
                  "public interface EmMappingA extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "EmMappingB",
                  "public interface EmMappingB extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "EmMappingOther",
                  "public interface EmMappingOther extends MappingSpec<Records.EmD, Records.OtherW>"
                      + " {}"),
              spec(
                  "ListNestMapping",
                  "public interface ListNestMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'xs' matches more than one mapping spec");
    }

    @Test
    @DisplayName("ambiguous element specs inside an array are rejected")
    void ambiguousArrayElementSpecsRejected() {
      Compilation compilation =
          compile(
              ELEMENT_RECORDS,
              spec(
                  "EmMappingA",
                  "public interface EmMappingA extends MappingSpec<Records.EmD, Records.EmW>"
                      + " {}"),
              spec(
                  "EmMappingB",
                  "public interface EmMappingB extends MappingSpec<Records.EmD, Records.EmW>"
                      + " {}"),
              spec(
                  "ArrayNestMapping",
                  "public interface ArrayNestMapping extends MappingSpec<Records.AD,"
                      + " Records.AW> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'xs' matches more than one mapping spec");
    }

    @Test
    @DisplayName("array elements that resolve to nothing fall through")
    void unresolvableArrayElementsFallThrough() {
      Compilation compilation =
          compile(
              ELEMENT_RECORDS,
              spec(
                  "UnmappedArrayMapping",
                  "public interface UnmappedArrayMapping extends"
                      + " MappingSpec<Records.UnmappedAD, Records.UnmappedAW> {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'UnmappedAW.xs' has no usable source");
    }

    @Test
    @DisplayName("an array against a List, and an array of primitives, are plain mismatches")
    void arrayShapeMismatchesFallThrough() {
      JavaFileObject shapes =
          records(
              """
              public final class Records {
                public record ListD(java.util.List<String> xs) {}

                public record ArrayW(String[] xs) {}

                public record PrimitiveD(int[] xs) {}

                public record ReferenceW(String[] xs) {}
              }
              """);
      // an array wire against a List domain: not a pair, so it never reaches array lifting
      Compilation againstList =
          compile(
              shapes,
              spec(
                  "ArrayVsListMapping",
                  "public interface ArrayVsListMapping extends MappingSpec<Records.ListD,"
                      + " Records.ArrayW> {}"));
      assertThat(againstList).failed();
      assertThat(againstList).hadErrorContaining("has no usable source");

      // a primitive DOMAIN element: a ValidatedPrism cannot focus it, so nothing lifts
      Compilation primitiveDomain =
          compile(
              shapes,
              spec(
                  "PrimitiveArrayMapping",
                  "public interface PrimitiveArrayMapping extends"
                      + " MappingSpec<Records.PrimitiveD, Records.ReferenceW> {}"));
      assertThat(primitiveDomain).failed();
      assertThat(primitiveDomain).hadErrorContaining("has no usable source");
    }

    @Test
    @DisplayName("Map components whose values resolve to nothing fall through")
    void unresolvableMapValuesFallThrough() {
      JavaFileObject maps =
          records(
              """
              public final class Records {
                public record D(java.util.Map<String, Integer> m) {}

                public record W(java.util.Map<String, String> m) {}
              }
              """);
      Compilation compilation =
          compile(
              maps,
              spec(
                  "IntMapMapping",
                  "public interface IntMapMapping extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.m' has no usable source");
    }

    @Test
    @DisplayName("ambiguous value specs inside a Map are rejected")
    void ambiguousMapValueSpecsRejected() {
      Compilation compilation =
          compile(
              ELEMENT_RECORDS,
              spec(
                  "EmMappingA",
                  "public interface EmMappingA extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "EmMappingB",
                  "public interface EmMappingB extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "MapNestMapping",
                  "public interface MapNestMapping extends MappingSpec<Records.MD, Records.MW>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'm' matches more than one mapping spec");
    }

    @Test
    @DisplayName("ambiguous element specs inside an Optional are rejected")
    void ambiguousOptionalElementSpecsRejected() {
      Compilation compilation =
          compile(
              ELEMENT_RECORDS,
              spec(
                  "EmMappingA",
                  "public interface EmMappingA extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "EmMappingB",
                  "public interface EmMappingB extends MappingSpec<Records.EmD, Records.EmW> {}"),
              spec(
                  "OptionalNestMapping",
                  "public interface OptionalNestMapping extends MappingSpec<Records.OD, Records.OW>"
                      + " {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("field 'o' matches more than one mapping spec");
    }

    @Test
    @DisplayName("a leaf-shaped method returning a primitive is not a leaf")
    void leafReturningPrimitiveIgnored() {
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}
                  }
                  """),
              spec(
                  "IntLeafMapping",
                  """
                  public interface IntLeafMapping extends MappingSpec<Records.D, Records.W> {
                    default int email() {
                      return 42;
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.email' has no usable source");
      assertThat(compilation).hadErrorContaining("A default method 'email()' exists but returns");
    }

    @Test
    @DisplayName("a leaf-shaped method returning a non-prism type is not a leaf")
    void leafReturningNonPrismIgnored() {
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}
                  }
                  """),
              spec(
                  "StringLeafMapping",
                  """
                  public interface StringLeafMapping extends MappingSpec<Records.D, Records.W> {
                    default String email() {
                      return "not a prism";
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.email' has no usable source");
    }

    @Test
    @DisplayName("a leaf-shaped method returning a raw ValidatedPrism is not a leaf")
    void leafReturningRawPrismIgnored() {
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}
                  }
                  """),
              spec(
                  "RawLeafMapping",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface RawLeafMapping extends MappingSpec<Records.D, Records.W> {
                    default ValidatedPrism email() {
                      return null;
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.email' has no usable source");
    }

    @Test
    @DisplayName("a leaf matching the wire side but not the domain side is not a leaf")
    void leafWithWrongDomainArgumentIgnored() {
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}
                  }
                  """),
              spec(
                  "HalfLeafMapping",
                  """
                  public interface HalfLeafMapping extends MappingSpec<Records.D, Records.W> {
                    default ValidatedPrism<String, String> email() {
                      return ValidatedPrism.of(Validated::validNel, v -> v);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.email' has no usable source");
    }

    @Test
    @DisplayName("hints skip abstract renames and unrelated methods, and non-matching projections")
    void hintsSkipNonCandidates() {
      Compilation compilation =
          compile(
              EMAIL,
              records(
                  """
                  public final class Records {
                    public record D(EmailAddress email) {}

                    public record W(String email) {}

                    public record EmptyW() {}

                    public record OtherD(String x) {}
                  }
                  """),
              spec(
                  "ProjectionA",
                  "public interface ProjectionA extends MappingSpec<EmailAddress, Records.EmptyW>"
                      + " {}"),
              spec(
                  "ProjectionB",
                  "public interface ProjectionB extends MappingSpec<Records.OtherD, Records.EmptyW>"
                      + " {}"),
              spec(
                  "HintsMapping",
                  """
                  public interface HintsMapping extends MappingSpec<Records.D, Records.W> {
                    @MapField(to = "email")
                    String email();

                    default String unrelatedHelper() {
                      return "not a leaf";
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.email' has no usable source");
    }
  }

  @Nested
  @DisplayName("Sealed projection hint")
  class SealedProjectionHint {

    @Test
    @DisplayName("a projection spec for a subtype is named in the dispatch failure")
    void projectionSpecNamedInDispatchFailure() {
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public sealed interface Pay permits Card, Bank {}

                public record Card(String number) implements Pay {}

                public record Bank(String iban, String bic) implements Pay {}

                public sealed interface PayDto permits CardDto, BankDto {}

                public record CardDto(String number) implements PayDto {}

                public record BankDto(String iban) implements PayDto {}
              }
              """);
      JavaFileObject cardSpec =
          JavaFileObjects.forSourceString(
              "com.example.CardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CardMapping extends MappingSpec<Records.Card, Records.CardDto> {}
              """);
      JavaFileObject bankProjection =
          JavaFileObjects.forSourceString(
              "com.example.BankMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BankMapping extends MappingSpec<Records.Bank, Records.BankDto> {}
              """);
      JavaFileObject paySpec =
          JavaFileObjects.forSourceString(
              "com.example.PayMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PayMapping extends MappingSpec<Records.Pay, Records.PayDto> {}
              """);

      Compilation compilation = compile(records, cardSpec, bankProjection, paySpec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("permitted subtype 'com.example.Records.Bank'");
      assertThat(compilation)
          .hadErrorContaining(
              "'BankMapping' maps it but is a projection (no parse), so it cannot take part in"
                  + " dispatch");
    }
  }

  @Nested
  @DisplayName("Filer fallback")
  class FilerFallback {

    @Test
    @DisplayName("a non-collision IOException reports the what/why/fix write failure")
    void ioExceptionReportsWriteFailure() {
      List<String> messages = new ArrayList<>();
      Messager messager =
          (Messager)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {Messager.class},
                  (proxy, method, args) -> {
                    if ("printMessage".equals(method.getName())) {
                      messages.add(String.valueOf(args[1]));
                    }
                    return null;
                  });
      Filer filer =
          (Filer)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {Filer.class},
                  (proxy, method, args) -> {
                    throw new IOException("disk full");
                  });
      ProcessingEnvironment env =
          (ProcessingEnvironment)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {ProcessingEnvironment.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "getFiler" -> filer;
                        case "getMessager" -> messager;
                        default -> null;
                      });
      Name simpleName =
          (Name)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {Name.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "toString" -> "BrokenMapping";
                        case "length" -> "BrokenMapping".length();
                        default -> null;
                      });
      TypeElement spec =
          (TypeElement)
              Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {TypeElement.class},
                  (proxy, method, args) ->
                      "getSimpleName".equals(method.getName()) ? simpleName : null);

      MappingProcessor processor = new MappingProcessor();
      processor.init(env);
      processor.writeFile(spec, "com.example", TypeSpec.classBuilder("Broken").build());

      Assertions.assertThat(messages)
          .singleElement()
          .asString()
          .contains("could not write the generated mapping for 'BrokenMapping'")
          .contains("disk full")
          .contains("Check build-output permissions and free disk space");
    }
  }

  @Nested
  @DisplayName("What/why/fix diagnostics")
  class Diagnostics {

    @Test
    @DisplayName("rejects non-interface placement")
    void rejectsNonInterface() {
      JavaFileObject bad =
          JavaFileObjects.forSourceString(
              "com.example.Bad",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;

              @GenerateMapping
              public class Bad {}
              """);

      Compilation compilation = compile(bad);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@GenerateMapping: can only be applied to interfaces");
    }

    @Test
    @DisplayName("reports a missing leaf with the found components and the exact fix")
    void reportsMissingLeaf() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserMapping extends MappingSpec<User, UserDto> {}
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'UserDto.email' has no usable source");
      assertThat(compilation)
          .hadErrorContaining(
              "Add 'default ValidatedPrism<java.lang.String, com.example.EmailAddress> email()'");
      assertThat(compilation)
          .hadErrorContaining("or declare a @GenerateMapping spec mapping those records");
    }

    @Test
    @DisplayName("rejects @MapField whose 'to' names no wire component")
    void rejectsUnknownRenameTarget() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserMapping extends MappingSpec<User, UserDto> {
                @MapField(to = "nope")
                String name();
              }
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("names no component of UserDto");
      assertThat(compilation).hadErrorContaining("Point 'to' at an existing wire component");
    }

    @Test
    @DisplayName("rejects @MapField whose method names no domain component")
    void rejectsUnknownRenameSource() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserMapping extends MappingSpec<User, UserDto> {
                @MapField(to = "name")
                String nickname();
              }
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not name a component of User");
    }

    @Test
    @DisplayName("rejects a bean-shaped domain type argument")
    void rejectsNonRecordArguments() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BadMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BadMapping extends MappingSpec<String, UserDto> {}
              """);

      Compilation compilation = compile(WIRE, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "is a bean-shaped class, which this mapper does not support on the domain side");
      assertThat(compilation).hadErrorContaining("the domain must be a record");
    }
  }

  @Nested
  @DisplayName("Generated-member collision sweep")
  class GeneratedMemberCollisionSweep {

    // Full-tier and projection-tier collisions live here; the sealed-tier test sits with its
    // fixtures in SealedDispatch, the bean 'ifPresent' tests in MappingProcessorBeanTest, and
    // the sparse-update tests in MappingProcessorUpdateTest.

    /** The full-tier spec (leaf on email, so not lossless) with one extra member spliced in. */
    private JavaFileObject fullSpecWith(String extraMember) {
      return JavaFileObjects.forSourceString(
          "com.example.UserMapping",
          """
          package com.example;

          import java.util.function.Function;
          import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.Iso;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          public interface UserMapping extends MappingSpec<User, UserDto> {
            default ValidatedPrism<String, EmailAddress> email() {
              return ValidatedPrism.of(
                  raw ->
                      raw.contains("@")
                          ? Validated.validNel(new EmailAddress(raw))
                          : Validated.invalidNel(FieldError.of("not an email address")),
                  EmailAddress::value);
            }

          %s
          }
          """
              .formatted(extraMember));
    }

    @Test
    @DisplayName("a default with the exact generated 'parse' signature is rejected, not overridden")
    void exactParseSignatureIsRejected() {
      JavaFileObject colliding =
          fullSpecWith(
              """
                default Validated<NonEmptyList<FieldError>, User> parse(UserDto wire) {
                  return Validated.invalidNel(FieldError.of("never runs"));
                }
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, colliding);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'parse(UserDto)' collides with the 'parse' member the generated UserMappingImpl"
                  + " emits")
          .inFile(colliding);
      assertThat(compilation).hadErrorContaining("a full mapping");
      assertThat(compilation).hadErrorContaining("silently overridden");
      assertThat(compilation)
          .hadErrorContaining("Rename the method, or remove it and rely on the generated 'parse'");
    }

    @Test
    @DisplayName(
        "a default with the same signature but another return type gets the same"
            + " diagnostic, not a raw javac error in generated code")
    void incompatibleReturnTypeGetsTheDiagnosticNotARawError() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith("  default String parse(UserDto wire) { return \"\"; }"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'parse(UserDto)' collides");
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("'build' and 'asValidatedPrism' are swept too")
    void buildAndAsValidatedPrismAreSwept() {
      Compilation buildCollision =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith("  default UserDto build(User domain) { return null; }"));
      assertThat(buildCollision).failed();
      assertThat(buildCollision)
          .hadErrorContaining("'build(User)' collides with the 'build' member");

      Compilation prismCollision =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith(
                  "  default ValidatedPrism<UserDto, User> asValidatedPrism() { return null; }"));
      assertThat(prismCollision).failed();
      assertThat(prismCollision)
          .hadErrorContaining("'asValidatedPrism()' collides with the 'asValidatedPrism' member");
    }

    @Test
    @DisplayName("a bounded generic default whose erasure matches the member is a collision too")
    void boundedGenericCollisionIsRejected() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith("  default <T extends User> UserDto build(T value) { return null; }"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'build(T)' collides with the 'build' member");
    }

    @Test
    @DisplayName("'asIso' is reserved only when the mapping is lossless (tier-aware sets)")
    void asIsoReservedOnlyWhenLossless() {
      JavaFileObject point =
          JavaFileObjects.forSourceString(
              "com.example.Point",
              """
              package com.example;

              public record Point(int x, int y) {}
              """);
      JavaFileObject pointDto =
          JavaFileObjects.forSourceString(
              "com.example.PointDto",
              """
              package com.example;

              public record PointDto(int x, int y) {}
              """);
      JavaFileObject losslessSpec =
          JavaFileObjects.forSourceString(
              "com.example.PointMapping",
              """
              package com.example;

              import org.higherkindedj.optics.Iso;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PointMapping extends MappingSpec<Point, PointDto> {
                default Iso<Point, PointDto> asIso() { return null; }
              }
              """);
      Compilation losslessCollision = compile(point, pointDto, losslessSpec);
      assertThat(losslessCollision).failed();
      assertThat(losslessCollision).hadErrorContaining("'asIso()' collides");

      // The leaf makes the User mapping fallible, so no asIso is emitted and the helper is legal.
      Compilation fallibleCompilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith("  default Iso<User, UserDto> asIso() { return null; }"));
      assertThat(fallibleCompilation).succeeded();
    }

    @Test
    @DisplayName("overloads with a different erased signature or arity stay legal")
    void overloadsStayLegal() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith(
                  """
                    default Validated<NonEmptyList<FieldError>, User> parse(String raw) {
                      return Validated.invalidNel(FieldError.of(raw));
                    }

                    default UserDto build() {
                      return null;
                    }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("static and private spec methods are not inherited by the Impl, so they pass")
    void staticAndPrivateMethodsPass() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith(
                  """
                    static UserDto build(User domain) {
                      return null;
                    }

                    private Validated<NonEmptyList<FieldError>, User> parse(UserDto wire) {
                      return Validated.invalidNel(FieldError.of("a private helper"));
                    }
                  """));

      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("'patch' is not reserved on a full mapping: the sets are per tier")
    void patchHelperOnFullMappingStaysLegal() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith(
                  """
                    default Validated<NonEmptyList<FieldError>, User> patch(User domain, UserDto wire) {
                      return Validated.invalidNel(FieldError.of("a helper, not a collision"));
                    }
                  """));

      assertThat(compilation).succeeded();
    }

    /** The leaf-carrying projection spec with one extra member spliced in. */
    private JavaFileObject patchSpecWith(String extraMember) {
      return JavaFileObjects.forSourceString(
          "com.example.AccountPatchMapping",
          """
          package com.example;

          import java.util.function.Function;
          import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          public interface AccountPatchMapping extends MappingSpec<Account, AccountPatchDto> {
            default ValidatedPrism<String, String> email() {
              return ValidatedPrism.of(
                  raw ->
                      raw.contains("@")
                          ? Validated.validNel(raw)
                          : Validated.invalidNel(FieldError.of("not an email address")),
                  email -> email);
            }

          %s
          }
          """
              .formatted(extraMember));
    }

    private static final JavaFileObject ACCOUNT =
        JavaFileObjects.forSourceString(
            "com.example.Account",
            """
            package com.example;

            public record Account(String id, String email, String notes, int age) {}
            """);

    private static final JavaFileObject ACCOUNT_PATCH_DTO =
        JavaFileObjects.forSourceString(
            "com.example.AccountPatchDto",
            """
            package com.example;

            public record AccountPatchDto(String email, String notes, int age) {}
            """);

    @Test
    @DisplayName("a default with the generated 'patch' signature is rejected on the patch tier")
    void patchCollisionOnLeafCarryingProjectionIsRejected() {
      Compilation compilation =
          compile(
              ACCOUNT,
              ACCOUNT_PATCH_DTO,
              patchSpecWith(
                  """
                    default Validated<NonEmptyList<FieldError>, Account> patch(
                        Account domain, AccountPatchDto wire) {
                      return Validated.invalidNel(FieldError.of("never runs"));
                    }
                  """));

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'patch(Account, AccountPatchDto)' collides with the 'patch' member the generated"
                  + " AccountPatchMappingImpl emits");
      assertThat(compilation).hadErrorContaining("emits for this tier (a validating projection)");
    }

    @Test
    @DisplayName(
        "user 'ifPresent' helpers neither collide with nor capture the $-namespaced"
            + " guard: every shape is legal and the guard still runs")
    void ifPresentHelpersNeitherCollideNorCaptureTheGuard() {
      // The generic helper matches the guard's own shape; the String one would be MORE specific
      // than the guard for every String read, so it would capture an unqualified 'ifPresent'
      // call. Both booby-trap their bodies: if generated code ever routed through them, patch
      // would go invalid below.
      Compilation compilation =
          compile(
              ACCOUNT,
              ACCOUNT_PATCH_DTO,
              patchSpecWith(
                  """
                    default <S, A> Validated<NonEmptyList<FieldError>, A> ifPresent(
                        S value, Function<? super S, Validated<NonEmptyList<FieldError>, A>> parse) {
                      return Validated.invalidNel(FieldError.of("captured by the generic helper"));
                    }

                    default Validated<NonEmptyList<FieldError>, String> ifPresent(
                        String value,
                        Function<? super String, Validated<NonEmptyList<FieldError>, String>> parse) {
                      return Validated.invalidNel(FieldError.of("captured by the String helper"));
                    }

                    default boolean ifPresent(String value) {
                      return value != null;
                    }
                  """));
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Object domain =
            result
                .loadClass("com.example.Account")
                .getDeclaredConstructor(String.class, String.class, String.class, int.class)
                .newInstance("7", "ada@example.com", "notes", 30);
        Object wire =
            result
                .loadClass("com.example.AccountPatchDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("grace@example.com", "updated", 31);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "patch", domain, wire);
        Assertions.assertThat(patched.isValid()).isTrue();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "a default with the generated 'asLens' signature is rejected on a lossy" + " projection")
    void asLensCollisionOnLossyProjectionIsRejected() {
      JavaFileObject person =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;

              public record Person(String name, String town, int age) {}
              """);
      JavaFileObject personDto =
          JavaFileObjects.forSourceString(
              "com.example.PersonDto",
              """
              package com.example;

              public record PersonDto(String town, int age) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PersonProjection",
              """
              package com.example;

              import org.higherkindedj.optics.Lens;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PersonProjection extends MappingSpec<Person, PersonDto> {
                default Lens<Person, PersonDto> asLens() { return null; }
              }
              """);

      Compilation compilation = compile(person, personDto, spec);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'asLens()' collides with the 'asLens' member the generated PersonProjectionImpl"
                  + " emits");
      assertThat(compilation).hadErrorContaining("a lossy projection");
    }

    @Test
    @DisplayName("a @MapField rename named after a zero-parameter generated member is rejected")
    void mapFieldRenameNamedAfterAZeroParamMemberIsRejected() {
      JavaFileObject doc =
          JavaFileObjects.forSourceString(
              "com.example.Doc",
              """
              package com.example;

              public record Doc(String asLens, String title, int pages) {}
              """);
      JavaFileObject docDto =
          JavaFileObjects.forSourceString(
              "com.example.DocDto",
              """
              package com.example;

              public record DocDto(String lensField, int pages) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DocProjection",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface DocProjection extends MappingSpec<Doc, DocDto> {
                @MapField(to = "lensField")
                String asLens();
              }
              """);

      Compilation compilation = compile(doc, docDto, spec);

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'asLens()' collides with the 'asLens' member");
      assertThat(compilation).hadErrorContaining("a lossy projection");
    }

    @Test
    @DisplayName(
        "an unresolved parameter type is never a collision, so the real cannot-find-symbol"
            + " error is not shadowed")
    void unresolvedParameterTypesAreNotCollisions() {
      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              fullSpecWith("  default UserDto build(Missing missing) { return null; }"));

      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      Assertions.assertThat(compilation.errors())
          .noneMatch(diagnostic -> diagnostic.getMessage(null).contains("collides"));
    }
  }

  @Nested
  @DisplayName("Threaded generic specs")
  class ThreadedGenericSpecs {

    private static final JavaFileObject PAGE =
        JavaFileObjects.forSourceString(
            "com.example.Page",
            """
            package com.example;

            import java.util.List;

            public record Page<T>(List<T> items, int total) {}
            """);

    private static final JavaFileObject PAGE_DTO =
        JavaFileObjects.forSourceString(
            "com.example.PageDto",
            """
            package com.example;

            import java.util.List;

            public record PageDto<T>(List<T> items, int total) {}
            """);

    private static final JavaFileObject PAGE_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.PageMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}
            """);

    @Test
    @DisplayName("an identity-threaded spec emits a generic Impl behind the instance() singleton")
    void identityThreadedSpecEmitsAGenericImpl() {
      Compilation compilation = compile(PAGE, PAGE_DTO, PAGE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.PageMappingImpl"))
          .contains("public final class PageMappingImpl<T> implements PageMapping<T>")
          .contains("private static final PageMappingImpl<?> INSTANCE = new PageMappingImpl<>()")
          .contains("@SuppressWarnings(\"unchecked\")")
          .contains("public static <T> PageMappingImpl<T> instance()")
          .contains("public PageDto<T> build(Page<T> domain)")
          .contains("public Validated<NonEmptyList<FieldError>, Page<T>> parse(PageDto<T> wire)")
          // identity elements copy by reference under the null-element scan; the primitive
          // stays bare
          .contains(".field(\"items\", hkj$allPresent(wire.items()))")
          .contains(".field(\"total\", Validated.validNel(wire.total()))")
          // a lossless threaded mapping keeps the Iso tier, threaded
          .contains("public Iso<Page<T>, PageDto<T>> asIso()");
    }

    @Test
    @DisplayName("one Impl serves every instantiation at runtime, guards included")
    void threadedSpecServesEveryInstantiation() {
      Compilation compilation = compile(PAGE, PAGE_DTO, PAGE_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.genericInstance("com.example.PageMappingImpl");
        Object page =
            result
                .loadClass("com.example.Page")
                .getDeclaredConstructor(List.class, int.class)
                .newInstance(List.of("a", "b"), 2);

        Object dto = invoke(impl, "build", page);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> back =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(back.isValid()).isTrue();
        Assertions.assertThat(back.get()).isEqualTo(page);

        // The same singleton serves an Integer page: identity elements copy verbatim (element
        // parsing belongs to leaf and nested legs), and instance() is genuinely cached.
        Object integerPage =
            result
                .loadClass("com.example.PageDto")
                .getDeclaredConstructor(List.class, int.class)
                .newInstance(List.of(7, 8), 2);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> integers =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", integerPage);
        Assertions.assertThat(integers.isValid()).isTrue();
        Assertions.assertThat(invoke(integers.get(), "items")).isEqualTo(List.of(7, 8));
        Assertions.assertThat(result.genericInstance("com.example.PageMappingImpl")).isSameAs(impl);

        // Identity legs copy the container by reference, but the null doctrine reaches
        // inside: a null ELEMENT is a located, accumulating invalid at its index, exactly
        // as a lifted leg would locate it.
        Object nullElementPage =
            result
                .loadClass("com.example.PageDto")
                .getDeclaredConstructor(List.class, int.class)
                .newInstance(Arrays.asList("a", null, null), 3);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> scanned =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", nullElementPage);
        Assertions.assertThat(scanned.isInvalid()).isTrue();
        Assertions.assertThat(scanned.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("items", "1"), "must not be null"),
                new FieldError(List.of("items", "2"), "must not be null"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("multi-parameter and bounded specs thread their variables and bounds")
    void multiParameterAndBoundedSpecsThread() {
      JavaFileObject result =
          JavaFileObjects.forSourceString(
              "com.example.Result",
              """
              package com.example;

              public record Result<E, A>(E error, A value) {}
              """);
      JavaFileObject resultDto =
          JavaFileObjects.forSourceString(
              "com.example.ResultDto",
              """
              package com.example;

              public record ResultDto<E, A>(E error, A value) {}
              """);
      JavaFileObject ranked =
          JavaFileObjects.forSourceString(
              "com.example.Ranked",
              """
              package com.example;

              public record Ranked<T extends Number>(T score, String label) {}
              """);
      JavaFileObject rankedDto =
          JavaFileObjects.forSourceString(
              "com.example.RankedDto",
              """
              package com.example;

              public record RankedDto<T extends Number>(T score, String label) {}
              """);
      JavaFileObject specs =
          JavaFileObjects.forSourceString(
              "com.example.ThreadedShapes",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class ThreadedShapes {
                @GenerateMapping
                public interface ResultMapping<E, A>
                    extends MappingSpec<Result<E, A>, ResultDto<E, A>> {}

                @GenerateMapping
                public interface RankedMapping<T extends Number>
                    extends MappingSpec<Ranked<T>, RankedDto<T>> {}
              }
              """);

      Compilation compilation = compile(result, resultDto, ranked, rankedDto, specs);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedShapesResultMappingImpl"))
          .contains(
              "public final class ThreadedShapesResultMappingImpl<E, A> implements"
                  + " ThreadedShapes.ResultMapping<E, A>")
          .contains("public static <E, A> ThreadedShapesResultMappingImpl<E, A> instance()")
          .contains("public ResultDto<E, A> build(Result<E, A> domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedShapesRankedMappingImpl"))
          .contains("class ThreadedShapesRankedMappingImpl<T extends Number>");
    }

    @Test
    @DisplayName("a same-typed default leaf on a threaded component still routes the elements")
    void sameTypedDefaultLeafThreads() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.NormalisingPageMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface NormalisingPageMapping<T>
                  extends MappingSpec<Page<T>, PageDto<T>> {
                default ValidatedPrism<T, T> items() {
                  return ValidatedPrism.of(Validated::validNel, t -> t);
                }
              }
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.NormalisingPageMappingImpl"))
          .contains(".field(\"items\", hkj$ifPresent(wire.items(), items()::parseAll))");
    }

    @Test
    @DisplayName("a generic outer spec threads its own variable into a nested threaded spec")
    void genericOuterThreadsVariableIntoNestedSpec() {
      JavaFileObject feed =
          JavaFileObjects.forSourceString(
              "com.example.Feed",
              """
              package com.example;

              public record Feed<T>(String id, Page<T> results) {}
              """);
      JavaFileObject feedDto =
          JavaFileObjects.forSourceString(
              "com.example.FeedDto",
              """
              package com.example;

              public record FeedDto<T>(String id, PageDto<T> results) {}
              """);
      JavaFileObject feedMapping =
          JavaFileObjects.forSourceString(
              "com.example.FeedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface FeedMapping<T> extends MappingSpec<Feed<T>, FeedDto<T>> {}
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, PAGE_MAPPING, feed, feedDto, feedMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.FeedMappingImpl"))
          .contains("PageMappingImpl.<T>instance().asValidatedPrism()");
    }

    @Test
    @DisplayName("a concrete and a threaded spec covering the same pair are ambiguous")
    void concreteAndThreadedCoverageIsAmbiguous() {
      JavaFileObject concrete =
          JavaFileObjects.forSourceString(
              "com.example.StringPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface StringPageMapping
                  extends MappingSpec<Page<String>, PageDto<String>> {}
              """);
      JavaFileObject report =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(String id, Page<String> results) {}
              """);
      JavaFileObject reportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(String id, PageDto<String> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);
      Compilation compilation =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, concrete, report, reportDto, reportMapping);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("matches more than one mapping spec");
    }

    @Test
    @DisplayName("a nested threaded spec parses at runtime, failures located through the path")
    void nestedThreadedSpecWorksAtRuntime() throws Exception {
      JavaFileObject report =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(String id, Page<String> results) {}
              """);
      JavaFileObject reportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(String id, PageDto<String> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);
      Compilation compilation =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, report, reportDto, reportMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.ReportMappingImpl");

      Object page =
          result
              .loadClass("com.example.Page")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of("a", "b"), 2);
      Object reportValue =
          result
              .loadClass("com.example.Report")
              .getDeclaredConstructor(String.class, result.loadClass("com.example.Page"))
              .newInstance("R1", page);
      Object dto = invoke(impl, "build", reportValue);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> back =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(back.isValid()).isTrue();
      Assertions.assertThat(back.get()).isEqualTo(reportValue);

      // The nested Page round-trips; a null on the outer id locates as its own component.
      Object badPageDto =
          result
              .loadClass("com.example.PageDto")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of("a"), 1);
      Object badDto =
          result
              .loadClass("com.example.ReportDto")
              .getDeclaredConstructor(String.class, result.loadClass("com.example.PageDto"))
              .newInstance(null, badPageDto);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(new FieldError(List.of("id"), "must not be null"));
    }

    @Test
    @DisplayName("a wire-side container mismatch refuses to unify")
    void wireSideContainerMismatchRefusesToUnify() {
      JavaFileObject otherDto =
          JavaFileObjects.forSourceString(
              "com.example.OtherDto",
              """
              package com.example;

              public record OtherDto<T>(java.util.List<T> items, int total) {}
              """);
      JavaFileObject mismatchedWire =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(Page<String> results) {}
              """);
      JavaFileObject mismatchedWireDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(OtherDto<String> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);
      Compilation wireRefuses =
          compile(
              PAGE,
              PAGE_DTO,
              PAGE_MAPPING,
              otherDto,
              mismatchedWire,
              mismatchedWireDto,
              reportMapping);
      assertThat(wireRefuses).failed();
      assertThat(wireRefuses)
          .hadErrorContaining("target field 'ReportDto.results' has no usable source");
      // The refusal is the wire-side container: OtherDto is not Page.
      assertThat(wireRefuses).hadErrorContaining("com.example.OtherDto");
    }

    @Test
    @DisplayName("an inconsistent binding refuses to unify: one variable, two arguments")
    void inconsistentBindingRefusesToUnify() {
      JavaFileObject mirror =
          JavaFileObjects.forSourceString(
              "com.example.Mirror",
              """
              package com.example;

              public record Mirror<A, B>(A left, B right) {}
              """);
      JavaFileObject mirrorDto =
          JavaFileObjects.forSourceString(
              "com.example.MirrorDto",
              """
              package com.example;

              public record MirrorDto<A, B>(A left, B right) {}
              """);
      JavaFileObject mirrorMapping =
          JavaFileObjects.forSourceString(
              "com.example.MirrorMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface MirrorMapping<T> extends MappingSpec<Mirror<T, T>, MirrorDto<T, T>> {}
              """);
      JavaFileObject useSite =
          JavaFileObjects.forSourceString(
              "com.example.Holder3",
              """
              package com.example;

              public final class Holder3 {
                public record D(Mirror<String, Integer> pair) {}

                public record W(MirrorDto<String, Integer> pair) {}
              }
              """);
      JavaFileObject holderMapping =
          JavaFileObjects.forSourceString(
              "com.example.Holder3Mapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface Holder3Mapping extends MappingSpec<Holder3.D, Holder3.W> {}
              """);
      Compilation compilation = compile(mirror, mirrorDto, mirrorMapping, useSite, holderMapping);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target field 'W.pair' has no usable source");

      // The refusal is the inconsistent binding, not an unmappable shape: the same MirrorMapping
      // resolves a homogeneous Mirror<String, String>, binding T once.
      JavaFileObject homogeneous =
          JavaFileObjects.forSourceString(
              "com.example.HolderH",
              """
              package com.example;

              public final class HolderH {
                public record D(Mirror<String, String> pair) {}

                public record W(MirrorDto<String, String> pair) {}
              }
              """);
      JavaFileObject homogeneousMapping =
          JavaFileObjects.forSourceString(
              "com.example.HolderHMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HolderHMapping extends MappingSpec<HolderH.D, HolderH.W> {}
              """);
      Compilation resolves =
          compile(mirror, mirrorDto, mirrorMapping, homogeneous, homogeneousMapping);
      assertThat(resolves).succeeded();
      Assertions.assertThat(generatedSource(resolves, "com.example.HolderHMappingImpl"))
          .contains("MirrorMappingImpl.<String>instance().asValidatedPrism()");
    }

    private static final JavaFileObject REPORT =
        JavaFileObjects.forSourceString(
            "com.example.Report",
            """
            package com.example;

            public record Report(Page<String> results) {}
            """);

    private static final JavaFileObject REPORT_DTO =
        JavaFileObjects.forSourceString(
            "com.example.ReportDto",
            """
            package com.example;

            public record ReportDto(PageDto<String> results) {}
            """);

    private static final JavaFileObject REPORT_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.ReportMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
            """);

    @Test
    @DisplayName("a spec with an unbound type variable refuses to unify at a use site")
    void unboundSpecVariableRefusesToUnify() {
      // PageMapping<T, U> never binds U, so no candidate can cover a concrete Page use site.
      JavaFileObject unboundMapping =
          JavaFileObjects.forSourceString(
              "com.example.PageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PageMapping<T, U> extends MappingSpec<Page<T>, PageDto<T>> {}
              """);
      Compilation unbound =
          compile(PAGE, PAGE_DTO, unboundMapping, REPORT, REPORT_DTO, REPORT_MAPPING);
      assertThat(unbound).failed();
      assertThat(unbound)
          .hadErrorContaining("target field 'ReportDto.results' has no usable source");
    }

    @Test
    @DisplayName("a wildcard argument at a use site refuses to unify")
    void wildcardArgumentRefusesToUnify() {
      JavaFileObject wildcardReport =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(Page<?> results) {}
              """);
      JavaFileObject wildcardReportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(PageDto<?> results) {}
              """);
      Compilation wildcard =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, wildcardReport, wildcardReportDto, REPORT_MAPPING);
      assertThat(wildcard).failed();
      assertThat(wildcard)
          .hadErrorContaining("target field 'ReportDto.results' has no usable source");
    }

    @Test
    @DisplayName("raw and unresolved use sites step aside from unification")
    void rawAndUnresolvedUseSitesStepAside() {
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);

      // A raw container at the use site: same erasure, no arguments to bind.
      JavaFileObject rawReport =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record Report(Page results) {}
              """);
      JavaFileObject rawReportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              @SuppressWarnings("rawtypes")
              public record ReportDto(PageDto results) {}
              """);
      Compilation raw =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, rawReport, rawReportDto, reportMapping);
      assertThat(raw).failed();
      assertThat(raw).hadErrorContaining("target field 'ReportDto.results' has no usable source");

      // A raw ARGUMENT binds consistently but is not a supported instantiation argument.
      JavaFileObject rawArgReport =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record Report(Page<List> results) {}
              """);
      JavaFileObject rawArgReportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record ReportDto(PageDto<List> results) {}
              """);
      Compilation rawArg =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, rawArgReport, rawArgReportDto, reportMapping);
      assertThat(rawArg).failed();
      assertThat(rawArg)
          .hadErrorContaining("target field 'ReportDto.results' has no usable source");

      // An unresolved argument is javac's diagnostic, never a spurious match or a crash.
      JavaFileObject unresolvedReport =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(Page<Missing> results) {}
              """);
      JavaFileObject unresolvedReportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(PageDto<Missing> results) {}
              """);
      Compilation unresolved =
          compile(
              PAGE, PAGE_DTO, PAGE_MAPPING, unresolvedReport, unresolvedReportDto, reportMapping);
      assertThat(unresolved).failed();
      assertThat(unresolved).hadErrorContaining("Missing");
    }

    @Test
    @DisplayName("array arguments unify structurally; a mismatched pair refuses")
    void arrayArgumentsUnifyStructurally() {
      JavaFileObject arrayMapping =
          JavaFileObjects.forSourceString(
              "com.example.ArrayPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ArrayPageMapping<T> extends MappingSpec<Page<T[]>, PageDto<T[]>> {}
              """);
      JavaFileObject report =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(Page<String[]> results) {}
              """);
      JavaFileObject reportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(PageDto<String[]> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);
      Compilation matches = compile(PAGE, PAGE_DTO, arrayMapping, report, reportDto, reportMapping);
      assertThat(matches).succeeded();
      Assertions.assertThat(generatedSource(matches, "com.example.ReportMappingImpl"))
          .contains("ArrayPageMappingImpl.<String>instance().asValidatedPrism()");

      JavaFileObject mismatchedDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(PageDto<String> results) {}
              """);
      Compilation refuses =
          compile(PAGE, PAGE_DTO, arrayMapping, report, mismatchedDto, reportMapping);
      assertThat(refuses).failed();
      assertThat(refuses)
          .hadErrorContaining("target field 'ReportDto.results' has no usable source");
    }

    private static final JavaFileObject ELEMENT_SPEC =
        JavaFileObjects.forSourceString(
            "com.example.ElementMappedPageMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface ElementMappedPageMapping<T, TDto>
                extends MappingSpec<Page<T>, PageDto<TDto>> {
              ValidatedPrism<TDto, T> items();
            }
            """);

    @Test
    @DisplayName("an element-mapped spec nests through the using spec's component leaf")
    void elementMappedSpecNestsThroughComponentLeaf() {
      JavaFileObject catalogue =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              public record Catalogue(Page<EmailAddress> entries) {}
              """);
      JavaFileObject catalogueDto =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              public record CatalogueDto(PageDto<String> entries) {}
              """);
      JavaFileObject catalogueMapping =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {
                default ValidatedPrism<String, EmailAddress> entries() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);
      Compilation compilation =
          compile(EMAIL, PAGE, PAGE_DTO, ELEMENT_SPEC, catalogue, catalogueDto, catalogueMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CatalogueMappingImpl"))
          .contains("ElementMappedPageMappingImpl.of(entries()).asValidatedPrism()");
    }

    @Test
    @DisplayName("an element-mapped spec nests through another registered mapping recursively")
    void elementMappedSpecNestsThroughRegisteredMapping() {
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer2",
              """
              package com.example;

              public record Customer2(String name) {}
              """);
      JavaFileObject customerDto =
          JavaFileObjects.forSourceString(
              "com.example.Customer2Dto",
              """
              package com.example;

              public record Customer2Dto(String name) {}
              """);
      JavaFileObject customerMapping =
          JavaFileObjects.forSourceString(
              "com.example.Customer2Mapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface Customer2Mapping extends MappingSpec<Customer2, Customer2Dto> {}
              """);
      JavaFileObject catalogue =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              public record Catalogue(Page<Customer2> entries) {}
              """);
      JavaFileObject catalogueDto =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              public record CatalogueDto(PageDto<Customer2Dto> entries) {}
              """);
      JavaFileObject catalogueMapping =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {}
              """);
      Compilation compilation =
          compile(
              PAGE,
              PAGE_DTO,
              ELEMENT_SPEC,
              customer,
              customerDto,
              customerMapping,
              catalogue,
              catalogueDto,
              catalogueMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CatalogueMappingImpl"))
          .contains(
              "ElementMappedPageMappingImpl.of(Customer2MappingImpl.INSTANCE.asValidatedPrism())"
                  + ".asValidatedPrism()");
    }

    @Test
    @DisplayName("element-mapped composition recurses and terminates through nested containers")
    void elementMappedCompositionRecurses() {
      JavaFileObject outer =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              public record Outer(Page<Page<String>> nested) {}
              """);
      JavaFileObject outerDto =
          JavaFileObjects.forSourceString(
              "com.example.OuterDto",
              """
              package com.example;

              public record OuterDto(PageDto<PageDto<String>> nested) {}
              """);
      JavaFileObject outerMapping =
          JavaFileObjects.forSourceString(
              "com.example.OuterMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface OuterMapping extends MappingSpec<Outer, OuterDto> {
                default ValidatedPrism<String, String> nested() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation compilation =
          compile(PAGE, PAGE_DTO, ELEMENT_SPEC, outer, outerDto, outerMapping);
      assertThat(compilation).succeeded();
      // The outer Page<Page<String>> resolves the element-mapped spec twice, the inner element
      // pair (String, String) bottoming out on the nested() leaf: of(of(nested())).
      Assertions.assertThat(generatedSource(compilation, "com.example.OuterMappingImpl"))
          .contains(
              "ElementMappedPageMappingImpl.of(ElementMappedPageMappingImpl.of(nested())"
                  + ".asValidatedPrism()).asValidatedPrism()");
    }

    @Test
    @DisplayName("a self-covering element-mapped spec is diagnosed, not overflowed")
    void selfCoveringElementMappedSpecDiagnosed() {
      JavaFileObject node =
          JavaFileObjects.forSourceString(
              "com.example.Node",
              """
              package com.example;

              public record Node<T>(T value) {}
              """);
      JavaFileObject nodeDto =
          JavaFileObjects.forSourceString(
              "com.example.NodeDto",
              """
              package com.example;

              public record NodeDto<U>(U value) {}
              """);
      // The abstract leaf's element pair (Node<T>, NodeDto<U>) is the spec's own declared pair,
      // so an of() composition would need its own prism as input: a cycle.
      JavaFileObject nodeMapping =
          JavaFileObjects.forSourceString(
              "com.example.NodeMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface NodeMapping<T, U> extends MappingSpec<Node<T>, NodeDto<U>> {
                ValidatedPrism<NodeDto<U>, Node<T>> value();
              }
              """);
      JavaFileObject tree =
          JavaFileObjects.forSourceString(
              "com.example.Tree",
              """
              package com.example;

              public record Tree(Node<String> root) {}
              """);
      JavaFileObject treeDto =
          JavaFileObjects.forSourceString(
              "com.example.TreeDto",
              """
              package com.example;

              public record TreeDto(NodeDto<String> root) {}
              """);
      JavaFileObject treeMapping =
          JavaFileObjects.forSourceString(
              "com.example.TreeMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TreeMapping extends MappingSpec<Tree, TreeDto> {}
              """);
      Compilation compilation = compile(node, nodeDto, nodeMapping, tree, treeDto, treeMapping);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("which maps itself");
      assertThat(compilation).hadErrorContaining("never terminates");
    }

    @Test
    @DisplayName("a generic element-mapped outer threads its own variables into a nested of()")
    void genericOuterElementMappedNestsElementMapped() {
      JavaFileObject feed =
          JavaFileObjects.forSourceString(
              "com.example.Feed",
              """
              package com.example;

              public record Feed<T>(Page<T> results) {}
              """);
      JavaFileObject feedDto =
          JavaFileObjects.forSourceString(
              "com.example.FeedDto",
              """
              package com.example;

              public record FeedDto<D>(PageDto<D> results) {}
              """);
      JavaFileObject feedMapping =
          JavaFileObjects.forSourceString(
              "com.example.FeedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface FeedMapping<T, D> extends MappingSpec<Feed<T>, FeedDto<D>> {
                ValidatedPrism<D, T> results();
              }
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, ELEMENT_SPEC, feed, feedDto, feedMapping);
      assertThat(compilation).succeeded();
      // FeedMapping is itself element-mapped; its own leaf supplies the inner of()'s prism, and
      // the type variables thread through unchanged.
      Assertions.assertThat(generatedSource(compilation, "com.example.FeedMappingImpl"))
          .contains("public final class FeedMappingImpl<T, D> implements FeedMapping<T, D>")
          .contains("ElementMappedPageMappingImpl.of(results()).asValidatedPrism()");
    }

    @Test
    @DisplayName("a nested element-mapped mapping works at runtime, failures located through it")
    void nestedElementMappedMappingWorksAtRuntime() throws Exception {
      JavaFileObject catalogue =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              public record Catalogue(Page<EmailAddress> entries) {}
              """);
      JavaFileObject catalogueDto =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              public record CatalogueDto(PageDto<String> entries) {}
              """);
      JavaFileObject catalogueMapping =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {
                default ValidatedPrism<String, EmailAddress> entries() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);
      Compilation compilation =
          compile(EMAIL, PAGE, PAGE_DTO, ELEMENT_SPEC, catalogue, catalogueDto, catalogueMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CatalogueMappingImpl");

      Object ada =
          result
              .loadClass("com.example.EmailAddress")
              .getDeclaredConstructor(String.class)
              .newInstance("ada@corp.example");
      Object page =
          result
              .loadClass("com.example.Page")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of(ada), 1);
      Object catalogueValue =
          result
              .loadClass("com.example.Catalogue")
              .getDeclaredConstructor(result.loadClass("com.example.Page"))
              .newInstance(page);
      Object dto = invoke(impl, "build", catalogueValue);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> back =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(back.isValid()).isTrue();
      Assertions.assertThat(back.get()).isEqualTo(catalogueValue);

      Object badPageDto =
          result
              .loadClass("com.example.PageDto")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of("ada@corp.example", "nope"), 2);
      Object badDto =
          result
              .loadClass("com.example.CatalogueDto")
              .getDeclaredConstructor(result.loadClass("com.example.PageDto"))
              .newInstance(badPageDto);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("entries", "items", "1"), "not an email address"));
    }

    @Test
    @DisplayName("a multi-leaf element-mapped spec nests when every pair resolves by registry")
    void multiLeafElementMappedSpecNests() {
      JavaFileObject duo =
          JavaFileObjects.forSourceString(
              "com.example.Duo",
              """
              package com.example;

              public record Duo<A, B>(A first, B second) {}
              """);
      JavaFileObject duoDto =
          JavaFileObjects.forSourceString(
              "com.example.DuoDto",
              """
              package com.example;

              public record DuoDto<A, B>(A first, B second) {}
              """);
      JavaFileObject duoMapping =
          JavaFileObjects.forSourceString(
              "com.example.DuoMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface DuoMapping<A, ADto, B, BDto>
                  extends MappingSpec<Duo<A, B>, DuoDto<ADto, BDto>> {
                ValidatedPrism<ADto, A> first();

                ValidatedPrism<BDto, B> second();
              }
              """);
      JavaFileObject left =
          JavaFileObjects.forSourceString(
              "com.example.LeftPart",
              """
              package com.example;

              public record LeftPart(String v) {}
              """);
      JavaFileObject leftDto =
          JavaFileObjects.forSourceString(
              "com.example.LeftPartDto",
              """
              package com.example;

              public record LeftPartDto(String v) {}
              """);
      JavaFileObject leftMapping =
          JavaFileObjects.forSourceString(
              "com.example.LeftPartMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface LeftPartMapping extends MappingSpec<LeftPart, LeftPartDto> {}
              """);
      JavaFileObject right =
          JavaFileObjects.forSourceString(
              "com.example.RightPart",
              """
              package com.example;

              public record RightPart(int v) {}
              """);
      JavaFileObject rightDto =
          JavaFileObjects.forSourceString(
              "com.example.RightPartDto",
              """
              package com.example;

              public record RightPartDto(int v) {}
              """);
      JavaFileObject rightMapping =
          JavaFileObjects.forSourceString(
              "com.example.RightPartMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface RightPartMapping extends MappingSpec<RightPart, RightPartDto> {}
              """);
      JavaFileObject basket =
          JavaFileObjects.forSourceString(
              "com.example.Basket",
              """
              package com.example;

              public record Basket(Duo<LeftPart, RightPart> pair) {}
              """);
      JavaFileObject basketDto =
          JavaFileObjects.forSourceString(
              "com.example.BasketDto",
              """
              package com.example;

              public record BasketDto(DuoDto<LeftPartDto, RightPartDto> pair) {}
              """);
      JavaFileObject basketMapping =
          JavaFileObjects.forSourceString(
              "com.example.BasketMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BasketMapping extends MappingSpec<Basket, BasketDto> {}
              """);
      Compilation compilation =
          compile(
              duo,
              duoDto,
              duoMapping,
              left,
              leftDto,
              leftMapping,
              right,
              rightDto,
              rightMapping,
              basket,
              basketDto,
              basketMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BasketMappingImpl"))
          .contains(
              "DuoMappingImpl.of(LeftPartMappingImpl.INSTANCE.asValidatedPrism(),"
                  + " RightPartMappingImpl.INSTANCE.asValidatedPrism()).asValidatedPrism()");
    }

    @Test
    @DisplayName("an ambiguous element pair propagates the ambiguity, not a spurious of()")
    void ambiguousElementPairPropagates() {
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer2",
              """
              package com.example;

              public record Customer2(String name) {}
              """);
      JavaFileObject customerDto =
          JavaFileObjects.forSourceString(
              "com.example.Customer2Dto",
              """
              package com.example;

              public record Customer2Dto(String name) {}
              """);
      JavaFileObject mappingOne =
          JavaFileObjects.forSourceString(
              "com.example.Customer2Mapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface Customer2Mapping extends MappingSpec<Customer2, Customer2Dto> {}
              """);
      JavaFileObject mappingTwo =
          JavaFileObjects.forSourceString(
              "com.example.Customer2MappingToo",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface Customer2MappingToo
                  extends MappingSpec<Customer2, Customer2Dto> {}
              """);
      JavaFileObject catalogue =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              public record Catalogue(Page<Customer2> entries) {}
              """);
      JavaFileObject catalogueDto =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              public record CatalogueDto(PageDto<Customer2Dto> entries) {}
              """);
      JavaFileObject catalogueMapping =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {}
              """);
      Compilation compilation =
          compile(
              PAGE,
              PAGE_DTO,
              ELEMENT_SPEC,
              customer,
              customerDto,
              mappingOne,
              mappingTwo,
              catalogue,
              catalogueDto,
              catalogueMapping);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("matches more than one mapping spec");
    }

    @Test
    @DisplayName("an unresolvable element pair is diagnosed with both levers")
    void unresolvableElementPairDiagnosed() {
      JavaFileObject catalogue =
          JavaFileObjects.forSourceString(
              "com.example.Catalogue",
              """
              package com.example;

              public record Catalogue(Page<EmailAddress> entries) {}
              """);
      JavaFileObject catalogueDto =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueDto",
              """
              package com.example;

              public record CatalogueDto(PageDto<String> entries) {}
              """);
      JavaFileObject catalogueMapping =
          JavaFileObjects.forSourceString(
              "com.example.CatalogueMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {}
              """);
      Compilation compilation =
          compile(EMAIL, PAGE, PAGE_DTO, ELEMENT_SPEC, catalogue, catalogueDto, catalogueMapping);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("the element pair (com.example.EmailAddress, java.lang.String)");
      assertThat(compilation).hadErrorContaining("has no mapping");
    }

    @Test
    @DisplayName("an abstract leaf makes the spec element-mapped: of() instead of instance()")
    void abstractLeafEmitsTheOfFactory() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ElementMappedPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ElementMappedPageMapping<T, TDto>
                  extends MappingSpec<Page<T>, PageDto<TDto>> {
                ValidatedPrism<TDto, T> items();
              }
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ElementMappedPageMappingImpl");
      Assertions.assertThat(generated)
          .contains(
              "public final class ElementMappedPageMappingImpl<T, TDto> implements"
                  + " ElementMappedPageMapping<T, TDto>")
          .contains("private final ValidatedPrism<TDto, T> items;")
          .contains(
              "public static <T, TDto> ElementMappedPageMappingImpl<T, TDto> of(ValidatedPrism<TDto, T> items)")
          .contains("public ValidatedPrism<TDto, T> items()")
          .contains("public PageDto<TDto> build(Page<T> domain)")
          .contains("public Validated<NonEmptyList<FieldError>, Page<T>> parse(PageDto<TDto> wire)")
          // the leaf is fallible, so the Iso tier is out; and the Impl carries state, so no
          // shared singleton exists in either spelling
          .doesNotContain("asIso")
          .doesNotContain("INSTANCE")
          .doesNotContain("instance()");
    }

    @Test
    @DisplayName("an element-mapped mapping parses through its of() prism, failures index-located")
    void elementMappedMappingWorksAtRuntime() throws Exception {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ElementMappedPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ElementMappedPageMapping<T, TDto>
                  extends MappingSpec<Page<T>, PageDto<TDto>> {
                ValidatedPrism<TDto, T> items();
              }
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);

      ValidatedPrism<String, Integer> numbers =
          ValidatedPrism.of(
              raw ->
                  raw.chars().allMatch(Character::isDigit) && !raw.isEmpty()
                      ? Validated.validNel(Integer.valueOf(raw))
                      : Validated.invalidNel(FieldError.of("not a number")),
              String::valueOf);
      Object impl =
          result
              .loadClass("com.example.ElementMappedPageMappingImpl")
              .getMethod("of", ValidatedPrism.class)
              .invoke(null, numbers);

      Object page =
          result
              .loadClass("com.example.Page")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of(4, 2), 2);
      Object dto = invoke(impl, "build", page);
      Assertions.assertThat(invoke(dto, "items")).isEqualTo(List.of("4", "2"));

      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> back =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(back.isValid()).isTrue();
      Assertions.assertThat(back.get()).isEqualTo(page);

      Object badDto =
          result
              .loadClass("com.example.PageDto")
              .getDeclaredConstructor(List.class, int.class)
              .newInstance(List.of("4", "x"), 2);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(new FieldError(List.of("items", "1"), "not a number"));

      // Two of() calls are two instances: the Impl carries the prism, so no singleton is shared.
      Object other =
          result
              .loadClass("com.example.ElementMappedPageMappingImpl")
              .getMethod("of", ValidatedPrism.class)
              .invoke(null, numbers);
      Assertions.assertThat(other).isNotSameAs(impl);
    }

    @Test
    @DisplayName("a raw ValidatedPrism abstract is not a leaf shape")
    void rawAbstractPrismIsNotALeaf() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RawLeafPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RawLeafPageMapping<T, TDto>
                  extends MappingSpec<Page<T>, PageDto<TDto>> {
                @SuppressWarnings("rawtypes")
                ValidatedPrism items();
              }
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("abstract method 'items' is neither a rename, a leaf, nor a bridge");
    }

    @Test
    @DisplayName("an abstract leaf on a concrete spec is diagnosed: nothing defers its parser")
    void abstractLeafOnConcreteSpecRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ConcreteLeafMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ConcreteLeafMapping extends MappingSpec<Records.D, Records.W> {
                ValidatedPrism<String, String> a();
              }
              """);
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public record D(String a) {}

                public record W(String a) {}
              }
              """);
      Compilation compilation = compile(records, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("abstract leaf 'a' needs a generic spec");
      assertThat(compilation).hadErrorContaining("Give the method a body ('default')");
    }

    @Test
    @DisplayName("agreeing mix-in abstract leaves count as one of() parameter")
    void agreeingMixinAbstractLeavesCountOnce() {
      JavaFileObject leafA =
          JavaFileObjects.forSourceString(
              "com.example.ItemsVocabularyA",
              """
              package com.example;

              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface ItemsVocabularyA {
                ValidatedPrism<String, Integer> items();
              }
              """);
      JavaFileObject leafB =
          JavaFileObjects.forSourceString(
              "com.example.ItemsVocabularyB",
              """
              package com.example;

              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface ItemsVocabularyB {
                ValidatedPrism<String, Integer> items();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CountedPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CountedPageMapping<X>
                  extends ItemsVocabularyA, ItemsVocabularyB,
                      MappingSpec<Page<Integer>, PageDto<String>> {}
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, leafA, leafB, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.CountedPageMappingImpl");
      // one field + one ctor param + one of() param + one override = four mentions, not eight
      Assertions.assertThat(generated.split("ValidatedPrism<String, Integer> items", -1))
          .hasSize(5);
      Assertions.assertThat(generated).contains("of(ValidatedPrism<String, Integer> items)");
    }

    @Test
    @DisplayName("an abstract String helper is still neither a rename, a leaf nor a bridge")
    void abstractStringHelperStaysDiagnosed() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.HelperPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HelperPageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {
                String label();
              }
              """);
      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("abstract method 'label' is neither a rename, a leaf, nor a bridge");
    }

    @Test
    @DisplayName("a static prism-shaped method named after a component is not its leaf")
    void staticPrismShapedMethodIsNotALeaf() {
      JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.example.Holder2",
              """
              package com.example;

              public final class Holder2 {
                public record D(Integer count) {}

                public record W(String count) {}
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Holder2Mapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface Holder2Mapping extends MappingSpec<Holder2.D, Holder2.W> {
                static ValidatedPrism<String, Integer> count() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Integer.valueOf(raw)), String::valueOf);
                }
              }
              """);
      // A static is not inherited by the Impl and is not a leaf declaration; the component's
      // type mismatch stays honestly diagnosed instead of silently routing through the helper.
      Compilation compilation = compile(holder, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no usable source");
    }

    @Test
    @DisplayName("a rename marker is never mistaken for the leaf its component still needs")
    void renameMarkerIsNotALeaf() {
      JavaFileObject holder =
          JavaFileObjects.forSourceString(
              "com.example.Holder",
              """
              package com.example;

              public final class Holder {
                public record D(Integer count) {}

                public record W(String total) {}
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.HolderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HolderMapping extends MappingSpec<Holder.D, Holder.W> {
                @MapField(to = "total")
                Integer count();
              }
              """);
      // The abstract marker method is named exactly like the component the leaf lookup runs
      // for; it must be skipped as a rename, leaving the type mismatch honestly diagnosed.
      Compilation compilation = compile(holder, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no usable source");
    }

    @Test
    @DisplayName("'of' is reserved on element-mapped specs; 'instance' is again free")
    void ofIsReservedOnElementMappedSpecs() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.ElementMappedPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ElementMappedPageMapping<T, TDto>
                  extends MappingSpec<Page<T>, PageDto<TDto>> {
                ValidatedPrism<TDto, T> items();

                default String of(ValidatedPrism<TDto, T> ignored) {
                  return "";
                }
              }
              """);
      Compilation collision = compile(PAGE, PAGE_DTO, colliding);
      assertThat(collision).failed();
      assertThat(collision).hadErrorContaining("collides with the 'of' member");

      // instance() is not emitted on an element-mapped Impl, so the name is not reserved.
      JavaFileObject instanceHelper =
          JavaFileObjects.forSourceString(
              "com.example.ElementMappedPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ElementMappedPageMapping<T, TDto>
                  extends MappingSpec<Page<T>, PageDto<TDto>> {
                ValidatedPrism<TDto, T> items();

                default String instance() {
                  return "helper";
                }
              }
              """);
      assertThat(compile(PAGE, PAGE_DTO, instanceHelper)).succeeded();
    }

    @Test
    @DisplayName("a threaded spec nests at a concrete use site by unifying its type arguments")
    void threadedSpecsNestByUnification() {
      JavaFileObject report =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(String id, Page<String> results) {}
              """);
      JavaFileObject reportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(String id, PageDto<String> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);

      Compilation compilation =
          compile(PAGE, PAGE_DTO, PAGE_MAPPING, report, reportDto, reportMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.ReportMappingImpl"))
          .contains("PageMappingImpl.<String>instance().asValidatedPrism()");
    }

    @Test
    @DisplayName(
        "'instance' is reserved on threaded specs: the singleton accessor cannot be" + " shadowed")
    void instanceIsReservedOnThreadedSpecs() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.PageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {
                default String instance() {
                  return "shadowed";
                }
              }
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, colliding);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'instance()' collides with the 'instance' member");
    }

    @Test
    @DisplayName(
        "threaded variables nest inside concrete argument shapes, and the other tiers,"
            + " renames and derived fields all thread")
    void threadedShapesAcrossTiers() {
      JavaFileObject pageItemsDto =
          JavaFileObjects.forSourceString(
              "com.example.PageItemsDto",
              """
              package com.example;

              import java.util.List;

              public record PageItemsDto<T>(List<T> items) {}
              """);
      JavaFileObject pageSummaryDto =
          JavaFileObjects.forSourceString(
              "com.example.PageSummaryDto",
              """
              package com.example;

              import java.util.List;

              public record PageSummaryDto<T>(List<T> items, int total, String summary) {}
              """);
      JavaFileObject pageCountDto =
          JavaFileObjects.forSourceString(
              "com.example.PageCountDto",
              """
              package com.example;

              import java.util.List;

              public record PageCountDto<T>(List<T> items, int count) {}
              """);
      JavaFileObject specs =
          JavaFileObjects.forSourceString(
              "com.example.ThreadedTiers",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.Getter;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public final class ThreadedTiers {
                @GenerateMapping
                public interface ItemsMapping<T> extends MappingSpec<Page<T>, PageItemsDto<T>> {}

                @GenerateMapping
                public interface PatchMapping<T> extends MappingSpec<Page<T>, PageItemsDto<T>> {
                  default ValidatedPrism<T, T> items() {
                    return ValidatedPrism.of(Validated::validNel, t -> t);
                  }
                }

                @GenerateMapping
                public interface RenamedMapping<T> extends MappingSpec<Page<T>, PageCountDto<T>> {
                  @MapField(to = "count")
                  int total();
                }

                @GenerateMapping
                public interface SummaryMapping<T>
                    extends MappingSpec<Page<T>, PageSummaryDto<T>> {
                  default Getter<Page<T>, String> summary() {
                    return Getter.of(p -> p.items().size() + " items");
                  }
                }

                @GenerateMapping
                public interface DeepThreadedMapping<T>
                    extends MappingSpec<Page<List<T>>, PageDto<List<T>>> {}
              }
              """);

      Compilation compilation =
          compile(PAGE, PAGE_DTO, pageItemsDto, pageSummaryDto, pageCountDto, specs);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedTiersItemsMappingImpl"))
          .contains("public Lens<Page<T>, PageItemsDto<T>> asLens()");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedTiersPatchMappingImpl"))
          .contains("public Validated<NonEmptyList<FieldError>, Page<T>> patch(Page<T> domain,");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedTiersRenamedMappingImpl"))
          .contains("public PageCountDto<T> build(Page<T> domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedTiersSummaryMappingImpl"))
          .contains("summary().get(domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.ThreadedTiersDeepThreadedMappingImpl"))
          .contains("public PageDto<List<T>> build(Page<List<T>> domain)");
    }

    @Test
    @DisplayName(
        "a variable on one side with a concrete on the other fails at the component,"
            + " with the usual what/why/fix")
    void mixedVariableAndConcreteArgumentsFailAtTheComponent() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.MixedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface MixedMapping<T> extends MappingSpec<Page<T>, PageDto<String>> {}
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'PageDto.items' has no usable source");
    }

    @Test
    @DisplayName("an unresolved type argument steps aside for javac's own diagnostic")
    void unresolvedTypeArgumentsAreNotGateErrors() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.MissingArgMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface MissingArgMapping
                  extends MappingSpec<Page<Missing>, PageDto<Missing>> {}
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot find symbol");
      Assertions.assertThat(compilation.errors())
          .noneMatch(
              diagnostic ->
                  diagnostic.getMessage(null).contains("is not a supported instantiation"));
    }

    @Test
    @DisplayName("the collision sweep runs on threaded specs with the threaded member set")
    void collisionSweepCoversThreadedSpecs() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.PageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {
                default PageDto<T> build(Page<T> domain) {
                  return null;
                }
              }
              """);

      Compilation compilation = compile(PAGE, PAGE_DTO, colliding);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'build(Page)' collides with the 'build' member");
    }
  }

  @Nested
  @DisplayName("Located nulls on record wires")
  class LocatedNullsOnRecordWires {

    @Test
    @DisplayName("a hand-written mapper signature gets the targeted answer, both directions")
    void handMapperSignatureGetsTargetedDiagnostic() {
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public record D(String a) {}

                public record W(String a) {}
              }
              """);
      JavaFileObject toDto =
          JavaFileObjects.forSourceString(
              "com.example.HandMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HandMapping extends MappingSpec<Records.D, Records.W> {
                Records.W toDto(Records.D domain);
              }
              """);
      Compilation outbound = compile(records, toDto);
      assertThat(outbound).failed();
      assertThat(outbound).hadErrorContaining("abstract method 'toDto' redeclares the mapping");
      assertThat(outbound).hadErrorContaining("Delete the method and call the generated Impl");

      JavaFileObject fromDto =
          JavaFileObjects.forSourceString(
              "com.example.HandMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HandMapping extends MappingSpec<Records.D, Records.W> {
                Records.D toDomain(Records.W wire);
              }
              """);
      Compilation inbound = compile(records, fromDto);
      assertThat(inbound).failed();
      assertThat(inbound).hadErrorContaining("abstract method 'toDomain' redeclares the mapping");

      // A half-match is not the hand-mapper shape: it falls through to the ordinary diagnostic.
      JavaFileObject domainHalf =
          JavaFileObjects.forSourceString(
              "com.example.HandMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HandMapping extends MappingSpec<Records.D, Records.W> {
                String describe(Records.D domain);
              }
              """);
      Compilation domainSide = compile(records, domainHalf);
      assertThat(domainSide).failed();
      assertThat(domainSide)
          .hadErrorContaining(
              "abstract method 'describe' is neither a rename, a leaf, nor a bridge");

      JavaFileObject wireHalf =
          JavaFileObjects.forSourceString(
              "com.example.HandMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HandMapping extends MappingSpec<Records.D, Records.W> {
                String describe(Records.W wire);
              }
              """);
      Compilation wireSide = compile(records, wireHalf);
      assertThat(wireSide).failed();
      assertThat(wireSide)
          .hadErrorContaining(
              "abstract method 'describe' is neither a rename, a leaf, nor a bridge");
    }

    @Test
    @DisplayName("a typo'd local leaf is an error with a nearest-name hint, never silently inert")
    void typodLocalLeafIsDiagnosed() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping2",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CustomerMapping2 extends MappingSpec<Records.D, Records.W> {
                default ValidatedPrism<String, String> emial() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(raw)
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      v -> v);
                }
              }
              """);
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public record D(String email) {}

                public record W(String email) {}
              }
              """);
      Compilation compilation = compile(records, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("leaf 'emial' names no component of D");
      assertThat(compilation).hadErrorContaining("Did you mean 'email()'?");
      assertThat(compilation).hadErrorContaining("would silently validate nothing");
    }

    @Test
    @DisplayName("an unmatched local leaf with no near name lists the components, hint-free")
    void unmatchedLocalLeafListsComponents() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping2",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CustomerMapping2 extends MappingSpec<Records.D, Records.W> {
                default ValidatedPrism<String, String> currency() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }
              }
              """);
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public record D(String email) {}

                public record W(String email) {}
              }
              """);
      Compilation compilation = compile(records, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("leaf 'currency' names no component of D");
      assertThat(compilation).hadErrorContaining("Found on D: [email]");
      Assertions.assertThat(compilation.diagnostics())
          .noneMatch(d -> d.getMessage(null).contains("Did you mean"));
    }

    @Test
    @DisplayName("an inherited unmatched leaf stays inert: shared vocabulary fits partial shapes")
    void inheritedUnmatchedLeafStaysInert() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.WideVocabulary",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface WideVocabulary {
                default ValidatedPrism<String, String> email() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }

                default ValidatedPrism<String, String> phone() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping2",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping2
                  extends WideVocabulary, MappingSpec<Records.D, Records.W> {}
              """);
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.example.Records",
              """
              package com.example;

              public final class Records {
                public record D(String email) {}

                public record W(String email) {}
              }
              """);
      // The spec has no 'phone' component; the inherited leaf for it stays inert by design.
      Compilation compilation = compile(records, vocabulary, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("an unmatched local leaf is an error on the update path too")
    void unmatchedLocalLeafOnUpdatePath() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account3",
              """
              package com.example;

              public record Account3(String email) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.Account3PatchDto",
              """
              package com.example;

              public class Account3PatchDto {
                private String email;

                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Account3PatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface Account3PatchMapping extends UpdateSpec<Account3, Account3PatchDto> {
                default ValidatedPrism<String, String> emali() {
                  return ValidatedPrism.of(Validated::validNel, v -> v);
                }
              }
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("leaf 'emali' names no component of Account3");
      assertThat(compilation).hadErrorContaining("Did you mean 'email()'?");
    }

    @Test
    @DisplayName("identity list elements are scanned: null elements locate by index, accumulating")
    void identityListElementsAreScanned() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Basket",
              """
              package com.example;

              import java.util.List;

              public record Basket(List<String> tags) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BasketDto",
              """
              package com.example;

              import java.util.List;

              public record BasketDto(List<String> tags) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BasketMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BasketMapping extends MappingSpec<Basket, BasketDto> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      // The scan is a guard, not fallibility: the identity mapping keeps its Iso tier.
      Assertions.assertThat(generatedSource(compilation, "com.example.BasketMappingImpl"))
          .contains(".field(\"tags\", hkj$allPresent(wire.tags()))")
          .contains("public Iso<Basket, BasketDto> asIso()");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.BasketMappingImpl");

      List<String> tags = List.of("a", "b");
      Object dto =
          result
              .loadClass("com.example.BasketDto")
              .getDeclaredConstructor(List.class)
              .newInstance(tags);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(parsed.isValid()).isTrue();
      // Identity legs copy, they do not rebuild: the same list reference passes through.
      Assertions.assertThat(invoke(parsed.get(), "tags")).isSameAs(tags);

      Object badDto =
          result
              .loadClass("com.example.BasketDto")
              .getDeclaredConstructor(List.class)
              .newInstance(Arrays.asList(null, "b", null));
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("tags", "0"), "must not be null"),
              new FieldError(List.of("tags", "2"), "must not be null"));

      // A null list itself stays the whole-component located invalid.
      Object nullListDto =
          result
              .loadClass("com.example.BasketDto")
              .getDeclaredConstructor(List.class)
              .newInstance(new Object[] {null});
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> nullList =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", nullListDto);
      Assertions.assertThat(nullList.getError().toJavaList())
          .containsExactly(new FieldError(List.of("tags"), "must not be null"));
    }

    @Test
    @DisplayName("identity map values are scanned: null values locate by key; keys stay structural")
    void identityMapValuesAreScanned() throws Exception {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Scores",
              """
              package com.example;

              import java.util.Map;

              public record Scores(Map<String, Integer> byPlayer) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ScoresDto",
              """
              package com.example;

              import java.util.Map;

              public record ScoresDto(Map<String, Integer> byPlayer) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ScoresMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ScoresMapping extends MappingSpec<Scores, ScoresDto> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.ScoresMappingImpl");

      java.util.Map<String, Integer> withNull = new java.util.LinkedHashMap<>();
      withNull.put("ada", 10);
      withNull.put("bob", null);
      Object badDto =
          result
              .loadClass("com.example.ScoresDto")
              .getDeclaredConstructor(java.util.Map.class)
              .newInstance(withNull);
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(new FieldError(List.of("byPlayer", "bob"), "must not be null"));

      // A null KEY is a structurally broken map: the caller contract, matching parseValues.
      java.util.Map<String, Integer> nullKey = new java.util.HashMap<>();
      nullKey.put(null, 1);
      Object nullKeyDto =
          result
              .loadClass("com.example.ScoresDto")
              .getDeclaredConstructor(java.util.Map.class)
              .newInstance(nullKey);
      Assertions.assertThatThrownBy(() -> invoke(impl, "parse", nullKeyDto))
          .hasRootCauseInstanceOf(NullPointerException.class)
          .rootCause()
          .hasMessageContaining("map keys must not be null");
    }

    @Test
    @DisplayName("projected identity containers are scanned in the patch tier too")
    void patchProjectedIdentityContainersAreScanned() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Profile2",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public record Profile2(
                  String id, EmailAddress email, List<String> tags, Map<String, Integer> scores) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.Profile2Dto",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public record Profile2Dto(
                  String email, List<String> tags, Map<String, Integer> scores) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Profile2Mapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface Profile2Mapping extends MappingSpec<Profile2, Profile2Dto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.Profile2MappingImpl"))
          .contains("public Validated<NonEmptyList<FieldError>, Profile2> patch(")
          .contains(".field(\"tags\", hkj$allPresent(wire.tags()))")
          .contains(".field(\"scores\", hkj$valuesPresent(wire.scores()))");
    }

    @SuppressWarnings("unchecked")
    private Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
    }

    @Test
    @DisplayName(
        "null components are located, accumulated invalids, never an NPE — the guard"
            + " beats the leaf")
    void nullComponentsAccumulateLocated() {
      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, SPEC);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.UserMappingImpl");
        Object wire =
            result
                .loadClass("com.example.UserDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance(null, "not-an-email", 36);

        Validated<NonEmptyList<FieldError>, Object> parsed = parse(impl, wire);
        assertThatValidated(parsed)
            .isInvalid()
            .hasFieldErrors("name: must not be null", "email: not an email address");

        // A null leaf read never reaches the leaf's prism (which would throw): guard first.
        Object nullLeafWire =
            result
                .loadClass("com.example.UserDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("Ada", null, 36);
        assertThatValidated(parse(impl, nullLeafWire)).hasFieldErrors("email: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a null inside a nested wire value locates through the nesting as a dotted path")
    void nullsLocateThroughNesting() {
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              public record Customer(String name) {}
              """);
      JavaFileObject customerDto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerDto",
              """
              package com.example;

              public record CustomerDto(String name) {}
              """);
      JavaFileObject customerMapping =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
              """);
      JavaFileObject order =
          JavaFileObjects.forSourceString(
              "com.example.Order",
              """
              package com.example;

              public record Order(String id, Customer customer) {}
              """);
      JavaFileObject orderDto =
          JavaFileObjects.forSourceString(
              "com.example.OrderDto",
              """
              package com.example;

              public record OrderDto(String id, CustomerDto customer) {}
              """);
      JavaFileObject orderMapping =
          JavaFileObjects.forSourceString(
              "com.example.OrderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OrderMapping extends MappingSpec<Order, OrderDto> {}
              """);

      Compilation compilation =
          compile(customer, customerDto, customerMapping, order, orderDto, orderMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.OrderMappingImpl");
        Object innerNull =
            result
                .loadClass("com.example.CustomerDto")
                .getDeclaredConstructor(String.class)
                .newInstance((Object) null);
        Object wire =
            result
                .loadClass("com.example.OrderDto")
                .getDeclaredConstructor(String.class, result.loadClass("com.example.CustomerDto"))
                .newInstance("7", innerNull);

        assertThatValidated(parse(impl, wire)).hasFieldErrors("customer.name: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("patch locates a nested null at depth: the 422 leg stays a 422, never a 500")
    void patchLocatesNestedNullAtDepth() {
      JavaFileObject address =
          JavaFileObjects.forSourceString(
              "com.example.Address",
              """
              package com.example;

              public record Address(String zip) {}
              """);
      JavaFileObject addressDto =
          JavaFileObjects.forSourceString(
              "com.example.AddressDto",
              """
              package com.example;

              public record AddressDto(String zip) {}
              """);
      JavaFileObject addressMapping =
          JavaFileObjects.forSourceString(
              "com.example.AddressMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AddressMapping extends MappingSpec<Address, AddressDto> {}
              """);
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              public record Customer(String id, Address address) {}
              """);
      JavaFileObject customerPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchDto",
              """
              package com.example;

              public record CustomerPatchDto(AddressDto address) {}
              """);
      JavaFileObject customerPatchMapping =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerPatchMapping
                  extends MappingSpec<Customer, CustomerPatchDto> {}
              """);

      Compilation compilation =
          compile(
              address,
              addressDto,
              addressMapping,
              customer,
              customerPatchDto,
              customerPatchMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CustomerPatchMappingImpl");
        Object domainAddress =
            result
                .loadClass("com.example.Address")
                .getDeclaredConstructor(String.class)
                .newInstance("12345");
        Object domain =
            result
                .loadClass("com.example.Customer")
                .getDeclaredConstructor(String.class, result.loadClass("com.example.Address"))
                .newInstance("7", domainAddress);
        Object wireAddress =
            result
                .loadClass("com.example.AddressDto")
                .getDeclaredConstructor(String.class)
                .newInstance((Object) null);
        Object wire =
            result
                .loadClass("com.example.CustomerPatchDto")
                .getDeclaredConstructor(result.loadClass("com.example.AddressDto"))
                .newInstance(wireAddress);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "patch", domain, wire);
        assertThatValidated(patched).isInvalid().hasFieldErrors("address.zip: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "a null container leg is located; a null element inside the container locates by index")
    void containerLegAndElementNullsAreLocated() {
      JavaFileObject roster =
          JavaFileObjects.forSourceString(
              "com.example.Roster",
              """
              package com.example;

              import java.util.List;

              public record Roster(String id, List<EmailAddress> emails) {}
              """);
      JavaFileObject rosterDto =
          JavaFileObjects.forSourceString(
              "com.example.RosterDto",
              """
              package com.example;

              import java.util.List;

              public record RosterDto(String id, List<String> emails) {}
              """);
      JavaFileObject rosterMapping =
          JavaFileObjects.forSourceString(
              "com.example.RosterMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RosterMapping extends MappingSpec<Roster, RosterDto> {
                default ValidatedPrism<String, EmailAddress> emails() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, roster, rosterDto, rosterMapping);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.RosterMappingImpl");
        Object nullListWire =
            result
                .loadClass("com.example.RosterDto")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("7", null);
        assertThatValidated(parse(impl, nullListWire)).hasFieldErrors("emails: must not be null");

        // A null ELEMENT is a located invalid at its index,
        // completing the doctrine inside containers.
        Object nullElementWire =
            result
                .loadClass("com.example.RosterDto")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("7", Arrays.asList("a@b.c", null));
        assertThatValidated(parse(impl, nullElementWire))
            .hasFieldErrors("emails.1: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  @DisplayName("Concrete instantiations of generic records")
  class GenericInstantiations {

    private static final JavaFileObject PAGE =
        JavaFileObjects.forSourceString(
            "com.example.Page",
            """
            package com.example;

            import java.util.List;

            public record Page<T>(List<T> items, int total) {}
            """);

    private static final JavaFileObject PAGE_DTO =
        JavaFileObjects.forSourceString(
            "com.example.PageDto",
            """
            package com.example;

            import java.util.List;

            public record PageDto<T>(List<T> items, int total) {}
            """);

    private static final JavaFileObject USER_PAGE_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.UserPageMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface UserPageMapping extends MappingSpec<Page<User>, PageDto<UserDto>> {}
            """);

    @Test
    @DisplayName(
        "a concrete instantiation classifies under substitution: elements lift through"
            + " the sibling spec, identity components copy")
    void concreteInstantiationClassifies() {
      Compilation compilation =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, USER_PAGE_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.UserPageMappingImpl");
      Assertions.assertThat(generated)
          .contains("public final class UserPageMappingImpl implements UserPageMapping")
          .contains("public PageDto<UserDto> build(Page<User> domain)")
          .contains(
              "public Validated<NonEmptyList<FieldError>, Page<User>> parse(PageDto<UserDto>"
                  + " wire)")
          .contains(
              ".field(\"items\", hkj$ifPresent(wire.items(),"
                  + " UserMappingImpl.INSTANCE.asValidatedPrism()::parseAll))")
          .contains(".field(\"total\", Validated.validNel(wire.total()))")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName(
        "the whole stack composes at runtime: substitution, container lifting, the null"
            + " doctrine and index location")
    void instantiatedGenericRoundTripsAndLocates() {
      Compilation compilation =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, USER_PAGE_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.UserPageMappingImpl");
        Object goodDto =
            result
                .loadClass("com.example.UserDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("Ada", "ada@x.com", 36);
        Object badDto =
            result
                .loadClass("com.example.UserDto")
                .getDeclaredConstructor(String.class, String.class, int.class)
                .newInstance("Bob", "nope", 41);
        Object pageDto =
            result
                .loadClass("com.example.PageDto")
                .getDeclaredConstructor(List.class, int.class)
                .newInstance(List.of(goodDto, badDto), 2);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", pageDto);
        assertThatValidated(parsed)
            .isInvalid()
            .hasFieldErrors("items.1.email: not an email address");

        // The null doctrine composes with the substitution too: a null element is located.
        Object nullElementPage =
            result
                .loadClass("com.example.PageDto")
                .getDeclaredConstructor(List.class, int.class)
                .newInstance(Arrays.asList(goodDto, null), 2);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> nullParsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", nullElementPage);
        assertThatValidated(nullParsed).hasFieldErrors("items.1: must not be null");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "an instantiated generic mapping registers like any other: siblings nest it by"
            + " its concrete types")
    void instantiatedGenericNestsIntoSiblings() {
      JavaFileObject report =
          JavaFileObjects.forSourceString(
              "com.example.Report",
              """
              package com.example;

              public record Report(String id, Page<User> results) {}
              """);
      JavaFileObject reportDto =
          JavaFileObjects.forSourceString(
              "com.example.ReportDto",
              """
              package com.example;

              public record ReportDto(String id, PageDto<UserDto> results) {}
              """);
      JavaFileObject reportMapping =
          JavaFileObjects.forSourceString(
              "com.example.ReportMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReportMapping extends MappingSpec<Report, ReportDto> {}
              """);

      Compilation compilation =
          compile(
              EMAIL,
              DOMAIN,
              WIRE,
              SPEC,
              PAGE,
              PAGE_DTO,
              USER_PAGE_MAPPING,
              report,
              reportDto,
              reportMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.ReportMappingImpl"))
          .contains(
              ".field(\"results\", hkj$ifPresent(wire.results(),"
                  + " UserPageMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }

    @Test
    @DisplayName("array, nested-generic and bounded arguments are concrete and map fine")
    void furtherConcreteShapesAreAccepted() {
      JavaFileObject ranked =
          JavaFileObjects.forSourceString(
              "com.example.Ranked",
              """
              package com.example;

              public record Ranked<T extends Number>(T score, String label) {}
              """);
      JavaFileObject rankedDto =
          JavaFileObjects.forSourceString(
              "com.example.RankedDto",
              """
              package com.example;

              public record RankedDto<T extends Number>(T score, String label) {}
              """);
      JavaFileObject duo =
          JavaFileObjects.forSourceString(
              "com.example.Duo",
              """
              package com.example;

              public record Duo<E, A>(E left, A right) {}
              """);
      JavaFileObject duoDto =
          JavaFileObjects.forSourceString(
              "com.example.DuoDto",
              """
              package com.example;

              public record DuoDto<E, A>(E left, A right) {}
              """);
      JavaFileObject specs =
          JavaFileObjects.forSourceString(
              "com.example.FurtherShapes",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class FurtherShapes {
                @GenerateMapping
                public interface ArrayPageMapping
                    extends MappingSpec<Page<String[]>, PageDto<String[]>> {}

                @GenerateMapping
                public interface PrimitiveArrayPageMapping
                    extends MappingSpec<Page<int[]>, PageDto<int[]>> {}

                @GenerateMapping
                public interface DeepPageMapping
                    extends MappingSpec<Page<List<String>>, PageDto<List<String>>> {}

                @GenerateMapping
                public interface IntRankedMapping
                    extends MappingSpec<Ranked<Integer>, RankedDto<Integer>> {}

                @GenerateMapping
                public interface TwoParamMapping
                    extends MappingSpec<Duo<String, Integer>, DuoDto<String, Integer>> {}
              }
              """);

      Compilation compilation =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, ranked, rankedDto, duo, duoDto, specs);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(compilation, "com.example.FurtherShapesArrayPageMappingImpl"))
          .contains("public PageDto<String[]> build(Page<String[]> domain)");
      Assertions.assertThat(
              generatedSource(
                  compilation, "com.example.FurtherShapesPrimitiveArrayPageMappingImpl"))
          .contains("public PageDto<int[]> build(Page<int[]> domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.FurtherShapesDeepPageMappingImpl"))
          .contains("public PageDto<List<String>> build(Page<List<String>> domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.FurtherShapesIntRankedMappingImpl"))
          .contains("public RankedDto<Integer> build(Ranked<Integer> domain)");
      Assertions.assertThat(
              generatedSource(compilation, "com.example.FurtherShapesTwoParamMappingImpl"))
          .contains("public DuoDto<String, Integer> build(Duo<String, Integer> domain)");
    }

    @Test
    @DisplayName("the lossy-projection and patch tiers classify under substitution too")
    void projectionTiersClassifyUnderSubstitution() {
      JavaFileObject pageItemsDto =
          JavaFileObjects.forSourceString(
              "com.example.PageItemsDto",
              """
              package com.example;

              import java.util.List;

              public record PageItemsDto<T>(List<T> items) {}
              """);
      JavaFileObject lensSpec =
          JavaFileObjects.forSourceString(
              "com.example.PageItemsMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PageItemsMapping
                  extends MappingSpec<Page<User>, PageItemsDto<User>> {}
              """);
      JavaFileObject patchSpec =
          JavaFileObjects.forSourceString(
              "com.example.PagePatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PagePatchMapping
                  extends MappingSpec<Page<User>, PageItemsDto<UserDto>> {}
              """);

      Compilation compilation =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, pageItemsDto, lensSpec, patchSpec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.PageItemsMappingImpl"))
          .contains("public Lens<Page<User>, PageItemsDto<User>> asLens()");
      Assertions.assertThat(generatedSource(compilation, "com.example.PagePatchMappingImpl"))
          .contains(
              "public Validated<NonEmptyList<FieldError>, Page<User>> patch(Page<User> domain,")
          .contains(
              ".field(\"items\", hkj$ifPresent(wire.items(),"
                  + " UserMappingImpl.INSTANCE.asValidatedPrism()::parseAll))");
    }

    @Test
    @DisplayName("Map and Optional components lift under the substitution as well")
    void containerKindsLiftUnderSubstitution() {
      JavaFileObject env =
          JavaFileObjects.forSourceString(
              "com.example.Env",
              """
              package com.example;

              import java.util.Map;
              import java.util.Optional;

              public record Env<T>(Map<String, T> byKey, Optional<T> head, int n) {}
              """);
      JavaFileObject envDto =
          JavaFileObjects.forSourceString(
              "com.example.EnvDto",
              """
              package com.example;

              import java.util.Map;
              import java.util.Optional;

              public record EnvDto<T>(Map<String, T> byKey, Optional<T> head, int n) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserEnvMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserEnvMapping extends MappingSpec<Env<User>, EnvDto<UserDto>> {}
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, SPEC, env, envDto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.UserEnvMappingImpl"))
          .contains(
              ".field(\"byKey\", hkj$ifPresent(wire.byKey(),"
                  + " UserMappingImpl.INSTANCE.asValidatedPrism()::parseValues))")
          .contains(".field(\"head\", hkj$ifPresent(wire.head(), o -> o.map(v ->")
          .contains(".field(\"n\", Validated.validNel(wire.n()))");
    }

    @Test
    @DisplayName(
        "the collision sweep runs on instantiated specs with the instantiated" + " member set")
    void collisionSweepCoversInstantiatedSpecs() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.UserPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserPageMapping
                  extends MappingSpec<Page<User>, PageDto<UserDto>> {
                default PageDto<UserDto> build(Page<User> domain) {
                  return null;
                }
              }
              """);

      Compilation compilation = compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, colliding);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'build(Page)' collides with the 'build' member");
    }

    @Test
    @DisplayName("raw, wildcard and generic-spec shapes stay diagnosed")
    void unsupportedGenericShapesAreDiagnosed() {
      JavaFileObject rawSpec =
          JavaFileObjects.forSourceString(
              "com.example.RawPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @SuppressWarnings("rawtypes")
              @GenerateMapping
              public interface RawPageMapping extends MappingSpec<Page, PageDto> {}
              """);
      Compilation raw = compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, rawSpec);
      assertThat(raw).failed();
      assertThat(raw).hadErrorContaining("'Page' is used raw");

      JavaFileObject wildcardSpec =
          JavaFileObjects.forSourceString(
              "com.example.WildPageMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WildPageMapping extends MappingSpec<Page<?>, PageDto<?>> {}
              """);
      Compilation wildcard = compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, wildcardSpec);
      assertThat(wildcard).failed();
      assertThat(wildcard)
          .hadErrorContaining("'com.example.Page<?>' is not a supported instantiation");

      // The wire side is checked independently: a concrete domain does not excuse it.
      JavaFileObject wildcardWireSpec =
          JavaFileObjects.forSourceString(
              "com.example.WildWireMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WildWireMapping extends MappingSpec<User, PageDto<?>> {}
              """);
      Compilation wildcardWire =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, wildcardWireSpec);
      assertThat(wildcardWire).failed();
      assertThat(wildcardWire)
          .hadErrorContaining("'com.example.PageDto<?>' is not a supported instantiation");

      // Concreteness is recursive: a wildcard nested inside an argument is caught too.
      JavaFileObject nestedWildcardSpec =
          JavaFileObjects.forSourceString(
              "com.example.NestedWildMapping",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface NestedWildMapping
                  extends MappingSpec<Page<List<?>>, PageDto<List<?>>> {}
              """);
      Compilation nestedWildcard =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, nestedWildcardSpec);
      assertThat(nestedWildcard).failed();
      assertThat(nestedWildcard)
          .hadErrorContaining(
              "'com.example.Page<java.util.List<?>>' is not a supported instantiation");

      // Raw nested arguments are caught recursively — raw is less safe than the rejected
      // wildcard.
      JavaFileObject nestedRawSpec =
          JavaFileObjects.forSourceString(
              "com.example.NestedRawMapping",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @SuppressWarnings("rawtypes")
              @GenerateMapping
              public interface NestedRawMapping extends MappingSpec<Page<List>, PageDto<List>> {}
              """);
      Compilation nestedRaw = compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, nestedRawSpec);
      assertThat(nestedRaw).failed();
      assertThat(nestedRaw)
          .hadErrorContaining(
              "'com.example.Page<java.util.List>' is not a supported instantiation");

      // The raw check fires on the wire leg too.
      JavaFileObject rawWireSpec =
          JavaFileObjects.forSourceString(
              "com.example.RawWireMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @SuppressWarnings("rawtypes")
              @GenerateMapping
              public interface RawWireMapping extends MappingSpec<User, PageDto> {}
              """);
      Compilation rawWire = compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, rawWireSpec);
      assertThat(rawWire).failed();
      assertThat(rawWire).hadErrorContaining("'PageDto' is used raw");

      // Wildcards stay wildcards even inside a generic holder (a member interface is static,
      // so the holder's variable cannot appear; genuinely foreign variables are unreachable and
      // the gate's check for them is defensive).
      JavaFileObject wildcardInHolderSpec =
          JavaFileObjects.forSourceString(
              "com.example.ForeignVarHolder",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public interface ForeignVarHolder<T> {
                @GenerateMapping
                interface Inner extends MappingSpec<Page<?>, PageDto<?>> {}
              }
              """);
      Compilation wildcardInHolder =
          compile(EMAIL, DOMAIN, WIRE, SPEC, PAGE, PAGE_DTO, wildcardInHolderSpec);
      assertThat(wildcardInHolder).failed();
      assertThat(wildcardInHolder)
          .hadErrorContaining("'com.example.Page<?>' is not a supported instantiation");
    }
  }

  @Nested
  @DisplayName("Spec inheritance - shared mix-ins")
  class SpecInheritance {

    private static final JavaFileObject ACCOUNT =
        JavaFileObjects.forSourceString(
            "com.example.Account",
            """
            package com.example;

            public record Account(String name, EmailAddress email) {}
            """);

    private static final JavaFileObject ACCOUNT_DTO =
        JavaFileObjects.forSourceString(
            "com.example.AccountDto",
            """
            package com.example;

            public record AccountDto(String fullName, String email, String display) {}
            """);

    // The headline mix-in: a rename, a leaf and a derived field shared as plain vocabulary.
    // The static helper pins JLS 8.4.8 - interface statics are not inherited, so it never
    // reaches classification.
    private static final JavaFileObject VOCABULARY =
        JavaFileObjects.forSourceString(
            "com.example.AccountVocabulary",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.Getter;
            import org.higherkindedj.optics.annotations.MapField;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface AccountVocabulary {
              @MapField(to = "fullName")
              String name();

              default ValidatedPrism<String, EmailAddress> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    EmailAddress::value);
              }

              default Getter<Account, String> display() {
                return Getter.of(a -> a.name() + " <" + a.email().value() + ">");
              }

              static String ignoredHelper(String s) {
                return s;
              }
            }
            """);

    private static final JavaFileObject ACCOUNT_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.AccountMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface AccountMapping
                extends AccountVocabulary, MappingSpec<Account, AccountDto> {}
            """);

    @Test
    @DisplayName("inherited renames, leaves and derived fields drive the mapping")
    void inheritedVocabularyDrivesTheMapping() {
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, ACCOUNT_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AccountMappingImpl"))
          .contains("public final class AccountMappingImpl implements AccountMapping")
          .contains(
              "new AccountDto(domain.name(), email().build(domain.email()),"
                  + " display().get(domain))")
          .contains(".field(\"name\", hkj$ifPresent(wire.fullName(), Validated::validNel))")
          .contains(".field(\"email\", hkj$ifPresent(wire.email(), email()::parse))")
          .doesNotContain(".field(\"display\"")
          // the inherited rename still gets its marker stub
          .contains("public String name()");
    }

    @Test
    @DisplayName("inherited vocabulary behaves at runtime, null doctrine included")
    void inheritedVocabularyBehavesAtRuntime() throws Exception {
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, ACCOUNT_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.AccountMappingImpl");
      Object email =
          result
              .loadClass("com.example.EmailAddress")
              .getDeclaredConstructor(String.class)
              .newInstance("ada@example.org");
      Object account =
          result
              .loadClass("com.example.Account")
              .getDeclaredConstructor(String.class, result.loadClass("com.example.EmailAddress"))
              .newInstance("Ada", email);

      Object dto = invoke(impl, "build", account);
      Assertions.assertThat(invoke(dto, "fullName")).isEqualTo("Ada");
      Assertions.assertThat(invoke(dto, "display")).isEqualTo("Ada <ada@example.org>");

      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> back =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(back.isValid()).isTrue();
      Assertions.assertThat(back.get()).isEqualTo(account);

      // An inherited leaf locates its failures like a local one - and the inherited rename's
      // wire property is guarded by the null doctrine.
      Object badDto =
          result
              .loadClass("com.example.AccountDto")
              .getDeclaredConstructor(String.class, String.class, String.class)
              .newInstance(null, "not-an-email", "x");
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> bad =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", badDto);
      Assertions.assertThat(bad.isInvalid()).isTrue();
      Assertions.assertThat(bad.getError().toJavaList())
          .containsExactly(
              new FieldError(List.of("name"), "must not be null"),
              new FieldError(List.of("email"), "not an email address"));
    }

    @Test
    @DisplayName("a local override hides the mix-in leaf - Java's own precedence")
    void localOverrideWins() throws Exception {
      JavaFileObject overriding =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface AccountMapping
                  extends AccountVocabulary, MappingSpec<Account, AccountDto> {
                @Override
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, overriding);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.AccountMappingImpl");
      Object dto =
          result
              .loadClass("com.example.AccountDto")
              .getDeclaredConstructor(String.class, String.class, String.class)
              .newInstance("Ada", "not-an-email", "x");

      // The mix-in's leaf would reject this; the spec's override accepts anything.
      @SuppressWarnings("unchecked")
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
      Assertions.assertThat(parsed.isValid()).isTrue();
      Assertions.assertThat(invoke(invoke(parsed.get(), "email"), "value"))
          .isEqualTo("not-an-email");
    }

    @Test
    @DisplayName("vocabulary is collected across the whole hierarchy, not just direct parents")
    void transitiveVocabularyIsCollected() {
      JavaFileObject grandparent =
          JavaFileObjects.forSourceString(
              "com.example.NameVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface NameVocabulary {
                @MapField(to = "fullName")
                String name();
              }
              """);
      JavaFileObject parent =
          JavaFileObjects.forSourceString(
              "com.example.ContactVocabulary",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.Getter;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public interface ContactVocabulary extends NameVocabulary {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                default Getter<Account, String> display() {
                  return Getter.of(a -> a.name());
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends ContactVocabulary, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, grandparent, parent, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AccountMappingImpl"))
          .contains(".field(\"name\", hkj$ifPresent(wire.fullName(), Validated::validNel))")
          .contains(".field(\"email\", hkj$ifPresent(wire.email(), email()::parse))")
          .contains("display().get(domain)");
    }

    @Test
    @DisplayName("unrelated mix-ins agreeing on a rename count as one declaration")
    void agreeingMixinRenamesCountOnce() {
      // JLS 9.4.1 lets override-equivalent abstracts coexist, so javac accepts this hierarchy;
      // two declarations of the same fact must fold into one rename and one stub.
      JavaFileObject agreeing =
          JavaFileObjects.forSourceString(
              "com.example.AgreeingVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface AgreeingVocabulary {
                @MapField(to = "fullName")
                String name();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends AccountVocabulary, AgreeingVocabulary,
                      MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, agreeing, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountMappingImpl");
      Assertions.assertThat(generated)
          .contains(".field(\"name\", hkj$ifPresent(wire.fullName(), Validated::validNel))");
      Assertions.assertThat(generated.split("public String name\\(\\)", -1)).hasSize(2);
    }

    @Test
    @DisplayName("mix-ins renaming the same component to different targets are diagnosed")
    void conflictingMixinRenamesAreDiagnosed() {
      JavaFileObject conflicting =
          JavaFileObjects.forSourceString(
              "com.example.ConflictingVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface ConflictingVocabulary {
                @MapField(to = "display")
                String name();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends AccountVocabulary, ConflictingVocabulary,
                      MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, conflicting, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("component 'name' has conflicting renames");
      assertThat(compilation).hadErrorContaining("(inherited from '");
      assertThat(compilation)
          .hadErrorContaining("Override the rename on the spec itself, or align the mix-ins");
    }

    @Test
    @DisplayName("a local rename re-declaration hides the mix-in's, like any override")
    void localRenameOverrideHidesTheMixins() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends AccountVocabulary, MappingSpec<Account, AccountDto> {
                @Override
                @MapField(to = "fullName")
                String name();
              }
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(compilation, "com.example.AccountMappingImpl")
                  .split("public String name\\(\\)", -1))
          .hasSize(2);
    }

    @Test
    @DisplayName("a mix-in reachable through two paths counts once (diamond)")
    void diamondReachableMixinCountsOnce() {
      JavaFileObject left =
          JavaFileObjects.forSourceString(
              "com.example.LeftVocabulary",
              """
              package com.example;

              public interface LeftVocabulary extends ContactVocabulary {}
              """);
      JavaFileObject right =
          JavaFileObjects.forSourceString(
              "com.example.RightVocabulary",
              """
              package com.example;

              public interface RightVocabulary extends ContactVocabulary {}
              """);
      JavaFileObject base =
          JavaFileObjects.forSourceString(
              "com.example.ContactVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface ContactVocabulary {
                @MapField(to = "fullName")
                String name();
              }
              """);
      JavaFileObject diamondDto =
          JavaFileObjects.forSourceString(
              "com.example.NameOnlyDto",
              """
              package com.example;

              public record NameOnlyDto(String fullName) {}
              """);
      JavaFileObject nameOnly =
          JavaFileObjects.forSourceString(
              "com.example.NameOnly",
              """
              package com.example;

              public record NameOnly(String name) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.NameOnlyMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface NameOnlyMapping
                  extends LeftVocabulary, RightVocabulary,
                      MappingSpec<NameOnly, NameOnlyDto> {}
              """);
      Compilation compilation = compile(base, left, right, nameOnly, diamondDto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(compilation, "com.example.NameOnlyMappingImpl")
                  .split("public String name\\(\\)", -1))
          .hasSize(2);
    }

    @Test
    @DisplayName("an inherited abstract non-rename is diagnosed with its declaring interface")
    void inheritedAbstractNonRenameIsDiagnosed() {
      JavaFileObject broken =
          JavaFileObjects.forSourceString(
              "com.example.BrokenVocabulary",
              """
              package com.example;

              public interface BrokenVocabulary {
                int bogus();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends BrokenVocabulary, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, broken, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "abstract method 'bogus' (inherited from 'BrokenVocabulary') is neither a rename,"
                  + " a leaf, nor a bridge");
    }

    @Test
    @DisplayName("a mix-in member colliding with an emitted member names its declaring interface")
    void inheritedCollisionNamesTheDeclaringInterface() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.BuildVocabulary",
              """
              package com.example;

              public interface BuildVocabulary {
                default AccountDto build(Account domain) {
                  return null;
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends AccountVocabulary, BuildVocabulary, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, VOCABULARY, colliding, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "(inherited from 'BuildVocabulary') collides with the 'build' member");
    }

    @Test
    @DisplayName("a generic mix-in is read under the spec")
    void genericMixinIsResolved() {
      // A pair of its own, because the shared fixtures claim every wire component between them:
      // the generic mix-in has to contribute a member no other vocabulary declares, or the stub
      // it is judged by could have come from the one that already says String.
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Contact",
              """
              package com.example;

              public record Contact(String name, String nickname) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ContactDto",
              """
              package com.example;

              public record ContactDto(String fullName, String alias) {}
              """);
      JavaFileObject plain =
          JavaFileObjects.forSourceString(
              "com.example.ContactVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface ContactVocabulary {
                @MapField(to = "fullName")
                String name();
              }
              """);
      JavaFileObject generic =
          JavaFileObjects.forSourceString(
              "com.example.ElementVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface ElementVocabulary<T> {
                @MapField(to = "alias")
                T nickname();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ContactMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ContactMapping
                  extends ElementVocabulary<String>,
                      ContactVocabulary,
                      MappingSpec<Contact, ContactDto> {}
              """);
      // Generic is no longer the question: a mix-in's members are read under the spec, so what
      // matters is whether the route to it can be substituted, not whether it declares parameters.
      // A non-generic mix-in rides alongside, so what is asserted is a mapping that completes
      // rather than one the gate stopped before it started.
      Compilation compilation = compile(domain, wire, plain, generic, spec);
      assertThat(compilation).succeeded();
      // 'nickname' is declared only on the generic mix-in, so only the substitution can give its
      // stub a String: read as declared it would be Object, which does not implement the spec.
      // 'name' alongside it says the plain mix-in still contributes as it always did.
      Assertions.assertThat(generatedSource(compilation, "com.example.ContactMappingImpl"))
          .contains("public String nickname()")
          .contains("public String name()");
    }

    @Test
    @DisplayName("a mix-in that transitively extends MappingSpec is rejected")
    void indirectSpecMixinIsRejected() {
      JavaFileObject base =
          JavaFileObjects.forSourceString(
              "com.example.BaseMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MappingSpec;

              public interface BaseMapping extends MappingSpec<Account, AccountDto> {}
              """);
      JavaFileObject sneaky =
          JavaFileObjects.forSourceString(
              "com.example.Sneaky",
              """
              package com.example;

              public interface Sneaky extends BaseMapping {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends Sneaky, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, base, sneaky, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("mix-in 'Sneaky' is itself a mapping spec");
    }

    @Test
    @DisplayName("an unresolved mix-in is javac's error, not a mix-in diagnostic")
    void unresolvedMixinStepsAside() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends MissingVocabulary, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("MissingVocabulary");
      Assertions.assertThat(compilation.diagnostics())
          .noneMatch(d -> d.getMessage(null).contains("mix-in"));
    }

    @Test
    @DisplayName("a mix-in with an unresolved superinterface is javac's error, not the gate's")
    void unresolvedMixinParentStepsAside() {
      JavaFileObject partial =
          JavaFileObjects.forSourceString(
              "com.example.PartialVocabulary",
              """
              package com.example;

              public interface PartialVocabulary extends MissingBase {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends PartialVocabulary, MappingSpec<Account, AccountDto> {}
              """);
      Compilation compilation = compile(EMAIL, ACCOUNT, ACCOUNT_DTO, partial, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("MissingBase");
      Assertions.assertThat(compilation.diagnostics())
          .noneMatch(d -> d.getMessage(null).contains("mix-in"));
    }

    @Test
    @DisplayName("a threaded generic spec composes with a non-generic mix-in")
    void threadedSpecComposesWithMixins() {
      JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              public record Box<T>(T value, String label) {}
              """);
      JavaFileObject boxDto =
          JavaFileObjects.forSourceString(
              "com.example.BoxDto",
              """
              package com.example;

              public record BoxDto<T>(T value, String tag) {}
              """);
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.BoxVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface BoxVocabulary {
                @MapField(to = "tag")
                String label();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoxMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BoxMapping<T>
                  extends BoxVocabulary, MappingSpec<Box<T>, BoxDto<T>> {}
              """);
      Compilation compilation = compile(box, boxDto, vocabulary, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BoxMappingImpl"))
          .contains("public final class BoxMappingImpl<T> implements BoxMapping<T>")
          .contains("public static <T> BoxMappingImpl<T> instance()")
          .contains(".field(\"label\", hkj$ifPresent(wire.tag(), Validated::validNel))");
    }
  }

  /**
   * One home for {@code @OptionalBridge}, deliberately including the bean-wire and sparse-tier
   * cases: those are behaviours of the annotation rather than of beans or of updates, and the
   * automatic bean bridge they contrast with has its own class in {@code MappingProcessorBeanTest}.
   */
  @Nested
  @DisplayName("The Optional bridge (@OptionalBridge)")
  class TheOptionalBridge {

    private static final JavaFileObject CUSTOMER =
        JavaFileObjects.forSourceString(
            "com.example.Customer",
            """
            package com.example;

            import java.util.Optional;

            public record Customer(String name, Optional<String> nickname) {}
            """);

    private static final JavaFileObject CUSTOMER_DTO =
        JavaFileObjects.forSourceString(
            "com.example.CustomerDto",
            """
            package com.example;

            public record CustomerDto(String name, String nickname) {}
            """);

    private static final JavaFileObject CUSTOMER_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CustomerMapping",
            """
            package com.example;

            import java.util.Optional;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;

            @GenerateMapping
            public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
              @OptionalBridge
              Optional<String> nickname();
            }
            """);

    /** A spec with a bridged component that also converts its present element through a leaf. */
    private static final JavaFileObject SUBSCRIBER =
        JavaFileObjects.forSourceString(
            "com.example.Subscriber",
            """
            package com.example;

            import java.util.Optional;

            public record Subscriber(String name, Optional<EmailAddress> email) {}
            """);

    private static final JavaFileObject SUBSCRIBER_DTO =
        JavaFileObjects.forSourceString(
            "com.example.SubscriberDto",
            """
            package com.example;

            public record SubscriberDto(String name, String email) {}
            """);

    private static final JavaFileObject SUBSCRIBER_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.SubscriberMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.OptionalBridge;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface SubscriberMapping extends MappingSpec<Subscriber, SubscriberDto> {
              @OptionalBridge
              default ValidatedPrism<String, EmailAddress> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    EmailAddress::value);
              }
            }
            """);

    @SuppressWarnings("unchecked")
    private Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
    }

    @Test
    @DisplayName("a marked component bridges: build unwraps to null, parse reads ofNullable")
    void markedComponentBridgesBothWays() {
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("return new CustomerDto(domain.name(), domain.nickname().orElse(null));")
          .contains(
              ".field(\"nickname\", Validated.validNel(Optional.ofNullable(wire.nickname())))")
          // the marker is a declaration, stubbed out exactly like a rename
          .contains("Bridge declaration only; not invocable.")
          .contains("@OptionalBridge markers declare bridges and are not invocable");
    }

    @Test
    @DisplayName("empty maps to a null wire component and back, and a present value round-trips")
    void emptyMapsToNullAndBack() throws ReflectiveOperationException {
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CustomerMappingImpl");
      Class<?> customer = result.loadClass("com.example.Customer");

      Object absent =
          customer
              .getDeclaredConstructor(String.class, Optional.class)
              .newInstance("Ada", Optional.empty());
      Object absentWire = invoke(impl, "build", absent);
      Assertions.assertThat(invoke(absentWire, "nickname")).isNull();
      Assertions.assertThat(parse(impl, absentWire).get()).isEqualTo(absent);

      Object present =
          customer
              .getDeclaredConstructor(String.class, Optional.class)
              .newInstance("Ada", Optional.of("countess"));
      Object presentWire = invoke(impl, "build", present);
      Assertions.assertThat(invoke(presentWire, "nickname")).isEqualTo("countess");
      Assertions.assertThat(parse(impl, presentWire).get()).isEqualTo(present);
    }

    @Test
    @DisplayName("a null wire component parses to Optional.empty, not a located FieldError")
    void nullParsesToEmptyRatherThanAnError() throws ReflectiveOperationException {
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.CustomerMappingImpl");
      Object wire =
          result
              .loadClass("com.example.CustomerDto")
              .getDeclaredConstructor(String.class, String.class)
              .newInstance("Ada", null);

      Validated<NonEmptyList<FieldError>, Object> parsed = parse(impl, wire);
      Assertions.assertThat(parsed.isValid()).isTrue();
      Assertions.assertThat(invoke(parsed.get(), "nickname")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("a bridged component costs the Iso tier, exactly as it does on a bean wire")
    void aBridgedComponentWithholdsAsIso() {
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("asValidatedPrism")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("without the annotation the component stays null-is-an-error: the default holds")
    void withoutTheAnnotationTheDefaultDoctrineHolds() {
      JavaFileObject bare =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, bare);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'CustomerDto.nickname' has no usable source");
      // and the refusal points at the opt-in rather than at a lossless-looking whole-Optional
      // leaf, with the declaration to paste
      assertThat(compilation)
          .hadErrorContaining(
              "Add '@OptionalBridge java.util.Optional<java.lang.String> nickname();' to the"
                  + " spec, so an absent value reads as a null wire component and back.");
    }

    @Test
    @DisplayName("the refusal offers the bridge plus an element leaf when the elements differ")
    void theRefusalOffersTheBridgeWithALeafWhenElementsDiffer() {
      JavaFileObject bare =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SubscriberMapping
                  extends MappingSpec<Subscriber, SubscriberDto> {}
              """);
      Compilation compilation = compile(EMAIL, SUBSCRIBER, SUBSCRIBER_DTO, bare);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Add '@OptionalBridge default ValidatedPrism<java.lang.String,"
                  + " com.example.EmailAddress> email()' to the spec, a leaf over the ELEMENT"
                  + " types");
    }

    @Test
    @DisplayName("a bridged component with a leaf validates the present value through it")
    void aBridgedLeafValidatesThePresentValue() throws ReflectiveOperationException {
      Compilation compilation = compile(EMAIL, SUBSCRIBER, SUBSCRIBER_DTO, SUBSCRIBER_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.SubscriberMappingImpl"))
          .contains("domain.email().map(email()::build).orElse(null)")
          .contains("Optional.ofNullable(wire.email())");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.SubscriberMappingImpl");
      Class<?> dto = result.loadClass("com.example.SubscriberDto");

      Object absent =
          dto.getDeclaredConstructor(String.class, String.class).newInstance("Ada", null);
      Assertions.assertThat(invoke(parse(impl, absent).get(), "email")).isEqualTo(Optional.empty());

      Object valid =
          dto.getDeclaredConstructor(String.class, String.class)
              .newInstance("Ada", "ada@example.org");
      Assertions.assertThat(parse(impl, valid).isValid()).isTrue();

      Object invalid =
          dto.getDeclaredConstructor(String.class, String.class).newInstance("Ada", "nope");
      Validated<NonEmptyList<FieldError>, Object> parsed = parse(impl, invalid);
      assertThatValidated(parsed).isInvalid().hasFieldErrors("email: not an email address");
    }

    @Test
    @DisplayName("a bridge composes with a rename on the same marker method")
    void aBridgeComposesWithARename() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerDto",
              """
              package com.example;

              public record CustomerDto(String name, String nick) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @MapField(to = "nick")
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("return new CustomerDto(domain.name(), domain.nickname().orElse(null));")
          .contains(".field(\"nickname\", Validated.validNel(Optional.ofNullable(wire.nick())))")
          // one method, one stub, naming both correspondences it declares
          .contains("Rename and bridge declaration only; not invocable.")
          .contains(
              "@MapField and @OptionalBridge methods declare correspondences and are not"
                  + " invocable");
    }

    @Test
    @DisplayName("a projection carrying a bridge maps through the validated patch, not asLens")
    void aBridgedProjectionTakesThePatchTier() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              import java.util.Optional;

              public record Customer(String id, String name, Optional<String> nickname) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerCardDto",
              """
              package com.example;

              public record CustomerCardDto(String name, String nickname) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerCardMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerCardMapping
                  extends MappingSpec<Customer, CustomerCardDto> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerCardMappingImpl"))
          .contains("public Validated<NonEmptyList<FieldError>, Customer> patch(")
          .doesNotContain("asLens() {");
    }

    @Test
    @DisplayName("an inherited bridge binds like any other shared vocabulary")
    void anInheritedBridgeBinds() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.Nicknames",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public interface Nicknames {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping
                  extends Nicknames, MappingSpec<Customer, CustomerDto> {}
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, vocabulary, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("domain.nickname().orElse(null)");
    }

    @Test
    @DisplayName("an inherited bridge that names nothing stays inert; a local one is rejected")
    void anUnmatchedBridgeIsLocalOnly() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.Extras",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public interface Extras {
                @OptionalBridge
                Optional<String> alias();
              }
              """);
      JavaFileObject inheriting =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface UserMapping extends Extras, MappingSpec<User, UserDto> {}
              """);
      assertThat(compile(EMAIL, plainUser(), plainUserDto(), vocabulary, inheriting)).succeeded();

      JavaFileObject local =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface UserMapping extends MappingSpec<User, UserDto> {
                @OptionalBridge
                Optional<String> nikname();
              }
              """);
      Compilation compilation = compile(EMAIL, plainUser(), plainUserDto(), local);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'nikname' names no component of User");
      assertThat(compilation).hadErrorContaining("Did you mean 'nickname()'?");
    }

    @Test
    @DisplayName("a bridge on a component that is not Optional is rejected")
    void aBridgeOnANonOptionalComponentIsRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                String name();
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'name' names a component that is not Optional");
      assertThat(compilation).hadErrorContaining("which has no absent state to bridge");
    }

    @Test
    @DisplayName("a bridge to a primitive wire component is rejected: it can never be null")
    void aBridgeToAPrimitiveWireComponentIsRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Reading",
              """
              package com.example;

              import java.util.Optional;

              public record Reading(String id, Optional<Integer> value) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.ReadingDto",
              """
              package com.example;

              public record ReadingDto(String id, int value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ReadingMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface ReadingMapping extends MappingSpec<Reading, ReadingDto> {
                @OptionalBridge
                Optional<Integer> value();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("bridges to the primitive record component 'value'");
      assertThat(compilation).hadErrorContaining("can never be null");
    }

    @Test
    @DisplayName("a bridge to an already-Optional wire component is reported as redundant")
    void aBridgeToAnOptionalWireComponentIsRedundant() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerDto",
              """
              package com.example;

              import java.util.Optional;

              public record CustomerDto(String name, Optional<String> nickname) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, dto, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "@OptionalBridge on 'nickname' is redundant: 'nickname' is already Optional");
      // the Optional pair maps by identity, exactly as it would without the annotation
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains(".field(\"nickname\", hkj$ifPresent(wire.nickname(), Validated::validNel))");
    }

    @Test
    @DisplayName("a bridge whose element types differ with no leaf names the element types")
    void aBridgeWithoutAConvertingLeafNamesTheElementTypes() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface SubscriberMapping extends MappingSpec<Subscriber, SubscriberDto> {
                @OptionalBridge
                Optional<EmailAddress> email();
              }
              """);
      Compilation compilation = compile(EMAIL, SUBSCRIBER, SUBSCRIBER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bridged to the nullable record component 'email' of type java.lang.String");
      assertThat(compilation)
          .hadErrorContaining("ValidatedPrism<java.lang.String, com.example.EmailAddress>");
    }

    @Test
    @DisplayName("a locally declared bridge on a bean wire compiles, reported as redundant")
    void aBridgeOnABeanWireIsRedundantNotWrong() {
      JavaFileObject bean =
          JavaFileObjects.forSourceString(
              "com.example.CustomerBean",
              """
              package com.example;

              public class CustomerBean {
                private String name;
                private String nickname;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getNickname() { return nickname; }
                public void setNickname(String nickname) { this.nickname = nickname; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerBeanMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerBeanMapping extends MappingSpec<Customer, CustomerBean> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, bean, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining("@OptionalBridge on 'nickname' is redundant on a bean wire");
      // the bean's own conditional write is unchanged: an empty Optional leaves the property unset
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerBeanMappingImpl"))
          .contains("domain.nickname().ifPresent(v -> wire.setNickname(v))");
    }

    @Test
    @DisplayName("an inherited bridge on a bean wire is silent: shared vocabulary serves both")
    void anInheritedBridgeOnABeanWireIsSilent() {
      JavaFileObject bean =
          JavaFileObjects.forSourceString(
              "com.example.CustomerBean",
              """
              package com.example;

              public class CustomerBean {
                private String name;
                private String nickname;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getNickname() { return nickname; }
                public void setNickname(String nickname) { this.nickname = nickname; }
              }
              """);
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.Nicknames",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public interface Nicknames {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerBeanMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerBeanMapping
                  extends Nicknames, MappingSpec<Customer, CustomerBean> {}
              """);
      Compilation compilation = compile(CUSTOMER, bean, vocabulary, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(compilation.diagnostics())
          .noneMatch(d -> d.toString().contains("redundant"));
    }

    @Test
    @DisplayName("a bridge has no meaning on a sealed mapping")
    void aBridgeOnASealedMappingIsRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Shape",
              """
              package com.example;

              public sealed interface Shape permits Circle {}
              """);
      JavaFileObject circle =
          JavaFileObjects.forSourceString(
              "com.example.Circle",
              """
              package com.example;

              public record Circle(double radius) implements Shape {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ShapeDto",
              """
              package com.example;

              public sealed interface ShapeDto permits CircleDto {}
              """);
      JavaFileObject circleDto =
          JavaFileObjects.forSourceString(
              "com.example.CircleDto",
              """
              package com.example;

              public record CircleDto(double radius) implements ShapeDto {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ShapeMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface ShapeMapping extends MappingSpec<Shape, ShapeDto> {
                @OptionalBridge
                Optional<String> label();
              }
              """);
      Compilation compilation = compile(domain, circle, wire, circleDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'label' has no meaning on a sealed mapping");
    }

    @Test
    @DisplayName("a bridge has no meaning on a sparse UpdateSpec: null already means absent")
    void aBridgeOnASparseUpdateIsRejected() {
      JavaFileObject bean =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchDto",
              """
              package com.example;

              public class CustomerPatchDto {
                private String name;
                private String nickname;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getNickname() { return nickname; }
                public void setNickname(String nickname) { this.nickname = nickname; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.OptionalBridge;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface CustomerPatchMapping
                  extends UpdateSpec<Customer, CustomerPatchDto> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, bean, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no meaning on a sparse UpdateSpec");
      assertThat(compilation).hadErrorContaining("declare the PATCH property as Optional<T>");
    }

    @Test
    @DisplayName("@OptionalBridge on a default method that is not a leaf is rejected")
    void aBridgeOnANonLeafDefaultIsRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                default Optional<String> nickname() {
                  return Optional.empty();
                }
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge method 'nickname' is neither a marker nor a leaf");
    }

    @Test
    @DisplayName("@OptionalBridge on a method with parameters is rejected")
    void aBridgeWithParametersIsRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                Optional<String> nickname(int unused);
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge method 'nickname' must not declare parameters");
    }

    @Test
    @DisplayName("a marker returning something other than the component's own type is rejected")
    void aMarkerMustRestateTheComponentType() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                String nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@OptionalBridge marker 'nickname' returns java.lang.String, not the component's own"
                  + " type");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the marker as 'java.util.Optional<java.lang.String> nickname()'");
    }

    @Test
    @DisplayName("a marker naming a type the Impl's package cannot reach is rejected")
    void aMarkerNamingAnUnreachableTypeIsRejected() {
      JavaFileObject outer =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public class Outer {
                private record Secret(String value) {}

                public record OuterDto(String name, String nickname) {}

                @GenerateMapping
                public interface Spec extends MappingSpec<Customer, OuterDto> {
                  @OptionalBridge
                  Optional<Secret> nickname();
                }
              }
              """);
      Compilation compilation = compile(CUSTOMER, outer);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@OptionalBridge marker 'nickname' names 'Secret', which cannot be reached from"
                  + " 'com.example'.");
    }

    @Test
    @DisplayName("a bridge on a component the projection does not carry is inert")
    void aBridgeOnAnUnprojectedComponentIsInert() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              import java.util.Optional;

              public record Customer(String id, String name, Optional<String> nickname) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerNameDto",
              """
              package com.example;

              public record CustomerNameDto(String name) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerNameMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerNameMapping
                  extends MappingSpec<Customer, CustomerNameDto> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      // nothing to bridge: the projection never reads 'nickname', so the tier is unaffected
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerNameMappingImpl"))
          .contains("asLens");
    }

    @Test
    @DisplayName("an element-mapped generic spec bridges through its supplied prism")
    void aGenericElementMappedSpecBridges() {
      JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              import java.util.Optional;

              public record Box<T>(String tag, Optional<T> item) {}
              """);
      JavaFileObject boxDto =
          JavaFileObjects.forSourceString(
              "com.example.BoxDto",
              """
              package com.example;

              public record BoxDto<D>(String tag, D item) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoxMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface BoxMapping<T, D> extends MappingSpec<Box<T>, BoxDto<D>> {
                @OptionalBridge
                ValidatedPrism<D, T> item();
              }
              """);
      Compilation compilation = compile(box, boxDto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BoxMappingImpl"))
          .contains("domain.item().map(item()::build).orElse(null)")
          .contains("Optional.ofNullable(wire.item())");
    }

    @Test
    @DisplayName("a raw mix-in carrying only a bridge is refused like any erased vocabulary")
    void aRawMixinCarryingOnlyABridgeIsRefused() {
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.Nicknames",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public interface Nicknames<T> {
                @OptionalBridge
                Optional<T> nickname();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              @SuppressWarnings("rawtypes")
              public interface CustomerMapping
                  extends MappingSpec<Customer, CustomerDto>, Nicknames {}
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, vocabulary, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("mix-in 'Nicknames' is extended raw");
    }

    @Test
    @DisplayName("a primitive wire component is never offered the bridge in the refusal")
    void aPrimitiveWireComponentIsNotOfferedTheBridge() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Reading",
              """
              package com.example;

              import java.util.Optional;

              public record Reading(String id, Optional<Integer> value) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.ReadingDto",
              """
              package com.example;

              public record ReadingDto(String id, int value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ReadingMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReadingMapping extends MappingSpec<Reading, ReadingDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no usable source");
      Assertions.assertThat(compilation.diagnostics())
          .noneMatch(d -> d.toString().contains("@OptionalBridge"));
    }

    @Test
    @DisplayName("a same-typed element leaf still wins over the bridge's identity copy")
    void aSameTypedElementLeafBeatsTheIdentityBridge() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                default ValidatedPrism<String, String> nickname() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.isBlank()
                              ? Validated.invalidNel(FieldError.of("must not be blank"))
                              : Validated.validNel(raw),
                      value -> value);
                }
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).succeeded();
      // the declared prism runs on the present value; the identity copy would have skipped it
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("domain.nickname().map(nickname()::build).orElse(null)")
          .contains("nickname().parse(v)");
    }

    @Test
    @DisplayName("a bridged leaf declared over the whole Optional is rejected, never inert")
    void aBridgedLeafOverTheWholeOptionalIsRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                default ValidatedPrism<String, Optional<String>> nickname() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Optional.of(raw)), o -> o.orElse(""));
                }
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@OptionalBridge leaf 'nickname' is declared over the whole Optional");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the leaf as 'ValidatedPrism<java.lang.String, java.lang.String>'");
    }

    @Test
    @DisplayName("a wildcard element bridges on its bound rather than dead-ending")
    void aWildcardElementBridgesOnItsBound() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Reading",
              """
              package com.example;

              import java.util.Optional;

              public record Reading(String id, Optional<? extends Number> value) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.ReadingDto",
              """
              package com.example;

              public record ReadingDto(String id, Number value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ReadingMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface ReadingMapping extends MappingSpec<Reading, ReadingDto> {
                @OptionalBridge
                Optional<? extends Number> value();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.ReadingMappingImpl"))
          .contains("domain.value().orElse(null)")
          .contains("Validated.validNel(Optional.ofNullable(wire.value()))");
    }

    @Test
    @DisplayName("a bridged List keeps the located-null scan its unbridged twin carries")
    void aBridgedContainerKeepsTheNullScan() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Album",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;

              public record Album(String name, Optional<List<String>> tags,
                  Optional<Map<String, String>> meta) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.AlbumDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public record AlbumDto(String name, List<String> tags, Map<String, String> meta) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AlbumMapping",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface AlbumMapping extends MappingSpec<Album, AlbumDto> {
                @OptionalBridge
                Optional<List<String>> tags();

                @OptionalBridge
                Optional<Map<String, String>> meta();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AlbumMappingImpl"))
          .contains("hkj$allPresent(v)")
          .contains("hkj$valuesPresent(v)");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.AlbumMappingImpl");
      var dtoCtor =
          result
              .loadClass("com.example.AlbumDto")
              .getDeclaredConstructor(String.class, List.class, Map.class);

      // absence is the only thing the bridge excuses: a present list holding a null still locates
      List<String> withNull = new ArrayList<>();
      withNull.add("jazz");
      withNull.add(null);
      Validated<NonEmptyList<FieldError>, Object> parsed =
          parse(impl, dtoCtor.newInstance("Blue", withNull, Map.of()));
      assertThatValidated(parsed).isInvalid().hasFieldErrors("tags.1: must not be null");

      // and an absent one is still valid emptiness
      Assertions.assertThat(parse(impl, dtoCtor.newInstance("Blue", null, null)).isValid())
          .isTrue();
    }

    @Test
    @DisplayName("a raw Optional component is refused for the reason that is actually true")
    void aRawOptionalComponentIsRefusedTruthfully() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Raw",
              """
              package com.example;

              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public record Raw(String name, Optional nickname) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RawMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              @SuppressWarnings("rawtypes")
              public interface RawMapping extends MappingSpec<Raw, CustomerDto> {
                @OptionalBridge
                Optional nickname();
              }
              """);
      Compilation compilation = compile(domain, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'nickname' names a raw Optional component");
      assertThat(compilation)
          .hadErrorContaining("Declare the type argument, for example Optional<String>");
    }

    @Test
    @DisplayName("an unbounded wildcard element bridges on Object, which it stands for")
    void anUnboundedWildcardElementBridgesOnObject() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Slot",
              """
              package com.example;

              import java.util.Optional;

              public record Slot(String id, Optional<? super String> value) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.SlotDto",
              """
              package com.example;

              public record SlotDto(String id, Object value) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SlotMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface SlotMapping extends MappingSpec<Slot, SlotDto> {
                @OptionalBridge
                Optional<? super String> value();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.SlotMappingImpl"))
          .contains("domain.value().orElse(null)");
    }

    @Test
    @DisplayName("a marker declaring its own type parameters is refused as a marker, not a leaf")
    void aMarkerDeclaringTypeParametersIsRefused() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
                @OptionalBridge
                <T> Optional<T> nickname();
              }
              """);
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("abstract method 'nickname' declares type parameters of its own");
      // the remedy is the marker's, not the element-mapped leaf's
      assertThat(compilation)
          .hadErrorContaining(
              "Give 'nickname' a concrete return type; a marker method declares a correspondence");
    }

    @Test
    @DisplayName("an inherited bridge is inert on a sparse UpdateSpec: one mix-in serves both")
    void anInheritedBridgeIsInertOnASparseUpdate() {
      JavaFileObject bean =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchDto",
              """
              package com.example;

              import java.util.Optional;

              public class CustomerPatchDto {
                private String name;
                // The sparse tier's own encoding for 'set to empty': an Optional-typed property,
                // defaulting to null so an omitted field still means 'leave unchanged'.
                private Optional<String> nickname;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Optional<String> getNickname() { return nickname; }
                public void setNickname(Optional<String> nickname) { this.nickname = nickname; }
              }
              """);
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.Nicknames",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              public interface Nicknames {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      JavaFileObject dense =
          JavaFileObjects.forSourceString(
              "com.example.CustomerMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CustomerMapping
                  extends Nicknames, MappingSpec<Customer, CustomerDto> {}
              """);
      JavaFileObject sparse =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface CustomerPatchMapping
                  extends Nicknames, UpdateSpec<Customer, CustomerPatchDto> {}
              """);
      // the one vocabulary serves the record spec and its PATCH sibling, as the book promises
      Compilation compilation = compile(CUSTOMER, CUSTOMER_DTO, bean, vocabulary, dense, sparse);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("domain.nickname().orElse(null)");
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerPatchMappingImpl"))
          .contains("updateFrom");
    }

    @Test
    @DisplayName("a projection whose only fallible leg is a bridge emits no guard helper")
    void aBridgeOnlyProjectionEmitsNoGuard() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              import java.util.Optional;

              public record Customer(String id, String name, Optional<String> nickname) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerAliasDto",
              """
              package com.example;

              public record CustomerAliasDto(String nickname) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CustomerAliasMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;

              @GenerateMapping
              public interface CustomerAliasMapping
                  extends MappingSpec<Customer, CustomerAliasDto> {
                @OptionalBridge
                Optional<String> nickname();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      // the bridge is fallible for tier selection, but reads its own null as absence, so the
      // patch tier it selects carries no guarded read to emit a helper for
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerAliasMappingImpl"))
          .contains("public Validated<NonEmptyList<FieldError>, Customer> patch(")
          .doesNotContain("hkj$ifPresent");
    }

    @Test
    @DisplayName("an abstract bridged leaf over the whole Optional is refused too")
    void anAbstractBridgedLeafOverTheWholeOptionalIsRejected() {
      JavaFileObject box =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              import java.util.Optional;

              public record Box<T>(String tag, Optional<T> item) {}
              """);
      JavaFileObject boxDto =
          JavaFileObjects.forSourceString(
              "com.example.BoxDto",
              """
              package com.example;

              public record BoxDto<D>(String tag, D item) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoxMapping",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.OptionalBridge;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface BoxMapping<T, D> extends MappingSpec<Box<T>, BoxDto<D>> {
                // an element-mapped leaf, but declared over the Optional rather than its element
                @OptionalBridge
                ValidatedPrism<D, Optional<T>> item();
              }
              """);
      Compilation compilation = compile(box, boxDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge leaf 'item' is declared over the whole Optional");
    }

    private static JavaFileObject plainUser() {
      return JavaFileObjects.forSourceString(
          "com.example.User",
          """
          package com.example;

          public record User(String name, String nickname) {}
          """);
    }

    private static JavaFileObject plainUserDto() {
      return JavaFileObjects.forSourceString(
          "com.example.UserDto",
          """
          package com.example;

          public record UserDto(String name, String nickname) {}
          """);
    }
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    return compilation.generatedSourceFiles().stream()
        .filter(f -> f.getName().contains(qualifiedName.replace('.', '/')))
        .findFirst()
        .map(
            f -> {
              try {
                return f.getCharContent(true).toString();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
            })
        .orElseThrow(() -> new AssertionError("generated source not found: " + qualifiedName));
  }
}

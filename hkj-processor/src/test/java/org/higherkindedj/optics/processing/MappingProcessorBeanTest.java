// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MappingProcessor - bean-shaped wire targets")
class MappingProcessorBeanTest {

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.example.EmailAddress",
          """
          package com.example;

          public record EmailAddress(String value) {}
          """);

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
  }

  /**
   * Compiles under the lints a user's own build may run with, so a generated write that is merely
   * unchecked - not a hard error - is still caught.
   */
  private Compilation compileLinted(JavaFileObject... sources) {
    return javac()
        .withProcessors(new MappingProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  @Nested
  @DisplayName("Mutable JavaBean full tier")
  class MutableFullTier {

    private static final JavaFileObject USER =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            public record User(String name, EmailAddress email, int age) {}
            """);

    // A mutable getter/setter bean: not a record, not annotatable.
    private static final JavaFileObject USER_DTO =
        JavaFileObjects.forSourceString(
            "com.example.UserDto",
            """
            package com.example;

            public class UserDto {
              private String name;
              private String email;
              private int age;

              public String getName() { return name; }
              public void setName(String name) { this.name = name; }
              public String getEmail() { return email; }
              public void setEmail(String email) { this.email = email; }
              public int getAge() { return age; }
              public void setAge(int age) { this.age = age; }
            }
            """);

    private static final JavaFileObject USER_MAPPING =
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

    @Test
    @DisplayName("build fills via setters, parse reads via getters null-guarded, no asIso")
    void buildViaSettersParseGuarded() {
      Compilation compilation = compile(EMAIL, USER, USER_DTO, USER_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.UserMappingImpl");
      Assertions.assertThat(generated)
          .contains("UserDto wire = new UserDto();")
          .contains("wire.setName(domain.name());")
          .contains("wire.setEmail(email().build(domain.email()));")
          .contains("wire.setAge(domain.age());")
          .contains("return wire;")
          .contains(".field(\"name\", hkj$ifPresent(wire.getName(), Validated::validNel))")
          .contains(".field(\"email\", hkj$ifPresent(wire.getEmail(), email()::parse))")
          .contains(".field(\"age\", Validated.validNel(wire.getAge()))")
          .contains("private static <S, A> Validated<NonEmptyList<FieldError>, A> hkj$ifPresent(")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("round-trips a domain value through the bean")
    void roundTrips() {
      Compilation compilation = compile(EMAIL, USER, USER_DTO, USER_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.UserMappingImpl");
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object user = result.newInstance("com.example.User", "Ada", email, 42);

        Object dto = invoke(impl, "build", user);
        Assertions.assertThat(invoke(dto, "getName")).isEqualTo("Ada");
        Assertions.assertThat(invoke(dto, "getEmail")).isEqualTo("ada@corp.example");
        Assertions.assertThat(invoke(dto, "getAge")).isEqualTo(42);

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.isValid()).isTrue();
        Assertions.assertThat(parsed.get()).isEqualTo(user);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "a null property parses to one located FieldError, accumulating, never hitting a leaf")
    void nullPropertyLocatedAndAccumulated() {
      Compilation compilation = compile(EMAIL, USER, USER_DTO, USER_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.UserMappingImpl");
        // name null and email null: both located, and the null email never reaches email().parse
        // (which would throw), proving the guard runs first.
        Object dto = result.loadClass("com.example.UserDto").getDeclaredConstructor().newInstance();

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("name"), "must not be null"),
                new FieldError(List.of("email"), "must not be null"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a generic domain is refused on a bean wire, even instantiated")
    void instantiatedGenericDomainRejectedOnBeanWire() {
      JavaFileObject page =
          JavaFileObjects.forSourceString(
              "com.example.Page",
              """
              package com.example;

              import java.util.List;

              public record Page<T>(List<T> items, int total) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PageBeanMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PageBeanMapping extends MappingSpec<Page<User>, UserDto> {}
              """);

      Compilation compilation = compile(EMAIL, USER, USER_DTO, page, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'Page' is generic, which a bean-wire mapping does not support yet.");
      // an instantiation is refused too, so the fix asks for no type parameters at all, and
      // points at the record-to-record mapping that may be generic
      assertThat(compilation)
          .hadErrorContaining(
              "Declare the spec and the types it maps without type parameters; a"
                  + " record-to-record mapping may use generic types, either concretely"
                  + " instantiated or threaded through the spec's type parameters.");
    }

    @Test
    @DisplayName(
        "a user 'ifPresent' helper stays legal beside the $-namespaced guard and never"
            + " captures its calls")
    void ifPresentHelperStaysLegalBesideTheGuard() {
      // The generic shape mirrors the guard's own; the String one would be more specific than
      // the guard for every String property read, so it would capture an unqualified call. Both
      // are booby-trapped: if generated code ever routed through them, the null-name parse
      // below would not report the guard's located FieldError.
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserMapping",
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
              public interface UserMapping extends MappingSpec<User, UserDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                default <S, A> Validated<NonEmptyList<FieldError>, A> ifPresent(
                    S value, Function<? super S, Validated<NonEmptyList<FieldError>, A>> parse) {
                  return Validated.invalidNel(FieldError.of("captured by the generic helper"));
                }

                default Validated<NonEmptyList<FieldError>, String> ifPresent(
                    String value,
                    Function<? super String, Validated<NonEmptyList<FieldError>, String>> parse) {
                  return Validated.invalidNel(FieldError.of("captured by the String helper"));
                }
              }
              """);

      Compilation compilation = compile(EMAIL, USER, USER_DTO, spec);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.UserMappingImpl");
        Object dto = result.loadClass("com.example.UserDto").getDeclaredConstructor().newInstance();
        invoke(dto, "setEmail", "ada@corp.example");
        invoke(dto, "setAge", 42);

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("name"), "must not be null"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  @DisplayName("Detection")
  class Detection {

    @Test
    @DisplayName("a boolean isX getter is a property; an all-primitive bean gains asIso")
    void booleanIsGetterAndAllPrimitiveIso() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Flag",
              """
              package com.example;

              public record Flag(boolean active, int level) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.FlagDto",
              """
              package com.example;

              public class FlagDto {
                private boolean active;
                private int level;

                public boolean isActive() { return active; }
                public void setActive(boolean active) { this.active = active; }
                public int getLevel() { return level; }
                public void setLevel(int level) { this.level = level; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.FlagMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface FlagMapping extends MappingSpec<Flag, FlagDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.FlagMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setActive(domain.active());")
          .contains("wire.setLevel(domain.level());")
          .contains(".field(\"active\", Validated.validNel(wire.isActive()))")
          .contains(".field(\"level\", Validated.validNel(wire.getLevel()))")
          .contains("public Iso<Flag, FlagDto> asIso()")
          .contains("Iso.of(this::build, wire -> new Flag(wire.isActive(), wire.getLevel()))")
          .doesNotContain("ifPresent");
    }

    @Test
    @DisplayName("getters and setters inherited from a base class are properties")
    void inheritedAccessors() {
      JavaFileObject base =
          JavaFileObjects.forSourceString(
              "com.example.NamedBase",
              """
              package com.example;

              public class NamedBase {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Employee",
              """
              package com.example;

              public record Employee(String name, String role) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeDto",
              """
              package com.example;

              public class EmployeeDto extends NamedBase {
                private String role;
                public String getRole() { return role; }
                public void setRole(String role) { this.role = role; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface EmployeeMapping extends MappingSpec<Employee, EmployeeDto> {}
              """);

      Compilation compilation = compile(base, domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.EmployeeMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setName(domain.name());")
          .contains("wire.setRole(domain.role());")
          .contains("hkj$ifPresent(wire.getName(), Validated::validNel)")
          .contains("hkj$ifPresent(wire.getRole(), Validated::validNel)");
    }
  }

  @Nested
  @DisplayName("Features carried over")
  class Features {

    @Test
    @DisplayName("@MapField renames a domain component to an all-caps bean property (decapitalise)")
    void renameToAllCapsProperty() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Site",
              """
              package com.example;

              public record Site(String link) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.SiteDto",
              """
              package com.example;

              public class SiteDto {
                private String url;
                public String getURL() { return url; }
                public void setURL(String url) { this.url = url; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SiteMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SiteMapping extends MappingSpec<Site, SiteDto> {
                @MapField(to = "URL")
                String link();
              }
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.SiteMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setURL(domain.link());")
          .contains(".field(\"link\", hkj$ifPresent(wire.getURL(), Validated::validNel))");
    }

    @Test
    @DisplayName("a derived field fills a bean property via a Getter; parse ignores it")
    void derivedFieldOnBean() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Person",
              """
              package com.example;

              public record Person(String first, String last) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.PersonDto",
              """
              package com.example;

              public class PersonDto {
                private String first;
                private String last;
                private String displayName;
                public String getFirst() { return first; }
                public void setFirst(String first) { this.first = first; }
                public String getLast() { return last; }
                public void setLast(String last) { this.last = last; }
                public String getDisplayName() { return displayName; }
                public void setDisplayName(String displayName) { this.displayName = displayName; }
              }
              """);
      JavaFileObject spec =
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

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.PersonMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setDisplayName(displayName().get(domain));")
          .contains(".field(\"first\", hkj$ifPresent(wire.getFirst(), Validated::validNel))")
          .doesNotContain(".field(\"displayName\"")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("List, Optional and Map bean properties lift through element leaves, guarded")
    void containerLiftingOnBean() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Contacts",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;

              public record Contacts(
                  List<EmailAddress> all,
                  Optional<EmailAddress> primary,
                  Map<String, EmailAddress> tagged) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ContactsDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;

              public class ContactsDto {
                private List<String> all;
                private Optional<String> primary;
                private Map<String, String> tagged;
                public List<String> getAll() { return all; }
                public void setAll(List<String> all) { this.all = all; }
                public Optional<String> getPrimary() { return primary; }
                public void setPrimary(Optional<String> primary) { this.primary = primary; }
                public Map<String, String> getTagged() { return tagged; }
                public void setTagged(Map<String, String> tagged) { this.tagged = tagged; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ContactsMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ContactsMapping extends MappingSpec<Contacts, ContactsDto> {
                default ValidatedPrism<String, EmailAddress> all() { return prism(); }
                default ValidatedPrism<String, EmailAddress> primary() { return prism(); }
                default ValidatedPrism<String, EmailAddress> tagged() { return prism(); }

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
      String generated = generatedSource(compilation, "com.example.ContactsMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setAll(all().buildAll(domain.all()));")
          .contains("wire.setTagged(tagged().buildValues(domain.tagged()));")
          .contains("wire.setPrimary(domain.primary().map(primary()::build));")
          .contains(".field(\"all\", hkj$ifPresent(wire.getAll(), all()::parseAll))")
          .contains(".field(\"tagged\", hkj$ifPresent(wire.getTagged(), tagged()::parseValues))")
          .contains(".field(\"primary\", hkj$ifPresent(wire.getPrimary(), o -> o.map(v ->");
    }

    @Test
    @DisplayName("a record spec nests a bean spec through its asValidatedPrism")
    void nestedBeanSpecInsideRecord() {
      JavaFileObject customer =
          JavaFileObjects.forSourceString(
              "com.example.Customer",
              """
              package com.example;

              public record Customer(String name, EmailAddress email) {}
              """);
      JavaFileObject customerDto =
          JavaFileObjects.forSourceString(
              "com.example.CustomerDto",
              """
              package com.example;

              public class CustomerDto {
                private String name;
                private String email;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              """);
      JavaFileObject customerMapping =
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
          compile(EMAIL, customer, customerDto, customerMapping, order, orderDto, orderMapping);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.OrderMappingImpl");
      Assertions.assertThat(generated)
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism().build(domain.customer())")
          .contains(
              ".field(\"customer\", hkj$ifPresent(wire.customer(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }
  }

  @Nested
  @DisplayName("Builder and collection strategies")
  class BuilderAndCollection {

    @Test
    @DisplayName("a Lombok-style bean builds through builder() and property-named setters")
    void lombokStyleBuilder() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Point",
              """
              package com.example;

              public record Point(int x, String label) {}
              """);
      // An immutable bean: no setters, no no-args constructor, only a builder.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.PointDto",
              """
              package com.example;

              public final class PointDto {
                private final int x;
                private final String label;
                private PointDto(int x, String label) { this.x = x; this.label = label; }
                public int getX() { return x; }
                public String getLabel() { return label; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  private int x;
                  private String label;
                  public Builder x(int x) { this.x = x; return this; }
                  public Builder label(String label) { this.label = label; return this; }
                  public PointDto build() { return new PointDto(x, label); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PointMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PointMapping extends MappingSpec<Point, PointDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.PointMappingImpl");
      Assertions.assertThat(generated)
          .contains("var b = PointDto.builder();")
          .contains("b.x(domain.x());")
          .contains("b.label(domain.label());")
          .contains("return b.build();")
          .contains(".field(\"label\", hkj$ifPresent(wire.getLabel(), Validated::validNel))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.PointMappingImpl");
        Object point = result.newInstance("com.example.Point", 3, "origin");
        Object dto = invoke(impl, "build", point);
        Assertions.assertThat(invoke(dto, "getX")).isEqualTo(3);
        Assertions.assertThat(invoke(dto, "getLabel")).isEqualTo("origin");

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.get()).isEqualTo(point);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a newBuilder() bean builds through its setX builder setters")
    void protobufStyleBuilder() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Msg",
              """
              package com.example;

              public record Msg(String subject) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.MsgProto",
              """
              package com.example;

              public final class MsgProto {
                private final String subject;
                private MsgProto(String subject) { this.subject = subject; }
                public String getSubject() { return subject; }
                public static Builder newBuilder() { return new Builder(); }
                public static final class Builder {
                  private String subject;
                  public Builder setSubject(String subject) { this.subject = subject; return this; }
                  public MsgProto build() { return new MsgProto(subject); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.MsgMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface MsgMapping extends MappingSpec<Msg, MsgProto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.MsgMappingImpl");
      Assertions.assertThat(generated)
          .contains("var b = MsgProto.newBuilder();")
          .contains("b.setSubject(domain.subject());")
          .contains("return b.build();");
    }

    @Test
    @DisplayName("a JAXB-style getter-only List is filled via getX().addAll(...)")
    void jaxbCollectionGetter() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Doc",
              """
              package com.example;

              import java.util.List;

              public record Doc(String title, List<String> tags) {}
              """);
      // JAXB convention: the List has a getter that lazily returns a live mutable list, no setter.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.DocDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;

              public class DocDto {
                private String title;
                private List<String> tags;
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public List<String> getTags() {
                  if (tags == null) { tags = new ArrayList<>(); }
                  return tags;
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DocMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface DocMapping extends MappingSpec<Doc, DocDto> {}
              """);

      Compilation compilation = compileLinted(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.DocMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setTitle(domain.title());")
          .contains("wire.getTags().addAll(hkj$copyOf(domain.tags()));")
          .contains(".field(\"tags\", hkj$allPresent(hkj$copyOf(wire.getTags())))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.DocMappingImpl");
        Object doc =
            result
                .loadClass("com.example.Doc")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("spec", List.of("a", "b"));
        Object dto = invoke(impl, "build", doc);
        Assertions.assertThat((List<?>) invoke(dto, "getTags")).isEqualTo(List.of("a", "b"));

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.get()).isEqualTo(doc);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    /** A getter-only list property of the given type, with no setter. */
    private static JavaFileObject listGetterOnly(String listType) {
      return JavaFileObjects.forSourceString(
          "com.example.DocDto",
          """
          package com.example;

          import java.util.ArrayList;
          import java.util.List;

          @SuppressWarnings("rawtypes")
          public class DocDto {
            private String title;
            private %1$s tags;
            public String getTitle() { return title; }
            public void setTitle(String title) { this.title = title; }
            public %1$s getTags() {
              if (tags == null) { tags = new ArrayList<>(); }
              return tags;
            }
          }
          """
              .formatted(listType));
    }

    /** A domain record whose {@code tags} component has the given type. */
    private static JavaFileObject docWith(String listType) {
      return JavaFileObjects.forSourceString(
          "com.example.Doc",
          """
          package com.example;

          import java.util.List;

          @SuppressWarnings("rawtypes")
          public record Doc(String title, %s tags) {}
          """
              .formatted(listType));
    }

    private static JavaFileObject docMapping(String wire) {
      return JavaFileObjects.forSourceString(
          "com.example.DocMapping",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface DocMapping extends MappingSpec<Doc, %s> {}
          """
              .formatted(wire));
    }

    @Test
    @DisplayName(
        "a raw getter-only List is refused, and told to name its element type: addAll cannot be"
            + " written over a raw receiver")
    void rawGetterOnlyListRejected() {
      Compilation compilation =
          compile(docWith("List"), listGetterOnly("List"), docMapping("DocDto"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("is a getter-only List, which a build cannot fill");
      assertThat(compilation).hadErrorContaining("a raw List names none, so the call is unchecked");
      assertThat(compilation)
          .hadErrorContaining("Declare the type arguments on 'getTags()', for example List<T>");
    }

    @Test
    @DisplayName(
        "a wildcard getter-only List is refused on its own terms: it has an argument already, so it"
            + " is told to replace the wildcard, not to add one")
    void wildcardGetterOnlyListRejected() {
      Compilation compilation =
          compile(
              docWith("List<? extends CharSequence>"),
              listGetterOnly("List<? extends CharSequence>"),
              docMapping("DocDto"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "is a getter-only List<? extends CharSequence>, which a build cannot");
      assertThat(compilation)
          .hadErrorContaining("a wildcard argument is a fresh type at each mention");
      assertThat(compilation)
          .hadErrorContaining("Replace the wildcard on 'getTags()' with the element type");
      Assertions.assertThat(compilation.errors())
          .as("a wildcard author already has an argument, so must not be told to add one")
          .noneMatch(error -> error.getMessage(null).contains("Declare the type arguments"));
    }

    @Test
    @DisplayName(
        "each tier refuses the getter-only List for its own reason: the dense one cannot fill it,"
            + " the sparse one cannot read absence from it, and neither message reaches the other")
    void eachTierRefusesTheGetterOnlyListForItsOwnReason() {
      JavaFileObject sparse =
          JavaFileObjects.forSourceString(
              "com.example.DocPatch",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface DocPatch extends UpdateSpec<Doc, DocDto> {}
              """);
      for (String listType : List.of("List", "List<? extends CharSequence>")) {
        Compilation dense =
            compile(docWith(listType), listGetterOnly(listType), docMapping("DocDto"));
        assertThat(dense).hadErrorContaining("which a build cannot fill");
        Assertions.assertThat(dense.errors())
            .as("%s: the dense tier writes the property, so absence is not its complaint", listType)
            .noneMatch(error -> error.getMessage(null).contains("carry a sparse update's absence"));

        Compilation update = compile(docWith(listType), listGetterOnly(listType), sparse);
        assertThat(update).hadErrorContaining("cannot carry a sparse update's absence");
        Assertions.assertThat(update.errors())
            .as(
                "%s: the sparse tier never writes the property, so filling is not its complaint",
                listType)
            .noneMatch(error -> error.getMessage(null).contains("a build cannot fill"));
      }
    }

    @Test
    @DisplayName("a projection onto the same getter-only List is refused on the same terms")
    void getterOnlyListWithoutAnElementTypeRejectedOnProjection() {
      JavaFileObject projection =
          JavaFileObjects.forSourceString(
              "com.example.DocProjectionDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;

              @SuppressWarnings("rawtypes")
              public class DocProjectionDto {
                private List tags;
                public List getTags() {
                  if (tags == null) { tags = new ArrayList<>(); }
                  return tags;
                }
              }
              """);
      Compilation compilation =
          compile(docWith("List"), projection, docMapping("DocProjectionDto"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("is a getter-only List, which a build cannot fill");
    }

    @Test
    @DisplayName("a setter takes the same raw and wildcard properties, which is the second fix")
    void aSetterTakesWhatTheCollectionGetterCannot() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.DocDto",
              """
              package com.example;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public class DocDto {
                private String title;
                private List tags;
                private List<? extends CharSequence> more;
                public String getTitle() { return title; }
                public void setTitle(String title) { this.title = title; }
                public List getTags() { return tags; }
                public void setTags(List tags) { this.tags = tags; }
                public List<? extends CharSequence> getMore() { return more; }
                public void setMore(List<? extends CharSequence> more) { this.more = more; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Doc",
              """
              package com.example;

              import java.util.List;

              @SuppressWarnings("rawtypes")
              public record Doc(String title, List tags, List<? extends CharSequence> more) {}
              """);
      Compilation compilation = compileLinted(domain, wire, docMapping("DocDto"));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.DocMappingImpl"))
          .contains("wire.setTags(hkj$copyOf((List<?>) domain.tags()));")
          .contains("wire.setMore(hkj$copyOf(domain.more()));");
    }

    @Test
    @DisplayName("a fluent setter (returning the bean) is accepted")
    void fluentSetter() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Tag",
              """
              package com.example;

              public record Tag(String value) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.TagDto",
              """
              package com.example;

              public class TagDto {
                private String value;
                public String getValue() { return value; }
                public TagDto setValue(String value) { this.value = value; return this; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TagMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TagMapping extends MappingSpec<Tag, TagDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TagMappingImpl");
      Assertions.assertThat(generated).contains("wire.setValue(domain.value());");
    }
  }

  @Nested
  @DisplayName("Overloaded writers")
  class OverloadedWriters {

    private static final JavaFileObject PERSON =
        JavaFileObjects.forSourceString(
            "com.example.Person",
            """
            package com.example;

            public record Person(int age) {}
            """);

    private static final JavaFileObject PERSON_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.PersonMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PersonMapping extends MappingSpec<Person, PersonDto> {}
            """);

    private static final String INT_SETTER = "public void setAge(int age) { this.age = age; }";

    // Takes the property at another type, so pairing it would refuse the bean as read and written
    // at different types.
    private static final String STRING_SETTER = "public void setAge(String age) { this.age = -1; }";

    private static JavaFileObject personDto(String first, String second) {
      return JavaFileObjects.forSourceString(
          "com.example.PersonDto",
          """
          package com.example;

          public class PersonDto {
            private int age;
            public int getAge() { return age; }
            %s
            %s
          }
          """
              .formatted(first, second));
    }

    @Test
    @DisplayName("a setter overloaded at another type pairs by the getter's type, in either order")
    void setterOverloads() throws ReflectiveOperationException {
      for (JavaFileObject wire :
          List.of(personDto(STRING_SETTER, INT_SETTER), personDto(INT_SETTER, STRING_SETTER))) {
        Compilation compilation = compile(PERSON, wire, PERSON_MAPPING);
        assertThat(compilation).succeeded();
        var result = new RuntimeCompilationHelper.CompiledResult(compilation);
        Object impl = result.instance("com.example.PersonMappingImpl");
        Object person = result.newInstance("com.example.Person", 42);
        Object dto = invoke(impl, "build", person);
        Assertions.assertThat(invoke(dto, "getAge")).isEqualTo(42);
        assertThatValidated(validated(invoke(impl, "parse", dto))).isValid().hasValue(person);
      }
    }

    private static final JavaFileObject STAMP =
        JavaFileObjects.forSourceString(
            "com.example.Stamp",
            """
            package com.example;

            public final class Stamp {
              public static final class Builder {
                public Stamp build() { return new Stamp(); }
              }
            }
            """);

    private static final JavaFileObject CARD =
        JavaFileObjects.forSourceString(
            "com.example.Card",
            """
            package com.example;

            public record Card(Stamp stamp) {}
            """);

    private static final JavaFileObject CARD_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CardMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CardMapping extends MappingSpec<Card, CardMessage> {}
            """);

    private static final String VALUE_SETTER =
        "public Builder setStamp(Stamp stamp) { this.stamp = stamp; return this; }";

    private static final String BUILDER_SETTER =
        "public Builder setStamp(Stamp.Builder stamp) { this.stamp = stamp.build(); return this; }";

    // A message field, as protobuf generates it: its builder takes the value or a builder for it.
    private static JavaFileObject cardMessage(String first, String second) {
      return JavaFileObjects.forSourceString(
          "com.example.CardMessage",
          """
          package com.example;

          public final class CardMessage {
            private final Stamp stamp;
            private CardMessage(Stamp stamp) { this.stamp = stamp; }
            public Stamp getStamp() { return stamp; }
            public static Builder newBuilder() { return new Builder(); }
            public static final class Builder {
              private Stamp stamp;
              %s
              %s
              public CardMessage build() { return new CardMessage(stamp); }
            }
          }
          """
              .formatted(first, second));
    }

    @Test
    @DisplayName("a builder setter overloaded at another type pairs by the getter's type, too")
    void builderSetterOverloads() throws ReflectiveOperationException {
      for (JavaFileObject wire :
          List.of(
              cardMessage(BUILDER_SETTER, VALUE_SETTER),
              cardMessage(VALUE_SETTER, BUILDER_SETTER))) {
        Compilation compilation = compile(STAMP, CARD, wire, CARD_MAPPING);
        assertThat(compilation).succeeded();
        var result = new RuntimeCompilationHelper.CompiledResult(compilation);
        Object impl = result.instance("com.example.CardMappingImpl");
        Object stamp = result.newInstance("com.example.Stamp");
        Object card = result.newInstance("com.example.Card", stamp);
        Object message = invoke(impl, "build", card);
        Assertions.assertThat(invoke(message, "getStamp")).isSameAs(stamp);
        assertThatValidated(validated(invoke(impl, "parse", message))).isValid().hasValue(card);
      }
    }

    @Test
    @DisplayName(
        "a setX builder setter at the getter's type wins over a property-named one that is not")
    void setXAtTheGettersType() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Label",
              """
              package com.example;

              public record Label(String text) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LabelView",
              """
              package com.example;

              import java.util.function.Supplier;

              public final class LabelView {
                private final String text;
                private LabelView(String text) { this.text = text; }
                public String getText() { return text; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  private String text;
                  public Builder text(Supplier<String> text) { this.text = text.get(); return this; }
                  public Builder setText(String text) { this.text = text; return this; }
                  public LabelView build() { return new LabelView(text); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.LabelMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface LabelMapping extends MappingSpec<Label, LabelView> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.LabelMappingImpl"))
          .contains("b.setText(domain.text());");
    }

    @Test
    @DisplayName("overloads none of which takes the getter's type are refused, naming the first")
    void noOverloadAtTheGettersType() {
      JavaFileObject wire =
          personDto(STRING_SETTER, "public void setAge(long age) { this.age = (int) age; }");
      Compilation compilation = compile(PERSON, wire, PERSON_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'age' on 'PersonDto' is read and written at different types");
    }

    /**
     * A bean whose property {@code x}, read at {@code type}, has a setter at that type and a rival
     * overload at {@code rival}, with a domain record and a spec for it, all named after {@code
     * name}.
     */
    private static JavaFileObject rivalling(String name, String type, String rival) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import java.util.*;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          class %1$sDto {
            private %2$s x;
            public %2$s getX() { return x; }
            public void setX(%2$s x) { this.x = x; }
            public void setX(%3$s x) {}
          }

          record %1$s(%2$s x) {}

          @GenerateMapping
          interface %1$sMapping extends MappingSpec<%1$s, %1$sDto> {}
          """
              .formatted(name, type, rival));
    }

    @Test
    @DisplayName("a rival overload is refused exactly where the value build passes fits it as well")
    void rivalOverloads() {
      // A copied container is a generic call's result, whose element javac infers for each writer:
      // the copy of a List<String> fits Collection<Object>, never Collection<Integer>.
      Map<String, String[]> refused =
          Map.of(
              "Widened", new String[] {"List<String>", "Collection<Object>"},
              "LowerBound", new String[] {"List<String>", "Collection<? super Integer>"},
              "Captured", new String[] {"List<? extends Number>", "Collection<Number>"},
              "Raw", new String[] {"List", "Collection<Object>"});
      Map<String, String[]> accepted =
          Map.of(
              "Supertype", new String[] {"List<String>", "Collection<String>"},
              "OtherElement", new String[] {"List<String>", "Collection<Integer>"},
              "UpperBound", new String[] {"List<String>", "Collection<? extends Integer>"},
              "Unrelated", new String[] {"List<String>", "Comparable<String>"},
              "ToArray", new String[] {"List<String>", "String[]"},
              "FromArray", new String[] {"String[]", "List<String>"},
              "NotCopied", new String[] {"String", "Comparable<Integer>"});
      Compilation refusals = compileRivals(refused);
      refused.forEach(
          (name, types) ->
              assertThat(refusals)
                  .hadErrorContaining(
                      "bean property 'x' on '"
                          + name
                          + "Dto' has two writers the generated call cannot choose between: setX("
                          + types[0]
                          + ") and setX("
                          + types[1]
                          + ")."));
      assertThat(refusals).hadErrorContaining("Remove or rename one of the two");
      Assertions.assertThat(refusals.errors()).hasSize(refused.size());
      // Each accepted shape compiles through to javac's own choice of setter.
      Compilation compilation = compileRivals(accepted);
      assertThat(compilation).succeeded();
      accepted
          .keySet()
          .forEach(
              name ->
                  Assertions.assertThat(
                          generatedSource(compilation, "com.example." + name + "MappingImpl"))
                      .contains("wire.setX("));
    }

    private Compilation compileRivals(Map<String, String[]> rivals) {
      return compile(
          rivals.entrySet().stream()
              .map(entry -> rivalling(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
              .toArray(JavaFileObject[]::new));
    }

    @Test
    @DisplayName("two builder setters at the getter's type once instantiated are refused")
    void sameTypeOnceInstantiated() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Title",
              """
              package com.example;

              public record Title(String text) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.TitleView",
              """
              package com.example;

              public final class TitleView {
                private final String text;
                private TitleView(String text) { this.text = text; }
                public String getText() { return text; }
                public static Builder<String> builder() { return new Builder<>(); }
                public static final class Builder<T extends CharSequence> {
                  private String text;
                  public Builder<T> setText(T text) { this.text = text.toString(); return this; }
                  public Builder<T> setText(String text) { this.text = text; return this; }
                  public TitleView build() { return new TitleView(text); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TitleMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TitleMapping extends MappingSpec<Title, TitleView> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'text' on 'TitleView' has two writers the generated call cannot"
                  + " choose between: setText(T) and setText(String).");
    }
  }

  /**
   * The edges of the {@code @Singular} reading, on hand-written builders shaped as Lombok writes
   * them. {@code LombokInteropTest} pins Lombok's own output; these are the shapes it never emits.
   */
  @Nested
  @DisplayName("@Singular collection setters")
  class SingularCollectionSetters {

    @Test
    @DisplayName(
        "only a clear-named writer taking ? extends arguments is a collection setter, and an exact"
            + " overload still wins")
    void collectionSetterRecognition() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Kit",
              """
              package com.example;

              import java.util.List;

              public record Kit(
                  String id, int count, String name, List<String> tags, List<String> items) {}
              """);
      // Each clear-named writer misses one part of the shape bar items(...), which has no adder,
      // and tags(...) has an overload at the getter's own type.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.KitDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.Collection;
              import java.util.List;

              public final class KitDto {
                private final String id;
                private final int count;
                private final String name;
                private final List<String> tags;
                private final List<String> items;

                private KitDto(Builder b) {
                  id = b.id; count = b.count; name = b.name; tags = b.tags; items = b.items;
                }

                public String getId() { return id; }
                public int getCount() { return count; }
                public String getName() { return name; }
                public List<String> getTags() { return tags; }
                public List<String> getItems() { return items; }

                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String id;
                  private int count;
                  private String name;
                  private List<String> tags;
                  private final List<String> items = new ArrayList<>();

                  public Builder id(String id) { this.id = id; return this; }
                  public Builder count(int count) { this.count = count; return this; }
                  public Builder clearCount() { count = 0; return this; }
                  public Builder name(String name) { this.name = name; return this; }
                  public Builder clearName() { name = null; return this; }
                  public Builder tags(List<String> tags) { this.tags = tags; return this; }
                  public Builder tags(Collection<? extends String> tags) {
                    this.tags = List.copyOf(tags);
                    return this;
                  }
                  public Builder clearTags() { tags = null; return this; }
                  public Builder items(Collection<? extends String> items) {
                    this.items.addAll(items);
                    return this;
                  }
                  public Builder clearItems() { items.clear(); return this; }
                  public Builder codes(Collection<? super String> codes) { return this; }
                  public Builder clearCodes() { return this; }
                  public KitDto build() { return new KitDto(this); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.KitMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface KitMapping extends MappingSpec<Kit, KitDto> {}
              """);

      Compilation compilation = compileLinted(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.KitMappingImpl"))
          .contains("b.tags(hkj$copyOf(domain.tags()));")
          .contains("b.items(hkj$copyOf(domain.items()));");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.KitMappingImpl");
        Object kit =
            result
                .loadClass("com.example.Kit")
                .getDeclaredConstructors()[0]
                .newInstance("k1", 2, "kit", List.of("t"), List.of("i1", "i2"));
        Object dto = invoke(impl, "build", kit);
        Assertions.assertThat(invoke(dto, "getItems")).isEqualTo(List.of("i1", "i2"));
        assertThatValidated(validated(invoke(impl, "parse", dto))).isValid().hasValue(kit);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    /** A two-way bean with a getter for {@code things} and a builder declaring {@code writers}. */
    private JavaFileObject collectionBean(String name, String writers) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import java.util.Collection;
          import java.util.List;

          public final class %1$s {
            private %1$s() {}
            public List<String> getThings() { return List.of(); }
            public static Builder builder() { return new Builder(); }
            public static final class Builder {
              %2$s
              public Builder clearThings() { return this; }
              public %1$s build() { return new %1$s(); }
            }
          }
          """
              .formatted(name, writers));
    }

    @Test
    @DisplayName(
        "a collection setter the getter's value does not fit, or that a rival overload shadows, is"
            + " refused")
    void collectionSetterTheGetterDoesNotFit() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Thing",
              """
              package com.example;

              import java.util.List;

              public record Thing(List<String> things) {}
              """);
      JavaFileObject specs =
          JavaFileObjects.forSourceString(
              "com.example.ThingMappings",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class ThingMappings {
                private ThingMappings() {}

                @GenerateMapping
                public interface MismatchMapping extends MappingSpec<Thing, MismatchDto> {}

                @GenerateMapping
                public interface RivalMapping extends MappingSpec<Thing, RivalDto> {}
              }
              """);

      Compilation compilation =
          compile(
              domain,
              specs,
              collectionBean(
                  "MismatchDto",
                  "public Builder things(Collection<? extends Integer> things) { return this; }"),
              collectionBean(
                  "RivalDto",
                  "public Builder things(Collection<? extends String> things) { return this; }\n"
                      + "public Builder things(Iterable<String> things) { return this; }"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(2);
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'things' on 'MismatchDto' is read and written at different types"
                  + " (List<String> vs Collection<? extends Integer>).");
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'things' on 'RivalDto' has two writers the generated call cannot"
                  + " choose between: things(Collection<? extends String>) and"
                  + " things(Iterable<String>).");
    }

    @Test
    @DisplayName("a clear-named writer taking a wildcard that is no collection is a plain writer")
    void wildcardNonCollectionIsNoCollectionSetter() {
      JavaFileObject types =
          JavaFileObjects.forSourceString(
              "com.example.Pets",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class Pets {
                private Pets() {}

                public record Animal(String name) {}

                public record Pet(Class<? extends Animal> type, Animal animal) {}

                public static final class PetRequest {
                  private final Class<? extends Animal> type;
                  private final Animal animal;

                  private PetRequest(Class<? extends Animal> type, Animal animal) {
                    this.type = type;
                    this.animal = animal;
                  }

                  @Override
                  public String toString() { return type.getSimpleName() + ":" + animal; }

                  public static Builder builder() { return new Builder(); }

                  public static final class Builder {
                    private Class<? extends Animal> type;
                    private Animal animal;

                    public Builder type(Class<? extends Animal> type) {
                      this.type = type;
                      return this;
                    }
                    public Builder clearType() { type = null; return this; }
                    public Builder animal(Animal animal) { this.animal = animal; return this; }
                    public PetRequest build() { return new PetRequest(type, animal); }
                  }
                }

                @GenerateMapping
                public interface PetMapping extends MappingSpec<Pet, PetRequest> {}
              }
              """);

      Compilation compilation = compile(types);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object animal = result.newInstance("com.example.Pets$Animal", "rex");
        Object pet =
            result
                .loadClass("com.example.Pets$Pet")
                .getDeclaredConstructors()[0]
                .newInstance(animal.getClass(), animal);
        Assertions.assertThat(
                invoke(result.instance("com.example.PetsPetMappingImpl"), "build", pet))
            .hasToString("Animal:Animal[name=rex]");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    /**
     * A build-only bean whose builder has only a {@code @Singular} collection taking {@code
     * parameter}.
     */
    private JavaFileObject singularRequest(String name, String parameter) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import java.util.ArrayList;
          import java.util.Collection;

          public final class %1$s {
            private %1$s() {}
            public static Builder builder() { return new Builder(); }
            public static final class Builder {
              public Builder tags(%2$s tags) { return this; }
              public Builder tag(String tag) { return this; }
              public Builder clearTags() { return this; }
              public %1$s build() { return new %1$s(); }
            }
          }
          """
              .formatted(name, parameter));
    }

    @Test
    @DisplayName(
        "a build-only collection setter takes the domain's own container or its List, Set or Map"
            + " of the setter's elements, and nothing else")
    void buildOnlyCollectionSetterNeedsAContainerItHolds() {
      JavaFileObject domains =
          JavaFileObjects.forSourceString(
              "com.example.Tagged",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.Map;
              import java.util.Set;

              public final class Tagged {
                private Tagged() {}
                public record Keyed(Map<String, String> tags) {}
                public record Numbered(Set<Integer> tags) {}
                public record Plain(String tags) {}
                public record Listed(ArrayList<String> tags) {}
              }
              """);
      JavaFileObject specs =
          JavaFileObjects.forSourceString(
              "com.example.TaggedMappings",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class TaggedMappings {
                private TaggedMappings() {}

                @GenerateMapping
                public interface KeyedMapping extends MappingSpec<Tagged.Keyed, KeyedRequest> {}

                @GenerateMapping
                public interface NumberedMapping
                    extends MappingSpec<Tagged.Numbered, NumberedRequest> {}

                @GenerateMapping
                public interface PlainMapping extends MappingSpec<Tagged.Plain, PlainRequest> {}

                @GenerateMapping
                public interface ListedMapping extends MappingSpec<Tagged.Listed, ListedRequest> {}
              }
              """);

      Compilation compilation =
          compile(
              domains,
              specs,
              singularRequest("KeyedRequest", "Collection<? extends String>"),
              singularRequest("NumberedRequest", "ArrayList<? extends String>"),
              singularRequest("PlainRequest", "Collection<? extends String>"),
              singularRequest("ListedRequest", "Collection<? extends String>"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(4);
      // A subtype of the setter's parameter would be handed over uncopied, so it is refused.
      assertThat(compilation)
          .hadErrorContaining(
              "(java.util.Collection<? extends java.lang.String> vs"
                  + " java.util.ArrayList<java.lang.String>)");
      assertThat(compilation)
          .hadErrorContaining(
              "(java.util.Collection<? extends java.lang.String> vs"
                  + " java.util.Map<java.lang.String, java.lang.String>)");
      assertThat(compilation)
          .hadErrorContaining(
              "(java.util.ArrayList<? extends java.lang.String> vs"
                  + " java.util.Set<java.lang.Integer>)");
      assertThat(compilation)
          .hadErrorContaining(
              "(java.util.Collection<? extends java.lang.String> vs java.lang.String)");
    }

    @Test
    @DisplayName("singularRank prefers an undone English ending to a bare prefix")
    void singularRankUndoesEnglishPlurals() {
      Assertions.assertThat(
              Map.of(
                  "tag", "tags",
                  "box", "boxes",
                  "namePart", "nameParts",
                  "entry", "entries",
                  "shelf", "shelves",
                  "knife", "knives",
                  "index", "indices",
                  "matrix", "matrices",
                  "analysis", "analyses"))
          .allSatisfy(
              (singular, plural) ->
                  Assertions.assertThat(BeanPropertyAnalyser.singularRank(singular, plural))
                      .as("%s of %s", singular, plural)
                      .isEqualTo(2));
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("child", "children")).isEqualTo(1);
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("name", "nameParts")).isEqualTo(1);
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("id", "tags")).isZero();
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("label", "tags")).isZero();
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("tags", "tags")).isZero();
      Assertions.assertThat(BeanPropertyAnalyser.singularRank("category", "entries")).isZero();
    }
  }

  @Nested
  @DisplayName("Optional bridging")
  class OptionalBridging {

    @Test
    @DisplayName("a domain Optional maps to a nullable bean property, writing null when empty")
    void identityBridge() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.Optional;

              public record Profile(String handle, Optional<String> bio) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ProfileDto",
              """
              package com.example;

              public class ProfileDto {
                private String handle;
                private String bio;
                public String getHandle() { return handle; }
                public void setHandle(String handle) { this.handle = handle; }
                public String getBio() { return bio; }
                public void setBio(String bio) { this.bio = bio; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ProfileMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ProfileMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setBio(domain.bio().orElse(null));")
          .contains(".field(\"bio\", Validated.validNel(Optional.ofNullable(wire.getBio())))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.ProfileMappingImpl");
        Object present =
            result
                .loadClass("com.example.Profile")
                .getDeclaredConstructor(String.class, java.util.Optional.class)
                .newInstance("ada", java.util.Optional.of("hi"));
        Object emptyProfile =
            result
                .loadClass("com.example.Profile")
                .getDeclaredConstructor(String.class, java.util.Optional.class)
                .newInstance("ada", java.util.Optional.empty());

        Object dtoPresent = invoke(impl, "build", present);
        Assertions.assertThat(invoke(dtoPresent, "getBio")).isEqualTo("hi");
        Object dtoEmpty = invoke(impl, "build", emptyProfile);
        Assertions.assertThat(invoke(dtoEmpty, "getBio")).isNull();

        Validated<NonEmptyList<FieldError>, Object> parsedEmpty =
            validated(invoke(impl, "parse", dtoEmpty));
        Assertions.assertThat(parsedEmpty.get()).isEqualTo(emptyProfile);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    // A field initialiser on a setter bean is the tier matrix's business (MappingTierMatrixTest
    // gives every bridged bean field one); a builder's own default is the other place a bean can
    // start out holding a value.
    @Test
    @DisplayName("an empty Optional replaces a builder's own default, so the round trip holds")
    void emptyBridgeReplacesTheBuildersDefault() throws ReflectiveOperationException {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public record Account(String name, Optional<String> status) {}

              // A builder that carries a default for a property it is never given.
              final class AccountView {
                private final String name;
                private final String status;
                private AccountView(Builder b) {
                  this.name = b.name;
                  this.status = b.statusSet ? b.status : "ACTIVE";
                }
                public String getName() { return name; }
                public String getStatus() { return status; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String name;
                  private String status;
                  private boolean statusSet;
                  public Builder name(String name) { this.name = name; return this; }
                  public Builder status(String status) {
                    this.status = status;
                    this.statusSet = true;
                    return this;
                  }
                  public AccountView build() { return new AccountView(this); }
                }
              }

              @GenerateMapping
              interface AccountViewMapping extends MappingSpec<Account, AccountView> {}
              """);

      Compilation compilation = compile(sources);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AccountViewMappingImpl"))
          .contains("b.status(domain.status().orElse(null));");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object mapping = result.instance("com.example.AccountViewMappingImpl");
      Object empty = result.newInstance("com.example.Account", "ada", Optional.empty());
      Object built = invoke(mapping, "build", empty);
      Assertions.assertThat(invoke(built, "getStatus")).isNull();
      assertThatValidated(validated(invoke(mapping, "parse", built))).isValid().hasValue(empty);
    }

    @Test
    @DisplayName("a domain Optional element maps through a leaf to a nullable bean property")
    void leafBridge() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              import java.util.Optional;

              public record Account(Optional<EmailAddress> email) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              public class AccountDto {
                private String email;
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
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
      String generated = generatedSource(compilation, "com.example.AccountMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setEmail(domain.email().map(email()::build).orElse(null));")
          .contains(
              ".field(\"email\", Optional.ofNullable(wire.getEmail()).map(v ->"
                  + " email().parse(v).map(Optional::of))");
    }

    @Test
    @DisplayName("a bad Optional-bridged element still accumulates a located failure")
    void bridgeElementValidation() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              import java.util.Optional;

              public record Account(Optional<EmailAddress> email) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              public class AccountDto {
                private String email;
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
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
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountMappingImpl");
        Object dto = result.newInstance("com.example.AccountDto");
        invoke(dto, "setEmail", "not-an-email");

        Validated<NonEmptyList<FieldError>, Object> parsed = validated(invoke(impl, "parse", dto));
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("email"), "not an email address"));

        // An absent (null) email is valid: it bridges to Optional.empty.
        Object emptyDto = result.newInstance("com.example.AccountDto");
        Validated<NonEmptyList<FieldError>, Object> parsedEmpty =
            validated(invoke(impl, "parse", emptyDto));
        Assertions.assertThat(parsedEmpty.isValid()).isTrue();
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a domain Optional element with no identity or leaf to the property is rejected")
    void bridgeWithoutElementSourceRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Box",
              """
              package com.example;

              import java.util.Optional;

              public record Box(Optional<Integer> count) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BoxDto",
              """
              package com.example;

              public class BoxDto {
                private String count;
                public String getCount() { return count; }
                public void setCount(String count) { this.count = count; }
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
              public interface BoxMapping extends MappingSpec<Box, BoxDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Box.count' is Optional<java.lang.Integer>, bridged to the nullable"
                  + " bean property 'count' of type java.lang.String");
      assertThat(compilation)
          .hadErrorContaining(
              "ValidatedPrism<java.lang.String, java.lang.Integer> (the element types, not the"
                  + " Optional)");
    }

    @Test
    @DisplayName("a non-Optional mismatched bean property is not a bridge and is rejected")
    void nonOptionalMismatchNotABridge() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Thing",
              """
              package com.example;

              public record Thing(int count) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ThingDto",
              """
              package com.example;

              public class ThingDto {
                private String count;
                public String getCount() { return count; }
                public void setCount(String count) { this.count = count; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ThingMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ThingMapping extends MappingSpec<Thing, ThingDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'ThingDto.count' has no usable source");
    }

    @Test
    @DisplayName(
        "a bean Optional property against a domain Optional (unresolvable element) is not a bridge")
    void beanOptionalPropertyIsNotABridge() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Holder",
              """
              package com.example;

              import java.util.Optional;

              public record Holder(Optional<Integer> value) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.HolderDto",
              """
              package com.example;

              import java.util.Optional;

              public class HolderDto {
                private Optional<String> value;
                public Optional<String> getValue() { return value; }
                public void setValue(Optional<String> value) { this.value = value; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.HolderMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface HolderMapping extends MappingSpec<Holder, HolderDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'HolderDto.value' has no usable source");
    }

    /** A domain record with an {@code Optional} list, the shape the bridge would carry. */
    private static final JavaFileObject BOOKMARKS =
        JavaFileObjects.forSourceString(
            "com.example.Bookmarks",
            """
            package com.example;

            import java.util.List;
            import java.util.Optional;

            public record Bookmarks(String owner, Optional<List<String>> urls) {}
            """);

    /** The JAXB convention: the getter creates the list on first call, and there is no setter. */
    private static final JavaFileObject LIVE_LIST_DTO =
        JavaFileObjects.forSourceString(
            "com.example.BookmarksDto",
            """
            package com.example;

            import java.util.ArrayList;
            import java.util.List;

            public class BookmarksDto {
              private String owner;
              private List<String> urls;
              public String getOwner() { return owner; }
              public void setOwner(String owner) { this.owner = owner; }
              public List<String> getUrls() {
                if (urls == null) { urls = new ArrayList<>(); }
                return urls;
              }
            }
            """);

    @Test
    @DisplayName("a bridge onto a getter-only List is refused: it has no absence to write into")
    void getterOnlyListBridgeRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BookmarksMapping extends MappingSpec<Bookmarks, BookmarksDto> {}
              """);

      Compilation compilation = compile(BOOKMARKS, LIVE_LIST_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Bookmarks.urls' is Optional<List<String>>, bridged to the getter-only"
                  + " bean property 'urls' (not supported yet).");
      assertThat(compilation)
          .hadErrorContaining(
              "The bridge writes an empty Optional as null, and 'urls' is written through its own"
                  + " getter (the JAXB convention, getUrls().addAll(...)), whose list is created on"
                  + " first call, so the property cannot hold a null");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'urls' as List<String>, dropping the Optional, so the property's own empty"
                  + " list encodes nothing, or give 'urls' a setter and a getter that returns what"
                  + " the setter stored, so absence can be written as null and read back.");
    }

    @Test
    @DisplayName("a lifted container onto a getter-only List is refused just the same")
    void getterOnlyListBridgeRejectedWhenItLifts() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Links",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              public record Links(String owner, Optional<List<Link>> urls) {
                public record Link(String href) {}
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.LinksMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface LinksMapping extends MappingSpec<Links, BookmarksDto> {
                default ValidatedPrism<String, Links.Link> urls() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new Links.Link(raw)), Links.Link::href);
                }
              }
              """);

      Compilation compilation = compile(domain, LIVE_LIST_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Links.urls' is Optional<List<Links.Link>>, bridged to the getter-only"
                  + " bean property 'urls' (not supported yet).");
    }

    @Test
    @DisplayName(
        "a bridged container lifts through its elements' spec and through a key leaf on a bean")
    void aBridgedContainerLiftsOnABean() {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Directory",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;
              import java.util.Optional;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public record Directory(
                  String id,
                  Optional<List<Contact>> contacts,
                  Optional<Map<EmailAddress, String>> owners) {}

              record Contact(String label) {}

              record ContactDto(String label) {}

              @GenerateMapping
              interface ContactMapping extends MappingSpec<Contact, ContactDto> {}

              class DirectoryBean {
                private String id;
                private List<ContactDto> contacts;
                private Map<String, String> owners;
                public String getId() { return id; }
                public void setId(String id) { this.id = id; }
                public List<ContactDto> getContacts() { return contacts; }
                public void setContacts(List<ContactDto> contacts) { this.contacts = contacts; }
                public Map<String, String> getOwners() { return owners; }
                public void setOwners(Map<String, String> owners) { this.owners = owners; }
              }

              // no marker: a bean wire bridges automatically, key leaf included
              @GenerateMapping
              interface DirectoryMapping extends MappingSpec<Directory, DirectoryBean> {
                @MapKey("owners")
                default ValidatedPrism<String, EmailAddress> ownersKey() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);

      Compilation compilation = compile(EMAIL, sources);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.DirectoryMappingImpl"))
          .contains(
              "wire.setContacts(domain.contacts()"
                  + ".map(ContactMappingImpl.INSTANCE.asValidatedPrism()::buildAll).orElse(null));")
          .contains("wire.setOwners(domain.owners().map(ownersKey()::buildKeys).orElse(null));")
          .contains("ownersKey().parseKeys(v)");
    }

    @Test
    @DisplayName("a leaf over the whole Optional wins over the automatic bridge on a bean")
    void aWholeOptionalLeafWinsOverTheAutomaticBridge() {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Handle",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public record Handle(Optional<String> nickname) {}

              class HandleBean {
                private String nickname;
                public String getNickname() { return nickname; }
                public void setNickname(String nickname) { this.nickname = nickname; }
              }

              @GenerateMapping
              interface HandleMapping extends MappingSpec<Handle, HandleBean> {
                // the more specific declaration: it maps the pair whole, so null is not absence
                default ValidatedPrism<String, Optional<String>> nickname() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(Optional.of(raw)), value -> value.orElse(""));
                }
              }
              """);

      Compilation compilation = compile(sources);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.HandleMappingImpl"))
          .contains("wire.setNickname(nickname().build(domain.nickname()));")
          .contains(".field(\"nickname\", hkj$ifPresent(wire.getNickname(), nickname()::parse))")
          .doesNotContain("Optional.ofNullable");
    }

    @Test
    @DisplayName(
        "a bridged element carrying a wildcard is named, not inferred, on both leg shapes and"
            + " through a leaf: Optional is invariant, so a capture is not the declared type")
    void bridgedWildcardElementIsNamed() throws ReflectiveOperationException {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Wild",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public final class Wild {
                public record Holder<T>(T value) {}

                // The element is a container carrying a wildcard: the identity leg.
                public record ListD(String id, Optional<List<? extends CharSequence>> x) {}

                public static class ListW {
                  private String id;
                  private List<? extends CharSequence> x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public List<? extends CharSequence> getX() { return x; }
                  public void setX(List<? extends CharSequence> x) { this.x = x; }
                }

                // The element is not a container at all, so this is not the container rule.
                public record BoxD(String id, Optional<Holder<? extends Number>> x) {}

                public static class BoxW {
                  private String id;
                  private Holder<? extends Number> x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public Holder<? extends Number> getX() { return x; }
                  public void setX(Holder<? extends Number> x) { this.x = x; }
                }

                // The leaf leg, with no wildcard on the wire side at all.
                public record LeafD(String id, Optional<List<? extends CharSequence>> x) {}

                public static class LeafW {
                  private String id;
                  private String x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public String getX() { return x; }
                  public void setX(String x) { this.x = x; }
                }

                @GenerateMapping
                public interface ListMapping extends MappingSpec<ListD, ListW> {}

                @GenerateMapping
                public interface BoxMapping extends MappingSpec<BoxD, BoxW> {}

                @GenerateMapping
                public interface LeafMapping extends MappingSpec<LeafD, LeafW> {
                  default ValidatedPrism<String, List<? extends CharSequence>> x() {
                    return ValidatedPrism.of(
                        raw -> Validated.validNel(List.of(raw)), values -> values.getFirst().toString());
                  }
                }
              }
              """);

      Compilation compilation = compileLinted(sources);
      assertThat(compilation).succeededWithoutWarnings();
      // A container element keeps its null scan: naming the Optional's argument is what the
      // scan's result needed, so the bridge is no longer a hole in the located-null doctrine.
      Assertions.assertThat(generatedSource(compilation, "com.example.WildListMappingImpl"))
          .contains("hkj$allPresent(v).map(Optional::<List<? extends CharSequence>>of)");
      Assertions.assertThat(generatedSource(compilation, "com.example.WildBoxMappingImpl"))
          .contains("Optional.<Wild.Holder<? extends Number>>ofNullable(wire.getX())");
      Assertions.assertThat(generatedSource(compilation, "com.example.WildLeafMappingImpl"))
          .contains("map(Optional::<List<? extends CharSequence>>of)");

      // Absent, present-empty and present are three values the bridge must keep apart.
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.WildListMappingImpl");
      var constructor =
          result
              .loadClass("com.example.Wild$ListD")
              .getDeclaredConstructor(String.class, Optional.class);
      for (Optional<List<? extends CharSequence>> x :
          List.of(
              Optional.<List<? extends CharSequence>>empty(),
              Optional.<List<? extends CharSequence>>of(List.of()),
              Optional.<List<? extends CharSequence>>of(List.of("a")))) {
        Object domain = constructor.newInstance("id", x);
        assertThatValidated(validated(invoke(impl, "parse", invoke(impl, "build", domain))))
            .as("x = %s", x)
            .isValid()
            .hasValue(domain);
      }
      Object hostile =
          constructor.newInstance("id", Optional.of(Arrays.asList("a", (CharSequence) null)));
      assertThatValidated(validated(invoke(impl, "parse", invoke(impl, "build", hostile))))
          .as("a null element is located, bridged or not")
          .isInvalid()
          .hasFieldErrors("x.1: must not be null");
    }

    @Test
    @DisplayName(
        "a wildcard on the element's enclosing type is named too: capture reaches the enclosing"
            + " link, even where the inner type writes no wildcard of its own")
    void bridgedEnclosingWildcardIsNamed() {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Encl",
              """
              package com.example;

              import java.util.Optional;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class Encl {
                public static class Outer<A> {
                  public class Inner<B> {}
                }

                public record D(String id, Optional<Outer<? extends Number>.Inner<String>> x) {}

                public static class W {
                  private String id;
                  private Outer<? extends Number>.Inner<String> x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public Outer<? extends Number>.Inner<String> getX() { return x; }
                  public void setX(Outer<? extends Number>.Inner<String> x) { this.x = x; }
                }

                @GenerateMapping
                public interface M extends MappingSpec<D, W> {}
              }
              """);

      Compilation compilation = compileLinted(sources);
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(generatedSource(compilation, "com.example.EnclMImpl"))
          .contains("Optional.<Encl.Outer<? extends Number>.Inner<String>>ofNullable(wire.getX())");
    }

    @Test
    @DisplayName(
        "the element is named only where inference would capture it: a wildcard below the"
            + " element's own arguments, or on the wire side alone, keeps the shorter leg")
    void onlyACapturingElementIsNamed() {
      JavaFileObject sources =
          JavaFileObjects.forSourceString(
              "com.example.Deep",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              public final class Deep {
                public record Holder<T>(T value) {}

                public record Tag(int size) {}

                // The wildcard is nested below the element's own arguments, so nothing captures.
                public record NestedD(String id, Optional<Holder<List<? extends CharSequence>>> x) {}

                public static class NestedW {
                  private String id;
                  private Holder<List<? extends CharSequence>> x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public Holder<List<? extends CharSequence>> getX() { return x; }
                  public void setX(Holder<List<? extends CharSequence>> x) { this.x = x; }
                }

                // An inner class element whose enclosing type carries no wildcard: the walk
                // reaches the enclosing link and finds nothing, so no witness.
                public record InnerD(String id, Optional<Outer<String>.Inner> x) {}

                public static class Outer<A> {
                  public class Inner {}
                }

                public static class InnerW {
                  private String id;
                  private Outer<String>.Inner x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public Outer<String>.Inner getX() { return x; }
                  public void setX(Outer<String>.Inner x) { this.x = x; }
                }

                // The wildcard is on the WIRE side only; the domain element is a plain type.
                public record WireD(String id, Optional<Tag> x) {}

                public static class WireW {
                  private String id;
                  private List<? extends CharSequence> x;
                  public String getId() { return id; }
                  public void setId(String id) { this.id = id; }
                  public List<? extends CharSequence> getX() { return x; }
                  public void setX(List<? extends CharSequence> x) { this.x = x; }
                }

                @GenerateMapping
                public interface NestedMapping extends MappingSpec<NestedD, NestedW> {}

                @GenerateMapping
                public interface InnerMapping extends MappingSpec<InnerD, InnerW> {}

                @GenerateMapping
                public interface WireMapping extends MappingSpec<WireD, WireW> {
                  default ValidatedPrism<List<? extends CharSequence>, Tag> x() {
                    return ValidatedPrism.of(
                        values -> Validated.validNel(new Tag(values.size())), _ -> List.of());
                  }
                }
              }
              """);

      Compilation compilation = compileLinted(sources);
      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(generatedSource(compilation, "com.example.DeepNestedMappingImpl"))
          .contains("Optional.ofNullable(wire.getX())")
          .doesNotContain("Optional.<");
      Assertions.assertThat(generatedSource(compilation, "com.example.DeepInnerMappingImpl"))
          .contains("Optional.ofNullable(wire.getX())")
          .doesNotContain("Optional.<");
      Assertions.assertThat(generatedSource(compilation, "com.example.DeepWireMappingImpl"))
          .contains("map(Optional::of)")
          .doesNotContain("Optional::<");
    }

    @Test
    @DisplayName("a getter-only List refuses a bridge that maps its element through a leaf too")
    void getterOnlyListBridgeThroughALeafRejected() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Bookmarks",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              public record Bookmarks(String owner, Optional<Urls> urls) {

                public record Urls(List<String> values) {}
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksMapping",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface BookmarksMapping extends MappingSpec<Bookmarks, BookmarksDto> {
                default ValidatedPrism<List<String>, Bookmarks.Urls> urls() {
                  return ValidatedPrism.of(
                      values -> Validated.validNel(new Bookmarks.Urls(values)),
                      Bookmarks.Urls::values);
                }
              }
              """);

      Compilation compilation = compile(domain, LIVE_LIST_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bridged to the getter-only bean property 'urls' (not supported yet)");
      // The fix names the element the leaf produces, not the property type.
      assertThat(compilation).hadErrorContaining("Declare 'urls' as Bookmarks.Urls, dropping the");
    }

    @Test
    @DisplayName(
        "a domain Optional whose element cannot reach a getter-only List keeps the element"
            + " diagnostic, which names both types")
    void getterOnlyListMismatchKeepsTheElementDiagnostic() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Bookmarks",
              """
              package com.example;

              import java.util.Optional;

              public record Bookmarks(String owner, Optional<String> urls) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BookmarksMapping extends MappingSpec<Bookmarks, BookmarksDto> {}
              """);

      Compilation compilation = compile(domain, LIVE_LIST_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "the element types differ and neither a leaf nor a mapping spec converts them");
      Assertions.assertThat(compilation.errors())
          .as("the getter-only refusal must not displace the more precise diagnostic")
          .noneMatch(error -> error.getMessage(null).contains("getter-only"));
    }

    @Test
    @DisplayName("a projection onto a getter-only List refuses the bridge on the same terms")
    void getterOnlyListBridgeRejectedOnProjection() {
      JavaFileObject projection =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksProjectionDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;

              public class BookmarksProjectionDto {
                private List<String> urls;
                public List<String> getUrls() {
                  if (urls == null) { urls = new ArrayList<>(); }
                  return urls;
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksProjectionMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BookmarksProjectionMapping
                  extends MappingSpec<Bookmarks, BookmarksProjectionDto> {}
              """);

      Compilation compilation = compile(BOOKMARKS, projection, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Bookmarks.urls' is Optional<List<String>>, bridged to the getter-only"
                  + " bean property 'urls' (not supported yet).");
    }

    @Test
    @DisplayName("a plain List domain component still fills the same getter-only property")
    void getterOnlyListTakesAPlainDomainList() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Bookmarks",
              """
              package com.example;

              import java.util.List;

              public record Bookmarks(String owner, List<String> urls) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BookmarksMapping extends MappingSpec<Bookmarks, BookmarksDto> {}
              """);

      Compilation compilation = compileLinted(domain, LIVE_LIST_DTO, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.BookmarksMappingImpl"))
          .contains("wire.getUrls().addAll(hkj$copyOf(domain.urls()));");
    }

    @Test
    @DisplayName("a bridge onto a List property with a setter keeps absence across the round trip")
    void settableListBridgeRoundTripsAbsence() throws ReflectiveOperationException {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksDto",
              """
              package com.example;

              import java.util.List;

              public class BookmarksDto {
                private String owner;
                private List<String> urls;
                public String getOwner() { return owner; }
                public void setOwner(String owner) { this.owner = owner; }
                public List<String> getUrls() { return urls; }
                public void setUrls(List<String> urls) { this.urls = urls; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BookmarksMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface BookmarksMapping extends MappingSpec<Bookmarks, BookmarksDto> {}
              """);

      Compilation compilation = compile(BOOKMARKS, wire, spec);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.BookmarksMappingImpl");
      var constructor =
          result
              .loadClass("com.example.Bookmarks")
              .getDeclaredConstructor(String.class, Optional.class);
      // Absence, a present empty list and a present non-empty one are three distinct values, and
      // a settable property keeps them apart where a live list could not.
      for (Optional<List<String>> urls :
          List.of(
              Optional.<List<String>>empty(),
              Optional.of(List.<String>of()),
              Optional.of(List.of("a")))) {
        Object domain = constructor.newInstance("ada", urls);
        assertThatValidated(validated(invoke(impl, "parse", invoke(impl, "build", domain))))
            .as("urls = %s", urls)
            .isValid()
            .hasValue(domain);
      }
    }
  }

  @Nested
  @DisplayName("Projection tier")
  class ProjectionTier {

    private static final JavaFileObject EMPLOYEE =
        JavaFileObjects.forSourceString(
            "com.example.Employee",
            """
            package com.example;

            public record Employee(String name, String department, int age) {}
            """);

    private static final JavaFileObject EMPLOYEE_CARD =
        JavaFileObjects.forSourceString(
            "com.example.EmployeeCardDto",
            """
            package com.example;

            public class EmployeeCardDto {
              private String name;
              private int age;
              public String getName() { return name; }
              public void setName(String name) { this.name = name; }
              public int getAge() { return age; }
              public void setAge(int age) { this.age = age; }
            }
            """);

    private static final JavaFileObject EMPLOYEE_CARD_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.EmployeeCardMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}
            """);

    @Test
    @DisplayName("an all-primitive bean projection gains a lawful asLens")
    void allPrimitiveProjectionAsLens() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Reading",
              """
              package com.example;

              public record Reading(int temperature, int humidity, long timestamp) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ReadingDto",
              """
              package com.example;

              public class ReadingDto {
                private int temperature;
                private int humidity;
                public int getTemperature() { return temperature; }
                public void setTemperature(int temperature) { this.temperature = temperature; }
                public int getHumidity() { return humidity; }
                public void setHumidity(int humidity) { this.humidity = humidity; }
              }
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

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ReadingMappingImpl");
      Assertions.assertThat(generated)
          .contains("ReadingDto wire = new ReadingDto();")
          .contains("wire.setTemperature(domain.temperature());")
          .contains("public Lens<Reading, ReadingDto> asLens()")
          .doesNotContain("patch(")
          .doesNotContain("parse(")
          .doesNotContain("asValidatedPrism");
    }

    @Test
    @DisplayName(
        "a reference-typed bean projection patches, keeps the rest, and locates an unset property")
    void referenceProjectionEmitsPatch() throws ReflectiveOperationException {
      Compilation compilation = compile(EMPLOYEE, EMPLOYEE_CARD, EMPLOYEE_CARD_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.EmployeeCardMappingImpl"))
          .contains("EmployeeCardDto wire = new EmployeeCardDto();")
          .contains("wire.setName(domain.name());")
          .contains("wire.setAge(domain.age());")
          .contains("public Validated<NonEmptyList<FieldError>, Employee> patch(Employee domain,")
          .contains("EmployeeCardDto wire) {")
          // A reference read is guarded into a located error; a primitive read cannot be null.
          .contains(".field(\"name\", hkj$ifPresent(wire.getName(), Validated::validNel))")
          .contains(".field(\"age\", Validated.validNel(wire.getAge()))")
          // The unprojected component is read from the domain argument, outside the guard.
          .contains(".apply((name, age) -> {")
          .contains("var department = domain.department();")
          .contains("return () -> new Employee(name, department, age);")
          .contains("private static <S, A> Validated<NonEmptyList<FieldError>, A> hkj$ifPresent(")
          .doesNotContain("asLens() {")
          .doesNotContain("parse(")
          .doesNotContain("asValidatedPrism");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.EmployeeCardMappingImpl");
      Object employee = result.newInstance("com.example.Employee", "Ada", "Research", 36);
      Object built = invoke(impl, "build", employee);
      Assertions.assertThat(invoke(built, "getName")).isEqualTo("Ada");
      Assertions.assertThat(invoke(built, "getAge")).isEqualTo(36);

      Object promoted =
          bean(result, "com.example.EmployeeCardDto", "setName", "Grace", "setAge", 41);
      assertThatValidated(patch(impl, employee, promoted))
          .isValid()
          .hasValue(result.newInstance("com.example.Employee", "Grace", "Research", 41));

      // An unset reference property is the ordinary case on a bean: a located error, not absence.
      Object unset = bean(result, "com.example.EmployeeCardDto", "setAge", 41);
      assertThatValidated(patch(impl, employee, unset))
          .isInvalid()
          .hasFieldErrors("name: must not be null");
    }

    @Test
    @DisplayName("a leaf on a bean projection validates, and every bad property accumulates")
    void leafCarryingBeanProjection() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Subscriber",
              """
              package com.example;

              public record Subscriber(String id, String name, EmailAddress email, int age) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberBean",
              """
              package com.example;

              public class SubscriberBean {
                private String name;
                private String email;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberBeanMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface SubscriberBeanMapping extends MappingSpec<Subscriber, SubscriberBean> {
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
      Assertions.assertThat(generatedSource(compilation, "com.example.SubscriberBeanMappingImpl"))
          .contains("wire.setEmail(email().build(domain.email()));")
          .contains(".field(\"email\", hkj$ifPresent(wire.getEmail(), email()::parse))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.SubscriberBeanMappingImpl");
      Object subscriber =
          result.newInstance(
              "com.example.Subscriber",
              "7",
              "Ada",
              result.newInstance("com.example.EmailAddress", "ada@corp.example"),
              36);
      Object broken = bean(result, "com.example.SubscriberBean", "setEmail", "nope");
      assertThatValidated(patch(impl, subscriber, broken))
          .isInvalid()
          .hasFieldErrors("name: must not be null", "email: not an email address");
    }

    @Test
    @DisplayName("a domain Optional bridges to a nullable property on a bean projection too")
    void bridgedBeanProjection() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Member",
              """
              package com.example;

              import java.util.Optional;

              public record Member(String id, String name, Optional<String> nickname) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.MemberCardBean",
              """
              package com.example;

              public class MemberCardBean {
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
              "com.example.MemberCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface MemberCardMapping extends MappingSpec<Member, MemberCardBean> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.MemberCardMappingImpl"))
          .contains("wire.setNickname(domain.nickname().orElse(null));")
          .contains(
              ".field(\"nickname\", Validated.validNel(Optional.ofNullable(wire.getNickname())))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.MemberCardMappingImpl");
      Object member = result.newInstance("com.example.Member", "7", "Ada", Optional.of("ace"));
      // An unset nickname is absence, not an error: the bridge is the one carve-out.
      Object renamed = bean(result, "com.example.MemberCardBean", "setName", "Grace");
      assertThatValidated(patch(impl, member, renamed))
          .isValid()
          .hasValue(result.newInstance("com.example.Member", "7", "Grace", Optional.empty()));
    }

    @Test
    @DisplayName("a builder bean projection builds through the builder and patches through getters")
    void builderBeanProjection() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Pin",
              """
              package com.example;

              public record Pin(int x, int y, String label) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.PinDto",
              """
              package com.example;

              public final class PinDto {
                private final int x;
                private final String label;
                private PinDto(int x, String label) { this.x = x; this.label = label; }
                public int getX() { return x; }
                public String getLabel() { return label; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  private int x;
                  private String label;
                  public Builder x(int x) { this.x = x; return this; }
                  public Builder label(String label) { this.label = label; return this; }
                  public PinDto build() { return new PinDto(x, label); }
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.PinMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface PinMapping extends MappingSpec<Pin, PinDto> {}
              """);
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.PinMappingImpl"))
          .contains("var b = PinDto.builder();")
          .contains("b.label(domain.label());")
          .contains("return b.build();")
          .contains(".field(\"label\", hkj$ifPresent(wire.getLabel(), Validated::validNel))")
          .contains("var y = domain.y();")
          .contains("new Pin(x, y, label)");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.PinMappingImpl");
      Object pin = result.newInstance("com.example.Pin", 1, 2, "home");
      Object builder = result.invokeStatic("com.example.PinDto", "builder");
      invoke(builder, "x", 5);
      invoke(builder, "label", "work");
      assertThatValidated(patch(impl, pin, invoke(builder, "build")))
          .isValid()
          .hasValue(result.newInstance("com.example.Pin", 5, 2, "work"));
    }

    @Test
    @DisplayName(
        "nested specs and identity containers on a bean projection locate their nulls inside")
    void nestedAndContainerBeanProjection() throws ReflectiveOperationException {
      JavaFileObject types =
          JavaFileObjects.forSourceString(
              "com.example.Orders",
              """
              package com.example;

              import java.util.List;

              public final class Orders {
                public record Customer(String name, EmailAddress email) {}

                public record CustomerDto(String name, String email) {}

                public record Order(String id, Customer customer, List<String> tags, int qty) {}
              }
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.OrderCardBean",
              """
              package com.example;

              import java.util.List;

              public class OrderCardBean {
                private Orders.CustomerDto customer;
                private List<String> tags;
                public Orders.CustomerDto getCustomer() { return customer; }
                public void setCustomer(Orders.CustomerDto customer) { this.customer = customer; }
                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
              }
              """);
      JavaFileObject customerSpec =
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
              public interface CustomerMapping
                  extends MappingSpec<Orders.Customer, Orders.CustomerDto> {
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
      JavaFileObject orderSpec =
          JavaFileObjects.forSourceString(
              "com.example.OrderCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface OrderCardMapping extends MappingSpec<Orders.Order, OrderCardBean> {}
              """);
      Compilation compilation = compile(EMAIL, types, wire, customerSpec, orderSpec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.OrderCardMappingImpl"))
          .contains(
              ".field(\"customer\", hkj$ifPresent(wire.getCustomer(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parse))")
          .contains(".field(\"tags\", hkj$allPresent(hkj$copyOf(wire.getTags())))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.OrderCardMappingImpl");
      Object customer =
          result.newInstance(
              "com.example.Orders$Customer",
              "Ada",
              result.newInstance("com.example.EmailAddress", "ada@corp.example"));
      Object order =
          result
              .loadClass("com.example.Orders$Order")
              .getDeclaredConstructor(String.class, customer.getClass(), List.class, int.class)
              .newInstance("o-1", customer, List.of("new"), 3);
      Object customerDto =
          result
              .loadClass("com.example.Orders$CustomerDto")
              .getDeclaredConstructor(String.class, String.class)
              .newInstance(null, "nope");
      Object broken =
          bean(
              result,
              "com.example.OrderCardBean",
              "setCustomer",
              customerDto,
              "setTags",
              Arrays.asList("rush", null));
      assertThatValidated(patch(impl, order, broken))
          .isInvalid()
          .hasFieldErrors(
              "customer.name: must not be null",
              "customer.email: not an email address",
              "tags.1: must not be null");
    }

    @Test
    @DisplayName("a bean projection wider than one fields() ladder patches through chunks")
    void wideBeanProjection() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.WideCard",
              "package com.example;\n\npublic record WideCard(String id, "
                  + IntStream.rangeClosed(1, 17)
                      .mapToObj(i -> "String f" + i)
                      .collect(Collectors.joining(", "))
                  + ") {}\n");
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WideCardBean",
              "package com.example;\n\npublic class WideCardBean {\n"
                  + IntStream.rangeClosed(1, 17)
                      .mapToObj(
                          i ->
                              """
                                private String f%1$d;
                                public String getF%1$d() { return f%1$d; }
                                public void setF%1$d(String f%1$d) { this.f%1$d = f%1$d; }
                              """
                                  .formatted(i))
                      .collect(Collectors.joining())
                  + "}\n");
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WideCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface WideCardMapping extends MappingSpec<WideCard, WideCardBean> {}
              """);
      var result = new RuntimeCompilationHelper.CompiledResult(compile(domain, wire, spec));
      Object impl = result.instance("com.example.WideCardMappingImpl");

      Object card =
          result.newInstance(
              "com.example.WideCard",
              IntStream.rangeClosed(0, 17).mapToObj(i -> i == 0 ? "id-1" : "old" + i).toArray());
      Object expected =
          result.newInstance(
              "com.example.WideCard",
              IntStream.rangeClosed(0, 17).mapToObj(i -> i == 0 ? "id-1" : "new" + i).toArray());
      Object filled =
          bean(
              result,
              "com.example.WideCardBean",
              IntStream.rangeClosed(1, 17)
                  .boxed()
                  .flatMap(i -> Stream.of("setF" + i, "new" + i))
                  .toArray());
      // Unset properties in both chunks: the 16-ladder and the trailing singleton.
      Object gapped =
          bean(
              result,
              "com.example.WideCardBean",
              IntStream.rangeClosed(1, 17)
                  .filter(i -> i != 3 && i != 17)
                  .boxed()
                  .flatMap(i -> Stream.of("setF" + i, "new" + i))
                  .toArray());
      assertThatValidated(patch(impl, card, filled)).isValid().hasValue(expected);
      assertThatValidated(patch(impl, card, gapped))
          .isInvalid()
          .hasFieldErrors("f3: must not be null", "f17: must not be null");
    }

    @Test
    @DisplayName(
        "a bean projection compiles warning-free with a boolean getter, a JAXB list, a map and a"
            + " property named domain")
    void beanProjectionCompilesUnderWerror() throws ReflectiveOperationException {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public record Profile(
                  String id,
                  String domain,
                  boolean active,
                  List<String> notes,
                  Map<String, Integer> scores,
                  List<Tag> tags,
                  int version) {
                public record Tag(String value) {}
              }
              """);
      // A JAXB-style getter-only notes list beside setter properties, and an isX() getter.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ProfileBean",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;
              import java.util.Map;

              public class ProfileBean {
                private String domain;
                private boolean active;
                private List<String> notes;
                private Map<String, Integer> scores;
                private List<String> tags;
                public String getDomain() { return domain; }
                public void setDomain(String domain) { this.domain = domain; }
                public boolean isActive() { return active; }
                public void setActive(boolean active) { this.active = active; }
                public List<String> getNotes() {
                  if (notes == null) { notes = new ArrayList<>(); }
                  return notes;
                }
                public Map<String, Integer> getScores() { return scores; }
                public void setScores(Map<String, Integer> scores) { this.scores = scores; }
                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ProfileMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ProfileMapping extends MappingSpec<Profile, ProfileBean> {
                default ValidatedPrism<String, Profile.Tag> tags() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new Profile.Tag(raw)), Profile.Tag::value);
                }
              }
              """);
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.ProfileMappingImpl"))
          .contains("wire.setActive(domain.active());")
          .contains("wire.getNotes().addAll(hkj$copyOf(domain.notes()));")
          .contains(".field(\"domain\", hkj$ifPresent(wire.getDomain(), Validated::validNel))")
          .contains(".field(\"active\", Validated.validNel(wire.isActive()))")
          .contains(".field(\"notes\", hkj$allPresent(hkj$copyOf(wire.getNotes())))")
          .contains(".field(\"scores\", hkj$valuesPresent(hkj$copyOf(wire.getScores())))")
          .contains(".field(\"tags\", hkj$ifPresent(wire.getTags(), tags()::parseAll))")
          // A component named after the method parameter takes a suffixed lambda parameter.
          .contains("var version = domain.version();")
          .contains("new Profile(id, domain_, active, notes, scores, tags, version)");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.ProfileMappingImpl");
      Object profile =
          result
              .loadClass("com.example.Profile")
              .getDeclaredConstructors()[0]
              .newInstance(
                  "p-1",
                  "corp.example",
                  true,
                  List.of("vip"),
                  Map.of("q1", 7),
                  List.of(result.newInstance("com.example.Profile$Tag", "gold")),
                  3);
      // The projection identity law: writing the domain's own projection back changes nothing.
      assertThatValidated(patch(impl, profile, invoke(impl, "build", profile)))
          .isValid()
          .hasValue(profile);
    }

    @Test
    @DisplayName(
        "a getter-only bean narrower than the domain maps parse-only, so it misses components"
            + " rather than projecting")
    void getterOnlyNarrowBeanMapsParseOnly() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ReadOnlyCard",
              """
              package com.example;

              public class ReadOnlyCard {
                private String name;
                public String getName() { return name; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ReadOnlyCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ReadOnlyCardMapping extends MappingSpec<Employee, ReadOnlyCard> {}
              """);
      // Nothing writes the bean, so it maps parse-only, and a parse has no projection to fall back
      // on: every domain component needs a getter.
      Compilation compilation = compile(EMPLOYEE, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadNoteContaining("'ReadOnlyCard' maps parse-only");
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Employee.department' has no wire counterpart named 'department'");
    }

    /** A bean from its no-args constructor, with each named setter applied to its value in turn. */
    private static Object bean(
        RuntimeCompilationHelper.CompiledResult result, String className, Object... setterValues)
        throws ReflectiveOperationException {
      Object bean = result.loadClass(className).getDeclaredConstructor().newInstance();
      for (int i = 0; i < setterValues.length; i += 2) {
        invoke(bean, (String) setterValues[i], setterValues[i + 1]);
      }
      return bean;
    }

    private static Validated<NonEmptyList<FieldError>, Object> patch(
        Object impl, Object domain, Object wire) {
      return validated(invoke(impl, "patch", domain, wire));
    }
  }

  @Nested
  @DisplayName("Constructor accessibility")
  class ConstructorAccessibility {

    @Test
    @DisplayName("a same-package bean with a package-private constructor is usable")
    void samePackageNonPublicConstructor() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.D",
              """
              package com.example;

              public record D(String a) {}
              """);
      // Package-private class (so an implicit package-private constructor), co-located with the
      // spec.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.SameBean",
              """
              package com.example;

              class SameBean {
                private String a;
                public String getA() { return a; }
                public void setA(String a) { this.a = a; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SameMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SameMapping extends MappingSpec<D, SameBean> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.SameMappingImpl"))
          .contains("SameBean wire = new SameBean();");
    }

    @Test
    @DisplayName(
        "a cross-package bean with a non-public constructor is never built, so it maps parse-only")
    void crossPackageNonPublicConstructor() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.D",
              """
              package com.example;

              public record D(String a) {}
              """);
      // A public class in another package whose no-args constructor is package-private: the impl
      // (emitted in com.example) could not call `new Foreign()`.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.other.Foreign",
              """
              package com.other;

              public class Foreign {
                Foreign() {}
                private String a;
                public String getA() { return a; }
                public void setA(String a) { this.a = a; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ForeignMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface ForeignMapping extends MappingSpec<D, com.other.Foreign> {}
              """);

      // The generated Impl could not call `new Foreign()`, so nothing writes the bean: it maps
      // parse-only rather than miscompiling, and the note names the constructor to change.
      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "'Foreign' has setters, but no no-args constructor the generated Impl"
                  + " can call from package 'com.example'");
      Assertions.assertThat(generatedSource(compilation, "com.example.ForeignMappingImpl"))
          .doesNotContain("new Foreign()");
    }
  }

  @Nested
  @DisplayName("Diagnostics")
  class Diagnostics {

    private static final JavaFileObject D =
        JavaFileObjects.forSourceString(
            "com.example.D",
            """
            package com.example;

            public record D(String a) {}
            """);

    private static JavaFileObject spec(String body) {
      return JavaFileObjects.forSourceString(
          "com.example.M",
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          """
              + body);
    }

    @Test
    @DisplayName("a bean whose getter and setter disagree on type is rejected")
    void getterSetterTypeMismatch() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WDto",
              """
              package com.example;

              public class WDto {
                public String getA() { return null; }
                public void setA(int a) {}
              }
              """);
      Compilation compilation =
          compile(D, wire, spec("public interface M extends MappingSpec<D, WDto> {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("bean property 'a' on 'WDto' is read and written at different types");
      assertThat(compilation)
          .hadErrorContaining("Align the getter and its setter (or builder setter)");
    }

    @Test
    @DisplayName("a bean that is neither constructible nor a builder maps parse-only")
    void noConstructionStrategy() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WDto",
              """
              package com.example;

              public class WDto {
                private final String a;
                public WDto(String a) { this.a = a; }
                public String getA() { return a; }
                public void setA(String a) {}
              }
              """);
      Compilation compilation =
          compile(D, wire, spec("public interface M extends MappingSpec<D, WDto> {}"));
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'WDto' maps parse-only");
    }

    @Test
    @DisplayName("a wire that is a plain interface is rejected as neither record nor bean")
    void wireIsInterface() {
      Compilation compilation =
          compile(D, spec("public interface M extends MappingSpec<D, Runnable> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is neither a record nor a bean-shaped class");
      assertThat(compilation).hadErrorContaining("Use a record, or a concrete bean class");
    }

    @Test
    @DisplayName("a wire that is an abstract class is rejected as neither record nor bean")
    void wireIsAbstractClass() {
      JavaFileObject abstractWire =
          JavaFileObjects.forSourceString(
              "com.example.AbstractDto",
              """
              package com.example;

              public abstract class AbstractDto {
                public abstract String getA();
              }
              """);
      Compilation compilation =
          compile(
              D, abstractWire, spec("public interface M extends MappingSpec<D, AbstractDto> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is neither a record nor a bean-shaped class");
    }

    @Test
    @DisplayName("a generic bean wire is rejected")
    void genericBeanWire() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BoxDto",
              """
              package com.example;

              public class BoxDto<T> {
                private String a;
                public String getA() { return a; }
                public void setA(String a) { this.a = a; }
              }
              """);
      Compilation compilation =
          compile(D, wire, spec("public interface M extends MappingSpec<D, BoxDto<String>> {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'BoxDto' is generic, which a bean-wire mapping does not support yet.");
    }
  }

  @Nested
  @DisplayName("Protobuf-java messages, which are not supported yet")
  class ProtobufMessages {

    // Stands in for protobuf-java's root interface, which the processor looks up by name.
    private static final JavaFileObject MESSAGE_LITE =
        JavaFileObjects.forSourceString(
            "com.google.protobuf.MessageLite",
            """
            package com.google.protobuf;

            public interface MessageLite {}
            """);

    private static final String RECORD_FIX =
        "by hand to a record, and map that instead: a protobuf-java message is not supported yet";

    private static final String PATCH_FIX =
        "by hand to a PATCH bean whose getters answer null until set, and map that instead";

    private static JavaFileObject specs(String name, String body) {
      return JavaFileObjects.forSourceString(
          "com.example." + name,
          """
          package com.example;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.annotations.Unmapped;
          import org.higherkindedj.optics.annotations.UpdateSpec;

          """
              + body);
    }

    private static final JavaFileObject USER =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            public record User(String name) {}
            """);

    // A string field's accessors as protobuf-java generates them, companions included.
    private static JavaFileObject userMessage(String implementsClause) {
      return JavaFileObjects.forSourceString(
          "com.example.UserMessage",
          """
          package com.example;

          public final class UserMessage %s {
            public static final class Bytes {}
            public static final class UnknownFields {}
            private final String name;
            private UserMessage(String name) { this.name = name; }
            public String getName() { return name; }
            public Bytes getNameBytes() { return new Bytes(); }
            public UnknownFields getUnknownFields() { return new UnknownFields(); }
            public static Builder newBuilder() { return new Builder(); }
            public static final class Builder {
              private String name;
              public Builder setName(String name) { this.name = name; return this; }
              public Builder setNameBytes(Bytes bytes) { return this; }
              public Builder setUnknownFields(UnknownFields fields) { return this; }
              public UserMessage build() { return new UserMessage(name); }
            }
          }
          """
              .formatted(implementsClause));
    }

    @Test
    @DisplayName("the companions a bean pairs up are named as the extras it leaves unfilled")
    void companionsNamed() {
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              USER,
              userMessage(""),
              specs(
                  "UserMapping",
                  """
                  @GenerateMapping
                  public interface UserMapping extends MappingSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'UserMessage' has more components than 'User', leaving [nameBytes, unknownFields]"
                  + " unfilled.");
      assertThat(compilation).hadErrorContaining("declare derived fields");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("protobuf"));
    }

    @Test
    @DisplayName("a message names its companions too, and is pointed at a record or a PATCH bean")
    void messageCompanions() {
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              USER,
              userMessage("implements com.google.protobuf.MessageLite"),
              specs(
                  "UserMappings",
                  """
                  @GenerateMapping
                  interface UserMapping extends MappingSpec<User, UserMessage> {}

                  @GenerateMapping
                  interface UserPatch extends UpdateSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'UserMessage' has more components than 'User', leaving [nameBytes, unknownFields]"
                  + " unfilled.");
      assertThat(compilation).hadErrorContaining("Convert 'UserMessage' " + RECORD_FIX);
      assertThat(compilation).hadErrorContaining("getXBytes() beside a string field");
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("declare derived fields"));
      assertThat(compilation)
          .hadErrorContaining("the wire property 'nameBytes' names no component of User.");
      assertThat(compilation).hadErrorContaining("Convert 'UserMessage' " + PATCH_FIX);
    }

    @Test
    @DisplayName("a message narrower than the domain is refused with the same pointer")
    void narrowerMessage() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.User",
              """
              package com.example;

              public record User(String name, String email, String bio, int age) {}
              """);
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              domain,
              userMessage("implements com.google.protobuf.MessageLite"),
              specs(
                  "UserMapping",
                  """
                  @GenerateMapping
                  public interface UserMapping extends MappingSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("projection field 'UserMessage.nameBytes' has no domain source.");
      assertThat(compilation).hadErrorContaining("Convert 'UserMessage' " + RECORD_FIX);
    }

    private static final JavaFileObject MASK =
        JavaFileObjects.forSourceString(
            "com.example.Mask",
            """
            package com.example;

            import java.util.List;

            public record Mask(List<String> paths) {}
            """);

    // A repeated field's accessors as protobuf-java generates them: read as a list, and written
    // only through adders, with or without the full runtime's unknown fields beside it.
    private static JavaFileObject maskMessage(
        String unknownFieldsGetter, String unknownFieldsSetter) {
      return JavaFileObjects.forSourceString(
          "com.example.MaskMessage",
          """
          package com.example;

          import java.util.List;

          public final class MaskMessage implements com.google.protobuf.MessageLite {
            public static final class UnknownFields {}
            private final List<String> paths;
            private MaskMessage(List<String> paths) { this.paths = paths; }
            public List<String> getPathsList() { return paths; }
            %s
            public static Builder newBuilder() { return new Builder(); }
            public static final class Builder {
              private List<String> paths = List.of();
              public Builder addAllPaths(Iterable<String> paths) { return this; }
              %s
              public MaskMessage build() { return new MaskMessage(paths); }
            }
          }
          """
              .formatted(unknownFieldsGetter, unknownFieldsSetter));
    }

    @Test
    @DisplayName("a repeated field, which has no setter, is refused with the same pointer")
    void repeatedField() {
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              MASK,
              maskMessage(
                  "public UnknownFields getUnknownFields() { return new UnknownFields(); }",
                  "public Builder setUnknownFields(UnknownFields fields) { return this; }"),
              specs(
                  "MaskMapping",
                  """
                  @GenerateMapping
                  public interface MaskMapping extends MappingSpec<Mask, MaskMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("domain field 'Mask.paths' has no wire counterpart named 'paths'.");
      assertThat(compilation).hadErrorContaining("Convert 'MaskMessage' " + RECORD_FIX);
      assertThat(compilation).hadErrorContaining("a repeated or map field, having no setter");
    }

    @Test
    @DisplayName(
        "a message that pairs no accessor at all is refused with the pointer on either tier")
    void nothingPaired() {
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              MASK,
              maskMessage("", ""),
              specs(
                  "MaskMappings",
                  """
                  @GenerateMapping
                  interface MaskMapping extends MappingSpec<Mask, MaskMessage> {}

                  @GenerateMapping
                  interface MaskPatch extends UpdateSpec<Mask, MaskMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'MaskMessage' is not a usable bean-shaped wire: no property it reads is one it can"
                  + " write.");
      assertThat(compilation).hadErrorContaining("Convert 'MaskMessage' " + RECORD_FIX);
      assertThat(compilation).hadErrorContaining("Convert 'MaskMessage' " + PATCH_FIX);
    }

    @Test
    @DisplayName("a map field's getter, which has no setter, is refused with the pointer")
    void mapField() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Labels",
              """
              package com.example;

              import java.util.Map;

              public record Labels(Map<String, String> fields) {}
              """);
      // A map field keeps a deprecated getter named after the field beside getXMap().
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LabelsMessage",
              """
              package com.example;

              import java.util.Map;

              public final class LabelsMessage implements com.google.protobuf.MessageLite {
                public static final class UnknownFields {}
                private final Map<String, String> fields;
                private LabelsMessage(Map<String, String> fields) { this.fields = fields; }
                public Map<String, String> getFields() { return fields; }
                public Map<String, String> getFieldsMap() { return fields; }
                public UnknownFields getUnknownFields() { return new UnknownFields(); }
                public static Builder newBuilder() { return new Builder(); }
                public static final class Builder {
                  private Map<String, String> fields = Map.of();
                  public Builder putAllFields(Map<String, String> fields) { return this; }
                  public Builder setUnknownFields(UnknownFields fields) { return this; }
                  public LabelsMessage build() { return new LabelsMessage(fields); }
                }
              }
              """);
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              domain,
              wire,
              specs(
                  "LabelsMappings",
                  """
                  @GenerateMapping
                  interface LabelsMapping extends MappingSpec<Labels, LabelsMessage> {}

                  @GenerateMapping
                  interface LabelsPatch extends UpdateSpec<Labels, LabelsMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'fields' on 'LabelsMessage' has a getter, getFields(), but no builder"
                  + " setter");
      assertThat(compilation).hadErrorContaining("Convert 'LabelsMessage' " + RECORD_FIX);
      assertThat(compilation).hadErrorContaining("Convert 'LabelsMessage' " + PATCH_FIX);
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("Add a setter for"));
    }

    @Test
    @DisplayName("a marker on a paired companion, and a primitive PATCH property, get the pointer")
    void markerAndPrimitive() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Stamp",
              """
              package com.example;

              public record Stamp(Long seconds) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.StampMessage",
              """
              package com.example;

              public final class StampMessage implements com.google.protobuf.MessageLite {
                public static final class UnknownFields {}
                private final long seconds;
                private StampMessage(long seconds) { this.seconds = seconds; }
                public long getSeconds() { return seconds; }
                public UnknownFields getUnknownFields() { return new UnknownFields(); }
                public static Builder newBuilder() { return new Builder(); }
                public static final class Builder {
                  private long seconds;
                  public Builder setSeconds(long seconds) { this.seconds = seconds; return this; }
                  public Builder setUnknownFields(UnknownFields fields) { return this; }
                  public StampMessage build() { return new StampMessage(seconds); }
                }
              }
              """);
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              domain,
              wire,
              specs(
                  "StampMappings",
                  """
                  @GenerateMapping
                  interface StampMapping extends MappingSpec<Stamp, StampMessage> {
                    @Unmapped
                    StampMessage.UnknownFields unknownFields();
                  }

                  @GenerateMapping
                  interface StampPatch extends UpdateSpec<Stamp, StampMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Unmapped method 'unknownFields' names a property 'StampMessage'");
      assertThat(compilation).hadErrorContaining("Convert 'StampMessage' " + RECORD_FIX);
      assertThat(compilation)
          .hadErrorContaining("the wire property 'seconds' is primitive and can never be absent.");
      assertThat(compilation).hadErrorContaining("Convert 'StampMessage' " + PATCH_FIX);
    }

    @Test
    @DisplayName(
        "a message whose builder inherits build() from a generic base names its companions")
    void inheritedBuild() {
      // The lite runtime's builders inherit build() from a generic base, returning its variable,
      // which the builder instantiates as the message.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.UserMessage",
              """
              package com.example;

              public final class UserMessage implements com.google.protobuf.MessageLite {
                public static final class Bytes {}
                public abstract static class LiteBuilder<M> {
                  public final M build() { return make(); }
                  protected abstract M make();
                }
                private final String name;
                private UserMessage(String name) { this.name = name; }
                public String getName() { return name; }
                public Bytes getNameBytes() { return new Bytes(); }
                public static Builder newBuilder() { return new Builder(); }
                public static final class Builder extends LiteBuilder<UserMessage> {
                  private String name;
                  public Builder setName(String name) { this.name = name; return this; }
                  public Builder setNameBytes(Bytes bytes) { return this; }
                  protected UserMessage make() { return new UserMessage(name); }
                }
              }
              """);
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              USER,
              wire,
              specs(
                  "UserMapping",
                  """
                  @GenerateMapping
                  public interface UserMapping extends MappingSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'UserMessage' has more components than 'User', leaving [nameBytes] unfilled.");
      assertThat(compilation).hadErrorContaining("Convert 'UserMessage' " + RECORD_FIX);
    }

    @Test
    @DisplayName("a message whose builder the mapper does not find is pointed at a record too")
    void unrecognisedBuilder() {
      // A builder whose only terminal is buildPartial() has no build() to call.
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.UserMessage",
              """
              package com.example;

              public final class UserMessage implements com.google.protobuf.MessageLite {
                private final String name;
                private UserMessage(String name) { this.name = name; }
                public String getName() { return name; }
                public static Builder newBuilder() { return new Builder(); }
                public static final class Builder {
                  private String name;
                  public Builder setName(String name) { this.name = name; return this; }
                  public UserMessage buildPartial() { return new UserMessage(name); }
                }
              }
              """);
      Compilation compilation =
          compile(
              MESSAGE_LITE,
              USER,
              wire,
              specs(
                  "UserMappings",
                  """
                  @GenerateMapping
                  interface UserMapping extends MappingSpec<User, UserMessage> {}

                  @GenerateMapping
                  interface UserPatch extends UpdateSpec<User, UserMessage> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadNoteContaining("'UserMessage' maps parse-only");
      assertThat(compilation).hadNoteContaining("Convert 'UserMessage' " + RECORD_FIX);
      assertThat(compilation)
          .hadErrorContaining("'UserMessage' has getters but no way to be written");
      assertThat(compilation).hadErrorContaining("Convert 'UserMessage' " + PATCH_FIX);
    }
  }

  @Nested
  @DisplayName("Property-analyser branch coverage")
  class AnalyserBranchCoverage {

    private static final JavaFileObject DOM =
        JavaFileObjects.forSourceString(
            "com.example.Dom",
            """
            package com.example;

            public record Dom(String a) {}
            """);

    /** Compiles a bean-wire spec mapping {@code Dom} to the given wire class. */
    private Compilation analyse(String wireName, String wireSource) {
      JavaFileObject wire = JavaFileObjects.forSourceString("com.example." + wireName, wireSource);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Map" + wireName,
              "package com.example;\n"
                  + "import org.higherkindedj.optics.annotations.GenerateMapping;\n"
                  + "import org.higherkindedj.optics.annotations.MappingSpec;\n"
                  + "@GenerateMapping public interface Map"
                  + wireName
                  + " extends MappingSpec<Dom, "
                  + wireName
                  + "> {}\n");
      return compile(DOM, wire, spec);
    }

    @Test
    @DisplayName("decapitalise handles the empty, acronym, single-char and normal cases")
    void decapitaliseEdgeCases() {
      Assertions.assertThat(BeanPropertyAnalyser.decapitalise("")).isEmpty();
      Assertions.assertThat(BeanPropertyAnalyser.decapitalise("URL")).isEqualTo("URL");
      Assertions.assertThat(BeanPropertyAnalyser.decapitalise("Name")).isEqualTo("name");
      Assertions.assertThat(BeanPropertyAnalyser.decapitalise("A")).isEqualTo("a");
    }

    @Test
    @DisplayName("accessorSuffix is what decapitalise reads back as the property")
    void accessorSuffixInvertsDecapitalise() {
      Assertions.assertThat(BeanPropertyAnalyser.accessorSuffix("email")).isEqualTo("Email");
      Assertions.assertThat(BeanPropertyAnalyser.accessorSuffix("URL")).isEqualTo("URL");
      Assertions.assertThat(BeanPropertyAnalyser.accessorSuffix("x")).isEqualTo("X");
      // Capitalised, 'eMail' would read back as 'EMail'.
      Assertions.assertThat(BeanPropertyAnalyser.accessorSuffix("eMail")).isEqualTo("eMail");
      Assertions.assertThat(List.of("email", "URL", "x", "eMail"))
          .allMatch(
              property ->
                  BeanPropertyAnalyser.decapitalise(BeanPropertyAnalyser.accessorSuffix(property))
                      .equals(property));
    }

    @Test
    @DisplayName("non-accessor methods (void, too-short, wrong-shape) are ignored, not misread")
    void nonAccessorMethodsIgnored() {
      // Exercises the getter/setter recognition guards: a void method and a wrong-return is-getter
      // are not getters; a getter-only String/int property is not a JAXB collection; "get"/"is"/
      // "set" that are too short, and a non-get/is/set method, are all skipped.
      Compilation compilation =
          analyse(
              "OddBean",
              """
              package com.example;

              public class OddBean {
                private String a;
                private String b;
                private int c;
                public String getA() { return a; }
                public void setA(String a) { this.a = a; }
                public String getB() { return b; }
                public int getC() { return c; }
                public void reset() {}
                public String get() { return ""; }
                public String describe() { return ""; }
                public String isReady() { return ""; }
                public boolean is() { return true; }
                public void set(String x) {}
              }
              """);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.MapOddBean"))
          .contains("wire.setA(domain.a());");
    }

    @Test
    @DisplayName("a builder whose setter type disagrees with the getter is rejected")
    void builderTypeMismatch() {
      Compilation compilation =
          analyse(
              "B8",
              """
              package com.example;

              public class B8 {
                private B8() {}
                private String a;
                public String getA() { return a; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public Builder a(int x) { return this; }
                  public B8 build() { return new B8(); }
                }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("bean property 'a' on 'B8' is read and written at different types");
    }

    @Test
    @DisplayName("a builder covering only some getters maps the covered ones")
    void builderPartialCoverage() {
      Compilation compilation =
          analyse(
              "B9",
              """
              package com.example;

              public class B9 {
                private B9() {}
                private String a;
                private String b;
                public String getA() { return a; }
                public String getB() { return b; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public Builder a(String v) { return this; }
                  public B9 build() { return new B9(); }
                }
              }
              """);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.MapB9"))
          .contains("b.a(domain.a())");
    }

    @Test
    @DisplayName("a builder whose setters match no getter is unusable")
    void builderNoMatchingProperty() {
      Compilation compilation =
          analyse(
              "B10",
              """
              package com.example;

              public class B10 {
                private B10() {}
                private String a;
                public String getA() { return a; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public Builder x(String v) { return this; }
                  public B10 build() { return new B10(); }
                }
              }
              """);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B10' is not a usable bean-shaped wire");
      assertThat(compilation).hadErrorContaining("It reads [a] and writes [x].");
    }

    @Test
    @DisplayName("a builder factory returning a non-class type is not a builder")
    void builderFactoryNonDeclaredReturn() {
      Compilation compilation =
          analyse(
              "B3",
              """
              package com.example;

              public class B3 {
                private B3() {}
                private String a;
                public String getA() { return a; }
                public static int builder() { return 0; }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B3' maps parse-only");
    }

    @Test
    @DisplayName("a builder factory with parameters is not a builder")
    void builderFactoryWithParameters() {
      Compilation compilation =
          analyse(
              "B4",
              """
              package com.example;

              public class B4 {
                private B4() {}
                private String a;
                public String getA() { return a; }
                public static Builder builder(int seed) { return new Builder(); }
                public static final class Builder {
                  public B4 build() { return new B4(); }
                }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B4' maps parse-only");
    }

    @Test
    @DisplayName("a non-static builder factory is not a builder")
    void builderFactoryNonStatic() {
      Compilation compilation =
          analyse(
              "B5",
              """
              package com.example;

              public class B5 {
                private B5() {}
                private String a;
                public String getA() { return a; }
                public Builder builder() { return new Builder(); }
                public static final class Builder {
                  public B5 build() { return new B5(); }
                }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B5' maps parse-only");
    }

    @Test
    @DisplayName("a non-public builder factory is not a builder")
    void builderFactoryNonPublic() {
      Compilation compilation =
          analyse(
              "B6",
              """
              package com.example;

              public class B6 {
                private B6() {}
                private String a;
                public String getA() { return a; }
                static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public B6 build() { return new B6(); }
                }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B6' maps parse-only");
    }

    @Test
    @DisplayName("a builder whose build() takes parameters does not yield the wire")
    void builderBuildWithParameters() {
      Compilation compilation =
          analyse(
              "B7a",
              """
              package com.example;

              public class B7a {
                private B7a() {}
                private String a;
                public String getA() { return a; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public B7a build(int x) { return new B7a(); }
                }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B7a' maps parse-only");
    }

    @Test
    @DisplayName("a builder whose build() returns another type does not yield the wire")
    void builderBuildWrongReturn() {
      Compilation compilation =
          analyse(
              "B7b",
              """
              package com.example;

              public class B7b {
                private B7b() {}
                private String a;
                public String getA() { return a; }
                public static Builder builder() { return new Builder(); }
                public static final class Builder {
                  public String build() { return ""; }
                }
              }
              """);
      // Not a builder, so nothing writes the bean: it maps parse-only.
      assertThat(compilation).succeeded();
      assertThat(compilation).hadNoteContaining("'B7b' maps parse-only");
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

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> validated(Object value) {
    return (Validated<NonEmptyList<FieldError>, Object>) value;
  }

  @Nested
  @DisplayName("Inherited Properties")
  class InheritedProperties {

    @Test
    @DisplayName("a bean inheriting a property from a generic base maps it")
    void beanInheritingFromAGenericBase() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.example.BaseDto",
              """
              package com.example;

              public class BaseDto<T> {
                  private T id;
                  public T getId() { return id; }
                  public void setId(T id) { this.id = id; }
              }
              """);

      final var dto =
          JavaFileObjects.forSourceString(
              "com.example.InheritedDto",
              """
              package com.example;

              public class InheritedDto extends BaseDto<String> {
                  private String name;
                  public InheritedDto() {}
                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
              }
              """);

      final var domain =
          JavaFileObjects.forSourceString(
              "com.example.Inherited",
              """
              package com.example;

              public record Inherited(String id, String name) {}
              """);

      final var spec =
          JavaFileObjects.forSourceString(
              "com.example.InheritedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface InheritedMapping extends MappingSpec<Inherited, InheritedDto> {}
              """);

      // getId() is declared 'T getId()' on BaseDto. Read off that element it is T, which matches no
      // domain field and whose remedy - a ValidatedPrism<T, String> - names a variable the author
      // cannot write; read under InheritedDto it is String.
      Compilation compilation = compile(base, dto, domain, spec);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.InheritedMappingImpl");
    }

    @Test
    @DisplayName("a property whose inherited accessors disagree names where they are declared")
    void inheritedAccessorsThatDisagreeNameTheirDeclaringType() {
      final var base =
          JavaFileObjects.forSourceString(
              "com.example.SkewBase",
              """
              package com.example;

              public class SkewBase<R, W> {
                  private R id;
                  public R getId() { return id; }
                  public void setId(W id) {}
              }
              """);

      final var dto =
          JavaFileObjects.forSourceString(
              "com.example.SkewDto",
              """
              package com.example;

              public class SkewDto extends SkewBase<String, Integer> {
                  public SkewDto() {}
              }
              """);

      final var domain =
          JavaFileObjects.forSourceString(
              "com.example.SkewDom",
              """
              package com.example;

              public record SkewDom(String id) {}
              """);

      final var spec =
          JavaFileObjects.forSourceString(
              "com.example.SkewMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface SkewMapping extends MappingSpec<SkewDom, SkewDto> {}
              """);

      Compilation compilation = compile(base, dto, domain, spec);

      assertThat(compilation).failed();
      // Read under SkewDto the two are String and Integer, so the skew is real - but there is
      // nothing in SkewDto to align, and the message has to say where the accessors live.
      assertThat(compilation).hadErrorContaining("(declared on 'SkewBase')");
      assertThat(compilation).hadErrorContaining("(String vs Integer)");
    }
  }
}

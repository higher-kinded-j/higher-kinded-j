// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MappingProcessor - bean-shaped wire targets (#628)")
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
          .contains(".field(\"name\", ifPresent(wire.getName(), Validated::validNel))")
          .contains(".field(\"email\", ifPresent(wire.getEmail(), email()::parse))")
          .contains(".field(\"age\", Validated.validNel(wire.getAge()))")
          .contains("private static <S, A> Validated<NonEmptyList<FieldError>, A> ifPresent(")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("round-trips a domain value through the bean")
    void roundTrips() {
      Compilation compilation = compile(EMAIL, USER, USER_DTO, USER_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl =
            result.loadClass("com.example.UserMappingImpl").getField("INSTANCE").get(null);
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object user = result.newInstance("com.example.User", "Ada", email, 42);

        Object dto = invoke(impl, "build", user);
        Assertions.assertThat(invoke(dto, "getName")).isEqualTo("Ada");
        Assertions.assertThat(invoke(dto, "getEmail")).isEqualTo("ada@corp.example");
        Assertions.assertThat(invoke(dto, "getAge")).isEqualTo(42);

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
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
        Object impl =
            result.loadClass("com.example.UserMappingImpl").getField("INSTANCE").get(null);
        // name null and email null: both located, and the null email never reaches email().parse
        // (which would throw), proving the guard runs first.
        Object dto = result.loadClass("com.example.UserDto").getDeclaredConstructor().newInstance();

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("name"), "must not be null"),
                new FieldError(List.of("email"), "must not be null"));
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
          .contains("ifPresent(wire.getName(), Validated::validNel)")
          .contains("ifPresent(wire.getRole(), Validated::validNel)");
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
          .contains(".field(\"link\", ifPresent(wire.getURL(), Validated::validNel))");
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
          .contains(".field(\"first\", ifPresent(wire.getFirst(), Validated::validNel))")
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
          .contains(".field(\"all\", ifPresent(wire.getAll(), all()::parseAll))")
          .contains(".field(\"tagged\", ifPresent(wire.getTagged(), tagged()::parseValues))")
          .contains(".field(\"primary\", ifPresent(wire.getPrimary(), o -> o.map(v ->");
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
              ".field(\"customer\","
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism().parse(wire.customer()))");
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
          .contains(".field(\"label\", ifPresent(wire.getLabel(), Validated::validNel))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl =
            result.loadClass("com.example.PointMappingImpl").getField("INSTANCE").get(null);
        Object point = result.newInstance("com.example.Point", 3, "origin");
        Object dto = invoke(impl, "build", point);
        Assertions.assertThat(invoke(dto, "getX")).isEqualTo(3);
        Assertions.assertThat(invoke(dto, "getLabel")).isEqualTo("origin");

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.get()).isEqualTo(point);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a protobuf-style bean builds through newBuilder() and setX setters")
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

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.DocMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.setTitle(domain.title());")
          .contains("wire.getTags().addAll(domain.tags());")
          .contains(".field(\"tags\", ifPresent(wire.getTags(), Validated::validNel))");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.loadClass("com.example.DocMappingImpl").getField("INSTANCE").get(null);
        Object doc =
            result
                .loadClass("com.example.Doc")
                .getDeclaredConstructor(String.class, List.class)
                .newInstance("spec", List.of("a", "b"));
        Object dto = invoke(impl, "build", doc);
        Assertions.assertThat((List<?>) invoke(dto, "getTags")).isEqualTo(List.of("a", "b"));

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.get()).isEqualTo(doc);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
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
  @DisplayName("Optional bridging")
  class OptionalBridging {

    @Test
    @DisplayName(
        "a domain Optional maps to a nullable bean property, skipping the write when empty")
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
          .contains("domain.bio().ifPresent(v -> wire.setBio(v));")
          .contains(".field(\"bio\", Validated.validNel(Optional.ofNullable(wire.getBio())))")
          .doesNotContain("asIso");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl =
            result.loadClass("com.example.ProfileMappingImpl").getField("INSTANCE").get(null);
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

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsedEmpty =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dtoEmpty);
        Assertions.assertThat(parsedEmpty.get()).isEqualTo(emptyProfile);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
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
          .contains("domain.email().map(email()::build).ifPresent(v -> wire.setEmail(v));")
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
        Object impl =
            result.loadClass("com.example.AccountMappingImpl").getField("INSTANCE").get(null);
        Object dto = result.newInstance("com.example.AccountDto");
        invoke(dto, "setEmail", "not-an-email");

        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsed =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", dto);
        Assertions.assertThat(parsed.getError().toJavaList())
            .containsExactly(new FieldError(List.of("email"), "not an email address"));

        // An absent (null) email is valid: it bridges to Optional.empty.
        Object emptyDto = result.newInstance("com.example.AccountDto");
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> parsedEmpty =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", emptyDto);
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
  }

  @Nested
  @DisplayName("Projection tier")
  class ProjectionTier {

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
          .doesNotContain("parse(")
          .doesNotContain("asValidatedPrism");
    }

    @Test
    @DisplayName("a reference-typed bean projection is deferred to the validated patch tier")
    void referenceProjectionDeferred() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Employee",
              """
              package com.example;

              public record Employee(String name, String department, int age) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeCardDto",
              """
              package com.example;

              public class EmployeeCardDto {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.EmployeeCardMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}
              """);

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'EmployeeCardDto' is a bean projection of 'Employee' with a reference-typed property");
      assertThat(compilation).hadErrorContaining("maps as a validated patch");
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
    @DisplayName("a cross-package bean with a non-public constructor is rejected, not miscompiled")
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

      Compilation compilation = compile(domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'Foreign' is not a usable bean-shaped wire");
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
    @DisplayName("a bean that is neither constructible nor a builder is rejected")
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'WDto' is not a usable bean-shaped wire");
      assertThat(compilation).hadErrorContaining("no construction strategy fits it");
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
          .hadErrorContaining("'BoxDto' is generic, which this mapper does not support");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B3' is not a usable bean-shaped wire");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B4' is not a usable bean-shaped wire");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B5' is not a usable bean-shaped wire");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B6' is not a usable bean-shaped wire");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B7a' is not a usable bean-shaped wire");
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
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'B7b' is not a usable bean-shaped wire");
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

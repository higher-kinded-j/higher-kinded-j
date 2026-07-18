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
          .hadErrorContaining(
              "bean property 'a' on 'WDto' has a getter and setter of different types");
      assertThat(compilation).hadErrorContaining("Align the getter and setter types on the bean");
    }

    @Test
    @DisplayName("a bean with no public no-args constructor is rejected")
    void noNoArgsConstructor() {
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
      assertThat(compilation).hadErrorContaining("has no public no-args constructor");
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

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.laws.MappingLaws;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The in-repo law guarantee for {@code @GenerateMapping}: every emission tier the generator can
 * produce is compiled here, its optics obtained reflectively, and law-checked through the published
 * {@code hkj-test} harness ({@code MappingLaws} delegating to {@code IsoLaws}/{@code
 * LensLaws}/{@code ValidatedPrismLaws}).
 *
 * <p>There is deliberately no law-test codegen: the processor writes into the compiled source set,
 * where the test-scope harness is not on the classpath. Instead this suite verifies every tier
 * in-repo, and users law-check their own specs with one {@code MappingLaws} call from their tests,
 * where {@code hkj-test} legitimately lives.
 */
@DisplayName("Generated mappings are law-checked through the published harness - every tier")
class GeneratedMappingLawsTest {

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.example.EmailAddress",
          """
          package com.example;

          public record EmailAddress(String value) {}
          """);

  /** The recurring fallible leaf: accepts strings containing {@code @}, rejects the rest. */
  private static final String EMAIL_PRISM =
      """
        private static ValidatedPrism<String, EmailAddress> emailPrism() {
          return ValidatedPrism.of(
              raw ->
                  raw.contains("@")
                      ? Validated.validNel(new EmailAddress(raw))
                      : Validated.invalidNel(FieldError.of("not an email address")),
              EmailAddress::value);
        }
      """;

  private static RuntimeCompilationHelper.CompiledResult compileMapping(JavaFileObject... sources) {
    Compilation compilation = javac().withProcessors(new MappingProcessor()).compile(sources);
    assertThat(compilation).succeeded();
    return new RuntimeCompilationHelper.CompiledResult(compilation);
  }

  private static Object implInstance(
      RuntimeCompilationHelper.CompiledResult result, String implClass) {
    try {
      return result.loadClass(implClass).getField("INSTANCE").get(null);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not load the INSTANCE of generated impl " + implClass, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static ValidatedPrism<Object, Object> asValidatedPrism(Object impl) {
    return (ValidatedPrism<Object, Object>) invoke(impl, "asValidatedPrism");
  }

  @Test
  @DisplayName(
      "lossless tier: asIso() and parse/build satisfy the iso, round-trip and coherence" + " laws")
  void losslessTierIsLawful() throws ReflectiveOperationException {
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

    var result = compileMapping(domain, wire, spec);
    Object impl = implInstance(result, "com.example.PersonMappingImpl");
    @SuppressWarnings("unchecked")
    Iso<Object, Object> iso = (Iso<Object, Object>) invoke(impl, "asIso");

    MappingLaws.assertMappingLaws(
        iso,
        asValidatedPrism(impl),
        result.newInstance("com.example.Person", "Ada", 36),
        result.newInstance("com.example.PersonDto", "Grace", 41));
  }

  @Test
  @DisplayName("projection tier: asLens() satisfies get-set, set-get and set-set")
  void projectionTierIsLawful() throws ReflectiveOperationException {
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

    var result = compileMapping(domain, wire, spec);
    Object impl = implInstance(result, "com.example.EmployeeCardMappingImpl");
    @SuppressWarnings("unchecked")
    Lens<Object, Object> lens = (Lens<Object, Object>) invoke(impl, "asLens");

    MappingLaws.assertMappingLaws(
        lens,
        result.newInstance("com.example.Employee", "Ada", "Platform", 36),
        result.newInstance("com.example.EmployeeCardDto", "Grace", "Compilers"),
        result.newInstance("com.example.EmployeeCardDto", "Alan", "Computing"));
  }

  @Test
  @DisplayName("fallible leaf tier: asValidatedPrism() satisfies both round trips and no-parse")
  void fallibleLeafTierIsLawful() throws ReflectiveOperationException {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            public record User(String name, EmailAddress email, int age) {}
            """);
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.UserDto",
            """
            package com.example;

            public record UserDto(String name, String email, int age) {}
            """);
    JavaFileObject spec =
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
                return emailPrism();
              }

            """
                + EMAIL_PRISM
                + """
            }
            """);

    var result = compileMapping(EMAIL, domain, wire, spec);
    Object impl = implInstance(result, "com.example.UserMappingImpl");

    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl),
        result.newInstance("com.example.UserDto", "Ada", "ada@example.org", 36),
        result.newInstance("com.example.UserDto", "Ada", "not-an-email", 36));
  }

  @Test
  @DisplayName("nested spec tier: a mapping delegating to a sibling impl stays lawful end to end")
  void nestedSpecTierIsLawful() throws ReflectiveOperationException {
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

            public record CustomerDto(String name, String email) {}
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
                return emailPrism();
              }

            """
                + EMAIL_PRISM
                + """
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

    var result =
        compileMapping(
            EMAIL, customer, customerDto, customerMapping, order, orderDto, orderMapping);
    Object impl = implInstance(result, "com.example.OrderMappingImpl");

    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl),
        result.newInstance(
            "com.example.OrderDto",
            "o-1",
            result.newInstance("com.example.CustomerDto", "Ada", "ada@example.org")),
        result.newInstance(
            "com.example.OrderDto",
            "o-1",
            result.newInstance("com.example.CustomerDto", "Ada", "not-an-email")));
  }

  @Test
  @DisplayName("container tier: List, Optional and Map lifting all stay lawful in one spec")
  void containerTierIsLawful() throws ReflectiveOperationException {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.Team",
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;

            public record Team(
                String name,
                List<EmailAddress> members,
                Optional<EmailAddress> lead,
                Map<String, EmailAddress> directory) {}
            """);
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.TeamDto",
            """
            package com.example;

            import java.util.List;
            import java.util.Map;
            import java.util.Optional;

            public record TeamDto(
                String name,
                List<String> members,
                Optional<String> lead,
                Map<String, String> directory) {}
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
                return emailPrism();
              }

              default ValidatedPrism<String, EmailAddress> lead() {
                return emailPrism();
              }

              default ValidatedPrism<String, EmailAddress> directory() {
                return emailPrism();
              }

            """
                + EMAIL_PRISM
                + """
            }
            """);

    var result = compileMapping(EMAIL, domain, wire, spec);
    Object impl = implInstance(result, "com.example.TeamMappingImpl");
    var dtoConstructor =
        result
            .loadClass("com.example.TeamDto")
            .getDeclaredConstructor(String.class, List.class, Optional.class, Map.class);

    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl),
        dtoConstructor.newInstance(
            "core",
            List.of("ada@example.org", "grace@example.org"),
            Optional.of("alan@example.org"),
            Map.of("ops", "kay@example.org")),
        dtoConstructor.newInstance(
            "core",
            List.of("ada@example.org", "not-an-email"),
            Optional.of("nope"),
            Map.of("ops", "also-nope")));
  }

  @Test
  @DisplayName("sealed dispatch tier: the dispatching prism is lawful over both subtype arms")
  void sealedDispatchTierIsLawful() throws ReflectiveOperationException {
    JavaFileObject payment =
        JavaFileObjects.forSourceString(
            "com.example.Payment",
            """
            package com.example;

            public sealed interface Payment permits Card, Bank {}
            """);
    JavaFileObject card =
        JavaFileObjects.forSourceString(
            "com.example.Card",
            """
            package com.example;

            public record Card(EmailAddress owner) implements Payment {}
            """);
    JavaFileObject bank =
        JavaFileObjects.forSourceString(
            "com.example.Bank",
            """
            package com.example;

            public record Bank(String iban) implements Payment {}
            """);
    JavaFileObject paymentDto =
        JavaFileObjects.forSourceString(
            "com.example.PaymentDto",
            """
            package com.example;

            public sealed interface PaymentDto permits CardDto, BankDto {}
            """);
    JavaFileObject cardDto =
        JavaFileObjects.forSourceString(
            "com.example.CardDto",
            """
            package com.example;

            public record CardDto(String owner) implements PaymentDto {}
            """);
    JavaFileObject bankDto =
        JavaFileObjects.forSourceString(
            "com.example.BankDto",
            """
            package com.example;

            public record BankDto(String iban) implements PaymentDto {}
            """);
    JavaFileObject cardMapping =
        JavaFileObjects.forSourceString(
            "com.example.CardMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface CardMapping extends MappingSpec<Card, CardDto> {
              default ValidatedPrism<String, EmailAddress> owner() {
                return emailPrism();
              }

            """
                + EMAIL_PRISM
                + """
            }
            """);
    JavaFileObject bankMapping =
        JavaFileObjects.forSourceString(
            "com.example.BankMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface BankMapping extends MappingSpec<Bank, BankDto> {}
            """);
    JavaFileObject paymentMapping =
        JavaFileObjects.forSourceString(
            "com.example.PaymentMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
            """);

    var result =
        compileMapping(
            EMAIL,
            payment,
            card,
            bank,
            paymentDto,
            cardDto,
            bankDto,
            cardMapping,
            bankMapping,
            paymentMapping);
    Object impl = implInstance(result, "com.example.PaymentMappingImpl");
    ValidatedPrism<Object, Object> prism = asValidatedPrism(impl);

    // The fallible Card arm: full round trips plus no-parse through the dispatch switch.
    MappingLaws.assertMappingLaws(
        prism,
        result.newInstance("com.example.CardDto", "ada@example.org"),
        result.newInstance("com.example.CardDto", "not-an-email"));
    // The identity Bank arm is total: the domain-sample overload round-trips it.
    MappingLaws.assertMappingLaws(
        prism, result.newInstance("com.example.Bank", "GB33BUKB20201555555555"));
  }

  @Test
  @DisplayName(
      "derived-field tier: build recomputes, parse ignores, and only non-derived"
          + " components round-trip")
  void derivedFieldTierIsLawful() throws ReflectiveOperationException {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.Profile",
            """
            package com.example;

            public record Profile(String first, String last) {}
            """);
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.ProfileDto",
            """
            package com.example;

            public record ProfileDto(String first, String last, String displayName) {}
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.ProfileMapping",
            """
            package com.example;

            import org.higherkindedj.optics.Getter;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
              default Getter<Profile, String> displayName() {
                return Getter.of(p -> p.first() + " " + p.last());
              }
            }
            """);

    var result = compileMapping(domain, wire, spec);
    Object impl = implInstance(result, "com.example.ProfileMappingImpl");

    // Parse is total (the derived component is ignored), so no non-parsing wire value exists:
    // the domain-sample overload asserts exactly the round trip a derived-field mapping offers.
    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl), result.newInstance("com.example.Profile", "Ada", "Lovelace"));
  }

  @Test
  @DisplayName(
      "bean-wire tier: a bean mapping's domain round trip is lawful through the published harness")
  void beanWireTierIsLawful() throws ReflectiveOperationException {
    JavaFileObject domain =
        JavaFileObjects.forSourceString(
            "com.example.User",
            """
            package com.example;

            import java.util.Optional;

            public record User(String name, EmailAddress email, Optional<String> nickname) {}
            """);
    // The wire is a mutable JavaBean: no equals(), so only the domain round trip is comparable.
    JavaFileObject wire =
        JavaFileObjects.forSourceString(
            "com.example.UserDto",
            """
            package com.example;

            public class UserDto {
              private String name;
              private String email;
              private String nickname;

              public String getName() { return name; }
              public void setName(String name) { this.name = name; }
              public String getEmail() { return email; }
              public void setEmail(String email) { this.email = email; }
              public String getNickname() { return nickname; }
              public void setNickname(String nickname) { this.nickname = nickname; }
            }
            """);
    JavaFileObject spec =
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
                return emailPrism();
              }

            """
                + EMAIL_PRISM
                + """
            }
            """);

    var result = compileMapping(EMAIL, domain, wire, spec);
    Object impl = implInstance(result, "com.example.UserMappingImpl");

    // parse(build(user)) == Valid(user), compared on the domain record (the bean has no equals).
    // Exercises the null-guarded getter reads and the Optional<->nullable bridge on the round trip.
    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl),
        result.newInstance(
            "com.example.User",
            "Ada",
            result.newInstance("com.example.EmailAddress", "ada@example.org"),
            Optional.of("countess")));
    MappingLaws.assertMappingLaws(
        asValidatedPrism(impl),
        result.newInstance(
            "com.example.User",
            "Grace",
            result.newInstance("com.example.EmailAddress", "grace@example.org"),
            Optional.empty()));
  }
}

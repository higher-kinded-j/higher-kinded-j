// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.palantir.javapoet.TypeSpec;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MappingProcessor - the @GenerateMapping Step-0 slice")
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
          .contains(".field(\"name\", Validated.validNel(wire.name()))")
          .contains(".field(\"email\", email().parse(wire.email()))")
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
          .contains(".field(\"name\", Validated.validNel(wire.fullName()))")
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
          .contains(".field(\"members\", members().parseAll(wire.members()))")
          .contains(".field(\"lead\", wire.lead().map(v -> lead().parse(v).map(Optional::of))")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("Map value lifting is deferred with a clear diagnostic")
    void mapLiftingDeferred() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Directory",
              """
              package com.example;

              import java.util.Map;

              public record Directory(Map<String, EmailAddress> entries) {}
              """);
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.DirectoryDto",
              """
              package com.example;

              import java.util.Map;

              public record DirectoryDto(Map<String, String> entries) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DirectoryMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface DirectoryMapping extends MappingSpec<Directory, DirectoryDto> {}
              """);

      Compilation compilation = compile(EMAIL, domain, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("needs Map value lifting");
      assertThat(compilation).hadErrorContaining("Map arrives with the full mapper");
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
              ".field(\"customer\","
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism().parse(wire.customer()))")
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
              ".field(\"customers\","
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism().parseAll(wire.customers()))");
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
              ".field(\"children\","
                  + " TreeMappingImpl.INSTANCE.asValidatedPrism().parseAll(wire.children()))");
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
              ".field(\"payment\","
                  + " PaymentMappingImpl.INSTANCE.asValidatedPrism().parse(wire.payment()))");
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
    @DisplayName("a projection field that changes type is rejected")
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
      assertThat(compilation).hadErrorContaining("projection field 'BadgeDto.name' changes type");
      assertThat(compilation).hadErrorContaining("the types must match exactly");
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
          .contains(".field(\"handle\", handle().parse(wire.handle()))")
          .contains("handle().build(domain.handle())")
          .doesNotContain("asIso");
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
      assertThat(compilation).hadErrorContaining("unlawful lens");
    }

    @Test
    @DisplayName("records beyond the 12-field parse ceiling get a diagnostic, not broken code")
    void arityCeilingRejected() {
      String comps =
          IntStream.rangeClosed(1, 13)
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
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("has 13 components; the accumulating parse supports at most 12");
      assertThat(compilation).hadErrorContaining("Group related components into nested records");
    }

    @Test
    @DisplayName("generic records are rejected with a diagnostic, not undeclared type variables")
    void genericRecordsRejected() {
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
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'Box' is generic, which this mapper does not support");
    }

    @Test
    @DisplayName("an abstract method that is neither a rename nor a leaf is rejected")
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
          .hadErrorContaining("abstract method 'helper' is neither a rename nor a leaf");
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
    @DisplayName("a spec inheriting from another interface is rejected")
    void specInheritanceRejected() {
      JavaFileObject base =
          JavaFileObjects.forSourceString(
              "com.example.BaseRenames",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface BaseRenames {
                @MapField(to = "a")
                String a();
              }
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
                  extends BaseRenames, MappingSpec<Records.D, Records.W> {}
              """);
      Compilation compilation = compile(PLAIN, base, inheriting);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'InheritingMapping' extends interfaces besides MappingSpec");
      assertThat(compilation)
          .hadErrorContaining("Declare every rename and leaf directly on the spec");
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
    @DisplayName("a record domain with a non-record wire is rejected")
    void recordWithNonRecordWireRejected() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "HalfMapping",
                  "public interface HalfMapping extends MappingSpec<Records.D, String> {}"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must both be records, or both sealed interfaces");
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
    @DisplayName("a generic spec interface is rejected")
    void genericSpecInterfaceRejected() {
      Compilation compilation =
          compile(
              PLAIN,
              spec(
                  "GenMapping",
                  "public interface GenMapping<T> extends MappingSpec<Records.D, Records.W> {}"));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'GenMapping' is generic, which this mapper does not support");
    }

    @Test
    @DisplayName("a generic wire record is rejected")
    void genericWireRejected() {
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
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("'WG' is generic, which this mapper does not support");
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
    @DisplayName("rejects non-record type arguments")
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
      assertThat(compilation).hadErrorContaining("must both be records");
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

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.Compiler.javac;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden file tests for the {@code @GenerateMapping} emission tiers. One golden {@code Impl} per
 * tier pins the exact generated text, so a refactor that drifts any tier's output fails the build
 * with a readable diff. Fragment assertions in {@link MappingProcessorTest} stay; they document
 * intent per feature, the goldens catch everything else.
 *
 * <p>Goldens live in {@code src/test/resources/golden/mapping/}; refresh with {@code ./gradlew
 * :hkj-processor:updateGoldenFiles} after an intentional generator change.
 */
@DisplayName("Mapping Golden File Tests")
class MappingGoldenFileTest {

  private static final String GOLDEN_RESOURCE_PATH = "/golden/mapping/";

  record GoldenTestCase(String description, String generatedClassName, String goldenFileName) {
    @Override
    public String toString() {
      return description;
    }
  }

  static Stream<GoldenTestCase> goldenFileTestCases() {
    return Stream.of(
        new GoldenTestCase(
            "lossless full mapping (asIso present)",
            "com.example.lossless.PersonMappingImpl",
            "PersonMappingImpl.java.golden"),
        new GoldenTestCase(
            "fallible full mapping, record wire (leaf + nested + List/Optional/Map + derived)",
            "com.example.fallible.AccountMappingImpl",
            "AccountMappingImpl.java.golden"),
        new GoldenTestCase(
            "fallible full mapping, bean wire (ifPresent-guarded getters, setter build)",
            "com.example.bean.UserMappingImpl",
            "BeanUserMappingImpl.java.golden"),
        new GoldenTestCase(
            "identity projection (asLens)",
            "com.example.projection.EmployeeCardMappingImpl",
            "EmployeeCardMappingImpl.java.golden"),
        new GoldenTestCase(
            "leaf-carrying projection (validated patch)",
            "com.example.patch.SubscriberDetailsMappingImpl",
            "SubscriberDetailsMappingImpl.java.golden"),
        new GoldenTestCase(
            "sparse update (updateFrom)",
            "com.example.update.UserPatchMappingImpl",
            "UserPatchMappingImpl.java.golden"),
        new GoldenTestCase(
            "sealed dispatch",
            "com.example.sealeddispatch.PaymentMappingImpl",
            "PaymentMappingImpl.java.golden"),
        new GoldenTestCase(
            "fallible full mapping wider than one fields() ladder (chunked parse)",
            "com.example.widerecord.WideOrderMappingImpl",
            "WideOrderMappingImpl.java.golden"),
        new GoldenTestCase(
            "wide bean wire (chunked parse, singleton trailing chunk)",
            "com.example.widebean.WideUserMappingImpl",
            "WideUserMappingImpl.java.golden"),
        new GoldenTestCase(
            "wide leaf-carrying projection (chunked patch)",
            "com.example.widepatch.WideProfileMappingImpl",
            "WideProfileMappingImpl.java.golden"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("goldenFileTestCases")
  @DisplayName("Generated Impl matches golden file")
  void generatedCodeMatchesGolden(GoldenTestCase testCase) throws IOException {
    Compilation compilation = compile();

    assertThat(compilation.status())
        .as("Compilation should succeed for %s", testCase.generatedClassName())
        .isEqualTo(Compilation.Status.SUCCESS);

    Optional<JavaFileObject> generatedFile =
        compilation.generatedSourceFile(testCase.generatedClassName());
    assertThat(generatedFile)
        .as("Generated file should exist: %s", testCase.generatedClassName())
        .isPresent();

    String generated = generatedFile.get().getCharContent(true).toString();

    if ("true".equals(System.getProperty("updateGolden"))) {
      Path goldenDir = Path.of("src/test/resources/golden/mapping");
      if (!Files.exists(goldenDir.getParent())) {
        goldenDir = Path.of("hkj-processor/src/test/resources/golden/mapping");
      }
      Files.createDirectories(goldenDir);
      Files.writeString(
          goldenDir.resolve(testCase.goldenFileName()), generated, StandardCharsets.UTF_8);
      System.out.println("Updated: " + testCase.goldenFileName());
      return;
    }

    String golden = readGoldenFile(testCase.goldenFileName());
    if (golden == null) {
      fail(
          "Golden file not initialised: "
              + testCase.goldenFileName()
              + ". Run: ./gradlew :hkj-processor:updateGoldenFiles");
    }
    assertThat(normalize(generated)).isEqualTo(normalize(golden));
  }

  private Compilation compile() {
    return javac()
        .withProcessors(new MappingProcessor())
        .compile(
            lossless(),
            fallible(),
            beanWire(),
            projection(),
            leafProjection(),
            update(),
            sealed(),
            wideRecord(),
            wideBean(),
            widePatch());
  }

  // ---- lossless full: identity components on both sides, so asIso is emitted ----
  private JavaFileObject lossless() {
    return JavaFileObjects.forSourceString(
        "com.example.lossless.Fixtures",
        """
        package com.example.lossless;

        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        record Person(String name, int age) {}

        record PersonDto(String name, int age) {}

        @GenerateMapping
        interface PersonMapping extends MappingSpec<Person, PersonDto> {}
        """);
  }

  // ---- fallible full, record wire: leaf, nested spec, List/Optional/Map lifting, derived ----
  private JavaFileObject fallible() {
    return JavaFileObjects.forSourceString(
        "com.example.fallible.Fixtures",
        """
        package com.example.fallible;

        import java.util.List;
        import java.util.Map;
        import java.util.Optional;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.Getter;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record Address(String city) {}

        record AddressDto(String city) {}

        @GenerateMapping
        interface AddressMapping extends MappingSpec<Address, AddressDto> {}

        record Account(
            String name,
            EmailAddress email,
            List<EmailAddress> cc,
            List<String> tags,
            Optional<EmailAddress> lead,
            Map<String, EmailAddress> contacts,
            Address address) {}

        record AccountDto(
            String name,
            String email,
            List<String> cc,
            List<String> tags,
            Optional<String> lead,
            Map<String, String> contacts,
            AddressDto address,
            String display) {}

        @GenerateMapping
        interface AccountMapping extends MappingSpec<Account, AccountDto> {
          default ValidatedPrism<String, EmailAddress> email() {
            return emailPrism();
          }

          default ValidatedPrism<String, EmailAddress> cc() {
            return emailPrism();
          }

          default ValidatedPrism<String, EmailAddress> lead() {
            return emailPrism();
          }

          default ValidatedPrism<String, EmailAddress> contacts() {
            return emailPrism();
          }

          default Getter<Account, String> display() {
            return Getter.of(a -> a.name() + " <" + a.email().value() + ">");
          }

          private static ValidatedPrism<String, EmailAddress> emailPrism() {
            return ValidatedPrism.of(
                raw ->
                    raw.contains("@")
                        ? Validated.validNel(new EmailAddress(raw))
                        : Validated.invalidNel(FieldError.of("not an email address")),
                EmailAddress::value);
          }
        }
        """);
  }

  // ---- fallible full, bean wire: guarded getters, setter-based build, no asIso ----
  private JavaFileObject beanWire() {
    return JavaFileObjects.forSourceString(
        "com.example.bean.Fixtures",
        """
        package com.example.bean;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record User(String name, EmailAddress email, int age) {}

        class UserDto {
          private String name;
          private String email;
          private int age;

          public String getName() {
            return name;
          }

          public void setName(String name) {
            this.name = name;
          }

          public String getEmail() {
            return email;
          }

          public void setEmail(String email) {
            this.email = email;
          }

          public int getAge() {
            return age;
          }

          public void setAge(int age) {
            this.age = age;
          }
        }

        @GenerateMapping
        interface UserMapping extends MappingSpec<User, UserDto> {
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
  }

  // ---- identity projection: wire smaller than domain, all identity, so asLens is emitted ----
  private JavaFileObject projection() {
    return JavaFileObjects.forSourceString(
        "com.example.projection.Fixtures",
        """
        package com.example.projection;

        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        record Employee(String name, String department, int age) {}

        record EmployeeCardDto(String name, String department) {}

        @GenerateMapping
        interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}
        """);
  }

  // ---- leaf-carrying projection: a fallible leaf on a projection selects the patch tier ----
  private JavaFileObject leafProjection() {
    return JavaFileObjects.forSourceString(
        "com.example.patch.Fixtures",
        """
        package com.example.patch;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record Subscriber(String id, EmailAddress email, int age) {}

        record SubscriberDetailsDto(String email, int age) {}

        @GenerateMapping
        interface SubscriberDetailsMapping extends MappingSpec<Subscriber, SubscriberDetailsDto> {
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
  }

  // ---- sparse update: UpdateSpec over a bean PATCH wire, updateFrom only ----
  private JavaFileObject update() {
    return JavaFileObjects.forSourceString(
        "com.example.update.Fixtures",
        """
        package com.example.update;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.UpdateSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record User(String name, EmailAddress email, int age) {}

        class UserPatchDto {
          private String name;
          private String email;
          private Integer age;

          public String getName() {
            return name;
          }

          public void setName(String name) {
            this.name = name;
          }

          public String getEmail() {
            return email;
          }

          public void setEmail(String email) {
            this.email = email;
          }

          public Integer getAge() {
            return age;
          }

          public void setAge(Integer age) {
            this.age = age;
          }
        }

        @GenerateMapping
        interface UserPatchMapping extends UpdateSpec<User, UserPatchDto> {
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
  }

  // ---- sealed dispatch: a MappingSpec over two sealed hierarchies, one spec per subtype pair ----
  private JavaFileObject sealed() {
    return JavaFileObjects.forSourceString(
        "com.example.sealeddispatch.Fixtures",
        """
        package com.example.sealeddispatch;

        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        sealed interface Payment permits Card, Bank {}

        record Card(String number) implements Payment {}

        record Bank(String iban) implements Payment {}

        sealed interface PaymentDto permits CardDto, BankDto {}

        record CardDto(String number) implements PaymentDto {}

        record BankDto(String iban) implements PaymentDto {}

        @GenerateMapping
        interface CardMapping extends MappingSpec<Card, CardDto> {}

        @GenerateMapping
        interface BankMapping extends MappingSpec<Bank, BankDto> {}

        @GenerateMapping
        interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}
        """);
  }

  // ---- wide full, record wire: 20 components, chunked as a 16-ladder plus a 4-ladder ----
  private JavaFileObject wideRecord() {
    String identityComps = components(1, 19);
    return JavaFileObjects.forSourceString(
        "com.example.widerecord.Fixtures",
        """
        package com.example.widerecord;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record WideOrder(%s, EmailAddress email) {}

        record WideOrderDto(%s, String email) {}

        @GenerateMapping
        interface WideOrderMapping extends MappingSpec<WideOrder, WideOrderDto> {
          default ValidatedPrism<String, EmailAddress> email() {
            return ValidatedPrism.of(
                raw ->
                    raw.contains("@")
                        ? Validated.validNel(new EmailAddress(raw))
                        : Validated.invalidNel(FieldError.of("not an email address")),
                EmailAddress::value);
          }
        }
        """
            .formatted(identityComps, identityComps));
  }

  // ---- wide bean wire: 17 properties, chunked as a 16-ladder plus a singleton ladder ----
  private JavaFileObject wideBean() {
    StringBuilder properties = new StringBuilder();
    for (int i = 1; i <= 16; i++) {
      properties.append(beanProperty("String", "f" + i));
    }
    properties.append(beanProperty("String", "email"));
    return JavaFileObjects.forSourceString(
        "com.example.widebean.Fixtures",
        """
        package com.example.widebean;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record WideUser(%s, EmailAddress email) {}

        class WideUserDto {
        %s}

        @GenerateMapping
        interface WideUserMapping extends MappingSpec<WideUser, WideUserDto> {
          default ValidatedPrism<String, EmailAddress> email() {
            return ValidatedPrism.of(
                raw ->
                    raw.contains("@")
                        ? Validated.validNel(new EmailAddress(raw))
                        : Validated.invalidNel(FieldError.of("not an email address")),
                EmailAddress::value);
          }
        }
        """
            .formatted(components(1, 16), properties));
  }

  // ---- wide leaf-carrying projection: 17 of 18 components project, chunked patch ----
  private JavaFileObject widePatch() {
    String projectedComps = components(1, 16);
    return JavaFileObjects.forSourceString(
        "com.example.widepatch.Fixtures",
        """
        package com.example.widepatch;

        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        record EmailAddress(String value) {}

        record WideProfile(%s, EmailAddress email, String internalNote) {}

        record WideProfilePatchDto(%s, String email) {}

        @GenerateMapping
        interface WideProfileMapping extends MappingSpec<WideProfile, WideProfilePatchDto> {
          default ValidatedPrism<String, EmailAddress> email() {
            return ValidatedPrism.of(
                raw ->
                    raw.contains("@")
                        ? Validated.validNel(new EmailAddress(raw))
                        : Validated.invalidNel(FieldError.of("not an email address")),
                EmailAddress::value);
          }
        }
        """
            .formatted(projectedComps, projectedComps));
  }

  private static String components(int from, int to) {
    return IntStream.rangeClosed(from, to)
        .mapToObj(i -> "String f" + i)
        .collect(Collectors.joining(", "));
  }

  private static String beanProperty(String type, String name) {
    String upper = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    return ("  private %1$s %2$s;\n\n"
            + "  public %1$s get%3$s() {\n    return %2$s;\n  }\n\n"
            + "  public void set%3$s(%1$s %2$s) {\n    this.%2$s = %2$s;\n  }\n\n")
        .formatted(type, name, upper);
  }

  private String readGoldenFile(String fileName) throws IOException {
    try (InputStream is = getClass().getResourceAsStream(GOLDEN_RESOURCE_PATH + fileName)) {
      return is == null ? null : new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String normalize(String source) {
    return source.replace("\r\n", "\n").replaceAll("[ \t]+\n", "\n").trim();
  }
}

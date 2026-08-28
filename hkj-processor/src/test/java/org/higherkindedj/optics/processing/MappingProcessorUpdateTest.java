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

@DisplayName("MappingProcessor - sparse PATCH write-back via UpdateSpec")
class MappingProcessorUpdateTest {

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.example.EmailAddress",
          """
          package com.example;

          public record EmailAddress(String value) {}
          """);

  private static final JavaFileObject USER =
      JavaFileObjects.forSourceString(
          "com.example.User",
          """
          package com.example;

          public record User(String name, EmailAddress email, int age) {}
          """);

  // A PATCH DTO: reference-typed getters/setters throughout — a wrapper Integer for age, so the
  // scalar can be absent (null) as well as present.
  private static final JavaFileObject USER_PATCH_DTO =
      JavaFileObjects.forSourceString(
          "com.example.UserPatchDto",
          """
          package com.example;

          public class UserPatchDto {
            private String name;
            private String email;
            private Integer age;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public String getEmail() { return email; }
            public void setEmail(String email) { this.email = email; }
            public Integer getAge() { return age; }
            public void setAge(Integer age) { this.age = age; }
          }
          """);

  private static final JavaFileObject USER_PATCH_MAPPING =
      JavaFileObjects.forSourceString(
          "com.example.UserPatchMapping",
          """
          package com.example;

          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.UpdateSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          public interface UserPatchMapping extends UpdateSpec<User, UserPatchDto> {
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
  @DisplayName("Emission")
  class Emission {

    @Test
    @DisplayName("emits only updateFrom - no build, parse, or as* tier")
    void emitsOnlyUpdateFrom() {
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, USER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.UserPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("updateFrom(UserPatchDto wire)")
          .contains("Edits.accumulate(")
          .contains("Edit.setIfPresent(")
          .contains("Setter.fromGetSet(User::name, (d, v) -> new User(v, d.email(), d.age()))")
          .contains("wire.getName()")
          .contains("Edit.parseIfPresent(")
          .contains("Setter.fromGetSet(User::email, (d, v) -> new User(d.name(), v, d.age()))")
          .contains("wire.getEmail()")
          .contains("email()::parse")
          .contains(".at(\"email\")")
          .contains("Setter.fromGetSet(User::age, (d, v) -> new User(d.name(), d.email(), v))")
          .contains("wire.getAge()")
          .doesNotContain("asIso")
          .doesNotContain("asValidatedPrism")
          .doesNotContain("asLens")
          .doesNotContain("build(User domain)")
          .doesNotContain("parse(UserPatchDto");
    }
  }

  @Nested
  @DisplayName("Runtime")
  class Runtime {

    private Object impl(RuntimeCompilationHelper.CompiledResult result) {
      return result.instance("com.example.UserPatchMappingImpl");
    }

    private Object patchDto(
        RuntimeCompilationHelper.CompiledResult result, String name, String email, Integer age)
        throws ReflectiveOperationException {
      Object dto =
          result.loadClass("com.example.UserPatchDto").getDeclaredConstructor().newInstance();
      invoke(dto, "setName", name);
      invoke(dto, "setEmail", email);
      invoke(dto, "setAge", age);
      return dto;
    }

    @SuppressWarnings("unchecked")
    private Validated<NonEmptyList<FieldError>, Object> apply(
        Object impl, Object dto, Object current) throws ReflectiveOperationException {
      Object accumulated = invoke(impl, "updateFrom", dto);
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
    }

    @Test
    @DisplayName("an all-absent DTO folds to the identity update")
    void allAbsentIsIdentity() {
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, USER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object current = result.newInstance("com.example.User", "Ada", email, 42);

        Validated<NonEmptyList<FieldError>, Object> patched =
            apply(impl(result), patchDto(result, null, null, null), current);

        Assertions.assertThat(patched.isValid()).isTrue();
        Assertions.assertThat(patched.get()).isEqualTo(current);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("present fields are applied; absent ones keep their value (identity + unboxing)")
    void presentFieldsApplied() {
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, USER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object current = result.newInstance("com.example.User", "Ada", email, 42);

        // Change only name and age (a wrapper Integer that unboxes into the int field); email
        // stays.
        Validated<NonEmptyList<FieldError>, Object> patched =
            apply(impl(result), patchDto(result, "Grace", null, 50), current);

        Assertions.assertThat(patched.isValid()).isTrue();
        Object updated = patched.get();
        Assertions.assertThat(invoke(updated, "name")).isEqualTo("Grace");
        Assertions.assertThat(invoke(updated, "age")).isEqualTo(50);
        Assertions.assertThat(invoke(updated, "email")).isEqualTo(email);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a present valid leaf is parsed and applied")
    void presentValidLeafParsed() {
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, USER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object current = result.newInstance("com.example.User", "Ada", email, 42);

        Validated<NonEmptyList<FieldError>, Object> patched =
            apply(impl(result), patchDto(result, null, "grace@corp.example", null), current);

        Assertions.assertThat(patched.isValid()).isTrue();
        Object newEmail = result.newInstance("com.example.EmailAddress", "grace@corp.example");
        Assertions.assertThat(invoke(patched.get(), "email")).isEqualTo(newEmail);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a present invalid leaf accumulates a located FieldError; pure edits add no error")
    void presentInvalidLeafLocated() {
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, USER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object email = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object current = result.newInstance("com.example.User", "Ada", email, 42);

        // A valid name (pure edit, no error) and an invalid email (located under "email").
        Validated<NonEmptyList<FieldError>, Object> patched =
            apply(impl(result), patchDto(result, "Grace", "not-an-email", null), current);

        Assertions.assertThat(patched.isInvalid()).isTrue();
        Assertions.assertThat(patched.getError().toJavaList())
            .containsExactly(new FieldError(List.of("email"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }
  }

  @Nested
  @DisplayName("Element lifting - one leaf vocabulary across tiers")
  class ElementLifting {

    private static final JavaFileObject PHONE =
        JavaFileObjects.forSourceString(
            "com.example.PhoneNumber",
            """
            package com.example;

            public record PhoneNumber(String value) {}
            """);

    private static final JavaFileObject CONTACT =
        JavaFileObjects.forSourceString(
            "com.example.Contact",
            """
            package com.example;

            import java.util.List;

            public record Contact(String name, List<PhoneNumber> phones) {}
            """);

    private static final JavaFileObject CONTACT_DTO =
        JavaFileObjects.forSourceString(
            "com.example.ContactDto",
            """
            package com.example;

            import java.util.List;

            public record ContactDto(String name, List<String> phones) {}
            """);

    private static final JavaFileObject CONTACT_PATCH_DTO =
        JavaFileObjects.forSourceString(
            "com.example.ContactPatchDto",
            """
            package com.example;

            import java.util.List;

            public class ContactPatchDto {
              private String name;
              private List<String> phones;

              public String getName() { return name; }
              public void setName(String name) { this.name = name; }
              public List<String> getPhones() { return phones; }
              public void setPhones(List<String> phones) { this.phones = phones; }
            }
            """);

    // The issue's headline: ONE mix-in element vocabulary, inherited by the full spec (which lifts
    // it elementwise) and the update spec (which now lifts it too).
    private static final JavaFileObject PHONE_VOCABULARY =
        JavaFileObjects.forSourceString(
            "com.example.PhoneVocabulary",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface PhoneVocabulary {
              default ValidatedPrism<String, PhoneNumber> phones() {
                return ValidatedPrism.of(
                    raw ->
                        raw.startsWith("+")
                            ? Validated.validNel(new PhoneNumber(raw))
                            : Validated.invalidNel(FieldError.of("not a phone number")),
                    PhoneNumber::value);
              }
            }
            """);

    private static final JavaFileObject CONTACT_PATCH_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.ContactPatchMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.UpdateSpec;

            @GenerateMapping
            public interface ContactPatchMapping
                extends PhoneVocabulary, UpdateSpec<Contact, ContactPatchDto> {}
            """);

    private static final JavaFileObject CONTACT_FULL_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.ContactMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface ContactMapping
                extends PhoneVocabulary, MappingSpec<Contact, ContactDto> {}
            """);

    private Object patchDto(
        RuntimeCompilationHelper.CompiledResult result, String name, List<String> phones)
        throws ReflectiveOperationException {
      Object dto =
          result.loadClass("com.example.ContactPatchDto").getDeclaredConstructor().newInstance();
      invoke(dto, "setName", name);
      invoke(dto, "setPhones", phones);
      return dto;
    }

    // The canonical record constructor, looked up positionally: newInstance matches parameters by
    // the arguments' concrete classes, which never equal an interface-typed component (List, Map).
    private Object construct(
        RuntimeCompilationHelper.CompiledResult result, String fqcn, Object... args)
        throws ReflectiveOperationException {
      return result.loadClass(fqcn).getDeclaredConstructors()[0].newInstance(args);
    }

    @SuppressWarnings("unchecked")
    private Validated<NonEmptyList<FieldError>, Object> apply(
        RuntimeCompilationHelper.CompiledResult result, Object dto, Object current)
        throws ReflectiveOperationException {
      Object impl = result.instance("com.example.ContactPatchMappingImpl");
      Object accumulated = invoke(impl, "updateFrom", dto);
      return (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
    }

    @Test
    @DisplayName("one mix-in element vocabulary serves a full spec and an update spec together")
    void sharedVocabularyServesBothTiers() {
      Compilation compilation =
          compile(
              PHONE,
              CONTACT,
              CONTACT_DTO,
              CONTACT_PATCH_DTO,
              PHONE_VOCABULARY,
              CONTACT_FULL_MAPPING,
              CONTACT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      String full = generatedSource(compilation, "com.example.ContactMappingImpl");
      String sparse = generatedSource(compilation, "com.example.ContactPatchMappingImpl");
      Assertions.assertThat(full).contains("phones()::parseAll");
      Assertions.assertThat(sparse)
          .contains("Edit.parseIfPresent(")
          .contains("phones()::parseAll")
          .contains(".at(\"phones\")");
    }

    @Test
    @DisplayName("a present list parses elementwise: wholesale replacement, located element errors")
    void presentListParsesElementwise() {
      Compilation compilation =
          compile(PHONE, CONTACT, CONTACT_PATCH_DTO, PHONE_VOCABULARY, CONTACT_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object oldPhone = result.newInstance("com.example.PhoneNumber", "+44");
        Object current = construct(result, "com.example.Contact", "Ada", List.of(oldPhone));

        // Present and valid: the whole component is replaced (wholesale), elements parsed.
        Validated<NonEmptyList<FieldError>, Object> replaced =
            apply(result, patchDto(result, null, List.of("+1", "+353")), current);
        Assertions.assertThat(replaced.isValid()).isTrue();
        Assertions.assertThat(invoke(replaced.get(), "phones"))
            .isEqualTo(
                List.of(
                    result.newInstance("com.example.PhoneNumber", "+1"),
                    result.newInstance("com.example.PhoneNumber", "+353")));

        // Absent: the domain list survives untouched.
        Validated<NonEmptyList<FieldError>, Object> untouched =
            apply(result, patchDto(result, "Grace", null), current);
        Assertions.assertThat(untouched.isValid()).isTrue();
        Assertions.assertThat(invoke(untouched.get(), "phones")).isEqualTo(List.of(oldPhone));

        // Every bad element is a located failure, accumulating across the whole list — never
        // stopping at the first: phones.0 AND phones.2, in list order.
        Validated<NonEmptyList<FieldError>, Object> located =
            apply(result, patchDto(result, null, List.of("nope", "+1", "bad")), current);
        Assertions.assertThat(located.isInvalid()).isTrue();
        Assertions.assertThat(located.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("phones", "0"), "not a phone number"),
                new FieldError(List.of("phones", "2"), "not a phone number"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a whole-container leaf wins over the element interpretation (the tie-break)")
    void wholeContainerLeafWins() {
      JavaFileObject elementLeafSpec =
          JavaFileObjects.forSourceString(
              "com.example.WholesalePatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface WholesalePatchMapping
                  extends PhoneVocabulary, UpdateSpec<Contact, ContactPatchDto> {}
              """);
      // With only the element leaf visible, the element interpretation lifts; declaring a
      // whole-container leaf instead takes the pair directly. (One method name cannot carry both
      // shapes at once, so the tie-break is the check order: whole container first.)
      JavaFileObject containerLeafSpec =
          JavaFileObjects.forSourceString(
              "com.example.ContainerLeafPatchMapping",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ContainerLeafPatchMapping
                  extends UpdateSpec<Contact, ContactPatchDto> {
                default ValidatedPrism<List<String>, List<PhoneNumber>> phones() {
                  return ValidatedPrism.of(
                      raws ->
                          Validated.validNel(raws.stream().map(PhoneNumber::new).toList()),
                      phones -> phones.stream().map(PhoneNumber::value).toList());
                }
              }
              """);
      Compilation compilation =
          compile(
              PHONE,
              CONTACT,
              CONTACT_PATCH_DTO,
              PHONE_VOCABULARY,
              elementLeafSpec,
              containerLeafSpec);
      assertThat(compilation).succeeded();
      String elementLifted = generatedSource(compilation, "com.example.WholesalePatchMappingImpl");
      Assertions.assertThat(elementLifted).contains("phones()::parseAll");
      String wholeContainer =
          generatedSource(compilation, "com.example.ContainerLeafPatchMappingImpl");
      Assertions.assertThat(wholeContainer)
          .contains("phones()::parse")
          .doesNotContain("phones()::parseAll");
    }

    @Test
    @DisplayName("a present map parses valuewise, located by key")
    void presentMapParsesValuewise() {
      JavaFileObject profile =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.Map;

              public record Profile(String name, Map<String, EmailAddress> contacts) {}
              """);
      JavaFileObject profilePatchDto =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchDto",
              """
              package com.example;

              import java.util.Map;

              public class ProfilePatchDto {
                private String name;
                private Map<String, String> contacts;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Map<String, String> getContacts() { return contacts; }
                public void setContacts(Map<String, String> contacts) { this.contacts = contacts; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface ProfilePatchMapping extends UpdateSpec<Profile, ProfilePatchDto> {
                default ValidatedPrism<String, EmailAddress> contacts() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, profile, profilePatchDto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ProfilePatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("contacts()::parseValues")
          .contains(".at(\"contacts\")");
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object current = construct(result, "com.example.Profile", "Ada", java.util.Map.of());
        Object impl = result.instance("com.example.ProfilePatchMappingImpl");
        Object dto =
            result.loadClass("com.example.ProfilePatchDto").getDeclaredConstructor().newInstance();
        // Two bad values: every failure locates under its key, accumulating in entry order.
        java.util.Map<String, String> contacts = new java.util.LinkedHashMap<>();
        contacts.put("work", "nope");
        contacts.put("home", "also-bad");
        invoke(dto, "setContacts", contacts);
        Object accumulated = invoke(impl, "updateFrom", dto);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(patched.isInvalid()).isTrue();
        Assertions.assertThat(patched.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("contacts", "work"), "not an email address"),
                new FieldError(List.of("contacts", "home"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "a present Optional parses its element; a present empty sets empty; absent leaves"
            + " unchanged")
    void presentOptionalParsesElement() {
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              import java.util.Optional;

              public record Account(String name, Optional<EmailAddress> backup) {}
              """);
      JavaFileObject accountPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchDto",
              """
              package com.example;

              import java.util.Optional;

              public class AccountPatchDto {
                private String name;
                private Optional<String> backup;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public Optional<String> getBackup() { return backup; }
                public void setBackup(Optional<String> backup) { this.backup = backup; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface AccountPatchMapping extends UpdateSpec<Account, AccountPatchDto> {
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
      Compilation compilation = compile(EMAIL, account, accountPatchDto, spec);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object oldEmail = result.newInstance("com.example.EmailAddress", "ada@corp.example");
        Object current =
            result.newInstance("com.example.Account", "Ada", java.util.Optional.of(oldEmail));
        Object impl = result.instance("com.example.AccountPatchMappingImpl");

        java.util.function.BiFunction<Object, Object, Validated<NonEmptyList<FieldError>, Object>>
            patch =
                (dtoBackup, base) -> {
                  try {
                    Object dto =
                        result
                            .loadClass("com.example.AccountPatchDto")
                            .getDeclaredConstructor()
                            .newInstance();
                    invoke(dto, "setBackup", dtoBackup);
                    Object accumulated = invoke(impl, "updateFrom", dto);
                    @SuppressWarnings("unchecked")
                    Validated<NonEmptyList<FieldError>, Object> applied =
                        (Validated<NonEmptyList<FieldError>, Object>)
                            invoke(accumulated, "apply", base);
                    return applied;
                  } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                  }
                };

        // Present with a valid element: parsed and replaced.
        Validated<NonEmptyList<FieldError>, Object> replaced =
            patch.apply(java.util.Optional.of("grace@corp.example"), current);
        Assertions.assertThat(replaced.isValid()).isTrue();
        Assertions.assertThat(invoke(replaced.get(), "backup"))
            .isEqualTo(
                java.util.Optional.of(
                    result.newInstance("com.example.EmailAddress", "grace@corp.example")));

        // Present and empty: 'set to empty' is expressible on an Optional wire.
        Validated<NonEmptyList<FieldError>, Object> cleared =
            patch.apply(java.util.Optional.empty(), current);
        Assertions.assertThat(cleared.isValid()).isTrue();
        Assertions.assertThat(invoke(cleared.get(), "backup"))
            .isEqualTo(java.util.Optional.empty());

        // Absent (null): unchanged.
        Validated<NonEmptyList<FieldError>, Object> untouched = patch.apply(null, current);
        Assertions.assertThat(untouched.isValid()).isTrue();
        Assertions.assertThat(invoke(untouched.get(), "backup"))
            .isEqualTo(java.util.Optional.of(oldEmail));

        // Present with an invalid element: located under the component name.
        Validated<NonEmptyList<FieldError>, Object> located =
            patch.apply(java.util.Optional.of("nope"), current);
        Assertions.assertThat(located.isInvalid()).isTrue();
        Assertions.assertThat(located.getError().toJavaList())
            .containsExactly(new FieldError(List.of("backup"), "not an email address"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a present identity container is scanned: a null element is located, accumulating")
    void identityContainerScanned() {
      JavaFileObject tagged =
          JavaFileObjects.forSourceString(
              "com.example.Tagged",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public record Tagged(String name, List<String> tags, Map<String, String> labels) {}
              """);
      JavaFileObject taggedPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.TaggedPatchDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              public class TaggedPatchDto {
                private String name;
                private List<String> tags;
                private Map<String, String> labels;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
                public Map<String, String> getLabels() { return labels; }
                public void setLabels(Map<String, String> labels) { this.labels = labels; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TaggedPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface TaggedPatchMapping extends UpdateSpec<Tagged, TaggedPatchDto> {}
              """);
      Compilation compilation = compile(tagged, taggedPatchDto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TaggedPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("TaggedPatchMappingImpl::hkj$allPresent")
          .contains("TaggedPatchMappingImpl::hkj$valuesPresent")
          .contains(".at(\"tags\")")
          .contains(".at(\"labels\")");
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object current =
            construct(
                result,
                "com.example.Tagged",
                "Ada",
                List.of("vip"),
                java.util.Map.of("tier", "gold"));
        Object impl = result.instance("com.example.TaggedPatchMappingImpl");
        Object dto =
            result.loadClass("com.example.TaggedPatchDto").getDeclaredConstructor().newInstance();
        invoke(dto, "setTags", java.util.Arrays.asList("new", null));
        // Both container scans run and accumulate: the null list element at its index AND the
        // null map value under its key, in edit order.
        java.util.Map<String, String> labels = new java.util.LinkedHashMap<>();
        labels.put("tier", null);
        invoke(dto, "setLabels", labels);
        Object accumulated = invoke(impl, "updateFrom", dto);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> scanned =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(scanned.isInvalid()).isTrue();
        Assertions.assertThat(scanned.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("tags", "1"), "must not be null"),
                new FieldError(List.of("labels", "tier"), "must not be null"));

        // A clean identity container still replaces wholesale, by reference.
        Object cleanDto =
            result.loadClass("com.example.TaggedPatchDto").getDeclaredConstructor().newInstance();
        List<String> replacement = List.of("new", "shiny");
        invoke(cleanDto, "setTags", replacement);
        Object cleanAccumulated = invoke(impl, "updateFrom", cleanDto);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> replaced =
            (Validated<NonEmptyList<FieldError>, Object>)
                invoke(cleanAccumulated, "apply", current);
        Assertions.assertThat(replaced.isValid()).isTrue();
        Assertions.assertThat(invoke(replaced.get(), "tags")).isSameAs(replacement);
        Assertions.assertThat(invoke(replaced.get(), "labels"))
            .isEqualTo(java.util.Map.of("tier", "gold"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a leafless container pair reports both leaf forms, element first")
    void leaflessContainerPairSuggestsBothForms() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.LeaflessPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface LeaflessPatchMapping extends UpdateSpec<Contact, ContactPatchDto> {}
              """);
      Compilation compilation = compile(PHONE, CONTACT, CONTACT_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare an element leaf 'default"
                  + " ValidatedPrism<java.lang.String, com.example.PhoneNumber> phones()'");
      assertThat(compilation).hadErrorContaining("whole-container leaf");
    }

    @Test
    @DisplayName(
        "raw and wildcard identity containers stay plain identity writes: the scan helper cannot"
            + " type them, and the generated code must always compile")
    void rawAndWildcardIdentityContainersStayPlainWrites() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.WildBag",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              @SuppressWarnings("rawtypes")
              public record WildBag(
                  List<? extends CharSequence> wilds,
                  Map<String, ? extends Number> attrs,
                  List rawTags,
                  Map rawLabels) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.WildBagPatchDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Map;

              @SuppressWarnings("rawtypes")
              public class WildBagPatchDto {
                private List<? extends CharSequence> wilds;
                private Map<String, ? extends Number> attrs;
                private List rawTags;
                private Map rawLabels;

                public List<? extends CharSequence> getWilds() { return wilds; }
                public void setWilds(List<? extends CharSequence> wilds) { this.wilds = wilds; }
                public Map<String, ? extends Number> getAttrs() { return attrs; }
                public void setAttrs(Map<String, ? extends Number> attrs) { this.attrs = attrs; }
                public List getRawTags() { return rawTags; }
                public void setRawTags(List rawTags) { this.rawTags = rawTags; }
                public Map getRawLabels() { return rawLabels; }
                public void setRawLabels(Map rawLabels) { this.rawLabels = rawLabels; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WildBagPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface WildBagPatchMapping extends UpdateSpec<WildBag, WildBagPatchDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.WildBagPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("Edit.setIfPresent(")
          .doesNotContain("hkj$allPresent")
          .doesNotContain("hkj$valuesPresent")
          .doesNotContain("parseIfPresent");
    }

    @Test
    @DisplayName("a container-against-scalar mismatch falls back to the whole-component suggestion")
    void containerVersusScalarMismatchSuggestsWholeComponentOnly() {
      JavaFileObject listVsScalar =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch1",
              """
              package com.example;

              public record Mismatch1(String phones) {}
              """);
      JavaFileObject listDto =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch1PatchDto",
              """
              package com.example;

              import java.util.List;

              public class Mismatch1PatchDto {
                private List<String> phones;

                public List<String> getPhones() { return phones; }
                public void setPhones(List<String> phones) { this.phones = phones; }
              }
              """);
      JavaFileObject listSpec =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch1PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Mismatch1PatchMapping
                  extends UpdateSpec<Mismatch1, Mismatch1PatchDto> {}
              """);
      Compilation listCase = compile(listVsScalar, listDto, listSpec);
      assertThat(listCase).failed();
      assertThat(listCase).hadErrorContaining("cannot be written into");
      Assertions.assertThat(listCase.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));

      JavaFileObject optionalVsScalar =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch2",
              """
              package com.example;

              public record Mismatch2(String backup) {}
              """);
      JavaFileObject optionalDto =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch2PatchDto",
              """
              package com.example;

              import java.util.Optional;

              public class Mismatch2PatchDto {
                private Optional<String> backup;

                public Optional<String> getBackup() { return backup; }
                public void setBackup(Optional<String> backup) { this.backup = backup; }
              }
              """);
      JavaFileObject optionalSpec =
          JavaFileObjects.forSourceString(
              "com.example.Mismatch2PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Mismatch2PatchMapping
                  extends UpdateSpec<Mismatch2, Mismatch2PatchDto> {}
              """);
      Compilation optionalCase = compile(optionalVsScalar, optionalDto, optionalSpec);
      assertThat(optionalCase).failed();
      assertThat(optionalCase).hadErrorContaining("cannot be written into");
      Assertions.assertThat(optionalCase.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));
    }

    @Test
    @DisplayName("a new correspondence Kind must choose its sparse emission before landing")
    void kindCanary() {
      // writeUpdateImpl's kind->parser switch routes unlisted kinds to prism::parse, which is
      // correct only for LEAF — the one unlisted kind a parsed sparse edit can carry today. A new
      // Kind constant fails this pin: give it an explicit arm there (the dense parseLeg switch is
      // compiler-enforced already) before extending this list.
      Assertions.assertThat(java.util.Arrays.stream(MappingProcessor.Kind.values()).map(Enum::name))
          .containsExactlyInAnyOrder(
              "IDENTITY",
              "IDENTITY_LIST",
              "IDENTITY_MAP",
              "LEAF",
              "LIST",
              "OPTIONAL",
              "OPTIONAL_BRIDGE",
              "MAP",
              "DERIVED");
    }

    @Test
    @DisplayName("a leafless map pair with matching keys reports both leaf forms, value leaf first")
    void leaflessMapPairSuggestsBothForms() {
      JavaFileObject profile =
          JavaFileObjects.forSourceString(
              "com.example.Profile2",
              """
              package com.example;

              import java.util.Map;

              public record Profile2(Map<String, EmailAddress> contacts) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.Profile2PatchDto",
              """
              package com.example;

              import java.util.Map;

              public class Profile2PatchDto {
                private Map<String, String> contacts;

                public Map<String, String> getContacts() { return contacts; }
                public void setContacts(Map<String, String> contacts) { this.contacts = contacts; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Profile2PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Profile2PatchMapping
                  extends UpdateSpec<Profile2, Profile2PatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, profile, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Declare an element leaf 'default"
                  + " ValidatedPrism<java.lang.String, com.example.EmailAddress> contacts()'");
      assertThat(compilation).hadErrorContaining("whole-container leaf");
    }

    @Test
    @DisplayName(
        "a map pair with mismatched keys never gets the futile element suggestion: the value leaf"
            + " would not be consulted")
    void mismatchedKeyMapPairSuggestsWholeContainerOnly() {
      JavaFileObject profile =
          JavaFileObjects.forSourceString(
              "com.example.Profile3",
              """
              package com.example;

              import java.util.Map;

              public record Profile3(Map<String, EmailAddress> contacts) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.Profile3PatchDto",
              """
              package com.example;

              import java.util.Map;

              public class Profile3PatchDto {
                private Map<Integer, String> contacts;

                public Map<Integer, String> getContacts() { return contacts; }
                public void setContacts(Map<Integer, String> contacts) { this.contacts = contacts; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Profile3PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Profile3PatchMapping
                  extends UpdateSpec<Profile3, Profile3PatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, profile, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      Assertions.assertThat(compilation.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));
    }

    @Test
    @DisplayName("a wildcard wire element never gets the unmatchable element suggestion")
    void wildcardWireElementSuggestsNoElementLeaf() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.WildcardPatchDto",
              """
              package com.example;

              import java.util.List;

              public class WildcardPatchDto {
                private List<? extends String> phones;

                public List<? extends String> getPhones() { return phones; }
                public void setPhones(List<? extends String> phones) { this.phones = phones; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WildcardPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface WildcardPatchMapping
                  extends UpdateSpec<Contact, WildcardPatchDto> {}
              """);
      Compilation compilation = compile(PHONE, CONTACT, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      Assertions.assertThat(compilation.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));
    }

    @Test
    @DisplayName("a wildcard domain element never gets the unmatchable element suggestion")
    void wildcardDomainElementSuggestsNoElementLeaf() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.WildcardDomain",
              """
              package com.example;

              import java.util.List;

              public record WildcardDomain(String name, List<? extends PhoneNumber> phones) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.WildcardDomainPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface WildcardDomainPatchMapping
                  extends UpdateSpec<WildcardDomain, ContactPatchDto> {}
              """);
      Compilation compilation = compile(PHONE, CONTACT_PATCH_DTO, domain, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      Assertions.assertThat(compilation.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));
    }

    @Test
    @DisplayName(
        "a leafless Optional pair is a no-update-source, not the bridge rejection: emptiness is"
            + " expressible, only the element types stopped it")
    void leaflessOptionalPairIsNoSource() {
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.example.Account2",
              """
              package com.example;

              import java.util.Optional;

              public record Account2(String name, Optional<EmailAddress> backup) {}
              """);
      JavaFileObject accountPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.Account2PatchDto",
              """
              package com.example;

              import java.util.Optional;

              public class Account2PatchDto {
                private Optional<String> backup;

                public Optional<String> getBackup() { return backup; }
                public void setBackup(Optional<String> backup) { this.backup = backup; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Account2PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Account2PatchMapping
                  extends UpdateSpec<Account2, Account2PatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, account, accountPatchDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare an element leaf 'default"
                  + " ValidatedPrism<java.lang.String, com.example.EmailAddress> backup()'");
    }
  }

  @Nested
  @DisplayName("Renames")
  class Renames {

    @Test
    @DisplayName("@MapField renames a domain component to a differently-named wire property")
    void renameThroughMapField() {
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              public record Account(String owner) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchDto",
              """
              package com.example;

              public class AccountPatchDto {
                private String holder;
                public String getHolder() { return holder; }
                public void setHolder(String holder) { this.holder = holder; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface AccountPatchMapping extends UpdateSpec<Account, AccountPatchDto> {
                @MapField(to = "holder")
                String owner();
              }
              """);

      Compilation compilation = compile(account, dto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.AccountPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("Setter.fromGetSet(Account::owner, (d, v) -> new Account(v))")
          .contains("wire.getHolder()")
          .contains("public String owner()");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.AccountPatchMappingImpl");
        Object current = result.newInstance("com.example.Account", "Ada");
        Object patch =
            result.loadClass("com.example.AccountPatchDto").getDeclaredConstructor().newInstance();
        invoke(patch, "setHolder", "Grace");

        Object accumulated = invoke(impl, "updateFrom", patch);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(invoke(patched.get(), "owner")).isEqualTo("Grace");
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("a rename inherited from a mix-in drives the sparse patch")
    void inheritedRenameDrivesTheSparsePatch() {
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              public record Account(String owner) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchDto",
              """
              package com.example;

              public class AccountPatchDto {
                private String holder;
                public String getHolder() { return holder; }
                public void setHolder(String holder) { this.holder = holder; }
              }
              """);
      JavaFileObject vocabulary =
          JavaFileObjects.forSourceString(
              "com.example.OwnerVocabulary",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.MapField;

              public interface OwnerVocabulary {
                @MapField(to = "holder")
                String owner();
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.AccountPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface AccountPatchMapping
                  extends OwnerVocabulary, UpdateSpec<Account, AccountPatchDto> {}
              """);

      Compilation compilation = compile(account, dto, vocabulary, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AccountPatchMappingImpl"))
          .contains("wire.getHolder()")
          .contains("public String owner()");
    }
  }

  @Nested
  @DisplayName("Nesting")
  class Nesting {

    private static final JavaFileObject ADDRESS =
        JavaFileObjects.forSourceString(
            "com.example.Address",
            """
            package com.example;

            public record Address(String city) {}
            """);

    private static final JavaFileObject ADDRESS_DTO =
        JavaFileObjects.forSourceString(
            "com.example.AddressDto",
            """
            package com.example;

            public class AddressDto {
              private String city;
              public String getCity() { return city; }
              public void setCity(String city) { this.city = city; }
            }
            """);

    private static final JavaFileObject CUSTOMER =
        JavaFileObjects.forSourceString(
            "com.example.Customer",
            """
            package com.example;

            public record Customer(Address address) {}
            """);

    private static final JavaFileObject CUSTOMER_PATCH_DTO =
        JavaFileObjects.forSourceString(
            "com.example.CustomerPatchDto",
            """
            package com.example;

            public class CustomerPatchDto {
              private AddressDto address;
              public AddressDto getAddress() { return address; }
              public void setAddress(AddressDto address) { this.address = address; }
            }
            """);

    private static final JavaFileObject ADDRESS_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.AddressMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface AddressMapping extends MappingSpec<Address, AddressDto> {}
            """);

    private static final JavaFileObject CUSTOMER_PATCH_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CustomerPatchMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.UpdateSpec;

            @GenerateMapping
            public interface CustomerPatchMapping extends UpdateSpec<Customer, CustomerPatchDto> {}
            """);

    @Test
    @DisplayName("a nested record is patched wholesale through its own full mapping spec")
    void nestedThroughFullSpec() {
      Compilation compilation =
          compile(
              ADDRESS,
              ADDRESS_DTO,
              CUSTOMER,
              CUSTOMER_PATCH_DTO,
              ADDRESS_MAPPING,
              CUSTOMER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.CustomerPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("Edit.parseIfPresent(")
          .contains("AddressMappingImpl.INSTANCE.asValidatedPrism()::parse")
          .contains("wire.getAddress()")
          .contains(".at(\"address\")");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CustomerPatchMappingImpl");
        Object oldAddress = result.newInstance("com.example.Address", "OldCity");
        Object current = result.newInstance("com.example.Customer", oldAddress);

        Object addressDto =
            result.loadClass("com.example.AddressDto").getDeclaredConstructor().newInstance();
        invoke(addressDto, "setCity", "NewCity");
        Object patch =
            result.loadClass("com.example.CustomerPatchDto").getDeclaredConstructor().newInstance();
        invoke(patch, "setAddress", addressDto);

        Object accumulated = invoke(impl, "updateFrom", patch);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(patched.isValid()).isTrue();
        Object newAddress = result.newInstance("com.example.Address", "NewCity");
        Assertions.assertThat(invoke(patched.get(), "address")).isEqualTo(newAddress);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("an absent nested record leaves the domain component unchanged")
    void absentNestedUnchanged() {
      Compilation compilation =
          compile(
              ADDRESS,
              ADDRESS_DTO,
              CUSTOMER,
              CUSTOMER_PATCH_DTO,
              ADDRESS_MAPPING,
              CUSTOMER_PATCH_MAPPING);
      assertThat(compilation).succeeded();
      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CustomerPatchMappingImpl");
        Object oldAddress = result.newInstance("com.example.Address", "OldCity");
        Object current = result.newInstance("com.example.Customer", oldAddress);
        Object patch =
            result
                .loadClass("com.example.CustomerPatchDto")
                .getDeclaredConstructor()
                .newInstance(); // address left null

        Object accumulated = invoke(impl, "updateFrom", patch);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> patched =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(patched.get()).isEqualTo(current);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("two full specs mapping the same nested pair are ambiguous")
    void ambiguousNestedSpec() {
      JavaFileObject addressMapping2 =
          JavaFileObjects.forSourceString(
              "com.example.AddressMapping2",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AddressMapping2 extends MappingSpec<Address, AddressDto> {}
              """);
      Compilation compilation =
          compile(
              ADDRESS,
              ADDRESS_DTO,
              CUSTOMER,
              CUSTOMER_PATCH_DTO,
              ADDRESS_MAPPING,
              addressMapping2,
              CUSTOMER_PATCH_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("matches more than one mapping spec");
    }
  }

  @Nested
  @DisplayName("Shape diagnostics")
  class ShapeDiagnostics {

    @Test
    @DisplayName("a raw UpdateSpec (no type arguments) is rejected")
    void rawUpdateSpec() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RawMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              @SuppressWarnings("rawtypes")
              public interface RawMapping extends UpdateSpec {}
              """);
      Compilation compilation = compile(spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not extend UpdateSpec<Domain, Wire>");
    }

    @Test
    @DisplayName("a mix-in carrying a leaf is accepted on an update spec")
    void extraSuperinterface() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ExtraMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              interface Marker {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }
              }

              @GenerateMapping
              public interface ExtraMapping extends UpdateSpec<User, UserPatchDto>, Marker {}
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).succeededWithoutWarnings();
      assertThat(compilation).generatedSourceFile("com.example.ExtraMappingImpl");
    }

    @Test
    @DisplayName("a generic mix-in is read under the spec on the update path too")
    void genericMixinResolved() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.GenericMixinMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              interface Vocabulary<T> {
                  default org.higherkindedj.optics.validated.ValidatedPrism<String, T> email() {
                      return org.higherkindedj.optics.validated.ValidatedPrism.of(
                          raw ->
                              org.higherkindedj.hkt.validated.Validated.invalidNel(
                                  org.higherkindedj.hkt.validated.FieldError.of("not supplied")),
                          value -> "");
                  }
              }

              @GenerateMapping
              public interface GenericMixinMapping
                  extends Vocabulary<EmailAddress>, UpdateSpec<User, UserPatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.GenericMixinMappingImpl");
    }

    @Test
    @DisplayName("a generic mix-in reached through a non-generic one is read under the spec")
    void transitiveGenericMixinResolved() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TransitiveMixinMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              interface BaseVocabulary<T> {
                  default org.higherkindedj.optics.validated.ValidatedPrism<String, T> email() {
                      return org.higherkindedj.optics.validated.ValidatedPrism.of(
                          raw ->
                              org.higherkindedj.hkt.validated.Validated.invalidNel(
                                  org.higherkindedj.hkt.validated.FieldError.of("not supplied")),
                          value -> "");
                  }
              }

              interface Vocabulary extends BaseVocabulary<EmailAddress> {}

              @GenerateMapping
              public interface TransitiveMixinMapping
                  extends Vocabulary, UpdateSpec<User, UserPatchDto> {}
              """);

      // 'ValidatedPrism<String, T>' is BaseVocabulary's vocabulary; the interface below it says T
      // is EmailAddress, and the spec has it at that. The update path reads members the same way
      // the
      // mapping path does, so it resolves here too.
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);

      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.example.TransitiveMixinMappingImpl");
    }

    @Test
    @DisplayName("a mix-in that is itself an update spec is rejected")
    void updateSpecMixinRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.StackedPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              interface BasePatch extends UpdateSpec<User, UserPatchDto> {}

              @GenerateMapping
              public interface StackedPatchMapping
                  extends BasePatch, UpdateSpec<User, UserPatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("mix-in 'BasePatch' is itself a mapping spec");
    }

    @Test
    @DisplayName("a sealed domain is rejected (dispatch has no sparse meaning)")
    void sealedDomain() {
      JavaFileObject shape =
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
      JavaFileObject dto =
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

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface ShapeMapping extends UpdateSpec<Shape, ShapeDto> {}
              """);
      Compilation compilation = compile(shape, circle, dto, circleDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot map a sealed hierarchy");
    }

    @Test
    @DisplayName("a sealed wire (record domain) is rejected too")
    void sealedWire() {
      JavaFileObject dto =
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
              "com.example.WireMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface WireMapping extends UpdateSpec<User, ShapeDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, dto, circleDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot map a sealed hierarchy");
    }

    @Test
    @DisplayName("a bean-shaped domain is rejected (parse assembles a record)")
    void beanDomain() {
      JavaFileObject domainBean =
          JavaFileObjects.forSourceString(
              "com.example.UserBean",
              """
              package com.example;

              public class UserBean {
                private String name;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BeanDomainMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface BeanDomainMapping extends UpdateSpec<UserBean, UserPatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER_PATCH_DTO, domainBean, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("UpdateSpec domain type argument");
      assertThat(compilation).hadErrorContaining("is not a record");
    }

    @Test
    @DisplayName("a record wire is rejected (a record cannot express absence)")
    void recordWire() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.UserRecordDto",
              """
              package com.example;

              public record UserRecordDto(String name) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RecordWireMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface RecordWireMapping extends UpdateSpec<User, UserRecordDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("is a record, which a sparse UpdateSpec cannot map");
    }

    @Test
    @DisplayName("a wire that is neither a record nor a bean is rejected")
    void nonBeanNonRecordWire() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.WireIface",
              """
              package com.example;

              public interface WireIface {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.IfaceWireMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface IfaceWireMapping extends UpdateSpec<User, WireIface> {}
              """);
      Compilation compilation = compile(EMAIL, USER, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("UpdateSpec wire type argument");
      assertThat(compilation).hadErrorContaining("is not a bean-shaped class");
    }

    @Test
    @DisplayName("a generic spec is rejected")
    void genericSpec() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.GenericMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface GenericMapping<T> extends UpdateSpec<User, UserPatchDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is generic");
    }

    @Test
    @DisplayName("an unusable bean wire (no getters/setters) is rejected")
    void unusableBean() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.OpaqueDto",
              """
              package com.example;

              public class OpaqueDto {
                private String name;
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.OpaqueMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface OpaqueMapping extends UpdateSpec<User, OpaqueDto> {}
              """);
      Compilation compilation = compile(EMAIL, USER, wire, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is not a usable bean-shaped wire");
    }

    @Test
    @DisplayName("a malformed @MapField (with a body) is rejected on the update path")
    void malformedMapField() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BadRenameMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface BadRenameMapping extends UpdateSpec<User, UserPatchDto> {
                @MapField(to = "name")
                default String name() { return ""; }
              }
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("must be abstract");
    }

    @Test
    @DisplayName("a @MapField naming no domain component is rejected on the update path")
    void renameNamesNoDomainComponent() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.StrayRenameMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface StrayRenameMapping extends UpdateSpec<User, UserPatchDto> {
                @MapField(to = "name")
                String nickname();
              }
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("does not name a component of User");
    }
  }

  @Nested
  @DisplayName("Classification diagnostics")
  class ClassificationDiagnostics {

    @Test
    @DisplayName("a primitive wire property is rejected (it can never be absent)")
    void primitiveProperty() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.CountPatchDto",
              """
              package com.example;

              public class CountPatchDto {
                private int count;
                public int getCount() { return count; }
                public void setCount(int count) { this.count = count; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Counter",
              """
              package com.example;

              public record Counter(int count) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CounterPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface CounterPatchMapping extends UpdateSpec<Counter, CountPatchDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("is primitive and can never be absent");
    }

    @Test
    @DisplayName("a wire property with no domain component is rejected")
    void danglingWireProperty() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.StrayPatchDto",
              """
              package com.example;

              public class StrayPatchDto {
                private String owner;
                private String extra;
                public String getOwner() { return owner; }
                public void setOwner(String owner) { this.owner = owner; }
                public String getExtra() { return extra; }
                public void setExtra(String extra) { this.extra = extra; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Owned",
              """
              package com.example;

              public record Owned(String owner) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.OwnedPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface OwnedPatchMapping extends UpdateSpec<Owned, StrayPatchDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("names no component of Owned");
    }

    @Test
    @DisplayName("a type mismatch against a primitive component with no leaf is rejected")
    void mismatchPrimitiveComponentNoLeaf() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.TextCountDto",
              """
              package com.example;

              public class TextCountDto {
                private String count;
                public String getCount() { return count; }
                public void setCount(String count) { this.count = count; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Counter2",
              """
              package com.example;

              public record Counter2(int count) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.Counter2PatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface Counter2PatchMapping extends UpdateSpec<Counter2, TextCountDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      // A leaf can never target a primitive component, so the fix steers to type alignment only.
      assertThat(compilation).hadErrorContaining("Align the types");
      assertThat(compilation).hadErrorContaining("make 'count' a wrapper type");
    }

    @Test
    @DisplayName("two wire properties resolving to one domain component are rejected")
    void duplicateDomainTarget() {
      // A rename (owner -> holder) plus a same-named 'owner' getter both land on Account.owner.
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.DupPatchDto",
              """
              package com.example;

              public class DupPatchDto {
                private String owner;
                private String holder;
                public String getOwner() { return owner; }
                public void setOwner(String owner) { this.owner = owner; }
                public String getHolder() { return holder; }
                public void setHolder(String holder) { this.holder = holder; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Acct",
              """
              package com.example;

              public record Acct(String owner) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DupPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapField;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface DupPatchMapping extends UpdateSpec<Acct, DupPatchDto> {
                @MapField(to = "holder")
                String owner();
              }
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("both write Acct.owner");
    }

    @Test
    @DisplayName("a domain Optional component (null-as-absent bridge) is rejected")
    void optionalBridgeRejected() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchDto",
              """
              package com.example;

              public class ProfilePatchDto {
                private String nickname;
                public String getNickname() { return nickname; }
                public void setNickname(String nickname) { this.nickname = nickname; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Profile",
              """
              package com.example;

              import java.util.Optional;

              public record Profile(Optional<String> nickname) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ProfilePatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface ProfilePatchMapping extends UpdateSpec<Profile, ProfilePatchDto> {}
              """);
      Compilation compilation = compile(domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("which a sparse update cannot express");
    }

    @Test
    @DisplayName("a derived-field default method has no meaning on an UpdateSpec")
    void derivedFieldRejected() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.DerivedPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.Getter;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface DerivedPatchMapping extends UpdateSpec<User, UserPatchDto> {
                default Getter<User, String> summary() {
                  return User::name;
                }
              }
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("has no meaning on a sparse UpdateSpec");
    }

    @Test
    @DisplayName("a type mismatch against a reference component with no leaf is rejected")
    void mismatchReferenceComponentNoLeaf() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.NumEmailDto",
              """
              package com.example;

              public class NumEmailDto {
                private Integer email;
                public Integer getEmail() { return email; }
                public void setEmail(Integer email) { this.email = email; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Contact",
              """
              package com.example;

              public record Contact(EmailAddress email) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ContactPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface ContactPatchMapping extends UpdateSpec<Contact, NumEmailDto> {}
              """);
      Compilation compilation = compile(EMAIL, domain, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("cannot be written into");
      // A reference-typed component CAN take a leaf, so the fix offers one.
      assertThat(compilation).hadErrorContaining("Declare a leaf 'default ValidatedPrism<");
    }
  }

  @Nested
  @DisplayName("Generated-member collision sweep")
  class GeneratedMemberCollisionSweep {

    @Test
    @DisplayName("a default with the generated 'updateFrom' signature is rejected")
    void updateFromCollisionIsRejected() {
      JavaFileObject colliding =
          JavaFileObjects.forSourceString(
              "com.example.UserPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.edit.Edits;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface UserPatchMapping extends UpdateSpec<User, UserPatchDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                default Edits.Accumulated<User> updateFrom(UserPatchDto wire) {
                  return null;
                }
              }
              """);

      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, colliding);

      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'updateFrom(UserPatchDto)' collides with the 'updateFrom' member the generated"
                  + " UserPatchMappingImpl emits");
      assertThat(compilation).hadErrorContaining("a sparse update");
    }

    @Test
    @DisplayName(
        "'build' and 'parse' helpers stay legal: a sparse update reserves only" + " 'updateFrom'")
    void buildAndParseHelpersStayLegal() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.UserPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface UserPatchMapping extends UpdateSpec<User, UserPatchDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.contains("@")
                              ? Validated.validNel(new EmailAddress(raw))
                              : Validated.invalidNel(FieldError.of("not an email address")),
                      EmailAddress::value);
                }

                default UserPatchDto build(User domain) {
                  return new UserPatchDto();
                }

                default Validated<NonEmptyList<FieldError>, User> parse(UserPatchDto wire) {
                  return Validated.invalidNel(FieldError.of("a helper, not a collision"));
                }
              }
              """);

      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);

      assertThat(compilation).succeeded();
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

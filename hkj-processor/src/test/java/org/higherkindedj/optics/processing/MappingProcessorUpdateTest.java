// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.hkt.assertions.ValidatedAssert.assertThatValidated;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
          // The setters write a Components record, and the lens constructs the User once.
          .contains("Edits.<User, Components>accumulate(")
          .contains("d -> new Components(d.name(), d.email(), d.age())")
          .contains("(d, c) -> new User(c.name(), c.email(), c.age())")
          .contains("private record Components(String name, EmailAddress email, int age)")
          .contains("Edit.setIfPresent(")
          .contains(
              "Setter.fromGetSet(Components::name, (c, v) -> new Components(v, c.email(),"
                  + " c.age()))")
          .contains("wire.getName()")
          .contains("Edit.parseIfPresent(")
          .contains(
              "Setter.fromGetSet(Components::email, (c, v) -> new Components(c.name(), v,"
                  + " c.age()))")
          .contains("wire.getEmail()")
          .contains("email()::parse")
          .contains(".at(\"email\")")
          .contains(
              "Setter.fromGetSet(Components::age, (c, v) -> new Components(c.name(), c.email(),"
                  + " v))")
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
  @DisplayName("Construct once - the domain's constructor sees only the values a PATCH ends on")
  class ConstructOnce {

    /**
     * One compilation for every case, under {@code -Xlint:all -Werror}: a record checking its
     * fields against each other, one refusing without a reason, one with no check whose PATCH
     * covers one field, one whose uncovered components the Impl cannot name without a warning or at
     * all, one with a same-arity overload of its constructor, one whose covered components carry
     * annotations the Impl can and cannot write, and a spec nesting types named like the Impl's
     * own. {@code Probes} calls the generated code directly, so each case is one static call.
     */
    private static final class Fixture {
      static final Compilation COMPILATION =
          javac()
              .withProcessors(new MappingProcessor())
              .withOptions("-Xlint:all,-processing", "-Werror")
              .compile(
                  JavaFileObjects.forSourceString(
                      "com.example.Range",
                      """
                      package com.example;

                      public record Range(int lo, int hi) {
                        public Range {
                          if (lo > hi) {
                            throw new IllegalArgumentException("lo > hi");
                          }
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Quiet",
                      """
                      package com.example;

                      public record Quiet(int n) {
                        public Quiet {
                          if (n < 0) {
                            throw new IllegalArgumentException();
                          }
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Plain",
                      """
                      package com.example;

                      public record Plain(String name, int age) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Audit",
                      """
                      package com.example.hidden;

                      record Audit(String who) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Legacy",
                      """
                      package com.example.hidden;

                      @Deprecated(forRemoval = true)
                      public record Legacy(String code) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Money",
                      """
                      package com.example;

                      public record Money(long cents, String currency) {
                        public Money(Number pounds, String currency) {
                          this(Math.round(pounds.doubleValue() * 100), currency);
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Checked",
                      """
                      package com.example.hidden;

                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;

                      @Target(ElementType.TYPE_USE)
                      @interface Checked {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Old",
                      """
                      package com.example.hidden;

                      import java.lang.annotation.ElementType;
                      import java.lang.annotation.Target;

                      @Deprecated
                      @Target(ElementType.TYPE_USE)
                      public @interface Old {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Tagged",
                      """
                      package com.example.hidden;

                      import org.jspecify.annotations.Nullable;

                      @SuppressWarnings("deprecation")
                      public record Tagged(@Checked String name, @Old String note,
                          @Nullable String remark) {}
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.hidden.Account",
                      """
                      package com.example.hidden;

                      @SuppressWarnings("removal")
                      public record Account(String name, Audit audit, Legacy legacy) {
                        public static Account opened(String name) {
                          return new Account(name, new Audit("system"), new Legacy("L1"));
                        }

                        public String auditor() {
                          return audit.who();
                        }
                      }
                      """),
                  bean("RangePatch", "Integer", "lo", "hi"),
                  bean("QuietPatch", "Integer", "n"),
                  bean("PlainPatch", "String", "name"),
                  bean("AccountPatch", "String", "name"),
                  bean("MoneyPatch", "Long", "cents"),
                  bean("TaggedPatch", "String", "name", "note", "remark"),
                  JavaFileObjects.forSourceString(
                      "com.example.Specs",
                      """
                      package com.example;

                      import com.example.hidden.Account;
                      import com.example.hidden.Tagged;
                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.UpdateSpec;

                      public final class Specs {
                        private Specs() {}

                        @GenerateMapping
                        public interface RangePatchMapping extends UpdateSpec<Range, RangePatch> {}

                        @GenerateMapping
                        public interface QuietPatchMapping extends UpdateSpec<Quiet, QuietPatch> {}

                        @GenerateMapping
                        public interface PlainPatchMapping extends UpdateSpec<Plain, PlainPatch> {}

                        @GenerateMapping
                        public interface AccountPatchMapping
                            extends UpdateSpec<Account, AccountPatch> {}

                        @GenerateMapping
                        public interface MoneyPatchMapping extends UpdateSpec<Money, MoneyPatch> {}

                        @GenerateMapping
                        public interface TaggedPatchMapping
                            extends UpdateSpec<Tagged, TaggedPatch> {}

                        @GenerateMapping
                        public interface ClashPatchMapping extends UpdateSpec<Plain, PlainPatch> {
                          interface Lens {}

                          interface Components {}

                          interface Setter {}
                        }
                      }
                      """),
                  JavaFileObjects.forSourceString(
                      "com.example.Probes",
                      """
                      package com.example;

                      import com.example.hidden.Account;
                      import com.example.hidden.Tagged;

                      public final class Probes {
                        private Probes() {}

                        static Object money(Long cents) {
                          MoneyPatch patch = new MoneyPatch();
                          patch.setCents(cents);
                          return SpecsMoneyPatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Money(100L, "GBP"));
                        }

                        static Object tagged(String remark) {
                          TaggedPatch patch = new TaggedPatch();
                          patch.setRemark(remark);
                          return SpecsTaggedPatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Tagged("a", "b", null));
                        }

                        static Object clash(String name) {
                          PlainPatch patch = new PlainPatch();
                          patch.setName(name);
                          return SpecsClashPatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Plain("Ada", 42));
                        }

                        static Object range(Integer lo, Integer hi) {
                          RangePatch patch = new RangePatch();
                          patch.setLo(lo);
                          patch.setHi(hi);
                          return SpecsRangePatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Range(1, 3));
                        }

                        static Object quiet(Integer n) {
                          QuietPatch patch = new QuietPatch();
                          patch.setN(n);
                          return SpecsQuietPatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Quiet(1));
                        }

                        static Object plain(String name) {
                          PlainPatch patch = new PlainPatch();
                          patch.setName(name);
                          return SpecsPlainPatchMappingImpl.INSTANCE.updateFrom(patch)
                              .apply(new Plain("Ada", 42));
                        }

                        static String account(String name) {
                          AccountPatch patch = new AccountPatch();
                          patch.setName(name);
                          Account patched =
                              SpecsAccountPatchMappingImpl.INSTANCE.updateFrom(patch)
                                  .apply(Account.opened("Ada"))
                                  .get();
                          return patched.name() + " audited by " + patched.auditor();
                        }
                      }
                      """));
      static final RuntimeCompilationHelper.CompiledResult RESULT =
          new RuntimeCompilationHelper.CompiledResult(COMPILATION);

      /** A PATCH bean in {@code com.example} with one {@code type} property per name. */
      private static JavaFileObject bean(String name, String type, String... properties) {
        StringBuilder body = new StringBuilder();
        for (String property : properties) {
          String capitalised = Character.toUpperCase(property.charAt(0)) + property.substring(1);
          body.append(
              """
                private %1$s %2$s;

                public %1$s get%3$s() { return %2$s; }

                public void set%3$s(%1$s %2$s) { this.%2$s = %2$s; }
              """
                  .formatted(type, property, capitalised));
        }
        return JavaFileObjects.forSourceString(
            "com.example." + name,
            "package com.example;\n\npublic class " + name + " {\n" + body + "}\n");
      }
    }

    /** Calls {@code Probes.name(args)} in the compiled fixture. */
    private static Object call(String name, Object... args) {
      try {
        return Fixture.RESULT.invokeStatic("com.example.Probes", name, args);
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @SuppressWarnings("unchecked") // reflective call into the generated Impl
    private static Validated<NonEmptyList<FieldError>, Object> probe(String name, Object... args) {
      return (Validated<NonEmptyList<FieldError>, Object>) call(name, args);
    }

    @Test
    @DisplayName("compiles without a warning, the uncovered components never written out")
    void compilesWithoutWarnings() {
      assertThat(Fixture.COMPILATION).succeededWithoutWarnings();
      Assertions.assertThat(
              generatedSource(Fixture.COMPILATION, "com.example.SpecsAccountPatchMappingImpl"))
          .contains("private record Components(String name)")
          .contains("(d, c) -> new Account(c.name(), d.audit(), d.legacy())");
    }

    @Test
    @DisplayName("a PATCH ending on valid values is valid, though one field at a time would not be")
    void validFinalValueIsValid() {
      // lo 5 first would build Range(5, 3), which the constructor refuses.
      assertThatValidated(probe("range", 5, 10))
          .hasValueSatisfying(
              range -> range.toString().equals("Range[lo=5, hi=10]"), "Range[lo=5, hi=10]");
    }

    @Test
    @DisplayName("a PATCH ending on refused values is Invalid at the root, with the message")
    void refusedFinalValueIsInvalid() {
      assertThatValidated(probe("range", 5, null))
          .hasError(NonEmptyList.of(FieldError.of("lo > hi")));
    }

    @Test
    @DisplayName("a refusal without a reason names the domain")
    void refusalWithoutReasonNamesTheDomain() {
      assertThatValidated(probe("quiet", -1)).hasFieldErrors("not a valid Quiet");
    }

    @Test
    @DisplayName("a record with no check keeps what the PATCH leaves out")
    void uncheckedRecordKeepsAbsentAndUncovered() {
      assertThatValidated(probe("plain", "Grace"))
          .hasValueSatisfying(
              plain -> plain.toString().equals("Plain[name=Grace, age=42]"), "the name patched");
      assertThatValidated(probe("plain", (Object) null))
          .hasValueSatisfying(
              plain -> plain.toString().equals("Plain[name=Ada, age=42]"), "nothing patched");
    }

    @Test
    @DisplayName("an uncovered component the Impl cannot name is carried over from the domain")
    void uncoveredComponentIsCarriedOver() {
      Assertions.assertThat(call("account", "Grace")).isEqualTo("Grace audited by system");
    }

    @Test
    @DisplayName("the canonical constructor is called, not a same-arity overload")
    void canonicalConstructorIsCalled() {
      // A Long read into Money(Number, String) would be taken as pounds: 25000 cents.
      assertThatValidated(probe("money", 250L))
          .hasValueSatisfying(
              money -> money.toString().equals("Money[cents=250, currency=GBP]"), "250 cents");
    }

    @Test
    @DisplayName("a covered component keeps only the annotations the Impl can write")
    void coveredComponentKeepsWritableAnnotations() {
      // @Checked is package-private in another package and @Old is deprecated: neither compiles
      // cleanly here, and the setters only ever inferred the type they annotate.
      Assertions.assertThat(
              generatedSource(Fixture.COMPILATION, "com.example.SpecsTaggedPatchMappingImpl"))
          .contains("private record Components(String name, String note, @Nullable String remark)");
      assertThatValidated(probe("tagged", "noted"))
          .hasValueSatisfying(
              tagged -> tagged.toString().equals("Tagged[name=a, note=b, remark=noted]"),
              "the remark patched");
    }

    @Test
    @DisplayName("a type the spec nests does not shadow the Impl's own names")
    void specNestedTypesDoNotShadow() {
      assertThatValidated(probe("clash", "Grace"))
          .hasValueSatisfying(
              plain -> plain.toString().equals("Plain[name=Grace, age=42]"), "the name patched");
    }

    @Test
    @DisplayName("an annotation missing from the classpath is left off a covered component")
    void missingAnnotationIsLeftOff(@TempDir Path tmp) throws IOException {
      JavaFileObject tag =
          JavaFileObjects.forSourceString(
              "tag.Tag",
              """
              package tag;

              import java.lang.annotation.ElementType;
              import java.lang.annotation.Retention;
              import java.lang.annotation.RetentionPolicy;
              import java.lang.annotation.Target;

              @Retention(RetentionPolicy.RUNTIME)
              @Target(ElementType.TYPE_USE)
              public @interface Tag {}
              """);
      Compilation tags = javac().compile(tag);
      assertThat(tags).succeeded();
      Path tagClasses = GeneratorTestHelper.classDirectory(tags, tmp.resolve("tags"));
      // The library compiles against the annotation, and does not pass it on.
      Compilation library =
          javac()
              .withClasspath(GeneratorTestHelper.classpathWith(tagClasses))
              .compile(
                  JavaFileObjects.forSourceString(
                      "lib.Labelled",
                      """
                      package lib;

                      public record Labelled(@tag.Tag String label, int rank) {}
                      """));
      assertThat(library).succeeded();
      Path libraryClasses = GeneratorTestHelper.classDirectory(library, tmp.resolve("lib"));

      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withClasspath(GeneratorTestHelper.classpathWith(libraryClasses))
              .withOptions("-Xlint:all,-processing", "-Werror")
              .compile(
                  Fixture.bean("LabelPatch", "String", "label"),
                  JavaFileObjects.forSourceString(
                      "com.example.LabelPatchMapping",
                      """
                      package com.example;

                      import lib.Labelled;
                      import org.higherkindedj.optics.annotations.GenerateMapping;
                      import org.higherkindedj.optics.annotations.UpdateSpec;

                      @GenerateMapping
                      public interface LabelPatchMapping extends UpdateSpec<Labelled, LabelPatch> {}
                      """));

      assertThat(compilation).succeededWithoutWarnings();
      Assertions.assertThat(generatedSource(compilation, "com.example.LabelPatchMappingImpl"))
          .contains("private record Components(String label)");
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
    @DisplayName("a Set patch component lifts and scans on the sparse tier too")
    void setLiftsAndScansOnTheSparseTier() {
      JavaFileObject crew =
          JavaFileObjects.forSourceString(
              "com.example.Crew",
              """
              package com.example;

              import java.util.Set;

              public record Crew(Set<PhoneNumber> phones, Set<String> tags) {}
              """);
      JavaFileObject crewPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.CrewPatchDto",
              """
              package com.example;

              import java.util.Set;

              public class CrewPatchDto {
                private Set<String> phones;
                private Set<String> tags;

                public Set<String> getPhones() { return phones; }
                public void setPhones(Set<String> phones) { this.phones = phones; }
                public Set<String> getTags() { return tags; }
                public void setTags(Set<String> tags) { this.tags = tags; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.CrewPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface CrewPatchMapping
                  extends PhoneVocabulary, UpdateSpec<Crew, CrewPatchDto> {}
              """);
      Compilation compilation = compile(PHONE, PHONE_VOCABULARY, crew, crewPatchDto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.CrewPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("phones()::parseAll")
          .contains("CrewPatchMappingImpl::hkj$allPresent")
          // only the one-level collection helper is declared - the emitted helpers follow the
          // components
          .contains(
              "<C extends Collection<?>> Validated<NonEmptyList<FieldError>, C> hkj$allPresent(")
          .doesNotContain("E[] values")
          .doesNotContain("Function<? super E");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.CrewPatchMappingImpl");
        Object current =
            construct(
                result,
                "com.example.Crew",
                new LinkedHashSet<>(List.of(result.newInstance("com.example.PhoneNumber", "+44"))),
                new LinkedHashSet<>(List.of("vip")));

        Object dto =
            result.loadClass("com.example.CrewPatchDto").getDeclaredConstructor().newInstance();
        invoke(dto, "setPhones", new LinkedHashSet<>(List.of("nope", "+1")));
        Set<String> tagsWithNull = new LinkedHashSet<>(List.of("fine"));
        tagsWithNull.add(null);
        invoke(dto, "setTags", tagsWithNull);
        Object accumulated = invoke(impl, "updateFrom", dto);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> located =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(located.isInvalid()).isTrue();
        // The lifted set locates by the element's own rendering; the identity set's null
        // element is unlocated, a set holding at most one.
        Assertions.assertThat(located.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("phones", "nope"), "not a phone number"),
                new FieldError(List.of("tags"), "must not contain a null element"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("arrays and keyed maps carry the same vocabulary onto the sparse tier")
    void arraysAndKeyedMapsOnTheSparseTier() {
      JavaFileObject stock =
          JavaFileObjects.forSourceString(
              "com.example.Stock",
              """
              package com.example;

              import java.util.Map;

              public record Stock(
                  PhoneNumber[] lines,
                  String[] codes,
                  Map<PhoneNumber, String> byLine,
                  Map<PhoneNumber, PhoneNumber> routes) {}
              """);
      JavaFileObject stockPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.StockPatchDto",
              """
              package com.example;

              import java.util.Map;

              public class StockPatchDto {
                private String[] lines;
                private String[] codes;
                private Map<String, String> byLine;
                private Map<String, String> routes;

                public String[] getLines() { return lines; }
                public void setLines(String[] lines) { this.lines = lines; }
                public String[] getCodes() { return codes; }
                public void setCodes(String[] codes) { this.codes = codes; }
                public Map<String, String> getByLine() { return byLine; }
                public void setByLine(Map<String, String> byLine) { this.byLine = byLine; }
                public Map<String, String> getRoutes() { return routes; }
                public void setRoutes(Map<String, String> routes) { this.routes = routes; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.StockPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.FieldError;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface StockPatchMapping extends UpdateSpec<Stock, StockPatchDto> {
                default ValidatedPrism<String, PhoneNumber> lines() {
                  return phone();
                }

                @MapKey("byLine")
                default ValidatedPrism<String, PhoneNumber> byLineKey() {
                  return phone();
                }

                // both sides of 'routes' convert
                default ValidatedPrism<String, PhoneNumber> routes() {
                  return phone();
                }

                @MapKey("routes")
                default ValidatedPrism<String, PhoneNumber> routeKey() {
                  return phone();
                }

                private static ValidatedPrism<String, PhoneNumber> phone() {
                  return ValidatedPrism.of(
                      raw ->
                          raw.startsWith("+")
                              ? Validated.validNel(new PhoneNumber(raw))
                              : Validated.invalidNel(FieldError.of("not a phone number")),
                      PhoneNumber::value);
                }
              }
              """);
      Compilation compilation = compile(PHONE, stock, stockPatchDto, spec);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.StockPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("v -> lines().parseAll(v, PhoneNumber[]::new)")
          .contains("byLineKey()::parseKeys")
          .contains("v -> routeKey().parseEntries(v, routes())")
          // the identity String[] carries the array scan overload
          .contains("StockPatchMappingImpl::hkj$allPresent")
          .contains("E[] values");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.StockPatchMappingImpl");
        Object current =
            construct(
                result,
                "com.example.Stock",
                java.lang.reflect.Array.newInstance(result.loadClass("com.example.PhoneNumber"), 0),
                new String[] {"old"},
                java.util.Map.of(),
                java.util.Map.of());
        Object dto =
            result.loadClass("com.example.StockPatchDto").getDeclaredConstructor().newInstance();
        invoke(dto, "setLines", (Object) new String[] {"+44", "nope"});
        invoke(dto, "setCodes", (Object) new String[] {"a", null});
        java.util.Map<String, String> byLine = new java.util.LinkedHashMap<>();
        byLine.put("bad", "x");
        invoke(dto, "setByLine", byLine);
        java.util.Map<String, String> routes = new java.util.LinkedHashMap<>();
        routes.put("+1", "alsoBad");
        invoke(dto, "setRoutes", routes);

        Object accumulated = invoke(impl, "updateFrom", dto);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> located =
            (Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current);
        Assertions.assertThat(located.isInvalid()).isTrue();
        Assertions.assertThat(located.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("lines", "1"), "not a phone number"),
                new FieldError(List.of("codes", "1"), "must not be null"),
                new FieldError(List.of("byLine", "bad"), "not a phone number"),
                new FieldError(List.of("routes", "+1"), "not a phone number"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName("the sparse tier validates @MapKey placement too")
    void sparseTierValidatesMapKeys() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.StrayKeyPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface StrayKeyPatchMapping extends UpdateSpec<Contact, ContactPatchDto> {
                @MapKey("nosuch")
                default ValidatedPrism<String, String> strayKey() {
                  return ValidatedPrism.of(Validated::validNel, s -> s);
                }
              }
              """);
      Compilation compilation = compile(PHONE, CONTACT, CONTACT_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@MapKey(\"nosuch\") names no component of Contact");
    }

    @Test
    @DisplayName("the sparse tier refuses a key leaf on an Optional<Map>, which it never bridges")
    void sparseTierRefusesAKeyLeafOnAnOptionalMap() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Registry",
              """
              package com.example;

              import java.util.Map;
              import java.util.Optional;

              public record Registry(Optional<Map<EmailAddress, String>> owners) {}
              """);
      JavaFileObject patch =
          JavaFileObjects.forSourceString(
              "com.example.RegistryPatchDto",
              """
              package com.example;

              import java.util.Map;

              public class RegistryPatchDto {
                private Map<String, String> owners;
                public Map<String, String> getOwners() { return owners; }
                public void setOwners(Map<String, String> owners) { this.owners = owners; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RegistryPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MapKey;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface RegistryPatchMapping extends UpdateSpec<Registry, RegistryPatchDto> {
                @MapKey("owners")
                default ValidatedPrism<String, EmailAddress> ownersKey() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, domain, patch, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@MapKey(\"owners\") names a component that is not a Map.");
    }

    @Test
    @DisplayName("an array whose element cannot be named is not offered an element leaf")
    void unnameableArrayPairSuggestsTheWholeComponent() {
      JavaFileObject boxed =
          JavaFileObjects.forSourceString(
              "com.example.Boxed",
              """
              package com.example;

              import java.util.List;

              public record Boxed(List<String>[] rows) {}
              """);
      JavaFileObject boxedPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.BoxedPatchDto",
              """
              package com.example;

              public class BoxedPatchDto {
                private String[] rows;

                public String[] getRows() { return rows; }
                public void setRows(String[] rows) { this.rows = rows; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.BoxedPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface BoxedPatchMapping extends UpdateSpec<Boxed, BoxedPatchDto> {}
              """);
      Compilation compilation = compile(boxed, boxedPatchDto, spec);
      assertThat(compilation).failed();
      // The element form is withheld: no leaf over those elements could ever be carried.
      Assertions.assertThat(compilation.errors())
          .noneMatch(d -> d.getMessage(null).contains("Declare an element leaf"));
    }

    @Test
    @DisplayName("a leafless array pair is offered the element leaf the tier lifts")
    void leaflessArrayPairSuggestsTheElementLeaf() {
      JavaFileObject squad =
          JavaFileObjects.forSourceString(
              "com.example.Squad",
              """
              package com.example;

              public record Squad(PhoneNumber[] lines) {}
              """);
      JavaFileObject squadPatchDto =
          JavaFileObjects.forSourceString(
              "com.example.SquadPatchDto",
              """
              package com.example;

              public class SquadPatchDto {
                private String[] lines;

                public String[] getLines() { return lines; }
                public void setLines(String[] lines) { this.lines = lines; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SquadPatchMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface SquadPatchMapping extends UpdateSpec<Squad, SquadPatchDto> {}
              """);
      Compilation compilation = compile(PHONE, squad, squadPatchDto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "Declare an element leaf 'default ValidatedPrism<java.lang.String,"
                  + " com.example.PhoneNumber> lines()'");
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

    /**
     * The sparse half of the rule {@code MappingTierMatrixTest} pins for the dense tiers: every
     * identity container carries the scan, raw and wildcard alike. The scan helper returns its
     * argument's own type, so a raw argument needs no unchecked conversion, and the parser's pinned
     * result type meets a wildcard argument without capturing it.
     */
    @Test
    @DisplayName(
        "raw and wildcard identity containers are scanned on the sparse tier too, and the generated"
            + " code compiles")
    void rawAndWildcardIdentityContainersAreScanned() {
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
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(domain, dto, spec);
      assertThat(compilation).succeededWithoutWarnings();
      String generated = generatedSource(compilation, "com.example.WildBagPatchMappingImpl");
      Assertions.assertThat(generated)
          .contains("wire.getWilds(), WildBagPatchMappingImpl::hkj$allPresent")
          .contains("wire.getAttrs(), WildBagPatchMappingImpl::hkj$valuesPresent")
          .contains("wire.getRawTags(), WildBagPatchMappingImpl::hkj$allPresent")
          .contains("wire.getRawLabels(), WildBagPatchMappingImpl::hkj$valuesPresent")
          .doesNotContain("Edit.setIfPresent(");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      try {
        Object impl = result.instance("com.example.WildBagPatchMappingImpl");
        Object current =
            construct(result, "com.example.WildBag", List.of(), Map.of(), List.of(), Map.of());
        Object patch =
            result.loadClass("com.example.WildBagPatchDto").getDeclaredConstructor().newInstance();
        Map<String, Integer> attrs = new HashMap<>();
        attrs.put("k", null);
        Map<String, String> rawLabels = new HashMap<>();
        rawLabels.put("k", null);
        invoke(patch, "setWilds", Arrays.asList("a", null));
        invoke(patch, "setAttrs", attrs);
        invoke(patch, "setRawTags", Arrays.asList(null, "b"));
        invoke(patch, "setRawLabels", rawLabels);
        @SuppressWarnings("unchecked")
        Validated<NonEmptyList<FieldError>, Object> located =
            (Validated<NonEmptyList<FieldError>, Object>)
                invoke(invoke(impl, "updateFrom", patch), "apply", current);
        Assertions.assertThat(located.getError().toJavaList())
            .containsExactly(
                new FieldError(List.of("wilds", "1"), "must not be null"),
                new FieldError(List.of("attrs", "k"), "must not be null"),
                new FieldError(List.of("rawTags", "0"), "must not be null"),
                new FieldError(List.of("rawLabels", "k"), "must not be null"));
      } catch (ReflectiveOperationException e) {
        throw new AssertionError(e);
      }
    }

    @Test
    @DisplayName(
        "an Optional PATCH property over a raw type, and an element leaf over one, compile under"
            + " -Xlint:rawtypes -Werror")
    void rawTypesInASparseUpdateCompileUnderWerror() {
      // The setter lambda takes the component's own type and the element parser the property's,
      // so a raw type nested in either is reported on a lambda parameter javac infers.
      JavaFileObject label =
          JavaFileObjects.forSourceString(
              "com.example.Label",
              """
              package com.example;

              public record Label(String value) {}
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.RawPatched",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public record RawPatched(String id, Optional<List> contacts, Optional<Label> label) {}
              """);
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.RawPatchedDto",
              """
              package com.example;

              import java.util.List;
              import java.util.Optional;

              @SuppressWarnings("rawtypes")
              public class RawPatchedDto {
                private Optional<List> contacts = null;
                private Optional<List<List>> label = null;

                public Optional<List> getContacts() { return contacts; }
                public void setContacts(Optional<List> contacts) { this.contacts = contacts; }
                public Optional<List<List>> getLabel() { return label; }
                public void setLabel(Optional<List<List>> label) { this.label = label; }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.RawPatchedMapping",
              """
              package com.example;

              import java.util.List;
              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              @SuppressWarnings("rawtypes")
              public interface RawPatchedMapping extends UpdateSpec<RawPatched, RawPatchedDto> {
                default ValidatedPrism<List<List>, Label> label() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new Label(String.valueOf(raw))),
                      label -> List.of(List.of(label.value())));
                }
              }
              """);
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
              .compile(label, domain, dto, spec);
      assertThat(compilation).succeededWithoutWarnings();
      // The raw List inside the identity Optional is scanned; the Optional of a raw List is
      // what the scan's inferred lambda parameter holds.
      Assertions.assertThat(generatedSource(compilation, "com.example.RawPatchedMappingImpl"))
          .contains("wire.getContacts(), e -> hkj$presentWithin(e, e2 -> hkj$allPresent(e2))")
          .contains("parseIfPresent(");
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
      // writeUpdateImpl's parser switch, buildCall and parseCall each route the kinds they do not
      // list through a default arm, to a leaf's or a lifted container's call, and bridgeParseLeg
      // sends every kind but IDENTITY through parseCall, so a new Kind would silently take one. A
      // new constant fails this pin: give it an explicit arm in each (the dense buildValue and
      // parseLeg switches are compiler-enforced already) before extending this list.
      Assertions.assertThat(Arrays.stream(MappingProcessor.Kind.values()).map(Enum::name))
          .containsExactlyInAnyOrder(
              "IDENTITY",
              "LEAF",
              "ELEMENTS",
              "ARRAY",
              "OPTIONAL",
              "OPTIONAL_BRIDGE",
              "MAP",
              "MAP_KEYS",
              "MAP_ENTRIES",
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
          .contains("Setter.fromGetSet(Components::owner, (c, v) -> new Components(v))")
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

    /** The domain a PATCH body edits: a name and a list the client may or may not send. */
    private static final JavaFileObject TAGGED =
        JavaFileObjects.forSourceString(
            "com.example.Tagged",
            """
            package com.example;

            import java.util.List;

            public record Tagged(String name, List<String> tags) {}
            """);

    private static final JavaFileObject TAGGED_PATCH =
        JavaFileObjects.forSourceString(
            "com.example.TaggedPatch",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.UpdateSpec;

            @GenerateMapping
            public interface TaggedPatch extends UpdateSpec<Tagged, TaggedPatchDto> {}
            """);

    private static final JavaFileObject ROLE =
        JavaFileObjects.forSourceString(
            "com.example.Role",
            """
            package com.example;

            public record Role(String name) {}
            """);

    private static final JavaFileObject ROLE_DTO =
        JavaFileObjects.forSourceString(
            "com.example.RoleDto",
            """
            package com.example;

            public record RoleDto(String name) {}
            """);

    private static final JavaFileObject ROLE_PATCH_DTO =
        JavaFileObjects.forSourceString(
            "com.example.RolePatchDto",
            """
            package com.example;

            public class RolePatchDto {
              private String name;
              public String getName() { return name; }
              public void setName(String name) { this.name = name; }
            }
            """);

    /** Both clauses at once: a full mapping to the record wire, a sparse patch to the bean. */
    private static final JavaFileObject BOTH_TIERS_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.RoleMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.annotations.UpdateSpec;

            @GenerateMapping
            public interface RoleMapping
                extends MappingSpec<Role, RoleDto>, UpdateSpec<Role, RolePatchDto> {}
            """);

    @Test
    @DisplayName(
        "a getter-only List on a PATCH bean is rejected: its getter creates the list, so an"
            + " omitted field could not read as absent")
    void getterOnlyListCannotCarryAbsence() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.TaggedPatchDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;

              public class TaggedPatchDto {
                private String name;
                private List<String> tags;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public List<String> getTags() {
                  if (tags == null) { tags = new ArrayList<>(); }
                  return tags;
                }
              }
              """);

      Compilation compilation = compile(TAGGED, dto, TAGGED_PATCH);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "is a getter-only List<String>, which cannot carry a sparse update's absence");
      assertThat(compilation)
          .hadErrorContaining("a request that omits 'tags' would read as a present empty list");
      // A setter alone is not the remedy: an initialiser or a creating getter defeats it too.
      assertThat(compilation)
          .hadErrorContaining(
              "Give 'tags' a setTags setter, and let getTags() answer null until it is set, with no"
                  + " initialiser on the field and no list created on first call, so an omitted"
                  + " field reads as absent; a generated class whose getter cannot change needs a"
                  + " hand-written PATCH bean instead.");
      // The element type is not what is wrong here, so the dense tier's remedy must not appear.
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("Declare the type arguments"));
    }

    @Test
    @DisplayName(
        "a setter on the same property keeps the absent signal: an omitted field leaves the"
            + " domain list untouched, and a sent one replaces it")
    void aSetterCarriesAbsence() throws ReflectiveOperationException {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.TaggedPatchDto",
              """
              package com.example;

              import java.util.List;

              public class TaggedPatchDto {
                private String name;
                private List<String> tags;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public List<String> getTags() { return tags; }
                public void setTags(List<String> tags) { this.tags = tags; }
              }
              """);

      Compilation compilation = compile(TAGGED, dto, TAGGED_PATCH);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object impl = result.instance("com.example.TaggedPatchImpl");
      // The canonical constructor by declared types: List.of(...) is not List.class at runtime.
      var tagged =
          result.loadClass("com.example.Tagged").getDeclaredConstructor(String.class, List.class);
      Object current = tagged.newInstance("old", List.of("keep", "these"));

      Object omitted = result.newInstance("com.example.TaggedPatchDto");
      invoke(omitted, "setName", "new");
      Assertions.assertThat(apply(impl, omitted, current))
          .as("an omitted field leaves the domain list alone")
          .isEqualTo(tagged.newInstance("new", List.of("keep", "these")));

      Object sent = result.newInstance("com.example.TaggedPatchDto");
      invoke(sent, "setTags", List.of("fresh"));
      Assertions.assertThat(apply(impl, sent, current))
          .as("a sent field replaces it")
          .isEqualTo(tagged.newInstance("old", List.of("fresh")));
    }

    /** {@code updateFrom(wire).apply(current)}, the sparse write-back in one step. */
    @SuppressWarnings("unchecked")
    private static Object apply(Object impl, Object wire, Object current) {
      Object accumulated = invoke(impl, "updateFrom", wire);
      return ((Validated<NonEmptyList<FieldError>, Object>) invoke(accumulated, "apply", current))
          .get();
    }

    @Test
    @DisplayName(
        "the dense tier keeps the same getter-only List: it writes every component, so absence"
            + " has nothing to mean there")
    void theDenseTierKeepsTheGetterOnlyList() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.TaggedDto",
              """
              package com.example;

              import java.util.ArrayList;
              import java.util.List;

              public class TaggedDto {
                private String name;
                private List<String> tags;
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public List<String> getTags() {
                  if (tags == null) { tags = new ArrayList<>(); }
                  return tags;
                }
              }
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.TaggedMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface TaggedMapping extends MappingSpec<Tagged, TaggedDto> {}
              """);

      Compilation compilation = compile(TAGGED, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.TaggedMappingImpl"))
          .contains("wire.getTags().addAll(domain.tags());");
    }

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
    @DisplayName("a spec extending both MappingSpec and UpdateSpec is rejected on its own")
    void bothTiersRejected() {
      Compilation compilation = compile(ROLE, ROLE_DTO, ROLE_PATCH_DTO, BOTH_TIERS_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'RoleMapping' extends both 'MappingSpec<Role, RoleDto>' and 'UpdateSpec<Role,"
                  + " RolePatchDto>'");
      // The refusal is the whole story: neither tier runs, so nothing else follows from it.
      assertThat(compilation).hadErrorCount(1);
    }

    @Test
    @DisplayName("a spec extending both tiers is never offered to a parent that nests its pair")
    void bothTiersNotNestable() {
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.example.Account",
              """
              package com.example;

              public record Account(Role role) {}
              """);
      JavaFileObject accountDto =
          JavaFileObjects.forSourceString(
              "com.example.AccountDto",
              """
              package com.example;

              public record AccountDto(RoleDto role) {}
              """);
      JavaFileObject accountMapping =
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
              ROLE,
              ROLE_DTO,
              ROLE_PATCH_DTO,
              BOTH_TIERS_MAPPING,
              account,
              accountDto,
              accountMapping);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("'RoleMapping' extends both");
      // The parent resolves nothing for the pair and says so at its own field, naming the spec
      // that maps it and why it cannot serve, rather than generating a call to a parse the
      // refused spec never emitted.
      assertThat(compilation).hadErrorContaining("target field 'AccountDto.role' has no usable");
      assertThat(compilation)
          .hadErrorContaining("'RoleMapping' maps this pair but extends both MappingSpec and");
      assertThat(compilation).hadErrorCount(2);
      // Every error is the processor's own. This pins the shape of the refusal rather than the
      // absence of the javac errors #837 was about: once a processor reports an error javac
      // never compiles the generated sources, so in one compilation those are unobservable.
      // The classpath route, where no refusal fires, is what pins them - see
      // MappingProcessorClasspathTest.
      Assertions.assertThat(compilation.diagnostics())
          .filteredOn(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
          .allSatisfy(
              diagnostic ->
                  Assertions.assertThat(diagnostic.getMessage(null))
                      .startsWith("@GenerateMapping:"));
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
      // A leaf can never target a primitive component, and a PATCH property can never be one, so
      // the fix offers the property's wrapper, which writes straight in, or a wrapper component
      // with a leaf into it.
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'count' on 'TextCountDto' as java.lang.Integer, which a sparse update"
                  + " writes straight into the int component, or declare 'Counter2.count' as"
                  + " java.lang.Integer and add a leaf 'default ValidatedPrism<java.lang.String,"
                  + " java.lang.Integer> count()'.");
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
      // The fix offers the property shape that expresses 'set to empty' without changing the wire
      // contract, and steers off the Optional.empty() field default, which would lose absence.
      assertThat(compilation)
          .hadErrorContaining(
              "Declare 'nickname' as Optional<java.lang.String> with the field defaulting to null,"
                  + " not Optional.empty()");
    }

    @Test
    @DisplayName("the Optional remedy keeps the wire property's own type, so an element leaf lifts")
    void optionalBridgeRemedyKeepsTheWireType() {
      JavaFileObject dto =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberPatchDto",
              """
              package com.example;

              public class SubscriberPatchDto {
                private String email;
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
              }
              """);
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.Subscriber",
              """
              package com.example;

              import java.util.Optional;

              public record Subscriber(Optional<EmailAddress> email) {}
              """);
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.SubscriberPatchMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface SubscriberPatchMapping
                  extends UpdateSpec<Subscriber, SubscriberPatchDto> {
                default ValidatedPrism<String, EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
                }
              }
              """);
      Compilation compilation = compile(EMAIL, domain, dto, spec);
      assertThat(compilation).failed();
      // Naming the domain element type would steer the author to Optional<EmailAddress>, which
      // matches by identity and bypasses this leaf; the wire's own type keeps the leaf lifting.
      assertThat(compilation).hadErrorContaining("Declare 'email' as Optional<java.lang.String>");
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

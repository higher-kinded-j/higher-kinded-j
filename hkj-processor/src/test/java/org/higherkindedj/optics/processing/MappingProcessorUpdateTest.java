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

@DisplayName("MappingProcessor - sparse PATCH write-back via UpdateSpec (#645)")
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

    private Object impl(RuntimeCompilationHelper.CompiledResult result)
        throws ReflectiveOperationException {
      return result.loadClass("com.example.UserPatchMappingImpl").getField("INSTANCE").get(null);
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
        Object impl =
            result.loadClass("com.example.AccountPatchMappingImpl").getField("INSTANCE").get(null);
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
        Object impl =
            result.loadClass("com.example.CustomerPatchMappingImpl").getField("INSTANCE").get(null);
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
        Object impl =
            result.loadClass("com.example.CustomerPatchMappingImpl").getField("INSTANCE").get(null);
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
    @DisplayName("a spec extending another interface besides UpdateSpec is rejected")
    void extraSuperinterface() {
      JavaFileObject spec =
          JavaFileObjects.forSourceString(
              "com.example.ExtraMapping",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              interface Marker {}

              @GenerateMapping
              public interface ExtraMapping extends UpdateSpec<User, UserPatchDto>, Marker {}
              """);
      Compilation compilation = compile(EMAIL, USER, USER_PATCH_DTO, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("extends interfaces besides UpdateSpec");
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

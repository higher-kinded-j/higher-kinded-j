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
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Accessors a two-way bean leaves unpaired. A bean maps the properties it both reads and writes, so
 * a getter nothing writes, or a writer nothing reads, is left out of the mapping. That is refused
 * where it would lose a value: an accessor named after a domain component the bean carries under no
 * name, on either tier, and on the sparse tier any setter the update cannot read. An unpaired
 * accessor that names nothing the mapping needs still maps as before.
 *
 * <p>Everything compiles under {@code -Xlint:unchecked,rawtypes -Werror}.
 */
@DisplayName("MappingProcessor - unpaired bean accessors")
class MappingProcessorUnpairedAccessorTest {

  private static final String PKG = "com.example.unpaired";

  private static final String IMPORTS =
      """
      import java.util.ArrayList;
      import java.util.List;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.MapField;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.Unmapped;
      import org.higherkindedj.optics.annotations.UpdateSpec;

      """;

  private static final JavaFileObject CONTACT =
      source("Contact", "public record Contact(String name, String email, String phone) {}");

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  private static Compilation compile(JavaFileObject... sources) {
    return javac()
        .withProcessors(new MappingProcessor())
        .withOptions("-Xlint:unchecked,rawtypes", "-Werror")
        .compile(sources);
  }

  private static String generatedSource(Compilation compilation, String simpleName) {
    Optional<JavaFileObject> file = compilation.generatedSourceFile(PKG + "." + simpleName);
    Assertions.assertThat(file).as("generated %s", simpleName).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static List<String> errors(Compilation compilation) {
    return compilation.errors().stream().map(error -> error.getMessage(Locale.ROOT)).toList();
  }

  /** Instantiates a compiled class through its first declared constructor: a record's canonical. */
  private static Object create(
      RuntimeCompilationHelper.CompiledResult result, String simpleName, Object... args)
      throws ReflectiveOperationException {
    return result.loadClass(PKG + "." + simpleName).getDeclaredConstructors()[0].newInstance(args);
  }

  @SuppressWarnings("unchecked") // reflective call into the generated Impl
  private static Validated<NonEmptyList<FieldError>, Object> update(
      Object mapping, Object wire, Object current) {
    return (Validated<NonEmptyList<FieldError>, Object>)
        invoke(invoke(mapping, "updateFrom", wire), "apply", current);
  }

  @Nested
  @DisplayName("Sparse tier: a PATCH bean")
  class Sparse {

    @Test
    @DisplayName(
        "a setter beside a misspelt getter, and a getter with no setter, are each refused by name")
    void misspeltGetterAndGetterOnlyAreRefused() {
      JavaFileObject patch =
          source(
              "ContactPatch",
              """
              public class ContactPatch {
                private String name;
                private String email;
                private String phone;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmial() { return email; }
                public void setEmail(String email) { this.email = email; }
                public String getPhone() { return phone; }
              }
              """);
      JavaFileObject spec =
          source(
              "ContactPatchMapping",
              """
              @GenerateMapping
              public interface ContactPatchMapping extends UpdateSpec<Contact, ContactPatch> {}
              """);
      Compilation compilation = compile(CONTACT, patch, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(2);
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'email' on 'ContactPatch' has a setter, setEmail(String), but no"
                  + " getter, so the mapping leaves it out. updateFrom folds in only the properties"
                  + " a PATCH bean both reads and writes, so a value the client sends for 'email'"
                  + " would never reach 'Contact.email', and nothing would say so. Add String"
                  + " getEmail() to 'ContactPatch'. Or, if getEmial() is meant to read 'email',"
                  + " rename it getEmail().");
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'phone' on 'ContactPatch' has a getter, getPhone(), but no setter, so"
                  + " the mapping leaves it out.");
      assertThat(compilation)
          .hadErrorContaining(
              "would never reach 'Contact.phone', and nothing would say so. Add setPhone(String)"
                  + " to 'ContactPatch'. Or, if clients must not change 'Contact.phone',"
                  + " declare '@Unmapped String phone();' on the spec.");
    }

    @Test
    @DisplayName("a misspelt setter is refused once, on the getter it fails to pair")
    void misspeltSetterIsRefusedOnce() {
      JavaFileObject patch =
          source(
              "ContactPatch",
              """
              public class ContactPatch {
                private String email;

                public String getEmail() { return email; }
                public void setEmial(String email) { this.email = email; }
                public String getPhone() { return null; }
                public void setPhone(String phone) {}
              }
              """);
      JavaFileObject spec =
          source(
              "ContactPatchMapping",
              """
              @GenerateMapping
              public interface ContactPatchMapping extends UpdateSpec<Contact, ContactPatch> {}
              """);
      Compilation compilation = compile(CONTACT, patch, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'email' on 'ContactPatch' has a getter, getEmail(), but no setter");
      assertThat(compilation)
          .hadErrorContaining(
              "Or, if setEmial(String) is meant to write 'email', rename it setEmail(String).");
    }

    @Test
    @DisplayName(
        "an unread setter is refused whatever it names, and a primitive is offered its wrapper")
    void unreadSettersAndPrimitivesAreRefused() {
      JavaFileObject patch =
          source(
              "ContactPatch",
              """
              public class ContactPatch {
                public String getEmail() { return null; }
                public void setEmail(String email) {}
                public void setNickname(String nickname) {}
              }
              """);
      JavaFileObject patchSpec =
          source(
              "ContactPatchMapping",
              """
              @GenerateMapping
              public interface ContactPatchMapping extends UpdateSpec<Contact, ContactPatch> {}
              """);
      // A legacy alias beside the property a rename carries 'email' through.
      JavaFileObject alias =
          source(
              "AliasPatch",
              """
              public class AliasPatch {
                public String getMail() { return null; }
                public void setMail(String mail) {}
                public void setEmail(String email) {}
              }
              """);
      JavaFileObject aliasSpec =
          source(
              "AliasPatchMapping",
              """
              @GenerateMapping
              public interface AliasPatchMapping extends UpdateSpec<Contact, AliasPatch> {
                @MapField(to = "mail")
                String email();
              }
              """);
      JavaFileObject flag = source("Flag", "public record Flag(String name, boolean active) {}");
      JavaFileObject flagPatch =
          source(
              "FlagPatch",
              """
              public class FlagPatch {
                public String getName() { return null; }
                public void setName(String name) {}
                public boolean isActive() { return false; }
              }
              """);
      JavaFileObject flagSpec =
          source(
              "FlagPatchMapping",
              """
              @GenerateMapping
              public interface FlagPatchMapping extends UpdateSpec<Flag, FlagPatch> {}
              """);
      Compilation compilation =
          compile(CONTACT, patch, patchSpec, alias, aliasSpec, flag, flagPatch, flagSpec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(3);
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'nickname' on 'ContactPatch' has a setter, setNickname(String), but"
                  + " no getter, so the mapping leaves it out. updateFrom folds in only the"
                  + " properties a PATCH bean both reads and writes, and a setter is how a client's"
                  + " value arrives, so a value the client sends for 'nickname' would be ignored"
                  + " without a word, and 'Contact' has no component named 'nickname' for a getter"
                  + " alone to map it to. Remove setNickname(String) from 'ContactPatch', or"
                  + " rename it after the component it should update and give it a getter. Or"
                  + " declare '@Unmapped String nickname();' on the spec, to leave it out"
                  + " deliberately.");
      assertThat(compilation)
          .hadErrorContaining(
              "would be ignored without a word: 'Contact.email' is renamed by @MapField, and the"
                  + " update reads it under that name. Remove setEmail(String) from 'AliasPatch'."
                  + " Or declare '@Unmapped String email();' on the spec, to leave it out"
                  + " deliberately.");
      assertThat(compilation)
          .hadErrorContaining(
              "Add setActive(Boolean) to 'FlagPatch', and declare isActive() with Boolean too,"
                  + " since a PATCH property must be able to be absent. Or, if clients must not"
                  + " change 'Flag.active', declare '@Unmapped boolean active();' on the spec.");
    }

    @Test
    @DisplayName("computed getters, setup() and a builder's adder that name nothing still map")
    void extrasNoComponentNamesStillMap() throws ReflectiveOperationException {
      JavaFileObject patch =
          source(
              "ContactPatch",
              """
              public class ContactPatch {
                private String email;

                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public boolean isEmpty() { return email == null; }
                public Boolean isValid() { return true; }
                public String getSummary() { return "patch of " + email; }
                public void setup(String locale) {}
              }
              """);
      JavaFileObject spec =
          source(
              "ContactPatchMapping",
              """
              @GenerateMapping
              public interface ContactPatchMapping extends UpdateSpec<Contact, ContactPatch> {}
              """);
      JavaFileObject order =
          source("Order", "public record Order(String id, List<String> tags) {}");
      // A @Singular-style builder: tag(String) adds one element and no getter reads 'tag'.
      JavaFileObject orderPatch =
          source(
              "OrderPatch",
              """
              public final class OrderPatch {
                private final String id;
                private final List<String> tags;

                private OrderPatch(String id, List<String> tags) {
                  this.id = id;
                  this.tags = tags;
                }

                public String getId() { return id; }
                public List<String> getTags() { return tags; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String id;
                  private List<String> tags;

                  public Builder id(String id) { this.id = id; return this; }
                  public Builder tags(List<String> tags) { this.tags = tags; return this; }
                  public Builder tag(String tag) {
                    if (tags == null) {
                      tags = new ArrayList<>();
                    }
                    tags.add(tag);
                    return this;
                  }
                  public OrderPatch build() { return new OrderPatch(id, tags); }
                }
              }
              """);
      JavaFileObject orderSpec =
          source(
              "OrderPatchMapping",
              """
              @GenerateMapping
              public interface OrderPatchMapping extends UpdateSpec<Order, OrderPatch> {}
              """);
      Compilation compilation = compile(CONTACT, patch, spec, order, orderPatch, orderSpec);
      assertThat(compilation).succeeded();

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object contactPatch = result.loadClass(PKG + ".ContactPatch").getConstructor().newInstance();
      invoke(contactPatch, "setEmail", "new@corp");
      assertThatValidated(
              update(
                  result.instance(PKG + ".ContactPatchMappingImpl"),
                  contactPatch,
                  create(result, "Contact", "ada", "old@corp", "+44")))
          .hasValue(create(result, "Contact", "ada", "new@corp", "+44"));

      Object builder =
          invoke(
              result.loadClass(PKG + ".OrderPatch").getMethod("builder").invoke(null), "tag", "x");
      assertThatValidated(
              update(
                  result.instance(PKG + ".OrderPatchMappingImpl"),
                  invoke(builder, "build"),
                  create(result, "Order", "o-1", List.of())))
          .hasValue(create(result, "Order", "o-1", List.of("x")));
    }
  }

  @Nested
  @DisplayName("Dense tier: a two-way bean")
  class Dense {

    @Test
    @DisplayName(
        "an inherited setter named after a component is refused, naming where it is declared")
    void inheritedSetterIsRefused() {
      JavaFileObject login =
          source("Login", "public record Login(String user, String password) {}");
      JavaFileObject credentials =
          source(
              "Credentials",
              """
              public class Credentials {
                public void setPassword(String password) {}
              }
              """);
      JavaFileObject bean =
          source(
              "LoginBean",
              """
              public class LoginBean extends Credentials {
                public String getUser() { return null; }
                public void setUser(String user) {}
                public String getDisplayName() { return "user"; }
              }
              """);
      JavaFileObject spec =
          source(
              "LoginMapping",
              """
              @GenerateMapping
              public interface LoginMapping extends MappingSpec<Login, LoginBean> {}
              """);
      Compilation compilation = compile(login, credentials, bean, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'password' on 'LoginBean' has a setter, setPassword(String) (declared"
                  + " on 'Credentials'), but no getter, so the mapping leaves it out. A mapping"
                  + " carries only the properties a bean both reads and writes, so 'Login.password'"
                  + " would go unmapped without a word: build would never write it. Add String"
                  + " getPassword() to 'LoginBean'. Or, if setPassword(String) (declared on"
                  + " 'Credentials') is not meant to carry 'Login.password', declare '@Unmapped"
                  + " String password();' on the spec.");
      // getDisplayName() is unpaired too, but far from 'password', so it is no misspelling of it.
      Assertions.assertThat(errors(compilation))
          .noneMatch(error -> error.contains("is meant to read"));
    }

    @Test
    @DisplayName(
        "an accessor named after a local rename's target is refused, and one named after the"
            + " component is its likely misspelling")
    void renamedAccessorIsRefused() {
      JavaFileObject dto =
          source(
              "ContactDto",
              """
              public class ContactDto {
                public String getName() { return null; }
                public void setName(String name) {}
                public String getPhone() { return null; }
                public void setPhone(String phone) {}
                public void setMail(String mail) {}
                public String getEmail() { return null; }
              }
              """);
      JavaFileObject spec =
          source(
              "ContactMapping",
              """
              @GenerateMapping
              public interface ContactMapping extends MappingSpec<Contact, ContactDto> {
                @MapField(to = "mail")
                String email();
              }
              """);
      Compilation compilation = compile(CONTACT, dto, spec);
      assertThat(compilation).failed();
      // A rename declared on the spec maps 'email' only as 'mail', so getEmail() is not refused.
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "so 'Contact.email' (renamed to 'mail') would go unmapped without a word: build would"
                  + " never write it. Add String getMail() to 'ContactDto'. Or, if getEmail() is"
                  + " meant to read 'mail', rename it getMail().");
    }

    @Test
    @DisplayName("a getter with no builder setter is refused, naming the builder method it nears")
    void builderGetterIsRefused() {
      JavaFileObject dto =
          source(
              "ContactDto",
              """
              public final class ContactDto {
                private final String name;
                private final String email;
                private final String phone;

                private ContactDto(String name, String email, String phone) {
                  this.name = name;
                  this.email = email;
                  this.phone = phone;
                }

                public String getName() { return name; }
                public String getEmail() { return email; }
                public String getPhone() { return phone; }
                public static Builder builder() { return new Builder(); }

                public static final class Builder {
                  private String name;
                  private String email;
                  private String phone;

                  public Builder name(String name) { this.name = name; return this; }
                  public Builder email(String email) { this.email = email; return this; }
                  public Builder phoen(String phone) { this.phone = phone; return this; }
                  public ContactDto build() { return new ContactDto(name, email, phone); }
                }
              }
              """);
      JavaFileObject spec =
          source(
              "ContactMapping",
              """
              @GenerateMapping
              public interface ContactMapping extends MappingSpec<Contact, ContactDto> {}
              """);
      Compilation compilation = compile(CONTACT, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'phone' on 'ContactDto' has a getter, getPhone(), but no builder"
                  + " setter, so the mapping leaves it out.");
      assertThat(compilation)
          .hadErrorContaining(
              "Add a setter for 'phone' taking String to the builder of 'ContactDto'. Or, if"
                  + " phoen(String) is meant to write 'phone', rename it phone(String).");
    }

    @Test
    @DisplayName(
        "a misspelling is offered once, at the same type, and never one a component needs itself")
    void misspellingsAreOfferedSparingly() {
      JavaFileObject thing =
          source(
              "Thing", "public record Thing(String id, String code, String mode, String mood) {}");
      JavaFileObject dto =
          source(
              "ThingDto",
              """
              public class ThingDto {
                public String getId() { return null; }
                public void setId(String id) {}
                public String getCode() { return null; }
                public void setCodes(List<String> codes) {}
                public void setMode(String mode) {}
                public void setMood(String mood) {}
                public String getMod() { return null; }
              }
              """);
      JavaFileObject spec =
          source(
              "ThingMapping",
              """
              @GenerateMapping
              public interface ThingMapping extends MappingSpec<Thing, ThingDto> {}
              """);
      Compilation compilation = compile(thing, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(3);
      // setCodes takes another type, and setMode is refused on its own, so getCode() has no hint.
      assertThat(compilation)
          .hadErrorContaining(
              "Add setCode(String) to 'ThingDto'. Or, if getCode() is not meant to carry"
                  + " 'Thing.code', declare '@Unmapped String code();' on the spec.");
      // getMod() nears both 'mode' and 'mood', and is offered to only one of them.
      Assertions.assertThat(errors(compilation))
          .filteredOn(error -> error.contains("if getMod() is meant to read"))
          .hasSize(1);
    }

    @Test
    @DisplayName("accessors no component names still map, on the full tier")
    void extrasNoComponentNamesStillMap() throws ReflectiveOperationException {
      JavaFileObject dto =
          source(
              "ContactDto",
              """
              public class ContactDto {
                private String name;
                private String email;
                private String phone;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public String getPhone() { return phone; }
                public void setPhone(String phone) { this.phone = phone; }
                public String getNameBytes() { return name; }
                public void setLegacyHandle(String handle) { this.name = handle; }
              }
              """);
      JavaFileObject spec =
          source(
              "ContactMapping",
              """
              @GenerateMapping
              public interface ContactMapping extends MappingSpec<Contact, ContactDto> {}
              """);
      Compilation compilation = compile(CONTACT, dto, spec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "ContactMappingImpl"))
          .contains("asValidatedPrism()")
          .doesNotContain("NameBytes")
          .doesNotContain("LegacyHandle");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object mapping = result.instance(PKG + ".ContactMappingImpl");
      Object contact = create(result, "Contact", "ada", "ada@corp", "+44");
      @SuppressWarnings("unchecked") // reflective call into the generated Impl
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>)
              invoke(mapping, "parse", invoke(mapping, "build", contact));
      assertThatValidated(parsed).hasValue(contact);
    }
  }

  @Nested
  @DisplayName("The @Unmapped marker")
  class UnmappedMarker {

    @Test
    @DisplayName("reads an accessor's omission as deliberate, on both tiers")
    void silencesRefusalsOnBothTiers() throws ReflectiveOperationException {
      JavaFileObject keyed =
          source("Keyed", "public record Keyed(String id, String name, String email) {}");
      // A response DTO reused as the PATCH body: the client must not set the server-assigned id.
      JavaFileObject patch =
          source(
              "KeyedPatch",
              """
              public class KeyedPatch {
                private String name;
                private String email;

                // An id the domain does not hold, so applying it could not go unnoticed.
                public String getId() { return "k-9"; }
                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public void setNickname(String nickname) {}
              }
              """);
      JavaFileObject patchSpec =
          source(
              "KeyedPatchMapping",
              """
              @GenerateMapping
              public interface KeyedPatchMapping extends UpdateSpec<Keyed, KeyedPatch> {
                @Unmapped
                String id();

                @Unmapped
                String nickname();
              }
              """);
      JavaFileObject view =
          source(
              "KeyedView",
              """
              public class KeyedView {
                private String name;
                private String email;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public String getId() { return "k-9"; }
              }
              """);
      JavaFileObject viewSpec =
          source(
              "KeyedViewMapping",
              """
              @GenerateMapping
              public interface KeyedViewMapping extends MappingSpec<Keyed, KeyedView> {
                @Unmapped
                String id();
              }
              """);
      Compilation compilation = compile(keyed, patch, patchSpec, view, viewSpec);
      assertThat(compilation).succeeded();
      // The Impl owes the marker a member, and stubs it out as it does a rename.
      Assertions.assertThat(generatedSource(compilation, "KeyedViewMappingImpl"))
          .contains("public String id()")
          .contains("Unmapped declaration only; not invocable.");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object sent = result.loadClass(PKG + ".KeyedPatch").getConstructor().newInstance();
      invoke(sent, "setEmail", "new@corp");
      assertThatValidated(
              update(
                  result.instance(PKG + ".KeyedPatchMappingImpl"),
                  sent,
                  create(result, "Keyed", "k-1", "ada", "old@corp")))
          .hasValue(create(result, "Keyed", "k-1", "ada", "new@corp"));
    }

    @Test
    @DisplayName("declared on the spec, it must be a marker naming an accessor left out")
    void localMarkerMustBindAndBeAMarker() {
      JavaFileObject dto =
          source(
              "ContactDto",
              """
              public class ContactDto {
                private String name;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return null; }
              }
              """);
      JavaFileObject carried =
          source(
              "CarriedMapping",
              """
              @GenerateMapping
              public interface CarriedMapping extends MappingSpec<Contact, ContactDto> {
                @Unmapped
                String name();

                @Unmapped
                String email();

                @Unmapped
                String phone();
              }
              """);
      JavaFileObject stray =
          source(
              "StrayPatchMapping",
              """
              @GenerateMapping
              public interface StrayPatchMapping extends UpdateSpec<Contact, ContactDto> {
                @Unmapped
                String emial();
              }
              """);
      JavaFileObject bodied =
          source(
              "BodiedMapping",
              """
              @GenerateMapping
              public interface BodiedMapping extends MappingSpec<Contact, ContactDto> {
                @Unmapped
                default String email() { return ""; }
              }
              """);
      JavaFileObject parameterised =
          source(
              "ParameterisedMapping",
              """
              @GenerateMapping
              public interface ParameterisedMapping extends MappingSpec<Contact, ContactDto> {
                @Unmapped
                String email(String locale);
              }
              """);
      Compilation compilation = compile(CONTACT, dto, carried, stray, bodied, parameterised);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Unmapped method 'name' names a property 'ContactDto' maps. The marker reads an"
                  + " accessor with no partner as deliberate, and 'name' is read and written, so"
                  + " the mapping carries it like any other property. Remove the marker; to leave"
                  + " the property out of the mapping, remove one of its accessors from"
                  + " 'ContactDto'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Unmapped method 'emial' names no accessor 'ContactDto' leaves out. The marker reads"
                  + " an accessor with no partner as deliberate. Left unpaired on 'ContactDto':"
                  + " [email]. Name the marker after the accessor's property, or remove it. Did you"
                  + " mean 'email()'?");
      assertThat(compilation).hadErrorContaining("@Unmapped method 'email' must be abstract.");
      assertThat(compilation)
          .hadErrorContaining("@Unmapped method 'email' must not declare parameters.");
    }

    @Test
    @DisplayName("inherited from a mix-in, it binds where it can and is otherwise inert")
    void inheritedMarkerIsInert() {
      JavaFileObject vocabulary =
          source(
              "PatchVocabulary",
              """
              public interface PatchVocabulary {
                @Unmapped
                String id();
              }
              """);
      JavaFileObject patch =
          source(
              "NamePatch",
              """
              public class NamePatch {
                private String name;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject spec =
          source(
              "NamePatchMapping",
              """
              @GenerateMapping
              public interface NamePatchMapping
                  extends UpdateSpec<Contact, NamePatch>, PatchVocabulary {}
              """);
      Compilation compilation = compile(CONTACT, vocabulary, patch, spec);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("a marker naming a type the spec's package cannot reach is refused")
    void markerNamesUnreachableType() {
      JavaFileObject nested =
          JavaFileObjects.forSourceString(
              PKG + ".Enclosing",
              "package "
                  + PKG
                  + ";\n\n"
                  + IMPORTS
                  + """
                  public final class Enclosing {
                    private record Hidden(String value) {}

                    public record Holder(String name, String phone) {}

                    public static class HolderDto {
                      private String name;

                      public String getName() { return name; }
                      public void setName(String name) { this.name = name; }
                      public String getPhone() { return null; }
                    }

                    @GenerateMapping
                    public interface HolderMapping extends MappingSpec<Holder, HolderDto> {
                      @Unmapped
                      Hidden phone();
                    }
                  }
                  """);
      Compilation compilation = compile(nested);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Unmapped marker 'phone' names 'Hidden', which cannot be reached from '"
                  + PKG
                  + "'");
    }

    @Test
    @DisplayName("has no meaning on a sealed mapping")
    void markerHasNoMeaningOnSealedMapping() {
      JavaFileObject shapes =
          source(
              "Shape",
              """
              public sealed interface Shape permits Shape.Circle {
                record Circle(String radius) implements Shape {}
              }
              """);
      JavaFileObject dtos =
          source(
              "ShapeDto",
              """
              public sealed interface ShapeDto permits ShapeDto.CircleDto {
                record CircleDto(String radius) implements ShapeDto {}
              }
              """);
      JavaFileObject circle =
          source(
              "CircleMapping",
              """
              @GenerateMapping
              public interface CircleMapping extends MappingSpec<Shape.Circle, ShapeDto.CircleDto> {}
              """);
      JavaFileObject dispatch =
          source(
              "ShapeMapping",
              """
              @GenerateMapping
              public interface ShapeMapping extends MappingSpec<Shape, ShapeDto> {
                @Unmapped
                String radius();
              }
              """);
      Compilation compilation = compile(shapes, dtos, circle, dispatch);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Unmapped on 'radius' has no meaning on a sealed mapping.");
    }
  }

  @Nested
  @DisplayName("A Boolean isX() getter")
  class BooleanIsGetter {

    @Test
    @DisplayName("pairs with setX(Boolean) on both tiers, and gives way to a getX() of its name")
    void pairsOnBothTiers() throws ReflectiveOperationException {
      JavaFileObject account =
          source("Account", "public record Account(String name, Boolean active) {}");
      // The shape JAXB declares for an optional xs:boolean.
      String bean =
          """
          public class %s {
            private String name;
            private Boolean active;

            public String getName() { return name; }
            public void setName(String name) { this.name = name; }
            public Boolean isActive() { return active; }
            public void setActive(Boolean active) { this.active = active; }
          }
          """;
      JavaFileObject dto = source("AccountDto", bean.formatted("AccountDto"));
      JavaFileObject patch = source("AccountPatch", bean.formatted("AccountPatch"));
      JavaFileObject mapping =
          source(
              "AccountMapping",
              """
              @GenerateMapping
              public interface AccountMapping extends MappingSpec<Account, AccountDto> {}
              """);
      JavaFileObject patchMapping =
          source(
              "AccountPatchMapping",
              """
              @GenerateMapping
              public interface AccountPatchMapping extends UpdateSpec<Account, AccountPatch> {}
              """);
      // Declared first, Boolean isState() still gives way to getState() of the same name.
      JavaFileObject toggle = source("Toggle", "public record Toggle(String state) {}");
      JavaFileObject toggleDto =
          source(
              "ToggleDto",
              """
              public class ToggleDto {
                public Boolean isState() { return true; }
                public String getState() { return null; }
                public void setState(String state) {}
              }
              """);
      JavaFileObject toggleMapping =
          source(
              "ToggleMapping",
              """
              @GenerateMapping
              public interface ToggleMapping extends MappingSpec<Toggle, ToggleDto> {}
              """);
      Compilation compilation =
          compile(account, dto, patch, mapping, patchMapping, toggle, toggleDto, toggleMapping);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "ToggleMappingImpl"))
          .contains("wire.getState()")
          .doesNotContain("isState");

      var result = new RuntimeCompilationHelper.CompiledResult(compilation);
      Object dense = result.instance(PKG + ".AccountMappingImpl");
      Object active = create(result, "Account", "ada", true);
      Object built = invoke(dense, "build", active);
      Assertions.assertThat(invoke(built, "isActive")).isEqualTo(true);
      @SuppressWarnings("unchecked") // reflective call into the generated Impl
      Validated<NonEmptyList<FieldError>, Object> parsed =
          (Validated<NonEmptyList<FieldError>, Object>) invoke(dense, "parse", built);
      assertThatValidated(parsed).hasValue(active);

      Object sent = result.loadClass(PKG + ".AccountPatch").getConstructor().newInstance();
      invoke(sent, "setActive", false);
      assertThatValidated(update(result.instance(PKG + ".AccountPatchMappingImpl"), sent, active))
          .hasValue(create(result, "Account", "ada", false));
    }
  }
}

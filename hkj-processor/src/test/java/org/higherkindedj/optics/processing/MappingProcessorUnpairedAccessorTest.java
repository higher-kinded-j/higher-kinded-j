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
      import java.util.Map;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.MapField;
      import org.higherkindedj.optics.annotations.MappingSpec;
      import org.higherkindedj.optics.annotations.ReadOnly;
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
      // seteTag(...) is how the JavaBeans beanspec spells eTag's setter.
      JavaFileObject patch =
          source(
              "ContactPatch",
              """
              public class ContactPatch {
                public String getEmail() { return null; }
                public void setEmail(String email) {}
                public void setNickname(String nickname) {}
                public void seteTag(String eTag) {}
                public void setDefault(Boolean on) {}
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
      assertThat(compilation).hadErrorCount(5);
      // A keyword can name no marker, so the line offers only what can be written.
      assertThat(compilation)
          .hadErrorContaining(
              "Remove setDefault(Boolean) from 'ContactPatch', or rename it after the component it"
                  + " should update and give it a getter.");
      Assertions.assertThat(errors(compilation))
          .noneMatch(error -> error.contains("@Unmapped Boolean default()"));
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'eTag' on 'ContactPatch' has a setter, seteTag(String), but no"
                  + " getter, so the mapping leaves it out.");
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
  @DisplayName("A word that starts like an accessor")
  class Words {

    private static final JavaFileObject TRIP =
        source("Trip", "public record Trip(String away, String up) {}");

    private static final JavaFileObject TRIP_DTO =
        source(
            "TripDto",
            """
            public class TripDto {
              public String getaway() { return null; }
              public void setaway(String away) {}
              public String getUp() { return null; }
              public void setUp(String up) {}
            }
            """);

    private static final JavaFileObject NOTE =
        source("Note", "public record Note(String title, String body) {}");

    // Build-only: nothing reads it, so it leaves no accessor unpaired.
    private static final JavaFileObject NOTE_DTO =
        source(
            "NoteDto",
            """
            public class NoteDto {
              public void settitle(String title) {}
              public void setBody(String body) {}
            }
            """);

    @Test
    @DisplayName(
        "one named after a component with no accessor of its kind is refused, on every tier,"
            + " offering a marker line that pastes, and only where it can be followed")
    void wordWithNoAccessorOfItsKindIsRefused() {
      JavaFileObject tripSpec =
          source(
              "TripMapping",
              """
              @GenerateMapping
              public interface TripMapping extends MappingSpec<Trip, TripDto> {}
              """);
      // A leaf named after the component, which the marker would replace.
      JavaFileObject noteSpec =
          source(
              "NoteMapping",
              """
              @GenerateMapping
              public interface NoteMapping extends MappingSpec<Note, NoteDto> {
                default String title(String title) {
                  return title.strip();
                }
              }
              """);
      JavaFileObject point = source("Point", "public record Point(Integer x, Integer y) {}");
      JavaFileObject pointPatch =
          source(
              "PointPatch",
              """
              public class PointPatch {
                public Integer getx() { return null; }
                public void setx(Integer x) {}
                public Integer getY() { return null; }
                public void setY(Integer y) {}
              }
              """);
      JavaFileObject pointSpec =
          source(
              "PointPatchMapping",
              """
              @GenerateMapping
              public interface PointPatchMapping extends UpdateSpec<Point, PointPatch> {}
              """);
      // A read-only marker the word would have bound, and a rename onto a word.
      JavaFileObject item =
          source("Item", "public record Item(String id, String label, String name) {}");
      JavaFileObject itemDto =
          source(
              "ItemDto",
              """
              public class ItemDto {
                public String getid() { return null; }
                public String gettag() { return null; }
                public void settag(String tag) {}
                public String getName() { return null; }
                public void setName(String name) {}
              }
              """);
      JavaFileObject itemSpec =
          source(
              "ItemMapping",
              """
              @GenerateMapping
              public interface ItemMapping extends MappingSpec<Item, ItemDto> {
                @ReadOnly
                String id();

                @MapField(to = "tag")
                String label();
              }
              """);
      // A getter word beside the setter alone, and a setter word beside a getter-only List, which
      // its getter would otherwise fill.
      JavaFileObject holidayDto =
          source(
              "HolidayDto",
              """
              public class HolidayDto {
                public String getSummary() { return "a computed getter"; }
                public String getaway() { return null; }
                public void setAway(String away) {}
                public String getUp() { return null; }
                public void setUp(String up) {}
              }
              """);
      JavaFileObject holidaySpec =
          source(
              "HolidayMapping",
              """
              @GenerateMapping
              public interface HolidayMapping extends MappingSpec<Trip, HolidayDto> {}
              """);
      JavaFileObject post =
          source("Post", "public record Post(List<String> tags, String title) {}");
      JavaFileObject postDto =
          source(
              "PostDto",
              """
              public class PostDto {
                private List<String> tags = new ArrayList<>();
                public List<String> getTags() { return new ArrayList<>(tags); }
                public void settags(List<String> tags) { this.tags = tags; }
                public String getTitle() { return null; }
                public void setTitle(String title) {}
              }
              """);
      JavaFileObject postSpec =
          source(
              "PostMapping",
              """
              @GenerateMapping
              public interface PostMapping extends MappingSpec<Post, PostDto> {}
              """);
      // A word beside an unpaired getter of its kind is passed over, and the getter refused.
      JavaFileObject soloDto =
          source(
              "SoloDto",
              """
              public class SoloDto {
                public String getaway() { return null; }
                public String getAway() { return null; }
                public String getUp() { return null; }
                public void setUp(String up) {}
              }
              """);
      JavaFileObject soloSpec =
          source(
              "SoloMapping",
              """
              @GenerateMapping
              public interface SoloMapping extends MappingSpec<Trip, SoloDto> {}
              """);
      // Words and nothing else.
      JavaFileObject wordsDto =
          source(
              "WordsDto",
              """
              public class WordsDto {
                public String getaway() { return null; }
                public void setaway(String away) {}
              }
              """);
      JavaFileObject wordsSpec =
          source(
              "WordsMapping",
              """
              @GenerateMapping
              public interface WordsMapping extends MappingSpec<Trip, WordsDto> {}
              """);
      // A word of a generic method's type and one of a raw type: the marker line names a type the
      // spec can declare.
      JavaFileObject gen =
          source(
              "Gen",
              "public record Gen(String name, Object b, Map<String, String> c, List<String> d) {}");
      JavaFileObject genDto =
          source(
              "GenDto",
              """
              public class GenDto {
                public <T> T getb() { return null; }
                @SuppressWarnings("rawtypes")
                public Map getc() { return null; }
                public List<String> getd() { return null; }
                public String getName() { return null; }
                public void setName(String name) {}
              }
              """);
      JavaFileObject genSpec =
          source(
              "GenMapping",
              """
              @GenerateMapping
              public interface GenMapping extends MappingSpec<Gen, GenDto> {}
              """);
      // A bean only read, whose parse must fill every component, so no marker can leave one out.
      JavaFileObject tripView =
          source(
              "TripView",
              """
              public class TripView {
                private TripView() {}
                public String getaway() { return null; }
                public String getUp() { return null; }
              }
              """);
      JavaFileObject tripViewSpec =
          source(
              "TripViewMapping",
              """
              @GenerateMapping
              public interface TripViewMapping extends MappingSpec<Trip, TripView> {}
              """);
      // A hook taking another type is no setter of 'up', so getUp() alone is the refusal.
      JavaFileObject hookDto =
          source(
              "HookDto",
              """
              public class HookDto {
                public String getUp() { return null; }
                public void setup(Runnable hook) {}
                public String getAway() { return null; }
                public void setAway(String away) {}
              }
              """);
      JavaFileObject hookSpec =
          source(
              "HookMapping",
              """
              @GenerateMapping
              public interface HookMapping extends MappingSpec<Trip, HookDto> {}
              """);
      // A mix-in's @ReadOnly that binds nothing here answers no setter word.
      JavaFileObject readVocabulary =
          source(
              "ReadVocabulary",
              """
              public interface ReadVocabulary {
                @ReadOnly
                String away();
              }
              """);
      JavaFileObject mixDto =
          source(
              "MixDto",
              """
              public class MixDto {
                public String getUp() { return null; }
                public void setUp(String up) {}
                public void setaway(String away) {}
              }
              """);
      JavaFileObject mixSpec =
          source(
              "MixMapping",
              """
              @GenerateMapping
              public interface MixMapping extends MappingSpec<Trip, MixDto>, ReadVocabulary {}
              """);
      // Only written, so a getter word beside its setter is the only reader there would be.
      JavaFileObject buildDto =
          source(
              "BuildDto",
              """
              public class BuildDto {
                public String getaway() { return null; }
                public void setAway(String away) {}
                public void setUp(String up) {}
              }
              """);
      JavaFileObject buildSpec =
          source(
              "BuildMapping",
              """
              @GenerateMapping
              public interface BuildMapping extends MappingSpec<Trip, BuildDto> {}
              """);
      // A marker naming a property the bean pairs is no less a typo beside a word of its name.
      JavaFileObject pairDto =
          source(
              "PairDto",
              """
              public class PairDto {
                public String getaway() { return null; }
                public String getAway() { return null; }
                public void setAway(String away) {}
                public String getUp() { return null; }
                public void setUp(String up) {}
              }
              """);
      JavaFileObject pairSpec =
          source(
              "PairMapping",
              """
              @GenerateMapping
              public interface PairMapping extends MappingSpec<Trip, PairDto> {
                @Unmapped
                String away();
              }
              """);
      Compilation compilation =
          compile(
              readVocabulary,
              mixDto,
              mixSpec,
              buildDto,
              buildSpec,
              gen,
              genDto,
              genSpec,
              tripView,
              tripViewSpec,
              hookDto,
              hookSpec,
              pairDto,
              pairSpec,
              TRIP,
              TRIP_DTO,
              tripSpec,
              NOTE,
              NOTE_DTO,
              noteSpec,
              point,
              pointPatch,
              pointSpec,
              item,
              itemDto,
              itemSpec,
              holidayDto,
              holidaySpec,
              post,
              postDto,
              postSpec,
              wordsDto,
              wordsSpec,
              soloDto,
              soloSpec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(17);
      assertThat(compilation)
          .hadErrorContaining("declare '@Unmapped List<String> d();' on the spec.");
      assertThat(compilation)
          .hadErrorContaining(
              "'MixDto' declares setaway(String), which is not read as the setter of 'away'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'BuildDto' declares getaway(), which is not read as the getter of 'away'.");
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'away' on 'SoloDto' has a getter, getAway(), but no setter");
      assertThat(compilation)
          .hadErrorContaining(
              "'TripDto' declares getaway() and setaway(String), which are not read as the getter"
                  + " and setter of 'away'. A name that runs on in lower case after its get, is or"
                  + " set prefix is a word rather than an accessor, which would be spelt"
                  + " getAway(). With nothing else on 'TripDto' to read or write it, the mapping"
                  + " would leave 'Trip.away' out, and nothing would say so. Rename getaway() to"
                  + " getAway() and setaway(String) to setAway(String). Or, if getaway() and"
                  + " setaway(String) are not meant to carry 'Trip.away', declare '@Unmapped"
                  + " String away();' on the spec.");
      assertThat(compilation)
          .hadErrorContaining(
              "'NoteDto' declares settitle(String), which is not read as the setter of 'title'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Rename settitle(String) to setTitle(String). Or, if settitle(String) is not meant to"
                  + " carry 'Note.title', replace");
      assertThat(compilation)
          .hadErrorContaining(
              "'PointPatch' declares getx() and setx(Integer), which are not read as the getter"
                  + " and setter of 'x'.");
      assertThat(compilation)
          .hadErrorContaining(
              "updateFrom folds in only what a PATCH bean both reads and writes, so a value the"
                  + " client sends for 'x' would never reach 'Point.x', and nothing would say so.");
      assertThat(compilation)
          .hadErrorContaining(
              "With nothing else on 'ItemDto' to read it, parse could not fill 'Item.id', and"
                  + " nothing would say so. Rename getid() to getId().");
      assertThat(compilation)
          .hadErrorContaining(
              "With nothing else on 'ItemDto' to read or write it, the mapping would leave"
                  + " 'Item.label' (renamed to 'tag') out, and nothing would say so. Rename gettag()"
                  + " to getTag() and settag(String) to setTag(String). Or, if gettag() and settag(String) are not"
                  + " meant to carry 'Item.label' (renamed to 'tag'), replace 'label()' with"
                  + " '@Unmapped String tag();'.");
      assertThat(compilation)
          .hadErrorContaining(
              "'HolidayDto' declares getaway(), which is not read as the getter of 'away'.");
      assertThat(compilation)
          .hadErrorContaining(
              "With nothing else on 'HolidayDto' to read it, parse could not fill 'Trip.away'");
      assertThat(compilation)
          .hadErrorContaining(
              "'PostDto' declares settags(List<String>), which is not read as the setter of"
                  + " 'tags'.");
      assertThat(compilation)
          .hadErrorContaining(
              "Nothing else on 'PostDto' sets 'Post.tags', so build would fill it through"
                  + " getTags().addAll(...) instead, and nothing would say so. Rename"
                  + " settags(List<String>) to setTags(List<String>).");
      assertThat(compilation)
          .hadErrorContaining(
              "'WordsDto' is not a usable bean-shaped wire: it has no property to read or write.");
      assertThat(compilation)
          .hadErrorContaining(
              "It declares [getaway(), setaway(String)], each running on in lower case after its"
                  + " get, is or set prefix, so none is an accessor.");
      assertThat(compilation).hadErrorContaining("declare '@Unmapped Object b();' on the spec.");
      assertThat(compilation).hadErrorContaining("declare '@Unmapped Map<?, ?> c();' on the spec.");
      assertThat(compilation)
          .hadErrorContaining(
              "With nothing else on 'TripView' to read it, parse could not fill 'Trip.away', and"
                  + " nothing would say so. Rename getaway() to getAway().");
      assertThat(compilation)
          .hadErrorContaining(
              "bean property 'up' on 'HookDto' has a getter, getUp(), but no setter");
      Assertions.assertThat(errors(compilation))
          .noneMatch(error -> error.contains("'HookDto' declares"));
      assertThat(compilation)
          .hadErrorContaining("@Unmapped method 'away' names a property 'PairDto' maps.");
    }

    @Test
    @DisplayName(
        "one is passed over where @ReadOnly, a setter that is never called, another name or a"
            + " property no spec can name answers it")
    void wordAnsweredElsewhereIsPassedOver() {
      // A @ReadOnly marker says 'away' has no setter, so setaway(...) is none that is missing.
      JavaFileObject readDto =
          source(
              "ReadDto",
              """
              public class ReadDto {
                public String getAway() { return null; }
                public void setaway(String away) {}
                public String getUp() { return null; }
                public void setUp(String up) {}
              }
              """);
      JavaFileObject readSpec =
          source(
              "ReadMapping",
              """
              @GenerateMapping
              public interface ReadMapping extends MappingSpec<Trip, ReadDto> {
                @ReadOnly
                String away();
              }
              """);
      // Only read, so no setter of it is ever called: a setup(...) hook is no missing one.
      JavaFileObject jobView =
          source(
              "JobView",
              """
              public class JobView {
                private JobView() {}
                public String getUp() { return null; }
                public String getAway() { return null; }
                public void setup(Runnable hook) {}
              }
              """);
      JavaFileObject jobSpec =
          source(
              "JobMapping",
              """
              @GenerateMapping
              public interface JobMapping extends MappingSpec<Trip, JobView> {}
              """);
      // 'away' crosses as 'holiday' through a mix-in's rename, so getaway() is no missing getter.
      JavaFileObject holidayVocabulary =
          source(
              "HolidayVocabulary",
              """
              public interface HolidayVocabulary {
                @MapField(to = "holiday")
                String away();
              }
              """);
      JavaFileObject renamedDto =
          source(
              "RenamedDto",
              """
              public class RenamedDto {
                public String getaway() { return "a label"; }
                public String getHoliday() { return null; }
                public void setHoliday(String holiday) {}
                public String getUp() { return null; }
                public void setUp(String up) {}
              }
              """);
      JavaFileObject renamedSpec =
          source(
              "RenamedMapping",
              """
              @GenerateMapping
              public interface RenamedMapping
                  extends MappingSpec<Trip, RenamedDto>, HolidayVocabulary {}
              """);
      // set2D(...) names a property no component and no marker can be named after.
      JavaFileObject plot = source("Plot", "public record Plot(Integer x) {}");
      JavaFileObject plotPatch =
          source(
              "PlotPatch",
              """
              public class PlotPatch {
                public Integer getX() { return null; }
                public void setX(Integer x) {}
                public void set2D(boolean on) {}
              }
              """);
      JavaFileObject plotSpec =
          source(
              "PlotPatchMapping",
              """
              @GenerateMapping
              public interface PlotPatchMapping extends UpdateSpec<Plot, PlotPatch> {}
              """);
      Compilation compilation =
          compile(
              TRIP,
              readDto,
              readSpec,
              jobView,
              jobSpec,
              holidayVocabulary,
              renamedDto,
              renamedSpec,
              plot,
              plotPatch,
              plotSpec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "RenamedMappingImpl"))
          .contains("wire.getHoliday()")
          .doesNotContain("getaway");
    }

    @Test
    @DisplayName("an @Unmapped marker leaves one out, on a two-way bean and a build-only one")
    void unmappedMarkerLeavesAWordOut() {
      JavaFileObject tripSpec =
          source(
              "TripMapping",
              """
              @GenerateMapping
              public interface TripMapping extends MappingSpec<Trip, TripDto> {
                @Unmapped
                String away();
              }
              """);
      JavaFileObject noteSpec =
          source(
              "NoteMapping",
              """
              @GenerateMapping
              public interface NoteMapping extends MappingSpec<Note, NoteDto> {
                @Unmapped
                String title();
              }
              """);
      Compilation compilation = compile(TRIP, TRIP_DTO, tripSpec, NOTE, NOTE_DTO, noteSpec);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "TripMappingImpl"))
          .contains("wire.setUp(domain.up());")
          .doesNotContain("getaway")
          .doesNotContain("setaway");
      Assertions.assertThat(generatedSource(compilation, "NoteMappingImpl"))
          .contains("wire.setBody(domain.body());")
          .doesNotContain("settitle");
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
    @DisplayName(
        "returning ValidatedPrism reads as a leaf, and is told to take another return type")
    void returningValidatedPrismReadsAsALeaf() {
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
                public String getExtra() { return null; }
              }
              """);
      JavaFileObject spec =
          source(
              "ContactMapping",
              """
              @GenerateMapping
              public interface ContactMapping extends MappingSpec<Contact, ContactDto> {
                @Unmapped
                org.higherkindedj.optics.validated.ValidatedPrism<String, String> extra();
              }
              """);
      Compilation compilation = compile(CONTACT, dto, spec);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorCount(1);
      assertThat(compilation)
          .hadErrorContaining(
              "abstract leaf 'extra' needs a generic spec. A concrete pair's leaf carries its own"
                  + " parser as a 'default' body; only a generic spec defers the element mapping"
                  + " to the generated 'of(...)' factory. An @Unmapped method returning"
                  + " ValidatedPrism reads as a leaf, not a marker. Give the method a body"
                  + " ('default'), or make the spec generic in the element types; to leave the"
                  + " accessor out, give it a return type other than ValidatedPrism, such as the"
                  + " accessor's own.");
    }

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
    @DisplayName("named after the accessor rather than its property, it is offered the property")
    void markerNamedAfterTheAccessorIsOfferedItsProperty() {
      JavaFileObject bean =
          source(
              "ContactBean",
              """
              public class ContactBean {
                private String name;
                private String email;
                private String phone;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public String getPhone() { return phone; }
                public void setPhone(String phone) { this.phone = phone; }
                public void setStamp(String stamp) {}
              }
              """);
      JavaFileObject spec =
          source(
              "ContactBeanMapping",
              """
              @GenerateMapping
              public interface ContactBeanMapping extends MappingSpec<Contact, ContactBean> {
                @Unmapped
                String setStamp();
              }
              """);
      assertThat(compile(CONTACT, bean, spec))
          .hadErrorContaining(
              "@Unmapped method 'setStamp' names no accessor 'ContactBean' leaves out. The marker"
                  + " reads an accessor with no partner as deliberate. Left unpaired on"
                  + " 'ContactBean': [stamp]. Name the marker after the accessor's property, or"
                  + " remove it. Did you mean 'stamp()'?");
    }

    @Test
    @DisplayName(
        "a bean crossed one way, or one that pairs every accessor, leaves a marker nothing to"
            + " leave out")
    void markerWithNothingToLeaveOut() {
      JavaFileObject view =
          source(
              "ContactView",
              """
              public class ContactView {
                public String getName() { return "ada"; }
                public String getEmail() { return "ada@corp"; }
                public String getPhone() { return "01"; }
                public String getNickname() { return "a"; }
              }
              """);
      JavaFileObject viewSpec =
          source(
              "ContactViewMapping",
              """
              @GenerateMapping
              public interface ContactViewMapping extends MappingSpec<Contact, ContactView> {
                @Unmapped
                String nickname();
              }
              """);
      JavaFileObject viewTypo =
          source(
              "ContactViewTypoMapping",
              """
              @GenerateMapping
              public interface ContactViewTypoMapping extends MappingSpec<Contact, ContactView> {
                @Unmapped
                String nicknmae();
              }
              """);
      JavaFileObject out =
          source(
              "ContactOut",
              """
              public class ContactOut {
                public void setName(String name) {}
                public void setEmail(String email) {}
                public void setPhone(String phone) {}
              }
              """);
      JavaFileObject outSpec =
          source(
              "ContactOutMapping",
              """
              @GenerateMapping
              public interface ContactOutMapping extends MappingSpec<Contact, ContactOut> {
                @Unmapped
                String phone();
              }
              """);
      JavaFileObject paired =
          source(
              "ContactBean",
              """
              public class ContactBean {
                private String name;
                private String email;
                private String phone;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getEmail() { return email; }
                public void setEmail(String email) { this.email = email; }
                public String getPhone() { return phone; }
                public void setPhone(String phone) { this.phone = phone; }
              }
              """);
      JavaFileObject pairedTypo =
          source(
              "ContactBeanMapping",
              """
              @GenerateMapping
              public interface ContactBeanMapping extends MappingSpec<Contact, ContactBean> {
                @Unmapped
                String phnoe();
              }
              """);
      Compilation compilation =
          compile(CONTACT, view, viewSpec, viewTypo, out, outSpec, paired, pairedTypo);
      assertThat(compilation).failed();
      String read =
          "has nothing to leave out: 'ContactView' is only read. The marker reads an accessor with"
              + " no partner as deliberate, and each getter of a bean that is only read is a"
              + " property of its own, so none is left unpaired: parse reads those the domain"
              + " needs and never calls the rest. Remove the marker.";
      assertThat(compilation).hadErrorContaining("@Unmapped method 'nickname' " + read);
      assertThat(compilation).hadErrorContaining("@Unmapped method 'nicknmae' " + read);
      assertThat(compilation)
          .hadErrorContaining(
              "@Unmapped method 'phone' has nothing to leave out: 'ContactOut' is only written."
                  + " The marker reads an accessor with no partner as deliberate, and each writer"
                  + " of a bean that is only written is a property of its own, so none is left"
                  + " unpaired: build fills each one. Remove the marker, and fill a writer no"
                  + " domain component names with a derived field, or remove that writer from"
                  + " 'ContactOut'.");
      assertThat(compilation)
          .hadErrorContaining(
              "@Unmapped method 'phnoe' names no accessor 'ContactBean' leaves out. The marker"
                  + " reads an accessor with no partner as deliberate. 'ContactBean' leaves no"
                  + " accessor unpaired. Remove the marker.");
      Assertions.assertThat(errors(compilation)).hasSize(4);
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

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Specs naming types another annotation processor writes. javac has no such type in the round that
 * writes it, so a spec whose classification reads one waits for the round that brings it, and so
 * does a spec nesting a spec that waits. Each case compiles beside a processor that writes the
 * types in its first round, and maps exactly as it would were the types written by hand; a type
 * that never appears leaves the spec ungenerated and the error to javac.
 */
@DisplayName("MappingProcessor - types another processor generates")
class MappingProcessorRoundsTest {

  private static final String PKG = "com.example.rounds";

  private static final String IMPORTS =
      """
      import java.util.List;
      import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
      import org.higherkindedj.hkt.validated.FieldError;
      import org.higherkindedj.hkt.validated.Validated;
      import org.higherkindedj.optics.annotations.GenerateMapping;
      import org.higherkindedj.optics.annotations.GenerateMerge;
      import org.higherkindedj.optics.annotations.MappingSpec;

      """;

  /** A mix-in with nothing in it, so a spec extending it waits for it and for nothing else. */
  private static final String CONTACT_VOCABULARY = "public interface ContactVocabulary {}";

  private static final JavaFileObject CONTACT =
      source("Contact", "public record Contact(String name) {}");

  private static final JavaFileObject CONTACT_DTO =
      source("ContactDto", "public record ContactDto(String name) {}");

  /** Maps the contact pair, extending a mix-in another processor writes. */
  private static final JavaFileObject WAITING_CONTACT_MAPPING =
      source(
          "ContactMapping",
          """
          @GenerateMapping
          public interface ContactMapping
              extends ContactVocabulary, MappingSpec<Contact, ContactDto> {}
          """);

  private static final JavaFileObject NAMED = source("Named", "public record Named(String id) {}");

  private static final JavaFileObject REACHED =
      source("Reached", "public record Reached(ContactDto contact) {}");

  private static final JavaFileObject CARD =
      source("Card", "public record Card(String id, Contact contact) {}");

  /** Merges into a card, whose contact nests through the contact mapping. */
  private static final String CARD_ASSEMBLY =
      """
      @GenerateMerge
      public interface CardAssembly {
        Validated<NonEmptyList<FieldError>, Card> assemble(Named named, Reached reached);
      }
      """;

  private static final JavaFileObject PAGE =
      source("Page", "public record Page<T>(List<T> items) {}");

  private static final JavaFileObject PAGE_DTO =
      source("PageDto", "public record PageDto<T>(List<T> items) {}");

  /** A generic class with an inner class, whose type {@code Outer<X>.Inner} carries an argument. */
  private static final JavaFileObject OUTER =
      source(
          "Outer",
          """
          public class Outer<T> {
            public class Inner {}
          }
          """);

  @TempDir Path tmp;

  /**
   * Writes the given sources in its first round, standing in for any processor whose output a
   * mapping names.
   */
  private static final class TypeWriter extends AbstractProcessor {

    private final Map<String, String> sources;
    private final Set<String> roots = new LinkedHashSet<>();
    private boolean written;

    TypeWriter(Map<String, String> sources) {
      this.sources = sources;
    }

    /**
     * Every type the compilation's rounds were handed as a root: a file any processor writes in one
     * round is a root of the next, so this tells what was written even when the compilation fails.
     */
    Set<String> roots() {
      return roots;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return Set.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      ElementFilter.typesIn(roundEnv.getRootElements())
          .forEach(root -> roots.add(root.getQualifiedName().toString()));
      if (!written) {
        written = true;
        // In name order, so the next round's roots come in the same order on every run.
        new TreeMap<>(sources).forEach(this::write);
      }
      return false;
    }

    /** Writes one type; a name with a module prefix, {@code billing/Vocabulary}, goes there. */
    private void write(String simpleName, String body) {
      int slash = simpleName.indexOf('/');
      String type =
          simpleName.substring(0, slash + 1) + PKG + "." + simpleName.substring(slash + 1);
      try (Writer out = processingEnv.getFiler().createSourceFile(type).openWriter()) {
        out.write("package " + PKG + ";\n\n" + IMPORTS + body);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName, "package " + PKG + ";\n\n" + IMPORTS + body);
  }

  /** An empty spec mapping one pair. */
  private static JavaFileObject spec(String name, String domain, String wire) {
    return source(
        name,
        "@GenerateMapping\npublic interface %s extends MappingSpec<%s, %s> {}\n"
            .formatted(name, domain, wire));
  }

  private static Compilation compile(Map<String, String> generated, JavaFileObject... sources) {
    return compile(new TypeWriter(generated), List.of(), List.of(), sources);
  }

  private static Compilation compile(
      List<String> options, Map<String, String> generated, JavaFileObject... sources) {
    return compile(new TypeWriter(generated), options, List.of(), sources);
  }

  private static Compilation compile(
      TypeWriter writer, List<String> options, List<Path> classDirs, JavaFileObject... sources) {
    return compile(
        writer, List.of(new MappingProcessor(), new MergeProcessor()), options, classDirs, sources);
  }

  /**
   * The writer goes first, then the {@code mapping} processors: javac does not offer a round to a
   * processor supporting every annotation once an earlier processor has claimed that round's
   * annotations.
   */
  private static Compilation compile(
      TypeWriter writer,
      List<Processor> mapping,
      List<String> options,
      List<Path> classDirs,
      JavaFileObject... sources) {
    return javac()
        .withProcessors(Stream.<Processor>concat(Stream.of(writer), mapping.stream()).toList())
        .withOptions(
            Stream.concat(Stream.of("-Xlint:unchecked,rawtypes", "-Werror"), options.stream())
                .toList())
        .withClasspath(GeneratorTestHelper.classpathWith(classDirs.toArray(Path[]::new)))
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

  /**
   * Every error is the processor's own refusal: none is javac's, reached through generated code.
   */
  private static void assertOnlyRefusals(Compilation compilation) {
    Assertions.assertThat(compilation.errors())
        .isNotEmpty()
        .allSatisfy(
            error -> Assertions.assertThat(error.getMessage(null)).contains("@GenerateMapping"));
  }

  @Test
  @DisplayName("a generated pair nests through the spec mapping it, which waits for the pair too")
  void aGeneratedPairNestsThroughItsSpec() {
    Compilation compilation =
        compile(
            Map.of(
                "Tag", "public record Tag(String value) {}",
                "TagDto", "public record TagDto(String value) {}"),
            spec("TagMapping", "Tag", "TagDto"),
            source("Label", "public record Label(String id, Tag tag) {}"),
            source("LabelDto", "public record LabelDto(String id, TagDto tag) {}"),
            spec("LabelMapping", "Label", "LabelDto"));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "LabelMappingImpl"))
        .contains("TagMappingImpl.INSTANCE.asValidatedPrism()");
  }

  @Test
  @DisplayName(
      "a generated type is refused wherever a hand-written one would be, never copied or matched")
  void aGeneratedTypeIsRefusedLikeAHandWrittenOne() {
    Compilation compilation =
        compile(
            Map.of(
                "Tag", "public record Tag(String value) {}",
                "TagDto", "public record TagDto(String value) {}"),
            PAGE,
            PAGE_DTO,
            OUTER,
            // a component pair no spec maps
            source("Label", "public record Label(String id, Tag tag) {}"),
            source("LabelDto", "public record LabelDto(String id, TagDto tag) {}"),
            spec("LabelMapping", "Label", "LabelDto"),
            // a type argument, against a spec for a different one
            spec("StringPageMapping", "Page<String>", "PageDto<String>"),
            source("Report", "public record Report(String id, Page<Tag> results) {}"),
            source("ReportDto", "public record ReportDto(String id, PageDto<String> results) {}"),
            spec("ReportMapping", "Report", "ReportDto"),
            // the argument of an enclosing type
            source("Wrapped", "public record Wrapped(String id, Outer<Tag>.Inner value) {}"),
            source(
                "WrappedDto", "public record WrappedDto(String id, Outer<String>.Inner value) {}"),
            spec("WrappedMapping", "Wrapped", "WrappedDto"),
            // a bean property inherited from an interface
            source(
                "HasTag",
                """
                public interface HasTag {
                  default TagDto getTag() {
                    return null;
                  }

                  default void setTag(TagDto tag) {}
                }
                """),
            source(
                "BadgeBean",
                """
                public class BadgeBean implements HasTag {
                  private String id;

                  public String getId() {
                    return id;
                  }

                  public void setId(String id) {
                    this.id = id;
                  }
                }
                """),
            source("Badge", "public record Badge(String id, String tag) {}"),
            spec("BadgeMapping", "Badge", "BadgeBean"),
            // a setter of the top-level builder a bean's static factory returns
            source(
                "TicketRequestBuilder",
                """
                public class TicketRequestBuilder {
                  public TicketRequestBuilder id(String id) {
                    return this;
                  }

                  public TicketRequestBuilder tag(TagDto tag) {
                    return this;
                  }

                  public TicketRequest build() {
                    return new TicketRequest();
                  }
                }
                """),
            source(
                "TicketRequest",
                """
                public class TicketRequest {
                  public static TicketRequestBuilder builder() {
                    return new TicketRequestBuilder();
                  }
                }
                """),
            source("Ticket", "public record Ticket(String id, String tag) {}"),
            spec("TicketMapping", "Ticket", "TicketRequest"));
    assertThat(compilation).failed();
    assertThat(compilation).hadErrorContaining("target field 'LabelDto.tag' has no usable source");
    assertThat(compilation)
        .hadErrorContaining("target field 'ReportDto.results' has no usable source");
    assertThat(compilation)
        .hadErrorContaining("target field 'WrappedDto.value' has no usable source");
    assertThat(compilation).hadErrorContaining("target field 'BadgeBean.tag' has no usable source");
    assertThat(compilation)
        .hadErrorContaining("target field 'TicketRequest.tag' has no usable source");
    assertOnlyRefusals(compilation);
  }

  @Test
  @DisplayName("a spec nesting a waiting spec waits with it, and nests once both resolve")
  void aSpecNestingAWaitingSpecWaitsWithIt() {
    Compilation compilation =
        compile(
            Map.of("ContactVocabulary", CONTACT_VOCABULARY),
            CONTACT,
            CONTACT_DTO,
            WAITING_CONTACT_MAPPING,
            source("Order", "public record Order(String id, Contact contact) {}"),
            source("OrderDto", "public record OrderDto(String id, ContactDto contact) {}"),
            spec("OrderMapping", "Order", "OrderDto"));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "OrderMappingImpl"))
        .contains("ContactMappingImpl.INSTANCE.asValidatedPrism()");
  }

  @Test
  @DisplayName(
      "where the compiler cannot say which file a type came from, a spec waits for what its"
          + " declaration names, and so do the specs nesting it")
  void specsWaitWithoutAFileObjectLookup() {
    // A compiler that cannot say where a type was read from has the records read as class files,
    // which name the types they nest through and make nothing wait. A spec's own declaration is
    // read as ever, since the processor met the spec in source.
    Compilation compilation =
        compile(
            new TypeWriter(Map.of("ContactVocabulary", CONTACT_VOCABULARY)),
            GeneratorTestHelper.withoutFileObjectLookup(
                new MappingProcessor(), new MergeProcessor()),
            List.of(),
            List.of(),
            CONTACT,
            CONTACT_DTO,
            WAITING_CONTACT_MAPPING,
            source("Order", "public record Order(String id, Contact contact) {}"),
            source("OrderDto", "public record OrderDto(String id, ContactDto contact) {}"),
            spec("OrderMapping", "Order", "OrderDto"),
            NAMED,
            REACHED,
            CARD,
            source("CardAssembly", CARD_ASSEMBLY));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "OrderMappingImpl"))
        .contains("ContactMappingImpl.INSTANCE.asValidatedPrism()");
    Assertions.assertThat(generatedSource(compilation, "CardAssemblyImpl"))
        .contains("ContactMappingImpl.INSTANCE");
  }

  @Test
  @DisplayName("a merge naming a generated type, or nesting a mapping that waits, waits too")
  void aMergeWaits() {
    Compilation compilation =
        compile(
            Map.of(
                "ContactVocabulary",
                CONTACT_VOCABULARY,
                "Audit",
                "public record Audit(String by) {}"),
            CONTACT,
            CONTACT_DTO,
            WAITING_CONTACT_MAPPING,
            NAMED,
            source("Audited", "public record Audited(Audit audit) {}"),
            source("Stamped", "public record Stamped(String id, Audit audit) {}"),
            source(
                "StampAssembly",
                """
                @GenerateMerge
                public interface StampAssembly {
                  Stamped assemble(Named named, Audited audited);
                }
                """),
            REACHED,
            CARD,
            source("CardAssembly", CARD_ASSEMBLY));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "StampAssemblyImpl")).contains("audit");
    Assertions.assertThat(generatedSource(compilation, "CardAssemblyImpl"))
        .contains("ContactMappingImpl.INSTANCE");
  }

  @Test
  @DisplayName("a merge another processor writes nests through a mapping that waited before it")
  void aWrittenMergeNestsThroughAWaitingMapping() {
    Compilation compilation =
        compile(
            // The merge processor first runs in the round the merge is written into, a round after
            // the contact mapping began waiting; with no index to read, only the record of the
            // mappings the compilation has met carries the mapping to it.
            List.of("-Ahkj.mapping.index=false"),
            Map.of("ContactVocabulary", CONTACT_VOCABULARY, "CardAssembly", CARD_ASSEMBLY),
            CONTACT,
            CONTACT_DTO,
            WAITING_CONTACT_MAPPING,
            NAMED,
            REACHED,
            CARD);
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "CardAssemblyImpl"))
        .contains("ContactMappingImpl.INSTANCE");
  }

  @Test
  @DisplayName("a spec arriving in a later round nests through a spec processed in an earlier one")
  void aLaterSpecNestsThroughAnEarlierOne() {
    Compilation compilation =
        compile(
            // Without the index, only the record of the specs the compilation has met carries the
            // customer mapping into the round the invoice mapping arrives in.
            List.of("-Ahkj.mapping.index=false"),
            Map.of(
                "InvoiceMapping",
                """
                @GenerateMapping
                public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}
                """),
            source("Customer", "public record Customer(String name) {}"),
            source("CustomerDto", "public record CustomerDto(String name) {}"),
            spec("CustomerMapping", "Customer", "CustomerDto"),
            source("Invoice", "public record Invoice(String id, Customer customer) {}"),
            source("InvoiceDto", "public record InvoiceDto(String id, CustomerDto customer) {}"));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "InvoiceMappingImpl"))
        .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()");
  }

  @Test
  @DisplayName("a spec naming the Impl it is generated as does not wait for it")
  void aSpecNamingItsOwnImplDoesNotWait() {
    Compilation compilation =
        compile(
            Map.of(),
            CONTACT,
            CONTACT_DTO,
            source(
                "ContactMapping",
                """
                @GenerateMapping
                public interface ContactMapping extends MappingSpec<Contact, ContactDto> {
                  // by its simple name, which resolves in the spec's own package
                  static ContactMappingImpl instance() {
                    return ContactMappingImpl.INSTANCE;
                  }

                  // and by its qualified name
                  static com.example.rounds.ContactMappingImpl qualified() {
                    return ContactMappingImpl.INSTANCE;
                  }
                }
                """));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "ContactMappingImpl")).contains("INSTANCE");
  }

  @Test
  @DisplayName("a generated type is not taken for a same-named Impl another package's spec writes")
  void aGeneratedTypeIsNotTakenForAnotherPackagesImpl() {
    Compilation compilation =
        compile(
            // another processor writes a wire that happens to be named like an Impl
            Map.of("WidgetMappingImpl", "public record WidgetMappingImpl(String id) {}"),
            source("Badge", "public record Badge(String id) {}"),
            spec("BadgeMapping", "Badge", "WidgetMappingImpl"),
            // and a spec in another package is generated as a WidgetMappingImpl of its own
            JavaFileObjects.forSourceString(
                "com.example.elsewhere.Widget",
                "package com.example.elsewhere;\n\npublic record Widget(String id) {}"),
            JavaFileObjects.forSourceString(
                "com.example.elsewhere.WidgetDto",
                "package com.example.elsewhere;\n\npublic record WidgetDto(String id) {}"),
            JavaFileObjects.forSourceString(
                "com.example.elsewhere.WidgetMapping",
                """
                package com.example.elsewhere;

                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.MappingSpec;

                @GenerateMapping
                public interface WidgetMapping extends MappingSpec<Widget, WidgetDto> {}
                """));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "BadgeMappingImpl"))
        .contains("public WidgetMappingImpl build(Badge domain)");
  }

  @Test
  @DisplayName(
      "a spec still waiting when a refusal ends processing writes nothing in the last round")
  void nothingIsWrittenInTheLastRound() {
    Compilation compilation =
        compile(
            Map.of("ContactVocabulary", CONTACT_VOCABULARY),
            CONTACT,
            CONTACT_DTO,
            WAITING_CONTACT_MAPPING,
            // a merge nesting the waiting mapping, which waits with it
            NAMED,
            REACHED,
            CARD,
            source("CardAssembly", CARD_ASSEMBLY),
            source("Broken", "public record Broken(String id, Integer count) {}"),
            source("BrokenDto", "public record BrokenDto(String id, String count) {}"),
            spec("BrokenMapping", "Broken", "BrokenDto"));
    assertThat(compilation).failed();
    assertThat(compilation)
        .hadErrorContaining("target field 'BrokenDto.count' has no usable source");
    // javac makes the round after an error the last, and a file written in it gets no round of its
    // own, which javac warns about.
    Assertions.assertThat(compilation.diagnostics())
        .noneMatch(diagnostic -> diagnostic.getMessage(null).contains("created in the last round"));
  }

  @Test
  @DisplayName("a spec naming a type that never appears generates nothing, wherever it names it")
  void aTypeThatNeverAppearsIsJavacsError() {
    TypeWriter writer = new TypeWriter(Map.of());
    Compilation compilation =
        compile(
            writer,
            List.of(),
            List.of(),
            PAGE,
            PAGE_DTO,
            OUTER,
            // a spec naming nothing unresolved, and nothing a waiting spec names, so it is written
            CONTACT,
            CONTACT_DTO,
            spec("ContactMapping", "Contact", "ContactDto"),
            // a component
            source("Report", "public record Report(String id, Missing missing) {}"),
            source("ReportDto", "public record ReportDto(String id, String missing) {}"),
            spec("ReportMapping", "Report", "ReportDto"),
            // an array element
            source("Holder", "public record Holder(Missing[] items) {}"),
            source("HolderDto", "public record HolderDto(String[] items) {}"),
            spec("HolderMapping", "Holder", "HolderDto"),
            // a use site's type argument, against a generic spec
            source(
                "PageMapping",
                """
                @GenerateMapping
                public interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}
                """),
            source("Paged", "public record Paged(Page<Missing> results) {}"),
            source("PagedDto", "public record PagedDto(PageDto<Missing> results) {}"),
            spec("PagedMapping", "Paged", "PagedDto"),
            // the spec's own type argument
            spec("MissingArgMapping", "Page<Missing>", "PageDto<Missing>"),
            // an enclosing type's argument
            source("Wrapped", "public record Wrapped(Outer<Missing>.Inner value) {}"),
            source("WrappedDto", "public record WrappedDto(Outer<String>.Inner value) {}"),
            spec("WrappedMapping", "Wrapped", "WrappedDto"),
            // a spec method's parameter
            source("User", "public record User(String name) {}"),
            source("UserDto", "public record UserDto(String name) {}"),
            source(
                "UserMapping",
                """
                @GenerateMapping
                public interface UserMapping extends MappingSpec<User, UserDto> {
                  default UserDto build(Missing missing) {
                    return null;
                  }
                }
                """),
            // a spec method's thrown type
            source("Invoice", "public record Invoice(String id) {}"),
            source("InvoiceDto", "public record InvoiceDto(String id) {}"),
            source(
                "InvoiceMapping",
                """
                @GenerateMapping
                public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {
                  default InvoiceDto draft() throws MissingException {
                    return null;
                  }
                }
                """),
            // a mix-in, and a mix-in's superinterface
            source("Account", "public record Account(String name) {}"),
            source("AccountDto", "public record AccountDto(String name) {}"),
            source(
                "AccountMapping",
                """
                @GenerateMapping
                public interface AccountMapping
                    extends MissingVocabulary, MappingSpec<Account, AccountDto> {}
                """),
            source(
                "PartialVocabulary", "public interface PartialVocabulary extends MissingBase {}"),
            source(
                "MemberMapping",
                """
                @GenerateMapping
                public interface MemberMapping
                    extends PartialVocabulary, MappingSpec<Account, AccountDto> {}
                """));
    assertThat(compilation).failed();
    Assertions.assertThat(compilation.errors())
        .isNotEmpty()
        .allSatisfy(
            error -> Assertions.assertThat(error.getMessage(null)).contains("cannot find symbol"));
    // compile-testing lists no generated files for a failed compilation, so the rounds' roots say
    // what was written: the contact mapping, which names nothing unresolved, and none of the specs
    // that wait.
    Assertions.assertThat(writer.roots())
        .contains(PKG + ".ContactMappingImpl")
        .doesNotContain(
            PKG + ".ReportMappingImpl",
            PKG + ".HolderMappingImpl",
            PKG + ".PagedMappingImpl",
            PKG + ".MissingArgMappingImpl",
            PKG + ".WrappedMappingImpl",
            PKG + ".UserMappingImpl",
            PKG + ".InvoiceMappingImpl",
            PKG + ".AccountMappingImpl",
            PKG + ".MemberMappingImpl");
  }

  @Test
  @DisplayName("a spec over a dependency's records waits with the waiting spec it nests through")
  void aSpecOverClassFileRecordsWaitsWithTheSpecItNests() throws IOException {
    Compilation library =
        javac()
            .compile(
                JavaFileObjects.forSourceString(
                    "com.library.Types",
                    """
                    package com.library;

                    public final class Types {
                      private Types() {}

                      public record Money(String amount) {}

                      public record MoneyDto(String amount) {}

                      public record Order(String id, Money total) {}

                      public record OrderDto(String id, MoneyDto total) {}
                    }
                    """));
    assertThat(library).succeeded();
    Path libraryClasses = GeneratorTestHelper.classDirectory(library, tmp.resolve("library"));
    Compilation compilation =
        compile(
            new TypeWriter(Map.of("MoneyVocabulary", "public interface MoneyVocabulary {}")),
            List.of(),
            List.of(libraryClasses),
            source(
                "MoneyMapping",
                """
                import com.library.Types;

                @GenerateMapping
                public interface MoneyMapping
                    extends MoneyVocabulary, MappingSpec<Types.Money, Types.MoneyDto> {}
                """),
            source(
                "OrderMapping",
                """
                import com.library.Types;

                @GenerateMapping
                public interface OrderMapping extends MappingSpec<Types.Order, Types.OrderDto> {}
                """));
    assertThat(compilation).succeededWithoutWarnings();
    Assertions.assertThat(generatedSource(compilation, "OrderMappingImpl"))
        .contains("MoneyMappingImpl.INSTANCE.asValidatedPrism()");
  }

  @Test
  @DisplayName("a spec waits in its own module, apart from a same-named spec in another module")
  void aSpecWaitsInItsOwnModule() throws IOException {
    List<Diagnostic<? extends JavaFileObject>> diagnostics =
        GeneratorTestHelper.compileModules(
            tmp,
            List.of(
                new TypeWriter(Map.of("billing/ContactVocabulary", CONTACT_VOCABULARY)),
                new MappingProcessor(),
                new MergeProcessor()),
            Map.of(
                "billing",
                List.of(CONTACT, CONTACT_DTO, WAITING_CONTACT_MAPPING),
                "shipping",
                List.of(CONTACT, CONTACT_DTO, spec("ContactMapping", "Contact", "ContactDto"))));
    // javac cannot write a type into a package two modules share, so each spec processed reports
    // the shared package, and those reports say which specs were processed: shipping's, while
    // billing's waits for its mix-in until the reports end processing.
    Assertions.assertThat(diagnostics)
        .filteredOn(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
        .isNotEmpty()
        .allSatisfy(
            error ->
                Assertions.assertThat(error.getSource().toUri().getPath()).contains("/shipping/"));
  }
}

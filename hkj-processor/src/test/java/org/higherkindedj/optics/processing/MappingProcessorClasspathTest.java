// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.invoke;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Nesting, sealed dispatch and merge fills across compilations: an upstream module is compiled with
 * the processor and laid out as class files on a directory, then a downstream compilation finds its
 * specs through the classpath index, exactly as a jar dependency would be found.
 */
@DisplayName("MappingProcessor - specs compiled into dependencies")
class MappingProcessorClasspathTest {

  private static final String INDEX_PACKAGE_DIR = "org/higherkindedj/mapping/index/";

  private static final List<File> TEST_CLASSPATH =
      Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
          .map(File::new)
          .toList();

  /** The upstream module's types: records, generic records and a sealed pair, in one holder. */
  private static final JavaFileObject UPSTREAM_TYPES =
      JavaFileObjects.forSourceString(
          "com.upstream.Upstream",
          """
          package com.upstream;

          import java.util.List;

          public final class Upstream {
            public record EmailAddress(String value) {}

            public record Customer(String name, EmailAddress email) {}

            public record CustomerDto(String name, String email) {}

            public record CustomerCard(String name) {}

            public record Page<T>(List<T> items, int number) {}

            public record PageDto<T>(List<T> items, int number) {}

            public record Batch<T>(List<T> items) {}

            public record BatchDto<T>(List<T> items) {}

            public sealed interface Shape permits Circle {}

            public record Circle(int radius) implements Shape {}

            public sealed interface ShapeDto permits CircleDto {}

            public record CircleDto(int radius) implements ShapeDto {}

            public record Address(String street, String city) {}

            public record Vendor(String name, Address address) {}

            public record VendorDto(String name, String street, String city) {}
          }
          """);

  private static final JavaFileObject CUSTOMER_MAPPING =
      customerMapping("com.upstream", "CustomerMapping");

  /** A projection of the same domain type: registered, but not parse-capable. */
  private static final JavaFileObject CARD_PROJECTION =
      JavaFileObjects.forSourceString(
          "com.upstream.CustomerCardMapping",
          """
          package com.upstream;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface CustomerCardMapping
              extends MappingSpec<Upstream.Customer, Upstream.CustomerCard> {}
          """);

  private static final JavaFileObject PAGE_MAPPING =
      JavaFileObjects.forSourceString(
          "com.upstream.PageMapping",
          """
          package com.upstream;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface PageMapping<T> extends MappingSpec<Upstream.Page<T>, Upstream.PageDto<T>> {}
          """);

  private static final JavaFileObject BATCH_MAPPING =
      JavaFileObjects.forSourceString(
          "com.upstream.BatchMapping",
          """
          package com.upstream;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          @GenerateMapping
          public interface BatchMapping<T, TDto>
              extends MappingSpec<Upstream.Batch<T>, Upstream.BatchDto<TDto>> {
            ValidatedPrism<TDto, T> items();
          }
          """);

  private static final JavaFileObject CIRCLE_MAPPING =
      JavaFileObjects.forSourceString(
          "com.upstream.CircleMapping",
          """
          package com.upstream;

          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface CircleMapping
              extends MappingSpec<Upstream.Circle, Upstream.CircleDto> {}
          """);

  /** A spec spreading a nested component across a flat wire: parse-capable, so nestable. */
  private static final JavaFileObject VENDOR_MAPPING =
      JavaFileObjects.forSourceString(
          "com.upstream.VendorMapping",
          """
          package com.upstream;

          import org.higherkindedj.optics.annotations.Flatten;
          import org.higherkindedj.optics.annotations.GenerateMapping;
          import org.higherkindedj.optics.annotations.MappingSpec;

          @GenerateMapping
          public interface VendorMapping extends MappingSpec<Upstream.Vendor, Upstream.VendorDto> {
            @Flatten
            Upstream.Address address();
          }
          """);

  private static final List<JavaFileObject> UPSTREAM =
      List.of(
          UPSTREAM_TYPES,
          CUSTOMER_MAPPING,
          CARD_PROJECTION,
          PAGE_MAPPING,
          BATCH_MAPPING,
          CIRCLE_MAPPING,
          VENDOR_MAPPING);

  /** The downstream module's records, each nesting one of the upstream pairs. */
  private static final JavaFileObject DOWNSTREAM_TYPES =
      JavaFileObjects.forSourceString(
          "com.downstream.Downstream",
          """
          package com.downstream;

          import com.upstream.Upstream;

          public final class Downstream {
            public record Invoice(String id, Upstream.Customer customer) {}

            public record InvoiceDto(String id, Upstream.CustomerDto customer) {}

            public record Tagged(Upstream.Page<String> tags) {}

            public record TaggedDto(Upstream.PageDto<String> tags) {}

            public record Roster(Upstream.Batch<Upstream.Customer> people) {}

            public record RosterDto(Upstream.BatchDto<Upstream.CustomerDto> people) {}

            public record Card(String id, Upstream.Customer customer) {}

            public record CardDto(String id, Upstream.CustomerCard customer) {}

            public record Profile(String name, Upstream.Customer customer) {}

            public record Named(String name) {}

            public record Wrapper(Upstream.CustomerDto customer) {}

            public record Purchase(String id, Upstream.Vendor vendor) {}

            public record PurchaseDto(String id, Upstream.VendorDto vendor) {}
          }
          """);

  private static final JavaFileObject INVOICE_MAPPING =
      downstreamSpec("InvoiceMapping", "Downstream.Invoice", "Downstream.InvoiceDto");

  private static final JavaFileObject TAGGED_MAPPING =
      downstreamSpec("TaggedMapping", "Downstream.Tagged", "Downstream.TaggedDto");

  private static final JavaFileObject ROSTER_MAPPING =
      downstreamSpec("RosterMapping", "Downstream.Roster", "Downstream.RosterDto");

  private static final JavaFileObject CARD_MAPPING =
      downstreamSpec("CardMapping", "Downstream.Card", "Downstream.CardDto");

  private static final JavaFileObject SHAPE_MAPPING =
      downstreamSpec("ShapeMapping", "Upstream.Shape", "Upstream.ShapeDto");

  private static final JavaFileObject PURCHASE_MAPPING =
      downstreamSpec("PurchaseMapping", "Downstream.Purchase", "Downstream.PurchaseDto");

  private static final JavaFileObject PROFILE_ASSEMBLY =
      JavaFileObjects.forSourceString(
          "com.downstream.ProfileAssembly",
          """
          package com.downstream;

          import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
          import org.higherkindedj.hkt.validated.FieldError;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.annotations.GenerateMerge;

          @GenerateMerge
          public interface ProfileAssembly {
            Validated<NonEmptyList<FieldError>, Downstream.Profile> assemble(
                Downstream.Named named, Downstream.Wrapper wrapper);
          }
          """);

  @TempDir Path tmp;

  /** A concrete spec for the customer pair, in any package and under any name. */
  private static JavaFileObject customerMapping(String packageName, String name) {
    return JavaFileObjects.forSourceString(
        packageName + "." + name,
        "package "
            + packageName
            + ";\n\n"
            + """
            import com.upstream.Upstream;
            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface %s extends MappingSpec<Upstream.Customer, Upstream.CustomerDto> {
              default ValidatedPrism<String, Upstream.EmailAddress> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new Upstream.EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    Upstream.EmailAddress::value);
              }
            }
            """
                .formatted(name));
  }

  private static JavaFileObject downstreamSpec(String name, String domain, String wire) {
    return JavaFileObjects.forSourceString(
        "com.downstream." + name,
        """
        package com.downstream;

        import com.upstream.Upstream;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MappingSpec;

        @GenerateMapping
        public interface %s extends MappingSpec<%s, %s> {}
        """
            .formatted(name, domain, wire));
  }

  /** Both processors, over the test classpath plus the given class directories. */
  private static Compiler compiler(Path... classDirs) {
    List<File> classpath = new ArrayList<>(TEST_CLASSPATH);
    for (Path dir : classDirs) {
      classpath.add(dir.toFile());
    }
    return javac()
        .withProcessors(new MappingProcessor(), new MergeProcessor())
        .withClasspath(classpath);
  }

  /**
   * Compiles one module's sources and lays its class files out under {@code dir}, the shape a jar
   * dependency has on a downstream classpath. {@code deps} are the directories it compiles against.
   */
  private Path module(String name, List<Path> deps, List<JavaFileObject> sources)
      throws IOException {
    Compilation compilation = compiler(deps.toArray(Path[]::new)).compile(sources);
    assertThat(compilation).succeeded();
    return classDirectory(name, compilation);
  }

  private Path classDirectory(String name, Compilation compilation) throws IOException {
    Path dir = tmp.resolve(name);
    for (JavaFileObject file : compilation.generatedFiles()) {
      if (file.getKind() != JavaFileObject.Kind.CLASS) {
        continue;
      }
      // /CLASS_OUTPUT/com/upstream/Upstream.class -> com/upstream/Upstream.class
      String path = file.getName();
      String marker = StandardLocation.CLASS_OUTPUT.getName() + "/";
      Path target = dir.resolve(path.substring(path.indexOf(marker) + marker.length()));
      Files.createDirectories(target.getParent());
      try (InputStream in = file.openInputStream()) {
        Files.copy(in, target);
      }
    }
    return dir;
  }

  private Path upstream() throws IOException {
    return module("upstream", List.of(), UPSTREAM);
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    Optional<JavaFileObject> file =
        compilation.generatedFile(
            StandardLocation.SOURCE_OUTPUT, qualifiedName.replace('.', '/') + ".java");
    Assertions.assertThat(file).isPresent();
    try {
      return file.get().getCharContent(true).toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  private static List<String> indexEntries(Compilation compilation) {
    return compilation.generatedFiles().stream()
        .map(JavaFileObject::getName)
        .filter(name -> name.contains(INDEX_PACKAGE_DIR))
        .toList();
  }

  @Nested
  @DisplayName("Resolution through the classpath index")
  class Resolution {

    @Test
    @DisplayName("a concrete pair nests through the dependency's Impl, and locates its failures")
    void concreteSpecNestsFromTheClasspath() throws Exception {
      Path upstream = upstream();
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.InvoiceMappingImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()");

      Path downstream = classDirectory("downstream", compilation);
      try (URLClassLoader loader =
          new URLClassLoader(
              new URL[] {upstream.toUri().toURL(), downstream.toUri().toURL()},
              getClass().getClassLoader())) {
        Object impl =
            loader.loadClass("com.downstream.InvoiceMappingImpl").getField("INSTANCE").get(null);
        Class<?> customerDto = loader.loadClass("com.upstream.Upstream$CustomerDto");
        Object customer =
            customerDto.getConstructor(String.class, String.class).newInstance("Ann", "nope");
        Object wire =
            loader
                .loadClass("com.downstream.Downstream$InvoiceDto")
                .getConstructor(String.class, customerDto)
                .newInstance("inv-1", customer);
        Validated<?, ?> parsed = (Validated<?, ?>) invoke(impl, "parse", wire);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(String.valueOf(parsed.getError()))
            .contains("customer.email: not an email address");
      }
    }

    @Test
    @DisplayName("a spec spreading a flattened component is read as parse-capable, and nests")
    void flatteningSpecNestsFromTheClasspath() throws Exception {
      Path upstream = upstream();
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, PURCHASE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.PurchaseMappingImpl"))
          .contains("VendorMappingImpl.INSTANCE.asValidatedPrism()");

      Path downstream = classDirectory("downstream", compilation);
      try (URLClassLoader loader =
          new URLClassLoader(
              new URL[] {upstream.toUri().toURL(), downstream.toUri().toURL()},
              getClass().getClassLoader())) {
        Object impl =
            loader.loadClass("com.downstream.PurchaseMappingImpl").getField("INSTANCE").get(null);
        Class<?> vendorDto = loader.loadClass("com.upstream.Upstream$VendorDto");
        Object vendor =
            vendorDto
                .getConstructor(String.class, String.class, String.class)
                .newInstance("Acme", null, "Leeds");
        Object wire =
            loader
                .loadClass("com.downstream.Downstream$PurchaseDto")
                .getConstructor(String.class, vendorDto)
                .newInstance("po-1", vendor);
        Validated<?, ?> parsed = (Validated<?, ?>) invoke(impl, "parse", wire);
        Assertions.assertThat(parsed.isInvalid()).isTrue();
        Assertions.assertThat(String.valueOf(parsed.getError()))
            .contains("vendor.address.street: must not be null");
      }
    }

    @Test
    @DisplayName("a threaded generic spec instantiates from the dependency")
    void threadedSpecInstantiatesFromTheClasspath() throws Exception {
      Compilation compilation = compiler(upstream()).compile(DOWNSTREAM_TYPES, TAGGED_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.TaggedMappingImpl"))
          .contains("PageMappingImpl.<String>instance().asValidatedPrism()");
    }

    @Test
    @DisplayName("an element-mapped spec composes with another dependency spec as its leaf")
    void elementMappedSpecComposesFromTheClasspath() throws Exception {
      Compilation compilation = compiler(upstream()).compile(DOWNSTREAM_TYPES, ROSTER_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.RosterMappingImpl"))
          .contains(
              "BatchMappingImpl.of(CustomerMappingImpl.INSTANCE.asValidatedPrism())"
                  + ".asValidatedPrism()");
    }

    @Test
    @DisplayName("sealed dispatch delegates to subtype specs in the dependency")
    void sealedDispatchDelegatesToClasspathSubtypeSpecs() throws Exception {
      Compilation compilation = compiler(upstream()).compile(SHAPE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ShapeMappingImpl"))
          .contains("CircleMappingImpl.INSTANCE");
    }

    @Test
    @DisplayName("a merge fill resolves through the dependency's spec")
    void mergeFillResolvesThroughTheClasspath() throws Exception {
      Compilation compilation = compiler(upstream()).compile(DOWNSTREAM_TYPES, PROFILE_ASSEMBLY);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ProfileAssemblyImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()");
    }

    @Test
    @DisplayName("a projection in the dependency is named in the no-usable-source hint")
    void projectionOnTheClasspathIsNamedInTheHint() throws Exception {
      Compilation compilation = compiler(upstream()).compile(DOWNSTREAM_TYPES, CARD_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'com.upstream.CustomerCardMapping (classpath)' maps this pair but is a projection"
                  + " (no parse), so it cannot be nested");
      assertThat(compilation)
          .hadErrorContaining(
              "here or in a dependency compiled with hkj-processor on its processor path");
    }
  }

  @Nested
  @DisplayName("Precedence and provenance")
  class Precedence {

    @Test
    @DisplayName("two dependencies mapping one pair are ambiguous, named with their provenance")
    void twoClasspathSpecsForOnePairAreAmbiguous() throws Exception {
      Path upstream = upstream();
      Path other =
          module(
              "other", List.of(upstream), List.of(customerMapping("com.other", "CustomerMapping")));
      Compilation compilation =
          compiler(upstream, other).compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "field 'customer' matches more than one mapping spec: [com.other.CustomerMapping"
                  + " (classpath), com.upstream.CustomerMapping (classpath)]");
      assertThat(compilation)
          .hadErrorContaining(
              "Add a leaf method 'customer()' delegating to the spec you want, or declare a"
                  + " @GenerateMapping spec for the pair in this compilation, which takes"
                  + " precedence over a dependency's.");
    }

    @Test
    @DisplayName(
        "two dependencies mapping one sealed subtype pair offer the spec-of-your-own remedy")
    void twoClasspathSubtypeSpecsAreAmbiguous() throws Exception {
      Path upstream = upstream();
      JavaFileObject otherCircle =
          JavaFileObjects.forSourceString(
              "com.other.CircleMapping",
              """
              package com.other;

              import com.upstream.Upstream;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CircleMapping
                  extends MappingSpec<Upstream.Circle, Upstream.CircleDto> {}
              """);
      Path other = module("other", List.of(upstream), List.of(otherCircle));
      Compilation compilation = compiler(upstream, other).compile(SHAPE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "permitted subtype 'com.upstream.Upstream.Circle' of 'Shape' matches more than one"
                  + " mapping spec: [com.other.CircleMapping (classpath), com.upstream.CircleMapping"
                  + " (classpath)]");
      assertThat(compilation)
          .hadErrorContaining(
              "Declare a @GenerateMapping spec for this subtype pair in this compilation, which"
                  + " takes precedence over a dependency's, or drop one of the two dependencies.");
    }

    @Test
    @DisplayName("two dependencies mapping one pair make a merge fill ambiguous the same way")
    void twoClasspathSpecsMakeAMergeFillAmbiguous() throws Exception {
      Path upstream = upstream();
      Path other =
          module(
              "other", List.of(upstream), List.of(customerMapping("com.other", "CustomerMapping")));
      Compilation compilation =
          compiler(upstream, other).compile(DOWNSTREAM_TYPES, PROFILE_ASSEMBLY);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "target component 'customer' matches more than one mapping spec:"
                  + " [com.other.CustomerMapping (classpath), com.upstream.CustomerMapping"
                  + " (classpath)]");
      assertThat(compilation)
          .hadErrorContaining(
              "declare a @GenerateMapping spec for the pair in this compilation, which takes"
                  + " precedence over a dependency's.");
    }

    @Test
    @DisplayName("a spec in this compilation shadows the dependency's, with a note")
    void localSpecShadowsTheClasspath() throws Exception {
      Compilation compilation =
          compiler(upstream())
              .compile(
                  DOWNSTREAM_TYPES,
                  customerMapping("com.downstream", "CustomerMapping"),
                  INVOICE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.InvoiceMappingImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()")
          .doesNotContain("com.upstream.CustomerMappingImpl");
      assertThat(compilation)
          .hadNoteContaining(
              "field 'customer' resolves through 'CustomerMapping' in this compilation, not"
                  + " through [com.upstream.CustomerMapping (classpath)]");
      assertThat(compilation)
          .hadNoteContaining(
              "Keep it, or delegate explicitly with a leaf 'customer()' if the classpath spec is"
                  + " the one meant");
    }

    @Test
    @DisplayName("sealed dispatch prefers a subtype spec in this compilation, with a note")
    void localSubtypeSpecShadowsTheClasspath() throws Exception {
      JavaFileObject localCircle =
          JavaFileObjects.forSourceString(
              "com.downstream.CircleMapping",
              """
              package com.downstream;

              import com.upstream.Upstream;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface CircleMapping
                  extends MappingSpec<Upstream.Circle, Upstream.CircleDto> {}
              """);
      Compilation compilation = compiler(upstream()).compile(localCircle, SHAPE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.ShapeMappingImpl"))
          .doesNotContain("com.upstream.CircleMappingImpl");
      assertThat(compilation)
          .hadNoteContaining(
              "permitted subtype 'com.upstream.Upstream.Circle' resolves through 'CircleMapping'"
                  + " in this compilation, not through [com.upstream.CircleMapping (classpath)]");
      assertThat(compilation)
          .hadNoteContaining(
              "Keep it, or remove the spec in this compilation if the classpath spec is the one"
                  + " meant");
    }

    @Test
    @DisplayName("a merge fill prefers a spec in this compilation, with a note")
    void localSpecShadowsTheClasspathForMergeFills() throws Exception {
      Compilation compilation =
          compiler(upstream())
              .compile(
                  DOWNSTREAM_TYPES,
                  customerMapping("com.downstream", "CustomerMapping"),
                  PROFILE_ASSEMBLY);
      assertThat(compilation).succeeded();
      assertThat(compilation)
          .hadNoteContaining(
              "target component 'customer' resolves through 'CustomerMapping' in this"
                  + " compilation, not through [com.upstream.CustomerMapping (classpath)]");
    }

    @Test
    @DisplayName("an index entry for a spec this compilation recompiles is the previous build's")
    void recompiledSpecIsRegisteredOnce() throws Exception {
      Compilation compilation =
          compiler(upstream()).compile(DOWNSTREAM_TYPES, CUSTOMER_MAPPING, INVOICE_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.InvoiceMappingImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()");
      Assertions.assertThat(compilation.notes())
          .noneMatch(note -> note.getMessage(null).contains("resolves through"));
    }
  }

  @Nested
  @DisplayName("Index entries")
  class Entries {

    @Test
    @DisplayName("every spec with an Impl writes one entry naming it, a nested spec included")
    void everyMappableSpecWritesAnEntry() {
      JavaFileObject nested =
          JavaFileObjects.forSourceString(
              "com.upstream.Holder",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class Holder {
                public record Tag(String value) {}

                public record TagDto(String value) {}

                @GenerateMapping
                public interface TagMapping extends MappingSpec<Tag, TagDto> {}
              }
              """);
      Compilation compilation = compiler().compile(UPSTREAM_TYPES, CUSTOMER_MAPPING, nested);
      assertThat(compilation).succeeded();
      Assertions.assertThat(
              generatedSource(
                  compilation, "org.higherkindedj.mapping.index.com$upstream$CustomerMapping"))
          .contains("package org.higherkindedj.mapping.index;")
          .contains("@Generated")
          .contains("@MappingIndexEntry(")
          .contains("spec = \"com.upstream.CustomerMapping\"")
          .contains("public final class com$upstream$CustomerMapping {")
          .contains("private com$upstream$CustomerMapping() {")
          .contains("Index entry for {@code com.upstream.CustomerMapping}");
      Assertions.assertThat(
              generatedSource(
                  compilation, "org.higherkindedj.mapping.index.com$upstream$Holder$TagMapping"))
          .contains("spec = \"com.upstream.Holder.TagMapping\"");
    }

    @Test
    @DisplayName("a projection, a generic spec and a sealed spec write entries too")
    void everyTierWritesAnEntry() {
      Compilation compilation = compiler().compile(UPSTREAM);
      assertThat(compilation).succeeded();
      Assertions.assertThat(indexEntries(compilation))
          .anyMatch(
              name -> name.endsWith(INDEX_PACKAGE_DIR + "com$upstream$CustomerCardMapping.java"))
          .anyMatch(name -> name.endsWith(INDEX_PACKAGE_DIR + "com$upstream$PageMapping.java"))
          .anyMatch(name -> name.endsWith(INDEX_PACKAGE_DIR + "com$upstream$BatchMapping.java"))
          .anyMatch(name -> name.endsWith(INDEX_PACKAGE_DIR + "com$upstream$CircleMapping.java"));
    }

    @Test
    @DisplayName("an UpdateSpec, which nothing can nest, writes no entry")
    void anUpdateSpecWritesNoEntry() {
      JavaFileObject patchDto =
          JavaFileObjects.forSourceString(
              "com.upstream.NamePatchDto",
              """
              package com.upstream;

              public class NamePatchDto {
                private String name;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      JavaFileObject patch =
          JavaFileObjects.forSourceString(
              "com.upstream.CardPatchMapping",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              @GenerateMapping
              public interface CardPatchMapping extends UpdateSpec<Upstream.CustomerCard, NamePatchDto> {}
              """);
      Compilation compilation = compiler().compile(UPSTREAM_TYPES, patchDto, patch);
      assertThat(compilation).succeeded();
      assertThat(compilation).generatedSourceFile("com.upstream.CardPatchMappingImpl");
      Assertions.assertThat(indexEntries(compilation)).isEmpty();
    }

    @Test
    @DisplayName("a class in the index package without the marker is passed over")
    void aClassWithoutTheMarkerIsIgnored() throws Exception {
      JavaFileObject stray =
          JavaFileObjects.forSourceString(
              "org.higherkindedj.mapping.index.Stray",
              """
              package org.higherkindedj.mapping.index;

              public final class Stray {}
              """);
      Path upstream =
          module("upstream", List.of(), List.of(UPSTREAM_TYPES, CUSTOMER_MAPPING, stray));
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).succeeded();
    }

    @Test
    @DisplayName("an entry whose spec is no longer on the classpath is passed over")
    void anEntryWhoseSpecIsGoneIsIgnored() throws Exception {
      Path upstream = upstream();
      Files.delete(upstream.resolve("com/upstream/CustomerMapping.class"));
      Files.delete(upstream.resolve("com/upstream/CustomerMappingImpl.class"));
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'InvoiceDto.customer' has no usable source");
      assertThat(compilation).hadWarningCount(0);
    }

    @Test
    @DisplayName("an entry whose spec has lost its Impl is never chosen; a use site is told why")
    void anEntryWhoseImplIsGoneIsExplainedAtTheUseSite() throws Exception {
      Path upstream = upstream();
      Files.delete(upstream.resolve("com/upstream/CustomerMappingImpl.class"));
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation).hadWarningCount(0);
      assertThat(compilation)
          .hadErrorContaining("target field 'InvoiceDto.customer' has no usable source");
      assertThat(compilation)
          .hadErrorContaining(
              "'com.upstream.CustomerMapping (classpath)' maps this pair, but its generated"
                  + " 'com.upstream.CustomerMappingImpl' is missing from the classpath");
      assertThat(compilation).hadErrorContaining("rebuild that dependency from clean");
    }

    @Test
    @DisplayName("a merge fill needing a spec whose Impl is gone is told the same")
    void aMergeFillNeedingAnImplLessSpecIsExplained() throws Exception {
      Path upstream = upstream();
      Files.delete(upstream.resolve("com/upstream/CustomerMappingImpl.class"));
      Compilation compilation = compiler(upstream).compile(DOWNSTREAM_TYPES, PROFILE_ASSEMBLY);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target component 'Profile.customer' has no usable fill");
      assertThat(compilation)
          .hadErrorContaining(
              "'com.upstream.CustomerMapping (classpath)' maps this pair, but its generated"
                  + " 'com.upstream.CustomerMappingImpl' is missing from the classpath");
    }

    @Test
    @DisplayName("sealed dispatch needing a subtype spec whose Impl is gone is told the same")
    void sealedDispatchNeedingAnImplLessSpecIsExplained() throws Exception {
      Path upstream = upstream();
      Files.delete(upstream.resolve("com/upstream/CircleMappingImpl.class"));
      Compilation compilation = compiler(upstream).compile(SHAPE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("permitted subtype 'com.upstream.Upstream.Circle' of 'Shape'");
      assertThat(compilation)
          .hadErrorContaining(
              "'com.upstream.CircleMapping (classpath)' maps this pair, but its generated"
                  + " 'com.upstream.CircleMappingImpl' is missing from the classpath");
    }
  }

  @Nested
  @DisplayName("Boundaries of the index")
  class Boundaries {

    /**
     * Generates a nesting spec in the first round, so that the mapping processor meets it in the
     * second, when the index entries written in the first are already in the package listing.
     */
    private static final class LateSpecGenerator extends AbstractProcessor {
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
        boolean firstRound =
            roundEnv.getRootElements().stream()
                .anyMatch(root -> root.getSimpleName().contentEquals("Downstream"));
        if (firstRound) {
          try (Writer out =
              processingEnv
                  .getFiler()
                  .createSourceFile("com.downstream.LateInvoiceMapping")
                  .openWriter()) {
            out.write(
                """
                package com.downstream;

                import org.higherkindedj.optics.annotations.GenerateMapping;
                import org.higherkindedj.optics.annotations.MappingSpec;

                @GenerateMapping
                public interface LateInvoiceMapping
                    extends MappingSpec<Downstream.Invoice, Downstream.InvoiceDto> {}
                """);
          } catch (IOException e) {
            throw new UncheckedIOException(e);
          }
        }
        return false;
      }
    }

    /** Packs a class directory into a jar, the shape a dependency has on a module path. */
    private Path jar(String name, Path classes) throws IOException {
      Path file = tmp.resolve(name + ".jar");
      try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file));
          Stream<Path> paths = Files.walk(classes)) {
        for (Path path : paths.filter(Files::isRegularFile).toList()) {
          out.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
          Files.copy(path, out);
          out.closeEntry();
        }
      }
      return file;
    }

    @Test
    @DisplayName("a spec of this compilation met through a first-round entry is still local")
    void anEntryWrittenInAnEarlierRoundNamesALocalSpec() throws Exception {
      // The generator goes first: javac skips a wildcard processor in a round whose annotations
      // an earlier processor has already claimed, and the mapping processor claims its own.
      Compilation compilation =
          compiler(upstream())
              .withProcessors(new LateSpecGenerator(), new MappingProcessor(), new MergeProcessor())
              .compile(DOWNSTREAM_TYPES, customerMapping("com.downstream", "CustomerMapping"));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.downstream.LateInvoiceMappingImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism()")
          .doesNotContain("com.upstream.CustomerMappingImpl");
      assertThat(compilation)
          .hadNoteContaining(
              "field 'customer' resolves through 'CustomerMapping' in this compilation, not"
                  + " through [com.upstream.CustomerMapping (classpath)]");
    }

    @Test
    @DisplayName(
        "a dependency on the module path owns the index package; nothing is written or read")
    void aDependencyOnTheModulePathTakesTheIndexOutOfUse() throws Exception {
      Path jar = jar("upstream", upstream());
      Compilation compilation =
          compiler()
              .withOptions("--module-path", jar.toString(), "--add-modules", "ALL-MODULE-PATH")
              .compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target field 'InvoiceDto.customer' has no usable source");
      assertThat(compilation)
          .hadErrorContaining(
              "here (the index package belongs to module 'upstream' on the module path, so no"
                  + " dependency's spec is consulted; put spec-carrying dependencies on the"
                  + " classpath)");
      // An entry written into the module's package would have drawn javac's own refusal.
      Assertions.assertThat(compilation.errors())
          .noneMatch(error -> error.getMessage(null).contains("exists in another module"));
    }

    @Test
    @DisplayName("the option turns the index off: no entry is written and none is read")
    void theOptionTurnsTheIndexOff() throws Exception {
      Compilation upstream = compiler().withOptions("-Ahkj.mapping.index=false").compile(UPSTREAM);
      assertThat(upstream).succeeded();
      assertThat(upstream).generatedSourceFile("com.upstream.CustomerMappingImpl");
      Assertions.assertThat(indexEntries(upstream)).isEmpty();

      Compilation downstream =
          compiler(upstream())
              .withOptions("-Ahkj.mapping.index=false")
              .compile(DOWNSTREAM_TYPES, INVOICE_MAPPING);
      assertThat(downstream).failed();
      assertThat(downstream)
          .hadErrorContaining("target field 'InvoiceDto.customer' has no usable source");
      assertThat(downstream)
          .hadErrorContaining(
              "here (the classpath index is off for this compilation: hkj.mapping.index=false)");
    }

    @Test
    @DisplayName("a spec named with a '$' can share an entry name; the entry write says so")
    void aDollarNamedSpecCollidingOnAnEntryIsReported() {
      Compilation compilation =
          compiler()
              .compile(
                  UPSTREAM_TYPES, customerMapping("com", "z$M"), customerMapping("com.z", "M"));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("could not write the index entry for '");
      assertThat(compilation)
          .hadErrorContaining("'org.higherkindedj.mapping.index.com$z$M' already exists");
      assertThat(compilation).hadErrorContaining("Rename the spec whose name contains '$'");
    }
  }

  @Nested
  @DisplayName("Named modules")
  class NamedModules {

    /** What a file-based javac run reports: whether it succeeded, its diagnostics, its output. */
    private record ModuleCompilation(
        boolean succeeded, List<Diagnostic<? extends JavaFileObject>> diagnostics, Path out) {

      List<String> messages(Diagnostic.Kind kind) {
        return diagnostics.stream()
            .filter(d -> d.getKind() == kind)
            .map(d -> d.getMessage(null))
            .toList();
      }
    }

    /**
     * Compiles the sources as the named module {@code module}, over real files: compile-testing's
     * in-memory file manager cannot place a {@code module-info}. The module reads the classpath
     * (the test's, plus {@code classDirs}) through {@code --add-reads}, as its {@code requires}
     * would read a module path.
     */
    private ModuleCompilation namedModule(
        String module, List<Path> classDirs, JavaFileObject... sources) throws IOException {
      Path src = tmp.resolve(module + "-src");
      Path out = Files.createDirectories(tmp.resolve(module + "-out"));
      List<Path> files = new ArrayList<>();
      files.add(write(src, "module-info.java", "module " + module + " {}\n"));
      for (JavaFileObject source : sources) {
        // JavaFileObjects.forSourceString names the file /com/upstream/Upstream.java.
        files.add(
            write(src, source.getName().substring(1), source.getCharContent(true).toString()));
      }
      List<File> classpath = new ArrayList<>(TEST_CLASSPATH);
      classDirs.forEach(dir -> classpath.add(dir.toFile()));
      List<String> options =
          List.of(
              "-d",
              out.toString(),
              "--add-reads",
              module + "=ALL-UNNAMED",
              "-classpath",
              classpath.stream()
                  .map(File::toString)
                  .collect(Collectors.joining(File.pathSeparator)));
      JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
      DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
      try (StandardJavaFileManager fileManager =
          javac.getStandardFileManager(diagnostics, null, null)) {
        JavaCompiler.CompilationTask task =
            javac.getTask(
                null,
                fileManager,
                diagnostics,
                options,
                null,
                fileManager.getJavaFileObjectsFromPaths(files));
        task.setProcessors(List.of(new MappingProcessor(), new MergeProcessor()));
        boolean succeeded = task.call();
        return new ModuleCompilation(succeeded, diagnostics.getDiagnostics(), out);
      }
    }

    private static Path write(Path root, String relative, String content) throws IOException {
      Path file = root.resolve(relative);
      Files.createDirectories(file.getParent());
      return Files.writeString(file, content);
    }

    @Test
    @DisplayName("a spec in a named module writes no entry, and its build stays quiet")
    void aSpecInANamedModuleWritesNoEntry() throws Exception {
      ModuleCompilation compilation =
          namedModule("com.upstream", List.of(), UPSTREAM_TYPES, CUSTOMER_MAPPING, CIRCLE_MAPPING);
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.ERROR)).isEmpty();
      Assertions.assertThat(compilation.succeeded()).isTrue();
      Assertions.assertThat(compilation.out().resolve("com/upstream/CustomerMappingImpl.class"))
          .exists();
      Assertions.assertThat(compilation.out().resolve(INDEX_PACKAGE_DIR)).doesNotExist();
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.NOTE)).isEmpty();
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.WARNING)).isEmpty();
    }

    @Test
    @DisplayName("a named module reads no index; the failing use site says so")
    void aNamedModuleReadsNoIndex() throws Exception {
      ModuleCompilation compilation =
          namedModule("com.downstream", List.of(upstream()), DOWNSTREAM_TYPES, INVOICE_MAPPING);
      Assertions.assertThat(compilation.succeeded()).isFalse();
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.ERROR))
          .anySatisfy(
              error ->
                  Assertions.assertThat(error)
                      .contains("target field 'InvoiceDto.customer' has no usable source")
                      .contains(
                          "declare a @GenerateMapping spec mapping those records, in this module"
                              + " (a named module reads no classpath index, so a spec in a"
                              + " dependency is not consulted; not supported yet)"));
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.NOTE)).isEmpty();
    }

    @Test
    @DisplayName("sealed dispatch and merge fills inside a named module say the same")
    void sealedAndMergeInsideANamedModuleSayTheSame() throws Exception {
      ModuleCompilation compilation =
          namedModule(
              "com.downstream",
              List.of(upstream()),
              DOWNSTREAM_TYPES,
              SHAPE_MAPPING,
              PROFILE_ASSEMBLY);
      Assertions.assertThat(compilation.succeeded()).isFalse();
      Assertions.assertThat(compilation.messages(Diagnostic.Kind.ERROR))
          .anySatisfy(
              error ->
                  Assertions.assertThat(error)
                      .contains("permitted subtype 'com.upstream.Upstream.Circle' of 'Shape'")
                      .contains("in this module (a named module reads no classpath index"))
          .anySatisfy(
              error ->
                  Assertions.assertThat(error)
                      .contains("target component 'Profile.customer' has no usable fill")
                      .contains("in this module (a named module reads no classpath index"));
    }
  }
}

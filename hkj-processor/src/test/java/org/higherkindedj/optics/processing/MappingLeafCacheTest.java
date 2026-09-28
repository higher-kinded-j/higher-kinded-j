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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A generated Impl reads each {@code default} leaf once, on first use, and keeps it: a leaf that
 * constructs its codec would otherwise construct it on every {@code build} and {@code parse}. The
 * read is lazy, so the two shapes that reach the Impl while it is still being set up, a recursive
 * spec and a spec holding its own {@code MAPPER}, see what they saw before.
 */
@DisplayName("@GenerateMapping reads each default leaf once, on first use")
class MappingLeafCacheTest {

  private static final JavaFileObject EMAIL =
      JavaFileObjects.forSourceString(
          "com.example.EmailAddress",
          """
          package com.example;

          public record EmailAddress(String value) {}
          """);

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

  /** The leaf every spec here shares, counting how often an Impl asks for it. */
  private static final JavaFileObject COUNTED =
      JavaFileObjects.forSourceString(
          "com.example.Counted",
          """
          package com.example;

          import java.util.concurrent.atomic.AtomicInteger;
          import org.higherkindedj.hkt.validated.Validated;
          import org.higherkindedj.optics.validated.ValidatedPrism;

          public interface Counted {
            AtomicInteger READS = new AtomicInteger();

            default ValidatedPrism<String, EmailAddress> email() {
              READS.incrementAndGet();
              return ValidatedPrism.of(
                  raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);
            }
          }
          """);

  private static Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
  }

  private static String generatedSource(Compilation compilation, String qualifiedName) {
    try {
      return compilation
          .generatedSourceFile(qualifiedName)
          .orElseThrow(() -> new AssertionError(qualifiedName + " was not generated"))
          .getCharContent(false)
          .toString();
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }

  @SuppressWarnings("unchecked") // the generated parse's result, read reflectively
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
  }

  @Test
  @DisplayName("a leaf is read once per Impl, however many builds and parses reach it")
  void aLeafIsReadOncePerImpl() throws Exception {
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.CustomerMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CustomerMapping extends Counted, MappingSpec<Customer, CustomerDto> {}
            """);

    var result =
        RuntimeCompilationHelper.compileWith(
            new MappingProcessor(), EMAIL, CUSTOMER, CUSTOMER_DTO, COUNTED, spec);
    Object impl = result.instance("com.example.CustomerMappingImpl");
    Object ada =
        result
            .loadClass("com.example.Customer")
            .getDeclaredConstructors()[0]
            .newInstance(
                "Ada",
                result
                    .loadClass("com.example.EmailAddress")
                    .getDeclaredConstructors()[0]
                    .newInstance("ada@example.org"));

    Object wire = invoke(impl, "build", ada);
    invoke(impl, "build", ada);
    assertThatValidated(parse(impl, wire)).hasValue(ada);
    assertThatValidated(parse(impl, wire)).hasValue(ada);

    AtomicInteger reads =
        (AtomicInteger) result.loadClass("com.example.Counted").getField("READS").get(null);
    Assertions.assertThat(reads.get()).isOne();
  }

  @Test
  @DisplayName("a PATCH Impl reads its leaf once too")
  void aPatchImplReadsItsLeafOnce() throws Exception {
    JavaFileObject patch =
        JavaFileObjects.forSourceString(
            "com.example.CustomerPatch",
            """
            package com.example;

            public class CustomerPatch {
              private String email;

              public String getEmail() {
                return email;
              }

              public void setEmail(String email) {
                this.email = email;
              }
            }
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.CustomerPatchMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.UpdateSpec;

            @GenerateMapping
            public interface CustomerPatchMapping
                extends Counted, UpdateSpec<Customer, CustomerPatch> {}
            """);

    var result =
        RuntimeCompilationHelper.compileWith(
            new MappingProcessor(), EMAIL, CUSTOMER, COUNTED, patch, spec);
    Object impl = result.instance("com.example.CustomerPatchMappingImpl");
    Object request = result.loadClass("com.example.CustomerPatch").getConstructor().newInstance();
    invoke(request, "setEmail", "countess@example.org");

    invoke(impl, "updateFrom", request);
    invoke(impl, "updateFrom", request);

    AtomicInteger reads =
        (AtomicInteger) result.loadClass("com.example.Counted").getField("READS").get(null);
    Assertions.assertThat(reads.get()).isOne();
  }

  @Test
  @DisplayName("a leaf that reaches its own Impl is read after INSTANCE is set, not while it is")
  void aSelfRecursiveLeafSeesItsInstance() throws Exception {
    JavaFileObject tree =
        JavaFileObjects.forSourceString(
            "com.example.Tree",
            """
            package com.example;

            import java.util.List;

            public record Tree(String value, List<Tree> children) {}
            """);
    JavaFileObject treeDto =
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

            import java.util.Objects;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface TreeMapping extends MappingSpec<Tree, TreeDto> {
              default ValidatedPrism<String, String> value() {
                // Null while the Impl is being constructed, so a leaf read then would throw.
                Objects.requireNonNull(TreeMappingImpl.INSTANCE, "read before INSTANCE was set");
                return ValidatedPrism.of(raw -> Validated.validNel(raw), raw -> raw);
              }
            }
            """);

    var result = RuntimeCompilationHelper.compileWith(new MappingProcessor(), tree, treeDto, spec);
    Object impl = result.instance("com.example.TreeMappingImpl");
    var constructor = result.loadClass("com.example.Tree").getDeclaredConstructors()[0];
    Object root =
        constructor.newInstance("root", List.of(constructor.newInstance("leaf", List.of())));

    assertThatValidated(parse(impl, invoke(impl, "build", root))).hasValue(root);
  }

  @Test
  @DisplayName(
      "with a MAPPER constant on the spec, a leaf reading a later constant sees it set on first"
          + " use")
  void aLeafReadingALaterSpecConstantSeesItSet() throws Exception {
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.ContactMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface ContactMapping extends MappingSpec<Customer, CustomerDto> {
              // Constructs the Impl part-way through this interface's initialisation, before
              // EMAIL below is set.
              ContactMappingImpl MAPPER = ContactMappingImpl.INSTANCE;

              ValidatedPrism<String, EmailAddress> EMAIL =
                  ValidatedPrism.of(
                      raw -> Validated.validNel(new EmailAddress(raw)), EmailAddress::value);

              default ValidatedPrism<String, EmailAddress> email() {
                return EMAIL;
              }
            }
            """);

    var result =
        RuntimeCompilationHelper.compileWith(
            new MappingProcessor(), EMAIL, CUSTOMER, CUSTOMER_DTO, spec);
    // The program's first use is the spec's MAPPER, so the spec initialises first.
    Object mapper = result.loadClass("com.example.ContactMapping").getField("MAPPER").get(null);
    Object wire =
        result
            .loadClass("com.example.CustomerDto")
            .getDeclaredConstructors()[0]
            .newInstance("Ada", "ada@example.org");

    assertThatValidated(parse(mapper, wire)).isValid();
  }

  @Test
  @DisplayName(
      "an inherited leaf the Impl never calls gets no override, even one whose type it could not"
          + " name")
  void anUncalledLeafGetsNoOverride() {
    JavaFileObject secret =
        JavaFileObjects.forSourceString(
            "com.other.Secret",
            """
            package com.other;

            final class Secret {}
            """);
    JavaFileObject vocabulary =
        JavaFileObjects.forSourceString(
            "com.other.Vocabulary",
            """
            package com.other;

            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            public interface Vocabulary {
              default ValidatedPrism<String, Secret> secret() {
                return ValidatedPrism.of(raw -> Validated.validNel(new Secret()), s -> "");
              }
            }
            """);
    JavaFileObject spec =
        JavaFileObjects.forSourceString(
            "com.example.CustomerMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CustomerMapping
                extends com.other.Vocabulary, Counted, MappingSpec<Customer, CustomerDto> {}
            """);

    Compilation compilation =
        compile(EMAIL, CUSTOMER, CUSTOMER_DTO, COUNTED, secret, vocabulary, spec);

    assertThat(compilation).succeeded();
    Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
        .contains("CustomerMapping.super.email()", "hkj$leaf$email")
        .doesNotContain("secret()");
  }
}

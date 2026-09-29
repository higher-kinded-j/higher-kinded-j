// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classDirectory;
import static org.higherkindedj.optics.processing.GeneratorTestHelper.classpathWith;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A spec, or an interface a spec extends, that holds the spec's own generated Impl in a constant
 * draws a warning at the constant, since the Impl's initialisation initialises that interface
 * first, and the constant can read {@code null}. The Impl's name is matched unresolved, in the
 * round that writes it, and resolved, where an earlier build's Impl is on the classpath. Another
 * spec's Impl is initialised on its own, and draws nothing.
 * {@code @SuppressWarnings("impl-constant")} on the field or an enclosing declaration keeps a
 * deliberate constant quiet, and the warning never stops the Impl being written.
 */
@DisplayName("A spec's generated Impl held in a constant on the spec")
class ImplConstantWarningTest {

  private static final String PKG = "com.example.constants";

  private static JavaFileObject source(String simpleName, String body) {
    return JavaFileObjects.forSourceString(
        PKG + "." + simpleName,
        """
        package com.example.constants;

        import java.util.ArrayList;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.GenerateMerge;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.UpdateSpec;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        """
            + body);
  }

  private static List<String> warnings(Compilation compilation) {
    return compilation.diagnostics().stream()
        .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.WARNING)
        .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
        .filter(message -> message.contains("in a constant"))
        .toList();
  }

  /** The warning's full text for {@code constant} holding {@code impl}, declared on {@code on}. */
  private static String warning(String tag, String constant, String impl, String on) {
    return tag
        + ": '"
        + constant
        + "' holds the generated "
        + impl
        + " in a constant, which can read null. "
        + impl
        + " implements '"
        + on
        + "', so once '"
        + on
        + "' declares a default method, such as a leaf, or a private instance method, a program"
        + " that uses "
        + impl
        + ".INSTANCE before reading the constant leaves it null for good. Keep the Impl in the"
        + " calling code: a local, or a private static final field on the class that calls it. To"
        + " keep this constant anyway, annotate it @SuppressWarnings(\"impl-constant\").";
  }

  private static String mapping(String constant, String impl, String on) {
    return warning("@GenerateMapping", constant, impl, on);
  }

  @Nested
  @DisplayName("in the compilation that writes the Impl")
  class SameCompilation {

    private static Compilation compilation;

    @BeforeAll
    static void compile() {
      JavaFileObject records =
          source(
              "Records",
              """
              public final class Records {
                private Records() {}

                public record Customer(String name, String email) {}

                public record CustomerDto(String name, String email) {}

                public record Warehouse(String code) {}

                public record WarehouseDto(String code) {}

                public record Route(String from) {}

                public record RouteDto(String from) {}

                public record Parcel(String id) {}

                public record ParcelDto(String id) {}

                public record Contact(String name) {}

                public static class ContactPatch {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
                }

                public record Receipt(String name, String code) {}
              }
              """);
      JavaFileObject customer =
          source(
              "CustomerMapping",
              """
              @GenerateMapping
              public interface CustomerMapping
                  extends MappingSpec<Records.Customer, Records.CustomerDto> {
                CustomerMappingImpl MAPPER = CustomerMappingImpl.INSTANCE;

                CustomerMappingImpl[] ARRAY = {CustomerMappingImpl.INSTANCE};

                @SuppressWarnings("unchecked")
                CustomerMappingImpl UNRELATED_SUPPRESSION = CustomerMappingImpl.INSTANCE;

                @SuppressWarnings("all")
                CustomerMappingImpl ALL = CustomerMappingImpl.INSTANCE;

                // Another spec's Impl is initialised on its own, and is set when this is read.
                WarehouseMappingImpl WAREHOUSES = WarehouseMappingImpl.INSTANCE;

                // Typed as the spec: the same trap, and not read by the check.
                CustomerMapping SELF = CustomerMappingImpl.INSTANCE;

                String NAME = "customers";

                int LIMIT = 10;

                ArrayList<String> TAGS = new ArrayList<>();

                default ValidatedPrism<String, String> email() {
                  return ValidatedPrism.of(Validated::validNel, raw -> raw);
                }
              }
              """);
      // No leaf: safe until the first one is added, so warned all the same.
      JavaFileObject warehouse =
          source(
              "WarehouseMapping",
              """
              @GenerateMapping
              public interface WarehouseMapping
                  extends MappingSpec<Records.Warehouse, Records.WarehouseDto> {
                @SuppressWarnings("impl-constant")
                WarehouseMappingImpl KEPT = WarehouseMappingImpl.INSTANCE;

                WarehouseMappingImpl MAPPER = WarehouseMappingImpl.INSTANCE;
              }
              """);
      // A mix-in reached by two routes, which ParcelMapping extends too: only RouteMapping's Impl
      // implements it through RouteMapping.
      JavaFileObject mixins =
          source(
              "Vocabulary",
              """
              public interface Vocabulary {
                RouteMappingImpl ROUTES = RouteMappingImpl.INSTANCE;
              }

              interface Left extends Vocabulary {}

              interface Right extends Vocabulary {}
              """);
      JavaFileObject route =
          source(
              "RouteMapping",
              """
              @GenerateMapping
              public interface RouteMapping
                  extends MappingSpec<Records.Route, Records.RouteDto>, Left, Right {}
              """);
      JavaFileObject parcel =
          source(
              "Outer",
              """
              public final class Outer {
                private Outer() {}

                @GenerateMapping
                public interface ParcelMapping
                    extends MappingSpec<Records.Parcel, Records.ParcelDto>, Vocabulary {
                  com.example.constants.OuterParcelMappingImpl MAPPER =
                      com.example.constants.OuterParcelMappingImpl.INSTANCE;
                }

                @SuppressWarnings("impl-constant")
                public interface Quiet {
                  ContactPatchMappingImpl QUIET = ContactPatchMappingImpl.INSTANCE;
                }
              }
              """);
      JavaFileObject contact =
          source(
              "ContactPatchMapping",
              """
              @GenerateMapping
              public interface ContactPatchMapping
                  extends UpdateSpec<Records.Contact, Records.ContactPatch>, Outer.Quiet {
                ContactPatchMappingImpl MAPPER = ContactPatchMappingImpl.INSTANCE;
              }
              """);
      JavaFileObject merge =
          source(
              "ReceiptMerge",
              """
              @GenerateMerge
              public interface ReceiptMerge {
                ReceiptMergeImpl MERGER = ReceiptMergeImpl.INSTANCE;

                Records.Receipt merge(Records.Customer customer, Records.Warehouse warehouse);
              }
              """);
      // Held by the code that calls it: the recommended place, and no warning.
      JavaFileObject caller =
          source(
              "CustomerService",
              """
              final class CustomerService {
                private static final CustomerMappingImpl CUSTOMERS = CustomerMappingImpl.INSTANCE;

                private CustomerService() {}
              }
              """);
      compilation =
          javac()
              .withProcessors(new MappingProcessor(), new MergeProcessor())
              .compile(records, customer, warehouse, mixins, route, parcel, contact, merge, caller);
    }

    @Test
    @DisplayName(
        "each constant holding its own spec's Impl warns, leaf or not, on a spec, a mix-in, an"
            + " UpdateSpec or a merge")
    void eachOwnImplConstantWarns() {
      assertThat(compilation).succeeded();
      Assertions.assertThat(warnings(compilation))
          .containsExactlyInAnyOrder(
              mapping("CustomerMapping.MAPPER", "CustomerMappingImpl", "CustomerMapping"),
              mapping("CustomerMapping.ARRAY", "CustomerMappingImpl", "CustomerMapping"),
              mapping(
                  "CustomerMapping.UNRELATED_SUPPRESSION",
                  "CustomerMappingImpl",
                  "CustomerMapping"),
              mapping("CustomerMapping.ALL", "CustomerMappingImpl", "CustomerMapping"),
              mapping("WarehouseMapping.MAPPER", "WarehouseMappingImpl", "WarehouseMapping"),
              mapping("Vocabulary.ROUTES", "RouteMappingImpl", "Vocabulary"),
              mapping("ParcelMapping.MAPPER", "OuterParcelMappingImpl", "ParcelMapping"),
              mapping(
                  "ContactPatchMapping.MAPPER", "ContactPatchMappingImpl", "ContactPatchMapping"),
              warning("@GenerateMerge", "ReceiptMerge.MERGER", "ReceiptMergeImpl", "ReceiptMerge"));
    }

    @Test
    @DisplayName("the warning does not stop the Impl being written")
    void implIsStillWritten() {
      for (String impl :
          List.of(
              "CustomerMappingImpl",
              "WarehouseMappingImpl",
              "RouteMappingImpl",
              "OuterParcelMappingImpl",
              "ContactPatchMappingImpl",
              "ReceiptMergeImpl")) {
        Assertions.assertThat(compilation.generatedSourceFile(PKG + "." + impl))
            .as(impl)
            .isPresent();
      }
    }
  }

  @Nested
  @DisplayName("against an Impl already compiled")
  class Compiled {

    @TempDir Path tmp;

    @Test
    @DisplayName(
        "a spec rebuilt against its earlier Impl warns, and another spec's compiled Impl does not")
    void resolvedOwnImplWarns() throws IOException {
      JavaFileObject records =
          JavaFileObjects.forSourceString(
              "com.upstream.Records",
              """
              package com.upstream;

              public final class Records {
                private Records() {}

                public record Account(String id) {}

                public record AccountDto(String id) {}
              }
              """);
      JavaFileObject account =
          JavaFileObjects.forSourceString(
              "com.upstream.AccountMapping",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends MappingSpec<Records.Account, Records.AccountDto> {}
              """);
      Compilation earlier =
          javac().withProcessors(new MappingProcessor()).compile(records, account);
      assertThat(earlier).succeeded();
      Path classes = classDirectory(earlier, tmp.resolve("earlier"));

      // The same spec, edited to hold its Impl, rebuilt with the earlier build's classes on the
      // classpath, as an incremental build leaves them.
      JavaFileObject edited =
          JavaFileObjects.forSourceString(
              "com.upstream.AccountMapping",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              @GenerateMapping
              public interface AccountMapping
                  extends MappingSpec<Records.Account, Records.AccountDto> {
                AccountMappingImpl MAPPER = AccountMappingImpl.INSTANCE;
              }
              """);
      JavaFileObject ledger =
          source(
              "LedgerMapping",
              """
              import com.upstream.AccountMappingImpl;

              @GenerateMapping
              public interface LedgerMapping
                  extends MappingSpec<LedgerMapping.Ledger, LedgerMapping.LedgerDto> {
                AccountMappingImpl ACCOUNTS = AccountMappingImpl.INSTANCE;

                record Ledger(String code) {}

                record LedgerDto(String code) {}
              }
              """);
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withClasspath(classpathWith(classes))
              .compile(edited, ledger);

      assertThat(compilation).succeeded();
      Assertions.assertThat(warnings(compilation))
          .containsExactly(
              mapping("AccountMapping.MAPPER", "AccountMappingImpl", "AccountMapping"));
    }
  }
}

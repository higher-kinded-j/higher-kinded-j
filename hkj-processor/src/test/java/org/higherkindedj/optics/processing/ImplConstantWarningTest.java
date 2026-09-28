// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
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
 * A spec, or an interface a spec extends, that holds a generated Impl in a constant draws a warning
 * at the constant, since the constant can read {@code null} once the interface declares a method
 * with a body. The Impl's name is matched unresolved, in the round that writes it, and resolved,
 * when it is compiled into a dependency. {@code @SuppressWarnings("impl-constant")} on the field or
 * an enclosing declaration keeps a deliberate constant quiet, and the warning never stops the Impl
 * being written.
 */
@DisplayName("MappingProcessor - a generated Impl held in a constant on a spec")
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
  private static String warning(String constant, String impl, String on) {
    return "@GenerateMapping: '"
        + constant
        + "' holds the generated "
        + impl
        + " in a constant, which can read null. Initialising "
        + impl
        + " first initialises '"
        + on
        + "' once it declares a default or private method, such as a leaf, so a program that uses"
        + " the Impl first leaves the constant null for good. Keep the Impl in the calling code: a"
        + " local, or a private static final field on the class that calls it. To keep this"
        + " constant anyway, annotate it @SuppressWarnings(\"impl-constant\").";
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

                @SuppressWarnings("unchecked")
                CustomerMappingImpl UNRELATED_SUPPRESSION = CustomerMappingImpl.INSTANCE;

                @SuppressWarnings("all")
                CustomerMappingImpl ALL = CustomerMappingImpl.INSTANCE;

                String NAME = "customers";

                int LIMIT = 10;

                ArrayList<String> TAGS = new ArrayList<>();

                ValidatedPrism<Records.CustomerDto, Records.Customer> PRISM = null;

                Stub STUB = new Stub();

                default ValidatedPrism<String, String> email() {
                  return ValidatedPrism.of(Validated::validNel, raw -> raw);
                }

                final class Stub implements CustomerMapping {}
              }
              """);
      // No leaf: safe today, armed by the first leaf, so warned all the same.
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
      // A mix-in two specs extend, one of them through two routes, is reported once.
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
                  CustomerMappingImpl QUIET = CustomerMappingImpl.INSTANCE;
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
              .withProcessors(new MappingProcessor())
              .compile(records, customer, warehouse, mixins, route, parcel, contact, caller);
    }

    @Test
    @DisplayName("each Impl constant warns once, leaf or not, on a spec, a mix-in or an UpdateSpec")
    void eachImplConstantWarnsOnce() {
      assertThat(compilation).succeeded();
      Assertions.assertThat(warnings(compilation))
          .containsExactlyInAnyOrder(
              warning("CustomerMapping.MAPPER", "CustomerMappingImpl", "CustomerMapping"),
              warning(
                  "CustomerMapping.UNRELATED_SUPPRESSION",
                  "CustomerMappingImpl",
                  "CustomerMapping"),
              warning("CustomerMapping.ALL", "CustomerMappingImpl", "CustomerMapping"),
              warning("WarehouseMapping.MAPPER", "WarehouseMappingImpl", "WarehouseMapping"),
              warning("Vocabulary.ROUTES", "RouteMappingImpl", "Vocabulary"),
              warning("ParcelMapping.MAPPER", "OuterParcelMappingImpl", "ParcelMapping"),
              warning(
                  "ContactPatchMapping.MAPPER", "ContactPatchMappingImpl", "ContactPatchMapping"));
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
              "ContactPatchMappingImpl")) {
        Assertions.assertThat(compilation.generatedSourceFile(PKG + "." + impl))
            .as(impl)
            .isPresent();
      }
    }
  }

  @Nested
  @DisplayName("against an Impl compiled into a dependency")
  class Dependency {

    @TempDir Path tmp;

    @Test
    @DisplayName("a resolved Impl warns, for a MappingSpec's and an UpdateSpec's alike")
    void resolvedImplWarns() throws IOException {
      JavaFileObject upstream =
          JavaFileObjects.forSourceString(
              "com.upstream.Upstream",
              """
              package com.upstream;

              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.annotations.UpdateSpec;

              public final class Upstream {
                private Upstream() {}

                public record Account(String id) {}

                public record AccountDto(String id) {}

                public record Profile(String name) {}

                public static class ProfilePatch {
                  private String name;

                  public String getName() { return name; }
                  public void setName(String name) { this.name = name; }
                }

                @GenerateMapping
                public interface AccountMapping extends MappingSpec<Account, AccountDto> {}

                @GenerateMapping
                public interface ProfileUpdate extends UpdateSpec<Profile, ProfilePatch> {}
              }
              """);
      Compilation dependency = javac().withProcessors(new MappingProcessor()).compile(upstream);
      assertThat(dependency).succeeded();
      Path classes = GeneratorTestHelper.classDirectory(dependency, tmp.resolve("upstream"));

      JavaFileObject downstream =
          source(
              "LedgerMapping",
              """
              import com.upstream.UpstreamAccountMappingImpl;
              import com.upstream.UpstreamProfileUpdateImpl;

              @GenerateMapping
              public interface LedgerMapping
                  extends MappingSpec<LedgerMapping.Ledger, LedgerMapping.LedgerDto> {
                UpstreamAccountMappingImpl ACCOUNTS = UpstreamAccountMappingImpl.INSTANCE;

                UpstreamProfileUpdateImpl PROFILES = UpstreamProfileUpdateImpl.INSTANCE;

                record Ledger(String code) {}

                record LedgerDto(String code) {}
              }
              """);
      Compilation compilation =
          javac()
              .withProcessors(new MappingProcessor())
              .withClasspath(classpathWith(classes))
              .compile(downstream);

      assertThat(compilation).succeeded();
      Assertions.assertThat(warnings(compilation))
          .containsExactlyInAnyOrder(
              warning("LedgerMapping.ACCOUNTS", "UpstreamAccountMappingImpl", "LedgerMapping"),
              warning("LedgerMapping.PROFILES", "UpstreamProfileUpdateImpl", "LedgerMapping"));
    }
  }
}

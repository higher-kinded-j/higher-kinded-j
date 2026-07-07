// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import java.io.IOException;
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("MergeProcessor - @GenerateMerge, one target from N sources")
class MergeProcessorTest {

  private static final JavaFileObject RECORDS =
      JavaFileObjects.forSourceString(
          "com.example.Records",
          """
          package com.example;

          public final class Records {
            public record User(String name, String email) {}

            public record Account(String iban, int balance) {}

            public record Settings(boolean darkMode) {}

            public record EmailAddress(String value) {}

            public record Dashboard(String name, String iban, boolean darkMode) {}

            public record TypedDashboard(String name, EmailAddress email, int balance) {}
          }
          """);

  private static JavaFileObject spec(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.GenerateMerge;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        @GenerateMerge
        """
            + body);
  }

  private Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MergeProcessor()).compile(sources);
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

  @Nested
  @DisplayName("Happy paths")
  class HappyPaths {

    @Test
    @DisplayName("identity fills generate a plain constructor call, no Validated")
    void identityFillsGeneratePlainConstruction() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "DashboardAssembly",
                  """
                  public interface DashboardAssembly {
                    Records.Dashboard assemble(
                        Records.User user, Records.Account account, Records.Settings settings);
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.DashboardAssemblyImpl");
      Assertions.assertThat(generated)
          .contains("public final class DashboardAssemblyImpl implements DashboardAssembly")
          .contains("public static final DashboardAssemblyImpl INSTANCE")
          .contains("Objects.requireNonNull(user, \"user must not be null\")")
          .contains("Objects.requireNonNull(settings, \"settings must not be null\")")
          .contains(
              "return new Records.Dashboard(user.name(), account.iban(), settings.darkMode())")
          .doesNotContain("Validated");
    }

    @Test
    @DisplayName("a fallible leaf switches to the accumulating fields() assembly")
    void fallibleLeafAccumulates() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "TypedAssembly",
                  """
                  public interface TypedAssembly {
                    Validated<NonEmptyList<FieldError>, Records.TypedDashboard> assemble(
                        Records.User user, Records.Account account);

                    default ValidatedPrism<String, Records.EmailAddress> email() {
                      return ValidatedPrism.of(
                          raw ->
                              raw.contains("@")
                                  ? Validated.validNel(new Records.EmailAddress(raw))
                                  : Validated.invalidNel(FieldError.of("not an email address")),
                          Records.EmailAddress::value);
                    }
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.TypedAssemblyImpl");
      Assertions.assertThat(generated)
          .contains("return Validated.fields()")
          .contains(".field(\"name\", Validated.validNel(user.name()))")
          .contains(".field(\"email\", email().parse(user.email()))")
          .contains(".field(\"balance\", Validated.validNel(account.balance()))")
          .contains(".apply(Records.TypedDashboard::new)");
    }
  }

  @Nested
  @DisplayName("Nested fills through the mapping registry")
  class NestedFills {

    private static final JavaFileObject NESTED_RECORDS =
        JavaFileObjects.forSourceString(
            "com.example.Nested",
            """
            package com.example;

            public final class Nested {
              public record Customer(String name, Records.EmailAddress email) {}

              public record CustomerDto(String name, String email) {}

              public record CustomerSummaryDto(String name) {}

              public record Wrapper(CustomerDto customer) {}

              public record Profile(String name, Customer customer) {}
            }
            """);

    private static final JavaFileObject CUSTOMER_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CustomerMapping",
            """
            package com.example;

            import org.higherkindedj.hkt.validated.FieldError;
            import org.higherkindedj.hkt.validated.Validated;
            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;
            import org.higherkindedj.optics.validated.ValidatedPrism;

            @GenerateMapping
            public interface CustomerMapping
                extends MappingSpec<Nested.Customer, Nested.CustomerDto> {
              default ValidatedPrism<String, Records.EmailAddress> email() {
                return ValidatedPrism.of(
                    raw ->
                        raw.contains("@")
                            ? Validated.validNel(new Records.EmailAddress(raw))
                            : Validated.invalidNel(FieldError.of("not an email address")),
                    Records.EmailAddress::value);
              }
            }
            """);

    /** A projection spec for the same domain type: registered but not parse-capable. */
    private static final JavaFileObject PROJECTION_MAPPING =
        JavaFileObjects.forSourceString(
            "com.example.CustomerSummaryMapping",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            @GenerateMapping
            public interface CustomerSummaryMapping
                extends MappingSpec<Nested.Customer, Nested.CustomerSummaryDto> {}
            """);

    /** Parse-capable decoys the nested filter must skip: wrong wire, then wrong domain. */
    private static final JavaFileObject DECOY_MAPPINGS =
        JavaFileObjects.forSourceString(
            "com.example.DecoyMappings",
            """
            package com.example;

            import org.higherkindedj.optics.annotations.GenerateMapping;
            import org.higherkindedj.optics.annotations.MappingSpec;

            public final class DecoyMappings {
              @GenerateMapping
              public interface WrongWire
                  extends MappingSpec<Records.EmailAddress, Records.EmailAddress> {}

              @GenerateMapping
              public interface WrongDomain extends MappingSpec<Records.User, Nested.CustomerDto> {}
            }
            """);

    private Compilation compileWithMapping(JavaFileObject... sources) {
      return javac().withProcessors(new MergeProcessor(), new MappingProcessor()).compile(sources);
    }

    @Test
    @DisplayName("a component pair mapped by a sibling spec fills through its Impl")
    void nestedComponentFillsThroughSiblingImpl() {
      Compilation compilation =
          compileWithMapping(
              RECORDS,
              NESTED_RECORDS,
              CUSTOMER_MAPPING,
              PROJECTION_MAPPING,
              DECOY_MAPPINGS,
              spec(
                  "ProfileAssembly",
                  """
                  public interface ProfileAssembly {
                    Validated<NonEmptyList<FieldError>, Nested.Profile> assemble(
                        Records.User user, Nested.Wrapper wrapper);
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.ProfileAssemblyImpl");
      Assertions.assertThat(generated)
          .contains(
              ".field(\"customer\","
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism().parse(wrapper.customer()))")
          .contains(".field(\"name\", Validated.validNel(user.name()))");
    }

    @Test
    @DisplayName("an explicit leaf beats the nested spec")
    void explicitLeafBeatsNestedSpec() {
      Compilation compilation =
          compileWithMapping(
              RECORDS,
              NESTED_RECORDS,
              CUSTOMER_MAPPING,
              spec(
                  "LeafFirstAssembly",
                  """
                  public interface LeafFirstAssembly {
                    Validated<NonEmptyList<FieldError>, Nested.Profile> assemble(
                        Records.User user, Nested.Wrapper wrapper);

                    default ValidatedPrism<Nested.CustomerDto, Nested.Customer> customer() {
                      return CustomerMappingImpl.INSTANCE.asValidatedPrism();
                    }
                  }
                  """));
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.LeafFirstAssemblyImpl");
      Assertions.assertThat(generated)
          .contains(".field(\"customer\", customer().parse(wrapper.customer()))")
          .doesNotContain("asValidatedPrism().parse");
    }

    @Test
    @DisplayName("two mapping specs for one pair make the nested fill ambiguous")
    void ambiguousNestedSpecsRejected() {
      JavaFileObject duplicate =
          JavaFileObjects.forSourceString(
              "com.example.OtherCustomerMapping",
              """
              package com.example;

              import org.higherkindedj.hkt.validated.Validated;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;
              import org.higherkindedj.optics.validated.ValidatedPrism;

              @GenerateMapping
              public interface OtherCustomerMapping
                  extends MappingSpec<Nested.Customer, Nested.CustomerDto> {
                default ValidatedPrism<String, Records.EmailAddress> email() {
                  return ValidatedPrism.of(
                      raw -> Validated.validNel(new Records.EmailAddress(raw)),
                      Records.EmailAddress::value);
                }
              }
              """);
      Compilation compilation =
          compileWithMapping(
              RECORDS,
              NESTED_RECORDS,
              CUSTOMER_MAPPING,
              duplicate,
              spec(
                  "AmbiguousAssembly",
                  """
                  public interface AmbiguousAssembly {
                    Validated<NonEmptyList<FieldError>, Nested.Profile> assemble(
                        Records.User user, Nested.Wrapper wrapper);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target component 'customer' matches more than one mapping spec");
      assertThat(compilation)
          .hadErrorContaining("Add a leaf method 'customer()' delegating to the spec you want");
    }

    @Test
    @DisplayName("a nested fill counts as fallible for the return-type discipline")
    void nestedFillDemandsValidatedReturn() {
      Compilation compilation =
          compileWithMapping(
              RECORDS,
              NESTED_RECORDS,
              CUSTOMER_MAPPING,
              spec(
                  "PlainNestedAssembly",
                  """
                  public interface PlainNestedAssembly {
                    Nested.Profile assemble(Records.User user, Nested.Wrapper wrapper);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("uses fallible fills but declares a plain 'Profile' return");
    }
  }

  @Nested
  @DisplayName("What/why/fix diagnostics")
  class Diagnostics {

    @Test
    @DisplayName("a component carried by two sources is ambiguous")
    void ambiguousComponentRejected() {
      JavaFileObject clashing =
          JavaFileObjects.forSourceString(
              "com.example.Clash",
              """
              package com.example;

              public final class Clash {
                public record A(String name) {}

                public record B(String name) {}

                public record Target(String name, String other) {}
              }
              """);
      Compilation compilation =
          compile(
              clashing,
              spec(
                  "ClashAssembly",
                  """
                  public interface ClashAssembly {
                    Clash.Target assemble(Clash.A a, Clash.B b);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("target component 'name' is ambiguous");
      assertThat(compilation).hadErrorContaining("Rename the component on all but one source");
    }

    @Test
    @DisplayName("an unfilled target component is rejected")
    void unfilledComponentRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "GapAssembly",
                  """
                  public interface GapAssembly {
                    Records.Dashboard assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target component 'Dashboard.darkMode' is not filled by any source");
    }

    @Test
    @DisplayName("fewer than two sources points at @GenerateMapping")
    void singleSourceRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "OneSourceAssembly",
                  """
                  public interface OneSourceAssembly {
                    Records.Dashboard assemble(Records.User user);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("declares fewer than two sources");
      assertThat(compilation).hadErrorContaining("use @GenerateMapping for a single source");
    }

    @Test
    @DisplayName("truthful types: fallible leaves demand a Validated return")
    void fallibleWithPlainReturnRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "LyingAssembly",
                  """
                  public interface LyingAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default ValidatedPrism<String, Records.EmailAddress> email() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Records.EmailAddress(raw)),
                          Records.EmailAddress::value);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("uses fallible fills but declares a plain 'TypedDashboard' return");
    }

    @Test
    @DisplayName("truthful types: an identity-only merge must not claim Validated")
    void losslessWithValidatedReturnRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "OverclaimAssembly",
                  """
                  public interface OverclaimAssembly {
                    Validated<NonEmptyList<FieldError>, Records.Dashboard> assemble(
                        Records.User user, Records.Account account, Records.Settings settings);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("declares a Validated return but every fill is an identity copy");
    }

    @Test
    @DisplayName("a Validated return with the wrong error channel is rejected")
    void wrongErrorChannelRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "WrongChannelAssembly",
                  """
                  public interface WrongChannelAssembly {
                    Validated<String, Records.Dashboard> assemble(
                        Records.User user, Records.Account account, Records.Settings settings);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("wrong error channel");
      assertThat(compilation)
          .hadErrorContaining("Declare 'Validated<NonEmptyList<FieldError>, Target>'");
    }

    @Test
    @DisplayName("two abstract methods are rejected")
    void twoAbstractMethodsRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "TwoMethodsAssembly",
                  """
                  public interface TwoMethodsAssembly {
                    Records.Dashboard assemble(Records.User user, Records.Account account);

                    Records.Dashboard other(Records.User user, Records.Settings settings);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("declares 2 abstract methods; a merge spec declares exactly one");
    }

    @Test
    @DisplayName("non-record sources and targets are rejected")
    void nonRecordShapesRejected() {
      Compilation sourceCase =
          compile(
              RECORDS,
              spec(
                  "BadSourceAssembly",
                  """
                  public interface BadSourceAssembly {
                    Records.Dashboard assemble(String user, Records.Account account);
                  }
                  """));
      assertThat(sourceCase).failed();
      assertThat(sourceCase).hadErrorContaining("source parameter 'user' is not a record");

      Compilation targetCase =
          compile(
              RECORDS,
              spec(
                  "BadTargetAssembly",
                  """
                  public interface BadTargetAssembly {
                    String assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(targetCase).failed();
      assertThat(targetCase).hadErrorContaining("merge target 'java.lang.String' is not a record");
    }

    @Test
    @DisplayName("a component whose types differ with no leaf names the expected signature")
    void missingLeafNamesSignature() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "NoLeafAssembly",
                  """
                  public interface NoLeafAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("target component 'TypedDashboard.email' has no usable fill");
      assertThat(compilation)
          .hadErrorContaining(
              "Add 'default ValidatedPrism<java.lang.String, com.example.Records.EmailAddress>"
                  + " email()'");
    }

    @Test
    @DisplayName("a 13-component fallible target hits the fields() ceiling")
    void fallibleArityCeilingRejected() {
      String comps =
          java.util.stream.IntStream.rangeClosed(1, 12)
              .mapToObj(i -> "String f" + i)
              .collect(java.util.stream.Collectors.joining(", "));
      JavaFileObject wide =
          JavaFileObjects.forSourceString(
              "com.example.Wide",
              "package com.example;\n"
                  + "public final class Wide {\n"
                  + "  public record Source("
                  + comps
                  + ", String raw) {}\n"
                  + "  public record Extra(String tail) {}\n"
                  + "  public record Target("
                  + comps
                  + ", Records.EmailAddress raw) {}\n"
                  + "}\n");
      Compilation compilation =
          compile(
              RECORDS,
              wide,
              spec(
                  "WideAssembly",
                  """
                  public interface WideAssembly {
                    Validated<NonEmptyList<FieldError>, Wide.Target> assemble(
                        Wide.Source source, Wide.Extra extra);

                    default ValidatedPrism<String, Records.EmailAddress> raw() {
                      return ValidatedPrism.of(
                          v -> Validated.validNel(new Records.EmailAddress(v)),
                          Records.EmailAddress::value);
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("has 13 components; the accumulating merge supports at most 12");
    }

    @Test
    @DisplayName("malformed Validated returns are each rejected")
    void malformedValidatedReturnsRejected() {
      record Shape(String name, String returnType, String expected) {}
      var shapes =
          java.util.List.of(
              new Shape("RawValidated", "Validated", "wrong error channel"),
              new Shape(
                  "WildcardChannel", "Validated<?, Records.Dashboard>", "wrong error channel"),
              new Shape(
                  "RawNel", "Validated<NonEmptyList, Records.Dashboard>", "wrong error channel"),
              new Shape(
                  "WildcardNel",
                  "Validated<NonEmptyList<?>, Records.Dashboard>",
                  "wrong error channel"),
              new Shape(
                  "StringNel",
                  "Validated<NonEmptyList<String>, Records.Dashboard>",
                  "wrong error channel"),
              new Shape(
                  "NonRecordInside",
                  "Validated<NonEmptyList<FieldError>, String>",
                  "merge target 'java.lang.String' is not a record"));
      for (Shape shape : shapes) {
        Compilation compilation =
            compile(
                RECORDS,
                spec(
                    shape.name() + "Assembly",
                    "@SuppressWarnings(\"rawtypes\")\n"
                        + "public interface "
                        + shape.name()
                        + "Assembly {\n  "
                        + shape.returnType()
                        + " assemble(Records.User user, Records.Account account,"
                        + " Records.Settings settings);\n}\n"));
        assertThat(compilation).failed();
        assertThat(compilation).hadErrorContaining(shape.expected());
      }
    }

    @Test
    @DisplayName("primitive returns and generic targets are rejected")
    void primitiveReturnAndGenericTargetRejected() {
      Compilation primitive =
          compile(
              RECORDS,
              spec(
                  "PrimitiveAssembly",
                  """
                  public interface PrimitiveAssembly {
                    int assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(primitive).failed();
      assertThat(primitive).hadErrorContaining("merge target 'int' is not a record");

      JavaFileObject generic =
          JavaFileObjects.forSourceString(
              "com.example.Gen",
              """
              package com.example;

              public final class Gen {
                public record Box<T>(String name) {}
              }
              """);
      Compilation genericTarget =
          compile(
              RECORDS,
              generic,
              spec(
                  "GenericTargetAssembly",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface GenericTargetAssembly {
                    Gen.Box assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(genericTarget).failed();
      assertThat(genericTarget).hadErrorContaining("'Box' is generic");
    }

    @Test
    @DisplayName("decoy leaf shapes are skipped, and the fill error names the real signature")
    void decoyLeafShapesSkipped() {
      Compilation overloads =
          compile(
              RECORDS,
              spec(
                  "DecoyAssembly",
                  """
                  public interface DecoyAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default ValidatedPrism<String, String> email() {
                      return ValidatedPrism.of(Validated::validNel, v -> v);
                    }

                    default ValidatedPrism<String, Records.EmailAddress> email(int variant) {
                      return null;
                    }

                    static ValidatedPrism<String, Records.EmailAddress> email(String seed) {
                      return null;
                    }
                  }
                  """));
      assertThat(overloads).failed();
      assertThat(overloads)
          .hadErrorContaining("target component 'TypedDashboard.email' has no usable fill");

      Compilation primitiveReturn =
          compile(
              RECORDS,
              spec(
                  "PrimitiveLeafAssembly",
                  """
                  public interface PrimitiveLeafAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default int email() {
                      return 42;
                    }
                  }
                  """));
      assertThat(primitiveReturn).failed();
      assertThat(primitiveReturn).hadErrorContaining("has no usable fill");

      Compilation rawPrism =
          compile(
              RECORDS,
              spec(
                  "RawLeafAssembly",
                  """
                  @SuppressWarnings("rawtypes")
                  public interface RawLeafAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default ValidatedPrism email() {
                      return null;
                    }
                  }
                  """));
      assertThat(rawPrism).failed();
      assertThat(rawPrism).hadErrorContaining("has no usable fill");

      Compilation nonPrismReturn =
          compile(
              RECORDS,
              spec(
                  "StringLeafAssembly",
                  """
                  public interface StringLeafAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default String email() {
                      return "not a prism";
                    }
                  }
                  """));
      assertThat(nonPrismReturn).failed();
      assertThat(nonPrismReturn).hadErrorContaining("has no usable fill");

      Compilation wrongSourceArg =
          compile(
              RECORDS,
              spec(
                  "WrongSourceLeafAssembly",
                  """
                  public interface WrongSourceLeafAssembly {
                    Records.TypedDashboard assemble(Records.User user, Records.Account account);

                    default ValidatedPrism<Integer, Records.EmailAddress> email() {
                      return null;
                    }
                  }
                  """));
      assertThat(wrongSourceArg).failed();
      assertThat(wrongSourceArg).hadErrorContaining("has no usable fill");
    }

    @Test
    @DisplayName("primitive source parameters are rejected")
    void primitiveSourceRejected() {
      Compilation compilation =
          compile(
              RECORDS,
              spec(
                  "PrimitiveSourceAssembly",
                  """
                  public interface PrimitiveSourceAssembly {
                    Records.Dashboard assemble(int user, Records.Account account);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("source parameter 'user' is not a record");
    }

    @Test
    @DisplayName("two specs colliding on one generated Impl name get a rename fix")
    void filerCollisionNamed() {
      JavaFileObject nested =
          JavaFileObjects.forSourceString(
              "com.example.Outer",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMerge;

              public final class Outer {
                public record D(String a) {}

                public record E(String b) {}

                public record T(String a, String b) {}

                @GenerateMerge
                public interface InnerAssembly {
                  T assemble(D d, E e);
                }
              }
              """);
      JavaFileObject topLevel =
          JavaFileObjects.forSourceString(
              "com.example.OuterInnerAssembly",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMerge;

              @GenerateMerge
              public interface OuterInnerAssembly {
                Outer.T assemble(Outer.D d, Outer.E e);
              }
              """);
      Compilation compilation = compile(nested, topLevel);
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("the class already exists");
      assertThat(compilation).hadErrorContaining("Rename one of the colliding specs");
    }

    @Test
    @DisplayName("a non-collision IOException reports the what/why/fix write failure")
    void ioExceptionReportsWriteFailure() {
      java.util.List<String> messages = new java.util.ArrayList<>();
      var messager =
          (javax.annotation.processing.Messager)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.Messager.class},
                  (proxy, method, args) -> {
                    if ("printMessage".equals(method.getName())) {
                      messages.add(String.valueOf(args[1]));
                    }
                    return null;
                  });
      var filer =
          (javax.annotation.processing.Filer)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.Filer.class},
                  (proxy, method, args) -> {
                    throw new IOException("disk full");
                  });
      var env =
          (javax.annotation.processing.ProcessingEnvironment)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.annotation.processing.ProcessingEnvironment.class},
                  (proxy, method, args) ->
                      switch (method.getName()) {
                        case "getFiler" -> filer;
                        case "getMessager" -> messager;
                        default -> null;
                      });
      var simpleName =
          (javax.lang.model.element.Name)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.lang.model.element.Name.class},
                  (proxy, method, args) ->
                      "toString".equals(method.getName()) ? "BrokenAssembly" : null);
      var specElement =
          (javax.lang.model.element.TypeElement)
              java.lang.reflect.Proxy.newProxyInstance(
                  getClass().getClassLoader(),
                  new Class<?>[] {javax.lang.model.element.TypeElement.class},
                  (proxy, method, args) ->
                      "getSimpleName".equals(method.getName()) ? simpleName : null);

      MergeProcessor processor = new MergeProcessor();
      processor.init(env);
      processor.writeFile(
          specElement,
          "com.example",
          com.palantir.javapoet.TypeSpec.classBuilder("Broken").build());

      Assertions.assertThat(messages)
          .singleElement()
          .asString()
          .contains("could not write the generated merge for 'BrokenAssembly'")
          .contains("disk full")
          .contains("Check build-output permissions and free disk space");
    }

    @Test
    @DisplayName("non-interface targets and generic specs are rejected")
    void structuralShapesRejected() {
      JavaFileObject clazz =
          JavaFileObjects.forSourceString(
              "com.example.NotAnInterface",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.GenerateMerge;

              @GenerateMerge
              public class NotAnInterface {}
              """);
      Compilation classCase = compile(RECORDS, clazz);
      assertThat(classCase).failed();
      assertThat(classCase).hadErrorContaining("can only be applied to interfaces");

      Compilation genericCase =
          compile(
              RECORDS,
              spec(
                  "GenericAssembly",
                  """
                  public interface GenericAssembly<T> {
                    Records.Dashboard assemble(Records.User user, Records.Account account);
                  }
                  """));
      assertThat(genericCase).failed();
      assertThat(genericCase).hadErrorContaining("'GenericAssembly' is generic");
    }
  }
}

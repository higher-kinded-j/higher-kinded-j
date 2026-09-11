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
import java.util.Optional;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.assertj.core.api.Assertions;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@code @Flatten}: a nested domain record component spread across the wire's flat components, both
 * directions, with the whole leaf vocabulary applying inside the group.
 */
@DisplayName("MappingProcessor - @Flatten spreads a nested component across a flat wire")
class MappingProcessorFlattenTest {

  private static final JavaFileObject TYPES =
      JavaFileObjects.forSourceString(
          "com.example.Types",
          """
          package com.example;

          import java.util.List;
          import java.util.Map;
          import java.util.Optional;

          public final class Types {
            public record Address(String street, String city, String postcode) {}

            public record Customer(String name, Address address) {}

            public record CustomerDto(String name, String street, String city, String postcode) {}

            public record Postcode(String value) {}

            public record CodedAddress(String street, String city, Postcode postcode) {}

            public record CodedCustomer(String name, CodedAddress address) {}

            public record Geo(double lat, double lon) {}

            public record GeoDto(double lat, double lon) {}

            public record Located(String street, Geo geo) {}

            public record Site(String name, Located address) {}

            public record SiteDto(String name, String street, GeoDto geo) {}

            public record Contact(String phone, String email) {}

            public record Account(String name, Address home, Contact contact) {}

            public record AccountDto(
                String name, String street, String city, String postcode, String phone, String email) {}

            public record Reach(String phone, Optional<String> fax) {}

            public record Member(String name, Reach reach) {}

            public record MemberDto(String name, String phone, String fax) {}

            public record Fronted(Address address, String name) {}

            public record FrontedDto(String street, String city, String postcode, String name) {}

            public record Tag(String value) {}

            public record Bag(List<Tag> tags, Optional<String> note, Map<String, Integer> counts) {}

            public record Holder(String name, Bag bag) {}

            public record HolderDto(
                String name, List<String> tags, Optional<String> note, Map<String, Integer> counts) {}

            public record Order(String id, Customer customer) {}

            public record OrderDto(String id, CustomerDto customer) {}

            public record Pair<T>(T item, Address address) {}

            public record PairDto<T>(T item, String street, String city, String postcode) {}

            public record Empty() {}

            public record Hollow(String name, Empty empty) {}

            public record HollowDto(String name) {}

            public record Box<T>(T item, String label) {}

            public record Wild(String name, Box<?> box) {}

            @SuppressWarnings("rawtypes")
            public record Raw(String name, Box box) {}

            public record BoxDto(String name, Object item, String label) {}
          }
          """);

  private static JavaFileObject spec(String name, String body) {
    return JavaFileObjects.forSourceString(
        "com.example." + name,
        """
        package com.example;

        import java.util.Optional;
        import org.higherkindedj.hkt.validated.FieldError;
        import org.higherkindedj.hkt.validated.Validated;
        import org.higherkindedj.optics.annotations.Flatten;
        import org.higherkindedj.optics.annotations.GenerateMapping;
        import org.higherkindedj.optics.annotations.MapField;
        import org.higherkindedj.optics.annotations.MappingSpec;
        import org.higherkindedj.optics.annotations.OptionalBridge;
        import org.higherkindedj.optics.annotations.UpdateSpec;
        import org.higherkindedj.optics.Getter;
        import org.higherkindedj.optics.validated.ValidatedPrism;

        """
            + body);
  }

  private static final JavaFileObject CUSTOMER_MAPPING =
      spec(
          "CustomerMapping",
          """
          @GenerateMapping
          public interface CustomerMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
            @Flatten
            Types.Address address();
          }
          """);

  private static Compilation compile(JavaFileObject... sources) {
    return javac().withProcessors(new MappingProcessor()).compile(sources);
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

  @SuppressWarnings("unchecked")
  private static Validated<NonEmptyList<FieldError>, Object> parse(Object impl, Object wire) {
    return (Validated<NonEmptyList<FieldError>, Object>) invoke(impl, "parse", wire);
  }

  @Nested
  @DisplayName("Emission")
  class Emission {

    @Test
    @DisplayName(
        "build spreads the record's components; parse assembles them through a nested ladder")
    void identityGroupSpreadsAndAssembles() {
      Compilation compilation = compile(TYPES, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      String generated = generatedSource(compilation, "com.example.CustomerMappingImpl");
      Assertions.assertThat(generated)
          .contains(
              "return new Types.CustomerDto(domain.name(), domain.address().street(),"
                  + " domain.address().city(), domain.address().postcode())")
          .contains(".field(\"address\", Validated.fields()")
          .contains(".field(\"street\", hkj$ifPresent(wire.street(), Validated::validNel))")
          .contains(".field(\"postcode\", hkj$ifPresent(wire.postcode(), Validated::validNel))")
          .contains(".apply(Types.Address::new))")
          .contains(".apply(Types.Customer::new)")
          // The marker is a stub, like a rename.
          .contains("public Types.Address address()")
          .contains("@Flatten markers declare flattened components and are not invocable");
    }

    @Test
    @DisplayName("an all-identity group keeps the mapping lossless, so asIso() reassembles it")
    void identityGroupKeepsAsIso() {
      Compilation compilation = compile(TYPES, CUSTOMER_MAPPING);
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CustomerMappingImpl"))
          .contains("public Iso<Types.Customer, Types.CustomerDto> asIso()")
          .contains(
              "wire -> new Types.Customer(wire.name(), new Types.Address(wire.street(),"
                  + " wire.city(), wire.postcode()))");
    }

    @Test
    @DisplayName("a leaf named after an inner component converts it, and costs the Iso tier")
    void leafInsideTheGroup() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "CodedCustomerMapping",
                  """
                  @GenerateMapping
                  public interface CodedCustomerMapping
                      extends MappingSpec<Types.CodedCustomer, Types.CustomerDto> {
                    @Flatten
                    Types.CodedAddress address();

                    default ValidatedPrism<String, Types.Postcode> postcode() {
                      return ValidatedPrism.of(
                          raw ->
                              raw.isBlank()
                                  ? Validated.invalidNel(FieldError.of("must not be blank"))
                                  : Validated.validNel(new Types.Postcode(raw)),
                          Types.Postcode::value);
                    }
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.CodedCustomerMappingImpl"))
          .contains("postcode().build(domain.address().postcode())")
          .contains(".field(\"postcode\", hkj$ifPresent(wire.postcode(), postcode()::parse))")
          .doesNotContain("asIso");
    }

    @Test
    @DisplayName("a rename named after an inner component points it at another wire component")
    void renameInsideTheGroup() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LineDto",
              """
              package com.example;

              public record LineDto(String name, String addressLine1, String city, String postcode) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "LineMapping",
                  """
                  @GenerateMapping
                  public interface LineMapping extends MappingSpec<Types.Customer, LineDto> {
                    @Flatten
                    Types.Address address();

                    @MapField(to = "addressLine1")
                    String street();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.LineMappingImpl"))
          .contains(
              "return new LineDto(domain.name(), domain.address().street(),"
                  + " domain.address().city(), domain.address().postcode())")
          .contains(".field(\"street\", hkj$ifPresent(wire.addressLine1(), Validated::validNel))");
    }

    @Test
    @DisplayName("an inner record nests through its own spec, inside the group")
    void nestedSpecInsideTheGroup() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "GeoMapping",
                  """
                  @GenerateMapping
                  public interface GeoMapping extends MappingSpec<Types.Geo, Types.GeoDto> {}
                  """),
              spec(
                  "SiteMapping",
                  """
                  @GenerateMapping
                  public interface SiteMapping extends MappingSpec<Types.Site, Types.SiteDto> {
                    @Flatten
                    Types.Located address();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.SiteMappingImpl"))
          .contains("GeoMappingImpl.INSTANCE.asValidatedPrism().build(domain.address().geo())")
          .contains(
              ".field(\"geo\", hkj$ifPresent(wire.geo(),"
                  + " GeoMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }

    @Test
    @DisplayName("two flattened components each assemble their own record")
    void twoGroups() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "AccountMapping",
                  """
                  @GenerateMapping
                  public interface AccountMapping extends MappingSpec<Types.Account, Types.AccountDto> {
                    @Flatten
                    Types.Address home();

                    @Flatten
                    Types.Contact contact();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.AccountMappingImpl"))
          .contains(".field(\"home\", Validated.fields()")
          .contains(".field(\"contact\", Validated.fields()")
          .contains(".apply(Types.Address::new))")
          .contains(".apply(Types.Contact::new))")
          .contains(
              "new Types.Account(wire.name(), new Types.Address(wire.street(), wire.city(),"
                  + " wire.postcode()), new Types.Contact(wire.phone(), wire.email()))");
    }

    @Test
    @DisplayName("unrelated mix-ins agreeing on a marker declare one group")
    void agreeingMixinsDeclareOneGroup() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "AddressVocabulary",
                  """
                  public interface AddressVocabulary {
                    @Flatten
                    Types.Address address();
                  }
                  """),
              spec(
                  "OtherVocabulary",
                  """
                  public interface OtherVocabulary {
                    @Flatten
                    Types.Address address();

                    @Flatten
                    Types.Contact contact();
                  }
                  """),
              spec(
                  "MixedMapping",
                  """
                  @GenerateMapping
                  public interface MixedMapping
                      extends MappingSpec<Types.Customer, Types.CustomerDto>,
                          AddressVocabulary,
                          OtherVocabulary {}
                  """));
      // The inherited 'contact' marker names no component of Customer and stays inert.
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.MixedMappingImpl"))
          .contains(".field(\"address\", Validated.fields()")
          .contains("public Types.Contact contact()");
    }

    @Test
    @DisplayName("a bridge named after an inner Optional component bridges it, local or inherited")
    void bridgeInsideTheGroup() {
      JavaFileObject local =
          spec(
              "MemberMapping",
              """
              @GenerateMapping
              public interface MemberMapping extends MappingSpec<Types.Member, Types.MemberDto> {
                @Flatten
                Types.Reach reach();

                @OptionalBridge
                Optional<String> fax();
              }
              """);
      JavaFileObject inherited =
          spec(
              "ReachVocabulary",
              """
              public interface ReachVocabulary {
                @OptionalBridge
                Optional<String> fax();
              }
              """);
      JavaFileObject extending =
          spec(
              "InheritedMemberMapping",
              """
              @GenerateMapping
              public interface InheritedMemberMapping
                  extends MappingSpec<Types.Member, Types.MemberDto>, ReachVocabulary {
                @Flatten
                Types.Reach reach();
              }
              """);
      Compilation compilation = compile(TYPES, local, inherited, extending);
      assertThat(compilation).succeeded();
      for (String impl : List.of("MemberMappingImpl", "InheritedMemberMappingImpl")) {
        Assertions.assertThat(generatedSource(compilation, "com.example." + impl))
            .contains("domain.reach().fax().orElse(null)")
            .contains(".field(\"fax\", Validated.validNel(Optional.ofNullable(wire.fax())))")
            .contains(".apply(Types.Reach::new))");
      }
    }

    @Test
    @DisplayName("a flattened first component assembles first, in build, parse and asIso()")
    void firstComponentFlattened() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "FrontedMapping",
                  """
                  @GenerateMapping
                  public interface FrontedMapping extends MappingSpec<Types.Fronted, Types.FrontedDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.FrontedMappingImpl"))
          .contains(
              "return new Types.FrontedDto(domain.address().street(), domain.address().city(),"
                  + " domain.address().postcode(), domain.name())")
          .contains(
              "wire -> new Types.Fronted(new Types.Address(wire.street(), wire.city(),"
                  + " wire.postcode()), wire.name())");
    }

    @Test
    @DisplayName("container components inside the group copy by identity or lift a leaf")
    void containersInsideTheGroup() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "HolderMapping",
                  """
                  @GenerateMapping
                  public interface HolderMapping extends MappingSpec<Types.Holder, Types.HolderDto> {
                    @Flatten
                    Types.Bag bag();

                    default ValidatedPrism<String, Types.Tag> tags() {
                      return ValidatedPrism.of(
                          raw -> Validated.validNel(new Types.Tag(raw)), Types.Tag::value);
                    }
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.HolderMappingImpl"))
          .contains("domain.bag().tags()")
          .contains("domain.bag().note()")
          .contains("domain.bag().counts()")
          .contains(".field(\"tags\", hkj$ifPresent(wire.tags(), tags()::parseAll))")
          .contains(".field(\"note\", ")
          .contains(".field(\"counts\", ")
          .contains(".apply(Types.Bag::new))");
    }

    @Test
    @DisplayName(
        "a rename from another component may feed the wire component named after the group")
    void renameFeedsTheFlattenedName() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.LabelledDto",
              """
              package com.example;

              public record LabelledDto(String address, String street, String city, String postcode) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "LabelledMapping",
                  """
                  @GenerateMapping
                  public interface LabelledMapping extends MappingSpec<Types.Customer, LabelledDto> {
                    @Flatten
                    Types.Address address();

                    @MapField(to = "address")
                    String name();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.LabelledMappingImpl"))
          .contains(
              "return new LabelledDto(domain.name(), domain.address().street(),"
                  + " domain.address().city(), domain.address().postcode())")
          .contains(".field(\"name\", hkj$ifPresent(wire.address(), Validated::validNel))");
    }

    @Test
    @DisplayName("a mapping carrying a group is parse-capable, so another spec nests it")
    void flatteningSpecNestsInTheSameCompilation() {
      Compilation compilation =
          compile(
              TYPES,
              CUSTOMER_MAPPING,
              spec(
                  "OrderMapping",
                  """
                  @GenerateMapping
                  public interface OrderMapping extends MappingSpec<Types.Order, Types.OrderDto> {}
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.OrderMappingImpl"))
          .contains("CustomerMappingImpl.INSTANCE.asValidatedPrism().build(domain.customer())")
          .contains(
              ".field(\"customer\", hkj$ifPresent(wire.customer(),"
                  + " CustomerMappingImpl.INSTANCE.asValidatedPrism()::parse))");
    }

    @Test
    @DisplayName("a generic domain instantiated concretely spreads its group like any other")
    void concreteInstantiationOfAGenericDomain() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "PairMapping",
                  """
                  @GenerateMapping
                  public interface PairMapping
                      extends MappingSpec<Types.Pair<String>, Types.PairDto<String>> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).succeeded();
      Assertions.assertThat(generatedSource(compilation, "com.example.PairMappingImpl"))
          .contains(".field(\"address\", Validated.fields()")
          .contains(".apply(Types.Address::new))")
          .contains("public Iso<Types.Pair<String>, Types.PairDto<String>> asIso()");
    }
  }

  @Nested
  @DisplayName("Runtime")
  class Runtime {

    @Test
    @DisplayName("the round trip holds, and a missing flat field locates under the domain path")
    void roundTripAndLocatedFailures() throws ReflectiveOperationException {
      var result =
          RuntimeCompilationHelper.compileWith(new MappingProcessor(), TYPES, CUSTOMER_MAPPING);
      Object impl = result.instance("com.example.CustomerMappingImpl");
      Object address = result.newInstance("com.example.Types$Address", "1 High St", "Leeds", "LS1");
      Object customer = result.newInstance("com.example.Types$Customer", "Ada", address);

      Object built = invoke(impl, "build", customer);
      Assertions.assertThat(String.valueOf(built))
          .isEqualTo("CustomerDto[name=Ada, street=1 High St, city=Leeds, postcode=LS1]");
      assertThatValidated(parse(impl, built)).isValid().hasValue(customer);

      Object missing =
          result
              .loadClass("com.example.Types$CustomerDto")
              .getDeclaredConstructor(String.class, String.class, String.class, String.class)
              .newInstance("Ada", null, "Leeds", null);
      assertThatValidated(parse(impl, missing))
          .isInvalid()
          .hasFieldErrors("address.street: must not be null", "address.postcode: must not be null");
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    @DisplayName("a marker with a body is refused")
    void markerWithBody() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "BodiedMapping",
                  """
                  @GenerateMapping
                  public interface BodiedMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    default Types.Address address() { return null; }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@Flatten method 'address' must be abstract");
    }

    @Test
    @DisplayName("a marker with parameters is refused")
    void markerWithParameters() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "ParamMapping",
                  """
                  @GenerateMapping
                  public interface ParamMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    Types.Address address(String which);
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Flatten method 'address' must not declare parameters");
    }

    @Test
    @DisplayName("a marker on a sealed mapping is refused")
    void markerOnSealedMapping() {
      JavaFileObject sealed =
          JavaFileObjects.forSourceString(
              "com.example.Shapes",
              """
              package com.example;

              public final class Shapes {
                public sealed interface Shape permits Circle {}

                public record Circle(int r) implements Shape {}

                public sealed interface ShapeDto permits CircleDto {}

                public record CircleDto(int r) implements ShapeDto {}
              }
              """);
      Compilation compilation =
          compile(
              TYPES,
              sealed,
              spec(
                  "ShapeMapping",
                  """
                  @GenerateMapping
                  public interface ShapeMapping extends MappingSpec<Shapes.Shape, Shapes.ShapeDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation).hadErrorContaining("@Flatten has no meaning on a sealed mapping");
    }

    @Test
    @DisplayName("a marker also carrying @OptionalBridge is refused")
    void markerWithBridge() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "BridgedMapping",
                  """
                  @GenerateMapping
                  public interface BridgedMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    @OptionalBridge
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Flatten method 'address' also carries @OptionalBridge");
    }

    @Test
    @DisplayName("a marker also carrying @MapField is refused")
    void markerWithRename() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "RenamedMapping",
                  """
                  @GenerateMapping
                  public interface RenamedMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    @MapField(to = "street")
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@MapField method 'address' also carries @Flatten");
    }

    @Test
    @DisplayName("a marker naming a type the spec's package cannot reach is refused")
    void markerNamesUnreachableType() {
      JavaFileObject nested =
          JavaFileObjects.forSourceString(
              "com.example.Enclosing",
              """
              package com.example;

              import org.higherkindedj.optics.annotations.Flatten;
              import org.higherkindedj.optics.annotations.GenerateMapping;
              import org.higherkindedj.optics.annotations.MappingSpec;

              public final class Enclosing {
                private record Hidden(String street, String city, String postcode) {}

                public record Holder(String name, Hidden address) {}

                public record HolderDto(String name, String street, String city, String postcode) {}

                @GenerateMapping
                public interface HolderMapping extends MappingSpec<Holder, HolderDto> {
                  @Flatten
                  Hidden address();
                }
              }
              """);
      Compilation compilation = compile(nested);
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten marker 'address' names 'Hidden', which cannot be reached from"
                  + " 'com.example'");
    }

    @Test
    @DisplayName("a local marker naming no domain component is refused, with a suggestion")
    void markerNamesNoComponent() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "TypoMapping",
                  """
                  @GenerateMapping
                  public interface TypoMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    Types.Address adress();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Flatten on 'adress' names no component of Customer");
      assertThat(compilation).hadErrorContaining("Did you mean 'address()'?");
    }

    @Test
    @DisplayName("a marker on a component that is not a record is refused")
    void markerOnNonRecord() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "NameMapping",
                  """
                  @GenerateMapping
                  public interface NameMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    String name();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'name' names a component of type String, which is not a record");
    }

    @Test
    @DisplayName("a wire component named after the flattened component is refused")
    void wireCarriesTheFlattenedName() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.BothDto",
              """
              package com.example;

              public record BothDto(
                  String name, String address, String street, String city, String postcode) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "BothMapping",
                  """
                  @GenerateMapping
                  public interface BothMapping extends MappingSpec<Types.Customer, BothDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'address' spreads a component while the wire also carries a component named 'address'");
    }

    @Test
    @DisplayName("an inner component sharing a name with the domain is refused")
    void innerNameCollidesWithDomain() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.CityCustomer",
              """
              package com.example;

              public record CityCustomer(String name, String city, Types.Address address) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              domain,
              spec(
                  "CityMapping",
                  """
                  @GenerateMapping
                  public interface CityMapping extends MappingSpec<CityCustomer, Types.CustomerDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'address' spreads a component 'city' that 'CityCustomer' also has");
    }

    @Test
    @DisplayName("two groups sharing an inner name are refused")
    void innerNameCollidesAcrossGroups() {
      JavaFileObject domain =
          JavaFileObjects.forSourceString(
              "com.example.TwoAddresses",
              """
              package com.example;

              public record TwoAddresses(String name, Types.Address home, Types.Address work) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              domain,
              spec(
                  "TwoMapping",
                  """
                  @GenerateMapping
                  public interface TwoMapping extends MappingSpec<TwoAddresses, Types.CustomerDto> {
                    @Flatten
                    Types.Address home();

                    @Flatten
                    Types.Address work();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'work' spreads a component 'street' that 'home' also has");
    }

    @Test
    @DisplayName("an inner component with no wire counterpart is refused, naming the record")
    void innerComponentWithoutCounterpart() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ShortDto",
              """
              package com.example;

              public record ShortDto(String name, String street, String city, String extra) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "ShortMapping",
                  """
                  @GenerateMapping
                  public interface ShortMapping extends MappingSpec<Types.Customer, ShortDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Address.postcode' (spread from 'address') has no wire counterpart named"
                  + " 'postcode'");
    }

    @Test
    @DisplayName("a bean-shaped wire is not supported yet")
    void beanWire() {
      JavaFileObject bean =
          JavaFileObjects.forSourceString(
              "com.example.CustomerBean",
              """
              package com.example;

              public class CustomerBean {
                private String name;
                private String street;
                private String city;
                private String postcode;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
                public String getStreet() { return street; }
                public void setStreet(String street) { this.street = street; }
                public String getCity() { return city; }
                public void setCity(String city) { this.city = city; }
                public String getPostcode() { return postcode; }
                public void setPostcode(String postcode) { this.postcode = postcode; }
              }
              """);
      Compilation compilation =
          compile(
              TYPES,
              bean,
              spec(
                  "BeanMapping",
                  """
                  @GenerateMapping
                  public interface BeanMapping extends MappingSpec<Types.Customer, CustomerBean> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'address' spreads across a bean-shaped wire (not supported yet)");
    }

    @Test
    @DisplayName("a generic spec is not supported yet")
    void genericSpec() {
      JavaFileObject generic =
          JavaFileObjects.forSourceString(
              "com.example.Boxes",
              """
              package com.example;

              public final class Boxes {
                public record Box<T>(T item, Types.Address address) {}

                public record BoxDto<T>(T item, String street, String city, String postcode) {}
              }
              """);
      Compilation compilation =
          compile(
              TYPES,
              generic,
              spec(
                  "BoxMapping",
                  """
                  @GenerateMapping
                  public interface BoxMapping<T> extends MappingSpec<Boxes.Box<T>, Boxes.BoxDto<T>> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'address' is declared on a generic spec (not supported yet)");
    }

    @Test
    @DisplayName("a projection wire is not supported yet")
    void projection() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.CardDto",
              """
              package com.example;

              public record CardDto(String street, String city, String postcode) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "CardMapping",
                  """
                  @GenerateMapping
                  public interface CardMapping extends MappingSpec<Types.Customer, CardDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'CardDto' has fewer components than 'Customer' spreads (not supported yet)");
    }

    @Test
    @DisplayName("a sparse UpdateSpec is not supported yet")
    void sparseUpdate() {
      JavaFileObject patch =
          JavaFileObjects.forSourceString(
              "com.example.CustomerPatch",
              """
              package com.example;

              public class CustomerPatch {
                private String name;

                public String getName() { return name; }
                public void setName(String name) { this.name = name; }
              }
              """);
      Compilation compilation =
          compile(
              TYPES,
              patch,
              spec(
                  "PatchMapping",
                  """
                  @GenerateMapping
                  public interface PatchMapping extends UpdateSpec<Types.Customer, CustomerPatch> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'address' has no meaning on a sparse UpdateSpec (not supported yet)");
    }

    @Test
    @DisplayName("a group wider than one fields() ladder is not supported yet")
    void wideGroup() {
      StringBuilder components = new StringBuilder();
      StringBuilder wireComponents = new StringBuilder("String name");
      for (int i = 1; i <= 17; i++) {
        components.append(i > 1 ? ", " : "").append("String f").append(i);
        wireComponents.append(", String f").append(i);
      }
      JavaFileObject wide =
          JavaFileObjects.forSourceString(
              "com.example.Wide",
              """
              package com.example;

              public final class Wide {
                public record Inner(%s) {}

                public record Outer(String name, Inner inner) {}

                public record OuterDto(%s) {}
              }
              """
                  .formatted(components, wireComponents));
      Compilation compilation =
          compile(
              wide,
              spec(
                  "WideMapping",
                  """
                  @GenerateMapping
                  public interface WideMapping extends MappingSpec<Wide.Outer, Wide.OuterDto> {
                    @Flatten
                    Wide.Inner inner();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'inner' spreads Inner, which has more than 16 components (not"
                  + " supported yet)");
    }

    @Test
    @DisplayName("a leaf-shaped marker is refused")
    void leafShapedMarker() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "LeafMarkerMapping",
                  """
                  @GenerateMapping
                  public interface LeafMarkerMapping
                      extends MappingSpec<Types.CodedCustomer, Types.CustomerDto> {
                    @Flatten
                    ValidatedPrism<String, Types.Postcode> postcode();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Flatten method 'postcode' returns a ValidatedPrism");
    }

    @Test
    @DisplayName("a marker whose return type is not the component's is refused")
    void markerReturnTypeMismatch() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "DriftedMapping",
                  """
                  @GenerateMapping
                  public interface DriftedMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    Types.Contact address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten marker 'address' returns Types.Contact, not the component's own type");
      assertThat(compilation).hadErrorContaining("Declare the marker as 'Types.Address address()'");
    }

    @Test
    @DisplayName("a record with no components is refused")
    void emptyInnerRecord() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "HollowMapping",
                  """
                  @GenerateMapping
                  public interface HollowMapping extends MappingSpec<Types.Hollow, Types.HollowDto> {
                    @Flatten
                    Types.Empty empty();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@Flatten on 'empty' spreads Empty, which has no components");
    }

    @Test
    @DisplayName("a wildcard-carrying group type is not supported yet")
    void wildcardGroupType() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "WildMapping",
                  """
                  @GenerateMapping
                  public interface WildMapping extends MappingSpec<Types.Wild, Types.BoxDto> {
                    @Flatten
                    Types.Box<?> box();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'box' spreads Types.Box<?>, a raw or wildcard-carrying type (not supported"
                  + " yet)");
    }

    @Test
    @DisplayName("a raw group type is not supported yet")
    void rawGroupType() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "RawMapping",
                  """
                  @GenerateMapping
                  public interface RawMapping extends MappingSpec<Types.Raw, Types.BoxDto> {
                    @Flatten
                    @SuppressWarnings("rawtypes")
                    Types.Box box();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'box' spreads Types.Box, a raw or wildcard-carrying type (not supported"
                  + " yet)");
    }

    @Test
    @DisplayName("a wire with components no group fills is refused, naming the groups")
    void wireWithUnfilledComponents() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.ExtraDto",
              """
              package com.example;

              public record ExtraDto(
                  String name, String street, String city, String postcode, String extra) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "ExtraMapping",
                  """
                  @GenerateMapping
                  public interface ExtraMapping extends MappingSpec<Types.Customer, ExtraDto> {
                    @Flatten
                    Types.Address address();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "'ExtraDto' has more components than 'Customer' fills, flattened [address]"
                  + " included.");
      assertThat(compilation).hadErrorContaining("with an '@Flatten' marker named after it");
    }

    @Test
    @DisplayName("a marker naming a component of a group is not supported yet")
    void secondLevelMarker() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "DeepSiteMapping",
                  """
                  @GenerateMapping
                  public interface DeepSiteMapping extends MappingSpec<Types.Site, Types.SiteDto> {
                    @Flatten
                    Types.Located address();

                    @Flatten
                    Types.Geo geo();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@Flatten on 'geo' names a component of the flattened group 'address' (not"
                  + " supported yet)");
      assertThat(compilation)
          .hadErrorContaining("Give the inner pair its own spec, or flatten 'geo' into Located");
    }

    @Test
    @DisplayName("a derived field named after an inner component is refused")
    void derivedFieldShadowsAnInnerComponent() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "DerivedMapping",
                  """
                  @GenerateMapping
                  public interface DerivedMapping extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    Types.Address address();

                    default Getter<Types.Customer, String> street() {
                      return Getter.of(customer -> customer.address().street().toUpperCase());
                    }
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "derived field 'street' fills a wire component the flattened component 'address'"
                  + " already spreads into");
    }

    @Test
    @DisplayName(
        "a renamed inner component colliding with a domain component's wire name is refused")
    void renamedInnerCollidesWithADomainComponent() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "CollidingMapping",
                  """
                  @GenerateMapping
                  public interface CollidingMapping
                      extends MappingSpec<Types.Customer, Types.CustomerDto> {
                    @Flatten
                    Types.Address address();

                    @MapField(to = "name")
                    String street();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain components 'name' and 'address.street' both map to wire component 'name'");
    }

    @Test
    @DisplayName(
        "a rename named after a flattened component, inherited beside the marker, is refused")
    void renameNamedAfterAFlattenedComponent() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "FlattenVocabulary",
                  """
                  public interface FlattenVocabulary {
                    @Flatten
                    Types.Address address();
                  }
                  """),
              spec(
                  "RenameVocabulary",
                  """
                  public interface RenameVocabulary {
                    @MapField(to = "street")
                    Types.Address address();
                  }
                  """),
              spec(
                  "TornMapping",
                  """
                  @GenerateMapping
                  public interface TornMapping
                      extends MappingSpec<Types.Customer, Types.CustomerDto>,
                          FlattenVocabulary,
                          RenameVocabulary {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "@MapField method 'address' (inherited from 'RenameVocabulary') names a flattened"
                  + " component");
    }

    @Test
    @DisplayName("a local bridge naming nothing on the domain or in a group is refused")
    void bridgeNamingNothingBesideAGroup() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "TypoBridgeMapping",
                  """
                  @GenerateMapping
                  public interface TypoBridgeMapping extends MappingSpec<Types.Member, Types.MemberDto> {
                    @Flatten
                    Types.Reach reach();

                    @OptionalBridge
                    Optional<String> fx();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'fx' names no component of Member");
      // The group's components are candidates too.
      assertThat(compilation).hadErrorContaining("Did you mean 'fax()'?");
    }

    @Test
    @DisplayName("a record component with no counterpart is offered @Flatten")
    void recordComponentWithoutCounterpartIsOfferedFlatten() {
      JavaFileObject wire =
          JavaFileObjects.forSourceString(
              "com.example.HomeDto",
              """
              package com.example;

              public record HomeDto(String name, String home) {}
              """);
      Compilation compilation =
          compile(
              TYPES,
              wire,
              spec(
                  "HomeMapping",
                  """
                  @GenerateMapping
                  public interface HomeMapping extends MappingSpec<Types.Customer, HomeDto> {}
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining(
              "domain field 'Customer.address' has no wire counterpart named 'address'");
      assertThat(compilation)
          .hadErrorContaining(
              "if the wire carries the components of Types.Address as flat fields, spread it with"
                  + " '@Flatten Types.Address address();'");
    }

    @Test
    @DisplayName("a bridge named after an inner component that is not Optional names its record")
    void bridgeOnANonOptionalInnerComponent() {
      Compilation compilation =
          compile(
              TYPES,
              spec(
                  "PhoneBridgeMapping",
                  """
                  @GenerateMapping
                  public interface PhoneBridgeMapping extends MappingSpec<Types.Member, Types.MemberDto> {
                    @Flatten
                    Types.Reach reach();

                    @OptionalBridge
                    String phone();
                  }
                  """));
      assertThat(compilation).failed();
      assertThat(compilation)
          .hadErrorContaining("@OptionalBridge on 'phone' names a component that is not Optional");
      assertThat(compilation)
          .hadErrorContaining("'Reach.phone' is java.lang.String, which has no absent state");
    }
  }
}

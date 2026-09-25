"""The mapping compiler-message catalogue, as data: one entry per message, each with the
reproducer that provokes it. build_messages.py compiles every reproducer, captures the message
the processor really prints, and renders the page from it."""

FIXTURE_IMPORTS = """import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.higherkindedj.hkt.error.ErrorEnvelope;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.annotations.Flatten;
import org.higherkindedj.optics.annotations.GenerateErrorEnvelope;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MapKey;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.OptionalBridge;
import org.higherkindedj.optics.annotations.Unmapped;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.StandardCodecs;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.NonNull;
"""

FIXTURE_TYPES = """record EmailAddress(String value) {}

final class EmailCodecs {
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  static final ValidatedPrism<String, Optional<EmailAddress>> OPTIONAL_EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.isEmpty()
                  ? Validated.validNel(Optional.empty())
                  : EMAIL.parse(raw).map(Optional::of),
          email -> email.map(EmailAddress::value).orElse(""));

  private EmailCodecs() {}
}

final class LabelCodecs {
  static final ValidatedPrism<Map<String, String>, Map<Locale, String>> LABELS =
      ValidatedPrism.of(
          raw -> Validated.validNel(Map.of()),
          labels -> Map.of());

  private LabelCodecs() {}
}
"""

# (group title, group id, [entries]). README.md beside this file describes each entry's fields.
GROUPS = [
    ("Spec members", "spec-members", [
        dict(id="no-wire-counterpart",
             heading="domain field 'X.y' has no wire counterpart named 'y'",
             fragment="has no wire counterpart named",
             meaning="A domain component has no wire component of the same name, and no rename points it at one.",
             fix="Rename one side so the names match, or add a rename to the spec, such as `@MapField(to = \"fullName\") String name();`.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Customer(String name) {}

record CustomerDto(String fullName) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}"""),
        dict(id="no-usable-source",
             heading="target field 'XDto.y' has no usable source",
             fragment="has no usable source",
             meaning="A wire component's type differs from its domain counterpart's, and nothing converts between them.",
             fix="Add the method the message spells out, a `default ValidatedPrism` that parses the field: a [standard codec](codecs.md#standard-codecs) such as `StandardCodecs.uuid()`, or your own. Where one side is a primitive, the message asks for its wrapper type first. A `List` against a `Set` lands here too, because elements map only when both sides declare the same container.",
             rule=("Validated leaves", "basics.md#validated-leaves"),
             code="""record Customer(EmailAddress email) {}

record CustomerDto(String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}"""),
        dict(id="leaf-names-no-component",
             heading="leaf 'x' names no component of Y",
             fragment="names no component of",
             marker="A leaf is a zero-parameter",
             display="leaf '…' names no component of",
             meaning="A leaf declared on the spec is named after nothing the domain has, which is usually a typo.",
             fix="Rename it after the component it parses; the message names the nearest one when it is close. Make it `private` or `static` if it is a helper.",
             rule=("How the two `default` families are told apart", "rules.md#how-the-two-default-families-are-told-apart"),
             code="""record Customer(EmailAddress email) {}

record CustomerDto(String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> emial() {
    return EmailCodecs.EMAIL;
  }
}"""),
        dict(id="redeclares-the-mapping",
             heading="abstract method 'x' redeclares the mapping itself",
             fragment="redeclares the mapping itself",
             meaning="The spec declares a mapping method, as a MapStruct mapper would; here the spec declares only vocabulary, and the Impl generates the methods.",
             fix="Delete the method, and call the generated Impl's `build` or `parse`.",
             rule=("Your first mapping", "basics.md#your-first-mapping"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  CustomerDto toDto(Customer customer);
}"""),
        dict(id="collides-with-a-generated-member",
             heading="'build(X)' collides with the 'build' member the generated XImpl emits",
             fragment="collides with the",
             meaning="The spec declares a method the generated Impl also declares, so it would be overridden or break the generated file.",
             fix="Rename the method, or remove it and rely on the generated one. To change how one component maps, declare a leaf named after it.",
             rule=("Validated leaves", "basics.md#validated-leaves"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default CustomerDto build(Customer customer) {
    return new CustomerDto(customer.name());
  }
}"""),
        dict(id="wire-has-more-components",
             heading="'XDto' has more components than 'X'",
             fragment="has more components than",
             meaning="The wire has components nothing on the domain fills, so `build` cannot write them.",
             fix="Remove the extra wire components, add domain components to match, derive them with `default Getter` methods, or spread a nested domain record across them with `@Flatten`.",
             rule=("Derived wire fields", "basics.md#derived-wire-fields"),
             code="""record Customer(String name) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}"""),
        dict(id="projection-field-has-no-domain-source",
             heading="projection field 'XDto.y' has no domain source",
             fragment="has no domain source",
             meaning="A wire with fewer components than the domain maps as a projection, and one of its components is named after nothing the domain has.",
             fix="Align the component names, or add a `@MapField` rename.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Customer(String name, String email) {}

record CustomerDto(String nmae) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}"""),
        dict(id="rename-names-no-component",
             heading="@MapField(to = \"y\") on 'x' names no component of XDto",
             fragment="names no component of",
             marker="Point 'to' at an existing wire component",
             display="@MapField(to = …) on '…' names no component of",
             meaning="A rename's `to` names nothing the wire has, which is usually a typo.",
             fix="Point `to` at an existing wire component; the message lists them.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Customer(String name) {}

record CustomerDto(String fullName) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  @MapField(to = "fulName")
  String name();
}"""),
        dict(id="derived-field-names-no-component",
             heading="derived field method 'x' names no component of XDto",
             fragment="names no component of",
             marker="its name must be the wire component build fills",
             display="derived field method '…' names no component of",
             meaning="A derived field is named after nothing the wire has, which is usually a typo.",
             fix="Rename the method after the wire component it derives, or remove it.",
             rule=("Derived wire fields", "basics.md#derived-wire-fields"),
             code="""record Customer(String name) {}

record CustomerDto(String name, String displayName) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default Getter<Customer, String> dispalyName() {
    return Getter.of(Customer::name);
  }
}"""),
        dict(id="both-map-to-one-wire-component",
             heading="domain components 'x' and 'y' both map to wire component 'y'",
             fragment="both map to wire component",
             meaning="A rename points at a wire component another domain component already fills by name.",
             fix="Point the rename at a different wire component.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Person(String first, String name) {}

record PersonDto(String name, String other) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "name")
  String first();
}"""),
        dict(id="rename-already-claimed",
             heading="@MapField(to = \"y\") on 'x' targets a wire component another rename already claims",
             fragment="targets a wire component another rename already claims",
             meaning="Two renames point at one wire component, and each wire component takes exactly one source.",
             fix="Point each rename at a different wire component.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Person(String first, String last) {}

record PersonDto(String name) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {
  @MapField(to = "name")
  String first();

  @MapField(to = "name")
  String last();
}"""),
        dict(id="neither-rename-leaf-bridge",
             heading="abstract method 'x' is neither a rename, a leaf, nor a bridge",
             fragment="is neither a rename, a leaf, nor a bridge",
             meaning="An abstract method on the spec carries nothing that says what it declares, so the generated Impl cannot implement it.",
             fix="Give it a `default` body, make it a `@MapField` rename, or mark it `@OptionalBridge`.",
             rule=("Renames", "basics.md#renames-mapfield"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  String displayName();
}"""),
        dict(id="getter-named-after-domain",
             heading="default method 'x' returns a Getter but is named after a domain component",
             fragment="returns a Getter but is named after a domain component",
             meaning="A derived field carries the name of a domain component, where the processor expects a leaf.",
             fix="Name it after the wire-only component it derives, or return a `ValidatedPrism` to make it a leaf.",
             rule=("How the two `default` families are told apart", "rules.md#how-the-two-default-families-are-told-apart"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default Getter<Customer, String> name() {
    return Getter.of(customer -> customer.name().toUpperCase(Locale.ROOT));
  }
}"""),
        dict(id="projection-with-derived-fields",
             heading="'XDto' combines a projection with derived fields",
             fragment="combines a projection with derived fields",
             meaning="A wire with fewer components than the domain (a projection) also declares a derived field, and the write-back could never keep a value `build` recomputes.",
             fix="Drop the derived field and map the smaller wire as a plain projection, or give the wire every domain component.",
             rule=("Derived fields and the emission tiers", "rules.md#derived-fields-and-the-emission-tiers"),
             code="""record Employee(String name, String dept, int age) {}

record BadgeDto(String name, String label) {}

@GenerateMapping
interface BadgeMapping extends MappingSpec<Employee, BadgeDto> {
  default Getter<Employee, String> label() {
    return Getter.of(employee -> employee.name() + " (" + employee.dept() + ")");
  }
}"""),
        dict(id="own-type-parameters",
             heading="abstract method 'x' declares type parameters of its own",
             fragment="declares type parameters of its own",
             meaning="An abstract leaf, rename or marker declares its own `<R>`, which the generated Impl has nowhere to declare.",
             fix="Give the method a concrete type. Element types belong on the spec's own type parameters.",
             rule=("The boundaries of a generic spec", "rules.md#generic-boundaries"),
             code="""record Page(List<String> items) {}

record PageDto(List<String> entries) {}

@GenerateMapping
interface PageMapping extends MappingSpec<Page, PageDto> {
  @MapField(to = "entries")
  <R> List<R> items();
}"""),
        dict(id="cannot-be-reached",
             heading="@MapField method 'x' names 'T', which cannot be reached from 'p'",
             fragment="which cannot be reached from",
             meaning="A member names a type the spec's package cannot see, and the Impl is generated in that package.",
             fix="Make the type, and the types enclosing it, `public`, or declare the spec in the package they are visible from.",
             rule=("A member's type must be visible from the spec's package", "rules.md#how-the-two-default-families-are-told-apart"),
             code="""class Shop {
  private record Sku(String value) {}

  record Item(Sku sku) {}

  record ItemDto(Sku code) {}

  @GenerateMapping
  interface ItemMapping extends MappingSpec<Item, ItemDto> {
    @MapField(to = "code")
    Sku sku();
  }
}"""),
    ]),
    ("Optional fields", "optional-fields", [
        dict(id="add-optional-bridge",
             heading="... has no usable source ... Add '@OptionalBridge ...' to the spec",
             fragment="Add '@OptionalBridge",
             meaning="A domain `Optional` faces a plain wire component, and the spec has not said that `null` means *absent*.",
             fix="Add the `@OptionalBridge` marker the message spells out. The whole-`Optional` leaf it offers second also compiles, but reports a `null` as a field error instead of reading it as empty.",
             rule=("Optional fields: `@OptionalBridge`", "absence.md#optional-bridge"),
             code="""record Reader(String name, Optional<String> nickname) {}

record ReaderDto(String name, String nickname) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {}"""),
        dict(id="bridges-to-a-primitive",
             heading="@OptionalBridge on 'x' bridges to the primitive record component 'x'",
             fragment="bridges to the primitive",
             meaning="The bridged wire component is a primitive, which cannot hold the `null` an empty `Optional` becomes.",
             fix="Declare the wire component as the wrapper type, `Integer` for an `int`.",
             rule=("A bridged component must take `null`", "rules.md#bridged-component-nullable"),
             code="""record Reader(String name, Optional<Integer> age) {}

record ReaderDto(String name, int age) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
  @OptionalBridge
  Optional<Integer> age();
}"""),
        dict(id="declared-non-null",
             heading="domain field 'X.y' is Optional<T>, bridged to the record component 'XDto.y', which is declared non-null",
             fragment="which is declared non-null",
             meaning="The bridged wire component is declared non-null, by an annotation or by a JSpecify `@NullMarked` scope.",
             fix="Mark it `@Nullable`, since it carries absence. Otherwise drop the `Optional`, or encode absence in a leaf over the whole `Optional`.",
             rule=("A bridged component must take `null`", "rules.md#bridged-component-nullable"),
             code="""record Reader(String name, Optional<String> nickname) {}

record ReaderDto(String name, @NonNull String nickname) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
  @OptionalBridge
  Optional<String> nickname();
}"""),
        dict(id="bridged-leaf-over-whole-optional",
             heading="@OptionalBridge leaf 'x' is declared over the whole Optional",
             fragment="is declared over the whole Optional",
             meaning="A bridged leaf converts the element the bridge found, but this one is declared over the `Optional` itself.",
             fix="Declare the leaf over the element types, or drop `@OptionalBridge` to keep a leaf over the whole `Optional`.",
             rule=("Optional fields: `@OptionalBridge`", "absence.md#optional-bridge"),
             code="""record Reader(String name, Optional<EmailAddress> email) {}

record ReaderDto(String name, String email) {}

@GenerateMapping
interface ReaderMapping extends MappingSpec<Reader, ReaderDto> {
  @OptionalBridge
  default ValidatedPrism<String, Optional<EmailAddress>> email() {
    return EmailCodecs.OPTIONAL_EMAIL;
  }
}"""),
        dict(id="redundant-on-a-bean-wire", kind="note",
             heading="@OptionalBridge on 'x' is redundant on a bean wire",
             fragment="is redundant on a bean wire",
             meaning="A bean wire bridges a domain `Optional` without the marker, so the annotation changes nothing.",
             fix="Remove it. A marker that a record-wire sibling also needs belongs on a shared mix-in, where it draws no note.",
             rule=("Optional fields: `@OptionalBridge`", "absence.md#optional-bridge"),
             code="""record Guest(String name, Optional<String> nickname) {}

class GuestBean {
  private String name;
  private String nickname;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
}

@GenerateMapping
interface GuestMapping extends MappingSpec<Guest, GuestBean> {
  @OptionalBridge
  Optional<String> nickname();
}"""),
    ]),
    ("Shared vocabulary", "shared-vocabulary", [
        dict(id="mix-in-is-a-spec",
             heading="mix-in 'X' is itself a mapping spec",
             fragment="is itself a mapping spec",
             meaning="A spec extends another spec as if it were a vocabulary; a spec generates an Impl, a mix-in only shares members.",
             fix="Move the shared renames and leaves onto a plain interface, and extend that instead.",
             rule=("Mix-in shapes the processor refuses", "rules.md#refused-mix-in-shapes"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

record Supplier(String name) {}

record SupplierDto(String name) {}

@GenerateMapping
interface SupplierMapping extends MappingSpec<Supplier, SupplierDto> {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto>, SupplierMapping {}"""),
        dict(id="extended-raw",
             heading="mix-in 'X' is extended raw by the spec",
             fragment="is extended raw by",
             meaning="A generic mix-in is extended without its type arguments, so every member it contributes arrives erased.",
             fix="Name the type arguments where the mix-in is extended, as `extends Renames<String>`.",
             rule=("A generic mix-in reached raw", "rules.md#a-generic-mix-in-reached-raw"),
             code="""interface Renames<T> {
  @MapField(to = "fullName")
  T name();
}

record Customer(String name) {}

record CustomerDto(String fullName) {}

@SuppressWarnings("rawtypes")
@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto>, Renames {}"""),
        dict(id="reached-through-raw",
             heading="mix-in 'X' is reached through 'Y', which the spec extends raw",
             fragment="which the spec extends raw",
             marker="extends raw",
             meaning="A generic mix-in sits behind an interface the spec extends raw, and the erasure reaches every member below it.",
             fix="Name the type arguments on the clause the message names, as `extends Middle<String>`: that is the line to edit, not the mix-in whose members went missing.",
             rule=("A generic mix-in reached raw", "rules.md#a-generic-mix-in-reached-raw"),
             code="""interface Renames<T> {
  @MapField(to = "fullName")
  T name();
}

interface Middle<T> extends Renames<T> {}

record Customer(String name) {}

record CustomerDto(String fullName) {}

@SuppressWarnings("rawtypes")
@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto>, Middle {}"""),
        dict(id="conflicting-renames",
             heading="component 'x' has conflicting renames",
             fragment="has conflicting renames",
             meaning="Two mix-ins rename the same component to different wire components, and neither overrides the other.",
             fix="Declare the rename on the spec itself, which overrides both, or align the mix-ins on one target.",
             rule=("Inheriting one member twice", "rules.md#inheriting-one-member-twice"),
             code="""interface BillingNames {
  @MapField(to = "fullName")
  String name();
}

interface DisplayNames {
  @MapField(to = "displayName")
  String name();
}

record Customer(String name, String nickname) {}

record CustomerDto(String fullName, String displayName) {}

@GenerateMapping
interface CustomerMapping
    extends MappingSpec<Customer, CustomerDto>, BillingNames, DisplayNames {}"""),
    ]),
    ("Containers", "containers", [
        dict(id="array-constructor",
             heading="field 'x' is an array whose element type T cannot name an array constructor",
             fragment="cannot name an array constructor",
             meaning="Lifting an array builds a new one, and Java cannot create an array of a type variable or a parameterised type.",
             fix="Declare the component as a `List` on both sides, or map the arrays whole with the leaf the message spells out.",
             rule=("What lifts, and what does not", "rules.md#what-lifts"),
             code="""record Tag(String value) {}

record TagDto(String value) {}

@GenerateMapping
interface TagMapping extends MappingSpec<Tag, TagDto> {}

record Board(List<Tag>[] rows) {}

record BoardDto(List<TagDto>[] rows) {}

@GenerateMapping
interface BoardMapping extends MappingSpec<Board, BoardDto> {}"""),
        dict(id="key-leaf-never-runs",
             heading="@MapKey(\"x\") leaf 'xKey()' never runs",
             fragment="never runs",
             meaning="A key leaf sits beside a leaf over the whole `Map`, which is tried first, so the key leaf would never convert anything.",
             fix="Follow the message: drop the whole-map leaf (the values then copy, where their types match, or take the value leaf it offers), or drop the key leaf to keep converting the map whole.",
             rule=("A key leaf beside a whole-map leaf", "rules.md#key-leaf-beside-a-whole-map-leaf"),
             code="""record Catalogue(Map<Locale, String> labels) {}

record CatalogueDto(Map<String, String> labels) {}

@GenerateMapping
interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {
  default ValidatedPrism<Map<String, String>, Map<Locale, String>> labels() {
    return LabelCodecs.LABELS;
  }

  @MapKey("labels")
  default ValidatedPrism<String, Locale> labelKeys() {
    return StandardCodecs.locale();
  }
}"""),
        dict(id="raw-map",
             heading="@MapKey(\"x\") names a raw Map component",
             fragment="names a raw Map component",
             meaning="A key leaf converts the key type, and a raw `Map` declares none.",
             fix="Declare both type arguments on the component, such as `Map<Locale, String>`.",
             rule=("What lifts, and what does not", "rules.md#what-lifts"),
             code="""@SuppressWarnings("rawtypes")
record Catalogue(Map labels) {}

@SuppressWarnings("rawtypes")
record CatalogueDto(Map labels) {}

@GenerateMapping
interface CatalogueMapping extends MappingSpec<Catalogue, CatalogueDto> {
  @MapKey("labels")
  default ValidatedPrism<String, Locale> labelKeys() {
    return StandardCodecs.locale();
  }
}"""),
        dict(id="more-than-one-spec",
             heading="field 'x' matches more than one mapping spec",
             fragment="matches more than one mapping spec",
             meaning="Two specs map the same pair, so a component of that pair has no single spec to nest through.",
             fix="Remove the duplicate spec, or add the leaf the message spells out, delegating to the spec you mean.",
             rule=("How a dependency's specs are found", "rules.md#how-a-dependencys-specs-are-found"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

@GenerateMapping
interface LegacyCustomerMapping extends MappingSpec<Customer, CustomerDto> {}

record Invoice(Customer customer) {}

record InvoiceDto(CustomerDto customer) {}

@GenerateMapping
interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}"""),
        dict(id="subtype-has-no-spec",
             heading="permitted subtype 'X' of 'Y' has no mapping spec",
             fragment="has no mapping spec",
             meaning="A sealed pair has a domain subtype that no spec maps, so the generated switch would miss a case.",
             fix="Declare a spec for the subtype pair, in this module or in a dependency compiled with `hkj-processor`.",
             rule=("Sealed hierarchies", "structure.md#sealed-hierarchies"),
             code="""sealed interface Payment permits Card, Bank {}

record Card(String number) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String number) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}"""),
        dict(id="subtype-never-produced",
             heading="permitted subtype 'X' of 'Y' is never produced",
             fragment="is never produced",
             meaning="A sealed wire has a subtype that no spec maps a domain subtype to, so `parse` would have nowhere to send it.",
             fix="Add a domain subtype and a spec mapping it to the wire subtype, or remove the wire subtype from the sealed interface.",
             rule=("Sealed hierarchies", "structure.md#sealed-hierarchies"),
             code="""sealed interface Payment permits Card, Bank {}

record Card(String number) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto, CashDto {}

record CardDto(String number) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

record CashDto(String currency) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface BankMapping extends MappingSpec<Bank, BankDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}"""),
        dict(id="subtype-targeted-twice",
             heading="permitted subtype 'X' of 'Y' is targeted by more than one domain subtype",
             fragment="is targeted by more than one domain subtype",
             meaning="Two domain subtypes map to one wire subtype, so `parse` could not tell which to build.",
             fix="Give each domain subtype its own wire subtype.",
             rule=("Sealed hierarchies", "structure.md#sealed-hierarchies"),
             code="""sealed interface Payment permits Card, GiftCard {}

record Card(String number) implements Payment {}

record GiftCard(String number) implements Payment {}

sealed interface PaymentDto permits CardDto {}

record CardDto(String number) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface GiftCardMapping extends MappingSpec<GiftCard, CardDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}"""),
        dict(id="no-meaning-on-a-sealed-mapping",
             heading="leaf 'x' has no meaning on a sealed mapping",
             fragment="has no meaning on a sealed mapping",
             meaning="A sealed spec declares a leaf, derived field, rename or marker, but a dispatch has no components to bind it to.",
             fix="Move the method onto the spec of the subtype pair it belongs to.",
             rule=("Spec members on a sealed mapping", "rules.md#how-the-two-default-families-are-told-apart"),
             code="""sealed interface Contact permits Email {}

record Email(EmailAddress address) implements Contact {}

sealed interface ContactDto permits EmailDto {}

record EmailDto(String address) implements ContactDto {}

@GenerateMapping
interface EmailMapping extends MappingSpec<Email, EmailDto> {
  default ValidatedPrism<String, EmailAddress> address() {
    return EmailCodecs.EMAIL;
  }
}

@GenerateMapping
interface ContactMapping extends MappingSpec<Contact, ContactDto> {
  default ValidatedPrism<String, EmailAddress> address() {
    return EmailCodecs.EMAIL;
  }
}"""),
    ]),
    ("Flattening", "flattening", [
        dict(id="spreads-a-shared-name",
             heading="@Flatten on 'x' spreads a component 'y' that 'Z' also has",
             fragment="spreads a component",
             meaning="A flattened record has a component named like one of the domain's own, so both would claim the same wire component.",
             fix="Rename one of the two record components.",
             rule=("Names in a flattened group", "rules.md#names-in-a-flattened-group"),
             code="""record Address(String street, String city) {}

record Customer(String name, String city, Address address) {}

record CustomerDto(String name, String city, String street) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  @Flatten
  Address address();
}"""),
        dict(id="spreads-across-a-bean",
             heading="@Flatten on 'x' spreads across a bean-shaped wire (not supported yet)",
             fragment="spreads across a bean-shaped wire",
             meaning="A flattened group is read from and written to record components, and this wire is a bean.",
             fix="Map the pair with a record wire.",
             rule=("Where a flattened component can appear", "rules.md#where-flattening-applies"),
             code="""record Address(String street, String city) {}

record Customer(String name, Address address) {}

class CustomerBean {
  private String name;
  private String street;
  private String city;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getStreet() { return street; }
  public void setStreet(String street) { this.street = street; }
  public String getCity() { return city; }
  public void setCity(String city) { this.city = city; }
}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerBean> {
  @Flatten
  Address address();
}"""),
        dict(id="flattens-a-group-member",
             heading="@Flatten on 'x' names a component of the flattened group 'y' (not supported yet)",
             fragment="names a component of the flattened group",
             meaning="A second marker tries to spread a record that sits inside a group already spread, and spreading is one level deep.",
             fix="Give the inner pair a spec of its own, against a nested wire component, or move the inner record's components into `Address` itself.",
             rule=("Where a flattened component can appear", "rules.md#where-flattening-applies"),
             code="""record Geo(String lat) {}

record Address(String street, Geo geo) {}

record Customer(String name, Address address) {}

record CustomerDto(String name, String street, String lat) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  @Flatten
  Address address();

  @Flatten
  Geo geo();
}"""),
    ]),
    ("Bean wires", "bean-wires", [
        dict(id="bean-domain",
             heading="the domain type argument 'X' is a bean-shaped class, which this mapper does not support on the domain side",
             fragment="does not support on the domain side",
             meaning="The domain is a bean, but `parse` builds the domain through a record's canonical constructor.",
             fix="Make the domain a record, or a sealed interface of records, and map the bean as the wire.",
             rule=("How a bean is read and written", "rules.md#how-a-bean-is-read-and-written"),
             code="""class Customer {
  private String name;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}

record CustomerDto(String name) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}"""),
        dict(id="setter-with-no-getter",
             heading="bean property 'x' on 'Y' has a setter but no getter, or a getter but no setter, so the mapping leaves it out",
             fragment="so the mapping leaves it out",
             meaning="An accessor has no partner, and leaving it out would drop a domain component, or on a PATCH bean ignore a value the client sends.",
             fix="Add the missing accessor, or correct the misspelt one the message names. An accessor meant to stay out takes an `@Unmapped` marker.",
             rule=("When an unpaired accessor is refused", "rules.md#unpaired-accessors"),
             code="""record Customer(String name, String email) {}

class CustomerBean {
  private String name;
  private String email;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getEmial() { return email; }
  public void setEmail(String email) { this.email = email; }
}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerBean> {}"""),
        dict(id="unmapped-names-no-accessor",
             heading="@Unmapped method 'x' names no accessor 'Y' leaves out",
             fragment="names no accessor",
             meaning="An `@Unmapped` marker names something the bean does not leave unpaired, which is usually a misspelling.",
             fix="Name the marker after the property of the accessor meant to stay out, which the message lists, or remove it.",
             rule=("Accessors meant to stay out", "beans.md#accessors-meant-to-stay-out"),
             code="""record Customer(String name) {}

class CustomerBean {
  private String name;
  private String id;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getId() { return id; }
}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerBean> {
  @Unmapped
  String ident();
}"""),
        dict(id="unmapped-names-a-mapped-property",
             heading="@Unmapped method 'x' names a property 'Y' maps",
             fragment="names a property",
             meaning="An `@Unmapped` marker names a property the bean both reads and writes, so the mapping carries it anyway.",
             fix="Remove the marker. To leave the property out, remove one of its accessors.",
             rule=("Accessors meant to stay out", "beans.md#accessors-meant-to-stay-out"),
             code="""record Customer(String name) {}

class CustomerBean {
  private String name;
  private String id;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerBean> {
  @Unmapped
  String id();
}"""),
        dict(id="getter-only-list-build",
             heading="bean property 'x' on 'Y' is a getter-only List, which a build cannot fill (not supported yet)",
             fragment="which a build cannot fill",
             meaning="A getter-only `List` is raw or has a wildcard, so the generated `addAll` that fills it cannot be written.",
             fix="Declare the list's element type on the getter, or give the property a setter.",
             rule=("A getter-only `List` must name its element type", "rules.md#getter-only-list-element-type"),
             code="""record Order(List<String> items) {}

@SuppressWarnings("rawtypes")
class OrderBean {
  private List items;

  public List getItems() {
    if (items == null) {
      items = new ArrayList();
    }
    return items;
  }
}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderBean> {}"""),
        dict(id="bridged-to-a-getter-only-list",
             heading="domain field 'X.y' is Optional<List<T>>, bridged to the getter-only bean property 'y' (not supported yet)",
             fragment="bridged to the getter-only bean property",
             meaning="A domain `Optional` faces a getter-only `List`, which creates its list on first call and so can never read as absent.",
             fix="Drop the `Optional` so the empty list carries the meaning, or give the property a setter and a getter that returns what was set.",
             rule=("A getter-only `List` refuses the bridge", "rules.md#getter-only-list-refuses-the-bridge"),
             code="""record Order(Optional<List<String>> items) {}

class OrderBean {
  private List<String> items;

  public List<String> getItems() {
    if (items == null) {
      items = new ArrayList<>();
    }
    return items;
  }
}

@GenerateMapping
interface OrderMapping extends MappingSpec<Order, OrderBean> {}"""),
        dict(id="reads-some-writes-others",
             heading="'X' is not a usable bean-shaped wire: no property it reads is one it can write",
             fragment="no property it reads is one it can write",
             meaning="The bean reads some names and writes others, which fits neither a two-way nor a one-way mapping.",
             fix="Pair each getter with its setter, which a misspelling usually explains, or remove the accessors of the direction the wire never crosses.",
             rule=("How a bean's direction is read", "rules.md#how-a-beans-direction-is-read"),
             code="""record Customer(String name, String email) {}

class CustomerBean {
  private String name;
  private String email;

  public String getName() { return name; }
  public void setEmail(String email) { this.email = email; }
}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerBean> {}"""),
    ]),
    ("Sparse PATCH", "sparse-patch", [
        dict(id="extends-both-tiers",
             heading="'X' extends both 'MappingSpec<...>' and 'UpdateSpec<...>'",
             fragment="extends both 'MappingSpec",
             meaning="One spec asks for both tiers, and the two emit disjoint members, so no Impl can answer both.",
             fix="Declare a spec per tier, and let a plain mix-in carry the renames and leaves they share.",
             rule=("One tier per spec", "rules.md#one-tier-per-spec"),
             code="""record Customer(String name) {}

class CustomerPatch {
  private String name;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
}

@GenerateMapping
interface CustomerMapping
    extends MappingSpec<Customer, CustomerPatch>, UpdateSpec<Customer, CustomerPatch> {}"""),
        dict(id="primitive-patch-property",
             heading="the wire property 'x' is primitive and can never be absent",
             fragment="is primitive and can never be absent",
             meaning="A PATCH property is a primitive, which always carries a value, so an omitted field could not be told apart from one sent as `0`.",
             fix="Declare the property as the wrapper type, `Integer` for an `int`.",
             rule=("No primitive wire property", "rules.md#no-primitive-patch-property"),
             code="""record Customer(String name, int age) {}

class CustomerPatch {
  private String name;
  private int age;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public int getAge() { return age; }
  public void setAge(int age) { this.age = age; }
}

@GenerateMapping
interface CustomerPatchMapping extends UpdateSpec<Customer, CustomerPatch> {}"""),
        dict(id="record-patch-wire",
             heading="the wire 'X' is a record, which a sparse UpdateSpec cannot map",
             fragment="which a sparse UpdateSpec cannot map",
             meaning="A PATCH wire is a record, whose components are always present, so absence cannot be expressed.",
             fix="Use a bean-shaped PATCH DTO with wrapper-typed properties, or a full `MappingSpec` if you meant a total mapping.",
             rule=("No record wire", "rules.md#no-record-patch-wire"),
             code="""record Customer(String name) {}

record CustomerPatch(String name) {}

@GenerateMapping
interface CustomerPatchMapping extends UpdateSpec<Customer, CustomerPatch> {}"""),
        dict(id="getter-only-list-patch",
             heading="bean property 'x' on 'Y' is a getter-only List<T>, which cannot carry a sparse update's absence (not supported yet)",
             fragment="cannot carry a sparse update's absence",
             meaning="A getter-only `List` never reads `null`, so a request that omits it would clear the domain value.",
             fix="Give the property a setter, and a getter that answers `null` until it is set.",
             rule=("No getter-only `List` property", "rules.md#no-getter-only-list-on-a-patch"),
             code="""record Order(List<String> items) {}

class OrderPatch {
  private List<String> items;

  public List<String> getItems() {
    if (items == null) {
      items = new ArrayList<>();
    }
    return items;
  }
}

@GenerateMapping
interface OrderPatchMapping extends UpdateSpec<Order, OrderPatch> {}"""),
        dict(id="optional-on-a-patch",
             heading="the wire property 'x' bridges the domain Optional component X.x, which a sparse update cannot express",
             fragment="which a sparse update cannot express",
             meaning="A plain PATCH property faces a domain `Optional`, and `null` already means *leave unchanged*, so nothing is left to mean *set to empty*.",
             fix="Declare the property as an `Optional`, with the field starting `null` rather than `Optional.empty()`.",
             rule=("No plain property bridged to a domain `Optional`", "rules.md#no-optional-bridge-on-a-patch"),
             code="""record Customer(String name, Optional<String> nickname) {}

class CustomerPatch {
  private String name;
  private String nickname;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getNickname() { return nickname; }
  public void setNickname(String nickname) { this.nickname = nickname; }
}

@GenerateMapping
interface CustomerPatchMapping extends UpdateSpec<Customer, CustomerPatch> {}"""),
    ]),
    ("Generic specs", "generic-specs", [
        dict(id="generic-bean-or-patch",
             heading="'X' is generic, which this mapper does not support",
             fragment="is generic, which this mapper does not support",
             meaning="The spec, its domain or its wire declares type parameters where a generic mapping cannot go: a bean or PATCH wire, or a sealed hierarchy, which is refused even at a concrete instantiation.",
             fix="Give a bean, PATCH or sealed mapping non-generic domain and wire types, or model the generic type as a record.",
             rule=("The boundaries of a generic spec", "rules.md#generic-boundaries"),
             code="""record Page<T>(List<T> items) {}

class PageBean<T> {
  private List<T> items;

  public List<T> getItems() { return items; }
  public void setItems(List<T> items) { this.items = items; }
}

@GenerateMapping
interface PageMapping<T> extends MappingSpec<Page<T>, PageBean<T>> {}"""),
        dict(id="abstract-leaf-needs-a-generic-spec",
             heading="abstract leaf 'x' needs a generic spec",
             fragment="needs a generic spec",
             meaning="A concrete spec declares a leaf with no body, and only an element-mapped generic spec defers a leaf to `of(...)`.",
             fix="Give the leaf a `default` body, or make the spec generic in the element types.",
             rule=("The boundaries of a generic spec", "rules.md#generic-boundaries"),
             code="""record Customer(EmailAddress email) {}

record CustomerDto(String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  ValidatedPrism<String, EmailAddress> email();
}"""),
    ]),
    ("Merge and error envelopes", "merge-and-error-envelopes", [
        dict(id="merge-component-ambiguous",
             heading="@GenerateMerge: target component 'x' is ambiguous: [...] both carry it",
             fragment="both carry it",
             meaning="Two sources carry a component the target needs, and every target component takes exactly one source.",
             fix="Rename the component on all but one source.",
             rule=("How a merge fills", "rules.md#how-a-merge-fills"),
             code="""record Customer(String name) {}

record Account(String name, String iban) {}

record Summary(String name, String iban) {}

@GenerateMerge
interface SummaryMerge {
  Summary merge(Customer customer, Account account);
}"""),
        dict(id="merge-plain-return",
             heading="@GenerateMerge: 'x' uses fallible fills but declares a plain 'T' return",
             fragment="uses fallible fills but declares a plain",
             meaning="A merge converts through a leaf, which can fail, but its signature promises a plain value.",
             fix="Declare the `Validated<NonEmptyList<FieldError>, T>` return the message spells out.",
             rule=("How a merge fills", "rules.md#how-a-merge-fills"),
             code="""record Contact(String email) {}

record Account(String iban) {}

record Summary(EmailAddress email, String iban) {}

@GenerateMerge
interface SummaryMerge {
  Summary merge(Contact contact, Account account);

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}"""),
        dict(id="merge-validated-identity",
             heading="@GenerateMerge: 'x' declares a Validated return but every fill is an identity copy",
             fragment="declares a Validated return but every fill is an identity copy",
             meaning="Every fill copies its source, so the merge cannot fail, but its signature says it can.",
             fix="Declare the plain target return, or give a component a leaf that can fail, which makes the `Validated` return true.",
             rule=("How a merge fills", "rules.md#how-a-merge-fills"),
             code="""record User(String name, String email) {}

record Settings(boolean darkMode) {}

record Header(String name, boolean darkMode) {}

@GenerateMerge
interface HeaderAssembly {
  Validated<NonEmptyList<FieldError>, Header> assemble(User user, Settings settings);
}"""),
        dict(id="merge-unfilled",
             heading="@GenerateMerge: target component 'x' is not filled by any source",
             fragment="is not filled by any source",
             meaning="No source has a component of that name, and a merge matches by name only: it has no rename.",
             fix="Rename a source component to match, or add a source that carries it.",
             rule=("How a merge fills", "rules.md#how-a-merge-fills"),
             code="""record Order(String orderId) {}

record Customer(String name) {}

record OrderView(String orderId, String customerName) {}

@GenerateMerge
interface OrderViewAssembly {
  OrderView assemble(Order order, Customer customer);
}"""),
        dict(id="envelope-primitive-context",
             heading="@GenerateErrorEnvelope: context component 'x' of 'C' is a primitive",
             fragment="is a primitive",
             meaning="The all-absent context holds `null` in every component, which a primitive cannot.",
             fix="Declare the component as a reference type, `Integer` for an `int`.",
             rule=("Error envelope rules", "rules.md#error-envelope-rules"),
             code="""record OrderContext(String orderId, int attempt) {}

@GenerateErrorEnvelope
sealed interface OrderError {
  record NotFound(String id, ErrorEnvelope<OrderContext> envelope) implements OrderError {}
}"""),
        dict(id="envelope-variant-not-a-record",
             heading="@GenerateErrorEnvelope: permitted variant 'X' of 'Y' is a class, not a record",
             fragment="is a class, not a record",
             meaning="The companion builds each variant from its record components, and this variant is a class.",
             fix="Make the variant a record carrying its own components and one `ErrorEnvelope<...>`.",
             rule=("Error envelope rules", "rules.md#error-envelope-rules"),
             code="""record OrderContext(String orderId) {}

@GenerateErrorEnvelope
sealed interface OrderError permits NotFound {}

final class NotFound implements OrderError {}"""),
        dict(id="envelope-generic",
             heading="@GenerateErrorEnvelope: 'X' is generic, which this companion does not support",
             fragment="which this companion does not support",
             meaning="The error hierarchy declares type parameters, which the generated factories would have no way to name.",
             fix="Declare the hierarchy, its variants and the context without type parameters.",
             rule=("Error envelope rules", "rules.md#error-envelope-rules"),
             code="""record OrderContext(String orderId) {}

@GenerateErrorEnvelope
sealed interface OrderError<T> {
  record NotFound<T>(T id, ErrorEnvelope<OrderContext> envelope) implements OrderError<T> {}
}"""),
    ]),
    ("Inside a generated Impl", "inside-a-generated-impl", [
        dict(id="private-access-in-an-impl",
             heading="XImpl.java: error: T has private access in Y",
             fragment="has private access in",
             meaning="A mapped component's type is `private` and nested in the class that holds the spec, and the Impl, generated beside that class, cannot see it. The processor lets this through, where it refuses the same type on a rename or marker.",
             fix="Make the nested type package-private, or `public`.",
             rule=("A member's type must be visible from the spec's package", "rules.md#how-the-two-default-families-are-told-apart"),
             code="""class Shop {
  private record Sku(String value) {}

  record Item(Sku sku) {}

  record ItemDto(Sku sku) {}

  @GenerateMapping
  interface ItemMapping extends MappingSpec<Item, ItemDto> {}
}"""),
    ]),
    ("At your call site", "at-your-call-site", [
        dict(id="no-impl-class",
             heading="cannot find symbol: class XMappingImpl",
             fragment="cannot find symbol",
             display="cannot find symbol … class XMappingImpl",
             marker="class CustomerMappingImpl",
             meaning="The generated Impl does not exist. Most often the spec was refused, which writes no Impl, so every use of it reports this too.",
             fix="If the build also printed a `@GenerateMapping:` error, fix that first. Otherwise annotate the spec, and check that `hkj-processor` is on the annotation processor path, not only the compile classpath. Called as `CustomerMappingImpl.INSTANCE.build(...)`, the same cause reads `package CustomerMappingImpl does not exist`.",
             rule=("Your first mapping", "basics.md#your-first-mapping"),
             code="""record Customer(String name) {}

record CustomerDto(String name) {}

interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {}

CustomerMappingImpl mapper = CustomerMappingImpl.INSTANCE;"""),
        dict(id="no-such-surface",
             heading="cannot find symbol: method asIso()",
             fragment="cannot find symbol",
             display="cannot find symbol … method asIso()",
             marker="method asIso()",
             meaning="The Impl has no such method, because your spec's shape does not support it: a leaf withholds `asIso()`, a projection has no `parse`, a one-directional bean has only `build` or only `parse`, and an `UpdateSpec` has only `updateFrom`.",
             fix="Call a method your spec's shape gets; [the method table](tiers.md#which-methods-your-spec-gets) says when each appears.",
             rule=("Which methods your spec gets", "tiers.md#which-methods-your-spec-gets"),
             code="""record Customer(EmailAddress email) {}

record CustomerDto(String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

var iso = CustomerMappingImpl.INSTANCE.asIso();"""),
    ]),
]

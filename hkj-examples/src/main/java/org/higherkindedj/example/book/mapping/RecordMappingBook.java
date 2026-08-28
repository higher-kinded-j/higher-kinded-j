// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.List;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.GenerateMerge;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.edit.Edits;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/mapping/ch_intro.html">Record Mapping</a> page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API. Change a spec here and the page changes with it; break one
 * and the build fails before the page can lie.
 *
 * <p>The specs are deliberately <b>top-level</b>, not nested in this class. A nested spec joins its
 * enclosing simple names, so {@code Shop.CustomerMapping} would generate {@code
 * ShopCustomerMappingImpl}. Top-level, it generates {@code CustomerMappingImpl} - which is the name
 * the page teaches.
 */
public final class RecordMappingBook {

  private RecordMappingBook() {}

  public static void main(String[] args) {
    // ANCHOR: basics_usage
    Person person = new Person("Ada", 36);

    // Same-named, same-typed components match automatically:
    PersonDto dto = PersonMappingImpl.INSTANCE.build(person); // total
    Validated<NonEmptyList<FieldError>, Person> back =
        PersonMappingImpl.INSTANCE.parse(dto); // accumulating, located
    // ANCHOR_END: basics_usage
    System.out.println(dto + " / " + back);

    // ANCHOR: leaf_usage
    CustomerMappingImpl.INSTANCE.parse(new CustomerDto("Bob", "not-an-email"));
    // Invalid(NonEmptyList[email: not an email address])
    // ANCHOR_END: leaf_usage
    System.out.println(CustomerMappingImpl.INSTANCE.parse(new CustomerDto("Bob", "not-an-email")));

    // ANCHOR: derived_usage
    ProfileMappingImpl.INSTANCE.build(new Profile("Ada", "Lovelace"));
    // ProfileDto[first=Ada, last=Lovelace, displayName=Ada Lovelace]
    // ANCHOR_END: derived_usage
    System.out.println(ProfileMappingImpl.INSTANCE.build(new Profile("Ada", "Lovelace")));

    // ANCHOR: mixin_usage
    // One vocabulary, two mappings - the inherited rename and leaf apply to both:
    ClientMappingImpl.INSTANCE.parse(new ClientDto("Ada Lovelace", "ada@example.org"));
    SupplierMappingImpl.INSTANCE.parse(new SupplierDto("Acme Ltd", "sales@acme.example", "01"));
    // ANCHOR_END: mixin_usage
    System.out.println(
        ClientMappingImpl.INSTANCE.parse(new ClientDto("Ada Lovelace", "not-an-email")));

    // ANCHOR: nesting_usage
    InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto("Bob", "nope")));
    // Invalid(NonEmptyList[customer.email: not an email address])
    // ANCHOR_END: nesting_usage
    System.out.println(
        InvoiceMappingImpl.INSTANCE.parse(new InvoiceDto("INV-2", new CustomerDto("Bob", "nope"))));

    // ANCHOR: generic_usage
    CustomerPageMappingImpl.INSTANCE.parse(
        new PageDto<>(
            List.of(new CustomerDto("Ada", "ada@corp.example"), new CustomerDto("Bob", "nope")),
            2));
    // Invalid(NonEmptyList[items.1.email: not an email address])
    // ANCHOR_END: generic_usage
    System.out.println(
        CustomerPageMappingImpl.INSTANCE.parse(
            new PageDto<>(
                List.of(new CustomerDto("Ada", "ada@corp.example"), new CustomerDto("Bob", "nope")),
                2)));

    // ANCHOR: threaded_usage
    // One generic Impl serves every instantiation; identity elements copy through.
    Page<String> tags = new Page<>(List.of("fp", "hkt"), 2);
    PageDto<String> tagsDto = PageMappingImpl.<String>instance().build(tags);
    Validated<NonEmptyList<FieldError>, Page<Integer>> counts =
        PageMappingImpl.<Integer>instance().parse(new PageDto<>(List.of(1, 2, 3), 3));
    // ANCHOR_END: threaded_usage
    System.out.println(tagsDto + " / " + counts);

    // ANCHOR: threaded_inferred
    PageMapping<String> inferredWitness = PageMappingImpl.instance(); // witness inferred
    // ANCHOR_END: threaded_inferred
    System.out.println(inferredWitness);

    // ANCHOR: element_usage
    // One spec, any element codec: each abstract leaf arrives as a prism through of(...).
    Validated<NonEmptyList<FieldError>, Page<EmailAddress>> mail =
        CodecPageMappingImpl.of(EmailCodecs.EMAIL)
            .parse(new PageDto<>(List.of("ada@example.org", "nope"), 2));
    // Invalid(NonEmptyList[items.1: not an email address])
    // ANCHOR_END: element_usage
    System.out.println(mail);

    // ANCHOR: projection_usage
    Employee employee = new Employee("Ada", "Research", 36);
    Lens<Employee, EmployeeCardDto> badge = EmployeeCardMappingImpl.INSTANCE.asLens();
    Employee moved = badge.set(new EmployeeCardDto("Ada", "Platform"), employee);
    // ANCHOR_END: projection_usage
    System.out.println(moved);

    // ANCHOR: leaf_projection_usage
    Subscriber subscriber = new Subscriber("7", new EmailAddress("ada@corp.example"), 36);

    // The projected components validate and write back; the unprojected id survives untouched.
    Validated<NonEmptyList<FieldError>, Subscriber> renewed =
        SubscriberDetailsMappingImpl.INSTANCE.patch(
            subscriber, new SubscriberDetailsDto("grace@corp.example", 37));
    // Valid(Subscriber[id=7, email=EmailAddress[value=grace@corp.example], age=37])

    // Dense semantics: every projected field applies - a null is a located error, never absence.
    SubscriberDetailsMappingImpl.INSTANCE.patch(subscriber, new SubscriberDetailsDto(null, 37));
    // Invalid(NonEmptyList[email: must not be null])
    // ANCHOR_END: leaf_projection_usage
    System.out.println(renewed);
    System.out.println(
        SubscriberDetailsMappingImpl.INSTANCE.patch(
            subscriber, new SubscriberDetailsDto(null, 37)));

    // ANCHOR: bean_usage
    Customer ada = new Customer("Ada", new EmailAddress("ada@corp.example"));
    ContactBean bean =
        ContactMappingImpl.INSTANCE.build(ada); // new ContactBean(); setName; setEmail
    Validated<NonEmptyList<FieldError>, Customer> fromBean =
        ContactMappingImpl.INSTANCE.parse(bean);
    // A null bean property parses to a located FieldError, e.g. [email: must not be null].
    // ANCHOR_END: bean_usage
    System.out.println(bean.getName() + " / " + fromBean);

    // ANCHOR: update_usage
    Customer current = new Customer("Ada", new EmailAddress("ada@corp.example"));

    ContactPatchBean patch = new ContactPatchBean();
    patch.setName("Ada Lovelace"); // email left null: not provided, keep the current one

    Edits.Accumulated<Customer> update = ContactPatchMappingImpl.INSTANCE.updateFrom(patch);
    Validated<NonEmptyList<FieldError>, Customer> patched = update.apply(current);
    // Valid(Customer[name=Ada Lovelace, email=ada@corp.example]) - only the name changed
    // ANCHOR_END: update_usage
    System.out.println(patched);

    // ANCHOR: update_container_usage
    Roster roster = new Roster("core", List.of(new PhoneNumber("+44")));

    RosterPatchBean rosterPatch = new RosterPatchBean();
    rosterPatch.setPhones(List.of("+1", "nope")); // a present list replaces wholesale...

    Validated<NonEmptyList<FieldError>, Roster> rosterPatched =
        RosterPatchMappingImpl.INSTANCE.updateFrom(rosterPatch).apply(roster);
    // ...but each element parses through the phones() leaf, located:
    // Invalid(NonEmptyList[phones.1: not a phone number])
    // ANCHOR_END: update_container_usage
    System.out.println(rosterPatched);

    // ANCHOR: merge_usage
    Dashboard dashboard =
        DashboardAssemblyImpl.INSTANCE.assemble(
            new User("Ada", "ada@corp.example"),
            new Account("GB29-XXXX", 4200),
            new Settings(true));
    // ANCHOR_END: merge_usage
    System.out.println(dashboard);

    System.out.println(
        ProfileCardAssemblyImpl.INSTANCE.assemble(
            new User("Ada", "ada@corp.example"), new Wrapper(new CustomerDto("Bob", "nope"))));
  }
}

// ANCHOR: email_leaf
record EmailAddress(String value) {}

final class EmailCodecs {
  static final ValidatedPrism<String, EmailAddress> EMAIL =
      ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);

  private EmailCodecs() {}
}

// ANCHOR_END: email_leaf

// ANCHOR: basics_spec
record Person(String name, int age) {}

record PersonDto(String name, int age) {}

@GenerateMapping
interface PersonMapping extends MappingSpec<Person, PersonDto> {}

// ANCHOR_END: basics_spec

// ANCHOR: leaf_spec
record Customer(String name, EmailAddress email) {}

record CustomerDto(String name, String email) {}

@GenerateMapping
interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
  default ValidatedPrism<String, EmailAddress> email() { // wire first, domain second
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: leaf_spec

// ANCHOR: rename_spec
record PersonCardDto(String fullName, int age) {}

@GenerateMapping
interface PersonCardMapping extends MappingSpec<Person, PersonCardDto> {
  @MapField(to = "fullName")
  String name(); // Person.name <-> PersonCardDto.fullName
}

// ANCHOR_END: rename_spec

// ANCHOR: derived_spec
record Profile(String first, String last) {}

record ProfileDto(String first, String last, String displayName) {}

@GenerateMapping
interface ProfileMapping extends MappingSpec<Profile, ProfileDto> {
  default Getter<Profile, String> displayName() {
    return Getter.of(p -> p.first() + " " + p.last());
  }
}

// ANCHOR_END: derived_spec

// ANCHOR: mixin_spec
// Plain vocabulary - not a spec itself. Any spec whose records share these
// shapes extends it alongside MappingSpec.
interface ContactVocabulary {
  @MapField(to = "fullName")
  String name();

  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

record Client(String name, EmailAddress email) {}

record ClientDto(String fullName, String email) {}

@GenerateMapping
interface ClientMapping extends ContactVocabulary, MappingSpec<Client, ClientDto> {}

record Supplier(String name, EmailAddress email, String phone) {}

record SupplierDto(String fullName, String email, String phone) {}

@GenerateMapping
interface SupplierMapping extends ContactVocabulary, MappingSpec<Supplier, SupplierDto> {}

// ANCHOR_END: mixin_spec

// ANCHOR: generic_mixin_spec
// Vocabulary parameterised by the type it speaks about: one interface, reused wherever the
// domain component happens to land.
interface Renames<T> {
  @MapField(to = "fullName")
  T name();
}

// The interface below it says what T is. The spec never repeats it, and never sees a T.
interface TextRenames extends Renames<String> {}

record Contractor(String name, EmailAddress email) {}

record ContractorDto(String fullName, String email) {}

@GenerateMapping
interface ContractorMapping extends TextRenames, MappingSpec<Contractor, ContractorDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: generic_mixin_spec

// ANCHOR: nesting_spec
record Invoice(String id, Customer customer) {}

record InvoiceDto(String id, CustomerDto customer) {}

@GenerateMapping
interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

// ANCHOR_END: nesting_spec

// ANCHOR: generic_spec
record Page<T>(List<T> items, int total) {}

record PageDto<T>(List<T> items, int total) {}

@GenerateMapping
interface CustomerPageMapping extends MappingSpec<Page<Customer>, PageDto<CustomerDto>> {}

// ANCHOR_END: generic_spec

// ANCHOR: threaded_spec
@GenerateMapping
interface PageMapping<T> extends MappingSpec<Page<T>, PageDto<T>> {}

// one Impl for every T: PageMappingImpl.<String>instance(), .<Integer>instance(), ...

// ANCHOR_END: threaded_spec

// ANCHOR: element_spec
// The element mapping is deliberately open: an abstract leaf, supplied at of(...) time.
@GenerateMapping
interface CodecPageMapping<T, TDto> extends MappingSpec<Page<T>, PageDto<TDto>> {
  ValidatedPrism<TDto, T> items();
}

// ANCHOR_END: element_spec

// ANCHOR: sealed_spec
sealed interface Payment permits Card, Bank {}

record Card(String pan) implements Payment {}

record Bank(String iban) implements Payment {}

sealed interface PaymentDto permits CardDto, BankDto {}

record CardDto(String pan) implements PaymentDto {}

record BankDto(String iban) implements PaymentDto {}

@GenerateMapping
interface CardMapping extends MappingSpec<Card, CardDto> {}

@GenerateMapping
interface BankMapping extends MappingSpec<Bank, BankDto> {}

@GenerateMapping
interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

// ANCHOR_END: sealed_spec

// ANCHOR: projection_spec
record Employee(String name, String department, int age) {}

record EmployeeCardDto(String name, String department) {}

@GenerateMapping
interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}

// ANCHOR_END: projection_spec

// ANCHOR: leaf_projection_spec
record Subscriber(String id, EmailAddress email, int age) {}

record SubscriberDetailsDto(String email, int age) {}

@GenerateMapping
interface SubscriberDetailsMapping extends MappingSpec<Subscriber, SubscriberDetailsDto> {
  default ValidatedPrism<String, EmailAddress> email() {
    return ValidatedPrism.of(
        raw ->
            raw.contains("@")
                ? Validated.validNel(new EmailAddress(raw))
                : Validated.invalidNel(FieldError.of("not an email address")),
        EmailAddress::value);
  }
}

// ANCHOR_END: leaf_projection_spec

// ANCHOR: merge_spec
record User(String name, String email) {}

record Account(String iban, int balance) {}

record Settings(boolean darkMode) {}

record Dashboard(String name, String iban, boolean darkMode) {}

@GenerateMerge
interface DashboardAssembly {
  Dashboard assemble(User user, Account account, Settings settings);
}

// ANCHOR_END: merge_spec

// ANCHOR: bean_spec
// A generated, mutable getter/setter DTO - not a record, so not annotatable. The spec still sits
// on your interface, never on the bean, so a third-party bean maps without being touched.
class ContactBean {
  private String name;
  private String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

@GenerateMapping
interface ContactMapping extends MappingSpec<Customer, ContactBean> {
  // build() writes through setters; parse() reads through getters, null-guarded and located.
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: bean_spec

// ANCHOR: update_spec
// A PATCH request bean. Here null means "not provided, leave unchanged" - the opposite of the bean
// parse above, where null is broken data. That contract is opted into by extending UpdateSpec.
class ContactPatchBean {
  private String name;
  private String email;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }
}

@GenerateMapping
interface ContactPatchMapping extends UpdateSpec<Customer, ContactPatchBean> {
  // Generates only updateFrom(ContactPatchBean) : Edits.Accumulated<Customer> - no build/parse/as*.
  // A present field is set (email parsed through its leaf, located on failure); an absent (null)
  // one is skipped, so the domain's current value survives.
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: update_spec

// ANCHOR: update_container
record PhoneNumber(String value) {}

record Roster(String team, List<PhoneNumber> phones) {}

record RosterDto(String team, List<String> phones) {} // the full tier's wire

// A PATCH bean whose phones property is a whole-list replacement, absent when null.
class RosterPatchBean {
  private String team;
  private List<String> phones;

  public String getTeam() {
    return team;
  }

  public void setTeam(String team) {
    this.team = team;
  }

  public List<String> getPhones() {
    return phones;
  }

  public void setPhones(List<String> phones) {
    this.phones = phones;
  }
}

// ONE element vocabulary: the leaf names the component and parses ELEMENTS.
interface PhoneVocabulary {
  default ValidatedPrism<String, PhoneNumber> phones() {
    return ValidatedPrism.of(
        raw ->
            raw.startsWith("+")
                ? Validated.validNel(new PhoneNumber(raw))
                : Validated.invalidNel(FieldError.of("not a phone number")),
        PhoneNumber::value);
  }
}

@GenerateMapping
interface RosterMapping extends PhoneVocabulary, MappingSpec<Roster, RosterDto> {}

// The full tier lifts the leaf elementwise: a bad element parses as phones.1.

@GenerateMapping
interface RosterPatchMapping extends PhoneVocabulary, UpdateSpec<Roster, RosterPatchBean> {}

// The sparse tier lifts the SAME leaf: a present list replaces wholesale, each element parsed,
// failures located phones.1 - one vocabulary, both tiers.

// ANCHOR_END: update_container

// ANCHOR: nested_merge_spec
record Wrapper(CustomerDto customer) {} // the wire side

record ProfileCard(String name, Customer customer) {} // the domain side

@GenerateMerge
interface ProfileCardAssembly {
  // ProfileCard.customer fills from Wrapper.customer through CustomerMapping,
  // so a bad email is: Invalid(NonEmptyList[customer.email: not an email address])
  Validated<NonEmptyList<FieldError>, ProfileCard> assemble(User user, Wrapper wrapper);
}
// ANCHOR_END: nested_merge_spec

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.mapping;

import java.util.Optional;
import org.higherkindedj.hkt.nonemptylist.NonEmptyList;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.annotations.Unmapped;
import org.higherkindedj.optics.annotations.UpdateSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/mapping/beans.html">Bean-Shaped Wires</a> page.
 *
 * <p>The book does not paraphrase this file: it {@code {{#include}}}s the anchored regions below,
 * so the page cannot drift from the API, and {@code BookExampleOutputTest} runs {@code main} and
 * holds each output comment to what it prints.
 *
 * <p>The specs are top-level, not nested in the class: a nested spec joins its enclosing simple
 * names, so {@code Shop.CustomerMapping} would generate {@code ShopCustomerMappingImpl}, where the
 * page teaches {@code CustomerMappingImpl}.
 *
 * <p>Types several pages share, such as {@code Customer} and {@code EmailAddress}, are declared in
 * {@link BasicsBook}, beside the page that introduces them.
 */
public final class BeansBook {

  private BeansBook() {}

  public static void main(String[] args) {
    // ANCHOR: bean_projection_usage
    Employee researcher = new Employee("Ada", "Research", 36);
    TransferBean transfer = new TransferBean();
    transfer.setDepartment("Platform");
    TransferMappingImpl transferMapping = TransferMappingImpl.INSTANCE;

    // The bean's property can be unset, so the projection validates: patch, never a lens.
    Validated<NonEmptyList<FieldError>, Employee> transferred =
        transferMapping.patch(researcher, transfer);
    // Valid(Employee[name=Ada, department=Platform, age=36])

    // Dense, as on a record wire: an unset property is a located error, never "keep the current
    // value".
    Validated<NonEmptyList<FieldError>, Employee> unset =
        transferMapping.patch(researcher, new TransferBean());
    // Invalid(NonEmptyList[department: must not be null])
    // ANCHOR_END: bean_projection_usage
    System.out.println(transferred);
    System.out.println(unset);

    // ANCHOR: bean_usage
    Customer ada = new Customer("Ada", new EmailAddress("ada@corp.example"));
    ContactMappingImpl contactMapping = ContactMappingImpl.INSTANCE;

    ContactBean bean = contactMapping.build(ada); // new ContactBean(); setName; setEmail
    Validated<NonEmptyList<FieldError>, Customer> fromBean = contactMapping.parse(bean);
    // Valid(Customer[name=Ada, email=EmailAddress[value=ada@corp.example]])

    ContactBean noEmail = new ContactBean(); // a bean can exist with a property never set
    noEmail.setName("Bob");
    Validated<NonEmptyList<FieldError>, Customer> fromUnset = contactMapping.parse(noEmail);
    // Invalid(NonEmptyList[email: must not be null])
    // ANCHOR_END: bean_usage
    System.out.println(fromBean);
    System.out.println(fromUnset);

    // ANCHOR: one_way_usage
    // Parse-only: the Impl has parse and asValidatedParse(), and no build.
    Validated<NonEmptyList<FieldError>, Customer> read =
        CustomerViewMappingImpl.INSTANCE.parse(new CustomerView("Ada", "ada@corp.example"));
    // Valid(Customer[name=Ada, email=EmailAddress[value=ada@corp.example]])

    // Build-only: the Impl has build and asValidatedBuild(), and no parse.
    CustomerRequest request =
        CustomerRequestMappingImpl.INSTANCE.build(
            new Customer("Ada", new EmailAddress("ada@corp.example")));
    // new CustomerRequest(); setName("Ada"); setEmail("ada@corp.example")
    // ANCHOR_END: one_way_usage
    System.out.println(read);
    System.out.println(request.describe());

    // ANCHOR: unmapped_usage
    TenantPatchBean tenantPatch = new TenantPatchBean();
    tenantPatch.setName("Ada Lovelace");

    Validated<NonEmptyList<FieldError>, Tenant> tenantPatched =
        TenantPatchMappingImpl.INSTANCE.updateFrom(tenantPatch).apply(new Tenant("t-1", "Ada"));
    // Valid(Tenant[id=t-1, name=Ada Lovelace]) - the t-9 the bean reads is never applied
    // ANCHOR_END: unmapped_usage
    System.out.println(tenantPatched);
  }
}

// ANCHOR: bean_spec
// A generated, mutable getter/setter DTO - generated, so not yours to annotate. The spec still sits
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

// ANCHOR: bean_projection_spec
// A bean carrying one of Employee's three components: a projection. A record wire of that shape
// copies by identity and keeps asLens(); a bean's reference property can be unset, which a lens's
// set could not refuse, so the Impl emits the validated patch instead.
class TransferBean {
  private String department;

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }
}

@GenerateMapping
interface TransferMapping extends MappingSpec<Employee, TransferBean> {}

// ANCHOR_END: bean_projection_spec

// ANCHOR: one_way_spec
// A vendor's read model: built once by its own client, then only ever read. It has getters and
// nothing that writes it, so the mapping is parse-only.
class CustomerView {
  private final String name;
  private final String email;

  CustomerView(String name, String email) {
    this.name = name;
    this.email = email;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }
}

@GenerateMapping
interface CustomerViewMapping extends MappingSpec<Customer, CustomerView> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// An outbound request: filled and sent, never read back. It has setters and no getters, so the
// mapping is build-only.
class CustomerRequest {
  private String name;
  private String email;

  public void setName(String name) {
    this.name = name;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  String describe() {
    return name + " <" + email + ">";
  }
}

@GenerateMapping
interface CustomerRequestMapping extends MappingSpec<Customer, CustomerRequest> {
  default ValidatedPrism<String, EmailAddress> email() {
    return EmailCodecs.EMAIL;
  }
}

// ANCHOR_END: one_way_spec

// ANCHOR: unmapped_spec
// A tenant record whose id the server assigns, and a PATCH body shared with the GET response: it
// reads the id and has no setter for it, so the client cannot change it.
record Tenant(String id, String name) {}

class TenantPatchBean {
  private String name;

  public String getId() {
    return "t-9"; // whatever the body carries, the update never applies it
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}

@GenerateMapping
interface TenantPatchMapping extends UpdateSpec<Tenant, TenantPatchBean> {
  // getId() has no setter, so it is no property of the mapping, and 'id' names a component of
  // Tenant: without the marker the mapping is refused, in case the accessor is a misspelt pair.
  // The marker says the omission is deliberate. It withholds the refusal and nothing else: the
  // update folds 'name' and never reads getId().
  @Unmapped
  String id();
}

// ANCHOR_END: unmapped_spec

// ANCHOR: default_trap
// A product listing whose subtitle is optional, and two generated beans for it.
record Listing(String title, Optional<String> subtitle) {}

class DraftListingBean {
  private String title;
  private @Nullable String subtitle = ""; // starts as "" in the field

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public @Nullable String getSubtitle() {
    return subtitle;
  }

  public void setSubtitle(@Nullable String subtitle) {
    this.subtitle = subtitle;
  }
}

class ListingBean {
  private String title;
  private @Nullable String subtitle;

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSubtitle() {
    return subtitle == null ? "" : subtitle; // answers "" in the getter
  }

  public void setSubtitle(@Nullable String subtitle) { // the bridge writes null for empty
    this.subtitle = subtitle;
  }
}

@GenerateMapping
interface DraftListingMapping extends MappingSpec<Listing, DraftListingBean> {}

@GenerateMapping
interface ListingMapping extends MappingSpec<Listing, ListingBean> {} // subtitle bridges itself

// ANCHOR_END: default_trap

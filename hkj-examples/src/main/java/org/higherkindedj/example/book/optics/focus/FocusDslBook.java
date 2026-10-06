// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.focus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.book.optics.JsonNodeOptics;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/focus_dsl.html">Focus DSL</a> page. The
 * page {@code {{#include}}}s the anchored regions, and {@code FocusDslBookTest} holds the claims
 * the page makes about this code.
 */
public final class FocusDslBook {

  private FocusDslBook() {}

  /** What the page's first use computes, so the test can read each value. */
  record FirstUse(String city, User moved, User shouty) {}

  static FirstUse firstUse(User alice) {
    // ANCHOR: first_use
    String city = UserFocus.address().city().get(alice);

    User moved = UserFocus.address().city().set("Paris", alice);

    User shouty = UserFocus.address().city().modify(String::toUpperCase, alice);
    // ANCHOR_END: first_use
    return new FirstUse(city, moved, shouty);
  }

  /** The values the page's path-per-component block reads. */
  record PerComponent(String companyName, List<Department> departments, Optional<String> email) {}

  static PerComponent perComponent(Company company, Employee employee) {
    // ANCHOR: per_component
    // A plain field: exactly one focus
    FocusPath<Company, String> namePath = CompanyFocus.name();
    String companyName = namePath.get(company);

    // A List field: the processor has already stepped into the elements
    TraversalPath<Company, Department> deptPath = CompanyFocus.departments();
    List<Department> allDepts = deptPath.getAll(company);

    // An Optional field: zero or one focus
    AffinePath<Employee, String> emailPath = EmployeeFocus.email();
    Optional<String> email = emailPath.getOptional(employee);
    // ANCHOR_END: per_component
    return new PerComponent(companyName, allDepts, email);
  }

  /** The values the page's chained path reads and writes. */
  record Chained(List<String> names, Company updated) {}

  static Chained chained(Company company) {
    // ANCHOR: chained
    TraversalPath<Company, String> allEmployeeNames =
        CompanyFocus.departments().via(DepartmentFocus.employees()).via(EmployeeFocus.name());

    // Read every one of them
    List<String> names = allEmployeeNames.getAll(company);

    // Or update every one of them
    Company updated = allEmployeeNames.modifyAll(String::toUpperCase, company);
    // ANCHOR_END: chained
    return new Chained(names, updated);
  }

  /** The values the page's FocusPath block computes. */
  record FocusOps(String name, Employee updated, Employee modified) {}

  static FocusOps focusOps(Employee employee) {
    // ANCHOR: focus_ops
    FocusPath<Employee, String> namePath = EmployeeFocus.name();

    String name = namePath.get(employee); // always a value
    Employee updated = namePath.set("Bob", employee); // always succeeds
    Employee modified = namePath.modify(String::toUpperCase, employee);
    // ANCHOR_END: focus_ops
    return new FocusOps(name, updated, modified);
  }

  /** The values the page's AffinePath block computes. */
  record AffineOps(Optional<String> email, Employee updated, Employee modified, boolean hasEmail) {}

  static AffineOps affineOps(Employee employee) {
    // ANCHOR: affine_ops
    AffinePath<Employee, String> emailPath = EmployeeFocus.email();

    Optional<String> email = emailPath.getOptional(employee); // may be empty
    Employee updated = emailPath.set("new@example.com", employee); // writes even when absent
    Employee modified = emailPath.modify(String::toLowerCase, employee);
    boolean hasEmail = emailPath.matches(employee);
    // ANCHOR_END: affine_ops
    return new AffineOps(email, updated, modified, hasEmail);
  }

  /** The values the page's TraversalPath block computes. */
  record TraversalOps(List<Employee> all, Department updated, Department modified, int headcount) {}

  static TraversalOps traversalOps(Department department, Employee replacement) {
    // ANCHOR: traversal_ops
    TraversalPath<Department, Employee> employeesPath = DepartmentFocus.employees();

    List<Employee> all = employeesPath.getAll(department);
    Department updated = employeesPath.setAll(replacement, department);
    Department modified =
        employeesPath.modifyAll(
            employee -> EmployeeLenses.age().modify(age -> age + 1, employee), department);
    int headcount = employeesPath.count(department);
    // ANCHOR_END: traversal_ops
    return new TraversalOps(all, updated, modified, headcount);
  }

  /** Every row of the page's "Find your field" table, each spelled as the row says. */
  record Fields(
      FocusPath<Order, String> customerEmail,
      FocusPath<Order, String> reference,
      TraversalPath<Order, Integer> quantities,
      AffinePath<Order, String> giftMessage,
      AffinePath<Order, String> couponCode,
      AffinePath<Order, String> legacyNote,
      AffinePath<Order, String> channel,
      TraversalPath<Order, String> tags,
      AffinePath<Order, String> approvedBy,
      AffinePath<Order, Payment.Card> card,
      AffinePath<Order, Payment.Card> sameCard,
      AffinePath<Order, ObjectNode> payloadObject) {}

  static Fields findYourField() {
    // ANCHOR: find_your_field
    // A record with @GenerateFocus: with navigators on, the next field chains straight on
    FocusPath<Order, String> customerEmail = OrderFocus.customer().email();

    // A plain value: read and write it
    FocusPath<Order, String> reference = OrderFocus.reference();

    // A List, Set or Collection: already on the elements, so the next hop is .via(...)
    TraversalPath<Order, Integer> quantities = OrderFocus.lines().via(LineItemFocus.quantity());

    // An Optional, or a component with a recognised @Nullable: zero or one
    AffinePath<Order, String> giftMessage = OrderFocus.giftMessage();
    AffinePath<Order, String> couponCode = OrderFocus.couponCode();

    // A reference that may hold null, with no annotation: say so with .nullable()
    AffinePath<Order, String> legacyNote = OrderFocus.legacyNote().nullable();

    // A Map: the path focuses the whole map, and .atKey(k) picks one value
    AffinePath<Order, String> channel = OrderFocus.attributes().atKey("channel");

    // An array: the path focuses the whole array, and .each(...) steps into it
    TraversalPath<Order, String> tags = OrderFocus.tags().each(EachInstances.arrayEach());

    // An Either, Maybe, Try or Validated: zero or one, on the success side
    AffinePath<Order, String> approvedBy = OrderFocus.approvedBy();

    // A sealed type: a generated prism picks one variant, or instanceOf by runtime type
    AffinePath<Order, Payment.Card> card = OrderFocus.payment().via(PaymentPrisms.card());
    AffinePath<Order, Payment.Card> sameCard =
        OrderFocus.payment().via(AffinePath.instanceOf(Payment.Card.class));

    // A type you cannot annotate: compose the optics @ImportOptics generated for it
    AffinePath<Order, ObjectNode> payloadObject = OrderFocus.payload().via(JsonNodeOptics.object());
    // ANCHOR_END: find_your_field
    return new Fields(
        customerEmail,
        reference,
        quantities,
        giftMessage,
        couponCode,
        legacyNote,
        channel,
        tags,
        approvedBy,
        card,
        sameCard,
        payloadObject);
  }
}

// ANCHOR: first_records
@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record User(String name, Address address) {}

// ANCHOR_END: first_records

// ANCHOR: company_records
@GenerateLenses
@GenerateFocus
record Company(String name, List<Department> departments) {}

@GenerateLenses
@GenerateFocus
record Department(String name, List<Employee> employees) {}

@GenerateLenses
@GenerateFocus
record Employee(String name, int age, Optional<String> email) {}

// ANCHOR_END: company_records

// ANCHOR: field_records
@GenerateFocus(generateNavigators = true)
record Customer(String name, String email) {}

@GenerateFocus
record LineItem(String sku, int quantity) {}

@GeneratePrisms
sealed interface Payment permits Payment.Card, Payment.Invoice {
  record Card(String last4) implements Payment {}

  record Invoice(String terms) implements Payment {}
}

@GenerateFocus(generateNavigators = true)
record Order(
    Customer customer,
    String reference,
    List<LineItem> lines,
    Optional<String> giftMessage,
    @Nullable String couponCode,
    String legacyNote,
    Map<String, String> attributes,
    String[] tags,
    Either<String, String> approvedBy,
    Payment payment,
    JsonNode payload) {}
// ANCHOR_END: field_records

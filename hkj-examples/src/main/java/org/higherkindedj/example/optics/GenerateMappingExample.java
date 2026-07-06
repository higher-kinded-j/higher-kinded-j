// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.validated.FieldError;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateMapping;
import org.higherkindedj.optics.annotations.MapField;
import org.higherkindedj.optics.annotations.MappingSpec;
import org.higherkindedj.optics.validated.ValidatedPrism;

/**
 * Demonstrates {@code @GenerateMapping} (Step-0 slice): a compile-time, reflection-free
 * bidirectional record mapping with a total {@code build} and an accumulating, located {@code
 * parse}.
 *
 * <p>Key concepts demonstrated: the empty-spec happy path with {@code @MapField} renames and a
 * truthful {@code asIso()} (lossless tier); a validated-leaf mapping whose {@code parse} reports
 * every bad field at once, located by component name; {@code List}/{@code Optional} container
 * lifting; nesting (a spec delegating to a sibling spec, failures located by dotted path); a
 * self-recursive mapping over a tree; sealed-interface dispatch over permitted subtype pairs; and a
 * lossy projection (Lens tier) whose {@code asLens()} writes the projected components back.
 */
public final class GenerateMappingExample {

  public record Customer(String name, EmailAddress email) {}

  public record EmailAddress(String value) {}

  public record CustomerDto(String name, String email) {}

  @GenerateMapping
  public interface CustomerMapping extends MappingSpec<Customer, CustomerDto> {
    default ValidatedPrism<String, EmailAddress> email() {
      return ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);
    }
  }

  public record Person(String name, int age) {}

  public record PersonDto(String fullName, int age) {}

  @GenerateMapping
  public interface PersonMapping extends MappingSpec<Person, PersonDto> {
    @MapField(to = "fullName")
    String name();
  }

  public record Team(String name, List<EmailAddress> members, Optional<EmailAddress> lead) {}

  public record TeamDto(String name, List<String> members, Optional<String> lead) {}

  @GenerateMapping
  public interface TeamMapping extends MappingSpec<Team, TeamDto> {
    default ValidatedPrism<String, EmailAddress> members() {
      return emailPrism();
    }

    default ValidatedPrism<String, EmailAddress> lead() {
      return emailPrism();
    }

    private static ValidatedPrism<String, EmailAddress> emailPrism() {
      return ValidatedPrism.of(
          raw ->
              raw.contains("@")
                  ? Validated.validNel(new EmailAddress(raw))
                  : Validated.invalidNel(FieldError.of("not an email address")),
          EmailAddress::value);
    }
  }

  public record Invoice(String id, Customer customer) {}

  public record InvoiceDto(String id, CustomerDto customer) {}

  @GenerateMapping
  public interface InvoiceMapping extends MappingSpec<Invoice, InvoiceDto> {}

  public record Tree(String value, List<Tree> children) {}

  public record TreeDto(String value, List<TreeDto> children) {}

  @GenerateMapping
  public interface TreeMapping extends MappingSpec<Tree, TreeDto> {}

  public sealed interface Payment permits Card, Bank {}

  public record Card(String number) implements Payment {}

  public record Bank(String iban) implements Payment {}

  public sealed interface PaymentDto permits CardDto, BankDto {}

  public record CardDto(String number) implements PaymentDto {}

  public record BankDto(String iban) implements PaymentDto {}

  @GenerateMapping
  public interface CardMapping extends MappingSpec<Card, CardDto> {}

  @GenerateMapping
  public interface BankMapping extends MappingSpec<Bank, BankDto> {}

  @GenerateMapping
  public interface PaymentMapping extends MappingSpec<Payment, PaymentDto> {}

  public record Wallet(String owner, Payment payment) {}

  public record WalletDto(String owner, PaymentDto payment) {}

  @GenerateMapping
  public interface WalletMapping extends MappingSpec<Wallet, WalletDto> {}

  public record Employee(String name, String department, int age) {}

  public record EmployeeCardDto(String name, String department) {}

  @GenerateMapping
  public interface EmployeeCardMapping extends MappingSpec<Employee, EmployeeCardDto> {}

  public static void main(String[] args) {
    System.out.println("=== Validated Mapping Example ===");
    Customer ada = new Customer("Ada", new EmailAddress("ada@corp.example"));
    CustomerDto dto = GenerateMappingExampleCustomerMappingImpl.INSTANCE.build(ada);
    System.out.println("Built:        " + dto);
    System.out.println(
        "Round trip:   " + GenerateMappingExampleCustomerMappingImpl.INSTANCE.parse(dto));
    System.out.println(
        "Located fail: "
            + GenerateMappingExampleCustomerMappingImpl.INSTANCE.parse(
                new CustomerDto("Bob", "not-an-email")));
    System.out.println("Expected: built DTO, Valid(round trip), Invalid located at \"email\"\n");

    System.out.println("=== Lossless Mapping Example (renames + asIso) ===");
    Person grace = new Person("Grace", 36);
    PersonDto personDto = GenerateMappingExamplePersonMappingImpl.INSTANCE.build(grace);
    System.out.println("Built:      " + personDto);
    System.out.println(
        "Iso back:   "
            + GenerateMappingExamplePersonMappingImpl.INSTANCE.asIso().reverseGet(personDto));
    System.out.println(
        "Iso there:  " + GenerateMappingExamplePersonMappingImpl.INSTANCE.asIso().get(grace));
    System.out.println("Expected: fullName carries the rename; the Iso round-trips both ways\n");

    System.out.println("=== Container Lifting Example (List + Optional) ===");
    Team core =
        new Team(
            "Core",
            List.of(new EmailAddress("ada@corp.example"), new EmailAddress("grace@corp.example")),
            Optional.of(new EmailAddress("ada@corp.example")));
    TeamDto teamDto = GenerateMappingExampleTeamMappingImpl.INSTANCE.build(core);
    System.out.println("Built:        " + teamDto);
    System.out.println(
        "Round trip:   " + GenerateMappingExampleTeamMappingImpl.INSTANCE.parse(teamDto));
    System.out.println(
        "Absent lead:  "
            + GenerateMappingExampleTeamMappingImpl.INSTANCE.parse(
                new TeamDto("Core", List.of("ada@corp.example"), Optional.empty())));
    System.out.println(
        "Located fail: "
            + GenerateMappingExampleTeamMappingImpl.INSTANCE.parse(
                new TeamDto(
                    "Core", List.of("bad-one", "grace@corp.example"), Optional.of("bad-two"))));
    System.out.println(
        "Expected: strings in the DTO, Valid(round trip), Valid with empty lead, and BOTH bad"
            + " values reported at once\n");

    System.out.println("=== Nested Mapping Example (spec delegates to spec) ===");
    Invoice invoice =
        new Invoice("INV-1", new Customer("Ada", new EmailAddress("ada@corp.example")));
    InvoiceDto invoiceDto = GenerateMappingExampleInvoiceMappingImpl.INSTANCE.build(invoice);
    System.out.println("Built:        " + invoiceDto);
    System.out.println(
        "Round trip:   " + GenerateMappingExampleInvoiceMappingImpl.INSTANCE.parse(invoiceDto));
    System.out.println(
        "Located fail: "
            + GenerateMappingExampleInvoiceMappingImpl.INSTANCE.parse(
                new InvoiceDto("INV-2", new CustomerDto("Bob", "not-an-email"))));
    System.out.println(
        "Expected: nested DTO built by CustomerMapping, Valid(round trip), and the failure"
            + " located through the nesting\n");

    System.out.println("=== Self-Recursive Mapping Example (Tree of Trees) ===");
    Tree tree =
        new Tree(
            "root",
            List.of(
                new Tree("left", List.of(new Tree("leaf", List.of()))),
                new Tree("right", List.of())));
    TreeDto treeDto = GenerateMappingExampleTreeMappingImpl.INSTANCE.build(tree);
    System.out.println("Built:      " + treeDto);
    System.out.println(
        "Round trip: " + GenerateMappingExampleTreeMappingImpl.INSTANCE.parse(treeDto));
    System.out.println("Expected: depth-3 round trip terminates and is Valid\n");

    System.out.println("=== Sealed Dispatch Example ===");
    Payment card = new Card("4111-1111");
    PaymentDto paymentDto = GenerateMappingExamplePaymentMappingImpl.INSTANCE.build(card);
    System.out.println("Built:      " + paymentDto);
    System.out.println(
        "Parse back: " + GenerateMappingExamplePaymentMappingImpl.INSTANCE.parse(paymentDto));
    Wallet wallet = new Wallet("Ada", new Bank("GB29-XXXX"));
    WalletDto walletDto = GenerateMappingExampleWalletMappingImpl.INSTANCE.build(wallet);
    System.out.println("Wallet:     " + walletDto);
    System.out.println(
        "Round trip: " + GenerateMappingExampleWalletMappingImpl.INSTANCE.parse(walletDto));
    System.out.println(
        "Expected: the dispatch picks the Card/Bank mapping, and the sealed mapping nests"
            + " inside Wallet like any other spec\n");

    System.out.println("=== Projection Example (Lens tier) ===");
    Employee engineer = new Employee("Ada", "Engineering", 36);
    EmployeeCardDto badge = GenerateMappingExampleEmployeeCardMappingImpl.INSTANCE.build(engineer);
    System.out.println("Projected:  " + badge);
    Lens<Employee, EmployeeCardDto> badgeLens =
        GenerateMappingExampleEmployeeCardMappingImpl.INSTANCE.asLens();
    Employee moved = badgeLens.set(new EmployeeCardDto("Ada", "Platform"), engineer);
    System.out.println("Written back: " + moved);
    System.out.println(
        "Expected: build drops age; asLens().set writes name and department back and keeps"
            + " age=36");
  }

  private GenerateMappingExample() {}
}

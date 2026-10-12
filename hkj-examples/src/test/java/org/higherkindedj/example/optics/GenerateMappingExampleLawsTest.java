// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.higherkindedj.example.optics.GenerateMappingExample.Attendee;
import org.higherkindedj.example.optics.GenerateMappingExample.AttendeeDto;
import org.higherkindedj.example.optics.GenerateMappingExample.BadgeDto;
import org.higherkindedj.example.optics.GenerateMappingExample.BadgeHolder;
import org.higherkindedj.example.optics.GenerateMappingExample.Bank;
import org.higherkindedj.example.optics.GenerateMappingExample.Card;
import org.higherkindedj.example.optics.GenerateMappingExample.Customer;
import org.higherkindedj.example.optics.GenerateMappingExample.CustomerDto;
import org.higherkindedj.example.optics.GenerateMappingExample.DirectoryDto;
import org.higherkindedj.example.optics.GenerateMappingExample.DistributionListDto;
import org.higherkindedj.example.optics.GenerateMappingExample.EmailAddress;
import org.higherkindedj.example.optics.GenerateMappingExample.InvoiceDto;
import org.higherkindedj.example.optics.GenerateMappingExample.Recipient;
import org.higherkindedj.example.optics.GenerateMappingExample.Topic;
import org.higherkindedj.example.optics.GenerateMappingExample.Waypoint;
import org.higherkindedj.optics.laws.MappingLaws;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The runnable laws story for {@link GenerateMappingExample}: every mapping the example
 * demonstrates is law-checked here through the published {@code MappingLaws} harness, one call per
 * emission tier. {@code hkj-test} is test-scope, which is exactly why these calls live in a test
 * and not in the example itself.
 */
@DisplayName("GenerateMappingExample mappings are law-checked through MappingLaws")
class GenerateMappingExampleLawsTest {

  @Test
  @DisplayName("lossless tier: AttendeeMapping passes the iso, round-trip and coherence laws")
  void attendeeMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleAttendeeMappingImpl.INSTANCE.asIso(),
        GenerateMappingExampleAttendeeMappingImpl.INSTANCE.asValidatedPrism(),
        new Attendee("Ada", 36),
        new AttendeeDto("Grace", 41));
  }

  @Test
  @DisplayName("fallible tier: CustomerMapping passes both round trips and the no-parse check")
  void customerMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleCustomerMappingImpl.INSTANCE.asValidatedPrism(),
        new CustomerDto("Ada", "ada@corp.example"),
        new CustomerDto("Bob", "not-an-email"));
  }

  @Test
  @DisplayName("container tier: DistributionListMapping lifts List and Optional lawfully")
  void distributionListMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleDistributionListMappingImpl.INSTANCE.asValidatedPrism(),
        new DistributionListDto(
            "Core", List.of("ada@corp.example"), Optional.of("grace@corp.example")),
        new DistributionListDto("Core", List.of("not-an-email"), Optional.empty()));
  }

  @Test
  @DisplayName("Map tier: DirectoryMapping lifts values lawfully, keys identity")
  void directoryMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleDirectoryMappingImpl.INSTANCE.asValidatedPrism(),
        new DirectoryDto("Platform", Map.of("ops", "kay@corp.example")),
        new DirectoryDto("Platform", Map.of("sre", "not-an-email")));
  }

  @Test
  @DisplayName("derived-field tier: RecipientMapping round-trips its non-derived components")
  void recipientMappingIsLawful() {
    // Parse is total (the derived displayName is ignored), so no non-parsing wire value
    // exists: the domain-sample overload asserts exactly what a derived-field mapping offers.
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleRecipientMappingImpl.INSTANCE.asValidatedPrism(),
        new Recipient("Ada", "Lovelace"));
  }

  @Test
  @DisplayName("nested tier: InvoiceMapping stays lawful through the delegated CustomerMapping")
  void invoiceMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleInvoiceMappingImpl.INSTANCE.asValidatedPrism(),
        new InvoiceDto("INV-1", new CustomerDto("Ada", "ada@corp.example")),
        new InvoiceDto("INV-2", new CustomerDto("Bob", "not-an-email")));
  }

  @Test
  @DisplayName("self-recursive tier: TopicMapping round-trips a finite tree")
  void topicMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleTopicMappingImpl.INSTANCE.asValidatedPrism(),
        new Topic("root", List.of(new Topic("leaf", List.of()))));
  }

  @Test
  @DisplayName("sealed dispatch tier: PaymentMapping is lawful over both subtype arms")
  void paymentMappingIsLawful() {
    // Both arms are identity mappings, so the dispatching parse is total: the domain-sample
    // overload round-trips each arm through the switch.
    MappingLaws.assertMappingLaws(
        GenerateMappingExamplePaymentMappingImpl.INSTANCE.asValidatedPrism(),
        new Card("4111-1111"));
    MappingLaws.assertMappingLaws(
        GenerateMappingExamplePaymentMappingImpl.INSTANCE.asValidatedPrism(),
        new Bank("GB29-XXXX"));
  }

  @Test
  @DisplayName("projection tier: BadgeMapping's asLens() passes the three lens laws")
  void badgeMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleBadgeMappingImpl.INSTANCE.asLens(),
        new BadgeHolder("Ada", "Engineering", 36),
        new BadgeDto("Grace", "Compilers"),
        new BadgeDto("Alan", "Computing"));
  }

  @Test
  @DisplayName("bean tier: ContactMapping's domain round trip is lawful (the bean has no equals)")
  void contactMappingIsLawful() {
    // A bean wire has no value equals(), so only the domain round trip is comparable: the
    // domain-sample overload asserts parse(build(customer)) == Valid(customer).
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleContactMappingImpl.INSTANCE.asValidatedPrism(),
        new Customer("Ada", new EmailAddress("ada@corp.example")));
  }

  @Test
  @DisplayName("builder-bean tier: WaypointMapping's domain round trip is lawful")
  void waypointMappingIsLawful() {
    MappingLaws.assertMappingLaws(
        GenerateMappingExampleWaypointMappingImpl.INSTANCE.asValidatedPrism(),
        new Waypoint(3, "origin"));
  }
}

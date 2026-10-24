// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.bridge;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.higherkindedj.example.optics.bridge.domain.Office;
import org.higherkindedj.example.optics.bridge.domain.OfficeFocus;
import org.higherkindedj.example.optics.bridge.domain.OfficeNetwork;
import org.higherkindedj.example.optics.bridge.domain.OfficeNetworkFocus;
import org.higherkindedj.example.optics.bridge.domain.OfficeWorker;
import org.higherkindedj.example.optics.bridge.domain.OfficeWorkerFocus;
import org.higherkindedj.example.optics.bridge.external.Address;
import org.higherkindedj.example.optics.bridge.external.AddressOptics;
import org.higherkindedj.example.optics.bridge.external.ContactDetails;

/**
 * Demonstrates Focus DSL bridging to external library types.
 *
 * <p>This example shows how to:
 *
 * <ul>
 *   <li>Navigate through local domain records using Focus DSL
 *   <li>Bridge into external types (Immutables-style) using spec interface optics
 *   <li>Compose deep traversals across library boundaries
 *   <li>Maintain fluent, IDE-discoverable navigation throughout
 * </ul>
 *
 * <p>Run with:
 *
 * <pre>
 * ./gradlew :hkj-examples:run -PmainClass=org.higherkindedj.example.optics.bridge.FocusBridgingExample
 * </pre>
 */
public class FocusBridgingExample {

  public static void main(String[] args) {
    System.out.println("=== Focus DSL Bridging to External Types ===\n");

    // Create sample data
    OfficeNetwork company = createSampleCompany();

    System.out.println("Initial company: " + company.name());
    System.out.println("Headquarters: " + company.headquarters());
    System.out.println();

    // Demonstrate bridging patterns
    demonstrateSimpleBridging(company);
    demonstrateDeepTraversals(company);
    demonstrateModifications(company);
    demonstrateDomainService(company);
  }

  private static void demonstrateSimpleBridging(OfficeNetwork company) {
    System.out.println("--- Simple Bridging ---\n");

    // Read through bridge
    String hqCity = CompanyBridge.HEADQUARTERS_CITY.get(company);
    System.out.println("Headquarters city (via bridge): " + hqCity);

    // Alternative: inline composition
    String hqCityInline =
        OfficeNetworkFocus.headquarters().toLens().andThen(AddressOptics.city()).get(company);
    System.out.println("Headquarters city (inline): " + hqCityInline);

    // Modify through bridge
    OfficeNetwork relocated = CompanyBridge.HEADQUARTERS_CITY.set("New York", company);
    System.out.println("After relocation: " + relocated.headquarters().city());
    System.out.println();
  }

  private static void demonstrateDeepTraversals(OfficeNetwork company) {
    System.out.println("--- Deep Traversals Across Boundaries ---\n");

    // All office cities - TraversalPath has getAll()
    List<String> cities = CompanyBridge.allOfficeCities().getAll(company);
    System.out.println("All office cities: " + cities);

    // All employee emails in entire company
    List<String> allEmails = CompanyBridge.allCompanyEmails().getAll(company);
    System.out.println("All employee emails: " + allEmails);

    // All employee phones
    List<String> allPhones = CompanyBridge.allCompanyPhones().getAll(company);
    System.out.println("All employee phones: " + allPhones);

    // Unique cities where company operates
    Set<String> operatingCities = new HashSet<>();
    operatingCities.add(CompanyBridge.HEADQUARTERS_CITY.get(company));
    operatingCities.addAll(CompanyBridge.allOfficeCities().getAll(company));
    System.out.println("Operating in cities: " + operatingCities);
    System.out.println();
  }

  private static void demonstrateModifications(OfficeNetwork company) {
    System.out.println("--- Modifications Through Bridges ---\n");

    // Standardise phone format - TraversalPath has modifyAll()
    UnaryOperator<String> formatPhone =
        phone -> phone.replaceAll("[^0-9]", "").replaceAll("(.{3})(.{3})(.{4})", "($1) $2-$3");

    OfficeNetwork withFormattedPhones =
        CompanyBridge.allCompanyPhones().modifyAll(formatPhone, company);

    System.out.println(
        "Phone after formatting: "
            + CompanyBridge.allCompanyPhones().getAll(withFormattedPhones).getFirst());

    // Update all headquarters fields at once
    OfficeNetwork withNewHq =
        OfficeNetworkFocus.headquarters()
            .toLens()
            .modify(
                addr ->
                    addr.withStreet("456 Corporate Ave")
                        .withCity("San Francisco")
                        .withPostcode("94105"),
                company);
    System.out.println("New HQ: " + withNewHq.headquarters());
    System.out.println();
  }

  private static void demonstrateDomainService(OfficeNetwork company) {
    System.out.println("--- Domain Service Using Bridges ---\n");

    CompanyService service = new CompanyService();

    // Relocate headquarters
    OfficeNetwork relocated = service.relocateHeadquarters(company, "Seattle");
    System.out.println(
        "After relocation to Seattle: " + CompanyBridge.HEADQUARTERS_CITY.get(relocated));

    // Get all operating cities
    Set<String> cities = service.getAllOperatingCities(company);
    System.out.println("All operating cities: " + cities);

    // Get all employee emails
    List<String> emails = service.getAllEmployeeEmails(company);
    System.out.println("All employee emails: " + emails);

    // Give raises to Boston employees
    OfficeNetwork withRaises = service.giveRaisesToCity(company, "Boston", BigDecimal.TEN);
    System.out.println("Raises applied to Boston staff");
    System.out.println();
  }

  /** Example domain service that uses bridged optics. */
  static class CompanyService {

    public OfficeNetwork relocateHeadquarters(OfficeNetwork company, String newCity) {
      return CompanyBridge.HEADQUARTERS_CITY.set(newCity, company);
    }

    public Set<String> getAllOperatingCities(OfficeNetwork company) {
      Set<String> cities = new HashSet<>();
      cities.add(CompanyBridge.HEADQUARTERS_CITY.get(company));
      cities.addAll(CompanyBridge.allOfficeCities().getAll(company));
      return cities;
    }

    public List<String> getAllEmployeeEmails(OfficeNetwork company) {
      return CompanyBridge.allCompanyEmails().getAll(company);
    }

    public OfficeNetwork giveRaisesToCity(
        OfficeNetwork company, String city, BigDecimal raisePercent) {
      BigDecimal multiplier = BigDecimal.ONE.add(raisePercent.divide(BigDecimal.valueOf(100)));

      // offices() already returns TraversalPath<OfficeNetwork, Office> (auto-traversed)
      // staff() already returns TraversalPath<Office, OfficeWorker> (auto-traversed)
      // No .each(...) needed - the generator handles collection traversal automatically
      return OfficeNetworkFocus.offices()
          .filter(office -> CompanyBridge.officeCity().get(office).equals(city))
          .via(OfficeFocus.staff())
          .via(OfficeWorkerFocus.salary())
          .modifyAll(salary -> salary.multiply(multiplier), company);
    }
  }

  private static OfficeNetwork createSampleCompany() {
    // Engineering office in Boston
    OfficeWorker alice =
        new OfficeWorker(
            "E001",
            "Alice Smith",
            ContactDetails.builder().email("alice@acme.com").phone("617-555-0101").build(),
            new BigDecimal("95000"));

    OfficeWorker bob =
        new OfficeWorker(
            "E002",
            "Bob Jones",
            ContactDetails.builder()
                .email("bob@acme.com")
                .phone("617-555-0102")
                .fax("617-555-9999")
                .build(),
            new BigDecimal("85000"));

    Office engineering =
        new Office(
            "Engineering",
            alice,
            List.of(alice, bob),
            Address.builder().street("100 Tech Drive").city("Boston").postcode("02101").build());

    // Sales office in Chicago
    OfficeWorker carol =
        new OfficeWorker(
            "E003",
            "Carol White",
            ContactDetails.builder().email("carol@acme.com").phone("312-555-0201").build(),
            new BigDecimal("75000"));

    Office sales =
        new Office(
            "Sales",
            carol,
            List.of(carol),
            Address.builder().street("200 Commerce St").city("Chicago").postcode("60601").build());

    // OfficeNetwork with headquarters in New York
    return new OfficeNetwork(
        "Acme Corp",
        Address.builder().street("1 Corporate Plaza").city("New York").postcode("10001").build(),
        List.of(engineering, sales));
  }
}

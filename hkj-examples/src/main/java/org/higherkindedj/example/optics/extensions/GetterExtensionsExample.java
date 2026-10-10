// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.optics.extensions;

import static org.higherkindedj.optics.extensions.GetterExtensions.*;

import org.higherkindedj.example.book.optics.cast.Address;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Getter;
import org.jspecify.annotations.Nullable;

/**
 * Comprehensive example demonstrating {@link org.higherkindedj.optics.extensions.GetterExtensions}
 * for null-safe getter operations.
 *
 * <p>This example showcases:
 *
 * <ul>
 *   <li>Using {@code getMaybe} for null-safe field access
 *   <li>Chaining operations safely through nullable structures
 *   <li>Providing fallback values with {@code orElse} and {@code orElseGet}
 *   <li>Composing getters for deep navigation
 *   <li>Real-world scenarios: staff profiles, API responses, configuration management
 * </ul>
 *
 * <p>GetterExtensions provides null-safe alternatives to Getter methods, ensuring safe navigation
 * through potentially null fields while maintaining consistency with higher-kinded-j's functional
 * programming patterns.
 */
public class GetterExtensionsExample {

  // Domain Models with Nullable Fields, around the chapter's Address
  record ContactInfo(@Nullable String phone, @Nullable String email, @Nullable String fax) {}

  // A company's staff member, as its directory lists them
  record StaffMember(
      String username,
      String displayName,
      @Nullable String bio,
      @Nullable Address address,
      ContactInfo contactInfo) {}

  record Company(String name, @Nullable StaffMember ceo, @Nullable Address headquarters) {}

  record ApiResponse<T>(boolean success, @Nullable T data, @Nullable String errorMessage) {}

  record Config(
      String appName, @Nullable String apiKey, @Nullable Integer port, @Nullable String logLevel) {}

  public static void main(String[] args) {
    System.out.println("=== GETTER EXTENSIONS EXAMPLE ===\n");

    demonstrateBasicNullSafety();
    demonstrateChaining();
    demonstrateFallbackStrategies();
    demonstrateComposition();
    demonstrateRealWorldScenarios();
  }

  // ============================================================================
  // SCENARIO 1: Basic Null Safety
  // ============================================================================

  private static void demonstrateBasicNullSafety() {
    System.out.println("--- Scenario 1: Basic Null Safety ---");

    // Create getters
    Getter<StaffMember, String> bioGetter = Getter.of(StaffMember::bio);
    Getter<StaffMember, Address> addressGetter = Getter.of(StaffMember::address);

    // A staff member with complete data
    ContactInfo contact1 = new ContactInfo("123-456-7890", "alice@example.com", null);
    Address address1 = new Address("123 Main St", "New York", "10001");
    StaffMember completeProfile =
        new StaffMember("alice", "Alice Smith", "Software Engineer", address1, contact1);

    // A staff member with missing data
    ContactInfo contact2 = new ContactInfo(null, "bob@example.com", null);
    StaffMember incompleteProfile = new StaffMember("bob", "Bob Jones", null, null, contact2);

    // Safe access to bio
    Maybe<String> bio1 = getMaybe(bioGetter, completeProfile);
    Maybe<String> bio2 = getMaybe(bioGetter, incompleteProfile);

    System.out.println("Complete profile bio: " + bio1.orElse("Not provided"));
    System.out.println("Incomplete profile bio: " + bio2.orElse("Not provided"));

    // Safe access to address
    Maybe<Address> addr1 = getMaybe(addressGetter, completeProfile);
    Maybe<Address> addr2 = getMaybe(addressGetter, incompleteProfile);

    if (addr1.isJust()) {
      Address a = addr1.get();
      System.out.println("Address found: " + a.city());
    }
    if (addr2.isNothing()) {
      System.out.println("No address available");
    } else {
      Address a = addr2.get();
      System.out.println("Address found: " + a.city());
    }

    System.out.println();
  }

  // ============================================================================
  // SCENARIO 2: Chaining Operations
  // ============================================================================

  private static void demonstrateChaining() {
    System.out.println("--- Scenario 2: Chaining Operations ---");

    Getter<StaffMember, Address> addressGetter = Getter.of(StaffMember::address);
    Getter<Address, String> streetGetter = Getter.of(Address::street);
    Getter<Address, String> postcodeGetter = Getter.of(Address::postcode);

    ContactInfo contact = new ContactInfo("123-456-7890", "alice@example.com", null);
    Address address = new Address("123 Main St", "New York", "10001");
    StaffMember profile = new StaffMember("alice", "Alice Smith", "Engineer", address, contact);

    // Chain to get street
    Maybe<String> street =
        getMaybe(addressGetter, profile).flatMap(addr -> getMaybe(streetGetter, addr));

    System.out.println("Street: " + street.orElse("Unknown"));

    // Chain to get postcode
    Maybe<String> postcode =
        getMaybe(addressGetter, profile).flatMap(addr -> getMaybe(postcodeGetter, addr));

    System.out.println("Postcode: " + postcode.orElse("Unknown"));

    // Chain with map for transformation
    Maybe<String> formattedAddress =
        getMaybe(addressGetter, profile)
            .map(addr -> addr.street() + ", " + addr.city())
            .map(String::toUpperCase);

    System.out.println("Formatted address: " + formattedAddress.orElse("N/A"));

    // Handle null in the middle of chain
    StaffMember profileNoAddress = new StaffMember("bob", "Bob Jones", null, null, contact);

    Maybe<String> streetMissing =
        getMaybe(addressGetter, profileNoAddress).flatMap(addr -> getMaybe(streetGetter, addr));

    System.out.println("Street (missing): " + streetMissing.orElse("Not available"));
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 3: Fallback Strategies
  // ============================================================================

  private static void demonstrateFallbackStrategies() {
    System.out.println("--- Scenario 3: Fallback Strategies ---");

    Getter<ContactInfo, String> phoneGetter = Getter.of(ContactInfo::phone);
    Getter<ContactInfo, String> emailGetter = Getter.of(ContactInfo::email);
    Getter<ContactInfo, String> faxGetter = Getter.of(ContactInfo::fax);

    // Contact with only email
    ContactInfo contact = new ContactInfo(null, "charlie@example.com", null);

    // Try phone, then email, then fax, then default
    String primaryContact =
        getMaybe(phoneGetter, contact)
            .orElseGet(
                () ->
                    getMaybe(emailGetter, contact)
                        .orElseGet(
                            () -> getMaybe(faxGetter, contact).orElse("No contact available")));

    System.out.println("Primary contact: " + primaryContact);

    // Configuration with defaults
    Config config = new Config("MyApp", null, null, null);

    Getter<Config, String> apiKeyGetter = Getter.of(Config::apiKey);
    Getter<Config, Integer> portGetter = Getter.of(Config::port);
    Getter<Config, String> logLevelGetter = Getter.of(Config::logLevel);

    String apiKey = getMaybe(apiKeyGetter, config).orElse("default-api-key");
    int port = getMaybe(portGetter, config).orElse(8080);
    String logLevel = getMaybe(logLevelGetter, config).orElse("INFO");

    System.out.println("API Key: " + apiKey);
    System.out.println("Port: " + port);
    System.out.println("Log Level: " + logLevel);

    // Dynamic default based on other fields
    String dynamicDefault =
        getMaybe(apiKeyGetter, config)
            .orElseGet(() -> "api-key-for-" + config.appName().toLowerCase());

    System.out.println("Dynamic API Key: " + dynamicDefault);
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 4: Composition
  // ============================================================================

  private static void demonstrateComposition() {
    System.out.println("--- Scenario 4: Composition ---");

    Getter<Company, StaffMember> ceoGetter = Getter.of(Company::ceo);
    Getter<StaffMember, Address> addressGetter = Getter.of(StaffMember::address);
    Getter<Address, String> cityGetter = Getter.of(Address::city);

    // Compose getters
    Getter<Company, StaffMember> ceoBioGetter = ceoGetter;
    Getter<Company, Address> ceoAddressGetter = ceoGetter.andThen(addressGetter);

    ContactInfo contact = new ContactInfo("123-456-7890", "ceo@example.com", null);
    Address ceoAddress = new Address("456 Executive Blvd", "San Francisco", "94102");
    StaffMember ceo = new StaffMember("jdoe", "Jane Doe", "CEO", ceoAddress, contact);

    Address hq = new Address("789 Corporate Way", "Seattle", "98101");
    Company company = new Company("TechCorp", ceo, hq);

    // Navigate through composed getters with Maybe
    Maybe<String> ceoCity = getMaybe(ceoAddressGetter, company).map(Address::city);

    System.out.println("CEO city: " + ceoCity.orElse("Unknown"));

    // Deep navigation with flatMap
    Maybe<String> ceoPostcode =
        getMaybe(ceoGetter, company)
            .flatMap(c -> getMaybe(addressGetter, c))
            .flatMap(a -> getMaybe(Getter.of(Address::postcode), a));

    System.out.println("CEO postcode: " + ceoPostcode.orElse("Unknown"));

    // Handle missing CEO: chain with flatMap, since the composed getter would read a missing CEO's
    // address
    Company startupWithoutCEO = new Company("StartupCo", null, null);

    Maybe<String> missingCeoCity =
        getMaybe(ceoGetter, startupWithoutCEO)
            .flatMap(c -> getMaybe(addressGetter, c))
            .map(Address::city);

    System.out.println("Startup CEO city: " + missingCeoCity.orElse("No CEO assigned"));
    System.out.println();
  }

  // ============================================================================
  // SCENARIO 5: Real-World Scenarios
  // ============================================================================

  private static void demonstrateRealWorldScenarios() {
    System.out.println("--- Scenario 5: Real-World Scenarios ---");

    // Scenario A: API Response Handling
    System.out.println("API Response Handling:");

    Getter<ApiResponse<String>, String> dataGetter = Getter.of(ApiResponse::data);
    Getter<ApiResponse<String>, String> errorGetter = Getter.of(ApiResponse::errorMessage);

    ApiResponse<String> successResponse = new ApiResponse<>(true, "Success data", null);
    ApiResponse<String> errorResponse = new ApiResponse<>(false, null, "Server error occurred");

    Maybe<String> data1 = getMaybe(dataGetter, successResponse);
    Maybe<String> error1 = getMaybe(errorGetter, successResponse);

    System.out.println("  Success case:");
    System.out.println("    Data: " + data1.orElse("No data"));
    System.out.println("    Error: " + error1.orElse("No error"));

    Maybe<String> data2 = getMaybe(dataGetter, errorResponse);
    Maybe<String> error2 = getMaybe(errorGetter, errorResponse);

    System.out.println("  Error case:");
    System.out.println("    Data: " + data2.orElse("No data"));
    System.out.println("    Error: " + error2.orElse("No error"));
    System.out.println();

    // Scenario B: Staff Profile Display
    System.out.println("Staff Profile Display:");

    Getter<StaffMember, String> bioGetter = Getter.of(StaffMember::bio);
    Getter<StaffMember, Address> addressGetter = Getter.of(StaffMember::address);

    ContactInfo contact1 = new ContactInfo("123-456-7890", "alice@example.com", null);
    Address address1 = new Address("123 Main St", "New York", "10001");
    StaffMember profile1 =
        new StaffMember("alice", "Alice Smith", "Software Engineer", address1, contact1);

    ContactInfo contact2 = new ContactInfo(null, "bob@example.com", null);
    StaffMember profile2 = new StaffMember("bob", "Bob Jones", null, null, contact2);

    displayStaffMember(profile1, bioGetter, addressGetter);
    displayStaffMember(profile2, bioGetter, addressGetter);
    System.out.println();

    // Scenario C: Configuration Validation
    System.out.println("Configuration Validation:");

    validateConfig(new Config("App1", "secret-key", 8080, "DEBUG"));
    validateConfig(new Config("App2", null, null, null));
    System.out.println();

    // Scenario D: Company Information Display
    System.out.println("Company Information:");

    Getter<Company, StaffMember> ceoGetter = Getter.of(Company::ceo);
    Getter<Company, Address> hqGetter = Getter.of(Company::headquarters);

    ContactInfo ceoContact = new ContactInfo("555-0001", "ceo@techcorp.com", null);
    Address ceoAddress = new Address("100 CEO Lane", "San Francisco", "94102");
    StaffMember ceo =
        new StaffMember("jdoe", "Jane Doe", "Visionary leader", ceoAddress, ceoContact);

    Address hq = new Address("200 HQ Plaza", "Seattle", "98101");
    Company company1 = new Company("TechCorp", ceo, hq);
    Company company2 = new Company("StartupCo", null, null);

    displayCompanyInfo(company1, ceoGetter, hqGetter);
    displayCompanyInfo(company2, ceoGetter, hqGetter);
  }

  private static void displayStaffMember(
      StaffMember profile,
      Getter<StaffMember, String> bioGetter,
      Getter<StaffMember, Address> addressGetter) {
    System.out.println("  Staff member: " + profile.username());
    System.out.println("    Display Name: " + profile.displayName());

    String bio = getMaybe(bioGetter, profile).orElse("No bio available");
    System.out.println("    Bio: " + bio);

    String location =
        getMaybe(addressGetter, profile)
            .map(a -> a.city() + " " + a.postcode())
            .orElse("Location not specified");

    System.out.println("    Location: " + location);
  }

  private static void validateConfig(Config config) {
    Getter<Config, String> apiKeyGetter = Getter.of(Config::apiKey);
    Getter<Config, Integer> portGetter = Getter.of(Config::port);
    Getter<Config, String> logLevelGetter = Getter.of(Config::logLevel);

    System.out.println("  Config for " + config.appName() + ":");

    boolean hasApiKey = getMaybe(apiKeyGetter, config).isJust();
    boolean hasPort = getMaybe(portGetter, config).isJust();
    boolean hasLogLevel = getMaybe(logLevelGetter, config).isJust();

    System.out.println("    API Key configured: " + hasApiKey);
    System.out.println("    Port configured: " + hasPort);
    System.out.println("    Log Level configured: " + hasLogLevel);

    boolean isValid = hasApiKey && hasPort && hasLogLevel;
    System.out.println("    Configuration valid: " + isValid);
  }

  private static void displayCompanyInfo(
      Company company, Getter<Company, StaffMember> ceoGetter, Getter<Company, Address> hqGetter) {
    System.out.println("  Company: " + company.name());

    String ceoName =
        getMaybe(ceoGetter, company).map(StaffMember::displayName).orElse("No CEO assigned");

    System.out.println("    CEO: " + ceoName);

    String hqLocation =
        getMaybe(hqGetter, company)
            .map(a -> a.city() + " " + a.postcode())
            .orElse("HQ not established");

    System.out.println("    Headquarters: " + hqLocation);
  }
}

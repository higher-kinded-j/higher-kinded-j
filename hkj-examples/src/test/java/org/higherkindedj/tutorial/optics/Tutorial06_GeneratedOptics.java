// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 06: Generated Optics - Leveraging Annotation Processing
 *
 * <p>Higher-kinded-j provides annotations that automatically generate optics for your types. This
 * eliminates boilerplate and ensures consistency.
 *
 * <p>Key Annotations: - @GenerateLenses: Creates lenses for all fields in a record -
 * @GeneratePrisms: Creates prisms for all variants of a sealed interface - @GenerateTraversals:
 * Creates traversals for collection fields
 *
 * <p>Generated classes: - RecordNameLenses: contains lens instances and with* helper methods -
 * InterfaceNamePrisms: contains prism instances for each variant - RecordNameTraversals: contains
 * traversal instances for collections
 */
public class Tutorial06_GeneratedOptics {

  /**
   * Exercise 1: Using @GenerateLenses
   *
   * <p>The @GenerateLenses annotation generates a *Lenses class with: - A lens() method for each
   * field - A with*() helper method for each field
   *
   * <p>Task: Use generated lenses to access and modify fields
   */
  @Test
  void exercise1_generatedLenses() {
    @GenerateLenses
    record Person(String name, int age, String email) {}

    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with generated lens access
    // Hint: PersonLenses.name().get(person)
    String name = null;

    // TODO: Replace null with generated lens modification
    // Hint: PersonLenses.age().set(31, person)
    Person updated = null;

    // TODO: Replace null with generated helper method
    // Hint: PersonLenses.withEmail(person, "new@example.com")
    Person updated2 = null;

    assertThat(name).isEqualTo("Alice");
    assertThat(updated.age()).isEqualTo(31);
    assertThat(updated2.email()).isEqualTo("new@example.com");
  }

  /**
   * Exercise 2: Using @GeneratePrisms
   *
   * <p>The @GeneratePrisms annotation on a sealed interface generates a *Prisms class with a prism
   * for each implementation.
   *
   * <p>Task: Use generated prisms to work with sum types
   */
  @Test
  void exercise2_generatedPrisms() {
    @GeneratePrisms
    sealed interface Result {}

    record Success(String message) implements Result {}

    record Failure(String error) implements Result {}

    Result result = new Success("Operation completed");

    // TODO: Replace null with generated prism access
    // Hint: ResultPrisms.success().getOptional(result)
    var successOpt = null;

    assertThat(successOpt.isJust()).isTrue();
    assertThat(successOpt.get().message()).isEqualTo("Operation completed");

    // TODO: Replace null with generated prism construction
    // Hint: ResultPrisms.failure().build(new Failure("Error"))
    Result failure = null;

    assertThat(failure).isInstanceOf(Failure.class);
  }

  /**
   * Exercise 3: Using @GenerateTraversals
   *
   * <p>The @GenerateTraversals annotation generates traversals for List, Set, and Map fields.
   *
   * <p>Task: Use generated traversals to work with collections
   */
  @Test
  void exercise3_generatedTraversals() {
    @GenerateLenses
    record Item(String name, double price) {}

    @GenerateTraversals
    record Cart(List<Item> items) {}

    Cart cart =
        new Cart(List.of(new Item("Widget", 10.0), new Item("Gadget", 20.0)));

    // TODO: Replace null with generated traversal to access all items
    // Hint: CartTraversals.items()
    var itemsTraversal = null;

    // Apply 10% discount to all items
    Cart discounted =
        Traversals.modify(itemsTraversal, item -> new Item(item.name(), item.price() * 0.9), cart);

    assertThat(discounted.items().get(0).price()).isCloseTo(9.0, within(0.01));
    assertThat(discounted.items().get(1).price()).isCloseTo(18.0, within(0.01));
  }

  /**
   * Exercise 4: Combining generated optics
   *
   * <p>Generated optics compose just like manually created ones.
   *
   * <p>Task: Use composition with generated optics
   */
  @Test
  void exercise4_combiningGeneratedOptics() {
    @GenerateLenses
    record Address(String street, String city) {}

    @GenerateLenses
    record Company(String name, Address address) {}

    @GenerateLenses
    record Employee(String name, Company company) {}

    Employee emp =
        new Employee("Alice", new Company("TechCorp", new Address("123 Main", "Springfield")));

    // TODO: Replace null with a composition of generated lenses
    // to access the employee's company's city
    // Hint: EmployeeLenses.company().andThen(...).andThen(...)
    var cityLens = null;

    assertThat(cityLens.get(emp)).isEqualTo("Springfield");

    Employee updated = cityLens.set("Shelbyville", emp);
    assertThat(updated.company().address().city()).isEqualTo("Shelbyville");
  }

  /**
   * Exercise 5: Generated traversals for Map fields
   *
   * <p>@GenerateTraversals also works with Map<K, V> fields, creating traversals over values.
   *
   * <p>Task: Use generated map traversals
   */
  @Test
  void exercise5_generatedMapTraversals() {
    @GenerateLenses
    record Product(String name, double price) {}

    @GenerateTraversals
    record Inventory(Map<String, Product> products) {}

    Inventory inventory =
        new Inventory(
            Map.of(
                "PROD-1", new Product("Widget", 10.0),
                "PROD-2", new Product("Gadget", 20.0)));

    // TODO: Replace null with generated map traversal
    // Hint: InventoryTraversals.products()
    var productsTraversal = null;

    // Increase all prices by 10%
    Inventory updated =
        Traversals.modify(
            productsTraversal, p -> new Product(p.name(), p.price() * 1.1), inventory);

    assertThat(updated.products().get("PROD-1").price()).isCloseTo(11.0, within(0.01));
    assertThat(updated.products().get("PROD-2").price()).isCloseTo(22.0, within(0.01));
  }

  /**
   * Exercise 6: Using with* helpers for convenient updates
   *
   * <p>Generated *Lenses classes include with* methods for more discoverable API.
   *
   * <p>Task: Use with* helpers instead of lens.set()
   */
  @Test
  void exercise6_withHelpers() {
    @GenerateLenses
    record User(String id, String name, String email, boolean active) {}

    User user = new User("user1", "Alice", "alice@example.com", true);

    // Using lens.set() (functional style)
    User updated1 = UserLenses.name().set("Bob", user);

    // TODO: Replace null with equivalent using with* helper
    // Hint: UserLenses.withName(user, "Bob")
    User updated2 = null;

    assertThat(updated1.name()).isEqualTo("Bob");
    assertThat(updated2.name()).isEqualTo("Bob");

    // with* methods are more discoverable in IDE autocomplete
    // Type "UserLenses.with" and see all available field setters
  }

  /**
   * Exercise 7: Complex scenario with all three annotations
   *
   * <p>Combine @GenerateLenses, @GeneratePrisms, and @GenerateTraversals in a real scenario.
   *
   * <p>Task: Model and manipulate a notification system
   */
  @Test
  void exercise7_complexScenario() {
    @GeneratePrisms
    sealed interface NotificationType {}

    @GenerateLenses
    record Email(String to, String subject) implements NotificationType {}

    @GenerateLenses
    record SMS(String phoneNumber, String message) implements NotificationType {}

    @GenerateLenses
    record Notification(String id, NotificationType type, boolean sent) {}

    @GenerateTraversals
    record NotificationQueue(List<Notification> notifications) {}

    NotificationQueue queue =
        new NotificationQueue(
            List.of(
                new Notification("1", new Email("user@example.com", "Welcome"), false),
                new Notification("2", new SMS("555-1234", "Code: 1234"), false),
                new Notification("3", new Email("admin@example.com", "Alert"), false)));

    // TODO: Replace null with a composition that:
    // 1. Traverses all notifications
    // 2. Filters to Email types only
    // 3. Gets the email subject
    var emailSubjectsTraversal =
        NotificationQueueTraversals.notifications()
            .andThen(NotificationLenses.type().asTraversal())
            .andThen(null
.asTraversal())
            .andThen(EmailLenses.subject().asTraversal());

    List<String> subjects = Traversals.getAll(emailSubjectsTraversal, queue);
    assertThat(subjects).containsExactly("Welcome", "Alert");

    // Mark all notifications as sent
    NotificationQueue updated =
        Traversals.modify(
            NotificationQueueTraversals.notifications(),
            n -> NotificationLenses.withSent(n, true),
            queue);

    assertThat(updated.notifications().get(0).sent()).isTrue();
    assertThat(updated.notifications().get(1).sent()).isTrue();
  }

  /**
   * Congratulations! You've completed Tutorial 06: Generated Optics
   *
   * <p>You now understand: ✓ How to use @GenerateLenses for automatic lens creation ✓ How to use
   * @GeneratePrisms for sealed interface variants ✓ How to use @GenerateTraversals for collections
   * ✓ How generated optics compose like manual ones ✓ How to use with* helpers for convenient
   * updates ✓ How to combine all three annotations in real scenarios
   *
   * <p>Next: Tutorial 07 - Real World Optics
   */
}

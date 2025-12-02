// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.tutorial.optics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/**
 * Tutorial 07: Generated Optics - Leveraging Annotation Processing
 *
 * <p>Higher-kinded-j provides annotations that automatically generate optics for your types. This
 * eliminates boilerplate and ensures consistency.
 *
 * <p>Key Annotations: - @GenerateLenses: Creates lenses for all fields in a record
 * - @GeneratePrisms: Creates prisms for all variants of a sealed interface - @GenerateTraversals:
 * Creates traversals for collection fields
 *
 * <p>Generated classes: - RecordNameLenses: contains lens instances and with* helper methods -
 * InterfaceNamePrisms: contains prism instances for each variant - RecordNameTraversals: contains
 * traversal instances for collections
 */
public class Tutorial07_GeneratedOptics {

  /** Helper method for incomplete exercises that throws a clear exception. */
  private static <T> T answerRequired() {
    throw new RuntimeException("Answer required");
  }

  /*
   * ========================================================================
   * NOTE: This Tutorial Focuses on GENERATED Optics (The Right Way!)
   * ========================================================================
   *
   * This tutorial shows you how to use annotation processing to generate optics automatically.
   * This is the CORRECT approach for production code!
   *
   * Key takeaways:
   * ────────────────────────────────────────────────────────────────────────
   * - Use @GenerateLenses, @GeneratePrisms, and @GenerateTraversals
   * - The annotation processor generates all optics at compile time
   * - Generated optics are type-safe, optimized, and zero-cost abstractions
   * - No manual optic writing needed!
   *
   * The manual helpers below are ONLY used to support exercises where
   * we demonstrate advanced traversal patterns. In your code, even these
   * would be generated automatically!
   */

  // Sealed interfaces must be at class level
  @GeneratePrisms
  sealed interface Result2 {}

  record Success2(String message) implements Result2 {}

  record Failure2(String error) implements Result2 {}

  @GeneratePrisms
  sealed interface NotificationType7 {}

  record Email7(String to, String subject) implements NotificationType7 {}

  record SMS7(String phoneNumber, String message) implements NotificationType7 {}

  // Manual helpers for advanced exercises (normally @GenerateTraversals would handle this)

  /**
   * Helper to create a Traversal for List fields (FOR EDUCATIONAL PURPOSES -
   * use @GenerateTraversals in production)
   */
  static <S, A> Traversal<S, A> listTraversal(
      Function<S, List<A>> getter, BiFunction<S, List<A>, S> setter) {
    return new Traversal<S, A>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<A, Kind<F, A>> f, S s, Applicative<F> applicative) {
        List<A> list = getter.apply(s);
        Kind<F, List<A>> traversedList = Traversals.traverseList(list, f, applicative);
        return applicative.map(newList -> setter.apply(s, newList), traversedList);
      }
    };
  }

  /**
   * Helper to create a Traversal for Map fields (FOR EDUCATIONAL PURPOSES - use @GenerateTraversals
   * in production)
   */
  static <S, K, V> Traversal<S, V> mapTraversal(
      Function<S, Map<K, V>> getter, BiFunction<S, Map<K, V>, S> setter) {
    return new Traversal<S, V>() {
      @Override
      public <F> Kind<F, S> modifyF(Function<V, Kind<F, V>> f, S s, Applicative<F> applicative) {
        Map<K, V> map = getter.apply(s);
        // Note: traverseMap doesn't exist in the library, so we'll convert to list
        var values = new ArrayList<>(map.values());
        var valuesKind = Traversals.traverseList(values, f, applicative);
        return applicative.map(
            newValues -> {
              var newMap = new LinkedHashMap<K, V>();
              var keyIter = map.keySet().iterator();
              var valueIter = newValues.iterator();
              while (keyIter.hasNext() && valueIter.hasNext()) {
                newMap.put(keyIter.next(), valueIter.next());
              }
              return setter.apply(s, newMap);
            },
            valuesKind);
      }
    };
  }

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

    // Manual implementation (annotation processor would generate this)
    class PersonLenses {
      public static Lens<Person, String> name() {
        return Lens.of(Person::name, (p, newName) -> new Person(newName, p.age(), p.email()));
      }

      public static Lens<Person, Integer> age() {
        return Lens.of(Person::age, (p, newAge) -> new Person(p.name(), newAge, p.email()));
      }

      public static Lens<Person, String> email() {
        return Lens.of(Person::email, (p, newEmail) -> new Person(p.name(), p.age(), newEmail));
      }

      public static Person withEmail(Person p, String newEmail) {
        return new Person(p.name(), p.age(), newEmail);
      }
    }

    Person person = new Person("Alice", 30, "alice@example.com");

    // TODO: Replace null with generated lens access
    // Hint: PersonLenses.name().get(person)
    String name = answerRequired();

    // TODO: Replace null with generated lens modification
    // Hint: PersonLenses.age().set(31, person)
    Person updated = answerRequired();

    // TODO: Replace null with generated helper method
    // Hint: PersonLenses.withEmail(person, "new@example.com")
    Person updated2 = answerRequired();

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
    // Manual implementation (annotation processor would generate this)
    class ResultPrisms {
      public static Prism<Result2, Success2> success() {
        return Prism.of(r -> r instanceof Success2 s ? Optional.of(s) : Optional.empty(), s -> s);
      }

      public static Prism<Result2, Failure2> failure() {
        return Prism.of(r -> r instanceof Failure2 f ? Optional.of(f) : Optional.empty(), f -> f);
      }
    }

    Result2 result = new Success2("Operation completed");

    // TODO: Replace null with generated prism access
    // Hint: ResultPrisms.success().getOptional(result)
    Optional<Success2> successOpt = answerRequired();

    assertThat(successOpt.isPresent()).isTrue();
    assertThat(successOpt.get().message()).isEqualTo("Operation completed");

    // TODO: Replace null with generated prism construction
    // Hint: ResultPrisms.failure().build(new Failure2("Error"))
    Result2 failure = answerRequired();

    assertThat(failure).isInstanceOf(Failure2.class);
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

    // Manual implementation (annotation processor would generate this)
    class CartTraversals {
      public static Traversal<Cart, Item> items() {
        return listTraversal(Cart::items, (c, items) -> new Cart(items));
      }
    }

    Cart cart = new Cart(List.of(new Item("Widget", 10.0), new Item("Gadget", 20.0)));

    // TODO: Replace null with generated traversal to access all items
    // Hint: CartTraversals.items()
    Traversal<Cart, Item> itemsTraversal = answerRequired();

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

    // Manual implementations (annotation processor would generate these)
    class AddressLenses {
      public static Lens<Address, String> street() {
        return Lens.of(Address::street, (a, newStreet) -> new Address(newStreet, a.city()));
      }

      public static Lens<Address, String> city() {
        return Lens.of(Address::city, (a, newCity) -> new Address(a.street(), newCity));
      }
    }

    class CompanyLenses {
      public static Lens<Company, String> name() {
        return Lens.of(Company::name, (c, newName) -> new Company(newName, c.address()));
      }

      public static Lens<Company, Address> address() {
        return Lens.of(Company::address, (c, newAddress) -> new Company(c.name(), newAddress));
      }
    }

    class EmployeeLenses {
      public static Lens<Employee, String> name() {
        return Lens.of(Employee::name, (e, newName) -> new Employee(newName, e.company()));
      }

      public static Lens<Employee, Company> company() {
        return Lens.of(Employee::company, (e, newCompany) -> new Employee(e.name(), newCompany));
      }
    }

    Employee emp =
        new Employee("Alice", new Company("TechCorp", new Address("123 Main", "Springfield")));

    // TODO: Replace null with a composition of generated lenses
    // to access the employee's company's city
    // Hint: EmployeeLenses.company().andThen(...).andThen(...)
    Lens<Employee, String> cityLens = answerRequired();

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

    // Manual implementation (annotation processor would generate this)
    class InventoryTraversals {
      public static Traversal<Inventory, Product> products() {
        return mapTraversal(Inventory::products, (i, products) -> new Inventory(products));
      }
    }

    Inventory inventory =
        new Inventory(
            Map.of(
                "PROD-1", new Product("Widget", 10.0),
                "PROD-2", new Product("Gadget", 20.0)));

    // TODO: Replace null with generated map traversal
    // Hint: InventoryTraversals.products()
    Traversal<Inventory, Product> productsTraversal = answerRequired();

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

    // Manual implementation (annotation processor would generate this)
    class UserLenses {
      public static Lens<User, String> id() {
        return Lens.of(User::id, (u, newId) -> new User(newId, u.name(), u.email(), u.active()));
      }

      public static Lens<User, String> name() {
        return Lens.of(
            User::name, (u, newName) -> new User(u.id(), newName, u.email(), u.active()));
      }

      public static Lens<User, String> email() {
        return Lens.of(
            User::email, (u, newEmail) -> new User(u.id(), u.name(), newEmail, u.active()));
      }

      public static Lens<User, Boolean> active() {
        return Lens.of(
            User::active, (u, newActive) -> new User(u.id(), u.name(), u.email(), newActive));
      }

      public static User withName(User u, String newName) {
        return new User(u.id(), newName, u.email(), u.active());
      }
    }

    User user = new User("user1", "Alice", "alice@example.com", true);

    // Using lens.set() (functional style)
    User updated1 = UserLenses.name().set("Bob", user);

    // TODO: Replace null with equivalent using with* helper
    // Hint: UserLenses.withName(user, "Bob")
    User updated2 = answerRequired();

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
    @GenerateLenses
    record Notification(String id, NotificationType7 type, boolean sent) {}

    @GenerateTraversals
    record NotificationQueue(List<Notification> notifications) {}

    // Manual implementations (annotation processor would generate these)
    class EmailLenses {
      public static Lens<Email7, String> to() {
        return Lens.of(Email7::to, (e, newTo) -> new Email7(newTo, e.subject()));
      }

      public static Lens<Email7, String> subject() {
        return Lens.of(Email7::subject, (e, newSubject) -> new Email7(e.to(), newSubject));
      }
    }

    class SMSLenses {
      public static Lens<SMS7, String> phoneNumber() {
        return Lens.of(SMS7::phoneNumber, (s, newPhone) -> new SMS7(newPhone, s.message()));
      }

      public static Lens<SMS7, String> message() {
        return Lens.of(SMS7::message, (s, newMessage) -> new SMS7(s.phoneNumber(), newMessage));
      }
    }

    class NotificationTypePrisms {
      public static Prism<NotificationType7, Email7> email() {
        return Prism.of(nt -> nt instanceof Email7 e ? Optional.of(e) : Optional.empty(), e -> e);
      }

      public static Prism<NotificationType7, SMS7> sms() {
        return Prism.of(nt -> nt instanceof SMS7 s ? Optional.of(s) : Optional.empty(), s -> s);
      }
    }

    class NotificationLenses {
      public static Lens<Notification, String> id() {
        return Lens.of(Notification::id, (n, newId) -> new Notification(newId, n.type(), n.sent()));
      }

      public static Lens<Notification, NotificationType7> type() {
        return Lens.of(
            Notification::type, (n, newType) -> new Notification(n.id(), newType, n.sent()));
      }

      public static Lens<Notification, Boolean> sent() {
        return Lens.of(
            Notification::sent, (n, newSent) -> new Notification(n.id(), n.type(), newSent));
      }

      public static Notification withSent(Notification n, boolean newSent) {
        return new Notification(n.id(), n.type(), newSent);
      }
    }

    class NotificationQueueTraversals {
      public static Traversal<NotificationQueue, Notification> notifications() {
        return listTraversal(
            NotificationQueue::notifications,
            (nq, notifications) -> new NotificationQueue(notifications));
      }
    }

    NotificationQueue queue =
        new NotificationQueue(
            List.of(
                new Notification("1", new Email7("user@example.com", "Welcome"), false),
                new Notification("2", new SMS7("555-1234", "Code: 1234"), false),
                new Notification("3", new Email7("admin@example.com", "Alert"), false)));

    // TODO: Replace null with a composition that:
    // 1. Traverses all notifications
    // 2. Filters to Email types only
    // 3. Gets the email subject
    var emailSubjectsTraversal =
        NotificationQueueTraversals.notifications()
            .andThen(NotificationLenses.type().asTraversal())
            .andThen(NotificationTypePrisms.email().asTraversal())
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
   * Congratulations! You've completed Tutorial 07: Generated Optics
   *
   * <p>You now understand: ✓ How to use @GenerateLenses for automatic lens creation ✓ How to
   * use @GeneratePrisms for sealed interface variants ✓ How to use @GenerateTraversals for
   * collections ✓ How generated optics compose like manual ones ✓ How to use with* helpers for
   * convenient updates ✓ How to combine all three annotations in real scenarios
   *
   * <p>Next: Tutorial 08 - Real World Optics
   */
}

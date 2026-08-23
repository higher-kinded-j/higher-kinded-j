// Fixture for hkj-book/src/optics/copy_strategies.md
//
// The page generates optics for external types through the four copy
// strategies. The "external" types and their spec interfaces live here, so the
// annotation processor generates the *Optics companions during snippet
// compilation and the page's snippets exercise them.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.math.BigDecimal;
import java.util.List;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.ImportOptics;
import org.higherkindedj.optics.annotations.OpticsSpec;
import org.higherkindedj.optics.annotations.ThroughField;
import org.higherkindedj.optics.annotations.ViaBuilder;
import org.higherkindedj.optics.annotations.ViaConstructor;
import org.higherkindedj.optics.annotations.ViaCopyAndSet;
import org.higherkindedj.optics.annotations.Wither;
import org.higherkindedj.optics.util.Traversals;

/** Stands in for a builder-based generated type (JOOQ POJO, Lombok @Builder, Immutables). */
final class Customer {
  private final String name;
  private final BigDecimal creditLimit;

  Customer(String name, BigDecimal creditLimit) {
    this.name = name;
    this.creditLimit = creditLimit;
  }

  public String name() {
    return name;
  }

  public BigDecimal creditLimit() {
    return creditLimit;
  }

  public Builder toBuilder() {
    return new Builder().name(name).creditLimit(creditLimit);
  }

  static final class Builder {
    private String name;
    private BigDecimal creditLimit;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder creditLimit(BigDecimal creditLimit) {
      this.creditLimit = creditLimit;
      return this;
    }

    public Customer build() {
      return new Customer(name, creditLimit);
    }
  }
}

/** Stands in for a wither-based immutable type. */
final class Money {
  private final String currency;
  private final long amount;

  Money(String currency, long amount) {
    this.currency = currency;
    this.amount = amount;
  }

  public String getCurrency() {
    return currency;
  }

  public long getAmount() {
    return amount;
  }

  public Money withAmount(long newAmount) {
    return new Money(currency, newAmount);
  }
}

/** Stands in for a constructor-only value type. */
final class Point {
  private final int x;
  private final int y;

  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }
}

/** Stands in for a legacy mutable type with a copy constructor and setters. */
final class Config {
  private String host;

  Config(String host) {
    this.host = host;
  }

  Config(Config other) {
    this.host = other.host;
  }

  public String host() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }
}

/** Stands in for an order type with a collection field. */
final class Order {
  private final List<Customer> customers;

  Order(List<Customer> customers) {
    this.customers = customers;
  }

  public List<Customer> customers() {
    return customers;
  }

  public Builder toBuilder() {
    return new Builder().customers(customers);
  }

  static final class Builder {
    private List<Customer> customers;

    public Builder customers(List<Customer> customers) {
      this.customers = customers;
      return this;
    }

    public Order build() {
      return new Order(customers);
    }
  }
}

@ImportOptics
interface CustomerOpticsSpec extends OpticsSpec<Customer> {

  @ViaBuilder
  Lens<Customer, String> name();

  @ViaBuilder
  Lens<Customer, BigDecimal> creditLimit();
}

@ImportOptics
interface MoneyOpticsSpec extends OpticsSpec<Money> {

  @Wither(value = "withAmount", getter = "getAmount")
  Lens<Money, Long> amount();
}

@ImportOptics
interface PointOpticsSpec extends OpticsSpec<Point> {

  @ViaConstructor(parameterOrder = {"x", "y"})
  Lens<Point, Integer> x();

  @ViaConstructor(parameterOrder = {"x", "y"})
  Lens<Point, Integer> y();
}

@ImportOptics
interface ConfigOpticsSpec extends OpticsSpec<Config> {

  @ViaCopyAndSet(setter = "setHost")
  Lens<Config, String> host();
}

@ImportOptics
interface OrderOpticsSpec extends OpticsSpec<Order> {

  @ViaBuilder
  Lens<Order, List<Customer>> customers();

  @ThroughField(field = "customers")
  Traversal<Order, Customer> eachCustomer();
}

class Fixture {
  static final Customer alice = new Customer("Alice", new BigDecimal("1000"));

  static final Order order = new Order(List.of(alice, new Customer("Bob", new BigDecimal("500"))));

  static final Money money = new Money("GBP", 2500L);

  static final Point origin = new Point(0, 0);

  static final Config config = new Config("localhost");
}

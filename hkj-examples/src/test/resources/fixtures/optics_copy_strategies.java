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
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.higherkindedj.example.book.optics.Audited;
import org.higherkindedj.example.book.optics.BaseEndpoint;
import org.higherkindedj.example.book.optics.Endpoint;
import org.higherkindedj.example.book.optics.cast.CastFixtures;
import org.higherkindedj.example.book.optics.cast.EmailAddress;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.example.book.optics.cast.LineItemLenses;
import org.higherkindedj.example.book.optics.cast.OrderStatus;
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
import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.TableField;
import org.jooq.impl.CustomRecord;
import org.jooq.impl.CustomTable;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

/**
 * The chapter cast's Customer, with the cast's components, in the form a builder-based generated
 * type takes (JOOQ POJO, Lombok @Builder, Immutables).
 */
final class Customer {
  private final String name;
  private final EmailAddress email;

  Customer(String name, EmailAddress email) {
    this.name = name;
    this.email = email;
  }

  public String name() {
    return name;
  }

  public EmailAddress email() {
    return email;
  }

  public Builder toBuilder() {
    return new Builder().name(name).email(email);
  }

  static final class Builder {
    private String name;
    private EmailAddress email;

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder email(EmailAddress email) {
      this.email = email;
      return this;
    }

    public Customer build() {
      return new Customer(name, email);
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

/**
 * The chapter cast's Order, with the cast's components, in the same builder-based form; its lines
 * are the cast's LineItem records. getId and withId are the JavaBean getter and prefixed builder
 * setter the page's Lombok naming example names.
 */
final class Order {
  private final UUID id;
  private final Customer customer;
  private final List<LineItem> lines;
  private final Instant placedAt;
  private final Currency currency;
  private final OrderStatus status;

  Order(
      UUID id,
      Customer customer,
      List<LineItem> lines,
      Instant placedAt,
      Currency currency,
      OrderStatus status) {
    this.id = id;
    this.customer = customer;
    this.lines = lines;
    this.placedAt = placedAt;
    this.currency = currency;
    this.status = status;
  }

  public UUID id() {
    return id;
  }

  public UUID getId() {
    return id;
  }

  public Customer customer() {
    return customer;
  }

  public List<LineItem> lines() {
    return lines;
  }

  public Instant placedAt() {
    return placedAt;
  }

  public Currency currency() {
    return currency;
  }

  public OrderStatus status() {
    return status;
  }

  public Builder toBuilder() {
    return new Builder()
        .id(id)
        .customer(customer)
        .lines(lines)
        .placedAt(placedAt)
        .currency(currency)
        .status(status);
  }

  static final class Builder {
    private UUID id;
    private Customer customer;
    private List<LineItem> lines;
    private Instant placedAt;
    private Currency currency;
    private OrderStatus status;

    public Builder id(UUID id) {
      this.id = id;
      return this;
    }

    public Builder withId(UUID id) {
      return id(id);
    }

    public Builder customer(Customer customer) {
      this.customer = customer;
      return this;
    }

    public Builder lines(List<LineItem> lines) {
      this.lines = lines;
      return this;
    }

    public Builder placedAt(Instant placedAt) {
      this.placedAt = placedAt;
      return this;
    }

    public Builder currency(Currency currency) {
      this.currency = currency;
      return this;
    }

    public Builder status(OrderStatus status) {
      this.status = status;
      return this;
    }

    public Order build() {
      return new Order(id, customer, lines, placedAt, currency, status);
    }
  }
}

/** Stands in for a type that spells all four builder steps differently. */
final class LegacyType {

  private final String name;

  LegacyType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  static final class Builder {

    private String name;

    public Builder setName(String name) {
      this.name = name;
      return this;
    }

    public LegacyType create() {
      return new LegacyType(name);
    }
  }
}

record Entry(String key, String value) {}

record MyType(List<Entry> entries) {}

@ImportOptics
interface CustomerOpticsSpec extends OpticsSpec<Customer> {

  @ViaBuilder
  Lens<Customer, String> name();

  @ViaBuilder
  Lens<Customer, EmailAddress> email();
}

@ImportOptics
interface MoneyOpticsSpec extends OpticsSpec<Money> {

  @Wither(value = "withAmount", getter = "getAmount")
  Lens<Money, Long> amount();
}

@ImportOptics
interface PointOpticsSpec extends OpticsSpec<Point> {

  @ViaConstructor
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
  Lens<Order, List<LineItem>> lines();

  @ThroughField(field = "lines")
  Traversal<Order, LineItem> eachLine();
}

/**
 * Stands in for the CUSTOMER table jOOQ generates from a schema, built from jOOQ's own CustomTable
 * so the page's query is checked against the real jOOQ API.
 */
final class CustomerTable extends CustomTable<CustomerRecord> {

  static final CustomerTable CUSTOMER_TABLE = new CustomerTable();

  final TableField<CustomerRecord, Boolean> ACTIVE =
      createField(DSL.name("active"), SQLDataType.BOOLEAN);

  private CustomerTable() {
    super(DSL.name("customer"));
  }

  @Override
  public Class<CustomerRecord> getRecordType() {
    return CustomerRecord.class;
  }
}

/**
 * Stands in for the record jOOQ generates for that table, a guest from jOOQ: a row of the table,
 * named as jOOQ names a table's record, rather than the chapter's Customer.
 */
final class CustomerRecord extends CustomRecord<CustomerRecord> {

  CustomerRecord() {
    super(CustomerTable.CUSTOMER_TABLE);
  }
}

class Fixture {
  static final CustomerTable CUSTOMER = CustomerTable.CUSTOMER_TABLE;

  static final DSLContext ctx = DSL.using(SQLDialect.DEFAULT);

  static final Customer ada = new Customer("Ada", new EmailAddress("ada@example.com"));

  static final Order order =
      new Order(
          CastFixtures.ORDER_ID,
          ada,
          List.of(CastFixtures.LAMP, CastFixtures.BULB),
          CastFixtures.PLACED_AT,
          CastFixtures.GBP,
          OrderStatus.NEW);

  static final Money money = new Money("GBP", 2500L);

  static final Point origin = new Point(0, 0);

  static final Config config = new Config("localhost");
}

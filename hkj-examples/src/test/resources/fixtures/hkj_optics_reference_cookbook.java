// Fixture for .claude/skills/hkj-optics/reference/cookbook.md
//
// Each recipe is an independent illustration, so each is compiled independently. A recipe that
// declares its own records shadows the ones here; a recipe that does not gets them from here. The
// domain types below therefore mirror the shapes the recipes show, and the generated-style
// companions (`OrderLenses`, `LoadedLenses`, ...) are hand-written rather than generated: the
// processor cannot generate a companion for a record a snippet has re-declared for itself, and the
// same class must compile against every recipe on the page.
//
// A name keeps one shape across the page, so a recipe that re-declares a record declares the same
// components as this copy. The order, its customer and lines, the org chart, the product, the
// inventory and the configuration map are the book's own types, with its components; the rest are
// the page's.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.Monoids;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.hkt.maybe.MaybeKind;
import org.higherkindedj.hkt.maybe.MaybeKindHelper;
import org.higherkindedj.hkt.maybe.MaybeMonad;
import org.higherkindedj.optics.Affine;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Prisms;
import org.higherkindedj.optics.util.Traversals;

// ===== Recipe 1 / best practice 2 / best practice 4: nested Optional fields =====

record DisplaySettings(boolean darkMode, int fontSize) {}

record PatronProfile(String bio, Optional<DisplaySettings> settings) {}

record Patron(String name, Optional<PatronProfile> profile) {}

final class DisplaySettingsLenses {
  static Lens<DisplaySettings, Integer> fontSize() {
    return Lens.of(
        DisplaySettings::fontSize, (s, size) -> new DisplaySettings(s.darkMode(), size));
  }
}

final class PatronProfileLenses {
  static Lens<PatronProfile, Optional<DisplaySettings>> settings() {
    return Lens.of(PatronProfile::settings, (p, s) -> new PatronProfile(p.bio(), s));
  }
}

final class PatronLenses {
  static Lens<Patron, String> name() {
    return Lens.of(Patron::name, (p, name) -> new Patron(name, p.profile()));
  }

  static Lens<Patron, Optional<PatronProfile>> profile() {
    return Lens.of(Patron::profile, (p, profile) -> new Patron(p.name(), profile));
  }
}

// ===== Recipe 2: a sum type whose variants carry fields =====

record Data(String value) {}

sealed interface FetchState permits Loaded, LoadFailed, Loading {}

record Loaded(Data data, String timestamp) implements FetchState {}

record LoadFailed(String message, int code) implements FetchState {}

record Loading() implements FetchState {}

final class LoadedLenses {
  static Lens<Loaded, Data> data() {
    return Lens.of(Loaded::data, (l, d) -> new Loaded(d, l.timestamp()));
  }
}

// ===== Recipe 3 / 10 / 14 / best practices 1 and 3: orders and their lines =====

record EmailAddress(String value) {}

record Customer(String name, EmailAddress email) {}

enum OrderStatus {
  NEW,
  PAID,
  SHIPPED
}

record LineItem(String sku, Integer quantity, BigDecimal price) {}

record Order(
    UUID id,
    Customer customer,
    List<LineItem> lines,
    Instant placedAt,
    Currency currency,
    OrderStatus status) {}

final class OrderLenses {
  static Lens<Order, List<LineItem>> lines() {
    return Lens.of(
        Order::lines,
        (o, lines) ->
            new Order(o.id(), o.customer(), lines, o.placedAt(), o.currency(), o.status()));
  }
}

final class LineItemLenses {
  static Lens<LineItem, String> sku() {
    return Lens.of(LineItem::sku, (li, sku) -> new LineItem(sku, li.quantity(), li.price()));
  }

  static Lens<LineItem, Integer> quantity() {
    return Lens.of(LineItem::quantity, (li, q) -> new LineItem(li.sku(), q, li.price()));
  }

  static Lens<LineItem, BigDecimal> price() {
    return Lens.of(LineItem::price, (li, p) -> new LineItem(li.sku(), li.quantity(), p));
  }
}

// ===== Recipe 4: polymorphic activity =====

sealed interface Activity permits UserAction, SystemNotice {}

record UserAction(String userId, String action) implements Activity {}

record SystemNotice(String level, String message) implements Activity {}

final class UserActionLenses {
  static Lens<UserAction, String> action() {
    return Lens.of(UserAction::action, (a, action) -> new UserAction(a.userId(), action));
  }
}

// ===== Recipe 5 / 15: configuration =====

record ConfigMap(Map<String, String> settings) {}

final class ConfigMapLenses {
  static Lens<ConfigMap, Map<String, String>> settings() {
    return Lens.of(ConfigMap::settings, (c, s) -> new ConfigMap(s));
  }
}

// ===== Recipe 6 / 11: the org chart =====

@GenerateFocus
record OrgMember(String name, int age, Optional<String> email, Integer salary) {}

@GenerateFocus
record OrgUnit(String name, List<OrgMember> members) {}

@GenerateFocus
record OrgChart(String name, List<OrgUnit> units) {}

final class OrgMemberLenses {
  static Lens<OrgMember, Integer> salary() {
    return Lens.of(
        OrgMember::salary, (m, salary) -> new OrgMember(m.name(), m.age(), m.email(), salary));
  }
}

final class OrgUnitLenses {
  static Lens<OrgUnit, List<OrgMember>> members() {
    return Lens.of(OrgUnit::members, (u, members) -> new OrgUnit(u.name(), members));
  }
}

final class OrgChartLenses {
  static Lens<OrgChart, List<OrgUnit>> units() {
    return Lens.of(OrgChart::units, (c, units) -> new OrgChart(c.name(), units));
  }
}

// ===== Recipe 7 / 13: the catalogue =====

record Product(String sku, String name, BigDecimal price, int stock) {}

record Inventory(List<Product> products, String warehouseId) {}

final class ProductLenses {
  static Lens<Product, BigDecimal> price() {
    return Lens.of(
        Product::price, (p, price) -> new Product(p.sku(), p.name(), price, p.stock()));
  }
}

// ===== Recipe 8: Either =====

record FormError(String message) {}

record UserData(String name, String email) {}

// ===== Recipe 9: a guild and its engineers =====

record Engineer(String name, String email) {}

record Guild(String name, Engineer lead, List<Engineer> members) {}

// ===== Recipe 12: notifications =====

sealed interface Notification permits EmailMessage, SMS, Push {}

record EmailMessage(String address, String subject, String body) implements Notification {}

record SMS(String number, String text) implements Notification {}

record Push(String token, String text) implements Notification {}

record Outbox(List<Notification> notifications) {}

class Fixture {

  // --- the structures the recipes operate on ---

  static final Patron patron = new Patron("Alice", Optional.empty());

  static final FetchState state = new Loaded(new Data("ok"), "2026-01-01T00:00:00Z");

  static final Order order =
      new Order(
          UUID.randomUUID(),
          new Customer("Alice", new EmailAddress("alice@example.com")),
          List.of(),
          Instant.EPOCH,
          Currency.getInstance("GBP"),
          OrderStatus.NEW);

  static final List<Activity> activities = List.of();

  static final ConfigMap config = new ConfigMap(Map.of("api.key", "0123456789"));

  static final OrgChart company = new OrgChart("Acme", List.of());

  static final Guild guild =
      new Guild("Platform", new Engineer("Alice", "alice@co"), List.of());

  static final List<Product> products = List.of();

  static final Inventory inventory = new Inventory(List.of(), "WH-1");

  static final Outbox outbox = new Outbox(List.of());

  static final Either<FormError, UserData> result =
      Either.right(new UserData("alice", "alice@example.com"));

  // --- Recipe 9: the lenses the page names but never builds ---

  static final Lens<Guild, Engineer> guildLeadLens =
      Lens.of(Guild::lead, (g, lead) -> new Guild(g.name(), lead, g.members()));

  static final Lens<Engineer, String> engineerNameLens =
      Lens.of(Engineer::name, (e, name) -> new Engineer(name, e.email()));

  static final Lens<Engineer, String> engineerEmailLens =
      Lens.of(Engineer::email, (e, email) -> new Engineer(e.name(), email));

  // --- Recipe 11 ---

  static final Lens<OrgChart, List<OrgUnit>> chartUnitsLens = OrgChartLenses.units();

  static final Lens<OrgUnit, List<OrgMember>> unitMembersLens = OrgUnitLenses.members();

  static final Lens<OrgMember, Integer> memberSalaryLens = OrgMemberLenses.salary();

  // --- Recipe 12 ---

  static final Lens<Outbox, List<Notification>> outboxNotificationsLens =
      Lens.of(Outbox::notifications, (o, ns) -> new Outbox(ns));

  static final Lens<EmailMessage, String> messageAddressLens =
      Lens.of(EmailMessage::address, (e, a) -> new EmailMessage(a, e.subject(), e.body()));

  static final Lens<EmailMessage, String> emailSubjectLens =
      Lens.of(EmailMessage::subject, (e, s) -> new EmailMessage(e.address(), s, e.body()));

  // --- Recipe 13 ---

  static final Lens<Inventory, List<Product>> inventoryProductsLens =
      Lens.of(Inventory::products, (i, ps) -> new Inventory(ps, i.warehouseId()));

  // --- Recipe 14 ---

  static final Lens<Order, List<LineItem>> orderLinesLens = OrderLenses.lines();

  static final Lens<LineItem, Integer> lineItemQuantityLens = LineItemLenses.quantity();

  static final Lens<LineItem, BigDecimal> lineItemPriceLens = LineItemLenses.price();

  static final Monoid<Integer> intSumMonoid = Monoids.integerAddition();

  static final Monoid<BigDecimal> decimalSumMonoid =
      new Monoid<>() {
        @Override
        public BigDecimal empty() {
          return BigDecimal.ZERO;
        }

        @Override
        public BigDecimal combine(BigDecimal a, BigDecimal b) {
          return a.add(b);
        }
      };

  // --- Recipe 15 ---

  static final Lens<ConfigMap, String> configApiKeyLens =
      Lens.of(
          c -> c.settings().getOrDefault("api.key", ""),
          (c, key) -> {
            Map<String, String> updated = new HashMap<>(c.settings());
            updated.put("api.key", key);
            return new ConfigMap(Map.copyOf(updated));
          });

  // --- Best practice 2 ---

  /** An optional nested record: the field the page composes a Prisms.some() onto. */
  static final Lens<PatronProfile, Optional<DisplaySettings>> settingsLens =
      PatronProfileLenses.settings();

  static final Prism<Optional<DisplaySettings>, DisplaySettings> settingsPrism = Prisms.some();
}

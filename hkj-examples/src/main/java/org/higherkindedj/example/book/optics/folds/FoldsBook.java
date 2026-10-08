// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.folds;

import static org.higherkindedj.optics.extensions.FoldExtensions.findMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.getAllMaybe;
import static org.higherkindedj.optics.extensions.FoldExtensions.previewMaybe;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.Monoid;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateFolds;
import org.higherkindedj.optics.annotations.GenerateLenses;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/folds.html">Folds</a> page. The page {@code
 * {{#include}}}s the anchored regions, and {@code FoldsBookTest} holds the claims the page makes
 * about this code.
 */
public final class FoldsBook {

  /** The lens the page's ordering example reads a name through. */
  static final Lens<Employee, String> NAME_LENS =
      Lens.of(Employee::name, (e, name) -> new Employee(name, e.email()));

  /** The lens the page's ordering example reads an email through. */
  static final Lens<Employee, String> EMAIL_LENS =
      Lens.of(Employee::email, (e, email) -> new Employee(e.name(), email));

  private FoldsBook() {}

  /** The purchase the page builds in Step 2, and queries in the steps after it. */
  static Purchase purchase() {
    // ANCHOR: purchase
    Purchase purchase =
        new Purchase(
            "ORD-123",
            List.of(
                new Product("Laptop", new BigDecimal("999.99"), "Electronics", true),
                new Product("Mouse", new BigDecimal("25.00"), "Electronics", true),
                new Product("Desk", new BigDecimal("350.00"), "Furniture", false)),
            "Alice");
    // ANCHOR_END: purchase
    return purchase;
  }

  static List<Product> getAll(Purchase purchase) {
    // ANCHOR: get_all
    Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

    List<Product> allProducts = itemsFold.getAll(purchase);
    // [Product[name=Laptop, price=999.99, ...], Product[name=Mouse, ...], Product[name=Desk, ...]]
    // ANCHOR_END: get_all
    return allProducts;
  }

  static List<Optional<Product>> preview(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: preview
    Optional<Product> firstProduct = itemsFold.preview(purchase);
    // Optional[Product[name=Laptop, price=999.99, ...]]

    Purchase emptyPurchase = new Purchase("ORD-456", List.of(), "Bob");
    Optional<Product> noProduct = itemsFold.preview(emptyPurchase);
    // Optional.empty
    // ANCHOR_END: preview
    return List.of(firstProduct, noProduct);
  }

  static Optional<Product> find(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: find
    Optional<Product> expensiveProduct =
        itemsFold.find(product -> product.price().compareTo(new BigDecimal("500")) > 0, purchase);
    // Optional[Product[name=Laptop, price=999.99, ...]]
    // ANCHOR_END: find
    return expensiveProduct;
  }

  static boolean exists(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: exists
    boolean hasOutOfStock = itemsFold.exists(product -> !product.inStock(), purchase);
    // true: the desk is out of stock
    // ANCHOR_END: exists
    return hasOutOfStock;
  }

  static boolean all(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: all
    boolean allInStock = itemsFold.all(product -> product.inStock(), purchase);
    // false: the desk is out of stock
    // ANCHOR_END: all
    return allInStock;
  }

  static boolean hasItems(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: is_empty
    boolean hasItems = !itemsFold.isEmpty(purchase);
    // true
    // ANCHOR_END: is_empty
    return hasItems;
  }

  static int length(Fold<Purchase, Product> itemsFold, Purchase purchase) {
    // ANCHOR: length
    int itemCount = itemsFold.length(purchase);
    // 3
    // ANCHOR_END: length
    return itemCount;
  }

  static List<String> allProductNames(Purchase purchase) {
    // ANCHOR: compose
    // Get all product names from all purchases in history
    Fold<PurchaseHistory, Purchase> historyToPurchases = PurchaseHistoryFolds.purchases();
    Fold<Purchase, Product> purchaseToProducts = PurchaseFolds.items();
    Lens<Product, String> productToName = ProductLenses.name();

    Fold<PurchaseHistory, String> historyToAllProductNames =
        historyToPurchases.andThen(purchaseToProducts).andThen(productToName.asFold());

    Purchase secondPurchase =
        new Purchase(
            "ORD-124",
            List.of(
                new Product("Keyboard", new BigDecimal("75.00"), "Electronics", true),
                new Product("Monitor", new BigDecimal("450.00"), "Electronics", true)),
            "Bob");
    PurchaseHistory history = new PurchaseHistory(List.of(purchase, secondPurchase));

    List<String> allProductNames = historyToAllProductNames.getAll(history);
    // [Laptop, Mouse, Desk, Keyboard, Monitor]
    // ANCHOR_END: compose
    return allProductNames;
  }

  static BigDecimal totalPrice(Purchase purchase) {
    // ANCHOR: total
    Fold<Purchase, Product> products = PurchaseFolds.items();

    // Define how to combine prices (addition)
    Monoid<BigDecimal> sumMonoid =
        new Monoid<>() {
          @Override
          public BigDecimal empty() {
            return BigDecimal.ZERO; // Start with zero
          }

          @Override
          public BigDecimal combine(BigDecimal a, BigDecimal b) {
            return a.add(b); // Add them
          }
        };

    // Extract each product's price and sum them all
    BigDecimal totalPrice =
        products.foldMap(
            sumMonoid,
            product -> product.price(), // Extract price from each product
            purchase);
    // 1374.99, which is 999.99 + 25.00 + 350.00
    // ANCHOR_END: total
    return totalPrice;
  }

  static List<Maybe<Product>> previewMaybeExample(Purchase purchase) {
    // ANCHOR: preview_maybe
    Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

    Maybe<Product> firstProduct = previewMaybe(itemsFold, purchase);
    // Just(Product[name=Laptop, price=999.99, ...])

    Purchase emptyPurchase = new Purchase("ORD-456", List.of(), "Bob");
    Maybe<Product> noProduct = previewMaybe(itemsFold, emptyPurchase);
    // Nothing
    // ANCHOR_END: preview_maybe
    return List.of(firstProduct, noProduct);
  }

  static List<Maybe<Product>> findMaybeExample(Purchase purchase) {
    // ANCHOR: find_maybe
    Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

    Maybe<Product> expensiveProduct =
        findMaybe(
            itemsFold, product -> product.price().compareTo(new BigDecimal("500")) > 0, purchase);
    // Just(Product[name=Laptop, price=999.99, ...])

    Maybe<Product> luxuryProduct =
        findMaybe(
            itemsFold, product -> product.price().compareTo(new BigDecimal("5000")) > 0, purchase);
    // Nothing
    // ANCHOR_END: find_maybe
    return List.of(expensiveProduct, luxuryProduct);
  }

  static List<Maybe<List<Product>>> getAllMaybeExample(Purchase purchase) {
    // ANCHOR: get_all_maybe
    Fold<Purchase, Product> itemsFold = PurchaseFolds.items();

    Maybe<List<Product>> allProducts = getAllMaybe(itemsFold, purchase);
    // Just([Product[name=Laptop, ...], Product[name=Mouse, ...], Product[name=Desk, ...]])

    Purchase emptyPurchase = new Purchase("ORD-456", List.of(), "Bob");
    Maybe<List<Product>> noProducts = getAllMaybe(itemsFold, emptyPurchase);
    // Nothing
    // ANCHOR_END: get_all_maybe
    return List.of(allProducts, noProducts);
  }

  static List<Object> emptyFold(Team team) {
    // ANCHOR: empty
    Fold<Team, String> nothing = Fold.empty();

    List<String> none = nothing.getAll(team);
    // []
    int count = nothing.length(team);
    // 0
    boolean empty = nothing.isEmpty(team);
    // true
    // ANCHOR_END: empty
    return List.of(none, count, empty);
  }

  static List<List<String>> ordering(
      Lens<Employee, String> nameLens, Lens<Employee, String> emailLens, Employee employee) {
    // ANCHOR: ordering
    Fold<Employee, String> nameFirst = nameLens.asFold().plus(emailLens.asFold());
    List<String> nameThenEmail = nameFirst.getAll(employee);
    // [Alice, alice@example.com]

    Fold<Employee, String> emailFirst = emailLens.asFold().plus(nameLens.asFold());
    List<String> emailThenName = emailFirst.getAll(employee);
    // [alice@example.com, Alice]
    // ANCHOR_END: ordering
    return List.of(nameThenEmail, emailThenName);
  }
}

@GenerateLenses
@GenerateFolds
record Product(String name, BigDecimal price, String category, boolean inStock) {}

@GenerateLenses
@GenerateFolds
record Purchase(String purchaseId, List<Product> items, String customerName) {}

@GenerateLenses
@GenerateFolds
record PurchaseHistory(List<Purchase> purchases) {}

record Employee(String name, String email) {}

record Team(String name, Employee lead, List<Employee> members) {}

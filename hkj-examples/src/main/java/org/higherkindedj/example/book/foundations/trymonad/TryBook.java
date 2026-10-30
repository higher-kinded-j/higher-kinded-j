// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.foundations.trymonad;

import static org.higherkindedj.hkt.instances.Witnesses.try_;
import static org.higherkindedj.hkt.trymonad.TryKindHelper.TRY;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.higherkindedj.example.book.optics.cast.LineItem;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadError;
import org.higherkindedj.hkt.effect.EitherPath;
import org.higherkindedj.hkt.effect.Path;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.trymonad.Try;
import org.higherkindedj.hkt.trymonad.TryKind;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/monads/try_monad.html">Try</a> page: a
 * wholesaler's order line parsed with {@code Try.of}, a feed file read with {@code Try.attempt}, a
 * check chained with {@code flatMap}, recovery, a {@code switch} that reads the result, the same
 * steps on a {@code TryPath}, and {@code Try}'s {@code MonadError} instance. The page {@code
 * {{#include}}}s the anchored regions, {@code TryBookTest} holds the claims the page makes, and the
 * book's output gate runs {@link #main} against the comments in the regions.
 */
public final class TryBook {

  /** A feed file that does not exist, so reading it fails with a checked exception. */
  static final String MISSING_FEED = "missing-feed.csv";

  private TryBook() {}

  // ANCHOR: parse
  // Integer.parseInt and new BigDecimal throw on text they cannot read
  static Try<LineItem> parseLine(String line) {
    return Try.of(
        () -> {
          String[] parts = line.split(",");
          return new LineItem(parts[0], Integer.parseInt(parts[1]), new BigDecimal(parts[2]));
        });
  }

  // ANCHOR_END: parse

  // ANCHOR: attempt
  // Files.readString declares IOException, a checked exception
  static Try<String> readFeed(String file) {
    return Try.attempt(() -> Files.readString(Paths.get(file)));
  }

  // ANCHOR_END: attempt

  // ANCHOR: chain
  static Try<LineItem> requirePositivePrice(LineItem item) {
    return item.price().signum() > 0
        ? Try.success(item)
        : Try.failure(new IllegalArgumentException("price must be positive: " + item.price()));
  }

  static Try<LineItem> checkedLine(String line) {
    return parseLine(line).flatMap(TryBook::requirePositivePrice);
  }

  // ANCHOR_END: chain

  // ANCHOR: recover
  // Some feeds write the pound sign, so try again without it
  static Try<BigDecimal> price(String text) {
    return Try.of(() -> new BigDecimal(text))
        .recoverWith(error -> Try.of(() -> new BigDecimal(text.replace("£", ""))));
  }

  // ANCHOR_END: recover

  // ANCHOR: describe
  static String describe(Try<LineItem> result) {
    return switch (result) {
      case Try.Success(LineItem item) -> item.quantity() + " x " + item.sku();
      case Try.Failure(Throwable cause) -> "unreadable line: " + cause.getMessage();
    };
  }

  // ANCHOR_END: describe

  // ANCHOR: path
  static EitherPath<String, LineItem> lineOrReason(String line) {
    return Path.tryPath(parseLine(line))
        .via(item -> Path.tryPath(requirePositivePrice(item)))
        .toEitherPath(error -> "skipped " + line + ": " + error.getMessage());
  }

  // ANCHOR_END: path

  public static void main(String[] args) {
    // ANCHOR: parse_outcomes
    Try<LineItem> lamp = parseLine("LAMP,2,40.00");
    // Success(LineItem[sku=LAMP, quantity=2, price=40.00])
    Try<LineItem> misspelt = parseLine("LAMP,two,40.00");
    // Failure(java.lang.NumberFormatException: For input string: "two")
    // ANCHOR_END: parse_outcomes
    System.out.println(lamp);
    System.out.println(misspelt);

    // ANCHOR: attempt_outcome
    Try<String> feed = readFeed(MISSING_FEED);
    // Failure(java.nio.file.NoSuchFileException: missing-feed.csv)
    // ANCHOR_END: attempt_outcome
    System.out.println(feed);

    // ANCHOR: chain_outcomes
    Try<LineItem> free = checkedLine("LAMP,2,0.00");
    // Failure(java.lang.IllegalArgumentException: price must be positive: 0.00)
    Try<LineItem> unread = checkedLine("LAMP,two,0.00");
    // Failure(java.lang.NumberFormatException: For input string: "two")
    // ANCHOR_END: chain_outcomes
    System.out.println(free);
    System.out.println(unread);

    // ANCHOR: recover_outcomes
    Try<BigDecimal> withSign = price("£40.00");
    // Success(40.00)
    Try<BigDecimal> unreadable = price("forty");
    // Failure(java.lang.NumberFormatException: ...)
    Try<String> noFeed = readFeed(MISSING_FEED).recover(error -> "");
    // Success()
    // ANCHOR_END: recover_outcomes
    System.out.println(withSign);
    System.out.println(unreadable);
    System.out.println(noFeed);

    // ANCHOR: path_outcome
    EitherPath<String, LineItem> skipped = lineOrReason("LAMP,2,0.00");
    // EitherPath(Left(skipped LAMP,2,0.00: price must be positive: 0.00))
    // ANCHOR_END: path_outcome
    System.out.println(skipped);

    // ANCHOR: kind
    MonadError<TryKind.Witness, Throwable> tries = Instances.monadError(try_());

    Kind<TryKind.Witness, LineItem> parsed = TRY.widen(parseLine("LAMP,two,40.00"));
    Kind<TryKind.Witness, LineItem> checked =
        tries.flatMap(item -> TRY.widen(requirePositivePrice(item)), parsed);
    Kind<TryKind.Witness, String> answer =
        tries.handleError(
            tries.map(item -> "read " + item.sku(), checked),
            error -> "skipped: " + error.getClass().getSimpleName());

    Try<String> reply = TRY.narrow(answer);
    // Success(skipped: NumberFormatException)
    // ANCHOR_END: kind
    System.out.println(reply);
  }
}

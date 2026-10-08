// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.example.book.optics.getters;

import static org.higherkindedj.optics.extensions.GetterExtensions.getMaybe;

import java.util.function.Function;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Getter;
import org.jspecify.annotations.Nullable;

/**
 * The code shown on the book's <a
 * href="https://higher-kinded-j.github.io/latest/optics/getters.html">Getters</a> page, in its
 * pattern for Maybe-safe composed getters. The page {@code {{#include}}}s the anchored region, and
 * the book's output gate runs {@code main} to hold its output comment to what it prints.
 */
// ANCHOR: safe_getters
public class SafeGetters {
  // Create a null-safe composed getter using Maybe
  public static <A, B, C> Function<A, Maybe<C>> safePath(Getter<A, B> first, Getter<B, C> second) {
    return source ->
        getMaybe(first, source).flatMap(intermediate -> getMaybe(second, intermediate));
  }

  // Usage example
  private static final Function<Person, Maybe<String>> SAFE_CITY_LOOKUP =
      safePath(Getter.of(Person::address), Getter.of(Address::city));

  public static void main(String[] args) {
    Person person = new Person("Jane", "Smith", 45, null);
    Maybe<String> city = SAFE_CITY_LOOKUP.apply(person);
    // Nothing   <- the null address is handled safely
    System.out.println(city);
  }
}

// ANCHOR_END: safe_getters

/** The page's address, as its earlier sections declare it. */
record Address(String street, String city, String zipCode, String country) {}

/** The page's person, whose address may be missing. */
record Person(String firstName, String lastName, int age, @Nullable Address address) {}

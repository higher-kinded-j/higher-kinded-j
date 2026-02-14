// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.maybe.Maybe;
import org.higherkindedj.optics.Affine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for the {@link Affines} utility class. */
class AffinesTest {

  // ============================================================================
  // some() Tests
  // ============================================================================

  @Nested
  @DisplayName("some() tests")
  class SomeTests {

    @Test
    @DisplayName("getOptional should return value when Optional is present")
    void getOptionalWhenPresent() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.of("hello");

      Optional<String> result = someAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when Optional is empty")
    void getOptionalWhenEmpty() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.empty();

      Optional<String> result = someAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should wrap value in Optional.of()")
    void setWrapsInOptional() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.of("old");

      Optional<String> result = someAffine.set("new", source);

      assertTrue(result.isPresent());
      assertEquals("new", result.get());
    }

    @Test
    @DisplayName("set on empty Optional should return non-empty Optional")
    void setOnEmptyOptional() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.empty();

      Optional<String> result = someAffine.set("new", source);

      assertTrue(result.isPresent());
      assertEquals("new", result.get());
    }
  }

  // ============================================================================
  // someWithRemove() Tests
  // ============================================================================

  @Nested
  @DisplayName("someWithRemove() tests")
  class SomeWithRemoveTests {

    @Test
    @DisplayName("getOptional should return value when Optional is present")
    void getOptionalWhenPresent() {
      Affine<Optional<String>, String> someAffine = Affines.someWithRemove();
      Optional<String> source = Optional.of("hello");

      Optional<String> result = someAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("set should wrap value in Optional.of()")
    void setWrapsInOptional() {
      Affine<Optional<String>, String> someAffine = Affines.someWithRemove();
      Optional<String> source = Optional.of("old");

      Optional<String> result = someAffine.set("new", source);

      assertTrue(result.isPresent());
      assertEquals("new", result.get());
    }

    @Test
    @DisplayName("remove should return empty Optional")
    void removeReturnsEmpty() {
      Affine<Optional<String>, String> someAffine = Affines.someWithRemove();
      Optional<String> source = Optional.of("hello");

      Optional<String> result = someAffine.remove(source);

      assertTrue(result.isEmpty());
    }
  }

  // ============================================================================
  // just() Tests
  // ============================================================================

  @Nested
  @DisplayName("just() tests")
  class JustTests {

    @Test
    @DisplayName("getOptional should return value when Maybe is Just")
    void getOptionalWhenJust() {
      Affine<Maybe<String>, String> justAffine = Affines.just();
      Maybe<String> source = Maybe.just("hello");

      Optional<String> result = justAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when Maybe is Nothing")
    void getOptionalWhenNothing() {
      Affine<Maybe<String>, String> justAffine = Affines.just();
      Maybe<String> source = Maybe.nothing();

      Optional<String> result = justAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should return Maybe.just() with new value")
    void setReturnsJust() {
      Affine<Maybe<String>, String> justAffine = Affines.just();
      Maybe<String> source = Maybe.just("old");

      Maybe<String> result = justAffine.set("new", source);

      assertTrue(result.isJust());
      assertEquals("new", result.get());
    }

    @Test
    @DisplayName("set on Nothing should return Just with new value")
    void setOnNothingReturnsJust() {
      Affine<Maybe<String>, String> justAffine = Affines.just();
      Maybe<String> source = Maybe.nothing();

      Maybe<String> result = justAffine.set("new", source);

      assertTrue(result.isJust());
      assertEquals("new", result.get());
    }
  }

  // ============================================================================
  // nullable() Tests
  // ============================================================================

  @Nested
  @DisplayName("nullable() tests")
  class NullableTests {

    @Test
    @DisplayName("getOptional should return value when non-null")
    void getOptionalWhenNonNull() {
      Affine<@Nullable String, String> nullableAffine = Affines.nullable();
      String source = "hello";

      Optional<String> result = nullableAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("hello", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when null")
    void getOptionalWhenNull() {
      Affine<@Nullable String, String> nullableAffine = Affines.nullable();
      @Nullable String source = null;

      Optional<String> result = nullableAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should return the new value directly")
    void setReturnsNewValue() {
      Affine<@Nullable String, String> nullableAffine = Affines.nullable();
      @Nullable String source = "old";

      String result = nullableAffine.set("new", source);

      assertEquals("new", result);
    }
  }

  // ============================================================================
  // listHead() Tests
  // ============================================================================

  @Nested
  @DisplayName("listHead() tests")
  class ListHeadTests {

    @Test
    @DisplayName("getOptional should return first element when list is non-empty")
    void getOptionalWhenNonEmpty() {
      Affine<List<String>, String> headAffine = Affines.listHead();
      List<String> source = List.of("first", "second", "third");

      Optional<String> result = headAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("first", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when list is empty")
    void getOptionalWhenEmpty() {
      Affine<List<String>, String> headAffine = Affines.listHead();
      List<String> source = List.of();

      Optional<String> result = headAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should replace first element preserving rest")
    void setReplacesFirst() {
      Affine<List<String>, String> headAffine = Affines.listHead();
      List<String> source = List.of("first", "second", "third");

      List<String> result = headAffine.set("NEW", source);

      assertEquals(List.of("NEW", "second", "third"), result);
    }

    @Test
    @DisplayName("set on empty list should return singleton list")
    void setOnEmptyReturnsNewList() {
      Affine<List<String>, String> headAffine = Affines.listHead();
      List<String> source = List.of();

      List<String> result = headAffine.set("NEW", source);

      assertEquals(List.of("NEW"), result);
    }
  }

  // ============================================================================
  // listLast() Tests
  // ============================================================================

  @Nested
  @DisplayName("listLast() tests")
  class ListLastTests {

    @Test
    @DisplayName("getOptional should return last element when list is non-empty")
    void getOptionalWhenNonEmpty() {
      Affine<List<String>, String> lastAffine = Affines.listLast();
      List<String> source = List.of("first", "second", "third");

      Optional<String> result = lastAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("third", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when list is empty")
    void getOptionalWhenEmpty() {
      Affine<List<String>, String> lastAffine = Affines.listLast();
      List<String> source = List.of();

      Optional<String> result = lastAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should replace last element preserving rest")
    void setReplacesLast() {
      Affine<List<String>, String> lastAffine = Affines.listLast();
      List<String> source = List.of("first", "second", "third");

      List<String> result = lastAffine.set("NEW", source);

      assertEquals(List.of("first", "second", "NEW"), result);
    }

    @Test
    @DisplayName("set on empty list should return singleton list")
    void setOnEmptyReturnsNewList() {
      Affine<List<String>, String> lastAffine = Affines.listLast();
      List<String> source = List.of();

      List<String> result = lastAffine.set("NEW", source);

      assertEquals(List.of("NEW"), result);
    }
  }

  // ============================================================================
  // listAt() Tests
  // ============================================================================

  @Nested
  @DisplayName("listAt() tests")
  class ListAtTests {

    @Test
    @DisplayName("getOptional should return element at index when in bounds")
    void getOptionalWhenInBounds() {
      Affine<List<String>, String> atAffine = Affines.listAt(1);
      List<String> source = List.of("first", "second", "third");

      Optional<String> result = atAffine.getOptional(source);

      assertTrue(result.isPresent());
      assertEquals("second", result.get());
    }

    @Test
    @DisplayName("getOptional should return empty when index out of bounds (too high)")
    void getOptionalWhenOutOfBoundsHigh() {
      Affine<List<String>, String> atAffine = Affines.listAt(10);
      List<String> source = List.of("first", "second");

      Optional<String> result = atAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getOptional should return empty when index is negative")
    void getOptionalWhenNegativeIndex() {
      Affine<List<String>, String> atAffine = Affines.listAt(-1);
      List<String> source = List.of("first", "second");

      Optional<String> result = atAffine.getOptional(source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("set should replace element at index preserving rest")
    void setReplacesAtIndex() {
      Affine<List<String>, String> atAffine = Affines.listAt(1);
      List<String> source = List.of("first", "second", "third");

      List<String> result = atAffine.set("NEW", source);

      assertEquals(List.of("first", "NEW", "third"), result);
    }

    @Test
    @DisplayName("set should return original list when index out of bounds")
    void setWhenOutOfBounds() {
      Affine<List<String>, String> atAffine = Affines.listAt(10);
      List<String> source = List.of("first", "second");

      List<String> result = atAffine.set("NEW", source);

      assertEquals(source, result);
    }

    @Test
    @DisplayName("set should return original list when index is negative")
    void setWhenNegativeIndex() {
      Affine<List<String>, String> atAffine = Affines.listAt(-1);
      List<String> source = List.of("first", "second");

      List<String> result = atAffine.set("NEW", source);

      assertEquals(source, result);
    }
  }

  // ============================================================================
  // Utility Method Tests
  // ============================================================================

  @Nested
  @DisplayName("Utility method tests")
  class UtilityMethodTests {

    @Test
    @DisplayName("getAll should return singleton list when value is present")
    void getAllWhenPresent() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.of("hello");

      List<String> result = Affines.getAll(someAffine, source);

      assertEquals(List.of("hello"), result);
    }

    @Test
    @DisplayName("getAll should return empty list when value is absent")
    void getAllWhenAbsent() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.empty();

      List<String> result = Affines.getAll(someAffine, source);

      assertEquals(List.of(), result);
    }

    @Test
    @DisplayName("modify should apply function to focused value")
    void modifyAppliesFunction() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.of("hello");

      Optional<String> result = Affines.modify(someAffine, String::toUpperCase, source);

      assertTrue(result.isPresent());
      assertEquals("HELLO", result.get());
    }

    @Test
    @DisplayName("modify should return unchanged when no focus")
    void modifyWhenNoFocus() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.empty();

      Optional<String> result = Affines.modify(someAffine, String::toUpperCase, source);

      assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("matches should return true when value is present")
    void matchesWhenPresent() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.of("hello");

      boolean result = Affines.matches(someAffine, source);

      assertTrue(result);
    }

    @Test
    @DisplayName("matches should return false when value is absent")
    void matchesWhenAbsent() {
      Affine<Optional<String>, String> someAffine = Affines.some();
      Optional<String> source = Optional.empty();

      boolean result = Affines.matches(someAffine, source);

      assertFalse(result);
    }
  }

  // ============================================================================
  // Integration Tests
  // ============================================================================

  @Nested
  @DisplayName("Integration tests")
  class IntegrationTests {

    record User(String name, Optional<String> email, Maybe<String> nickname) {}

    @Test
    @DisplayName("Affines work with records containing optional fields")
    void affinesWithRecords() {
      Affine<User, Optional<String>> emailLens =
          Affine.of(
              user -> Optional.of(user.email()),
              (user, email) -> new User(user.name(), email, user.nickname()));

      Affine<User, String> emailAffine = emailLens.andThen(Affines.some());

      User user = new User("Alice", Optional.of("alice@example.com"), Maybe.nothing());

      // Get email
      Optional<String> email = emailAffine.getOptional(user);
      assertTrue(email.isPresent());
      assertEquals("alice@example.com", email.get());

      // Modify email
      User updated = emailAffine.modify(String::toUpperCase, user);
      assertEquals("ALICE@EXAMPLE.COM", updated.email().get());
    }

    @Test
    @DisplayName("listAt works with mutable lists passed as source")
    void listAtWithMutableList() {
      Affine<List<String>, String> atAffine = Affines.listAt(1);
      List<String> source = new ArrayList<>(List.of("first", "second", "third"));

      List<String> result = atAffine.set("NEW", source);

      // Original should be unchanged (defensive copy)
      assertEquals(List.of("first", "second", "third"), source);
      assertEquals(List.of("first", "NEW", "third"), result);
    }
  }
}

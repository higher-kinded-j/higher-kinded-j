// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.validated;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import org.higherkindedj.hkt.Kind;
import org.junit.jupiter.api.Test;

/** Tests for {@link ValidatedKindHelper}. */
class ValidatedKindHelperTest {

  private final String testValue = "TestValue";
  private final List<String> testError = Collections.singletonList("TestError");
  private final Integer testIntValue = 42;

  // --- Test widen() ---

  @Test
  void widen_withValidInstance_shouldReturnKind() {
    Validated<List<String>, String> originalValid = Validated.valid(testValue);
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.widen(originalValid);

    assertNotNull(kind);
    // To fully verify, narrow it back
    Validated<List<String>, String> narrowed = VALIDATED.narrow(kind);
    assertEquals(originalValid, narrowed);
    assertTrue(narrowed.isValid());
    assertEquals(testValue, narrowed.get());
  }

  @Test
  void widen_withInvalidInstance_shouldReturnKind() {
    Validated<List<String>, String> originalInvalid = Validated.invalid(testError);
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.widen(originalInvalid);

    assertNotNull(kind);
    // To fully verify, narrow it back
    Validated<List<String>, String> narrowed = VALIDATED.narrow(kind);
    assertEquals(originalInvalid, narrowed);
    assertTrue(narrowed.isInvalid());
    assertEquals(testError, narrowed.getError());
  }

  // --- Test narrow() ---

  @Test
  void narrow_withKindFromValid_shouldReturnValidatedInstance() {
    Validated<List<String>, Integer> originalValid = Validated.valid(testIntValue);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kind = VALIDATED.widen(originalValid);
    Validated<List<String>, Integer> narrowed = VALIDATED.narrow(kind);

    assertNotNull(narrowed);
    assertInstanceOf(Valid.class, narrowed);
    assertEquals(originalValid, narrowed);
    assertTrue(narrowed.isValid());
    assertEquals(testIntValue, narrowed.get());
  }

  @Test
  void narrow_withKindFromInvalid_shouldReturnValidatedInstance() {
    Validated<List<String>, Integer> originalInvalid = Validated.invalid(testError);
    Kind<ValidatedKind.Witness<List<String>>, Integer> kind = VALIDATED.widen(originalInvalid);
    Validated<List<String>, Integer> narrowed = VALIDATED.narrow(kind);

    assertNotNull(narrowed);
    assertInstanceOf(Invalid.class, narrowed);
    assertEquals(originalInvalid, narrowed);
    assertTrue(narrowed.isInvalid());
    assertEquals(testError, narrowed.getError());
    assertThrows(NoSuchElementException.class, narrowed::get);
  }

  // Test narrow with a non-Validated Kind (difficult to set up without other Kind types)
  // Typically, this would involve creating a mock or a different Kind implementation.
  // For now, we rely on the type safety of the witness and the explicit cast.
  // If a different Kind were passed, a ClassCastException would occur, which is expected.

  // --- Test factory valid() ---

  @Test
  void factoryValid_shouldCreateAKindRepresentingValid() {
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.valid(testValue);
    assertNotNull(kind);

    Validated<List<String>, String> validated = VALIDATED.narrow(kind);
    assertTrue(validated.isValid());
    assertFalse(validated.isInvalid());
    assertEquals(testValue, validated.get());
    assertThrows(NoSuchElementException.class, validated::getError);
  }

  @Test
  void factoryValid_withNullValue_shouldThrowNullPointerException() {
    // Validated.valid() itself throws NPE for null, so widen(Validated.valid(null)) will also fail
    // early.
    assertThrows(
        NullPointerException.class,
        () -> {
          VALIDATED.valid(null);
        });
  }

  // --- Test factory invalid() ---

  @Test
  void factoryInvalid_shouldCreateAKindRepresentingInvalid() {
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.invalid(testError);
    assertNotNull(kind);

    Validated<List<String>, String> validated = VALIDATED.narrow(kind);
    assertTrue(validated.isInvalid());
    assertFalse(validated.isValid());
    assertEquals(testError, validated.getError());
    assertThrows(NoSuchElementException.class, validated::get);
  }

  @Test
  void factoryInvalid_withNullError_shouldThrowNullPointerException() {
    // Validated.invalid() itself throws NPE for null, so widen(Validated.invalid(null)) will also
    // fail early.
    List<String> nullErrorList = null;
    assertThrows(
        NullPointerException.class,
        () -> {
          VALIDATED.invalid(nullErrorList);
        });
  }

  // --- Test round trip widen and narrow ---
  @Test
  void roundTrip_validInstance_preservesIdentityAndData() {
    Validated<List<String>, String> original = Validated.valid("Round trip");
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.widen(original);
    Validated<List<String>, String> narrowed = VALIDATED.narrow(kind);

    assertEquals(original, narrowed);
    assertTrue(narrowed.isValid());
    assertEquals("Round trip", narrowed.get());
  }

  @Test
  void roundTrip_invalidInstance_preservesIdentityAndData() {
    List<String> error = Collections.singletonList("Error round trip");
    Validated<List<String>, String> original = Validated.invalid(error);
    Kind<ValidatedKind.Witness<List<String>>, String> kind = VALIDATED.widen(original);
    Validated<List<String>, String> narrowed = VALIDATED.narrow(kind);

    assertEquals(original, narrowed);
    assertTrue(narrowed.isInvalid());
    assertEquals(error, narrowed.getError());
  }
}

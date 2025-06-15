// Copyright (c) 2025 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.hkt.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class UnitTest {

  @Test
  void testUnitInstanceAndToString() {
    // Access the enum instance to cover its initialization
    Unit unitInstance = Unit.INSTANCE;
    assertNotNull(unitInstance, "Unit.INSTANCE should not be null");

    // Call the toString() method to cover its execution
    String unitString = unitInstance.toString();
    assertEquals("()", unitString, "toString() should return '()'");
  }
}

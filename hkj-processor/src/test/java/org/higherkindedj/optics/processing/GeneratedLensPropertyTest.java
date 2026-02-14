// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.compile;
import static org.higherkindedj.optics.processing.RuntimeCompilationHelper.packageInfo;

import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import net.jqwik.api.lifecycle.BeforeContainer;
import org.higherkindedj.optics.processing.RuntimeCompilationHelper.CompiledResult;

/**
 * Property-based tests for generated lenses using jqwik.
 *
 * <p>These tests verify that generated lenses satisfy the fundamental lens laws with random inputs:
 *
 * <ul>
 *   <li><b>GetPut</b>: {@code set(get(s), s) == s}
 *   <li><b>PutGet</b>: {@code get(set(a, s)) == a}
 *   <li><b>PutPut</b>: {@code set(a2, set(a1, s)) == set(a2, s)}
 * </ul>
 *
 * <p>The tests use runtime compilation to generate lenses for test types, then verify the laws hold
 * for hundreds of randomly generated test cases.
 */
class GeneratedLensPropertyTest {

  private static final String TEST_PACKAGE = "org.higherkindedj.test.optics";

  // Test record with multiple field types
  private static final JavaFileObject PERSON_RECORD =
      JavaFileObjects.forSourceString(
          "org.higherkindedj.test.Person",
          """
          package org.higherkindedj.test;

          public record Person(String name, int age, boolean active) {}
          """);

  private static final JavaFileObject PACKAGE_INFO =
      packageInfo(TEST_PACKAGE, "org.higherkindedj.test.Person");

  private static CompiledResult compiled;

  @BeforeContainer
  static void compileTestTypes() {
    compiled = compile(PERSON_RECORD, PACKAGE_INFO);
  }

  // ===== Lens Law Properties for String field =====

  @Property(tries = 100)
  @Label("GetPut Law for name lens: set(get(s), s) == s")
  void nameLensGetPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object gotten = compiled.invokeLensGet(lens, person);
    Object result = compiled.invokeLensSet(lens, gotten, person);

    assertThat(result).isEqualTo(person);
  }

  @Property(tries = 100)
  @Label("PutGet Law for name lens: get(set(a, s)) == a")
  void nameLensPutGetLaw(
      @ForAll @StringLength(min = 1, max = 50) String originalName,
      @ForAll @StringLength(min = 1, max = 50) String newName,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
    Object person =
        compiled.newInstance("org.higherkindedj.test.Person", originalName, age, active);

    Object updated = compiled.invokeLensSet(lens, newName, person);
    Object gotten = compiled.invokeLensGet(lens, updated);

    assertThat(gotten).isEqualTo(newName);
  }

  @Property(tries = 100)
  @Label("PutPut Law for name lens: set(a2, set(a1, s)) == set(a2, s)")
  void nameLensPutPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @StringLength(min = 1, max = 50) String name1,
      @ForAll @StringLength(min = 1, max = 50) String name2,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object setTwice =
        compiled.invokeLensSet(lens, name2, compiled.invokeLensSet(lens, name1, person));
    Object setOnce = compiled.invokeLensSet(lens, name2, person);

    assertThat(setTwice).isEqualTo(setOnce);
  }

  // ===== Lens Law Properties for int field =====

  @Property(tries = 100)
  @Label("GetPut Law for age lens: set(get(s), s) == s")
  void ageLensGetPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "age");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object gotten = compiled.invokeLensGet(lens, person);
    Object result = compiled.invokeLensSet(lens, gotten, person);

    assertThat(result).isEqualTo(person);
  }

  @Property(tries = 100)
  @Label("PutGet Law for age lens: get(set(a, s)) == a")
  void ageLensPutGetLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int originalAge,
      @ForAll @IntRange(min = 0, max = 150) int newAge,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "age");
    Object person =
        compiled.newInstance("org.higherkindedj.test.Person", name, originalAge, active);

    Object updated = compiled.invokeLensSet(lens, newAge, person);
    Object gotten = compiled.invokeLensGet(lens, updated);

    assertThat(gotten).isEqualTo(newAge);
  }

  @Property(tries = 100)
  @Label("PutPut Law for age lens: set(a2, set(a1, s)) == set(a2, s)")
  void ageLensPutPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll @IntRange(min = 0, max = 150) int age1,
      @ForAll @IntRange(min = 0, max = 150) int age2,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "age");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object setTwice =
        compiled.invokeLensSet(lens, age2, compiled.invokeLensSet(lens, age1, person));
    Object setOnce = compiled.invokeLensSet(lens, age2, person);

    assertThat(setTwice).isEqualTo(setOnce);
  }

  // ===== Lens Law Properties for boolean field =====

  @Property(tries = 100)
  @Label("GetPut Law for active lens: set(get(s), s) == s")
  void activeLensGetPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "active");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object gotten = compiled.invokeLensGet(lens, person);
    Object result = compiled.invokeLensSet(lens, gotten, person);

    assertThat(result).isEqualTo(person);
  }

  @Property(tries = 100)
  @Label("PutGet Law for active lens: get(set(a, s)) == a")
  void activeLensPutGetLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean originalActive,
      @ForAll boolean newActive)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "active");
    Object person =
        compiled.newInstance("org.higherkindedj.test.Person", name, age, originalActive);

    Object updated = compiled.invokeLensSet(lens, newActive, person);
    Object gotten = compiled.invokeLensGet(lens, updated);

    assertThat(gotten).isEqualTo(newActive);
  }

  @Property(tries = 100)
  @Label("PutPut Law for active lens: set(a2, set(a1, s)) == set(a2, s)")
  void activeLensPutPutLaw(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active,
      @ForAll boolean active1,
      @ForAll boolean active2)
      throws ReflectiveOperationException {

    Object lens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "active");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    Object setTwice =
        compiled.invokeLensSet(lens, active2, compiled.invokeLensSet(lens, active1, person));
    Object setOnce = compiled.invokeLensSet(lens, active2, person);

    assertThat(setTwice).isEqualTo(setOnce);
  }

  // ===== Additional Properties =====

  @Property(tries = 50)
  @Label("Multiple lens updates are independent")
  void multipleLensUpdatesAreIndependent(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active,
      @ForAll @StringLength(min = 1, max = 50) String newName,
      @ForAll @IntRange(min = 0, max = 150) int newAge)
      throws ReflectiveOperationException {

    Object nameLens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
    Object ageLens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "age");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    // Apply updates in one order
    Object order1 =
        compiled.invokeLensSet(ageLens, newAge, compiled.invokeLensSet(nameLens, newName, person));

    // Apply updates in other order
    Object order2 =
        compiled.invokeLensSet(nameLens, newName, compiled.invokeLensSet(ageLens, newAge, person));

    assertThat(order1).isEqualTo(order2);
  }

  @Property(tries = 50)
  @Label("Original object unchanged after lens operations")
  void originalUnchangedAfterLensOperations(
      @ForAll @StringLength(min = 1, max = 50) String name,
      @ForAll @IntRange(min = 0, max = 150) int age,
      @ForAll boolean active,
      @ForAll @StringLength(min = 1, max = 50) String newName)
      throws ReflectiveOperationException {

    Object nameLens = compiled.invokeStatic(TEST_PACKAGE + ".PersonLenses", "name");
    Object person = compiled.newInstance("org.higherkindedj.test.Person", name, age, active);

    // Store original values
    Object originalName = compiled.invokeLensGet(nameLens, person);

    // Modify
    compiled.invokeLensSet(nameLens, newName, person);

    // Original should be unchanged
    assertThat(compiled.invokeLensGet(nameLens, person)).isEqualTo(originalName);
  }
}

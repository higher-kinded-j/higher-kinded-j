// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.free;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.*;
import java.util.List;
import java.util.Optional;
import org.higherkindedj.hkt.free.Free;
import org.higherkindedj.optics.Fold;
import org.higherkindedj.optics.Getter;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.FocusPaths;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Traversals;
import org.junit.jupiter.api.Test;

/** Tests for the Free monad DSL {@link OpticPrograms} and interpreters. */
class OpticProgramsTest {

  // Test data
  record Person(String name, int age) {}

  static final Lens<Person, String> NAME_LENS =
      Lens.of(Person::name, (person, name) -> new Person(name, person.age()));

  static final Lens<Person, Integer> AGE_LENS =
      Lens.of(Person::age, (person, age) -> new Person(person.name(), age));

  record Team(String name, List<Person> members) {}

  static final Lens<Team, String> TEAM_NAME_LENS =
      Lens.of(Team::name, (team, name) -> new Team(name, team.members()));

  static final Lens<Team, List<Person>> TEAM_MEMBERS_LENS =
      Lens.of(Team::members, (team, members) -> new Team(team.name(), members));

  static final Traversal<Team, Person> TEAM_MEMBERS_TRAVERSAL =
      TEAM_MEMBERS_LENS.asTraversal().andThen(Traversals.forList());

  // ============================================================================
  // Direct Interpreter Tests
  // ============================================================================

  @Test
  void testGetProgram() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, String> program = OpticPrograms.get(person, NAME_LENS);

    String result = OpticInterpreters.direct().run(program);
    assertEquals("Alice", result);
  }

  @Test
  void testSetProgram() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program = OpticPrograms.set(person, NAME_LENS, "Bob");

    Person result = OpticInterpreters.direct().run(program);
    assertEquals("Bob", result.name());
    assertEquals(25, result.age());
  }

  @Test
  void testModifyProgram() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, AGE_LENS, age -> age + 1);

    Person result = OpticInterpreters.direct().run(program);
    assertEquals("Alice", result.name());
    assertEquals(26, result.age());
  }

  @Test
  void testPreviewProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25)));
    Free<OpticOpKind.Witness, Optional<Person>> program =
        OpticPrograms.preview(team, TEAM_MEMBERS_TRAVERSAL);

    Optional<Person> result = OpticInterpreters.direct().run(program);
    assertTrue(result.isPresent());
    assertEquals("Alice", result.get().name());
  }

  @Test
  void testGetAllProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Free<OpticOpKind.Witness, List<Person>> program =
        OpticPrograms.getAll(team, TEAM_MEMBERS_TRAVERSAL);

    List<Person> result = OpticInterpreters.direct().run(program);
    assertEquals(2, result.size());
  }

  @Test
  void testModifyAllProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Free<OpticOpKind.Witness, Team> program = OpticPrograms.modifyAll(team, ages, age -> age + 1);

    Team result = OpticInterpreters.direct().run(program);
    assertEquals(26, result.members().get(0).age());
    assertEquals(31, result.members().get(1).age());
  }

  @Test
  void testSetAllProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Free<OpticOpKind.Witness, Team> program = OpticPrograms.setAll(team, ages, 21);

    Team result = OpticInterpreters.direct().run(program);
    assertEquals(21, result.members().get(0).age());
    assertEquals(21, result.members().get(1).age());
  }

  @Test
  void testExistsProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Free<OpticOpKind.Witness, Boolean> program = OpticPrograms.exists(team, ages, age -> age >= 30);

    Boolean result = OpticInterpreters.direct().run(program);
    assertTrue(result);
  }

  @Test
  void testAllProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Free<OpticOpKind.Witness, Boolean> program = OpticPrograms.all(team, ages, age -> age >= 18);

    Boolean result = OpticInterpreters.direct().run(program);
    assertTrue(result);
  }

  @Test
  void testCountProgram() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Free<OpticOpKind.Witness, Integer> program = OpticPrograms.count(team, TEAM_MEMBERS_TRAVERSAL);

    Integer result = OpticInterpreters.direct().run(program);
    assertEquals(2, result);
  }

  // ============================================================================
  // Composition Tests
  // ============================================================================

  @Test
  void testComposedProgram() {
    Person person = new Person("Alice", 25);

    // Compose multiple operations
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, NAME_LENS, name -> name.toUpperCase())
            .flatMap(p1 -> OpticPrograms.modify(p1, AGE_LENS, age -> age + 1));

    Person result = OpticInterpreters.direct().run(program);
    assertEquals("ALICE", result.name());
    assertEquals(26, result.age());
  }

  @Test
  void testConditionalProgram() {
    Person minor = new Person("Alice", 17);
    Person adult = new Person("Bob", 25);

    Person minorResult = OpticInterpreters.direct().run(createAgeIncrementProgram(minor));
    assertEquals(17, minorResult.age()); // No change

    Person adultResult = OpticInterpreters.direct().run(createAgeIncrementProgram(adult));
    assertEquals(26, adultResult.age()); // Incremented
  }

  // Helper method: creates a program that only modifies if age >= 18
  private Free<OpticOpKind.Witness, Person> createAgeIncrementProgram(Person p) {
    return OpticPrograms.get(p, AGE_LENS)
        .flatMap(
            age -> {
              if (age >= 18) {
                return OpticPrograms.modify(p, AGE_LENS, a -> a + 1);
              } else {
                return OpticPrograms.pure(p);
              }
            });
  }

  @Test
  void testComplexProgram() {
    Person person = new Person("Alice", 25);

    // Multi-step program with conditional logic
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.get(person, AGE_LENS)
            .flatMap(
                age ->
                    OpticPrograms.modify(person, AGE_LENS, a -> a + 1)
                        .flatMap(
                            p1 ->
                                OpticPrograms.modify(
                                        p1, NAME_LENS, name -> name + " (Age: " + (age + 1) + ")")
                                    .flatMap(
                                        p2 ->
                                            OpticPrograms.get(p2, AGE_LENS)
                                                .flatMap(
                                                    newAge -> {
                                                      if (newAge > 25) {
                                                        return OpticPrograms.modify(
                                                            p2, NAME_LENS, n -> n + " [Senior]");
                                                      } else {
                                                        return OpticPrograms.pure(p2);
                                                      }
                                                    }))));

    Person result = OpticInterpreters.direct().run(program);
    assertEquals(26, result.age());
    assertEquals("Alice (Age: 26) [Senior]", result.name());
  }

  // ============================================================================
  // Logging Interpreter Tests
  // ============================================================================

  @Test
  void testLoggingInterpreter() {
    Person person = new Person("Alice", 25);

    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, AGE_LENS, age -> age + 1)
            .flatMap(p1 -> OpticPrograms.set(p1, NAME_LENS, "Bob"));

    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Person result = logger.run(program);

    // Check result
    assertEquals("Bob", result.name());
    assertEquals(26, result.age());

    // Check log
    List<String> log = logger.getLog();
    assertEquals(2, log.size());
    assertTrue(log.get(0).contains("MODIFY"));
    assertTrue(log.get(1).contains("SET"));
  }

  @Test
  void testLoggingInterpreterMultipleRuns() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, AGE_LENS, age -> age + 1);

    LoggingOpticInterpreter logger = OpticInterpreters.logging();

    // First run
    logger.run(program);
    assertEquals(1, logger.getLog().size());

    // Clear and run again
    logger.clearLog();
    logger.run(program);
    assertEquals(1, logger.getLog().size());
  }

  @Test
  void testLoggingInterpreterWithAnonymousOptic() {
    Person person = new Person("Alice", 25);

    // Create an actual anonymous Getter to test opticName branch coverage
    // This creates an anonymous inner class with empty getSimpleName()
    Getter<Person, String> anonymousGetter =
        new Getter<Person, String>() {
          @Override
          public String get(Person source) {
            return source.name();
          }
        };

    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Free<OpticOpKind.Witness, String> program = OpticPrograms.get(person, anonymousGetter);

    String result = logger.run(program);
    assertEquals("Alice", result);
    assertEquals(1, logger.getLog().size());
    assertTrue(logger.getLog().get(0).contains("GET"));
    // Verify the optic name contains the class name (from getName() with lastDot >= 0)
    assertTrue(logger.getLog().get(0).contains("OpticProgramsTest$"));
  }

  @Test
  void testLoggingInterpreterAllOperations() {
    Person person = new Person("Alice", 25);
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    LoggingOpticInterpreter logger = OpticInterpreters.logging();

    // Test GET
    logger.run(OpticPrograms.get(person, NAME_LENS));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("GET"));

    // Test PREVIEW
    logger.run(OpticPrograms.preview(team, TEAM_MEMBERS_TRAVERSAL));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("PREVIEW"));

    // Test GET_ALL
    logger.run(OpticPrograms.getAll(team, TEAM_MEMBERS_TRAVERSAL));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("GET_ALL"));

    // Test SET
    logger.run(OpticPrograms.set(person, NAME_LENS, "Bob"));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("SET"));

    // Test SET_ALL
    logger.run(OpticPrograms.setAll(team, ages, 21));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("SET_ALL"));

    // Test MODIFY
    logger.run(OpticPrograms.modify(person, AGE_LENS, age -> age + 1));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("MODIFY"));

    // Test MODIFY_ALL
    logger.run(OpticPrograms.modifyAll(team, ages, age -> age + 1));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("MODIFY_ALL"));

    // Test EXISTS
    logger.run(OpticPrograms.exists(team, ages, age -> age >= 18));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("EXISTS"));

    // Test ALL
    logger.run(OpticPrograms.all(team, ages, age -> age >= 18));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("ALL"));

    // Test COUNT
    logger.run(OpticPrograms.count(team, TEAM_MEMBERS_TRAVERSAL));
    assertTrue(logger.getLog().get(logger.getLog().size() - 1).contains("COUNT"));

    // Verify all operations logged
    assertTrue(logger.getLog().size() >= 10);
  }

  @Test
  void testLoggingInterpreterOpticNameWithDot() {
    LoggingOpticInterpreter logger = OpticInterpreters.logging();

    // Test with an anonymous class (getSimpleName() is empty)
    // The class name will be something like "org.package.ClassName$1" with dots
    Object anonymousOptic =
        new Getter<Person, String>() {
          @Override
          public String get(Person source) {
            return source.name();
          }
        };

    String result = logger.opticName(anonymousOptic);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    // Should contain the substring after the last dot (e.g., "OpticProgramsTest$1")
    assertTrue(
        result.contains("OpticProgramsTest") || result.contains("$"),
        "Expected class name after last dot, got: " + result);
  }

  // ============================================================================
  // Validation Interpreter Tests
  // ============================================================================

  @Test
  void testValidationInterpreterValid() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, AGE_LENS, age -> age + 1);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    assertTrue(result.isValid());
    assertEquals(0, result.errors().size());
  }

  @Test
  void testValidationInterpreterWarnings() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program = OpticPrograms.set(person, NAME_LENS, null);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    // Should warn about null value
    assertTrue(result.isValid()); // No errors, but...
    assertTrue(result.hasWarnings()); // ...has warnings
  }

  @Test
  void testValidationInterpreterAllOperations() {
    Person person = new Person("Alice", 25);
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    ValidationOpticInterpreter validator = OpticInterpreters.validating();

    // Test GET
    assertTrue(validator.validate(OpticPrograms.get(person, NAME_LENS)).isValid());

    // Test PREVIEW
    assertTrue(validator.validate(OpticPrograms.preview(team, TEAM_MEMBERS_TRAVERSAL)).isValid());

    // Test GET_ALL
    assertTrue(validator.validate(OpticPrograms.getAll(team, TEAM_MEMBERS_TRAVERSAL)).isValid());

    // Test SET
    assertTrue(validator.validate(OpticPrograms.set(person, NAME_LENS, "Bob")).isValid());

    // Test SET_ALL
    assertTrue(validator.validate(OpticPrograms.setAll(team, ages, 21)).isValid());

    // Test MODIFY
    assertTrue(
        validator.validate(OpticPrograms.modify(person, AGE_LENS, age -> age + 1)).isValid());

    // Test MODIFY_ALL
    assertTrue(validator.validate(OpticPrograms.modifyAll(team, ages, age -> age + 1)).isValid());

    // Test EXISTS
    assertTrue(validator.validate(OpticPrograms.exists(team, ages, age -> age >= 18)).isValid());

    // Test ALL
    assertTrue(validator.validate(OpticPrograms.all(team, ages, age -> age >= 18)).isValid());

    // Test COUNT
    assertTrue(validator.validate(OpticPrograms.count(team, TEAM_MEMBERS_TRAVERSAL)).isValid());
  }

  @Test
  void testValidationInterpreterErrors() {
    Person person = new Person("Alice", 25);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();

    // Test null check warnings
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.set(person, NAME_LENS, null));
    assertTrue(result.hasWarnings());
    assertFalse(result.warnings().isEmpty());
  }

  @Test
  void testValidationResultToString() {
    Person person = new Person("Alice", 25);
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.modify(person, AGE_LENS, age -> age + 1));

    String toString = result.toString();
    assertNotNull(toString);
    assertTrue(toString.contains("ValidationResult"));
  }

  @Test
  void testValidationInterpreterSetAllWithNull() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.setAll(team, ages, null));

    // Should warn about null value AND have error because execution fails (null Integer)
    assertTrue(result.hasWarnings()); // ...has warnings about null
    assertFalse(result.isValid()); // Has errors because execution fails
    assertFalse(result.errors().isEmpty());
  }

  @Test
  void testValidationInterpreterModifyProducingNull() {
    Person person = new Person("Alice", 25);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.modify(person, NAME_LENS, name -> null));

    // Should warn about null value produced by modifier
    assertTrue(result.isValid()); // No errors, but...
    assertTrue(result.hasWarnings()); // ...has warnings
    assertFalse(result.warnings().isEmpty());
  }

  @Test
  void testValidationInterpreterModifyWithException() {
    Person person = new Person("Alice", 25);

    // Create a lens that throws an exception when getting
    Lens<Person, String> faultyLens =
        Lens.of(
            p -> {
              throw new RuntimeException("Test exception");
            },
            (p, name) -> new Person(name, p.age()));

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.modify(person, faultyLens, name -> name.toUpperCase()));

    // Should have errors due to exception
    assertFalse(result.isValid());
    assertFalse(result.errors().isEmpty());
    assertTrue(result.errors().get(0).contains("MODIFY operation failed"));
  }

  @Test
  void testValidationInterpreterNoWarnings() {
    Person person = new Person("Alice", 25);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.modify(person, AGE_LENS, age -> age + 1));

    // Should have no errors and no warnings
    assertTrue(result.isValid());
    assertFalse(result.hasWarnings());
    assertTrue(result.warnings().isEmpty());
    assertTrue(result.errors().isEmpty());
  }

  @Test
  void testValidationInterpreterChainedOperations() {
    // This test verifies that chained flatMap operations work correctly.
    // The validation interpreter must execute operations to provide proper
    // results for subsequent flatMap calls.
    Person person = new Person("Alice", 25);

    // Chain multiple operations: set name -> modify age -> modify name
    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.set(person, NAME_LENS, "Bob")
            .flatMap(p -> OpticPrograms.modify(p, AGE_LENS, age -> age + 1))
            .flatMap(p -> OpticPrograms.modify(p, NAME_LENS, name -> name.toUpperCase()));

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    // Should validate successfully with no errors
    assertTrue(result.isValid(), "Chained operations should validate successfully");
    assertTrue(result.errors().isEmpty(), "Should have no errors");
  }

  @Test
  void testValidationInterpreterChainedOperationsMatchDirectInterpreter() {
    // Verify that validation interpreter produces the same result as direct interpreter
    // for chained operations
    Person person = new Person("Alice", 25);

    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.set(person, NAME_LENS, "Bob")
            .flatMap(p -> OpticPrograms.modify(p, AGE_LENS, age -> age + 5))
            .flatMap(p -> OpticPrograms.set(p, NAME_LENS, "Charlie"));

    // Run with direct interpreter
    Person directResult = OpticInterpreters.direct().run(program);

    // Validation should succeed
    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult validationResult = validator.validate(program);

    assertTrue(validationResult.isValid());
    assertEquals("Charlie", directResult.name());
    assertEquals(30, directResult.age());
  }

  @Test
  void testValidationInterpreterChainedWithGetOperation() {
    // Test that GET operations in a chain work correctly
    Person person = new Person("Alice", 25);

    Free<OpticOpKind.Witness, String> program =
        OpticPrograms.modify(person, AGE_LENS, age -> age + 1)
            .flatMap(p -> OpticPrograms.get(p, NAME_LENS));

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

    assertTrue(result.isValid());
  }

  @Test
  void testValidationInterpreterSetWithException() {
    Person person = new Person("Alice", 25);

    // Create a lens that throws an exception when setting
    Lens<Person, String> faultyLens =
        Lens.of(
            Person::name,
            (p, name) -> {
              throw new RuntimeException("Set failed");
            });

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.set(person, faultyLens, "Bob"));

    // Should have errors due to exception during set
    assertFalse(result.isValid());
    assertFalse(result.errors().isEmpty());
    assertTrue(result.errors().get(0).contains("SET operation failed"));
  }

  @Test
  void testValidationInterpreterModifyAllWithException() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));

    // Create a traversal with a lens that throws during modification
    Lens<Person, Integer> faultyAgeLens =
        Lens.of(
            Person::age,
            (p, age) -> {
              throw new RuntimeException("ModifyAll failed");
            });
    Traversal<Team, Integer> faultyAges =
        TEAM_MEMBERS_TRAVERSAL.andThen(faultyAgeLens.asTraversal());

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationOpticInterpreter.ValidationResult result =
        validator.validate(OpticPrograms.modifyAll(team, faultyAges, age -> age + 1));

    // Should have errors due to exception during modifyAll
    assertFalse(result.isValid());
    assertFalse(result.errors().isEmpty());
    assertTrue(result.errors().get(0).contains("MODIFY_ALL operation failed"));
  }

  // ============================================================================
  // Pure Value Tests
  // ============================================================================

  @Test
  void testPureValue() {
    Person person = new Person("Alice", 25);
    Free<OpticOpKind.Witness, Person> program = OpticPrograms.pure(person);

    Person result = OpticInterpreters.direct().run(program);
    assertEquals(person, result);
  }

  @Test
  void testPureInConditional() {
    Person person = new Person("Alice", 25);

    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.get(person, AGE_LENS)
            .flatMap(
                age -> {
                  if (age < 18) {
                    return OpticPrograms.pure(person); // No changes
                  } else {
                    return OpticPrograms.modify(person, AGE_LENS, a -> a + 1);
                  }
                });

    Person result = OpticInterpreters.direct().run(program);
    assertEquals(26, result.age()); // Modified because age >= 18
  }

  // ============================================================================
  // Test Traversal Parameter Overloads
  // ============================================================================

  @Test
  void testProgramsWithTraversalParameters() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());

    // Test preview with Traversal
    Free<OpticOpKind.Witness, Optional<Person>> previewProgram =
        OpticPrograms.preview(team, TEAM_MEMBERS_TRAVERSAL);
    Optional<Person> firstPerson = OpticInterpreters.direct().run(previewProgram);
    assertTrue(firstPerson.isPresent());
    assertEquals("Alice", firstPerson.get().name());

    // Test getAll with Traversal
    Free<OpticOpKind.Witness, List<Person>> getAllProgram =
        OpticPrograms.getAll(team, TEAM_MEMBERS_TRAVERSAL);
    List<Person> allMembers = OpticInterpreters.direct().run(getAllProgram);
    assertEquals(2, allMembers.size());

    // Test exists with Traversal
    Free<OpticOpKind.Witness, Boolean> existsProgram =
        OpticPrograms.exists(team, ages, age -> age >= 30);
    assertTrue(OpticInterpreters.direct().run(existsProgram));

    // Test all with Traversal
    Free<OpticOpKind.Witness, Boolean> allProgram = OpticPrograms.all(team, ages, age -> age >= 18);
    assertTrue(OpticInterpreters.direct().run(allProgram));

    // Test count with Traversal
    Free<OpticOpKind.Witness, Integer> countProgram =
        OpticPrograms.count(team, TEAM_MEMBERS_TRAVERSAL);
    assertEquals(2, OpticInterpreters.direct().run(countProgram));
  }

  // ============================================================================
  // Test Fold Parameter Overloads
  // ============================================================================

  @Test
  void testProgramsWithFoldParameters() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    Fold<Team, Person> membersFold = Fold.of(Team::members);
    Traversal<Team, Integer> ages = TEAM_MEMBERS_TRAVERSAL.andThen(AGE_LENS.asTraversal());
    Fold<Team, Integer> agesFold = Fold.of(t -> Traversals.getAll(ages, t));

    // Test preview with Fold
    Free<OpticOpKind.Witness, Optional<Person>> previewProgram =
        OpticPrograms.preview(team, membersFold);
    Optional<Person> firstPerson = OpticInterpreters.direct().run(previewProgram);
    assertTrue(firstPerson.isPresent());
    assertEquals("Alice", firstPerson.get().name());

    // Test getAll with Fold
    Free<OpticOpKind.Witness, List<Person>> getAllProgram = OpticPrograms.getAll(team, membersFold);
    List<Person> allMembers = OpticInterpreters.direct().run(getAllProgram);
    assertEquals(2, allMembers.size());

    // Test exists with Fold
    Free<OpticOpKind.Witness, Boolean> existsProgram =
        OpticPrograms.exists(team, agesFold, age -> age >= 30);
    assertTrue(OpticInterpreters.direct().run(existsProgram));

    // Test all with Fold
    Free<OpticOpKind.Witness, Boolean> allProgram =
        OpticPrograms.all(team, agesFold, age -> age >= 18);
    assertTrue(OpticInterpreters.direct().run(allProgram));

    // Test count with Fold
    Free<OpticOpKind.Witness, Integer> countProgram = OpticPrograms.count(team, membersFold);
    assertEquals(2, OpticInterpreters.direct().run(countProgram));
  }

  @Test
  void testGetProgramWithGetter() {
    Person person = new Person("Alice", 25);
    Getter<Person, String> nameGetter = Getter.of(Person::name);

    Free<OpticOpKind.Witness, String> program = OpticPrograms.get(person, nameGetter);
    String result = OpticInterpreters.direct().run(program);
    assertEquals("Alice", result);
  }

  // ============================================================================
  // Focus DSL Overload Tests
  // ============================================================================

  @Test
  void testGetWithFocusPath() {
    Person person = new Person("Alice", 25);
    FocusPath<Person, String> namePath = FocusPath.of(NAME_LENS);

    Free<OpticOpKind.Witness, String> program = OpticPrograms.get(person, namePath);
    String result = OpticInterpreters.direct().run(program);
    assertEquals("Alice", result);
  }

  @Test
  void testPreviewWithAffinePath() {
    record Box(Optional<String> value) {}

    Lens<Box, Optional<String>> valueLens = Lens.of(Box::value, (box, v) -> new Box(v));
    AffinePath<Box, String> valuePath = FocusPath.of(valueLens).via(FocusPaths.optionalSome());

    Box fullBox = new Box(Optional.of("treasure"));
    Box emptyBox = new Box(Optional.empty());

    Free<OpticOpKind.Witness, Optional<String>> fullProgram =
        OpticPrograms.preview(fullBox, valuePath);
    Optional<String> fullResult = OpticInterpreters.direct().run(fullProgram);
    assertTrue(fullResult.isPresent());
    assertEquals("treasure", fullResult.get());

    Free<OpticOpKind.Witness, Optional<String>> emptyProgram =
        OpticPrograms.preview(emptyBox, valuePath);
    Optional<String> emptyResult = OpticInterpreters.direct().run(emptyProgram);
    assertFalse(emptyResult.isPresent());
  }

  @Test
  void testGetAllWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Person> membersPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList());

    Free<OpticOpKind.Witness, List<Person>> program = OpticPrograms.getAll(team, membersPath);
    List<Person> result = OpticInterpreters.direct().run(program);
    assertEquals(2, result.size());
    assertEquals("Alice", result.get(0).name());
    assertEquals("Bob", result.get(1).name());
  }

  @Test
  void testSetWithFocusPath() {
    Person person = new Person("Alice", 25);
    FocusPath<Person, Integer> agePath = FocusPath.of(AGE_LENS);

    Free<OpticOpKind.Witness, Person> program = OpticPrograms.set(person, agePath, 30);
    Person result = OpticInterpreters.direct().run(program);
    assertEquals("Alice", result.name());
    assertEquals(30, result.age());
  }

  @Test
  void testSetAllWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Integer> agesPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList()).via(AGE_LENS);

    Free<OpticOpKind.Witness, Team> program = OpticPrograms.setAll(team, agesPath, 21);
    Team result = OpticInterpreters.direct().run(program);
    assertEquals(21, result.members().get(0).age());
    assertEquals(21, result.members().get(1).age());
  }

  @Test
  void testModifyWithFocusPath() {
    Person person = new Person("Alice", 25);
    FocusPath<Person, String> namePath = FocusPath.of(NAME_LENS);

    Free<OpticOpKind.Witness, Person> program =
        OpticPrograms.modify(person, namePath, String::toUpperCase);
    Person result = OpticInterpreters.direct().run(program);
    assertEquals("ALICE", result.name());
    assertEquals(25, result.age());
  }

  @Test
  void testModifyAllWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Integer> agesPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList()).via(AGE_LENS);

    Free<OpticOpKind.Witness, Team> program =
        OpticPrograms.modifyAll(team, agesPath, age -> age + 5);
    Team result = OpticInterpreters.direct().run(program);
    assertEquals(30, result.members().get(0).age());
    assertEquals(35, result.members().get(1).age());
  }

  @Test
  void testExistsWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Integer> agesPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList()).via(AGE_LENS);

    Free<OpticOpKind.Witness, Boolean> hasAdultProgram =
        OpticPrograms.exists(team, agesPath, age -> age >= 30);
    assertTrue(OpticInterpreters.direct().run(hasAdultProgram));

    Free<OpticOpKind.Witness, Boolean> hasSeniorProgram =
        OpticPrograms.exists(team, agesPath, age -> age >= 65);
    assertFalse(OpticInterpreters.direct().run(hasSeniorProgram));
  }

  @Test
  void testAllWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Integer> agesPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList()).via(AGE_LENS);

    Free<OpticOpKind.Witness, Boolean> allAdultsProgram =
        OpticPrograms.all(team, agesPath, age -> age >= 18);
    assertTrue(OpticInterpreters.direct().run(allAdultsProgram));

    Free<OpticOpKind.Witness, Boolean> allSeniorsProgram =
        OpticPrograms.all(team, agesPath, age -> age >= 30);
    assertFalse(OpticInterpreters.direct().run(allSeniorsProgram));
  }

  @Test
  void testCountWithTraversalPath() {
    Team team = new Team("Wildcats", List.of(new Person("Alice", 25), new Person("Bob", 30)));
    TraversalPath<Team, Person> membersPath =
        FocusPath.of(TEAM_MEMBERS_LENS).via(Traversals.forList());

    Free<OpticOpKind.Witness, Integer> program = OpticPrograms.count(team, membersPath);
    Integer result = OpticInterpreters.direct().run(program);
    assertEquals(2, result);

    Team emptyTeam = new Team("Lonely", List.of());
    Free<OpticOpKind.Witness, Integer> emptyProgram = OpticPrograms.count(emptyTeam, membersPath);
    assertEquals(0, OpticInterpreters.direct().run(emptyProgram));
  }

  // ============================================================================
  // Private Constructor Tests
  // ============================================================================

  @Test
  void testOpticProgramsPrivateConstructor() throws Exception {
    Constructor<OpticPrograms> constructor = OpticPrograms.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    Exception exception =
        assertThrowsExactly(InvocationTargetException.class, constructor::newInstance);
    assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    assertEquals("Utility class", exception.getCause().getMessage());
  }

  @Test
  void testOpticInterpretersPrivateConstructor() throws Exception {
    Constructor<OpticInterpreters> constructor = OpticInterpreters.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    Exception exception =
        assertThrowsExactly(InvocationTargetException.class, constructor::newInstance);
    assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    assertEquals("Utility class", exception.getCause().getMessage());
  }

  @Test
  void testOpticOpKindWitnessPrivateConstructor() throws Exception {
    Constructor<OpticOpKind.Witness> constructor =
        OpticOpKind.Witness.class.getDeclaredConstructor();
    constructor.setAccessible(true);

    Exception exception = assertThrows(InvocationTargetException.class, constructor::newInstance);
    assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
    assertEquals("Witness class", exception.getCause().getMessage());
  }
}

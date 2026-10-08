# Free Monad DSL: Composable Optic Programs

_Build optic operations as a program value, then choose to run, log, check or test it._

![free_monad.jpg](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- Build a program with `OpticPrograms.get`, `set`, `modify` and `modifyAll`, and run it with `OpticInterpreters.direct()`
- Sequence steps with `flatMap`, branching on an earlier result and returning `pure` where a branch has nothing to do
- Run one program under the logging, validating or mock interpreter to audit, check or test it
- Write reusable program fragments and combine them, keeping side effects out of program construction
- Decide when a program is worth its extra layer over direct execution with `OpticOps`
~~~

~~~admonish example title="See Example Code"
[FreeMonadOpticDSLExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FreeMonadOpticDSLExample.java)
~~~

## Introduction: Beyond Immediate Execution

When you use optics directly, they execute immediately. You read a value, transform a field, update a structure; all happens right away. This direct execution is perfect for simple cases, but what if you need more?

Consider these real-world requirements:

- **Audit trails**: Record every data change for compliance
- **Validation**: Check all constraints before making any changes
- **Testing**: Verify your logic without touching real data
- **Structural analysis**: count what a program contains, with `ProgramAnalyser.analyse`, without running a step

This is where the Free Monad DSL comes in. It lets you **describe** a sequence of optic operations as data, then **interpret** that description in different ways.

~~~admonish tip title="Why this matters"
A Free monad program plays the part of a `Stream` pipeline before its terminal operation: building it runs nothing, it only describes what to do. Unlike a stream, the description is a value you can hand to different interpreters, to log, check or test it, and you can run it more than once. The Free monad DSL gives you that same power with optic operations.
~~~

---

## Part 1: Understanding Free Monads (Gently)

### What Is a Free Monad?

A Free monad is a way to build a **program as data**. Instead of executing operations immediately, you construct a data structure that describes what operations to perform. Later, you choose how to execute (interpret) that structure.

Think of it like this:

<!-- verify -->
```java
// Direct execution (happens immediately)
Person updated = PersonLenses.age().set(30, person);

// Free monad (just builds a description)
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.set(person, PersonLenses.age(), 30);
// Nothing happened yet! We just described what to do.

// Now we choose how to interpret it
Person result = OpticInterpreters.direct().run(program);
// NOW it executed
```

### Why Is This Useful?

By separating **description** from **execution**, you can:

1. **Review** the program before running it
2. **Check** every operation and collect the problems, instead of the result
3. **Log** every operation for audit trails
4. **Test** the logic with mock data
5. **Transform** the program (optimise, translate, etc.)

For optics specifically, this means you can build complex data transformation workflows and then choose how to execute them based on your needs.

---

~~~admonish note title="Reading the type"
`Free<OpticOpKind.Witness, Person>` is a program whose steps are optic operations and whose answer is a `Person`. The first parameter names the vocabulary of steps (`OpticOpKind.Witness` is the marker for "optic operations"); the second is what you get back when someone runs it. The builders live in `OpticPrograms`, and `OpticOps` is the same set of operations executed immediately, with no program in between. All of it is in `org.higherkindedj.optics.free`.
~~~

## Part 2: Building Your First Optic Program

### Simple Programs: Get, Set, Modify

Let's start with the basics, a person with a name, an age and a status:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:person}}
```

Each builder describes one operation on that person, and returns it as a program:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:build}}
```

At this point, **nothing has executed**. We've just built descriptions of operations. To actually run them:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:run}}
```

---

### Composing Programs: The Power of `flatMap`

The real power emerges when you compose multiple operations. The `flatMap` method lets you sequence operations where each step can depend on previous results:

<!-- verify -->
```java
// Program: Get the age, then if they're an adult, increment it
Free<OpticOpKind.Witness, Person> adultBirthdayProgram =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age -> {
            if (age >= 18) {
                return OpticPrograms.modify(
                    person,
                    PersonLenses.age(),
                    a -> a + 1
                );
            } else {
                // Return unchanged person
                return OpticPrograms.pure(person);
            }
        });

// Execute it
Person result = OpticInterpreters.direct().run(adultBirthdayProgram);
```

Let's break down what's happening:

1. `get` creates a program that will retrieve the age
2. `flatMap` says "once you have the age, use it to decide what to do next"
3. Inside `flatMap`, we make a decision based on the age value
4. We return a new program (either `modify` or `pure`)
5. The interpreter executes the composed program step by step

---

### Multi-Step programs: Complex Workflows

You can chain multiple `flatMap` calls to build sophisticated workflows. An employee has a salary and a status:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:employee}}
```

The review program raises the salary by 10%, then reads it back to decide on a promotion:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:annual_review_program}}
```

Running it for an employee on 95,000 gives the raise and the promotion:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:annual_review_run}}
```

---

## Part 3: Working with Collections (Traversals and Folds)

The DSL supports batch operations through traversals. A team holds a list of players, each with a score:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:team_records}}
```

One program doubles every score, then asks whether every player now passes:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:team}}
```

### Querying with programs

<!-- verify -->
```java
// Program: Find all high scorers
Free<OpticOpKind.Witness, List<Player>> findHighScorers =
    OpticPrograms.getAll(team, TeamTraversals.players())
        .flatMap(players -> {
            List<Player> highScorers = players.stream()
                .filter(p -> p.score() > 85)
                .toList();
            return OpticPrograms.pure(highScorers);
        });

// Execute
List<Player> topPlayers = OpticInterpreters.direct().run(findHighScorers);
```

---

## Part 4: Real-World Scenarios

### Scenario 1: Data Migration with Validation

<!-- verify -->
```java
@GenerateLenses
public record UserV1(String username, String email) {}

@GenerateLenses
public record UserV2(String username, String email, boolean verified) {}

// Note: Either is from higher-kinded-j (org.higherkindedj.hkt.either.Either)
// It represents a value that can be either a Left (error) or Right (success)

// Program: Migrate user with email validation
Free<OpticOpKind.Witness, Either<String, UserV2>> migrateUser(UserV1 oldUser) {
    return OpticPrograms.get(oldUser, UserV1Lenses.email())
        .flatMap(email -> {
            if (email.contains("@") && email.contains(".")) {
                // Valid email - proceed with migration
                UserV2 newUser = new UserV2(
                    oldUser.username(),
                    email,
                    false  // Will be verified later
                );
                return OpticPrograms.pure(Either.right(newUser));
            } else {
                // Invalid email - fail migration
                return OpticPrograms.pure(Either.left(
                    "Invalid email: " + email
                ));
            }
        });
}

// Execute migration
Free<OpticOpKind.Witness, Either<String, UserV2>> program =
    migrateUser(new UserV1("alice", "alice@example.com"));

Either<String, UserV2> result = OpticInterpreters.direct().run(program);
```

~~~admonish tip title="Why Use Free Monad Here?"
By building the migration as a program, you can:
- Validate the entire migration plan before executing
- Log every transformation for audit purposes
- Test the migration logic without touching real data
- Recover from a failing step with `handleError`, when the target monad is a `MonadError` (against `Id` the handler is ignored)
~~~

---

### Scenario 2: Audit Trail for Financial Transactions

A transaction moves an amount from one account to another:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:transfer_records}}
```

The transfer program reads the amount, then debits the source and credits the destination:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:transfer_program}}
```

The logging interpreter runs it and keeps one line per operation:

``` java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/book/optics/free/FreeMonadBook.java:transfer_run}}
```

---

### Scenario 3: Checked Run Before Committing the Result

<!-- verify -->
```java
@GenerateLenses
@GenerateTraversals
public record ProductCatalogue(List<Product> products) {}

@GenerateLenses
public record Product(String id, BigDecimal price, int stock) {}

// Program: Bulk price update
Free<OpticOpKind.Witness, ProductCatalogue> bulkPriceUpdate(
    ProductCatalogue catalogue,
    BigDecimal markup
) {
    return OpticPrograms.modifyAll(
        catalogue,
        ProductCatalogueTraversals.products()
            .andThen(ProductLenses.price()),
        price -> price.multiply(BigDecimal.ONE.add(markup))
    );
}

// First, run it under the validating interpreter and read the report
ProductCatalogue catalogue = new ProductCatalogue(
    List.of(
        new Product("P001", new BigDecimal("99.99"), 10),
        new Product("P002", new BigDecimal("49.99"), 5)
    )
);

Free<OpticOpKind.Witness, ProductCatalogue> program =
    bulkPriceUpdate(catalogue, new BigDecimal("0.10"));  // 10% markup

// Validate first
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationOpticInterpreter.ValidationResult validation =
    validator.validate(program);

if (validation.isValid()) {
    // All good - now execute for real
    ProductCatalogue updated = OpticInterpreters.direct().run(program);
    System.out.println("Price update successful!");
} else {
    // Something wrong - review errors
    validation.errors().forEach(System.err::println);
    validation.warnings().forEach(System.out::println);
}
```

---

## Part 5: Advanced Patterns

### Pattern 1: Reusable Program Fragments

You can build libraries of reusable program fragments:

<!-- verify -->
```java
// Library of common operations
public class PersonPrograms {
    public static Free<OpticOpKind.Witness, Person> celebrateBirthday(
        Person person
    ) {
        return OpticPrograms.modify(
            person,
            PersonLenses.age(),
            age -> age + 1
        );
    }

    public static Free<OpticOpKind.Witness, Person> promoteIfEligible(
        Person person
    ) {
        return OpticPrograms.get(person, PersonLenses.age())
            .flatMap(age -> {
                if (age >= 30) {
                    return OpticPrograms.set(
                        person,
                        PersonLenses.status(),
                        "SENIOR"
                    );
                } else {
                    return OpticPrograms.pure(person);
                }
            });
    }

    // Combine operations
    public static Free<OpticOpKind.Witness, Person> annualUpdate(
        Person person
    ) {
        return celebrateBirthday(person)
            .flatMap(PersonPrograms::promoteIfEligible);
    }
}

// Use them
Person alice = new Person("Alice", 29, "JUNIOR");
Free<OpticOpKind.Witness, Person> program = PersonPrograms.annualUpdate(alice);
Person updated = OpticInterpreters.direct().run(program);
```

---

### Pattern 2: Conditional Branching

<!-- verify -->
```java
enum PerformanceRating { EXCELLENT, GOOD, SATISFACTORY, POOR }

// Program with complex branching logic
Free<OpticOpKind.Witness, Employee> processPerformanceReview(
    Employee employee,
    PerformanceRating rating
) {
    return switch (rating) {
        case EXCELLENT -> OpticPrograms.modify(
            employee,
            EmployeeLenses.salary(),
            salary -> salary + (salary / 5)  // 20% raise
        ).flatMap(emp ->
            OpticPrograms.set(emp, EmployeeLenses.status(), EmployeeStatus.SENIOR)
        );

        case GOOD -> OpticPrograms.modify(
            employee,
            EmployeeLenses.salary(),
            salary -> salary + (salary / 10)  // 10% raise
        );

        case SATISFACTORY -> OpticPrograms.pure(employee);  // No change

        case POOR -> OpticPrograms.set(
            employee,
            EmployeeLenses.status(),
            EmployeeStatus.PROBATION
        );
    };
}
```

---

### Pattern 3: Accumulating Results

<!-- verify -->
```java
// Note: Tuple and Tuple2 are from higher-kinded-j (org.higherkindedj.hkt.tuple.Tuple, Tuple2)
// Tuple.of() creates a Tuple2 instance to pair two values together

// Program that accumulates statistics while processing
record ProcessingStats(int processed, int modified, int skipped) {}

Free<OpticOpKind.Witness, Tuple2<Team, ProcessingStats>> processTeamWithStats(
    Team team
) {
    // The stats are derived after the fact here; threading them through the
// flatMaps would make the program carry them itself
    return OpticPrograms.getAll(team, TeamTraversals.players())
        .flatMap(players -> {
            int processed = players.size();
            int modified = (int) players.stream()
                .filter(p -> p.score() < 50)
                .count();
            int skipped = processed - modified;

            return OpticPrograms.modifyAll(
                team,
                TeamTraversals.players(),
                player -> player.score() < 50
                    ? OpticOps.set(player, PlayerLenses.score(), 50)
                    : player
            ).map(updatedTeam ->
                Tuple.of(
                    updatedTeam,
                    new ProcessingStats(processed, modified, skipped)
                )
            );
        });
}
```

---

## Part 6: Comparison with Direct Execution

### When to Use Free Monad DSL

**Use Free Monad DSL when you need:**

- Audit trails and logging
- Validation before execution
- Testing complex logic
- Multiple execution strategies
- Structural analysis before anything runs

### When to Use Direct Execution

**Use Direct Execution ([Fluent API](fluent_api.md)) when:**

- Simple, straightforward operations
- No need for introspection
- Performance is critical
- The workflow is stable and well-understood

---

### Side-by-Side Comparison

<!-- verify -->
```java
// Direct execution (immediate)
Person result = OpticOps.modify(
    person,
    PersonLenses.age(),
    age -> age + 1
);

// Free monad (deferred)
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.modify(
        person,
        PersonLenses.age(),
        age -> age + 1
    );

Person interpreted = OpticInterpreters.direct().run(program);
```

The Free monad version requires more code, but gives you the power to:

<!-- verify -->
```java
// Log it
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Person result = logger.run(program);
logger.getLog().forEach(System.out::println);

// Validate it
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationOpticInterpreter.ValidationResult validation = validator.validate(program);

// Test it with mocks. A stub per operation, because `modify` yields the source type
// while a `get` would yield the focus type; see Interpreters for the class itself.
Person mockPerson = new Person("Mock", 99, "ACTIVE");
MockOpticInterpreter mock = new MockOpticInterpreter(op -> switch (op) {
    case OpticOp.Modify<?, ?> ignored -> mockPerson;   // this program is a single modify
    default -> throw new UnsupportedOperationException(op.getClass().getSimpleName());
});
Person mockResult = mock.run(program);
```

---

## Common Pitfalls

### Don't: Forget that programs are immutable

<!-- verify -->
```java
// Wrong: flatMap returns a NEW program, and this line throws it away
Free<OpticOpKind.Witness, Integer> program = OpticPrograms.get(person, PersonLenses.age());
program.flatMap(age -> OpticPrograms.set(person, PersonLenses.age(), age + 1));

// The original program is unchanged: it still only reads the age
```

### Do: Assign the result of `flatMap`

<!-- verify -->
```java
// Correct - capture the new program
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age -> OpticPrograms.modify(person, PersonLenses.age(), a -> a + 1));
```

---

### Don't: Mix side effects in program construction

<!-- verify -->
```java
// Wrong - side effect during construction
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age -> {
            System.out.println("Age: " + age);  // Side effect!
            return OpticPrograms.pure(person);
        });
```

### Do: Keep program construction pure

<!-- verify -->
```java
// Correct - side effects only in interpreters
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age -> OpticPrograms.pure(person));

// Side effects happen during interpretation
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Person result = logger.run(program);
logger.getLog().forEach(System.out::println);  // Side effect here is fine
```

---

~~~admonish info title="Hands-On Learning"
Practise the Free Monad DSL in [Tutorial 11: Advanced Optics DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial11_AdvancedOpticsDSL.java) (7 exercises).
~~~

~~~admonish info title="Key Takeaways"
* **A program is a value, not an action.** `OpticPrograms.get`/`set`/`modify` build a `Free<OpticOpKind.Witness, A>` and run nothing; execution is a separate, later decision.
* **`flatMap` is where the sequencing lives.** Each step sees the previous step's result, so a multi-stage workflow reads top to bottom while still being pure data.
* **The same description supports several answers.** Run it, log it, run it for a report instead of a result, or hand it to an interpreter of your own; the program does not change.
* **The cost is a layer of indirection.** The same edit takes a program value, an interpreter and a `run` call where direct execution took one line, so reach for this when you need inspection or several execution modes, not for a single update.
* **`pure` lifts a plain value in.** It is how a branch that has nothing to do still returns something the rest of the chain can `flatMap` over.
~~~

~~~admonish tip title="See Also"
- [Interpreters](interpreters.md): the execution strategies these programs are handed to
- [Updates That Can Fail](fluent_api.md): the direct-execution counterpart through `OpticOps`, when a description buys you nothing
- [Optic Composition Rules](composition_rules.md): what each composed optic these programs navigate with can do
~~~

~~~admonish tip title="Further Reading"
- **Gabriel Gonzalez**: [Why Free Monads Matter](https://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html): the foundational explanation
- **Scott Wlaschin**: [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/): error-handling patterns
~~~

---

**Previous:** [Programs as Data](ch6_intro.md)
**Next:** [Interpreters](interpreters.md)

# Free Monad DSL: Composable Optic Programs

![free_monad.jpg](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- What Free monads are and why they're powerful for optics
- How to build composable optic programs step by step
- Separating program description from execution
- Using conditional logic and branching in programs
- Real-world scenarios: audit trails, validation, and testing
- Creating reusable program fragments
~~~

~~~admonish title="Hands On Practice"
[Tutorial11_AdvancedOpticsDSL.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial11_AdvancedOpticsDSL.java)
~~~

~~~admonish title="Example Code"
[FreeMonadOpticDSLExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FreeMonadOpticDSLExample.java)
~~~

## Introduction: Beyond Immediate Execution

When you use optics directly, they execute immediately. You read a value, transform a field, update a structure; all happens right away. This direct execution is perfect for simple cases, but what if you need more?

Consider these real-world requirements:

- **Audit trails**: Record every data change for compliance
- **Validation**: Check all constraints before making any changes
- **Testing**: Verify your logic without touching real data
- **Optimisation**: Analyse and fuse multiple operations for efficiency
- **Dry-runs**: See what would change without actually changing it

This is where the Free monad DSL comes in. It lets you **describe** a sequence of optic operations as data, then **interpret** that description in different ways.

~~~admonish tip title="The Core Insight"
A Free monad program is like a recipe. Writing the recipe doesn't cook the meal; it just describes what to do. You can review the recipe, validate it, translate it, or follow it to cook. The Free monad DSL gives you that same power with optic operations.
~~~

---

## Part 1: Understanding Free Monads (Gently)

### What Is a Free Monad?

A Free monad is a way to build a **program as data**. Instead of executing operations immediately, you construct a data structure that describes what operations to perform. Later, you choose how to execute (interpret) that structure.

Think of it like this:

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
2. **Validate** all operations without executing them
3. **Log** every operation for audit trails
4. **Test** the logic with mock data
5. **Transform** the program (optimise, translate, etc.)

For optics specifically, this means you can build complex data transformation workflows and then choose how to execute them based on your needs.

---

## Part 2: Building Your First Optic Program

### Simple Programs: Get, Set, Modify

Let's start with the basics:

```java
@GenerateLenses
public record Person(String name, int age, String status) {}

Person person = new Person("Alice", 25, "ACTIVE");

// Build a program that gets the age
Free<OpticOpKind.Witness, Integer> getProgram =
    OpticPrograms.get(person, PersonLenses.age());

// Build a program that sets the age
Free<OpticOpKind.Witness, Person> setProgram =
    OpticPrograms.set(person, PersonLenses.age(), 30);

// Build a program that modifies the age
Free<OpticOpKind.Witness, Person> modifyProgram =
    OpticPrograms.modify(person, PersonLenses.age(), age -> age + 1);
```

At this point, **nothing has executed**. We've just built descriptions of operations. To actually run them:

```java
// Execute with direct interpreter
DirectOpticInterpreter interpreter = OpticInterpreters.direct();

Integer age = interpreter.run(getProgram);           // 25
Person updated = interpreter.run(setProgram);         // age is now 30
Person modified = interpreter.run(modifyProgram);     // age is now 26
```

---

### Composing Programs: The Power of `flatMap`

The real power emerges when you compose multiple operations. The `flatMap` method lets you sequence operations where each step can depend on previous results:

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

You can chain multiple `flatMap` calls to build sophisticated workflows:

```java
@GenerateLenses
public record Employee(String name, int salary, EmployeeStatus status) {}

enum EmployeeStatus { JUNIOR, SENIOR, RETIRED }

// Program: Annual review and potential promotion
Free<OpticOpKind.Witness, Employee> annualReviewProgram(Employee employee) {
    return OpticPrograms.get(employee, EmployeeLenses.salary())
        .flatMap(currentSalary -> {
            // Step 1: Give a 10% raise
            int newSalary = currentSalary + (currentSalary / 10);
            return OpticPrograms.set(employee, EmployeeLenses.salary(), newSalary);
        })
        .flatMap(raisedEmployee ->
            // Step 2: Check if salary justifies promotion
            OpticPrograms.get(raisedEmployee, EmployeeLenses.salary())
                .flatMap(salary -> {
                    if (salary > 100_000) {
                        return OpticPrograms.set(
                            raisedEmployee,
                            EmployeeLenses.status(),
                            EmployeeStatus.SENIOR
                        );
                    } else {
                        return OpticPrograms.pure(raisedEmployee);
                    }
                })
        );
}

// Execute for an employee
Employee alice = new Employee("Alice", 95_000, EmployeeStatus.JUNIOR);
Free<OpticOpKind.Witness, Employee> program = annualReviewProgram(alice);

Employee promoted = OpticInterpreters.direct().run(program);
// Result: Employee("Alice", 104_500, SENIOR)
```

---

## Part 3: Working with Collections (Traversals and Folds)

The DSL works beautifully with traversals for batch operations:

```java
@GenerateLenses
@GenerateTraversals
public record Team(String name, List<Player> players) {}

@GenerateLenses
public record Player(String name, int score) {}

Team team = new Team("Wildcats",
    List.of(
        new Player("Alice", 80),
        new Player("Bob", 90)
    ));

// Program: Double all scores and check if everyone passes
Free<OpticOpKind.Witness, Boolean> scoreUpdateProgram =
    OpticPrograms.modifyAll(
        team,
        TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
        score -> score * 2
    )
    .flatMap(updatedTeam ->
        // Now check if all players have passing scores
        OpticPrograms.all(
            updatedTeam,
            TeamTraversals.players().andThen(PlayerLenses.score().asTraversal()),
            score -> score >= 100
        )
    );

// Execute
Boolean allPass = OpticInterpreters.direct().run(scoreUpdateProgram);
// Result: true (Alice: 160, Bob: 180)
```

### Querying with programs

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
- Roll back if any step fails
~~~

---

### Scenario 2: Audit Trail for Financial Transactions

```java
@GenerateLenses
public record Account(String accountId, BigDecimal balance) {}

@GenerateLenses
public record Transaction(Account from, Account to, BigDecimal amount) {}

// Program: Transfer money between accounts
Free<OpticOpKind.Witness, Transaction> transferProgram(
    Transaction transaction
) {
    return OpticPrograms.get(transaction, TransactionLenses.amount())
        .flatMap(amount ->
            // Deduct from source account
            OpticPrograms.modify(
                transaction,
                TransactionLenses.from().andThen(AccountLenses.balance()),
                balance -> balance.subtract(amount)
            )
        )
        .flatMap(txn ->
            // Add to destination account
            OpticPrograms.modify(
                txn,
                TransactionLenses.to().andThen(AccountLenses.balance()),
                balance -> balance.add(txn.amount())
            )
        );
}

// Execute with logging for audit trail
Account acc1 = new Account("ACC001", new BigDecimal("1000.00"));
Account acc2 = new Account("ACC002", new BigDecimal("500.00"));
Transaction txn = new Transaction(acc1, acc2, new BigDecimal("100.00"));

Free<OpticOpKind.Witness, Transaction> program = transferProgram(txn);

// Use logging interpreter to record every operation
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Transaction result = logger.run(program);

// Review audit trail
logger.getLog().forEach(System.out::println);
/* Output:
GET: TransactionLenses.amount() -> 100.00
MODIFY: TransactionLenses.from().andThen(AccountLenses.balance()) from 1000.00 to 900.00
MODIFY: TransactionLenses.to().andThen(AccountLenses.balance()) from 500.00 to 600.00
*/
```

---

### Scenario 3: Dry-Run Validation Before Production

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
            .andThen(ProductLenses.price().asTraversal()),
        price -> price.multiply(BigDecimal.ONE.add(markup))
    );
}

// First, validate without executing
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

```java
// Note: Tuple and Tuple2 are from higher-kinded-j (org.higherkindedj.hkt.tuple.Tuple, Tuple2)
// Tuple.of() creates a Tuple2 instance to pair two values together

// Program that accumulates statistics while processing
record ProcessingStats(int processed, int modified, int skipped) {}

Free<OpticOpKind.Witness, Tuple2<Team, ProcessingStats>> processTeamWithStats(
    Team team
) {
    // This is simplified - in practice you'd thread stats through flatMaps
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
- Optimisation opportunities
- Dry-run capabilities

### When to Use Direct Execution

**Use Direct Execution ([Fluent API](fluent_api.md)) when:**

- Simple, straightforward operations
- No need for introspection
- Performance is critical
- The workflow is stable and well-understood

---

### Side-by-Side Comparison

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

Person result = OpticInterpreters.direct().run(program);
```

The Free monad version requires more code, but gives you the power to:

```java
// Log it
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Person result = logger.run(program);
logger.getLog().forEach(System.out::println);

// Validate it
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationResult validation = validator.validate(program);

// Test it with mocks
MockOpticInterpreter mock = new MockOpticInterpreter();
Person mockResult = mock.run(program);
```

---

## Common Pitfalls

### Don't: Forget that programs are immutable

```java
// Wrong - trying to "modify" a program
Free<OpticOpKind.Witness, Person> program = OpticPrograms.get(person, PersonLenses.age());
program.flatMap(age -> ...);  // This returns a NEW program!

// The original program is unchanged
```

### Do: Assign the result of `flatMap`

```java
// Correct - capture the new program
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age -> OpticPrograms.modify(person, PersonLenses.age(), a -> a + 1));
```

---

### Don't: Mix side effects in program construction

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

~~~admonish tip title="Further Reading"
- **Gabriel Gonzalez**: [Why Free Monads Matter](https://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html) - The foundational explanation
- **Scott Wlaschin**: [Railway Oriented Programming](https://fsharpforfunandprofit.com/rop/) - Error handling patterns
~~~

~~~admonish info title="Hands-On Learning"
Practice the Free Monad DSL in [Tutorial 11: Advanced Optics DSL](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial11_AdvancedOpticsDSL.java) (7 exercises, ~12 minutes).
~~~

---

**Next Steps:**

- [Optic Interpreters](interpreters.md) - Deep dive into execution strategies
- [Fluent API for Optics](fluent_api.md) - Direct execution patterns
- [Advanced Patterns](composing_optics.md) - Complex real-world scenarios

---

**Previous:** [Fluent API](fluent_api.md)
**Next:** [Interpreters](interpreters.md)

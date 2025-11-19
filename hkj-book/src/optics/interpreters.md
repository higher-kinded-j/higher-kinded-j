# Optic Interpreters: Multiple Execution Strategies

![interpreters.jpg](../images/lens2.jpg)

~~~admonish info title="What You'll Learn"
- How the Interpreter pattern separates description from execution
- The three built-in interpreters: Direct, Logging, and Validation
- When to use each interpreter effectively
- How to create custom interpreters for specific needs
- Combining interpreters for powerful workflows
- Real-world applications: audit trails, testing, and optimisation
~~~

~~~admonish title="Example Code"
[OpticInterpretersExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/free/OpticInterpretersExample.java)
~~~

## Introduction: The Power of Interpretation

In the [Free Monad DSL](free_monad_dsl.md) guide, we learnt how to build optic operations as programmes—data structures that describe what to do, rather than doing it immediately. But a description alone is useless without execution. That's where **interpreters** come in.

An interpreter takes a programme and executes it in a specific way. By providing different interpreters, you can run the same programme with completely different behaviours:

- **DirectOpticInterpreter**: Executes operations immediately (production use)
- **LoggingOpticInterpreter**: Records every operation for audit trails
- **ValidationOpticInterpreter**: Checks constraints without modifying data
- **Custom interpreters**: Performance profiling, testing, mocking, and more

This separation of concerns—*what to do* vs *how to do it*—is the essence of the Interpreter pattern and the key to the Free monad's flexibility.

~~~admonish tip title="The Core Benefit"
Write your business logic once as a programme. Execute it in multiple ways: validate it in tests, log it in production, mock it during development, and optimise it for performance—all without changing the business logic itself.
~~~

---

## Part 1: The Interpreter Pattern Explained

### From Design Patterns to Functional Programming

The Interpreter pattern, described in the Gang of Four's *Design Patterns*, suggests representing operations as objects in an abstract syntax tree (AST), then traversing that tree to execute them. The Free monad is essentially a functional programming implementation of this pattern.

```java
// Our "AST" - a programme built from operations
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, PersonLenses.age())
        .flatMap(age ->
            OpticPrograms.modify(person, PersonLenses.age(), a -> a + 1)
        );

// Our "interpreter" - executes the AST
DirectOpticInterpreter interpreter = OpticInterpreters.direct();
Person result = interpreter.run(program);
```

### Why Multiple Interpreters?

Different situations require different execution strategies:

| **Situation** | **Interpreter** | **Why** |
|--------------|----------------|---------|
| Production execution | Direct | Fast, straightforward |
| Compliance & auditing | Logging | Records every change |
| Pre-flight checks | Validation | Verifies without executing |
| Unit testing | Mock/Custom | No real data needed |
| Performance tuning | Profiling/Custom | Measures execution time |
| Dry-run simulations | Validation | See what would happen |

---

## Part 2: The Direct Interpreter

The `DirectOpticInterpreter` is the simplest interpreter—it executes optic operations immediately, exactly as you'd expect.

### Basic Usage

```java
@GenerateLenses
public record Person(String name, int age) {}

Person person = new Person("Alice", 25);

// Build a programme
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.modify(person, PersonLenses.age(), age -> age + 1);

// Execute with direct interpreter
DirectOpticInterpreter interpreter = OpticInterpreters.direct();
Person result = interpreter.run(program);

System.out.println(result);  // Person("Alice", 26)
```

### When to Use

✅ **Production execution**: When you just want to run the operations
✅ **Simple workflows**: When audit trails or validation aren't needed
✅ **Performance-critical paths**: Minimal overhead

### Characteristics

- **Fast**: No additional processing
- **Simple**: Executes exactly as described
- **No Side Effects**: Pure optic operations only

~~~admonish example title="Production Workflow"
```java
@GenerateLenses
record Employee(String name, int salary, String status) {}

enum PerformanceRating { EXCELLENT, GOOD, SATISFACTORY, POOR }

// Employee management system
public Employee processAnnualReview(
    Employee employee,
    PerformanceRating rating
) {
    Free<OpticOpKind.Witness, Employee> program =
        buildReviewProgram(employee, rating);

    // Direct execution in production
    return OpticInterpreters.direct().run(program);
}
```
~~~

---

## Part 3: The Logging Interpreter

The `LoggingOpticInterpreter` executes operations whilst recording detailed logs of every operation performed. This is invaluable for:

- **Audit trails**: Compliance requirements (GDPR, SOX, etc.)
- **Debugging**: Understanding what happened when
- **Monitoring**: Tracking data changes in production

### Basic Usage

```java
@GenerateLenses
public record Account(String accountId, BigDecimal balance) {}

Account account = new Account("ACC001", new BigDecimal("1000.00"));

// Build a programme
Free<OpticOpKind.Witness, Account> program =
    OpticPrograms.modify(
        account,
        AccountLenses.balance(),
        balance -> balance.subtract(new BigDecimal("100.00"))
    );

// Execute with logging
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Account result = logger.run(program);

// Review the log
List<String> log = logger.getLog();
log.forEach(System.out::println);
/* Output:
MODIFY: AccountLenses.balance() from 1000.00 to 900.00
*/
```

### Comprehensive Example: Financial Transaction Audit

```java
@GenerateLenses
public record Transaction(
    String txnId,
    Account from,
    Account to,
    BigDecimal amount,
    LocalDateTime timestamp
) {}

// Build a transfer programme
Free<OpticOpKind.Witness, Transaction> transferProgram(Transaction txn) {
    return OpticPrograms.get(txn, TransactionLenses.amount())
        .flatMap(amount ->
            // Debit source account
            OpticPrograms.modify(
                txn,
                TransactionLenses.from().andThen(AccountLenses.balance()),
                balance -> balance.subtract(amount)
            )
        )
        .flatMap(debited ->
            // Credit destination account
            OpticPrograms.modify(
                debited,
                TransactionLenses.to().andThen(AccountLenses.balance()),
                balance -> balance.add(debited.amount())
            )
        );
}

// Execute with audit logging
Transaction txn = new Transaction(
    "TXN-12345",
    new Account("ACC001", new BigDecimal("1000.00")),
    new Account("ACC002", new BigDecimal("500.00")),
    new BigDecimal("250.00"),
    LocalDateTime.now()
);

LoggingOpticInterpreter logger = OpticInterpreters.logging();
Transaction result = logger.run(transferProgram(txn));

// Persist audit trail to database
logger.getLog().forEach(entry -> auditService.record(txn.txnId(), entry));
```

### Log Format

The logging interpreter provides detailed, human-readable logs:

```
GET: TransactionLenses.amount() -> 250.00
MODIFY: TransactionLenses.from().andThen(AccountLenses.balance()) from 1000.00 to 750.00
MODIFY: TransactionLenses.to().andThen(AccountLenses.balance()) from 500.00 to 750.00
```

### Managing Logs

```java
LoggingOpticInterpreter logger = OpticInterpreters.logging();

// Run first programme
logger.run(program1);
List<String> firstLog = logger.getLog();

// Clear for next programme
logger.clearLog();

// Run second programme
logger.run(program2);
List<String> secondLog = logger.getLog();
```

~~~admonish warning title="Performance Consideration"
The logging interpreter does add overhead (string formatting, list management). For high-frequency operations, consider:
- Using sampling (log every Nth transaction)
- Async logging (log to queue, process later)
- Conditional logging (only for high-value transactions)
~~~

---

## Part 4: The Validation Interpreter

The `ValidationOpticInterpreter` performs a "dry-run" of your programme, checking constraints and collecting errors/warnings **without actually executing the operations**. This is perfect for:

- **Pre-flight checks**: Validate before committing
- **Testing**: Verify logic without side effects
- **What-if scenarios**: See what would happen

### Basic Usage

```java
@GenerateLenses
public record Person(String name, int age) {}

Person person = new Person("Alice", 25);

// Build a programme
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.set(person, PersonLenses.name(), null);  // Oops!

// Validate without executing
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationOpticInterpreter.ValidationResult result = validator.validate(program);

if (!result.isValid()) {
    // Has errors
    result.errors().forEach(System.err::println);
}

if (result.hasWarnings()) {
    // Has warnings
    result.warnings().forEach(System.out::println);
    // Output: "SET operation with null value: PersonLenses.name()"
}
```

### Validation Rules

The validation interpreter checks for:

1. **Null values**: Warns when setting null
2. **Modifier failures**: Errors when modifiers throw exceptions
3. **Custom constraints**: (via custom interpreter subclass)

### Real-World Example: Data Migration Validation

```java
@GenerateLenses
public record UserV1(String username, String email, Integer age) {}

@GenerateLenses
public record UserV2(
    String username,
    String email,
    int age,  // Now non-null!
    boolean verified
) {}

// Migration programme
Free<OpticOpKind.Witness, UserV2> migrateUser(UserV1 oldUser) {
    return OpticPrograms.get(oldUser, UserV1Lenses.age())
        .flatMap(age -> {
            if (age == null) {
                // This would fail!
                throw new IllegalArgumentException("Age cannot be null in V2");
            }

            UserV2 newUser = new UserV2(
                oldUser.username(),
                oldUser.email(),
                age,
                false
            );

            return OpticPrograms.pure(newUser);
        });
}

// Validate migration for each user
List<UserV1> oldUsers = loadOldUsers();
List<ValidationResult> validations = new ArrayList<>();

for (UserV1 user : oldUsers) {
    Free<OpticOpKind.Witness, UserV2> program = migrateUser(user);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationResult validation = validator.validate(program);

    validations.add(validation);

    if (!validation.isValid()) {
        System.err.println("User " + user.username() + " failed validation:");
        validation.errors().forEach(System.err::println);
    }
}

// Only proceed if all valid
if (validations.stream().allMatch(ValidationResult::isValid)) {
    // Execute migrations with direct interpreter
    oldUsers.forEach(user -> {
        Free<OpticOpKind.Witness, UserV2> program = migrateUser(user);
        UserV2 migrated = OpticInterpreters.direct().run(program);
        saveNewUser(migrated);
    });
}
```

### Validation Result API

```java
// Simple exception for validation failures
class ValidationException extends RuntimeException {
    public ValidationException(String message) {
        super(message);
    }
    public ValidationException(List<String> errors) {
        super("Validation failed: " + String.join(", ", errors));
    }
}

// Simple exception for business logic failures
class BusinessException extends RuntimeException {
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

public record ValidationResult(
    List<String> errors,    // Blocking issues
    List<String> warnings   // Non-blocking concerns
) {
    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
}
```

~~~admonish tip title="Testing Tip"
Use the validation interpreter in unit tests to verify programme structure without executing operations:

```java
@Test
void testProgrammeLogic() {
    Free<OpticOpKind.Witness, Person> program =
        buildComplexProgram(testData);

    ValidationOpticInterpreter validator = OpticInterpreters.validating();
    ValidationResult result = validator.validate(program);

    // Verify no errors in logic
    assertTrue(result.isValid());
}
```
~~~

---

## Part 5: Creating Custom Interpreters

You can create custom interpreters for specific needs: performance profiling, mocking, optimisation, or any other execution strategy.

### The Interpreter Interface

All interpreters implement a natural transformation from `OpticOp` to some effect type (usually `Id` for simplicity):

```java
public interface OpticInterpreter {
    <A> A run(Free<OpticOpKind.Witness, A> program);
}
```

### Example 1: Performance Profiling Interpreter

```java
public final class ProfilingOpticInterpreter {
    private final Map<String, Long> executionTimes = new HashMap<>();
    private final Map<String, Integer> executionCounts = new HashMap<>();

    public <A> A run(Free<OpticOpKind.Witness, A> program) {
        Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
            kind -> {
                OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow(
                    (Kind<OpticOpKind.Witness, Object>) kind
                );

                String opName = getOperationName(op);
                long startTime = System.nanoTime();

                // Execute the operation
                Object result = executeOperation(op);

                long endTime = System.nanoTime();
                long duration = endTime - startTime;

                // Record metrics
                executionTimes.merge(opName, duration, Long::sum);
                executionCounts.merge(opName, 1, Integer::sum);

                return Id.of(result);
            };

        Kind<IdKind.Witness, A> resultKind =
            program.foldMap(transform, IdMonad.instance());
        return IdKindHelper.ID.narrow(resultKind).value();
    }

    public Map<String, Long> getAverageExecutionTimes() {
        Map<String, Long> averages = new HashMap<>();
        executionTimes.forEach((op, totalTime) -> {
            int count = executionCounts.get(op);
            averages.put(op, totalTime / count);
        });
        return averages;
    }

    private String getOperationName(OpticOp<?, ?> op) {
        return switch (op) {
            case OpticOp.Get<?, ?> get -> "GET: " + get.optic().getClass().getSimpleName();
            case OpticOp.Set<?, ?> set -> "SET: " + set.optic().getClass().getSimpleName();
            case OpticOp.Modify<?, ?> mod -> "MODIFY: " + mod.optic().getClass().getSimpleName();
            // ... other cases
            default -> "UNKNOWN";
        };
    }

    private Object executeOperation(OpticOp<?, ?> op) {
        // Execute using direct interpretation logic
        return switch (op) {
            case OpticOp.Get<?, ?> get -> get.optic().get(get.source());
            case OpticOp.Set<?, ?> set -> set.optic().set(set.newValue(), set.source());
            case OpticOp.Modify<?, ?> mod -> {
                var current = mod.optic().get(mod.source());
                var updated = mod.modifier().apply(current);
                yield mod.optic().set(updated, mod.source());
            }
            // ... other cases
        };
    }
}
```

**Usage:**

```java
Free<OpticOpKind.Witness, Team> program = buildComplexTeamUpdate(team);

ProfilingOpticInterpreter profiler = new ProfilingOpticInterpreter();
Team result = profiler.run(program);

// Analyse performance
Map<String, Long> avgTimes = profiler.getAverageExecutionTimes();
avgTimes.forEach((op, time) ->
    System.out.println(op + ": " + time + "ns average")
);
```

---

### Example 2: Mock Interpreter for Testing

```java
public final class MockOpticInterpreter<S> {
    private final S mockData;

    public MockOpticInterpreter(S mockData) {
        this.mockData = mockData;
    }

    @SuppressWarnings("unchecked")
    public <A> A run(Free<OpticOpKind.Witness, A> program) {
        Function<Kind<OpticOpKind.Witness, ?>, Kind<IdKind.Witness, ?>> transform =
            kind -> {
                OpticOp<?, ?> op = OpticOpKindHelper.OP.narrow(
                    (Kind<OpticOpKind.Witness, Object>) kind
                );

                // All operations just return mock data
                Object result = switch (op) {
                    case OpticOp.Get<?, ?> ignored -> mockData;
                    case OpticOp.Set<?, ?> ignored -> mockData;
                    case OpticOp.Modify<?, ?> ignored -> mockData;
                    case OpticOp.GetAll<?, ?> ignored -> List.of(mockData);
                    case OpticOp.Preview<?, ?> ignored -> Optional.of(mockData);
                    default -> throw new UnsupportedOperationException(
                        "Unsupported operation: " + op.getClass().getSimpleName()
                    );
                };

                return Id.of(result);
            };

        Kind<IdKind.Witness, A> resultKind =
            program.foldMap(transform, IdMonad.instance());
        return IdKindHelper.ID.narrow(resultKind).value();
    }
}
```

**Usage in tests:**

```java
@Test
void testBusinessLogic() {
    // Create mock data
    Person mockPerson = new Person("MockUser", 99);

    // Build programme (business logic)
    Free<OpticOpKind.Witness, Person> program =
        buildComplexBusinessLogic(mockPerson);

    // Execute with mock interpreter (no real data needed!)
    MockOpticInterpreter<Person> mock = new MockOpticInterpreter<>(mockPerson);
    Person result = mock.run(program);

    // Verify result
    assertEquals("MockUser", result.name());
}
```

---

## Part 6: Combining Interpreters

You can run the same programme through multiple interpreters for powerful workflows:

### Pattern 1: Validate-Then-Execute

```java
Free<OpticOpKind.Witness, Order> orderProcessing = buildOrderProgramme(order);

// Step 1: Validate
ValidationOpticInterpreter validator = OpticInterpreters.validating();
ValidationResult validation = validator.validate(orderProcessing);

if (!validation.isValid()) {
    validation.errors().forEach(System.err::println);
    throw new ValidationException("Order processing failed validation");
}

// Step 2: Execute with logging
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Order result = logger.run(orderProcessing);

// Step 3: Persist audit trail
logger.getLog().forEach(entry -> auditRepository.save(order.id(), entry));
```

---

### Pattern 2: Profile-Optimise-Execute

```java
Free<OpticOpKind.Witness, Dataset> dataProcessing = buildDataPipeline(dataset);

// Step 1: Profile to find bottlenecks
ProfilingOpticInterpreter profiler = new ProfilingOpticInterpreter();
profiler.run(dataProcessing);

Map<String, Long> times = profiler.getAverageExecutionTimes();
String slowest = times.entrySet().stream()
    .max(Map.Entry.comparingByValue())
    .map(Map.Entry::getKey)
    .orElse("none");

System.out.println("Slowest operation: " + slowest);

// Step 2: Optimise programme based on profiling
Free<OpticOpKind.Witness, Dataset> optimised = optimiseProgramme(
    dataProcessing,
    slowest
);

// Step 3: Execute optimised programme
Dataset result = OpticInterpreters.direct().run(optimised);
```

---

### Pattern 3: Test-Validate-Execute Pipeline

```java
// Development: Mock interpreter
MockOpticInterpreter<Order> mockInterp = new MockOpticInterpreter<>(mockOrder);
Order mockResult = mockInterp.run(programme);
assert mockResult.status() == OrderStatus.COMPLETED;

// Staging: Validation interpreter
ValidationResult validation = OpticInterpreters.validating().validate(programme);
assert validation.isValid();

// Production: Logging interpreter
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Order prodResult = logger.run(programme);
logger.getLog().forEach(auditService::record);
```

---

## Part 7: Best Practices

### Choose the Right Interpreter

| **Use Case** | **Interpreter** | **Reason** |
|-------------|----------------|-----------|
| Production CRUD | Direct | Fast, simple |
| Financial transactions | Logging | Audit trail |
| Data migration | Validation | Safety checks |
| Unit tests | Mock/Custom | No dependencies |
| Performance tuning | Profiling | Measure impact |
| Compliance | Logging | Regulatory requirements |

---

### Interpreter Lifecycle

```java
// ✅ Good: Reuse interpreter for multiple programmes
LoggingOpticInterpreter logger = OpticInterpreters.logging();

for (Transaction txn : transactions) {
    Free<OpticOpKind.Witness, Transaction> program = buildTransfer(txn);
    Transaction result = logger.run(program);
    // Log accumulates across programmes
}

List<String> fullAuditTrail = logger.getLog();

// ❌ Bad: Creating new interpreter each time loses history
for (Transaction txn : transactions) {
    LoggingOpticInterpreter logger = OpticInterpreters.logging();  // New each time!
    Transaction result = logger.run(buildTransfer(txn));
    // Can only see this programme's log
}
```

---

### Error Handling

```java
Free<OpticOpKind.Witness, Order> program = buildOrderProcessing(order);

// Wrap interpreter execution in try-catch
try {
    // Validate first
    ValidationResult validation = OpticInterpreters.validating().validate(program);

    if (!validation.isValid()) {
        throw new ValidationException(validation.errors());
    }

    // Execute with logging
    LoggingOpticInterpreter logger = OpticInterpreters.logging();
    Order result = logger.run(program);

    // Success - persist log
    auditRepository.saveAll(logger.getLog());

    return result;

} catch (ValidationException e) {
    // Handle validation errors
    logger.error("Validation failed", e);
    throw new BusinessException("Order processing failed validation", e);

} catch (Exception e) {
    // Handle execution errors
    logger.error("Execution failed", e);
    throw new BusinessException("Order processing failed", e);
}
```

---

## Further Reading

- **Interpreter Pattern**: [Design Patterns: Elements of Reusable Object-Oriented Software](https://en.wikipedia.org/wiki/Interpreter_pattern) - Gang of Four
- **Natural Transformations**: [Category Theory for Programmers](https://bartoszmilewski.com/2014/10/28/category-theory-for-programmers-the-preface/) by Bartosz Milewski
- **Free Monad Interpreters**: [Why free monads matter](http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html) by Gabriel Gonzalez
- **Aspect-Oriented Programming**: [AspectJ in Action](https://www.manning.com/books/aspectj-in-action-second-edition) by Ramnivas Laddad
- **Cross-Cutting Concerns**: [On the Criteria To Be Used in Decomposing Systems into Modules](https://www.win.tue.nl/~wstomv/edu/2ip30/references/criteria_for_modularization.pdf) by David Parnas

---

**Next Steps:**

- [Free Monad DSL for Optics](free_monad_dsl.md) - Building composable programmes
- [Fluent API for Optics](fluent_api.md) - Direct execution patterns
- [Practical Examples](optics_examples.md) - Real-world applications

# Complete Tutorial Solutions Reference

This document contains the solutions for all exercises in the tutorial series.

## Important API Notes

The Core Types tutorials use the current higher-kinded-j API:

### List Operations
- Use `java.util.List` instead of a custom wrapper
- For typeclass operations, use `ListMonad.INSTANCE` (ListFunctor is package-private)
- Widen with `LIST.widen(list)` and narrow with `LIST.narrow(kind)`
- For simple list transformations, use Java streams

### Error Handling with Either
- Either does NOT have instance methods: `handleErrorWith()`, `recover()`, or `orElse()`
- Use `fold()` for error recovery: `either.fold(error -> defaultValue, value -> value)`
- For fallback: `primary.fold(err -> fallback, Either::right)`

### Reader.ask()
- Must specify type parameter: `Reader.<Config>ask()`
- Returns `Reader<R, R>` that yields the environment

### Try Operations
- Try DOES have `recover()` and `recoverWith()` methods
- Use for exception handling: `Try.of(() -> riskyOperation)`

## Core Types Solutions

### Tutorial 01: Kind Basics

**Exercise 1:**
```java
Kind<EitherKind.Witness<String>, Integer> kind = EITHER.widen(either);
```

**Exercise 2:**
```java
Either<String, Integer> either = EITHER.narrow(kind);
```

**Exercise 3:**
```java
Kind<ListKind.Witness, String> kind = LIST.widen(list);
List<String> narrowedList = LIST.narrow(kind);
```

**Exercise 4:**
```java
Kind<EitherKind.Witness<String>, Boolean> kind = EITHER.widen(either);
```

### Tutorial 02: Functor Mapping

**Exercise 1:**
```java
Either<String, String> result = either.map(Object::toString);
```

**Exercise 2:**
```java
Either<String, String> result = error.map(i -> i.toString());
```

**Exercise 3:**
```java
ListMonad monad = ListMonad.INSTANCE;
Kind<ListKind.Witness, Integer> doubled = monad.map(n -> n * 2, numbers);
```

**Exercise 4:**
```java
Either<String, String> result = value.map(n -> n * 2).map(n -> n + 5).map(Object::toString);
```

**Exercise 5:**
```java
Kind<EitherKind.Witness<String>, String> mapped = functor.map(i -> "Value: " + i, kind);
```

**Exercise 6:**
```java
ListMonad monad = ListMonad.INSTANCE;
Kind<ListKind.Witness, String> uppercase = monad.map(String::toUpperCase, words);
```

### Tutorial 03: Applicative Combining

**Exercise 1:**
```java
Either<String, Integer> result = Either.right(42);
```

**Exercise 2:**
```java
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(EITHER.widen(value1), EITHER.widen(value2), (a, b) -> a + b)
);
```

**Exercise 3:**
```java
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Integer> result = EITHER.narrow(
    applicative.map2(EITHER.widen(value1), EITHER.widen(error), (a, b) -> a + b)
);
```

**Exercise 4:**
```java
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Person> result = EITHER.narrow(
    applicative.map3(EITHER.widen(name), EITHER.widen(age), EITHER.widen(email), (n, a, e) -> new Person(n, a, e))
);
```

**Exercise 5:**
```java
Validated<String, FormData> result = name.map3(age, email, (n, a, e) -> new FormData(n, a, e));
```

**Exercise 6:**
```java
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Order> result = EITHER.narrow(
    applicative.map4(EITHER.widen(id), EITHER.widen(product), EITHER.widen(quantity), EITHER.widen(price), (i, p, q, pr) -> new Order(i, p, q, pr))
);
```

**Exercise 7:**
```java
EitherMonad<String> applicative = EitherMonad.instance();
Either<String, Address> result = EITHER.narrow(
    applicative.map5(EITHER.widen(street), EITHER.widen(city), EITHER.widen(state), EITHER.widen(zip), EITHER.widen(country), (s, c, st, z, co) -> new Address(s, c, st, z, co))
);
```

### Tutorial 04: Monad Chaining

**Exercise 1:**
```java
Either<String, Integer> result = input.flatMap(parse);
```

**Exercise 2:**
```java
Either<String, Double> result = input.flatMap(parse).flatMap(validatePositive).flatMap(divideHundredBy);
```

**Exercise 3:**
```java
Either<String, Integer> result = input.flatMap(parse).flatMap(validatePositive);
```

**Exercise 4:**
```java
Either<String, Integer> result = value.flatMap(validate);
```

**Exercise 5:**
```java
Maybe<String> email = userId.flatMap(findUser).flatMap(user -> user.email());
```

**Exercise 6:**
```java
ListMonad monad = ListMonad.INSTANCE;
Kind<ListKind.Witness, String> pairs = monad.flatMap(n1 -> monad.map(n2 -> n1 + "-" + n2, LIST.widen(numbers2)), numbers1);
```

**Exercise 7:**
```java
MaybeMonad applicative = MaybeMonad.instance();
return MAYBE.narrow(
    applicative.map2(MAYBE.widen(age), MAYBE.widen(city), (a, c) -> "Age: " + a + ", City: " + c)
);
```

### Tutorial 05: Error Handling Patterns

**Exercise 1:**
```java
Kind<EitherKind.Witness<String>, Integer> error = monad.raiseError("Invalid input");
```

**Exercise 2:**
```java
Integer recovered = failed.fold(error -> 0, value -> value);
```

**Exercise 3:**
```java
Integer recovered = error.fold(err -> -1, value -> value);
```

**Exercise 4:**
```java
if (err.equals("NOT_FOUND")) {
  return 0;
} else {
  return -999;
}
```

**Exercise 5:**
```java
Integer result = input.flatMap(parse).flatMap(validatePositive).fold(err -> 1, value -> value);
```

**Exercise 6:**
```java
Either<String, String> result = primary.fold(err -> fallback, value -> Either.right(value));
```

**Exercise 7:**
```java
Try<Integer> result = Try.of(() -> riskyDivision.apply(0));
Try<Integer> recovered = result.recover(ex -> -1);
```

### Tutorial 06: Concrete Types

**Exercise 1:**
```java
return age -> {
  if (age < 0 || age > 150) return Either.left("Invalid age");
  if (age < 18) return Either.left("Too young");
  return Either.right(age);
};
```

**Exercise 2:**
```java
return key -> {
  if (key.equals("key1")) return Maybe.just("value");
  return Maybe.nothing();
};
```

**Exercise 3:**
```java
String result1 = present.orElse("Default");
String result2 = absent.orElse("Default");
```

**Exercise 4:**
```java
List<Integer> result = numbers.stream().filter(n -> n % 2 == 0).map(n -> n * 10).collect(Collectors.toList());
```

**Exercise 5:**
```java
Validated<String, User> validUser = validName.map3(validAge, validEmail, (n, a, e) -> new User(n, a, e));
Validated<String, User> invalidUser = invalidName.map3(invalidAge, invalidEmail, (n, a, e) -> new User(n, a, e));
```

**Exercise 6:**
```java
Either<String, String> either1 = present.fold(() -> Either.left("Not found"), Either::right);
Either<String, String> either2 = absent.fold(() -> Either.left("Not found"), Either::right);
```

**Exercise 7:**
```java
return a -> b -> {
  if (b == 0) return Either.left("Division by zero");
  return Either.right(a / b);
};

return a -> b -> {
  if (b == 0) return Maybe.nothing();
  return Maybe.just(a / b);
};
```

### Tutorial 07: Real World

**Exercise 1:**
```java
Either<ValidationError, Registration> result =
    validateUsername.apply("alice")
        .map4(
            validateEmail.apply("alice@example.com"),
            validatePassword.apply("password123"),
            validateAge.apply(25),
            (username, email, password, age) -> new Registration(username, email, password, age));
```

**Exercise 2:**
```java
List<ProcessedData> processed = rawData.stream()
    .map(processRecord)
    .filter(Either::isRight)
    .map(Either::getRight)
    .collect(Collectors.toList());
```

**Exercise 3:**
```java
return greeting + " (v" + config.version() + ")";
```

**Exercise 4:**
```java
Either<String, User> result =
    findUser.apply("user1")
        .fold(() -> Either.left("User not found"), Either::right)
        .flatMap(validateUser);

Either<String, User> missing = findUser.apply("user999").fold(() -> Either.left("User not found"), Either::right);
```

**Exercise 5:**
```java
List<Either<String, String>> processed = items.stream().map(processItem).collect(Collectors.toList());
```

**Exercise 6:**
```java
Either<String, ProcessedOrder> result = validateOrder.apply(order).map(processOrder.apply(config));
```

## Optics Solutions

### Tutorial 01: Lens Basics

**Exercise 1:**
```java
String name = nameLens.get(person);
```

**Exercise 2:**
```java
Person updated = nameLens.set("Bob", person);
```

**Exercise 3:**
```java
Person updated = ageLens.modify(age -> age + 1, person);
```

**Exercise 4:**
```java
Person updated = ageLens.modify(age -> age + 5, nameLens.set("Bob", person));
```

**Exercise 5:**
```java
String name = ProductLenses.name().get(product);
Product updated = ProductLenses.price().modify(p -> p * 1.1, product);
```

**Exercise 6:**
```java
Lens<Person, String> emailLens = Lens.of(
    Person::email,
    (person, newEmail) -> new Person(person.name(), person.age(), newEmail)
);
```

**Exercise 7:**
```java
Person updated = nameLens.modify(String::toUpperCase, person);
```

### Tutorial 02: Lens Composition

**Exercise 1:**
```java
Lens<Person, String> personToCompanyName = personToCompany.andThen(companyToName);
```

**Exercise 2:**
```java
Lens<Person, String> personToCity = PersonLenses.company()
    .andThen(CompanyLenses.address())
    .andThen(AddressLenses.city());
```

**Exercise 3:**
```java
Person updated = personToStreet.modify(String::toUpperCase, person);
```

**Exercise 4:**
```java
Person updated = cityLens.set("Capital City", companyNameLens.set("MegaCorp", person));
```

**Exercise 5:**
```java
Lens<Application, String> appToHost = appToDb.andThen(dbToConfig).andThen(configToHost);
Lens<Application, Integer> appToPort = appToDb.andThen(dbToConfig).andThen(configToPort);
```

**Exercise 6:**
```java
Person updated2 = PersonLenses.withCompany(
    person,
    CompanyLenses.withAddress(
        person.company(),
        AddressLenses.withCity(person.company().address(), "New City")
    )
);
```

**Exercise 7:**
```java
Lens<User, String> billingCityLens = UserLenses.paymentInfo()
    .andThen(PaymentInfoLenses.billingAddress())
    .andThen(AddressLenses.city());
```

### Tutorial 03: Prism Basics

**Exercise 1:**
```java
Maybe<Circle> extracted = circlePrism.getOptional(circle);
```

**Exercise 2:**
```java
Shape shape = circlePrism.build(circle);
```

**Exercise 3:**
```java
Shape doubledCircle = circlePrism.modify(c -> new Circle(c.radius() * 2), circle);
```

**Exercise 4:**
```java
// Already complete in the tutorial
```

**Exercise 5:**
```java
Maybe<String> value = stringPrism.getOptional(stringValue).map(js -> js.value());
```

**Exercise 6:**
```java
JsonValue updated2 = stringPrism.modify(js -> new JsonString(js.value().toUpperCase()), string2);
```

**Exercise 7:**
```java
boolean isCircle1 = circlePrism.matches(shape1);
boolean isCircle2 = circlePrism.matches(shape2);
boolean isCircle3 = circlePrism.matches(shape3);
```

### Tutorial 04: Traversal Basics

**Exercise 1:**
```java
Team updated = Traversals.modify(playersTraversal, p -> new Player(p.name(), p.score() * 2), team);
```

**Exercise 2:**
```java
Traversal<League, Player> allPlayers = LeagueTraversals.teams().andThen(TeamTraversals.players());
```

**Exercise 3:**
```java
Traversal<Team, Integer> allScores = TeamTraversals.players()
    .andThen(PlayerLenses.score().asTraversal());
```

**Exercise 4:**
```java
Traversal<Team, Player> highScorers = playersTraversal.filtered(p -> p.score() > 100);
```

**Exercise 5:**
```java
List<String> names = Traversals.getAll(allNames, team);
```

**Exercise 6:**
```java
Traversal<Tournament, Player> winningHighScorers =
    TournamentTraversals.teams()
        .filtered(t -> t.won())
        .andThen(TeamTraversals.players())
        .filtered(p -> p.score() >= 100);
```

**Exercise 7:**
```java
int bonus = p.score() >= 100 ? 10 : 5;
```

### Tutorial 05: Optics Composition

**Exercise 1:**
```java
Prism<Order, CreditCard> orderToCreditCard = orderToPayment.andThen(creditCardPrism);
```

**Exercise 2:**
```java
Prism<PaymentMethod, String> cvvPrism = creditCardPrism.andThen(cvvLens);
```

**Exercise 3:**
```java
Traversal<Order, Item> orderToItems = itemsLens.asTraversal().andThen(listTraversal);
```

**Exercise 4:**
```java
Prism<JsonObject, String> valueAccess = dataLens.andThen(stringPrism).andThen(valueLens);
```

**Exercise 5:**
```java
Traversal<JsonArray, JsonString> stringValues = valuesTraversal.andThen(stringPrism.asTraversal());
```

**Exercise 6:**
```java
Traversal<League, Integer> allScores = LeagueTraversals.teams()
    .andThen(TeamTraversals.players())
    .andThen(PlayerLenses.score().asTraversal());
```

**Exercise 7:**
```java
Lens<User, String> userToCity = userToAddress.andThen(addressToCity);
Prism<User, String> userToEmailAddress = userToContact.andThen(emailPrism).andThen(emailToAddress);
```

### Tutorial 06: Generated Optics

**Exercise 1:**
```java
String name = PersonLenses.name().get(person);
Person updated = PersonLenses.age().set(31, person);
Person updated2 = PersonLenses.withEmail(person, "new@example.com");
```

**Exercise 2:**
```java
var successOpt = ResultPrisms.success().getOptional(result);
Result failure = ResultPrisms.failure().build(new Failure("Error"));
```

**Exercise 3:**
```java
var itemsTraversal = CartTraversals.items();
```

**Exercise 4:**
```java
var cityLens = EmployeeLenses.company()
    .andThen(CompanyLenses.address())
    .andThen(AddressLenses.city());
```

**Exercise 5:**
```java
var productsTraversal = InventoryTraversals.products();
```

**Exercise 6:**
```java
User updated2 = UserLenses.withName(user, "Bob");
```

**Exercise 7:**
```java
.andThen(JsonValuePrisms.jsonString().asTraversal())
```

### Tutorial 07: Real World Optics

**Exercise 1:**
```java
Lens<UserProfile, Boolean> emailNotifLens = UserProfileLenses.settings()
    .andThen(UserSettingsLenses.notifications())
    .andThen(NotificationPrefsLenses.email());
```

**Exercise 2:**
```java
Traversal<ApiResponse, String> citiesTraversal = successPrism.asTraversal()
    .andThen(locationsTraversal)
    .andThen(LocationLenses.city().asTraversal());

// For latitudes:
.andThen(LocationLenses.coords())
```

**Exercise 3:**
```java
Traversal<Order, Double> pricesTraversal = OrderTraversals.items()
    .andThen(LineItemLenses.price().asTraversal());

Order discounted = Traversals.modify(pricesTraversal, p -> p * 0.9, order);
```

**Exercise 4:**
```java
Traversal<CustomerDatabase, String> namesTraversal = CustomerDatabaseTraversals.customers()
    .andThen(CustomerLenses.name().asTraversal());

// For cities:
.andThen(CustomerTraversals.addresses())
```

**Exercise 5:**
```java
Lens<AppConfig, Boolean> dbSslLens = AppConfigLenses.database()
    .andThen(DatabaseConfigLenses.ssl());

updated = cacheHostLens.set("prod.example.com", updated);
```

**Exercise 6:**
```java
Traversal<EventStream, Double> purchaseAmounts = EventStreamTraversals.events()
    .andThen(purchasePrism.asTraversal())
    .andThen(PurchaseLenses.amount().asTraversal());
```

---

## Tips for Using These Solutions

1. **Try first, then check** - Attempt each exercise before looking at the solution
2. **Understand, don't memorize** - Focus on understanding the patterns
3. **Experiment** - Try variations to deepen your understanding
4. **Compare approaches** - Your solution might be different but equally valid

## Common Patterns

### Lens Composition
```java
outerLens.andThen(innerLens).andThen(deepestLens)
```

### Prism with Optional Extraction
```java
prism.getOptional(value).map(extracted -> ...)
```

### Traversal Modification
```java
Traversals.modify(traversal, transformFunction, source)
```

### Converting Optics
```java
lens.asTraversal()  // Lens -> Traversal
prism.asTraversal() // Prism -> Traversal
```

### Tutorial 08: Fluent Optics API

**Exercise 1:**
```java
String name = OpticOps.get(person, nameLens);
Person updated = OpticOps.set(person, ageLens, 31);
```

**Exercise 2:**
```java
Person older = OpticOps.modify(person, ageLens, age -> age + 1);
Person capitalised = OpticOps.modify(person, nameLens, String::toUpperCase);
```

**Exercise 3:**
```java
List<String> names = OpticOps.getAll(team, playerNames);
Team bonusApplied = OpticOps.modifyAll(team, playersTraversal, p -> new Player(p.name(), p.score() + 10));
```

**Exercise 4:**
```java
boolean hasHighScorers = OpticOps.exists(team, scores, score -> score >= 100);
int playerCount = OpticOps.count(team, TeamTraversals.players());
Optional<Player> topPlayer = OpticOps.find(team, players, p -> p.score() > 100);
```

**Exercise 5:**
```java
Either<String, User> validResult = OpticOps.modifyEither(user, emailLens, validateEmail);
Either<String, User> invalidResult = OpticOps.modifyEither(invalidUser, emailLens, validateEmail);
```

**Exercise 6:**
```java
Maybe<Person> validResult = OpticOps.modifyMaybe(person, ageLens, validateAge);
Maybe<Person> invalidResult = OpticOps.modifyMaybe(invalidPerson, ageLens, validateAge);
```

**Exercise 7:**
```java
Validated<List<String>, Order> result = OpticOps.modifyAllValidated(order, prices, validatePrice);
Validated<List<String>, Order> validResult = OpticOps.modifyAllValidated(validOrder, validPrices, validatePrice);
```

### Tutorial 09: Advanced Optics DSL

**Exercise 1:**
```java
Free<OpticOpKind.Witness, Integer> getProgram = OpticPrograms.get(person, ageLens);
Free<OpticOpKind.Witness, Person> setProgram = OpticPrograms.set(person, ageLens, 31);
```

**Exercise 2:**
```java
Free<OpticOpKind.Witness, Counter> program =
    OpticPrograms.get(counter, valueLens)
        .flatMap(currentValue -> OpticPrograms.set(counter, valueLens, currentValue + 10));
```

**Exercise 3:**
```java
if (balance >= 1000) {
    return OpticPrograms.set(account, statusLens, "APPROVED");
} else {
    return OpticPrograms.set(account, statusLens, "DENIED");
}
```

**Exercise 4:**
```java
.flatMap(u2 -> OpticPrograms.set(u2, activeLens, true));
```

**Exercise 5:**
```java
LoggingOpticInterpreter logger = OpticInterpreters.logging();
List<String> log = logger.getLog();
```

**Exercise 6:**
```java
ValidationOpticInterpreter validator = OpticInterpreters.validation();
List<String> issues = validator.validate(program);
```

**Exercise 7:**
```java
.flatMap(o4 -> OpticPrograms.set(o4, statusLens, "SHIPPED"));
```

---

## New API Features

### Fluent Optics API (OpticOps)
```java
// Static methods (source-first)
String name = OpticOps.get(person, PersonLenses.name());
Person updated = OpticOps.set(person, PersonLenses.age(), 31);
Person modified = OpticOps.modify(person, PersonLenses.age(), age -> age + 1);

// Collection operations
List<String> names = OpticOps.getAll(team, playerNames);
Team updated = OpticOps.modifyAll(team, players, p -> transform(p));

// Query operations
boolean hasAdults = OpticOps.exists(team, ages, age -> age >= 18);
int count = OpticOps.count(team, players);
Optional<Player> first = OpticOps.find(team, players, p -> p.score() > 100);

// Validation-aware operations
Either<String, User> result = OpticOps.modifyEither(user, lens, validator);
Maybe<Person> result = OpticOps.modifyMaybe(person, lens, validator);
Validated<List<String>, Order> result = OpticOps.modifyAllValidated(order, traversal, validator);
```

### Free Monad DSL (OpticPrograms)
```java
// Build programs as data structures
Free<OpticOpKind.Witness, Person> program =
    OpticPrograms.get(person, ageLens)
        .flatMap(age ->
            age >= 18
                ? OpticPrograms.set(person, statusLens, "ADULT")
                : OpticPrograms.pure(person)
        );

// Execute with different interpreters
Person result = OpticInterpreters.direct().run(program);

// Logging for audit trails
LoggingOpticInterpreter logger = OpticInterpreters.logging();
Person result = logger.run(program);
List<String> auditLog = logger.getLog();

// Validation for dry-runs
ValidationOpticInterpreter validator = OpticInterpreters.validation();
List<String> issues = validator.validate(program);
```

# Lens Extensions: Validated Field Operations

![lenses.jpg](../images/optics.jpg)

Lenses provide a composable way to focus on and update fields in immutable data structures. But what happens when those fields might be `null`, updates require validation, or operations might throw exceptions?

Traditional lenses work brilliantly with clean, valid data. Real-world applications, however, deal with nullable fields, validation requirements, and exception-prone operations. **Lens Extensions** bridge this gap by augmenting lenses with built-in support for Higher-Kinded-J's core types.

Think of lens extensions as **safety rails** for your lenses—they catch null values, validate modifications, and handle exceptions whilst maintaining the elegance of functional composition.

## The Problem: Defensive Programming Clutter

Let's see what happens when we try to use lenses with real-world messy data:

~~~admonish failure title="❌ Traditional Lens Usage with Validation"
```java
public User updateUserEmail(User user, String newEmail) {
    Lens<User, String> emailLens = UserLenses.email();

    // Null checking
    if (user == null) {
        throw new NullPointerException("User cannot be null");
    }

    String currentEmail = emailLens.get(user);
    if (currentEmail == null) {
        // Now what? Set default? Throw exception?
    }

    // Validation
    if (newEmail == null || !newEmail.contains("@")) {
        throw new ValidationException("Invalid email format");
    }

    // Update
    try {
        String validated = validateEmailFormat(newEmail);
        return emailLens.set(validated, user);
    } catch (Exception e) {
        // Handle exception, but lens already called set()
        throw new RuntimeException("Update failed", e);
    }
}
```

The lens operation is buried under layers of null checks, validation, and exception handling.
~~~

~~~admonish success title="✅ With Lens Extensions"
```java
public Either<String, User> updateUserEmail(User user, String newEmail) {
    Lens<User, String> emailLens = UserLenses.email();

    return modifyEither(
        emailLens,
        email -> validateEmail(email),  // Returns Either<String, String>
        user
    );
}

private Either<String, String> validateEmail(String email) {
    if (email == null || !email.contains("@")) {
        return Either.left("Invalid email format");
    }
    return Either.right(email.toLowerCase());
}
```

Clean separation: the lens defines **where** to update, the validation function defines **what** is valid, and `Either` carries the result or error. No defensive programming clutter.
~~~

## Available Lens Extensions

Higher-Kinded-J provides lens extensions in the `LensExtensions` utility class. All methods are static, designed for import with `import static`:

```java
import static org.higherkindedj.optics.extensions.LensExtensions.*;
```

~~~admonish note title="💡 Alternative: Fluent API"
These extension methods are also available through the [Fluent API](fluent_api.md), which provides method chaining and a more discoverable interface. For example, `getMaybe(lens, source)` can also be written as `OpticOps.getting(source).through(lens).asMaybe()`.
~~~

### Safe Access Methods

These methods safely **get** values from fields that might be `null`:

#### `getMaybe` — Null-Safe Field Access

```java
public static <S, A> Maybe<A> getMaybe(Lens<S, A> lens, S source)
```

Returns `Maybe.just(value)` if the field is non-null, `Maybe.nothing()` otherwise.

```java
Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

UserProfile withBio = new UserProfile("u1", "Alice", "alice@example.com", 30, "Software Engineer");
Maybe<String> bio = getMaybe(bioLens, withBio);  // Maybe.just("Software Engineer")

UserProfile withoutBio = new UserProfile("u2", "Bob", "bob@example.com", 25, null);
Maybe<String> noBio = getMaybe(bioLens, withoutBio);  // Maybe.nothing()

// Use with default
String displayBio = bio.orElse("No bio provided");
```

~~~admonish tip title="💡 When to Use getMaybe"
Use `getMaybe` when:
- Accessing optional fields (bio, middle name, phone number)
- Avoiding `NullPointerException` when calling methods on the field
- Composing multiple optional accesses
- Converting between optics and functional style
~~~

#### `getEither` — Access with Default Error

```java
public static <S, A, E> Either<E, A> getEither(Lens<S, A> lens, E error, S source)
```

Returns `Either.right(value)` if non-null, `Either.left(error)` if null.

```java
Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();

UserProfile validProfile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");
Either<String, Integer> age = getEither(ageLens, "Age not provided", validProfile);
// Either.right(30)

UserProfile invalidProfile = new UserProfile("u2", "Bob", "bob@example.com", null, "Student");
Either<String, Integer> noAge = getEither(ageLens, "Age not provided", invalidProfile);
// Either.left("Age not provided")

// Use in a pipeline
String message = age.fold(
    error -> "Error: " + error,
    a -> "Age: " + a
);
```

#### `getValidated` — Access with Validation Error

```java
public static <S, A, E> Validated<E, A> getValidated(Lens<S, A> lens, E error, S source)
```

Like `getEither`, but returns `Validated` for consistency with validation workflows.

```java
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

UserProfile profile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");
Validated<String, String> email = getValidated(emailLens, "Email is required", profile);
// Validated.valid("alice@example.com")

UserProfile noEmail = new UserProfile("u2", "Bob", null, 25, "Student");
Validated<String, String> missing = getValidated(emailLens, "Email is required", noEmail);
// Validated.invalid("Email is required")
```

### Modification Methods

These methods **modify** fields with validation, null-safety, or exception handling:

#### `modifyMaybe` — Optional Modifications

```java
public static <S, A> Maybe<S> modifyMaybe(
    Lens<S, A> lens,
    Function<A, Maybe<A>> modifier,
    S source)
```

Apply a modification that might not succeed. Returns `Maybe.just(updated)` if the modification succeeds, `Maybe.nothing()` if it fails.

```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();
UserProfile profile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

// Successful modification
Maybe<UserProfile> updated = modifyMaybe(
    nameLens,
    name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
    profile
);
// Maybe.just(UserProfile with name "ALICE")

// Failed modification
UserProfile shortName = new UserProfile("u2", "A", "a@example.com", 25, "Student");
Maybe<UserProfile> failed = modifyMaybe(
    nameLens,
    name -> name.length() >= 2 ? Maybe.just(name.toUpperCase()) : Maybe.nothing(),
    shortName
);
// Maybe.nothing()

// Use result
String result = updated
    .map(p -> "Updated: " + p.name())
    .orElse("Update failed");
```

~~~admonish example title="Real-World Example: Optional Formatting"
```java
Lens<Product, String> skuLens = ProductLenses.sku();

// Only format SKU if it matches a pattern
Maybe<Product> formatted = modifyMaybe(
    skuLens,
    sku -> sku.matches("^[A-Z]{3}-\\d{4}$")
        ? Maybe.just(sku.toUpperCase())
        : Maybe.nothing(),  // Leave invalid SKUs unchanged
    product
);
```
~~~

#### `modifyEither` — Fail-Fast Validation

```java
public static <S, A, E> Either<E, S> modifyEither(
    Lens<S, A> lens,
    Function<A, Either<E, A>> modifier,
    S source)
```

Apply a modification with validation. Returns `Either.right(updated)` if valid, `Either.left(error)` if invalid. **Stops at first error**.

```java
Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();
UserProfile profile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

// Valid modification
Either<String, UserProfile> updated = modifyEither(
    ageLens,
    age -> {
        if (age < 0) return Either.left("Age cannot be negative");
        if (age > 150) return Either.left("Age must be realistic");
        return Either.right(age + 1);  // Birthday!
    },
    profile
);
// Either.right(UserProfile with age 31)

// Invalid modification
UserProfile invalidAge = new UserProfile("u2", "Bob", "bob@example.com", 200, "Time traveller");
Either<String, UserProfile> failed = modifyEither(
    ageLens,
    age -> {
        if (age < 0) return Either.left("Age cannot be negative");
        if (age > 150) return Either.left("Age must be realistic");
        return Either.right(age + 1);
    },
    invalidAge
);
// Either.left("Age must be realistic")

// Display result
String message = updated.fold(
    error -> "❌ " + error,
    user -> "✅ Updated age to " + user.age()
);
```

~~~admonish tip title="💡 When to Use modifyEither"
Use `modifyEither` for **fail-fast** validation:
- Single field updates where you want to stop at the first error
- API request validation (reject immediately if any field is invalid)
- Form submissions where you show the first error encountered
- Operations where continuing after an error doesn't make sense
~~~

#### `modifyValidated` — Validated Modifications

```java
public static <S, A, E> Validated<E, S> modifyValidated(
    Lens<S, A> lens,
    Function<A, Validated<E, A>> modifier,
    S source)
```

Like `modifyEither`, but returns `Validated` for consistency with error accumulation workflows.

```java
Lens<UserProfile, String> emailLens = UserProfileLenses.email();
UserProfile profile = new UserProfile("u1", "Alice", "old@example.com", 30, "Engineer");

// Valid email format
Validated<String, UserProfile> updated = modifyValidated(
    emailLens,
    email -> {
        if (!email.contains("@")) {
            return Validated.invalid("Email must contain @");
        }
        if (!email.endsWith(".com") && !email.endsWith(".co.uk")) {
            return Validated.invalid("Email must end with .com or .co.uk");
        }
        return Validated.valid(email.toLowerCase());
    },
    profile
);
// Validated.valid(UserProfile with email "old@example.com")

// Invalid email format
UserProfile badEmail = new UserProfile("u2", "Bob", "invalid-email", 25, "Student");
Validated<String, UserProfile> failed = modifyValidated(
    emailLens,
    email -> {
        if (!email.contains("@")) {
            return Validated.invalid("Email must contain @");
        }
        if (!email.endsWith(".com") && !email.endsWith(".co.uk")) {
            return Validated.invalid("Email must end with .com or .co.uk");
        }
        return Validated.valid(email.toLowerCase());
    },
    badEmail
);
// Validated.invalid("Email must contain @")
```

~~~admonish tip title="💡 Either vs Validated for Single Fields"
For **single field** validation, `modifyEither` and `modifyValidated` behave identically (both fail fast). The difference matters when validating **multiple fields**—use `Validated` when you want to accumulate errors across fields.
~~~

#### `modifyTry` — Exception-Safe Modifications

```java
public static <S, A> Try<S> modifyTry(
    Lens<S, A> lens,
    Function<A, Try<A>> modifier,
    S source)
```

Apply a modification that might throw exceptions. Returns `Try.success(updated)` if successful, `Try.failure(exception)` if an exception occurred.

```java
Lens<UserProfile, String> emailLens = UserProfileLenses.email();
UserProfile profile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

// Successful database update
Try<UserProfile> updated = modifyTry(
    emailLens,
    email -> Try.of(() -> updateEmailInDatabase(email)),
    profile
);
// Try.success(UserProfile with updated email)

// Failed database update
UserProfile badEmail = new UserProfile("u2", "Bob", "fail@example.com", 25, "Student");
Try<UserProfile> failed = modifyTry(
    emailLens,
    email -> Try.of(() -> updateEmailInDatabase(email)),
    badEmail
);
// Try.failure(RuntimeException: "Database connection failed")

// Handle result
updated.match(
    user -> logger.info("Email updated: {}", user.email()),
    error -> logger.error("Update failed", error)
);
```

~~~admonish example title="Real-World Example: Database Operations"
```java
// Update user's profile picture by uploading to S3
Lens<User, String> profilePictureLens = UserLenses.profilePictureUrl();

Try<User> result = modifyTry(
    profilePictureLens,
    oldUrl -> Try.of(() -> {
        // Upload new image to S3 (might throw IOException, AmazonS3Exception)
        String newUrl = s3Client.uploadImage(imageData);
        // Delete old image if it exists
        if (oldUrl != null && !oldUrl.isEmpty()) {
            s3Client.deleteImage(oldUrl);
        }
        return newUrl;
    }),
    user
);

result.match(
    updated -> sendSuccessResponse(updated),
    error -> sendErrorResponse("Image upload failed: " + error.getMessage())
);
```
~~~

#### `setIfValid` — Conditional Updates

```java
public static <S, A, E> Either<E, S> setIfValid(
    Lens<S, A> lens,
    Function<A, Either<E, A>> validator,
    A newValue,
    S source)
```

Set a new value **only if it passes validation**. Unlike `modifyEither`, you provide the new value directly rather than deriving it from the old value.

```java
Lens<UserProfile, String> nameLens = UserProfileLenses.name();
UserProfile profile = new UserProfile("u1", "Alice", "alice@example.com", 30, "Engineer");

// Valid name format
Either<String, UserProfile> updated = setIfValid(
    nameLens,
    name -> {
        if (name.length() < 2) {
            return Either.left("Name must be at least 2 characters");
        }
        if (!name.matches("[A-Z][a-z]+")) {
            return Either.left("Name must start with capital letter");
        }
        return Either.right(name);
    },
    "Robert",
    profile
);
// Either.right(UserProfile with name "Robert")

// Invalid name format
Either<String, UserProfile> failed = setIfValid(
    nameLens,
    name -> {
        if (name.length() < 2) {
            return Either.left("Name must be at least 2 characters");
        }
        if (!name.matches("[A-Z][a-z]+")) {
            return Either.left("Name must start with capital letter");
        }
        return Either.right(name);
    },
    "bob123",
    profile
);
// Either.left("Name must start with capital letter")
```

~~~admonish tip title="💡 setIfValid vs modifyEither"
**Use `setIfValid` when:**
- The new value comes from user input or external source
- You're not transforming the old value
- You want to validate before setting

**Use `modifyEither` when:**
- The new value is derived from the old value (e.g., incrementing, formatting)
- You're transforming the current value
~~~

## Composing Lens Extensions

Lens extensions compose naturally with other optics operations:

### Chaining Multiple Updates

```java
UserProfile original = new UserProfile("u1", "alice", "ALICE@EXAMPLE.COM", 30, null);

Lens<UserProfile, String> nameLens = UserProfileLenses.name();
Lens<UserProfile, String> emailLens = UserProfileLenses.email();

// Chain multiple validations
Either<String, UserProfile> result = modifyEither(
    nameLens,
    name -> Either.right(capitalize(name)),
    original
).flatMap(user ->
    modifyEither(
        emailLens,
        email -> Either.right(email.toLowerCase()),
        user
    )
);
// Either.right(UserProfile with name "Alice", email "alice@example.com")
```

### Nested Structure Updates

```java
@GenerateLenses
record Address(String street, String city, String postcode) {}

@GenerateLenses
record User(String name, Address address) {}

Lens<User, Address> addressLens = UserLenses.address();
Lens<Address, String> postcodeLens = AddressLenses.postcode();
Lens<User, String> userPostcodeLens = addressLens.andThen(postcodeLens);

User user = new User("Alice", new Address("123 Main St", "London", "SW1A 1AA"));

// Validate and update nested field
Either<String, User> updated = modifyEither(
    userPostcodeLens,
    postcode -> validatePostcode(postcode),
    user
);
```

## Common Patterns

### Pattern 1: Form Validation

Validating individual form fields with immediate feedback:

```java
public Either<String, UserProfile> validateAndUpdateEmail(
    UserProfile profile,
    String newEmail
) {
    Lens<UserProfile, String> emailLens = UserProfileLenses.email();

    return modifyEither(
        emailLens,
        email -> {
            if (email == null || email.isEmpty()) {
                return Either.left("Email is required");
            }
            if (!email.contains("@")) {
                return Either.left("Email must contain @");
            }
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
                return Either.left("Email format is invalid");
            }
            return Either.right(email.toLowerCase());
        },
        profile
    );
}
```

### Pattern 2: Safe Field Access with Default

Safely accessing nullable fields and providing defaults:

```java
Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

String displayBio = getMaybe(bioLens, profile)
    .orElse("No bio provided");

// Or with transformation
String formattedBio = getMaybe(bioLens, profile)
    .map(bio -> bio.length() > 100 ? bio.substring(0, 100) + "..." : bio)
    .orElse("No bio");
```

### Pattern 3: Database Operations with Exception Handling

Performing database updates that might fail:

```java
public Try<User> updateUserInDatabase(User user, String newEmail) {
    Lens<User, String> emailLens = UserLenses.email();

    return modifyTry(
        emailLens,
        email -> Try.of(() -> {
            // Validate email is unique in database
            if (emailExists(email)) {
                throw new DuplicateEmailException("Email already in use");
            }
            // Update in database
            database.updateEmail(user.id(), email);
            return email;
        }),
        user
    );
}
```

## Before/After Comparison

Let's see a complete real-world scenario:

**Scenario:** User profile update form with validation.

~~~admonish failure title="❌ Traditional Approach"
```java
public class UserProfileUpdater {
    public UserProfile updateProfile(
        UserProfile profile,
        String newEmail,
        Integer newAge,
        String newBio
    ) throws ValidationException {
        // Email validation
        if (newEmail != null) {
            if (!newEmail.contains("@")) {
                throw new ValidationException("Invalid email");
            }
            profile = new UserProfile(
                profile.userId(),
                profile.name(),
                newEmail.toLowerCase(),
                profile.age(),
                profile.bio()
            );
        }

        // Age validation
        if (newAge != null) {
            if (newAge < 0 || newAge > 150) {
                throw new ValidationException("Invalid age");
            }
            profile = new UserProfile(
                profile.userId(),
                profile.name(),
                profile.email(),
                newAge,
                profile.bio()
            );
        }

        // Bio update (optional)
        if (newBio != null && newBio.length() > 10) {
            profile = new UserProfile(
                profile.userId(),
                profile.name(),
                profile.email(),
                profile.age(),
                newBio
            );
        }

        return profile;
    }
}
```

Problems:
- Repeated record construction (error-prone)
- Mixed validation and update logic
- Throws exceptions (not functional)
- Can't collect multiple errors
- Hard to test individual validations
~~~

~~~admonish success title="✅ With Lens Extensions"
```java
public class UserProfileUpdater {
    public Either<List<String>, UserProfile> updateProfile(
        UserProfile profile,
        String newEmail,
        Integer newAge,
        String newBio
    ) {
        Lens<UserProfile, String> emailLens = UserProfileLenses.email();
        Lens<UserProfile, Integer> ageLens = UserProfileLenses.age();
        Lens<UserProfile, String> bioLens = UserProfileLenses.bio();

        // Update email
        Either<String, UserProfile> emailResult =
            modifyEither(emailLens, this::validateEmail, profile);

        // Update age
        Either<String, UserProfile> ageResult =
            emailResult.flatMap(p -> modifyEither(ageLens, this::validateAge, p));

        // Update bio (optional)
        Either<String, UserProfile> finalResult =
            ageResult.flatMap(p -> modifyMaybe(bioLens, this::formatBio, p)
                .map(Either::<String, UserProfile>right)
                .orElse(Either.right(p)));

        return finalResult.mapLeft(List::of);
    }

    private Either<String, String> validateEmail(String email) {
        if (!email.contains("@")) {
            return Either.left("Email must contain @");
        }
        return Either.right(email.toLowerCase());
    }

    private Either<String, Integer> validateAge(Integer age) {
        if (age < 0 || age > 150) {
            return Either.left("Age must be between 0 and 150");
        }
        return Either.right(age);
    }

    private Maybe<String> formatBio(String bio) {
        return bio.length() > 10 ? Maybe.just(bio) : Maybe.nothing();
    }
}
```

Benefits:
- Clean separation of concerns
- Functional error handling
- Each validation is testable in isolation
- Lenses handle immutable updates
- Clear data flow
~~~

## Best Practices

~~~admonish tip title="💡 Choose the Right Extension Method"
**Use `getMaybe`** when accessing optional fields

**Use `modifyEither`** for fail-fast single field validation

**Use `modifyValidated`** for consistency with multi-field validation (error accumulation)

**Use `modifyTry`** for operations that throw exceptions (database, I/O, network)

**Use `setIfValid`** when setting user-provided values with validation
~~~

~~~admonish tip title="💡 Keep Validation Functions Pure"
Your validation and modification functions should be pure:

```java
// ✅ Pure validation
private Either<String, String> validateEmail(String email) {
    if (!email.contains("@")) {
        return Either.left("Invalid email");
    }
    return Either.right(email.toLowerCase());
}

// ❌ Impure validation (has side effects)
private Either<String, String> validateEmail(String email) {
    logger.info("Validating email: {}", email);  // Side effect!
    if (!email.contains("@")) {
        return Either.left("Invalid email");
    }
    return Either.right(email.toLowerCase());
}
```

Pure functions are easier to test, reason about, and compose.
~~~

~~~admonish warning title="⚠️ Lens Extensions Don't Handle Null Sources"
Lens extensions handle `null` **field values**, but not `null` **source objects**:

```java
UserProfile profile = null;
Maybe<String> bio = getMaybe(bioLens, profile);  // NullPointerException!

// Wrap the source in Maybe first
Maybe<UserProfile> maybeProfile = Maybe.fromNullable(profile);
Maybe<String> safeBio = maybeProfile.flatMap(p -> getMaybe(bioLens, p));
```
~~~

## Working Example

For a complete, runnable demonstration of all lens extension patterns, see:

~~~admonish example title="LensExtensionsExample.java"
[View the complete example →](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/LensExtensionsExample.java)

This example demonstrates:
- All lens extension methods
- User profile management with validation
- Null-safe field access
- Exception-safe database operations
- Form validation patterns
- Real-world scenarios with before/after comparisons
~~~

## Summary

Lens extensions provide:

🛡️ **Safety Rails** — Handle null values, validation, and exceptions without cluttering business logic

🎯 **Separation of Concerns** — Lenses define structure, validators define rules, core types carry results

🔄 **Composability** — Chain multiple validations and updates in a functional pipeline

📊 **Error Handling** — Choose fail-fast (`Either`) or exception-safe (`Try`) based on your needs

🧪 **Testability** — Validation logic is pure and easy to test in isolation

## Next Steps

Now that you understand lens extensions for individual fields, learn how to process collections with validation and error handling:

**Next:** [Traversal Extensions: Bulk Operations](traversal_extensions.md)

Or return to the overview:

**Back:** [Working with Core Types and Optics](core_type_integration.md)

# Capstone: Composing Optics for Deep Validation

~~~admonish info title="What You'll Learn"
- How to compose multiple optic types into powerful processing pipelines
- Building type-safe validation workflows with error accumulation
- Using `asTraversal()` to ensure safe optic composition
- Creating reusable validation paths with effectful operations
- Simplified validation with `modifyAllValidated`, `modifyAllEither`, and `modifyMaybe`
- Understanding when composition is superior to manual validation logic
- Advanced patterns for multi-level and conditional validation scenarios
~~~

~~~admonish example title="See Example Code"
[ValidatedTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java)
~~~

In the previous guides, we explored each core optic (`Lens`, `Prism`, `Iso` and `Traversal`) as individual tools. We've seen how they provide focused, reusable, and composable access to immutable data.

Now, it's time to put it all together.

This guide showcases the true power of the optics approach by composing multiple different optics to solve a single, complex, real-world problem: performing deep, effectful validation on a nested data structure.

---

~~~admonish tip title="Why this matters"
The alternative to this composition is a nested loop that pattern-matches the principal, iterates the permissions, collects errors into a mutable list, and rebuilds the form field by field. That version cannot be reused for anything else, and every new rule reopens it. The composed optic is a value: the same `Traversal<Form, String>` validates, updates, counts and reports, and adding a rule means handing `modifyF` a different function rather than editing a traversal.
~~~

## The Scenario: Validating User Permissions

Imagine a data model for a form that can be filled out by either a registered `User` or a `Guest`. Our goal is to validate that every `Permission` held by a `User` has a valid name.

This single task requires us to:

1. Focus on the form's `principal` field (**a job for a Lens**).
2. Safely "select" the `User` case, ignoring any `Guest`s (**a job for a Prism**).
3. Operate on every `Permission` in the user's list (**a job for a Traversal**).

---

## Think of This Composition Like...

- **A telescope with multiple lenses**: Each optic focuses deeper into the data structure
- **A manufacturing pipeline**: Each stage processes and refines the data further
- **A filter chain**: Data flows through multiple filters, each handling a specific concern
- **A surgical procedure**: Precise, layered operations that work together for a complex outcome

---

### 1. The Data Model

Here is the nested data structure, annotated to generate all the optics we will need.

```java
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;
import java.util.List;

@GenerateLenses
public record Permission(String name) {}

@GeneratePrisms
public sealed interface Principal {}

@GenerateLenses
@GenerateTraversals
public record User(String username, List<Permission> permissions) implements Principal {}

public record Guest() implements Principal {}

@GenerateLenses
public record Form(int formId, Principal principal) {}
```

### 2. The Validation Logic

Our validation function will take a permission name (`String`) and return a `Validated<String, String>`. The `Validated` applicative functor will automatically handle accumulating any errors found.

```java
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import java.util.Set;

private static final Set<String> VALID_PERMISSIONS = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

public static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
    if (VALID_PERMISSIONS.contains(name)) {
        return VALIDATED.widen(Validated.valid(name));
    } else {
        return VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
    }
}
```

### 3. Understanding the Composition Strategy

Before diving into the code, let's understand why we need each type of optic and how they work together:

**Why a Lens for `principal`?**

* The `principal` field always exists in a `Form`
* We need guaranteed access to focus on this field
* A `Lens` provides exactly this: reliable access to required data

**Why a Prism for `User`?**

* The `principal` could be either a `User` or a `Guest`
* We only want to validate `User` permissions, ignoring `Guest`s
* A `Prism` provides safe, optional access to specific sum type cases

**Why a Traversal for `permissions`?**

* We need to validate *every* permission in the list
* We want to accumulate *all* validation errors, not stop at the first one
* A `Traversal` provides bulk operations over collections

**Why convert everything to `Traversal`?**

* `Traversal` is the most general optic type
* It can represent zero-or-more targets (perfect for our "might be empty" scenario)
* All other optics can be converted to `Traversal` for seamless composition

### 4. Composing the Master Optic

Now for the main event. We will compose our generated optics to create a single `Traversal` that declaratively represents the path from a `Form` all the way down to each permission `name`. While the `with*` helpers are great for simple, shallow updates, a deep and conditional update like this requires composition.

To ensure type-safety across different optic types, we convert each `Lens` and `Prism` in the chain to a `Traversal` using the `.asTraversal()` method.

```java
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;

// Get the individual generated optics
Lens<Form, Principal> formPrincipalLens = FormLenses.principal();
Prism<Principal, User> principalUserPrism = PrincipalPrisms.user();
Traversal<User, Permission> userPermissionsTraversal = UserTraversals.permissions();
Lens<Permission, String> permissionNameLens = PermissionLenses.name();

// Compose them into a single, deep Traversal
Traversal<Form, String> formToPermissionNameTraversal =
    formPrincipalLens.asTraversal()
        .andThen(principalUserPrism.asTraversal())
        .andThen(userPermissionsTraversal)
        .andThen(permissionNameLens.asTraversal());
```

This single `formToPermissionNameTraversal` object now encapsulates the entire complex path.

---

## When to Use Optic Composition vs Other Approaches

### Use Optic Composition When:

* **Complex nested validation** - Multiple levels of data structure with conditional logic
* **Reusable validation paths** - The same validation logic applies to multiple scenarios
* **Type-safe bulk operations** - You need to ensure compile-time safety for collection operations
* **Error accumulation** - You want to collect all errors, not stop at the first failure

```java
// Perfect for reusable, complex validation: the path below is built once and
// reused for every rule that needs to reach a permission name.
Traversal<Form, String> allPermissionNames = FORM_TO_PERMISSION_NAMES;

Validated<String, Form> checked = validatePermissions(form);
Validated<String, Form> rechecked = validatePermissions(updatedForm);
```

### Use Direct Validation When:

* **Simple, flat structures** - No deep nesting or conditional access needed
* **One-off validation** - Logic won't be reused elsewhere
* **Performance critical** - Minimal abstraction overhead required


```java
// Simple validation doesn't need optics
public Validated<String, User> validateUser(User user) {
    if (user.username().length() < 3) {
        return Validated.invalid("Username too short");
    }
    return Validated.valid(user);
}
```

### Use Stream Processing When:

* **Complex transformations** - Multiple operations that don't map to optic patterns
* **Aggregation logic** - Computing statistics or summaries
* **Filtering and collecting** - Changing the structure of collections


```java
// Better with streams for aggregation
Map<String, Long> permissionCounts = forms.stream()
    .map(Form::principal)
    .filter(User.class::isInstance)
    .map(User.class::cast)
    .flatMap(user -> user.permissions().stream())
    .collect(groupingBy(Permission::name, counting()));
```

---

## Common Pitfalls

### Don't Do This:


```java
// Over-composing simple cases
Traversal<Form, Integer> formIdTraversal = FormLenses.formId().asTraversal();
// Just use: form.formId()

// Forgetting error accumulation setup
// This won't accumulate errors properly without the right Applicative
var badResult = traversal.modifyF(validatePermissionName, form, /* wrong applicative */);

// Creating complex compositions inline
var inlineResult = FormLenses.principal().asTraversal()
    .andThen(PrincipalPrisms.user().asTraversal())
    .andThen(UserTraversals.permissions())
    .andThen(PermissionLenses.name().asTraversal())
    .modifyF(validatePermissionName, form, applicative); // Hard to read and reuse

// Ignoring the path semantics
// This tries to validate ALL strings, not just permission names
Traversal<Form, String> badTraversal = /* any string traversal */;
```

### Do This Instead:


```java
// Use direct access for simple cases
int formId = form.formId(); // Clear and direct

// Set up error accumulation properly
Applicative<ValidatedKind.Witness<String>> validatedApplicative =
    Instances.validated(Semigroups.string("; "));

// Create reusable, well-named compositions
public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
    FormLenses.principal().asTraversal()
        .andThen(PrincipalPrisms.user().asTraversal())
        .andThen(UserTraversals.permissions())
        .andThen(PermissionLenses.name().asTraversal());

// Use the well-named traversal
var result = FORM_TO_PERMISSION_NAMES.modifyF(validatePermissionName, form, validatedApplicative);

// Be specific about what you're validating
// This traversal has clear semantics: Form -> User permissions -> permission names
```

---

## Performance Notes

Optic composition trades a little throughput for composability, and it is worth being precise about which:

* **Element references are shared**: values a modification leaves alone are reused, not copied; the containers along the path are rebuilt
* **No hidden laziness**: every focus the path reaches is visited, so cost is proportional to the number of foci
* **Single pass**: one `modifyF` walks the structure once, whatever the effect
* **A prism that does not match costs nothing further**: the rest of the chain is never entered for that element

**Best Practice**: Create composed optics as constants for reuse:


```java
public class ValidationOptics {
    // Reusable validation paths
    public static final Traversal<Form, String> USER_PERMISSION_NAMES =
        FormLenses.principal().asTraversal()
            .andThen(PrincipalPrisms.user().asTraversal())
            .andThen(UserTraversals.permissions())
            .andThen(PermissionLenses.name().asTraversal());

    public static final Traversal<Form, String> USERNAMES =
        FormLenses.principal().asTraversal()
            .andThen(PrincipalPrisms.user().asTraversal())
            .andThen(UserLenses.username().asTraversal());

    // Helper methods for common validations
    public static Validated<String, Form> validatePermissions(Form form) {
        return VALIDATED.narrow(USER_PERMISSION_NAMES.modifyF(
            ValidationOptics::validatePermissionName,
            form,
            getValidatedApplicative()
        ));
    }
}
```

---

## Advanced Composition Patterns

### 1. Multi-Level Validation


```java
// Validate both user data AND permissions in one pass
public static Validated<String, Form> validateFormCompletely(Form form) {
    // First validate the user's basic info
    var userValidation = FormLenses.principal().asTraversal()
        .andThen(PrincipalPrisms.user().asTraversal())
        .andThen(UserLenses.username().asTraversal())
        .modifyF(ValidationOptics::validateUsername, form, getValidatedApplicative());
  
    // Then validate permissions
    var permissionValidation = FORM_TO_PERMISSION_NAMES
        .modifyF(ValidationOptics::validatePermissionName, form, getValidatedApplicative());
  
    // Combine both validations
    return VALIDATED.narrow(getValidatedApplicative().map2(
        userValidation,
        permissionValidation,
        (validForm1, validForm2) -> validForm2 // Return the final form
    ));
}
```

### 2. Conditional Validation Paths


```java
// A prism can only branch where the model is actually sealed. `Principal` is,
// so this pair is legal: one path for each variant.
public static final Traversal<Form, String> USER_PERMISSIONS =
    FormLenses.principal().asTraversal()
        .andThen(PrincipalPrisms.user().asTraversal())
        .andThen(UserTraversals.permissions())
        .andThen(PermissionLenses.name().asTraversal());

// `User` is a record, so there is no `UserPrisms`: @GeneratePrisms applies to
// sealed interfaces and enums only. To narrow further, filter on a field.
public static final Traversal<Form, String> DESTRUCTIVE_PERMISSIONS =
    USER_PERMISSIONS.filtered(name -> name.startsWith("PERM_DELETE"));
```

If you genuinely need per-role paths, the split has to exist in the model: make `User` a sealed interface over `AdminUser` and `RegularUser`, and `@GeneratePrisms` will give you `UserPrisms.adminUser()`. A record cannot be narrowed by a prism, only filtered.

### 3. Cross-Field Validation


```java
// Validate that a user's permissions are appropriate for who they are
public static Validated<String, Form> validatePermissionsForUser(Form form) {
    return FormLenses.principal().asTraversal()
        .andThen(PrincipalPrisms.user().asTraversal())
        .modifyF(user -> {
            // Cross-field: the username decides which permissions are allowed
            Set<String> allowedPerms = allowedPermissionsFor(user.username());
            List<String> errors = user.permissions().stream()
                .map(Permission::name)
                .filter(perm -> !allowedPerms.contains(perm))
                .map(perm -> "Permission '" + perm + "' not allowed for " + user.username())
                .toList();
          
            return errors.isEmpty() 
                ? VALIDATED.widen(Validated.valid(user))
                : VALIDATED.widen(Validated.invalid(String.join("; ", errors)));
        }, form, getValidatedApplicative());
}
```

---

## Complete, Runnable Example

With our composed `Traversal`, we can now use `modifyF` to run our validation logic. The `Traversal` handles the navigation and filtering, while the `Validated` applicative (created with a `Semigroup` for joining error strings) handles the effects and error accumulation.


```java
package org.higherkindedj.example.optics;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.instances.Witnesses;
import static org.higherkindedj.hkt.instances.Witnesses.*;

import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.util.List;
import java.util.Set;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.annotations.GeneratePrisms;
import org.higherkindedj.optics.annotations.GenerateTraversals;

public class ValidatedTraversalExample {

    // --- Data Model ---
    @GenerateLenses
    public record Permission(String name) {}

    @GeneratePrisms
    public sealed interface Principal {}

    @GenerateLenses
    @GenerateTraversals
    public record User(String username, List<Permission> permissions) implements Principal {}

    public record Guest() implements Principal {}

    @GenerateLenses
    public record Form(int formId, Principal principal) {}

    // --- Validation Logic ---
    private static final Set<String> VALID_PERMISSIONS = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

    public static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
        if (VALID_PERMISSIONS.contains(name)) {
            return VALIDATED.widen(Validated.valid(name));
        } else {
            return VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
        }
    }

    // --- Reusable Optic Compositions ---
    public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
            FormLenses.principal().asTraversal()
                    .andThen(PrincipalPrisms.user().asTraversal())
                    .andThen(UserTraversals.permissions())
                    .andThen(PermissionLenses.name().asTraversal());

    // --- Helper Methods ---
    private static Applicative<ValidatedKind.Witness<String>> getValidatedApplicative() {
        return Instances.validated(Semigroups.string("; "));
    }

    public static Validated<String, Form> validateFormPermissions(Form form) {
        Kind<ValidatedKind.Witness<String>, Form> result =
                FORM_TO_PERMISSION_NAMES.modifyF(
                        ValidatedTraversalExample::validatePermissionName,
                        form,
                        getValidatedApplicative()
                );
        return VALIDATED.narrow(result);
    }

    public static void main(String[] args) {
        System.out.println("=== OPTIC COMPOSITION VALIDATION EXAMPLE ===");
        System.out.println();

        // --- SCENARIO 1: Form with valid permissions ---
        System.out.println("--- Scenario 1: Valid Permissions ---");
        var validUser = new User("alice", List.of(
                new Permission("PERM_READ"),
                new Permission("PERM_WRITE")
        ));
        var validForm = new Form(1, validUser);

        System.out.println("Input: " + validForm);
        Validated<String, Form> validResult = validateFormPermissions(validForm);
        System.out.println("Result: " + validResult);
        System.out.println();

        // --- SCENARIO 2: Form with multiple invalid permissions ---
        System.out.println("--- Scenario 2: Multiple Invalid Permissions ---");
        var invalidUser = new User("charlie", List.of(
                new Permission("PERM_EXECUTE"),  // Invalid
                new Permission("PERM_WRITE"),    // Valid
                new Permission("PERM_SUDO"),     // Invalid
                new Permission("PERM_READ")      // Valid
        ));
        var multipleInvalidForm = new Form(3, invalidUser);

        System.out.println("Input: " + multipleInvalidForm);
        Validated<String, Form> invalidResult = validateFormPermissions(multipleInvalidForm);
        System.out.println("Result (errors accumulated): " + invalidResult);
        System.out.println();

        // --- SCENARIO 3: Form with Guest principal (no targets for traversal) ---
        System.out.println("--- Scenario 3: Guest Principal (No Validation Targets) ---");
        var guestForm = new Form(4, new Guest());

        System.out.println("Input: " + guestForm);
        Validated<String, Form> guestResult = validateFormPermissions(guestForm);
        System.out.println("Result (path does not match): " + guestResult);
        System.out.println();

        // --- SCENARIO 4: Form with empty permissions list ---
        System.out.println("--- Scenario 4: Empty Permissions List ---");
        var emptyPermissionsUser = new User("diana", List.of());
        var emptyPermissionsForm = new Form(5, emptyPermissionsUser);

        System.out.println("Input: " + emptyPermissionsForm);
        Validated<String, Form> emptyResult = validateFormPermissions(emptyPermissionsForm);
        System.out.println("Result (empty list): " + emptyResult);
        System.out.println();

        // --- SCENARIO 5: Demonstrating optic reusability ---
        System.out.println("--- Scenario 5: Optic Reusability ---");

        List<Form> formsToValidate = List.of(validForm, multipleInvalidForm, guestForm);

        System.out.println("Batch validation results:");
        formsToValidate.forEach(form -> {
            Validated<String, Form> result = validateFormPermissions(form);
            String status = result.isValid() ? "✓ VALID" : "✗ INVALID";
            System.out.println("  Form " + form.formId() + ": " + status);
            if (result.isInvalid()) {
                System.out.println("    Errors: " + result.getError());
            }
        });
        System.out.println();

        // --- SCENARIO 6: Alternative validation with different error accumulation ---
        System.out.println("--- Scenario 6: Different Error Accumulation Strategy ---");

        // Use list-based error accumulation instead of string concatenation
        Applicative<ValidatedKind.Witness<List<String>>> listApplicative =
                Instances.validated(Semigroups.list());

        java.util.function.Function<String, Kind<ValidatedKind.Witness<List<String>>, String>> listValidation =
                name -> VALID_PERMISSIONS.contains(name)
                        ? VALIDATED.widen(Validated.valid(name))
                        : VALIDATED.widen(Validated.invalid(List.of("Invalid permission: " + name)));

        Kind<ValidatedKind.Witness<List<String>>, Form> listResult =
                FORM_TO_PERMISSION_NAMES.modifyF(listValidation, multipleInvalidForm, listApplicative);

        System.out.println("Input: " + multipleInvalidForm);
        System.out.println("Result with list accumulation: " + VALIDATED.narrow(listResult));
    }
}
```

**Expected Output:**

```
=== OPTIC COMPOSITION VALIDATION EXAMPLE ===

--- Scenario 1: Valid Permissions ---
Input: Form[formId=1, principal=User[username=alice, permissions=[Permission[name=PERM_READ], Permission[name=PERM_WRITE]]]]
Result: Valid(Form[formId=1, principal=User[username=alice, permissions=[Permission[name=PERM_READ], Permission[name=PERM_WRITE]]]])

--- Scenario 2: Multiple Invalid Permissions ---
Input: Form[formId=3, principal=User[username=charlie, permissions=[Permission[name=PERM_EXECUTE], Permission[name=PERM_WRITE], Permission[name=PERM_SUDO], Permission[name=PERM_READ]]]]
Result (errors accumulated): Invalid(Invalid permission: PERM_EXECUTE; Invalid permission: PERM_SUDO)

--- Scenario 3: Guest Principal (No Validation Targets) ---
Input: Form[formId=4, principal=Guest[]]
Result (path does not match): Valid(Form[formId=4, principal=Guest[]])

--- Scenario 4: Empty Permissions List ---
Input: Form[formId=5, principal=User[username=diana, permissions=[]]]
Result (empty list): Valid(Form[formId=5, principal=User[username=diana, permissions=[]]])

--- Scenario 5: Optic Reusability ---
Batch validation results:
  Form 1: ✓ VALID
  Form 3: ✗ INVALID
    Errors: Invalid permission: PERM_EXECUTE; Invalid permission: PERM_SUDO
  Form 4: ✓ VALID

--- Scenario 6: Different Error Accumulation Strategy ---
Input: Form[formId=3, principal=User[username=charlie, permissions=[Permission[name=PERM_EXECUTE], Permission[name=PERM_WRITE], Permission[name=PERM_SUDO], Permission[name=PERM_READ]]]]
Result with list accumulation: Invalid([Invalid permission: PERM_EXECUTE, Invalid permission: PERM_SUDO])
```

This shows how our single, composed optic correctly handled all cases: it accumulated multiple failures into a single `Invalid` result, and it correctly did nothing (resulting in a `Valid` state) when the path did not match. This is the power of composing simple, reusable optics to solve complex problems in a safe, declarative, and boilerplate-free way.

---

## Why This Approach is Powerful

This capstone example demonstrates several key advantages of the optics approach:

### **Declarative Composition**

The `formToPermissionNameTraversal` reads like a clear path specification: "From a Form, go to the principal, if it's a User, then to each permission, then to each name." This is self-documenting code.

### **Type Safety**

Every step in the composition is checked at compile time. It's impossible to accidentally apply permission validation to Guest data or to skip the User filtering step.

### **Automatic Error Accumulation**

The `Validated` applicative automatically collects all validation errors without us having to write any error-handling boilerplate. We get comprehensive validation reports for free.

### **Reusability**

The same composed optic can be used for validation, data extraction, transformation, or any other operation. We write the path once and reuse it everywhere.

### **Composability**

Each individual optic (Lens, Prism, Traversal) can be tested and reasoned about independently, then composed to create more complex behaviour.

### **Graceful Handling of Edge Cases**

The composition automatically handles empty collections, missing data, and type mismatches without special case code.

By mastering optic composition, you gain a powerful tool for building robust, maintainable data processing pipelines that are both expressive and efficient.

---

## Without the Applicative: Validation-Aware Methods

~~~admonish tip title="Enhanced Validation Patterns"
Higher-kinded-j provides specialised validation methods that simplify the patterns shown above. These methods eliminate the need for explicit `Applicative` setup whilst maintaining full type safety and error accumulation capabilities.
~~~

### The Traditional Approach (Revisited)

In the examples above, we used the general `modifyF` method with explicit `Applicative` configuration:

```java
// Traditional approach: requires explicit Applicative setup
Applicative<ValidatedKind.Witness<String>> applicative =
    Instances.validated(Semigroups.string("; "));

Kind<ValidatedKind.Witness<String>, Form> result =
    FORM_TO_PERMISSION_NAMES.modifyF(
        ValidatedTraversalExample::validatePermissionName,
        form,
        applicative
    );

Validated<String, Form> validated = VALIDATED.narrow(result);
```

Whilst powerful and flexible, this approach requires:
* Understanding of `Applicative` functors
* Manual creation of the `Applicative` instance
* Explicit narrowing of `Kind` results
* Knowledge of `Witness` types and HKT encoding

### The Simplified Approach: Validation-Aware Methods

These methods provide a more direct API for the common validation patterns:

~~~admonish warning title="Two families, two argument orders"
`OpticOps` takes the **source first**: `modifyAllValidated(form, path, validator)`. The `LensExtensions` and `TraversalExtensions` statics on [Optics Extensions](optics_extensions.md) take the **optic first**: `modifyAllValidated(path, validator, form)`. They do the same work; only the convention differs, and mixing them up is a compile error rather than a silent bug.
~~~

#### 1. **Error Accumulation with `modifyAllValidated`**

Simplifies the most common case: validating multiple fields and accumulating all errors.

```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllValidated;

// Simplified: direct Validated result, automatic error accumulation
Validated<List<String>, Form> result = modifyAllValidated(
    form,
    FORM_TO_PERMISSION_NAMES,
    name -> VALID_PERMISSIONS.contains(name)
        ? Validated.valid(name)
        : Validated.invalid("Invalid permission: " + name));
```

**Benefits:**
* No `Applicative` setup required
* Direct `Validated` result (no `Kind` wrapping)
* Automatic error accumulation with `List<E>`
* Clear intent: "validate all and collect errors"

#### 2. **First Error Only, with `modifyAllEither`**

When the caller only needs to know that *something* failed, and which failure the traversal met first:

```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllEither;

// Short-circuit: stops at first error
Either<String, Form> result = modifyAllEither(
    form,
    FORM_TO_PERMISSION_NAMES,
    name -> VALID_PERMISSIONS.contains(name)
        ? Either.right(name)
        : Either.left("Invalid permission: " + name));
```

**Benefits:**
* A direct `Either` result: no `Kind`, no narrowing
* One error rather than a report, which is what a batch job or an internal caller usually wants
* The traversal still evaluates every element; only the *result* keeps the first failure. Choose this for the shape of the answer, not to save work

### Comparison: Traditional vs Validation-Aware Methods

| Aspect | Traditional `modifyF` | Validation-Aware Methods |
|--------|----------------------|--------------------------|
| **Applicative Setup** | Required (explicit) | Not required (automatic) |
| **Type Complexity** | High (`Kind`, `Witness`) | Low (direct types) |
| **Error Accumulation** | Yes (via Applicative) | Yes (`modifyAllValidated`) |
| **First-error result** | Manual (via Either Applicative) | Built-in (`modifyAllEither`) |
| **Learning Curve** | Steep (HKT knowledge) | Gentle (familiar types) |
| **Flexibility** | Maximum (any Applicative) | Focused (common patterns) |
| **Boilerplate** | More (setup code) | Less (direct API) |
| **Use Case** | Generic effectful operations | Validation-specific scenarios |

### When to Use Each Approach

**Use `modifyAllValidated` when:**
* You need to **collect all validation errors**
* Building **form validation** or **data quality checks**
* Users need **comprehensive error reports**

```java
// Perfect for form validation
Validated<List<String>, OrderForm> validated = modifyAllValidated(
    orderForm, ORDER_TO_PRICES, OrderRules::validatePrice);
```

**Use `modifyAllEither` when:**
* One error is **sufficient feedback**
* The caller is a **batch job or internal service**, not a person filling in a form
* You want the **`Either` shape** the rest of your pipeline already speaks

```java
// Perfect when one message is all the caller will act on
Either<String, OrderForm> validated = modifyAllEither(
    orderForm, ORDER_TO_PRICES, OrderRules::validatePrice);
```

**Use `modifyMaybe` when:**
* A **single** optional modification either lands or yields nothing
* Building **data enrichment** pipelines where a miss means "leave the whole thing alone"
* Failure needs **no detail**, only presence or absence

```java
// modifyMaybe focuses ONE field through a Lens: nothing() discards the whole update
Maybe<OrderForm> enriched = modifyMaybe(orderForm, ORDER_DISCOUNT, OrderRules::tryApplyDiscount);
```

It is all-or-nothing on that one focus, not a per-element filter. For "modify what you can and keep the rest", reach for `TraversalExtensions.modifyWherePossible`.

**Use traditional `modifyF` when:**
* Working with **custom Applicative** functors
* Need **maximum flexibility**
* Building **generic abstractions**
* Using effects **beyond validation** (IO, Future, etc.)

```java
// Still valuable for generic effectful operations
Kind<F, Form> result = FORM_TO_PERMISSION_NAMES.modifyF(
    effectfulValidation,
    form,
    customApplicative
);
```

### Real-World Example: Simplified Validation

Here's how the original example can be simplified using the new methods:

```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllValidated;
import org.higherkindedj.hkt.validated.Validated;
import java.util.List;

public class SimplifiedValidation {
    // Same traversal as before
    public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
        FormLenses.principal().asTraversal()
            .andThen(PrincipalPrisms.user().asTraversal())
            .andThen(UserTraversals.permissions())
            .andThen(PermissionLenses.name().asTraversal());

    // Simplified validation - no Applicative setup needed
    public static Validated<List<String>, Form> validateFormPermissions(Form form) {
        return modifyAllValidated(
            form,
            FORM_TO_PERMISSION_NAMES,
            name -> VALID_PERMISSIONS.contains(name)
                ? Validated.valid(name)
                : Validated.invalid("Invalid permission: " + name));
    }

    // Alternative: keep only the first error
    public static Either<String, Form> validateFormPermissionsFirstError(Form form) {
        return modifyAllEither(
            form,
            FORM_TO_PERMISSION_NAMES,
            name -> VALID_PERMISSIONS.contains(name)
                ? Either.right(name)
                : Either.left("Invalid permission: " + name));
    }
}
```

**Benefits of the Simplified Approach:**
* **~60% less code**: No `Applicative` setup, no `Kind` wrapping, no narrowing
* **Clearer intent**: Method name explicitly states the validation strategy
* **Easier to learn**: Uses familiar types (`Validated`, `Either`, `Maybe`)
* **Equally powerful**: Same type safety, same error accumulation, same composition

~~~admonish example title="Complete Example"
See [FluentValidationExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/fluent/FluentValidationExample.java) for comprehensive demonstrations of all validation-aware methods, including complex real-world scenarios like order validation and bulk data import.
~~~

~~~admonish info title="Key Takeaways"
* **Four optics, three kinds, one value.** `Lens >>> Prism >>> Traversal >>> Lens` collapses into a single `Traversal<Form, String>` that you name once and reuse for reads, writes and validations.
* **`.asTraversal()` is the levelling step.** Composing mixed optic kinds means widening each to the weakest one in the chain, which is why the composed path is a `Traversal` rather than a `Lens`.
* **The prism is the safety.** A `Form` holding a `Guest` puts nothing in focus, so the whole pipeline returns cleanly with no branch written for the absent case.
* **`Validated` accumulates, `Either` keeps the first.** The optic never changes; only the `Applicative` handed to `modifyF` does, and that single choice is the whole difference between a full report and one message. Neither skips elements.
* **The fluent methods remove the ceremony, not the power.** `OpticOps` gives the same accumulation without `widen`, `narrow` or an explicit `Applicative` at the call site.
~~~

~~~admonish tip title="See Also"
- [Fluent API for Optics](fluent_api.md#part-2-validation-aware-modification): the four validation strategies, the builders, and when to drop to `modifyF`
- [Composition Rules](composition_rules.md): why a chain of mixed optics widens to a `Traversal`
- [Core Type Integration](core_type_integration.md): the prisms that let a core type sit mid-path
~~~

~~~admonish info title="Hands-On Learning"
Practice optic composition in [Tutorial 06: Optics Composition](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial06_OpticsComposition.java) (7 exercises, ~10 minutes).
~~~

---

**Previous:** [Integration and Recipes](ch5_intro.md)
**Next:** [Core Type Integration](core_type_integration.md)

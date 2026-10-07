# Deep Validation with `modifyF`

_Validate every element a composed path reaches with `modifyF`, and report every failure at once._

~~~admonish info title="What You'll Learn"
- Compose a `Lens`, a `Prism` and a `Traversal` with `andThen`, and predict the optic type the chain returns
- Validate every focus of the composed path with `modifyF` and a `Validated` applicative, accumulating every error
- Decide when optic composition beats direct validation or a stream pipeline
- Combine two path validations with `map2`, and narrow with `filtered` where the model has no prism
- Skip the `Applicative` setup with `modifyAllValidated` or `modifyAllEither`, choosing by whether the caller needs every error
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

## Building the path, step by step {#building-the-path-step-by-step}

Composing optics plays the part of `Function.andThen`: each optic feeds the next. Unlike chained functions, the composed optic also writes, and its type says how many values the whole path reaches.

### 1. The Data Model

Here is the nested data structure, annotated to generate all the optics we will need.

<!-- verify -->
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

<!-- verify -->
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

**Why is the result a `Traversal`?**

* The path reaches zero or more names: none for a `Guest`, one per permission for a `User`
* `andThen` works the type out at each step: a `Lens` then a `Prism` is an `Affine`, and an `Affine` then a `Traversal` is a `Traversal`
* So the chain needs no conversions; [Composition Rules](composition_rules.md) has the full table

### 4. Composing the Master Optic

Now for the main event. We will compose our generated optics to create a single `Traversal` that declaratively represents the path from a `Form` all the way down to each permission `name`. While the `with*` helpers are great for simple, shallow updates, a deep and conditional update like this requires composition.

Each `andThen` returns the most precise optic the two steps allow, so the generated optics chain directly.

<!-- verify -->
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
    formPrincipalLens                       // Lens<Form, Principal>
        .andThen(principalUserPrism)        // Affine<Form, User>
        .andThen(userPermissionsTraversal)  // Traversal<Form, Permission>
        .andThen(permissionNameLens);       // Traversal<Form, String>
```

This single `formToPermissionNameTraversal` object now encapsulates the entire complex path.

---

## When to Use Optic Composition vs Other Approaches

### Use Optic Composition When

* **Complex nested validation**: Multiple levels of data structure with conditional logic
* **Reusable validation paths**: The same validation logic applies to multiple scenarios
* **Type-safe bulk operations**: You need to ensure compile-time safety for collection operations
* **Error accumulation**: You want to collect all errors, not stop at the first failure

<!-- verify -->
```java
// Perfect for reusable, complex validation: the path below is built once and
// reused for every rule that needs to reach a permission name.
Traversal<Form, String> allPermissionNames = FORM_TO_PERMISSION_NAMES;

Validated<String, Form> checked = validatePermissions(form);
Validated<String, Form> rechecked = validatePermissions(updatedForm);
```

### Use Direct Validation When

* **Simple, flat structures**: No deep nesting or conditional access needed
* **One-off validation**: Logic won't be reused elsewhere
* **A hot loop you have measured**: [Production Readiness](production_readiness.md#runtime-cost) says what each call allocates


<!-- verify -->
```java
// Simple validation doesn't need optics
public Validated<String, User> validateUser(User user) {
    if (user.username().length() < 3) {
        return Validated.invalid("Username too short");
    }
    return Validated.valid(user);
}
```

### Use Stream Processing When

* **Complex transformations**: Multiple operations that don't map to optic patterns
* **Aggregation logic**: Computing statistics or summaries
* **Filtering and collecting**: Changing the structure of collections


<!-- verify -->
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

### Don't Do This


```java
// Over-composing simple cases
Traversal<Form, Integer> formIdTraversal = FormLenses.formId().asTraversal();
// Just use: form.formId()

// Forgetting error accumulation setup
// This won't accumulate errors properly without the right Applicative
var badResult = traversal.modifyF(validatePermissionName, form, /* wrong applicative */);

// Creating complex compositions inline
var inlineResult = FormLenses.principal()
    .andThen(PrincipalPrisms.user())
    .andThen(UserTraversals.permissions())
    .andThen(PermissionLenses.name())
    .modifyF(validatePermissionName, form, applicative); // Hard to read and reuse

// Ignoring the path semantics
// This tries to validate ALL strings, not just permission names
Traversal<Form, String> badTraversal = /* any string traversal */;
```

### Do This Instead


<!-- verify -->
```java
// Use direct access for simple cases
int formId = form.formId(); // Clear and direct

// Set up error accumulation properly
Applicative<ValidatedKind.Witness<String>> validatedApplicative =
    Instances.validated(Semigroups.string("; "));

// Create reusable, well-named compositions
public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
    FormLenses.principal()
        .andThen(PrincipalPrisms.user())
        .andThen(UserTraversals.permissions())
        .andThen(PermissionLenses.name());

// Use the well-named traversal
var result = FORM_TO_PERMISSION_NAMES.modifyF(
    ValidationOptics::validatePermissionName, form, validatedApplicative);

// Be specific about what you're validating
// This traversal has clear semantics: Form -> User permissions -> permission names
```

---

## Advanced Composition Patterns

### 1. Multi-Level Validation


<!-- verify -->
```java
// Validate the user's details and their permissions, collecting the errors from both
public static Validated<String, Form> validateFormCompletely(Form form) {
    // First validate the user's basic info
    var userValidation = FormLenses.principal()
        .andThen(PrincipalPrisms.user())
        .andThen(UserLenses.username())
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


<!-- verify -->
```java
// A prism can only branch where the model is actually sealed. `Principal` is,
// so this pair is legal: one path for each variant.
public static final Traversal<Form, String> USER_PERMISSIONS =
    FormLenses.principal()
        .andThen(PrincipalPrisms.user())
        .andThen(UserTraversals.permissions())
        .andThen(PermissionLenses.name());

// `User` is a record, so there is no `UserPrisms`: @GeneratePrisms applies to
// sealed interfaces and enums only. To narrow further, filter on a field.
public static final Traversal<Form, String> DESTRUCTIVE_PERMISSIONS =
    USER_PERMISSIONS.filtered(name -> name.startsWith("PERM_DELETE"));
```

If you genuinely need per-role paths, the split has to exist in the model: make `User` a sealed interface over `AdminUser` and `RegularUser`, and `@GeneratePrisms` will give you `UserPrisms.adminUser()`. A record cannot be narrowed by a prism, only filtered.

### 3. Cross-Field Validation


<!-- verify -->
```java
// Validate that a user's permissions are appropriate for who they are
public static Validated<String, Form> validatePermissionsForUser(Form form) {
    return VALIDATED.narrow(FormLenses.principal()
        .andThen(PrincipalPrisms.user())
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
        }, form, getValidatedApplicative()));
}
```

---

## Complete, Runnable Example {#complete-runnable-example}

With our composed `Traversal`, we can now use `modifyF` to run our validation logic. The `Traversal` handles the navigation and filtering, while the `Validated` applicative (created with a `Semigroup` for joining error strings) handles the effects and error accumulation.


The program names its record `VTUser`, to keep it apart from the other examples in its package, so its generated prism is `PrincipalPrisms.vTUser()` where this page writes `user()`.

```java
{{#include ../../../hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java:complete_example}}
```

**Expected Output:**

```
=== OPTIC COMPOSITION VALIDATION EXAMPLE ===

--- Scenario 1: Valid Permissions ---
Input: Form[formId=1, principal=VTUser[username=alice, permissions=[Permission[name=PERM_READ], Permission[name=PERM_WRITE]]]]
Result: Valid(Form[formId=1, principal=VTUser[username=alice, permissions=[Permission[name=PERM_READ], Permission[name=PERM_WRITE]]]])

--- Scenario 2: Multiple Invalid Permissions ---
Input: Form[formId=3, principal=VTUser[username=charlie, permissions=[Permission[name=PERM_EXECUTE], Permission[name=PERM_WRITE], Permission[name=PERM_SUDO], Permission[name=PERM_READ]]]]
Result (errors accumulated): Invalid(Invalid permission: PERM_EXECUTE; Invalid permission: PERM_SUDO)

--- Scenario 3: Guest Principal (No Validation Targets) ---
Input: Form[formId=4, principal=Guest[]]
Result (path does not match): Valid(Form[formId=4, principal=Guest[]])

--- Scenario 4: Empty Permissions List ---
Input: Form[formId=5, principal=VTUser[username=diana, permissions=[]]]
Result (empty list): Valid(Form[formId=5, principal=VTUser[username=diana, permissions=[]]])

--- Scenario 5: Optic Reusability ---
Batch validation results:
  Form 1: ✓ VALID
  Form 3: ✗ INVALID
    Errors: Invalid permission: PERM_EXECUTE; Invalid permission: PERM_SUDO
  Form 4: ✓ VALID

--- Scenario 6: Different Error Accumulation Strategy ---
Input: Form[formId=3, principal=VTUser[username=charlie, permissions=[Permission[name=PERM_EXECUTE], Permission[name=PERM_WRITE], Permission[name=PERM_SUDO], Permission[name=PERM_READ]]]]
Result with list accumulation: Invalid([Invalid permission: PERM_EXECUTE, Invalid permission: PERM_SUDO])
--- Scenario 7: Selective Validation (Skipping the Expensive Check) ---
Input: Form[formId=7, principal=VTUser[username=eve, permissions=[Permission[name=], Permission[name=PERM_READ], Permission[name=INVALID_PERM]]]]
  Running EXPENSIVE validation for: PERM_READ
  Running EXPENSIVE validation for: INVALID_PERM
Result: Invalid(Invalid permission: INVALID_PERM)
Note: the expensive validation ran only for non-empty permissions
```

This shows how our single, composed optic correctly handled all cases: it accumulated multiple failures into a single `Invalid` result, and it correctly did nothing (resulting in a `Valid` state) when the path did not match. This is the power of composing simple, reusable optics to solve complex problems in a safe, declarative, and boilerplate-free way.

Scenario 7 runs the same path through `modifyWhen` with a `Selective`. The cheap check runs first, and the expensive validation is called only for the names that pass it. The output shows no call for the empty name, so the expensive function may assume the check held. A name the check rejects is kept as it is and adds no error, which is why only `INVALID_PERM` is reported.

---

## Why This Approach is Powerful

This example shows several advantages of the optics approach:

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

By mastering optic composition, you gain a powerful tool for building robust, maintainable data processing pipelines that read as the path they follow.

---

## Without the Applicative: Validation-Aware Methods

~~~admonish tip title="Enhanced Validation Patterns"
Higher-kinded-j provides specialised validation methods that simplify the patterns shown above. These methods eliminate the need for explicit `Applicative` setup whilst maintaining full type safety and error accumulation capabilities.
~~~

### The Traditional Approach (Revisited)

In the examples above, we used the general `modifyF` method with explicit `Applicative` configuration:

<!-- verify -->
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

<!-- verify -->
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

<!-- verify -->
```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllEither;

// Every element is validated; the result keeps only the first error
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

<!-- verify -->
```java
// Perfect for form validation
Validated<List<String>, OrderForm> validated = modifyAllValidated(
    orderForm, ORDER_TO_PRICES, OrderRules::validatePrice);
```

**Use `modifyAllEither` when:**
* One error is **sufficient feedback**
* The caller is a **batch job or internal service**, not a person filling in a form
* You want the **`Either` shape** the rest of your pipeline already speaks

<!-- verify -->
```java
// Perfect when one message is all the caller will act on
Either<String, OrderForm> validated = modifyAllEither(
    orderForm, ORDER_TO_PRICES, OrderRules::checkPrice);
```

**Use `modifyMaybe` when:**
* A **single** optional modification either lands or yields nothing
* Building **data enrichment** pipelines where a miss means "leave the whole thing alone"
* Failure needs **no detail**, only presence or absence

<!-- verify -->
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

<!-- verify -->
```java
// Still valuable for generic effectful operations: one method for any applicative F
<F extends WitnessArity<TypeArity.Unary>> Kind<F, Form> checkNames(
    Function<String, Kind<F, String>> effectfulValidation, Applicative<F> customApplicative) {
    return FORM_TO_PERMISSION_NAMES.modifyF(effectfulValidation, form, customApplicative);
}
```

### Real-World Example: Simplified Validation

Here's how the original example can be simplified using the new methods:

<!-- verify -->
```java
import static org.higherkindedj.optics.fluent.OpticOps.modifyAllValidated;
import org.higherkindedj.hkt.validated.Validated;
import java.util.List;

public class SimplifiedValidation {
    private static final Set<String> VALID_PERMISSIONS =
        Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

    // Same traversal as before
    public static final Traversal<Form, String> FORM_TO_PERMISSION_NAMES =
        FormLenses.principal()
            .andThen(PrincipalPrisms.user())
            .andThen(UserTraversals.permissions())
            .andThen(PermissionLenses.name());

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
* **Four optics, three kinds, one value.** `Lens.andThen(Prism).andThen(Traversal).andThen(Lens)` collapses into a single `Traversal<Form, String>` that you name once and reuse for reads, writes and validations.
* **`andThen` works out the result type.** Composing mixed optic kinds gives the most precise kind that covers every step, which is why the composed path is a `Traversal` rather than a `Lens`, with no conversion needed.
* **The prism is the safety.** A `Form` holding a `Guest` puts nothing in focus, so the whole pipeline returns cleanly with no branch written for the absent case.
* **`Validated` accumulates, `Either` keeps the first.** The optic never changes; only the `Applicative` handed to `modifyF` does, and that single choice is the whole difference between a full report and one message. Neither skips elements.
* **The fluent methods remove the ceremony, not the power.** `OpticOps` gives the same accumulation without `widen`, `narrow` or an explicit `Applicative` at the call site.
~~~

~~~admonish tip title="See Also"
- [Updates That Can Fail](fluent_api.md#part-2-validation-aware-modification): the four validation strategies, the builders, and when to drop to `modifyF`
- [Composition Rules](composition_rules.md): why a chain of mixed optics widens to a `Traversal`
- [Core Type Integration](core_type_integration.md): the prisms that let a core type sit mid-path
- [Production Readiness](production_readiness.md#runtime-cost): what each optic allocates, and when to cache a composed optic
~~~

~~~admonish info title="Hands-On Learning"
Practise optic composition in [Tutorial 06: Optics Composition](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/test/java/org/higherkindedj/tutorial/optics/Tutorial06_OpticsComposition.java) (7 exercises).
~~~

---

**Previous:** [Validated Prisms](validated_prism.md)
**Next:** [Optic-Driven Batching](optic_batching.md)

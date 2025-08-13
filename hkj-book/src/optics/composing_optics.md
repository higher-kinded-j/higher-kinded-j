# Capstone Example:

## _Composing Optics for Deep Validation_

~~~admonish
[ValidatedTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java)
~~~

In the previous guides, we explored each core optic—`Lens`, `Prism`, and `Traversal`—as individual tools. We've seen how they provide focused, reusable, and composable access to immutable data.

Now, it's time to put it all together.

This guide showcases the true power of the optics approach by composing multiple different optics to solve a single, complex, real-world problem: performing deep, effectful validation on a nested data structure.

---

## The Scenario: Validating User Permissions

Imagine a data model for a form that can be filled out by either a registered `User` or a `Guest`. Our goal is to validate that every `Permission` held by a `User` has a valid name.

This single task requires us to:

1. Focus on the form's `principal` field (**a job for a Lens**).
2. Safely "select" the `User` case, ignoring any `Guest`s (**a job for a Prism**).
3. Operate on every `Permission` in the user's list (**a job for a Traversal**).

### 1. The Data Model

Here is the nested data structure, annotated to generate all the optics we will need.

``` java
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

``` java
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

### 3. Composing the Master Optic

Now for the main event. We will compose our generated optics to create a single `Traversal` that declaratively represents the path from a `Form` all the way down to each permission `name`. While the new `with*` helpers are great for simple, shallow updates, a deep and conditional update like this requires composition.

To ensure type-safety across different optic types, we convert each `Lens` and `Prism` in the chain to a `Traversal` using the `.asTraversal()` method.


``` java
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

## Complete, Runnable Example

With our composed `Traversal`, we can now use `modifyF` to run our validation logic. The `Traversal` handles the navigation and filtering, while the `Validated` applicative (created with a `Semigroup` for joining error strings) handles the effects and error accumulation.


``` java
package org.higherkindedj.example.optics;

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
    @GenerateLenses public record Permission(String name) {}
    @GeneratePrisms public sealed interface Principal {}
    @GenerateLenses @GenerateTraversals public record User(String username, List<Permission> permissions) implements Principal {}
    public record Guest() implements Principal {}
    @GenerateLenses public record Form(int formId, Principal principal) {}

    // --- Validation Logic ---
    private static final Set<String> VALID_PERMISSIONS = Set.of("PERM_READ", "PERM_WRITE", "PERM_DELETE");

    public static Kind<ValidatedKind.Witness<String>, String> validatePermissionName(String name) {
        if (VALID_PERMISSIONS.contains(name)) {
            return VALIDATED.widen(Validated.valid(name));
        } else {
            return VALIDATED.widen(Validated.invalid("Invalid permission: " + name));
        }
    }

    public static void main(String[] args) {
        // --- Setup ---
        // Create an Applicative for Validated that accumulates errors by joining strings.
        Applicative<ValidatedKind.Witness<String>> validatedApplicative =
            ValidatedMonad.instance(Semigroups.string("; "));

        // Compose the master optic.
        Traversal<Form, String> formToPermissionNameTraversal =
            FormLenses.principal().asTraversal()
                .andThen(PrincipalPrisms.user().asTraversal())
                .andThen(UserTraversals.permissions())
                .andThen(PermissionLenses.name().asTraversal());

        System.out.println("--- Running Traversal Scenarios for Deep Validation ---");

        // --- Scenario 1: A form with multiple invalid permissions ---
        var multipleInvalidForm = new Form(3, new User("Charlie", List.of(
                new Permission("PERM_EXECUTE"),
                new Permission("PERM_WRITE"),
                new Permission("PERM_SUDO"))));
        System.out.println("\nInput: " + multipleInvalidForm);

        Kind<ValidatedKind.Witness<String>, Form> result = formToPermissionNameTraversal.modifyF(
                ValidatedTraversalExample::validatePermissionName, multipleInvalidForm, validatedApplicative);
      
        System.out.println("Result (errors accumulated): " + VALIDATED.narrow(result));


        // --- Scenario 2: A form with a Guest principal (no targets for traversal) ---
        var guestForm = new Form(4, new Guest());
        System.out.println("\nInput: " + guestForm);

        Kind<ValidatedKind.Witness<String>, Form> guestResult = formToPermissionNameTraversal.modifyF(
            ValidatedTraversalExample::validatePermissionName, guestForm, validatedApplicative);
      
        System.out.println("Result (path does not match): " + VALIDATED.narrow(guestResult));
    }
}
```

**Expected Output:**

```
--- Running Traversal Scenarios for Deep Validation ---

Input: Form[formId=3, principal=User[username=Charlie, permissions=[Permission[name=PERM_EXECUTE], Permission[name=PERM_WRITE], Permission[name=PERM_SUDO]]]]
Result (errors accumulated): Invalid(Invalid permission: PERM_EXECUTE; Invalid permission: PERM_SUDO)

Input: Form[formId=4, principal=Guest[]]
Result (path does not match): Valid(Form[formId=4, principal=Guest[]])
```

This shows how our single, composed optic correctly handled all cases: it accumulated multiple failures into a single `Invalid` result, and it correctly did nothing (resulting in a `Valid` state) when the path did not match. This is the power of composing simple, reusable optics to solve complex problems in a safe, declarative, and boilerplate-free way.


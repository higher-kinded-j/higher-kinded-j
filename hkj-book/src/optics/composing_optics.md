# Capstone Example: 
## _Composing Optics for Deep Validation_

~~~ admonish example title="See Example Code:"
[ValidatedTraversalExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/ValidatedTraversalExample.java)
~~~

In the previous guides, we explored each core optic—`Lens`, `Prism`, `Iso`, and `Traversal`—as individual tools in our functional toolkit. We've seen how they provide focused, reusable, and composable access to immutable data.

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

Our validation function will take a permission name (`String`) and return a `Validated<String, String>`. The `Validated` applicative functor will automatically handle accumulating any errors.

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

### 3. Composing the Master Optic

Now for the main event. We will compose our generated optics to create a single `Traversal` that declaratively represents the path from a `Form` all the way down to each permission `name`.

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

With our composed `Traversal`, we can now use `modifyF` to run our validation logic. The `Traversal` handles the navigation and filtering, while the `Validated` applicative handles the effects and error accumulation.


``` java
package org.higherkindedj.example.all;

// All necessary imports...
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.hkt.validated.ValidatedMonad;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.Traversal;
//...

public class DeepValidationExample {
    // ... (Data models and validation logic as defined above)

    public static void main(String[] args) {
        // --- Setup ---
        var validatedApplicative = ValidatedMonad.instance();
        var formToPermissionNameTraversal = createTraversal(); // Composition from above

        // --- Scenarios ---
        var validForm = new Form(1, new User("Alice", List.of(new Permission("PERM_READ"))));
        var invalidForm = new Form(2, new User("Bob", List.of(new Permission("PERM_ADMIN"))));
        var guestForm = new Form(3, new Guest());

        // --- Execution ---
        System.out.println("Running validation...");

        // Scenario 1: Success
        var result1 = formToPermissionNameTraversal.modifyF(
            DeepValidationExample::validatePermissionName, validForm, validatedApplicative
        );
        System.out.println("Valid Form Result: " + VALIDATED.narrow(result1));

        // Scenario 2: Failure
        var result2 = formToPermissionNameTraversal.modifyF(
            DeepValidationExample::validatePermissionName, invalidForm, validatedApplicative
        );
        System.out.println("Invalid Form Result: " + VALIDATED.narrow(result2));

        // Scenario 3: No targets for validation (Prism does not match)
        var result3 = formToPermissionNameTraversal.modifyF(
            DeepValidationExample::validatePermissionName, guestForm, validatedApplicative
        );
        System.out.println("Guest Form Result: " + VALIDATED.narrow(result3));
    }

    public static Traversal<Form, String> createTraversal() {
        // ... (composition logic from section 3)
    }
}
```

**Expected Output:**

```
Running validation...
Valid Form Result: Valid(value=Form[formId=1, principal=User[username=Alice, permissions=[Permission[name=PERM_READ]]]])
Invalid Form Result: Invalid(errors=NonEmptyList[Invalid permission: PERM_ADMIN])
Guest Form Result: Valid(value=Form[formId=3, principal=Guest[]])
```

This shows how our single, composed optic correctly handled all three cases: success, failure, and a path where the target didn't exist. This is the power of composing simple, reusable optics to solve complex problems in a safe, declarative, and boilerplate-free way.

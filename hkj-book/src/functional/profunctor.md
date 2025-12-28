# Profunctor: Building Adaptable Data Pipelines

~~~admonish info title="What You'll Learn"
- How to build adaptable data transformation pipelines
- The dual nature of Profunctors: contravariant inputs and covariant outputs
- Using `lmap`, `rmap`, and `dimap` to adapt functions for different contexts
- Creating flexible API adapters and validation pipelines
- Real-world applications in data format transformation and system integration
~~~

```admonish
[ProfunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/profunctor/ProfunctorExample.java)
```

So far, we've explored type classes that work with single type parameters: `Functor`, `Applicative`, and `Monad` all operate on types like `F<A>`. But what about types that take *two* parameters, like `Function<A, B>` or `Either<L, R>`? This is where **Profunctors** come in.

A **Profunctor** is a powerful abstraction for working with types that are **contravariant** in their first type parameter and **covariant** in their second. Think of it as a generalisation of how functions work: you can pre-process the input (contravariant) and post-process the output (covariant).

~~~admonish note
New to variance terminology? See the [Glossary](../glossary.md) for detailed explanations of covariant, contravariant, and invariant with Java-focused examples.
~~~

---

## What is a Profunctor?

A `Profunctor` is a type class for any type constructor `P<A, B>` that supports three key operations:

* **`lmap`**: Map over the first (input) type parameter contravariantly
* **`rmap`**: Map over the second (output) type parameter covariantly
* **`dimap`**: Map over both parameters simultaneously

The interface for `Profunctor` in `hkj-api` works with `Kind2<P, A, B>`:

```java
@NullMarked
public interface Profunctor<P extends WitnessArity<TypeArity.Binary>> {

    // Map over the input (contravariant)
    default <A, B, C> Kind2<P, C, B> lmap(
        Function<? super C, ? extends A> f, 
        Kind2<P, A, B> pab) {
        return dimap(f, Function.identity(), pab);
    }

    // Map over the output (covariant)
    default <A, B, C> Kind2<P, A, C> rmap(
        Function<? super B, ? extends C> g, 
        Kind2<P, A, B> pab) {
        return dimap(Function.identity(), g, pab);
    }

    // Map over both input and output
    <A, B, C, D> Kind2<P, C, D> dimap(
        Function<? super C, ? extends A> f,
        Function<? super B, ? extends D> g,
        Kind2<P, A, B> pab);
}
```

---

## The Canonical Example: Functions

The most intuitive example of a profunctor is the humble `Function<A, B>`. Functions are naturally:

* **Contravariant in their input**: If you have a function `String -> Integer`, you can adapt it to work with any type that can be converted *to* a `String`
* **Covariant in their output**: You can adapt the same function to produce any type that an `Integer` can be converted *to*

Let's see this in action with `FunctionProfunctor`:

```java
import static org.higherkindedj.hkt.func.FunctionKindHelper.FUNCTION;
import org.higherkindedj.hkt.func.FunctionProfunctor;

// Our original function: calculate string length
Function<String, Integer> stringLength = String::length;
Kind2<FunctionKind.Witness, String, Integer> lengthFunction = FUNCTION.widen(stringLength);

FunctionProfunctor profunctor = FunctionProfunctor.INSTANCE;

// LMAP: Adapt the input - now we can use integers!
Kind2<FunctionKind.Witness, Integer, Integer> intToLength =
    profunctor.lmap(Object::toString, lengthFunction);

Function<Integer, Integer> intLengthFunc = FUNCTION.getFunction(intToLength);
System.out.println(intLengthFunc.apply(12345)); // Output: 5

// RMAP: Adapt the output - now we get formatted strings!
Kind2<FunctionKind.Witness, String, String> lengthToString =
    profunctor.rmap(len -> "Length: " + len, lengthFunction);

Function<String, String> lengthStringFunc = FUNCTION.getFunction(lengthToString);
System.out.println(lengthStringFunc.apply("Hello")); // Output: "Length: 5"

// DIMAP: Adapt both sides simultaneously
Kind2<FunctionKind.Witness, Integer, String> fullTransform =
    profunctor.dimap(
        Object::toString,           // int -> string
        len -> "Result: " + len,    // int -> string
        lengthFunction);

Function<Integer, String> fullFunc = FUNCTION.getFunction(fullTransform);
System.out.println(fullFunc.apply(42)); // Output: "Result: 2"
```

---

## Why Profunctors Matter

Profunctors excel at creating **adaptable data transformation pipelines**. They're particularly powerful for:

### 1. **API Adapters**

When you need to integrate with external systems that expect different data formats:


```java
// Core business logic: validate a userLogin
Function<User, ValidationResult> validateUser = userLogin -> {
    boolean isValid = userLogin.email().contains("@") && !userLogin.name().isEmpty();
    return new ValidationResult(isValid, isValid ? "Valid userLogin" : "Invalid userLogin data");
};

// The API expects UserDto input and ApiResponse output
Kind2<FunctionKind.Witness, UserDto, ApiResponse<ValidationResult>> apiValidator =
    profunctor.dimap(
        // Convert UserDto -> User (contravariant)
        dto -> new User(dto.fullName(), dto.emailAddress(), 
                        LocalDate.parse(dto.birthDateString())),
        // Convert ValidationResult -> ApiResponse (covariant)  
        result -> new ApiResponse<>(result, "OK", result.isValid() ? 200 : 400),
        FUNCTION.widen(validateUser));

// Now our core logic works seamlessly with the external API format!
Function<UserDto, ApiResponse<ValidationResult>> apiFunc = FUNCTION.getFunction(apiValidator);
```

### 2. **Validation Pipelines**

Build reusable validation logic that adapts to different input and output formats:


```java
// Core validation: check if a number is positive
Function<Double, Boolean> isPositive = x -> x > 0;

// Adapt for string input with detailed error messages
Kind2<FunctionKind.Witness, String, String> stringValidator =
    profunctor.dimap(
        // Parse string to double
        str -> {
            try {
                return Double.parseDouble(str);
            } catch (NumberFormatException e) {
                return -1.0; // Invalid marker
            }
        },
        // Convert boolean to message
        isValid -> isValid ? "✓ Valid positive number" : "✗ Not a positive number",
        FUNCTION.widen(isPositive));

Function<String, String> validator = FUNCTION.getFunction(stringValidator);
System.out.println(validator.apply("42.5"));  // "✓ Valid positive number"
System.out.println(validator.apply("-10"));   // "✗ Not a positive number"
```


### 3. **Data Transformation Chains**

Chain multiple adaptations to build complex data processing pipelines:

```java
// Core transformation: User -> UserDto  
Function<User, UserDto> userToDto = userLogin ->
    new UserDto(userLogin.name(), userLogin.email(), 
                userLogin.birthDate().format(DateTimeFormatter.ISO_LOCAL_DATE));

// Build a CSV-to-JSON pipeline
Kind2<FunctionKind.Witness, String, String> csvToJsonTransform =
    profunctor.dimap(
        csvParser,    // String -> User (parse CSV)
        dtoToJson,    // UserDto -> String (serialise to JSON)
        FUNCTION.widen(userToDto));

// Add error handling with another rmap
Kind2<FunctionKind.Witness, String, ApiResponse<String>> safeTransform =
    profunctor.rmap(
        jsonString -> {
            if (jsonString.contains("INVALID")) {
                return new ApiResponse<>("", "ERROR: Invalid input data", 400);
            }
            return new ApiResponse<>(jsonString, "SUCCESS", 200);
        },
        csvToJsonTransform);
```

---

## Profunctor Laws

For a `Profunctor` to be lawful, it must satisfy two key properties:

1. **Identity**: `dimap(identity, identity, p) == p`
1. **Composition**: `dimap(f1 ∘ f2, g1 ∘ g2, p) == dimap(f2, g1, dimap(f1, g2, p))`

These laws ensure that profunctor operations are predictable and composable; you can build complex transformations by combining simpler ones without unexpected behaviour.

---

## When to Use Profunctors

Profunctors are ideal when you need to:

* **Adapt existing functions** to work with different input/output types
* **Build flexible APIs** that can handle multiple data formats
* **Create reusable transformation pipelines** that can be configured for different use cases
* **Integrate with external systems** without changing your core business logic
* **Handle both sides of a computation** (input preprocessing and output postprocessing)

The next time you find yourself writing similar functions that differ only in their input parsing or output formatting, consider whether a profunctor could help you write the logic once and adapt it as needed!

---

~~~admonish tip title="Further Reading"
- **Don't Fear the Profunctor Optics**: [Tutorial](https://github.com/hablapps/DontFearTheProfunctorOptics/blob/master/ProfunctorOptics.md) - Accessible introduction with practical examples
- **Mojang/DataFixerUpper**: [Profunctor implementation](https://github.com/Mojang/DataFixerUpper) - Minecraft's profunctor optics library in Java, used for data transformation between game versions
- **Community Documentation**: [Documented-DataFixerUpper](https://github.com/kvverti/Documented-DataFixerUpper) - Detailed explanations of Mojang's DFU library
~~~

---

**Previous:** [Selective](selective.md)
**Next:** [Bifunctor](bifunctor.md)


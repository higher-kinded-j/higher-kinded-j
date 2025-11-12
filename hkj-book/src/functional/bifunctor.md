# Bifunctor: Mapping Over Both Sides ⚖️

~~~admonish info title="What You'll Learn"
- How to transform types with two covariant parameters independently or simultaneously
- The difference between sum types (Either, Validated) and product types (Tuple2, Writer)
- Using `bimap`, `first`, and `second` operations effectively
- Transforming both error and success channels in validation scenarios
- Real-world applications in API design, data migration, and error handling
~~~

~~~admonish example title="See Example Code:"
[BifunctorExample.java](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/basic/bifunctor/BifunctorExample.java)
~~~

Whilst `Functor` lets us map over types with a single parameter like `F<A>`, many useful types have *two* parameters. `Either<L, R>`, `Tuple2<A, B>`, `Validated<E, A>`, and `Writer<W, A>` all carry two distinct types. The **`Bifunctor`** type class provides a uniform interface for transforming both parameters.

Unlike `Profunctor`, which is contravariant in the first parameter and covariant in the second (representing input/output relationships), `Bifunctor` is **covariant in both parameters**. This makes it perfect for types where both sides hold data that can be independently transformed.

---

## What is a Bifunctor?

A **`Bifunctor`** is a type class for any type constructor `F<A, B>` that supports mapping over both its type parameters. It provides three core operations:

* **`bimap`**: Transform both type parameters simultaneously
* **`first`**: Transform only the first type parameter
* **`second`**: Transform only the second type parameter

The interface for `Bifunctor` in `hkj-api` works with `Kind2<F, A, B>`:

```java
@NullMarked
public interface Bifunctor<F> {

    // Transform only the first parameter
    default <A, B, C> Kind2<F, C, B> first(
        Function<? super A, ? extends C> f,
        Kind2<F, A, B> fab) {
        return bimap(f, Function.identity(), fab);
    }

    // Transform only the second parameter
    default <A, B, D> Kind2<F, A, D> second(
        Function<? super B, ? extends D> g,
        Kind2<F, A, B> fab) {
        return bimap(Function.identity(), g, fab);
    }

    // Transform both parameters simultaneously
    <A, B, C, D> Kind2<F, C, D> bimap(
        Function<? super A, ? extends C> f,
        Function<? super B, ? extends D> g,
        Kind2<F, A, B> fab);
}
```

---

## Sum Types vs Product Types

Understanding the distinction between **sum types** and **product types** is crucial to using bifunctors effectively.

### Sum Types (Exclusive OR) 🔀

A **sum type** represents a choice between alternatives—you have *either* one value *or* another, but never both. In type theory, if type `A` has `n` possible values and type `B` has `m` possible values, then `Either<A, B>` has `n + m` possible values (hence "sum").

Examples in higher-kinded-j:
* **`Either<L, R>`**: Holds *either* a `Left` value (conventionally an error) *or* a `Right` value (conventionally a success)
* **`Validated<E, A>`**: Holds *either* an `Invalid` error *or* a `Valid` result

When you use `bimap` on a sum type, only *one* of the two functions will actually execute, depending on which variant is present.

### Product Types (Both AND) 🔗

A **product type** contains multiple values simultaneously—you have *both* the first value *and* the second value. In type theory, if type `A` has `n` possible values and type `B` has `m` possible values, then `Tuple2<A, B>` has `n × m` possible values (hence "product").

Examples in higher-kinded-j:
* **`Tuple2<A, B>`**: Holds *both* a first value *and* a second value
* **`Writer<W, A>`**: Holds *both* a log/output value *and* a computation result

When you use `bimap` on a product type, *both* functions execute because both values are always present.

---

## The Bifunctor Laws

For a `Bifunctor` to be lawful, it must satisfy two fundamental properties:

1. **Identity Law**: Mapping with identity functions changes nothing
   ```java
   bifunctor.bimap(x -> x, y -> y, fab); // Must be equivalent to fab
   ```

2. **Composition Law**: Mapping with composed functions is equivalent to mapping in sequence
   ```java
   Function<A, B> f1 = ...;
   Function<B, C> f2 = ...;
   Function<D, E> g1 = ...;
   Function<E, F> g2 = ...;

   // These must be equivalent:
   bifunctor.bimap(f2.compose(f1), g2.compose(g1), fab);
   bifunctor.bimap(f2, g2, bifunctor.bimap(f1, g1, fab));
   ```

These laws ensure that bifunctor operations are predictable, composable, and preserve the structure of your data.

---

## Why is it useful?

Bifunctors provide a uniform interface for transforming dual-parameter types, which arise frequently in functional programming. Rather than learning different APIs for transforming `Either`, `Tuple2`, `Validated`, and `Writer`, you use the same operations everywhere.

### Key Use Cases

* **Error Handling**: Transform both error and success channels simultaneously
* **API Design**: Normalise internal representations to external formats
* **Data Migration**: Convert both fields of legacy data structures
* **Validation**: Format both error messages and valid results
* **Logging**: Transform both the log output and the computation result

---

## Example 1: Either – A Sum Type

`Either<L, R>` is the quintessential sum type. It holds *either* a `Left` (conventionally an error) *or* a `Right` (conventionally a success).

```java
import static org.higherkindedj.hkt.either.EitherKindHelper.EITHER;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.hkt.either.EitherBifunctor;
import org.higherkindedj.hkt.Kind2;

Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;

// Success case: transform the Right channel
Either<String, Integer> success = Either.right(42);
Kind2<EitherKind2.Witness, String, String> formatted =
    bifunctor.second(
        n -> "Success: " + n,
        EITHER.widen2(success));

System.out.println(EITHER.narrow2(formatted));
// Output: Right(Success: 42)

// Error case: transform the Left channel
Either<String, Integer> error = Either.left("FILE_NOT_FOUND");
Kind2<EitherKind2.Witness, String, Integer> enhanced =
    bifunctor.first(
        err -> "Error Code: " + err,
        EITHER.widen2(error));

System.out.println(EITHER.narrow2(enhanced));
// Output: Left(Error Code: FILE_NOT_FOUND)

// Transform both channels with bimap
Either<String, Integer> either = Either.right(100);
Kind2<EitherKind2.Witness, Integer, String> both =
    bifunctor.bimap(
        String::length,        // Left: string -> int (not executed here)
        n -> "Value: " + n,    // Right: int -> string (executed)
        EITHER.widen2(either));

System.out.println(EITHER.narrow2(both));
// Output: Right(Value: 100)
```

**Note:** With `Either`, only one function in `bimap` executes because `Either` is a *sum type*—you have either Left *or* Right, never both.

---

## Example 2: Tuple2 – A Product Type

`Tuple2<A, B>` is a product type that holds *both* a first value *and* a second value simultaneously.

```java
import static org.higherkindedj.hkt.tuple.Tuple2KindHelper.TUPLE2;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.tuple.Tuple2;
import org.higherkindedj.hkt.tuple.Tuple2Bifunctor;

Bifunctor<Tuple2Kind2.Witness> bifunctor = Tuple2Bifunctor.INSTANCE;

// A tuple representing (name, age)
Tuple2<String, Integer> person = new Tuple2<>("Alice", 30);

// Transform only the first element
Kind2<Tuple2Kind2.Witness, Integer, Integer> nameLength =
    bifunctor.first(String::length, TUPLE2.widen2(person));

System.out.println(TUPLE2.narrow2(nameLength));
// Output: Tuple2(5, 30)

// Transform only the second element
Kind2<Tuple2Kind2.Witness, String, String> ageFormatted =
    bifunctor.second(age -> age + " years", TUPLE2.widen2(person));

System.out.println(TUPLE2.narrow2(ageFormatted));
// Output: Tuple2(Alice, 30 years)

// Transform both simultaneously with bimap
Kind2<Tuple2Kind2.Witness, String, String> formatted =
    bifunctor.bimap(
        name -> "Name: " + name,  // First: executed
        age -> "Age: " + age,      // Second: executed
        TUPLE2.widen2(person));

System.out.println(TUPLE2.narrow2(formatted));
// Output: Tuple2(Name: Alice, Age: 30)
```

**Note:** With `Tuple2`, both functions in `bimap` execute because `Tuple2` is a *product type*—both values are always present.

---

## Example 3: Validated – Error Accumulation

`Validated<E, A>` is a sum type designed for validation scenarios where you need to accumulate errors.

```java
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedBifunctor;
import java.util.List;

Bifunctor<ValidatedKind2.Witness> bifunctor = ValidatedBifunctor.INSTANCE;

// Valid case
Validated<List<String>, Integer> valid = Validated.valid(100);
Kind2<ValidatedKind2.Witness, List<String>, String> transformedValid =
    bifunctor.second(n -> "Score: " + n, VALIDATED.widen2(valid));

System.out.println(VALIDATED.narrow2(transformedValid));
// Output: Valid(Score: 100)

// Invalid case with multiple errors
Validated<List<String>, Integer> invalid =
    Validated.invalid(List.of("TOO_SMALL", "OUT_OF_RANGE"));

// Transform errors to be more user-friendly
Kind2<ValidatedKind2.Witness, String, Integer> userFriendly =
    bifunctor.first(
        errors -> "Validation failed: " + String.join(", ", errors),
        VALIDATED.widen2(invalid));

System.out.println(VALIDATED.narrow2(userFriendly));
// Output: Invalid(Validation failed: TOO_SMALL, OUT_OF_RANGE)
```

---

## Example 4: Writer – Logging with Computation

`Writer<W, A>` is a product type that holds *both* a log value *and* a computation result.

```java
import static org.higherkindedj.hkt.writer.WriterKindHelper.WRITER;
import org.higherkindedj.hkt.Bifunctor;
import org.higherkindedj.hkt.writer.Writer;
import org.higherkindedj.hkt.writer.WriterBifunctor;

Bifunctor<WriterKind2.Witness> bifunctor = WriterBifunctor.INSTANCE;

// A Writer with a log and a result
Writer<String, Integer> computation = new Writer<>("Calculated: ", 42);

// Transform the log channel
Kind2<WriterKind2.Witness, String, Integer> uppercaseLog =
    bifunctor.first(String::toUpperCase, WRITER.widen2(computation));

System.out.println(WRITER.narrow2(uppercaseLog));
// Output: Writer(CALCULATED: , 42)

// Transform both log and result
Kind2<WriterKind2.Witness, List<String>, String> structured =
    bifunctor.bimap(
        log -> List.of("[LOG]", log),   // Wrap log in structured format
        value -> "Result: " + value,     // Format the result
        WRITER.widen2(computation));

System.out.println(WRITER.narrow2(structured));
// Output: Writer([LOG], Calculated: , Result: 42)
```

---

## Real-World Scenario: API Response Transformation

One of the most common uses of bifunctors is transforming internal data representations to external API formats.

```java
// Internal representation uses simple error codes and domain objects
Either<String, UserData> internalResult = Either.left("USER_NOT_FOUND");

// External API requires structured error objects and formatted responses
Function<String, ApiError> toApiError =
    code -> new ApiError(code, "Error occurred", 404);

Function<UserData, ApiResponse> toApiResponse =
    user -> new ApiResponse(user.name(), user.email(), 200);

Bifunctor<EitherKind2.Witness> bifunctor = EitherBifunctor.INSTANCE;

Kind2<EitherKind2.Witness, ApiError, ApiResponse> apiResult =
    bifunctor.bimap(
        toApiError,     // Transform internal error to API error format
        toApiResponse,  // Transform internal data to API response format
        EITHER.widen2(internalResult));

// Result: Left(ApiError(USER_NOT_FOUND, Error occurred, 404))
```

This approach keeps your domain logic clean whilst providing flexible adaptation to external requirements.

---

## Bifunctor vs Profunctor

Whilst both type classes work with dual-parameter types, they serve different purposes:

| Feature | Bifunctor | Profunctor |
|---------|-----------|------------|
| First parameter | Covariant (output) | Contravariant (input) |
| Second parameter | Covariant (output) | Covariant (output) |
| Typical use | Data structures with two outputs | Functions and transformations |
| Examples | `Either<L, R>`, `Tuple2<A, B>` | `Function<A, B>`, optics |
| Use case | Transform both "sides" of data | Adapt input and output of pipelines |

**Use Bifunctor when:** Both parameters represent data you want to transform (errors and results, first and second elements).

**Use Profunctor when:** The first parameter represents input (contravariant) and the second represents output (covariant), like in functions.

---

## When to Use Bifunctor

Bifunctors are ideal when you need to:

* **Normalise API responses** by transforming both error and success formats
* **Migrate data schemas** by transforming both fields of legacy structures
* **Format validation results** by enhancing both error messages and valid values
* **Process paired data** like tuples, logs with results, or any product type
* **Handle sum types uniformly** by providing transformations for all variants

The power of bifunctors lies in their ability to abstract over the dual-parameter structure whilst preserving the semantics (sum vs product) of the underlying type.

---

## Summary

* **Bifunctor** provides `bimap`, `first`, and `second` for transforming dual-parameter types
* **Sum types** (Either, Validated) execute only one function based on which variant is present
* **Product types** (Tuple2, Writer) execute both functions since both values are present
* **Use cases** include API design, validation, data migration, and error handling
* **Differs from Profunctor** by being covariant in both parameters rather than contravariant/covariant

Understanding bifunctors empowers you to write generic, reusable transformation logic that works uniformly across diverse dual-parameter types.

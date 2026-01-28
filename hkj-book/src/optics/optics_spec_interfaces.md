# Taming JSON with Optics

## _Spec Interfaces for Jackson and Beyond_

> "The art of programming is the art of organising complexity, of mastering multitude and avoiding its bastard chaos."
> -- Edsger W. Dijkstra

Dijkstra's words ring especially true when facing JSON. A nested, dynamically-typed structure with optional fields, variable array contents, and no compile-time guarantees: this is complexity waiting to become chaos. The imperative approach fights this with defensive code: null checks upon null checks, type assertions, and deeply nested conditionals.

Spec interfaces offer a different path: we declare the *structure* of the complexity upfront, and the processor generates type-safe navigation tools. The chaos doesn't disappear, but we master it by describing it explicitly rather than defending against it implicitly. Each prism we declare is an assertion about what shapes the data might take; each composition is a clear statement of our navigation intent.

~~~admonish info title="What You'll Learn"
- How spec interfaces give us precise control over external types
- Building a complete optics toolkit for Jackson's `JsonNode`
- Using `@InstanceOf` prisms for type-safe JSON navigation
- Composing JSON optics into practical transformation pipelines
~~~

---

## The Jackson Challenge

Jackson's `JsonNode` is everywhere. REST APIs return it. Configuration files parse into it. Event streams carry it. And it's notoriously awkward to work with functionally.

Consider extracting and transforming data from an API response:

```java
// Typical Jackson code - imperative and defensive
JsonNode response = objectMapper.readTree(json);
if (response.has("data") && response.get("data").isObject()) {
    JsonNode data = response.get("data");
    if (data.has("users") && data.get("users").isArray()) {
        ArrayNode users = (ArrayNode) data.get("users");
        for (JsonNode user : users) {
            if (user.has("email")) {
                String email = user.get("email").asText();
                // finally, do something with it
            }
        }
    }
}
```

Every access requires null checks. Every type assumption needs validation. The actual transformation logic drowns in defensive code.

**What if we could write this instead?**

```java
// With optics - declarative and composable
var emails = JsonOptics.field("data")
    .andThen(JsonOptics.field("users"))
    .andThen(JsonOptics.elements())
    .andThen(JsonOptics.field("email"))
    .andThen(JsonOptics.textValue())
    .toListOf(response);
```

No null checks. No casting. No nested conditionals. Just a pipeline that describes *what* we want, not *how* to defensively get it.

This is what spec interfaces enable.

---

## Why JsonNode Resists Auto-Detection

When we try `@ImportOptics(JsonNode.class)`, the processor struggles. Jackson's `JsonNode` has several properties that confuse auto-detection:

1. **No sealed hierarchy** - `ObjectNode`, `ArrayNode`, `TextNode` exist as subtypes, but `JsonNode` isn't a sealed interface. The processor can't discover them automatically.

2. **No copy mechanism** - `JsonNode` is essentially immutable but has no builder pattern, withers, or convenient constructors. We create new nodes through factories.

3. **Predicate-based type checking** - Jackson uses `isObject()`, `isArray()`, `isTextual()` methods. Some APIs even use `asArray()` and `asObject()` for narrowing.

For types like this, we need to explicitly tell the processor how things work. That's what a **spec interface** does.

---

## Our First Spec Interface

A spec interface extends `OpticsSpec<S>` and declares what optics we want with annotations explaining how to generate them:

```java
package com.myapp.optics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import org.higherkindedj.optics.Prism;
import org.higherkindedj.optics.annotations.*;

@ImportOptics
public interface JsonOptics extends OpticsSpec<JsonNode> {

    @InstanceOf(ObjectNode.class)
    Prism<JsonNode, ObjectNode> object();

    @InstanceOf(ArrayNode.class)
    Prism<JsonNode, ArrayNode> array();

    @InstanceOf(TextNode.class)
    Prism<JsonNode, TextNode> text();

    @InstanceOf(NumericNode.class)
    Prism<JsonNode, NumericNode> numeric();

    @InstanceOf(BooleanNode.class)
    Prism<JsonNode, BooleanNode> bool();
}
```

The `@InstanceOf` annotation tells the processor: "Generate a prism that matches when the node is an instance of this class." The processor creates:

```java
// Generated: JsonOptics.java
public final class JsonOptics {

    public static Prism<JsonNode, ObjectNode> object() {
        return Prism.of(
            node -> node instanceof ObjectNode o ? Optional.of(o) : Optional.empty(),
            obj -> obj
        );
    }

    public static Prism<JsonNode, ArrayNode> array() {
        return Prism.of(
            node -> node instanceof ArrayNode a ? Optional.of(a) : Optional.empty(),
            arr -> arr
        );
    }

    // ... similar for text, numeric, bool
}
```

Now we have type-safe prisms that let us focus on specific JSON node types without casting or null checks.

---

## Building Richer Tools with Default Methods

The primitive prisms are useful, but real JSON work needs more. Default methods let us compose higher-level optics:

```java
@ImportOptics
public interface JsonOptics extends OpticsSpec<JsonNode> {

    // === Generated prisms ===

    @InstanceOf(ObjectNode.class)
    Prism<JsonNode, ObjectNode> object();

    @InstanceOf(ArrayNode.class)
    Prism<JsonNode, ArrayNode> array();

    @InstanceOf(TextNode.class)
    Prism<JsonNode, TextNode> text();

    @InstanceOf(NumericNode.class)
    Prism<JsonNode, NumericNode> numeric();

    // === Composed optics via default methods ===

    /**
     * Access a field on an object node.
     * Returns empty if the node isn't an object or the field doesn't exist.
     */
    default Affine<JsonNode, JsonNode> field(String name) {
        return JsonOptics.object().andThen(
            Affine.of(
                obj -> Optional.ofNullable(obj.get(name)),
                (obj, value) -> {
                    ObjectNode copy = obj.deepCopy();
                    copy.set(name, value);
                    return copy;
                }
            )
        );
    }

    /**
     * Traverse all elements of an array node.
     */
    default Traversal<JsonNode, JsonNode> elements() {
        return JsonOptics.array().andThen(
            Traversal.fromIterable(
                arr -> arr,
                (arr, elements) -> {
                    ArrayNode result = arr.arrayNode();
                    elements.forEach(result::add);
                    return result;
                }
            )
        );
    }

    /**
     * Extract the text value from a text node.
     */
    default Affine<JsonNode, String> textValue() {
        return JsonOptics.text().andThen(
            Affine.of(
                node -> Optional.of(node.asText()),
                (node, value) -> new TextNode(value)
            )
        );
    }

    /**
     * Extract the numeric value from a numeric node.
     */
    default Affine<JsonNode, Double> numericValue() {
        return JsonOptics.numeric().andThen(
            Affine.of(
                node -> Optional.of(node.doubleValue()),
                (node, value) -> DoubleNode.valueOf(value)
            )
        );
    }
}
```

Now we have a complete toolkit. The generated prisms provide the foundation; the default methods build practical tools on top.

~~~admonish warning title="Default Method Requirements"
Default methods in spec interfaces have an important requirement: **you must use explicit class-qualified references** when calling other optics from the same interface.

```java
// Correct: explicit class-qualified reference
default Affine<JsonNode, JsonNode> field(String name) {
    return JsonOptics.object().andThen(...);  // JsonOptics.object()
}

// Incorrect: unqualified reference (will not work)
default Affine<JsonNode, JsonNode> field(String name) {
    return object().andThen(...);  // object() alone won't resolve
}
```

This is because the processor generates a utility class with static methods, and it cannot copy the implementation body from your interface's default method. The generated class contains a placeholder that throws `UnsupportedOperationException` if invoked directly. Your default methods work because they call the generated static methods explicitly.
~~~

---

## A Real Pipeline: API Response Processing

Let's build something we'd actually use. Our REST API returns responses like:

```
                    ┌─────────────────────────────────────┐
                    │           JsonNode (root)           │
                    └─────────────────────────────────────┘
                                     │
                              object() prism
                                     │
                                     ▼
                    ┌─────────────────────────────────────┐
                    │  ObjectNode { "status", "data" }    │
                    └─────────────────────────────────────┘
                                     │
                              field("data")
                                     │
                                     ▼
                    ┌─────────────────────────────────────┐
                    │  ObjectNode { "users", "page" }     │
                    └─────────────────────────────────────┘
                                     │
                              field("users")
                                     │
                                     ▼
                    ┌─────────────────────────────────────┐
                    │       ArrayNode [ user, user... ]   │
                    └─────────────────────────────────────┘
                                     │
                              elements() traversal
                                     │
              ┌──────────────────────┼──────────────────────┐
              ▼                      ▼                      ▼
    ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
    │ ObjectNode {     │  │ ObjectNode {     │  │ ObjectNode {     │
    │  "name": "Alice" │  │  "name": "Bob"   │  │  "name": "Carol" │
    │  "email": "..."  │  │  "email": "..."  │  │  "email": "..."  │
    │ }                │  │ }                │  │ }                │
    └──────────────────┘  └──────────────────┘  └──────────────────┘
```

```json
{
  "status": "success",
  "data": {
    "users": [
      { "id": 1, "name": "Alice", "email": "alice@example.com", "age": 32 },
      { "id": 2, "name": "Bob", "email": "bob@example.com", "age": 28 },
      { "id": 3, "name": "Carol", "email": "carol@example.com", "age": 45 }
    ],
    "page": 1,
    "totalPages": 5
  }
}
```

Here's how optics make processing this elegant:

```java
public class UserApiClient {

    // Define paths as composable pieces
    private static final Affine<JsonNode, JsonNode> DATA =
        JsonOptics.field("data");

    private static final Affine<JsonNode, JsonNode> USERS_FIELD =
        DATA.andThen(JsonOptics.field("users"));

    private static final Traversal<JsonNode, JsonNode> EACH_USER =
        USERS_FIELD.andThen(JsonOptics.elements());

    /**
     * Extract all email addresses from the response.
     */
    public List<String> extractEmails(JsonNode response) {
        return EACH_USER
            .andThen(JsonOptics.field("email"))
            .andThen(JsonOptics.textValue())
            .toListOf(response);
    }

    /**
     * Find users over a certain age.
     */
    public List<JsonNode> findUsersOverAge(JsonNode response, int minAge) {
        return EACH_USER
            .filter(user ->
                JsonOptics.field("age")
                    .andThen(JsonOptics.numericValue())
                    .getOptional(user)
                    .map(age -> age >= minAge)
                    .orElse(false)
            )
            .toListOf(response);
    }

    /**
     * Anonymise all emails in the response.
     */
    public JsonNode anonymiseEmails(JsonNode response) {
        return EACH_USER
            .andThen(JsonOptics.field("email"))
            .andThen(JsonOptics.textValue())
            .modify(email -> maskEmail(email), response);
    }

    /**
     * Increment everyone's age by 1 (happy birthday!).
     */
    public JsonNode incrementAges(JsonNode response) {
        return EACH_USER
            .andThen(JsonOptics.field("age"))
            .andThen(JsonOptics.numericValue())
            .modify(age -> age + 1, response);
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***@" + email.substring(atIndex + 1);
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
```

Compare this to the imperative version with its nested null checks and manual iteration. The optics version:
- Reads like a description of what we want
- Composes cleanly (paths can be reused and combined)
- Handles missing/wrong-typed data gracefully (returns empty, not exceptions)

---

## Understanding @InstanceOf vs @MatchWhen

The spec interface supports two ways to define prisms for type discrimination:

### @InstanceOf - When Subtypes Are Real Classes

Use `@InstanceOf` when checking against actual Java subtypes:

```java
@InstanceOf(ObjectNode.class)
Prism<JsonNode, ObjectNode> object();
```

This generates:
```java
node instanceof ObjectNode o ? Optional.of(o) : Optional.empty()
```

For Jackson, this works because `ObjectNode`, `ArrayNode`, etc. are genuine subclasses of `JsonNode`.

### @MatchWhen - When Libraries Use Check/Extract Patterns

Some libraries don't expose subtypes directly. Instead, they provide predicate methods:

```java
// Hypothetical API
if (value.isString()) {
    String s = value.asString();  // safe after check
}
```

For these, use `@MatchWhen`:

```java
@MatchWhen(predicate = "isString", getter = "asString")
Prism<Value, String> string();
```

This generates:
```java
value.isString() ? Optional.of(value.asString()) : Optional.empty()
```

---

## Building Domain-Specific Optics

The real power emerges when we layer domain concepts on the JSON optics. Instead of thinking in terms of "field X of the array in field Y", we think in terms of our domain:

```java
public final class UserJsonOptics {

    // Low-level path
    private static final Traversal<JsonNode, JsonNode> USERS =
        JsonOptics.field("data")
            .andThen(JsonOptics.field("users"))
            .andThen(JsonOptics.elements());

    // Domain optics - these are what our code should use
    public static Traversal<JsonNode, String> userEmails() {
        return USERS
            .andThen(JsonOptics.field("email"))
            .andThen(JsonOptics.textValue());
    }

    public static Traversal<JsonNode, String> userNames() {
        return USERS
            .andThen(JsonOptics.field("name"))
            .andThen(JsonOptics.textValue());
    }

    public static Traversal<JsonNode, Double> userAges() {
        return USERS
            .andThen(JsonOptics.field("age"))
            .andThen(JsonOptics.numericValue());
    }

    public static Affine<JsonNode, Integer> pageNumber() {
        return JsonOptics.field("data")
            .andThen(JsonOptics.field("page"))
            .andThen(JsonOptics.numericValue())
            .andThen(Affine.of(
                d -> Optional.of(d.intValue()),
                (d, i) -> (double) i
            ));
    }
}
```

Now our business logic is clean:

```java
// In the service layer
List<String> emails = UserJsonOptics.userEmails().toListOf(response);
int page = UserJsonOptics.pageNumber().getOptional(response).orElse(1);

// Increment ages
JsonNode updated = UserJsonOptics.userAges().modify(age -> age + 1, response);
```

The JSON structure details are encapsulated. If the API changes its structure, we update the optics in one place.

---

## The Spec Interface Pattern

Here's the general pattern for creating optics for any external type:

```java
@ImportOptics
public interface ExternalTypeOptics extends OpticsSpec<ExternalType> {

    // 1. Primitive optics with annotations
    //    - @InstanceOf for subtype prisms
    //    - @ViaBuilder, @Wither for lenses (covered in next chapter)

    @InstanceOf(Subtype.class)
    Prism<ExternalType, Subtype> subtype();

    // 2. Composed optics as default methods
    //    - Build practical tools from the primitives

    default Affine<ExternalType, NestedType> nested() {
        return ExternalTypeOptics.subtype()
            .andThen(/* more composition */);
    }

    // 3. Domain-specific conveniences
    //    - Hide complexity, expose intent

    default Traversal<ExternalType, Element> allElements() {
        return /* composition that makes sense for our domain */;
    }
}
```

The processor handles annotated abstract methods. We compose them into useful tools via default methods.

---

## Taking It Further: EffectPath Integration

Raw optics return empty `Optional` when navigation fails, silently. For JSON processing, we often want to know *why* extraction failed: Was the field missing? Was it the wrong type? Was the array empty?

The **EffectPath API** wraps optics with explicit error tracking:

```java
import org.higherkindedj.effect.path.EffectPath;
import org.higherkindedj.effect.path.PathError;

public class ValidatedJsonExtractor {

    // Wrap our optics in EffectPath for error-aware navigation
    private static final EffectPath<JsonNode, JsonNode> DATA_PATH =
        EffectPath.fromAffine(JsonOptics.field("data"), "data");

    private static final EffectPath<JsonNode, JsonNode> USERS_PATH =
        DATA_PATH.andThen(
            EffectPath.fromAffine(JsonOptics.field("users"), "users")
        );

    /**
     * Extract emails with full error reporting.
     */
    public Either<PathError, List<String>> extractEmailsOrError(JsonNode response) {
        return USERS_PATH
            .andThen(EffectPath.fromTraversal(JsonOptics.elements(), "users[]"))
            .andThen(EffectPath.fromAffine(JsonOptics.field("email"), "email"))
            .andThen(EffectPath.fromAffine(JsonOptics.textValue(), "text"))
            .getAllOrError(response);
    }

    /**
     * Extract with validation - accumulates ALL errors, not just the first.
     */
    public Validated<List<PathError>, List<String>> validateEmails(JsonNode response) {
        return USERS_PATH
            .andThen(EffectPath.fromTraversal(JsonOptics.elements(), "users[]"))
            .andThen(EffectPath.fromAffine(JsonOptics.field("email"), "email"))
            .andThen(EffectPath.fromAffine(JsonOptics.textValue(), "text"))
            .validateAll(response);
    }
}
```

Now when extraction fails, we get actionable information:

```java
Either<PathError, List<String>> result = extractor.extractEmailsOrError(badJson);

result.fold(
    error -> log.error("Extraction failed at path '{}': {}",
        error.path(), error.message()),
    emails -> processEmails(emails)
);
```

This is particularly valuable for:
- **API response validation** - Know exactly which field is malformed
- **Configuration parsing** - Report all missing/invalid settings at once
- **Data migration** - Log which records failed and why

The EffectPath API transforms "it returned empty" into "field 'data.users[2].email' was not a text node", which is far more useful for debugging.

---

## Beyond Jackson

The same pattern works for any external type that resists auto-detection:

- **Protocol Buffers** - Use `@InstanceOf` for oneof fields
- **XML DOM** - Create prisms for element types
- **Custom AST nodes** - Navigate compiler/parser output
- **Legacy library types** - Wrap awkward APIs in clean optics

The next chapter covers **@ViaBuilder** and other copy strategies for types that need lenses, not just prisms. JOOQ's generated records are the perfect example.

---

~~~admonish tip title="Testing Our Spec Interface"
Always verify the optics satisfy the laws:
```java
@Test
void objectPrismSatisfiesLaws() {
    ObjectNode obj = mapper.createObjectNode();
    obj.put("name", "test");

    // Review law: reverseGet then getOptional returns the value
    Prism<JsonNode, ObjectNode> prism = JsonOptics.object();
    assertThat(prism.getOptional(prism.reverseGet(obj))).contains(obj);
}
```
~~~

~~~admonish info title="Key Takeaways"
* Spec interfaces give us explicit control when auto-detection fails
* `@InstanceOf` creates prisms from Java subtype relationships
* Default methods compose primitives into domain-specific tools
* The pattern separates structure (annotations) from behaviour (compositions)
~~~

---

## Making Your Jackson Integration Even Better

~~~admonish tip title="Extending the Integration"
Consider these opportunities to enhance your JSON optics:

- **Schema-driven generation**: If you have JSON Schema or OpenAPI specs, generate spec interfaces from them automatically
- **Error accumulation**: Combine with `Validated` to collect *all* extraction errors, not just the first
- **Streaming support**: For large JSON documents, create optics that work with Jackson's streaming API
- **Custom node types**: If you've extended Jackson with custom `JsonNode` subclasses, add prisms for them
- **Caching**: Frequently-used paths can be stored as static fields to avoid repeated composition
~~~

---

## Further Reading

**Jackson Documentation:**
- [Jackson Databind](https://github.com/FasterXML/jackson-databind) - Core databinding functionality
- [JsonNode Javadoc](https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/com/fasterxml/jackson/databind/JsonNode.html) - API reference

**Alternative JSON Libraries:**
The same spec interface pattern works for other JSON libraries:
- [Gson](https://github.com/google/gson) - Google's JSON library with `JsonElement` hierarchy
- [JSON-P](https://javaee.github.io/jsonp/) - Jakarta JSON Processing (standard Java EE API)
- [Moshi](https://github.com/square/moshi) - Square's modern JSON library

**Related Higher-Kinded-J Features:**
- EffectPath API - Error-aware navigation covered above
- Validated monad - Accumulating validation errors
- Either monad - Short-circuiting error handling

---

**Previous:** [Optics for External Types](importing_optics.md)
**Next:** [Database Records with JOOQ](copy_strategies.md)

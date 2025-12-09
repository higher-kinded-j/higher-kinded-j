# String Traversals: Declarative Text Processing

## _Type-Safe Text Manipulation Without Regex Complexity_

~~~admonish info title="What You'll Learn"
- Breaking strings into traversable units (characters, words, lines)
- Declarative text normalisation and validation
- Composing string traversals with filtered optics for pattern matching
- Real-world text processing: logs, CSV, configuration files
- When to use string traversals vs Stream API vs regex
- Performance characteristics and best practices
~~~

~~~admonish title="Example Code"
[StringTraversalsExample](https://github.com/higher-kinded-j/higher-kinded-j/blob/main/hkj-examples/src/main/java/org/higherkindedj/example/optics/StringTraversalsExample.java)
~~~

Working with text in Java often feels like choosing between extremes: verbose manual string manipulation with `substring()` and `indexOf()`, or cryptic regular expressions that become unmaintainable. String traversals offer a middle path: declarative, composable, and type-safe.

Consider these common scenarios from enterprise Java applications:

* **Configuration Management**: Normalising property values across `.properties` files
* **Log Analysis**: Filtering and transforming log entries line-by-line
* **Data Import**: Processing CSV files with per-field transformations
* **API Integration**: Standardising email addresses from external systems
* **Validation**: Checking character-level constraints (length, allowed characters)

The traditional approach mixes parsing logic with transformation logic, making code difficult to test and reuse:

```java
// Traditional: Mixed concerns, hard to compose
String normaliseEmail(String email) {
    String[] parts = email.toLowerCase().split("@");
    if (parts.length != 2) throw new IllegalArgumentException();
    String domain = parts[1].trim();
    return parts[0] + "@" + domain;
}

// What if we need to normalise just the domain? Or multiple emails in a document?
// We'd need separate methods or complex parameters.
```

**String traversals** let you separate the "what" (the structure) from the "how" (the transformation), making your text processing logic reusable and composable.

---

## Think of String Traversals Like...

* **Java Stream's split() + map()**: Like `text.lines().map(...)` but integrated into optic composition
* **IntelliJ's "Replace in Selection"**: Focus on text units, transform them, reassemble automatically
* **Unix text tools**: Similar to `awk` and `sed` pipelines, but type-safe and composable
* **SQL's string functions**: Like `UPPER()`, `TRIM()`, `SPLIT_PART()`, but for immutable Java strings

The key insight: text structure (characters, words, lines) becomes part of your optic's identity, not preprocessing before the real work.

---

## Three Ways to Decompose Text

The `StringTraversals` utility class provides three fundamental decompositions:

| Method | Unit | Example Input | Focused Elements |
|--------|------|---------------|------------------|
| **`chars()`** | Characters | `"hello"` | `['h', 'e', 'l', 'l', 'o']` |
| **`worded()`** | Words (by `\s+`) | `"hello world"` | `["hello", "world"]` |
| **`lined()`** | Lines (by `\n`, `\r\n`, `\r`) | `"line1\nline2"` | `["line1", "line2"]` |

Each returns a `Traversal<String, ?>` that can be composed with other optics and applied via `Traversals.modify()` or `Traversals.getAll()`.

---

## A Step-by-Step Walkthrough

### Step 1: Character-Level Processing with `chars()`

The `chars()` traversal breaks a string into individual characters, allowing transformations at the finest granularity.

```java
import org.higherkindedj.optics.util.StringTraversals;
import org.higherkindedj.optics.util.Traversals;

// Create a character traversal
Traversal<String, Character> charTraversal = StringTraversals.chars();

// Transform all characters to uppercase
String uppercased = Traversals.modify(charTraversal, Character::toUpperCase, "hello world");
// Result: "HELLO WORLD"

// Extract all characters as a list
List<Character> chars = Traversals.getAll(charTraversal, "abc");
// Result: ['a', 'b', 'c']

// Compose with filtered for selective transformation
Traversal<String, Character> vowels = charTraversal.filtered(c ->
    "aeiouAEIOU".indexOf(c) >= 0
);
String result = Traversals.modify(vowels, Character::toUpperCase, "hello world");
// Result: "hEllO wOrld"  (only vowels uppercased)
```

**Use Cases**:
- Character-level validation (alphanumeric checks)
- ROT13 or Caesar cipher transformations
- Character frequency analysis
- Removing or replacing specific characters

### Step 2: Word-Level Processing with `worded()`

The `worded()` traversal splits by whitespace (`\s+`), focusing on each word independently.

**Key Semantics**:
- Multiple consecutive spaces are normalised to single spaces
- Leading and trailing whitespace is removed
- Empty strings or whitespace-only strings produce no words

```java
Traversal<String, String> wordTraversal = StringTraversals.worded();

// Capitalise each word
String capitalised = Traversals.modify(
    wordTraversal,
    word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase(),
    "hello WORLD from JAVA"
);
// Result: "Hello World From Java"

// Extract all words (whitespace normalisation automatic)
List<String> words = Traversals.getAll(wordTraversal, "foo  bar\t\tbaz");
// Result: ["foo", "bar", "baz"]

// Compose with filtered for conditional transformation
Traversal<String, String> longWords = wordTraversal.filtered(w -> w.length() > 5);
String emphasised = Traversals.modify(longWords, w -> w.toUpperCase(), "make software better");
// Result: "make SOFTWARE BETTER"
```

**Use Cases**:
- Title case formatting
- Stop word filtering
- Word-based text normalisation
- Search query processing
- Email domain extraction

### Step 3: Line-Level Processing with `lined()`

The `lined()` traversal splits by line separators (`\n`, `\r\n`, or `\r`), treating each line as a focus target.

**Key Semantics**:
- All line endings are normalised to `\n` in output
- Empty strings produce no lines
- Trailing newlines are preserved in individual line processing

```java
Traversal<String, String> lineTraversal = StringTraversals.lined();

// Prefix each line with a marker
String prefixed = Traversals.modify(
    lineTraversal,
    line -> "> " + line,
    "line1\nline2\nline3"
);
// Result: "> line1\n> line2\n> line3"

// Extract all non-empty lines
List<String> lines = Traversals.getAll(lineTraversal, "first\n\nthird");
// Result: ["first", "", "third"]  (empty line preserved)

// Filter lines by content
Traversal<String, String> errorLines = lineTraversal.filtered(line ->
    line.contains("ERROR")
);
String errors = Traversals.getAll(errorLines, logContent).stream()
    .collect(Collectors.joining("\n"));
```

**Use Cases**:
- Log file filtering and transformation
- CSV row processing
- Configuration file parsing
- Code formatting (indentation, comments)
- Multi-line text validation

---

## Real-World Example: Email Normalisation Service

A common requirement in enterprise systems: normalise email addresses from various sources before storage.

```java
import org.higherkindedj.optics.util.StringTraversals;
import org.higherkindedj.optics.util.Traversals;

public class EmailNormaliser {

    // Normalise the local part (before @) to lowercase
    // Normalise the domain (after @) to lowercase and trim
    public static String normalise(String email) {
        Traversal<String, String> words = StringTraversals.worded();

        // Split email by @ symbol (treated as whitespace separator for this example)
        // In production, you'd want more robust parsing
        String lowercased = Traversals.modify(words, String::toLowerCase, email);

        return lowercased.trim();
    }

    // More sophisticated: normalise domain parts separately
    public static String normaliseDomain(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex == -1) return email;

        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);

        // Normalise domain components
        Traversal<String, String> domainParts = StringTraversals.worded();
        String normalisedDomain = Traversals.modify(
            domainParts,
            String::toLowerCase,
            domain.replace(".", " ")  // Split domain by dots
        ).replace(" ", ".");  // Rejoin

        return local + "@" + normalisedDomain;
    }
}
```

---

## Composing String Traversals

The power emerges when combining string traversals with other optics:

### With Filtered Traversals – Pattern Matching

```java
// Find and transform lines starting with a prefix
Traversal<String, String> commentLines =
    StringTraversals.lined().filtered(line -> line.trim().startsWith("#"));

String withoutComments = Traversals.modify(
    commentLines,
    line -> "",  // Remove comment lines by replacing with empty
    sourceCode
);
```

### With Nested Structures – Bulk Text Processing

```java
@GenerateLenses
public record Document(String title, List<String> paragraphs) {}

// Capitalise first letter of each word in all paragraphs
Traversal<Document, String> allWords =
    DocumentLenses.paragraphs().asTraversal()
        .andThen(Traversals.forList())
        .andThen(StringTraversals.worded());

Document formatted = Traversals.modify(
    allWords,
    word -> word.substring(0, 1).toUpperCase() + word.substring(1),
    document
);
```

### With Effectful Operations – Validation

```java
import org.higherkindedj.hkt.optional.OptionalMonad;

// Validate that all words are alphanumeric
Traversal<String, String> words = StringTraversals.worded();

Function<String, Kind<OptionalKind.Witness, String>> validateWord = word -> {
    boolean alphanumeric = word.chars().allMatch(Character::isLetterOrDigit);
    return alphanumeric
        ? OptionalKindHelper.OPTIONAL.widen(Optional.of(word))
        : OptionalKindHelper.OPTIONAL.widen(Optional.empty());
};

Optional<String> validated = OptionalKindHelper.OPTIONAL.narrow(
    words.modifyF(validateWord, input, OptionalMonad.INSTANCE)
);
// Returns Optional.empty() if any word contains non-alphanumeric characters
```

---

## Common Patterns

### Log File Processing

```java
// Extract ERROR lines from application logs
Traversal<String, String> errorLines =
    StringTraversals.lined().filtered(line -> line.contains("ERROR"));

List<String> errors = Traversals.getAll(errorLines, logContent);

// Add timestamps to each line
Traversal<String, String> allLines = StringTraversals.lined();
String timestamped = Traversals.modify(
    allLines,
    line -> LocalDateTime.now() + " " + line,
    originalLog
);
```

### CSV Processing

```java
// Process CSV by splitting into lines, then cells
Traversal<String, String> rows = StringTraversals.lined();
Traversal<String, String> cells = StringTraversals.worded();  // Simplified; use split(",") in production

// Transform specific column (e.g., third column to uppercase)
String processedCsv = Traversals.modify(
    rows,
    row -> {
        List<String> parts = List.of(row.split(","));
        if (parts.size() > 2) {
            List<String> modified = new ArrayList<>(parts);
            modified.set(2, parts.get(2).toUpperCase());
            return String.join(",", modified);
        }
        return row;
    },
    csvContent
);
```

### Configuration File Normalisation

```java
// Trim all property values in .properties format
Traversal<String, String> propertyLines = StringTraversals.lined();

String normalised = Traversals.modify(
    propertyLines,
    line -> {
        if (line.contains("=")) {
            String[] parts = line.split("=", 2);
            return parts[0].trim() + "=" + parts[1].trim();
        }
        return line;
    },
    propertiesContent
);
```

---

## When to Use String Traversals vs Other Approaches

### Use String Traversals When:

* **Reusable text transformations** - Define once, apply across multiple strings
* **Composable pipelines** - Building complex optic chains with lenses and prisms
* **Type-safe operations** - Character/word/line transformations with compile-time safety
* **Immutable updates** - Transforming text whilst keeping data immutable
* **Declarative intent** - Express "what" without "how" (no manual indexing)

```java
// Perfect: Reusable, composable, declarative
Traversal<Config, String> allPropertyValues =
    ConfigLenses.properties().asTraversal()
        .andThen(StringTraversals.lined())
        .andThen(StringTraversals.worded());

Config trimmed = Traversals.modify(allPropertyValues, String::trim, config);
```

### Use Stream API When:

* **Complex filtering** - Multiple conditions with short-circuiting
* **Aggregations** - Counting, collecting to new structures
* **No structural preservation needed** - Extracting data, not updating in place
* **One-time operations** - Not reused across different contexts

```java
// Better with streams: Complex aggregation
long wordCount = text.lines()
    .flatMap(line -> Arrays.stream(line.split("\\s+")))
    .filter(word -> word.length() > 5)
    .count();
```

### Use Regular Expressions When:

* **Complex pattern matching** - Extracting structured data (emails, URLs, dates)
* **Search and replace** - Simple find-and-replace operations
* **Validation** - Checking format compliance (phone numbers, postal codes)

```java
// Sometimes regex is clearest
Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
Matcher matcher = emailPattern.matcher(text);
while (matcher.find()) {
    processEmail(matcher.group());
}
```

---

## Common Pitfalls

### Don't Do This:

```java
// Inefficient: Creating traversals in loops
for (String paragraph : document.paragraphs()) {
    Traversal<String, String> words = StringTraversals.worded();
    Traversals.modify(words, String::toUpperCase, paragraph);
}

// Over-engineering: Using traversals for simple operations
Traversal<String, Character> chars = StringTraversals.chars();
String upper = Traversals.modify(chars, Character::toUpperCase, "hello");
// Just use: "hello".toUpperCase()

// Wrong expectation: Thinking it changes string length
Traversal<String, Character> chars = StringTraversals.chars();
String result = Traversals.modify(chars.filtered(c -> c != 'a'), c -> c, "banana");
// Result: "banana" (still 6 chars, 'a' positions unchanged)
// Filtered traversals preserve structure!
```

### Do This Instead:

```java
// Efficient: Create traversal once, reuse
Traversal<String, String> words = StringTraversals.worded();
List<String> processed = document.paragraphs().stream()
    .map(p -> Traversals.modify(words, String::toUpperCase, p))
    .collect(toList());

// Right tool: Use built-in methods for simple cases
String upper = text.toUpperCase();  // Simple and clear

// Correct expectation: Use getAll for extraction
Traversal<String, Character> vowels = StringTraversals.chars()
    .filtered(c -> "aeiou".indexOf(c) >= 0);
List<Character> extracted = Traversals.getAll(vowels, "banana");
// Result: ['a', 'a', 'a'] - extracts vowels without changing structure
```

---

## Performance Notes

String traversals are optimised for immutability:

* **Single pass**: Text is decomposed and reconstructed in one traversal
* **No intermediate strings**: Operates on character/word lists internally
* **Structural sharing**: For filtered operations, unchanged portions reference original
* **Lazy bounds checking**: Minimal overhead for validation

**Best Practice**: For frequently used string transformations, create traversals as constants:

```java
public class TextProcessing {
    // Reusable string traversals
    public static final Traversal<String, String> WORDS =
        StringTraversals.worded();

    public static final Traversal<String, String> LINES =
        StringTraversals.lined();

    public static final Traversal<String, Character> VOWELS =
        StringTraversals.chars().filtered(c -> "aeiouAEIOU".indexOf(c) >= 0);

    // Domain-specific compositions
    public static final Traversal<String, String> ERROR_LOG_LINES =
        LINES.filtered(line -> line.contains("ERROR"));
}
```

---

## Integration with Functional Java Ecosystem

String traversals complement existing functional libraries:

### Cyclops Integration

```java
import cyclops.control.Validated;

// Validate each word using Cyclops Validated
Traversal<String, String> words = StringTraversals.worded();

Function<String, Kind<ValidatedKind.Witness<List<String>>, String>> validateLength =
    word -> word.length() <= 10
        ? VALIDATED.widen(Validated.valid(word))
        : VALIDATED.widen(Validated.invalid(List.of("Word too long: " + word)));

Validated<List<String>, String> result = VALIDATED.narrow(
    words.modifyF(validateLength, input, validatedApplicative)
);
```

---

## Related Resources

**Functional Java Libraries**:
- [Cyclops](https://github.com/aol/cyclops) - Functional control structures and higher-kinded types
- [jOOλ](https://github.com/jOOQ/jOOL) - Functional utilities complementing Java Streams

**Further Reading**:
- *Functional Programming in Java* by Venkat Subramaniam - Practical FP patterns
- *Modern Java in Action* by Raoul-Gabriel Urma - Streams, lambdas, and functional style
- [Optics By Example](https://leanpub.com/optics-by-example) by Chris Penner - Haskell optics comprehensive guide

**Comparison with Other Languages**:
- Haskell's [`Data.Text`](https://hackage.haskell.org/package/text-2.0.2/docs/Data-Text.html) - Similar text processing with optics
- Scala's [Monocle](https://www.optics.dev/Monocle/) - String traversals via `Traversal[String, Char]`

---

**Previous:** [Limiting Traversals: Focusing on List Portions](limiting_traversals.md)
**Next:** [Common Data Structure Traversals](common_data_structure_traversals.md)

// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Holds a book example's output comments to what the example actually prints.
 *
 * <p>The pages say their code is "compiled and run by the build", and the style guide prefers a
 * result shown as a trailing comment inside an include, on the grounds that the build proves it.
 * Compilation alone proves no such thing: a comment is not compiled, so a value that changes shape
 * leaves the page quoting something the program never printed. One had already drifted when this
 * test was written, an {@code EmailAddress} record printing as {@code EmailAddress[value=...]}
 * where the page still showed the bare address.
 *
 * <p>So each example with a {@code main} is run, its output captured, and every output comment on a
 * line of its own inside an {@code ANCHOR} block matched against it, in source order: each claim
 * consumes one printed value, and the next resumes after it, so a value printed once cannot answer
 * two claims. A comment at the end of a code line is not read. A claim shows the value of the
 * statement above it, so the example should print that same value, not compute it again: bind it to
 * a variable in the region, and print the variable after it. The shapes the examples use are all
 * honoured:
 *
 * <ul>
 *   <li>a value on one line, <code>// Valid(Person[name=Ada, age=36])</code>, or any other type's
 *       own rendering, <code>// Both(...)</code>, or a bare list, <code>// [45.0]</code>;
 *   <li>a number, a boolean or <code>Nothing</code> on a line of its own;
 *   <li>a value wrapped over several comment lines, joined here before matching;
 *   <li>an abbreviated value, where <code>...</code> stands for elided detail and matches anything.
 * </ul>
 *
 * <p>Trailing commentary is not part of the claim: an inline <code>&lt;- note</code> and anything
 * after the value's closing bracket are dropped, and whitespace is ignored, so re-wrapping a long
 * line cannot fail the build. What must hold is the value itself.
 */
@DisplayName("a book example prints what its comments claim")
class BookExampleOutputTest {

  /** The book's runnable examples, as source, so the comments can be read. */
  private static final Path EXAMPLES = Path.of(required("hkj.examples.dir"));

  /**
   * A comment line that opens a printed value: a type's own rendering, {@code Valid(...)}, {@code
   * Both(...)} or a record's {@code Name[...]}, or a bare list, {@code [45.0]}. A comment that is
   * neither this nor a {@link #SCALAR} is explanation.
   */
  private static final Pattern OPENS_A_VALUE = Pattern.compile("^(\\[|[A-Z][A-Za-z0-9_]*[(\\[]).*");

  /**
   * A printed value with no brackets to close, claimed on its own line: a number, a boolean, or a
   * {@code Maybe}'s {@code Nothing}.
   */
  private static final Pattern SCALAR = Pattern.compile("^(-?\\d+(\\.\\d+)?|true|false|Nothing)$");

  /** The line that opens a region the book includes, and the one that closes it, with its name. */
  private static final Pattern ANCHOR_START = Pattern.compile("ANCHOR:\\s*(\\S+)");

  private static final Pattern ANCHOR_END = Pattern.compile("ANCHOR_END:\\s*(\\S+)");

  /**
   * One example that can be run, the values its comments promise, any comment that opened a value
   * and never closed it, and the claims whose value the example is not shown printing. The unclosed
   * and unbound ones are carried rather than thrown here, so they fail against the example they
   * belong to instead of breaking test discovery for all of them.
   */
  record Example(String className, List<Claim> claims, Claim unclosed, List<Unbound> unbound) {
    @Override
    public String toString() {
      return className.substring(className.lastIndexOf('.') + 1)
          + " ("
          + claims.size()
          + " output comments)";
    }
  }

  /** One output comment: where it is, and the value it claims the example prints. */
  record Claim(int line, String value) {}

  /** A claim whose value the example is not shown printing, and why. */
  record Unbound(Claim claim, String reason) {}

  static Stream<Example> examples() {
    try (Stream<Path> sources = Files.walk(EXAMPLES)) {
      List<Example> found =
          sources
              .filter(path -> path.toString().endsWith(".java"))
              .map(BookExampleOutputTest::exampleOf)
              .filter(java.util.Objects::nonNull)
              .sorted(java.util.Comparator.comparing(Example::className))
              .toList();
      assertThat(found)
          .as("book examples with a main(), under " + EXAMPLES)
          .hasSizeGreaterThanOrEqualTo(MINIMUM_RUNNABLE_EXAMPLES);
      return found.stream();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * How many output comments the gate must read from each example. A parser that reads fewer passes
   * every value it skips, and a single total would let one example's comments cover for another's,
   * so each example has a floor of its own, and an example that gains claims gains a floor.
   */
  private static final Map<String, Integer> MINIMUM_CLAIMS =
      Map.ofEntries(
          Map.entry("AbsenceBook", 4),
          Map.entry("BasicsBook", 4),
          Map.entry("BeansBook", 6),
          Map.entry("BoundaryCapstoneBook", 4),
          Map.entry("EitherOrBothBook", 1),
          Map.entry("EitherOrBothPathBook", 6),
          Map.entry("GenericsBook", 4),
          Map.entry("JsonApiBook", 4),
          Map.entry("MergeBook", 2),
          Map.entry("MultiEditBook", 2),
          Map.entry("NonEmptyListBook", 3),
          Map.entry("SparsePatchBook", 5),
          Map.entry("StandardCodecsBook", 2),
          Map.entry("StructureBook", 11),
          Map.entry("TiersBook", 2),
          Map.entry("ValidatedAssemblyBook", 3));

  @Test
  @DisplayName("every claims floor names an example that exists")
  void everyFloorNamesAnExample() {
    assertThat(examples().map(example -> simpleName(example.className())).toList())
        .as("a floor whose example was renamed or removed holds nothing; move or drop it")
        .containsAll(MINIMUM_CLAIMS.keySet());
  }

  @Test
  @DisplayName("reads each claim on a line of its own, after its code, in every open anchor")
  void readsTheClaimsInAnAnchor() {
    List<Claim> claims = new ArrayList<>();
    Claim unclosed =
        claimsIn(
            """
            // ANCHOR: outer
            mapping.parse(dto);

            // Invalid(NonEmptyList[email: not an email address])
            // A comment that explains is not a claim.
            // ANCHOR: inner
            total.get(); // 2.0 at the end of a code line is not read
            // 1.0
            // ANCHOR_END: inner
            list.get();
            // [a,
            //  b]
            // ANCHOR_END: outer
            // Valid(outside any anchor, so not a claim)
            """,
            claims);

    assertThat(unclosed).isNull();
    assertThat(claims)
        .extracting(Claim::line, Claim::value)
        .containsExactly(
            tuple(4, "Invalid(NonEmptyList[email: not an email address])"),
            tuple(8, "1.0"),
            tuple(11, "[a, b]"));
  }

  @Test
  @DisplayName("matches claims in order, each consuming one printed value")
  void eachClaimConsumesOnePrintedValue() {
    List<String> printed = List.of("Right(0) / Both(NonEmptyList[deprecated], 42)", "page : 1.0");

    assertThat(
            firstUnprinted(
                claims("Right(0)", "Both(NonEmptyList[deprecated], 42)", "1.0"), printed))
        .isNull();
    assertThat(firstUnprinted(claims("Right(0) / Both(NonEmptyList[deprecated], 42)"), printed))
        .isNull();
    assertThat(firstUnprinted(claims("Both(NonEmptyList[deprecated], 42)", "Right(0)"), printed))
        .extracting(Claim::line)
        .isEqualTo(2);
    assertThat(firstUnprinted(claims("1.0", "1.0"), printed)).extracting(Claim::line).isEqualTo(2);
  }

  @Test
  @DisplayName("requires each claim to follow a statement that binds a value the example prints")
  void requiresEachClaimToFollowAPrintedBinding() {
    String text =
        """
        // ANCHOR: demo
        Validated<NonEmptyList<FieldError>, Customer> parsed =
            mapping.parse(dto);
        // Invalid(NonEmptyList[email: not an email address])
        int other = 1;
        mapping.parse(other);
        // Valid(Customer[name=Ada])
        String base = "https://api.example.org"; // a note
        client.get(base);
        // Valid(Response[ok])
        @SuppressWarnings("unused")
        int value = result.getOrElse(0); // bound, with a note

        // the default, when nothing is set
        // 8
        // Valid(Second[claim])
        var ghost = mapping.parse(dto);
        // Invalid(NonEmptyList[ghost: never printed])
        // ANCHOR_END: demo
        System.out.println(parsed + " / " + value + " / " + other + " / " + base);
        """;
    List<Claim> claims = new ArrayList<>();
    claimsIn(text, claims);

    assertThat(unboundClaims(text, claims))
        .extracting(unbound -> unbound.claim().line())
        .containsExactly(7, 10, 16, 18);
  }

  private static List<Claim> claims(String... values) {
    List<Claim> claims = new ArrayList<>();
    for (int i = 0; i < values.length; i++) claims.add(new Claim(i + 1, values[i]));
    return claims;
  }

  /**
   * The number of runnable examples must never fall below this. Deleting a {@code main}, or moving
   * an example out of the book package, would otherwise quietly shrink what this gate covers.
   */
  private static final int MINIMUM_RUNNABLE_EXAMPLES = 18;

  private static Example exampleOf(Path source) {
    String text = read(source);
    if (!text.contains("public static void main(")) return null;
    String className = classNameOf(source);
    List<Claim> claims = new ArrayList<>();
    Claim unclosed = claimsIn(text, claims);
    return new Example(className, claims, unclosed, unboundClaims(text, claims));
  }

  /** The fully qualified name, taken from the file's own package declaration. */
  private static String classNameOf(Path source) {
    String text = read(source);
    int start = text.indexOf("package ");
    int end = text.indexOf(';', start);
    String pkg = text.substring(start + "package ".length(), end).trim();
    String simple = source.getFileName().toString().replace(".java", "");
    return pkg + "." + simple;
  }

  /**
   * Every value an anchor's comments promise, in source order.
   *
   * <p>A value that opens and never closes is a failure, not something to pass over: a missing
   * bracket, or a stray one in a message, would otherwise drop the claim silently and leave the
   * page quoting an output nothing checks.
   *
   * <p>A claim can sit anywhere an anchor is open, nested anchors included, and only an anchor line
   * opens or closes one. A code line inside ends a value that was wrapping, and nothing more: an
   * output comment follows the code that prints it.
   */
  static Claim claimsIn(String text, List<Claim> claims) {
    Claim unclosed = null;
    Set<String> open = new HashSet<>();
    int startedAt = 0;
    StringBuilder pending = null;

    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      Matcher ends = ANCHOR_END.matcher(line);
      Matcher starts = ANCHOR_START.matcher(line);
      boolean anchorEnds = ends.find();
      boolean anchorStarts = !anchorEnds && starts.find();
      String comment = !open.isEmpty() && !anchorStarts && !anchorEnds ? commentBody(line) : null;

      if (anchorStarts || anchorEnds || comment == null) {
        if (pending != null && unclosed == null)
          unclosed = new Claim(startedAt, pending.toString());
        pending = null;
        if (anchorStarts) open.add(starts.group(1));
        if (anchorEnds) open.remove(ends.group(1));
        continue;
      }
      if (pending != null) {
        pending.append(' ').append(comment);
      } else if (SCALAR.matcher(comment).matches()) {
        claims.add(new Claim(i + 1, comment));
        continue;
      } else if (OPENS_A_VALUE.matcher(comment).matches()) {
        pending = new StringBuilder(comment);
        startedAt = i + 1;
      } else {
        continue;
      }
      String value = balancedPrefix(pending.toString());
      if (value != null) {
        claims.add(new Claim(startedAt, value));
        pending = null;
      }
    }
    if (pending != null && unclosed == null) unclosed = new Claim(startedAt, pending.toString());
    return unclosed;
  }

  /**
   * The first line of a statement that binds its value to a variable, {@code Validated<...> parsed
   * =}, {@code int value =}, {@code final var x =}, with any annotations before it. The variable's
   * name is captured.
   */
  private static final Pattern DECLARATION =
      Pattern.compile(
          "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:final\\s+)?[A-Za-z_][\\w.]*(?:<.*>)?(?:\\[])*"
              + "\\s+([a-z_]\\w*)\\s*=");

  /** A line of nothing but annotations, which belongs to the declaration below it. */
  private static final Pattern ANNOTATIONS =
      Pattern.compile("^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s*)+$");

  /**
   * A line that ends a statement: a {@code ;}, {@code {} or {@code }}, then at most a comment. A
   * {@code //} inside a string literal does not end the code before it.
   */
  private static final Pattern ENDS_A_STATEMENT =
      Pattern.compile("[;{}]\\s*(?://[^\"]*|/\\*.*\\*/)?\\s*$");

  /**
   * The claims the example is not shown printing. A claim shows the value of the statement above
   * it, and the gate checks only what the example prints, so the statement must bind its value to a
   * variable, and a print after the region must show that variable. A bare expression, computed
   * again for a print, is a second computation the page does not show; and one statement supports
   * one claim, since each claim consumes one printed value.
   */
  static List<Unbound> unboundClaims(String text, List<Claim> claims) {
    String[] lines = text.split("\n", -1);
    Set<Integer> starts = new HashSet<>();
    for (Claim claim : claims) starts.add(claim.line() - 1);
    List<Unbound> unbound = new ArrayList<>();
    for (Claim claim : claims) {
      String reason = unboundReason(lines, claim.line() - 1, starts);
      if (reason != null) unbound.add(new Unbound(claim, reason));
    }
    return unbound;
  }

  private static String unboundReason(String[] lines, int claimIndex, Set<Integer> claimStarts) {
    int end = claimIndex - 1;
    while (end >= 0 && isCommentOrBlank(lines[end])) {
      if (claimStarts.contains(end)) {
        return "the claim at line %d already shows this statement's value".formatted(end + 1);
      }
      end--;
    }
    if (end < 0) return "no statement comes before it";
    int start = end;
    while (start > 0
        && !isCommentOrBlank(lines[start - 1])
        && !ENDS_A_STATEMENT.matcher(lines[start - 1]).find()) {
      start--;
    }
    while (start < end && ANNOTATIONS.matcher(lines[start]).matches()) start++;
    Matcher declaration = DECLARATION.matcher(lines[start]);
    if (!declaration.find()) {
      return "the statement at line %d binds no variable: %s"
          .formatted(start + 1, lines[start].strip());
    }
    String variable = declaration.group(1);
    String after = String.join("\n", List.of(lines).subList(claimIndex, lines.length));
    Pattern printed =
        Pattern.compile(
            "System\\.out\\.print\\w*\\([^;]*\\b" + Pattern.quote(variable) + "\\b",
            Pattern.DOTALL);
    if (!printed.matcher(after).find()) {
      return "line %d binds %s, but no print after it shows %s"
          .formatted(start + 1, variable, variable);
    }
    return null;
  }

  private static boolean isCommentOrBlank(String line) {
    String trimmed = line.strip();
    return trimmed.isEmpty() || trimmed.startsWith("//");
  }

  /** The text of a {@code //} comment, with any inline {@code <- note} dropped. */
  private static String commentBody(String line) {
    String trimmed = line.strip();
    if (!trimmed.startsWith("//")) return null;
    return withoutNote(trimmed.substring(2).strip());
  }

  private static String withoutNote(String text) {
    int note = text.indexOf("<-");
    return (note > 0 ? text.substring(0, note) : text).strip();
  }

  /**
   * The value at the start of the text, ending where its first bracket closes. Anything after that
   * is the author talking to the reader, and a value still open at the end of the line is one that
   * wraps onto the next.
   */
  private static String balancedPrefix(String text) {
    int depth = 0;
    boolean opened = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(' || c == '[' || c == '{') {
        depth++;
        opened = true;
      } else if (c == ')' || c == ']' || c == '}') {
        depth--;
        if (opened && depth == 0) return text.substring(0, i + 1);
      }
    }
    return null;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("examples")
  @DisplayName("prints every value its output comments promise")
  void printsWhatItsCommentsClaim(Example example) {
    if (!example.unbound().isEmpty()) {
      fail(
          """
          %s claims values it is not shown printing:
          %s
          A claim shows the value of the statement above it. Bind that value in the region, \
          `Type name = ...;`, one claim per statement, and print `name` after the region."""
              .formatted(
                  example.className(),
                  example.unbound().stream()
                      .map(u -> "  line %d: %s".formatted(u.claim().line(), u.reason()))
                      .collect(java.util.stream.Collectors.joining("\n"))));
    }
    if (example.unclosed() != null) {
      fail(
          """
          %s:%d opens an output value that never closes, so nothing checks it.

            the comment reads: %s
          Close the brackets, or write the line as prose rather than as an output."""
              .formatted(
                  example.className(), example.unclosed().line(), example.unclosed().value()));
    }

    String name = simpleName(example.className());
    assertThat(example.claims().isEmpty() || MINIMUM_CLAIMS.containsKey(name))
        .as("%s has output comments and no floor in MINIMUM_CLAIMS: add one", name)
        .isTrue();
    assertThat(example.claims())
        .as("the output comments read from %s fell below its floor", name)
        .hasSizeGreaterThanOrEqualTo(MINIMUM_CLAIMS.getOrDefault(name, 0));

    List<String> printed = run(example.className());
    Claim unprinted = firstUnprinted(example.claims(), printed);
    if (unprinted != null) {
      fail(
          """
          %s:%d claims an output the example does not print, after the values the claims before \
          it matched.

            the comment says: %s
            the example printed:
          %s
          Update the comment to what the program prints, or fix the example. If the line is \
          prose rather than an output, reword it so it does not open with a value."""
              .formatted(
                  example.className(),
                  unprinted.line(),
                  unprinted.value(),
                  printed.stream()
                      .map(line -> "    " + line)
                      .collect(java.util.stream.Collectors.joining("\n"))));
    }
  }

  /** Where the next claim may resume: a printed line, and the {@code " / "} part within it. */
  private record Position(int line, int part) {}

  /**
   * The first claim the printed lines do not bear out, or null when every one is printed. Claims
   * match in source order, and each consumes one printed value, a whole line or one of the values
   * it joins with {@code " / "}, so the next resumes after it: a value printed once cannot answer
   * two claims, and two values on one line cannot be claimed in the reverse order.
   */
  static Claim firstUnprinted(List<Claim> claims, List<String> printed) {
    Position from = new Position(0, 0);
    for (Claim claim : claims) {
      from = consume(claim.value(), printed, from);
      if (from == null) return claim;
    }
    return null;
  }

  private static Position consume(String value, List<String> printed, Position from) {
    for (int line = from.line(); line < printed.size(); line++) {
      String text = printed.get(line);
      int start = line == from.line() ? from.part() : 0;
      if (start == 0 && matches(value, candidates(text))) return new Position(line + 1, 0);
      String[] parts = text.split(" / ");
      for (int part = start; part < parts.length; part++) {
        if (matches(value, candidates(parts[part]))) return new Position(line, part + 1);
      }
    }
    return null;
  }

  /**
   * What one printed value offers a claim: the value itself and, where it labels what follows,
   * {@code emails : [...]}, the labelled value alone.
   */
  private static List<String> candidates(String text) {
    Matcher labelled = LABELLED.matcher(text.strip());
    return labelled.matches()
        ? List.of(normalise(text), normalise(labelled.group(1)))
        : List.of(normalise(text));
  }

  private static String simpleName(String className) {
    return className.substring(className.lastIndexOf('.') + 1);
  }

  /**
   * A printed line that names its value first, {@code emails : [...]}. The label is plain words, so
   * a value that merely contains a colon, {@code Invalid(NonEmptyList[email: ...])}, is not one.
   */
  private static final Pattern LABELLED = Pattern.compile("^[A-Za-z][\\w .-]*?\\s*:\\s+(.+)$");

  /** Whitespace never carries the claim, so it plays no part in the comparison. */
  private static String normalise(String text) {
    return withoutNote(text).replaceAll("\\s+", "");
  }

  private static boolean matches(String value, List<String> segments) {
    String wanted = normalise(value);
    if (!wanted.contains("...")) return segments.contains(wanted);
    // An abbreviated value stands in for the detail it elides.
    StringBuilder pattern = new StringBuilder();
    String[] parts = wanted.split(Pattern.quote("..."), -1);
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) pattern.append(".*");
      pattern.append(Pattern.quote(parts[i]));
    }
    Pattern abbreviated = Pattern.compile(pattern.toString());
    return segments.stream().anyMatch(segment -> abbreviated.matcher(segment).matches());
  }

  /** Runs the example's {@code main}, capturing what it prints. */
  private static List<String> run(String className) {
    PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try (PrintStream sink = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
      System.setOut(sink);
      Method main = Class.forName(className).getMethod("main", String[].class);
      main.invoke(null, (Object) new String[0]);
    } catch (InvocationTargetException e) {
      throw new AssertionError(className + " threw when run: " + e.getCause(), e.getCause());
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("could not run " + className, e);
    } finally {
      System.setOut(original);
    }
    return captured
        .toString(StandardCharsets.UTF_8)
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .toList();
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * The directory the gate reads, passed by the build. Without it the test would silently pass over
   * an empty set, so it fails loudly instead.
   */
  private static String required(String property) {
    String value = System.getProperty(property);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          property + " is not set: run this through `gradle :hkj-examples:bookVerify`");
    }
    return value;
  }
}

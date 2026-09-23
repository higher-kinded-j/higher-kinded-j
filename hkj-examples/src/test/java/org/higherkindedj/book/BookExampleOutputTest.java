// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
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
 * <p>So each example with a {@code main} is run, its output captured, and every output comment
 * inside an {@code ANCHOR} block matched against it. The three shapes the examples use are all
 * honoured:
 *
 * <ul>
 *   <li>a value on one line, <code>// Valid(Person[name=Ada, age=36])</code>;
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
   * A comment line that opens a printed value: {@code Valid(...)}, {@code Invalid(...)}, or a
   * record's own {@code Name[...]}. Everything else in an anchor is explanation.
   */
  private static final Pattern OPENS_A_VALUE =
      Pattern.compile("^(Valid\\(|Invalid\\(|[A-Z][A-Za-z0-9_]*\\[).*");

  /**
   * One example that can be run, the values its comments promise, and any comment that opened a
   * value and never closed it. The unclosed one is carried rather than thrown here, so it fails
   * against the example it belongs to instead of breaking test discovery for all of them.
   */
  record Example(String className, List<Claim> claims, Claim unclosed) {
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
   * The number of runnable examples must never fall below this. Deleting a {@code main}, or moving
   * an example out of the book package, would otherwise quietly shrink what this gate covers.
   */
  private static final int MINIMUM_RUNNABLE_EXAMPLES = 10;

  private static Example exampleOf(Path source) {
    String text = read(source);
    if (!text.contains("public static void main(")) return null;
    String className = classNameOf(source);
    List<Claim> claims = new ArrayList<>();
    Claim unclosed = claimsIn(text, claims);
    return new Example(className, claims, unclosed);
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
   */
  private static Claim claimsIn(String text, List<Claim> claims) {
    Claim unclosed = null;
    boolean insideAnchor = false;
    int startedAt = 0;
    StringBuilder pending = null;

    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      boolean anchorEnds = line.contains("ANCHOR_END:");
      boolean anchorStarts = !anchorEnds && line.contains("ANCHOR:");
      String comment = insideAnchor && !anchorStarts && !anchorEnds ? commentBody(line) : null;

      if (anchorStarts || anchorEnds || comment == null) {
        if (pending != null && unclosed == null)
          unclosed = new Claim(startedAt, pending.toString());
        pending = null;
        insideAnchor = anchorStarts;
        continue;
      }
      if (pending != null) {
        pending.append(' ').append(comment);
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
    if (example.unclosed() != null) {
      fail(
          """
          %s:%d opens an output value that never closes, so nothing checks it.

            the comment reads: %s
          Close the brackets, or write the line as prose rather than as an output."""
              .formatted(
                  example.className(), example.unclosed().line(), example.unclosed().value()));
    }

    List<String> printed = run(example.className());
    List<String> segments = new ArrayList<>();
    for (String line : printed) {
      segments.add(normalise(line));
      // A line may print two values joined by " / ", as the examples often do.
      for (String part : line.split(" / ")) segments.add(normalise(part));
    }

    for (Claim claim : example.claims()) {
      if (!matches(claim.value(), segments)) {
        fail(
            """
            %s:%d claims an output the example does not print.

              the comment says: %s
              the example printed:
            %s
            Update the comment to what the program prints, or fix the example."""
                .formatted(
                    example.className(),
                    claim.line(),
                    claim.value(),
                    printed.stream()
                        .map(line -> "    " + line)
                        .collect(java.util.stream.Collectors.joining("\n"))));
      }
    }
  }

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

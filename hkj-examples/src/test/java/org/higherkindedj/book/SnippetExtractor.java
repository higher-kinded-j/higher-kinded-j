// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.book;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Pulls the verified code snippets out of an hkj-book page and assembles them into a single Java
 * compilation unit.
 *
 * <p>A snippet opts in with an HTML comment on the line before its fence:
 *
 * <pre>{@code
 * <!-- verify -->
 * ``` java
 * Validated<NonEmptyList<FieldError>, User> user = ...;
 * ```
 * }</pre>
 *
 * <p>The comment is invisible in the rendered book. Opting in per snippet lets the gate be adopted
 * page by page rather than all at once. A marker may also quote a diagnostic instead of asking for
 * a compile - {@code <!-- verify:rejects "..." -->} and {@code <!-- verify:reports "..." -->} - so
 * that a page documenting what the processor refuses is held to what it actually says. See {@link
 * Expectation}.
 *
 * <p>Each snippet is compiled <em>independently</em>, because the snippets on a page are
 * illustrations rather than one program: two of them may legitimately show different {@code User}
 * records. What a snippet elides for readability (the domain types, the {@code parseX} helpers)
 * comes from an optional per-page fixture, which is kept out of the book so the page stays
 * readable.
 */
final class SnippetExtractor {

  /**
   * Opt-in marker. Must sit on its own line immediately before the fence.
   *
   * <p>Bare, it asks for the snippet to compile. With a tail it asks for a diagnostic instead:
   * {@code :rejects "fragment"} or {@code :reports "fragment"}. Everything after {@code verify} is
   * captured so a marker that is misspelt fails loudly rather than being read as the bare form.
   */
  private static final Pattern MARKER = Pattern.compile("^\\s*<!--\\s*verify\\b(.*?)\\s*-->\\s*$");

  /** The tail of a marker that quotes a diagnostic. */
  private static final Pattern DIAGNOSTIC_TAIL =
      Pattern.compile("^:(rejects|reports)\\s+\"(.+)\"$");

  /**
   * How short a quoted fragment may be. A fragment is the whole assertion, so one short enough to
   * match any message at all would leave the marker in place and the check gone.
   */
  private static final int MINIMUM_FRAGMENT_LENGTH = 10;

  /** mdbook writes both "```java" and "``` java". */
  private static final Pattern FENCE_OPEN = Pattern.compile("^\\s*```+\\s*java\\b.*$");

  private static final Pattern FENCE_CLOSE = Pattern.compile("^\\s*```+\\s*$");

  private static final Pattern IMPORT =
      Pattern.compile("^\\s*import\\s+(static\\s+)?[\\w.*]+\\s*;\\s*$");

  /**
   * A top-level type declaration. Anything else is treated as loose statements and wrapped in a
   * method body.
   */
  private static final Pattern DECLARATION =
      Pattern.compile(
          "^\\s*(public\\s+|final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*"
              + "(class|record|interface|enum|@interface)\\s+\\w+");

  /**
   * Any annotation, not just HKJ's own. What it annotates is decided by looking past it: a page may
   * write {@code @RestController} above a class or {@code @Test} above a method, and hoisting the
   * class away from its annotations produces "illegal start of type" rather than a real finding.
   */
  private static final Pattern ANNOTATION = Pattern.compile("^\\s*@\\w+.*$");

  /**
   * A declaration on the SAME line as its annotation, e.g. {@code @GenerateMapping public interface
   * CardMapping extends MappingSpec<Card, CardDto> {}}. The look-ahead past annotations must stop
   * here, or the declaration is skipped over and dumped into the statement body.
   */
  private static final Pattern INLINE_DECLARATION =
      Pattern.compile("\\b(class|record|interface|enum)\\s+\\w+");

  /**
   * A method declaration, which a page writes when it is showing you a signature rather than a
   * statement. It becomes a member of the wrapper class; wrapping it in a method body would nest a
   * method inside a method, which is not legal Java.
   */
  private static final Pattern METHOD =
      Pattern.compile(
          "^\\s*(public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|default\\s+)*"
              // A generic method declares its own type parameters before the return type.
              + "(<[^=;{]+>\\s*)?"
              + "[\\w.$]+(<[^=;]*>)?(\\[])?\\s+\\w+\\s*\\([^;]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{\\s*$");

  /**
   * A body-less signature, which a page writes when it is quoting a shape rather than showing an
   * implementation. It becomes a member of an interface wrapper, where that is legal and still
   * type-checked; a class wrapper would reject it as a method with no body.
   *
   * <p>The look-ahead excludes the statement keywords, because {@code new Order(id, total);} has
   * the same shape as a signature once the modifiers are optional. Type arguments exclude brackets
   * and {@code -} for the same reason: a fluent call chain ending in {@code ;} would otherwise let
   * {@code Path.<E, A>right(order).via(x -> f(x));} pass as one, reading everything from {@code <}
   * to the {@code >} of a lambda arrow as the return type's arguments.
   */
  private static final Pattern ABSTRACT_METHOD =
      Pattern.compile(
          "^\\s*(?!(new|return|throw|assert|yield|case)\\b)"
              + "(public\\s+|protected\\s+|abstract\\s+|static\\s+)*"
              + "(<[^=;{()\\-]+>\\s*)?"
              + "[\\w.$]+(<[^=;{()\\-]*>)?(\\[])?"
              + "\\s+\\w+\\s*\\([^;{]*\\)\\s*(throws\\s+[\\w.,\\s]+)?;$");

  /**
   * A {@code static} field, which a page writes when it is showing a constant rather than a step: a
   * {@code ScopedValue} key, a shared {@code RetryPolicy}. It becomes a member of the wrapper,
   * because {@code static} is not a modifier a local variable may carry.
   */
  private static final Pattern STATIC_FIELD =
      Pattern.compile("^\\s*(public\\s+|private\\s+|protected\\s+)?static\\s+.*");

  /** How many lines a wrapped signature may span before we stop looking. */
  private static final int MAX_SIGNATURE_LINES = 6;

  /** A fixture may be generic, so a page can show `VResultPath<E, A>` without naming a domain. */
  private static final Pattern FIXTURE_DECL =
      Pattern.compile("\\bclass\\s+Fixture\\s*(<([^>]*)>)?");

  private SnippetExtractor() {}

  /**
   * What the gate must observe when it compiles a snippet.
   *
   * <p>The two diagnostic forms exist because the pages most likely to be invalidated by a
   * processor change are the ones documenting what the processor <em>refuses</em>, and a marker
   * meaning "this compiles" cannot express those at all. The quoted fragment is the valuable half:
   * it is what rots when a message is reworded.
   */
  sealed interface Expectation {

    /** The bare {@code <!-- verify -->}: the snippet compiles, with no error and no warning. */
    Expectation COMPILES = new Compiles();

    /** {@code <!-- verify -->}. */
    record Compiles() implements Expectation {}

    /**
     * {@code <!-- verify:rejects "fragment" -->}: the snippet does not compile, and one of the
     * errors quotes {@code fragment}.
     */
    record Rejects(String fragment) implements Expectation {}

    /**
     * {@code <!-- verify:reports "fragment" -->}: the snippet compiles, and a note or a warning
     * quotes {@code fragment}. A processor note is how the library reports a gap it cannot fail the
     * build over, so the page documents one exactly as it documents an error.
     */
    record Reports(String fragment) implements Expectation {}
  }

  /** One verified snippet: its source, what it asserts, and where it came from. */
  record Snippet(int index, int lineNumber, String body, Expectation expectation) {}

  /** Every verified snippet on one page. */
  record Page(Path file, String slug, List<Snippet> snippets) {
    Page {
      snippets = List.copyOf(snippets);
    }

    boolean isEmpty() {
      return snippets.isEmpty();
    }
  }

  static Page extract(Path page, Path bookRoot) throws IOException {
    List<String> lines = Files.readAllLines(page);
    List<Snippet> snippets = new ArrayList<>();

    for (int i = 0; i < lines.size(); i++) {
      var marker = MARKER.matcher(lines.get(i));
      if (!marker.matches()) {
        continue;
      }
      Expectation expectation = expectationOf(marker.group(1), page, i + 1);
      // The fence must follow the marker, allowing for a blank line between them.
      int fence = i + 1;
      while (fence < lines.size() && lines.get(fence).isBlank()) {
        fence++;
      }
      if (fence >= lines.size() || !FENCE_OPEN.matcher(lines.get(fence)).matches()) {
        throw new IllegalStateException(
            "%s:%d: `<!-- verify -->` is not followed by a ``` java fence."
                .formatted(page.getFileName(), i + 1));
      }
      StringBuilder body = new StringBuilder();
      int line = fence + 1;
      for (; line < lines.size() && !FENCE_CLOSE.matcher(lines.get(line)).matches(); line++) {
        body.append(lines.get(line)).append('\n');
      }
      snippets.add(new Snippet(snippets.size(), fence + 2, body.toString(), expectation));
      i = line;
    }
    return new Page(page, slugOf(page, bookRoot), snippets);
  }

  /**
   * Reads the marker's tail. Empty asks for a compile; {@code :rejects "..."} or {@code :reports
   * "..."} asks for a diagnostic. Anything else is a typo, and a typo read as the bare form would
   * quietly turn a rejection check into a compile check the snippet cannot pass.
   */
  private static Expectation expectationOf(String tail, Path page, int lineNumber) {
    if (tail.isBlank()) {
      return Expectation.COMPILES;
    }
    var diagnostic = DIAGNOSTIC_TAIL.matcher(tail.strip());
    if (!diagnostic.matches()) {
      throw new IllegalStateException(
          ("%s:%d: `<!-- verify%s -->` is not a marker. Write `<!-- verify -->`, "
                  + "`<!-- verify:rejects \"quoted diagnostic\" -->` or "
                  + "`<!-- verify:reports \"quoted diagnostic\" -->`.")
              .formatted(page.getFileName(), lineNumber, tail));
    }
    String fragment = diagnostic.group(2);
    if (fragment.strip().length() < MINIMUM_FRAGMENT_LENGTH) {
      throw new IllegalStateException(
          ("%s:%d: the quoted diagnostic \"%s\" is too short to assert anything. Quote at least"
                  + " %d characters of the message the page claims.")
              .formatted(page.getFileName(), lineNumber, fragment, MINIMUM_FRAGMENT_LENGTH));
    }
    return "rejects".equals(diagnostic.group(1))
        ? new Expectation.Rejects(fragment)
        : new Expectation.Reports(fragment);
  }

  /** "effect/path_vresult.md" -> "effect_path_vresult". A legal Java identifier, and unique. */
  static String slugOf(Path page, Path bookRoot) {
    String relative = bookRoot.relativize(page).toString();
    return relative.replaceAll("\\.md$", "").replaceAll("[^A-Za-z0-9]", "_");
  }

  /**
   * Assembles one snippet (plus its page's optional fixture) into a compilation unit.
   *
   * <p>Imports are hoisted. A snippet that declares a type contributes a top-level type; anything
   * else is wrapped in a method, so loose statements type-check in a real scope.
   *
   * <p>A type the snippet declares itself wins over the fixture's, so a page may show its own
   * {@code User} without colliding with the one the fixture supplies for its other snippets.
   */
  static String toCompilationUnit(Page page, Snippet snippet, String fixture) {
    List<String> imports = new ArrayList<>();
    List<String> snippetTypes = new ArrayList<>();
    List<String> members = new ArrayList<>();
    List<String> statements = new ArrayList<>();
    List<String> signatures = new ArrayList<>();
    partition(snippet.body(), imports, snippetTypes, members, statements, signatures);

    List<String> fixtureTypes = new ArrayList<>();
    if (!fixture.isBlank()) {
      partition(
          fixture, imports, fixtureTypes, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
    // Drop any fixture type the snippet declares for itself.
    List<String> declared = snippetTypes.stream().map(SnippetExtractor::typeNameOf).toList();
    List<String> types = new ArrayList<>(snippetTypes);
    fixtureTypes.stream().filter(t -> !declared.contains(typeNameOf(t))).forEach(types::add);

    // A fixture supplies the helpers a snippet calls bare (`parseName(dto.name())`). Extending it
    // puts them in scope without the page having to spell out an import it never wrote. A generic
    // fixture also lends its type parameters, so a page can show `VResultPath<E, A>` as a shape.
    String fixtureParams = fixtureTypeParameters(types);

    String name = page.slug() + "_" + snippet.index();
    StringBuilder unit = new StringBuilder("package bookverify;\n\n");
    imports.stream().distinct().sorted().forEach(i -> unit.append(i).append('\n'));
    // A snippet that is nothing but body-less signatures is a page quoting shapes, so it is
    // wrapped in an interface. Anything else becomes a class, which is what can hold a method body
    // and inherit the fixture's helpers.
    boolean quotesSignatures = !signatures.isEmpty() && members.isEmpty() && statements.isEmpty();

    // Only `unused`: a snippet legitimately declares locals it never reads. `unchecked`/`rawtypes`
    // are deliberately NOT suppressed. The real build compiles with -Xlint:unchecked,rawtypes
    // -Werror, so suppressing them here would green-light a page showing code a reader cannot
    // build.
    unit.append("\n@SuppressWarnings(\"unused\")\n");
    unit.append(quotesSignatures ? "interface " : "final class ").append(name);
    if (fixtureParams != null) {
      unit.append(fixtureParams);
      if (!quotesSignatures) {
        unit.append(" extends Fixture").append(fixtureParams);
      }
    }
    unit.append(" {\n");
    signatures.forEach(m -> unit.append('\n').append(m).append('\n'));
    members.forEach(m -> unit.append('\n').append(m).append('\n'));
    if (!statements.isEmpty()) {
      unit.append("\n  void snippet() throws Exception {\n");
      statements.forEach(unit::append);
      unit.append("  }\n");
    }
    unit.append("}\n\n");
    // Top-level types last: a file may hold many, provided none is public.
    types.forEach(t -> unit.append(t).append('\n'));
    return unit.toString();
  }

  /**
   * The type-parameter list to mirror from the fixture: {@code ""} for a plain {@code Fixture},
   * {@code "<E, A>"} for a generic one, or {@code null} when the page has no fixture at all.
   */
  private static String fixtureTypeParameters(List<String> types) {
    for (String type : types) {
      if (!"Fixture".equals(typeNameOf(type))) {
        continue;
      }
      var matcher = FIXTURE_DECL.matcher(type);
      if (matcher.find() && matcher.group(2) != null) {
        // Strip any bounds: `<E extends Throwable>` is declared once, referenced as `<E>`.
        String names =
            Arrays.stream(matcher.group(2).split(","))
                .map(p -> p.strip().split("\\s+")[0])
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return "<" + names + ">";
      }
      return "";
    }
    return null;
  }

  /**
   * The last line of a method signature starting at {@code start}, or -1 if that is not a signature.
   *
   * <p>A page routinely wraps a signature over several lines:
   *
   * <pre>{@code
   * public static Either<DomainError, Company> updateManagerEmail(
   *     Company company, String department, String email) {
   * }</pre>
   *
   * <p>Matching one line at a time misses that, and the declaration is then treated as loose
   * statements and wrapped inside a method, which does not parse. So join lines until it closes.
   */
  private static int endOfSignature(List<String> lines, int start) {
    String first = lines.get(start).strip();
    // A signature starts with code and opens a parameter list. Without this guard the join runs on
    // from a blank line through several complete declarations until the concatenation happens to
    // end
    // in `{`, and swallows them whole.
    if (first.isBlank() || first.startsWith("//") || !first.contains("(")) {
      return -1;
    }
    StringBuilder joined = new StringBuilder();
    int limit = Math.min(lines.size(), start + MAX_SIGNATURE_LINES);
    for (int i = start; i < limit; i++) {
      if (i > start) {
        joined.append(' ');
      }
      // Strip comments and literals first, exactly as endOfDeclaration does: a trailing `// note`
      // on the `{` line would otherwise stop the signature ending in `{`, and a `;` or `}` inside a
      // comment would trip the guard below into a false negative.
      joined.append(stripLiterals(lines.get(i)).strip());
      String candidate = joined.toString().strip();
      // A `;` ends a statement and a `}` closes a construct: either way we have run past whatever
      // this is, and it was not a signature.
      if (candidate.endsWith(";") || candidate.contains("}")) {
        return -1;
      }
      if (candidate.endsWith("{") && METHOD.matcher(candidate).matches()) {
        return i;
      }
    }
    return -1;
  }

  /**
   * The last line of a body-less signature starting at {@code start}, or -1 if that is not one.
   *
   * <p>A quoted signature wraps over several lines as readily as a declaration does, so the lines
   * are joined until one ends in {@code ;}. A brace on the way means this has a body, and belongs
   * to {@link #endOfSignature} instead.
   */
  private static int endOfAbstractSignature(List<String> lines, int start) {
    String first = lines.get(start).strip();
    if (first.isBlank() || first.startsWith("//") || !first.contains("(")) {
      return -1;
    }
    StringBuilder joined = new StringBuilder();
    int limit = Math.min(lines.size(), start + MAX_SIGNATURE_LINES);
    for (int i = start; i < limit; i++) {
      if (i > start) {
        joined.append(' ');
      }
      joined.append(stripLiterals(lines.get(i)).strip());
      String candidate = joined.toString().strip();
      // A brace means this has a body; an arrow or an assignment means it is a statement. None of
      // the three can appear in a signature the page is merely quoting.
      if (candidate.contains("{")
          || candidate.contains("}")
          || candidate.contains("->")
          || candidate.contains("=")) {
        return -1;
      }
      if (candidate.endsWith(";")) {
        return ABSTRACT_METHOD.matcher(candidate).matches() ? i : -1;
      }
    }
    return -1;
  }

  /** The declared name, so a snippet's own type can shadow the fixture's. */
  private static String typeNameOf(String declaration) {
    var matcher =
        Pattern.compile("\\b(class|record|interface|enum)\\s+(\\w+)").matcher(declaration);
    return matcher.find() ? matcher.group(2) : declaration;
  }

  /**
   * Splits a block into imports, top-level types, class members, body-less signatures, and loose
   * statements.
   */
  private static void partition(
      String source,
      List<String> imports,
      List<String> declarations,
      List<String> members,
      List<String> loose,
      List<String> signatures) {
    List<String> lines = List.of(source.split("\n", -1));
    int i = 0;
    while (i < lines.size()) {
      String line = lines.get(i);
      if (IMPORT.matcher(line).matches()) {
        imports.add(line.strip());
        i++;
      } else if (ANNOTATION.matcher(line).matches()) {
        // Look past the annotations to see what they annotate, and keep them attached to it.
        int subject = i;
        while (subject < lines.size()
            && ANNOTATION.matcher(lines.get(subject)).matches()
            && !INLINE_DECLARATION.matcher(lines.get(subject)).find()) {
          subject++;
        }
        if (subject < lines.size() && DECLARATION.matcher(lines.get(subject)).find()) {
          int end = endOfDeclaration(lines, subject);
          String declaration = String.join("\n", lines.subList(i, end + 1));
          declarations.add(
              declaration.replaceAll(
                  "\\bpublic\\s+(?=(final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*"
                      + "(class|record|interface|enum)\\b)",
                  ""));
          i = end + 1;
        } else if (subject < lines.size() && endOfSignature(lines, subject) >= 0) {
          int end = endOfDeclaration(lines, endOfSignature(lines, subject));
          members.add("  " + String.join("\n  ", lines.subList(i, end + 1)));
          i = end + 1;
        } else if (subject < lines.size() && endOfAbstractSignature(lines, subject) >= 0) {
          // An annotated signature quotation: `@InstanceOf(..) Prism<JsonNode, ObjectNode> o();`.
          int end = endOfAbstractSignature(lines, subject);
          signatures.add("  " + String.join("\n  ", lines.subList(i, end + 1)));
          i = end + 1;
        } else {
          // An annotation on something we do not model (a field, a parameter): leave it in place.
          loose.add("    " + line + "\n");
          i++;
        }
      } else if (DECLARATION.matcher(line).find()) {
        int end = endOfDeclaration(lines, i);
        String declaration = String.join("\n", lines.subList(i, end + 1));
        // Several top-level types share one file, so none of them may be public. The modifier is
        // not
        // always at the start of a line: a page may write `@GenerateMapping public interface X {}`.
        declarations.add(
            declaration.replaceAll(
                "\\bpublic\\s+(?=(final\\s+|abstract\\s+|sealed\\s+|non-sealed\\s+|static\\s+)*"
                    + "(class|record|interface|enum)\\b)",
                ""));
        i = end + 1;
      } else if (endOfSignature(lines, i) >= 0) {
        // The page is showing a whole method. It becomes a member: a method cannot nest in a
        // method. The signature may wrap, so brace-walk from where it actually closes.
        int end = endOfDeclaration(lines, endOfSignature(lines, i));
        members.add("  " + String.join("\n  ", lines.subList(i, end + 1)));
        i = end + 1;
      } else if (endOfAbstractSignature(lines, i) >= 0) {
        // The page is quoting a signature. It becomes a member of the interface wrapper, which is
        // the one place a method with no body type-checks.
        int end = endOfAbstractSignature(lines, i);
        signatures.add("  " + String.join("\n  ", lines.subList(i, end + 1)));
        i = end + 1;
      } else if (STATIC_FIELD.matcher(line).matches()) {
        // A static field is a member, not a statement: `static` cannot modify a local.
        int end = endOfDeclaration(lines, i);
        members.add("  " + String.join("\n  ", lines.subList(i, end + 1)));
        i = end + 1;
      } else if (line.isBlank() || line.strip().startsWith("//")) {
        i++;
      } else {
        loose.add("    " + line + "\n");
        i++;
      }
    }
  }

  /**
   * Walks braces to find where a declaration ends. Handles the one-line `record X(...) {}` form.
   */
  private static int endOfDeclaration(List<String> lines, int start) {
    int depth = 0;
    boolean opened = false;
    for (int i = start; i < lines.size(); i++) {
      String line = stripLiterals(lines.get(i));
      for (char c : line.toCharArray()) {
        if (c == '{') {
          depth++;
          opened = true;
        } else if (c == '}') {
          depth--;
        }
      }
      if (opened && depth <= 0) {
        return i;
      }
      // An abstract/interface member with no body: `Dashboard assemble(User u);`
      if (!opened && line.strip().endsWith(";")) {
        return i;
      }
    }
    return lines.size() - 1;
  }

  /**
   * Braces inside strings, char literals and comments must not count towards nesting depth: a lone
   * {@code // }} in a comment would otherwise close a declaration early. Strings are blanked first,
   * so a {@code //} inside one is already gone before comments are stripped. Comments are removed
   * entirely rather than blanked to {@code ""}, so a trailing comment cannot mask the {@code ;}
   * that marks a body-less member.
   */
  private static String stripLiterals(String line) {
    return line.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"")
        .replaceAll("'(\\\\.|[^'\\\\])'", "' '")
        .replaceAll("//.*", "")
        .replaceAll("/\\*.*?\\*/", "");
  }
}

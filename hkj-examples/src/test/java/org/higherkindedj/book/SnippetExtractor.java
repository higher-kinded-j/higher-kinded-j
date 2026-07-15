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
 * page by page rather than all at once.
 *
 * <p>Each snippet is compiled <em>independently</em>, because the snippets on a page are
 * illustrations rather than one program: two of them may legitimately show different {@code User}
 * records. What a snippet elides for readability (the domain types, the {@code parseX} helpers)
 * comes from an optional per-page fixture, which is kept out of the book so the page stays
 * readable.
 */
final class SnippetExtractor {

  /** Opt-in marker. Must sit on its own line immediately before the fence. */
  private static final Pattern MARKER = Pattern.compile("^\\s*<!--\\s*verify\\s*-->\\s*$");

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

  /** How many lines a wrapped signature may span before we stop looking. */
  private static final int MAX_SIGNATURE_LINES = 6;

  /** A fixture may be generic, so a page can show `VResultPath<E, A>` without naming a domain. */
  private static final Pattern FIXTURE_DECL =
      Pattern.compile("\\bclass\\s+Fixture\\s*(<([^>]*)>)?");

  private SnippetExtractor() {}

  /** One verified snippet: its source, and where it came from (for the failure message). */
  record Snippet(int index, int lineNumber, String body) {}

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
      if (!MARKER.matcher(lines.get(i)).matches()) {
        continue;
      }
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
      snippets.add(new Snippet(snippets.size(), fence + 2, body.toString()));
      i = line;
    }
    return new Page(page, slugOf(page, bookRoot), snippets);
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
    partition(snippet.body(), imports, snippetTypes, members, statements);

    List<String> fixtureTypes = new ArrayList<>();
    if (!fixture.isBlank()) {
      partition(fixture, imports, fixtureTypes, new ArrayList<>(), new ArrayList<>());
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
    // Only `unused`: a snippet legitimately declares locals it never reads. `unchecked`/`rawtypes`
    // are deliberately NOT suppressed. The real build compiles with -Xlint:unchecked,rawtypes
    // -Werror, so suppressing them here would green-light a page showing code a reader cannot
    // build.
    unit.append("\n@SuppressWarnings(\"unused\")\n");
    unit.append("final class ").append(name);
    if (fixtureParams != null) {
      unit.append(fixtureParams).append(" extends Fixture").append(fixtureParams);
    }
    unit.append(" {\n");
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

  /** The declared name, so a snippet's own type can shadow the fixture's. */
  private static String typeNameOf(String declaration) {
    var matcher =
        Pattern.compile("\\b(class|record|interface|enum)\\s+(\\w+)").matcher(declaration);
    return matcher.find() ? matcher.group(2) : declaration;
  }

  /** Splits a block into imports, top-level types, class members, and loose statements. */
  private static void partition(
      String source,
      List<String> imports,
      List<String> declarations,
      List<String> members,
      List<String> loose) {
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

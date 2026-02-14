// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Applicative;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.TypeArity;
import org.higherkindedj.hkt.WitnessArity;
import org.higherkindedj.optics.Traversal;
import org.jspecify.annotations.NullMarked;

/**
 * A final utility class providing static factory methods for creating {@link Traversal}s for {@link
 * String} types.
 *
 * <p>These combinators enable text processing operations by breaking strings into smaller units
 * (characters, words, lines) that can be traversed and modified.
 *
 * <p>All traversals maintain referential transparency and preserve immutability.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Transform each character
 * Traversal<String, Character> charTraversal = StringTraversals.chars();
 * String uppercased = Traversals.modify(charTraversal, Character::toUpperCase, "hello");
 * // Result: "HELLO"
 *
 * // Transform each word
 * Traversal<String, String> wordTraversal = StringTraversals.worded();
 * String result = Traversals.modify(wordTraversal, String::toUpperCase, "hello world");
 * // Result: "HELLO WORLD"
 *
 * // Transform each line
 * Traversal<String, String> lineTraversal = StringTraversals.lined();
 * String prefixed = Traversals.modify(lineTraversal, line -> "> " + line, "line1\nline2");
 * // Result: "> line1\n> line2"
 * }</pre>
 */
@NullMarked
public final class StringTraversals {

  /** Private constructor to prevent instantiation. */
  private StringTraversals() {}

  /**
   * Creates a {@code Traversal} that focuses on each character in a string.
   *
   * <p>The string is decomposed into individual characters, each of which can be transformed via an
   * effectful function. The transformed characters are then recombined into a new string.
   *
   * <p>Empty strings produce an empty traversal (zero elements focused).
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<String, Character> charTraversal = StringTraversals.chars();
   *
   * // Uppercase all characters
   * String result = Traversals.modify(charTraversal, Character::toUpperCase, "hello");
   * // Result: "HELLO"
   *
   * // Get all characters
   * List<Character> chars = Traversals.getAll(charTraversal, "abc");
   * // Result: ['a', 'b', 'c']
   *
   * // Filter vowels and uppercase them (via composition)
   * Traversal<String, Character> vowels = charTraversal.filtered(c ->
   *     "aeiouAEIOU".indexOf(c) >= 0
   * );
   * String modified = Traversals.modify(vowels, Character::toUpperCase, "hello world");
   * // Result: "hEllO wOrld"
   * }</pre>
   *
   * @return A {@code Traversal} focusing on each character in a string.
   */
  public static Traversal<String, Character> chars() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> modifyF(
          final Function<Character, Kind<F, Character>> f,
          final String source,
          final Applicative<F> applicative) {

        if (source.isEmpty()) {
          return applicative.of(source);
        }

        // Convert string to list of characters
        final List<Character> charList =
            source.chars().mapToObj(c -> (char) c).collect(Collectors.toList());

        // Traverse the character list
        final Kind<F, List<Character>> traversedChars =
            Traversals.traverseList(charList, f, applicative);

        // Reconstruct string from characters
        return applicative.map(
            chars -> {
              final StringBuilder sb = new StringBuilder(chars.size());
              for (final Character ch : chars) {
                sb.append(ch);
              }
              return sb.toString();
            },
            traversedChars);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that splits a string by whitespace and focuses on each word.
   *
   * <p>The string is split using {@code "\\s+"} (one or more whitespace characters), producing a
   * list of words. Each word can be transformed via an effectful function. The transformed words
   * are then joined back with single spaces.
   *
   * <p><b>Note:</b> Multiple consecutive whitespace characters are normalized to a single space in
   * the output when words are present. Leading and trailing whitespace is removed when joining
   * words.
   *
   * <p>Empty strings or strings containing only whitespace produce an empty traversal (focusing on
   * zero words), and {@code modify} operations preserve the original string unchanged.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<String, String> wordTraversal = StringTraversals.worded();
   *
   * // Uppercase each word
   * String result = Traversals.modify(wordTraversal, String::toUpperCase, "hello world");
   * // Result: "HELLO WORLD"
   *
   * // Get all words
   * List<String> words = Traversals.getAll(wordTraversal, "foo  bar\tbaz");
   * // Result: ["foo", "bar", "baz"]
   *
   * // Capitalize first letter of each word
   * String capitalized = Traversals.modify(
   *     wordTraversal,
   *     word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase(),
   *     "hello WORLD"
   * );
   * // Result: "Hello World"
   * }</pre>
   *
   * @return A {@code Traversal} focusing on each word in a string.
   */
  public static Traversal<String, String> worded() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> modifyF(
          final Function<String, Kind<F, String>> f,
          final String source,
          final Applicative<F> applicative) {

        final String trimmed = source.trim();
        if (trimmed.isEmpty()) {
          return applicative.of(source);
        }

        // Split by whitespace (one or more whitespace characters)
        final List<String> words =
            Arrays.stream(trimmed.split("\\s+")).collect(Collectors.toList());

        // Traverse the word list
        final Kind<F, List<String>> traversedWords = Traversals.traverseList(words, f, applicative);

        // Join words back with single spaces
        return applicative.map(wordList -> String.join(" ", wordList), traversedWords);
      }
    };
  }

  /**
   * Creates a {@code Traversal} that splits a string by line separators and focuses on each line.
   *
   * <p>The string is split using line separators ({@code \n}, {@code \r\n}, or {@code \r}). Each
   * line can be transformed via an effectful function. The transformed lines are then joined back
   * with {@code \n} (Unix-style line separator).
   *
   * <p><b>Note:</b> The output normalizes all line separators to {@code \n}. If you need to
   * preserve the original line separator style, consider using a different approach.
   *
   * <p>Empty strings produce an empty traversal. Strings with no line separators focus on the
   * entire string as a single line.
   *
   * <p>Example:
   *
   * <pre>{@code
   * Traversal<String, String> lineTraversal = StringTraversals.lined();
   *
   * // Prefix each line
   * String result = Traversals.modify(lineTraversal, line -> "> " + line, "foo\nbar\nbaz");
   * // Result: "> foo\n> bar\n> baz"
   *
   * // Get all lines
   * List<String> lines = Traversals.getAll(lineTraversal, "line1\nline2\nline3");
   * // Result: ["line1", "line2", "line3"]
   *
   * // Number each line
   * AtomicInteger counter = new AtomicInteger(1);
   * String numbered = Traversals.modify(
   *     lineTraversal,
   *     line -> counter.getAndIncrement() + ". " + line,
   *     "first\nsecond\nthird"
   * );
   * // Result: "1. first\n2. second\n3. third"
   * }</pre>
   *
   * @return A {@code Traversal} focusing on each line in a string.
   */
  public static Traversal<String, String> lined() {
    return new Traversal<>() {
      @Override
      public <F extends WitnessArity<TypeArity.Unary>> Kind<F, String> modifyF(
          final Function<String, Kind<F, String>> f,
          final String source,
          final Applicative<F> applicative) {

        if (source.isEmpty()) {
          return applicative.of(source);
        }

        // Split by line separators (\n, \r\n, or \r)
        final List<String> lines = Arrays.asList(source.split("\\r?\\n|\\r"));

        // Traverse the line list
        final Kind<F, List<String>> traversedLines = Traversals.traverseList(lines, f, applicative);

        // Join lines back with \n (Unix-style line separator)
        return applicative.map(lineList -> String.join("\n", lineList), traversedLines);
      }
    };
  }
}

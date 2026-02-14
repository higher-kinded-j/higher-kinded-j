// Copyright (c) 2025 - 2026 Magnus Smith
// Licensed under the MIT License. See LICENSE.md in the project root for license information.
package org.higherkindedj.optics.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.list.ListKind;
import org.higherkindedj.hkt.list.ListKindHelper;
import org.higherkindedj.hkt.list.ListMonad;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.optional.OptionalMonad;
import org.higherkindedj.optics.Traversal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("StringTraversals - String-specific Traversals")
class StringTraversalsTest {

  @Nested
  @DisplayName("chars() - Focus on each character")
  class CharsTests {

    @Test
    @DisplayName("chars() should modify each character")
    void charsModifiesEachCharacter() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      String result = Traversals.modify(charTraversal, Character::toUpperCase, "hello");

      assertThat(result).isEqualTo("HELLO");
    }

    @Test
    @DisplayName("chars() getAll should return all characters")
    void charsGetAllReturnsAllCharacters() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      List<Character> result = Traversals.getAll(charTraversal, "abc");

      assertThat(result).containsExactly('a', 'b', 'c');
    }

    @Test
    @DisplayName("chars() with empty string should return empty")
    void charsWithEmptyString() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      String result = Traversals.modify(charTraversal, Character::toUpperCase, "");

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(charTraversal, "")).isEmpty();
    }

    @Test
    @DisplayName("chars() with single character")
    void charsWithSingleCharacter() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      String result = Traversals.modify(charTraversal, c -> Character.toUpperCase(c), "a");

      assertThat(result).isEqualTo("A");
      assertThat(Traversals.getAll(charTraversal, "x")).containsExactly('x');
    }

    @Test
    @DisplayName("chars() should preserve order")
    void charsPreservesOrder() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      String result = Traversals.modify(charTraversal, c -> c, "abc123");

      assertThat(result).isEqualTo("abc123");
    }

    @Test
    @DisplayName("chars() should handle special characters")
    void charsHandlesSpecialCharacters() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      List<Character> result = Traversals.getAll(charTraversal, "a!b@c#");

      assertThat(result).containsExactly('a', '!', 'b', '@', 'c', '#');
    }

    @Test
    @DisplayName("chars() should compose with filtered")
    void charsComposesWithFiltered() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();
      Traversal<String, Character> vowelsOnly =
          charTraversal.filtered(c -> "aeiouAEIOU".indexOf(c) >= 0);

      String result = Traversals.modify(vowelsOnly, Character::toUpperCase, "hello world");

      assertThat(result).isEqualTo("hEllO wOrld");
    }

    @Test
    @DisplayName("chars() should transform to different characters")
    void charsTransformsToDifferentCharacters() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      String result = Traversals.modify(charTraversal, c -> c == 'a' ? 'z' : c, "banana");

      assertThat(result).isEqualTo("bznznz");
    }

    @Test
    @DisplayName("chars() with effectful operation should produce all combinations")
    void charsWithEffectfulOperation() {
      Traversal<String, Character> charTraversal = StringTraversals.chars();

      Kind<ListKind.Witness, String> result =
          charTraversal.modifyF(
              c -> ListKindHelper.LIST.widen(List.of(c, Character.toUpperCase(c))),
              "ab",
              ListMonad.INSTANCE);

      List<String> resultList = ListKindHelper.LIST.narrow(result);

      // Should produce all combinations: 2^2 = 4 results
      // Order depends on applicative sequencing (left-to-right, inner choices vary fastest)
      assertThat(resultList).containsExactly("ab", "aB", "Ab", "AB");
    }
  }

  @Nested
  @DisplayName("worded() - Focus on each word")
  class WordedTests {

    @Test
    @DisplayName("worded() should modify each word")
    void wordedModifiesEachWord() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, "hello world");

      assertThat(result).isEqualTo("HELLO WORLD");
    }

    @Test
    @DisplayName("worded() getAll should return all words")
    void wordedGetAllReturnsAllWords() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      List<String> result = Traversals.getAll(wordTraversal, "foo bar baz");

      assertThat(result).containsExactly("foo", "bar", "baz");
    }

    @Test
    @DisplayName("worded() with empty string should return empty")
    void wordedWithEmptyString() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, "");

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(wordTraversal, "")).isEmpty();
    }

    @Test
    @DisplayName("worded() with whitespace-only string should return original")
    void wordedWithWhitespaceOnly() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, "   \t  ");

      assertThat(result).isEqualTo("   \t  ");
      assertThat(Traversals.getAll(wordTraversal, "   ")).isEmpty();
    }

    @Test
    @DisplayName("worded() should normalize multiple spaces to single space")
    void wordedNormalizesWhitespace() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, w -> w, "foo  bar\t\tbaz");

      assertThat(result).isEqualTo("foo bar baz");
    }

    @Test
    @DisplayName("worded() with single word")
    void wordedWithSingleWord() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, "hello");

      assertThat(result).isEqualTo("HELLO");
      assertThat(Traversals.getAll(wordTraversal, "world")).containsExactly("world");
    }

    @Test
    @DisplayName("worded() should handle leading and trailing whitespace")
    void wordedHandlesLeadingTrailingWhitespace() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, "  hello world  ");

      assertThat(result).isEqualTo("HELLO WORLD");
    }

    @Test
    @DisplayName("worded() should capitalise first letter of each word")
    void wordedCapitalisesWords() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result =
          Traversals.modify(
              wordTraversal,
              word -> word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase(),
              "hello WORLD");

      assertThat(result).isEqualTo("Hello World");
    }

    @Test
    @DisplayName("worded() should compose with filtered")
    void wordedComposesWithFiltered() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();
      Traversal<String, String> longWords = wordTraversal.filtered(w -> w.length() > 3);

      String result = Traversals.modify(longWords, String::toUpperCase, "hi hello world ok");

      assertThat(result).isEqualTo("hi HELLO WORLD ok");
    }

    @Test
    @DisplayName("worded() should handle tab characters")
    void wordedHandlesTabs() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      List<String> result = Traversals.getAll(wordTraversal, "foo\tbar\tbaz");

      assertThat(result).containsExactly("foo", "bar", "baz");
    }

    @Test
    @DisplayName("worded() should handle string with many consecutive whitespace types")
    void wordedHandlesVariousWhitespace() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String input = "  \t\n  word1  \t  word2   \n\t  word3  ";
      String result = Traversals.modify(wordTraversal, String::toUpperCase, input);

      assertThat(result).isEqualTo("WORD1 WORD2 WORD3");
    }

    @Test
    @DisplayName("worded() should handle string after splitting with effectful operation")
    void wordedWithEffectfulOperation() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String input = "a b c";
      Kind<ListKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word, word.toUpperCase())),
              input,
              ListMonad.INSTANCE);

      List<String> resultList = ListKindHelper.LIST.narrow(result);

      // Should produce all combinations: 2^3 = 8 results
      assertThat(resultList).hasSize(8);
      assertThat(resultList).contains("a b c", "A B C", "a B c", "A b C");
    }

    @Test
    @DisplayName("worded() with single whitespace character")
    void wordedWithSingleSpace() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String result = Traversals.modify(wordTraversal, String::toUpperCase, " ");

      assertThat(result).isEqualTo(" ");
    }

    @Test
    @DisplayName("worded() should preserve original when only whitespace")
    void wordedPreservesWhitespaceOnlySource() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String source = "   \t  \n  ";
      List<String> words = Traversals.getAll(wordTraversal, source);

      assertThat(words).isEmpty();
    }

    @Test
    @DisplayName("worded() with modifyF on whitespace-only source should preserve it")
    void wordedModifyFWithWhitespaceOnlySource() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String source = "   \t  \n  ";
      Kind<OptionalKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              source,
              OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);

      // Should preserve original when no words to process
      assertThat(optResult).contains(source);
    }

    @Test
    @DisplayName("worded() with modifyF on empty trimmed source")
    void wordedModifyFWithEmptyTrimmedSource() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String source = "";
      Kind<OptionalKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              source,
              OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).contains("");
    }

    @Test
    @DisplayName("worded() with modifyF using ListMonad on whitespace-only source")
    void wordedModifyFListMonadWhitespaceOnly() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String source = "   ";
      Kind<ListKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word, word.toUpperCase())),
              source,
              ListMonad.INSTANCE);

      List<String> resultList = ListKindHelper.LIST.narrow(result);

      // Should preserve original when no words
      assertThat(resultList).containsExactly(source);
    }

    @Test
    @DisplayName("worded() with Optional applicative should succeed for all words")
    void wordedWithOptionalApplicative() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String input = "hello world";
      Kind<OptionalKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              input,
              OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).contains("HELLO WORLD");
    }

    @Test
    @DisplayName("worded() with Optional applicative should fail if transformation fails")
    void wordedWithOptionalApplicativeFails() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String input = "hello world";
      Kind<OptionalKind.Witness, String> result =
          wordTraversal.modifyF(
              word ->
                  word.equals("world")
                      ? OptionalKindHelper.OPTIONAL.widen(Optional.empty())
                      : OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              input,
              OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);

      assertThat(optResult).isEmpty();
    }

    @Test
    @DisplayName(
        "worded() with modifyF on trimmed-empty source using different applicative contexts")
    void wordedTrimmedEmptyWithVariousApplicatives() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      // Empty string with OptionalMonad should preserve empty
      String emptySource = "";
      Kind<OptionalKind.Witness, String> optResult =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              emptySource,
              OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(optResult)).contains("");

      // Empty string with ListMonad should preserve empty
      Kind<ListKind.Witness, String> listResult =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word.toUpperCase())),
              emptySource,
              ListMonad.INSTANCE);
      assertThat(ListKindHelper.LIST.narrow(listResult)).containsExactly("");
    }

    @Test
    @DisplayName(
        "worded() with single space character should return original preserving whitespace")
    void wordedSingleSpacePreserved() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      // Single space trims to empty
      String singleSpace = " ";
      String result = Traversals.modify(wordTraversal, String::toUpperCase, singleSpace);

      assertThat(result).isEqualTo(" ");

      // Verify with modifyF using OptionalMonad
      Kind<OptionalKind.Witness, String> optResult =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              singleSpace,
              OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(optResult)).contains(" ");
    }

    @Test
    @DisplayName("worded() with tabs and spaces only should preserve structure")
    void wordedTabsAndSpacesOnly() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String tabsAndSpaces = "\t  \t  ";

      // With modify
      String modifyResult = Traversals.modify(wordTraversal, String::toUpperCase, tabsAndSpaces);
      assertThat(modifyResult).isEqualTo(tabsAndSpaces);

      // With modifyF using ListMonad
      Kind<ListKind.Witness, String> listResult =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word, word.toUpperCase())),
              tabsAndSpaces,
              ListMonad.INSTANCE);
      assertThat(ListKindHelper.LIST.narrow(listResult)).containsExactly(tabsAndSpaces);

      // Verify getAll returns empty
      List<String> words = Traversals.getAll(wordTraversal, tabsAndSpaces);
      assertThat(words).isEmpty();
    }

    @Test
    @DisplayName("worded() with newlines only should preserve structure")
    void wordedNewlinesOnly() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String newlinesOnly = "\n\n\n";

      String result = Traversals.modify(wordTraversal, String::toUpperCase, newlinesOnly);
      assertThat(result).isEqualTo(newlinesOnly);

      // Verify with modifyF
      Kind<OptionalKind.Witness, String> optResult =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              newlinesOnly,
              OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(optResult)).contains(newlinesOnly);
    }

    @Test
    @DisplayName("worded() with mixed whitespace characters should preserve when empty")
    void wordedMixedWhitespacePreserved() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      String mixed = " \t\n\r ";

      // With modify
      String result = Traversals.modify(wordTraversal, String::toUpperCase, mixed);
      assertThat(result).isEqualTo(mixed);

      // With modifyF using both applicatives
      Kind<OptionalKind.Witness, String> optResult =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              mixed,
              OptionalMonad.INSTANCE);
      assertThat(OptionalKindHelper.OPTIONAL.narrow(optResult)).contains(mixed);

      Kind<ListKind.Witness, String> listResult =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word.toUpperCase())),
              mixed,
              ListMonad.INSTANCE);
      assertThat(ListKindHelper.LIST.narrow(listResult)).containsExactly(mixed);
    }

    @Test
    @DisplayName("worded() normal traversal path with OptionalMonad transformation")
    void wordedNormalPathOptionalMonad() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      // Ensure we go through the normal traversal path (not early returns)
      String input = "alpha beta gamma";
      Kind<OptionalKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> OptionalKindHelper.OPTIONAL.widen(Optional.of(word.toUpperCase())),
              input,
              OptionalMonad.INSTANCE);

      Optional<String> optResult = OptionalKindHelper.OPTIONAL.narrow(result);
      assertThat(optResult).contains("ALPHA BETA GAMMA");
    }

    @Test
    @DisplayName("worded() normal traversal path with ListMonad non-determinism")
    void wordedNormalPathListMonad() {
      Traversal<String, String> wordTraversal = StringTraversals.worded();

      // Ensure we go through the normal traversal path with effectful transformation
      String input = "x y";
      Kind<ListKind.Witness, String> result =
          wordTraversal.modifyF(
              word -> ListKindHelper.LIST.widen(List.of(word, word.toUpperCase())),
              input,
              ListMonad.INSTANCE);

      List<String> resultList = ListKindHelper.LIST.narrow(result);

      // Should produce 2^2 = 4 combinations
      assertThat(resultList).hasSize(4);
      assertThat(resultList).containsExactly("x y", "x Y", "X y", "X Y");
    }
  }

  @Nested
  @DisplayName("lined() - Focus on each line")
  class LinedTests {

    @Test
    @DisplayName("lined() should modify each line")
    void linedModifiesEachLine() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, line -> "> " + line, "foo\nbar\nbaz");

      assertThat(result).isEqualTo("> foo\n> bar\n> baz");
    }

    @Test
    @DisplayName("lined() getAll should return all lines")
    void linedGetAllReturnsAllLines() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      List<String> result = Traversals.getAll(lineTraversal, "line1\nline2\nline3");

      assertThat(result).containsExactly("line1", "line2", "line3");
    }

    @Test
    @DisplayName("lined() with empty string should return empty")
    void linedWithEmptyString() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, line -> "> " + line, "");

      assertThat(result).isEmpty();
      assertThat(Traversals.getAll(lineTraversal, "")).isEmpty();
    }

    @Test
    @DisplayName("lined() with single line (no newlines)")
    void linedWithSingleLine() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, String::toUpperCase, "hello");

      assertThat(result).isEqualTo("HELLO");
      assertThat(Traversals.getAll(lineTraversal, "world")).containsExactly("world");
    }

    @Test
    @DisplayName("lined() should normalize \\r\\n to \\n")
    void linedNormalizesWindowsLineEndings() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, line -> line, "foo\r\nbar\r\nbaz");

      assertThat(result).isEqualTo("foo\nbar\nbaz");
    }

    @Test
    @DisplayName("lined() should handle \\r line endings")
    void linedHandlesMacLineEndings() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, line -> line, "foo\rbar\rbaz");

      assertThat(result).isEqualTo("foo\nbar\nbaz");
    }

    @Test
    @DisplayName("lined() should number each line")
    void linedNumbersLines() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();
      AtomicInteger counter = new AtomicInteger(1);

      String result =
          Traversals.modify(
              lineTraversal,
              line -> counter.getAndIncrement() + ". " + line,
              "first\nsecond\nthird");

      assertThat(result).isEqualTo("1. first\n2. second\n3. third");
    }

    @Test
    @DisplayName("lined() should compose with filtered")
    void linedComposesWithFiltered() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();
      Traversal<String, String> nonEmptyLines = lineTraversal.filtered(line -> !line.isEmpty());

      String result = Traversals.modify(nonEmptyLines, String::toUpperCase, "foo\n\nbar\nbaz");

      assertThat(result).isEqualTo("FOO\n\nBAR\nBAZ");
    }

    @Test
    @DisplayName("lined() should handle empty lines")
    void linedHandlesEmptyLines() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      List<String> result = Traversals.getAll(lineTraversal, "foo\n\nbar");

      assertThat(result).containsExactly("foo", "", "bar");
    }

    @Test
    @DisplayName("lined() should handle trailing newline")
    void linedHandlesTrailingNewline() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      // Trailing newline doesn't create an extra empty line (standard split behavior)
      List<String> result = Traversals.getAll(lineTraversal, "foo\nbar\n");

      assertThat(result).containsExactly("foo", "bar");
    }

    @Test
    @DisplayName("lined() should indent each line")
    void linedIndentsLines() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      String result = Traversals.modify(lineTraversal, line -> "  " + line, "foo\nbar");

      assertThat(result).isEqualTo("  foo\n  bar");
    }

    @Test
    @DisplayName("lined() with effectful operation should produce all combinations")
    void linedWithEffectfulOperation() {
      Traversal<String, String> lineTraversal = StringTraversals.lined();

      Kind<ListKind.Witness, String> result =
          lineTraversal.modifyF(
              line -> ListKindHelper.LIST.widen(List.of(line, line.toUpperCase())),
              "a\nb",
              ListMonad.INSTANCE);

      List<String> resultList = ListKindHelper.LIST.narrow(result);

      // Should produce all combinations: 2^2 = 4 results
      // Order depends on applicative sequencing (left-to-right, inner choices vary fastest)
      assertThat(resultList).containsExactly("a\nb", "a\nB", "A\nb", "A\nB");
    }
  }
}

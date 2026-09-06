// Fixture for hkj-book/src/optics/string_traversals.md
//
// The page works text - characters, words and lines - and reaches for a document and a properties
// file to show the traversals composing. The two records are declared here; the snippet that shows
// the document shadows this copy.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.toList;
import static org.higherkindedj.hkt.instances.Witnesses.optional;
import static org.higherkindedj.hkt.validated.ValidatedKindHelper.VALIDATED;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.Semigroups;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.optional.OptionalKind;
import org.higherkindedj.hkt.optional.OptionalKindHelper;
import org.higherkindedj.hkt.validated.Validated;
import org.higherkindedj.hkt.validated.ValidatedKind;
import org.higherkindedj.optics.Traversal;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.util.StringTraversals;
import org.higherkindedj.optics.util.Traversals;

@GenerateLenses
record Document(String title, List<String> paragraphs) {}

@GenerateLenses
record Config(String properties) {}

class Fixture {

  static <A> A sample() {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }

  static final Document document = sample();

  static final Config config = sample();

  static final String input = "hello world";

  static final String text = input;

  static final String logContent = "INFO started\nERROR failed";

  static final String originalLog = logContent;

  static final String sourceCode = "# a comment\nvalue = 1";

  static final String csvContent = "a,b,c\nd,e,f";

  static final String propertiesContent = "key = value";

  static void processEmail(String email) {
    throw new UnsupportedOperationException("a fixture value: snippets are compiled, not run");
  }
}

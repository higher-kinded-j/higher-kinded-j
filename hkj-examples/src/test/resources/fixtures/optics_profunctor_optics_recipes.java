// Fixture for hkj-book/src/optics/profunctor_optics_recipes.md
//
// Every recipe on the page declares the model it adapts, so this fixture only carries the imports
// those declarations and their helper classes need.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static java.util.stream.Collectors.joining;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.higherkindedj.optics.Iso;
import org.higherkindedj.optics.Lens;
import org.higherkindedj.optics.annotations.GenerateLenses;

record UserId(String value) {}

record UserName(String value) {}

record Email(String value) {}

@GenerateLenses
record User(UserId id, UserName name, Email email, LocalDate createdAt) {}

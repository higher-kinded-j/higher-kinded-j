// Fixture for hkj-book/src/optics/focus_navigation.md
//
// The page includes its examples from NavigationBook; the two fences left are
// annotation declarations for controlling navigator generation. They name the
// records here, and the annotation processor generates the *Focus and
// *Lenses companions during snippet compilation.
//
// NOTE: imports in a fixture serve the snippet this file is spliced into (this
// one also happens to use its imports itself). Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures
// (see build.gradle.kts).

import java.util.Map;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;

@GenerateLenses
@GenerateFocus
record Setting(String value) {}

@GenerateLenses
@GenerateFocus
record Config(Map<String, Setting> settings) {}

@GenerateLenses
@GenerateFocus(generateNavigators = true)
record Address(String street, String city) {}

@GenerateFocus(generateNavigators = true)
record Level2(String value) {}

@GenerateFocus(generateNavigators = true)
record Level1(Level2 nested) {}

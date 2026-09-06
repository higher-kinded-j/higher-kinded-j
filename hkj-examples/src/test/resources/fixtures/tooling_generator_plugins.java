// Fixture for hkj-book/src/tooling/generator_plugins.md
//
// The usage snippet annotates a record with an Eclipse Collections field; the value it modifies is
// declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.optics.util.Traversals;

class Fixture {

  static final Object warehouseSeed = null;
}

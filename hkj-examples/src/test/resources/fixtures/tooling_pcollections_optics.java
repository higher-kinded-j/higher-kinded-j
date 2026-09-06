// Fixture for hkj-book/src/tooling/pcollections_optics.md
//
// The portfolio record the page annotates holds positions; the position type is declared here.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import org.higherkindedj.optics.Each;
import org.higherkindedj.optics.each.EachInstances;
import org.pcollections.HashTreePMap;
import org.pcollections.PMap;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

record Position(String symbol, double quantity) {}

class Fixture {}

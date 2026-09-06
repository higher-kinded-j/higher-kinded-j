// Fixture for hkj-book/src/tooling/pcollections_integration.md
//
// Both snippets widen a PVector into the List HKT and narrow it back.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.list;
import static org.higherkindedj.hkt.list.ListKindHelper.LIST;

import java.util.List;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.list.ListKind;
import org.pcollections.PVector;
import org.pcollections.TreePVector;

class Fixture {}

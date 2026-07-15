// Fixture for .claude/skills/hkj-optics/reference/container-types.md
//
// The page shows what @GenerateFocus emits for each container cardinality, one field at a time. The
// records that carry those fields are declared here and annotated for real, so the processor
// generates EmployeeLenses / AssetClassFocus during the gate: the page's `FocusPath.of(...).some()`,
// `.each(...)`, `.at(index)` and `.nullable()` shapes are therefore checked against genuinely
// generated code rather than a stand-in for it.
//
// The records are TOP-LEVEL, not nested: a nested record makes the processor join the enclosing
// names (`FixtureEmployeeLenses`), which is not what the page says.
//
// NOTE: the imports below look unused *here*. They are for the snippet this file is spliced into.
// That is why spotless excludes src/test/resources/fixtures (see build.gradle.kts).

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import java.util.Set;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import org.higherkindedj.optics.processing.generator.BaseTraversableGenerator;
import org.higherkindedj.optics.processing.spi.Cardinality;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.higherkindedj.hkt.either.Either;
import org.higherkindedj.optics.annotations.GenerateFocus;
import org.higherkindedj.optics.annotations.GenerateLenses;
import org.higherkindedj.optics.each.EachInstances;
import org.higherkindedj.optics.focus.AffinePath;
import org.higherkindedj.optics.focus.FocusPath;
import org.higherkindedj.optics.focus.TraversalPath;
import org.higherkindedj.optics.util.Affines;
import org.jspecify.annotations.Nullable;

/** A leaf the page traverses to. */
record Skill(String name, int level) {}

/** One field per cardinality the page tabulates: required, Optional, @Nullable, List, Either, Map. */
@GenerateLenses
@GenerateFocus
record Employee(
    String name,
    Optional<String> email,
    @Nullable String nickname,
    List<Skill> skills,
    Either<String, Integer> timeout,
    Map<String, Integer> scores) {}

/** A holding in the reader's portfolio: the leaf of the Eclipse Collections example. */
record Position(String ticker, int quantity) {}

/** An SPI ZERO_OR_MORE field: its static Focus method returns FocusPath, hence the manual widening. */
@GenerateLenses
@GenerateFocus
record AssetClass(String className, ImmutableList<Position> positions) {}

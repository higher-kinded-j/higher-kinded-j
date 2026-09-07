// Fixture for hkj-book/src/monads/stream_monad.md
//
// The page works entirely against StreamOps and the MonadZero instance, so what it elides is the
// static import that makes `range(1, 11)` read as it does.
//
// The fixture is generic so the reference sections can quote `fromArray(T... elements)` and
// `zip(..., BiFunction<A, B, C>)` as shapes, without inventing a domain for them.
//
// NOTE: imports in a fixture serve the snippets it is spliced into. Spotless excludes
// src/test/resources so an "unused import" cleanup cannot break fixtures (see build.gradle.kts).

import static org.higherkindedj.hkt.instances.Witnesses.stream;
import static org.higherkindedj.hkt.stream.StreamKindHelper.STREAM;
import static org.higherkindedj.hkt.stream.StreamOps.concat;
import static org.higherkindedj.hkt.stream.StreamOps.drop;
import static org.higherkindedj.hkt.stream.StreamOps.filter;
import static org.higherkindedj.hkt.stream.StreamOps.forEach;
import static org.higherkindedj.hkt.stream.StreamOps.fromArray;
import static org.higherkindedj.hkt.stream.StreamOps.fromIterable;
import static org.higherkindedj.hkt.stream.StreamOps.range;
import static org.higherkindedj.hkt.stream.StreamOps.rangeClosed;
import static org.higherkindedj.hkt.stream.StreamOps.take;
import static org.higherkindedj.hkt.stream.StreamOps.tap;
import static org.higherkindedj.hkt.stream.StreamOps.toList;
import static org.higherkindedj.hkt.stream.StreamOps.toSet;
import static org.higherkindedj.hkt.stream.StreamOps.zip;
import static org.higherkindedj.hkt.stream.StreamOps.zipWithIndex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.higherkindedj.hkt.Kind;
import org.higherkindedj.hkt.MonadZero;
import org.higherkindedj.hkt.instances.Instances;
import org.higherkindedj.hkt.stream.StreamKind;
import org.higherkindedj.hkt.tuple.Tuple2;

class Fixture<A, B, C, T> {}
